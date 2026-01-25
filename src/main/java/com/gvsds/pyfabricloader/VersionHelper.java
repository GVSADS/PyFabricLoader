package com.gvsds.pyfabricloader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft版本检测和兼容性工具类
 */
public class VersionHelper {
    private static final String MINECRAFT_VERSION_CLASS = "net.minecraft.MinecraftVersion";
    private static final String GAME_VERSION_CLASS = "net.minecraft.SharedConstants";
    private static String detectedVersion = null;
    
    /**
     * 检测当前运行的Minecraft版本
     * @return Minecraft版本字符串，如"1.19.2"
     */
    public static String getMinecraftVersion() {
        if (detectedVersion != null) {
            return detectedVersion;
        }
        
        try {
            // 尝试通过MinecraftVersion类获取版本（1.17+）
            try {
                Class<?> minecraftVersionClass = Class.forName(MINECRAFT_VERSION_CLASS);
                Object instance = minecraftVersionClass.getMethod("getInstance").invoke(null);
                detectedVersion = (String) minecraftVersionClass.getMethod("getName").invoke(instance);
                return detectedVersion;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // 尝试通过SharedConstants类获取版本
                try {
                    Class<?> sharedConstantsClass = Class.forName(GAME_VERSION_CLASS);
                    Object gameVersion = sharedConstantsClass.getField("getGameVersion").get(null);
                    detectedVersion = (String) gameVersion.getClass().getMethod("getName").invoke(gameVersion);
                    return detectedVersion;
                } catch (Exception ex) {
                    // 尝试直接获取版本字符串
                    try {
                        Class<?> sharedConstantsClass = Class.forName(GAME_VERSION_CLASS);
                        detectedVersion = (String) sharedConstantsClass.getField("VERSION_STRING").get(null);
                        return detectedVersion;
                    } catch (Exception exc) {
                        PyFabricLoader.LOGGER.warn("Failed to detect Minecraft version");
                        detectedVersion = "unknown";
                    }
                }
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error detecting Minecraft version: {}", e.getMessage(), e);
        }
        
        return detectedVersion;
    }
    
    /**
     * 比较两个Minecraft版本
     * @param version1 第一个版本
     * @param version2 第二个版本
     * @return 如果version1大于version2返回正数，如果小于返回负数，如果等于返回0
     */
    public static int compareVersions(String version1, String version2) {
        if (version1 == null || version2 == null) {
            return 0;
        }
        
        // 解析版本号（如1.19.2 -> [1, 19, 2]）
        int[] parts1 = parseVersion(version1);
        int[] parts2 = parseVersion(version2);
        
        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            int part1 = i < parts1.length ? parts1[i] : 0;
            int part2 = i < parts2.length ? parts2[i] : 0;
            
            if (part1 != part2) {
                return part1 - part2;
            }
        }
        
        return 0;
    }
    
    /**
     * 解析版本字符串为整数数组
     */
    private static int[] parseVersion(String version) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(version);
        
        int count = 0;
        Matcher countMatcher = pattern.matcher(version);
        while (countMatcher.find()) {
            count++;
        }
        
        int[] parts = new int[count];
        int index = 0;
        while (matcher.find()) {
            parts[index++] = Integer.parseInt(matcher.group());
        }
        
        return parts;
    }
    
    /**
     * 检查当前版本是否大于或等于指定版本
     */
    public static boolean isVersionAtLeast(String version) {
        return compareVersions(getMinecraftVersion(), version) >= 0;
    }
    
    /**
     * 获取当前版本对应的实现类后缀
     * @return 版本后缀，如"1_19_2"
     */
    public static String getVersionSuffix() {
        String version = getMinecraftVersion();
        return version.replace('.', '_').replaceFirst("^([0-9]+_[0-9]+)(_[0-9]+)?.*$", "$1");
    }
}