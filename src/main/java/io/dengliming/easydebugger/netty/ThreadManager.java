package io.dengliming.easydebugger.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;

public enum ThreadManager {
    INSTANCE;
    private final EventLoopGroup workerGroup;

    ThreadManager() {
        int workerThreadNum = Math.max(1, SystemPropertyUtil.getInt(
                "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

        workerGroup = new NioEventLoopGroup(workerThreadNum, new DefaultThreadFactory("CWT"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            workerGroup.shutdownGracefully();
        }));
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }
}
