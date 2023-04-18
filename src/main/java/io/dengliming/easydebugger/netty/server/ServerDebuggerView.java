package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.model.ChatMsgBox;
import io.dengliming.easydebugger.model.ClientItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerDebuggerView {
    private final Map<String, ChatMsgBox> chatMsgBoxMap;
    private final ObservableList<ClientItem> clientList;
    private ClientItem selectedClient;
    private boolean listenStatus;

    public ServerDebuggerView() {
        chatMsgBoxMap = new ConcurrentHashMap<>();
        clientList = FXCollections.observableArrayList();
    }

    public ChatMsgBox getOrCreateChatMsgBox(String clientId) {
        return chatMsgBoxMap.computeIfAbsent(clientId, k -> new ChatMsgBox());
    }

    public void addLeftMsg(String clientId, String msg) {
        if (chatMsgBoxMap.containsKey(clientId)) {
            chatMsgBoxMap.get(clientId).addLeftMsg(msg);
        }
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
