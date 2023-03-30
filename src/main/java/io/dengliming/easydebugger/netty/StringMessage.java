package io.dengliming.easydebugger.netty;

import java.nio.charset.StandardCharsets;

public class StringMessage implements IMessage {

    private final String msg;
    public StringMessage(String msg) {
        this.msg = msg;
    }

    @Override
    public int length() {
        return getMessage().length;
    }

    @Override
    public byte[] getMessage() {
        return msg.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public MsgType msgType() {
        return MsgType.STRING;
    }
}
