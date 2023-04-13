package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.constant.ConnectType;

public class UdpClientController extends AbstractClientController {
    @Override
    protected ConnectType connectType() {
        return ConnectType.UDP_CLIENT;
    }
}
