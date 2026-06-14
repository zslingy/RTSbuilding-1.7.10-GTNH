# 细览 08 — 兼容层与 Mixin

> **关联主览**: [00-主览-项目结构总览.md](00-主览-项目结构总览.md) §3 (compat/ + mixin/)

---

## 1. 兼容层概览

RTSbuilding 为三个外部模组提供了兼容层，均采用 **软依赖** 策略：编译时可见，运行时缺失则静默降级。

### 文件清单

```
compat/
├── ae2/
│   └── RtsAe2Compat.java          ← AE2 ME 网络兼容
├── nei/
│   ├── RtsNeiCompat.java          ← NEI 配方覆盖层兼容
│   └── RtsCraftingOverlayHandler.java
└── remote/
    └── RtsRemoteMenuCompat.java   ← 远程菜单兼容

mixin/
└── ChestMenuMixin.java            ← 箱子菜单注入
```

---

## 2. AE2 兼容层 (compat/ae2/)

### 2.1 RtsAe2Compat.java

与 **Applied Energistics 2 Unofficial (rv3-beta-695-GTNH)** 集成：

```java
public class RtsAe2Compat {
    // 可用性检查
    public static boolean isAvailable();

    // ME 网络连接
    public static boolean tryConnectAe2(TileEntity te, ForgeDirection side);

    // 查询全部可用物品
    public static List<Ae2StorageEntry> queryAllItems(TileEntity te, ForgeDirection side);

    // 存取操作
    public static boolean extractItem(TileEntity te, ForgeDirection side,
                                       String itemId, int meta, long amount);
    public static boolean insertItem(TileEntity te, ForgeDirection side,
                                      ItemStack stack);

    // 内部数据类
    class Ae2StorageEntry {
        String itemId;
        int meta;
        long count;
    }
}
```

### 2.2 集成点

| 位置 | 调用 | 说明 |
|------|------|------|
| `RtsStorageSession.populateFromAe2()` | `RtsAe2Compat.queryAllItems()` | 从 ME 网络获取存储清单 |
| `RtsStorageSession.populateFromAe2Cached()` | 同上 + TTL 缓存 | 2 秒内重复请求直接返回缓存 |
| `RtsStorageManager` | `extractItem()` / `insertItem()` | 物品存取操作 |

### 2.3 TTL 缓存机制

```java
private long lastAe2QueryTime = 0;
private static final long AE2_QUERY_TTL_MS = 2000;

public boolean populateFromAe2Cached(TileEntity te, ForgeDirection side) {
    long now = System.currentTimeMillis();
    if (now - lastAe2QueryTime < AE2_QUERY_TTL_MS && !entries.isEmpty()) {
        return true;  // 缓存命中，跳过 ME 网络遍历
    }
    boolean ok = populateFromAe2(te, side);
    if (ok) lastAe2QueryTime = now;
    return ok;
}
```

### 2.4 AE2 绑定管理

```java
// RtsStorageSession 持久化 AE2 绑定
void setAe2Link(int x, int y, int z, int dimId);
void clearAe2Link();
boolean isAe2Linked();
```

---

## 3. NEI 兼容层 (compat/nei/)

### 3.1 RtsNeiCompat.java

与 **NotEnoughItems 2.8.48-GTNH** 集成：

```java
public class RtsNeiCompat {
    // 注册覆盖层处理器
    public static void registerNeiOverlayIfAvailable();

    // 配方转移处理
    public static boolean tryTransferRecipe(ItemStack[] items);
}
```

### 3.2 RtsCraftingOverlayHandler.java

NEI 配方覆盖层处理：

- 在 RTS 合成终端上显示 NEI 物品面板
- 支持 `Shift+Click` 配方自动填充
- 集成 JEI_TRANSFER 技能节点

### 3.3 注册时机

```java
// ClientProxy.initNeiCompat()
try {
    RtsNeiCompat.registerNeiOverlayIfAvailable();
} catch (Throwable t) {
    LOGGER.info("NEI not available, skipping NEI integration");
}
```

在 `ClientProxy.init()` 阶段注册，早于 GUI 打开，确保首次打开合成终端时 NEI 覆盖层已就绪。

### 3.4 依赖声明

```groovy
// dependencies.gradle
compileOnly("com.github.GTNewHorizons:NotEnoughItems:2.8.48-GTNH:dev")
```

使用 `compileOnly` + try-catch 保证 NEI 缺失时仍可启动。

---

## 4. 远程菜单兼容 (compat/remote/)

### RtsRemoteMenuCompat.java

处理远程打开容器 GUI 的兼容逻辑：

- 适配不同 mod 的容器 GUI 类型
- 提供通用的远程物品移动接口
- `C2SRtsImportMenuSlotMessage` / `C2SRtsCloseRemoteMenuMessage` 使用

---

## 5. Mixin 层

### 5.1 ChestMenuMixin.java

唯一的 Mixin 注入点：

```java
@Mixin(targets = "net.minecraft.inventory.ContainerChest")
public abstract class ChestMenuMixin {
    // 拦截箱子容器的物品操作
    // 为 RTS 远程 GUI 绑定提供钩子
}
```

Mixins 配置（两份 JSON 文件内容相同）：

```json
{
  "required": true,
  "package": "com.rtsbuilding.rtsbuilding.mixin",
  "compatibilityLevel": "JAVA_8",
  "refmap": "mixins.rtsbuilding.refmap.json",
  "client": [],
  "mixins": ["ChestMenuMixin"],
  "injectors": { "defaultRequire": 1 }
}
```

- `required: true` — Mixin 必须加载
- `compatibilityLevel: JAVA_8` — 目标环境 Java 8
- `client: []` — 无纯客户端 Mixin
- 仅 1 个 Mixin 类，注入到 `ContainerChest`

### 5.2 构建集成

构建系统通过 GTNH Convention 自动处理 Mixin：

```properties
# gradle.properties
usesMixins = true
mixinsPackage = mixin
```

- 自动生成 `mixins.rtsbuilding.refmap.json`（refmap）
- 自动验证 `mixinsPackage` 下的 Mixin 类存在性
- UniMixins 提供运行时 Mixin 加载框架

---

## 6. 兼容层设计原则

| 原则 | 实现方式 |
|------|---------|
| **软依赖** | 全部使用 `compileOnly` + try-catch |
| **静默降级** | 缺失时仅记录日志，不抛出异常 |
| **功能开关** | 兼容功能基于目标 mod 是否加载动态启用 |
| **接口隔离** | 每个兼容层独立一个类，失败只影响该功能 |
| **类型安全** | 不使用反射调用（与原始 1.21.1 版不同的是 1.7.10 采用硬引用） |

---

## 7. 依赖关系图

```
RTSbuilding
    │
    ├─ [必选] GTNHLib 0.7.10
    │     └─ JVM Downgrader 存根
    │
    ├─ [必需运行时] UniMixins
    │     └─ Mixin 加载框架
    │
    ├─ [可选] NotEnoughItems 2.8.48-GTNH
    │     └─ compat/nei/ → RtsNeiCompat
    │
    └─ [可选] Applied Energistics 2 Unofficial
          └─ compat/ae2/ → RtsAe2Compat
```