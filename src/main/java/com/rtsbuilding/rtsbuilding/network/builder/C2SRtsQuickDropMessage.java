package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsQuickDropMessage implements IMessage {

    private String itemId;
    private byte amount;
    private double dropX, dropY, dropZ;

    public C2SRtsQuickDropMessage() {}

    public C2SRtsQuickDropMessage(String itemId, byte amount, double dropX, double dropY, double dropZ) {
        this.itemId = itemId != null ? itemId : "";
        this.amount = amount;
        this.dropX = dropX;
        this.dropY = dropY;
        this.dropZ = dropZ;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf(buf, itemId, 128);
        buf.writeByte(amount);
        buf.writeDouble(dropX);
        buf.writeDouble(dropY);
        buf.writeDouble(dropZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        itemId = readUtf(buf, 128);
        amount = buf.readByte();
        dropX = buf.readDouble();
        dropY = buf.readDouble();
        dropZ = buf.readDouble();
    }

    public String getItemId() {
        return itemId;
    }

    public byte getAmount() {
        return amount;
    }

    public double getDropX() {
        return dropX;
    }

    public double getDropY() {
        return dropY;
    }

    public double getDropZ() {
        return dropZ;
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

    public static class Handler implements IMessageHandler<C2SRtsQuickDropMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsQuickDropMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsStorageSession session = RtsStorageManager.getSession(player);
            session.scanLinkedContainers(player.worldObj);

            long amount = m.amount & 0xFFL;
            if (RtsStorageManager.tryConsumeBlock(player, m.itemId, 0, amount)) {
                Item item = (Item) Item.itemRegistry.getObject(m.itemId);
                if (item != null) {
                    ItemStack stack = new ItemStack(item, (int) amount, 0);
                    EntityItem entityItem = new EntityItem(player.worldObj, m.dropX, m.dropY, m.dropZ, stack);
                    entityItem.delayBeforeCanPickup = 10;
                    player.worldObj.spawnEntityInWorld(entityItem);
                }
            }

            RtsStorageManager.sendStoragePage(player, 0, 0);
            return null;
        }
    }
}
