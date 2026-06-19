package com.rtsbuilding.rtsbuilding.network.builder;

import net.minecraft.entity.player.EntityPlayerMP;

import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsDeleteWorkflowMessage implements IMessage {

    private int entryId;

    public C2SRtsDeleteWorkflowMessage() {}

    public C2SRtsDeleteWorkflowMessage(int entryId) {
        this.entryId = entryId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entryId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entryId = buf.readInt();
    }

    public static class Handler implements IMessageHandler<C2SRtsDeleteWorkflowMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsDeleteWorkflowMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player != null) RtsWorkflowEngine.getInstance()
                .deleteWorkflow(player, msg.entryId);
            return null;
        }
    }
}
