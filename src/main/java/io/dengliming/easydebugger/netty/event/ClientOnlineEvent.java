package io.dengliming.easydebugger.netty.event;

/**
 * 客户端连接成功事件
 */
public class ClientOnlineEvent extends ChannelEvent {

    private String serverId;
    public ClientOnlineEvent(Object source) {
        super(source);
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
