package io.dengliming.easydebugger.model;

import io.netty.channel.Channel;

import java.net.InetSocketAddress;

/**
 * 客户端会话
 */
public class ClientSession {

    private String id;
    private Channel channel;
    private InetSocketAddress sender;

    public ClientSession(String id, Channel channel) {
        this.id = id;
        this.channel = channel;
    }

    public ClientSession(String id, Channel channel, InetSocketAddress sender) {
        this.id = id;
        this.channel = channel;
        this.sender = sender;
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

    public InetSocketAddress getSender() {
        return sender;
    }

    public void setSender(InetSocketAddress sender) {
        this.sender = sender;
    }
}
