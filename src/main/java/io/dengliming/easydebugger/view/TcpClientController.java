package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.constant.CommonConstant;
import io.dengliming.easydebugger.constant.ConnectType;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.client.SocketDebuggerClient;
import io.dengliming.easydebugger.utils.Alerts;
import io.dengliming.easydebugger.utils.SocketDebuggerCache;
import io.netty.channel.ChannelFuture;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.ResourceBundle;

import static io.dengliming.easydebugger.constant.CommonConstant.*;

@Slf4j
public class TcpClientController extends AbstractClientController {

    @FXML
    private Button connectBtn;
    @FXML
    private Text statusText;
    @FXML
    private Circle statusCircle;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        super.initialize(location, resources);
        initConnectBtn();
    }

    private void initConnectBtn() {
        connectBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig selectedConfig = getSelectedConnectConfig();
            if (selectedConfig == null) {
                return;
            }

            try {
                SocketDebuggerClient clientDebugger = SocketDebuggerCache.INSTANCE.getOrCreateClient(selectedConfig, this);

                if (connectBtn.getText().equals(DIS_CONNECT_TEXT)) {
                    clientDebugger.disconnect();
                } else {
                    clientDebugger.connect(it -> {
                        ChannelFuture future = (ChannelFuture) it;
                        if (!future.isSuccess()) {
                            Platform.runLater(() -> Alerts.showError("连接异常！", future.cause()));
                            log.error("连接异常", future.cause());
                        }
                    }, 3000);
                }
            } catch (Exception e) {
                log.error("连接异常", e);
            }
        });
    }

    @Override
    protected void setClientStatus(boolean online) {
        if (online) {
            statusText.setText(CONNECTED_TEXT);
            connectBtn.setText(DIS_CONNECT_TEXT);
            statusCircle.setFill(CommonConstant.GREEN_PAINT);
        } else {
            statusText.setText(UNCONNECTED);
            connectBtn.setText(CONNECT_TEXT);
            statusCircle.setFill(CommonConstant.DARKGREY_PAINT);
        }
    }

    @Override
    protected boolean verifyConnectStatus() {
        if (statusText.getText().equals(CONNECTED_TEXT)) {
            return true;
        }
        return false;
    }

    @Override
    protected ConnectType connectType() {
        return ConnectType.TCP_CLIENT;
    }
}