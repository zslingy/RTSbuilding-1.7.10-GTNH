package com.rtsbuilding.rtsbuilding.client.pathfinding;

import java.util.LinkedHashMap;
import java.util.Map;

public class RtsMovementModeRegistry {

    private static final Map<String, MovementModeHandler> MODES = new LinkedHashMap<>();

    static {
        register(BuiltinMovementModes.WALK);
        register(BuiltinMovementModes.FLIGHT);
        register(BuiltinMovementModes.TELEPORT);
    }

    public static void register(MovementModeHandler handler) {
        MODES.put(handler.name(), handler);
    }

    public static MovementModeHandler get(String name) {
        return MODES.getOrDefault(name, BuiltinMovementModes.WALK);
    }
}
