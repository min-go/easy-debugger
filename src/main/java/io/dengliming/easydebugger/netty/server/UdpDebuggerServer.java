package io.dengliming.easydebugger.netty.server;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.ConnectProperties;
import io.dengliming.easydebugger.netty.codec.UdpMsgDecoder;
import io.dengliming.easydebugger.netty.codec.MsgEncoder;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;

import static io.dengliming.easydebugger.constant.CommonConstant.*;

@Slf4j
public class UdpDebuggerServer extends AbstractDebuggerServer {

    private EventLoopGroup workerGroup;

    public UdpDebuggerServer(ConnectConfig config, IGenericEventListener eventListener) {
        super(new ConnectProperties(config.getHost(), config.getPort(), config.getUid()), eventListener);
    }

    @Override
    protected AbstractBootstrap initBootstrap() {
        workerGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap()
                .group(workerGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();

                        // 设置编码器
                        p.addFirst(SERVER_ENCODER_HANDLER, createProtocolEncoder());

                        // 设置解码器
                        p.addFirst(SERVER_DECODER_HANDLER, createProtocolDecoder());

                        // 业务处理器新增到最后
                        p.addLast(SERVER_SERVICE_HANDLER, new UdpServerChannelHandler(getConfig(), getEventListener()));

                    }
                });

        doInitOptions(bootstrap);
        return bootstrap;
    }

    protected void doInitOptions(AbstractBootstrap bootstrap) {
        bootstrap.option(ChannelOption.SO_RCVBUF, 1024 * 1024)
                .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
                .option(EpollChannelOption.SO_REUSEPORT, true)
                .option(ChannelOption.SO_BROADCAST, true);
    }

    @Override
    protected ChannelOutboundHandlerAdapter createProtocolEncoder() {
        return new MsgEncoder();
    }

    @Override
    protected ChannelInboundHandlerAdapter createProtocolDecoder() {
        return new UdpMsgDecoder();
    }

    @Override
    public void destroy() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
