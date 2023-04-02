package io.dengliming.easydebugger.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

public enum ThreadManager {
    INSTANCE;
    private final EventLoopGroup workerGroup;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    ThreadManager() {
        int workerThreadNum = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

        workerGroup = new NioEventLoopGroup(workerThreadNum, new DefaultThreadFactory("CWT"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            workerGroup.shutdownGracefully();
        }));

        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(5,
                new ThreadFactory() {
                    private final AtomicLong threadCounter = new AtomicLong();
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "sched_" + threadCounter.incrementAndGet());
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    public ScheduledThreadPoolExecutor getScheduledThreadPoolExecutor() {
        return scheduledThreadPoolExecutor;
    }
}
