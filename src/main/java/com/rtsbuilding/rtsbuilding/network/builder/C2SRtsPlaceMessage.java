package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsPlaceMessage implements IMessage {

    private int clickedX, clickedY, clickedZ;
    private byte face;
    private double hitX, hitY, hitZ;
    private byte rotateSteps;
    private boolean forcePlace, skipIfOccupied;
    private String itemId;
    private ItemStack itemPrototype;
    private double rayOriginX, rayOriginY, rayOriginZ;
    private double rayDirX, rayDirY, rayDirZ;
    private boolean quickBuild;

    public C2SRtsPlaceMessage() {
        itemPrototype = null;
    }

    public C2SRtsPlaceMessage(int clickedX, int clickedY, int clickedZ, byte face, double hitX, double hitY,
        double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
        ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX,
        double rayDirY, double rayDirZ, boolean quickBuild) {
        this.clickedX = clickedX;
        this.clickedY = clickedY;
        this.clickedZ = clickedZ;
        this.face = face;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.rotateSteps = rotateSteps;
        this.forcePlace = forcePlace;
        this.skipIfOccupied = skipIfOccupied;
        this.itemId = itemId != null ? itemId : "";
        this.itemPrototype = itemPrototype != null ? itemPrototype.copy() : null;
        this.rayOriginX = rayOriginX;
        this.rayOriginY = rayOriginY;
        this.rayOriginZ = rayOriginZ;
        this.rayDirX = rayDirX;
        this.rayDirY = rayDirY;
        this.rayDirZ = rayDirZ;
        this.quickBuild = quickBuild;
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
        buf.writeByte(rotateSteps);
        buf.writeBoolean(forcePlace);
        buf.writeBoolean(skipIfOccupied);
        writeUtf(buf, itemId, 128);
        boolean hasItem = itemPrototype != null;
        buf.writeBoolean(hasItem);
        if (hasItem) ByteBufUtils.writeItemStack(buf, itemPrototype);
        buf.writeDouble(rayOriginX);
        buf.writeDouble(rayOriginY);
        buf.writeDouble(rayOriginZ);
        buf.writeDouble(rayDirX);
        buf.writeDouble(rayDirY);
        buf.writeDouble(rayDirZ);
        buf.writeBoolean(quickBuild);
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
        rotateSteps = buf.readByte();
        forcePlace = buf.readBoolean();
        skipIfOccupied = buf.readBoolean();
        itemId = readUtf(buf, 128);
        itemPrototype = buf.readBoolean() ? ByteBufUtils.readItemStack(buf) : null;
        rayOriginX = buf.readDouble();
        rayOriginY = buf.readDouble();
        rayOriginZ = buf.readDouble();
        rayDirX = buf.readDouble();
        rayDirY = buf.readDouble();
        rayDirZ = buf.readDouble();
        quickBuild = buf.readBoolean();
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

    public byte getRotateSteps() {
        return rotateSteps;
    }

    public boolean isForcePlace() {
        return forcePlace;
    }

    public boolean isSkipIfOccupied() {
        return skipIfOccupied;
    }

    public String getItemId() {
        return itemId;
    }

    public ItemStack getItemPrototype() {
        return itemPrototype;
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

    public boolean isQuickBuild() {
        return quickBuild;
    }

    // ---- Handler（阶段B：服务端方块放置） ----
    public static class Handler implements IMessageHandler<C2SRtsPlaceMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsPlaceMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // 前置检查：必须在 RTS 相机模式中
            if (!RtsCameraManager.isActive(player)) {
                RtsbuildingMod.LOGGER.debug("C2SRtsPlaceMessage: player {} not in RTS mode", player.getDisplayName());
                return null;
            }

            // 前置检查：必须在操作范围内
            if (!RtsCameraManager.isWithinActionRange(player, msg.clickedX, msg.clickedY, msg.clickedZ)) {
                RtsbuildingMod.LOGGER.debug(
                    "C2SRtsPlaceMessage: position ({}, {}, {}) out of range for {}",
                    msg.clickedX,
                    msg.clickedY,
                    msg.clickedZ,
                    player.getDisplayName());
                return null;
            }

            World world = player.worldObj;

            // skipIfOccupied：如果目标位置已有非空气方块，静默跳过
            if (msg.skipIfOccupied && !world.isAirBlock(msg.clickedX, msg.clickedY, msg.clickedZ)) {
                return null;
            }

            // 解析物品 → 方块
            Block block = null;
            int meta = 0;

            // 优先使用 itemPrototype（ItemStack），其次从 itemId 字符串解析
            ItemStack stack = msg.itemPrototype;
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                ItemBlock itemBlock = (ItemBlock) stack.getItem();
                block = itemBlock.field_150939_a; // Block field in ItemBlock
                meta = stack.getItemDamage();
            } else if (msg.itemId != null && !msg.itemId.isEmpty()) {
                // 从字符串解析（格式："minecraft:stone" 或 modid:blockname）
                String id = msg.itemId;
                if (id.contains(":")) {
                    // 去掉命名空间前缀（1.7.10 不需要）
                    id = id.substring(id.indexOf(':') + 1);
                }
                // 先尝试通过 Item 解析
                Item item = (Item) Item.itemRegistry.getObject(id);
                if (item instanceof ItemBlock) {
                    block = ((ItemBlock) item).field_150939_a;
                }
                if (block == null) {
                    // 尝试直接获取 Block
                    block = (Block) Block.blockRegistry.getObject(id);
                }
            }

            if (block == null) {
                RtsbuildingMod.LOGGER.debug("C2SRtsPlaceMessage: could not resolve block for itemId={}", msg.itemId);
                return null;
            }

            // 应用旋转（每次 90°）
            if (msg.rotateSteps != 0) {
                int rotation = (msg.rotateSteps & 0xFF) % 4;
                // 旋转映射：0→0, 1→1, 2→2, 3→3（对应 meta 值）
                meta = rotation;
            }

            // Step 1: 消耗物品（创造模式直接跳过）
            boolean hasLinkedStorage = RtsStorageManager.getSession(player)
                .isAnyLinked();
            boolean isCreative = player.capabilities.isCreativeMode;
            boolean consumed = false;
            net.minecraft.item.ItemStack consumedStack = null;
            boolean consumedFromInventory = false;

            if (isCreative) {
                consumed = true;
            } else if (hasLinkedStorage) {
                consumed = RtsStorageManager.tryConsumeBlock(player, msg.itemId, meta, 1);
            }

            if (!consumed) {
                net.minecraft.item.Item blockItem = net.minecraft.item.Item.getItemFromBlock(block);
                if (blockItem != null) {
                    for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                        net.minecraft.item.ItemStack invStack = player.inventory.mainInventory[i];
                        if (invStack != null && invStack.getItem() == blockItem && invStack.getItemDamage() == meta) {
                            consumedStack = invStack.copy();
                            invStack.stackSize--;
                            if (invStack.stackSize <= 0) {
                                player.inventory.mainInventory[i] = null;
                            }
                            consumed = true;
                            consumedFromInventory = true;
                            break;
                        }
                    }
                    if (consumed) {
                        player.inventoryContainer.detectAndSendChanges();
                    }
                }
            }

            if (!consumed) {
                RtsbuildingMod.LOGGER
                    .debug("C2SRtsPlaceMessage: insufficient {} in storage or inventory, placement denied", msg.itemId);
                return null;
            }

            // Step 2: 计算 metadata
            int actualMeta = block.onBlockPlaced(
                world,
                msg.clickedX,
                msg.clickedY,
                msg.clickedZ,
                msg.face,
                (float) msg.hitX,
                (float) msg.hitY,
                (float) msg.hitZ,
                meta);

            // Step 3: 执行放置
            boolean placed;
            if (msg.forcePlace || msg.isQuickBuild()) {
                placed = world.setBlock(msg.clickedX, msg.clickedY, msg.clickedZ, block, actualMeta, 3);
            } else {
                Block existing = world.getBlock(msg.clickedX, msg.clickedY, msg.clickedZ);
                if (existing == null || existing.isAir(world, msg.clickedX, msg.clickedY, msg.clickedZ)
                    || existing.getMaterial()
                        .isReplaceable()) {
                    placed = world.setBlock(msg.clickedX, msg.clickedY, msg.clickedZ, block, actualMeta, 3);
                } else {
                    int offsetX = msg.clickedX;
                    int offsetY = msg.clickedY;
                    int offsetZ = msg.clickedZ;
                    switch (msg.face) {
                        case 0:
                            offsetY--;
                            break;
                        case 1:
                            offsetY++;
                            break;
                        case 2:
                            offsetZ--;
                            break;
                        case 3:
                            offsetZ++;
                            break;
                        case 4:
                            offsetX--;
                            break;
                        case 5:
                            offsetX++;
                            break;
                    }
                    placed = world.setBlock(offsetX, offsetY, offsetZ, block, actualMeta, 3);
                }
            }

            // Step 4: 放置失败则回滚消耗（创造模式不需要）
            if (!placed) {
                if (consumedFromInventory) {
                    player.inventory.addItemStackToInventory(consumedStack);
                    player.inventoryContainer.detectAndSendChanges();
                } else if (!isCreative && hasLinkedStorage) {
                    RtsStorageManager.getSession(player)
                        .addItem(msg.itemId, meta, 1);
                }
                RtsbuildingMod.LOGGER.debug(
                    "C2SRtsPlaceMessage: placement failed for {} at ({}, {}, {}), rolled back consumption",
                    msg.itemId,
                    msg.clickedX,
                    msg.clickedY,
                    msg.clickedZ);
                return null;
            }

            // 放置成功
            world.playSoundEffect(
                msg.clickedX + 0.5,
                msg.clickedY + 0.5,
                msg.clickedZ + 0.5,
                block.stepSound.func_150496_b(),
                (block.stepSound.getVolume() + 1.0F) / 2.0F,
                block.stepSound.getPitch() * 0.8F);
            world.playAuxSFX(2005, msg.clickedX, msg.clickedY, msg.clickedZ, 0);
            RtsbuildingMod.LOGGER.debug(
                "C2SRtsPlaceMessage: placed {} at ({}, {}, {}) by {}",
                block.getUnlocalizedName(),
                msg.clickedX,
                msg.clickedY,
                msg.clickedZ,
                player.getDisplayName());

            // 问题4修复：放置成功后刷新存储页面
            if (consumed) {
                RtsStorageManager.sendStoragePage(player, 0, 0);
            }

            return null;
        }
    }
}
