package io.dengliming.easydebugger.model;

import io.dengliming.easydebugger.netty.MsgType;

import java.util.UUID;

public class ConnectConfig {

    private String uid;
    private String host;
    private int port;
    private String name;
    private boolean repeatSend;

    //--------------重复发送使用属性start--------------
    private int sendInterval;
    private MsgType repeatSendMsgType;
    private String repeatSendMsg;
    //--------------重复发送使用属性end--------------


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

    public MsgType getRepeatSendMsgType() {
        return repeatSendMsgType;
    }

    public void setRepeatSendMsgType(MsgType repeatSendMsgType) {
        this.repeatSendMsgType = repeatSendMsgType;
    }

    public String getRepeatSendMsg() {
        return repeatSendMsg;
    }

    public void setRepeatSendMsg(String repeatSendMsg) {
        this.repeatSendMsg = repeatSendMsg;
    }
}
