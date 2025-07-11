package com.aiolos.badgeridentitycore.provider.rpc;

import com.aiolos.badgercommon.redis.UserRedisKeyBuilder;
import com.aiolos.badgeridentitycore.api.AccountTokenApi;
import com.aiolos.badgeridentitycore.dto.AccountDTO;
import com.aiolos.badgeridentitycore.provider.utils.AnonymousIdGenerator;
import com.aiolos.common.utils.ConvertBeanUtil;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@DubboService
public class AccountTokenApiImpl implements AccountTokenApi {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private UserRedisKeyBuilder userRedisKeyBuilder;
    @Resource
    private AnonymousIdGenerator anonymousIdGenerator;

    @Override
    public String createToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(userRedisKeyBuilder.buildUserTokenKey(token), userId, 7, TimeUnit.DAYS);
        return token;
    }

    @Override
    public AccountDTO getUserByToken(String token) {
        AccountDTO dto = new AccountDTO();
        Long userId = null;
        Object obj = redisTemplate.opsForValue().get(userRedisKeyBuilder.buildUserTokenKey(token));
        if (obj instanceof Integer) {
            userId = ((Integer) obj).longValue();
        } else if (obj instanceof Long) {
            userId = (Long) obj;
        } else {
            return dto;
        }
        dto.setUserId(userId);
        Object userObj = redisTemplate.opsForValue().get(userRedisKeyBuilder.buildUserInfoKey(userId));
        if (userObj != null) {
            dto = ConvertBeanUtil.convert(userObj, AccountDTO.class);
        }

        return dto;
    }

    @Override
    public Long getOrCreateAnonymousId(String deviceId) {
        String key = userRedisKeyBuilder.buildAnonymousIdKey(deviceId);
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj != null) {
            return (Long) obj;
        }

        long anonymousId = anonymousIdGenerator.generateAnonymousId();
        redisTemplate.opsForValue().set(key, anonymousId, 7, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(userRedisKeyBuilder.buildAnonymousDeviceIdKey(anonymousId), deviceId, 7, TimeUnit.DAYS);
        
        return anonymousId;
    }
}
