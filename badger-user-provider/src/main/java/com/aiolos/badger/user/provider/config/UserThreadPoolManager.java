package com.aiolos.badger.user.provider.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UserThreadPoolManager {

    public static ThreadPoolExecutor commonAsyncPool = new ThreadPoolExecutor(2, 8, 3, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000),
            r -> {
                Thread thread = new Thread(r);
                thread.setName("msg-common-pool-thread-" + thread.getId());
                return thread;
            });
}
