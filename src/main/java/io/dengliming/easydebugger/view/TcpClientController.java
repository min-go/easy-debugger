package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.model.ChatMsgBox;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.*;
import io.dengliming.easydebugger.utils.ConfigStorage;
import io.dengliming.easydebugger.utils.TcpDebugCache;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
public class TcpClientController implements IClientEventListener, Initializable {
    @FXML
    private ListView connectConfigListView;
    @FXML
    private Button clearBtn;
    @FXML
    private TextField sendMsgField;
    @FXML
    private Button sendMsgBtn;
    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private Button deleteBtn;
    @FXML
    private Button addBtn;
    @FXML
    private Button editBtn;
    @FXML
    private Button connectBtn;
    @FXML
    private RadioButton stringMsgOption;
    @FXML
    private RadioButton hexMsgOption;
    @FXML
    private ScrollPane msgContentPane;
    @FXML
    private Text statusText;
    private ToggleGroup group;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initConnectConfigListView();

        initAddBtn();

        initEditBtn();

        initDeleteBtn();

        initClearBtn();

        initSendMsgBtn();

        initConnectBtn();

        initToggleGroup();
    }

    private void initToggleGroup() {
        group = new ToggleGroup();
        stringMsgOption.setToggleGroup(group);
        hexMsgOption.setToggleGroup(group);
        stringMsgOption.setSelected(true);
    }

    private void initClearBtn() {
        clearBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> ((ListView) (msgContentPane.getContent())).getItems().clear());
    }

    private void initSendMsgBtn() {
        sendMsgBtn.setOnAction(event -> {
            // 发送消息
            String message = sendMsgField.getText();
            if (message == null || "".equals(message)) {
                return;
            }

            ConnectConfig selectedItem = selectSingleConfig();
            if (selectedItem == null) {
                return;
            }

            try {
                TcpDebuggerClient client = (TcpDebuggerClient) TcpDebugCache.INSTANCE.getOrCreateClient(selectedItem, this);
                client.sendMsg(hexMsgOption.isSelected() ? MsgType.HEX : MsgType.STRING, message);
            } catch (Exception e) {
                log.error("connect error.", e);
            }

            sendMsgField.clear();
        });
    }

    private void initDeleteBtn() {
        deleteBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ObservableList selectedItems = connectConfigListView.getSelectionModel().getSelectedItems();
            if (selectedItems == null || selectedItems.size() == 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setHeaderText("请选择要删除的连接！");
                alert.showAndWait();
                return;
            }

            List<String> clientList = new ArrayList<>(selectedItems.size());
            for (Object selectedItem : selectedItems) {
                clientList.add(((ConnectConfig) selectedItem).getUid());
            }
            ConfigStorage.INSTANCE.removeAll(selectedItems);
            connectConfigListView.getItems().removeAll(selectedItems);

            for (String clientId : clientList) {
                TcpDebugCache.INSTANCE.removeCache(clientId);
            }

            hostField.clear();
            portField.clear();
            setClientStatus(false);
        });
    }

    private void initAddBtn() {
        addBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig tmpConfig = new ConnectConfig();
            boolean result = showConnectConfigEditDialog(tmpConfig);
            if (result) {
                connectConfigListView.getItems().add(tmpConfig);
                ConfigStorage.INSTANCE.add(tmpConfig);
            }
        });
    }

    private void initEditBtn() {
        editBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig selectedItem = selectSingleConfig();
            if (selectedItem == null) {
                return;
            }
            int selectedIndex = connectConfigListView.getSelectionModel().getSelectedIndex();

            boolean result = showConnectConfigEditDialog(selectedItem);
            if (result) {
                connectConfigListView.getItems().set(selectedIndex, selectedItem);

                hostField.setText(selectedItem.getHost());
                portField.setText(String.valueOf(selectedItem.getPort()));

                // 重新编辑之后重置连接
                TcpDebugCache.INSTANCE.disconnectClient(selectedItem.getUid());
                setClientStatus(false);

                ConfigStorage.INSTANCE.set(selectedItem);
            }
        });
    }

    private boolean showConnectConfigEditDialog(ConnectConfig connectConfig) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/fxml/edit-connect-config-dialog.fxml"));
            DialogPane page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("添加配置");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);
            ConnectConfigDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setConfig(connectConfig);
            dialogStage.showAndWait();
            return controller.isOkClicked();
        } catch (IOException e) {
            log.error("", e);
        }
        return false;
    }

    private void initConnectBtn() {
        connectBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig selectedItem = selectSingleConfig();
            if (selectedItem == null) {
                return;
            }

            try {
                AbstractSocketClient client = TcpDebugCache.INSTANCE.getOrCreateClient(selectedItem, this);
                client.connect(null, 3000);
            } catch (Exception e) {
                log.error("连接异常", e);
            }
        });
    }

    private ConnectConfig selectSingleConfig() {
        ConnectConfig selectedItem = (ConnectConfig) connectConfigListView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText("请选择一个连接");
            alert.showAndWait();
        }
        return selectedItem;
    }

    @Override
    public void onClientEvent(ClientEvent event) {
        Platform.runLater(() -> {
            String clientId = event.getSource().toString();
            if (event instanceof ClientReadMessageEvent) {
                ClientReadMessageEvent msgEvent = (ClientReadMessageEvent) event;
                TcpDebugCache.INSTANCE.addLeftMsg(clientId, msgEvent.getMsg().toString());
                return;
            }

            if (event instanceof ClientInactiveEvent) {
                ConnectConfig selectedConfig = selectSingleConfig();
                if (selectedConfig != null && clientId.equals(selectedConfig.getUid())) {
                    setClientStatus(false);
                }
                TcpDebugCache.INSTANCE.setChatMsgBoxOffline(clientId);
                return;
            }

            if (event instanceof ClientOnlineEvent) {
                ConnectConfig selectedConfig = selectSingleConfig();
                if (selectedConfig != null && clientId.equals(selectedConfig.getUid())) {
                    setClientStatus(true);
                }
                TcpDebugCache.INSTANCE.setChatMsgBoxOnline(clientId);
                return;
            }

            if (event instanceof ClientExceptionEvent) {
                // TODO
                log.error("", ((ClientExceptionEvent) event).getCause());
            }
        });
    }

    private void initConnectConfigListView() {
        File file = ConfigStorage.INSTANCE.getConnectConfigFile();
        if (file != null && file.exists()) {
            ConfigStorage.INSTANCE.loadConnectConfigDataFromFile(file);
        }
        connectConfigListView.setCellFactory(param -> new ConnectConfigCell());
        connectConfigListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            // TODO 判断是否改变
            hostField.setText(((ConnectConfig) newValue).getHost());
            portField.setText(String.valueOf(((ConnectConfig) newValue).getPort()));

            // 填充聊天框
            String key = ((ConnectConfig) newValue).getUid();
            ChatMsgBox chatMsgBox = TcpDebugCache.INSTANCE.getOrCreateChatMsgBox(key);
            msgContentPane.setContent(chatMsgBox.getContentListView());
            statusText.setText(chatMsgBox.getStatusText().getText());
            statusText.setFill(chatMsgBox.getStatusText().getFill());
        });
        connectConfigListView.getItems().addAll(ConfigStorage.INSTANCE.getConnectConfigs());
    }

    private void setClientStatus(boolean online) {
        if (online) {
            statusText.setText("已连接");
            statusText.setFill(Paint.valueOf("green"));
        } else {
            statusText.setText("未连接");
            statusText.setFill(Paint.valueOf("red"));
        }
    }

}
