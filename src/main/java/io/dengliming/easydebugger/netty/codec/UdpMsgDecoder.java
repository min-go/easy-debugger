package io.dengliming.easydebugger.netty.codec;

import io.dengliming.easydebugger.netty.UdpMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

public class UdpMsgDecoder extends SimpleChannelInboundHandler<DatagramPacket> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket in) throws Exception {
        ByteBuf content = in.content();
        final int readableBytes = content.readableBytes();
        final byte[] message = new byte[readableBytes];
        content.readBytes(message);
        UdpMessage socketMessage = new UdpMessage(message);
        socketMessage.setSender(in.sender());
        socketMessage.setRecipient(in.recipient());
        ctx.fireChannelRead(socketMessage);
    }
}
