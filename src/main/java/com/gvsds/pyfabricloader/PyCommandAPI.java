package com.gvsds.pyfabricloader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.lang.reflect.Method;
import org.python.util.PythonInterpreter;

/**
 * Python Command API for PyFabricLoader
 * This class provides a simplified API for Python scripts to register Minecraft commands
 * without directly accessing Minecraft server command classes.
 */
public class PyCommandAPI {
    private static PyCommandAPI instance;
    
    private PyCommandAPI() {
        // Private constructor for singleton
    }
    
    public static synchronized PyCommandAPI getInstance() {
        if (instance == null) {
            instance = new PyCommandAPI();
        }
        return instance;
    }
    
    /**
     * Register a new command with a string argument
     * @param commandName The name of the command
     * @param argumentName The name of the argument
     * @param isGreedy Whether the argument should consume all remaining input
     * @param callback The Python callback function to execute when the command is run
     */
    public void registerCommandWithStringArgument(String commandName, String argumentName, boolean isGreedy, Object callback) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Create the command node
            LiteralCommandNode<ServerCommandSource> commandNode = CommandManager
                .literal(commandName)
                .then(CommandManager.argument(argumentName, isGreedy ? StringArgumentType.greedyString() : StringArgumentType.string())
                    .executes(context -> executePythonCallback(context, callback, argumentName)))
                .build();
            
            // Register the command
            dispatcher.getRoot().addChild(commandNode);
            PyFabricLoader.LOGGER.info("Registered command: /{}", commandName);
        });
    }
    
    /**
     * Register a simple command without arguments
     * @param commandName The name of the command
     * @param callback The Python callback function to execute when the command is run
     */
    public void registerSimpleCommand(String commandName, Object callback) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Create the command node
            LiteralCommandNode<ServerCommandSource> commandNode = CommandManager
                .literal(commandName)
                .executes(context -> executePythonCallback(context, callback, null))
                .build();
            
            // Register the command
            dispatcher.getRoot().addChild(commandNode);
            PyFabricLoader.LOGGER.info("Registered command: /{}", commandName);
        });
    }
    
    /**
     * Display a title to a player
     * @param player The player to display the title to
     * @param message The title message
     * @param fadeIn Fade in time in ticks
     * @param stay Stay time in ticks
     * @param fadeOut Fade out time in ticks
     */
    public void showTitle(Object player, String message, int fadeIn, int stay, int fadeOut) {
        try {
            if (player != null && player instanceof net.minecraft.server.network.ServerPlayerEntity) {
                net.minecraft.server.network.ServerPlayerEntity serverPlayer = (net.minecraft.server.network.ServerPlayerEntity) player;
                Text titleText = Text.literal(message);
                
                // In newer Minecraft versions, use PlayerEntity's sendMessage with title action
                // Create title component with fade in, stay, fade out parameters
                serverPlayer.sendMessage(titleText, true);
                
                // Alternative approach: Use ServerPlayNetworkHandler to send title packet
                // This is a simplified version that sends a chat message instead
                // For full title functionality, packet handling would be needed
                PyFabricLoader.LOGGER.info("Sent title message to player: {}", message);
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error showing title: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Display a title to a player by name
     * @param playerName The name of the player to display the title to
     * @param message The title message
     * @param fadeIn Fade in time in ticks
     * @param stay Stay time in ticks
     * @param fadeOut Fade out time in ticks
     * @return True if successful, false otherwise
     */
    public boolean showTitleByPlayerName(String playerName, String message, int fadeIn, int stay, int fadeOut) {
        try {
            // Get the server instance through reflection to avoid direct dependency on specific methods
            net.minecraft.server.MinecraftServer server = null;
            
            // Try multiple reflection approaches to get the server instance
            try {
                // Try getting server through FabricLoader
                Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
                java.lang.reflect.Method getInstanceMethod = loaderClass.getMethod("getInstance");
                Object fabricLoader = getInstanceMethod.invoke(null);
                
                // Try to get server instance through Loader's game instance
                try {
                    java.lang.reflect.Method getGameInstanceMethod = loaderClass.getMethod("getGameInstance");
                    Object gameInstance = getGameInstanceMethod.invoke(fabricLoader);
                    if (gameInstance instanceof net.minecraft.server.MinecraftServer) {
                        server = (net.minecraft.server.MinecraftServer) gameInstance;
                    }
                } catch (NoSuchMethodException e) {
                    PyFabricLoader.LOGGER.warn("getGameInstance method not found: {}", e.getMessage());
                }
            } catch (Exception e) {
                PyFabricLoader.LOGGER.warn("Could not get server through FabricLoader: {}", e.getMessage());
            }
            
            // Alternative approach: Try to get server from MinecraftServer class
            if (server == null) {
                try {
                    Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
                    // Try various possible methods
                    String[] possibleMethods = {"getServer", "getCurrentServer", "getInstance"};
                    
                    for (String methodName : possibleMethods) {
                        try {
                            java.lang.reflect.Method method = serverClass.getMethod(methodName);
                            Object serverObj = method.invoke(null);
                            if (serverObj instanceof net.minecraft.server.MinecraftServer) {
                                server = (net.minecraft.server.MinecraftServer) serverObj;
                                break;
                            }
                        } catch (NoSuchMethodException ignored) {
                            // Method not found, try next one
                        }
                    }
                } catch (Exception e) {
                    PyFabricLoader.LOGGER.warn("Could not get server instance through reflection: {}", e.getMessage());
                }
            }
            
            // If we have a server and player name, try to find the player
            if (server != null && playerName != null) {
                try {
                    // Get the player manager
                    net.minecraft.server.PlayerManager playerManager = server.getPlayerManager();
                    if (playerManager != null) {
                        // Get player by name
                        net.minecraft.server.network.ServerPlayerEntity player = playerManager.getPlayer(playerName);
                        if (player != null) {
                            // Use existing showTitle method to display the title
                            showTitle(player, message, fadeIn, stay, fadeOut);
                            return true;
                        } else {
                            PyFabricLoader.LOGGER.warn("Player not found: {}", playerName);
                        }
                    }
                } catch (Exception e) {
                    PyFabricLoader.LOGGER.error("Error accessing player manager: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error in showTitleByPlayerName: {}", e.getMessage(), e);
        }
        return false;
    }
    
    /**
     * Send a feedback message to the command source
     * @param source The command source
     * @param message The message to send
     * @param broadcast Whether to broadcast to all operators
     */
    public void sendFeedback(Object source, String message, boolean broadcast) {
        try {
            if (source != null && source instanceof ServerCommandSource) {
                ServerCommandSource commandSource = (ServerCommandSource) source;
                Text textMessage = Text.literal(message);
                
                try {
                // Try to use the method directly with Text parameter first (1.18.x)
                commandSource.getClass().getMethod("sendFeedback", Text.class, boolean.class)
                    .invoke(commandSource, textMessage, broadcast);
            } catch (NoSuchMethodException e) {
                // If that fails, try with Supplier parameter (1.19+)
                try {
                    // 先创建Supplier对象再传递给反射调用
                    java.util.function.Supplier<Text> textSupplier = () -> textMessage;
                    commandSource.getClass().getMethod("sendFeedback", java.util.function.Supplier.class, boolean.class)
                        .invoke(commandSource, textSupplier, broadcast);
                } catch (Exception ex) {
                    PyFabricLoader.LOGGER.error("Failed to send feedback: {}", ex.getMessage());
                }
            } catch (Exception e) {
                PyFabricLoader.LOGGER.error("Failed to send feedback: {}", e.getMessage());
            }
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error sending feedback: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send an error message to the command source
     * @param source The command source
     * @param message The error message to send
     */
    public void sendError(Object source, String message) {
        try {
            if (source != null && source instanceof ServerCommandSource) {
                ServerCommandSource commandSource = (ServerCommandSource) source;
                Text textMessage = Text.literal(message);
                
                //#if MC>=11900
                // 1.19+版本使用函数式接口参数
                commandSource.sendError(textMessage);
                //#else
                // 1.18.x版本直接使用Text参数
                commandSource.sendError(textMessage);
                //#endif
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error sending error message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get the player from a command source
     * @param source The command source
     * @return The player object or null if not a player
     */
    public Object getPlayer(Object source) {
        try {
            if (source != null && source instanceof ServerCommandSource) {
                ServerCommandSource commandSource = (ServerCommandSource) source;
                return commandSource.getPlayer();
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error getting player: {}", e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Get a player by name
     * @param playerName The name of the player to find
     * @return The player object or null if not found
     */
    public Object getPlayerByName(String playerName) {
        try {
            // Get the server instance through reflection to avoid direct dependency
            net.minecraft.server.MinecraftServer server = null;
            
            // Try multiple reflection approaches to get the server instance
            try {
                // Try getting server through FabricLoader
                Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
                java.lang.reflect.Method getInstanceMethod = loaderClass.getMethod("getInstance");
                Object fabricLoader = getInstanceMethod.invoke(null);
                
                // Try to get server instance through Loader's game instance
                try {
                    java.lang.reflect.Method getGameInstanceMethod = loaderClass.getMethod("getGameInstance");
                    Object gameInstance = getGameInstanceMethod.invoke(fabricLoader);
                    if (gameInstance instanceof net.minecraft.server.MinecraftServer) {
                        server = (net.minecraft.server.MinecraftServer) gameInstance;
                    }
                } catch (NoSuchMethodException e) {
                    // Method not found, continue
                }
            } catch (Exception e) {
                // Continue with other methods
            }
            
            // Alternative approach: Try to get server from MinecraftServer class
            if (server == null) {
                try {
                    Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
                    // Try various possible methods
                    String[] possibleMethods = {"getServer", "getCurrentServer", "getInstance"};
                    
                    for (String methodName : possibleMethods) {
                        try {
                            java.lang.reflect.Method method = serverClass.getMethod(methodName);
                            Object serverObj = method.invoke(null);
                            if (serverObj instanceof net.minecraft.server.MinecraftServer) {
                                server = (net.minecraft.server.MinecraftServer) serverObj;
                                break;
                            }
                        } catch (NoSuchMethodException ignored) {
                            // Method not found, try next one
                        }
                    }
                } catch (Exception e) {
                    // Continue
                }
            }
            
            if (server != null && playerName != null) {
                try {
                    // Get the player manager
                    net.minecraft.server.PlayerManager playerManager = server.getPlayerManager();
                    if (playerManager != null) {
                        // Get player by name
                        return playerManager.getPlayer(playerName);
                    }
                } catch (Exception e) {
                    PyFabricLoader.LOGGER.error("Error accessing player manager: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error getting player by name: {}", e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Execute a Python callback function when a command is run
     */
    private int executePythonCallback(CommandContext<ServerCommandSource> context, Object callback, String argumentName) {
        try {
            // Get the command source
            ServerCommandSource source = context.getSource();
            
            // Get the argument value if specified
            Object argumentValue = null;
            if (argumentName != null) {
                argumentValue = StringArgumentType.getString(context, argumentName);
            }
            
            // Call the Python callback function using Jython's PyObject interface
            // This is the most reliable way to call Python functions from Java
            Object result = null;
            
            // Import Jython PyObject class dynamically to avoid direct dependency issues
            try {
                // Try direct approach first - for Jython 2.x compatibility
                if (argumentName != null) {
                    // With argument - use PythonInterpreter's call helper method
                    // Create a new PythonInterpreter instance for this call
                    org.python.util.PythonInterpreter interpreter = new org.python.util.PythonInterpreter();
                    try {
                        // Use exec to call the function with arguments
                        interpreter.set("__callback", callback);
                        interpreter.set("__source", source);
                        interpreter.set("__arg", argumentValue);
                        interpreter.exec("__result = __callback(__source, __arg)");
                        result = interpreter.get("__result");
                    } finally {
                        interpreter.cleanup();
                    }
                } else {
                    // Without argument
                    org.python.util.PythonInterpreter interpreter = new org.python.util.PythonInterpreter();
                    try {
                        interpreter.set("__callback", callback);
                        interpreter.set("__source", source);
                        interpreter.exec("__result = __callback(__source)");
                        result = interpreter.get("__result");
                    } finally {
                        interpreter.cleanup();
                    }
                }
            } catch (Exception e) {
                // Fallback to reflection with different signatures
                Method[] methods = callback.getClass().getMethods();
                for (Method method : methods) {
                    if (method.getName().equals("__call__")) {
                        try {
                            // Try with different parameter combinations
                            Class<?>[] paramTypes = method.getParameterTypes();
                            if (paramTypes.length == 2 && argumentName != null) {
                                result = method.invoke(callback, source, argumentValue);
                                break;
                            } else if (paramTypes.length == 1) {
                                result = method.invoke(callback, source);
                                break;
                            } else if (paramTypes.length == 1 && paramTypes[0].isArray()) {
                                // Try with array parameter
                                if (argumentName != null) {
                                    result = method.invoke(callback, new Object[]{new Object[]{source, argumentValue}});
                                } else {
                                    result = method.invoke(callback, new Object[]{new Object[]{source}});
                                }
                                break;
                            }
                        } catch (IllegalArgumentException ex) {
                            // This signature doesn't match, continue
                            continue;
                        }
                    }
                }
            }
            
            // Convert result to integer (command result code)
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
            return 1; // Default success
        } catch (Exception e) {
            PyFabricLoader.LOGGER.error("Error executing Python command callback: {}", e.getMessage(), e);
            return 0; // Error
        }
    }
}
