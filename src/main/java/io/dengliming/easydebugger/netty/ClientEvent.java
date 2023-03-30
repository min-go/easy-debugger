package io.dengliming.easydebugger.netty;

import javafx.event.Event;

import java.util.EventObject;

public abstract class ClientEvent extends EventObject {
    private long timestamp;

    public ClientEvent(Object source) {
        super(source);
        this.timestamp = System.currentTimeMillis();
    }
}
