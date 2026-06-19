package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.policy.RtsBreakPolicy;

public class AreaDestroyExecutePipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<List> KEY_POSITIONS = new TypedKey<List>("positions", List.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        List positions = ctx.getArg(KEY_POSITIONS);
        if (positions == null || positions.isEmpty() || positions.size() % 3 != 0) {
            return PipelineResult.failure("invalid area destroy positions");
        }
        World world = ctx.player().worldObj;
        Byte slot = ctx.getArg(MiningExecutePipe.KEY_TOOL_SLOT);
        ItemStack tool = findTool(
            ctx.player(),
            slot == null ? -1 : slot.byteValue(),
            ctx.getArg(MiningExecutePipe.KEY_TOOL_ITEM_ID),
            (ItemStack) ctx.getArg(MiningExecutePipe.KEY_TOOL_PROTOTYPE));
        int broken = 0;
        int count = positions.size() / 3;
        for (int i = 0; i < count; i++) {
            int x = ((Integer) positions.get(i * 3)).intValue();
            int y = ((Integer) positions.get(i * 3 + 1)).intValue();
            int z = ((Integer) positions.get(i * 3 + 2)).intValue();
            if (RtsBreakPolicy.canBreakBlock(ctx.player(), world, x, y, z)
                && breakBlock(world, ctx.player(), x, y, z, tool)) broken++;
        }
        if (broken > 0) RtsbuildingMod.LOGGER.info(
            "AreaDestroy: {} broke {} of {} blocks",
            ctx.player()
                .getDisplayName(),
            broken,
            count);
        return broken > 0 ? PipelineResult.success() : PipelineResult.failure("no area destroy blocks broken");
    }

    static ItemStack findTool(EntityPlayerMP player, int preferredSlot, String toolItemId, ItemStack prototype) {
        if (preferredSlot >= 0 && preferredSlot < 9) {
            ItemStack hotbar = player.inventory.getStackInSlot(preferredSlot);
            if (hotbar != null && matchesTool(hotbar, toolItemId, prototype)) return hotbar;
        }
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && matchesTool(stack, toolItemId, prototype)) return stack;
        }
        return null;
    }

    private static boolean matchesTool(ItemStack stack, String toolItemId, ItemStack prototype) {
        if (stack == null) return false;
        if (prototype != null && prototype.getItem() != null)
            return stack.getItem() == prototype.getItem() && stack.getItemDamage() == prototype.getItemDamage();
        if (toolItemId != null && !toolItemId.isEmpty())
            return toolItemId.equals(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
        return false;
    }

    static boolean breakBlock(World world, EntityPlayerMP player, int x, int y, int z, ItemStack tool) {
        Block block = world.getBlock(x, y, z);
        if (block == null || world.isAirBlock(x, y, z)) return false;
        int meta = world.getBlockMetadata(x, y, z);
        if (block.getBlockHardness(world, x, y, z) < 0) return false;
        boolean creative = player.capabilities.isCreativeMode;
        if (!creative && (tool == null || !tool.getItem()
            .canHarvestBlock(block, tool))) return false;
        world.playSoundEffect(
            x + 0.5,
            y + 0.5,
            z + 0.5,
            block.stepSound.getBreakSound(),
            (block.stepSound.getVolume() + 1.0F) / 2.0F,
            block.stepSound.getPitch() * 0.8F);
        world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (meta << 12));
        block.harvestBlock(world, player, x, y, z, meta);
        world.setBlockToAir(x, y, z);
        if (!creative && tool != null) tool.damageItem(1, player);
        return true;
    }
}
