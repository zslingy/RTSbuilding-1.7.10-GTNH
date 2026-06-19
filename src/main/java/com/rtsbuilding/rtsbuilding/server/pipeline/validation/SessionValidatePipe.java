package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

public final class SessionValidatePipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<RtsStorageSession> KEY_SESSION = new TypedKey<RtsStorageSession>(
        "session",
        RtsStorageSession.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        if (ctx.player() == null || ctx.player().worldObj == null) return PipelineResult.failure("invalid player");
        ctx.setData(KEY_SESSION, RtsStorageManager.getSession(ctx.player()));
        return PipelineResult.success();
    }
}
