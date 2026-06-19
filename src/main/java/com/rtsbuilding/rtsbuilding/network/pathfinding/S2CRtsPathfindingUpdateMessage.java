package com.rtsbuilding.rtsbuilding.network.pathfinding;

import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class S2CRtsPathfindingUpdateMessage implements IMessage {

    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private boolean arrived;

    public S2CRtsPathfindingUpdateMessage() {}

    public S2CRtsPathfindingUpdateMessage(double cameraX, double cameraY, double cameraZ, boolean arrived) {
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.arrived = arrived;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(cameraX);
        buf.writeDouble(cameraY);
        buf.writeDouble(cameraZ);
        buf.writeBoolean(arrived);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        cameraX = buf.readDouble();
        cameraY = buf.readDouble();
        cameraZ = buf.readDouble();
        arrived = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<S2CRtsPathfindingUpdateMessage, IMessage> {

        @Override
        public IMessage onMessage(S2CRtsPathfindingUpdateMessage msg, MessageContext ctx) {
            RtsClientPathfinding.onPathfindingUpdate(msg.cameraX, msg.cameraY, msg.cameraZ, msg.arrived);
            return null;
        }
    }
}
