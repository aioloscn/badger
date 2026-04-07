package com.aiolos.badger.common.redis;

import java.util.concurrent.ThreadLocalRandom;

public class RedisKeyBuilder {

    private final String applicationName;
    private static final String SPLIT = ":";

    public RedisKeyBuilder(RedisKeyProperties redisKeyProperties) {
        this.applicationName = redisKeyProperties.getName();
    }

    public String getSplit() {
        return SPLIT;
    }

    public String getPrefix() {
        return applicationName + SPLIT;
    }

    public int randomExpireSeconds() {
        return ThreadLocalRandom.current().nextInt(60, 3600);
    }

    public long randomExpireSeconds(long bound) {
        return ThreadLocalRandom.current().nextLong(bound);
    }
}
