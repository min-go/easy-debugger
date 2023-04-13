package io.dengliming.easydebugger.netty.client;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UdpDebuggerClient extends SocketDebuggerClient {

    public UdpDebuggerClient(ConnectConfig config, IGenericEventListener clientEventListener) {
        super(config, clientEventListener);
    }

    @Override
    protected void doInitOptions(Bootstrap bootstrap) {
        bootstrap.option(ChannelOption.SO_BROADCAST, true);
    }

    @Override
    protected Class<? extends Channel> channel() {
        return NioDatagramChannel.class;
    }
}
