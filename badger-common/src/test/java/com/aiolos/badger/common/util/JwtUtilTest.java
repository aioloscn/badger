package com.aiolos.badger.common.util;

import java.util.HashMap;
import java.util.Map;

public class JwtUtilTest {

    public static void main(String[] args) {
        String userId = "1234567890";
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "admin");

        // 1. 生成 Token
        String token = JwtUtil.generateToken(userId, claims, 1000 * 60 * 60L, "access");
        System.out.println("Generated JWT Token: " + token);

        // 2. 解析 Token
        String parsedUserId = JwtUtil.getSubjectFromToken(token);
        if (!userId.equals(parsedUserId)) {
            throw new RuntimeException("Parse failed");
        }
        
        var parsedClaims = JwtUtil.parseToken(token);
        if (!"admin".equals(parsedClaims.get("role"))) {
            throw new RuntimeException("Claims failed");
        }
        System.out.println("Parsed Claims: " + parsedClaims);
        System.out.println("Test Passed!");
    }
}
