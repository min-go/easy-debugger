package io.dengliming.easydebugger.netty;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.client.SocketDebuggerClient;
import io.dengliming.easydebugger.netty.client.TcpDebuggerClient;
import io.dengliming.easydebugger.netty.client.UdpDebuggerClient;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.dengliming.easydebugger.netty.server.IServer;
import io.dengliming.easydebugger.netty.server.TcpDebuggerServer;
import io.dengliming.easydebugger.netty.server.UdpDebuggerServer;

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
            default:
                throw new RuntimeException("Unsupported connect type " + config.getConnectType().name());
        }
        return client;
    }

    public static IServer createServerDebugger(ConnectConfig config, IGenericEventListener clientEventListener) {
        IServer server = null;
        switch (config.getConnectType()) {
            case TCP_SERVER:
                server = new TcpDebuggerServer(config, clientEventListener);
                break;
            case UDP_SERVER:
                server = new UdpDebuggerServer(config, clientEventListener);
                break;
            default:
                throw new RuntimeException("Unsupported connect type " + config.getConnectType().name());
        }
        return server;
    }
}
