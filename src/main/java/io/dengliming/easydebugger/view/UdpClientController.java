package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.constant.ConnectType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UdpClientController extends AbstractClientController {
    @Override
    protected ConnectType connectType() {
        return ConnectType.UDP_CLIENT;
    }
}
