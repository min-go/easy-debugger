package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.model.ClientSession;
import io.dengliming.easydebugger.model.SessionHolder;
import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.UdpMessage;
import io.dengliming.easydebugger.netty.event.ClientReadMessageEvent;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.dengliming.easydebugger.utils.T;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class UdpServerChannelHandler extends SimpleChannelInboundHandler<UdpMessage> {

    private final ConnectProperties config;
    private final IGenericEventListener eventListener;

    public UdpServerChannelHandler(ConnectProperties config, IGenericEventListener eventListener) {
        this.config = config;
        this.eventListener = eventListener;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, UdpMessage msg) {
        String clientId = T.generateClientId(msg.getSender());
        ClientReadMessageEvent event = new ClientReadMessageEvent(clientId, msg);
        ClientSession session = new ClientSession(clientId, ctx.channel(), msg.getSender());
        SessionHolder.INSTANCE.put(session.getId(), session);
        event.setServerId(config.getConnectKey());
        eventListener.onEvent(event);
        ctx.fireChannelRead(msg);
    }

}