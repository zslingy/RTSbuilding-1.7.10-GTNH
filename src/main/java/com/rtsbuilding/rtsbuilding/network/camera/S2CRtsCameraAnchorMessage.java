package com.rtsbuilding.rtsbuilding.network.camera;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class S2CRtsCameraAnchorMessage implements IMessage {

    private int cameraId;
    private double anchorX, anchorY, anchorZ;

    public S2CRtsCameraAnchorMessage() {}

    public S2CRtsCameraAnchorMessage(int cameraId, double anchorX, double anchorY, double anchorZ) {
        this.cameraId = cameraId;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(cameraId);
        buf.writeDouble(anchorX);
        buf.writeDouble(anchorY);
        buf.writeDouble(anchorZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        cameraId = buf.readInt();
        anchorX = buf.readDouble();
        anchorY = buf.readDouble();
        anchorZ = buf.readDouble();
    }

    public int getCameraId() {
        return cameraId;
    }

    public double getAnchorX() {
        return anchorX;
    }

    public double getAnchorY() {
        return anchorY;
    }

    public double getAnchorZ() {
        return anchorZ;
    }

    public static class Handler implements IMessageHandler<S2CRtsCameraAnchorMessage, IMessage> {

        @Override
        public IMessage onMessage(S2CRtsCameraAnchorMessage m, MessageContext ctx) {
            RtsClientState.get().camera.setAnchorPosition(m.anchorX, m.anchorY, m.anchorZ);
            return null;
        }
    }
}
