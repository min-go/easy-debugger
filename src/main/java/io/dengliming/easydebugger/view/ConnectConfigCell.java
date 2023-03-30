package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.model.ConnectConfig;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public class ConnectConfigCell extends ListCell<ConnectConfig> {

    public ConnectConfigCell() {
        super();
    }

    @Override
    protected void updateItem(ConnectConfig item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            Label titleLabel = new Label();
            titleLabel.setText(item.getName());
            titleLabel.setFont(new Font(16));
            titleLabel.setWrapText(true);
            Label subTitleLabel = new Label();
            subTitleLabel.setText(item.getHost() + ":" + item.getPort());
            subTitleLabel.setFont(new Font(10));
            VBox vBox = new VBox(titleLabel, subTitleLabel);
            vBox.setSpacing(5);
            setGraphic(vBox);
        }
    }
}
