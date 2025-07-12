package com.aiolos.badger.user.provider.mq.consumer;

import com.aiolos.badger.common.redis.UserRedisKeyBuilder;
import com.aiolos.badger.enums.UserCacheEnum;
import com.aiolos.badger.mq.message.UserCacheMessage;
import com.aiolos.common.redis.builder.CommonUserRedisKeyBuilder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component
public class UpdateUserInfoConsumer {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private CommonUserRedisKeyBuilder commonUserRedisKeyBuilder;
    @Resource
    private UserRedisKeyBuilder userRedisKeyBuilder;

    @Bean
    public Consumer<UserCacheMessage> cacheAsyncDelete() {
        return message -> {
            log.info("接收到删除redis缓存消息: {}", message);
            if (message != null && message.getUserId() != null) {
                
                if (message.getUserCacheEnum() == UserCacheEnum.USER_INFO_CACHE) {
                    redisTemplate.delete(commonUserRedisKeyBuilder.buildUserInfoKey(message.getUserId()));
                    log.info("已删除用户{}的缓存", message.getUserId());
                } else if (message.getUserCacheEnum() == UserCacheEnum.USER_TAG_CACHE) {
                    redisTemplate.delete(userRedisKeyBuilder.buildUserTagKey(message.getUserId()));
                    log.info("已删除用户{}的标签缓存", message.getUserId());
                }
            }
        };
    }
}
