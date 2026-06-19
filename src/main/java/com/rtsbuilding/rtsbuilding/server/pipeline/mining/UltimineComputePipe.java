package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.RtsMineManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.util.RtsUltimineCollector;

public final class UltimineComputePipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<int[]> KEY_QUEUED_POSITIONS = new TypedKey<int[]>("queuedPositions", int[].class);
    public static final TypedKey<Integer> KEY_QUEUED_INDEX = new TypedKey<Integer>("queuedIndex", Integer.class);
    public static final TypedKey<Integer> KEY_TOTAL_BLOCKS = new TypedKey<Integer>("totalBlocks", Integer.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        Integer x = ctx.getArg(MiningExecutePipe.KEY_X);
        Integer y = ctx.getArg(MiningExecutePipe.KEY_Y);
        Integer z = ctx.getArg(MiningExecutePipe.KEY_Z);
        if (x == null || y == null || z == null) return PipelineResult.failure("missing ultimine seed");

        Integer limitArg = ctx.getArg(UltimineExecutePipe.KEY_LIMIT);
        int limit = limitArg != null ? limitArg.intValue() : 64;

        List<int[]> targets = RtsUltimineCollector
            .collect(ctx.player().worldObj, x.intValue(), y.intValue(), z.intValue(), limit, 64);
        if (targets.isEmpty()) return PipelineResult.success();

        int[] flat = new int[targets.size() * 3];
        for (int i = 0; i < targets.size(); i++) {
            int[] p = targets.get(i);
            flat[i * 3] = p[0];
            flat[i * 3 + 1] = p[1];
            flat[i * 3 + 2] = p[2];
        }
        ctx.setData(KEY_QUEUED_POSITIONS, flat);
        ctx.setData(KEY_QUEUED_INDEX, Integer.valueOf(0));
        ctx.setData(KEY_TOTAL_BLOCKS, Integer.valueOf(targets.size()));

        Byte slot = ctx.getArg(MiningExecutePipe.KEY_TOOL_SLOT);
        ItemStack tool = RtsMineManager.findTool(
            ctx.player(),
            slot != null ? slot.intValue() : -1,
            (String) ctx.getArg(MiningExecutePipe.KEY_TOOL_ITEM_ID),
            (ItemStack) ctx.getArg(MiningExecutePipe.KEY_TOOL_PROTOTYPE));
        if (tool != null) ctx.setData("ultimineTool", tool);

        return PipelineResult.success();
    }
}
