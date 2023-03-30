package io.dengliming.easydebugger.utils;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

public enum Tooltips {
    INSTANCE;

    private Node ownerNode;

    public void setOwnerNode(Node ownerNode) {
        this.ownerNode = ownerNode;
    }

    public void show(Node ownerNode, String msg) {
        Tooltip tooltip = new Tooltip(msg);

        // 设置提示信息的样式和位置
        tooltip.setStyle("-fx-font-size: 14px; -fx-background-color: #ffffff; -fx-text-fill: #000000; -fx-padding: 10px; -fx-alignment: center;");
        tooltip.setAutoHide(true);
        Bounds bounds = SceneUtils.getScreenBounds(ownerNode);
        double x = bounds.getMinX() + (bounds.getWidth() - tooltip.getWidth()) / 2;
        double y = bounds.getMinY() + 50;

        tooltip.setShowDuration(Duration.seconds(3));

        tooltip.show(ownerNode, x, y);
    }

    public void show(String msg) {
        show(ownerNode, msg);
    }
}
