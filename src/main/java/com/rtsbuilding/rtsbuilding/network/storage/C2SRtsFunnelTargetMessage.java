package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsFunnelTargetMessage implements IMessage {

    private int slot;

    public C2SRtsFunnelTargetMessage() {}

    public C2SRtsFunnelTargetMessage(int s) {
        slot = s;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(slot);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        slot = buf.readInt();
    }

    public int getSlot() {
        return slot;
    }

    /** Bug7修复：将指定存储槽位标记为漏斗吸入目标 */
    public static class Handler implements IMessageHandler<C2SRtsFunnelTargetMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsFunnelTargetMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsStorageSession session = RtsStorageManager.getSession(player);
            session.setFunnelTargetSlot(m.getSlot());
            session.setFunnelActive(true);

            RtsbuildingMod.LOGGER.debug(
                "FunnelTarget: player {} set funnel target slot {} (active={})",
                player.getDisplayName(),
                m.slot,
                session.isFunnelActive());
            return null;
        }
    }
}
