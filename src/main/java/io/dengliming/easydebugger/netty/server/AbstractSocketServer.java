package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

import static io.dengliming.easydebugger.constant.CommonConstant.*;

@Slf4j
public abstract class AbstractSocketServer implements IServer {

    private final ConnectProperties config;
    private final IGenericEventListener eventListener;
    private AbstractBootstrap bootstrap;
    private static Channel serverChannel;
    /**
     * boss 线程组，用于服务端接受客户端的连接
     */
    private EventLoopGroup bossGroup;
    /**
     * worker 线程组，用于服务端接受客户端的数据读写
     */
    private EventLoopGroup workerGroup;

    public AbstractSocketServer(ConnectProperties config, IGenericEventListener eventListener) {
        this.config = config;
        this.eventListener = eventListener;
    }

    @Override
    public ChannelFuture start() {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .handler(new LoggingHandler(LogLevel.INFO))
                .channel(serverSocketChannel())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();

                        // 设置编码器
                        p.addFirst(SERVER_ENCODER_HANDLER, createProtocolEncoder());

                        // 设置解码器
                        p.addFirst(SERVER_DECODER_HANDLER, createProtocolDecoder());

                        // 业务处理器新增到最后
                        p.addLast(SERVER_SERVICE_HANDLER, new ServerChannelHandler(AbstractSocketServer.this));

                        // 自定义处理
                        doInitChannel(p);
                    }
                });
        doInitOptions(bootstrap);
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

    protected void doInitOptions(AbstractBootstrap bootstrap) {
    }

    protected void doInitChannel(ChannelPipeline p) {

    }

    protected abstract Class serverSocketChannel();

    protected abstract ChannelOutboundHandlerAdapter createProtocolEncoder();

    protected abstract ChannelInboundHandlerAdapter createProtocolDecoder();

    public ConnectProperties getConfig() {
        return config;
    }

    public IGenericEventListener getEventListener() {
        return eventListener;
    }

    @Override
    public void destroy() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
