package com.rtsbuilding.rtsbuilding.network.progression;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * C2S 设置生存进度开关消息（管理员命令）。
 * 阶段C实现：权限检查后切换全局进度开关并同步所有玩家。
 */
public class C2SRtsSetSurvivalProgressionMessage implements IMessage {

    private boolean enabled;

    public C2SRtsSetSurvivalProgressionMessage() {}

    public C2SRtsSetSurvivalProgressionMessage(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        enabled = buf.readBoolean();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static class Handler implements IMessageHandler<C2SRtsSetSurvivalProgressionMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsSetSurvivalProgressionMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            // 管理员权限检查：op 级别 >= 2
            if (!player.mcServer.getConfigurationManager()
                .func_152596_g(player.getGameProfile())) {
                return null;
            }
            Config.setSurvivalProgressionEnabled(msg.enabled);
            // 同步所有在线玩家
            for (Object obj : player.mcServer.getConfigurationManager().playerEntityList) {
                EntityPlayerMP p = (EntityPlayerMP) obj;
                RtsProgressionManager.syncToPlayer(p);
            }
            return null;
        }
    }
}
