package io.dengliming.easydebugger.netty;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class ConnectProperties {
    /**
     * 主机
     */
    private String host;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 客户端唯一标识key
     */
    private String connectKey;

    private long connectTimeoutMillisecond = 3000;

    public ConnectProperties(String host, Integer port, String connectKey) {
        this.host = host;
        this.port = port;
        this.connectKey = connectKey;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getConnectKey() {
        return connectKey;
    }

    public long getConnectTimeoutMillisecond() {
        return connectTimeoutMillisecond;
    }

    public void setConnectTimeoutMillisecond(long connectTimeoutMillisecond) {
        this.connectTimeoutMillisecond = connectTimeoutMillisecond;
    }

    public SocketAddress remoteSocketAddress() {
        return new InetSocketAddress(this.getHost(), this.getPort());
    }
}
