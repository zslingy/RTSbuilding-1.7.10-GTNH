# 第四阶段跟进更新计划 — 补全 no-op 桩与激活剩余 Handler

> 方向：补完所有 no-op 桩实现，激活剩余网络消息 Handler，修复遗留 bug，统一剩余网络入口

**制定日期**: 2026-06-20
**基准版本**: main v1.1.4 / port 第三阶段完成
**前置依赖**: 第三阶段 网络同步与调用点统一（已完成）

---

## 一、阶段目标

第三阶段完成后，管线系统主干已就绪，但仍有多个 no-op 桩和网络 Handler 未激活。本阶段目标：

1. 修复第三阶段遗留的管线注册 pipe 错位 bug
2. 激活所有剩余 no-op 网络消息 Handler（3 个 stub + 1 个 TODO）
3. 补全工具归还逻辑（`ToolReturnPipe` + 管道注册）
4. 实现蓝图导出功能（`BlueprintWriters`）
5. 实现挖掘负载分析（`RtsMiningRules`）
6. 统一剩余网络入口到管线（Break / QuickDrop）

---

## 二、第三阶段遗留项分析

### 2.1 管线注册 bug（需修复）

第三阶段将 AREA_MINE/AREA_DESTROY 管线的 pipe 从 `UltimineExecutePipe` 替换为专用 pipe 时，因多处相同字符串匹配失败，导致 pipe 错位：

| 管线 | 实际注册的 Pipe | 应该注册的 Pipe | 影响 |
|------|----------------|----------------|------|
| ULTIMINE | `AreaMineExecutePipe` | `UltimineExecutePipe` | 连锁挖掘会尝试按区域挖掘逻辑执行（缺失 bounds 参数导致 failure） |
| AREA_MINE | `AreaDestroyExecutePipe` | `AreaMineExecutePipe` | 区域挖掘会按区域摧毁逻辑执行（缺失 positions 参数导致 failure） |
| AREA_DESTROY | `UltimineExecutePipe` | `AreaDestroyExecutePipe` | 区域摧毁会按连锁挖掘逻辑执行（缺失 seed 坐标导致 failure） |

**结果**：三个管线都会因参数缺失而 `failure`，回退到直接逻辑执行——功能上仍可用，但管线系统未生效。

### 2.2 no-op 桩现状总览

| 文件 | 类型 | 是否注册进管线 | 运行时影响 |
|------|------|-------------|----------|
| `ToolReturnPipe` | no-op | **否** | 工具借出数据未被利用（工具从不由管线管理），无实际影响 |
| `PendingPlacementPipe` | no-op | **是**（PLACE_BATCH） | 放置后验证/确认阶段缺失，当前无害 |
| `HistoryRecordPipe` | no-op | **否** | 死代码，无影响 |
| `UltimineTickPipe` | no-op | **否** | 死代码，无影响 |

**结论**：4 个 no-op pipe 中只有 `PendingPlacementPipe` 实际参与运行时管道，其余均为预留桩。

### 2.3 网络 Handler stub 总览

| 消息 | 当前状态 | 影响 |
|------|---------|------|
| `S2CRtsDamageFeedbackMessage.Handler` | `return null` | 相机实体受伤无客户端反馈 |
| `C2SRtsQuestDetectMessage.Handler` | `return null` | 服务端不响应任务检测请求 |
| `S2CRtsQuestDetectStatusMessage.Handler` | `return null` | 任务检测进度无客户端 UI 反馈 |
| `C2SRtsLinkedQuickMoveMessage.Handler` | `return null` + TODO | 合成终端快速取物不可用 |
| `BlueprintWriters.writeBlueprint()` | 空方法 + stub 注释 | 蓝图导出功能缺失 |
| `RtsMiningRules.scanLoadout()` / `classifyTool()` | 返回空列表/NONE | 工具负载分析不可用 |

---

## 三、修复管线注册 bug

### 3.1 修改文件

| 文件 | 修改内容 |
|------|----------|
| `server/pipeline/core/RtsPipelineRegistration.java` | 纠正 ULTIMINE/AREA_MINE/AREA_DESTROY 的 pipe 引用 |

### 3.2 实现要点

```
ULTIMINE: AreaMineExecutePipe() → UltimineExecutePipe()
AREA_MINE: AreaDestroyExecutePipe() → AreaMineExecutePipe()
AREA_DESTROY: UltimineExecutePipe() → AreaDestroyExecutePipe()
```

不影响 import 行（三种 pipe 已在 import 中）。

---

## 四、激活网络消息 Handler

### 4.1 S2CRtsDamageFeedbackMessage.Handler — 受伤反馈

**文件**: `network/feedback/S2CRtsDamageFeedbackMessage.java`

**实现**: Handler 收到消息后调用客户端 HUD 渲染：
1. 在 `InteractionViewModel` 或 `RtsClientState` 中新增 `damageAmount` / `lowHealth` 字段
2. Handler 设置这些字段
3. 在 `RtsMineProgressHud` 或独立 HUD 渲染中绘制伤害数值闪烁和低血量警告

**1.7.10 适配**: 使用 `Minecraft.getMinecraft().fontRenderer.drawStringWithShadow()` 绘制伤害数字。

### 4.2 C2SRtsQuestDetectMessage.Handler — 服务端任务检测响应

**文件**: `network/progression/C2SRtsQuestDetectMessage.java`

**实现**:
1. Handler 验证 `RtsProgressionManager.canUse(player, RtsFeature.QUEST_DETECT)`
2. 调用 `RtsQuestDetectService`（新建简单服务/或在现有 ProgressionManager 中添加）执行检测
3. 返回 `S2CRtsQuestDetectStatusMessage(phase, scanned, completed)` 给客户端

**新建文件**: `server/progression/RtsQuestDetectService.java`

### 4.3 S2CRtsQuestDetectStatusMessage.Handler — 客户端任务检测 UI 反馈

**文件**: `network/progression/S2CRtsQuestDetectStatusMessage.java`

**实现**:
1. Handler 收到服务端进度后更新 `ProgressionViewModel`
2. 如果 `ProgressionViewModel` 不存在对应字段，直接在 Handler 中用 overlay 文字显示检测进度

### 4.4 C2SRtsLinkedQuickMoveMessage.Handler — 链接存储快速取物

**文件**: `network/storage/C2SRtsLinkedQuickMoveMessage.java`

**实现**:
1. Handler 从消息中获取 `itemId`、`meta`、`amount`
2. 调用 `RtsStorageManager.tryConsumeBlock()` 从链接存储提取物品
3. 将提取的物品插入玩家背包（`player.inventory.addItemStackToInventory()`）
4. 刷新存储页面

### 4.5 1.7.10 适配要点

| main 版本实现 | 1.7.10 适配方案 |
|--------------|----------------|
| `HUD overlay` | `GuiIngameForge` 或 `RenderGameOverlayEvent` 绘制 |
| `Component.literal()` | `new ChatComponentText("...")` |
| `PlayerInventory.add()` | `player.inventory.addItemStackToInventory()` |

---

## 五、实现工具归还（ToolReturnPipe）

### 5.1 修改文件

| 文件 | 修改内容 |
|------|----------|
| `server/pipeline/tool/ToolReturnPipe.java` | 实现归还逻辑 |
| `server/pipeline/core/RtsPipelineRegistration.java` | 在所有使用 ToolBorrowPipe 的管线末尾添加 ToolReturnPipe |

### 5.2 实现要点

**ToolReturnPipe.execute()**:
1. 从 `PipelineContext` 获取 `ToolBorrowPipe.KEY_TOOL_LEASE` 中保存的工具副本
2. 获取玩家当前手持物品
3. 如果当前手持为空且原持有不为空，将原持有还给玩家手持位
4. 如果原持有工具耐久减少（`getItemDamage() > leaseDamage`），从玩家背包中找同物品补回耐久消耗
5. 在 PLACE_SINGLE/PLACE_BATCH/QUICK_BUILD 管线中**不需要** ToolReturnPipe（放置不消耗工具耐久）

**管线注册更新**:

所有挖掘类管线（MINE_SINGLE/ULTIMINE/AREA_MINE/AREA_DESTROY）在 `UiRefreshPipe` 前添加 `ToolReturnPipe`：

```java
// MINE_SINGLE 示例
PipelineRegistry.<PipelineContext>register(RtsWorkflowType.MINE_SINGLE)
    .pipe(new ProgressionGatePipe(RtsFeature.REMOTE_BREAK))
    .pipe(new SessionValidatePipe())
    .pipe(new SessionDimensionPipe())
    .pipe(new StopPreviousPipe())
    .pipe(new WorkflowStartPipe(RtsWorkflowType.MINE_SINGLE))
    .pipe(new ToolBorrowPipe())
    .pipe(new MiningExecutePipe())
    .pipe(new ToolReturnPipe())          // ← 新增
    .pipe(new WorkflowProgressPipe())
    .pipe(new NetworkSyncPipe())
    .pipe(new UiRefreshPipe())
    .asyncCompletion()
    .register();
```

**注意**: MINE_SINGLE 管线当前用的是 `asyncCompletion()` 且没有 Progress/Sync pipe；本次一并补齐。

---

## 六、实现蓝图导出（BlueprintWriters）

### 6.1 修改文件

| 文件 | 修改内容 |
|------|----------|
| `blueprint/format/BlueprintWriters.java` | 实现 `writeBlueprint()` 方法 |

### 6.2 实现要点

**支持的导出格式**: 
- `VANILLA_NBT` — 使用 `CompressedStreamTools.writeCompressed()` 写入 NBT 格式（对齐 `BlueprintReaders.VANILLA_NBT`）

**writeBlueprint() 实现**:
1. 创建 `NBTTagCompound` root
2. 写入元数据（`palette` 方块列表、`size` 三维尺寸）
3. 写入 `blocks` 标签列表（每层一个 `NBTTagList`）
4. 使用 `CompressedStreamTools.writeCompressed(tag, new DataOutputStream(out))` 写入压缩输出流

**1.7.10 适配**: 使用 `CompressedStreamTools` 替代 NeoForge 的 `CompressedStreamTools.writeCompressed()`，API 相同。

---

## 七、实现挖掘负载分析（RtsMiningRules）

### 7.1 修改文件

| 文件 | 修改内容 |
|------|----------|
| `server/loadout/RtsMiningRules.java` | 实现 `scanLoadout()` 和 `classifyTool()` |
| （可选）`common/LoadoutTypes.java` | 如果 `MiningLoadoutRole` / `MiningLoadoutState` 未定义则新建 |

### 7.2 实现要点

**classifyTool()** — 根据物品类型分类：
- 镐类（`ItemPickaxe`） → `PICKAXE`
- 斧类（`ItemAxe`） → `AXE`
- 锹类（`ItemShovel`） → `SHOVEL`
- 其他工具 → `NONE`

**scanLoadout()** — 扫描玩家背包：
1. 遍历 `player.inventory.mainInventory`
2. 对每个工具调用 `classifyTool()` 分类
3. 按分类累计 `MiningLoadoutState`（包含工具 ItemStack 引用、挖掘速度乘数、耐久）
4. 返回 `List<MiningLoadoutState>`

**1.7.10 适配**: 使用 1.7.10 的工具类层级（`ItemPickaxe`/`ItemAxe`/`ItemSpade`）。

---

## 八、统一剩余网络入口

### 8.1 Break 消息：MINE_SINGLE 管线扩展

| 文件 | 修改内容 |
|------|----------|
| `network/builder/C2SRtsBreakMessage.java` | Handler 改为走 `MINE_SINGLE` 管线（复用挖掘管线） |

C2SRtsBreakMessage 本质是单方块立即破坏（不渐进），参数与 C2SRtsMineMessage 部分重叠（位置、面、工具）。Handler 改为：
1. 尝试走 MINE_SINGLE 管线
2. 失败则回退现有直接破坏逻辑

### 8.2 其余消息：无需管线化

以下消息不涉及工作流/存储操作，保持现有直接逻辑：

| 消息 | 原因 |
|------|------|
| `C2SRtsQuickDropMessage` | 单次背包整理操作 |
| `C2SRtsInteractMessage` | 远程右键交互，不涉及连续操作 |
| `C2SRtsRotateBlockMessage` | 单次方块旋转 |
| `C2SRtsUseItemMessage` | 手持物品使用 |
| `C2SRtsSetModeMessage` | 模式配置切换 |
| `C2SRtsStoreFluidMessage` | 流体存储操作 |
| `C2SRtsPlaceFluidMessage` | 流体放置操作 |

---

## 九、补全 MINE_SINGLE 管线

当前 MINE_SINGLE 管线缺少 WorkflowProgressPipe 和 NetworkSyncPipe：

```
当前: ... → ToolBorrowPipe → MiningExecutePipe → UiRefreshPipe → asyncCompletion
补全: ... → ToolBorrowPipe → MiningExecutePipe → ToolReturnPipe → WorkflowProgressPipe → NetworkSyncPipe → UiRefreshPipe → asyncCompletion
```

---

## 十、文件清单汇总

### 10.1 需要新建的文件（2 个）

```
src/main/java/com/rtsbuilding/rtsbuilding/
├── server/progression/
│   └── RtsQuestDetectService.java          — 任务检测服务
```

### 10.2 需要修改的文件（~14 个）

| 文件 | 修改内容 |
|------|----------|
| `server/pipeline/core/RtsPipelineRegistration.java` | 修复 pipe 错位 + 添加 ToolReturnPipe + 补全 MINE_SINGLE 管线 |
| `server/pipeline/tool/ToolReturnPipe.java` | 实现工具归还逻辑 |
| `network/feedback/S2CRtsDamageFeedbackMessage.java` | 激活 Handler |
| `network/progression/C2SRtsQuestDetectMessage.java` | 激活 Handler |
| `network/progression/S2CRtsQuestDetectStatusMessage.java` | 激活 Handler |
| `network/storage/C2SRtsLinkedQuickMoveMessage.java` | 激活 Handler |
| `network/builder/C2SRtsBreakMessage.java` | Handler 走 MINE_SINGLE 管线 |
| `blueprint/format/BlueprintWriters.java` | 实现 VANILLA_NBT 导出 |
| `server/loadout/RtsMiningRules.java` | 实现 scanLoadout/classifyTool |
| `client/InteractionViewModel.java` | 新增伤害反馈字段（如果不存在） |
| `client/ProgressionViewModel.java` | 新增任务检测进度字段（如果不存在） |

---

## 十一、验收标准

1. **管线注册**: ULTIMINE/AREA_MINE/AREA_DESTROY pipe 正确对应；MINE_SINGLE 管线补全 Progress + Sync + Return
2. **网络 Handler**:
   - S2CRtsDamageFeedbackMessage 客户端可接收并显示伤害
   - C2SRtsQuestDetectMessage 服务端返回检测结果
   - S2CRtsQuestDetectStatusMessage 客户端可显示检测进度
   - C2SRtsLinkedQuickMoveMessage 可从链接存储提取物品
3. **ToolReturnPipe**: 挖掘管线末尾有 ToolReturnPipe，确保工具数据被正确管理
4. **BlueprintWriters**: `writeBlueprint()` 可导出 NBT 格式蓝图
5. **RtsMiningRules**: `scanLoadout()` 可分类玩家背包工具
6. **构建验证**: `.\gradlew spotlessApply` + `.\gradlew build` 通过

---

## 十二、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 管线注册修错后引入新的签名不匹配 | 中 | 逐管线编译验证 |
| ToolReturnPipe 归还逻辑与现有工具管理冲突 | 低 | ToolReturnPipe 操作的是 `toolLease` 副本，不影响原始工具 |
| 任务检测服务依赖外部模组 API | 中 | 先用 HQM/BetterQuesting 反射兼容，失败时静默回退 |
| 蓝图导出 NBT 格式与读取格式不对称 | 低 | 先实现最简格式，后续迭代补充 |
| 1.7.10 任务检测服务缺少 API | 中 | 先实现基础框架 + 日志输出，特定模组 API 在兼容层补充 |

---

## 十三、Java 版本说明

GTNH 通过 JVM Downgrader 和 UniMixins 在 Java 25 上运行 1.7.10 Forge。编译目标仍然是 J8 bytecode（通过 Jabel），但开发时可以使用更高版本的 Java 语法特性。本阶段实现中：
- 仍优先使用 Java 8 兼容写法以保持一致性
- 网络消息仍使用 `IMessage` / `IMessageHandler` 模式（1.7.10 Forge 标准）
- `CompressedStreamTools` 直接使用，API 与 NeoForge 相同

---

## 十四、后续阶段预览

| 阶段 | 内容 | 预计范围 |
|------|------|----------|
| 第五阶段 | 历史/撤销系统 + 挂起放置恢复 | 8 个新文件 + 5 个新网络消息 |
| 第六阶段 | 渲染器补充 + 客户端 UI 补充 | 20+ 个新文件 |
| 第七阶段 | Mixin 补充 + 寻路系统 + 服务层拆分 | 15+ 个文件 |
| 第八阶段 | API 层 + 测试框架 | 12+ 个文件 |

---

## 十五、审批确认

请审阅以上计划，确认以下事项：

1. 管线注册 bug 修复方案是否合理？
2. 4 个网络 Handler 激活的范围是否可接受？
3. ToolReturnPipe 的实现方案（仅挖掘管线追加）是否合适？
4. 蓝图导出仅支持 VANILLA_NBT 格式是否满足当前需求？
5. 是否有其他需要纳入本阶段的遗漏项？
