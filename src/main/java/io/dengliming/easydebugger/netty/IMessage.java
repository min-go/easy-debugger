package io.dengliming.easydebugger.netty;

public interface IMessage {
    /**
     * 返回报文长度
     * @return
     */
    int length();

    /**
     * 返回报文字节
     * @return
     */
    byte[] getMessage();

    MsgType msgType();
}
