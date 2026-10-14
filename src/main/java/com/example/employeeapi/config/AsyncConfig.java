package com.example.employeeapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String EMPLOYEE_EXECUTOR = "employeeTaskExecutor";

    @Bean(name = EMPLOYEE_EXECUTOR)
    ThreadPoolTaskExecutor employeeTaskExecutor(@Value("${app.async.core-pool-size}") int corePoolSize,
                                                @Value("${app.async.max-pool-size}") int maxPoolSize,
                                                @Value("${app.async.queue-capacity}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("employee-worker-");
        // Never run the job on the HTTP thread; a full queue is reported as a failed transaction instead.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
