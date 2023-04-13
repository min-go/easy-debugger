package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.dengliming.easydebugger.netty.codec.MsgEncoder;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.handler.codec.string.StringDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpDebuggerServer extends TcpSocketServer {

    public TcpDebuggerServer(ConnectConfig config, IGenericEventListener eventListener) {
        super(new ConnectProperties(config.getHost(), config.getPort(), config.getUid()), eventListener);
    }

    @Override
    protected ChannelOutboundHandlerAdapter createProtocolEncoder() {
        return new MsgEncoder();
    }

    @Override
    protected ChannelInboundHandlerAdapter createProtocolDecoder() {
        return new StringDecoder();
    }

}
