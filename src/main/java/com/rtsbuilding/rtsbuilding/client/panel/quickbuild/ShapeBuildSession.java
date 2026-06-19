package com.rtsbuilding.rtsbuilding.client.panel.quickbuild;

import com.rtsbuilding.rtsbuilding.common.BuildShape;
import com.rtsbuilding.rtsbuilding.util.BlockPos;

/**
 * 快速建造当前锚点会话。
 */
public class ShapeBuildSession {

    public BuildShape shape;
    public ShapeBuildPhase phase;
    public BlockPos pointA;
    public BlockPos pointB;
    public int clickedFace;
    public int placementFace;
    public int heightOffset;
    public int rotationDegrees;
    public boolean cylinder;

    public ShapeBuildSession(BuildShape shape, BlockPos pointA, int clickedFace, int rotationDegrees,
        boolean cylinder) {
        this.shape = shape;
        this.phase = ShapeBuildPhase.NEED_SECOND_POINT;
        this.pointA = pointA;
        this.clickedFace = clickedFace;
        this.placementFace = clickedFace;
        this.rotationDegrees = rotationDegrees;
        this.cylinder = cylinder;
    }
}
