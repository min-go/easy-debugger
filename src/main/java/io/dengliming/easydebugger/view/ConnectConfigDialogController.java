package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.constant.MsgType;
import io.dengliming.easydebugger.utils.Alerts;
import io.dengliming.easydebugger.utils.T;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class ConnectConfigDialogController implements Initializable {

    private boolean isOkClicked;

    @FXML
    private TextField nameField;
    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private ComboBox<String> msgTypeComboBox;
    @FXML
    private CheckBox repeatSendBox;
    @FXML
    private TextField sendIntervalField;
    @FXML
    private TextField repeatSendMsg;

    private ConnectConfig config;
    private Stage dialogStage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    @FXML
    public void handleSubmit() {
        if (!isInputValid()) {
            return;
        }

        if (config != null) {
            config.setName(nameField.getText());
            config.setHost(hostField.getText());
            config.setPort(Integer.parseInt(portField.getText()));
            config.setRepeatSend(repeatSendBox.isSelected());
            String msgType = msgTypeComboBox.getSelectionModel().getSelectedItem();
            if (msgType != null) {
                config.setSendMsgType(MsgType.getByName(msgType));
            }
            config.setSendMsg(repeatSendMsg.getText());
            String sendInterval = sendIntervalField.getText();
            if (sendInterval != null && sendInterval.length() > 0) {
                config.setSendInterval(Integer.parseInt(sendInterval));
            }
        }

        this.isOkClicked = true;
        this.dialogStage.close();
    }

    @FXML
    public void handleCancel() {
        this.dialogStage.close();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setConfig(ConnectConfig config) {
        this.config = config;
        hostField.setText(config.getHost());
        nameField.setText(config.getName());
        portField.setText(config.getPort() > 0 ? String.valueOf(config.getPort()) : "");
        if (config.getSendMsgType() != null) {
            msgTypeComboBox.setValue(config.getSendMsgType().getName());
        }
        repeatSendBox.setSelected(config.isRepeatSend());
        repeatSendMsg.setText(config.getSendMsg());
        sendIntervalField.setText(String.valueOf(config.getSendInterval()));
    }

    public boolean isOkClicked() {
        return isOkClicked;
    }

    private boolean isInputValid() {
        String errorMessage = "";
        if (nameField.getText() == null || nameField.getText().length() == 0) {
            errorMessage = "请输入名称！";
        } else if (hostField.getText() == null || hostField.getText().length() == 0) {
            errorMessage = "请输入主机地址！";
        } else if (!T.isValidPort(portField.getText())) {
            errorMessage = "请输入0~65535端口号！";
        }

        // 如果选中重复发送
        else if (repeatSendBox.isSelected()) {
            if (sendIntervalField.getText() == null || sendIntervalField.getText().length() == 0) {
                errorMessage = "请输入重复发送的间隔！";
            } else if (repeatSendMsg.getText() == null || repeatSendMsg.getText().length() == 0) {
                errorMessage = "请输入重复发送的内容！";
            }
        }

        if (errorMessage.length() == 0) {
            return true;
        }

        Alerts.showWarning(null, errorMessage);
        return false;
    }
}
