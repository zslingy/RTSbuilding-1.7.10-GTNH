package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
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
     * - 临时切换玩家手持物品到 RTS 选中的工具槽位
     * - 支持 IInventory 容器（displayGUIChest）
     * - 支持大箱子合并（检查相邻箱子）
     * - 尝试 block.onBlockActivated / itemOnUse 触发工具交互
     * - 实体交互时临时移动玩家到目标附近
     * - Bug11修复：支持 itemId 非空时从存储系统提取物品使用（水桶等非方块物品）
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

            // 临时保存玩家状态
            int savedSlot = player.inventory.currentItem;
            ItemStack savedSlot0Item = player.inventory.mainInventory[0];
            double savedPosX = player.posX;
            double savedPosY = player.posY;
            double savedPosZ = player.posZ;
            float savedYaw = player.rotationYaw;
            float savedPitch = player.rotationPitch;

            // Bug11修复：标记是否从 itemId 注入了物品到 slot 0，用于 finally 中回收
            boolean injectedItemId = false;

            try {
                // Bug11修复: 如果 itemId 非空（如水桶），从 itemId 创建 ItemStack 临时放入 slot 0
                String itemId = msg.getItemId();
                if (itemId != null && !itemId.isEmpty() && !"minecraft:air".equals(itemId)) {
                    ItemStack resolved = resolveItemStack(itemId);
                    if (resolved != null) {
                        player.inventory.currentItem = 0;
                        player.inventory.mainInventory[0] = resolved.copy();
                        injectedItemId = true;
                    }
                } else if (msg.getSourceType() == SOURCE_TOOL_SLOT) {
                    // 临时切换手持物品到 RTS 工具槽位
                    int toolSlot = msg.getToolSlot();
                    if (toolSlot >= 0 && toolSlot < 9) {
                        player.inventory.currentItem = toolSlot;
                    }
                }

                // 计算目标命中点和临时传送位置
                double hitX = msg.getHitX();
                double hitY = msg.getHitY();
                double hitZ = msg.getHitZ();

                // 实体交互：对齐原版 resolveInteractionPosition(entity)
                // 传送到实体中心反方向 1.8 格 + 向上 0.2 格
                if (msg.getEntityId() != NO_ENTITY && msg.getEntityId() >= 0) {
                    net.minecraft.entity.Entity entity = world.getEntityByID(msg.getEntityId());
                    if (entity != null && entity.isEntityAlive()) {
                        // 计算实体包围盒中心
                        double entCX = (entity.boundingBox.minX + entity.boundingBox.maxX) / 2.0D;
                        double entCY = (entity.boundingBox.minY + entity.boundingBox.maxY) / 2.0D;
                        double entCZ = (entity.boundingBox.minZ + entity.boundingBox.maxZ) / 2.0D;

                        // 从命中点到实体中心的方向
                        double dx = entCX - hitX;
                        double dy = entCY - hitY;
                        double dz = entCZ - hitZ;
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist < 1.0e-6D) {
                            dx = 0;
                            dy = 0;
                            dz = 1;
                            dist = 1;
                        }

                        // 传送到实体中心反方向 1.8 格 + 向上 0.2 格
                        double px = entCX - (dx / dist) * 1.8D;
                        double py = entCY + 0.2D;
                        double pz = entCZ - (dz / dist) * 1.8D;
                        player.setPosition(px, py, pz);

                        // 朝向实体
                        double toEX = entCX - px;
                        double toEY = entCY - py;
                        double toEZ = entCZ - pz;
                        double toDist = Math.sqrt(toEX * toEX + toEZ * toEZ);
                        player.rotationYaw = (float) (Math.atan2(-toEX, toEZ) * 180.0D / Math.PI);
                        player.rotationPitch = (float) (-(Math.atan2(toEY, toDist) * 180.0D / Math.PI));

                        // 三级 fallback（对齐原版 interactEntityWithMainHand）：
                        // ① player.interactWith(entity) — 标准实体交互（剪羊毛、骑马等）
                        // 1.7.10 中 interactWith 内部会调用 entity.interact(player)
                        // 和 item.itemInteractionForEntity()
                        boolean interacted = player.interactWith(entity);

                        // ② 尝试物品的 onItemRightClick（桶右键实体等）
                        if (!interacted) {
                            net.minecraft.item.ItemStack held = player.getCurrentEquippedItem();
                            if (held != null && held.getItem() != null) {
                                net.minecraft.item.ItemStack result = held.getItem()
                                    .onItemRightClick(held, world, player);
                                if (result != null) {
                                    player.inventory.mainInventory[player.inventory.currentItem] = result;
                                    interacted = true;
                                }
                            }
                        }

                        if (interacted) {
                            RtsbuildingMod.LOGGER.debug(
                                "C2SRtsInteractMessage: {} interacted with entity {}",
                                player.getDisplayName(),
                                entity.getClass()
                                    .getSimpleName());
                        }

                        return null;
                    }
                }

                // 方块交互：计算临时传送位置（对齐原版 resolveInteractionPosition(blockHit)）
                // hitX/Y/Z 是射线与方块面的精确交点
                // 位置 = hitPoint - faceNormal * 2.2, 然后 y += 1.1（眼睛高度偏移）
                double nx = hitX;
                double ny = hitY;
                double nz = hitZ;
                switch (face) {
                    case 0:
                        ny += 2.2D;
                        break; // 底面法线 (0,-1,0), subtract → +2.2
                    case 1:
                        ny -= 2.2D;
                        break; // 顶面法线 (0,1,0), subtract → -2.2
                    case 2:
                        nz += 2.2D;
                        break; // 北面法线 (0,0,-1), subtract → +2.2
                    case 3:
                        nz -= 2.2D;
                        break; // 南面法线 (0,0,1), subtract → -2.2
                    case 4:
                        nx += 2.2D;
                        break; // 西面法线 (-1,0,0), subtract → +2.2
                    case 5:
                        nx -= 2.2D;
                        break; // 东面法线 (1,0,0), subtract → -2.2
                }
                ny += 1.1D;
                player.setPosition(nx, ny, nz);

                // 计算朝向（从眼睛位置看向命中点）
                double eyeHeight = player.yOffset;
                double eyeX = nx;
                double eyeY = ny + eyeHeight;
                double eyeZ = nz;
                double dx2 = hitX - eyeX;
                double dy2 = hitY - eyeY;
                double dz2 = hitZ - eyeZ;
                double xzDist = Math.sqrt(dx2 * dx2 + dz2 * dz2);
                player.rotationYaw = (float) (Math.atan2(-dx2, dz2) * 180.0D / Math.PI);
                player.rotationPitch = (float) (-(Math.atan2(dy2, xzDist) * 180.0D / Math.PI));

                TileEntity te = world.getTileEntity(x, y, z);

                // 优先级1：IInventory 容器 → 直接打开 GUI
                if (te instanceof IInventory) {
                    IInventory inv = (IInventory) te;
                    if (inv instanceof net.minecraft.tileentity.TileEntityChest) {
                        IInventory largeChest = findLargeChest(world, (net.minecraft.tileentity.TileEntityChest) te);
                        player.displayGUIChest(largeChest);
                    } else if (inv instanceof net.minecraft.tileentity.TileEntityFurnace) {
                        player.openGui(RtsbuildingMod.instance, 1, world, x, y, z);
                    } else {
                        player.displayGUIChest(inv);
                    }
                    // 注册远程菜单会话，使 ContainerMixin 绕过 canInteractWith 距离检查
                    if (player.openContainer != null) {
                        com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat
                            .beginRemoteSession(player, player.openContainer);
                    }
                    return null;
                }

                // 优先级2：工具对方块交互（对齐原版：先 onItemUse，再 onBlockActivated）
                net.minecraft.item.ItemStack held = player.getCurrentEquippedItem();
                if (held != null && held.getItem() != null) {
                    float localHitX = (float) (hitX - x);
                    float localHitY = (float) (hitY - y);
                    float localHitZ = (float) (hitZ - z);
                    // 先尝试 onItemUse（桶取水/放水、锄头耕地、斧头去皮等）
                    boolean itemUsed = held.getItem()
                        .onItemUse(held, player, world, x, y, z, face, localHitX, localHitY, localHitZ);
                    if (itemUsed) {
                        RtsbuildingMod.LOGGER.debug(
                            "C2SRtsInteractMessage: {} used item {} on block at ({}, {}, {})",
                            player.getDisplayName(),
                            held.getDisplayName(),
                            x,
                            y,
                            z);
                        return null;
                    }
                }

                // 再尝试 block.onBlockActivated（门、拉杆、按钮、工作台等）
                Block block = world.getBlock(x, y, z);
                if (block != null && !block.isAir(world, x, y, z)) {
                    try {
                        float localHitX2 = (float) (hitX - x);
                        float localHitY2 = (float) (hitY - y);
                        float localHitZ2 = (float) (hitZ - z);
                        boolean activated = block
                            .onBlockActivated(world, x, y, z, player, face, localHitX2, localHitY2, localHitZ2);
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

                return null;
            } finally {
                // Bug11修复: 从 itemId 注入的物品，使用后回收剩余物品
                if (injectedItemId) {
                    ItemStack remaining = player.inventory.mainInventory[0];
                    if (remaining != null && remaining.stackSize > 0) {
                        if (!player.inventory.addItemStackToInventory(remaining)) {
                            player.dropPlayerItemWithRandomChoice(remaining, false);
                        }
                    }
                    player.inventory.mainInventory[0] = savedSlot0Item;
                }
                // 恢复玩家状态
                player.inventory.currentItem = savedSlot;
                player.setPositionAndRotation(savedPosX, savedPosY, savedPosZ, savedYaw, savedPitch);
                player.inventoryContainer.detectAndSendChanges();
            }
        }

        // Bug11修复: 从 itemId 解析 ItemStack（对齐 C2SRtsUseItemMessage.resolveItemStack）
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

        /**
         * 查找大箱子：检查相邻 TileEntityChest，合并为 InventoryLargeChest。
         */
        private static IInventory findLargeChest(World world, net.minecraft.tileentity.TileEntityChest chest) {
            net.minecraft.tileentity.TileEntityChest adjXN = null;
            net.minecraft.tileentity.TileEntityChest adjXP = null;
            net.minecraft.tileentity.TileEntityChest adjZN = null;
            net.minecraft.tileentity.TileEntityChest adjZP = null;

            TileEntity te;

            te = world.getTileEntity(chest.xCoord - 1, chest.yCoord, chest.zCoord);
            if (te instanceof net.minecraft.tileentity.TileEntityChest)
                adjXN = (net.minecraft.tileentity.TileEntityChest) te;

            te = world.getTileEntity(chest.xCoord + 1, chest.yCoord, chest.zCoord);
            if (te instanceof net.minecraft.tileentity.TileEntityChest)
                adjXP = (net.minecraft.tileentity.TileEntityChest) te;

            te = world.getTileEntity(chest.xCoord, chest.yCoord, chest.zCoord - 1);
            if (te instanceof net.minecraft.tileentity.TileEntityChest)
                adjZN = (net.minecraft.tileentity.TileEntityChest) te;

            te = world.getTileEntity(chest.xCoord, chest.yCoord, chest.zCoord + 1);
            if (te instanceof net.minecraft.tileentity.TileEntityChest)
                adjZP = (net.minecraft.tileentity.TileEntityChest) te;

            // 同一方向只能有一个相邻箱子（大箱子是 2 个，不是 4 个）
            if (adjXN != null && adjXP == null) {
                return new net.minecraft.inventory.InventoryLargeChest("container.chest", adjXN, chest);
            } else if (adjXP != null && adjXN == null) {
                return new net.minecraft.inventory.InventoryLargeChest("container.chest", chest, adjXP);
            } else if (adjZN != null && adjZP == null) {
                return new net.minecraft.inventory.InventoryLargeChest("container.chest", adjZN, chest);
            } else if (adjZP != null && adjZN == null) {
                return new net.minecraft.inventory.InventoryLargeChest("container.chest", chest, adjZP);
            }

            return chest;
        }
    }
}
