package com.rtsbuilding.rtsbuilding.network.progression;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.rtsbuilding.rtsbuilding.client.ProgressionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S2C 进度状态响应消息。
 * 阶段5实现：Handler 接收并填充 ProgressionViewModel。
 */
public class S2CRtsProgressionStateMessage implements IMessage {

    private boolean enabled;
    private boolean homeSet;
    private int homePosX, homePosY, homePosZ;
    private String homeDimension;
    private long homeCooldownTicks;
    private int radiusBlocks;
    private int fluidCapacityBuckets;
    private int ultimineLimit;
    private boolean bypassHomeRadius;
    private List<String> unlockedNodes;
    private List<String> unlockableNodes;
    private List<String> costOverrides;

    public S2CRtsProgressionStateMessage() {
        unlockedNodes = new ArrayList<>();
        unlockableNodes = new ArrayList<>();
        costOverrides = new ArrayList<>();
    }

    public S2CRtsProgressionStateMessage(boolean enabled, boolean homeSet, int homePosX, int homePosY, int homePosZ,
        String homeDimension, long homeCooldownTicks, int radiusBlocks, int fluidCapacityBuckets, int ultimineLimit,
        boolean bypassHomeRadius, List<String> unlockedNodes, List<String> unlockableNodes,
        List<String> costOverrides) {
        this.enabled = enabled;
        this.homeSet = homeSet;
        this.homePosX = homePosX;
        this.homePosY = homePosY;
        this.homePosZ = homePosZ;
        this.homeDimension = homeDimension != null ? homeDimension : "";
        this.homeCooldownTicks = Math.max(0L, homeCooldownTicks);
        this.radiusBlocks = Math.max(0, radiusBlocks);
        this.fluidCapacityBuckets = Math.max(0, fluidCapacityBuckets);
        this.ultimineLimit = Math.max(0, ultimineLimit);
        this.bypassHomeRadius = bypassHomeRadius;
        this.unlockedNodes = unlockedNodes != null ? unlockedNodes : new ArrayList<>();
        this.unlockableNodes = unlockableNodes != null ? unlockableNodes : new ArrayList<>();
        this.costOverrides = costOverrides != null ? costOverrides : new ArrayList<>();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeBoolean(homeSet);
        buf.writeInt(homePosX);
        buf.writeInt(homePosY);
        buf.writeInt(homePosZ);
        writeUtf(buf, homeDimension, 128);
        buf.writeLong(homeCooldownTicks);
        buf.writeInt(radiusBlocks);
        buf.writeInt(fluidCapacityBuckets);
        buf.writeInt(ultimineLimit);
        buf.writeBoolean(bypassHomeRadius);
        int size = Math.min(unlockedNodes.size(), 256);
        buf.writeInt(size);
        for (int i = 0; i < size; i++) {
            writeUtf(buf, unlockedNodes.get(i), 128);
        }
        int unlockableSize = Math.min(unlockableNodes.size(), 256);
        buf.writeInt(unlockableSize);
        for (int i = 0; i < unlockableSize; i++) {
            writeUtf(buf, unlockableNodes.get(i), 128);
        }
        int overrideSize = Math.min(costOverrides.size(), 256);
        buf.writeInt(overrideSize);
        for (int i = 0; i < overrideSize; i++) {
            writeUtf(buf, costOverrides.get(i), 640);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        enabled = buf.readBoolean();
        homeSet = buf.readBoolean();
        homePosX = buf.readInt();
        homePosY = buf.readInt();
        homePosZ = buf.readInt();
        homeDimension = readUtf(buf, 128);
        homeCooldownTicks = buf.readLong();
        radiusBlocks = buf.readInt();
        fluidCapacityBuckets = buf.readInt();
        ultimineLimit = buf.readInt();
        bypassHomeRadius = buf.readBoolean();
        int size = Math.max(0, Math.min(buf.readInt(), 256));
        unlockedNodes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            unlockedNodes.add(readUtf(buf, 128));
        }
        int unlockableSize = Math.max(0, Math.min(buf.readInt(), 256));
        unlockableNodes = new ArrayList<>(unlockableSize);
        for (int i = 0; i < unlockableSize; i++) {
            unlockableNodes.add(readUtf(buf, 128));
        }
        int overrideSize = Math.max(0, Math.min(buf.readInt(), 256));
        costOverrides = new ArrayList<>(overrideSize);
        for (int i = 0; i < overrideSize; i++) {
            costOverrides.add(readUtf(buf, 640));
        }
    }

    private static void writeUtf(ByteBuf buf, String s, int maxBytes) {
        if (s == null) s = "";
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int len = Math.min(b.length, maxBytes);
        buf.writeInt(len);
        buf.writeBytes(b, 0, len);
    }

    private static String readUtf(ByteBuf buf, int maxBytes) {
        int len = Math.max(0, Math.min(buf.readInt(), maxBytes));
        byte[] b = new byte[len];
        if (len > 0) buf.readBytes(b);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    // Getters
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isHomeSet() {
        return homeSet;
    }

    public int getHomePosX() {
        return homePosX;
    }

    public int getHomePosY() {
        return homePosY;
    }

    public int getHomePosZ() {
        return homePosZ;
    }

    public String getHomeDimension() {
        return homeDimension;
    }

    public long getHomeCooldownTicks() {
        return homeCooldownTicks;
    }

    public int getRadiusBlocks() {
        return radiusBlocks;
    }

    public int getFluidCapacityBuckets() {
        return fluidCapacityBuckets;
    }

    public int getUltimineLimit() {
        return ultimineLimit;
    }

    public boolean isBypassHomeRadius() {
        return bypassHomeRadius;
    }

    public List<String> getUnlockedNodes() {
        return unlockedNodes;
    }

    public List<String> getUnlockableNodes() {
        return unlockableNodes;
    }

    public List<String> getCostOverrides() {
        return costOverrides;
    }

    public static class Handler implements IMessageHandler<S2CRtsProgressionStateMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsProgressionStateMessage msg, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return null;

            RtsClientState state = RtsClientState.get();
            ProgressionViewModel pvm = state.progression;

            // 填充家园信息
            pvm.homeSet = msg.isHomeSet();
            pvm.homeX = msg.getHomePosX();
            pvm.homeY = msg.getHomePosY();
            pvm.homeZ = msg.getHomePosZ();

            // 填充已解锁节点
            pvm.unlockedNodes.clear();
            for (String node : msg.getUnlockedNodes()) {
                pvm.unlockedNodes.put(node, true);
            }
            // 可解锁节点（标记为未解锁）
            for (String node : msg.getUnlockableNodes()) {
                if (!pvm.unlockedNodes.containsKey(node)) {
                    pvm.unlockedNodes.put(node, false);
                }
            }

            pvm.stateDirty = false;
            return null;
        }
    }
}
