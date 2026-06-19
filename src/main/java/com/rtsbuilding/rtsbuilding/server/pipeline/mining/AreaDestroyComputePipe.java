package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;

public final class AreaDestroyComputePipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        List positions = ctx.getArg(AreaDestroyExecutePipe.KEY_POSITIONS);
        if (positions == null || positions.isEmpty() || positions.size() % 3 != 0)
            return PipelineResult.failure("invalid area destroy positions");

        Byte slot = ctx.getArg(MiningExecutePipe.KEY_TOOL_SLOT);
        int count = positions.size() / 3;
        int[] flat = new int[count * 3];
        for (int i = 0; i < count; i++) {
            flat[i * 3] = ((Integer) positions.get(i * 3)).intValue();
            flat[i * 3 + 1] = ((Integer) positions.get(i * 3 + 1)).intValue();
            flat[i * 3 + 2] = ((Integer) positions.get(i * 3 + 2)).intValue();
        }

        ctx.setData(UltimineComputePipe.KEY_QUEUED_POSITIONS, flat);
        ctx.setData(UltimineComputePipe.KEY_QUEUED_INDEX, Integer.valueOf(0));
        ctx.setData(UltimineComputePipe.KEY_TOTAL_BLOCKS, Integer.valueOf(count));

        ItemStack tool = AreaDestroyExecutePipe.findTool(
            ctx.player(),
            slot != null ? slot.intValue() : -1,
            (String) ctx.getArg(MiningExecutePipe.KEY_TOOL_ITEM_ID),
            (ItemStack) ctx.getArg(MiningExecutePipe.KEY_TOOL_PROTOTYPE));
        if (tool != null) ctx.setData("ultimineTool", tool);

        return PipelineResult.success();
    }
}
