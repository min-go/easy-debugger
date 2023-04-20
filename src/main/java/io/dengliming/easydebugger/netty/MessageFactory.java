package io.dengliming.easydebugger.netty;

import io.dengliming.easydebugger.constant.MsgType;
import io.dengliming.easydebugger.utils.T;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class MessageFactory {

    public static SocketMessage createSocketMessage(MsgType type, String msg) {
        return new SocketMessage(buildMessage(type, msg));
    }

    public static UdpMessage createUdpMessage(MsgType type, String msg) {
        return new UdpMessage(buildMessage(type, msg));
    }

    public static WebSocketMessage createWebSocketMessage(MsgType type, String msg) {
        return new WebSocketMessage(buildMessage(type, msg));
    }

    private static byte[] buildMessage(MsgType type, String msg) {
        byte[] message;
        if (Objects.requireNonNull(type) == MsgType.HEX) {
            message = T.toHexBytes(msg);
        } else {
            message = msg.getBytes(StandardCharsets.UTF_8);
        }
        return message;
    }
}
