package io.dengliming.easydebugger.netty;


import java.util.function.Consumer;

public interface IClient<T> {

    int getPort();

    String getHost();

    void init(T arg);

    /**
     * 连接远程服务器
     *
     * @param consumer 连接回调
     * @param timeout  (毫秒) 连接超时时间 0表示不同步
     */
    Object connect(Consumer<?> consumer, long timeout);

    /**
     * 断开连接
     */
    Object disconnect();

    void destroy();
}
