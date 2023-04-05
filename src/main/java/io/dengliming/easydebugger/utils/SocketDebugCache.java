package io.dengliming.easydebugger.utils;

import io.dengliming.easydebugger.model.ChatMsgBox;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public enum SocketDebugCache {
    INSTANCE;

    private final Map<String, AbstractSocketClient> CLIENT_CACHE = new ConcurrentHashMap<>();
    private final Map<String, ChatMsgBox> MSG_BOX_CACHE = new ConcurrentHashMap<>();

    public AbstractSocketClient getOrCreateClient(ConnectConfig config, IClientEventListener clientEventListener) {
        AbstractSocketClient client = CLIENT_CACHE.get(config.getUid());
        if (client != null) {
            return client;
        }
        synchronized (CLIENT_CACHE) {
            client = CLIENT_CACHE.get(config.getUid());
            if (client != null) {
                return client;
            }

            switch (config.getConnectType()) {
                case TCP_CLIENT:
                    client = new TcpDebuggerClient(config, clientEventListener);
                    break;
                case UDP_CLIENT:
                    client = new UdpDebuggerClient(config, clientEventListener);
                    break;
                default:
                    throw new RuntimeException("Unsupported client type " + config.getConnectType().name());
            }
            CLIENT_CACHE.put(config.getUid(), client);
            client.init();
        }
        return client;
    }

    public ChatMsgBox getOrCreateChatMsgBox(String clientId) {
        ChatMsgBox chatMsgBox = MSG_BOX_CACHE.get(clientId);
        if (chatMsgBox != null) {
            return chatMsgBox;
        }
        synchronized (MSG_BOX_CACHE) {
            chatMsgBox = MSG_BOX_CACHE.get(clientId);
            if (chatMsgBox != null) {
                return chatMsgBox;
            }
            chatMsgBox = new ChatMsgBox();
            MSG_BOX_CACHE.put(clientId, chatMsgBox);
        }
        return chatMsgBox;
    }

    public void disconnectClient(String clientId) {
        AbstractSocketClient client = CLIENT_CACHE.get(clientId);
        if (client != null) {
            client.disconnect();
        }
    }

    public void removeCache(String clientId) {
        ChatMsgBox box = MSG_BOX_CACHE.remove(clientId);
        if (box != null) {
            box.clear();
        }
        AbstractSocketClient client = CLIENT_CACHE.remove(clientId);
        if (client != null) {
            client.destroy();
        }
    }

    public void setChatMsgBoxOnline(String clientId) {
        if (MSG_BOX_CACHE.containsKey(clientId)) {
            MSG_BOX_CACHE.get(clientId).onLine();
        }
        if (CLIENT_CACHE.containsKey(clientId)) {
            ((SocketDebuggerClient) CLIENT_CACHE.get(clientId)).setStopScheduled(false);
        }
    }

    public void setChatMsgBoxOffline(String clientId) {
        if (MSG_BOX_CACHE.containsKey(clientId)) {
            MSG_BOX_CACHE.get(clientId).onOffline();
        }
        if (CLIENT_CACHE.containsKey(clientId)) {
            ((SocketDebuggerClient) CLIENT_CACHE.get(clientId)).setStopScheduled(true);
        }
    }

    public void addLeftMsg(String clientId, String msg) {
        if (MSG_BOX_CACHE.containsKey(clientId)) {
            MSG_BOX_CACHE.get(clientId).addLeftMsg(msg);
        }
    }

}
