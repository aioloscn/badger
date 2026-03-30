package com.aiolos.badger.identitycore.provider.rpc;

import com.aiolos.badger.common.redis.UserRedisKeyBuilder;
import com.aiolos.badger.common.util.JwtUtil;
import com.aiolos.badger.identitycore.api.AccountTokenApi;
import com.aiolos.badger.identitycore.dto.AccountDTO;
import com.aiolos.badger.identitycore.dto.AccountTokenDTO;
import com.aiolos.badger.identitycore.provider.util.AnonymousIdGenerator;
import com.aiolos.common.util.ConvertBeanUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.concurrent.TimeUnit;

@DubboService
public class AccountTokenApiImpl implements AccountTokenApi {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private UserRedisKeyBuilder userRedisKeyBuilder;
    @Resource
    private AnonymousIdGenerator anonymousIdGenerator;
    @Resource
    private OAuth2AuthorizationService oAuth2AuthorizationService;

    @Override
    public AccountTokenDTO createToken(Long userId) {
        // 1. 生成 JWT Access Token（2 小时）
        String accessToken = JwtUtil.generateToken(String.valueOf(userId), null, JwtUtil.ACCESS_TOKEN_EXPIRE_MILLIS, "access");
        
        // 2. 生成 JWT Refresh Token（7 天）
        String refreshToken = JwtUtil.generateToken(String.valueOf(userId), null, JwtUtil.REFRESH_TOKEN_EXPIRE_MILLIS, "refresh");

        // 3. 将 Token 存入 Redis，作为 Refresh Token 的状态控制
        redisTemplate.opsForValue().set(userRedisKeyBuilder.buildUserTokenKey(refreshToken), userId, 7, TimeUnit.DAYS);
        
        return new AccountTokenDTO(accessToken, refreshToken);
    }

    @Override
    public AccountTokenDTO refreshToken(String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        // 1. 解析 Refresh Token 并校验
        String subject;
        try {
            var claims = JwtUtil.parseToken(refreshToken);
            if (!"refresh".equals(claims.get("type"))) {
                throw new IllegalArgumentException("Invalid token type");
            }
            subject = claims.getSubject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or expired refresh token", e);
        }

        // 2. 校验 Redis 中的状态（防止 Token 被主动作废）
        String tokenKey = userRedisKeyBuilder.buildUserTokenKey(refreshToken);
        Object userIdObj = redisTemplate.opsForValue().get(tokenKey);
        if (userIdObj == null) {
            throw new IllegalArgumentException("Refresh token has been revoked or expired");
        }

        // 3. 生成新的一对 Token
        Long userId = Long.valueOf(subject);
        AccountTokenDTO newTokens = createToken(userId);

        // 4. 删除旧的 Refresh Token，保证一次性使用（可选，增加安全性）
        redisTemplate.delete(tokenKey);

        return newTokens;
    }

    @Override
    public AccountDTO getUserByToken(String token) {
        AccountDTO dto = new AccountDTO();
        Long userId = null;
        
        try {
            // 优先通过 JWT 解析出 userId，无需每次都查 Redis，提升性能
            String subject = JwtUtil.getSubjectFromToken(token);
            userId = Long.parseLong(subject);
        } catch (Exception e) {
            // JWT 解析失败（过期、格式错误等），尝试回源 Redis 判断是否有效
            Object obj = redisTemplate.opsForValue().get(userRedisKeyBuilder.buildUserTokenKey(token));
            if (obj instanceof Integer) {
                userId = ((Integer) obj).longValue();
            } else if (obj instanceof Long) {
                userId = (Long) obj;
            }
            if (userId == null) {
                OAuth2Authorization authorization = oAuth2AuthorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
                if (authorization != null && StringUtils.isNotBlank(authorization.getPrincipalName())) {
                    try {
                        userId = Long.parseLong(authorization.getPrincipalName());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        if (userId == null) {
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
