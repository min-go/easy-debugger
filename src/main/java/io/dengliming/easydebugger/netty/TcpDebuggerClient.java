package io.dengliming.easydebugger.netty;

import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.handler.codec.string.StringDecoder;

public class TcpDebuggerClient extends TcpSocketClient {

    public TcpDebuggerClient(ClientConnectProperties config, IClientEventListener eventHandler) {
        super(config, eventHandler);
    }

    @Override
    protected ChannelInboundHandler createProtocolDecoder() {
        return new StringDecoder();
    }

    @Override
    protected ChannelOutboundHandlerAdapter createProtocolEncoder() {
        return new MsgEncoder();
    }
}
