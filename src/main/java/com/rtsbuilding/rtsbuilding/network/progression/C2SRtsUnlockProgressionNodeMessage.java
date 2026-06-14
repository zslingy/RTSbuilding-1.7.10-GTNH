package com.rtsbuilding.rtsbuilding.network.progression;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 解锁进度节点消息。
 * 阶段C实现：Handler 解析 ResourceLocation 并调用进度系统解锁。
 */
public class C2SRtsUnlockProgressionNodeMessage implements IMessage {

    private String nodeId;

    public C2SRtsUnlockProgressionNodeMessage() {}

    public C2SRtsUnlockProgressionNodeMessage(String nodeId) {
        this.nodeId = nodeId != null ? nodeId : "";
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf(buf, nodeId, 128);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        nodeId = readUtf(buf, 128);
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

    public String getNodeId() {
        return nodeId;
    }

    public static class Handler implements IMessageHandler<C2SRtsUnlockProgressionNodeMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsUnlockProgressionNodeMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            try {
                ResourceLocation nodeRL = new ResourceLocation(msg.nodeId);
                RtsProgressionManager.unlockNode(player, nodeRL)
                    .notifyPlayer(player);
            } catch (Exception ignored) {
                // 无效的 ResourceLocation，静默忽略
            }
            return null;
        }
    }
}
