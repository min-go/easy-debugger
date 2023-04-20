package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.model.ClientSession;
import io.dengliming.easydebugger.model.SessionHolder;
import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.event.*;
import io.dengliming.easydebugger.utils.T;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class WebSocketServerChannelHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final ConnectProperties config;
    private final IGenericEventListener eventListener;

    public WebSocketServerChannelHandler(ConnectProperties config, IGenericEventListener eventListener) {
        this.config = config;
        this.eventListener = eventListener;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        String msg = null;
        if (frame instanceof BinaryWebSocketFrame) {
            ByteBuf content = frame.content();
            byte[] result = new byte[content.readableBytes()];
            content.readBytes(result);
            msg = new String(result, StandardCharsets.UTF_8);
        } else if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            msg = textFrame.text();
        }
        if (!T.hasLength(msg)) {
            return;
        }
        ClientReadMessageEvent event = new ClientReadMessageEvent(T.generateClientId((InetSocketAddress) ctx.channel().remoteAddress()), msg);

        System.out.println("=============<<<>>>>>>" + event.getSource());
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
            ctx.fireChannelInactive();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            eventListener.onEvent(new ExceptionEvent(T.generateClientId((InetSocketAddress) ctx.channel().remoteAddress()), cause));
        } finally {
            ctx.fireExceptionCaught(cause);
        }
    }
}