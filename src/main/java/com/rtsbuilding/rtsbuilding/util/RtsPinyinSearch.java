package com.rtsbuilding.rtsbuilding.util;

import java.lang.reflect.Method;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

/**
 * RTS 拼音搜索 — 运行时兼容 NotEnoughCharacters 的 PinIn 引擎。
 * 
 * 策略：
 * 1. 反射检测 me.towdium.pinin.PinIn（NEC 依赖库）
 * 2. 可用 → 创建 PinIn 实例，调用 contains() 做拼音匹配
 * 3. 不可用 → 降级为简单子串匹配
 * 
 * 性能注意：PinIn 实例创建较重，使用静态单例。
 */
public class RtsPinyinSearch {

    /** PinIn 引擎单例，null = 不可用 */
    private static Object PININ_INSTANCE;
    /** contains 方法引用 */
    private static Method CONTAINS_METHOD;
    /** 是否已初始化 */
    private static boolean initialized = false;
    /** 是否拼音搜索可用 */
    private static boolean available = false;

    private RtsPinyinSearch() {}

    // ======== 初始化 ========

    private static void init() {
        if (initialized) return;
        initialized = true;

        try {
            // 反射加载 PinIn
            Class<?> pinInClass = Class.forName("me.towdium.pinin.PinIn");
            Object dictLoader = createDictLoader();
            if (dictLoader == null) {
                RtsbuildingMod.LOGGER.warn("[RtsPinyin] PinIn DictLoader not found, falling back to substring match");
                return;
            }

            // 创建 PinIn 实例
            PININ_INSTANCE = pinInClass.getConstructor(
                dictLoader.getClass()
                    .getInterfaces()[0])
                .newInstance(dictLoader);

            // 调用 config().accelerate(true).commit()
            Method configMethod = pinInClass.getMethod("config");
            Object config = configMethod.invoke(PININ_INSTANCE);
            Method accelerateMethod = config.getClass()
                .getMethod("accelerate", boolean.class);
            config = accelerateMethod.invoke(config, true);
            Method commitMethod = config.getClass()
                .getMethod("commit");
            commitMethod.invoke(config);

            // 获取 contains 方法
            CONTAINS_METHOD = pinInClass.getMethod("contains", String.class, String.class);
            available = true;
            RtsbuildingMod.LOGGER.info("[RtsPinyin] PinIn engine initialized successfully");
        } catch (ClassNotFoundException e) {
            RtsbuildingMod.LOGGER
                .info("[RtsPinyin] PinIn class not found (NEC not installed), falling back to substring match");
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.warn("[RtsPinyin] Failed to initialize PinIn: " + e.getMessage());
        }
    }

    private static Object createDictLoader() {
        try {
            // 使用默认 DictLoader
            Class<?> dictLoaderClass = Class.forName("me.towdium.pinin.DictLoader$Default");
            return dictLoaderClass.getDeclaredConstructor()
                .newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    // ======== 公共 API ========

    /**
     * 检查 source 是否匹配 query（支持拼音）。
     * 
     * @param source 源文本（如 "iron_ingot" 或 "铁锭"）
     * @param query  搜索词（如 "tie" 或 "tieding"）
     * @return true 如果匹配
     */
    public static boolean matches(String source, String query) {
        init();
        if (!available || CONTAINS_METHOD == null || PININ_INSTANCE == null) {
            return substringMatch(source, query);
        }
        try {
            return (Boolean) CONTAINS_METHOD.invoke(PININ_INSTANCE, source.toLowerCase(), query.toLowerCase());
        } catch (Exception e) {
            return substringMatch(source, query);
        }
    }

    /**
     * 拼音搜索是否可用。
     */
    public static boolean isAvailable() {
        init();
        return available;
    }

    // ======== 降级逻辑 ========

    private static boolean substringMatch(String source, String query) {
        if (source == null || query == null) return false;
        return source.toLowerCase()
            .contains(query.toLowerCase());
    }
}
