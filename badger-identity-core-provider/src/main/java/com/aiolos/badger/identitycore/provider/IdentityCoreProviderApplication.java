package com.aiolos.badger.identitycore.provider;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDubbo
@ComponentScan("com.aiolos")
public class IdentityCoreProviderApplication {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(IdentityCoreProviderApplication.class);
        // 为了暴露 OAuth2 的 HTTP 授权端点，必须启用 Web 环境
        springApplication.setWebApplicationType(WebApplicationType.SERVLET);
        springApplication.run(args);
    }
}
