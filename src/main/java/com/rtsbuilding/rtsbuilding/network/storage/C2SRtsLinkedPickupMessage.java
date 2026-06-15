package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 从链接存储拾取物品到玩家背包。
 * Bug1修复：实现 Handler，支持从 AE2 或 IInventory 容器提取物品到玩家背包。
 */
public class C2SRtsLinkedPickupMessage implements IMessage {

    private int slot, mouseButton;

    public C2SRtsLinkedPickupMessage() {}

    public C2SRtsLinkedPickupMessage(int s, int b) {
        slot = s;
        mouseButton = b;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
        buf.writeInt(mouseButton);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
        mouseButton = buf.readInt();
    }

    public int getSlot() {
        return slot;
    }

    public int getMouseButton() {
        return mouseButton;
    }

    public static class Handler implements IMessageHandler<C2SRtsLinkedPickupMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsLinkedPickupMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null || player.worldObj == null) return null;

            RtsStorageSession session = RtsStorageManager.getSession(player);

            // 从左键 (0) 触发：从链接存储提取物品到玩家背包
            if (m.getMouseButton() == 0) {
                if (session.isAe2Linked() && RtsAe2Compat.isAvailable()) {
                    TileEntity te = player.worldObj
                        .getTileEntity(session.getLinkedX(), session.getLinkedY(), session.getLinkedZ());
                    if (te != null) {
                        // 从 AE2 中提取匹配的物品到背包
                        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                            ItemStack hotbarStack = player.inventory.mainInventory[hotbarSlot];
                            if (hotbarStack == null || hotbarStack.getItem() == null) continue;
                            String itemId = net.minecraft.item.Item.itemRegistry
                                .getNameForObject(hotbarStack.getItem());
                            if (itemId == null) continue;
                            long extracted = RtsAe2Compat.extractItem(
                                te,
                                ForgeDirection.UNKNOWN,
                                itemId,
                                hotbarStack.getItemDamage(),
                                hotbarStack.getMaxStackSize() - hotbarStack.stackSize);
                            if (extracted > 0) {
                                hotbarStack.stackSize += (int) extracted;
                                player.inventory.markDirty();
                                session.invalidateAe2Cache();
                                RtsbuildingMod.LOGGER.debug(
                                    "LinkedPickup: extracted {} {} to {} hotbar[{}]",
                                    extracted,
                                    itemId,
                                    player.getDisplayName(),
                                    hotbarSlot);
                                break;
                            }
                        }
                    }
                } else if (session.isContainerLinked()) {
                    boolean picked = false;
                    for (com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef ref : session
                        .getLinkedStorages()) {
                        TileEntity te = player.worldObj.getTileEntity(ref.x, ref.y, ref.z);
                        if (te instanceof net.minecraft.inventory.IInventory) {
                            net.minecraft.inventory.IInventory inv = (net.minecraft.inventory.IInventory) te;
                            for (int ci = 0; ci < inv.getSizeInventory() && ci < 256; ci++) {
                                ItemStack containerStack = inv.getStackInSlot(ci);
                                if (containerStack == null || containerStack.getItem() == null) continue;
                                ItemStack containerPicked = containerStack.splitStack(1);
                                if (player.inventory.addItemStackToInventory(containerPicked)) {
                                    if (containerStack.stackSize <= 0) {
                                        inv.setInventorySlotContents(ci, null);
                                    }
                                    inv.markDirty();
                                    player.inventory.markDirty();
                                    picked = true;
                                    break;
                                } else {
                                    containerStack.stackSize++;
                                }
                            }
                            if (picked) break;
                        }
                    }
                }
                return null;
            }

            return null;
        }
    }
}
