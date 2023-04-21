package io.dengliming.easydebugger.netty.client;

import io.dengliming.easydebugger.constant.MsgType;
import io.dengliming.easydebugger.model.ChatMsgBox;
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

    public SocketDebuggerClient(ConnectConfig config, IGenericEventListener clientEventListener) {
        super(new ConnectProperties(config.getHost(), config.getPort(), config.getUid()), clientEventListener);
        this.connectConfig = config;
        // 是否配置重复发送
        enableRepeatSendSchedule();
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
            ChannelFuture future = this.writeAndFlush(doBuildMessage(msgType, message));
            if (future != null) {
                future.addListener(f -> {
                    if (!f.isSuccess()) {
                        log.error("sendMsg error.", future.cause());
                        return;
                    }
                    SocketDebuggerClient.this.getChatMsgBox().addRightMsg(message);
                });
            }
        } catch (Exception e) {
            log.error("sendMsg error.", e);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        offline();
    }

    public void enableRepeatSendSchedule() {
        disableRepeatSendSchedule();
        // 是否配置重复发送
        if (connectConfig.isRepeatSend() && connectConfig.getSendInterval() > 0 && T.hasLength(connectConfig.getSendMsg())) {
            this.scheduledFuture = ThreadManager.INSTANCE.getScheduledThreadPoolExecutor()
                    .scheduleWithFixedDelay(() -> sendMsg(connectConfig.getSendMsgType(), connectConfig.getSendMsg()),
                            connectConfig.getSendInterval(), connectConfig.getSendInterval(), TimeUnit.SECONDS);
            log.info("enableRepeatSendSchedule({}) done.", connectConfig.getName());
        }
    }

    public void disableRepeatSendSchedule() {
        try {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
                log.info("disableRepeatSendSchedule({}) done.", connectConfig.getName());
            }
        } catch (Exception e) {
            log.error("disableRepeatSendSchedule({}) error", connectConfig.getName(), e);
        }
    }

    @Override
    public ChannelFuture disconnect() {
        ChannelFuture channelFuture = super.disconnect();
        return channelFuture == null ? null : channelFuture.addListener(future -> {
            if (future.isSuccess()) {
                disableRepeatSendSchedule();
            }
        });
    }

    public void online() {
        getChatMsgBox().setOnline(true);
        enableRepeatSendSchedule();
    }

    public void offline() {
        getChatMsgBox().setOnline(false);
        disableRepeatSendSchedule();
    }

    public ChatMsgBox getChatMsgBox() {
        return SocketDebuggerCache
                .INSTANCE
                .getOrCreateClientDebuggerView(connectConfig.getUid())
                .getChatMsgBox();
    }

    protected Object doBuildMessage(MsgType msgType, String message) {
        return MessageFactory.createSocketMessage(msgType, message);
    }
}
