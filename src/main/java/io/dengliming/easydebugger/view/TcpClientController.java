package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.constant.ConnectType;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.AbstractSocketClient;
import io.dengliming.easydebugger.utils.SocketDebugCache;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Paint;
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        super.initialize(location, resources);
        initConnectBtn();
    }

    private void initConnectBtn() {
        connectBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig selectedItem = selectSingleConfig();
            if (selectedItem == null) {
                return;
            }

            try {
                AbstractSocketClient client = SocketDebugCache.INSTANCE.getOrCreateClient(selectedItem, this);

                if (connectBtn.getText().equals(DIS_CONNECT_TEXT)) {
                    client.disconnect();
                } else {
                    client.connect(null, 3000);
                }
            } catch (Exception e) {
                log.error("连接异常", e);
            }
        });
    }

    @Override
    protected void setStatusText(Text text) {
        statusText.setText(text.getText());
        statusText.setFill(text.getFill());
        if (text.getText().equals(UNCONNECTED)) {
            connectBtn.setText(CONNECT_TEXT);
        } else {
            connectBtn.setText(DIS_CONNECT_TEXT);
        }
    }

    @Override
    protected void setClientStatus(boolean online) {
        if (online) {
            statusText.setText(CONNECTED_TEXT);
            statusText.setFill(Paint.valueOf("green"));
            connectBtn.setText(DIS_CONNECT_TEXT);
        } else {
            statusText.setText(UNCONNECTED);
            statusText.setFill(Paint.valueOf("red"));
            connectBtn.setText(CONNECT_TEXT);
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
