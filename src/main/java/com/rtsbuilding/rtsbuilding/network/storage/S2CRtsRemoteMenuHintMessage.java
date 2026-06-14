package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CRtsRemoteMenuHintMessage implements IMessage {

    private int posX, posY, posZ;

    public S2CRtsRemoteMenuHintMessage() {}

    public S2CRtsRemoteMenuHintMessage(int x, int y, int z) {
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

    public static class Handler implements IMessageHandler<S2CRtsRemoteMenuHintMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsRemoteMenuHintMessage m, MessageContext c) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null) return null;
            // 远程菜单提示——服务器通知客户端此处方块可远程打开
            // 完整实现需调用方块的 openGui 或自定义容器，当前仅记录
            if (mc.theWorld.blockExists(m.posX, m.posY, m.posZ)) {
                TileEntity te = mc.theWorld.getTileEntity(m.posX, m.posY, m.posZ);
                if (te != null) {
                    mc.thePlayer.openGui(te.getBlockType(), te.getBlockMetadata(), mc.theWorld, m.posX, m.posY, m.posZ);
                }
            }
            return null;
        }
    }
}
