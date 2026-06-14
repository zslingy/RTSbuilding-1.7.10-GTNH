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

            RtsStorageManager.unlinkStorage(player);

            // 发送确认（客户端需要清除存储显示并回退到模拟数据）
            S2CRtsLinkStorageStatusMessage response = new S2CRtsLinkStorageStatusMessage(
                m.getPosX(),
                m.getPosY(),
                m.getPosZ(),
                false);
            RtsNetworkManager.NETWORK.sendTo(response, player);

            return null;
        }
    }
}
