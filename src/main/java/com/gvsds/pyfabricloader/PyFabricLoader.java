package com.gvsds.pyfabricloader;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PyFabricLoader implements ModInitializer {
	public static final String MOD_ID = "pyfabricloader";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Initializing PyFabricLoader...");
		
		// 初始化ConfigManager
		ConfigManager.getInstance().initialize();
		
		// 初始化Python管理器
		PythonManager.getInstance().initialize();
		
		// 注册命令
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			CommandHandler.registerServerCommands(dispatcher, registryAccess);
		});
		
		LOGGER.info("PyFabricLoader initialized successfully!");
	}
}