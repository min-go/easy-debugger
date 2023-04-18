package io.dengliming.easydebugger.netty;

import java.net.InetSocketAddress;

public class UdpMessage extends SocketMessage {

    private InetSocketAddress sender;
    private InetSocketAddress recipient;

    public UdpMessage(byte[] message) {
        super(message);
    }

    public InetSocketAddress getSender() {
        return sender;
    }

    public UdpMessage setSender(InetSocketAddress sender) {
        this.sender = sender;
        return this;
    }

    public InetSocketAddress getRecipient() {
        return recipient;
    }

    public UdpMessage setRecipient(InetSocketAddress recipient) {
        this.recipient = recipient;
        return this;
    }
}
