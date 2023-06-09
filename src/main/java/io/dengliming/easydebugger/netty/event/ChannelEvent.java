package io.dengliming.easydebugger.netty.event;

import java.util.EventObject;

/**
 * 抽象channel事件
 */
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
