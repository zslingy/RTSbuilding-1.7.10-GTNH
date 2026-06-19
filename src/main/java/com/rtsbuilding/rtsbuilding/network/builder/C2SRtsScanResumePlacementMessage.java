package com.rtsbuilding.rtsbuilding.network.builder;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService.RecoveryBlock;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsScanResumePlacementMessage implements IMessage {

    public C2SRtsScanResumePlacementMessage() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<C2SRtsScanResumePlacementMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsScanResumePlacementMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;

            List<RecoveryBlock> blocks = RtsPlacedRecoveryService.getInstance()
                .scanRecoverable(player);
            int count = blocks.size();
            int[] xs = new int[count];
            int[] ys = new int[count];
            int[] zs = new int[count];
            String[] ids = new String[count];
            int[] metas = new int[count];
            for (int i = 0; i < count; i++) {
                RecoveryBlock rb = blocks.get(i);
                xs[i] = rb.x;
                ys[i] = rb.y;
                zs[i] = rb.z;
                ids[i] = rb.itemId;
                metas[i] = rb.meta;
            }
            return new S2CRtsResumePlacementScanMessage(count, xs, ys, zs, ids, metas);
        }
    }
}
