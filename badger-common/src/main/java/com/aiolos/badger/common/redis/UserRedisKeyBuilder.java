package com.aiolos.badger.common.redis;

import org.springframework.stereotype.Component;

@Component
public class UserRedisKeyBuilder extends RedisKeyBuilder {

    private static final String USER_INFO_KEY = "userInfo";
    private static final String USER_TOKEN_KEY = "userToken";
    private static final String ANONYMOUS_ID_KEY = "anonymousId";
    private static final String ANONYMOUS_DEVICE_ID_KEY = "anonymousDeviceId";
    private static final String USER_TAG_LOCK_KEY = "userTagLock";
    private static final String USER_TAG_KEY = "userTag";
    private static final String USER_PHONE_KEY = "userPhone";

    public UserRedisKeyBuilder(RedisKeyProperties redisKeyProperties) {
        super(redisKeyProperties);
    }

    public String buildUserInfoKey(Long userId) {
        return USER_INFO_KEY + super.getSplit() + userId;
    }

    public String buildUserTokenKey(String token) {
        return USER_TOKEN_KEY + super.getSplit() + token;
    }

    public String buildAnonymousIdKey(String deviceId) {
        return ANONYMOUS_ID_KEY + super.getSplit() + deviceId;
    }

    public String buildAnonymousDeviceIdKey(Long anonymousId) {
        return ANONYMOUS_DEVICE_ID_KEY + super.getSplit() + anonymousId;
    }
    
    public String buildUserTagLockKey(Long userId) {
        return USER_TAG_LOCK_KEY + super.getSplit() + userId;
    }
    
    public String buildUserTagKey(Long userId) {
        return USER_TAG_KEY + super.getSplit() + userId;
    }
    
    public String buildUserPhoneKey(String phone) {
        return USER_PHONE_KEY + super.getSplit() + phone;
    }
}
