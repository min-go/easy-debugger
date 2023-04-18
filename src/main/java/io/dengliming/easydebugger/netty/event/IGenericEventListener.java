package io.dengliming.easydebugger.netty.event;

import java.util.EventListener;

/**
 * 统一通用事件监听
 *
 * @param <T>
 */
public interface IGenericEventListener<T> extends EventListener {

    void onEvent(T event);

}
