# 细览 02 — 主 Mod 入口与核心架构

> **关联主览**: [00-主览-项目结构总览.md](00-主览-项目结构总览.md) §3 (根包) + §7 (架构图)

---

## 1. 文件清单

| 文件 | 行数 | 职责 |
|------|------|------|
| `RtsbuildingMod.java` | ~50 | 主 Mod 类，`@Mod` 注解入口，生命周期分发 |
| `CommonProxy.java` | ~50 | 服务端代理：网络注册、实体注册 |
| `ClientProxy.java` | ~150 | 客户端代理：按键绑定（25个）、渲染器注册、NEI 集成 |
| `Config.java` | ~212 | 配置文件管理（4个分类），支持热更新和网络同步 |
| `RtsCommunityLinks.java` | ~10 | 社区链接常量 |

---

## 2. RtsbuildingMod.java — 主 Mod 类

```java
@Mod(
    modid = RtsbuildingMod.MODID,
    name = RtsbuildingMod.MODNAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]")
public class RtsbuildingMod {
    public static final String MODID = "rtsbuilding";
    public static final String MODNAME = "RTS Building Build From Above";

    @Instance(MODID)
    public static RtsbuildingMod instance;

    @SidedProxy(
        clientSide = "com.rtsbuilding.rtsbuilding.ClientProxy",
        serverSide = "com.rtsbuilding.rtsbuilding.CommonProxy")
    public static CommonProxy proxy;
}
```

### 生命周期方法

| 方法 | 触发时机 | 代理分发 |
|------|---------|---------|
| `preInit(event)` | 预初始化 | → `proxy.preInit(event)` |
| `init(event)` | 初始化 | → `proxy.init(event)` |
| `postInit(event)` | 后初始化 | → `proxy.postInit(event)` |
| `serverStarting(event)` | 服务端启动 | → `proxy.serverStarting(event)` |

### 版本 Token

通过 `gradle.properties` 中的 `generateGradleTokenClass`，编译时自动生成 `Tags.VERSION`，确保版本号始终与构建一致。

---

## 3. CommonProxy.java — 服务端代理

### preInit

```
1. Config.synchronizeConfiguration(event.getSuggestedConfigurationFile())
   → 加载/创建 rtsbuilding.cfg，读取 4 个分类的所有配置项

2. RtsNetworkManager.registerMessages()
   → 注册所有 C2S 和 S2C 网络消息（见细览 03）

3. 实体注册
   → EntityRegistry.findGlobalUniqueEntityId()
   → EntityRegistry.registerModEntity(
       RtsCameraEntity.class, "rts_camera", entityId,
       RtsbuildingMod.instance, 128, 1, false)
```

### serverStarting

```java
// 阶段7：配方缓存惰性重建
RecipeScanCache.markDirty();
```

---

## 4. ClientProxy.java — 客户端代理

### 4.1 按键绑定表（25 个）

| 类别 | KeyBinding | 默认键 | 用途 |
|------|-----------|--------|------|
| **核心** | `keyToggleRts` | R | 切换 RTS 模式 |
| | `keyOpenScreen` | G | 打开 RTS 主屏幕 |
| **模式** | `keyModeInteract` | 1 | 交互模式 |
| | `keyModeLinkStorage` | 2 | 链接存储模式 |
| | `keyModeRotate` | 3 | 旋转模式 |
| | `keyModeFunnel` | 4 | 漏斗模式 |
| **相机** | `keyCameraForward` | W | 前移 |
| | `keyCameraBack` | S | 后移 |
| | `keyCameraLeft` | A | 左移 |
| | `keyCameraRight` | D | 右移 |
| | `keyCameraUp` | Q | 上升 |
| | `keyCameraDown` | E | 下降 |
| | `keyCameraZoomIn` | NUMPAD_ADD | 放大 |
| | `keyCameraZoomOut` | NUMPAD_SUBTRACT | 缩小 |
| **建造** | `keyPlace` | ENTER | 放置方块 |
| | `keyBreak` | BACKSPACE | 破坏方块 |
| | `keyQuickBuild` | B | 快速建造 |
| | `keyRotateBlock` | ] | 旋转方块 |
| **采矿** | `keyToggleMining` | M | 切换采矿模式 |
| **其他** | `keyOpenGuide` | H | 打开指南 |
| | `keyDebugInfo` | F3 | 调试信息 |

所有按键绑定在 `ClientRegistry.registerKeyBinding()` 注册，归属于 `"RTS Building"` 类别。

### 4.2 init — 事件注册

```java
// 阶段5：世界渲染器 → Forge 事件总线
MinecraftForge.EVENT_BUS.register(worldRenderer);

// 阶段6：按键处理 → FML 总线
FMLCommonHandler.instance().bus().register(new RtsKeyHandler());

// 阶段6：NEI 集成（软依赖）
RtsNeiCompat.registerNeiOverlayIfAvailable();
```

### 4.3 RtsKeyHandler

内部类，监听 `InputEvent.KeyInputEvent`：

| 按键 | 操作 |
|------|------|
| `keyOpenScreen` | 打开 `RtsScreen` |
| `keyDebugInfo` | 切换 `Config.debugMode` |

### 4.4 NEI 集成策略

```java
private void initNeiCompat() {
    try {
        RtsNeiCompat.registerNeiOverlayIfAvailable();
    } catch (Throwable t) {
        LOGGER.info("NEI not available, skipping NEI integration");
    }
}
```

采用 try-catch 保证 NEI 未安装时仍能正常启动。

---

## 5. Config.java — 配置管理

### 5.1 配置分类

| 分类常量 | 显示名 | 包含项 |
|---------|--------|--------|
| `CATEGORY_GENERAL` | `general` | `maxActionRadiusBlocks` (48-512, 默认128) |
| `CATEGORY_BLUEPRINTS` | `blueprints` | `enableBlueprints`, `maxBlueprintBlocks` (1-200000, 默认20000) |
| `CATEGORY_PROGRESSION` | `progression` | `enableSurvivalProgression`, `shareSurvivalProgressionWithTeams`, `progressionCostOverrides` |
| `CATEGORY_RENDERING` | `rendering` | `useWireframePreview` |

### 5.2 热更新支持

Config 实现了完整的运行时热更新 API，支持通过 GUI 或命令动态修改：

| 方法 | 操作 |
|------|------|
| `setSurvivalProgressionEnabled(boolean)` | 启用/禁用生存进度 |
| `setMaxActionRadiusBlocks(int)` | 修改操作半径（自动 clamp） |
| `setWireframePreviewEnabled(boolean)` | 切换线框预览 |
| `setProgressionCostOverride(nodePath, costsText)` | 设置单个技能节点消耗覆盖 |
| `saveProgressionSettings(...)` | 批量保存多个设置 |

每个 setter 都执行 `config.get(...).set(...)` + `config.save()`，确保修改即时持久化。

### 5.3 消耗覆盖格式

```
progressionCostOverrides: String[]
格式: node_path=minecraft:item:count,minecraft:item2:count
示例: ultimine=minecraft:diamond_pickaxe:1,minecraft:redstone_block:1
```

`progressionCostOverrides()` 方法解析为 `Map<String, String>`。

---

## 6. RtsCommunityLinks.java — 社区链接

```java
public static final String DISCORD_INVITE = "https://discord.gg/9Pw6vZfAm";
public static final String GITHUB_REPOSITORY = "https://github.com/Hcrab/RTSbuilding";
public static final String QQ_GROUP = "910318076";
```

纯静态常量类，用于 GUI 中显示社区链接按钮。

---

## 7. 架构要点

1. **SidedProxy 模式**: 经典 Forge 1.7.10 双端代理，`@SidedProxy` 根据运行端自动注入对应实现
2. **生命周期链**: `RtsbuildingMod` → `proxy.preInit/init/postInit/serverStarting`
3. **热更新配置**: 完整的 getter/setter + `config.save()`，不依赖重启
4. **软依赖降级**: NEI/AE2 均使用 try-catch 包裹，缺失时静默跳过
5. **版本管理**: Gradle Token 自动注入 `Tags.VERSION`，避免手动同步