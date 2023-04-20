package io.dengliming.easydebugger.netty;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

public class WebSocketMessage extends SocketMessage {

    public WebSocketMessage(byte[] message) {
        super(message);
    }

    public BinaryWebSocketFrame buildBinaryWebSocketFrame() {
        return new BinaryWebSocketFrame(Unpooled.copiedBuffer(getMessage()));
    }
}
