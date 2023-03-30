package io.dengliming.easydebugger.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

public class MsgEncoder extends MessageToMessageEncoder<IMessage> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, IMessage msg, List<Object> out) {
        out.add(Unpooled.wrappedBuffer(msg.getMessage()));
    }
}
