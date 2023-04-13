package io.dengliming.easydebugger.netty.event;

/**
 * 异常事件
 */
public class ExceptionEvent extends ChannelEvent {

    private final Throwable cause;
    public ExceptionEvent(Object source, Throwable cause) {
        super(source);
        this.cause = cause;
    }

    public Throwable getCause() {
        return cause;
    }
}
