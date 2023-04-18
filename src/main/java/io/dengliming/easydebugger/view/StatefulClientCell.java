package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.model.ClientItem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;

/**
 * 连接客户端（需要显示状态的）
 */
public class StatefulClientCell extends TableCell<ClientItem, ClientItem> {

    private final Label label;
    private final Circle circle;
    private final HBox hBox;

    public StatefulClientCell() {
        label = new Label();
        label.setWrapText(true);
        circle = new Circle(5);
        circle.setFill(Paint.valueOf("darkgrey"));
        circle.setStroke(Paint.valueOf("#0000"));
        hBox = new HBox();
        hBox.getChildren().add(circle);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().add(label);
        hBox.setSpacing(5);
        hBox.setPadding(new Insets(5));
    }

    @Override
    protected void updateItem(ClientItem item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
        } else {
            label.setText(item.getClientName());
            if (item.isConnected()) {
                circle.setFill(Paint.valueOf("green"));
            } else {
                circle.setFill(Paint.valueOf("darkgrey"));
            }
            setGraphic(hBox);
        }
    }

}
