package com.rtsbuilding.rtsbuilding.network.builder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsPlaceBatchMessage implements IMessage {

    private static final int MAX_POSITIONS = 32768;
    private List<Integer> clickedPositions; // flat: x0,y0,z0,x1,y1,z1...
    private byte face;
    private double hitOffsetX, hitOffsetY, hitOffsetZ;
    private byte rotateSteps;
    private boolean forcePlace, skipIfOccupied;
    private String itemId;
    private ItemStack itemPrototype;
    private double rayOriginX, rayOriginY, rayOriginZ;
    private double rayDirX, rayDirY, rayDirZ;

    public C2SRtsPlaceBatchMessage() {
        clickedPositions = new ArrayList<>();
    }

    public C2SRtsPlaceBatchMessage(List<Integer> clickedPositions, byte face, double hitOffsetX, double hitOffsetY,
        double hitOffsetZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
        ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX,
        double rayDirY, double rayDirZ) {
        this.clickedPositions = clickedPositions != null ? clickedPositions : new ArrayList<>();
        this.face = face;
        this.hitOffsetX = hitOffsetX;
        this.hitOffsetY = hitOffsetY;
        this.hitOffsetZ = hitOffsetZ;
        this.rotateSteps = rotateSteps;
        this.forcePlace = forcePlace;
        this.skipIfOccupied = skipIfOccupied;
        this.itemId = itemId != null ? itemId : "";
        this.itemPrototype = itemPrototype != null ? itemPrototype.copy() : null;
        this.rayOriginX = rayOriginX;
        this.rayOriginY = rayOriginY;
        this.rayOriginZ = rayOriginZ;
        this.rayDirX = rayDirX;
        this.rayDirY = rayDirY;
        this.rayDirZ = rayDirZ;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int size = Math.min(clickedPositions.size() / 3, MAX_POSITIONS);
        buf.writeInt(size);
        for (int i = 0; i < size * 3; i++) buf.writeInt(clickedPositions.get(i));
        buf.writeByte(face);
        buf.writeDouble(hitOffsetX);
        buf.writeDouble(hitOffsetY);
        buf.writeDouble(hitOffsetZ);
        buf.writeByte(rotateSteps);
        buf.writeBoolean(forcePlace);
        buf.writeBoolean(skipIfOccupied);
        writeUtf(buf, itemId, 128);
        boolean hasItem = itemPrototype != null;
        buf.writeBoolean(hasItem);
        if (hasItem) ByteBufUtils.writeItemStack(buf, itemPrototype);
        buf.writeDouble(rayOriginX);
        buf.writeDouble(rayOriginY);
        buf.writeDouble(rayOriginZ);
        buf.writeDouble(rayDirX);
        buf.writeDouble(rayDirY);
        buf.writeDouble(rayDirZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = Math.max(0, Math.min(buf.readInt(), MAX_POSITIONS));
        clickedPositions = new ArrayList<>(size * 3);
        for (int i = 0; i < size * 3; i++) clickedPositions.add(buf.readInt());
        face = buf.readByte();
        hitOffsetX = buf.readDouble();
        hitOffsetY = buf.readDouble();
        hitOffsetZ = buf.readDouble();
        rotateSteps = buf.readByte();
        forcePlace = buf.readBoolean();
        skipIfOccupied = buf.readBoolean();
        itemId = readUtf(buf, 128);
        itemPrototype = buf.readBoolean() ? ByteBufUtils.readItemStack(buf) : null;
        rayOriginX = buf.readDouble();
        rayOriginY = buf.readDouble();
        rayOriginZ = buf.readDouble();
        rayDirX = buf.readDouble();
        rayDirY = buf.readDouble();
        rayDirZ = buf.readDouble();
    }

    private static void writeUtf(ByteBuf b, String s, int max) {
        if (s == null) s = "";
        byte[] d = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int l = Math.min(d.length, max);
        b.writeInt(l);
        b.writeBytes(d, 0, l);
    }

    private static String readUtf(ByteBuf b, int max) {
        int l = Math.max(0, Math.min(b.readInt(), max));
        byte[] d = new byte[l];
        if (l > 0) b.readBytes(d);
        return new String(d, java.nio.charset.StandardCharsets.UTF_8);
    }

    public List<Integer> getClickedPositions() {
        return clickedPositions;
    }

    public byte getFace() {
        return face;
    }

    public double getHitOffsetX() {
        return hitOffsetX;
    }

    public double getHitOffsetY() {
        return hitOffsetY;
    }

    public double getHitOffsetZ() {
        return hitOffsetZ;
    }

    public byte getRotateSteps() {
        return rotateSteps;
    }

    public boolean isForcePlace() {
        return forcePlace;
    }

    public boolean isSkipIfOccupied() {
        return skipIfOccupied;
    }

    public String getItemId() {
        return itemId;
    }

    public ItemStack getItemPrototype() {
        return itemPrototype;
    }

    public double getRayOriginX() {
        return rayOriginX;
    }

    public double getRayOriginY() {
        return rayOriginY;
    }

    public double getRayOriginZ() {
        return rayOriginZ;
    }

    public double getRayDirX() {
        return rayDirX;
    }

    public double getRayDirY() {
        return rayDirY;
    }

    public double getRayDirZ() {
        return rayDirZ;
    }

    public static class Handler implements IMessageHandler<C2SRtsPlaceBatchMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsPlaceBatchMessage msg, MessageContext ctx) {
            return null;
        }
    }
}
