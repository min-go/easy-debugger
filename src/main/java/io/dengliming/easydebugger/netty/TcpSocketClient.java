package io.dengliming.easydebugger.netty;

import io.netty.channel.Channel;
import io.netty.channel.socket.nio.NioSocketChannel;

public abstract class TcpSocketClient extends AbstractSocketClient {

    public TcpSocketClient(ClientConnectProperties config, IClientEventListener clientEventHandler) {
        super(config, clientEventHandler);
    }

    @Override
    protected Class<? extends Channel> channel() {
        return NioSocketChannel.class;
    }
}

