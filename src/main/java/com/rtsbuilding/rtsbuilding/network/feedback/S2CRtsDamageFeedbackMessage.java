package com.rtsbuilding.rtsbuilding.network.feedback;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public class S2CRtsDamageFeedbackMessage implements IMessage {

    private float amount;
    private boolean lowHealth;

    public S2CRtsDamageFeedbackMessage() {}

    public S2CRtsDamageFeedbackMessage(float amount, boolean lowHealth) {
        this.amount = Math.max(0.0F, amount);
        this.lowHealth = lowHealth;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(Math.max(0.0F, amount));
        buf.writeBoolean(lowHealth);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        amount = buf.readFloat();
        lowHealth = buf.readBoolean();
    }

    public float getAmount() {
        return amount;
    }

    public boolean isLowHealth() {
        return lowHealth;
    }

    public static class Handler implements IMessageHandler<S2CRtsDamageFeedbackMessage, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(S2CRtsDamageFeedbackMessage msg, MessageContext ctx) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            if (mc.thePlayer == null) return null;
            com.rtsbuilding.rtsbuilding.client.RtsClientState.get().interaction.damageAmount = msg.getAmount();
            com.rtsbuilding.rtsbuilding.client.RtsClientState.get().interaction.lowHealth = msg.isLowHealth();
            if (msg.isLowHealth()) {
                mc.thePlayer
                    .sendChatMessage("Low health warning: last damage " + String.format("%.1f", msg.getAmount()));
            }
            return null;
        }
    }
}
