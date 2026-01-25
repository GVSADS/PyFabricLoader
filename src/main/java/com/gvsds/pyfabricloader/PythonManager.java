package com.gvsds.pyfabricloader;

import org.python.core.*;
import org.python.util.PythonInterpreter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import com.gvsds.pyfabricloader.ConfigManager;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.io.InputStream;
import net.fabricmc.loader.api.Version;

public class PythonManager {
    private static final PythonManager INSTANCE = new PythonManager();
    private final Map<String, PyModInfo> loadedMods = new HashMap<>();
    private final Map<String, PythonInterpreter> interpreters = new HashMap<>();
    private final File modsDir;
    private final File configsDir;
    private final File libsDir;
    private final File filesDir;
    private PythonInterpreter globalInterpreter;

    private PythonManager() {
        // 初始化工作目录
        File pyfabricDir = new File("pyfabric");
        modsDir = new File(pyfabricDir, "mods");
        configsDir = new File(pyfabricDir, "configs");
        libsDir = new File(pyfabricDir, "libs");
        filesDir = new File(pyfabricDir, "files");
    }
    
    /**
     * 检查版本是否满足条件
     * 支持的格式：
     * - >=1.20.1
     * - <1.20.1
     * - =1.20.1
     * - !=["1.20.1", "1.34.7"]
     */
    private boolean checkVersion(String condition, String currentVersion) {
        try {
            if (condition == null || condition.isEmpty()) {
                return true;
            }
            
            // 处理不等于多个版本的情况
            if (condition.startsWith("!=[")) {
                JsonArray versionsArray = JsonParser.parseString(condition).getAsJsonArray();
                for (JsonElement element : versionsArray) {
                    if (currentVersion.equals(element.getAsString())) {
                        return false;
                    }
                }
                return true;
            }
            
            // 处理其他比较操作
            if (condition.startsWith(">=")) {
                return compareVersions(currentVersion, condition.substring(2)) >= 0;
            } else if (condition.startsWith("<=")) {
                return compareVersions(currentVersion, condition.substring(2)) <= 0;
            } else if (condition.startsWith(">")) {
                return compareVersions(currentVersion, condition.substring(1)) > 0;
            } else if (condition.startsWith("<")) {
                return compareVersions(currentVersion, condition.substring(1)) < 0;
            } else if (condition.startsWith("=")) {
                return currentVersion.equals(condition.substring(1));
            }
            
            // 如果没有前缀，则默认是等于
            return currentVersion.equals(condition);
        } catch (Exception e) {
            PyFabricLoader.LOGGER.warn("Failed to check version condition {} for {}", condition, currentVersion, e);
            return false;
        }
    }
    
    /**
     * 比较两个版本字符串
     * @return 0 if equal, 1 if version1 > version2, -1 if version1 < version2
     */
    private int compareVersions(String version1, String version2) {
        try {
            String[] parts1 = version1.split("\\.");
            String[] parts2 = version2.split("\\.");
            
            int maxLength = Math.max(parts1.length, parts2.length);
            
            for (int i = 0; i < maxLength; i++) {
                int num1 = i < parts1.length ? Integer.parseInt(parts1[i].replaceAll("\\D", "")) : 0;
                int num2 = i < parts2.length ? Integer.parseInt(parts2[i].replaceAll("\\D", "")) : 0;
                
                if (num1 != num2) {
                    return Integer.compare(num1, num2);
                }
            }
            
            return 0;
        } catch (Exception e) {
            PyFabricLoader.LOGGER.warn("Failed to compare versions {} and {}", version1, version2, e);
            return 0;
        }
    }
    
    /**
     * 获取当前Minecraft版本
     */
    private String getCurrentMinecraftVersion() {
        try {
            // 通过Fabric API获取Minecraft版本
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Exception e) {
            PyFabricLoader.LOGGER.warn("Failed to get Minecraft version", e);
            return "unknown";
        }
    }
    
    /**
     * 获取当前PyFabricLoader版本
     */
    private String getCurrentPyFabricVersion() {
        try {
            // 尝试通过Fabric API获取PyFabricLoader版本
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("pyfabricloader")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("1.0.0"); // 默认版本
        } catch (Exception e) {
            PyFabricLoader.LOGGER.warn("Failed to get PyFabricLoader version", e);
            return "1.0.0";
        }
    }

    public static PythonManager getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        // 创建必要的文件夹
        createDirectories();
        
        // 从jar中提取jython到libs目录
        extractJythonFromJar();
        
        try {
            // 确保libs目录在Python路径中
            String pythonPath = System.getProperty("python.path", 
                    System.getProperty("java.class.path").replace(File.pathSeparatorChar, ':'));
            pythonPath += ":" + libsDir.getAbsolutePath();
            System.setProperty("python.path", pythonPath);
            
            // 设置Jython使用当前线程的上下文类加载器
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            
            // 预加载关键的Jython类以确保它们可用
            PyFabricLoader.LOGGER.info("Preloading critical Jython classes...");
            Class.forName("org.python.core.PyObject");
            Class.forName("org.python.core.PyString");
            Class.forName("org.python.util.PythonInterpreter");
            PyFabricLoader.LOGGER.info("Jython core classes preloaded successfully");
            
            // 初始化全局Python解释器
            Properties props = new Properties();
            props.setProperty("python.console.encoding", "UTF-8");
            props.setProperty("python.security.respectJavaAccessibility", "false");
            // 设置使用系统类加载器
            props.setProperty("python.cachedir", new File(libsDir, ".jython-cache").getAbsolutePath());
            // 禁用Jython的finalize钩子以避免访问被限制的java.lang.finalize()方法
            props.setProperty("python.import.site", "false");
            props.setProperty("python.jit", "false");
            props.setProperty("python.security.respectJavaAccessibility", "true");
            
            PythonInterpreter.initialize(System.getProperties(), props, new String[0]);
            
            PyFabricLoader.LOGGER.info("Creating PythonInterpreter instance...");
            globalInterpreter = new PythonInterpreter();
            setupGlobalInterpreter();
            
            // 加载所有mod
            loadAllMods();
        } catch (ClassNotFoundException e) {
            PyFabricLoader.LOGGER.error("Critical Jython class not found: " + e.getMessage(), e);
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error during Jython initialization: " + e.getMessage(), e);
        }
    }

    private void createDirectories() {
        modsDir.mkdirs();
        configsDir.mkdirs();
        libsDir.mkdirs();
        filesDir.mkdirs();
        PyFabricLoader.LOGGER.info("Created directories: mods={}, configs={}, libs={}, files={}", 
                modsDir.exists(), configsDir.exists(), libsDir.exists(), filesDir.exists());
    }

    private void setupGlobalInterpreter() {
        // 设置全局变量
        globalInterpreter.set("__name__", "__main__");
        globalInterpreter.set("ModInfos", new PyDictionary());
        
        // 注册PyCommandAPI实例，让Python脚本可以直接访问
        globalInterpreter.set("PyCommandAPI", PyCommandAPI.getInstance());
        
        // 注册ConfigManager实例，让Python脚本可以访问配置信息
        globalInterpreter.set("ConfigManager", ConfigManager.getInstance());
        
        // 添加libs目录到Python路径，确保jython可以导入pyfabric/libs下的py文件
        try {
            String pythonPath = System.getProperty("python.path", 
                    System.getProperty("java.class.path").replace(File.pathSeparatorChar, ':'));
            pythonPath += ":" + libsDir.getAbsolutePath();
            System.setProperty("python.path", pythonPath);
            
            // 直接在解释器中添加路径，确保导入生效
            globalInterpreter.exec("import sys");
            globalInterpreter.exec("sys.path.append('" + libsDir.getAbsolutePath() + "')");
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Failed to set Python path", e);
        }
    }

    public void loadAllMods() {
        ConfigManager configManager = ConfigManager.getInstance();
        
        // 加载ZIP格式的mods
        if (configManager.isModeEnabled("Mods")) {
            PyFabricLoader.LOGGER.info("Loading mods from ZIP files...");
            String matchingPattern = configManager.getModuleMatchingPattern();
            String priorityPattern = configManager.getPriorityModuleMatchingPattern();
            List<String> customOrder = configManager.getCustomLoadOrder();
            
            // 先处理自定义顺序的mods
            for (String modName : customOrder) {
                File modFile = new File(modsDir, modName);
                if (!modFile.exists()) {
                    modFile = new File(modsDir, modName + ".zip");
                }
                if (modFile.exists()) {
                    loadMod(modFile);
                }
            }
            
            // 加载剩余的ZIP mods
            File[] zipFiles = modsDir.listFiles((dir, name) -> {
                if (!name.toLowerCase().endsWith(".zip")) return false;
                // 检查是否已经在自定义顺序中加载过
                if (customOrder.stream().anyMatch(order -> name.equals(order) || name.equals(order + ".zip"))) {
                    return false;
                }
                // 应用匹配规则
                return name.matches(matchingPattern) || name.matches(priorityPattern);
            });
            
            if (zipFiles != null) {
                for (File zipFile : zipFiles) {
                    loadMod(zipFile);
                }
            }
        }
        
        // 加载单文件模式的mods
        if (configManager.isModeEnabled("ModSingleFileMode")) {
            PyFabricLoader.LOGGER.info("Loading mods from single Python files...");
            File[] pyFiles = modsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".py"));
            if (pyFiles != null) {
                for (File pyFile : pyFiles) {
                    loadSingleFileMod(pyFile);
                }
            }
        }
    }
    
    private boolean loadSingleFileMod(File pyFile) {
        String modId = pyFile.getName().replace(".py", "");
        PyFabricLoader.LOGGER.info("Loading single file mod: {}", modId);
        
        try {
            // 创建新的解释器实例
            PythonInterpreter interpreter = new PythonInterpreter();
            interpreter.set("__name__", modId);
            interpreter.set("ModInfos", new PyDictionary());
            interpreter.set("PyCommandAPI", PyCommandAPI.getInstance());
            interpreter.set("ConfigManager", ConfigManager.getInstance());
            
            // 添加mods目录到Python路径
            interpreter.exec("import sys");
            interpreter.exec("sys.path.append('" + modsDir.getAbsolutePath() + "')");
            
            // 执行Python文件
            interpreter.execfile(pyFile.getAbsolutePath());
            
            // 获取ModInfos
            PyDictionary modInfos = (PyDictionary) interpreter.get("ModInfos");
            PyModInfo modInfo = new PyModInfo(modId, modInfos);
            
            // 存储mod信息和解释器
            loadedMods.put(modId, modInfo);
            interpreters.put(modId, interpreter);
            
            PyFabricLoader.LOGGER.info("Successfully loaded single file mod: {} - {}", modId, modInfo.getName());
            return true;
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Failed to load single file mod: {}", modId, e);
            return false;
        }
    }

    public boolean loadMod(File zipFile) {
        String modId = zipFile.getName().replace(".zip", "");
        PyFabricLoader.LOGGER.info("Loading mod: {}", modId);

        try {
            // 创建临时目录解压zip
            Path tempDir = Files.createTempDirectory("pyfabric_mod_");
            extractZip(zipFile, tempDir.toFile());

            // 查找info.json文件
            File infoJsonFile = new File(tempDir.toFile(), "info.json");
            if (!infoJsonFile.exists()) {
                PyFabricLoader.LOGGER.warn("No info.json found in {}, skipping mod", zipFile.getName());
                deleteDirectory(tempDir.toFile());
                return false;
            }

            // 读取和解析info.json
            Gson gson = new Gson();
            JsonObject infoJson = gson.fromJson(java.nio.file.Files.newBufferedReader(infoJsonFile.toPath()), JsonObject.class);

            // 验证版本要求
            String pyfabricVersion = infoJson.has("pyfabric-version") ? infoJson.get("pyfabric-version").getAsString() : null;
            String minecraftVersion = infoJson.has("minecraft-version") ? infoJson.get("minecraft-version").getAsString() : null;
            String currentPyFabricVersion = getCurrentPyFabricVersion();
            String currentMinecraftVersion = getCurrentMinecraftVersion();

            // 验证PyFabricLoader版本
            if (!checkVersion(pyfabricVersion, currentPyFabricVersion)) {
                PyFabricLoader.LOGGER.warn("Mod {} requires PyFabricLoader version {} but current is {}, skipping", 
                        modId, pyfabricVersion, currentPyFabricVersion);
                deleteDirectory(tempDir.toFile());
                return false;
            }

            // 验证Minecraft版本
            if (!checkVersion(minecraftVersion, currentMinecraftVersion)) {
                PyFabricLoader.LOGGER.warn("Mod {} requires Minecraft version {} but current is {}, skipping", 
                        modId, minecraftVersion, currentMinecraftVersion);
                deleteDirectory(tempDir.toFile());
                return false;
            }

            // 查找__init__.py
            File initPy = new File(tempDir.toFile(), "__init__.py");
            if (!initPy.exists()) {
                PyFabricLoader.LOGGER.warn("No __init__.py found in {}", zipFile.getName());
                deleteDirectory(tempDir.toFile());
                return false;
            }

            // 创建新的解释器实例
            PythonInterpreter interpreter = new PythonInterpreter();
            interpreter.set("__name__", modId);
            interpreter.set("ModInfos", new PyDictionary());
            
            // 添加临时目录到Python路径
            interpreter.exec("import sys");
            interpreter.exec("sys.path.append('" + tempDir.toAbsolutePath() + "')");

            // 执行__init__.py
            interpreter.execfile(initPy.getAbsolutePath());

            // 从info.json创建PyModInfo
            PyModInfo modInfo = new PyModInfo(modId, infoJson);
            
            // 存储mod信息和解释器
            loadedMods.put(modId, modInfo);
            interpreters.put(modId, interpreter);
            
            PyFabricLoader.LOGGER.info("Successfully loaded mod: {} - {}", modId, modInfo.getName());
            return true;
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Failed to load mod: {}", modId, e);
            return false;
        }
    }

    public boolean reloadMod(String modName) {
        // 先移除旧的mod
        unloadMod(modName);
        
        // 查找并加载新的mod
        File zipFile = new File(modsDir, modName + ".zip");
        if (!zipFile.exists()) {
            // 尝试直接使用文件名
            zipFile = new File(modsDir, modName);
            if (!zipFile.exists()) {
                return false;
            }
        }
        
        return loadMod(zipFile);
    }

    public void reloadAllMods() {
        // 卸载所有mod
        unloadAllMods();
        // 重新加载所有mod
        loadAllMods();
    }

    public void unloadMod(String modId) {
        if (loadedMods.containsKey(modId)) {
            loadedMods.remove(modId);
            PythonInterpreter interpreter = interpreters.remove(modId);
            if (interpreter != null) {
                interpreter.close();
            }
            PyFabricLoader.LOGGER.info("Unloaded mod: {}", modId);
        }
    }

    public void unloadAllMods() {
        for (String modId : new ArrayList<>(loadedMods.keySet())) {
            unloadMod(modId);
        }
    }

    public List<PyModInfo> getLoadedMods() {
        return new ArrayList<>(loadedMods.values());
    }

    public String executePython(String code) {
        try {
            StringWriter writer = new StringWriter();
            globalInterpreter.setOut(writer);
            globalInterpreter.setErr(writer);
            globalInterpreter.exec(code);
            return writer.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public String executePythonFile(String fileName) {
        // 修改为执行pyfabric/files下的文件
        File pythonFile = new File(filesDir, fileName);
        if (!pythonFile.exists()) {
            return "File not found: " + fileName + " in " + filesDir.getAbsolutePath();
        }

        try {
            StringWriter writer = new StringWriter();
            globalInterpreter.setOut(writer);
            globalInterpreter.setErr(writer);
            globalInterpreter.execfile(pythonFile.getAbsolutePath());
            return writer.toString();
        } catch (Exception e) {
            return "Error executing " + fileName + ": " + e.getMessage();
        }
    }

    private void extractZip(File zipFile, File targetDir) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryFile = new File(targetDir, entry.getName());
                
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    // 确保父目录存在
                    entryFile.getParentFile().mkdirs();
                    // 复制文件内容
                    try (java.io.InputStream in = zip.getInputStream(entry);
                         java.io.OutputStream out = new FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
    
    /**
     * 从jar中提取jython相关文件到libs目录
     */
    private void extractJythonFromJar() {
        try {
            // 检查jython是否已经存在
            File jythonLibDir = new File(libsDir, "Lib");
            if (jythonLibDir.exists() && jythonLibDir.isDirectory() && jythonLibDir.listFiles() != null && jythonLibDir.listFiles().length > 0) {
                PyFabricLoader.LOGGER.info("Jython already exists in libs directory, skipping extraction");
                return;
            }
            
            PyFabricLoader.LOGGER.info("Extracting Jython resources to libs directory...");
            
            // 确保libs目录存在
            libsDir.mkdirs();
            
            // 首先尝试直接从当前jar中提取Jython的Lib目录
            try {
                // 使用类加载器直接访问当前jar中的Jython资源
                // 创建一个测试文件来确保目录可写
                File testPy = new File(libsDir, "__init__.py");
                try (FileOutputStream fos = new FileOutputStream(testPy)) {
                    fos.write("# Jython initialization file\n".getBytes());
                }
                
                // 直接从项目的libs目录复制Jython jar到pyfabric/libs目录
                File jythonJar = new File("libs/jython-slim-3.0.1-SNAPSHOT-all.jar");
                if (jythonJar.exists()) {
                    File targetJythonJar = new File(libsDir, "jython-slim-3.0.1-SNAPSHOT-all.jar");
                    java.nio.file.Files.copy(jythonJar.toPath(), targetJythonJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    PyFabricLoader.LOGGER.info("Copied Jython jar to libs directory");
                    
                    // 从复制的jar中提取Lib目录
                    extractJythonFromSpecificJar(targetJythonJar.getAbsolutePath());
                } else {
                    PyFabricLoader.LOGGER.warn("Jython jar not found in project libs directory");
                }
            } catch (Exception e) {
                PyFabricLoader.LOGGER.error("Error copying Jython resources", e);
            }
            
            PyFabricLoader.LOGGER.info("Jython resource extraction completed");
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Failed to extract Jython resources", e);
        }
    }
    
    /**
     * 查找Jython jar文件的路径
     */
    private String findJythonJarPath() {
        try {
            // 获取org.python包的类，然后获取其所在的jar路径
            Class<?> pyClass = Class.forName("org.python.core.PyObject");
            String path = pyClass.getProtectionDomain().getCodeSource().getLocation().getPath();
            // 处理URL编码
            return java.net.URLDecoder.decode(path, "UTF-8");
        } catch (Exception e) {
            PyFabricLoader.LOGGER.warn("Could not determine Jython jar path", e);
            return null;
        }
    }
    
    /**
     * 从指定的Jython jar中提取文件到libs目录
     */
    private void extractJythonFromSpecificJar(String jarPath) throws IOException {
        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            PyFabricLoader.LOGGER.warn("Jython jar file not found: " + jarPath);
            return;
        }
        
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                // 只提取Lib目录下的Python文件
                if (entry.getName().startsWith("Lib/") && !entry.isDirectory()) {
                    File destFile = new File(libsDir, entry.getName().substring(4)); // 去掉"Lib/"前缀
                    destFile.getParentFile().mkdirs();
                    
                    try (java.io.InputStream in = jar.getInputStream(entry);
                         java.io.OutputStream out = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    public static class PyModInfo {
        private final String id;
        private final String name;
        private final String version;
        private final String description;

        // 从info.json的JsonObject创建
        public PyModInfo(String id, JsonObject infoJson) {
            this.id = id;
            this.name = infoJson.has("name") ? infoJson.get("name").getAsString() : id;
            this.version = infoJson.has("version") ? infoJson.get("version").getAsString() : "1.0.0";
            this.description = infoJson.has("description") ? infoJson.get("description").getAsString() : "";
        }

        // 兼容旧版从PyDictionary创建的方式
        public PyModInfo(String id, PyDictionary modInfos) {
            this.id = id;
            this.name = modInfos.get("name") != null ? modInfos.get("name").toString() : id;
            this.version = modInfos.get("version") != null ? modInfos.get("version").toString() : "1.0.0";
            this.description = modInfos.get("description") != null ? modInfos.get("description").toString() : "";
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getDescription() { return description; }
    }
}