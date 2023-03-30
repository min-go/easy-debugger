package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.model.ConnectConfig;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;

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
    private ButtonType submitBtnType;

    private ConnectConfig config;

    private Dialog dialog;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    public void setDialog(Dialog dialog) {
        this.dialog = dialog;
        this.dialog.setResultConverter(buttonType -> {
            if (((ButtonType) buttonType).getButtonData() == submitBtnType.getButtonData()) {
                if (config != null) {
                    config.setName(nameField.getText());
                    config.setHost(hostField.getText());
                    config.setPort(Integer.parseInt(portField.getText()));
                }

                this.isOkClicked = true;
            }
            return null;
        });
    }

    public void setConfig(ConnectConfig config) {
        this.config = config;
        hostField.setText(config.getHost());
        nameField.setText(config.getName());
        portField.setText(config.getPort() > 0 ? String.valueOf(config.getPort()) : "");
    }

    public boolean isOkClicked() {
        return isOkClicked;
    }
}
