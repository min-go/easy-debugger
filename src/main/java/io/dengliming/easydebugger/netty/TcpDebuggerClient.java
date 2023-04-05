package io.dengliming.easydebugger.netty;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.netty.channel.Channel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpDebuggerClient extends SocketDebuggerClient {

    public TcpDebuggerClient(ConnectConfig config, IClientEventListener clientEventListener) {
        super(config, clientEventListener);
    }

    @Override
    protected Class<? extends Channel> channel() {
        return NioSocketChannel.class;
    }
}
