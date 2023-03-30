package io.dengliming.easydebugger.netty;

/**
 * 客户端异常事件
 */
public class ClientExceptionEvent extends ClientEvent {

    private final Throwable cause;
    public ClientExceptionEvent(Object source, Throwable cause) {
        super(source);
        this.cause = cause;
    }

    public Throwable getCause() {
        return cause;
    }
}
