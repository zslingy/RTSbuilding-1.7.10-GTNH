package com.rtsbuilding.rtsbuilding.server.pipeline.placement;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;

public final class PlacementExecutePipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<Integer> KEY_X = new TypedKey<Integer>("x", Integer.class);
    public static final TypedKey<Integer> KEY_Y = new TypedKey<Integer>("y", Integer.class);
    public static final TypedKey<Integer> KEY_Z = new TypedKey<Integer>("z", Integer.class);
    public static final TypedKey<List> KEY_POSITIONS = new TypedKey<List>("positions", List.class);
    public static final TypedKey<Byte> KEY_FACE = new TypedKey<Byte>("face", Byte.class);
    public static final TypedKey<Double> KEY_HIT_X = new TypedKey<Double>("hitX", Double.class);
    public static final TypedKey<Double> KEY_HIT_Y = new TypedKey<Double>("hitY", Double.class);
    public static final TypedKey<Double> KEY_HIT_Z = new TypedKey<Double>("hitZ", Double.class);
    public static final TypedKey<Byte> KEY_ROTATE_STEPS = new TypedKey<Byte>("rotateSteps", Byte.class);
    public static final TypedKey<Boolean> KEY_FORCE_PLACE = new TypedKey<Boolean>("forcePlace", Boolean.class);
    public static final TypedKey<Boolean> KEY_SKIP_IF_OCCUPIED = new TypedKey<Boolean>("skipIfOccupied", Boolean.class);
    public static final TypedKey<String> KEY_ITEM_ID = new TypedKey<String>("itemId", String.class);
    public static final TypedKey<ItemStack> KEY_ITEM_PROTOTYPE = new TypedKey<ItemStack>(
        "itemPrototype",
        ItemStack.class);
    public static final TypedKey<Boolean> KEY_QUICK_BUILD = new TypedKey<Boolean>("quickBuild", Boolean.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        List positions = ctx.getArg(KEY_POSITIONS);
        if (positions != null) return placeBatch(ctx, positions);
        Integer x = ctx.getArg(KEY_X), y = ctx.getArg(KEY_Y), z = ctx.getArg(KEY_Z);
        if (x == null || y == null || z == null) return PipelineResult.failure("missing placement position");
        return placeOne(ctx, x.intValue(), y.intValue(), z.intValue()) ? PipelineResult.success()
            : PipelineResult.failure("placement failed");
    }

    private PipelineResult placeBatch(PipelineContext ctx, List positions) {
        if (positions.isEmpty() || positions.size() % 3 != 0) return PipelineResult.failure("invalid placement batch");
        boolean placedAny = false;
        for (int i = 0; i < positions.size() / 3; i++) {
            int x = ((Integer) positions.get(i * 3)).intValue();
            int y = ((Integer) positions.get(i * 3 + 1)).intValue();
            int z = ((Integer) positions.get(i * 3 + 2)).intValue();
            placedAny |= placeOne(ctx, x, y, z);
        }
        return placedAny ? PipelineResult.success() : PipelineResult.failure("batch placement failed");
    }

    private boolean placeOne(PipelineContext ctx, int x, int y, int z) {
        Byte face = ctx.getArg(KEY_FACE);
        Double hitX = ctx.getArg(KEY_HIT_X), hitY = ctx.getArg(KEY_HIT_Y), hitZ = ctx.getArg(KEY_HIT_Z);
        Byte rotate = ctx.getArg(KEY_ROTATE_STEPS);
        Boolean force = ctx.getArg(KEY_FORCE_PLACE), skip = ctx.getArg(KEY_SKIP_IF_OCCUPIED),
            quick = ctx.getArg(KEY_QUICK_BUILD);
        return RtsStorageManager.placeBlockDirect(
            ctx.player(),
            x,
            y,
            z,
            face == null ? (byte) 1 : face.byteValue(),
            hitX == null ? 0.5D : hitX.doubleValue(),
            hitY == null ? 0.5D : hitY.doubleValue(),
            hitZ == null ? 0.5D : hitZ.doubleValue(),
            rotate == null ? (byte) 0 : rotate.byteValue(),
            force != null && force.booleanValue(),
            skip != null && skip.booleanValue(),
            ctx.getArg(KEY_ITEM_ID),
            ctx.getArg(KEY_ITEM_PROTOTYPE),
            quick != null && quick.booleanValue());
    }
}
