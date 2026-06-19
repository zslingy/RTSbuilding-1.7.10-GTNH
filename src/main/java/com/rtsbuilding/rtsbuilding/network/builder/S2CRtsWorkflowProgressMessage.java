package com.rtsbuilding.rtsbuilding.network.builder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rtsbuilding.rtsbuilding.client.WorkflowViewModel;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CRtsWorkflowProgressMessage implements IMessage {

    private RtsWorkflowStatus status = RtsWorkflowStatus.idle();

    public S2CRtsWorkflowProgressMessage() {}

    public S2CRtsWorkflowProgressMessage(RtsWorkflowStatus status) {
        this.status = status == null ? RtsWorkflowStatus.idle() : status;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeStatus(buf, status);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        status = readStatus(buf);
    }

    public RtsWorkflowStatus toStatus() {
        return status;
    }

    static void writeStatus(ByteBuf buf, RtsWorkflowStatus status) {
        RtsWorkflowStatus safe = status == null ? RtsWorkflowStatus.idle() : status;
        buf.writeInt(safe.entryId());
        buf.writeInt(
            safe.type() == null ? -1
                : safe.type()
                    .ordinal());
        buf.writeInt(
            safe.priority() == null ? RtsWorkflowPriority.NORMAL.ordinal()
                : safe.priority()
                    .ordinal());
        buf.writeInt(safe.totalBlocks());
        buf.writeInt(safe.completedBlocks());
        buf.writeInt(safe.failedBlocks());
        buf.writeBoolean(safe.suspended());
        buf.writeBoolean(safe.paused());
        writeUtf(buf, safe.detailMessage(), 512);
        List<String> missing = safe.missingItems();
        int count = Math.min(missing.size(), 64);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) writeUtf(buf, missing.get(i), 256);
    }

    static RtsWorkflowStatus readStatus(ByteBuf buf) {
        int entryId = buf.readInt();
        RtsWorkflowType type = readEnum(RtsWorkflowType.values(), buf.readInt());
        RtsWorkflowPriority priority = readEnum(RtsWorkflowPriority.values(), buf.readInt());
        int total = buf.readInt();
        int completed = buf.readInt();
        int failed = buf.readInt();
        boolean suspended = buf.readBoolean();
        boolean paused = buf.readBoolean();
        String detail = readUtf(buf, 512);
        int count = Math.max(0, Math.min(buf.readInt(), 64));
        List<String> missing = count == 0 ? Collections.<String>emptyList() : new ArrayList<String>(count);
        for (int i = 0; i < count; i++) missing.add(readUtf(buf, 256));
        if (type == null) return RtsWorkflowStatus.idle();
        return RtsWorkflowStatus
            .fromRaw(type, priority, total, completed, failed, missing, detail, suspended, paused, entryId);
    }

    private static <T> T readEnum(T[] values, int ordinal) {
        return ordinal < 0 || ordinal >= values.length ? null : values[ordinal];
    }

    private static void writeUtf(ByteBuf buf, String value, int max) {
        String safe = value == null ? "" : value;
        byte[] data = safe.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(data.length, max);
        buf.writeInt(length);
        buf.writeBytes(data, 0, length);
    }

    private static String readUtf(ByteBuf buf, int max) {
        int length = Math.max(0, Math.min(buf.readInt(), max));
        byte[] data = new byte[length];
        if (length > 0) buf.readBytes(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<S2CRtsWorkflowProgressMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsWorkflowProgressMessage msg, MessageContext ctx) {
            WorkflowViewModel.updateFromPacket(msg);
            return null;
        }
    }
}
