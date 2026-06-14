# AGENTS.md — RTSbuilding-1.7.10-port

## Project Overview
- **Mod Name**: RTS Building: Build From Above
- **Mod ID**: `rtsbuilding`
- **Root Package**: `com.rtsbuilding.rtsbuilding`
- **Minecraft Version**: 1.7.10
- **Forge Version**: 10.13.4.1614
- **Mod Loader**: FML (Forge Mod Loader) via GTNH Convention plugin
- **Mappings**: MCP stable_12
- **Java Syntax**: Jabel (modern Java syntax compiled to J8 bytecode)
- **Gradle Version**: 9.3.1
- **License**: LGPL v3
- **Authors**: JerryLunar, H-crab; ported by 五世桃花亭
- **Ported From**: RTSbuilding-main (1.21.1 NeoForge → 1.7.10 Forge GTNH)
- **Source Files**: ~120 Java files

## Build Commands
```bash
./gradlew build                    # Build the mod
./gradlew setupCIWorkspace         # CI workspace setup (JitPack)
./gradlew setupDecompWorkspace     # Local dev workspace setup
```

## Project Structure
```
src/main/java/com/rtsbuilding/rtsbuilding/
├── RtsbuildingMod.java            # @Mod entry point, delegates to proxy
├── CommonProxy.java               # Server proxy: entity, network, events, GUI handler
├── ClientProxy.java               # Client proxy: 21 keybindings, renderers, input
├── Config.java                    # Forge Configuration (4 categories, 7 values)
├── RtsCommunityLinks.java         # Discord/GitHub/QQ links
├── common/BuilderMode.java        # Builder mode enum
├── entity/RtsCameraEntity.java    # No-clip camera entity (EntityLivingBase)
├── mixin/ChestMenuMixin.java      # Remote chest menu distance bypass
├── util/                          # BlockPos, RtsCountUtil, RtsPinyinSearch, RtsPlayerUtil
├── blueprint/                     # Blueprint system (format readers/writers, transforms, placement)
├── client/                        # Client UI: screens, panels, renderers, overlays, popups
├── compat/                        # AE2, NEI, remote menu compatibility
├── network/                       # 62 network discriminators (IMessage + IMessageHandler)
├── progression/                   # Skill tree: 19 nodes, 16 features, costs/effects
└── server/                        # Server core: storage, camera, mining, progression, tracking
```

## Key Source Files
| File | Lines | Purpose |
|------|-------|---------|
| `RtsbuildingMod.java` | 53 | @Mod entry point |
| `ClientProxy.java` | 351 | Client init, keybindings, renderers |
| `CommonProxy.java` | 88 | Server init, entity reg, network |
| `Config.java` | 212 | Forge Configuration |
| `RtsNetworkManager.java` | 340 | 62 packet discriminators |
| `RtsStorageManager.java` | 545 | Storage linking/scanning/transfers |
| `RtsCameraManager.java` | varies | Camera session management |
| `RtsScreen.java` | varies | Main RTS GUI screen |
| `RtsWorldRenderer.java` | varies | World overlay renderer |
| `ChestMenuMixin.java` | varies | Remote chest menu mixin |

## Coding Conventions
- **Style**: Eclipse formatter (4-space indent, 120 char line length), enforced by Spotless
- **Import Order**: java → javax → net → org → com (see `gtnhShared/spotless.importorder`)
- **Line Endings**: LF (enforced via `.gitattributes`)
- **Encoding**: UTF-8
- **Annotations**: `@Mod`, `@Instance`, `@SidedProxy`, `@EventHandler` (FML pattern)
- **Network**: `SimpleNetworkWrapper` with `IMessage` + `IMessageHandler` pattern
- **GUI**: `GuiScreen` / `GuiContainer` (1.7.10 style)
- **Rendering**: Tessellator + GL11 (fixed-function pipeline)
- **Config**: `Configuration` class from Forge

## Architecture Notes
- **Client-Server Proxy**: `@SidedProxy` pattern (ClientProxy / CommonProxy)
- **Network**: 62 discriminators across 7 sub-packages (builder, camera, craft, feedback, progression, storage, blueprint)
- **Mixin**: UniMixins, 1 mixin (ChestMenuMixin), `JAVA_8` compat level
- **Dependencies**: GTNHLib 0.7.10, NEI 2.8.48-GTNH, AE2 Unofficial rv3-beta-695-GTNH (all compileOnly)

## Resources
- `src/main/resources/mcmod.info` — Mod metadata (JSON v2 with ${} tokens)
- `src/main/resources/mixins.rtsbuilding.json` — Mixin config
- `src/main/resources/assets/rtsbuilding/lang/` — 4 locales (en_US, zh_CN, zh_HK, zh_TW)
- `src/main/resources/assets/rtsbuilding/textures/gui/` — UI textures (topbar, quickbuild, general)

## Testing
- No test framework configured. No `src/test/` directory.

## Documentation
- `迁移文档/` — 9 porting analysis/migration documents (stages 1-7)
- `项目结构文档/` — 10 project structure documents (detailed module analysis)
- `错误日志.txt` — Build error log

## Dependencies (compileOnly)
- `com.github.GTNewHorizons:GTNHLib:0.7.10:dev`
- `com.github.GTNewHorizons:NotEnoughItems:2.8.48-GTNH:dev`
- `com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-695-GTNH:dev`

<!-- AGENTS.md -->
# 项目语言规范

- **日常交流**：使用简体中文。
- **代码注释**：使用简体中文，涉及编程术语时，请保留英文原文并（在必要时）提供中文解释。
- **Git Commit**：使用中文，格式遵循 "feat: 新增功能"。
