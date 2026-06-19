package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 链接存储消息。
 * 阶段6实现：Handler 将目标坐标绑定到玩家的 RtsStorageSession，
 * 优先检测 AE2 ME 网络，其次普通容器。
 * 
 * Bug3修复 (2026-06-12)：添加 linkMode 字段，区分 NORMAL(可存可取) 和 EXTRACT_ONLY(仅提取)。
 */
public class C2SRtsLinkStorageMessage implements IMessage {

    /** 绑定模式：0 = NORMAL（可存入可提取），1 = EXTRACT_ONLY（仅提取不存入） */
    public static final byte MODE_NORMAL = 0;
    public static final byte MODE_EXTRACT_ONLY = 1;

    private int posX, posY, posZ;
    private byte linkMode;

    public C2SRtsLinkStorageMessage() {}

    public C2SRtsLinkStorageMessage(int x, int y, int z) {
        this(x, y, z, MODE_NORMAL);
    }

    public C2SRtsLinkStorageMessage(int x, int y, int z, byte linkMode) {
        posX = x;
        posY = y;
        posZ = z;
        this.linkMode = linkMode;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(posX);
        buf.writeInt(posY);
        buf.writeInt(posZ);
        buf.writeByte(linkMode);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posX = buf.readInt();
        posY = buf.readInt();
        posZ = buf.readInt();
        linkMode = buf.readByte();
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

    public static class Handler implements IMessageHandler<C2SRtsLinkStorageMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsLinkStorageMessage m, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            boolean success = RtsStorageManager
                .linkStorage(player, m.getPosX(), m.getPosY(), m.getPosZ(), m.getLinkMode());

            if (success) {
                S2CRtsLinkStorageStatusMessage response = new S2CRtsLinkStorageStatusMessage(
                    m.getPosX(),
                    m.getPosY(),
                    m.getPosZ(),
                    true);
                RtsNetworkManager.NETWORK.sendTo(response, player);

                RtsStorageManager.sendStoragePage(player, 0, 0);
            } else {
                S2CRtsLinkStorageStatusMessage response = new S2CRtsLinkStorageStatusMessage(
                    m.getPosX(),
                    m.getPosY(),
                    m.getPosZ(),
                    false);
                RtsNetworkManager.NETWORK.sendTo(response, player);
            }

            return null;
        }
    }
}
