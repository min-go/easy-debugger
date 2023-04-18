package io.dengliming.easydebugger.constant;

import javafx.scene.paint.Paint;

/**
 * 公用常量
 */
public interface CommonConstant {
    String DIS_CONNECT_TEXT = "断开连接";
    String CONNECT_TEXT = "连接";
    String CONNECTED_TEXT = "已连接";
    String UNCONNECTED = "未连接";
    int DEFAULT_PORT = 8080;

    Paint GREEN_PAINT = Paint.valueOf("green");
    Paint DARKGREY_PAINT = Paint.valueOf("darkgrey");
    String LISTENING = "监听中";
    String CLOSE = "关闭";
    String NO_LISTEN = "未监听";
    String START_LISTEN = "开始监听";

    /**
     * 服务端解码器
     */
    String SERVER_DECODER_HANDLER = "ServerProtocolDecoder";

    /**
     * 服务端编码器
     */
    String SERVER_ENCODER_HANDLER = "ServerProtocolEncoder";

    String SERVER_SERVICE_HANDLER = "ServerServiceHandler";

    String CLIENT_PROTOCOL_DECODER = "ClientProtocolDecoder";

    String CLIENT_PROTOCOL_ENCODER = "ClientProtocolEncoder";

    String CLIENT_SERVICE_HANDLER = "ClientServiceHandler";

    String SELECTION_STYLE = "-fx-selection-bar: #d8d8d8; -fx-selection-bar-non-focused: #d8d8d8;";
}
