package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TcpSocketServer extends AbstractSocketServer {

    public TcpSocketServer(ConnectProperties config, IGenericEventListener eventListener) {
        super(config, eventListener);
    }

    @Override
    protected void doInitOptions(AbstractBootstrap bootstrap) {
        ((ServerBootstrap) bootstrap).option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
    }

    @Override
    protected Class serverSocketChannel() {
        return NioServerSocketChannel.class;
    }
}
