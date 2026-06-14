package com.rtsbuilding.rtsbuilding.server.camera;

import java.util.UUID;

/**
 * RTS 相机会话记录 — 存储服务端相机状态。
 *
 * 替代原版 Java 14+ 的 record 类型，兼容 1.7.10。
 */
public final class Session {

    private final UUID cameraUuid;
    private final double anchorX, anchorY, anchorZ;
    private final double cameraPosX, cameraPosY, cameraPosZ;
    private final float yawDeg, pitchDeg;
    private final double heightOffset;
    private final boolean homeSelection;
    private final double maxRadius;
    private final boolean closeRangeAllowed;

    public Session(UUID cameraUuid, double anchorX, double anchorY, double anchorZ, double cameraPosX,
        double cameraPosY, double cameraPosZ, float yawDeg, float pitchDeg, double heightOffset, boolean homeSelection,
        double maxRadius, boolean closeRangeAllowed) {
        this.cameraUuid = cameraUuid;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.cameraPosX = cameraPosX;
        this.cameraPosY = cameraPosY;
        this.cameraPosZ = cameraPosZ;
        this.yawDeg = yawDeg;
        this.pitchDeg = pitchDeg;
        this.heightOffset = heightOffset;
        this.homeSelection = homeSelection;
        this.maxRadius = maxRadius;
        this.closeRangeAllowed = closeRangeAllowed;
    }

    public UUID cameraUuid() {
        return cameraUuid;
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorY() {
        return anchorY;
    }

    public double anchorZ() {
        return anchorZ;
    }

    public double cameraPosX() {
        return cameraPosX;
    }

    public double cameraPosY() {
        return cameraPosY;
    }

    public double cameraPosZ() {
        return cameraPosZ;
    }

    public float yawDeg() {
        return yawDeg;
    }

    public float pitchDeg() {
        return pitchDeg;
    }

    public double heightOffset() {
        return heightOffset;
    }

    public boolean homeSelection() {
        return homeSelection;
    }

    public double maxRadius() {
        return maxRadius;
    }

    public boolean closeRangeAllowed() {
        return closeRangeAllowed;
    }
}
