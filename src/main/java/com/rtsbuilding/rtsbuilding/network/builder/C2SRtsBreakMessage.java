package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.policy.RtsBreakPolicy;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsBreakMessage implements IMessage {

    private int posX, posY, posZ;
    private byte face;
    private boolean allowAdjacentFallback;

    public C2SRtsBreakMessage() {}

    public C2SRtsBreakMessage(int posX, int posY, int posZ, byte face, boolean allowAdjacentFallback) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.face = face;
        this.allowAdjacentFallback = allowAdjacentFallback;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(posX);
        buf.writeInt(posY);
        buf.writeInt(posZ);
        buf.writeByte(face);
        buf.writeBoolean(allowAdjacentFallback);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        posX = buf.readInt();
        posY = buf.readInt();
        posZ = buf.readInt();
        face = buf.readByte();
        allowAdjacentFallback = buf.readBoolean();
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

    public byte getFace() {
        return face;
    }

    public boolean isAllowAdjacentFallback() {
        return allowAdjacentFallback;
    }

    // ---- Handler（阶段B：服务端方块破坏） ----
    public static class Handler implements IMessageHandler<C2SRtsBreakMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsBreakMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            // 前置检查：必须在 RTS 相机模式中
            if (!RtsCameraManager.isActive(player)) {
                RtsbuildingMod.LOGGER.debug("C2SRtsBreakMessage: player {} not in RTS mode", player.getDisplayName());
                return null;
            }

            // 前置检查：必须在操作范围内
            if (!RtsCameraManager.isWithinActionRange(player, msg.posX, msg.posY, msg.posZ)) {
                RtsbuildingMod.LOGGER.debug(
                    "C2SRtsBreakMessage: position ({}, {}, {}) out of range for {}",
                    msg.posX,
                    msg.posY,
                    msg.posZ,
                    player.getDisplayName());
                return null;
            }

            // 策略检查
            World world = player.worldObj;
            if (!RtsBreakPolicy.canBreakBlock(player, world, msg.posX, msg.posY, msg.posZ)) {
                RtsbuildingMod.LOGGER.debug(
                    "C2SRtsBreakMessage: policy denied break at ({}, {}, {}) for {}",
                    msg.posX,
                    msg.posY,
                    msg.posZ,
                    player.getDisplayName());
                return null;
            }

            Block block = world.getBlock(msg.posX, msg.posY, msg.posZ);
            if (block == null || block.isAir(world, msg.posX, msg.posY, msg.posZ)) return null;

            int meta = world.getBlockMetadata(msg.posX, msg.posY, msg.posZ);

            // 播放破坏音效
            world.playSoundEffect(
                msg.posX + 0.5,
                msg.posY + 0.5,
                msg.posZ + 0.5,
                block.stepSound.getBreakSound(),
                (block.stepSound.getVolume() + 1.0F) / 2.0F,
                block.stepSound.getPitch() * 0.8F);

            // 破坏粒子效果
            world.playAuxSFX(2001, msg.posX, msg.posY, msg.posZ, Block.getIdFromBlock(block) + (meta << 12));

            // 掉落方块物品
            block.dropBlockAsItem(world, msg.posX, msg.posY, msg.posZ, meta, 0);

            // 设为空气
            world.setBlockToAir(msg.posX, msg.posY, msg.posZ);

            RtsbuildingMod.LOGGER.debug(
                "C2SRtsBreakMessage: broke {} at ({}, {}, {}) by {}",
                block.getUnlocalizedName(),
                msg.posX,
                msg.posY,
                msg.posZ,
                player.getDisplayName());

            return null;
        }
    }
}
