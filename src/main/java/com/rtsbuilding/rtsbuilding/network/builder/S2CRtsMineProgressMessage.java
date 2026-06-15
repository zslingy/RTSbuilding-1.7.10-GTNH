package com.rtsbuilding.rtsbuilding.network.builder;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CRtsMineProgressMessage implements IMessage {

    /** RTS 挖掘进度渲染用实体 ID（ASCII "RTS"），与原版 RTSbuilding 1.21.1 一致 */
    private static final int RTS_MINE_RENDER_ID = 0x525453;

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
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.theWorld == null) return null;

            int x = msg.getPosX(), y = msg.getPosY(), z = msg.getPosZ();
            int stage = msg.getStage();

            // 更新 InteractionViewModel 进度状态（供 UltiminePanel 进度条显示）
            com.rtsbuilding.rtsbuilding.client.InteractionViewModel ivm = com.rtsbuilding.rtsbuilding.client.RtsClientState
                .get().interaction;

            if (stage < 0) {
                // stage < 0 表示清除裂纹动画(abort)
                mc.theWorld.destroyBlockInWorldPartially(RTS_MINE_RENDER_ID, x, y, z, -1);
                ivm.mineProgressX = -1;
                ivm.mineProgressY = -1;
                ivm.mineProgressZ = -1;
                ivm.mineProgressStage = 0;
            } else if (stage >= 10) {
                // 挖掘完成 → 清除裂纹
                mc.theWorld.destroyBlockInWorldPartially(RTS_MINE_RENDER_ID, x, y, z, -1);
                ivm.mineProgressX = -1;
                ivm.mineProgressY = -1;
                ivm.mineProgressZ = -1;
                ivm.mineProgressStage = 0;
            } else {
                // 更新裂纹阶段 (0-9)
                mc.theWorld.destroyBlockInWorldPartially(RTS_MINE_RENDER_ID, x, y, z, Math.min(9, stage));
                ivm.mineProgressX = x;
                ivm.mineProgressY = y;
                ivm.mineProgressZ = z;
                ivm.mineProgressStage = (byte) stage;
            }
            return null;
        }
    }
}
