package com.aiolos.badger.user.provider.service.impl;

import com.aiolos.badger.common.constant.UserTagFieldNameConstants;
import com.aiolos.badger.common.redis.UserRedisKeyBuilder;
import com.aiolos.badger.enums.UserTagEnum;
import com.aiolos.badger.model.po.UserTag;
import com.aiolos.badger.service.UserTagService;
import com.aiolos.badger.user.provider.mq.producer.UpdateUserInfoProducer;
import com.aiolos.badger.user.provider.service.BadgerUserTagService;
import com.aiolos.common.util.ConvertBeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BadgerUserTagServiceImpl implements BadgerUserTagService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private UserRedisKeyBuilder userRedisKeyBuilder;
    @Autowired
    private UserTagService userTagService;
    @Autowired
    private UpdateUserInfoProducer updateUserInfoProducer;
    
    @Override
    public boolean setTag(Long userId, UserTagEnum userTagEnum) {

        // 基于redis分布式锁设置用户标签，redisTemplate.delete(setNXKey);无法验证锁持有者会误删
        /*String setNXKey = userProviderCommonRedisKeyBuilder.buildUserTagLockKey(userId);
        String setNXResult = redisTemplate.execute((RedisCallback<String>) connection -> {
            // 获取序列化器
            RedisSerializer keySerializer = redisTemplate.getKeySerializer();
            RedisSerializer valueSerializer = redisTemplate.getValueSerializer();
            return (String) connection.execute("set",
                    keySerializer.serialize(setNXKey),      // key
                    valueSerializer.serialize("-1"),      // value
                    "NX".getBytes(StandardCharsets.UTF_8),  // 仅当key不存在时才设置
                    "EX".getBytes(StandardCharsets.UTF_8),  // 设置过期时间，单位秒
                    "3".getBytes(StandardCharsets.UTF_8));  // 过期时间3秒
        });
        if (!"ok".equalsIgnoreCase(setNXResult)) {
            log.error("{}获取redis锁失败", Thread.currentThread().getName());
            return false;
        }*/

        String key = userRedisKeyBuilder.buildUserTagLockKey(userId);
        String value = UUID.randomUUID().toString().replace("-", "");
        String lockScript = 
                "if redis.call('set', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then " +
                "   return true" +    // 加锁成功
                "else " +
                "   local currentValue = redis.call('get', KEYS[1]) " + // 这个脚本没有重复使用所以不会执行到这里
                "   if currentValue == ARGV[1] then " +
                "       redis.call('expire', KEYS[1], ARGV[2])" +   // 锁重入/续期
                "       return true " +
                "   end " +
                "end " +
                "return false"; // 加锁失败
        Boolean locked = redisTemplate.execute(new DefaultRedisScript<>(lockScript, Boolean.class), Collections.singletonList(key), value, "3");// 3秒过期
        log.info("{}获取redis锁结果: {}", Thread.currentThread().getName(), locked);

        boolean updated = false;
        if (Boolean.TRUE.equals(locked)) {
            try {
                UserTag userTag = getUserTagFromRedis(userId);
                if (userTag != null) {
                    if (!checkTag(userTag, userTagEnum)) {
                        userTag.setTagInfo01(userTag.getTagInfo01() | userTagEnum.getTag());
                        updated = userTagService.update(userTag, Wrappers.lambdaUpdate(UserTag.class).eq(UserTag::getUserId, userId));

                        redisTemplate.delete(userRedisKeyBuilder.buildUserTagKey(userId));
                        log.info("修改用户{}的标签{}, result: {}", userId, userTagEnum.getDesc(), updated);
                    }
                } else {
                    userTag = new UserTag();
                    userTag.setUserId(userId);
                    userTag.setTagInfo01(userTagEnum.getTag());
                    updated = userTagService.save(userTag);
                    log.info("保存用户{}的标签{}, result: {}", userId, userTagEnum.getDesc(), updated);
                }
            } finally {
                String unlockScript = 
                        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "   return redis.call('del', KEYS[1]) " + 
                        "else " +
                        "   return 0 " +
                        "end";
                redisTemplate.execute(new DefaultRedisScript<>(unlockScript, Long.class), Collections.singletonList(key), value);
            }
        }
        return updated;
    }

    @Override
    public boolean cancelTag(Long userId, UserTagEnum userTagEnum) {
        
        boolean updated = false;
        UserTag userTag = getUserTagFromRedis(userId);
        if (userTag != null) {
            if (checkTag(userTag, userTagEnum)) {
                
                // a &(~b) 对应位清零
                userTag.setTagInfo01(userTag.getTagInfo01() & ~userTagEnum.getTag());
                updated = userTagService.update(userTag, Wrappers.lambdaUpdate(UserTag.class).eq(UserTag::getUserId, userId));
                if (updated) {
                    redisTemplate.delete(userRedisKeyBuilder.buildUserTagKey(userId));
                    updateUserInfoProducer.deleteUserTagCache(userId);
                }
            }
        }
        return updated;
    }

    @Override
    public boolean checkTag(Long userId, UserTagEnum userTagEnum) {
        UserTag userTag = getUserTagFromRedis(userId);
        return checkTag(userTag, userTagEnum);
    }

    private boolean checkTag(UserTag userTag, UserTagEnum userTagEnum) {

        if (userTag == null || userTag.getUserId() == null || userTagEnum.getTag() <= 0)
            return false;
        
        if (userTagEnum.getFieldName().equals(UserTagFieldNameConstants.TAG_INFO_01) && userTag.getTagInfo01() > 0) {
            return (userTag.getTagInfo01() & userTagEnum.getTag()) == userTagEnum.getTag();
        } else if (userTagEnum.getFieldName().equals(UserTagFieldNameConstants.TAG_INFO_02) && userTag.getTagInfo02() > 0) {
            return (userTag.getTagInfo02() & userTagEnum.getTag()) == userTagEnum.getTag();
        } else if (userTagEnum.getFieldName().equals(UserTagFieldNameConstants.TAG_INFO_03) && userTag.getTagInfo03() > 0) {
            return (userTag.getTagInfo03() & userTagEnum.getTag()) == userTagEnum.getTag();
        }
        return false;
    }
    
    private UserTag getUserTagFromRedis(Long userId) {
        UserTag userTag = null;
        Object obj = redisTemplate.opsForValue().get(userRedisKeyBuilder.buildUserTagKey(userId));
        if (obj != null) {
            userTag = ConvertBeanUtil.convert(obj, UserTag.class);
        } else {
            userTag = userTagService.getOne(Wrappers.lambdaQuery(UserTag.class).eq(UserTag::getUserId, userId));
            if (userTag != null) {
                redisTemplate.opsForValue().set(userRedisKeyBuilder.buildUserTagKey(userId), userTag, 7, TimeUnit.DAYS);
            }
        }
        return userTag;
    }
}
