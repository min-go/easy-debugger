package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.codec.MsgEncoder;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

import static io.dengliming.easydebugger.constant.CommonConstant.*;

@Slf4j
public class WebSocketDebuggerServer extends AbstractDebuggerServer {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public WebSocketDebuggerServer(ConnectConfig config, IGenericEventListener eventListener) {
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

                        p.addLast(new HttpServerCodec()); // HTTP 协议解析，用于握手阶段
                        p.addLast(new HttpObjectAggregator(65536)); // HTTP 协议解析，用于握手阶段
                        p.addLast(new WebSocketServerCompressionHandler()); // WebSocket 数据压缩扩展
                        p.addLast(new WebSocketServerProtocolHandler("/", null, true)); // WebSocket 握手、控制帧处理

                        // 业务处理器新增到最后
                        p.addLast(SERVER_SERVICE_HANDLER, new WebSocketServerChannelHandler(getConfig(), getEventListener()));
                    }
                });
        doInitOptions(bootstrap);
        return bootstrap;
    }

    protected void doInitOptions(AbstractBootstrap bootstrap) {
        bootstrap.handler(new LoggingHandler(LogLevel.INFO));
    }

    @Override
    protected ChannelOutboundHandlerAdapter createProtocolEncoder() {
        return null;
    }

    @Override
    protected ChannelInboundHandlerAdapter createProtocolDecoder() {
        return null;
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
