package io.dengliming.easydebugger.utils;

import javafx.scene.control.Alert;

public final class Alerts {

    public static void showWarning(String header, String content) {
        showAndWait(Alert.AlertType.WARNING, header, content);
    }

    public static void showError(String header, String content) {
        showAndWait(Alert.AlertType.ERROR, header, content);
    }

    public static void showError(String header, Throwable cause) {
        String content = null;
        if (cause != null) {
            content = cause.getMessage();
        }
        showAndWait(Alert.AlertType.ERROR, header, content);
    }

    private static void showAndWait(Alert.AlertType alertType, String header, String content) {
        Alert alert = new Alert(alertType);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

}
