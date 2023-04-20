package io.dengliming.easydebugger.utils;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.DebuggerFactory;
import io.dengliming.easydebugger.netty.client.ClientDebuggerView;
import io.dengliming.easydebugger.netty.client.SocketDebuggerClient;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.dengliming.easydebugger.netty.server.AbstractDebuggerServer;
import io.dengliming.easydebugger.netty.server.IServer;
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
    private final Map<String, SocketDebuggerClient> CLIENT_CACHE = new ConcurrentHashMap<>();
    /**
     * 用于缓存netty服务端debugger数据
     */
    private final Map<String, AbstractDebuggerServer> SERVER_CACHE = new ConcurrentHashMap<>();

    private final Map<String, ClientDebuggerView> CLIENT_DEBUGGER_VIEW_CACHE = new ConcurrentHashMap<>();

    public AbstractDebuggerServer getOrCreateServer(ConnectConfig config, IGenericEventListener eventListener) {
        return SERVER_CACHE.computeIfAbsent(config.getUid(), k -> DebuggerFactory.createServerDebugger(config, eventListener));
    }

    public SocketDebuggerClient getOrCreateClient(ConnectConfig config, IGenericEventListener clientEventListener) {
        return CLIENT_CACHE.computeIfAbsent(config.getUid(), k -> {
            SocketDebuggerClient client = DebuggerFactory.createClientDebugger(config, clientEventListener);
            client.init();
            return client;
        });
    }

    public ClientDebuggerView getOrCreateClientDebuggerView(String clientId) {
        return CLIENT_DEBUGGER_VIEW_CACHE.computeIfAbsent(clientId, k -> new ClientDebuggerView());
    }

    public ClientDebuggerView removeClientDebuggerView(String clientId) {
        return CLIENT_DEBUGGER_VIEW_CACHE.remove(clientId);
    }

    public IServer getServerDebugger(String serverId) {
        return SERVER_CACHE.get(serverId);
    }

    public SocketDebuggerClient getClientDebugger(String clientId) {
        return CLIENT_CACHE.get(clientId);
    }

    public void removeClientCache(String clientId) {
        SocketDebuggerClient clientDebugger = CLIENT_CACHE.remove(clientId);
        if (clientDebugger != null) {
            clientDebugger.destroy();
        }
    }

    public void removeServerCache(String serverId) {
        IServer server = SERVER_CACHE.remove(serverId);
        if (server != null) {
            server.destroy();
        }
    }
}
