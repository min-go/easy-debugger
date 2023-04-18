package io.dengliming.easydebugger.view;

import io.dengliming.easydebugger.model.ConnectConfig;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

/**
 * 表示ListView中的单行（连接配置）
 */
public class ConnectConfigCell extends ListCell<ConnectConfig> {

    private final Label titleLabel;
    private final Label subTitleLabel;
    private final VBox vBox;

    public ConnectConfigCell() {
        titleLabel = new Label();
        titleLabel.setFont(new Font(16));
        titleLabel.setWrapText(true);
        subTitleLabel = new Label();
        subTitleLabel.setFont(new Font(10));
        vBox = new VBox(titleLabel, subTitleLabel);
        vBox.setSpacing(5);
    }

    @Override
    protected void updateItem(ConnectConfig item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            titleLabel.setText(item.getName());
            subTitleLabel.setText(item.getHost() + ":" + item.getPort());
            setGraphic(vBox);
        }
    }
}
