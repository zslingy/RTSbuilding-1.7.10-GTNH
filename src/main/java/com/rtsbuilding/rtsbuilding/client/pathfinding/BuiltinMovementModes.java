package com.rtsbuilding.rtsbuilding.client.pathfinding;

import net.minecraft.entity.Entity;

public final class BuiltinMovementModes {

    private BuiltinMovementModes() {}

    public static final MovementModeHandler WALK = new MovementModeHandler() {

        @Override
        public String name() {
            return "walk";
        }

        @Override
        public void apply(Entity entity, MovementParams params) {
            double dx = params.targetX - entity.posX;
            double dz = params.targetZ - entity.posZ;
            double dist = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
            entity.motionX = dx / dist * 0.15;
            entity.motionZ = dz / dist * 0.15;
            entity.velocityChanged = true;
        }

        @Override
        public boolean isActive(Entity entity) {
            double dx = entity.posX - entity.prevPosX;
            double dz = entity.posZ - entity.prevPosZ;
            return dx * dx + dz * dz > 0.0001;
        }

        @Override
        public void stop(Entity entity) {
            entity.motionX = 0;
            entity.motionZ = 0;
            entity.velocityChanged = true;
        }
    };

    public static final MovementModeHandler FLIGHT = new MovementModeHandler() {

        @Override
        public String name() {
            return "flight";
        }

        @Override
        public void apply(Entity entity, MovementParams params) {
            double dx = params.targetX - entity.posX;
            double dy = params.targetY - entity.posY;
            double dz = params.targetZ - entity.posZ;
            double dist = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double scale = params.speed * 0.05 / dist;
            entity.motionX = dx * scale;
            entity.motionY = dy * scale;
            entity.motionZ = dz * scale;
            entity.velocityChanged = true;
        }

        @Override
        public boolean isActive(Entity entity) {
            double dx = entity.posX - entity.prevPosX;
            double dy = entity.posY - entity.prevPosY;
            double dz = entity.posZ - entity.prevPosZ;
            return dx * dx + dy * dy + dz * dz > 0.0001;
        }

        @Override
        public void stop(Entity entity) {
            entity.motionX = 0;
            entity.motionY = 0;
            entity.motionZ = 0;
            entity.velocityChanged = true;
        }
    };

    public static final MovementModeHandler TELEPORT = new MovementModeHandler() {

        @Override
        public String name() {
            return "teleport";
        }

        @Override
        public void apply(Entity entity, MovementParams params) {
            entity.setPosition(params.targetX, params.targetY, params.targetZ);
        }

        @Override
        public boolean isActive(Entity entity) {
            return false;
        }

        @Override
        public void stop(Entity entity) {}
    };
}
