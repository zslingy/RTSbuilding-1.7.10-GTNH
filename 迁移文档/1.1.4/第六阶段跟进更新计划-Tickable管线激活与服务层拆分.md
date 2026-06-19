# 第六阶段跟进更新计划 — Tickable管线激活与服务层拆分

> 方向：激活 UltimineTickPipe 分帧管道，渐进式拆分 3 大 Manager，补齐客户端面板，统一形状系统

**制定日期**: 2026-06-20
**基准版本**: main v1.1.4 / port 第五阶段完成
**前置依赖**: 第五阶段 历史/撤销与挂起放置恢复（已完成）

---

## 一、阶段目标

第五阶段完成后，8 条管线全部以 `asyncCompletion` 运行——ULTIMINE/AREA_MINE/AREA_DESTROY 在单个 tick 内完成所有挖掘/破坏。main 版本这三条管线使用 `tickable` 模式分帧执行，避免大量方块操作造成卡顿。本阶段目标：

1. 激活 `UltimineTickPipe` — 分帧挖掘/破坏，每 tick 处理有限方块
2. 将 ULTIMINE/AREA_MINE/AREA_DESTROY 管线从 `asyncCompletion` 切换为 `tickable`
3. 渐进式拆分 `RtsStorageManager` / `RtsMineManager` / `RtsCameraManager`
4. 补齐客户端 UI 面板（工作流 + 恢复放置）
5. 实现引导系统（GuidePanel）
6. 统一形状系统（客户端 QuickBuild BuildShape ↔ 服务端 AreaMine byte 值）

---

## 二、UltimineTickPipe — 分帧管线化

### 2.1 背景

当前架构：
```
ULTIMINE 管线: ... → UltimineExecutePipe → ... → asyncCompletion
                         ↓
              RtsMineManager.startUltimineDirect()  ← 同步执行全部方块破坏
```

目标架构：
```
ULTIMINE 管线: ... → UltimineComputePipe → ... → tickable(UltimineTickPipe)
                         ↓                              ↓
              计算挖掘列表存入 context         每 tick 挖 N 个方块，逐步清除队列
```

### 2.2 修改文件

| 文件 | 修改内容 |
|------|----------|
| `server/pipeline/mining/UltimineTickPipe.java` | 实现分帧迭代逻辑（替换 stub） |
| `server/pipeline/mining/UltimineComputePipe.java` | **新建**：将 ultimine 坐标计算从 execute 中分离 |
| `server/pipeline/mining/AreaMineComputePipe.java` | **新建**：将 area mine 坐标计算分离 |
| `server/pipeline/mining/AreaDestroyComputePipe.java` | **新建**：将 area destroy 坐标计算分离 |
| `server/pipeline/core/RtsPipelineRegistration.java` | ULTIMINE/AREA_MINE/AREA_DESTROY 从 asyncCompletion 切换为 tickable |
| `server/pipeline/core/PipelineContext.java` | 新增 `KEY_QUEUED_POSITIONS` TypedKey |

### 2.3 UltimineTickPipe 实现设计

```java
public final class UltimineTickPipe implements TickablePipe {
    private static final int BLOCKS_PER_TICK = 8;  // 每 tick 最多处理 8 个方块

    @Override
    public TickResult tick(PipelineContext ctx) {
        List<BlockPos> queue = ctx.getData(KEY_QUEUED_POSITIONS);
        if (queue == null || queue.isEmpty()) return TickResult.COMPLETE;

        int processed = 0;
        while (!queue.isEmpty() && processed < BLOCKS_PER_TICK) {
            BlockPos pos = queue.remove(queue.size() - 1);
            RtsMineManager.breakBlockDirect(ctx.player(), pos.x, pos.y, pos.z);
            processed++;
        }

        if (queue.isEmpty()) return TickResult.COMPLETE;
        return TickResult.CONTINUE;
    }
}
```

**关键设计决策**:
- `UltimineComputePipe` 先计算所有待挖掘坐标存入 `PipelineContext`
- `UltimineTickPipe` 通过 `TickablePipelineRegistry` 注册，由 `TickHandler.onServerTick()` 驱动
- 每 tick 返回 `CONTINUE` 则下 tick 继续；返回 `COMPLETE` 则清理并执行后续 pipe

### 2.4 UltraComputePipe / AreaComputePipe 提取

当前 `UltimineExecutePipe.execute()` 直接调用 `RtsMineManager.startUltimineDirect()` 完成一切。需要拆分为：
1. `UltimineComputePipe.execute()` — 调用 `RtsMineManager.computeUltiminePositions()` 计算坐标列表并写入 `KEY_QUEUED_POSITIONS`
2. `UltimineTickPipe.tick()` — 逐 tick 迭代队列调用 `breakBlockDirect()`

同理 AREA_MINE 和 AREA_DESTROY 各自拆分为 Compute + Tick。

### 2.5 管线注册变更

```
// 当前 (asyncCompletion)
ULTIMINE: ProgressionGate → SessionValidate → Dimension → StopPrevious
        → WorkflowStart → ToolBorrow → UltimineExecute → ToolReturn
        → WorkflowProgress → NetworkSync → UiRefresh
        → WorkflowComplete → HistoryRecord
        → asyncCompletion

// 变更后 (tickable)
ULTIMINE: ProgressionGate → SessionValidate → Dimension → StopPrevious
        → WorkflowStart → ToolBorrow → UltimineCompute → ToolReturn
        → WorkflowProgress → NetworkSync → UiRefresh
        → tickable → WorkflowComplete → HistoryRecord

AREA_MINE:    ... → AreaMineCompute → ... → tickable → ...
AREA_DESTROY: ... → AreaDestroyCompute → ... → tickable → ...
```

`tickable()` 后追加 `WorkflowCompletePipe` + `HistoryRecordPipe`，保证 tick 完成后记录历史和完成工作流。

**PipelineRegistry 需新增** `tickable()` 注册方式（或扩展 `register()` 链式调用）。

### 2.6 PipelineRegistry 扩展

当前 `WorkflowPipeline` 支持 `register()` 和 `asyncCompletion()`，需要新增 `tickable()` 方法：

```java
// WorkflowPipeline 新增方法
public WorkflowPipeline tickable() {
    this.completionMode = CompletionMode.TICKABLE;
    return this;
}
```

`register()` 调用时检测 `completionMode`：
- `ASYNC` — 正常注册到 `PipelineRegistry`
- `TICKABLE` — 注册时将 `TickablePipe` 存入上下文，由 TickHandler 管理

---

## 三、服务层拆分

### 3.1 现状

当前 3 个 Manager 承载所有业务逻辑：

| Manager | 行数 | 职责混合程度 |
|---------|------|------------|
| `RtsStorageManager` | 1131 | 存储、放置、消耗、容器交互、页面查询、AE2 兼容 |
| `RtsMineManager` | ~300 | 挖掘、连锁、区域挖掘、区域摧毁 |
| `RtsCameraManager` | ~200 | 相机实体、切换、移动、同步 |

### 3.2 拆分计划

本阶段优先拆分最臃肿的 `RtsStorageManager`：

| 提取目标 | 文件 | 职责 |
|---------|------|------|
| `RtsPlacementService` | `server/service/` | `placeBlockDirect()` 及关联的消耗/回滚/音效逻辑 |
| `RtsContainerService` | `server/service/` | 容器扫描、`extractFromIInventory`、linked storage 管理 |
| `RtsSessionService` | `server/service/` | 会话创建、销毁、断线重连恢复 |
| `RtsTransferService` | `server/service/` | 物品传输（`tryConsumeBlock`、`addItem`、`transferAll`） |

**实施策略**:
- 每次提取一个方法组到新 Service 类
- 保持 `RtsStorageManager` 原有 public API 不变（委托给新 Service）
- 逐步减少 Manager 的直接逻辑，最终成为 Facade
- 其他 2 个 Manager 后续阶段拆分

### 3.3 新 Service 接口设计

```java
// RtsPlacementService
public static boolean placeBlockDirect(EntityPlayerMP player, int x, int y, int z, ...);
public static boolean placeBlockBatch(EntityPlayerMP player, List<BlockPos> positions, ...);

// RtsContainerService
public static List<LinkedStorageRef> scanContainers(EntityPlayerMP player);
public static boolean extractFromIInventory(IInventory inv, String itemId, int meta, long amount);

// RtsTransferService
public static boolean tryConsumeBlock(EntityPlayerMP player, String itemId, int meta, long amount);
public static boolean addItemToStorage(EntityPlayerMP player, ItemStack stack);
```

---

## 四、客户端 UI 面板

### 4.1 RtsWorkflowPanel — 工作流面板

**文件**: `client/panel/workflow/RtsWorkflowPanel.java`

**功能**: 显示玩家当前活跃工作流列表（从 `WorkflowViewModel` 读取）
- 每行显示：工作流类型图标、进度条、已处理/总计方块数
- 支持取消按钮（发送 `C2SRtsDeleteWorkflowMessage`）
- 支持暂停/恢复按钮（发送 `C2SRtsPauseWorkflowMessage`）
- 空状态："无活跃工作流"

**数据源**: 第五阶段已实现的 `WorkflowViewModel` + `S2CRtsWorkflowProgressMessage`

```
RtsWorkflowPanel (GUI overlay, 非独立窗口)
├── 标题 "Active Workflows"
├── 列表项 [类型图标] [进度条] [N/M blocks] [暂停▐▐] [取消✕]
└── 空状态文字
```

**1.7.10 渲染**: 使用 `Gui.drawRect` 绘制背景和进度条，`fontRenderer.drawString` 绘制文字

### 4.2 RtsResumePlacementPanel — 恢复放置面板

**文件**: `client/panel/RtsResumePlacementPanel.java`

**功能**: 当 `InteractionViewModel.pendingPlacementCount > 0` 时显示
- 恢复按钮（"恢复 N 个挂起放置"）
- 放弃按钮（"放弃挂起放置"）
- 触发后发送 `C2SRtsSubmitPendingMessage` / `C2SRtsResumePlacementActionMessage`

### 4.3 GuidePanel — 引导面板

**文件**: `client/panel/guide/GuidePanel.java` + `client/panel/guide/GuideTypes.java`

**功能**: 首次进入 RTS 模式时显示分步引导
- 步骤 1：介绍 RTS 视角操作（WASD + 滚轮）
- 步骤 2：介绍模式切换（顶栏按钮）
- 步骤 3：介绍链接存储
- 步骤 4：介绍挖掘/放置

**GuideTypes 枚举**:
```
CAMERA_MOVEMENT, MODE_SWITCH, LINK_STORAGE, MINING, PLACEMENT, QUICK_BUILD, ULTIMINE
```

**实现要点**:
- 持久化到 `RtsClientState`（已完成步骤标记）
- 每步骤显示半透明遮罩 + 高亮目标 UI 区域 + 文字说明
- 点击"下一步"或关闭按钮推进流程

---

## 五、统一形状系统

### 5.1 现状

QuickBuild 和 Area Mining 使用两套独立的形状表示：

| 系统 | 客户端 | 表示方式 |
|------|--------|---------|
| QuickBuild | `BuildShape` 枚举 (BLOCK/LINE/SQUARE/WALL/CIRCLE/BOX) | 枚举值 |
| Area Mining | `AreaMineExecutePipe.isInShape()` | byte 0-5 |

### 5.2 统一方案

**新建**: `common/BuildShape.java` → 提升 BuildShape 从 quickbuild 包到 common 包（整个 mod 通用）

**修改**:
- `AreaMineExecutePipe.isInShape()` — 改为使用 `BuildShape` 枚举而非 byte
- `C2SRtsAreaMineMessage` — shape 字段从 byte 改为 BuildShape 枚举的 ordinal
- `AreaMineComputePipe` — 统一使用 `ShapeGeometryUtil` 计算坐标

**ShapeGeometryUtil 复用**: `AreaMineComputePipe` 直接调用 `ShapeGeometryUtil.computePositions()` 计算区域坐标，无需重复实现 `isInShape()` 内联逻辑。

---

## 六、文件清单汇总

### 6.1 需要新建的文件（~12 个）

```
src/main/java/com/rtsbuilding/rtsbuilding/
├── server/pipeline/mining/
│   ├── UltimineComputePipe.java                — 连锁挖掘坐标计算
│   ├── AreaMineComputePipe.java                — 区域挖掘坐标计算
│   └── AreaDestroyComputePipe.java             — 区域摧毁坐标计算
├── server/service/
│   ├── RtsPlacementService.java                — 放置服务
│   ├── RtsContainerService.java                — 容器扫描服务
│   ├── RtsSessionService.java                  — 会话管理服务
│   └── RtsTransferService.java                 — 物品传输服务
├── client/panel/workflow/
│   └── RtsWorkflowPanel.java                   — 工作流面板
├── client/panel/
│   └── RtsResumePlacementPanel.java            — 恢复放置面板
├── client/panel/guide/
│   ├── GuidePanel.java                         — 引导面板
│   └── GuideTypes.java                         — 引导步骤枚举
└── common/
    └── BuildShape.java                         — 统一形状枚举（从 quickbuild 包提升）
```

### 6.2 需要修改的文件（~12 个）

| 文件 | 修改内容 |
|------|----------|
| `server/pipeline/mining/UltimineTickPipe.java` | 实现分帧迭代逻辑 |
| `server/pipeline/mining/UltimineExecutePipe.java` | 简化为委托 UltimineComputePipe（或删除） |
| `server/pipeline/mining/AreaMineExecutePipe.java` | 简化为委托 AreaMineComputePipe |
| `server/pipeline/mining/AreaDestroyExecutePipe.java` | 简化为委托 AreaDestroyComputePipe |
| `server/pipeline/core/RtsPipelineRegistration.java` | ULTIMINE/AREA_MINE/AREA_DESTROY 管线改为 tickable |
| `server/pipeline/core/WorkflowPipeline.java` | 新增 `tickable()` 方法和 CompletionMode 枚举 |
| `server/pipeline/core/PipelineRegistry.java` | tickable 模式注册逻辑（注册到 TickablePipelineRegistry） |
| `server/pipeline/core/PipelineContext.java` | 新增 `KEY_QUEUED_POSITIONS` TypedKey |
| `server/RtsStorageManager.java` | 方法迁移到 Service 类，改为 Facade 委托 |
| `server/RtsMineManager.java` | 新增 `breakBlockDirect()` / `computeUltiminePositions()` 方法 |
| `client/RtsClientState.java` | 新增 `guideStep` 持久化字段 |
| `client/RtsScreen.java` | 集成 RtsWorkflowPanel、GuidePanel |

### 6.3 可选删除/简化文件

| 文件 | 操作 |
|------|------|
| `client/panel/quickbuild/BuildShape.java` | 删除，改用 `common/BuildShape` |
| `server/pipeline/mining/UltimineExecutePipe.java` | 可简化或删除（如果等于 UltimineComputePipe + TickPipe） |

---

## 七、验收标准

1. **Tickable 管线**:
   - ULTIMINE/AREA_MINE/AREA_DESTROY 管线以 tickable 模式运行
   - 每 tick 最多处理 8 个方块（可配置）
   - Tick 完成后 `WorkflowCompletePipe` 和 `HistoryRecordPipe` 正确执行
   - `TickablePipelineRegistry.tickAll()` 正确驱动所有活跃 tickable 管线

2. **服务层拆分**:
   - `RtsStorageManager` 4 个职责提取到独立 Service
   - 现有调用点无需修改（Manager public API 保持不变）
   - `RtsMineManager` 新增 `breakBlockDirect()` 方法

3. **客户端面板**:
   - `RtsWorkflowPanel` 正确显示活跃工作流状态
   - `RtsResumePlacementPanel` 在挂起放置 > 0 时显示
   - `GuidePanel` 分步骤引导新用户

4. **形状系统统一**:
   - `BuildShape` 提升到 common 包
   - AreaMine 管线使用 `BuildShape` 枚举而非 byte 值
   - `ShapeGeometryUtil` 在服务端 `AreaMineComputePipe` 中复用

5. **构建验证**: `.\gradlew spotlessApply` + `.\gradlew build` 通过

---

## 八、分步实施策略

### 第一步：Tickable 管线基础设施
1. PipelineRegistry/WorkflowPipeline 新增 `tickable()` 支持
2. 实现 `UltimineComputePipe` + 更新 `UltimineTickPipe`
3. 更新 ULTIMINE 管线为 tickable 模式
4. `build` 验证

### 第二步：AreaMine + AreaDestroy tickable
5. 实现 `AreaMineComputePipe` + `AreaDestroyComputePipe`
6. 提取 `breakBlockDirect()` 到 `RtsMineManager`
7. 更新 AREA_MINE / AREA_DESTROY 管线为 tickable
8. `build` 验证

### 第三步：统一形状 + 服务拆分
9. 提升 `BuildShape` → `common/BuildShape`
10. AREA 管线改为使用 `BuildShape` + `ShapeGeometryUtil`
11. 拆分 `RtsStorageManager` → 4 个 Service
12. `build` 验证

### 第四步：客户端面板
13. 实现 `RtsWorkflowPanel`
14. 实现 `RtsResumePlacementPanel`
15. 实现 `GuidePanel` + `GuideTypes`
16. 集成到 `RtsScreen`
17. `spotlessApply` + `build` 最终验证

---

## 九、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| tickable 模式引入并发问题 | 中 | TickablePipelineRegistry 使用 ConcurrentHashMap，每玩家同时只一个活跃 tickable |
| 服务拆分破坏现有 API | 中 | 新 Service 并行存在，Manager 委托，不删除原方法 |
| BuildShape 提升引发大量 import 变更 | 低 | IDE 自动重构，spotless 统一 import |
| GuidePanel 国际化缺失 | 低 | 复用现有 lang 文件，先实现英文/中文 |
| tickable → sync pipe 执行时机 | 中 | tickable 完成后在 TickHandler 中执行后续 pipe（WorkflowCompletePipe/HistoryRecordPipe） |

---

## 十、后续阶段预览

| 阶段 | 内容 | 预计范围 |
|------|------|----------|
| 第七阶段 | 寻路系统 + Mixin 补充 + 剩余服务拆分 | 6 寻路 + 2 Mixin + 服务继续拆分 |
| 第八阶段 | API 层 + 测试框架 | 12+ API 文件 |

---

## 十一、审批确认

1. Tickable 管线激活方案是否合理？（8 方块/tick 速率）
2. 服务层拆分范围是否可接受？（4 个 Service 从 RtsStorageManager 提取）
3. 客户端面板优先实现 RtsWorkflowPanel + GuidePanel 是否满足需求？
4. BuildShape 提升到 common 包是否与其他模块冲突？
5. 是否需要调整分步顺序或刪减某些功能？
