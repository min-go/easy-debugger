package io.dengliming.easydebugger.netty;

/**
 * 协议不可写异常
 */
public class UnWritableProtocolException extends ProtocolException {

    public UnWritableProtocolException() {
        super();
    }

    public UnWritableProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
