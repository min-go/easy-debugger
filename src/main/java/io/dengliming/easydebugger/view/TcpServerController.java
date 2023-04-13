package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.constant.CommonConstant;
import io.dengliming.easydebugger.constant.ConnectType;
import io.dengliming.easydebugger.model.*;
import io.dengliming.easydebugger.netty.IMessage;
import io.dengliming.easydebugger.netty.MessageFactory;
import io.dengliming.easydebugger.netty.MsgType;
import io.dengliming.easydebugger.netty.event.*;
import io.dengliming.easydebugger.utils.Alerts;
import io.dengliming.easydebugger.utils.ConfigStorage;
import io.dengliming.easydebugger.utils.SocketDebuggerCache;
import io.dengliming.easydebugger.utils.T;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

@Slf4j
public class TcpServerController implements IGenericEventListener<ChannelEvent>, Initializable {

    @FXML
    private ListView connectConfigListView;
    @FXML
    private Button deleteBtn;
    @FXML
    private Button addBtn;
    @FXML
    private Text serverName;
    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private Button editBtn;
    @FXML
    private Button listenBtn;
    @FXML
    private Text listenStatusText;
    @FXML
    private Button clearBtn;
    @FXML
    private ScrollPane msgContentPane;
    @FXML
    private TableView<ClientItem> clientList;
    @FXML
    private TableColumn<ClientItem, ClientItem> clientNameColumn;
    @FXML
    private TextField sendMsgField;
    @FXML
    private Button sendMsgBtn;
    @FXML
    private RadioButton stringMsgOption;
    @FXML
    private RadioButton hexMsgOption;
    private ToggleGroup group;
    @FXML
    private Circle listenStatusCircle;

    @FXML
    private CheckBox groupSendCheckBox;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initConnectConfigListView();

        initAddBtn();

        initDeleteBtn();

        initEditBtn();

        initClearBtn();

        initToggleGroup();

        initSendMsgBtn();

        initClientListTableView();

        initListenBtn();
    }

    private void initConnectConfigListView() {
        connectConfigListView.setCellFactory(param -> new ConnectConfigCell());
        connectConfigListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                hostField.clear();
                portField.clear();
                serverName.setText("--");
                setListenStatus(false);
                return;
            }
            hostField.setText(((ConnectConfig) newValue).getHost());
            portField.setText(String.valueOf(((ConnectConfig) newValue).getPort()));
            serverName.setText(((ConnectConfig) newValue).getName());

            //
            ServerDebugger serverDebugger = SocketDebuggerCache.INSTANCE.getOrCreateServer((ConnectConfig) newValue, this);
            clientList.setItems(serverDebugger.getClientList());
            msgContentPane.setContent(null);
            setListenStatus(serverDebugger.isListenStatus());
            if (serverDebugger.getSelectedClient() != null) {
                for (int i = 0; i < clientList.getItems().size(); i++) {
                    if (serverDebugger.getSelectedClient().getClientName().equals(clientList.getItems().get(i).getClientName())) {
                        clientList.getSelectionModel().select(i);

                        msgContentPane.setContent(serverDebugger.getOrCreateChatMsgBox(clientList.getItems().get(i).getClientName()).getContentListView());
                    }
                }
            }
            clientList.refresh();
        });
        connectConfigListView.getItems().addAll(ConfigStorage.INSTANCE.getConnectConfigs(ConnectType.TCP_SERVER));
        connectConfigListView.getSelectionModel().select(0);
        connectConfigListView.setStyle(CommonConstant.SELECTION_STYLE);
    }

    private void initAddBtn() {
        addBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig tmpConfig = new ConnectConfig();
            tmpConfig.setConnectType(ConnectType.TCP_SERVER);
            tmpConfig.setHost(T.getLocalHostIp());
            tmpConfig.setPort(CommonConstant.DEFAULT_PORT);
            boolean isOk = showConnectConfigEditDialog(tmpConfig);
            if (isOk) {
                connectConfigListView.getItems().add(tmpConfig);
                ConfigStorage.INSTANCE.add(tmpConfig);
            }
        });
    }


    private void initDeleteBtn() {
        deleteBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ObservableList selectedItems = connectConfigListView.getSelectionModel().getSelectedItems();
            if (selectedItems == null || selectedItems.size() == 0) {
                Alerts.showWarning("请选择要删除的连接！", null);
                return;
            }

            List<String> configList = new ArrayList<>(selectedItems.size());
            for (Object selectedItem : selectedItems) {
                configList.add(((ConnectConfig) selectedItem).getUid());
            }
            connectConfigListView.getItems().removeAll(selectedItems);
            ConfigStorage.INSTANCE.removeAll(configList);
        });
    }

    private void initEditBtn() {
        editBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig selectedItem = (ConnectConfig) connectConfigListView.getSelectionModel().getSelectedItem();
            if (selectedItem == null) {
                Alerts.showWarning("请选择要操作的配置！", null);
                return;
            }
            int selectedIndex = connectConfigListView.getSelectionModel().getSelectedIndex();

            boolean isOk = showConnectConfigEditDialog(selectedItem);
            if (isOk) {
                connectConfigListView.getItems().set(selectedIndex, selectedItem);
                connectConfigListView.getSelectionModel().select(selectedIndex);

                hostField.setText(selectedItem.getHost());
                portField.setText(String.valueOf(selectedItem.getPort()));

                ConfigStorage.INSTANCE.set(selectedItem);
            }
        });
    }

    private void initListenBtn() {
        listenBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ConnectConfig selectedItem = (ConnectConfig) connectConfigListView.getSelectionModel().getSelectedItem();
            if (selectedItem == null) {
                Alerts.showWarning("请选择要操作的配置！", null);
                return;
            }

            // 开始监听
            ServerDebugger serverDebugger = SocketDebuggerCache.INSTANCE.getOrCreateServer(selectedItem, this);
            if (listenBtn.getText().equals(CommonConstant.CLOSE)) {
                serverDebugger.setListenStatus(false);
                serverDebugger.destroy();
                Platform.runLater(() -> setListenStatus(false));
            } else {
                serverDebugger.start().addListener(future -> {
                    if (future.isSuccess()) {
                        Platform.runLater(() -> setListenStatus(true));
                        serverDebugger.setListenStatus(true);
                    } else {
                        Platform.runLater(() -> Alerts.showError("监听异常！", future.cause() != null ? future.cause().getMessage() : null));
                        log.error("监听失败", future.cause());
                    }
                });
            }
        });
    }

    private boolean showConnectConfigEditDialog(ConnectConfig connectConfig) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/fxml/tcp-server-config-dialog.fxml"));
            DialogPane page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("添加配置");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);
            TcpServerConfigDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setConfig(connectConfig);
            dialogStage.showAndWait();
            return controller.isOkClicked();
        } catch (IOException e) {
            log.error("", e);
        }
        return false;
    }

    private void initClearBtn() {
        clearBtn.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> ((ListView) (msgContentPane.getContent())).getItems().clear());
    }

    private void initToggleGroup() {
        group = new ToggleGroup();
        stringMsgOption.setToggleGroup(group);
        hexMsgOption.setToggleGroup(group);
        stringMsgOption.setSelected(true);
    }

    private void initSendMsgBtn() {
        sendMsgBtn.setOnAction(event -> {
            // 发送消息
            String message = sendMsgField.getText();
            if (!T.hasLength(message)) {
                return;
            }

            ConnectConfig selectedItem = (ConnectConfig) connectConfigListView.getSelectionModel().getSelectedItem();
            if (selectedItem == null) {
                Alerts.showWarning("请选择要操作的配置！", null);
                return;
            }

            ClientItem clientItem = clientList.getSelectionModel().getSelectedItem();
            // 群发
            if (groupSendCheckBox.isSelected()) {
                clientList.getItems().forEach(it -> {
                    sendMsg(selectedItem.getUid(), it.getClientName(), hexMsgOption.isSelected() ? MsgType.HEX : MsgType.STRING, sendMsgField.getText());
                });
            } else {
                if (clientItem != null) {
                    sendMsg(selectedItem.getUid(), clientItem.getClientName(), hexMsgOption.isSelected() ? MsgType.HEX : MsgType.STRING, sendMsgField.getText());
                }
            }

            sendMsgField.clear();
        });
    }

    private void initClientListTableView() {
        clientNameColumn.setCellFactory(col -> new ClientCell());
        clientNameColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        clientList.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection == null) {
                return;
            }

            ConnectConfig config = ConfigStorage.INSTANCE.getConnectConfig(newSelection.getServerId());
            ServerDebugger serverDebugger = SocketDebuggerCache.INSTANCE.getOrCreateServer(config, this);
            ChatMsgBox chatMsgBox = serverDebugger.getOrCreateChatMsgBox(newSelection.getClientName());
            msgContentPane.setContent(chatMsgBox.getContentListView());
            serverDebugger.setSelectedClient(newSelection);
        });

        // 设置选中第一个元素
        clientList.getSelectionModel().select(0);
        clientList.setStyle(CommonConstant.SELECTION_STYLE);
        clientList.refresh();
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
                SocketDebuggerCache.INSTANCE.getOrCreateServer(serverConfig, this).addLeftMsg(clientId, msgEvent.getMsg().toString());

                // 自动回复消息
                if (serverConfig.isAutoReply() && T.hasLength(serverConfig.getSendMsg())) {
                    sendMsg(serverConfig.getUid(), clientId, serverConfig.getSendMsgType(), serverConfig.getSendMsg());
                }
                return;
            }

            if (event instanceof ClientInactiveEvent) {
                Optional<ClientItem> clientItem = clientList
                        .getItems()
                        .stream()
                        .filter(it -> it.getClientName().equals(clientId))
                        .findFirst();
                if (!clientItem.isPresent()) {
                    clientList.getItems().add(new ClientItem(((ClientInactiveEvent) event).getServerId(), clientId, false));
                } else {
                    clientItem.get().setConnected(false);
                }
                clientList.refresh();
                return;
            }

            if (event instanceof ClientOnlineEvent) {
                Optional<ClientItem> clientItem = clientList
                        .getItems()
                        .stream()
                        .filter(it -> it.getClientName().equals(clientId))
                        .findFirst();
                if (!clientItem.isPresent()) {
                    clientList.getItems().add(new ClientItem(((ClientOnlineEvent) event).getServerId(), clientId, true));
                } else {
                    clientItem.get().setConnected(true);
                }

                // 如何当前还没有选中默认选中一个
                if (clientList.getItems().size() > 0 && clientList.getSelectionModel().getSelectedIndex() < 0) {
                    clientList.getSelectionModel().select(0);
                }
                clientList.refresh();
                return;
            }

            if (event instanceof ExceptionEvent) {
                // TODO
                log.error("", ((ExceptionEvent) event).getCause());
            }
        });
    }

    private void setListenStatus(boolean online) {
        if (online) {
            listenStatusCircle.setFill(CommonConstant.GREEN_PAINT);
            listenStatusText.setText(CommonConstant.LISTENING);
            listenBtn.setText(CommonConstant.CLOSE);
        } else {
            listenStatusCircle.setFill(CommonConstant.DARKGREY_PAINT);
            listenStatusText.setText(CommonConstant.NO_LISTEN);
            listenBtn.setText(CommonConstant.START_LISTEN);
        }
    }

    // 发送消息
    private void sendMsg(String serverId, String clientId, MsgType msgType, String sendMsg) {
        try {
            ConnectConfig config = ConfigStorage.INSTANCE.getConnectConfig(serverId);
            if (config == null) {
                return;
            }
            // 获取当前客户端会话
            ClientSession clientSession = SessionHolder.INSTANCE.get(clientId);
            if (clientSession != null) {
                IMessage msg = MessageFactory.createMessage(msgType, sendMsg);
                clientSession.getChannel().writeAndFlush(msg).addListener(future -> {
                    if (!future.isSuccess()) {
                        return;
                    }
                    SocketDebuggerCache.INSTANCE
                            .getOrCreateServer(config, this)
                            .getOrCreateChatMsgBox(clientId)
                            .addRightMsg(sendMsg);
                });
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }
}
