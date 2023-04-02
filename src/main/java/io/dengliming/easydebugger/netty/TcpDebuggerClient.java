package io.dengliming.easydebugger.netty;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.utils.TcpDebugCache;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.handler.codec.string.StringDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TcpDebuggerClient extends TcpSocketClient {

    private final ConnectConfig connectConfig;
    private ScheduledFuture scheduledFuture;
    public TcpDebuggerClient(ConnectConfig config, IClientEventListener eventHandler) {
        super(new ClientConnectProperties(config.getHost(), config.getPort(), config.getUid()), eventHandler);
        this.connectConfig = config;

        // 是否配置重复发送
        if (config.isRepeatSend() && config.getSendInterval() > 0) {
            this.scheduledFuture = ThreadManager.INSTANCE.getScheduledThreadPoolExecutor()
                    .scheduleWithFixedDelay(() -> sendMsg(config.getRepeatSendMsgType(), config.getRepeatSendMsg()),
                            config.getSendInterval(), config.getSendInterval(), TimeUnit.SECONDS);
        }
    }

    @Override
    protected ChannelInboundHandler createProtocolDecoder() {
        return new StringDecoder();
    }

    @Override
    protected ChannelOutboundHandlerAdapter createProtocolEncoder() {
        return new MsgEncoder();
    }

    public void sendMsg(MsgType msgType, String message) {
        try {
            this.connect(null, 3000);
            IMessage msg = null;
            if (msgType == MsgType.HEX) {
                msg = new HexStringMessage(message);
            } else {
                msg = new StringMessage(message);
            }
            this.writeAndFlush(msg);
            TcpDebugCache.INSTANCE.getOrCreateChatMsgBox(connectConfig.getUid()).addRightMsg(message);
        } catch (Exception e) {
            log.error("connect error.", e);
        }
    }

    @Override
    public void destroy() {
        super.destroy();

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }
}
