package io.dengliming.easydebugger.model;

import io.dengliming.easydebugger.utils.T;
import io.dengliming.easydebugger.view.NoSelectionModel;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.time.LocalDateTime;

/**
 * 服务端与客户端通信的聊天框
 */
public class ChatMsgBox {

    private static final Font CONTENT_FONT = new Font(13);
    private static final Font TIME_FONT = new Font(10);

    private boolean online;

    private ListView<Object> contentListView;

    public ChatMsgBox() {
        this.contentListView = new ListView<>();
        StackPane stackPane = new StackPane();
        Label emptyLabel = new Label("暂无数据...");
        emptyLabel.setStyle("-fx-text-fill: gray;");
        stackPane.getChildren().add(emptyLabel);
        this.contentListView.setPlaceholder(stackPane);
        this.contentListView.setSelectionModel(new NoSelectionModel<>());
    }

    public ChatMsgBox addLeftMsg(String message) {
        Platform.runLater(() -> contentListView.getItems().add(buildMessage(message, T.format(LocalDateTime.now()), true)));
        return this;
    }

    public ChatMsgBox addRightMsg(String message) {
        Platform.runLater(() -> contentListView.getItems().add(buildMessage(message, T.format(LocalDateTime.now()), false)));
        return this;
    }

    public ChatMsgBox clear() {
        Platform.runLater(() -> contentListView.getItems().clear());
        return this;
    }

    private VBox buildMessage(String message, String timestamp, boolean left) {
        Label content = new Label();
        content.setText(message);
        content.setWrapText(true);
        content.setPrefHeight(Region.USE_COMPUTED_SIZE);
        content.setFont(CONTENT_FONT);
        content.setStyle("-fx-background-color: #f0f0f0;-fx-background-radius: 10px;");
        content.setPadding(new Insets(5));

        // 计算字符宽度
        Text text = new Text(message);
        text.setFont(CONTENT_FONT);
        // 这里加上内边距
        double width = text.getLayoutBounds().getWidth() + 10;

        // 设置Label的最大宽度
        content.setMaxWidth(width > 450 ? 450 : width);

        Label time = new Label();
        time.setText(timestamp);
        time.setFont(TIME_FONT);
        time.setWrapText(true);
        VBox vBox = new VBox(content, time);
        vBox.setSpacing(5);

        if (left) {
            content.setAlignment(Pos.CENTER_LEFT);
            vBox.setAlignment(Pos.CENTER_LEFT);
        } else {
            content.setAlignment(Pos.CENTER_RIGHT);
            vBox.setAlignment(Pos.CENTER_RIGHT);
        }

        vBox.maxWidthProperty().bind(contentListView.widthProperty());
        vBox.setFillWidth(true);
        return vBox;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public ListView<Object> getContentListView() {
        return contentListView;
    }

    public void setContentListView(ListView<Object> contentListView) {
        this.contentListView = contentListView;
    }

}
