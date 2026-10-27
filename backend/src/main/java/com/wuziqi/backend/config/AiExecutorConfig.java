package com.wuziqi.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AiExecutorConfig {

    @Bean(name = "aiExecutor", destroyMethod = "shutdown")
    public ExecutorService aiExecutor() {
        int cpu = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, cpu / 2);
        int maxPoolSize = Math.max(corePoolSize, 4);
        AtomicInteger threadIndex = new AtomicInteger(1);

        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                30,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(256),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("gomoku-ai-" + threadIndex.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
