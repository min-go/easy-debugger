package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.constant.ConnectType;
import io.dengliming.easydebugger.constant.MsgType;
import io.dengliming.easydebugger.model.ClientSession;
import io.dengliming.easydebugger.netty.MessageFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketServerController extends AbstractServerController {

    @Override
    protected ConnectType connectType() {
        return ConnectType.WEBSOCKET_SERVER;
    }

    @Override
    protected Object buildSocketMessage(ClientSession session, MsgType msgType, String sendMsg) {
        return MessageFactory.createWebSocketMessage(msgType, sendMsg).buildBinaryWebSocketFrame();
    }
}
