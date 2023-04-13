package io.dengliming.easydebugger.netty.event;

import java.util.EventObject;

public abstract class ChannelEvent extends EventObject {
    private final long timestamp;

    public ChannelEvent(Object source) {
        super(source);
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
