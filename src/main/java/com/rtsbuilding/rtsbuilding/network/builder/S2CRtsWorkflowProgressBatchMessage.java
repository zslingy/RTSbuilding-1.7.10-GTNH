package com.rtsbuilding.rtsbuilding.network.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rtsbuilding.rtsbuilding.client.WorkflowViewModel;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CRtsWorkflowProgressBatchMessage implements IMessage {

    private List<RtsWorkflowStatus> statuses = Collections.emptyList();

    public S2CRtsWorkflowProgressBatchMessage() {}

    public S2CRtsWorkflowProgressBatchMessage(List<RtsWorkflowStatus> statuses) {
        this.statuses = statuses == null ? Collections.<RtsWorkflowStatus>emptyList()
            : new ArrayList<RtsWorkflowStatus>(statuses);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(statuses.size(), 64);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) S2CRtsWorkflowProgressMessage.writeStatus(buf, statuses.get(i));
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = Math.max(0, Math.min(buf.readInt(), 64));
        List<RtsWorkflowStatus> decoded = new ArrayList<RtsWorkflowStatus>(count);
        for (int i = 0; i < count; i++) decoded.add(S2CRtsWorkflowProgressMessage.readStatus(buf));
        statuses = decoded;
    }

    public List<RtsWorkflowStatus> toStatuses() {
        return Collections.unmodifiableList(statuses);
    }

    public static class Handler implements IMessageHandler<S2CRtsWorkflowProgressBatchMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsWorkflowProgressBatchMessage msg, MessageContext ctx) {
            WorkflowViewModel.updateFromBatch(msg);
            return null;
        }
    }
}
