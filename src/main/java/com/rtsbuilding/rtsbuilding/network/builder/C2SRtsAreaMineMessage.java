package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.policy.RtsBreakPolicy;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsAreaMineMessage implements IMessage {

    private int minX, maxX, minY, maxY, minZ, maxZ;
    private byte toolSlot;
    private String toolItemId;
    private ItemStack toolPrototype;
    private byte shapeType, fillType;

    public C2SRtsAreaMineMessage() {}

    public C2SRtsAreaMineMessage(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, byte toolSlot,
        String toolItemId, ItemStack toolPrototype, byte shapeType, byte fillType) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.toolSlot = toolSlot;
        this.toolItemId = toolItemId != null ? toolItemId : "";
        this.toolPrototype = toolPrototype != null ? toolPrototype.copy() : null;
        this.shapeType = shapeType;
        this.fillType = fillType;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(minX);
        buf.writeInt(maxX);
        buf.writeInt(minY);
        buf.writeInt(maxY);
        buf.writeInt(minZ);
        buf.writeInt(maxZ);
        buf.writeByte(toolSlot);
        writeUtf(buf, toolItemId, 256);
        boolean hasTool = toolPrototype != null && toolPrototype.getItem() != null;
        buf.writeBoolean(hasTool);
        if (hasTool) ByteBufUtils.writeItemStack(buf, toolPrototype);
        buf.writeByte(shapeType);
        buf.writeByte(fillType);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        minX = buf.readInt();
        maxX = buf.readInt();
        minY = buf.readInt();
        maxY = buf.readInt();
        minZ = buf.readInt();
        maxZ = buf.readInt();
        toolSlot = buf.readByte();
        toolItemId = readUtf(buf, 256);
        toolPrototype = buf.readBoolean() ? ByteBufUtils.readItemStack(buf) : null;
        shapeType = buf.readByte();
        fillType = buf.readByte();
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

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
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

    public byte getShapeType() {
        return shapeType;
    }

    public byte getFillType() {
        return fillType;
    }

    public static class Handler implements IMessageHandler<C2SRtsAreaMineMessage, IMessage> {

        private static final int AREA_MINE_MAX_SIZE = 12;

        @Override
        public IMessage onMessage(C2SRtsAreaMineMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // RTS 模式检查
            if (!RtsCameraManager.isActive(player)) return null;

            // 进度检查
            if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
                return null;
            }

            World world = player.worldObj;
            int slot = Math.max(0, Math.min(msg.toolSlot, 8));
            ItemStack tool = findTool(player, slot, msg.toolItemId, msg.toolPrototype);

            // 边界裁剪
            int cMinX = msg.minX;
            int cMaxX = Math.min(msg.minX + AREA_MINE_MAX_SIZE - 1, msg.maxX);
            int cMinZ = msg.minZ;
            int cMaxZ = Math.min(msg.minZ + AREA_MINE_MAX_SIZE - 1, msg.maxZ);
            int cMinY = msg.minY;
            int cMaxY = Math.min(msg.minY + AREA_MINE_MAX_SIZE - 1, msg.maxY);

            // 形状过滤参数
            double cx = (cMinX + cMaxX + 1) / 2.0D;
            double cz = (cMinZ + cMaxZ + 1) / 2.0D;
            double rx = (cMaxX - cMinX + 1) / 2.0D;
            double rz = (cMaxZ - cMinZ + 1) / 2.0D;
            double cylRadiusSq = Math.max(rx, rz) * Math.max(rx, rz);

            int broken = 0;
            for (int y = cMinY; y <= cMaxY; y++) {
                for (int x = cMinX; x <= cMaxX; x++) {
                    for (int z = cMinZ; z <= cMaxZ; z++) {
                        if (!isInShape(
                            msg.shapeType,
                            msg.fillType,
                            cMinX,
                            cMaxX,
                            cMinY,
                            cMaxY,
                            cMinZ,
                            cMaxZ,
                            x,
                            y,
                            z,
                            cx,
                            cz,
                            cylRadiusSq)) {
                            continue;
                        }
                        if (!RtsBreakPolicy.canBreakBlock(player, world, x, y, z)) {
                            continue;
                        }
                        if (breakBlock(world, player, x, y, z, tool)) {
                            broken++;
                        }
                    }
                }
            }

            if (broken > 0) {
                RtsbuildingMod.LOGGER.info(
                    "AreaMine: {} broke {} blocks in [{},{}]-[{},{}]-[{},{}] shape={}/{}",
                    player.getDisplayName(),
                    broken,
                    cMinX,
                    cMaxX,
                    cMinY,
                    cMaxY,
                    cMinZ,
                    cMaxZ,
                    msg.shapeType,
                    msg.fillType);
            }
            return null;
        }

        private static boolean isInShape(byte shapeType, byte fillType, int minX, int maxX, int minY, int maxY,
            int minZ, int maxZ, int x, int y, int z, double cx, double cz, double radiusSq) {
            int boxDx = maxX - minX, boxDy = maxY - minY, boxDz = maxZ - minZ;
            if (shapeType == 0) {
                int cxB = minX + boxDx / 2, cyB = minY + boxDy / 2, czB = minZ + boxDz / 2;
                return x == cxB && y == cyB && z == czB;
            }
            if (shapeType == 1) {
                if (boxDx >= boxDy && boxDx >= boxDz) return y == minY && z == minZ;
                if (boxDy >= boxDx && boxDy >= boxDz) return x == minX && z == minZ;
                return x == minX && y == minY;
            }
            if (shapeType == 2) return y == minY;
            if (shapeType == 3) return (x == minX || x == maxX) || (z == minZ || z == maxZ);
            if (shapeType == 4) {
                double ddx = x + 0.5 - cx, ddz = z + 0.5 - cz;
                return (ddx * ddx + ddz * ddz) <= radiusSq + 0.5;
            }
            if (fillType == 1) {
                boolean onSurface = (x == minX || x == maxX) || (y == minY || y == maxY) || (z == minZ || z == maxZ);
                if (!onSurface) return false;
            }
            if (fillType == 2) {
                int onEdgeCount = (x == minX || x == maxX ? 1 : 0) + (y == minY || y == maxY ? 1 : 0)
                    + (z == minZ || z == maxZ ? 1 : 0);
                return onEdgeCount >= 2;
            }
            return true;
        }

        private static ItemStack findTool(EntityPlayerMP player, int preferredSlot, String toolItemId,
            ItemStack prototype) {
            if (preferredSlot >= 0 && preferredSlot < 9) {
                ItemStack hotbar = player.inventory.getStackInSlot(preferredSlot);
                if (hotbar != null && matchesTool(hotbar, toolItemId, prototype)) return hotbar;
            }
            for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                ItemStack s = player.inventory.getStackInSlot(i);
                if (s != null && matchesTool(s, toolItemId, prototype)) return s;
            }
            return null;
        }

        private static boolean matchesTool(ItemStack stack, String toolItemId, ItemStack prototype) {
            if (stack == null) return false;
            if (prototype != null && prototype.getItem() != null) {
                return stack.getItem() == prototype.getItem() && stack.getItemDamage() == prototype.getItemDamage();
            }
            if (!toolItemId.isEmpty()) {
                String stackId = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                return toolItemId.equals(stackId);
            }
            return false;
        }

        private static boolean breakBlock(World world, EntityPlayerMP player, int x, int y, int z, ItemStack tool) {
            Block block = world.getBlock(x, y, z);
            if (block == null || world.isAirBlock(x, y, z)) return false;
            int meta = world.getBlockMetadata(x, y, z);
            float hardness = block.getBlockHardness(world, x, y, z);
            if (hardness < 0) return false;

            boolean creative = player.capabilities.isCreativeMode;
            if (!creative) {
                if (tool == null) return false;
                if (!tool.getItem()
                    .canHarvestBlock(block, tool)) return false;
            }

            world.playSoundEffect(
                x + 0.5,
                y + 0.5,
                z + 0.5,
                block.stepSound.getBreakSound(),
                (block.stepSound.getVolume() + 1.0F) / 2.0F,
                block.stepSound.getPitch() * 0.8F);

            // 破坏粒子效果
            world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (meta << 12));
            block.harvestBlock(world, player, x, y, z, meta);
            world.setBlockToAir(x, y, z);

            if (!creative && tool != null) {
                tool.damageItem(1, player);
                if (tool.stackSize <= 0) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                }
            }
            return true;
        }
    }
}
