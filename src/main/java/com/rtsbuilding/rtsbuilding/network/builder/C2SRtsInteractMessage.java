package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsInteractMessage implements IMessage {

    private int entityId;
    private int clickedX, clickedY, clickedZ;
    private byte face;
    private double hitX, hitY, hitZ;
    private byte sourceType, toolSlot;
    private String itemId;
    private double rayOriginX, rayOriginY, rayOriginZ;
    private double rayDirX, rayDirY, rayDirZ;
    public static final byte SOURCE_TOOL_SLOT = 0;
    public static final byte SOURCE_PIN_ITEM = 1;
    public static final int NO_ENTITY = -1;

    public C2SRtsInteractMessage() {}

    public C2SRtsInteractMessage(int entityId, int clickedX, int clickedY, int clickedZ, byte face, double hitX,
        double hitY, double hitZ, byte sourceType, byte toolSlot, String itemId, double rayOriginX, double rayOriginY,
        double rayOriginZ, double rayDirX, double rayDirY, double rayDirZ) {
        this.entityId = entityId;
        this.clickedX = clickedX;
        this.clickedY = clickedY;
        this.clickedZ = clickedZ;
        this.face = face;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.sourceType = sourceType;
        this.toolSlot = toolSlot;
        this.itemId = itemId != null ? itemId : "";
        this.rayOriginX = rayOriginX;
        this.rayOriginY = rayOriginY;
        this.rayOriginZ = rayOriginZ;
        this.rayDirX = rayDirX;
        this.rayDirY = rayDirY;
        this.rayDirZ = rayDirZ;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(clickedX);
        buf.writeInt(clickedY);
        buf.writeInt(clickedZ);
        buf.writeByte(face);
        buf.writeDouble(hitX);
        buf.writeDouble(hitY);
        buf.writeDouble(hitZ);
        buf.writeByte(sourceType);
        buf.writeByte(toolSlot);
        writeUtf(buf, itemId, 128);
        buf.writeDouble(rayOriginX);
        buf.writeDouble(rayOriginY);
        buf.writeDouble(rayOriginZ);
        buf.writeDouble(rayDirX);
        buf.writeDouble(rayDirY);
        buf.writeDouble(rayDirZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        clickedX = buf.readInt();
        clickedY = buf.readInt();
        clickedZ = buf.readInt();
        face = buf.readByte();
        hitX = buf.readDouble();
        hitY = buf.readDouble();
        hitZ = buf.readDouble();
        sourceType = buf.readByte();
        toolSlot = buf.readByte();
        itemId = readUtf(buf, 128);
        rayOriginX = buf.readDouble();
        rayOriginY = buf.readDouble();
        rayOriginZ = buf.readDouble();
        rayDirX = buf.readDouble();
        rayDirY = buf.readDouble();
        rayDirZ = buf.readDouble();
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

    public int getEntityId() {
        return entityId;
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

    public byte getSourceType() {
        return sourceType;
    }

    public byte getToolSlot() {
        return toolSlot;
    }

    public String getItemId() {
        return itemId;
    }

    public double getRayOriginX() {
        return rayOriginX;
    }

    public double getRayOriginY() {
        return rayOriginY;
    }

    public double getRayOriginZ() {
        return rayOriginZ;
    }

    public double getRayDirX() {
        return rayDirX;
    }

    public double getRayDirY() {
        return rayDirY;
    }

    public double getRayDirZ() {
        return rayDirZ;
    }

    /**
     * 对标原版 RtsStorageManager.interactTarget()：
     * - 空手右键 → 尝试打开目标方块 GUI（箱子、熔炉、发射器等）
     * - 支持 IInventory 容器（displayGUIChest）
     * - 也尝试 block.onBlockActivated 触发其他交互（门、拉杆等）
     */
    public static class Handler implements IMessageHandler<C2SRtsInteractMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsInteractMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            World world = player.worldObj;
            if (world == null) return null;

            int x = msg.getClickedX();
            int y = msg.getClickedY();
            int z = msg.getClickedZ();
            byte face = msg.getFace();

            if (!world.blockExists(x, y, z)) {
                RtsbuildingMod.LOGGER.debug("C2SRtsInteractMessage: target block not loaded at ({}, {}, {})", x, y, z);
                return null;
            }

            TileEntity te = world.getTileEntity(x, y, z);

            // 优先级1：IInventory 容器 → 直接打开 GUI
            if (te instanceof IInventory) {
                IInventory inv = (IInventory) te;
                // 检查是否有自定义的 GuiHandler
                if (inv instanceof net.minecraft.tileentity.TileEntityChest) {
                    // 箱子：使用 displayGUIChest（支持双箱子）
                    player.displayGUIChest(inv);
                    RtsbuildingMod.LOGGER.debug(
                        "C2SRtsInteractMessage: {} opened chest at ({}, {}, {})",
                        player.getDisplayName(),
                        x,
                        y,
                        z);
                } else if (inv instanceof net.minecraft.tileentity.TileEntityFurnace) {
                    // 熔炉：打开熔炉 GUI (vanilla guiId = 0)
                    player.openGui(
                        RtsbuildingMod.instance,
                        1, // furnace GUI id
                        world,
                        x,
                        y,
                        z);
                    RtsbuildingMod.LOGGER.debug(
                        "C2SRtsInteractMessage: {} opened furnace at ({}, {}, {})",
                        player.getDisplayName(),
                        x,
                        y,
                        z);
                } else {
                    // 其他 IInventory（漏斗、发射器、投掷器、酿造台等）→ 通用 chest GUI
                    player.displayGUIChest(inv);
                    RtsbuildingMod.LOGGER.debug(
                        "C2SRtsInteractMessage: {} opened inventory at ({}, {}, {}) class={}",
                        player.getDisplayName(),
                        x,
                        y,
                        z,
                        te.getClass()
                            .getSimpleName());
                }
                return null;
            }

            // 优先级2：尝试 block.onBlockActivated（门、拉杆、按钮、工作台等）
            Block block = world.getBlock(x, y, z);
            if (block != null && !block.isAir(world, x, y, z)) {
                try {
                    boolean activated = block.onBlockActivated(
                        world,
                        x,
                        y,
                        z,
                        player,
                        face,
                        (float) msg.getHitX(),
                        (float) msg.getHitY(),
                        (float) msg.getHitZ());
                    if (activated) {
                        RtsbuildingMod.LOGGER.debug(
                            "C2SRtsInteractMessage: {} activated block {} at ({}, {}, {})",
                            player.getDisplayName(),
                            block.getLocalizedName(),
                            x,
                            y,
                            z);
                    }
                } catch (Exception e) {
                    RtsbuildingMod.LOGGER.warn(
                        "C2SRtsInteractMessage: block.onBlockActivated failed at ({}, {}, {}): {}",
                        x,
                        y,
                        z,
                        e.getMessage());
                }
            }

            // 优先级3：实体交互（如果消息中有 entityId）
            if (msg.getEntityId() != NO_ENTITY && msg.getEntityId() >= 0) {
                net.minecraft.entity.Entity entity = world.getEntityByID(msg.getEntityId());
                if (entity != null && entity.isEntityAlive()) {
                    try {
                        boolean interacted = player.interactWith(entity);
                        if (interacted) {
                            RtsbuildingMod.LOGGER.debug(
                                "C2SRtsInteractMessage: {} interacted with entity {} at ({}, {}, {})",
                                player.getDisplayName(),
                                entity.getClass()
                                    .getSimpleName(),
                                x,
                                y,
                                z);
                        }
                    } catch (Exception e) {
                        RtsbuildingMod.LOGGER
                            .warn("C2SRtsInteractMessage: player.interactWith failed: {}", e.getMessage());
                    }
                }
            }

            return null;
        }
    }
}
