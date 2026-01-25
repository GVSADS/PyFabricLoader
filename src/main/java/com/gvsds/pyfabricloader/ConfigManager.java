package com.gvsds.pyfabricloader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration Manager for PyFabricLoader
 * Handles loading configuration from loader.json and language files
 */
public class ConfigManager {
    private static final ConfigManager INSTANCE = new ConfigManager();
    private static final String CONFIG_FILE = "loader.json";
    private static final String LANG_DIR = "lang/";
    
    private JsonObject config = new JsonObject();
    private Map<String, JsonObject> translations = new HashMap<>();
    private String currentLang = "zh-CN";
    private final Gson gson = new Gson();
    
    private ConfigManager() {
        // Private constructor for singleton
    }
    
    public static ConfigManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize the configuration manager
     * Loads the config and language files
     */
    public void initialize() {
        try {
            // First try to load config from JAR resources
            loadConfigFromJar();
            
            // If config exists in external directory (pyfabric/configs/), load it and merge with JAR config
            loadConfigFromExternal();
            
            // Initialize language system
            initializeLanguage();
            
            PyFabricLoader.LOGGER.info("ConfigManager initialized successfully");
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Failed to initialize ConfigManager: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Load configuration from JAR resources
     */
    private void loadConfigFromJar() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream != null) {
                try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    config = gson.fromJson(reader, JsonObject.class);
                    PyFabricLoader.LOGGER.info("Loaded config from JAR resources");
                }
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.warn("Failed to load config from JAR resources: {}", e.getMessage());
        }
    }
    
    /**
     * Load configuration from external directory (pyfabric/configs/)
     */
    private void loadConfigFromExternal() {
        try {
            // Create config directory if it doesn't exist
            Path configDir = FabricLoader.getInstance().getGameDir().resolve("pyfabric/configs");
            Files.createDirectories(configDir);
            
            // Load external config file
            Path configPath = configDir.resolve(CONFIG_FILE);
            if (Files.exists(configPath)) {
                try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                    JsonObject externalConfig = gson.fromJson(reader, JsonObject.class);
                    // Merge external config with JAR config (external takes precedence)
                    mergeConfigs(config, externalConfig);
                    PyFabricLoader.LOGGER.info("Loaded and merged external config from: {}", configPath);
                }
            } else {
                // If external config doesn't exist, save current config to external directory
                saveConfigToExternal();
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.warn("Failed to load external config: {}", e.getMessage());
        }
    }
    
    /**
     * Save current config to external directory
     */
    private void saveConfigToExternal() {
        try {
            Path configDir = FabricLoader.getInstance().getGameDir().resolve("pyfabric/configs");
            Files.createDirectories(configDir);
            
            Path configPath = configDir.resolve(CONFIG_FILE);
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
                PyFabricLoader.LOGGER.info("Saved default config to: {}", configPath);
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.warn("Failed to save default config: {}", e.getMessage());
        }
    }
    
    /**
     * Merge two JSON configurations, with the second taking precedence
     */
    private void mergeConfigs(JsonObject base, JsonObject override) {
        for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            
            if (base.has(key) && base.get(key).isJsonObject() && value.isJsonObject()) {
                // Recursively merge nested objects
                mergeConfigs(base.getAsJsonObject(key), value.getAsJsonObject());
            } else {
                // Override or add the value
                base.add(key, value);
            }
        }
    }
    
    /**
     * Initialize language system
     */
    private void initializeLanguage() {
        // Get language from config
        if (config.has("Lang")) {
            currentLang = config.get("Lang").getAsString();
        }
        
        // Load supported languages
        String[] supportedLangs = {"zh-CN", "zh-TW", "en"};
        
        for (String lang : supportedLangs) {
            try {
                // Try to load from external directory first
                loadLanguageFromExternal(lang);
                
                // If not found externally, load from JAR
                if (!translations.containsKey(lang)) {
                    loadLanguageFromJar(lang);
                }
            } catch (Exception e) {
                PyFabricLoader.LOGGER.warn("Failed to load language file for {}: {}", lang, e.getMessage());
            }
        }
        
        PyFabricLoader.LOGGER.info("Language system initialized, current language: {}", currentLang);
    }
    
    /**
     * Load language file from JAR resources
     */
    private void loadLanguageFromJar(String lang) {
        String langFile = LANG_DIR + lang + ".json";
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(langFile)) {
            if (inputStream != null) {
                try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    JsonObject langJson = gson.fromJson(reader, JsonObject.class);
                    translations.put(lang, langJson);
                    PyFabricLoader.LOGGER.info("Loaded language file from JAR: {}", lang);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load language file from JAR: " + lang, e);
        }
    }
    
    /**
     * Load language file from external directory
     */
    private void loadLanguageFromExternal(String lang) {
        try {
            Path langDir = FabricLoader.getInstance().getGameDir().resolve("pyfabric/configs/lang");
            Files.createDirectories(langDir);
            
            Path langPath = langDir.resolve(lang + ".json");
            if (Files.exists(langPath)) {
                try (Reader reader = Files.newBufferedReader(langPath, StandardCharsets.UTF_8)) {
                    JsonObject langJson = gson.fromJson(reader, JsonObject.class);
                    translations.put(lang, langJson);
                    PyFabricLoader.LOGGER.info("Loaded language file from external directory: {}", lang);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load external language file: " + lang, e);
        }
    }
    
    /**
     * Get a translated string
     * @param key The translation key
     * @return The translated string or the key if not found
     */
    public String getTranslation(String key) {
        return getTranslation(key, currentLang);
    }
    
    /**
     * Get a translated string with specific language
     * @param key The translation key
     * @param lang The language code
     * @return The translated string or the key if not found
     */
    public String getTranslation(String key, String lang) {
        if (!translations.containsKey(lang)) {
            // Fallback to zh-CN if requested language not available
            if (!lang.equals("zh-CN")) {
                return getTranslation(key, "zh-CN");
            }
            return key;
        }
        
        JsonObject langJson = translations.get(lang);
        String[] parts = key.split("\\.");
        JsonObject current = langJson;
        
        for (int i = 0; i < parts.length - 1; i++) {
            if (current.has(parts[i]) && current.get(parts[i]).isJsonObject()) {
                current = current.getAsJsonObject(parts[i]);
            } else {
                return key;
            }
        }
        
        String lastPart = parts[parts.length - 1];
        if (current.has(lastPart) && current.get(lastPart).isJsonPrimitive() && current.get(lastPart).getAsJsonPrimitive().isString()) {
            return current.get(lastPart).getAsString();
        }
        
        return key;
    }
    
    /**
     * Get a translated string with specific language and format parameters
     * This method ensures proper handling when passing a String parameter that should be used for formatting
     * @param key The translation key
     * @param lang The language code
     * @param formatArgs The format arguments
     * @return The formatted translated string
     */
    public String getTranslation(String key, String lang, Object... formatArgs) {
        String template = getTranslation(key, lang);
        try {
            return String.format(template, formatArgs);
        } catch (Exception e) {
            return template + " [format error]";
        }
    }
    
    /**
     * Get a formatted translated string
     * @param key The translation key
     * @param args The format arguments
     * @return The formatted translated string
     */
    public String getTranslation(String key, Object... args) {
        String template = getTranslation(key);
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template + " [format error]";
        }
    }
    
    /**
     * Switch language with server command source feedback
     * @param langCode The language code to switch to
     * @param source The server command source for feedback
     * @return True if language was switched successfully
     */
    public boolean switchLanguage(String langCode, ServerCommandSource source) {
        if (translations.containsKey(langCode)) {
            currentLang = langCode;
            
            // Update language setting in config file
            config.addProperty("Lang", langCode);
            saveConfigToExternal();
            
            if (source != null) {
                sendFeedback(source, getTranslation("messages.language_changed", langCode), false);
            }
            
            return true;
        } else {
            if (source != null) {
                sendFeedback(source, getTranslation("messages.language_invalid", langCode), false);
            }
            return false;
        }
    }
    
    private void sendFeedback(ServerCommandSource source, String message, boolean broadcast) {
        try {
            Text textMessage = Text.literal(message);
            
            // Use reflection to call sendFeedback to support different Minecraft versions
            try {
                // Try 1.19+ version with Supplier<Text>
                Method sendFeedbackMethod = ServerCommandSource.class.getMethod("sendFeedback", java.util.function.Supplier.class, boolean.class);
                sendFeedbackMethod.invoke(source, (java.util.function.Supplier<Text>)() -> textMessage, broadcast);
            } catch (NoSuchMethodException e1) {
                // Fallback to 1.18.x version with direct Text parameter
                try {
                    Method sendFeedbackMethod = ServerCommandSource.class.getMethod("sendFeedback", Text.class, boolean.class);
                    sendFeedbackMethod.invoke(source, textMessage, broadcast);
                } catch (NoSuchMethodException e2) {
                    PyFabricLoader.LOGGER.error("Could not find sendFeedback method: {}", e2.getMessage());
                }
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error sending feedback: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Set the current language
     * @param lang The language code
     */
    public void setLanguage(String lang) {
        if (translations.containsKey(lang)) {
            currentLang = lang;
            config.addProperty("Lang", lang);
            saveConfigToExternal();
            PyFabricLoader.LOGGER.info("Language changed to: {}", lang);
        } else {
            PyFabricLoader.LOGGER.warn("Requested language not available: {}", lang);
        }
    }
    
    /**
     * Get current language code
     * @return The current language code
     */
    public String getCurrentLanguage() {
        return currentLang;
    }
    
    /**
     * Get config value
     * @param key The config key
     * @param defaultValue The default value if key not found
     * @return The config value or default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        JsonElement element = getConfigElement(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean()
                : defaultValue;
    }
    
    /**
     * Get config value
     * @param key The config key
     * @param defaultValue The default value if key not found
     * @return The config value or default
     */
    public String getString(String key, String defaultValue) {
        JsonElement element = getConfigElement(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : defaultValue;
    }
    
    /**
     * Get nested config element
     * @param key The config key with dot notation (e.g. "Mode.Mods")
     * @return The config element or null if not found
     */
    private JsonElement getConfigElement(String key) {
        String[] parts = key.split("\\.");
        JsonObject current = config;
        
        for (int i = 0; i < parts.length - 1; i++) {
            if (current.has(parts[i]) && current.get(parts[i]).isJsonObject()) {
                current = current.getAsJsonObject(parts[i]);
            } else {
                return null;
            }
        }
        
        String lastPart = parts[parts.length - 1];
        return current.has(lastPart) ? current.get(lastPart) : null;
    }
    
    /**
     * Get custom load order from config
     * @return List of module names in custom load order
     */
    public List<String> getCustomLoadOrder() {
        List<String> loadOrder = new ArrayList<>();
        if (config.has("Preload") && config.get("Preload").isJsonObject()) {
            JsonObject preloadObj = config.getAsJsonObject("Preload");
            if (preloadObj.has("CustomLoadOrder") && preloadObj.get("CustomLoadOrder").isJsonArray()) {
                JsonArray array = preloadObj.getAsJsonArray("CustomLoadOrder");
                for (int i = 0; i < array.size(); i++) {
                    if (array.get(i).isJsonArray()) {
                        JsonArray entry = array.get(i).getAsJsonArray();
                        if (entry.size() > 0 && entry.get(0).isJsonPrimitive()) {
                            loadOrder.add(entry.get(0).getAsString());
                        }
                    }
                }
            }
        }
        return loadOrder;
    }
    
    /**
     * Get all loaded language packs
     * @return Map of language codes to language JSON objects
     */
    public Map<String, JsonObject> getLanguagePacks() {
        return translations;
    }
    
    /**
     * Get the entire config JSON object
     * @return The config JSON object
     */
    public JsonObject getConfig() {
        return config;
    }
    
    /**
     * Check if a specific mode is enabled in the config
     * @param mode The mode name to check
     * @return True if the mode is enabled
     */
    public boolean isModeEnabled(String mode) {
        return getBoolean("Mode." + mode, true);
    }
    
    /**
     * Get the module matching pattern from config
     * @return The module matching regex pattern
     */
    public String getModuleMatchingPattern() {
        return getString("Preload.ModuleMatching", ".*\\.zip$");
    }
    
    /**
     * Get the priority module matching pattern from config
     * @return The priority module matching regex pattern
     */
    public String getPriorityModuleMatchingPattern() {
         return getString("Preload.PriorityModuleMatching", "^!.*\\.zip$");
     }
  }