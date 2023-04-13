package io.dengliming.easydebugger.model;

import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.dengliming.easydebugger.netty.server.AbstractSocketServer;
import io.dengliming.easydebugger.netty.server.TcpDebuggerServer;
import io.netty.channel.ChannelFuture;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerDebugger {

    private final AbstractSocketServer server;
    private final Map<String, ChatMsgBox> chatMsgBoxMap;
    private final ObservableList<ClientItem> clientList;
    private ClientItem selectedClient;
    private boolean listenStatus;

    public ServerDebugger(ConnectConfig config, IGenericEventListener eventListener) {
        switch (config.getConnectType()) {
            case TCP_SERVER:
                server = new TcpDebuggerServer(config, eventListener);
                break;
            default:
                throw new RuntimeException("Unsupported server type " + config.getConnectType().name());
        }
        chatMsgBoxMap = new ConcurrentHashMap<>();
        clientList = FXCollections.observableArrayList();
    }

    public ChannelFuture start() {
        return server.start();
    }

    public ChatMsgBox getOrCreateChatMsgBox(String clientId) {
        ChatMsgBox chatMsgBox = chatMsgBoxMap.get(clientId);
        if (chatMsgBox != null) {
            return chatMsgBox;
        }
        synchronized (chatMsgBoxMap) {
            chatMsgBox = chatMsgBoxMap.get(clientId);
            if (chatMsgBox != null) {
                return chatMsgBox;
            }
            chatMsgBox = new ChatMsgBox();
            chatMsgBoxMap.put(clientId, chatMsgBox);
        }
        return chatMsgBox;
    }

    public void addLeftMsg(String clientId, String msg) {
        if (chatMsgBoxMap.containsKey(clientId)) {
            chatMsgBoxMap.get(clientId).addLeftMsg(msg);
        }
    }

    public void destroy() {
        server.destroy();
    }

    public ObservableList<ClientItem> getClientList() {
        return clientList;
    }

    public ClientItem getSelectedClient() {
        return selectedClient;
    }

    public void setSelectedClient(ClientItem selectedClient) {
        this.selectedClient = selectedClient;
    }

    public boolean isListenStatus() {
        return listenStatus;
    }

    public void setListenStatus(boolean listenStatus) {
        this.listenStatus = listenStatus;
    }
}
