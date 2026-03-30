package com.aiolos.badger.user.provider.service.impl;

import com.aiolos.badger.common.redis.UserRedisKeyBuilder;
import com.aiolos.badger.identitycore.api.AccountTokenApi;
import com.aiolos.badger.identitycore.dto.AccountTokenDTO;
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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BadgerUserServiceImpl implements BadgerUserService {

    private static final BCryptPasswordEncoder BCRYPT_PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

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

    @Value("${cookie-domain}")
    private String cookieDomain;

    @Transactional
    @Override
    public UserVO login(LoginBO loginBO, HttpServletResponse response) {
        if (loginBO == null || StringUtils.isBlank(loginBO.getPhone())) {
            ExceptionUtil.throwException(ErrorEnum.BIND_EXCEPTION_ERROR);
        }
        boolean useSmsLogin = StringUtils.isNotBlank(loginBO.getCode());
        boolean usePasswordLogin = StringUtils.isNotBlank(loginBO.getPassword());
        if (useSmsLogin == usePasswordLogin) {
            ExceptionUtil.throwException(ErrorEnum.BIND_EXCEPTION_ERROR);
        }

        UserVO userVO;
        if (useSmsLogin) {
            String smsRedisKey = commonSmsRedisKeyBuilder.buildSmsLoginCodeKey(loginBO.getPhone());
            Object redisVal = redisTemplate.opsForValue().get(smsRedisKey);
            if (redisVal == null) {
                ExceptionUtil.throwException(ErrorEnum.SMS_CODE_EXPIRED);
            }
            if (!redisVal.toString().equals(loginBO.getCode())) {
                ExceptionUtil.throwException(ErrorEnum.SMS_CODE_INCORRECT);
            }

            userVO = this.queryByPhone(loginBO.getPhone());
            if (userVO == null) {
                User newUser = new User();
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

            redisTemplate.delete(smsRedisKey);
            redisTemplate.delete(userRedisKeyBuilder.buildUserPhoneKey(loginBO.getPhone()));
        } else {
            User user = getUserByPhoneFromDb(loginBO.getPhone());
            if (user == null || user.getUserId() == null) {
                ExceptionUtil.throwException(ErrorEnum.USER_DOES_NOT_EXIST);
            }
            if (StringUtils.isBlank(user.getPassword()) || !PASSWORD_ENCODER.matches(loginBO.getPassword(), user.getPassword())) {
                ExceptionUtil.throwException(ErrorEnum.BIND_EXCEPTION_ERROR);
            }
            userVO = ConvertBeanUtil.convert(user, UserVO.class);
            redisTemplate.delete(userRedisKeyBuilder.buildUserPhoneKey(loginBO.getPhone()));
        }

        userVO.setPassword(null);
        redisTemplate.opsForValue().set(commonUserRedisKeyBuilder.buildUserInfoKey(userVO.getUserId()), userVO, 7, TimeUnit.DAYS);
        AccountTokenDTO tokenDTO = accountTokenApi.createToken(userVO.getUserId());
        userVO.setToken(tokenDTO.getAccessToken());
        userVO.setRefreshToken(tokenDTO.getRefreshToken());
        return userVO;
    }

    @Override
    public AccountTokenDTO refreshToken(String refreshToken) {
        return accountTokenApi.refreshToken(refreshToken);
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        // 直接从 Header 获取 Token
        String token = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (StringUtils.isBlank(token)) {
            return;
        }

        String tokenKey = userRedisKeyBuilder.buildUserTokenKey(token);
        Long userId = getUserIdByTokenKey(tokenKey);
        if (userId == null) {
            redisTemplate.delete(tokenKey);
            return;
        }
        redisTemplate.delete(Arrays.asList(tokenKey, commonUserRedisKeyBuilder.buildUserInfoKey(userId)));
    }

    private Long getUserIdByTokenKey(String tokenKey) {
        Object obj = redisTemplate.opsForValue().get(tokenKey);
        if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        }
        if (obj instanceof Long) {
            return (Long) obj;
        }
        return null;
    }

    private String getCookieValue(HttpServletRequest request, String cookieName) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @Override
    public UserVO getUserById(Long userId) {
        if (userId == null || userId <= 0) {
            return new UserVO();
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
    public void changePasswordBySms(Long userId, String code, String newPassword) {
        if (userId == null || userId <= 0 || StringUtils.isBlank(code) || StringUtils.isBlank(newPassword)) {
            ExceptionUtil.throwException(ErrorEnum.BIND_EXCEPTION_ERROR);
        }
        if (newPassword.length() < 8) {
            ExceptionUtil.throwException(ErrorEnum.BIND_EXCEPTION_ERROR);
        }

        User user = userService.getById(userId);
        if (user == null || StringUtils.isBlank(user.getPhone())) {
            ExceptionUtil.throwException(ErrorEnum.USER_DOES_NOT_EXIST);
        }

        String smsRedisKey = commonSmsRedisKeyBuilder.buildSmsLoginCodeKey(user.getPhone());
        Object redisVal = redisTemplate.opsForValue().get(smsRedisKey);
        if (redisVal == null) {
            ExceptionUtil.throwException(ErrorEnum.SMS_CODE_EXPIRED);
        }
        if (!code.equals(redisVal.toString())) {
            ExceptionUtil.throwException(ErrorEnum.SMS_CODE_INCORRECT);
        }

        String encodedPassword = "{bcrypt}" + BCRYPT_PASSWORD_ENCODER.encode(newPassword);
        boolean updated = userService.lambdaUpdate()
                .set(User::getPassword, encodedPassword)
                .eq(User::getUserId, userId)
                .update();
        if (!updated) {
            ExceptionUtil.throwException(ErrorEnum.BIND_EXCEPTION_ERROR);
        }

        redisTemplate.delete(smsRedisKey);
        redisTemplate.delete(commonUserRedisKeyBuilder.buildUserInfoKey(userId));
        redisTemplate.delete(userRedisKeyBuilder.buildUserPhoneKey(user.getPhone()));
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
            if (StringUtils.isNotBlank(userVO.getPassword())) {
                return userVO;
            }
            UserPhoneMapping mapping = userPhoneMappingService.lambdaQuery().eq(UserPhoneMapping::getPhone, phone).one();
            if (mapping != null) {
                User user = userService.lambdaQuery().eq(User::getUserId, mapping.getUserId()).one();
                if (user != null) {
                    UserVO dbUserVO = ConvertBeanUtil.convert(user, UserVO.class);
                    redisTemplate.opsForValue().set(key, dbUserVO, 30, TimeUnit.MINUTES);
                    return dbUserVO;
                }
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

    @Override
    public User getUserByPhoneFromDb(String phone) {
        if (StringUtils.isBlank(phone)) {
            return null;
        }
        UserPhoneMapping mapping = userPhoneMappingService.lambdaQuery().eq(UserPhoneMapping::getPhone, phone).one();
        if (mapping == null) {
            return null;
        }
        return userService.lambdaQuery().eq(User::getUserId, mapping.getUserId()).one();
    }
}
