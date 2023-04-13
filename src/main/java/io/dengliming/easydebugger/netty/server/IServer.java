package io.dengliming.easydebugger.netty.server;

import io.netty.channel.ChannelFuture;

public interface IServer {

    ChannelFuture start();

    void destroy();
}
