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

public class C2SRtsUseItemMessage implements IMessage {

    private int clickedX, clickedY, clickedZ;
    private byte face;
    private double hitX, hitY, hitZ;
    private byte toolSlot;
    private String itemId;

    public C2SRtsUseItemMessage() {}

    public C2SRtsUseItemMessage(int clickedX, int clickedY, int clickedZ, byte face, double hitX, double hitY,
        double hitZ) {
        this(clickedX, clickedY, clickedZ, face, hitX, hitY, hitZ, (byte) -1);
    }

    public C2SRtsUseItemMessage(int clickedX, int clickedY, int clickedZ, byte face, double hitX, double hitY,
        double hitZ, byte toolSlot) {
        this(clickedX, clickedY, clickedZ, face, hitX, hitY, hitZ, toolSlot, null);
    }

    public C2SRtsUseItemMessage(int clickedX, int clickedY, int clickedZ, byte face, double hitX, double hitY,
        double hitZ, byte toolSlot, String itemId) {
        this.clickedX = clickedX;
        this.clickedY = clickedY;
        this.clickedZ = clickedZ;
        this.face = face;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.toolSlot = toolSlot;
        this.itemId = itemId;
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
        buf.writeByte(toolSlot);
        buf.writeBoolean(itemId != null);
        if (itemId != null) {
            byte[] data = itemId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int len = Math.min(data.length, 128);
            buf.writeShort(len);
            buf.writeBytes(data, 0, len);
        }
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
        if (buf.readableBytes() >= 1) {
            toolSlot = buf.readByte();
        } else {
            toolSlot = -1;
        }
        if (buf.readableBytes() >= 1 && buf.readBoolean()) {
            int len = Math.min(buf.readShort(), 128);
            byte[] data = new byte[len];
            buf.readBytes(data);
            itemId = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            itemId = null;
        }
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

    public byte getToolSlot() {
        return toolSlot;
    }

    public String getItemId() {
        return itemId;
    }

    public static class Handler implements IMessageHandler<C2SRtsUseItemMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsUseItemMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            if (!RtsCameraManager.isActive(player)) return null;

            World world = player.worldObj;
            if (world == null) return null;

            int savedSlot = player.inventory.currentItem;
            double savedPosX = player.posX;
            double savedPosY = player.posY;
            double savedPosZ = player.posZ;
            float savedYaw = player.rotationYaw;
            float savedPitch = player.rotationPitch;

            try {
                String itemId = msg.getItemId();
                byte toolSlot = msg.getToolSlot();

                if (itemId != null && !itemId.isEmpty() && !"minecraft:air".equals(itemId)) {
                    ItemStack useStack = resolveItemStack(itemId);
                    if (useStack == null) return null;
                    player.inventory.currentItem = 0;
                    player.inventory.mainInventory[0] = useStack.copy();
                } else if (toolSlot >= 0 && toolSlot < 9) {
                    player.inventory.currentItem = toolSlot;
                }

                ItemStack held = player.getCurrentEquippedItem();
                if (held == null || held.getItem() == null) return null;

                double nx = msg.getHitX();
                double ny = msg.getHitY();
                double nz = msg.getHitZ();
                switch (msg.getFace()) {
                    case 0:
                        ny += 2.2D;
                        break;
                    case 1:
                        ny -= 2.2D;
                        break;
                    case 2:
                        nz += 2.2D;
                        break;
                    case 3:
                        nz -= 2.2D;
                        break;
                    case 4:
                        nx += 2.2D;
                        break;
                    case 5:
                        nx -= 2.2D;
                        break;
                }
                ny += 1.1D;

                player.setPosition(nx, ny, nz);

                double hitX = msg.getHitX();
                double hitY = msg.getHitY();
                double hitZ = msg.getHitZ();
                double dx2 = hitX - nx;
                double dy2 = (hitY + player.yOffset) - (ny + player.yOffset);
                double dz2 = hitZ - nz;
                double xzDist = Math.sqrt(dx2 * dx2 + dz2 * dz2);
                player.rotationYaw = (float) (Math.atan2(-dx2, dz2) * 180.0D / Math.PI);
                player.rotationPitch = (float) (-(Math.atan2(dy2, xzDist) * 180.0D / Math.PI));

                float localHitX = (float) (msg.getHitX() - msg.getClickedX());
                float localHitY = (float) (msg.getHitY() - msg.getClickedY());
                float localHitZ = (float) (msg.getHitZ() - msg.getClickedZ());

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
                    ItemStack result = held.getItem()
                        .onItemRightClick(held, world, player);
                    if (result != null) {
                        player.inventory.mainInventory[player.inventory.currentItem] = result;
                    }
                } else if (player.capabilities.isCreativeMode) {
                    player.inventory.mainInventory[player.inventory.currentItem] = held;
                }

                held = player.inventory.mainInventory[player.inventory.currentItem];
                if (!player.capabilities.isCreativeMode && held != null && held.stackSize <= 0) {
                    player.inventory.mainInventory[player.inventory.currentItem] = null;
                }

                player.inventoryContainer.detectAndSendChanges();

                RtsbuildingMod.LOGGER.debug(
                    "C2SRtsUseItemMessage: {} used item {} at ({}, {}, {}) used={}",
                    player.getDisplayName(),
                    held != null ? held.getDisplayName() : "(none)",
                    msg.getClickedX(),
                    msg.getClickedY(),
                    msg.getClickedZ(),
                    used);
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.warn(
                    "C2SRtsUseItemMessage: failed to use item for {}: {}",
                    player.getDisplayName(),
                    e.getMessage());
            } finally {
                if (msg.getItemId() != null && !msg.getItemId()
                    .isEmpty()) {
                    ItemStack remaining = player.inventory.mainInventory[0];
                    if (remaining != null && remaining.stackSize > 0) {
                        if (!player.inventory.addItemStackToInventory(remaining)) {
                            player.dropPlayerItemWithRandomChoice(remaining, false);
                        }
                    }
                    player.inventory.mainInventory[0] = null;
                }
                player.inventory.currentItem = savedSlot;
                player.setPositionAndRotation(savedPosX, savedPosY, savedPosZ, savedYaw, savedPitch);
                player.inventoryContainer.detectAndSendChanges();
            }
            return null;
        }

        private static ItemStack resolveItemStack(String itemId) {
            if (itemId == null || itemId.isEmpty()) return null;
            String lookupId = itemId;
            if (lookupId.startsWith("minecraft:")) {
                lookupId = lookupId.substring("minecraft:".length());
            }
            net.minecraft.item.Item item = (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry
                .getObject(lookupId);
            if (item == null) {
                item = (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry.getObject(itemId);
            }
            if (item == null) return null;
            return new ItemStack(item, 1, 0);
        }
    }
}
