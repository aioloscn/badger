package com.aiolos.badger.common.redis;

import com.aiolos.common.redis.RedisKeyProperties;
import com.aiolos.common.redis.builder.CommonSmsRedisKeyBuilder;
import org.springframework.stereotype.Component;

@Component
public class SmsRedisKeyBuilder extends CommonSmsRedisKeyBuilder {

    private static final String PREVENT_REPEAT_SENDING_KEY = "sms:prevent";
    
    public SmsRedisKeyBuilder(RedisKeyProperties redisKeyProperties) {
        super(redisKeyProperties);
    }
    
    public String buildPreventRepeatSendingKey(String phone) {
        return super.getPrefix() + PREVENT_REPEAT_SENDING_KEY + super.getSplit() + phone;
    }
}
