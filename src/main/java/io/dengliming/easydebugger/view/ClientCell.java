package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.model.ClientItem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;

/**
 * 连接客户端（无状态的），UDP服务端
 */
public class ClientCell extends TableCell<ClientItem, ClientItem> {

    private final Label label;
    private final HBox hBox;

    public ClientCell() {
        label = new Label();
        label.setWrapText(true);
        hBox = new HBox();
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
            setGraphic(hBox);
        }
    }

}
