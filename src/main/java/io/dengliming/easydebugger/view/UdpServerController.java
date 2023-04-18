package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.constant.ConnectType;
import io.dengliming.easydebugger.constant.MsgType;
import io.dengliming.easydebugger.model.ClientItem;
import io.dengliming.easydebugger.model.ClientSession;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.MessageFactory;
import io.dengliming.easydebugger.netty.SocketMessage;
import io.dengliming.easydebugger.netty.UdpMessage;
import io.dengliming.easydebugger.netty.event.ChannelEvent;
import io.dengliming.easydebugger.netty.event.ClientReadMessageEvent;
import io.dengliming.easydebugger.netty.server.AbstractDebuggerServer;
import io.dengliming.easydebugger.utils.ConfigStorage;
import io.dengliming.easydebugger.utils.SocketDebuggerCache;
import io.dengliming.easydebugger.utils.T;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
public class UdpServerController extends AbstractServerController {

    @Override
    protected void initClientNameCellFactory() {
        clientNameColumn.setCellFactory(col -> new ClientCell());
        clientNameColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
    }

    @Override
    public void onEvent(ChannelEvent event) {
        Platform.runLater(() -> {
            String clientId = event.getSource().toString();
            if (event instanceof ClientReadMessageEvent) {
                ClientReadMessageEvent msgEvent = (ClientReadMessageEvent) event;
                ConnectConfig serverConfig = ConfigStorage.INSTANCE.getConnectConfig(msgEvent.getServerId());
                if (serverConfig == null) {
                    return;
                }

                Optional<ClientItem> clientItem = clientList
                        .getItems()
                        .stream()
                        .filter(it -> it.getClientName().equals(clientId))
                        .findFirst();
                if (!clientItem.isPresent()) {
                    clientList.getItems().add(new ClientItem(msgEvent.getServerId(), clientId, true));
                } else {
                    clientItem.get().setConnected(true);
                }

                // 如何当前还没有选中默认选中一个
                if (clientList.getItems().size() > 0 && clientList.getSelectionModel().getSelectedIndex() < 0) {
                    clientList.getSelectionModel().select(0);
                }
                clientList.refresh();

                ((AbstractDebuggerServer) SocketDebuggerCache.INSTANCE.getOrCreateServer(serverConfig, this))
                        .getServerDebuggerView().addLeftMsg(clientId, new String(((UdpMessage) msgEvent.getMsg()).getMessage(), StandardCharsets.UTF_8));

                // 自动回复消息
                if (serverConfig.isAutoReply() && T.hasLength(serverConfig.getSendMsg())) {
                    sendMsg(serverConfig.getUid(), clientId, serverConfig.getSendMsgType(), serverConfig.getSendMsg());
                }
            }
        });
    }

    @Override
    protected ConnectType connectType() {
        return ConnectType.UDP_SERVER;
    }

    @Override
    protected SocketMessage buildSocketMessage(ClientSession session, MsgType msgType, String sendMsg) {
        return MessageFactory.createUdpMessage(msgType, sendMsg).setRecipient(session.getSender());
    }
}
