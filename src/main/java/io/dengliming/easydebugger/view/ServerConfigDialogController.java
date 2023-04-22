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

public class ServerConfigDialogController implements Initializable {

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
    private CheckBox autoReplyBox;
    @FXML
    private TextField replyMsg;

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
            config.setAutoReply(autoReplyBox.isSelected());
            if (!autoReplyBox.isSelected()) {
                config.setSendMsgType(null);
                config.setSendMsg("");
            } else {
                String msgType = msgTypeComboBox.getSelectionModel().getSelectedItem();
                if (msgType != null) {
                    config.setSendMsgType(MsgType.getByName(msgType));
                }
                config.setSendMsg(replyMsg.getText());
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
        autoReplyBox.setSelected(config.isAutoReply());
        replyMsg.setText(config.getSendMsg());
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

        // 如果选中自动回复
        else if (autoReplyBox.isSelected()) {
            if (replyMsg.getText() == null || replyMsg.getText().length() == 0) {
                errorMessage = "请输入回复的内容！";
            }
            else {
                String msgType = msgTypeComboBox.getSelectionModel().getSelectedItem();
                if (msgType != null && MsgType.getByName(msgType) == MsgType.HEX && !T.isHexString(replyMsg.getText())) {
                    errorMessage = "请输入正确的16进制内容！";
                }
            }
        }

        if (errorMessage.length() == 0) {
            return true;
        }

        Alerts.showWarning(null, errorMessage);
        return false;
    }
}
