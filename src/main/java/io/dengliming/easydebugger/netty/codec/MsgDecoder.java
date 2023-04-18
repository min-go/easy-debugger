package io.dengliming.easydebugger.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public class MsgDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
        byte[] req = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(req);
        list.add(new String(req));
    }

}
