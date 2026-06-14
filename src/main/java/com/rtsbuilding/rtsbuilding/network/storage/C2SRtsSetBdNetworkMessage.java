package com.rtsbuilding.rtsbuilding.network.storage;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsSetBdNetworkMessage implements IMessage {

    private String channel;
    private byte mode;

    public C2SRtsSetBdNetworkMessage() {}

    public C2SRtsSetBdNetworkMessage(String c, byte m) {
        channel = c != null ? c : "";
        mode = m;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf(buf, channel, 64);
        buf.writeByte(mode);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        channel = readUtf(buf, 64);
        mode = buf.readByte();
    }

    private static void writeUtf(ByteBuf b, String s, int max) {
        if (s == null) s = "";
        byte[] d = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int l = Math.min(d.length, max);
        b.writeInt(l);
        b.writeBytes(d, 0, l);
    }

    private static String readUtf(ByteBuf b, int max) {
        int l = Math.max(0, Math.min(b.readInt(), max));
        byte[] d = new byte[l];
        if (l > 0) b.readBytes(d);
        return new String(d, java.nio.charset.StandardCharsets.UTF_8);
    }

    public String getChannel() {
        return channel;
    }

    public byte getMode() {
        return mode;
    }

    public static class Handler implements IMessageHandler<C2SRtsSetBdNetworkMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSetBdNetworkMessage m, MessageContext c) {
            return null;
        }
    }
}
