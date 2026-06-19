package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.policy.RtsBreakPolicy;

public final class AreaMineExecutePipe implements PipelinePipe<PipelineContext> {

    private static final int AREA_MINE_MAX_SIZE = 12;
    public static final TypedKey<Integer> KEY_MIN_X = new TypedKey<Integer>("minX", Integer.class);
    public static final TypedKey<Integer> KEY_MAX_X = new TypedKey<Integer>("maxX", Integer.class);
    public static final TypedKey<Integer> KEY_MIN_Y = new TypedKey<Integer>("minY", Integer.class);
    public static final TypedKey<Integer> KEY_MAX_Y = new TypedKey<Integer>("maxY", Integer.class);
    public static final TypedKey<Integer> KEY_MIN_Z = new TypedKey<Integer>("minZ", Integer.class);
    public static final TypedKey<Integer> KEY_MAX_Z = new TypedKey<Integer>("maxZ", Integer.class);
    public static final TypedKey<Byte> KEY_SHAPE_TYPE = new TypedKey<Byte>("shapeType", Byte.class);
    public static final TypedKey<Byte> KEY_FILL_TYPE = new TypedKey<Byte>("fillType", Byte.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        Integer minX = ctx.getArg(KEY_MIN_X), maxX = ctx.getArg(KEY_MAX_X), minY = ctx.getArg(KEY_MIN_Y),
            maxY = ctx.getArg(KEY_MAX_Y), minZ = ctx.getArg(KEY_MIN_Z), maxZ = ctx.getArg(KEY_MAX_Z);
        if (minX == null || maxX == null || minY == null || maxY == null || minZ == null || maxZ == null)
            return PipelineResult.failure("missing area mine bounds");
        Byte shape = ctx.getArg(KEY_SHAPE_TYPE), fill = ctx.getArg(KEY_FILL_TYPE),
            slot = ctx.getArg(MiningExecutePipe.KEY_TOOL_SLOT);
        World world = ctx.player().worldObj;
        ItemStack tool = AreaDestroyExecutePipe.findTool(
            ctx.player(),
            slot == null ? -1 : slot.byteValue(),
            ctx.getArg(MiningExecutePipe.KEY_TOOL_ITEM_ID),
            (ItemStack) ctx.getArg(MiningExecutePipe.KEY_TOOL_PROTOTYPE));
        int cMinX = minX.intValue(), cMaxX = Math.min(minX.intValue() + AREA_MINE_MAX_SIZE - 1, maxX.intValue());
        int cMinY = minY.intValue(), cMaxY = Math.min(minY.intValue() + AREA_MINE_MAX_SIZE - 1, maxY.intValue());
        int cMinZ = minZ.intValue(), cMaxZ = Math.min(minZ.intValue() + AREA_MINE_MAX_SIZE - 1, maxZ.intValue());
        double cx = (cMinX + cMaxX + 1) / 2.0D, cz = (cMinZ + cMaxZ + 1) / 2.0D;
        double radiusSq = Math.max((cMaxX - cMinX + 1) / 2.0D, (cMaxZ - cMinZ + 1) / 2.0D);
        radiusSq *= radiusSq;
        int broken = 0;
        for (int y = cMinY; y <= cMaxY; y++) for (int x = cMinX; x <= cMaxX; x++) for (int z = cMinZ; z <= cMaxZ; z++) {
            if (!isInShapePublic(
                shape == null ? 0 : shape.byteValue(),
                fill == null ? 0 : fill.byteValue(),
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
                radiusSq)) continue;
            if (RtsBreakPolicy.canBreakBlock(ctx.player(), world, x, y, z)
                && AreaDestroyExecutePipe.breakBlock(world, ctx.player(), x, y, z, tool)) broken++;
        }
        if (broken > 0) RtsbuildingMod.LOGGER.info(
            "AreaMine: {} broke {} blocks",
            ctx.player()
                .getDisplayName(),
            broken);
        return broken > 0 ? PipelineResult.success() : PipelineResult.failure("no area mine blocks broken");
    }

    public static boolean isInShapePublic(byte shapeType, byte fillType, int minX, int maxX, int minY, int maxY,
        int minZ, int maxZ, int x, int y, int z, double cx, double cz, double radiusSq) {
        int boxDx = maxX - minX, boxDy = maxY - minY, boxDz = maxZ - minZ;
        if (shapeType == 0) return x == minX + boxDx / 2 && y == minY + boxDy / 2 && z == minZ + boxDz / 2;
        if (shapeType == 1) return boxDx >= boxDy && boxDx >= boxDz ? y == minY && z == minZ
            : boxDy >= boxDx && boxDy >= boxDz ? x == minX && z == minZ : x == minX && y == minY;
        if (shapeType == 2) return y == minY;
        if (shapeType == 3) return x == minX || x == maxX || z == minZ || z == maxZ;
        if (shapeType == 4) {
            double ddx = x + 0.5 - cx, ddz = z + 0.5 - cz;
            return ddx * ddx + ddz * ddz <= radiusSq + 0.5;
        }
        if (fillType == 1 && !((x == minX || x == maxX) || (y == minY || y == maxY) || (z == minZ || z == maxZ)))
            return false;
        if (fillType == 2) return (x == minX || x == maxX ? 1 : 0) + (y == minY || y == maxY ? 1 : 0)
            + (z == minZ || z == maxZ ? 1 : 0) >= 2;
        return true;
    }
}
