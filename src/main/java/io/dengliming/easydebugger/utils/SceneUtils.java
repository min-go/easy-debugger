package io.dengliming.easydebugger.utils;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;

public class SceneUtils {
    public static Bounds getScreenBounds(Node node) {
        Scene scene = node.getScene();
        return getScreenBounds(scene);
    }

    public static Bounds getScreenBounds(Scene scene) {
        return getScreenBounds(scene.getWindow());
    }

    public static Bounds getScreenBounds(javafx.stage.Window window) {
        Bounds bounds = window.getScene().getRoot().getBoundsInLocal();
        return window.getScene().getRoot().localToScreen(bounds);
    }

    public static double getWidth(String msg) {
        int len = msg.length();
        double width = 0;
        for (int i = 0; i < len; i++) {
            if (isChinese(msg.charAt(i))) {
                width += 16;
            } else {
                width += 16;
            }
        }

        width += 22; // 补全前后空格

        if (width > 450) {
            return 450;
        }

        return width < 50 ? 50 : width;
    }

    private static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
