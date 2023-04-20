package io.dengliming.easydebugger.netty.client;

import io.dengliming.easydebugger.model.ChatMsgBox;

public class ClientDebuggerView {

    private final ChatMsgBox chatMsgBox;

    public ClientDebuggerView() {
        this.chatMsgBox = new ChatMsgBox();
    }

    public ChatMsgBox getChatMsgBox() {
        return chatMsgBox;
    }

    public void online() {
        chatMsgBox.setOnline(true);
    }

    public void offline() {
        chatMsgBox.setOnline(false);
    }
}
