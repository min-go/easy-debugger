package io.dengliming.easydebugger.netty.client;

import io.dengliming.easydebugger.model.ClientDebugger;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.*;
import io.dengliming.easydebugger.netty.codec.MsgEncoder;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
import io.dengliming.easydebugger.utils.SocketDebuggerCache;
import io.dengliming.easydebugger.utils.T;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.handler.codec.string.StringDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class SocketDebuggerClient extends AbstractSocketClient {

    private final ConnectConfig connectConfig;
    private ScheduledFuture scheduledFuture;
    private volatile boolean stopScheduled;

    public SocketDebuggerClient(ConnectConfig config, IGenericEventListener clientEventListener) {
        super(new ConnectProperties(config.getHost(), config.getPort(), config.getUid()), clientEventListener);
        this.connectConfig = config;

        // 是否配置重复发送
        if (config.isRepeatSend() && config.getSendInterval() > 0 && T.hasLength(config.getSendMsg())) {
            this.scheduledFuture = ThreadManager.INSTANCE.getScheduledThreadPoolExecutor()
                    .scheduleWithFixedDelay(() -> {
                                if (stopScheduled) {
                                    return;
                                }
                                sendMsg(config.getSendMsgType(), config.getSendMsg());
                            },
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
            IMessage msg = MessageFactory.createMessage(msgType, message);
            ChannelFuture future = this.writeAndFlush(msg);
            if (future != null) {
                future.addListener(f -> {
                    if (!f.isSuccess()) {
                        return;
                    }
                    ClientDebugger clientDebugger = SocketDebuggerCache.INSTANCE.getClientDebugger(connectConfig.getUid());
                    if (clientDebugger != null) {
                        clientDebugger.getChatMsgBox().addRightMsg(message);
                    }
                });
            }
        } catch (Exception e) {
            log.error("sendMsg error.", e);
        }
    }

    @Override
    public void destroy() {
        super.destroy();

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    public void setStopScheduled(boolean val) {
        this.stopScheduled = val;
    }

    @Override
    public ChannelFuture disconnect() {
        return super.disconnect().addListener(future -> {
            if (future.isSuccess()) {
                setStopScheduled(true);
            }
        });
    }
}
