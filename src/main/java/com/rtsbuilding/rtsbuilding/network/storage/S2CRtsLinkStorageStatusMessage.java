package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.client.Minecraft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.util.BlockPos;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S2C 链接存储状态消息。
 * 服务端告知客户端 AE2/容器链接是否成功。
 */
public class S2CRtsLinkStorageStatusMessage implements IMessage {

    private int posX, posY, posZ;
    private boolean success;

    public S2CRtsLinkStorageStatusMessage() {}

    public S2CRtsLinkStorageStatusMessage(int x, int y, int z, boolean success) {
        posX = x;
        posY = y;
        posZ = z;
        this.success = success;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(posX);
        buf.writeInt(posY);
        buf.writeInt(posZ);
        buf.writeBoolean(success);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posX = buf.readInt();
        posY = buf.readInt();
        posZ = buf.readInt();
        success = buf.readBoolean();
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public int getPosZ() {
        return posZ;
    }

    public boolean isSuccess() {
        return success;
    }

    public static class Handler implements IMessageHandler<S2CRtsLinkStorageStatusMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsLinkStorageStatusMessage m, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return null;

            RtsClientState state = RtsClientState.get();
            if (state == null) return null;

            BlockPos pos = new BlockPos(m.getPosX(), m.getPosY(), m.getPosZ());
            if (m.isSuccess()) {
                boolean alreadyLinked = false;
                for (BlockPos existing : state.storage.linkedStoragePositions) {
                    if (existing != null && existing.equals(pos)) {
                        alreadyLinked = true;
                        break;
                    }
                }
                if (!alreadyLinked) {
                    state.storage.linkedStoragePositions.add(pos);
                }
                state.storage.linkedStorageCount = state.storage.linkedStoragePositions.size();
                state.storage.dirty = true;
                RtsbuildingMod.LOGGER
                    .info("Client: storage linked at ({}, {}, {})", m.getPosX(), m.getPosY(), m.getPosZ());
            } else {
                java.util.Iterator<BlockPos> it = state.storage.linkedStoragePositions.iterator();
                while (it.hasNext()) {
                    BlockPos existing = it.next();
                    if (existing != null && existing.equals(pos)) {
                        it.remove();
                    }
                }
                state.storage.linkedStorageCount = state.storage.linkedStoragePositions.size();
                state.storage.dirty = true;
                RtsbuildingMod.LOGGER
                    .info("Client: storage unlinked at ({}, {}, {})", m.getPosX(), m.getPosY(), m.getPosZ());
            }

            return null;
        }
    }
}
