package io.dengliming.easydebugger.netty.event;

/**
 * 客户端掉线事件
 */
public class ClientInactiveEvent extends ChannelEvent {

    private String serverId;

    public ClientInactiveEvent(Object source) {
        super(source);
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
