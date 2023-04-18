package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.model.ClientSession;
import io.dengliming.easydebugger.model.SessionHolder;
import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.event.*;
import io.dengliming.easydebugger.utils.T;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.net.InetSocketAddress;

public class ServerChannelHandler extends SimpleChannelInboundHandler {

    private final ConnectProperties config;
    private final IGenericEventListener eventListener;

    public ServerChannelHandler(ConnectProperties config, IGenericEventListener eventListener) {
        this.config = config;
        this.eventListener = eventListener;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        ClientReadMessageEvent event = new ClientReadMessageEvent(T.generateClientId((InetSocketAddress) ctx.channel().remoteAddress()), msg);
        event.setServerId(config.getConnectKey());
        eventListener.onEvent(event);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        String clientId = T.generateClientId((InetSocketAddress) channel.remoteAddress());
        ClientSession session = new ClientSession(clientId, channel);
        SessionHolder.INSTANCE.put(session.getId(), session);
        ClientOnlineEvent clientOnlineEvent = new ClientOnlineEvent(clientId);
        clientOnlineEvent.setServerId(config.getConnectKey());
        eventListener.onEvent(clientOnlineEvent);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            String clientId = T.generateClientId((InetSocketAddress) ctx.channel().remoteAddress());
            SessionHolder.INSTANCE.remove(clientId);
            ClientInactiveEvent clientInactiveEvent = new ClientInactiveEvent(clientId);
            clientInactiveEvent.setServerId(config.getConnectKey());
            eventListener.onEvent(clientInactiveEvent);
        } finally {
            super.channelInactive(ctx);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            eventListener.onEvent(new ExceptionEvent(T.generateClientId((InetSocketAddress) ctx.channel().remoteAddress()), cause));
        } finally {
            super.exceptionCaught(ctx, cause);
        }
    }
}