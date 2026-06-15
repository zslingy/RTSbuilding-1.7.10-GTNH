package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 解除链接存储消息。
 * 阶段6实现：Handler 清除玩家的存储绑定。
 */
public class C2SRtsUnlinkStorageMessage implements IMessage {

    private int posX, posY, posZ;

    public C2SRtsUnlinkStorageMessage() {}

    public C2SRtsUnlinkStorageMessage(int x, int y, int z) {
        posX = x;
        posY = y;
        posZ = z;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(posX);
        buf.writeInt(posY);
        buf.writeInt(posZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posX = buf.readInt();
        posY = buf.readInt();
        posZ = buf.readInt();
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

    public static class Handler implements IMessageHandler<C2SRtsUnlinkStorageMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsUnlinkStorageMessage m, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // 解除指定坐标的链接（支持容器和 AE2）
            RtsStorageManager.unlinkStorageAt(player, m.getPosX(), m.getPosY(), m.getPosZ());

            // 发送状态确认
            S2CRtsLinkStorageStatusMessage response = new S2CRtsLinkStorageStatusMessage(
                m.getPosX(),
                m.getPosY(),
                m.getPosZ(),
                false);
            RtsNetworkManager.NETWORK.sendTo(response, player);

            // 发送更新后的存储页面（携带最新 linkedEntries）
            RtsStorageManager.sendStoragePage(player, 0, 0);

            return null;
        }
    }
}
