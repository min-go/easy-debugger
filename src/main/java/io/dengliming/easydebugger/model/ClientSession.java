package io.dengliming.easydebugger.model;

import io.netty.channel.Channel;

public class ClientSession {

    private String id;
    private Channel channel;

    public ClientSession(String id, Channel channel) {
        this.id = id;
        this.channel = channel;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}
