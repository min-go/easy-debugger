package io.dengliming.easydebugger.netty;

public class SocketMessage implements IMessage {

    /**
     * 二进制报文
     */
    private byte[] message;

    public SocketMessage(byte[] message) {
        this.message = message;
    }

    @Override
    public int length() {
        return message.length;
    }

    @Override
    public byte[] getMessage() {
        return this.message;
    }

}
