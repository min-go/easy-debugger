package io.dengliming.easydebugger.netty;

/**
 * 客户端接收消息事件
 */
public class ClientReadMessageEvent extends ClientEvent {

    private final Object msg;
    public ClientReadMessageEvent(Object source, Object msg) {
        super(source);
        this.msg = msg;
    }

    public Object getMsg() {
        return msg;
    }
}
