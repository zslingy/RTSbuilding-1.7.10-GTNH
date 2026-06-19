package com.rtsbuilding.rtsbuilding.network.builder;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.WorkflowPipeline;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PlacementExecutePipe;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsPlaceMessage implements IMessage {

    private int clickedX, clickedY, clickedZ;
    private byte face;
    private double hitX, hitY, hitZ;
    private byte rotateSteps;
    private boolean forcePlace, skipIfOccupied;
    private String itemId;
    private ItemStack itemPrototype;
    private double rayOriginX, rayOriginY, rayOriginZ;
    private double rayDirX, rayDirY, rayDirZ;
    private boolean quickBuild;

    public C2SRtsPlaceMessage() {
        itemPrototype = null;
    }

    public C2SRtsPlaceMessage(int clickedX, int clickedY, int clickedZ, byte face, double hitX, double hitY,
        double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
        ItemStack itemPrototype, double rayOriginX, double rayOriginY, double rayOriginZ, double rayDirX,
        double rayDirY, double rayDirZ, boolean quickBuild) {
        this.clickedX = clickedX;
        this.clickedY = clickedY;
        this.clickedZ = clickedZ;
        this.face = face;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
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
        this.quickBuild = quickBuild;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(clickedX);
        buf.writeInt(clickedY);
        buf.writeInt(clickedZ);
        buf.writeByte(face);
        buf.writeDouble(hitX);
        buf.writeDouble(hitY);
        buf.writeDouble(hitZ);
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
        buf.writeBoolean(quickBuild);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        clickedX = buf.readInt();
        clickedY = buf.readInt();
        clickedZ = buf.readInt();
        face = buf.readByte();
        hitX = buf.readDouble();
        hitY = buf.readDouble();
        hitZ = buf.readDouble();
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
        quickBuild = buf.readBoolean();
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

    public int getClickedX() {
        return clickedX;
    }

    public int getClickedY() {
        return clickedY;
    }

    public int getClickedZ() {
        return clickedZ;
    }

    public byte getFace() {
        return face;
    }

    public double getHitX() {
        return hitX;
    }

    public double getHitY() {
        return hitY;
    }

    public double getHitZ() {
        return hitZ;
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

    public boolean isQuickBuild() {
        return quickBuild;
    }

    public static class Handler implements IMessageHandler<C2SRtsPlaceMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsPlaceMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            RtsWorkflowType type = msg.isQuickBuild() ? RtsWorkflowType.QUICK_BUILD : RtsWorkflowType.PLACE_SINGLE;
            if (PipelineRegistry.has(type)) {
                Map<String, Object> args = new HashMap<String, Object>();
                args.put(PlacementExecutePipe.KEY_X.name(), Integer.valueOf(msg.clickedX));
                args.put(PlacementExecutePipe.KEY_Y.name(), Integer.valueOf(msg.clickedY));
                args.put(PlacementExecutePipe.KEY_Z.name(), Integer.valueOf(msg.clickedZ));
                args.put(PlacementExecutePipe.KEY_FACE.name(), Byte.valueOf(msg.face));
                args.put(PlacementExecutePipe.KEY_HIT_X.name(), Double.valueOf(msg.hitX));
                args.put(PlacementExecutePipe.KEY_HIT_Y.name(), Double.valueOf(msg.hitY));
                args.put(PlacementExecutePipe.KEY_HIT_Z.name(), Double.valueOf(msg.hitZ));
                args.put(PlacementExecutePipe.KEY_ROTATE_STEPS.name(), Byte.valueOf(msg.rotateSteps));
                args.put(PlacementExecutePipe.KEY_FORCE_PLACE.name(), Boolean.valueOf(msg.forcePlace));
                args.put(PlacementExecutePipe.KEY_SKIP_IF_OCCUPIED.name(), Boolean.valueOf(msg.skipIfOccupied));
                args.put(PlacementExecutePipe.KEY_ITEM_ID.name(), msg.itemId);
                args.put(PlacementExecutePipe.KEY_ITEM_PROTOTYPE.name(), msg.itemPrototype);
                args.put(PlacementExecutePipe.KEY_QUICK_BUILD.name(), Boolean.valueOf(msg.quickBuild));
                @SuppressWarnings("unchecked")
                WorkflowPipeline<PipelineContext> pipeline = (WorkflowPipeline<PipelineContext>) PipelineRegistry
                    .get(type);
                PipelineResult result = pipeline.execute(new PipelineContext(player, args));
                if (!(result instanceof PipelineResult.Failure)) return null;
            }

            RtsStorageManager.placeBlockDirect(
                player,
                msg.clickedX,
                msg.clickedY,
                msg.clickedZ,
                msg.face,
                msg.hitX,
                msg.hitY,
                msg.hitZ,
                msg.rotateSteps,
                msg.forcePlace,
                msg.skipIfOccupied,
                msg.itemId,
                msg.itemPrototype,
                msg.quickBuild);

            return null;
        }
    }
}
