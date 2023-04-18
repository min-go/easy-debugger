package io.dengliming.easydebugger.netty.client;

import io.dengliming.easydebugger.constant.MsgType;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.MessageFactory;
import io.dengliming.easydebugger.netty.SocketMessage;
import io.dengliming.easydebugger.netty.codec.MsgDecoder;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.DatagramPacketDecoder;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

@Slf4j
public class UdpDebuggerClient extends SocketDebuggerClient {

    public UdpDebuggerClient(ConnectConfig config, IGenericEventListener clientEventListener) {
        super(config, clientEventListener);
    }

    @Override
    protected void doInitOptions(Bootstrap bootstrap) {
        bootstrap.option(ChannelOption.SO_BROADCAST, true);
    }

    @Override
    protected Class<? extends Channel> channel() {
        return NioDatagramChannel.class;
    }

    @Override
    protected ChannelInboundHandler createProtocolDecoder() {
        return new DatagramPacketDecoder(new MsgDecoder());
    }

    @Override
    protected SocketMessage doBuildMessage(MsgType msgType, String message) {
        return MessageFactory.createUdpMessage(msgType, message)
                .setRecipient((InetSocketAddress) getConfig().remoteSocketAddress());
    }
}
