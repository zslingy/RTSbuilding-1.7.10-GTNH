package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsSetAutoStoreMessage implements IMessage {

    private boolean enabled;
    private int slot;

    public C2SRtsSetAutoStoreMessage() {}

    public C2SRtsSetAutoStoreMessage(boolean e, int s) {
        enabled = e;
        slot = s;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeInt(slot);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        enabled = buf.readBoolean();
        slot = buf.readInt();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSlot() {
        return slot;
    }

    public static class Handler implements IMessageHandler<C2SRtsSetAutoStoreMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSetAutoStoreMessage m, MessageContext c) {
            final EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;

            if (!RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
                RtsbuildingMod.LOGGER.debug(
                    "C2SRtsSetAutoStoreMessage: player {} not unlocked AUTO_STORE_MINED_DROPS",
                    player.getDisplayName());
                return null;
            }

            RtsStorageSession session = RtsStorageManager.getSession(player);
            if (session != null) {
                session.setAutoStoreMinedDrops(m.isEnabled());
                session.writeToNBT(
                    player.getEntityData()
                        .getCompoundTag("rtsbuilding_storage"));

                RtsNetworkManager.NETWORK.sendTo(new S2CRtsSetAutoStoreMessage(m.isEnabled()), player);

                RtsbuildingMod.LOGGER.debug(
                    "C2SRtsSetAutoStoreMessage: {} autoStoreMinedDrops={}",
                    player.getDisplayName(),
                    m.isEnabled());
            }
            return null;
        }
    }
}
