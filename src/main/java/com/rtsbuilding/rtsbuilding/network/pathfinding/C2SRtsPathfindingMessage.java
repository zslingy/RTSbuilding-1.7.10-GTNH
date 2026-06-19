package com.rtsbuilding.rtsbuilding.network.pathfinding;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.pathfinding.RtsPathfindingService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsPathfindingMessage implements IMessage {

    private double targetX;
    private double targetY;
    private double targetZ;
    private String mode;

    public C2SRtsPathfindingMessage() {}

    public C2SRtsPathfindingMessage(double targetX, double targetY, double targetZ, String mode) {
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.mode = mode;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(targetX);
        buf.writeDouble(targetY);
        buf.writeDouble(targetZ);
        byte[] modeBytes = (mode != null ? mode : "walk").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeByte(modeBytes.length);
        buf.writeBytes(modeBytes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetX = buf.readDouble();
        targetY = buf.readDouble();
        targetZ = buf.readDouble();
        int len = buf.readByte();
        byte[] modeBytes = new byte[len];
        buf.readBytes(modeBytes);
        mode = new String(modeBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<C2SRtsPathfindingMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsPathfindingMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            RtsPathfindingService.startPathfinding(player, msg.targetX, msg.targetY, msg.targetZ, msg.mode);
            return null;
        }
    }
}
