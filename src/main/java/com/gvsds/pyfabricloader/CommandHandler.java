package com.gvsds.pyfabricloader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import java.util.List;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public class CommandHandler {
    private static final ConfigManager configManager = ConfigManager.getInstance();
    private static final String MOD_VERSION = "1.0.0";
    private static final String AUTHOR = "银河万通软件开发工作室 工程部 TermiNexus";
    private static final String BILI_URL = "https://space.bilibili.com/3546619129628868";
    private static final String WEBSITE_URL = "https://www.gvsds.com";

    public static void registerServerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(
            CommandManager.literal("pyfabricloader")
                .executes(context -> showHelp(context.getSource()))
                .then(CommandManager.literal("list")
                    .executes(context -> listMods(context.getSource())))
                .then(CommandManager.literal("reload")
                    .requires(source -> source.hasPermissionLevel(4))
                    .executes(context -> reloadAllMods(context.getSource()))
                    .then(CommandManager.argument("file", StringArgumentType.greedyString())
                        .executes(context -> reloadSpecificMod(context.getSource(), StringArgumentType.getString(context, "file")))))
                .then(CommandManager.literal("exec")
                    .then(CommandManager.argument("code", StringArgumentType.greedyString())
                        .executes(context -> executePython(context.getSource(), StringArgumentType.getString(context, "code")))))
                .then(CommandManager.literal("run")
                    .then(CommandManager.argument("file", StringArgumentType.string())
                        .executes(context -> executePythonFile(context.getSource(), StringArgumentType.getString(context, "file")))))
                .then(CommandManager.literal("help")
                    .executes(context -> showHelp(context.getSource())))
                .then(CommandManager.literal("about")
                    .executes(context -> showAbout(context.getSource())))
                .then(CommandManager.literal("lang")
                    .then(CommandManager.argument("language", StringArgumentType.string())
                    .executes(context -> setLanguage(context.getSource(), StringArgumentType.getString(context, "language")))))
        );
    }

    private static int showHelp(ServerCommandSource source) {
        StringBuilder help = new StringBuilder();
        help.append("§6").append(configManager.getTranslation("commands.help.header")).append("\n");
        help.append("§a/pyfabricloader §r- ").append(configManager.getTranslation("commands.help.main")).append("\n");
        help.append("§a/pyfabricloader list §r- ").append(configManager.getTranslation("commands.help.list")).append("\n");
        help.append("§a/pyfabricloader reload §r- ").append(configManager.getTranslation("commands.help.reload")).append("\n");
        help.append("§a/pyfabricloader reload [file.zip] §r- ").append(configManager.getTranslation("commands.help.reload_file")).append("\n");
        help.append("§a/pyfabricloader exec [代码] §r- ").append(configManager.getTranslation("commands.help.exec")).append("\n");
        help.append("§a/pyfabricloader run [文件名.py] §r- ").append(configManager.getTranslation("commands.help.run")).append("\n");
        help.append("§a/pyfabricloader help §r- ").append(configManager.getTranslation("commands.help.help")).append("\n");
        help.append("§a/pyfabricloader about §r- ").append(configManager.getTranslation("commands.help.about")).append("\n");
        help.append("§a/pyfabricloader lang [语言] §r- ").append(configManager.getTranslation("commands.help.lang")).append(" (zh-CN, zh-TW, en)");
        
        sendFeedback(source, help.toString(), false);
        return 1;
    }

    private static int listMods(ServerCommandSource source) {
        List<PythonManager.PyModInfo> mods = PythonManager.getInstance().getLoadedMods();
        
        if (mods.isEmpty()) {
            sendFeedback(source, "§6" + configManager.getTranslation("messages.no_mods"), false);
            return 1;
        }

        StringBuilder modList = new StringBuilder("§6" + configManager.getTranslation("messages.loaded_mods", configManager.getCurrentLanguage(), mods.size()) + "\n");
        for (PythonManager.PyModInfo mod : mods) {
            modList.append("§a- §r").append(mod.getName())
                   .append(" (").append(mod.getId()).append(")")
                   .append(" - v").append(mod.getVersion());
            if (!mod.getDescription().isEmpty()) {
                modList.append("\n  §7").append(mod.getDescription());
            }
            modList.append("\n");
        }
        
        sendFeedback(source, modList.toString(), false);
        return 1;
    }

    private static int reloadAllMods(ServerCommandSource source) {
        try {
            PythonManager.getInstance().reloadAllMods();
            sendFeedback(source, "§a" + configManager.getTranslation("messages.reload_success"), false);
        } catch (Exception e) {
            sendFeedback(source, "§c" + configManager.getTranslation("messages.reload_failed", configManager.getCurrentLanguage(), e.getMessage()), false);
            PyFabricLoader.LOGGER.error("Error reloading all mods", e);
        }
        return 1;
    }

    private static int reloadSpecificMod(ServerCommandSource source, String fileName) {
        try {
            boolean success = PythonManager.getInstance().reloadMod(fileName);
            if (success) {
                sendFeedback(source, "§a" + configManager.getTranslation("messages.mod_reloaded", configManager.getCurrentLanguage(), fileName), false);
            } else {
                sendFeedback(source, "§c" + configManager.getTranslation("messages.mod_not_found", configManager.getCurrentLanguage(), fileName), false);
            }
        } catch (Exception e) {
            sendFeedback(source, "§c" + configManager.getTranslation("messages.mod_reload_failed", configManager.getCurrentLanguage(), fileName, e.getMessage()), false);
            PyFabricLoader.LOGGER.error("Error reloading mod: {}", fileName, e);
        }
        return 1;
    }

    private static int executePython(ServerCommandSource source, String code) {
        try {
            String result = PythonManager.getInstance().executePython(code);
            if (result.isEmpty()) {
                sendFeedback(source, "§a" + configManager.getTranslation("messages.execution_success_no_output"), false);
            } else {
                sendFeedback(source, "§6" + configManager.getTranslation("messages.execution_result") + "\n" + result, false);
            }
        } catch (Exception e) {
            sendFeedback(source, "§c" + configManager.getTranslation("messages.execution_error", configManager.getCurrentLanguage(), e.getMessage()), false);
            PyFabricLoader.LOGGER.error("Error executing Python code", e);
        }
        return 1;
    }

    private static int executePythonFile(ServerCommandSource source, String fileName) {
        try {
            String result = PythonManager.getInstance().executePythonFile(fileName);
            sendFeedback(source, "§6" + configManager.getTranslation("messages.file_execution_result", configManager.getCurrentLanguage(), fileName) + "\n" + result, false);
        } catch (Exception e) {
            sendFeedback(source, "§c" + configManager.getTranslation("messages.file_execution_failed", configManager.getCurrentLanguage(), fileName, e.getMessage()), false);
            PyFabricLoader.LOGGER.error("Error executing Python file: {}", fileName, e);
        }
        return 1;
    }

    private static int showAbout(ServerCommandSource source) {
        StringBuilder about = new StringBuilder();
        about.append("§6").append(configManager.getTranslation("messages.about.header", configManager.getCurrentLanguage(), MOD_VERSION)).append("\n");
        about.append("§a").append(configManager.getTranslation("messages.about.author", configManager.getCurrentLanguage(), AUTHOR)).append("\n");
        about.append("§b").append(configManager.getTranslation("messages.about.bilibili")).append("§r").append(BILI_URL).append("\n");
        about.append("§b").append(configManager.getTranslation("messages.about.website")).append("§r").append(WEBSITE_URL).append("\n");
        about.append("§7").append(configManager.getTranslation("messages.about.thanks"));
        
        sendFeedback(source, about.toString(), false);
        return 1;
    }
    
    private static int setLanguage(ServerCommandSource source, String language) {
        try {
            if (language.equals("zh-CN") || language.equals("zh-TW") || language.equals("en")) {
                configManager.setLanguage(language);
                sendFeedback(source, "§a" + configManager.getTranslation("messages.language_changed", configManager.getCurrentLanguage(), language), false);
            } else {
                sendFeedback(source, "§c" + configManager.getTranslation("messages.language_invalid", configManager.getCurrentLanguage(), language), false);
            }
        } catch (Exception e) {
            sendFeedback(source, "§c" + configManager.getTranslation("messages.language_change_failed", configManager.getCurrentLanguage(), language), false);
            PyFabricLoader.LOGGER.error("Error changing language to: {}", language, e);
        }
        return 1;
    }
    
    private static void sendFeedback(ServerCommandSource source, String message, boolean broadcast) {
        try {
            Text textMessage = Text.literal(message);
            
            // 使用反射调用sendFeedback方法，适配不同版本
            try {
                // 尝试调用1.19+版本的方法 (ServerCommandSource.sendFeedback(Supplier<Text>, boolean))
                Method sendFeedbackMethod = ServerCommandSource.class.getMethod("sendFeedback", Supplier.class, boolean.class);
                sendFeedbackMethod.invoke(source, (Supplier<Text>) () -> textMessage, broadcast);
            } catch (NoSuchMethodException e1) {
                try {
                    // 尝试调用1.18.x及以下版本的方法 (ServerCommandSource.sendFeedback(Text, boolean))
                    Method sendFeedbackMethod = ServerCommandSource.class.getMethod("sendFeedback", Text.class, boolean.class);
                    sendFeedbackMethod.invoke(source, textMessage, broadcast);
                } catch (NoSuchMethodException e2) {
                    PyFabricLoader.LOGGER.error("No compatible sendFeedback method found", e2);
                }
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error sending feedback: {}", e.getMessage(), e);
        }
    }
}