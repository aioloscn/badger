package com.aiolos.badger.identitycore.provider.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Random;

@Component
public class AnonymousIdGenerator {
    
    @Value("${config.service.id}")
    private Integer serviceId;

    private static final Random RANDOM = new SecureRandom();
    
    /**
     * 生成匿名用户ID：
     * 1) 低32位时间戳 + 10位服务ID + 14位随机数组合，避免同毫秒冲突；
     * 2) 统一返回负数，和已登录用户正数ID做语义隔离。
     */
    public long generateAnonymousId() {
        long timestamp = System.currentTimeMillis();
        // 仅保留服务ID低10位，范围 0~1023
        int servicePart = serviceId & 0x3FF;
        // 14位随机数，范围 0~16383
        int randomPart = RANDOM.nextInt(0x4000);
        // 低32位时间戳左移22位，为服务ID和随机数预留空间
        long anonymousId = ((timestamp & 0xFFFFFFFFL) << 22)
                | ((long) servicePart << 14)
                | randomPart;

        // 边界保护：理论上当三段都为0时会得到0（极低概率），避免返回0这个无语义ID
        if (anonymousId == 0L) {
            return -1L;
        }
        // 匿名ID统一使用负数
        return -anonymousId;
    }
}
