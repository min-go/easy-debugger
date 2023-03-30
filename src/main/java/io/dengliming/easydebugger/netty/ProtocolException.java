package io.dengliming.easydebugger.netty;

public class ProtocolException extends RuntimeException {

    public ProtocolException() {
        super();
    }

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
