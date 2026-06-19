package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class S2CRtsResumePlacementScanMessage implements IMessage {

    private int count;
    private int[] posX, posY, posZ;
    private String[] blockId;
    private int[] meta;

    public S2CRtsResumePlacementScanMessage() {}

    public S2CRtsResumePlacementScanMessage(int count, int[] posX, int[] posY, int[] posZ, String[] blockId,
        int[] meta) {
        this.count = count;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.blockId = blockId;
        this.meta = meta;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeInt(posX[i]);
            buf.writeInt(posY[i]);
            buf.writeInt(posZ[i]);
            writeUtf(buf, blockId[i], 128);
            buf.writeInt(meta[i]);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        count = buf.readInt();
        posX = new int[count];
        posY = new int[count];
        posZ = new int[count];
        blockId = new String[count];
        meta = new int[count];
        for (int i = 0; i < count; i++) {
            posX[i] = buf.readInt();
            posY[i] = buf.readInt();
            posZ[i] = buf.readInt();
            blockId[i] = readUtf(buf, 128);
            meta[i] = buf.readInt();
        }
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

    public int getCount() {
        return count;
    }

    public int[] getPosX() {
        return posX;
    }

    public int[] getPosY() {
        return posY;
    }

    public int[] getPosZ() {
        return posZ;
    }

    public String[] getBlockId() {
        return blockId;
    }

    public int[] getMeta() {
        return meta;
    }

    public static class Handler implements IMessageHandler<S2CRtsResumePlacementScanMessage, IMessage> {

        @Override
        public IMessage onMessage(S2CRtsResumePlacementScanMessage m, MessageContext ctx) {
            RtsClientState.get().interaction.pendingPlacementCount = m.count;
            return null;
        }
    }
}
