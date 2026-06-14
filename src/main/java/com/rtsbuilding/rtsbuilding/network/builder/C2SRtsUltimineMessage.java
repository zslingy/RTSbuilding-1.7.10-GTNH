package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.RtsMineManager;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsUltimineMessage implements IMessage {

    private int posX, posY, posZ;
    private byte face;
    private byte toolSlot;
    private String toolItemId;
    private ItemStack toolPrototype;
    private short limit;
    private byte mode;

    public C2SRtsUltimineMessage() {}

    public C2SRtsUltimineMessage(int posX, int posY, int posZ, byte face, byte toolSlot, String toolItemId,
        ItemStack toolPrototype, short limit, byte mode) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.face = face;
        this.toolSlot = toolSlot;
        this.toolItemId = toolItemId != null ? toolItemId : "";
        this.toolPrototype = toolPrototype != null ? toolPrototype.copy() : null;
        this.limit = limit;
        this.mode = mode;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(posX);
        buf.writeInt(posY);
        buf.writeInt(posZ);
        buf.writeByte(face);
        buf.writeByte(toolSlot);
        writeUtf(buf, toolItemId, 256);
        boolean hasTool = toolPrototype != null && toolPrototype.getItem() != null;
        buf.writeBoolean(hasTool);
        if (hasTool) ByteBufUtils.writeItemStack(buf, toolPrototype);
        buf.writeShort(limit);
        buf.writeByte(mode);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posX = buf.readInt();
        posY = buf.readInt();
        posZ = buf.readInt();
        face = buf.readByte();
        toolSlot = buf.readByte();
        toolItemId = readUtf(buf, 256);
        toolPrototype = buf.readBoolean() ? ByteBufUtils.readItemStack(buf) : null;
        limit = buf.readShort();
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

    public byte getToolSlot() {
        return toolSlot;
    }

    public String getToolItemId() {
        return toolItemId;
    }

    public ItemStack getToolPrototype() {
        return toolPrototype;
    }

    public short getLimit() {
        return limit;
    }

    public byte getMode() {
        return mode;
    }

    public static class Handler implements IMessageHandler<C2SRtsUltimineMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsUltimineMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            if (!RtsCameraManager.isActive(player)) return null;

            if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
                return null;
            }

            World world = player.worldObj;
            int seedX = msg.posX, seedY = msg.posY, seedZ = msg.posZ;

            if (!world.blockExists(seedX, seedY, seedZ)) return null;
            Block seedBlock = world.getBlock(seedX, seedY, seedZ);
            if (seedBlock == null || world.isAirBlock(seedX, seedY, seedZ)) return null;

            int clampedLimit = Math.min(Math.max(1, msg.limit), 256);
            int cap = Math.min(clampedLimit, RtsProgressionManager.getUltimineLimit(player));

            int slot = Math.max(0, Math.min(msg.toolSlot, 8));

            // 委托给 RtsMineManager 的渐进式连锁挖掘状态机
            RtsMineManager.startUltimine(
                player,
                seedX,
                seedY,
                seedZ,
                msg.face,
                slot,
                msg.toolItemId,
                msg.toolPrototype,
                cap,
                msg.mode);

            return null;
        }
    }
}
