package com.rtsbuilding.rtsbuilding.server.service;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

public class RtsPlacementService {

    public static boolean placeBlockDirect(EntityPlayerMP player, int clickedX, int clickedY, int clickedZ, byte face,
        double hitX, double hitY, double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
        String itemId, ItemStack itemPrototype, boolean quickBuild) {
        if (player == null) return false;
        World world = player.worldObj;
        if (world == null) return false;

        RtsStorageSession session = RtsSessionService.getSession(player);

        // 解析放置方块
        Block placeBlock = null;
        int placeMeta = 0;
        if (itemPrototype != null && itemPrototype.getItem() instanceof ItemBlock) {
            Block block = ((ItemBlock) itemPrototype.getItem()).field_150939_a;
            if (block != null) {
                placeBlock = block;
                placeMeta = itemPrototype.getItemDamage();
            }
        }

        if (placeBlock == null && itemId != null) {
            Item item = (Item) Item.itemRegistry.getObject(itemId);
            if (item instanceof ItemBlock) {
                placeBlock = ((ItemBlock) item).field_150939_a;
            }
        }

        if (placeBlock == null) {
            RtsbuildingMod.LOGGER.warn("RtsPlacementService: cannot resolve block for itemId={}", itemId);
            return false;
        }

        // 检查库存
        if (!forcePlace) {
            long available = RtsTransferService.getAvailableAmount(player, itemId, 0);
            if (available <= 0) {
                RtsbuildingMod.LOGGER.warn("RtsPlacementService: insufficient items for itemId={}", itemId);
                return false;
            }
        }

        // 计算放置坐标
        int targetX = clickedX;
        int targetY = clickedY;
        int targetZ = clickedZ;
        if (face == 0) targetY--;
        else if (face == 1) targetY++;
        else if (face == 2) targetZ--;
        else if (face == 3) targetZ++;
        else if (face == 4) targetX--;
        else if (face == 5) targetX++;

        if (!world.blockExists(targetX, targetY, targetZ)) return false;

        Block existing = world.getBlock(targetX, targetY, targetZ);
        if (existing != null && !existing.isReplaceable(world, targetX, targetY, targetZ)) {
            if (skipIfOccupied) return false;
            if (!forcePlace) return false;
        }

        // 放置方块
        int prevMeta = world.getBlockMetadata(targetX, targetY, targetZ);
        boolean placed = world.setBlock(targetX, targetY, targetZ, placeBlock, placeMeta, 3);
        if (!placed) return false;

        // 消耗库存
        if (!forcePlace) {
            RtsTransferService.tryConsumeBlock(player, itemId, 0, 1);
        }

        // 播放音效
        world.playSoundEffect(
            targetX + 0.5,
            targetY + 0.5,
            targetZ + 0.5,
            placeBlock.stepSound.func_150496_b(),
            (placeBlock.stepSound.getVolume() + 1.0F) / 2.0F,
            placeBlock.stepSound.getPitch() * 0.8F);

        return true;
    }
}
