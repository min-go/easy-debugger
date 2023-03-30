package io.dengliming.easydebugger.netty;

/**
 * 客户端掉线事件
 */
public class ClientInactiveEvent extends ClientEvent {

    public ClientInactiveEvent(Object source) {
        super(source);
    }
}
