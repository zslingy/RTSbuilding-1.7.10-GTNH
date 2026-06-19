package com.rtsbuilding.rtsbuilding.server.pipeline.tool;

import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;

public final class ToolBorrowPipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<ItemStack> KEY_TOOL_LEASE = new TypedKey<ItemStack>("toolLease", ItemStack.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        ItemStack held = ctx.player()
            .getCurrentEquippedItem();
        if (held != null) ctx.setData(KEY_TOOL_LEASE, held.copy());
        return PipelineResult.success();
    }
}
