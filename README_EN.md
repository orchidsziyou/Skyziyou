# Skyziyou

English | [简体中文](./README.md)

> This mod was generated with AI assistance; please do not use it if that bothers you.

A multi-feature Minecraft Fabric client mod (mainly for Hypixel SkyBlock): hold a customizable key to open an 8-direction radial wheel in the center of the screen, flick the mouse toward a direction to select a sector, then left click (or release the key) to quickly run a preset command or send a chat message. It also ships standalone features such as automatic party invites via private messages.

## Screenshots

**In-game wheel:**

![In-game wheel](UI.png)

**Settings screen:**

![Setting screen](setting.png)

## Features

- **8-direction wheel**: Up, Up Right, Right, Down Right, Down, Down Left, Left, Up Left — each slot is independently configurable
- **Two entry types**:
  - Entries starting with `/` are executed as **commands**
  - Everything else is sent as a **chat message**
- **Two trigger modes**: left click to run, or enable "Execute On Release" to run when the trigger key is released
- **Smart interaction**:
  - The **camera is locked** while the wheel is open, so picking a sector never turns your view
  - **Vanilla mouse clicks are swallowed** while the wheel is open — no accidental attacks or block placements
  - Right click cancels anytime; the wheel auto-closes after a configurable timeout
- **Built-in category settings menu**: type `/skyziyou` or `/sz` in game; every feature has its own sub-page, changes apply instantly and are saved on close
- **Custom key shortcuts**: bind any key (including mouse buttons, e.g. numpad 2) directly to a command or chat message and run it with a single press — configured in the "Custom Shortcuts" sub-page; each shortcut can be toggled on/off individually, and key conflicts are flagged in red
- **Auto Party Invite**: when a Hypixel private message contains exactly `!invite`, automatically runs `/p <player>` (60s per-player cooldown, commands queued 1s apart)

## Requirements

| Component | Version |
|---|---|
| Minecraft | 26.1.x |
| Fabric Loader | >= 0.19.0 |
| Fabric API | latest |
| Fabric Language Kotlin | >= 1.13.0 |
| Java | >= 25 |

Client-side only; servers do not need to install anything.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/)
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) into your `mods` folder
3. Download the mod jar from the [Releases](../../releases) page into your `mods` folder
4. Start the game

## Usage

1. Type `/skyziyou` (or the short form `/sz`) in game to open the settings category menu
2. Open the **Command Wheel** sub-page, click the **Wheel Keybind** button, then press any key (or mouse button) to bind the trigger
3. Fill in the 8 input boxes with the command or message for each direction (leave a slot empty to disable it)
4. Hold the trigger key to open the wheel → flick the mouse toward a direction → left click (or release the key) to run it
5. Right click, or wait for the timeout, to close without running anything
6. Extra direct shortcuts: open the "Custom Shortcuts" sub-page, add a row and bind a key with a command (e.g. numpad 2 → `/is`)
7. All settings are stored in `.minecraft/config/skyziyou.json`; edit it manually if needed, or delete the file to reset

## Building from Source

```powershell
git clone https://github.com/orchidsziyou/Skyziyou.git
cd Skyziyou

# Windows (PowerShell)
.\gradlew.bat build

# Linux / macOS
./gradlew build
```

The built jar is located in `build/libs/`; use the one without the `-sources` suffix.

## Project Structure

```
src/main/
├── java/com/skyziyou/mixin/
│   ├── MixinMouseHandler.java                  # Mixin: camera lock, click swallowing, mouse delta capture
│   └── ClientCommonPacketListenerAccessor.java # Server brand accessor (Hypixel detection)
├── kotlin/com/skyziyou/
│   ├── SkyziyouMod.kt          # Mod entrypoint: config, renderer, event registration
│   ├── ConfigScreen.kt         # Settings category menu + BaseConfigScreen base class
│   ├── WheelState.kt           # Core wheel state machine: open/select/close/execute
│   ├── WheelRenderer.kt        # HUD rendering: scanline disc + text labels
│   ├── SkyziyouConfig.kt       # JSON config I/O (atomic writes, legacy config migration)
│   ├── WheelConfigScreen.kt    # Command Wheel settings sub-page
│   ├── Shortcuts.kt / ShortcutsScreen.kt      # Custom key shortcuts
│   └── features/party/
│       ├── AutoInviteFeature.kt       # !invite PM auto party invite
│       └── AutoInviteConfigScreen.kt  # Settings sub-page for that feature
└── resources/
    ├── fabric.mod.json          # Mod metadata
    └── skyziyou.mixins.json     # Mixin config
```

## License

[MIT](LICENSE)
