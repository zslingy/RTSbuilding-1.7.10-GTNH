package com.rtsbuilding.rtsbuilding.server.pipeline.placement;

import java.util.List;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;

public final class PendingPlacementPipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<Boolean> KEY_HAS_PENDING = new TypedKey<Boolean>("hasPending", Boolean.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        String itemId = ctx.getArg(PlacementExecutePipe.KEY_ITEM_ID);
        if (itemId == null || itemId.isEmpty()) return PipelineResult.success();

        List positions = ctx.getArg(PlacementExecutePipe.KEY_POSITIONS);
        Byte face = ctx.getArg(PlacementExecutePipe.KEY_FACE);
        byte f = face != null ? face.byteValue() : 1;
        RtsPendingPlacementService service = RtsPendingPlacementService.getInstance();

        if (positions != null && positions.size() % 3 == 0) {
            for (int i = 0; i < positions.size() / 3; i++) {
                int x = ((Integer) positions.get(i * 3)).intValue();
                int y = ((Integer) positions.get(i * 3 + 1)).intValue();
                int z = ((Integer) positions.get(i * 3 + 2)).intValue();
                service.addPending(ctx.player(), itemId, 0, x, y, z, f);
            }
        } else {
            Integer x = ctx.getArg(PlacementExecutePipe.KEY_X);
            Integer y = ctx.getArg(PlacementExecutePipe.KEY_Y);
            Integer z = ctx.getArg(PlacementExecutePipe.KEY_Z);
            if (x != null && y != null && z != null) {
                service.addPending(ctx.player(), itemId, 0, x.intValue(), y.intValue(), z.intValue(), f);
            }
        }

        ctx.setData(KEY_HAS_PENDING, service.hasPendings(ctx.player()));
        return PipelineResult.success();
    }
}
