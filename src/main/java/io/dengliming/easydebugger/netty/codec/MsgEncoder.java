package io.dengliming.easydebugger.netty.codec;

import io.dengliming.easydebugger.netty.SocketMessage;
import io.dengliming.easydebugger.netty.UdpMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

public class MsgEncoder extends MessageToMessageEncoder<SocketMessage> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, SocketMessage msg, List<Object> out) {
        if (msg instanceof UdpMessage) {
            UdpMessage udpMessage = (UdpMessage) msg;
            out.add(new DatagramPacket(Unpooled.wrappedBuffer(udpMessage.getMessage()),
                    udpMessage.getRecipient(), udpMessage.getSender()));
        } else {
            out.add(Unpooled.wrappedBuffer(msg.getMessage()));
        }
    }
}
