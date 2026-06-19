package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsLinkedQuickMoveMessage implements IMessage {

    private int slot, mode;

    public C2SRtsLinkedQuickMoveMessage() {}

    public C2SRtsLinkedQuickMoveMessage(int s, int m) {
        slot = s;
        mode = m;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        buf.writeInt(mode);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        mode = buf.readInt();
    }

    public int getSlot() {
        return slot;
    }

    public int getMode() {
        return mode;
    }

    public static class Handler implements IMessageHandler<C2SRtsLinkedQuickMoveMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsLinkedQuickMoveMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;
            RtsStorageSession session = RtsStorageManager.getSession(player);
            session.scanLinkedContainers(player.worldObj);
            RtsStorageSession.PageResult result = session.queryPage("name_asc", 0, 88);
            if (m.slot >= 0 && m.slot < result.items.size()) {
                RtsStorageSession.StorageEntry entry = result.items.get(m.slot);
                if (entry != null && entry.itemId != null) {
                    int amount = m.mode == 0 ? 1 : (int) Math.min(entry.count, 64);
                    if (RtsStorageManager.tryConsumeBlock(player, entry.itemId, entry.meta, amount)) {
                        net.minecraft.item.Item item = (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry
                            .getObject(entry.itemId);
                        if (item != null) {
                            net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(
                                item,
                                amount,
                                entry.meta);
                            player.inventory.addItemStackToInventory(stack);
                            player.inventoryContainer.detectAndSendChanges();
                        }
                    }
                }
            }
            RtsStorageManager.sendStoragePage(player, 0, 0);
            return null;
        }
    }
}
