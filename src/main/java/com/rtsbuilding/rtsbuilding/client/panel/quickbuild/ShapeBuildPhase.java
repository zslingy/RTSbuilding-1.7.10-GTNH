package com.rtsbuilding.rtsbuilding.client.panel.quickbuild;

/**
 * 快速建造多阶段锚点流程。
 */
public enum ShapeBuildPhase {
    IDLE,
    NEED_SECOND_POINT,
    NEED_HEIGHT,
    READY_CONFIRM
}
