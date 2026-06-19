package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.entity.Entity;

public interface MovementModeHandler {

    String name();

    void apply(Entity entity, MovementParams params);

    boolean isActive(Entity entity);

    void stop(Entity entity);
}
