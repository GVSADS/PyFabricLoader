# -*- coding: utf-8 -*-
# PyFabricLoader Test Module

from com.gvsds.pyfabricloader import PyCommandAPI
from java.lang import String

ModInfos = {
    "id": "test_title_module",
    "name": "Test Title Module",
    "version": "1.0.0",
    "description": "A module to test title functionality"
}

def ShowTitle(player, message, fadeIn=10, stay=70, fadeOut=20):
    try:
        command_api = PyCommandAPI.getInstance()
        command_api.showTitle(player, message, fadeIn, stay, fadeOut)
        return True
    except Exception as e:
        print("Error showing title: " + str(e))
        return False

def ShowTitleByName(player_name, message, fadeIn=10, stay=70, fadeOut=20):
    try:
        command_api = PyCommandAPI.getInstance()
        return command_api.showTitleByPlayerName(player_name, message, fadeIn, stay, fadeOut)
    except Exception as e:
        print("Error showing title by name: " + str(e))
        return False

def ExecuteTestPy(source, message):
    try:
        command_api = PyCommandAPI.getInstance()
        player = command_api.getPlayer(source)
        if player:
            success = ShowTitle(player, message)
            if success:
                command_api.sendFeedback(source, "Title displayed: %s" % message, False)
                return 1
            else:
                command_api.sendError(source, "Failed to display title")
                return 0
        else:
            command_api.sendError(source, "This command can only be executed by players")
            return 0
    except Exception as e:
        command_api.sendError(source, "Execution error: " + str(e))
        return 0

def ExecuteSimpleTest(source):
    try:
        command_api = PyCommandAPI.getInstance()
        command_api.sendFeedback(source, "Simple test command executed successfully!", False)
        player = command_api.getPlayer(source)
        if player:
            command_api.sendFeedback(source, "Current player: %s" % player.getName().getString(), False)
        else:
            command_api.sendError(source, "Player not found")
    except Exception as e:
        print("Error in simple test: " + str(e))

def ExecuteTitleCommand(source, arguments):
    try:
        command_api = PyCommandAPI.getInstance()
        args = arguments.split()
        if len(args) >= 3 and args[1] == "title":
            player_name = args[0]
            message = " ".join(args[2:])
            success = ShowTitleByName(player_name, message)
            if success:
                command_api.sendFeedback(source, "Title displayed to %s: %s" % (player_name, message), False)
                return 1
            else:
                command_api.sendError(source, "Failed to display title to %s" % player_name)
                return 0
        else:
            command_api.sendError(source, "Invalid command format. Use: /title-to-player <player> title <message>")
            return 0
    except Exception as e:
        command_api.sendError(source, "Error executing command: %s" % str(e))
        return 0

def RegisterCommands():
    command_api = PyCommandAPI.getInstance()
    command_api.registerCommandWithStringArgument("test-py", "message", True, ExecuteTestPy)
    print("Successfully registered /test-py command")
    command_api.registerCommandWithStringArgument("title-to-player", "Shows a title to a specified player", True, ExecuteTitleCommand)
    print("Successfully registered /title-to-player command with format: /title-to-player <player> title <message>")
    command_api.registerSimpleCommand("simple-test", ExecuteSimpleTest)
    print("Successfully registered /simple-test command")

print("TestTitleModule initializing...")
RegisterCommands()
print("TestTitleModule initialization complete!")