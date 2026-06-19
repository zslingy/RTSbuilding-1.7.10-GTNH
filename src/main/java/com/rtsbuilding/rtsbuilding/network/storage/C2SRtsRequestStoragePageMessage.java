package com.rtsbuilding.rtsbuilding.network.storage;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 请求存储页面消息。
 * 包含排序模式（sortMode），服务端根据该字段排序后返回结果。
 */
public class C2SRtsRequestStoragePageMessage implements IMessage {

    private int page, windowId;
    private String sortMode;

    public C2SRtsRequestStoragePageMessage() {}

    public C2SRtsRequestStoragePageMessage(int p, int w) {
        this(p, w, "name_asc");
    }

    public C2SRtsRequestStoragePageMessage(int p, int w, String sort) {
        page = p;
        windowId = w;
        sortMode = sort != null ? sort : "name_asc";
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(page);
        buf.writeInt(windowId);
        byte[] bytes = sortMode.getBytes();
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        page = buf.readInt();
        windowId = buf.readInt();
        short len = buf.readShort();
        if (len > 0 && len < 64) {
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            sortMode = new String(bytes);
        } else {
            sortMode = "name_asc";
        }
    }

    public int getPage() {
        return page;
    }

    public int getWindowId() {
        return windowId;
    }

    public String getSortMode() {
        return sortMode;
    }

    public static class Handler implements IMessageHandler<C2SRtsRequestStoragePageMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsRequestStoragePageMessage m, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsStorageManager.sendStoragePage(player, m.getPage(), m.getWindowId(), m.getSortMode());
            return null;
        }
    }
}
