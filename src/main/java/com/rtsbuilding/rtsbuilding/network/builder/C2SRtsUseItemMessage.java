package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 手持物品使用消息 — 用于 RTS 模式下非方块物品的右键使用。
 * 例如：食物、药水、工具等。
 */
public class C2SRtsUseItemMessage implements IMessage {

    private int clickedX, clickedY, clickedZ;
    private byte face;
    private double hitX, hitY, hitZ;

    public C2SRtsUseItemMessage() {}

    public C2SRtsUseItemMessage(int clickedX, int clickedY, int clickedZ, byte face, double hitX, double hitY,
        double hitZ) {
        this.clickedX = clickedX;
        this.clickedY = clickedY;
        this.clickedZ = clickedZ;
        this.face = face;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(clickedX);
        buf.writeInt(clickedY);
        buf.writeInt(clickedZ);
        buf.writeByte(face);
        buf.writeDouble(hitX);
        buf.writeDouble(hitY);
        buf.writeDouble(hitZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        clickedX = buf.readInt();
        clickedY = buf.readInt();
        clickedZ = buf.readInt();
        face = buf.readByte();
        hitX = buf.readDouble();
        hitY = buf.readDouble();
        hitZ = buf.readDouble();
    }

    public int getClickedX() {
        return clickedX;
    }

    public int getClickedY() {
        return clickedY;
    }

    public int getClickedZ() {
        return clickedZ;
    }

    public byte getFace() {
        return face;
    }

    public double getHitX() {
        return hitX;
    }

    public double getHitY() {
        return hitY;
    }

    public double getHitZ() {
        return hitZ;
    }

    public static class Handler implements IMessageHandler<C2SRtsUseItemMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsUseItemMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            if (!RtsCameraManager.isActive(player)) return null;

            World world = player.worldObj;
            if (world == null) return null;

            ItemStack held = player.getCurrentEquippedItem();
            if (held == null || held.getItem() == null) return null;

            try {
                float localHitX = (float) (msg.getHitX() - msg.getClickedX());
                float localHitY = (float) (msg.getHitY() - msg.getClickedY());
                float localHitZ = (float) (msg.getHitZ() - msg.getClickedZ());
                // 优先调用 onItemUse（工具对方块交互：斧头去皮、锄头耕地等）
                boolean used = held.getItem()
                    .onItemUse(
                        held,
                        player,
                        world,
                        msg.getClickedX(),
                        msg.getClickedY(),
                        msg.getClickedZ(),
                        msg.getFace(),
                        localHitX,
                        localHitY,
                        localHitZ);

                if (!used) {
                    // 回退到 onItemRightClick（食物、药水等消耗品）
                    ItemStack result = held.getItem()
                        .onItemRightClick(held, world, player);
                    if (result != null) {
                        player.inventory.mainInventory[player.inventory.currentItem] = result;
                        held = result;
                    }
                }

                if (held.stackSize <= 0) {
                    player.inventory.mainInventory[player.inventory.currentItem] = null;
                } else if (held.stackSize > held.getMaxStackSize() && !held.isItemStackDamageable()) {
                    held.stackSize = held.getMaxStackSize();
                }

                player.inventoryContainer.detectAndSendChanges();

                RtsbuildingMod.LOGGER.debug(
                    "C2SRtsUseItemMessage: {} used item {} at ({}, {}, {}) used={}",
                    player.getDisplayName(),
                    held.getDisplayName(),
                    msg.getClickedX(),
                    msg.getClickedY(),
                    msg.getClickedZ(),
                    used);
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.warn(
                    "C2SRtsUseItemMessage: failed to use item for {}: {}",
                    player.getDisplayName(),
                    e.getMessage());
            }

            return null;
        }
    }
}
