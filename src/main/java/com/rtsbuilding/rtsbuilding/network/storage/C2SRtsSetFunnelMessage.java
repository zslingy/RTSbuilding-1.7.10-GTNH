package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsSetFunnelMessage implements IMessage {

    private int inventorySlot, storageSlot;
    /** Bug5修复：漏斗目标世界坐标（鼠标指向位置） */
    private double targetX, targetY, targetZ;
    private boolean hasPosition;
    private int rangeSize = 5;

    public C2SRtsSetFunnelMessage() {}

    public C2SRtsSetFunnelMessage(int i, int s) {
        this(i, s, 0, 0, 0, false);
    }

    public C2SRtsSetFunnelMessage(int i, int s, double tx, double ty, double tz, boolean hasPos) {
        this(i, s, tx, ty, tz, hasPos, 5);
    }

    public C2SRtsSetFunnelMessage(int i, int s, double tx, double ty, double tz, boolean hasPos, int rangeSize) {
        inventorySlot = i;
        storageSlot = s;
        targetX = tx;
        targetY = ty;
        targetZ = tz;
        hasPosition = hasPos;
        this.rangeSize = clampRange(rangeSize);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(inventorySlot);
        buf.writeInt(storageSlot);
        buf.writeBoolean(hasPosition);
        if (hasPosition) {
            buf.writeDouble(targetX);
            buf.writeDouble(targetY);
            buf.writeDouble(targetZ);
        }
        buf.writeInt(rangeSize);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        inventorySlot = buf.readInt();
        storageSlot = buf.readInt();
        hasPosition = buf.readBoolean();
        if (hasPosition) {
            targetX = buf.readDouble();
            targetY = buf.readDouble();
            targetZ = buf.readDouble();
        }
        if (buf.readableBytes() >= 4) {
            rangeSize = clampRange(buf.readInt());
        } else {
            rangeSize = 5;
        }
    }

    public int getInventorySlot() {
        return inventorySlot;
    }

    public int getStorageSlot() {
        return storageSlot;
    }

    public double getTargetX() {
        return targetX;
    }

    public double getTargetY() {
        return targetY;
    }

    public double getTargetZ() {
        return targetZ;
    }

    public boolean hasPosition() {
        return hasPosition;
    }

    public int getRangeSize() {
        return rangeSize;
    }

    private static int clampRange(int rangeSize) {
        return Math.max(1, Math.min(16, rangeSize));
    }

    /**
     * Bug7修复：服务端接收漏斗开启/关闭请求，更新 RtsStorageSession。
     * inventorySlot: 0=关闭, >=0=开启（指定来源物品栏槽位）
     * storageSlot: 目标存储槽位（当前未使用，留作扩展）
     */
    public static class Handler implements IMessageHandler<C2SRtsSetFunnelMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSetFunnelMessage m, MessageContext c) {
            EntityPlayerMP player = c.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsStorageSession session = RtsStorageManager.getSession(player);
            if (m.getInventorySlot() >= 0) {
                session.setFunnelActive(true);
                session.setFunnelTargetSlot(m.getInventorySlot());
                session.setFunnelRangeSize(m.getRangeSize());
                if (m.hasPosition()) {
                    session.setFunnelTargetPos(m.getTargetX(), m.getTargetY(), m.getTargetZ());
                    RtsbuildingMod.LOGGER.debug(
                        "Funnel enabled for {} (source slot {}, target {},{},{})",
                        player.getDisplayName(),
                        m.getInventorySlot(),
                        m.getTargetX(),
                        m.getTargetY(),
                        m.getTargetZ());
                } else {
                    RtsbuildingMod.LOGGER
                        .debug("Funnel enabled for {} (source slot {})", player.getDisplayName(), m.getInventorySlot());
                }
            } else {
                session.setFunnelActive(false);
                session.setFunnelTargetSlot(-1);
                RtsbuildingMod.LOGGER.debug("Funnel disabled for {}", player.getDisplayName());
            }
            return null;
        }
    }
}
