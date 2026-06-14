package com.rtsbuilding.rtsbuilding.network.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel;
import com.rtsbuilding.rtsbuilding.client.StorageViewModel.StorageEntry;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * S2C 存储页面响应消息。
 * 阶段5实现：Handler 接收服务端数据并填充 StorageViewModel。
 */
public class S2CRtsStoragePageMessage implements IMessage {

    private int page, totalPages, windowId;
    private List<ItemStack> stacks = new ArrayList<>();

    public S2CRtsStoragePageMessage() {}

    public S2CRtsStoragePageMessage(int page, int totalPages, int windowId, List<ItemStack> stacks) {
        this.page = page;
        this.totalPages = totalPages;
        this.windowId = windowId;
        this.stacks = stacks != null ? stacks : new ArrayList<>();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(page);
        buf.writeInt(totalPages);
        buf.writeInt(windowId);
        int size = Math.min(stacks.size(), 65536);
        buf.writeInt(size);
        for (int i = 0; i < size; i++) {
            ItemStack s = stacks.get(i);
            buf.writeBoolean(s != null);
            if (s != null) ByteBufUtils.writeItemStack(buf, s);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        page = buf.readInt();
        totalPages = buf.readInt();
        windowId = buf.readInt();
        int size = Math.max(0, Math.min(buf.readInt(), 65536));
        stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(buf.readBoolean() ? ByteBufUtils.readItemStack(buf) : null);
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

    public static class Handler implements IMessageHandler<S2CRtsStoragePageMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsStoragePageMessage msg, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return null;

            RtsClientState state = RtsClientState.get();
            StorageViewModel svm = state.storage;

            // 更新分页元数据
            svm.currentPage = msg.getPage();
            svm.totalPages = Math.max(1, msg.getTotalPages());

            // 将 ItemStack 转换为 StorageEntry
            svm.entries.clear();
            for (ItemStack stack : msg.getStacks()) {
                if (stack == null || stack.getItem() == null) continue;
                String itemId = (String) cpw.mods.fml.common.registry.GameData.getItemRegistry()
                    .getNameForObject(stack.getItem());
                if (itemId == null) itemId = "unknown";
                String displayName = stack.getDisplayName();
                svm.entries.add(new StorageEntry(itemId, stack.getItemDamage(), stack.stackSize, displayName, false));
            }

            // 清除 dirty 标记
            svm.dirty = false;

            return null;
        }
    }
}
