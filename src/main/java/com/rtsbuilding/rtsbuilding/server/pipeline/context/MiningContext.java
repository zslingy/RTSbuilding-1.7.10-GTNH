package com.rtsbuilding.rtsbuilding.server.pipeline.context;

import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;

public final class MiningContext extends PipelineContext {

    public MiningContext(EntityPlayerMP player, Map<String, Object> args) {
        super(player, args);
    }
}
