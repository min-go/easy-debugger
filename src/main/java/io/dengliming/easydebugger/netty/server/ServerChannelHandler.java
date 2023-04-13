package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.model.ClientSession;
import io.dengliming.easydebugger.model.SessionHolder;
import io.dengliming.easydebugger.netty.event.ClientInactiveEvent;
import io.dengliming.easydebugger.netty.event.ClientOnlineEvent;
import io.dengliming.easydebugger.netty.event.ClientReadMessageEvent;
import io.dengliming.easydebugger.netty.event.ExceptionEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.net.InetSocketAddress;

public class ServerChannelHandler extends SimpleChannelInboundHandler {

    private final AbstractSocketServer server;

    public ServerChannelHandler(AbstractSocketServer server) {
        this.server = server;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        ClientReadMessageEvent event = new ClientReadMessageEvent(buildClientId(ctx.channel()), msg);
        event.setServerId(server.getConfig().getConnectKey());
        server.getEventListener().onEvent(event);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        ClientSession session = new ClientSession(buildClientId(channel), channel);
        SessionHolder.INSTANCE.put(session.getId(), session);
        ClientOnlineEvent clientOnlineEvent = new ClientOnlineEvent(buildClientId(ctx.channel()));
        clientOnlineEvent.setServerId(server.getConfig().getConnectKey());
        server.getEventListener().onEvent(clientOnlineEvent);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            String clientId = buildClientId(ctx.channel());
            SessionHolder.INSTANCE.remove(clientId);
            ClientInactiveEvent clientInactiveEvent = new ClientInactiveEvent(buildClientId(ctx.channel()));
            clientInactiveEvent.setServerId(server.getConfig().getConnectKey());
            server.getEventListener().onEvent(clientInactiveEvent);
        } finally {
            super.channelInactive(ctx);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            server.getEventListener().onEvent(new ExceptionEvent(buildClientId(ctx.channel()), cause));
        } finally {
            super.exceptionCaught(ctx, cause);
        }
    }

    private String buildClientId(Channel channel) {
        InetSocketAddress socketAddress = (InetSocketAddress) channel.remoteAddress();
        String clientIp = socketAddress.getAddress().getHostAddress();
        int clientPort = socketAddress.getPort();
        return String.format("%s:%d", clientIp, clientPort);
    }
}