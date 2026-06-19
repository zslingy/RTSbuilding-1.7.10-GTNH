package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.RtsMineManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;

public final class MiningExecutePipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<Integer> KEY_X = new TypedKey<Integer>("x", Integer.class);
    public static final TypedKey<Integer> KEY_Y = new TypedKey<Integer>("y", Integer.class);
    public static final TypedKey<Integer> KEY_Z = new TypedKey<Integer>("z", Integer.class);
    public static final TypedKey<Byte> KEY_FACE = new TypedKey<Byte>("face", Byte.class);
    public static final TypedKey<Byte> KEY_TOOL_SLOT = new TypedKey<Byte>("toolSlot", Byte.class);
    public static final TypedKey<String> KEY_TOOL_ITEM_ID = new TypedKey<String>("toolItemId", String.class);
    public static final TypedKey<ItemStack> KEY_TOOL_PROTOTYPE = new TypedKey<ItemStack>(
        "toolPrototype",
        ItemStack.class);
    public static final TypedKey<Boolean> KEY_ALLOW_PLACED_RECOVERY = new TypedKey<Boolean>(
        "allowPlacedBlockRecovery",
        Boolean.class);
    public static final TypedKey<Boolean> KEY_ULTIMINE = new TypedKey<Boolean>("ultimine", Boolean.class);
    public static final TypedKey<Integer> KEY_ULTIMINE_LIMIT = new TypedKey<Integer>("ultimineLimit", Integer.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        Integer x = ctx.getArg(KEY_X), y = ctx.getArg(KEY_Y), z = ctx.getArg(KEY_Z);
        if (x == null || y == null || z == null) return PipelineResult.failure("missing mining position");
        Byte face = ctx.getArg(KEY_FACE);
        Byte slot = ctx.getArg(KEY_TOOL_SLOT);
        Boolean recovery = ctx.getArg(KEY_ALLOW_PLACED_RECOVERY);
        Boolean ultimine = ctx.getArg(KEY_ULTIMINE);
        Integer ultimineLimit = ctx.getArg(KEY_ULTIMINE_LIMIT);
        RtsMineManager.startMiningDirect(
            ctx.player(),
            x.intValue(),
            y.intValue(),
            z.intValue(),
            face == null ? (byte) 0 : face.byteValue(),
            slot == null ? (byte) -1 : slot.byteValue(),
            ctx.getArg(KEY_TOOL_ITEM_ID),
            ctx.getArg(KEY_TOOL_PROTOTYPE),
            recovery != null && recovery.booleanValue(),
            ultimine != null && ultimine.booleanValue(),
            ultimineLimit != null ? ultimineLimit.intValue() : 64);
        return PipelineResult.success();
    }
}
