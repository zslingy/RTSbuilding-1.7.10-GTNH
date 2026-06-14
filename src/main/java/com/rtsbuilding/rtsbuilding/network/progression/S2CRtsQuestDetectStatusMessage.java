package com.rtsbuilding.rtsbuilding.network.progression;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CRtsQuestDetectStatusMessage implements IMessage {

    private byte phase;
    private int scannedTasks;
    private int totalTasks;
    private int completedTasks;
    public static final byte PHASE_STARTED = 0;
    public static final byte PHASE_COMPLETE = 1;
    public static final byte PHASE_UNAVAILABLE = 2;
    public static final byte PHASE_ERROR = 3;

    public S2CRtsQuestDetectStatusMessage() {}

    public S2CRtsQuestDetectStatusMessage(byte phase, int scannedTasks, int totalTasks, int completedTasks) {
        this.phase = phase;
        this.scannedTasks = Math.max(0, scannedTasks);
        this.totalTasks = Math.max(0, totalTasks);
        this.completedTasks = Math.max(0, completedTasks);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(phase);
        buf.writeInt(scannedTasks);
        buf.writeInt(totalTasks);
        buf.writeInt(completedTasks);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        phase = buf.readByte();
        scannedTasks = buf.readInt();
        totalTasks = buf.readInt();
        completedTasks = buf.readInt();
    }

    public byte getPhase() {
        return phase;
    }

    public int getScannedTasks() {
        return scannedTasks;
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public int getCompletedTasks() {
        return completedTasks;
    }

    public static class Handler implements IMessageHandler<S2CRtsQuestDetectStatusMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsQuestDetectStatusMessage msg, MessageContext ctx) {
            return null; // stub — stage 4
        }
    }
}
