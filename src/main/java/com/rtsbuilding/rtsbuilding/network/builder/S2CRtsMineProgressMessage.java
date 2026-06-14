package com.rtsbuilding.rtsbuilding.network.builder;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CRtsMineProgressMessage implements IMessage {

    private int posX, posY, posZ;
    private byte stage;

    public S2CRtsMineProgressMessage() {}

    public S2CRtsMineProgressMessage(int posX, int posY, int posZ, byte stage) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.stage = stage;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(posX);
        buf.writeInt(posY);
        buf.writeInt(posZ);
        buf.writeByte(stage);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posX = buf.readInt();
        posY = buf.readInt();
        posZ = buf.readInt();
        stage = buf.readByte();
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

    public byte getStage() {
        return stage;
    }

    public static class Handler implements IMessageHandler<S2CRtsMineProgressMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsMineProgressMessage msg, MessageContext ctx) {
            // Bug4修复：更新客户端挖掘进度状态，供 AnimationRenderer 渲染破坏进度覆盖层
            com.rtsbuilding.rtsbuilding.client.RtsClientState state = com.rtsbuilding.rtsbuilding.client.RtsClientState
                .get();
            if (state != null && state.interaction != null) {
                if (msg.getStage() >= 10) {
                    // 挖掘完成 → 清除状态
                    state.interaction.mineProgressX = -1;
                    state.interaction.mineProgressY = -1;
                    state.interaction.mineProgressZ = -1;
                    state.interaction.mineProgressStage = 0;
                } else {
                    state.interaction.mineProgressX = msg.getPosX();
                    state.interaction.mineProgressY = msg.getPosY();
                    state.interaction.mineProgressZ = msg.getPosZ();
                    state.interaction.mineProgressStage = msg.getStage();
                }
            }
            return null;
        }
    }
}
