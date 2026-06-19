# 第七阶段跟进更新计划 — Mixin 寻路与服务层拆分

> 方向：补齐 Mixin 注入层，实现寻路系统，完成服务层拆分，收尾第六阶段遗留

**制定日期**: 2026-06-21
**基准版本**: main v1.1.4 / port 第六阶段完成
**前置依赖**: 第六阶段 Tickable管线激活与服务层拆分（已完成）

---

## 一、阶段目标

第六阶段完成后，8 条管线全部正常运行（3 条 tickable + 5 条 asyncCompletion），工作流面板和引导面板已建好但未集成到 RtsScreen。本阶段目标：

1. 补齐 2 个缺失 Mixin（LocalPlayer + ModdedRemote）
2. 实现寻路系统（服务端 1 个 + 客户端 5 个）
3. 完成服务层拆分（从 RtsStorageManager 提取 4 个 Service，整理其他 Manager）
4. 收尾第六阶段遗留（统一形状 / RtsScreen 集成 / RtsCraftTerminalScreen TODO）

---

## 二、Mixin 补充 (2 个)

### 2.1 背景

port 当前有 2 个 Mixin：`ContainerMixin`（容器）和 `ChestMenuMixin`（远程箱子距离绕过）。main 版本还有 2 个：
- `LocalPlayerMixin` — RTS 模式下欺骗服务端玩家仍在相机位置（防止被踢）
- `ModdedRemoteStillValidMixin` — Iron Furnaces / Generator Galore 容器 GUI 不因距离关闭

### 2.2 缺失清单

| Mixin | 目标类 (1.7.10) | 用途 | 优先级 |
|-------|-----------------|------|--------|
| `LocalPlayerMixin` | `EntityClientPlayerMP` | `onUpdateEntity()` 注入：RTS 模式激活时强制按相机实体位置发送移动包 | P1 |
| `ModdedRemoteStillValidMixin` | `ironfurnaces.container.BlockIronFurnaceContainerBase` 等 | `stillValid()` 始终返回 true（`@Pseudo`） | P2 |

### 2.3 LocalPlayerMixin 设计

main 版本通过注入 `isControlledCamera()` 实现，1.7.10 无此方法。替代方案：

```
注入点: EntityClientPlayerMP.onUpdateEntity()  // 每 tick 发送移动包之前
注入逻辑:
  - 检查 RTS 模式是否激活 (RtsClientState.rtsMode)
  - 若激活，用 RtsCameraEntity 的位置/视角替换 player 的位置
  - 发送完移动包后恢复原值
```

等效替代 `isControlledCamera()` 的地方：
1. `NetHandlerPlayClient` — 处理服务端位置校正时跳过
2. `EntityRenderer` — 渲染视角反转时使用相机位置

**具体注入**：

```java
@Mixin(EntityClientPlayerMP.class)
public abstract class LocalPlayerMixin {
    // 注入 onUpdateEntity()，RTS 模式下用相机位置发移动包
    @Inject(method = "onUpdateEntity", at = @At("HEAD"))
    private void rtsbuilding$overridePosition(CallbackInfo ci) { ... }
    
    // 注入 NetHandlerPlayClient，跳过服务端位置校验
    // 或注入 EntityClientPlayerMP，在接收到 S08PacketPlayerPosLook 时不强制传送
}
```

### 2.4 ModdedRemoteStillValidMixin 设计

```java
@Pseudo
@Mixin(targets = {
    "ironfurnaces.container.BlockIronFurnaceContainerBase",
    "generators.galore.container.BlockGeneratorContainerBase"
})
public abstract class ModdedRemoteStillValidMixin {
    @Overwrite
    public boolean stillValid(EntityPlayer player) { return true; }
}
```

### 2.5 文件清单

```
src/main/java/com/rtsbuilding/rtsbuilding/mixin/
├── LocalPlayerMixin.java                   — EntityClientPlayerMP 注入 (新建)
└── ModdedRemoteStillValidMixin.java        — 远程容器距离绕过 (新建)
```

修改文件：
```
src/main/resources/mixins.rtsbuilding.json     — 添加 2 个新 Mixin 声明
```

---

## 三、寻路系统 (6 个)

### 3.1 背景

main 版本支持玩家点击地面后自动寻路移动。1.7.10 使用 `PathNavigate` 替代现代寻路 API。

### 3.2 服务端 (1 个)

```
server/pathfinding/
└── RtsPathfindingService.java
      — 接收 C2SRtsPathfindingMessage（目标坐标）
      — 使用 Minecraft PathNavigate 计算路径
      — 驱动玩家实体沿路径移动
      — 到达目标或超时后停止
```

**1.7.10 适配**：
- `PathNavigate` → `PathNavigate`（1.7.10 同名，但 API 不同）
- 路径计算：`world.getEntityPathToXYZ(player, x, y, z, distance, canOpenDoors, ...)`
- 每 tick 调用 `onUpdateEntity()` 驱动移动
- 到达目标 1 格内判定为完成

### 3.3 客户端 (5 个)

```
client/pathfinding/
├── RtsClientPathfinding.java           — 客户端寻路协调器
├── RtsMovementModeRegistry.java        — 移动模式注册表 (WALK / FLIGHT / TELEPORT)
├── MovementModeHandler.java            — 移动模式处理器接口
├── BuiltinMovementModes.java           — 内置移动模式实现 (3 种)
└── MovementParams.java                 — 移动参数 (是否穿墙/速度/超时)
```

### 3.4 交互流程

```
1. 玩家右键地面 → InteractionViewModel 检测寻路目标
2. 发送 C2SRtsPathfindingMessage(x, y, z, mode)
3. RtsPathfindingService 计算路径并开始驱动
4. 每 tick 更新位置，发送 S2CRtsPathfindingUpdateMessage 给客户端
5. 客户端渲染移动目标指示器 (PlayerMoveTargetRenderer，后续阶段)
6. 到达后发送 S2CRtsPathfindingCompleteMessage
```

### 3.5 网络消息 (2 个)

| 消息 | 方向 | 功能 |
|------|------|------|
| `C2SRtsPathfindingMessage` | C→S | 请求寻路到目标坐标 |
| `S2CRtsPathfindingUpdateMessage` | S→C | 寻路进度更新 |

### 3.6 文件清单

```
新建 (6 个):
src/main/java/com/rtsbuilding/rtsbuilding/
├── server/pathfinding/RtsPathfindingService.java
├── client/pathfinding/RtsClientPathfinding.java
├── client/pathfinding/RtsMovementModeRegistry.java
├── client/pathfinding/MovementModeHandler.java
├── client/pathfinding/BuiltinMovementModes.java
└── client/pathfinding/MovementParams.java

新建 (2 个):
├── network/pathfinding/C2SRtsPathfindingMessage.java
└── network/pathfinding/S2CRtsPathfindingUpdateMessage.java
```

---

## 四、服务层拆分

### 4.1 现状

| Manager | 行数 | 需提取 |
|---------|------|--------|
| `RtsStorageManager` | 1131 | RtsPlacementService / RtsContainerService / RtsSessionService / RtsTransferService |
| `RtsMineManager` | ~300 | (当前结构尚可，延后) |
| `RtsCameraManager` | ~200 | (当前结构尚可，延后) |

### 4.2 拆分方案

**本轮优先拆分 RtsStorageManager 的 4 个职责域**：

| 提取 Service | 职责 | 提取方法 |
|-------------|------|----------|
| `RtsPlacementService` | 远程放置、音效、回滚 | `placeBlockDirect()`, `placeBlockBatch()`, 所有 `doPlace*` 系列 |
| `RtsContainerService` | 容器扫描、IInventory 交互、AE2 兼容委托 | `scanContainers()`, `extractFromIInventory()`, linked storage 查询 |
| `RtsSessionService` | 会话生命周期、维度切换、断线重连 | `createSession()`, `destroySession()`, `reconnectSession()` |
| `RtsTransferService` | 物品消耗/添加/传输 | `tryConsumeBlock()`, `addItemToStorage()`, `transferAll()` |

### 4.3 实施策略

- 每个 Service 从 RtsStorageManager 中提取相关方法，改为 `public static`
- RtsStorageManager 原有 public 方法保留签名，内部委托给新 Service
- 逐步减少 Manager 直接逻辑，最终成为 Facade
- 管线中已有的 Service（`RtsPendingPlacementService` / `RtsPlacedRecoveryService`）保持不变

### 4.4 文件清单

```
新建 (4 个):
src/main/java/com/rtsbuilding/rtsbuilding/server/service/
├── RtsPlacementService.java          — 提取自 RtsStorageManager 放置方法
├── RtsContainerService.java          — 提取自 RtsStorageManager 容器方法
├── RtsSessionService.java            — 提取自 RtsStorageManager 会话方法
└── RtsTransferService.java           — 提取自 RtsStorageManager 传输方法

修改 (1 个):
└── server/storage/RtsStorageManager.java  — 方法体替换为委托调用
```

---

## 五、第六阶段遗留收尾

### 5.1 统一形状系统

**当前**: QuickBuild 使用 `client/panel/quickbuild/BuildShape` 枚举，AreaMine 使用 byte 值判断形状。

**目标**: 将 `BuildShape` 提升到 `common/BuildShape`，AreaMine 管线统一使用。

| 文件 | 操作 |
|------|------|
| `common/BuildShape.java` | **新建** — 从 `client/panel/quickbuild/BuildShape` 复制并添加 `ordinal` → 枚举映射 |
| `client/panel/quickbuild/BuildShape.java` | **删除** — 替换为 `import ...common.BuildShape` |
| `server/pipeline/mining/AreaMineComputePipe.java` | 修改 — 用 `BuildShape` 替换 byte 形状判断 |
| `server/pipeline/mining/AreaMineExecutePipe.java` | 修改 — `isInShapePublic` 改为使用 `BuildShape` |
| 所有引用 `BuildShape` 的文件 | 修改 import |

### 5.2 RtsScreen 面板集成

**当前**: `RtsWorkflowPanel` / `RtsResumePlacementPanel` / `GuidePanel` 已建好但未在 RtsScreen 中渲染。

**需要**:
1. `RtsScreen` 在 `drawScreen()` 中调用 `rtsWorkflowPanel.draw()`
2. `RtsScreen` 在 `mouseClicked()` 中委托给面板
3. `GuidePanel` 在首次进入 RTS 模式时弹出（检查 `guideStep`）
4. `RtsResumePlacementPanel` 在 `pendingPlacementCount > 0` 时显示

### 5.3 RtsCraftTerminalScreen TODO

**位置**: `RtsCraftTerminalScreen.java:226`
**内容**: `// TODO: Send network message to pick up item from linked storage`

**实现**: 在合成终端点击关联存储物品时，发送 `C2SRtsCraftTerminalPickupMessage`（或复用现有消息），触发物品提取到玩家背包。

### 5.4 文件清单

```
新建 (1 个):
├── common/BuildShape.java                        — 统一形状枚举

删除 (1 个):
└── client/panel/quickbuild/BuildShape.java       — 提升到 common

修改 (~12 个):
├── client/panel/quickbuild/*.java                — import 更新 (约 6 个文件)
├── server/pipeline/mining/AreaMineComputePipe.java
├── server/pipeline/mining/AreaMineExecutePipe.java
├── client/screen/RtsScreen.java                  — 集成 3 个面板渲染 + 点击
├── client/screen/RtsCraftTerminalScreen.java     — 实现 TODO
└── network/RtsNetworkManager.java                — 注册 2 个寻路消息 (79-80)
```

---

## 六、文件清单汇总

### 6.1 需要新建的文件（15 个）

```
src/main/java/com/rtsbuilding/rtsbuilding/
├── mixin/
│   ├── LocalPlayerMixin.java                     — EntityClientPlayerMP 注入
│   └── ModdedRemoteStillValidMixin.java          — 远程容器距离绕过
├── server/pathfinding/
│   └── RtsPathfindingService.java                — 服务端寻路
├── client/pathfinding/
│   ├── RtsClientPathfinding.java                 — 客户端协调器
│   ├── RtsMovementModeRegistry.java              — 移动模式注册
│   ├── MovementModeHandler.java                  — 模式处理器接口
│   ├── BuiltinMovementModes.java                 — 内置模式实现
│   └── MovementParams.java                       — 移动参数
├── network/pathfinding/
│   ├── C2SRtsPathfindingMessage.java             — 寻路请求
│   └── S2CRtsPathfindingUpdateMessage.java       — 寻路进度
├── server/service/
│   ├── RtsPlacementService.java                  — 放置服务
│   ├── RtsContainerService.java                  — 容器服务
│   ├── RtsSessionService.java                    — 会话服务
│   └── RtsTransferService.java                   — 传输服务
└── common/
    └── BuildShape.java                           — 统一形状枚举
```

### 6.2 需要修改的文件（~18 个）

| 文件 | 修改内容 |
|------|----------|
| `mixins.rtsbuilding.json` | 添加 2 个新 Mixin 声明 |
| `server/storage/RtsStorageManager.java` | 方法方法体替换为委托到 4 个 Service |
| `client/panel/quickbuild/*.java` | BuildShape import 更新（约 6 个文件） |
| `server/pipeline/mining/AreaMineComputePipe.java` | 使用 BuildShape 枚举 |
| `server/pipeline/mining/AreaMineExecutePipe.java` | isInShapePublic 改用 BuildShape |
| `client/screen/RtsScreen.java` | 集成 3 个面板渲染 + 点击委托 |
| `client/screen/RtsCraftTerminalScreen.java` | 实现 "pick up item" TODO |
| `network/RtsNetworkManager.java` | 注册 2 个寻路消息 (79-80) |
| `client/RtsClientState.java` | 添加寻路状态字段 |
| `client/RtsClientEventHandler.java` | 注册寻路 tick handler |
| `server/RtsServerEventHandler.java` | 注册寻路 tick handler |
| `client/ClientProxy.java` | 初始化寻路客户端模块 |

### 6.3 可选删除文件

| 文件 | 操作 |
|------|------|
| `client/panel/quickbuild/BuildShape.java` | 删除，改用 `common/BuildShape` |

---

## 七、验收标准

1. **Mixin 补充**:
   - `LocalPlayerMixin` 正确注入 `EntityClientPlayerMP`，RTS 模式下不触发反作弊踢出
   - `ModdedRemoteStillValidMixin` 对 Iron Furnaces 容器保持 GUI 开启
   - `mixins.rtsbuilding.json` 声明正确，不与其他 Mixin 冲突

2. **寻路系统**:
   - 右键地面触发寻路请求
   - 玩家实体沿路径移动到目标 1 格内
   - `C2SRtsPathfindingMessage` / `S2CRtsPathfindingUpdateMessage` 正常工作
   - 支持 3 种移动模式（步行/飞行/传送）

3. **服务层拆分**:
   - `RtsStorageManager` 的 4 个职责域提取到独立 Service
   - 现有调用点行为不变（Manager public API 保持兼容）
   - `RtsMineManager` 和 `RtsCameraManager` 整理后维持现状

4. **遗留收尾**:
   - `BuildShape` 统一到 `common` 包，所有引用更新
   - `RtsWorkflowPanel` / `RtsResumePlacementPanel` / `GuidePanel` 在 RtsScreen 中正确渲染
   - `RtsCraftTerminalScreen` 的 TODO 已实现

5. **构建验证**: `.\gradlew spotlessApply` + `.\gradlew build` 通过

---

## 八、分步实施策略

### 第一步：Mixin 补充 + 注册
1. 实现 `LocalPlayerMixin`
2. 实现 `ModdedRemoteStillValidMixin`
3. 更新 `mixins.rtsbuilding.json`
4. `build` 验证 Mixin 编译

### 第二步：寻路系统
5. 实现 `MovementParams` + `MovementModeHandler` + `BuiltinMovementModes`
6. 实现 `RtsMovementModeRegistry`
7. 实现 `RtsClientPathfinding`
8. 实现 `RtsPathfindingService`
9. 实现 2 个寻路网络消息 + 注册
10. `build` 验证

### 第三步：服务层拆分
11. 提取 `RtsPlacementService`
12. 提取 `RtsContainerService`
13. 提取 `RtsSessionService`
14. 提取 `RtsTransferService`
15. 重构 `RtsStorageManager` 委托调用
16. `build` 验证

### 第四步：遗留收尾
17. 提升 `BuildShape` → `common/BuildShape`
18. 更新所有引用 import
19. 集成面板到 `RtsScreen`
20. 实现 `RtsCraftTerminalScreen` TODO
21. `spotlessApply` + `build` 最终验证

---

## 九、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `LocalPlayerMixin` 在 1.7.10 无明确注入点 | 中 | 研究 `NetHandlerPlayClient` 和 `EntityClientPlayerMP` 源码，用 `@Inject` + local capture 替代 `@Overwrite` |
| 寻路 `PathNavigate` API 差异大 | 中 | 参考原版生物 AI 寻路实现，渐进式验证 |
| 服务拆分破坏隐式耦合 | 中 | 拆分后立即 build 验证，保留旧方法签名 |
| BuildShape 提升引发大量 compile error | 低 | IDE 批量重构 + spotless 统一 |
| 寻路网络消息注册冲突 | 低 | disc 值从 79 开始，递增分配 |

---

## 十、后续阶段预览

| 阶段 | 内容 | 预计范围 |
|------|------|----------|
| 第八阶段 | 渲染器补充（蓝图/动画/覆盖层 15 个） | 15+ 渲染器文件 |
| 第九阶段 | API 层 + 测试框架 + 兼容模块收尾 | 12+ API + 测试 |

---

## 十一、审批确认

1. Mixin 补充优先级是否合理？（LocalPlayer P1，ModdedRemote P2）
2. 寻路系统 6 个文件范围是否可接受？
3. 服务层拆分仅限 RtsStorageManager 本轮是否合理？（RtsMineManager / RtsCameraManager 延后）
4. 第六阶段遗留收尾是否需调整优先级？
5. 是否需要调整分步顺序或刪减某些功能？
