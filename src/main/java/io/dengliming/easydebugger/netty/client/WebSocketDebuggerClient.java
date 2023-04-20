package io.dengliming.easydebugger.netty.client;

import io.dengliming.easydebugger.constant.CommonConstant;
import io.dengliming.easydebugger.constant.MsgType;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.MessageFactory;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Slf4j
public class WebSocketDebuggerClient extends SocketDebuggerClient {

    public WebSocketDebuggerClient(ConnectConfig config, IGenericEventListener clientEventListener) {
        super(config, clientEventListener);
    }

    @Override
    protected Bootstrap createBootstrap(NioEventLoopGroup clientGroup) {
        Bootstrap b = new Bootstrap();
        // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
        // If you change it to V00, ping is not supported and remember to change
        // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
        final WebSocketClientHandler handler =
                new WebSocketClientHandler(
                        WebSocketClientHandshakerFactory.newHandshaker(
                                URI.create(String.format("ws://%s:%s", getHost(), getPort())),
                                WebSocketVersion.V13, null, false,
                                HttpHeaders.EMPTY_HEADERS, 1280000), this);

        b.group(clientGroup)
                .channel(channel())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        setChannel(ch);
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(WebSocketClientCompressionHandler.INSTANCE); // WebSocket 数据压缩扩展
                        pipeline.addLast(CommonConstant.CLIENT_SERVICE_HANDLER, handler);
                    }
                });
        return b;
    }

    @Override
    protected Class<? extends Channel> channel() {
        return NioSocketChannel.class;
    }

    @Override
    protected Object doBuildMessage(MsgType msgType, String message) {
        return MessageFactory.createWebSocketMessage(msgType, message).buildBinaryWebSocketFrame();
    }
}
