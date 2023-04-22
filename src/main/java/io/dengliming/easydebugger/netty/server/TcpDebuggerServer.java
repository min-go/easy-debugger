package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.codec.MsgDecoder;
import io.dengliming.easydebugger.netty.codec.MsgEncoder;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

import static io.dengliming.easydebugger.constant.CommonConstant.*;

@Slf4j
public class TcpDebuggerServer extends AbstractDebuggerServer {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public TcpDebuggerServer(ConnectConfig config, IGenericEventListener eventListener) {
        super(new ConnectProperties(config.getHost(), config.getPort(), config.getUid()), eventListener);
    }

    @Override
    protected AbstractBootstrap initBootstrap() {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();

                        // 设置编码器
                        p.addFirst(SERVER_ENCODER_HANDLER, createProtocolEncoder());

                        // 设置解码器
                        p.addFirst(SERVER_DECODER_HANDLER, createProtocolDecoder());

                        // 业务处理器新增到最后
                        p.addLast(SERVER_SERVICE_HANDLER, new ServerChannelHandler(getConfig(), getEventListener()));
                    }
                });
        doInitOptions(bootstrap);
        return bootstrap;
    }

    protected void doInitOptions(AbstractBootstrap bootstrap) {
        bootstrap.handler(new LoggingHandler(LogLevel.INFO));
        ((ServerBootstrap) bootstrap).option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
    }

    @Override
    protected ChannelOutboundHandlerAdapter createProtocolEncoder() {
        return new MsgEncoder();
    }

    @Override
    protected ChannelInboundHandlerAdapter createProtocolDecoder() {
        return new MsgDecoder();
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
