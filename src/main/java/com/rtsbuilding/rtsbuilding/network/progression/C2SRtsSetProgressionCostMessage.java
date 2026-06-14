package com.rtsbuilding.rtsbuilding.network.progression;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 设置进度节点消耗覆盖消息（管理员命令）。
 * 阶段C实现：权限检查后覆盖节点消耗并同步所有玩家。
 */
public class C2SRtsSetProgressionCostMessage implements IMessage {

    private String nodeId;
    private String costsText;

    public C2SRtsSetProgressionCostMessage() {}

    public C2SRtsSetProgressionCostMessage(String nodeId, String costsText) {
        this.nodeId = nodeId != null ? nodeId : "";
        this.costsText = costsText != null ? costsText : "";
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeUtf(buf, nodeId, 128);
        writeUtf(buf, costsText, 512);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        nodeId = readUtf(buf, 128);
        costsText = readUtf(buf, 512);
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

    public String getCostsText() {
        return costsText;
    }

    public static class Handler implements IMessageHandler<C2SRtsSetProgressionCostMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSetProgressionCostMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // 管理员权限检查：op 级别 >= 2
            if (!player.mcServer.getConfigurationManager()
                .func_152596_g(player.getGameProfile())) {
                return null;
            }
            try {
                ResourceLocation nodeRL = new ResourceLocation(msg.nodeId);
                if (!RtsProgressionNodes.contains(nodeRL)) {
                    return null;
                }
                Config.setProgressionCostOverride(nodeRL.getResourcePath(), msg.costsText);
                // 同步所有在线玩家
                for (Object obj : player.mcServer.getConfigurationManager().playerEntityList) {
                    EntityPlayerMP p = (EntityPlayerMP) obj;
                    RtsProgressionManager.syncToPlayer(p);
                }
            } catch (Exception ignored) {
                // 无效的 ResourceLocation，静默忽略
            }
            return null;
        }
    }
}
