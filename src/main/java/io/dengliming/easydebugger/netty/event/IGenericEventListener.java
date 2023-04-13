package io.dengliming.easydebugger.netty.event;

import java.util.EventListener;

public interface IGenericEventListener<T> extends EventListener {

    void onEvent(T event);

}
