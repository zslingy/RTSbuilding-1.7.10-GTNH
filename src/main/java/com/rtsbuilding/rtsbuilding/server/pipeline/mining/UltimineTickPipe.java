package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.RtsMineManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipe;

public final class UltimineTickPipe implements TickablePipe {

    private static final int BLOCKS_PER_TICK = 8;

    @Override
    public TickResult tick(PipelineContext ctx) {
        int[] flat = ctx.getData(UltimineComputePipe.KEY_QUEUED_POSITIONS);
        Integer idxObj = ctx.getData(UltimineComputePipe.KEY_QUEUED_INDEX);
        if (flat == null || idxObj == null) return TickResult.COMPLETE;

        int idx = idxObj.intValue();
        int total = flat.length / 3;
        ItemStack tool = ctx.getData("ultimineTool");

        int processed = 0;
        while (idx < total && processed < BLOCKS_PER_TICK) {
            int x = flat[idx * 3];
            int y = flat[idx * 3 + 1];
            int z = flat[idx * 3 + 2];
            RtsMineManager.breakBlockDirect(ctx.player(), x, y, z, tool);
            idx++;
            processed++;
        }

        ctx.setData(UltimineComputePipe.KEY_QUEUED_INDEX, Integer.valueOf(idx));
        ctx.setData(UltimineComputePipe.KEY_TOTAL_BLOCKS, Integer.valueOf(total));

        if (idx >= total) return TickResult.COMPLETE;
        return TickResult.CONTINUE;
    }
}
