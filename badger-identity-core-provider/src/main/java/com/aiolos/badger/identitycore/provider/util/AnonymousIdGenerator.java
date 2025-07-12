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
    
    public long generateAnonymousId() {
        long timestamp = System.currentTimeMillis();

        // 取时间戳的低32位（约49天循环）
        int truncatedTimestamp = (int) timestamp;

        // 服务ID取低10位（0-1023）
        int servicePart = serviceId & 0x3FF;

        // 生成14位随机数（0-16383）
        int randomPart = RANDOM.nextInt(0x4000);

        // 组合ID 共52位，前端能接收的范围 ±(2^53 - 1)
        return ((long) truncatedTimestamp << 22)  // 时间戳32位左移22位
                | ((long) servicePart << 14)       // 服务ID10位左移14位
                | randomPart;
    }
}