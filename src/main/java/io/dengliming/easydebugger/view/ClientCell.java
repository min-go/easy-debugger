package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.model.ClientItem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;

public class ClientCell extends TableCell<ClientItem, ClientItem> {

    private final Label label = new Label();
    private final Circle circle = new Circle(5);
    private final HBox hBox = new HBox();

    public ClientCell() {
        circle.setFill(Paint.valueOf("darkgrey"));
        circle.setStroke(Paint.valueOf("#0000"));
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(circle, label);
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
            label.setWrapText(true);
            if (item.isConnected()) {
                circle.setFill(Paint.valueOf("green"));
            } else {
                circle.setFill(Paint.valueOf("darkgrey"));
            }
            setGraphic(hBox);
        }
    }

}
