package io.dengliming.easydebugger.model;

import java.util.UUID;

public class ConnectConfig {

    private String host;
    private int port;
    private String name;
    private String uid;

    public ConnectConfig() {
        this.uid = UUID.randomUUID().toString().replaceAll("-", "");
    }

    public ConnectConfig(String host, int port) {
        this();
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUid() {
        return uid;
    }
}
