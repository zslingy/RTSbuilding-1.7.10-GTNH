package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;

public final class AreaMineComputePipe implements PipelinePipe<PipelineContext> {

    private static final int AREA_MINE_MAX_SIZE = 12;

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        Integer minX = ctx.getArg(AreaMineExecutePipe.KEY_MIN_X);
        Integer maxX = ctx.getArg(AreaMineExecutePipe.KEY_MAX_X);
        Integer minY = ctx.getArg(AreaMineExecutePipe.KEY_MIN_Y);
        Integer maxY = ctx.getArg(AreaMineExecutePipe.KEY_MAX_Y);
        Integer minZ = ctx.getArg(AreaMineExecutePipe.KEY_MIN_Z);
        Integer maxZ = ctx.getArg(AreaMineExecutePipe.KEY_MAX_Z);
        if (minX == null || maxX == null || minY == null || maxY == null || minZ == null || maxZ == null)
            return PipelineResult.failure("missing area mine bounds");

        Byte shape = ctx.getArg(AreaMineExecutePipe.KEY_SHAPE_TYPE);
        Byte fill = ctx.getArg(AreaMineExecutePipe.KEY_FILL_TYPE);
        Byte slot = ctx.getArg(MiningExecutePipe.KEY_TOOL_SLOT);

        int cMinX = minX.intValue(), cMaxX = Math.min(minX.intValue() + AREA_MINE_MAX_SIZE - 1, maxX.intValue());
        int cMinY = minY.intValue(), cMaxY = Math.min(minY.intValue() + AREA_MINE_MAX_SIZE - 1, maxY.intValue());
        int cMinZ = minZ.intValue(), cMaxZ = Math.min(minZ.intValue() + AREA_MINE_MAX_SIZE - 1, maxZ.intValue());
        double cx = (cMinX + cMaxX + 1) / 2.0D, cz = (cMinZ + cMaxZ + 1) / 2.0D;
        double radiusSq = Math.max((cMaxX - cMinX + 1) / 2.0D, (cMaxZ - cMinZ + 1) / 2.0D);
        radiusSq *= radiusSq;
        byte s = shape != null ? shape.byteValue() : 0;
        byte f = fill != null ? fill.byteValue() : 0;

        java.util.ArrayList<Integer> posList = new java.util.ArrayList<Integer>();
        for (int y = cMinY; y <= cMaxY; y++) {
            for (int x = cMinX; x <= cMaxX; x++) {
                for (int z = cMinZ; z <= cMaxZ; z++) {
                    if (!AreaMineExecutePipe
                        .isInShapePublic(s, f, cMinX, cMaxX, cMinY, cMaxY, cMinZ, cMaxZ, x, y, z, cx, cz, radiusSq))
                        continue;
                    posList.add(Integer.valueOf(x));
                    posList.add(Integer.valueOf(y));
                    posList.add(Integer.valueOf(z));
                }
            }
        }

        if (posList.isEmpty()) return PipelineResult.failure("no area mine blocks in shape");

        int[] flat = new int[posList.size()];
        for (int i = 0; i < posList.size(); i++) flat[i] = posList.get(i)
            .intValue();
        ctx.setData(UltimineComputePipe.KEY_QUEUED_POSITIONS, flat);
        ctx.setData(UltimineComputePipe.KEY_QUEUED_INDEX, Integer.valueOf(0));
        ctx.setData(UltimineComputePipe.KEY_TOTAL_BLOCKS, Integer.valueOf(flat.length / 3));

        ItemStack tool = AreaDestroyExecutePipe.findTool(
            ctx.player(),
            slot != null ? slot.intValue() : -1,
            (String) ctx.getArg(MiningExecutePipe.KEY_TOOL_ITEM_ID),
            (ItemStack) ctx.getArg(MiningExecutePipe.KEY_TOOL_PROTOTYPE));
        if (tool != null) ctx.setData("ultimineTool", tool);

        return PipelineResult.success();
    }
}
