package io.dengliming.easydebugger.utils;

import io.dengliming.easydebugger.model.ClientDebugger;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.model.ServerDebugger;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一缓存（包含netty客户端、服务端以及相关通信数据）
 */
@Slf4j
public enum SocketDebuggerCache {
    INSTANCE;

    /**
     * 用于缓存netty客户端
     */
    private final Map<String, ClientDebugger> CLIENT_CACHE = new ConcurrentHashMap<>();
    /**
     * 用于缓存netty服务端debugger数据
     */
    private final Map<String, ServerDebugger> SERVER_CACHE = new ConcurrentHashMap<>();

    public ServerDebugger getOrCreateServer(ConnectConfig config, IGenericEventListener eventListener) {
        ServerDebugger serverDebugger = SERVER_CACHE.get(config.getUid());
        if (serverDebugger != null) {
            return serverDebugger;
        }
        synchronized (SERVER_CACHE) {
            serverDebugger = SERVER_CACHE.get(config.getUid());
            if (serverDebugger != null) {
                return serverDebugger;
            }

            serverDebugger = new ServerDebugger(config, eventListener);
            SERVER_CACHE.put(config.getUid(), serverDebugger);
        }
        return serverDebugger;
    }

    public ClientDebugger getOrCreateClient(ConnectConfig config, IGenericEventListener clientEventListener) {
        ClientDebugger client = CLIENT_CACHE.get(config.getUid());
        if (client != null) {
            return client;
        }
        synchronized (CLIENT_CACHE) {
            client = CLIENT_CACHE.get(config.getUid());
            if (client != null) {
                return client;
            }

            client = new ClientDebugger(config, clientEventListener);
            CLIENT_CACHE.put(config.getUid(), client);
            client.init();
        }
        return client;
    }

    public ServerDebugger getServerDebugger(String serverId) {
        return SERVER_CACHE.get(serverId);
    }

    public ClientDebugger getClientDebugger(String clientId) {
        return CLIENT_CACHE.get(clientId);
    }

    public void removeClientCache(String clientId) {
        ClientDebugger clientDebugger = CLIENT_CACHE.remove(clientId);
        if (clientDebugger != null) {
            clientDebugger.destroy();
        }
    }
}
