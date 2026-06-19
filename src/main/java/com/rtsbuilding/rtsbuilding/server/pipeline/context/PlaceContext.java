package com.rtsbuilding.rtsbuilding.server.pipeline.context;

import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;

public final class PlaceContext extends PipelineContext {

    public PlaceContext(EntityPlayerMP player, Map<String, Object> args) {
        super(player, args);
    }
}
