package com.rtsbuilding.rtsbuilding.blueprint.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.blueprint.server.BlueprintPlacementService;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SBlueprintPlaceMessage implements IMessage {

    private String fileName;
    private int anchorX, anchorY, anchorZ;
    private byte rotateSteps;

    public C2SBlueprintPlaceMessage() {}

    public C2SBlueprintPlaceMessage(String fileName, int anchorX, int anchorY, int anchorZ, byte rotateSteps) {
        this.fileName = fileName != null ? fileName : "";
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.rotateSteps = rotateSteps;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, fileName);
        buf.writeInt(anchorX);
        buf.writeInt(anchorY);
        buf.writeInt(anchorZ);
        buf.writeByte(rotateSteps);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        fileName = ByteBufUtils.readUTF8String(buf);
        anchorX = buf.readInt();
        anchorY = buf.readInt();
        anchorZ = buf.readInt();
        rotateSteps = buf.readByte();
    }

    public static class Handler implements IMessageHandler<C2SBlueprintPlaceMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SBlueprintPlaceMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;
            BlueprintPlacementService
                .queuePlacement(player, m.fileName, m.anchorX, m.anchorY, m.anchorZ, m.rotateSteps);
            return null;
        }
    }
}
