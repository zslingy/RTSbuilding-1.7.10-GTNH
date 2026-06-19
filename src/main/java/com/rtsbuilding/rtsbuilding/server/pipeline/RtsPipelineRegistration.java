package com.rtsbuilding.rtsbuilding.server.pipeline;

public final class RtsPipelineRegistration {

    private RtsPipelineRegistration() {}

    public static void registerAll() {
        com.rtsbuilding.rtsbuilding.server.pipeline.core.RtsPipelineRegistration.registerAll();
    }
}
