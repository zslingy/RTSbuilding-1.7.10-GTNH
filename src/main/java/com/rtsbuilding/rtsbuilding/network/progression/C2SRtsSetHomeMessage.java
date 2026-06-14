package com.rtsbuilding.rtsbuilding.network.progression;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 设置家园位置消息。
 * 阶段C实现：Handler 调用 RtsProgressionManager.commitHome，
 * 成功后恢复普通 RTS 相机模式。
 */
public class C2SRtsSetHomeMessage implements IMessage {

    private int posX, posY, posZ;

    public C2SRtsSetHomeMessage() {}

    public C2SRtsSetHomeMessage(int posX, int posY, int posZ) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
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

    public static class Handler implements IMessageHandler<C2SRtsSetHomeMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSetHomeMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            if (RtsProgressionManager.commitHome(player, msg.posX, msg.posY, msg.posZ)) {
                RtsCameraManager.restartNormalFromHomeSelection(player);
            }
            return null;
        }
    }
}
