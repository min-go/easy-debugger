package io.dengliming.easydebugger.model;

import io.dengliming.easydebugger.utils.DateUtils;
import io.dengliming.easydebugger.utils.SceneUtils;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.time.LocalDateTime;


public class ChatMsgBox {

    private String clientId;
    private Text statusText;

    private ListView<Object> contentListView;

    public ChatMsgBox() {
        this.contentListView = new ListView<>();
        StackPane stackPane = new StackPane();
        Label emptyLabel = new Label("暂无数据...");
        emptyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        stackPane.getChildren().add(emptyLabel);
        this.contentListView.setPlaceholder(stackPane);
        this.statusText = new Text("未连接");
        this.statusText.setFill(Paint.valueOf("red"));
    }

    public ChatMsgBox addLeftMsg(String message) {
        Platform.runLater(() -> contentListView.getItems().add(buildMessage(message, DateUtils.format(LocalDateTime.now()), true)));
        return this;
    }

    public ChatMsgBox addRightMsg(String message) {
        Platform.runLater(() -> contentListView.getItems().add(buildMessage(message, DateUtils.format(LocalDateTime.now()), false)));
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
        content.setPrefWidth(SceneUtils.getWidth(message));
        content.setFont(new Font(13));
        Label time1 = new Label();
        time1.setText(timestamp);
        time1.setFont(new Font(10));
        VBox vBox = new VBox(content, time1);
        vBox.setSpacing(5);
        if (left) {
            content.setAlignment(Pos.CENTER_LEFT);
            vBox.setAlignment(Pos.CENTER_LEFT);
        } else {
            content.setAlignment(Pos.CENTER_RIGHT);
            vBox.setAlignment(Pos.CENTER_RIGHT);
        }

        vBox.setFillWidth(true);
        return vBox;
    }

    public Text getStatusText() {
        return statusText;
    }

    private void setStatus(String text, Paint v) {
        this.statusText.setText(text);
        this.statusText.setFill(v);
    }

    public void onOffline() {
        setStatus("未连接", Paint.valueOf("red"));
    }

    public void onLine() {
        setStatus("已连接", Paint.valueOf("green"));
    }

    public ListView<Object> getContentListView() {
        return contentListView;
    }

    public void setContentListView(ListView<Object> contentListView) {
        this.contentListView = contentListView;
    }
}
