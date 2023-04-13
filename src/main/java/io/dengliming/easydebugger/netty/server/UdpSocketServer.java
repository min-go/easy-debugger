package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class UdpSocketServer extends AbstractSocketServer {

    public UdpSocketServer(ConnectProperties config, IGenericEventListener eventListener) {
        super(config, eventListener);
    }

    @Override
    protected void doInitOptions(AbstractBootstrap bootstrap) {
        bootstrap.option(ChannelOption.SO_RCVBUF, 1024 * 1024)
                .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
                .option(EpollChannelOption.SO_REUSEPORT, true)
                .option(ChannelOption.SO_BROADCAST, true);
    }

    @Override
    protected Class serverSocketChannel() {
        return NioDatagramChannel.class;
    }
}
