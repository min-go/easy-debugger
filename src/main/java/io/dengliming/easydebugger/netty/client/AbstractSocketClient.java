package io.dengliming.easydebugger.netty.client;

import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.ProtocolException;
import io.dengliming.easydebugger.netty.UnWritableProtocolException;
import io.dengliming.easydebugger.netty.event.ClientOnlineEvent;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.BlockingOperationException;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

import static io.dengliming.easydebugger.constant.CommonConstant.*;

@Slf4j
public abstract class AbstractSocketClient implements IClient<NioEventLoopGroup> {

    private final ConnectProperties config;
    private final IGenericEventListener clientEventListener;
    private Channel channel;
    private Bootstrap bootstrap;
    private EventLoopGroup workerGroup;

    public AbstractSocketClient(ConnectProperties config, IGenericEventListener clientEventListener) {
        this.config = config;
        this.clientEventListener = clientEventListener;
    }

    public void init() {
        this.init(new NioEventLoopGroup());
    }

    public void init(NioEventLoopGroup clientGroup) {
        this.bootstrap = new Bootstrap()
                .group(clientGroup)
                .channel(channel())
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        // 关掉原有的链接
                        if (AbstractSocketClient.this.channel != null) {
                            AbstractSocketClient.this.channel.close();
                        }

                        AbstractSocketClient.this.channel = channel;
                        ChannelPipeline pipeline = channel.pipeline();

                        // 设置客户端编解码器
                        pipeline.addFirst(CLIENT_PROTOCOL_DECODER, createProtocolDecoder());
                        pipeline.addFirst(CLIENT_PROTOCOL_ENCODER, createProtocolEncoder());

                        // 业务处理器新增到最后
                        pipeline.addLast(CLIENT_SERVICE_HANDLER, new ClientChannelHandler(AbstractSocketClient.this));

                        // 自定义处理器
                        AbstractSocketClient.this.doInitChannel(channel);

                    }
                });
        this.doInitOptions(this.bootstrap);
        this.workerGroup = clientGroup;
    }

    @Override
    public ChannelFuture connect(Consumer<?> consumer, long timeout) {
        if (timeout <= 0) {
            timeout = getConfig().getConnectTimeoutMillisecond();
        }
        try {
            // 只有在未激活的情况下才进行连接
            if (this.getChannel() == null || !this.getChannel().isActive()) {
                return this.doConnect(consumer == null ? (future) -> {
                    if (future.isSuccess()) {
                        log.info("客户端标识：{} - 远程主机 {}:{} - 连接服务器成功", getConfig().getConnectKey(),
                                this.getHost(), this.getPort());
                    } else {
                        log.error("客户端标识：{} - 远程主机 {}:{} - 连接服务器失败", getConfig().getConnectKey(),
                                this.getHost(), this.getPort(), future.cause());
                    }
                } : (Consumer<ChannelFuture>) consumer, timeout);
            } else {
                return getChannel().newSucceededFuture();
            }
        } catch (Exception e) {
            log.error("连接失败异常", e);
            return getChannel().newFailedFuture(new ProtocolException("连接失败 " + e.getMessage(), e));
        }
    }

    /**
     * 真正连接服务器的方法
     *
     * @param consumer 可以自定义连接成功或的操作
     * @param timeout  指定连接超时时间
     */
    protected ChannelFuture doConnect(Consumer<ChannelFuture> consumer, long timeout) {
        if (timeout > 0) {
            this.bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout);
        }

        return this.bootstrap.connect(getConfig().getHost(), getConfig().getPort()).addListener(future -> {
            try {
                consumer.accept((ChannelFuture) future);
            } finally {
                if (future.isSuccess()) {
                    this.successCallback((ChannelFuture) future);
                }
            }
        });
    }

    protected void successCallback(ChannelFuture future) {
        clientEventListener.onEvent(new ClientOnlineEvent(getConfig().getConnectKey()));
    }

    /**
     * 断开连接
     */
    @Override
    public ChannelFuture disconnect() {
        if (this.channel == null) {
            return null;
        }
        return this.channel.disconnect().addListener(future -> {
            if (future.isSuccess()) {
                if (log.isDebugEnabled()) {
                    log.debug("关闭客户端(成功) - 远程主机 {}:{}", getConfig().getHost(), getConfig().getPort());
                }
            } else {
                // 断开失败
                log.error("关闭客户端(失败) - 远程主机 {}:{}", getConfig().getHost(), getConfig().getPort(), future.cause());
            }
        });
    }

    public ChannelFuture writeAndFlush(Object msg, Object... args) {
        return writeAndFlush(msg, false, args);
    }

    public ChannelFuture writeAndFlush(Object msg, boolean reconnect, Object... args) {
        if ((this.getChannel() == null || !this.getChannel().isActive())) {
            if (!reconnect) {
                return null;
            }
            try {
                // 尝试重连, 并且等待
                return connect((arg) -> {
                    ChannelFuture future = (ChannelFuture) arg;
                    if (future.isSuccess()) {
                        if (log.isDebugEnabled()) {
                            log.debug("客户端写报文重连成功 - 远程主机 {}:{} - 客户端标识：{}"
                                    , this.getHost(), this.getPort(), getConfig().getConnectKey());
                        }
                    } else {
                        log.error("客户端写报文重连失败 - 远程主机 {}:{} - 客户端标识：{}"
                                , this.getHost(), this.getPort(), getConfig().getConnectKey(), future.cause());
                    }
                }, 3000).addListener((future) -> {
                    final ChannelFuture channelFuture = (ChannelFuture) future;
                    // 重连成功后写出报文
                    if (channelFuture.isSuccess()) {
                        this.writeAndFlush(msg);
                    }
                }).syncUninterruptibly();
            } catch (BlockingOperationException e) {
                throw new ProtocolException("写报文尝试重连失败 连接同步死锁", e);
            } catch (Exception e) {
                throw new ProtocolException("写报文尝试重连失败 " + e.getMessage(), e);
            }
        } else if (this.getChannel() != null && this.getChannel().isWritable()) {
            return this.getChannel().writeAndFlush(msg);
        } else {
            return this.getChannel().newFailedFuture(new UnWritableProtocolException());
        }
    }

    protected void doInitChannel(Channel channel) {
    }

    protected void doInitOptions(Bootstrap bootstrap) {
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
    }

    protected Class<? extends Channel> channel() {
        return NioSocketChannel.class;
    }

    public String getHost() {
        return getConfig().getHost();
    }

    public int getPort() {
        return getConfig().getPort();
    }

    public ConnectProperties getConfig() {
        return config;
    }

    public Channel getChannel() {
        return channel;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    /**
     * 创建客户端socket解码器
     *
     * @return
     */
    protected abstract ChannelInboundHandler createProtocolDecoder();

    /**
     * 创建socket编码器
     */
    protected abstract ChannelOutboundHandlerAdapter createProtocolEncoder();

    public IGenericEventListener getClientEventListener() {
        return clientEventListener;
    }

    @Override
    public void destroy() {
        if (null != channel) {
            channel.close();
        }
        workerGroup.shutdownGracefully();
    }
}

