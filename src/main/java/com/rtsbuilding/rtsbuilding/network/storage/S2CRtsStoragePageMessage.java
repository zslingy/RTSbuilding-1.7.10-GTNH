package com.rtsbuilding.rtsbuilding.network.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.client.InteractionViewModel;
import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel.StorageEntry;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * S2C 存储页面响应消息。
 * 阶段5实现：Handler 接收服务端数据并填充 StorageViewModel。
 * 多容器支持：携带 linkedEntries 并行数组，客户端用于显示已链接存储列表。
 */
public class S2CRtsStoragePageMessage implements IMessage {

    private int page, totalPages, windowId;
    private List<ItemStack> stacks = new ArrayList<>();
    private int[] linkedDimIds = new int[0];
    private int[] linkedX = new int[0];
    private int[] linkedY = new int[0];
    private int[] linkedZ = new int[0];
    private byte[] linkedModes = new byte[0];
    private int[] linkedPriorities = new int[0];
    private int[] guiBindingX = new int[0];
    private int[] guiBindingY = new int[0];
    private int[] guiBindingZ = new int[0];
    private int[] guiBindingDimIds = new int[0];

    public S2CRtsStoragePageMessage() {}

    public S2CRtsStoragePageMessage(int page, int totalPages, int windowId, List<ItemStack> stacks) {
        this(
            page,
            totalPages,
            windowId,
            stacks,
            new int[0],
            new int[0],
            new int[0],
            new int[0],
            new byte[0],
            new int[0],
            new int[0],
            new int[0],
            new int[0],
            new int[0]);
    }

    public S2CRtsStoragePageMessage(int page, int totalPages, int windowId, List<ItemStack> stacks, int[] linkedDimIds,
        int[] linkedX, int[] linkedY, int[] linkedZ, byte[] linkedModes, int[] linkedPriorities, int[] guiBindingX,
        int[] guiBindingY, int[] guiBindingZ, int[] guiBindingDimIds) {
        this.page = page;
        this.totalPages = totalPages;
        this.windowId = windowId;
        this.stacks = stacks != null ? stacks : new ArrayList<>();
        this.linkedDimIds = linkedDimIds != null ? linkedDimIds : new int[0];
        this.linkedX = linkedX != null ? linkedX : new int[0];
        this.linkedY = linkedY != null ? linkedY : new int[0];
        this.linkedZ = linkedZ != null ? linkedZ : new int[0];
        this.linkedModes = linkedModes != null ? linkedModes : new byte[0];
        this.linkedPriorities = linkedPriorities != null ? linkedPriorities : new int[0];
        this.guiBindingX = guiBindingX != null ? guiBindingX : new int[0];
        this.guiBindingY = guiBindingY != null ? guiBindingY : new int[0];
        this.guiBindingZ = guiBindingZ != null ? guiBindingZ : new int[0];
        this.guiBindingDimIds = guiBindingDimIds != null ? guiBindingDimIds : new int[0];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(page);
        buf.writeInt(totalPages);
        buf.writeInt(windowId);
        // ItemStacks
        int size = Math.min(stacks.size(), 65536);
        buf.writeInt(size);
        for (int i = 0; i < size; i++) {
            ItemStack s = stacks.get(i);
            buf.writeBoolean(s != null);
            if (s != null) ByteBufUtils.writeItemStack(buf, s);
        }
        // Linked entries
        int linkedCount = Math.min(linkedDimIds.length, 256);
        buf.writeInt(linkedCount);
        for (int i = 0; i < linkedCount; i++) {
            buf.writeInt(linkedDimIds[i]);
            buf.writeInt(linkedX[i]);
            buf.writeInt(linkedY[i]);
            buf.writeInt(linkedZ[i]);
            buf.writeByte(linkedModes[i]);
            buf.writeInt(linkedPriorities[i]);
        }
        // GUI bindings (8 slots)
        int guiBindCount = Math.min(guiBindingX.length, 8);
        buf.writeInt(guiBindCount);
        for (int i = 0; i < guiBindCount; i++) {
            buf.writeInt(guiBindingX[i]);
            buf.writeInt(guiBindingY[i]);
            buf.writeInt(guiBindingZ[i]);
            buf.writeInt(guiBindingDimIds[i]);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        page = buf.readInt();
        totalPages = buf.readInt();
        windowId = buf.readInt();
        // ItemStacks
        int size = Math.max(0, Math.min(buf.readInt(), 65536));
        stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(buf.readBoolean() ? ByteBufUtils.readItemStack(buf) : null);
        }
        // Linked entries
        int linkedCount = Math.max(0, Math.min(buf.readInt(), 256));
        linkedDimIds = new int[linkedCount];
        linkedX = new int[linkedCount];
        linkedY = new int[linkedCount];
        linkedZ = new int[linkedCount];
        linkedModes = new byte[linkedCount];
        linkedPriorities = new int[linkedCount];
        for (int i = 0; i < linkedCount; i++) {
            linkedDimIds[i] = buf.readInt();
            linkedX[i] = buf.readInt();
            linkedY[i] = buf.readInt();
            linkedZ[i] = buf.readInt();
            linkedModes[i] = buf.readByte();
            linkedPriorities[i] = buf.readInt();
        }
        // GUI bindings
        int guiBindCount = Math.max(0, Math.min(buf.readInt(), 8));
        guiBindingX = new int[guiBindCount];
        guiBindingY = new int[guiBindCount];
        guiBindingZ = new int[guiBindCount];
        guiBindingDimIds = new int[guiBindCount];
        for (int i = 0; i < guiBindCount; i++) {
            guiBindingX[i] = buf.readInt();
            guiBindingY[i] = buf.readInt();
            guiBindingZ[i] = buf.readInt();
            guiBindingDimIds[i] = buf.readInt();
        }
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getWindowId() {
        return windowId;
    }

    public List<ItemStack> getStacks() {
        return stacks;
    }

    public int[] getLinkedDimIds() {
        return linkedDimIds;
    }

    public int[] getLinkedX() {
        return linkedX;
    }

    public int[] getLinkedY() {
        return linkedY;
    }

    public int[] getLinkedZ() {
        return linkedZ;
    }

    public byte[] getLinkedModes() {
        return linkedModes;
    }

    public int[] getLinkedPriorities() {
        return linkedPriorities;
    }

    public int getLinkedCount() {
        return linkedDimIds.length;
    }

    public int[] getGuiBindingX() {
        return guiBindingX;
    }

    public int[] getGuiBindingY() {
        return guiBindingY;
    }

    public int[] getGuiBindingZ() {
        return guiBindingZ;
    }

    public int[] getGuiBindingDimIds() {
        return guiBindingDimIds;
    }

    public int getGuiBindingCount() {
        return guiBindingX.length;
    }

    public static class Handler implements IMessageHandler<S2CRtsStoragePageMessage, IMessage> {

        @Override
        public IMessage onMessage(S2CRtsStoragePageMessage msg, MessageContext ctx) {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null) return null;

                RtsClientState state = RtsClientState.get();
                StorageViewModel svm = state.storage;

                // 更新分页元数据
                svm.currentPage = msg.getPage();
                svm.totalPages = Math.max(1, msg.getTotalPages());

                // 将 ItemStack 转换为 StorageEntry（直接存储 ItemStack，避免注册表查找失败）
                svm.entries.clear();
                for (ItemStack stack : msg.getStacks()) {
                    if (stack == null || stack.getItem() == null) continue;
                    String displayName = stack.getDisplayName();
                    svm.entries.add(new StorageEntry(stack, stack.stackSize, displayName, false));
                }

                // 更新已链接存储条目列表
                svm.linkedEntries.clear();
                svm.linkedStoragePositions.clear();
                int linkedCount = msg.getLinkedCount();
                int[] dimIds = msg.getLinkedDimIds();
                int[] lx = msg.getLinkedX();
                int[] ly = msg.getLinkedY();
                int[] lz = msg.getLinkedZ();
                byte[] modes = msg.getLinkedModes();
                int[] priorities = msg.getLinkedPriorities();
                for (int i = 0; i < linkedCount; i++) {
                    svm.linkedEntries.add(
                        new StorageViewModel.LinkedStorageEntry(
                            dimIds[i],
                            lx[i],
                            ly[i],
                            lz[i],
                            modes[i],
                            priorities[i]));
                    svm.linkedStoragePositions.add(new com.rtsbuilding.rtsbuilding.util.BlockPos(lx[i], ly[i], lz[i]));
                }
                svm.linkedStorageCount = linkedCount;

                // 更新 GUI 绑定数据
                InteractionViewModel ivm = state.interaction;
                ivm.guiBindings.clear();
                int guiBindCount = msg.getGuiBindingCount();
                int[] gx = msg.getGuiBindingX();
                int[] gy = msg.getGuiBindingY();
                int[] gz = msg.getGuiBindingZ();
                int[] gDim = msg.getGuiBindingDimIds();
                for (int i = 0; i < guiBindCount; i++) {
                    ivm.guiBindings.add(new InteractionViewModel.GuiBindingSlot(gx[i], gy[i], gz[i], gDim[i]));
                }

                // 清除 dirty 标记
                svm.dirty = false;

                com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER.debug(
                    "S2CRtsStoragePage: received page={}, totalPages={}, stacks={}, entries={}, linkedEntries={}",
                    msg.getPage(),
                    msg.getTotalPages(),
                    msg.getStacks()
                        .size(),
                    svm.entries.size(),
                    linkedCount);
            } catch (Exception e) {
                com.rtsbuilding.rtsbuilding.RtsbuildingMod.LOGGER
                    .error("S2CRtsStoragePage: handler error: {}", e.toString());
            }

            return null;
        }
    }
}
