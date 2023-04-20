package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.constant.ConnectType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketClientController extends TcpClientController {
    @Override
    protected ConnectType connectType() {
        return ConnectType.WEBSOCKET_CLIENT;
    }
}