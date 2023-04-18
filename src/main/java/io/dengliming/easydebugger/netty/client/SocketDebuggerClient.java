package io.dengliming.easydebugger.netty.client;

import io.dengliming.easydebugger.constant.MsgType;
import io.dengliming.easydebugger.model.ChatMsgBox;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.*;
import io.dengliming.easydebugger.netty.codec.MsgEncoder;
import io.dengliming.easydebugger.netty.event.IGenericEventListener;
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
    private final ChatMsgBox chatMsgBox;
    private ScheduledFuture scheduledFuture;
    private volatile boolean stopScheduled = true;

    public SocketDebuggerClient(ConnectConfig config, IGenericEventListener clientEventListener) {
        super(new ConnectProperties(config.getHost(), config.getPort(), config.getUid()), clientEventListener);
        this.connectConfig = config;
        this.chatMsgBox = new ChatMsgBox();

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
            //IMessage msg = MessageFactory.createMessage(msgType, message);
            ChannelFuture future = this.writeAndFlush(doBuildMessage(msgType, message));
            if (future != null) {
                future.addListener(f -> {
                    if (!f.isSuccess()) {
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
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        chatMsgBox.clear();
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

    public void online() {
        chatMsgBox.setOnline(true);
        setStopScheduled(false);
    }

    public void offline() {
        chatMsgBox.setOnline(false);
        setStopScheduled(true);
    }

    public ChatMsgBox getChatMsgBox() {
        return chatMsgBox;
    }

    protected SocketMessage doBuildMessage(MsgType msgType, String message) {
        return MessageFactory.createSocketMessage(msgType, message);
    }
}
