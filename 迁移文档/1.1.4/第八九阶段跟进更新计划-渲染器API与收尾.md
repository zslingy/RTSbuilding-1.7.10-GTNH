# 剩余跟进更新计划 — 第八、九阶段

> 方向：补齐渲染器与实体渲染、开放 API 层、测试框架、收尾清理

**制定日期**: 2026-06-21
**基准版本**: main v1.1.4 / port 第七阶段完成
**前置依赖**: 第七阶段 Mixin 寻路与服务层拆分（已完成）

---

## 一、现状总览

第七阶段完成后，管线系统（8条）、工作流引擎、网络消息（80个）、历史/撤销、挂起放置、Tickable 分帧、Mixin 注入、寻路系统、服务层拆分已全部完成。当前所有包结构：

| 包 | 已存在 | 未完成 |
|----|--------|--------|
| `client/render/` | 5 个渲染器 | AnimationRenderer（文档误标完成，实际不存在） |
| `client/overlay/` | 3 个 (1 个桩) | QuestDetectOverlay 待激活 |
| `client/rendering/` | 不存在 | 整目录缺失（蓝图/动画渲染器） |
| `client/panel/` | 全部完成 | — |
| `server/pipeline/` | 全部完成 | — |
| `server/service/` | 4+2 Service | 其余 Manager 方法可继续拆分 |
| `mixin/` | 4 个 (2 原有 + 2 新增) | — |
| `network/` | 80 个消息 | 注释计数未更新 |
| `api/` | 不存在 | 整目录缺失（12 接口） |
| `common/` | BuilderMode + BuildShape | — |
| `src/test/` | 不存在 | 无测试框架 |

---

## 二、第八阶段 — 渲染器补充与实体渲染

### 2.1 阶段目标

1. 实现 `AnimationRenderer` — 放置/破坏缩放脉冲动画
2. 激活 `S2CRtsPlaceAnimationMessage` / `S2CRtsBreakAnimationMessage` Handler 渲染逻辑
3. 实现 `RtsCameraEntityRenderer` — 相机实体世界渲染
4. 实现 `BlueprintGhostPreview` — 蓝图放置预览
5. 实现世界文字叠加层（建造范围标签）
6. 激活 `QuestDetectOverlay`

### 2.2 AnimationRenderer — 放置/破坏动画

**当前状态**: `S2CRtsPlaceAnimationMessage.Handler` 和 `S2CRtsBreakAnimationMessage.Handler` 是空桩（`return null`）。

**设计**:

```java
// client/render/AnimationRenderer.java
public class AnimationRenderer {
    // 活跃动画队列
    private static final List<ActiveAnimation> activeAnimations = new ArrayList<>();
    private static final int ANIMATION_DURATION = 10; // ticks

    public static void addPlaceAnimation(int x, int y, int z) { ... }
    public static void addBreakAnimation(int x, int y, int z) { ... }

    // 每帧由 RtsWorldRenderer 调用
    public static void render(float partialTicks) {
        // 遍历活跃动画，绘制缩放线框
        // 放置动画：绿色线框从 0.5 缩放到 1.0
        // 破坏动画：红色线框从 1.0 缩放到 0.5 并消失
    }
}
```

**1.7.10 渲染**: Tessellator + GL11 线框模式，`GL11.glLineWidth()`, `GL11.glBegin(GL11.GL_LINE_LOOP)`。

### 2.3 RtsCameraEntityRenderer — 相机实体渲染

**当前状态**: `RtsCameraEntity` 在 `CommonProxy` 中注册为实体（`EntityRegistry.registerModEntity`），但**没有对应的 Render 子类**，也没有调用 `RenderingRegistry.registerEntityRenderingHandler()`。这可能在服务端正常，但在客户端可能导致渲染问题。

**设计**:

```java
// client/render/RtsCameraEntityRenderer.java
@cpw.mods.fml.client.registry.RenderingRegistry
public class RtsCameraEntityRenderer extends Render {
    // 相机锚点渲染：半透明十字线或小立方体
    // 仅在 RTS 模式激活且 owner 匹配时显示

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        // GL11 绘制：小半透明标记（Tessellator 线框立方体）
    }
}
```

**注册**: 在 `ClientProxy.init()` 中调用：
```java
RenderingRegistry.registerEntityRenderingHandler(
    RtsCameraEntity.class, new RtsCameraEntityRenderer());
```

### 2.4 BlueprintGhostPreview — 蓝图预览

**文件**: `client/rendering/blueprint/BlueprintGhostRenderer.java`

**功能**: 当客户端有活跃蓝图放置预览时，在目标位置渲染半透明方块幽灵。

```
blueprint/client/
└── BlueprintGhostRenderer.java  — 蓝图幽灵方块渲染
```

**1.7.10 渲染**: 复用 `ShapeGhostRenderer` 的绘制模式（Tessellator + 半透明蓝色方块）。

### 2.5 世界文字叠加层

**文件**: `client/render/WorldTextRenderer.java`

**功能**: 在 3D 世界空间中绘制标签文字（如建造范围尺寸标签、操作模式名称）。

**1.7.10 渲染**: `FontRenderer.drawString()` + GL11 变换（`glPushMatrix/glTranslate/glRotate`）实现世界空间文字。

### 2.6 QuestDetectOverlay 激活

**当前状态**: `QuestDetectOverlay.isVisible()` 返回 `false`，整体是桩。

**激活**: 响应 `S2CRtsQuestDetectStatusMessage` 设置扫描状态，显示进度条叠加层。

### 2.7 文件清单

```
新建 (7 个):
src/main/java/com/rtsbuilding/rtsbuilding/
├── client/render/AnimationRenderer.java              — 放置/破坏缩放动画
├── client/render/RtsCameraEntityRenderer.java        — 相机实体渲染
├── client/render/WorldTextRenderer.java              — 世界文字标签
├── client/rendering/blueprint/
│   └── BlueprintGhostRenderer.java                   — 蓝图幽灵预览
└── client/rendering/animation/
    ├── PlacementAnimationRenderer.java               — 放置生长动画
    └── DestroyAnimationRenderer.java                 — 破坏缩小动画

修改 (~6 个):
├── client/render/RtsWorldRenderer.java               — 调度 AnimationRenderer + WorldText
├── client/ClientProxy.java                           — 注册 RtsCameraEntityRenderer + BlueprintGhost
├── network/builder/S2CRtsPlaceAnimationMessage.java  — Handler 接入 AnimationRenderer
├── network/builder/S2CRtsBreakAnimationMessage.java  — Handler 接入 AnimationRenderer
├── client/overlay/QuestDetectOverlay.java            — 激活 isVisible/扫描进度
└── client/RtsClientState.java                        — 添加 animation 队列引用
```

---

## 三、第九阶段 — API 层 + 测试框架 + 收尾清理

### 3.1 阶段目标

1. 实现公共 API 层（8 个子 API 接口 + 1 主入口）
2. 搭建测试框架（JUnit 5 + JMH 基准）
3. 激活 3 个蓝图解析器桩
4. 清理废弃代码和文档

### 3.2 API 层

```
server/api/
├── RtsAPI.java                    — 主 API 入口，聚合所有子 API 委托
├── RtsStorageQueryAPI.java        — 存储查询（通过 RtsSessionService）
├── RtsBlueprintAPI.java           — 蓝图材料查询
├── RtsPlacementAPI.java           — 远程放置
├── RtsInteractionAPI.java         — 远程交互
├── RtsMiningAPI.java              — 远程挖掘
├── RtsTransferAPI.java            — 物品传输
├── RtsCraftingAPI.java            — 合成终端
└── RtsFluidAPI.java               — 流体操作
```

**设计原则**: 每个 API 接口是静态方法集，内部委托给现有 Service/Manager。提供稳定的公共契约供外部模组集成。

### 3.3 测试框架

```
src/test/java/com/rtsbuilding/rtsbuilding/
├── pipeline/PipelineResultTest.java          — PipelineResult 行为测试
├── pipeline/TypedKeyTest.java               — TypedKey 类型安全测试
├── workflow/RtsWorkflowEngineTest.java       — 工作流引擎单元测试
└── storage/RtsStorageSessionTest.java        — 存储会话读写测试
```

**依赖**: JUnit 5（需要添加到 `build.gradle.kts`）。

### 3.4 蓝图解析器激活

```
blueprint/format/
├── SpongeSchemReader.java           — Sponge Schematic v1/v2 格式解析
├── LitematicReader.java             — Litematica 格式解析
└── BuildingGadgetsTemplateReader.java — Building Gadgets JSON 格式
```

当前这 3 个解析器均返回空蓝图 + 警告日志。实现完整的 NBT/JSON 解析。

### 3.5 收尾清理

| 项 | 操作 |
|----|------|
| `RtsBottomPanel` Blueprints 占位符 | 替换为实际蓝图列表或保留为 "无蓝图" 提示 |
| `RtsNetworkManager` 注释计数 | 更新为实际消息数（Camera 6 / Builder 28+ / Craft 8 / Progression 9 / Feedback 1 / Storage 24+ / Blueprint 2 / Pathfinding 2） |
| `RtsStorageSession.setLinkMode()` @Deprecated | 保留或移除（评估调用点） |
| `S2CRtsOpenCraftTerminalMessage` DEPRECATED | 可保留（注册兼容），更新 javadoc 说明 |
| `RtsWorkflowSlotManager` (service 包) @Deprecated | 确认无调用点后可删除 |
| `ContainerMixin` 未注册 | 确认是否需要注册到 mixin config |
| `LocalPlayerMixin` 方法描述符警告 | 调查 `onLivingUpdate` 在 MCP 中的确切实体名称 |

### 3.6 文件清单

```
新建 (9 + test files):
src/main/java/com/rtsbuilding/rtsbuilding/
├── server/api/
│   ├── RtsAPI.java
│   ├── RtsStorageQueryAPI.java
│   ├── RtsBlueprintAPI.java
│   ├── RtsPlacementAPI.java
│   ├── RtsInteractionAPI.java
│   ├── RtsMiningAPI.java
│   ├── RtsTransferAPI.java
│   ├── RtsCraftingAPI.java
│   └── RtsFluidAPI.java

修改 (~10 个):
├── blueprint/format/SpongeSchemReader.java            — 实现完整解析
├── blueprint/format/LitematicReader.java              — 实现完整解析
├── blueprint/format/BuildingGadgetsTemplateReader.java— 实现完整解析
├── client/panel/RtsBottomPanel.java                   — Blueprints 占位符替换
├── network/RtsNetworkManager.java                     — 注释计数修正
├── build.gradle.kts                                   — JUnit 5 依赖
├── server/storage/RtsStorageSession.java              — 评估 @Deprecated 项
└── AGENTS.md                                          — 更新网络消息计数

可选删除:
├── server/workflow/service/RtsWorkflowSlotManager.java  — @Deprecated 空壳
└── network/craft/S2CRtsOpenCraftTerminalMessage.java    — 评估是否移除
```

---

## 四、文件清单汇总

### 第八阶段 (~13 文件)

| 步骤 | 新建 | 修改 |
|------|------|------|
| AnimationRenderer | 3 | 4 (`RtsWorldRenderer` + 2 动画消息 Handler + `RtsClientState`) |
| RtsCameraEntityRenderer | 1 | 1 (`ClientProxy`) |
| BlueprintGhostRenderer | 1 | 1 (`ClientProxy`) |
| WorldTextRenderer | 1 | 1 (`RtsWorldRenderer`) |
| QuestDetectOverlay 激活 | — | 1 |

### 第九阶段 (~19 文件)

| 步骤 | 新建 | 修改 | 删除 |
|------|------|------|------|
| API 层 | 9 | — | — |
| 测试框架 | 4 test | 1 (`build.gradle.kts`) | — |
| 蓝图解析器 | — | 3 | — |
| 收尾清理 | — | ~5 | ~2 |

---

## 五、验收标准

### 第八阶段

1. **AnimationRenderer**: `S2CRtsPlaceAnimationMessage` / `S2CRtsBreakAnimationMessage` 触发可见的缩放动画
2. **RtsCameraEntityRenderer**: 相机实体在 RTS 模式下可见（半透明标记）
3. **BlueprintGhostRenderer**: 蓝图放置时显示幽灵方块预览
4. **WorldTextRenderer**: 建造范围尺寸标签可见
5. **QuestDetectOverlay**: 任务扫描时显示进度

### 第九阶段

1. **API 层**: 8 个子 API 编译通过，委托正确
2. **测试框架**: 至少 4 个核心模块单元测试通过
3. **蓝图解析器**: SpongeSchem / Litematica / BuildingGadgets 格式正确解析
4. **构建验证**: `spotlessApply` + `build` 通过，无新增警告

---

## 六、分步实施策略

### 第八阶段

1. 实现 `AnimationRenderer` + 修改 2 个动画消息 Handler
2. 实现 `RtsCameraEntityRenderer` + `ClientProxy` 注册
3. 实现 `BlueprintGhostRenderer`
4. 实现 `WorldTextRenderer` + `RtsWorldRenderer` 调度
5. 激活 `QuestDetectOverlay`
6. `spotlessApply` + `build` 验证

### 第九阶段

7. 实现 9 个 API 文件
8. 搭建测试框架 + 编写 4 个测试
9. 激活 3 个蓝图解析器
10. 收尾清理（注释修正 / @Deprecated 处理 / 占位符替换）
11. `spotlessApply` + `build` 最终验证

---

## 七、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| AnimationRenderer 1.7.10 渲染兼容性 | 中 | Tessellator 线框已验证可行（ShapeGhostRenderer 已用） |
| RtsCameraEntity 无 Render 可能崩溃 | 低 | 1.7.10 对无 Render 的实体行为是"不渲染"而非崩溃 |
| 蓝图解析器格式差异大 | 中 | 渐进实现，先从最简单的 BG JSON 开始 |
| API 层维护成本 | 低 | 仅提供静态委托，不引入新逻辑 |
| JUnit 5 与 GTNH 构建系统兼容 | 低 | RetroFuturaGradle 支持 JUnit 5 test source set |

---

## 八、总结

| 阶段 | 文件数 | 主要交付物 |
|------|--------|-----------|
| 已完成 (1-7) | ~100 新建 + ~80 修改 | 工作流引擎、管线系统(8条)、网络消息(80disc)、历史撤销、Tickable分帧、Mixin注入、寻路、服务拆分 |
| 第八阶段 | 7 新建 + 6 修改 | AnimationRenderer、RtsCameraEntityRenderer、BlueprintGhost、WorldText、QuestDetectOverlay |
| 第九阶段 | 9+test 新建 + 8 修改 + 2 删除 | API 层(9)、测试框架、蓝图解析器(3)、收尾清理 |

剩余 2 个阶段完成后，port 版本将完全对齐 main v1.1.4 功能。（兼容模块因 1.7.10 无对应模组而整体跳过。）
