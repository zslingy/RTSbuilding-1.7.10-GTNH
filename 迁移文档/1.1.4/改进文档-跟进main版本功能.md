# RTSbuilding-1.7.10-port 改进计划文档

> 目标：跟进 RTSbuilding-main (1.21.1) 的功能，补齐移植版缺失的模块

**制定日期**: 2026-06-19
**最后更新**: 2026-06-21（第七阶段完成，补充第八、九阶段计划）
**基准版本**: main v1.1.4 / port 第七阶段完成
**原始缺失源文件数**: ~316 个 (main 487 vs port 171)
**当前缺失源文件数**: ~35 个（估算，经过跟进阶段 1-7 大幅减少）

---

## 一、跟进进度总览

| 跟进阶段 | 内容 | 状态 | 新建文件数 | 修改文件数 |
|----------|------|------|-----------|-----------|
| 第一阶段 | UI设计完善（顶栏纹理、QuickBuild图标、WindowButton） | ✅ 已完成 | 4 | ~8 |
| 第二阶段 | 工作流引擎+管线系统（14+28 文件） | ✅ 已完成 | 42+ | ~10 |
| 第三阶段 | 网络同步与调用点统一（6 文件+4 消息 67-70） | ✅ 已完成 | 6 | ~12 |
| 第四阶段 | 补全 no-op 桩与激活剩余 Handler | ✅ 已完成 | 2 | ~14 |
| 第五阶段 | 历史/撤销与挂起放置恢复（16 文件+8 消息 71-78） | ✅ 已完成 | 16 | 9 |
| 第六阶段 | Tickable管线激活+面板（8 文件+UI 面板） | ✅ 已完成 | 8 | 8 |
| 第七阶段 | Mixin+寻路+服务层拆分+遗留收尾（15 文件+2 消息 79-80） | ✅ 已完成 | 15 | ~18 |
| **第八阶段** | **渲染器补充（AnimationRenderer / 实体渲染 / 蓝图预览 / 世界文字）** | **📋 已计划** | **7** | **~6** |
| **第九阶段** | **API层+测试框架+蓝图解析器+收尾清理** | **📋 已计划** | **9+test** | **~10** |

---

## 二、已完成功能对照

### 2.1 工作流引擎 (Workflow Engine) — ✅ 已完成 (第二阶段)

```
server/workflow/
├── core/
│   ├── RtsWorkflowEngine.java        — 全局单例引擎，ConcurrentHashMap 二级映射
│   ├── RtsWorkflowEntry.java         — 工作流条目
│   ├── RtsWorkflowToken.java         — Token 封装 (外部 API)
│   └── RtsWorkflowSlotManager.java   — 每玩家 8 槽位管理
├── event/
│   ├── RtsWorkflowEventBus.java      — 事件总线
│   ├── RtsWorkflowEventListener.java — 监听器接口
│   └── WorkflowEventType.java        — 10 种事件类型
├── model/
│   ├── RtsWorkflowType.java          — 8 种工作流类型
│   ├── RtsWorkflowPriority.java      — 4 级优先级
│   └── RtsWorkflowStatus.java        — 不可变进度快照
└── service/
    ├── RtsWorkflowSyncService.java   — 网络同步
    ├── RtsWorkflowTimeoutService.java — 超时清理
    └── RtsWorkflowStore.java         — NBT 持久化
```

### 2.2 管线系统 (Pipeline System) — ✅ 已完成 (第二阶段 + 第六阶段)

**8 条管线当前状态**:

| 管线 | 完成方式 | 关键 Pipe |
|------|----------|-----------|
| MINE_SINGLE | asyncCompletion | MiningExecutePipe |
| ULTIMINE | **tickable** (分帧 8/tick) | UltimineComputePipe → UltimineTickPipe |
| AREA_MINE | **tickable** (分帧 8/tick) | AreaMineComputePipe → UltimineTickPipe |
| AREA_DESTROY | **tickable** (分帧 8/tick) | AreaDestroyComputePipe → UltimineTickPipe |
| PLACE_SINGLE | asyncCompletion | PlacementExecutePipe |
| PLACE_BATCH | asyncCompletion | PlacementExecutePipe → PendingPlacementPipe |
| QUICK_BUILD | asyncCompletion | PlacementExecutePipe |
| STOP_MINING | 同步 | StopMiningPipe |

**管线核心文件**:
```
server/pipeline/core/   — PipelineRegistry, WorkflowPipeline, PipelinePipe, PipelineResult,
                          PipelineContext, TypedKey, TickablePipe, TickablePipelineRegistry,
                          ActivePipeline, RtsPipelineRegistration
server/pipeline/validation/  — SessionValidatePipe, SessionDimensionPipe, ProgressionGatePipe
server/pipeline/workflow/    — WorkflowStartPipe, WorkflowProgressPipe, WorkflowCompletePipe
server/pipeline/tool/        — ToolBorrowPipe, ToolReturnPipe
server/pipeline/mining/      — MiningExecutePipe, UltimineComputePipe, UltimineTickPipe,
                               AreaMineComputePipe, AreaDestroyComputePipe, AreaMineExecutePipe,
                               AreaDestroyExecutePipe, StopPreviousPipe, StopMiningPipe
server/pipeline/placement/   — PlacementExecutePipe, PendingPlacementPipe
server/pipeline/sync/        — NetworkSyncPipe, UiRefreshPipe, HistoryRecordPipe
```

### 2.3 网络消息 — ✅ 已完成 (第三+四+五阶段，共 80 个消息)

**RtsNetworkManager 当前 disc 最大值: 78**（第 7 阶段将增至 80）

| 类别 | 原有 | 已补充 | 当前总数 |
|------|------|--------|---------|
| Camera | 3 | +3 (74-76) | 6 |
| Builder | 17 | +8 (67-73, 77-78) + 更多 | 28+ |
| Craft | 8 | — | 8 |
| Progression | 9 | — | 9 |
| Feedback | 1 | — | 1 |
| Storage | 23 | +3 (74-76) | 26+ |
| Blueprint | 2 | — | 2 |

**新增消息 (67-78)**:
- 67: C2SRtsAreaMineMessage
- 68: C2SRtsAreaDestroyMessage
- 69: S2CRtsWorkflowProgressMessage
- 70: S2CRtsWorkflowProgressBatchMessage
- 71: C2SRtsUndoMessage
- 72: S2CRtsHistorySyncMessage
- 73: C2SRtsSubmitPendingMessage
- 74: C2SRtsScanResumePlacementMessage
- 75: C2SRtsResumePlacementActionMessage
- 76: S2CRtsResumePlacementScanMessage
- 77: S2CRtsStorageDirtyMessage
- 78: S2CRtsCameraAnchorMessage

### 2.4 历史/撤销系统 — ✅ 已完成 (第五阶段)

```
server/history/
├── ServerHistoryManager.java    — 全局单例，双端队列管理撤销/重做
├── HistoryExecutor.java         — 正向/反向回滚执行
├── HistoryEntry.java            — 历史条目
└── HistoryBlockRecord.java      — 方块记录 (之前/之后状态)
```

### 2.5 挂起放置与恢复 — ✅ 已完成 (第五阶段)

```
server/service/
├── RtsPendingPlacementService.java  — 挂起放置管理
└── RtsPlacedRecoveryService.java    — 放置恢复扫描
```

### 2.6 Tickable 管线 — ✅ 已完成 (第六阶段)

```
server/pipeline/mining/
├── UltimineComputePipe.java      — BFS 计算连锁挖掘坐标
├── UltimineTickPipe.java         — 分帧迭代 (BLOCKS_PER_TICK=8)
├── AreaMineComputePipe.java      — 从 bounds/shape 计算区域坐标
└── AreaDestroyComputePipe.java   — 从 positions 转 flat 数组
```

### 2.7 客户端 UI 面板 — ✅ 已完成 (第一+第六阶段)

```
client/widget/
├── WindowButton.java             — 按钮组件
├── WindowSlider.java             — 滑块组件
└── WindowTextBox.java            — 文本框组件

client/panel/workflow/
└── RtsWorkflowPanel.java         — 工作流状态面板（进度条+取消）

client/panel/
└── RtsResumePlacementPanel.java  — 挂起放置恢复面板

client/panel/guide/
├── GuidePanel.java               — 新手分步引导（7步）
└── GuideTypes.java               — 引导步骤枚举（含标题+描述）
```

---

## 三、待完成功能

### 3.1 P1 — 高优先级 (第八阶段)

| 类别 | 文件数 | 描述 |
|------|--------|------|
| AnimationRenderer | 1 新建 + 2 修改 | 放置/破坏缩放动画，接入 S2C 动画消息 Handler |
| RtsCameraEntityRenderer | 1 新建 + 1 修改 | 相机实体世界渲染 + ClientProxy 注册 |
| BlueprintGhostRenderer | 1 新建 + 1 修改 | 蓝图放置幽灵方块预览 |
| WorldTextRenderer | 1 新建 + 1 修改 | 建造范围尺寸世界文字标签 |
| QuestDetectOverlay 激活 | 1 修改 | isVisible + 扫描进度条响应 |

### 3.2 P2 — 中优先级 (第九阶段)

| 类别 | 文件数 | 描述 |
|------|--------|------|
| API 层 | 9 | RtsAPI 主入口 + 8 个子 API（委托现有 Service/Manager） |
| 测试框架 | 4 test + 1 build | JUnit 5 核心模块单元测试 |
| 蓝图解析器激活 | 3 修改 | SpongeSchem / Litematic / BuildingGadgets 完整解析 |

### 3.3 P3 — 低优先级 (第九阶段收尾)

| 类别 | 文件数 | 描述 |
|------|--------|------|
| 收尾清理 | ~6 修改 + ~2 删除 | 注释计数修正、@Deprecated 处理、占位符替换 |
| 兼容模块 | 0 | 1.7.10 大部分兼容模组不可用，跳过 |

---

## 四、实施路线图

### 第七阶段：Mixin + 寻路 + 服务层拆分 + 遗留收尾 ✅

详见 `第七阶段跟进更新计划-Mixin寻路与服务层拆分.md`

**已交付**:
- Mixin 补充 (`LocalPlayerMixin` + `ModdedRemoteStillValidMixin`)
- 寻路系统 (6 个寻路文件 + 2 个网络消息 79-80)
- 服务层拆分 (4 个 Service 从 `RtsStorageManager` 提取)
- 遗留收尾 (统一 `BuildShape` / RtsScreen 集成 / RtsCraftTerminalScreen TODO)

### 第八阶段：渲染器补充与实体渲染

详见 `第八九阶段跟进更新计划-渲染器API与收尾.md`

**目标**: AnimationRenderer、RtsCameraEntityRenderer、BlueprintGhost、WorldText

**步骤**:
1. 实现 `AnimationRenderer` + 激活 2 个动画消息 Handler
2. 实现 `RtsCameraEntityRenderer` + `ClientProxy` 注册
3. 实现 `BlueprintGhostRenderer`
4. 实现 `WorldTextRenderer`
5. 激活 `QuestDetectOverlay`

### 第九阶段：API 层 + 测试 + 收尾

**目标**: 公共 API、测试框架、蓝图解析器激活、收尾清理

**步骤**:
1. 实现 9 个 API 文件
2. 搭建测试框架 + 编写 4 个测试
3. 激活 3 个蓝图解析器
4. 收尾清理（注释修正 / @Deprecated 处理 / 占位符替换）

---

## 五、关键架构决策

### 5.1 管线系统是否值得移植？

✅ 已移植。管线系统是 main 版本的核心架构，提供类型安全、可组合、可测试的操作编排。

### 5.2 工作流引擎是否值得移植？

✅ 已移植。支持并发操作、暂停/恢复、超时清理、持久化。

### 5.3 服务层拆分的时机？

✅ 正在拆分（第七阶段）。管线系统和工作流引擎完成后，管线天然提供了服务拆分的边界。

### 5.4 1.7.10 特有的适配挑战

| 挑战 | main 实现 | 1.7.10 适配方案 | 状态 |
|------|----------|----------------|------|
| `isControlledCamera` | `LocalPlayerMixin` | 注入 `EntityClientPlayerMP.onLivingUpdate()`（第七阶段） | ✅ |
| `ResourceKey<Level>` | 维度资源键 | `int dimensionId` | ✅ |
| `record` 类型 | Java 16+ record | Jabel 编译的 final class + 手动 getter | ✅ |
| `sealed interface` | Java 17+ sealed | 普通接口 + 3 实现类 | ✅ |
| `ItemStack` 不可变 | 现代 API | 直接修改 `stackSize` | ✅ |
| `IItemHandler` | NeoForge Capability | `IInventory` 直接操作 | ✅ |
| `BlockPos` | 原版类 | 自定义 `BlockPos` 类 | ✅ |
| `Vec3` | 原版类 | 手动 x/y/z 字段 | ✅ |
| `PathNavigate` | 现代寻路 API | 1.7.10 直接运动控制（第七阶段） | ✅ |

---

## 六、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| AnimationRenderer 1.7.10 渲染兼容性 | 中 | Tessellator 线框已验证可行（ShapeGhostRenderer 已用） |
| RtsCameraEntity 无 Render 可能崩溃 | 低 | 1.7.10 对无 Render 的实体行为是"不渲染"而非崩溃 |
| 蓝图解析器格式差异大 | 中 | 渐进实现，先从最简单的 BG JSON 开始 |
| API 层维护成本 | 低 | 仅提供静态委托，不引入新逻辑 |
| JUnit 5 与 GTNH 构建系统兼容 | 低 | RetroFuturaGradle 支持 JUnit 5 test source set |

---

## 七、总结

从最初的 ~316 缺失文件经过 7 个跟进阶段，已完成工作流引擎、管线系统（8条，3 tickable + 5 asyncCompletion）、网络消息（80个 disc）、历史/撤销、挂起放置、Tickable 分帧管线、客户端 UI 面板、Mixin 注入、寻路系统、服务层拆分（4 Service）、统一形状系统等核心功能。

**剩余工作**：第八阶段（渲染器补充 ~13 文件改动）、第九阶段（API+测试+收尾 ~25 文件改动）。总计约 38 个文件待处理，分 2 个阶段渐进式完成。兼容模块因 1.7.10 无对应模组而整体跳过。

**剩余工作**：第七阶段 (Mixin+寻路+拆分+收尾 ~35 文件改动)、第八阶段 (渲染器 ~15 文件)、第九阶段 (API+测试 ~15 文件)。总计约 65 个文件待处理，按阶段渐进式完成。
