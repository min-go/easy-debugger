package io.dengliming.easydebugger.netty.client;

import io.dengliming.easydebugger.netty.event.ClientInactiveEvent;
import io.dengliming.easydebugger.netty.event.ClientOnlineEvent;
import io.dengliming.easydebugger.netty.event.ClientReadMessageEvent;
import io.dengliming.easydebugger.netty.event.ExceptionEvent;
import io.dengliming.easydebugger.utils.T;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
@ChannelHandler.Sharable
public class WebSocketClientHandler extends SimpleChannelInboundHandler {

    private final WebSocketClientHandshaker handShaker;
    private final AbstractSocketClient client;
    private ChannelPromise handshakeFuture;

    public WebSocketClientHandler(final WebSocketClientHandshaker handShaker, AbstractSocketClient client) {
        this.handShaker = handShaker;
        this.client = client;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        handShaker.handshake(ctx.channel());
        String connectKey = client.getConfig().getConnectKey();
        client.getClientEventListener().onEvent(new ClientOnlineEvent(connectKey));
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        String connectKey = client.getConfig().getConnectKey();
        client.getClientEventListener().onEvent(new ClientInactiveEvent(connectKey));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        final Channel ch = ctx.channel();
        if (!handShaker.isHandshakeComplete()) {
            // web socket client connected
            handShaker.finishHandshake(ch, (FullHttpResponse) msg);
            handshakeFuture.setSuccess();
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.getStatus() +
                    ", content=" + response.content().toString(StandardCharsets.UTF_8) + ')');
        }

        String message = null;
        if (msg instanceof TextWebSocketFrame) {
            final TextWebSocketFrame textFrame = (TextWebSocketFrame) msg;
            message = textFrame.text();
        } else if (msg instanceof PongWebSocketFrame) {
        } else if (msg instanceof CloseWebSocketFrame) {
            ch.close();
        } else if (msg instanceof BinaryWebSocketFrame) {
            ByteBuf content = ((BinaryWebSocketFrame) msg).content();
            byte[] result = new byte[content.readableBytes()];
            content.readBytes(result);
            message = new String(result, StandardCharsets.UTF_8);
        }

        if (!T.hasLength(message)) {
            return;
        }

        String connectKey = client.getConfig().getConnectKey();
        client.getClientEventListener().onEvent(new ClientReadMessageEvent(connectKey, message));
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        log.error("", cause);

        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }

        String connectKey = client.getConfig().getConnectKey();
        client.getClientEventListener().onEvent(new ExceptionEvent(connectKey, cause));
        ctx.close();
    }
}