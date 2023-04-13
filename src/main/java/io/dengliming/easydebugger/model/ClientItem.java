package io.dengliming.easydebugger.model;

public class ClientItem {

    private String serverId;
    private String clientName;
    private boolean connected;

    public ClientItem(String serverId, String clientName) {
        this.serverId = serverId;
        this.clientName = clientName;
    }

    public ClientItem(String serverId, String clientName, boolean connected) {
        this(serverId, clientName);
        this.connected = connected;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getServerId() {
        return serverId;
    }
}
