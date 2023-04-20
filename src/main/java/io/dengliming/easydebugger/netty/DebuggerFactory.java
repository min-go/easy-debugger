package io.dengliming.easydebugger.netty;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.client.SocketDebuggerClient;
import io.dengliming.easydebugger.netty.client.TcpDebuggerClient;
import io.dengliming.easydebugger.netty.client.UdpDebuggerClient;
import io.dengliming.easydebugger.netty.client.WebSocketDebuggerClient;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.dengliming.easydebugger.netty.server.*;

public final class DebuggerFactory {

    public static SocketDebuggerClient createClientDebugger(ConnectConfig config, IGenericEventListener clientEventListener) {
        SocketDebuggerClient client;
        switch (config.getConnectType()) {
            case TCP_CLIENT:
                client = new TcpDebuggerClient(config, clientEventListener);
                break;
            case UDP_CLIENT:
                client = new UdpDebuggerClient(config, clientEventListener);
                break;
            case WEBSOCKET_CLIENT:
                client = new WebSocketDebuggerClient(config, clientEventListener);
                break;
            default:
                throw new RuntimeException("Unsupported connect type " + config.getConnectType().name());
        }
        return client;
    }

    public static AbstractDebuggerServer createServerDebugger(ConnectConfig config, IGenericEventListener clientEventListener) {
        AbstractDebuggerServer server = null;
        switch (config.getConnectType()) {
            case TCP_SERVER:
                server = new TcpDebuggerServer(config, clientEventListener);
                break;
            case UDP_SERVER:
                server = new UdpDebuggerServer(config, clientEventListener);
                break;
            case WEBSOCKET_SERVER:
                server = new WebSocketDebuggerServer(config, clientEventListener);
                break;
            default:
                throw new RuntimeException("Unsupported connect type " + config.getConnectType().name());
        }
        return server;
    }
}
