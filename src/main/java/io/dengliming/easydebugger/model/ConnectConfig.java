package io.dengliming.easydebugger.model;

import io.dengliming.easydebugger.constant.ConnectType;
import io.dengliming.easydebugger.constant.MsgType;

import java.util.UUID;

/**
 * 连接配置
 */
public class ConnectConfig {

    /**
     * 配置的唯一标识
     */
    private String uid;
    /**
     * 主机
     */
    private String host;
    /**
     * 端口
     */
    private int port;
    /**
     * 名称或备注
     */
    private String name;
    /**
     * 是否重复发送（客户端使用）
     */
    private boolean repeatSend;
    /**
     * 是否自动回复（服务端使用）
     */
    private boolean autoReply;

    /**
     * 连接类型
     */
    private ConnectType connectType = ConnectType.TCP_CLIENT;

    /**
     * 发送间隔（配合重复发送使用）
     */
    private int sendInterval;
    /**
     * 发送消息类型
     */
    private MsgType sendMsgType;
    /**
     * 发送消息
     */
    private String sendMsg;

    public ConnectConfig() {
        this.uid = UUID.randomUUID().toString().replaceAll("-", "");
    }

    public ConnectConfig(String host, int port) {
        this();
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUid() {
        return uid;
    }

    public boolean isRepeatSend() {
        return repeatSend;
    }

    public void setRepeatSend(boolean repeatSend) {
        this.repeatSend = repeatSend;
    }

    public int getSendInterval() {
        return sendInterval;
    }

    public void setSendInterval(int sendInterval) {
        this.sendInterval = sendInterval;
    }

    public MsgType getSendMsgType() {
        return sendMsgType;
    }

    public void setSendMsgType(MsgType sendMsgType) {
        this.sendMsgType = sendMsgType;
    }

    public String getSendMsg() {
        return sendMsg;
    }

    public void setSendMsg(String sendMsg) {
        this.sendMsg = sendMsg;
    }

    public ConnectType getConnectType() {
        return connectType;
    }

    public void setConnectType(ConnectType connectType) {
        this.connectType = connectType;
    }

    public boolean isAutoReply() {
        return autoReply;
    }

    public void setAutoReply(boolean autoReply) {
        this.autoReply = autoReply;
    }
}
