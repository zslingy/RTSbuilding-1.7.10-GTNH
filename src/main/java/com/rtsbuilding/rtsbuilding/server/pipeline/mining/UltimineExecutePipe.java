package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.RtsMineManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;

public final class UltimineExecutePipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<Integer> KEY_LIMIT = new TypedKey<Integer>("limit", Integer.class);
    public static final TypedKey<Byte> KEY_MODE = new TypedKey<Byte>("mode", Byte.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        Integer x = ctx.getArg(MiningExecutePipe.KEY_X), y = ctx.getArg(MiningExecutePipe.KEY_Y),
            z = ctx.getArg(MiningExecutePipe.KEY_Z);
        if (x == null || y == null || z == null) return PipelineResult.failure("missing ultimine seed");
        Byte face = ctx.getArg(MiningExecutePipe.KEY_FACE);
        Byte slot = ctx.getArg(MiningExecutePipe.KEY_TOOL_SLOT);
        Integer limit = ctx.getArg(KEY_LIMIT);
        Byte mode = ctx.getArg(KEY_MODE);
        RtsMineManager.startUltimineDirect(
            ctx.player(),
            x.intValue(),
            y.intValue(),
            z.intValue(),
            face == null ? (byte) 0 : face.byteValue(),
            slot == null ? -1 : slot.byteValue(),
            (String) ctx.getArg(MiningExecutePipe.KEY_TOOL_ITEM_ID),
            (ItemStack) ctx.getArg(MiningExecutePipe.KEY_TOOL_PROTOTYPE),
            limit == null ? 64 : limit.intValue(),
            mode == null ? (byte) 0 : mode.byteValue());
        return PipelineResult.success();
    }
}
