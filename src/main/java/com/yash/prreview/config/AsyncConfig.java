package com.yash.prreview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configures async execution using Java 21+ Virtual Threads.
 *
 * Virtual threads eliminate thread pool sizing concerns — the JVM creates
 * millions of them cheaply, each blocking independently without wasting a
 * platform thread while waiting for I/O (AI calls, GitHub API).
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        // Virtual thread executor — Java 21+ GA feature
        // Each @Async method and CompletableFuture.supplyAsync() call gets its own virtual thread
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
