# PyFabricLoader

A Python mod loader for Minecraft Fabric using Jython, allowing to load and run Python mods.

## Features

- Load and run Python mods in Minecraft Fabric
- Easy-to-use Python API for mod development
- Command system for Python mod management
- Configuration management for Python mods
- Support for multiple Minecraft versions (1.18.1 - 1.21.10)
- Cross-platform compatibility

## Requirements

- Minecraft 1.18.1 - 1.21.10
- Fabric Loader 0.14.0+
- Java 17+
- Fabric API

## Installation

1. Install Fabric Loader and Fabric API for your Minecraft version
2. Download the latest PyFabricLoader JAR file
3. Place the JAR file in your Minecraft mods folder
4. Launch Minecraft with Fabric

## Usage

### Adding Python Mods

1. Create a `PyFabric` folder in your Minecraft directory
2. Place your Python mod files in this folder
3. Each mod should have an `__init__.py` file as the entry point

### Python Mod Structure

```
PyFabric/
└── your_mod/
    ├── __init__.py
    └── other_files.py
```

### Example Python Mod

```python
# __init__.py

def on_load():
    print("Your mod has been loaded!")

def on_unload():
    print("Your mod has been unloaded!")
```

### Commands

- `/pyfabric reload` - Reload all Python mods
- `/pyfabric list` - List all loaded Python mods
- `/pyfabric help` - Show help for PyFabric commands

## Development

### Setting Up the Development Environment

1. Clone the repository
2. Open the project in your preferred IDE (IntelliJ IDEA recommended)
3. Run `./gradlew setupDecompWorkspace` to set up the workspace
4. Run `./gradlew genSources` to generate sources
5. Start coding!

### Building the Project

```bash
./gradlew build
```

The built JAR file will be located in `build/libs/`.

## API Documentation

### Python API

#### Event Handlers

- `on_load()` - Called when the mod is loaded
- `on_unload()` - Called when the mod is unloaded

#### Minecraft API

PyFabricLoader provides a Python API for interacting with Minecraft. Check the `PyCommandAPI.java` file for available methods.

## Configuration

### Mod Configuration

Each Python mod can have its own configuration file in the `config/pyfabric/` directory.

### Global Configuration

The global configuration file is located at `config/pyfabric/config.json`.

## License

MIT License

## Credits

- Developed by 银河万通软件开发工作室 工程部 TermiNexus
- Website: [https://www.gvsds.com](https://www.gvsds.com)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For support, please visit our website or create an issue on GitHub.

## Changelog

### Version 1.0.0
- Initial release
- Basic Python mod loading functionality
- Command system
- Configuration management
