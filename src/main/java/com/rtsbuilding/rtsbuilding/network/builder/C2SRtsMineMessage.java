package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsMineMessage implements IMessage {

    private int posX, posY, posZ;
    private byte face;
    private boolean start;
    private byte toolSlot;
    private String toolItemId;
    private ItemStack toolPrototype;
    private boolean allowPlacedBlockRecovery;

    public C2SRtsMineMessage() {}

    public C2SRtsMineMessage(int posX, int posY, int posZ, byte face, boolean start, byte toolSlot, String toolItemId,
        ItemStack toolPrototype, boolean allowPlacedBlockRecovery) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.face = face;
        this.start = start;
        this.toolSlot = toolSlot;
        this.toolItemId = toolItemId != null ? toolItemId : "";
        this.toolPrototype = toolPrototype != null ? toolPrototype.copy() : null;
        this.allowPlacedBlockRecovery = allowPlacedBlockRecovery;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(posX);
        buf.writeInt(posY);
        buf.writeInt(posZ);
        buf.writeByte(face);
        buf.writeBoolean(start);
        buf.writeByte(toolSlot);
        writeUtf(buf, toolItemId, 256);
        boolean hasTool = toolPrototype != null && toolPrototype.getItem() != null;
        buf.writeBoolean(hasTool);
        if (hasTool) ByteBufUtils.writeItemStack(buf, toolPrototype);
        buf.writeBoolean(allowPlacedBlockRecovery);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posX = buf.readInt();
        posY = buf.readInt();
        posZ = buf.readInt();
        face = buf.readByte();
        start = buf.readBoolean();
        toolSlot = buf.readByte();
        toolItemId = readUtf(buf, 256);
        toolPrototype = buf.readBoolean() ? ByteBufUtils.readItemStack(buf) : null;
        allowPlacedBlockRecovery = buf.readBoolean();
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

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public int getPosZ() {
        return posZ;
    }

    public byte getFace() {
        return face;
    }

    public boolean isStart() {
        return start;
    }

    public byte getToolSlot() {
        return toolSlot;
    }

    public String getToolItemId() {
        return toolItemId;
    }

    public ItemStack getToolPrototype() {
        return toolPrototype;
    }

    public boolean isAllowPlacedBlockRecovery() {
        return allowPlacedBlockRecovery;
    }

    public static class Handler implements IMessageHandler<C2SRtsMineMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsMineMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            if (msg.isStart()) {
                // 从玩家背包获取工具原型（客户端发送的 toolPrototype 可能为 null）
                ItemStack tool = msg.getToolPrototype();
                if (tool == null && msg.getToolSlot() >= 0 && msg.getToolSlot() < 9) {
                    tool = player.inventory.mainInventory[msg.getToolSlot()];
                }
                if (tool != null && tool.getItem() != null) {
                    tool = tool.copy();
                }

                com.rtsbuilding.rtsbuilding.server.RtsMineManager.startMining(
                    player,
                    msg.getPosX(),
                    msg.getPosY(),
                    msg.getPosZ(),
                    msg.getFace(),
                    msg.getToolSlot(),
                    msg.getToolItemId(),
                    tool,
                    msg.isAllowPlacedBlockRecovery());
            } else {
                com.rtsbuilding.rtsbuilding.server.RtsMineManager.abortMining(player);
            }
            return null;
        }
    }
}
