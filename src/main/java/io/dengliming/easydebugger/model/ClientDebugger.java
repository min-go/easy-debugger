package io.dengliming.easydebugger.model;

import io.dengliming.easydebugger.netty.client.AbstractSocketClient;
import io.dengliming.easydebugger.netty.client.SocketDebuggerClient;
import io.dengliming.easydebugger.netty.client.TcpDebuggerClient;
import io.dengliming.easydebugger.netty.client.UdpDebuggerClient;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.channel.ChannelFuture;

import java.util.function.Consumer;

public class ClientDebugger {

    private final AbstractSocketClient client;
    private final ChatMsgBox chatMsgBox;

    public ClientDebugger(ConnectConfig config, IGenericEventListener clientEventListener) {
        switch (config.getConnectType()) {
            case TCP_CLIENT:
                this.client = new TcpDebuggerClient(config, clientEventListener);
                break;
            case UDP_CLIENT:
                this.client = new UdpDebuggerClient(config, clientEventListener);
                break;
            default:
                throw new RuntimeException("Unsupported client type " + config.getConnectType().name());
        }

        this.chatMsgBox = new ChatMsgBox();
    }

    public void init() {
        client.init();
    }

    public ChannelFuture connect(Consumer<?> consumer, long timeout) {
        return client.connect(consumer, timeout);
    }

    public ChannelFuture disconnect() {
        return client.disconnect();
    }

    public void online() {
        chatMsgBox.setOnline(true);
        ((SocketDebuggerClient) client).setStopScheduled(false);
    }

    public void offline() {
        chatMsgBox.setOnline(false);
        ((SocketDebuggerClient) client).setStopScheduled(true);
    }

    public void addLeftMsg(String msg) {
        chatMsgBox.addLeftMsg(msg);
    }

    public void disconnectClient() {
        client.disconnect();
    }

    public void destroy() {
        chatMsgBox.clear();
        client.destroy();
    }

    public AbstractSocketClient getClient() {
        return client;
    }

    public ChatMsgBox getChatMsgBox() {
        return chatMsgBox;
    }
}
