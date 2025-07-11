package com.aiolos.badgeridgenerator.provider;

import com.aiolos.badgeridgenerator.api.IdGeneratorApi;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

import java.util.concurrent.CountDownLatch;

@SpringBootApplication
@EnableDubbo
@EnableDiscoveryClient
@ComponentScan("com.aiolos")
@MapperScan("com.aiolos.badgerservice.mapper")
public class IdGeneratorApplication implements CommandLineRunner {

    @Resource
    private IdGeneratorApi idGeneratorApi;

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(IdGeneratorApplication.class);
        springApplication.setWebApplicationType(WebApplicationType.NONE);
        springApplication.run(args);
    }

    @Override
    public void run(String... args) throws Exception {

        /*CountDownLatch latch = new CountDownLatch(1);
        for (int i = 0; i < 6; i++) {
            new Thread(() -> {
                try {
                    latch.await();
                    Long seqId = idGeneratorApi.getNonSeqId(1);
                    System.out.println(Thread.currentThread().getName() + ": " + seqId);
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, "thread" + i).start();
        }
        latch.countDown();
        Thread.sleep(100000);*/
    }
}
