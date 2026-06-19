package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService.PendingBlock;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsSubmitPendingMessage implements IMessage {

    public C2SRtsSubmitPendingMessage() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<C2SRtsSubmitPendingMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSubmitPendingMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;

            java.util.List<PendingBlock> pendings = RtsPendingPlacementService.getInstance()
                .getPendings(player);
            RtsPendingPlacementService.getInstance()
                .clearPendings(player);
            return new S2CRtsResumePlacementScanMessage(
                0,
                new int[0],
                new int[0],
                new int[0],
                new String[0],
                new int[0]);
        }
    }
}
