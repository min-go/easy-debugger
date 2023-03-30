package io.dengliming.easydebugger.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ClientChannelHandler extends SimpleChannelInboundHandler {
    private AbstractSocketClient client;

    public ClientChannelHandler(AbstractSocketClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) {
        String connectKey = client.getConfig().getConnectKey();
        client.getClientEventListener().onClientEvent(new ClientReadMessageEvent(connectKey, o));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String connectKey = client.getConfig().getConnectKey();
        client.getClientEventListener().onClientEvent(new ClientExceptionEvent(connectKey, cause));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String connectKey = client.getConfig().getConnectKey();
        client.getClientEventListener().onClientEvent(new ClientInactiveEvent(connectKey));
    }
}
