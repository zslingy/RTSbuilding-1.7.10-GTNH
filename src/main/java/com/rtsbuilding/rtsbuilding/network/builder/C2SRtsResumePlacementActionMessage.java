package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService.PendingBlock;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsResumePlacementActionMessage implements IMessage {

    private int actionIndex;
    private boolean executeAll;

    public C2SRtsResumePlacementActionMessage() {}

    public C2SRtsResumePlacementActionMessage(int actionIndex, boolean executeAll) {
        this.actionIndex = actionIndex;
        this.executeAll = executeAll;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(actionIndex);
        buf.writeBoolean(executeAll);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        actionIndex = buf.readInt();
        executeAll = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<C2SRtsResumePlacementActionMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsResumePlacementActionMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsPendingPlacementService service = RtsPendingPlacementService.getInstance();
            java.util.List<PendingBlock> pendings = service.getPendings(player);

            if (m.executeAll) {
                for (PendingBlock pb : pendings) {
                    RtsStorageManager.tryConsumeBlock(player, pb.itemId, pb.meta, 1L);
                    RtsStorageManager.placeBlockDirect(
                        player,
                        pb.x,
                        pb.y,
                        pb.z,
                        pb.face,
                        0.5,
                        0.5,
                        0.5,
                        (byte) 0,
                        false,
                        true,
                        pb.itemId,
                        (ItemStack) null,
                        false);
                }
                service.clearPendings(player);
            } else if (m.actionIndex >= 0 && m.actionIndex < pendings.size()) {
                PendingBlock pb = pendings.get(m.actionIndex);
                RtsStorageManager.tryConsumeBlock(player, pb.itemId, pb.meta, 1L);
                RtsStorageManager.placeBlockDirect(
                    player,
                    pb.x,
                    pb.y,
                    pb.z,
                    pb.face,
                    0.5,
                    0.5,
                    0.5,
                    (byte) 0,
                    false,
                    true,
                    pb.itemId,
                    (ItemStack) null,
                    false);
                service.removePending(player, m.actionIndex);
            }
            return null;
        }
    }
}
