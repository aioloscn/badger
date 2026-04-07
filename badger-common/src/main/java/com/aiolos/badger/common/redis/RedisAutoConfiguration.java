package com.aiolos.badger.common.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(RedisKeyProperties.class)
public class RedisAutoConfiguration {
    
    @Bean
    public RedisKeyBuilder redisKeyBuilder(RedisKeyProperties redisKeyProperties) {
        return new RedisKeyBuilder(redisKeyProperties);
    }
}
