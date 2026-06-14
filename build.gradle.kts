plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Fix: Inject missing FML-owned class SRG mappings into the reobfuscation pipeline.
// MCP stable_12 + Forge mcp-srg.srg do not cover FML classes like
// FMLControlledNamespacedRegistry, so RetroFuturaGradle cannot remap their
// MCP method names back to runtime (Notch) names.
//
// At runtime FMLControlledNamespacedRegistry.getObject(Object) is named a(Object),
// but without this mapping it stays as getObject → NoSuchMethodError.
//
// We patch build/resources/patchedMc/packaged.srg just before the jar task
// so the reobfuscation step picks up the extra mapping.
tasks.named("jar") {
    doFirst {
        val srgFile = layout.buildDirectory
            .file("resources/patchedMc/packaged.srg")
            .get().asFile
        if (srgFile.exists()) {
            val extraMapping = "MD: cpw/mods/fml/common/registry/FMLControlledNamespacedRegistry/getObject" +
                " (Ljava/lang/Object;)Ljava/lang/Object; " +
                "cpw/mods/fml/common/registry/FMLControlledNamespacedRegistry/a" +
                " (Ljava/lang/Object;)Ljava/lang/Object;"
            // Avoid appending duplicate lines
            if (!srgFile.readText().contains("FMLControlledNamespacedRegistry/getObject")) {
                srgFile.appendText("\n" + extraMapping + "\n")
            }
        }
    }
}