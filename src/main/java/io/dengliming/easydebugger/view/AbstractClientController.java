package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.constant.CommonConstant;
import io.dengliming.easydebugger.constant.ConnectType;
import io.dengliming.easydebugger.model.ChatMsgBox;
import io.dengliming.easydebugger.model.ClientDebugger;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.netty.MsgType;
import io.dengliming.easydebugger.netty.client.TcpDebuggerClient;
import io.dengliming.easydebugger.netty.event.*;
import io.dengliming.easydebugger.utils.Alerts;
import io.dengliming.easydebugger.utils.ConfigStorage;
import io.dengliming.easydebugger.utils.SocketDebuggerCache;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@Slf4j
public abstract class AbstractClientController implements IGenericEventListener<ChannelEvent>, Initializable {
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
    private RadioButton stringMsgOption;
    @FXML
    private RadioButton hexMsgOption;
    @FXML
    private ScrollPane msgContentPane;
    @FXML
    private Text clientName;
    private ToggleGroup group;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initConnectConfigListView();

        initAddBtn();

        initEditBtn();

        initDeleteBtn();

        initClearBtn();

        initSendMsgBtn();

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

            ConnectConfig selectedItem = getSelectedConnectConfig();
            if (selectedItem == null) {
                Alerts.showWarning("请选择一个连接", null);
                return;
            }

            if (!verifyConnectStatus()) {
                return;
            }

            try {
                ClientDebugger clientDebugger = SocketDebuggerCache.INSTANCE.getOrCreateClient(selectedItem, this);
                ((TcpDebuggerClient) (clientDebugger.getClient())).sendMsg(hexMsgOption.isSelected() ? MsgType.HEX : MsgType.STRING, message);
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
                Alerts.showWarning("请选择要删除的连接！", null);
                return;
            }

            List<String> clientList = new ArrayList<>(selectedItems.size());
            for (Object selectedItem : selectedItems) {
                clientList.add(((ConnectConfig) selectedItem).getUid());
            }
            connectConfigListView.getItems().removeAll(selectedItems);
            ConfigStorage.INSTANCE.removeAll(clientList);
            clientList.forEach(SocketDebuggerCache.INSTANCE::removeClientCache);
        });
    }

    private void initAddBtn() {
        addBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig tmpConfig = new ConnectConfig();
            tmpConfig.setConnectType(connectType());
            boolean isOk = showConnectConfigEditDialog(tmpConfig);
            if (isOk) {
                connectConfigListView.getItems().add(tmpConfig);
                ConfigStorage.INSTANCE.add(tmpConfig);
            }
        });
    }

    private void initEditBtn() {
        editBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig selectedItem = getSelectedConnectConfig();
            if (selectedItem == null) {
                Alerts.showWarning("请选择一个连接", null);
                return;
            }
            int selectedIndex = connectConfigListView.getSelectionModel().getSelectedIndex();

            boolean isOk = showConnectConfigEditDialog(selectedItem);
            if (isOk) {
                connectConfigListView.getItems().set(selectedIndex, selectedItem);
                // 这里因为修改元素之后会自动取消选中，这里重新选中
                connectConfigListView.getSelectionModel().select(selectedIndex);

                hostField.setText(selectedItem.getHost());
                portField.setText(String.valueOf(selectedItem.getPort()));

                // 重新编辑之后重置连接
                ClientDebugger clientDebugger = SocketDebuggerCache.INSTANCE.getClientDebugger(selectedItem.getUid());
                if (clientDebugger != null) {
                    clientDebugger.disconnectClient();
                }
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

    @Override
    public void onEvent(ChannelEvent event) {
        Platform.runLater(() -> {
            String clientId = event.getSource().toString();
            ClientDebugger clientDebugger = SocketDebuggerCache.INSTANCE.getClientDebugger(clientId);
            if (event instanceof ClientReadMessageEvent) {
                ClientReadMessageEvent msgEvent = (ClientReadMessageEvent) event;
                if (clientDebugger != null) {
                    clientDebugger.addLeftMsg(msgEvent.getMsg().toString());
                }
                return;
            }

            if (event instanceof ClientInactiveEvent) {
                ConnectConfig selectedConfig = getSelectedConnectConfig();
                if (selectedConfig != null && clientId.equals(selectedConfig.getUid())) {
                    setClientStatus(false);
                }
                if (clientDebugger != null) {
                    clientDebugger.offline();
                }
                return;
            }

            if (event instanceof ClientOnlineEvent) {
                ConnectConfig selectedConfig = getSelectedConnectConfig();
                if (selectedConfig != null && clientId.equals(selectedConfig.getUid())) {
                    setClientStatus(true);
                }

                if (clientDebugger != null) {
                    clientDebugger.online();
                }
                return;
            }

            if (event instanceof ExceptionEvent) {
                // TODO
                log.error("", ((ExceptionEvent) event).getCause());
            }
        });
    }

    private void initConnectConfigListView() {
        connectConfigListView.setCellFactory(param -> new ConnectConfigCell());
        connectConfigListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                hostField.clear();
                portField.clear();
                clientName.setText("--");
                setClientStatus(false);
                return;
            }

            hostField.setText(((ConnectConfig) newValue).getHost());
            portField.setText(String.valueOf(((ConnectConfig) newValue).getPort()));
            clientName.setText(((ConnectConfig) newValue).getName());

            // 填充聊天框
            ChatMsgBox chatMsgBox = SocketDebuggerCache.INSTANCE
                    .getOrCreateClient((ConnectConfig) newValue, this)
                    .getChatMsgBox();
            msgContentPane.setContent(chatMsgBox.getContentListView());
            setClientStatus(chatMsgBox.isOnline());
        });
        connectConfigListView.getItems().addAll(ConfigStorage.INSTANCE.getConnectConfigs(connectType()));
        connectConfigListView.getSelectionModel().select(0);
        connectConfigListView.setStyle(CommonConstant.SELECTION_STYLE);
    }

    protected ConnectConfig getSelectedConnectConfig() {
        return (ConnectConfig) connectConfigListView.getSelectionModel().getSelectedItem();
    }

    protected boolean verifyConnectStatus() {
        return true;
    }

    protected void setClientStatus(boolean online) {}

    protected abstract ConnectType connectType();
}
