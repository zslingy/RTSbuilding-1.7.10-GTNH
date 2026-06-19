package com.rtsbuilding.rtsbuilding.network.builder;

import java.util.List;
import java.util.Optional;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsPauseWorkflowMessage implements IMessage {

    private int entryId;
    private boolean paused;

    public C2SRtsPauseWorkflowMessage() {}

    public C2SRtsPauseWorkflowMessage(int entryId, boolean paused) {
        this.entryId = entryId;
        this.paused = paused;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entryId);
        buf.writeBoolean(paused);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entryId = buf.readInt();
        paused = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<C2SRtsPauseWorkflowMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsPauseWorkflowMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;
            RtsWorkflowEngine engine = RtsWorkflowEngine.getInstance();
            if (msg.entryId == -1) {
                List<RtsWorkflowStatus> statuses = engine.getAllProgress(player);
                for (RtsWorkflowStatus status : statuses) apply(engine.from(player, status.entryId()), msg.paused);
            } else {
                apply(engine.from(player, msg.entryId), msg.paused);
            }
            return null;
        }

        private static void apply(Optional<RtsWorkflowToken> token, boolean paused) {
            if (!token.isPresent()) return;
            if (paused) token.get()
                .pause();
            else token.get()
                .unpause();
        }
    }
}
