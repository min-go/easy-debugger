package io.dengliming.easydebugger.netty;

public class MessageFactory {

    public static IMessage createMessage(MsgType type, String msg) {
        IMessage message = null;
        switch (type) {
            case HEX:
                message = new HexStringMessage(msg);
                break;
            default:
                message = new StringMessage(msg);
        }
        return message;
    }
}
