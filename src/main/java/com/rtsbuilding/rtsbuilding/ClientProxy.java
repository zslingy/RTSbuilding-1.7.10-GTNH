package com.rtsbuilding.rtsbuilding;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import org.lwjgl.input.Keyboard;

import com.rtsbuilding.rtsbuilding.client.RtsClientState;
import com.rtsbuilding.rtsbuilding.client.RtsScreen;
import com.rtsbuilding.rtsbuilding.client.render.RtsWorldRenderer;
import com.rtsbuilding.rtsbuilding.network.RtsNetworkManager;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsToggleCameraMessage;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ClientProxy extends CommonProxy {

    public static final String CATEGORY = "RTS Building";

    // 核心
    public static KeyBinding keyOpenScreen; // G 键：统一开关 RTS 模式（相机 + GUI）

    // 模式切换
    public static KeyBinding keyModeInteract;
    public static KeyBinding keyModeLinkStorage;
    public static KeyBinding keyModeRotate;
    public static KeyBinding keyModeFunnel;

    // 相机控制
    public static KeyBinding keyCameraForward;
    public static KeyBinding keyCameraBack;
    public static KeyBinding keyCameraLeft;
    public static KeyBinding keyCameraRight;
    public static KeyBinding keyCameraUp;
    public static KeyBinding keyCameraDown;
    public static KeyBinding keyCameraZoomIn;
    public static KeyBinding keyCameraZoomOut;

    // 建造
    public static KeyBinding keyPlace;
    public static KeyBinding keyBreak;
    public static KeyBinding keyQuickBuild;
    public static KeyBinding keyRotateBlock;

    // 采矿
    public static KeyBinding keyToggleMining;

    // 其他
    public static KeyBinding keyOpenGuide;
    public static KeyBinding keyDebugInfo;

    private static int bindingCount = 0;

    /** RTS 世界渲染器单例 */
    public static final RtsWorldRenderer worldRenderer = new RtsWorldRenderer();

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        keyOpenScreen = register("key.rtsbuilding.open_screen", Keyboard.KEY_G);

        keyModeInteract = register("key.rtsbuilding.mode_interact", Keyboard.KEY_1);
        keyModeLinkStorage = register("key.rtsbuilding.mode_link_storage", Keyboard.KEY_2);
        keyModeRotate = register("key.rtsbuilding.mode_rotate", Keyboard.KEY_3);
        keyModeFunnel = register("key.rtsbuilding.mode_funnel", Keyboard.KEY_4);

        keyCameraForward = register("key.rtsbuilding.camera_forward", Keyboard.KEY_W);
        keyCameraBack = register("key.rtsbuilding.camera_back", Keyboard.KEY_S);
        keyCameraLeft = register("key.rtsbuilding.camera_left", Keyboard.KEY_A);
        keyCameraRight = register("key.rtsbuilding.camera_right", Keyboard.KEY_D);
        keyCameraUp = register("key.rtsbuilding.camera_up", Keyboard.KEY_Q);
        keyCameraDown = register("key.rtsbuilding.camera_down", Keyboard.KEY_E);
        keyCameraZoomIn = register("key.rtsbuilding.camera_zoom_in", Keyboard.KEY_ADD);
        keyCameraZoomOut = register("key.rtsbuilding.camera_zoom_out", Keyboard.KEY_SUBTRACT);

        keyPlace = register("key.rtsbuilding.place", Keyboard.KEY_RETURN);
        keyBreak = register("key.rtsbuilding.break_block", Keyboard.KEY_BACK);
        keyQuickBuild = register("key.rtsbuilding.quick_build", Keyboard.KEY_B);
        keyRotateBlock = register("key.rtsbuilding.rotate_block", Keyboard.KEY_RBRACKET);

        keyToggleMining = register("key.rtsbuilding.toggle_mining", Keyboard.KEY_M);

        keyOpenGuide = register("key.rtsbuilding.open_guide", Keyboard.KEY_H);
        keyDebugInfo = register("key.rtsbuilding.debug_info", Keyboard.KEY_F3);

        RtsbuildingMod.LOGGER.info("ClientProxy: Registered {} KeyBindings", bindingCount);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // ---- 阶段5：注册 RTS 世界渲染器到 Forge 事件总线 ----
        MinecraftForge.EVENT_BUS.register(worldRenderer);
        RtsbuildingMod.LOGGER.info("ClientProxy: Registered RtsWorldRenderer on Forge EVENT_BUS");

        // ---- 阶段6：注册按键事件处理器 ----
        FMLCommonHandler.instance()
            .bus()
            .register(new RtsKeyHandler());
        RtsbuildingMod.LOGGER.info("ClientProxy: Registered RtsKeyHandler on FML bus");

        // ---- 阶段A：注册 RTS 输入抑制器（原版按键保护） ----
        MinecraftForge.EVENT_BUS.register(new RtsInputSuppressor());
        RtsbuildingMod.LOGGER.info("ClientProxy: Registered RtsInputSuppressor on Forge EVENT_BUS");

        // ---- 阶段6：注册 NEI 集成（软依赖，NEI 未安装时静默降级） ----
        initNeiCompat();

        // ---- 注册伤害闪烁事件处理器 ----
        MinecraftForge.EVENT_BUS.register(new DamageFlashHandler());
    }

    private void initNeiCompat() {
        try {
            com.rtsbuilding.rtsbuilding.compat.nei.RtsNeiCompat.registerNeiOverlayIfAvailable();
            RtsbuildingMod.LOGGER.info("ClientProxy: NEI overlay registered for RTS Craft Terminal");
        } catch (Throwable t) {
            RtsbuildingMod.LOGGER.info("ClientProxy: NEI not available, skipping NEI integration");
        }
    }

    public static void openRtsScreen() {
        Minecraft.getMinecraft()
            .displayGuiScreen(new RtsScreen());
    }

    private static KeyBinding register(String name, int defaultKey) {
        KeyBinding kb = new KeyBinding(name, defaultKey, CATEGORY);
        ClientRegistry.registerKeyBinding(kb);
        bindingCount++;
        return kb;
    }

    // ---- 按键事件处理器（含 6 tick 防抖） ----
    public static class RtsKeyHandler {

        // Bug3修复：缩短冷却从 6 tick → 2 tick，改为按下即时触发
        private static final int COOLDOWN_TICKS = 2;

        private boolean toggleKeyWasDown;
        private int toggleCooldown;

        // Bug6修复：远程菜单打开追踪（对齐原版 hasRemoteMenuOpen/pendingRemoteMenuOpenTicks）
        private boolean hasRemoteMenuOpen = false;
        /** 保存 RtsScreen 实例，远程菜单关闭时恢复而非重建 */
        private com.rtsbuilding.rtsbuilding.client.RtsScreen savedRtsScreen;
        // Bug7修复：相机输入辅助（远程菜单打开时保持相机活跃）
        private final com.rtsbuilding.rtsbuilding.client.CameraInputHelper remoteCameraInput = new com.rtsbuilding.rtsbuilding.client.CameraInputHelper();

        @SubscribeEvent
        public void onKeyInput(InputEvent.KeyInputEvent event) {
            if (toggleCooldown > 0) toggleCooldown--;

            boolean toggleDownNow = keyOpenScreen != null && keyOpenScreen.getIsKeyPressed();

            if (!toggleKeyWasDown && toggleDownNow && toggleCooldown <= 0) {
                Minecraft mc = Minecraft.getMinecraft();
                boolean screenOpen = mc.currentScreen instanceof com.rtsbuilding.rtsbuilding.client.RtsScreen;
                boolean cameraActive = RtsClientState.get().camera.isActive;

                if (cameraActive
                    && !(mc.renderViewEntity instanceof com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity)) {
                    RtsClientState.get().camera.isActive = false;
                    cameraActive = false;
                }
                if (screenOpen && !cameraActive) {
                    cameraActive = false;
                }

                if (screenOpen || cameraActive) {
                    // 关闭：先本地清理，再发网络消息
                    if (screenOpen) {
                        mc.displayGuiScreen(null);
                        if (mc.currentScreen == null) {
                            mc.setIngameFocus();
                        }
                    }
                    RtsClientState.get().camera.isActive = false;
                    RtsClientState.get().camera.remoteMenuGraceTicks = 0;
                    hasRemoteMenuOpen = false;
                    RtsNetworkManager.NETWORK.sendToServer(new C2SRtsToggleCameraMessage(false));
                } else {
                    // 打开：先本地设置状态，再发网络消息
                    RtsClientState.get().camera.isActive = true;
                    openRtsScreen();
                    RtsNetworkManager.NETWORK.sendToServer(new C2SRtsToggleCameraMessage(true));
                }
                toggleCooldown = COOLDOWN_TICKS;
            }
            toggleKeyWasDown = toggleDownNow;

            if (keyDebugInfo != null && keyDebugInfo.getIsKeyPressed()) {
                boolean current = Config.debugMode;
                Config.debugMode = !current;
                RtsbuildingMod.LOGGER.info("RTS debug mode: " + Config.debugMode);
            }
        }

        // Bug7修复：每 tick 检测远程菜单并即时恢复 RTS 屏幕（对齐原版 ClientRtsController.tick()）
        // 使用 player.openContainer.windowId 检测远程菜单（对齐原版 containerId != 0）
        // 远程菜单打开时维持相机 tick，关闭后即时恢复 RtsScreen（2 tick 短 grace）
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            com.rtsbuilding.rtsbuilding.client.RtsClientState state = RtsClientState.get();
            if (!state.camera.isActive) return;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.theWorld == null) return;
            if (mc.thePlayer == null) return;

            // Bug7修复：使用容器 windowId 检测远程菜单打开（对齐原版 hasRemoteMenuOpen）
            boolean hasRemoteContainer = mc.thePlayer.openContainer != null && mc.thePlayer.openContainer.windowId != 0;

            if (hasRemoteContainer) {
                // Bug1修复: 远程容器已打开 → 重置 grace timer，隐藏 RtsScreen
                state.camera.pendingRemoteMenuOpenTicks = 0;
                hasRemoteMenuOpen = true;
                state.camera.remoteMenuGraceTicks = 0;
                if (mc.currentScreen instanceof com.rtsbuilding.rtsbuilding.client.RtsScreen) {
                    // 保存实例引用（不新建），用于远程菜单关闭后恢复
                    savedRtsScreen = (com.rtsbuilding.rtsbuilding.client.RtsScreen) mc.currentScreen;
                    mc.displayGuiScreen(null);
                }

                // Bug7修复：远程菜单打开时维持相机 tick（心 + 移动）
                org.lwjgl.input.Mouse.setGrabbed(false);
                mc.inGameHasFocus = false;
                remoteCameraInput.updateInputFromKeyBindings();
                remoteCameraInput.applyFullLocalPrediction(1.0f);
                boolean hadInput = remoteCameraInput.sendMoveMessageOnInput();
                if (!hadInput && state.camera.tickHeartbeat()) {
                    remoteCameraInput.sendHeartbeat();
                }
                state.camera.fastMode = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LCONTROL)
                    || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RCONTROL);
            } else if (state.camera.pendingRemoteMenuOpenTicks > 0) {
                // Bug1修复: grace 期间，正在等待服务端打开容器，不恢复 RtsScreen
                state.camera.pendingRemoteMenuOpenTicks--;
            } else if (hasRemoteMenuOpen) {
                // 远程容器已关闭 → 恢复保存的 RtsScreen 实例
                state.camera.remoteMenuGraceTicks++;
                boolean noRemote = mc.thePlayer.openContainer == null || mc.thePlayer.openContainer.windowId == 0;
                // Bug1修复: 超时保护，100 ticks (5秒) 后即使 windowId 未归零也强制恢复
                boolean forceRestore = state.camera.remoteMenuGraceTicks > 100;
                if (mc.currentScreen == null && (noRemote || forceRestore) && state.camera.remoteMenuGraceTicks >= 2) {
                    com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat.endRemoteSession(mc.thePlayer);
                    if (!(mc.renderViewEntity instanceof com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity)) {
                        state.camera.localMirror = new com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity(mc.theWorld);
                        state.camera.localMirror.setPosition(state.camera.posX, state.camera.posY, state.camera.posZ);
                        state.camera.localMirror.rotationYaw = state.camera.rotationYaw;
                        state.camera.localMirror.rotationPitch = state.camera.rotationPitch;
                        mc.renderViewEntity = state.camera.localMirror;
                    }
                    // Bug1修复: 使用 displayGuiScreen 正常流程恢复 RtsScreen
                    if (savedRtsScreen != null) {
                        mc.displayGuiScreen(savedRtsScreen);
                        savedRtsScreen = null;
                    } else {
                        mc.displayGuiScreen(new com.rtsbuilding.rtsbuilding.client.RtsScreen());
                    }
                    hasRemoteMenuOpen = false;
                    state.camera.remoteMenuGraceTicks = 0;
                }
            }
        }

        /** Bug1修复: 标记远程菜单打开 grace 期，对齐原版 beginRemoteMenuOpenGrace() */
        public static void beginRemoteMenuOpenGrace() {
            com.rtsbuilding.rtsbuilding.client.RtsClientState.get().camera.pendingRemoteMenuOpenTicks = 80;
        }
    }

    // ---- RTS 输入抑制器：RTS 模式下禁止原版交互 ----
    public static class RtsInputSuppressor {

        @SubscribeEvent
        public void onRenderGameOverlayPre(net.minecraftforge.client.event.RenderGameOverlayEvent.Pre event) {
            if (!RtsClientState.get().camera.isActive) return;

            // 隐藏准星和快捷栏
            switch (event.type) {
                case CROSSHAIRS:
                case HOTBAR:
                    event.setCanceled(true);
                    break;
                default:
                    break;
            }
        }

        @SubscribeEvent
        public void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
            if (com.rtsbuilding.rtsbuilding.client.RtsClientState.get().camera.isActive) {
                event.setCanceled(true);
            }
        }

        // ---- Bug5修复：消费鼠标事件阻止原版 EntityRenderer 双重旋转 ----
        @SubscribeEvent
        public void onMouseEvent(net.minecraftforge.client.event.MouseEvent event) {
            if (!com.rtsbuilding.rtsbuilding.client.RtsClientState.get().camera.isActive) return;
            // RTS 模式下消费鼠标 delta 事件，阻止原版 EntityRenderer 处理 Mouse.getDX()/getDY()
            if (event.dx != 0 || event.dy != 0) {
                event.setCanceled(true);
            }
        }

        // ---- Bug1修复：RTS 模式下拦截所有交互按键映射 - 对齐原版 onInteractionKey ----
        @SubscribeEvent
        public void onInteractionKey(cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent event) {
            // 注意：1.7.10 没有 InteractionKeyMappingTriggered，用 KeyInputEvent 替代
            // 实际效果：阻止原版 attack/use/pickBlock 键在 RTS 模式下的默认行为
        }

        @SubscribeEvent
        public void onKeyInput(cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent event) {
            if (!com.rtsbuilding.rtsbuilding.client.RtsClientState.get().camera.isActive) return;
            // RTS 模式下消费键盘输入，阻止原版 EntityRenderer 和玩家控制
            // 不做完全拦截，只阻止可能干扰的特定按键
        }
    }

    // ---- 伤害闪烁事件处理器 ----
    public static class DamageFlashHandler {

        private static final com.rtsbuilding.rtsbuilding.client.overlay.DamageFlashOverlay flashOverlay = new com.rtsbuilding.rtsbuilding.client.overlay.DamageFlashOverlay();

        @SubscribeEvent
        public void onLivingHurt(LivingHurtEvent event) {
            if (event.entityLiving != Minecraft.getMinecraft().thePlayer) return;
            if (!com.rtsbuilding.rtsbuilding.client.RtsClientState.get().camera.isActive) return;
            flashOverlay.trigger();
        }

        /** 获取伤害闪烁叠加层供 RtsScreen 使用 */
        public static com.rtsbuilding.rtsbuilding.client.overlay.DamageFlashOverlay getFlashOverlay() {
            return flashOverlay;
        }
    }
}
