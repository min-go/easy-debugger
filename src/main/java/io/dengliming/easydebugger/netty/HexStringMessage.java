package io.dengliming.easydebugger.netty;

public class HexStringMessage implements IMessage {

    private final String msg;
    public HexStringMessage(String msg) {
        this.msg = msg;
    }

    @Override
    public int length() {
        return getMessage().length;
    }

    @Override
    public byte[] getMessage() {
        return toHexBytes(msg);
    }

    @Override
    public MsgType msgType() {
        return MsgType.HEX;
    }

    private byte[] toHexBytes(String str) {
        if (str == null || str.trim().equals("")) {
            return new byte[0];
        }
        byte[] bytes = new byte[str.length() / 2];
        for (int i = 0; i < str.length() / 2; i++) {
            String subStr = str.substring(i * 2, i * 2 + 2);
            bytes[i] = (byte) Integer.parseInt(subStr, 16);
        }
        return bytes;
    }
}
