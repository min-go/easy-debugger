package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.AbstractSocketClient;
import io.dengliming.easydebugger.utils.ConnectType;
import io.dengliming.easydebugger.utils.SocketDebugCache;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpClientController extends AbstractClientController {

    @FXML
    private Button connectBtn;
    @FXML
    private Text statusText;

    private void initConnectBtn() {
        connectBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig selectedItem = selectSingleConfig();
            if (selectedItem == null) {
                return;
            }

            try {
                AbstractSocketClient client = SocketDebugCache.INSTANCE.getOrCreateClient(selectedItem, this);
                client.connect(null, 3000);
            } catch (Exception e) {
                log.error("连接异常", e);
            }
        });
    }

    @Override
    protected void setStatusText(Text text) {
        statusText.setText(text.getText());
        statusText.setFill(text.getFill());
    }

    @Override
    protected void setClientStatus(boolean online) {
        if (online) {
            statusText.setText("已连接");
            statusText.setFill(Paint.valueOf("green"));
        } else {
            statusText.setText("未连接");
            statusText.setFill(Paint.valueOf("red"));
        }
    }

    @Override
    protected ConnectType connectType() {
        return ConnectType.TCP_CLIENT;
    }
}
