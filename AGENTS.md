# AGENTS.md — RTSbuilding-1.7.10-port

## Project Overview
- **Mod**: RTS Building: Build From Above (`rtsbuilding`), ported from 1.21.1 NeoForge to 1.7.10 Forge GTNH
- **Root Package**: `com.rtsbuilding.rtsbuilding`
- **Tech Stack**: Minecraft 1.7.10 / Forge 10.13.4.1614 / FML / MCP stable_12 / Gradle 9.3.1 (RetroFuturaGradle via `com.gtnewhorizons.gtnhconvention` plugin)
- **Java**: Jabel — modern Java syntax compiles to J8 bytecode; `.java-version` = 25 (required for Jabel) but output targets J8
- **Generated Class**: `com.rtsbuilding.rtsbuilding.Tags` (not in VCS, generated at build time from `gradle.properties` → `gradleTokenVersion`)

## Build & Run Commands
```bash
./gradlew build                    # Build the mod jar
./gradlew setupDecompWorkspace     # Local dev workspace setup (run once)
./gradlew setupCIWorkspace         # CI workspace setup (JitPack)
./gradlew spotlessCheck            # Check formatting (Eclipse + import order)
./gradlew spotlessApply            # Auto-fix formatting
```
- Spotless enforces: Eclipse formatter (`gtnhShared/spotless.eclipseformat.xml`), import order (`gtnhShared/spotless.importorder`: java → javax → net → org → com), removes unused imports, prettier for JSON
- `build/libs/rtsbuilding-NO-GIT-TAG-SET.jar` is the output; set a git tag for proper versioning

## Critical Build Quirk: SRG Mapping Patch
`build.gradle.kts` patches `packaged.srg` at jar time to add a missing FML SRG mapping (`FMLControlledNamespacedRegistry.getObject` → `a`). Without this, runtime throws `NoSuchMethodError`. If you modify the build script or update Forge/FML, verify this patch still applies.

## Project Structure
```
src/main/java/com/rtsbuilding/rtsbuilding/
├── RtsbuildingMod.java            # @Mod entry point, delegates to proxy
├── CommonProxy.java               # Server: entity reg, network, events, GUI handler
├── ClientProxy.java               # Client: 20 keybindings, renderers, input
├── Config.java                    # Forge Configuration (4 categories)
├── common/                        # BuilderMode enum
├── entity/                        # RtsCameraEntity (no-clip camera)
├── mixin/                         # ChestMenuMixin (remote chest distance bypass)
├── util/                          # BlockPos, RtsCountUtil, RtsPinyinSearch, RtsPlayerUtil
├── blueprint/                     # Blueprint system (readers, transforms, placement, network)
├── client/                        # UI: screens, panels, renderers, overlays, popups, view models
├── compat/                        # AE2, NEI, remote menu compatibility
├── network/                       # 63 network messages (IMessage + IMessageHandler)
├── progression/                   # Skill tree: nodes, features, costs/effects
└── server/                        # Storage, camera, mining, progression, tracking, data, menu
```

## Network Architecture
- `SimpleNetworkWrapper` channel `"rtsbuilding"` with 63 discriminators in `RtsNetworkManager.java`
- 7 sub-packages: `camera` (3), `builder` (17), `craft` (8), `feedback` (1), `progression` (9), `storage` (23), `blueprint.network` (2)
- Naming: `C2S*` = client→server, `S2C*` = server→client
- **When adding a new packet**: register in `RtsNetworkManager.registerMessages()` at the end of the appropriate section, incrementing `disc` — do NOT reuse discriminator numbers

## Key Coding Conventions
- **Formatting**: Spotless enforced — run `spotlessApply` before committing. Eclipse formatter 4-space indent, 120 char line width
- **Import Order**: java → javax → net → org → com
- **Line Endings**: LF (`.gitattributes` enforces)
- **Encoding**: UTF-8
- **FML Pattern**: `@Mod` + `@SidedProxy` (ClientProxy/CommonProxy), `@EventHandler` lifecycle
- **Rendering**: Tessellator + GL11 fixed-function pipeline (1.7.10 style)
- **GUI**: `GuiScreen` / `GuiContainer` / `IGuiHandler` pattern
- **Config**: `Configuration` class from Forge, synchronized in `CommonProxy.preInit()`
- **Mixin**: UniMixins, 1 mixin (`ChestMenuMixin`), `JAVA_8` compat level, `required: true`

## Dependencies (all compileOnly)
- `com.github.GTNewHorizons:GTNHLib:0.7.10:dev` — also provides JVM Downgrader stubs at runtime
- `com.github.GTNewHorizons:NotEnoughItems:2.8.48-GTNH:dev` — optional compat
- `com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-695-GTNH:dev` — optional compat

## Testing
- No test framework or `src/test/` directory exists. Verify changes by building and running `./gradlew runClient`.

## Documentation
- `迁移文档/` — 9 porting analysis documents (stages 1-7)
- `项目结构文档/` — 10 module analysis documents
- `错误日志.txt` — Build error log

## Language Convention (Chinese)
- **日常交流**：使用简体中文。
- **思考过程**：使用简体中文。
- **代码注释**：使用简体中文，涉及编程术语时保留英文原文
- **Git Commit**：使用中文，格式遵循 "feat: 新增功能"
