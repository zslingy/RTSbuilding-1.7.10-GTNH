package com.rtsbuilding.rtsbuilding.network.builder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.policy.RtsBreakPolicy;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class C2SRtsAreaDestroyMessage implements IMessage {

    private static final int MAX_POSITIONS = 32768;
    private List<Integer> positions;
    private byte toolSlot;
    private String toolItemId;
    private ItemStack toolPrototype;

    public C2SRtsAreaDestroyMessage() {
        positions = new ArrayList<>();
    }

    public C2SRtsAreaDestroyMessage(List<Integer> positions, byte toolSlot, String toolItemId,
        ItemStack toolPrototype) {
        this.positions = positions != null ? positions : new ArrayList<>();
        this.toolSlot = toolSlot;
        this.toolItemId = toolItemId != null ? toolItemId : "";
        this.toolPrototype = toolPrototype != null ? toolPrototype.copy() : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int size = Math.min(positions.size() / 3, MAX_POSITIONS);
        buf.writeInt(size);
        for (int i = 0; i < size * 3; i++) buf.writeInt(positions.get(i));
        buf.writeByte(toolSlot);
        writeUtf(buf, toolItemId, 256);
        boolean hasTool = toolPrototype != null && toolPrototype.getItem() != null;
        buf.writeBoolean(hasTool);
        if (hasTool) ByteBufUtils.writeItemStack(buf, toolPrototype);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = Math.max(0, Math.min(buf.readInt(), MAX_POSITIONS));
        positions = new ArrayList<>(size * 3);
        for (int i = 0; i < size * 3; i++) positions.add(buf.readInt());
        toolSlot = buf.readByte();
        toolItemId = readUtf(buf, 256);
        toolPrototype = buf.readBoolean() ? ByteBufUtils.readItemStack(buf) : null;
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

    public List<Integer> getPositions() {
        return positions;
    }

    public byte getToolSlot() {
        return toolSlot;
    }

    public String getToolItemId() {
        return toolItemId;
    }

    public ItemStack getToolPrototype() {
        return toolPrototype;
    }

    public static class Handler implements IMessageHandler<C2SRtsAreaDestroyMessage, IMessage> {

        @Override
        public IMessage onMessage(C2SRtsAreaDestroyMessage msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            if (com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry
                .has(com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType.AREA_DESTROY)) {
                java.util.Map<String, Object> args = new java.util.HashMap<String, Object>();
                args.put(
                    com.rtsbuilding.rtsbuilding.server.pipeline.mining.AreaDestroyExecutePipe.KEY_POSITIONS.name(),
                    msg.positions);
                args.put(
                    com.rtsbuilding.rtsbuilding.server.pipeline.mining.MiningExecutePipe.KEY_TOOL_SLOT.name(),
                    Byte.valueOf(msg.toolSlot));
                args.put(
                    com.rtsbuilding.rtsbuilding.server.pipeline.mining.MiningExecutePipe.KEY_TOOL_ITEM_ID.name(),
                    msg.toolItemId);
                args.put(
                    com.rtsbuilding.rtsbuilding.server.pipeline.mining.MiningExecutePipe.KEY_TOOL_PROTOTYPE.name(),
                    msg.toolPrototype);
                @SuppressWarnings("unchecked")
                com.rtsbuilding.rtsbuilding.server.pipeline.core.WorkflowPipeline<com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext> pipeline = (com.rtsbuilding.rtsbuilding.server.pipeline.core.WorkflowPipeline<com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext>) com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineRegistry
                    .get(com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType.AREA_DESTROY);
                com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult result = pipeline
                    .execute(new com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext(player, args));
                if (!(result instanceof com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult.Failure))
                    return null;
            }

            if (!RtsCameraManager.isActive(player)) return null;

            if (!RtsProgressionManager.canUse(player, RtsFeature.AREA_DESTROY)) {
                return null;
            }

            List<Integer> posList = msg.positions;
            if (posList == null || posList.isEmpty() || posList.size() % 3 != 0) return null;

            World world = player.worldObj;
            int slot = Math.max(0, Math.min(msg.toolSlot, 8));
            ItemStack tool = findTool(player, slot, msg.toolItemId, msg.toolPrototype);

            int broken = 0;
            int count = posList.size() / 3;
            for (int i = 0; i < count; i++) {
                int x = posList.get(i * 3);
                int y = posList.get(i * 3 + 1);
                int z = posList.get(i * 3 + 2);
                if (!RtsBreakPolicy.canBreakBlock(player, world, x, y, z)) {
                    continue;
                }
                if (breakBlock(world, player, x, y, z, tool)) {
                    broken++;
                }
            }

            if (broken > 0) {
                RtsbuildingMod.LOGGER
                    .info("AreaDestroy: {} broke {} of {} blocks", player.getDisplayName(), broken, count);
            }
            return null;
        }

        private static ItemStack findTool(EntityPlayerMP player, int preferredSlot, String toolItemId,
            ItemStack prototype) {
            if (preferredSlot >= 0 && preferredSlot < 9) {
                ItemStack hotbar = player.inventory.getStackInSlot(preferredSlot);
                if (hotbar != null && matchesTool(hotbar, toolItemId, prototype)) return hotbar;
            }
            for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                ItemStack s = player.inventory.getStackInSlot(i);
                if (s != null && matchesTool(s, toolItemId, prototype)) return s;
            }
            return null;
        }

        private static boolean matchesTool(ItemStack stack, String toolItemId, ItemStack prototype) {
            if (stack == null) return false;
            if (prototype != null && prototype.getItem() != null) {
                return stack.getItem() == prototype.getItem() && stack.getItemDamage() == prototype.getItemDamage();
            }
            if (!toolItemId.isEmpty()) {
                String stackId = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                return toolItemId.equals(stackId);
            }
            return false;
        }

        private static boolean breakBlock(World world, EntityPlayerMP player, int x, int y, int z, ItemStack tool) {
            Block block = world.getBlock(x, y, z);
            if (block == null || world.isAirBlock(x, y, z)) return false;
            int meta = world.getBlockMetadata(x, y, z);
            float hardness = block.getBlockHardness(world, x, y, z);
            if (hardness < 0) return false;

            boolean creative = player.capabilities.isCreativeMode;
            if (!creative) {
                if (tool == null) return false;
                if (!tool.getItem()
                    .canHarvestBlock(block, tool)) return false;
            }

            world.playSoundEffect(
                x + 0.5,
                y + 0.5,
                z + 0.5,
                block.stepSound.getBreakSound(),
                (block.stepSound.getVolume() + 1.0F) / 2.0F,
                block.stepSound.getPitch() * 0.8F);

            // 破坏粒子效果
            world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(block) + (meta << 12));
            block.harvestBlock(world, player, x, y, z, meta);
            world.setBlockToAir(x, y, z);

            if (!creative && tool != null) {
                tool.damageItem(1, player);
                if (tool.stackSize <= 0) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                }
            }
            return true;
        }
    }
}
