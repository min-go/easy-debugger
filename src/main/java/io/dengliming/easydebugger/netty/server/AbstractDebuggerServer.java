package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractDebuggerServer implements IServer {

    private final ConnectProperties config;
    private final IGenericEventListener eventListener;
    private final ServerDebuggerView serverDebuggerView;
    private AbstractBootstrap bootstrap;

    public AbstractDebuggerServer(ConnectProperties config, IGenericEventListener eventListener) {
        this.config = config;
        this.eventListener = eventListener;
        this.serverDebuggerView = new ServerDebuggerView();
    }

    @Override
    public ChannelFuture start() {
        this.bootstrap = initBootstrap();
        return doBind(bootstrap);
    }

    protected abstract AbstractBootstrap initBootstrap();

    protected ChannelFuture doBind(AbstractBootstrap bootstrap) {
        ChannelFuture bind;
        if (config.getHost() != null && !"".equals(config.getHost())) {
            bind = bootstrap.bind(config.getHost(), config.getPort());
        } else {
            bind = bootstrap.bind(config.getPort());
        }

        return bind.addListener(future -> {
            Integer port = config.getPort();
            String host = config.getHost() == null ? "0.0.0.0" : config.getHost();
            if (future.isSuccess()) {
                log.info("监听端口成功 主机：{}:{}", host, port);
            } else {
                log.error("监听端口失败 主机: {}:{} - 异常信息: {}", host, port, future.cause().getMessage(), future.cause());
            }
        });
    }

    protected abstract ChannelOutboundHandlerAdapter createProtocolEncoder();

    protected abstract ChannelInboundHandlerAdapter createProtocolDecoder();

    public ConnectProperties getConfig() {
        return config;
    }

    public IGenericEventListener getEventListener() {
        return eventListener;
    }

    public ServerDebuggerView getServerDebuggerView() {
        return serverDebuggerView;
    }

    public AbstractBootstrap getBootstrap() {
        return bootstrap;
    }
}
