package io.dengliming.easydebugger.netty;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

public enum ThreadManager {
    INSTANCE;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    ThreadManager() {
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(5,
                new ThreadFactory() {
                    private final AtomicLong threadCounter = new AtomicLong();
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "sched_" + threadCounter.incrementAndGet());
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduledThreadPoolExecutor.shutdown();
        }));
    }

    public ScheduledThreadPoolExecutor getScheduledThreadPoolExecutor() {
        return scheduledThreadPoolExecutor;
    }
}
