package com.aiolos.badger.user.provider.service.impl;

import com.aiolos.badger.common.redis.UserRedisKeyBuilder;
import com.aiolos.badger.identitycore.api.AccountTokenApi;
import com.aiolos.badger.idgenerator.api.IdGeneratorApi;
import com.aiolos.badger.idgenerator.enums.IdPolicyEnum;
import com.aiolos.badger.model.po.UserPhoneMapping;
import com.aiolos.badger.service.UserPhoneMappingService;
import com.aiolos.common.enums.error.ErrorEnum;
import com.aiolos.common.exception.util.ExceptionUtil;
import com.aiolos.common.redis.builder.CommonSmsRedisKeyBuilder;
import com.aiolos.common.redis.builder.CommonUserRedisKeyBuilder;
import com.aiolos.common.util.ConvertBeanUtil;
import com.aiolos.badger.model.po.User;
import com.aiolos.badger.service.UserService;
import com.aiolos.badger.user.dto.UserDTO;
import com.aiolos.badger.user.provider.model.bo.LoginBO;
import com.aiolos.badger.user.provider.model.vo.UserVO;
import com.aiolos.badger.user.provider.mq.producer.UpdateUserInfoProducer;
import com.aiolos.badger.user.provider.service.BadgerUserService;
import com.google.common.collect.Maps;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BadgerUserServiceImpl implements BadgerUserService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private UserService userService;
    @Resource
    private UserPhoneMappingService userPhoneMappingService;
    @Resource
    private CommonUserRedisKeyBuilder commonUserRedisKeyBuilder;
    @Resource
    private CommonSmsRedisKeyBuilder commonSmsRedisKeyBuilder;
    @Resource
    private UserRedisKeyBuilder userRedisKeyBuilder;
    @Resource
    private UpdateUserInfoProducer updateUserInfoProducer;
    @DubboReference
    private IdGeneratorApi idGeneratorApi;
    @DubboReference
    private AccountTokenApi accountTokenApi;

    @Value("${spring.profiles.active}")
    private String activeProfile;
    @Value("${cookie-domain}")
    private String cookieDomain;

    @Transactional
    @Override
    public UserVO login(LoginBO loginBO, HttpServletResponse response) {
        
        if (StringUtils.isBlank(loginBO.getCode()) || StringUtils.isBlank(loginBO.getCode())) {
            ExceptionUtil.throwException(ErrorEnum.BIND_EXCEPTION_ERROR);
        }

        String smsRedisKey = commonSmsRedisKeyBuilder.buildSmsLoginCodeKey(loginBO.getPhone());
        Object redisVal = redisTemplate.opsForValue().get(smsRedisKey);
        if (redisVal == null) {
            ExceptionUtil.throwException(ErrorEnum.SMS_CODE_EXPIRED);
        }

        String cacheCode = redisVal.toString();
        if (!cacheCode.equals(loginBO.getCode())) {
            ExceptionUtil.throwException(ErrorEnum.SMS_CODE_INCORRECT);
        }

        // 未注册则注册
        UserVO userVO = this.queryByPhone(loginBO.getPhone());
        if (userVO == null) {
            User newUser = new User();
            // 即便获取不到分布式id，mybatis-plus的@TableId("user_id")会隐式创建一个Long类型id，type=IdType.NONE也一样
            // 如果ShardingSphere有配置keyGenerateStrategy也会自动生成主键，可以设置雪花算法
            newUser.setUserId(idGeneratorApi.getNonSeqId(IdPolicyEnum.USER_ID_POLICY.getPrimaryKey()));
            newUser.setNickName("用户_" + RandomUtils.secure().randomInt(10000000, 99999999));
            newUser.setPhone(loginBO.getPhone());
            userVO = ConvertBeanUtil.convert(newUser, UserVO.class);

            if (userService.save(newUser)) {
                UserPhoneMapping mapping = new UserPhoneMapping();
                mapping.setUserId(newUser.getUserId());
                mapping.setPhone(loginBO.getPhone());
                userPhoneMappingService.save(mapping);
            }
        }

        redisTemplate.opsForValue().set(commonUserRedisKeyBuilder.buildUserInfoKey(userVO.getUserId()), userVO, 7, TimeUnit.DAYS);

        redisTemplate.delete(smsRedisKey);
        redisTemplate.delete(userRedisKeyBuilder.buildUserPhoneKey(loginBO.getPhone()));

        String token = accountTokenApi.createToken(userVO.getUserId());
        ResponseCookie cookie = ResponseCookie.from("vs-token", token)
                .maxAge(Duration.ofDays(30))
                .httpOnly(true)
                .secure(activeProfile.equalsIgnoreCase("prod")) // 仅https传输
                .domain(cookieDomain)
                .path("/")
                .build();
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Set-Cookie", cookie.toString());
        return userVO;
    }

    @Override
    public UserVO getUserById(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        
        String userKey = commonUserRedisKeyBuilder.buildUserInfoKey(userId);
        Object obj = redisTemplate.opsForValue().get(userKey);
        if (obj != null) {
            UserVO userVO = ConvertBeanUtil.convert(obj, UserVO.class);
            return userVO.getUserId() != null ? userVO : null;
        }
        UserVO userVO = ConvertBeanUtil.convert(userService.getById(userId), UserVO.class);
        if (userVO != null) {
            redisTemplate.opsForValue().set(userKey, userVO, 7, TimeUnit.DAYS);
        } else {
            // 缓存空值
            redisTemplate.opsForValue().set(userKey, new UserVO(), 60, TimeUnit.SECONDS);
        }
        return userVO;
    }

    @Override
    public void insertUser(UserDTO userDTO) {
        if (userDTO == null || userDTO.getUserId() == null) {
            return;
        }
        userService.save(ConvertBeanUtil.convert(userDTO, User.class));
    }

    @Override
    public void updateUserInfo(UserDTO userDTO) {
        if (userDTO == null || userDTO.getUserId() == null) {
            return;
        }
        // 目前情况分库分表
        boolean updated = userService.updateById(ConvertBeanUtil.convert(userDTO, User.class));
        if (updated) {
            redisTemplate.delete(commonUserRedisKeyBuilder.buildUserInfoKey(userDTO.getUserId()));
            updateUserInfoProducer.deleteUserCache(userDTO.getUserId());
        }
    }

    @Override
    public Map<Long, UserVO> batchQueryUserInfo(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Maps.newHashMap();
        }
        userIds = userIds.stream().filter(id -> id >= 100).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(userIds)) {
            return Maps.newHashMap();
        }

        List<String> keyList = userIds.stream().map(userId -> commonUserRedisKeyBuilder.buildUserInfoKey(userId)).collect(Collectors.toList());
        List<UserVO> dtoListInRedis = redisTemplate.opsForValue().multiGet(keyList).stream()
                .filter(Objects::nonNull).map(x -> ConvertBeanUtil.convert(x, UserVO.class)).collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(dtoListInRedis) && dtoListInRedis.size() == userIds.size()) {
            return dtoListInRedis.stream().collect(Collectors.toMap(UserVO::getUserId, Function.identity()));
        }

        List<Long> keyInRedis = dtoListInRedis.stream().map(UserVO::getUserId).collect(Collectors.toList());
        List<Long> keyNotInRedis = userIds.stream().filter(id -> !keyInRedis.contains(id)).collect(Collectors.toList());

        Map<Long, List<Long>> userIdMap = keyNotInRedis.stream().collect(Collectors.groupingBy(id -> id % 100));

        ForkJoinPool forkJoinPool = new ForkJoinPool(4);
        List<UserVO> dbQueryResult = new ArrayList<>();
        try {
            dbQueryResult = forkJoinPool.submit(() ->
                    userIdMap.values().parallelStream()
                            .flatMap(ids -> ConvertBeanUtil.convertList(userService.listByIds(ids), UserVO.class).stream())
                            .collect(Collectors.toList())
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            forkJoinPool.shutdown();
        }

        // 从数据库查询出来的数据缓存到redis
        if (!CollectionUtils.isEmpty(dbQueryResult)) {
            Map<String, UserVO> dbQueryMap = dbQueryResult.stream().collect(Collectors.toMap(dto -> commonUserRedisKeyBuilder.buildUserInfoKey(dto.getUserId()), Function.identity()));
            redisTemplate.opsForValue().multiSet(dbQueryMap);
            // 批量设置过期时间
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                dbQueryMap.keySet().forEach(key -> connection.keyCommands().expire(key.getBytes(), commonUserRedisKeyBuilder.randomExpireSeconds( 60 * 60 * 24 * 7)));
                return null;
            });

            dbQueryResult.addAll(dtoListInRedis);
        }

        return dbQueryResult.stream().collect(Collectors.toMap(UserVO::getUserId, Function.identity()));
    }

    @Override
    public UserVO queryByPhone(String phone) {
        if (StringUtils.isBlank(phone))
            return null;
        String key = userRedisKeyBuilder.buildUserPhoneKey(phone);
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj != null) {
            // 可能是空缓存
            UserVO userVO = ConvertBeanUtil.convert(obj, UserVO.class);
            if (userVO.getUserId() == null) {
                return null;
            }
            return userVO;
        }

        UserPhoneMapping mapping = userPhoneMappingService.lambdaQuery().eq(UserPhoneMapping::getPhone, phone).one();
        if (mapping != null) {
            User user = userService.lambdaQuery().eq(User::getUserId, mapping.getUserId()).one();
            if (user != null) {
                UserVO userVO = ConvertBeanUtil.convert(user, UserVO.class);
                redisTemplate.opsForValue().set(key, userVO, 30, TimeUnit.MINUTES);
                return userVO;
            }
        }
        
        // 防止缓存穿透，设置空缓存
        redisTemplate.opsForValue().set(key, new UserVO(), 60, TimeUnit.SECONDS);
        return null;
    }
}
