package com.aiolos.badger.common.redis;

import com.aiolos.common.redis.RedisKeyProperties;
import com.aiolos.common.redis.builder.CommonUserRedisKeyBuilder;
import org.springframework.stereotype.Component;

@Component
public class UserRedisKeyBuilder extends CommonUserRedisKeyBuilder {

    private static final String USER_TAG_LOCK_KEY = "userTagLock";
    private static final String USER_TAG_KEY = "userTag";
    private static final String USER_PHONE_KEY = "userPhone";

    public UserRedisKeyBuilder(RedisKeyProperties redisKeyProperties) {
        super(redisKeyProperties);
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
