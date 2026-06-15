package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 更新已链接存储设置消息。
 * 对齐原版 C2SRtsUpdateLinkedStoragePayload，支持修改模式和优先级。
 */
public class C2SRtsUpdateLinkedStorageMessage implements IMessage {

    private int posX, posY, posZ;
    private byte linkMode;
    private int priority;

    public C2SRtsUpdateLinkedStorageMessage() {}

    public C2SRtsUpdateLinkedStorageMessage(int x, int y, int z, byte linkMode, int priority) {
        posX = x;
        posY = y;
        posZ = z;
        this.linkMode = linkMode;
        this.priority = priority;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(posX);
        buf.writeInt(posY);
        buf.writeInt(posZ);
        buf.writeByte(linkMode);
        buf.writeInt(priority);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posX = buf.readInt();
        posY = buf.readInt();
        posZ = buf.readInt();
        linkMode = buf.readByte();
        priority = buf.readInt();
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

    public byte getLinkMode() {
        return linkMode;
    }

    public int getPriority() {
        return priority;
    }

    public static class Handler implements IMessageHandler<C2SRtsUpdateLinkedStorageMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsUpdateLinkedStorageMessage m, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsStorageSession session = RtsStorageManager.getSession(player);
            int dimId = player.worldObj.provider.dimensionId;
            LinkedStorageRef ref = new LinkedStorageRef(dimId, m.getPosX(), m.getPosY(), m.getPosZ());

            if (!session.hasLinkedStorage(ref)) {
                RtsbuildingMod.LOGGER.debug(
                    "C2SRtsUpdateLinkedStorage: ref not found at ({}, {}, {}) for {}",
                    m.getPosX(),
                    m.getPosY(),
                    m.getPosZ(),
                    player.getDisplayName());
                return null;
            }

            // 更新模式和优先级
            session.setLinkedMode(ref, m.getLinkMode());
            session.setLinkedPriority(ref, m.getPriority());
            RtsStorageManager.saveSessionNBT(player);

            RtsbuildingMod.LOGGER.debug(
                "C2SRtsUpdateLinkedStorage: updated ({}, {}, {}) mode={} priority={} for {}",
                m.getPosX(),
                m.getPosY(),
                m.getPosZ(),
                m.getLinkMode(),
                m.getPriority(),
                player.getDisplayName());

            // 发送更新后的存储页面（携带最新 linkedEntries）
            RtsStorageManager.sendStoragePage(player, 0, 0);

            return null;
        }
    }
}
