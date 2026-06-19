package com.rtsbuilding.rtsbuilding.server.pipeline.tool;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;

public final class ToolReturnPipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        ItemStack lease = ctx.getData(ToolBorrowPipe.KEY_TOOL_LEASE);
        if (lease == null) return PipelineResult.success();
        EntityPlayerMP player = ctx.player();
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null && lease != null) {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, lease.copy());
            player.inventoryContainer.detectAndSendChanges();
        }
        return PipelineResult.success();
    }
}
