package io.dengliming.easydebugger.netty.event;

/**
 * 客户端接收消息事件
 */
public class ClientReadMessageEvent extends ChannelEvent {

    private final Object msg;
    private String serverId;
    public ClientReadMessageEvent(Object source, Object msg) {
        super(source);
        this.msg = msg;
    }

    public Object getMsg() {
        return msg;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
