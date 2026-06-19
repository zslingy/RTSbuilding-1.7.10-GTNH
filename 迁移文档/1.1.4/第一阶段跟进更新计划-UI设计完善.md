# 第一阶段跟进更新计划 — UI 设计完善

> 方向：沿用 main 版本 UI 纹理资源，修复 port 版本 UI 未使用纹理的问题

**制定日期**: 2026-06-19  
**基准版本**: main v1.1.4 / port 阶段7完成  
**预估工作量**: 3-4 天（单人全职）

---

## 一、阶段目标

将 port 版本的 UI 系统从"纯代码绘制"迁移到"纹理渲染"，使 UI 视觉效果与 main 版本保持一致。

**核心问题**：
- port 版本有 47 个纹理文件（来自 main 版本），但**完全没有被代码使用**
- 所有 UI 组件使用 `Gui.drawRect` + Unicode 符号纯代码绘制
- 顶栏按钮使用 `⛏ ⚒ ◻ ℹ ⚙` 等 Unicode 字符，而不是纹理图标

**修复方向**：
1. 保留 main 版本的纹理资源（已正确复制到 `assets/rtsbuilding/textures/`）
2. 删除冗余的嵌套目录 `assets/rtsbuilding/rtsbuilding/`
3. 修改 UI 组件代码，使用纹理渲染替代纯代码绘制

---

## 二、现状分析

### 2.1 纹理资源现状

**已存在的纹理文件**（47个）：

| 目录 | 文件数 | 说明 |
|------|--------|------|
| `textures/gui/topbar/` | 36个 | 9个按钮 × 4状态（active/hover/inactive/pressed） |
| `textures/gui/general/` | 4个 | 通用按钮纹理 |
| `textures/gui/quickbuild/` | 7个 | 快速建造形状图标 |

**冗余目录**：
- `assets/rtsbuilding/rtsbuilding/textures/` — main 版本残留副本，与标准路径完全相同

### 2.2 UI 组件绘制方式分析

**当前状态：所有 UI 使用纯代码绘制，纹理完全未使用**

| 组件 | 当前绘制方式 | 应改为 |
|------|--------------|--------|
| **RtsTopBarPanel** | `Gui.drawRect` + Unicode 符号（⛏ ⚒ ◻ ℹ ⚙） | 纹理渲染（topbar/*.png） |
| **RtsBottomPanel** | `Gui.drawRect` + `FontRenderer` | 保持纯代码绘制（无对应纹理） |
| **GearMenuPanel** | `Gui.drawRect` + `GL11.glScissor` | 保持纯代码绘制（无对应纹理） |
| **RtsWindowPanel** | `GL11` + `Tessellator` 自定义 drawRect | 保持纯代码绘制（基类） |
| **BlueprintPanel** | `Gui.drawRect` | 保持纯代码绘制（无对应纹理） |
| **QuickBuildPanel** | `Gui.drawRect` + `FontRenderer` | 可选用 quickbuild/*.png 纹理 |
| **UltiminePanel** | `Gui.drawRect` + `FontRenderer` | 保持纯代码绘制（无对应纹理） |
| **FunnelPanel** | `Gui.drawRect` | 保持纯代码绘制（无对应纹理） |

### 2.3 顶栏按钮纹理映射

main 版本的顶栏按钮使用纹理图标，port 版本使用 Unicode 符号：

| 按钮 | main 版本纹理 | port 版本 Unicode | 需要修改 |
|------|---------------|-------------------|----------|
| Interact | `mode_interact_*.png` | 文本"交互" | ✅ |
| Link Storage | `mode_link_*.png` | 文本"存储绑定" | ✅ |
| Rotate | `mode_rotate_*.png` | 文本"旋转" | ✅ |
| Funnel | `mode_funnel_*.png` | 文本"漏斗" | ✅ |
| Quick Build | `quick_build_*.png` | `⚒` | ✅ |
| Ultimine | `ultimine_*.png` | `⛏` | ✅ |
| Chunk View | `chunk_view_*.png` | `◻` | ✅ |
| Guide | `quest_detect_*.png` | `ℹ` | ✅ |
| Gear | `settings_gear_*.png` | `⚙` | ✅ |

---

## 三、实施计划

### 步骤 1：资源目录清理（0.5 小时）

**任务**：

1. **删除冗余嵌套目录**
   - 删除 `assets/rtsbuilding/rtsbuilding/` 整个目录
   - 该目录是 main 版本残留副本，与标准路径 `assets/rtsbuilding/` 完全相同
   - 保留标准路径下的 47 个纹理文件

2. **验证纹理文件完整性**
   - 确认 `assets/rtsbuilding/textures/gui/topbar/` 有 36 个文件
   - 确认 `assets/rtsbuilding/textures/gui/general/` 有 4 个文件
   - 确认 `assets/rtsbuilding/textures/gui/quickbuild/` 有 7 个文件

**输出**：
- 干净的资源目录结构
- 纹理文件完整保留

### 步骤 2：顶栏按钮纹理化改造（1-1.5 天）

**任务**：

1. **修改 `RtsTopBarPanel.java`**
   - 添加纹理 `ResourceLocation` 定义
   - 修改按钮渲染逻辑，使用 `bindTexture` + `drawTexturedModalRect` 替代 `Gui.drawRect`
   - 根据按钮状态（active/hover/inactive/pressed）选择对应纹理
   - 移除 Unicode 符号（⛏ ⚒ ◻ ℹ ⚙）

2. **纹理渲染技术方案**
   ```java
   // 1.7.10 纹理渲染标准方式
   Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE_LOCATION);
   Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, textureWidth, textureHeight);
   ```

3. **按钮状态判断**
   - `inactive` — 默认状态
   - `hover` — 鼠标悬停
   - `active` — 模式激活（如当前交互模式）
   - `pressed` — 鼠标按下

**输出**：
- 修改后的 `RtsTopBarPanel.java`（使用纹理渲染）

### 步骤 3：快速建造面板纹理化改造（0.5 天）

**任务**：

1. **修改 `QuickBuildPanel.java`**
   - 添加形状图标纹理（`quickbuild/*.png`）
   - 修改形状按钮渲染，使用纹理替代文本图标
   - 保留文本标签作为辅助说明

2. **纹理映射**
   - `single_block.png` — 单块形状
   - `line_block.png` — 直线形状
   - `square_block.png` — 方形形状
   - `wall_block.png` — 墙形状
   - `circle_block.png` — 圆形形状
   - `box_block.png` — 立方体形状
   - `radio_button.png` — 单选按钮

**输出**：
- 修改后的 `QuickBuildPanel.java`（使用纹理图标）

### 步骤 4：通用按钮组件纹理支持（0.5 天）

**任务**：

1. **创建 `WindowButton` 组件**
   - 支持纹理模式（从 `general/*.png` 加载）
   - 支持纯代码绘制模式（向后兼容）
   - 支持 4 状态：inactive/hover/active/pressed

2. **纹理资源**
   - `default_button.png` — 默认按钮
   - `mode_button.png` — 模式按钮
   - `switch_button.png` — 切换按钮
   - `close_button.png` — 关闭按钮

3. **集成到现有面板**
   - `RtsTopBarPanel` — 使用 `mode_button.png` 作为按钮背景
   - `GearMenuPanel` — 使用 `switch_button.png` 作为切换开关
   - `RtsWindowPanel` — 使用 `close_button.png` 作为关闭按钮

**输出**：
- 新建 `client/widget/WindowButton.java`
- 修改相关面板集成

### 步骤 5：关闭按钮纹理化（0.5 小时）

**任务**：

1. **修改 `RtsWindowPanel.java`**
   - 将关闭按钮从纯代码绘制（`"x"` 文本）改为纹理渲染
   - 使用 `general/close_button.png` 纹理
   - 保留 hover 效果

**输出**：
- 修改后的 `RtsWindowPanel.java`

### 步骤 6：验证与测试（0.5 天）

**任务**：

1. **编译验证**
   - `./gradlew build` 编译通过
   - `./gradlew spotlessCheck` 格式检查通过

2. **运行时测试**
   - `./gradlew runClient` 启动客户端
   - 验证顶栏按钮纹理正确显示
   - 验证快速建造形状图标正确显示
   - 验证关闭按钮纹理正确显示
   - 验证按钮状态切换（hover/active/pressed）

3. **视觉对比**
   - 与 main 版本截图对比，确保视觉效果一致

**输出**：
- 编译通过的 jar 文件
- 测试报告

---

## 四、文件清单

### 4.1 需要删除的目录

| 路径 | 说明 |
|------|------|
| `assets/rtsbuilding/rtsbuilding/` | main 版本残留副本（47个PNG + 4个JSON） |

### 4.2 需要修改的文件

| 文件 | 修改内容 |
|------|----------|
| `RtsTopBarPanel.java` | 按钮渲染从 Unicode 改为纹理 |
| `QuickBuildPanel.java` | 形状按钮从文本改为纹理图标 |
| `RtsWindowPanel.java` | 关闭按钮从文本改为纹理 |
| `GearMenuPanel.java` | 切换开关可选用纹理（可选） |

### 4.3 需要新建的文件

| 文件 | 说明 |
|------|------|
| `client/widget/WindowButton.java` | 通用纹理按钮组件 |

---

## 五、工作量评估

| 步骤 | 任务 | 工作量 | 依赖 |
|------|------|--------|------|
| 步骤 1 | 资源目录清理 | 0.5 小时 | 无 |
| 步骤 2 | 顶栏按钮纹理化改造 | 1-1.5 天 | 步骤 1 |
| 步骤 3 | 快速建造面板纹理化改造 | 0.5 天 | 步骤 1 |
| 步骤 4 | 通用按钮组件纹理支持 | 0.5 天 | 步骤 1 |
| 步骤 5 | 关闭按钮纹理化 | 0.5 小时 | 步骤 4 |
| 步骤 6 | 验证与测试 | 0.5 天 | 步骤 2-5 |
| **总计** | | **3-4 天** | |

---

## 六、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 纹理在 1.7.10 中渲染异常 | 中 | 使用标准 `drawTexturedModalRect` API |
| 按钮状态判断逻辑复杂 | 低 | 参考 main 版本实现 |
| 纹理尺寸与布局不匹配 | 低 | 保持原有布局常量，纹理按比例缩放 |
| 部分面板无对应纹理 | 低 | 保持纯代码绘制（如 RtsBottomPanel） |

---

## 七、验收标准

1. **资源目录**：
   - `assets/rtsbuilding/rtsbuilding/` 目录已删除
   - 47 个纹理文件完整保留在 `assets/rtsbuilding/textures/`

2. **UI 渲染**：
   - 顶栏 9 个按钮使用纹理渲染，不再使用 Unicode 符号
   - 快速建造形状按钮使用纹理图标
   - 关闭按钮使用纹理渲染
   - 按钮状态切换（hover/active/pressed）视觉效果正确

3. **编译验证**：
   - `./gradlew build` 编译通过
   - `./gradlew spotlessCheck` 格式检查通过

---

## 八、后续阶段衔接

第一阶段完成后，UI 纹理渲染问题将解决。后续阶段：

- **第二阶段**：工作流引擎 + 管线系统（阶段8）
- **第三阶段**：网络消息 + 撤销系统（阶段9）
- **第四阶段**：渲染器补充（阶段10）— 蓝图/动画/覆盖层渲染器

---

## 九、审批确认

请审阅以上计划，确认以下事项：

1. 删除 `assets/rtsbuilding/rtsbuilding/` 冗余目录是否同意？
2. 顶栏按钮从 Unicode 改为纹理渲染的方案是否同意？
3. 哪些面板需要纹理化改造？（当前计划：RtsTopBarPanel、QuickBuildPanel、RtsWindowPanel）
4. 工作量估算是否可接受？

**待用户审批后开始实施。**
