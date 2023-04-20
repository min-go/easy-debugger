package io.dengliming.easydebugger.netty.client;

import io.dengliming.easydebugger.netty.event.ExceptionEvent;
import io.dengliming.easydebugger.netty.event.ClientInactiveEvent;
import io.dengliming.easydebugger.netty.event.ClientReadMessageEvent;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@ChannelHandler.Sharable
public class ClientChannelHandler extends SimpleChannelInboundHandler {
    private AbstractSocketClient client;

    public ClientChannelHandler(AbstractSocketClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) {
        String connectKey = client.getConfig().getConnectKey();
        client.getClientEventListener().onEvent(new ClientReadMessageEvent(connectKey, o));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String connectKey = client.getConfig().getConnectKey();
        client.getClientEventListener().onEvent(new ExceptionEvent(connectKey, cause));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String connectKey = client.getConfig().getConnectKey();
        client.getClientEventListener().onEvent(new ClientInactiveEvent(connectKey));
    }
}
