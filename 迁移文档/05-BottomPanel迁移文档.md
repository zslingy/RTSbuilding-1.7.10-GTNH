# RTS BottomPanel 迁移文档

> 从 NeoForge 1.21 BuilderScreen.BottomPanel 迁移到 1.7.10 RtsBottomPanel
> 生成时间: 2026-06-11

## 原版布局概览 (BuilderScreenConstants)

```
屏幕布局:
┌─────────────────────────────────────────────────┐
│ 顶部栏 (TOP_H=52)                                │
│ 模式按钮 + 快速建造 + 连锁挖掘 + 蓝图入口          │
├─────────────────────────────────────────────────┤
│                                                   │
│  3D 世界视图区域 (可交互)                           │
│                                                   │
├─────────────────────────────────────────────────┤
│ 底部面板 (BOTTOM_PANEL)                           │
│ ┌─────────────────────────────────────────────┐  │
│ │ 标签栏 (HEADER_H=18)                         │  │
│ │ "RTS" | STORAGE | CREATIVE | BLUEPRINTS     │  │
│ │        右侧: 物品状态 + 引导/刷新按钮          │  │
│ ├─────────────────────────────────────────────┤  │
│ │ 搜索栏 + 分页器                               │  │
│ ├─────────────────────────────────────────────┤  │
│ │ 工具区 (TOOL_AREA_H=18)                       │  │
│ │ [0][1]...[8][空手] | [Pin0][Pin1]...          │  │
│ ├──────────────┬──────────────────────┬───────┤  │
│ │ 分类面板      │ 存储网格(可调行数)      │ 合成  │  │
│ │ CATEGORY_W   │ + 最近物品网格          │ 面板  │  │
│ │ =124         │ SLOT=22                │ 126   │  │
│ └──────────────┴──────────────────────┴───────┘  │
│ 排序按钮 (S/A/D/+/-) + 合成底座                    │
└─────────────────────────────────────────────────┘
```

## 关键常量

| 常量 | 值 | 说明 |
|------|-----|------|
| DEFAULT_BOTTOM_H | 110 | 默认底部面板高度 |
| MIN_BOTTOM_H | 72 | 最小高度 |
| MAX_BOTTOM_H | 320 | 最大高度 |
| BOTTOM_PANEL_PADDING | 8 | 内边距 |
| BOTTOM_PANEL_HEADER_H | 18 | 标题栏高度 |
| SLOT | 22 | 存储格子大小 |
| HOTBAR_SLOT | 18 | 快捷栏格子 |
| HOTBAR_PITCH | 20 | 快捷栏间距 |
| CRAFT_PANEL_W | 126 | 合成面板宽度 |
| CATEGORY_W | 124 | 分类面板宽度 |

## 布局计算 (从 PanelLayouts.java 源码提取)

```java
// 底部面板
panelX = 4, panelW = screenW - 8
panelY = screenH - bottomH - 4 (底部留4px边距)

// 分类面板
categoryX = panelX + BOTTOM_PANEL_PADDING
categoryY = panelY + BOTTOM_PANEL_HEADER_H + 4 + searchH + 4 + TOOL_AREA_H + 4

// 存储网格
storageX = categoryX + CATEGORY_W + 4
storageY = categoryY
storageW = panelW - BOTTOM_PANEL_PADDING*2 - CATEGORY_W - 4 - CRAFT_PANEL_W - CRAFT_PANEL_GAP

// 合成面板
craftPanelX = panelX + panelW - BOTTOM_PANEL_PADDING - CRAFT_PANEL_W
craftPanelY = categoryY

// 可显示行数计算公式
storageRows = Math.max(MIN_STORAGE_GRID_ROWS,
    (panelH - BOTTOM_PANEL_HEADER_H - 4 - searchH - 4 - TOOL_AREA_H - 4 - GRID_BOTTOM_PADDING) / SLOT)
```

## 当前移植状态

### 已存在的面板 (独立渲染，位置冲突)
| 面板 | 当前布局方式 | 问题 |
|------|-------------|------|
| StorageGridView | `gridY = screen.height - 240 + 28` | 硬编码位置，与其他面板重叠 |
| StorageCategoryView | 侧栏 | 位置可能与主网格冲突 |
| RecentGridView | 最近使用 | 未与主网格对齐 |
| CraftPanelView | 合成 | 位置独立 |
| PinSlotView | 快捷栏 | 未嵌入底部面板 |

### 缺失的功能
- 底部面板框架/边框 (原版: `drawPanelFrame` 带圆角和阴影)
- 标签页切换 (STORAGE / CREATIVE / BLUEPRINTS)
- 排序按钮 (S/A/D/+/-)
- 引导/刷新按钮 (i/R)
- 工具区 (原版9格hotbar + Pin固定位)
- 分页器
- 搜索清除按钮
- 空手按钮

## 迁移策略

采用**容器面板模式**: 创建 `RtsBottomPanel` 作为底部区域容器，
内部协调所有子面板的布局，类似原版 BottomPanel 但精简实现。

1. `RtsBottomPanel` 实现 IRtsPanel，作为所有底部面板的容器
2. 子面板通过 `RtsBottomPanel` 获取计算好的布局坐标
3. 子面板只渲染自己的内容区域，不自行决定位置
4. `RtsBottomPanel` 负责: 框架、标签栏、搜索区、工具区

### 标签页方案
- 简化标签: STORAGE (默认)、BLUEPRINTS
- 不实现 CREATIVE (1.7.10 创造模式逻辑不同)
- 不实现可调高度 (固定 DEFAULT_BOTTOM_H=110)

### 工具区方案
- 渲染玩家9格快捷栏 (从 inventory.getItem(i) 获取)
- 空手按钮
- 固定位 (从 PinSlotView 迁移)

### 合成面板
- 精简 CraftPanelView 嵌入容器布局
- 合成搜索框放在底部面板搜索栏旁边