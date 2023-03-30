package io.dengliming.easydebugger.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioDatagramChannel;

public abstract class UdpSocketClient extends AbstractSocketClient {

    public UdpSocketClient(ClientConnectProperties config, IClientEventListener clientEventHandler) {
        super(config, clientEventHandler);
    }

    @Override
    protected void doInitOptions(Bootstrap bootstrap) {
        bootstrap.option(ChannelOption.SO_BROADCAST, true);
    }

    @Override
    protected Class<? extends Channel> channel() {
        return NioDatagramChannel.class;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, Object... args) {
        return super.writeAndFlush(msg);
    }
}

