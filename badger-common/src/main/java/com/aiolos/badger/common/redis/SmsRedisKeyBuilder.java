package com.aiolos.badger.common.redis;

import org.springframework.stereotype.Component;

@Component
public class SmsRedisKeyBuilder extends RedisKeyBuilder {

    private static final String SMS_LOGIN_CODE_KEY = "sms:loginCode";
    private static final String PREVENT_REPEAT_SENDING_KEY = "sms:prevent";
    
    public SmsRedisKeyBuilder(RedisKeyProperties redisKeyProperties) {
        super(redisKeyProperties);
    }

    public String buildSmsLoginCodeKey(String phone) {
        return SMS_LOGIN_CODE_KEY + super.getSplit() + phone;
    }
    
    public String buildPreventRepeatSendingKey(String phone) {
        return super.getPrefix() + PREVENT_REPEAT_SENDING_KEY + super.getSplit() + phone;
    }
}
