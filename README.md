# SurvivalMethod

A Minecraft (Paper) plugin that introduces realistic survival mechanics: a stamina system and a thirst system. Both systems are highly configurable and can be enabled or disabled independently.
This plugin does not require any additional resource packs.

## Features

### Stamina System

- **Stamina Consumption**: Sprinting consumes stamina, which is displayed on the experience bar.
- **Exhaustion**: When stamina runs out, your movement speed is reduced, and you cannot jump.
- **Regeneration**: Stamina regenerates automatically when you are not sprinting. The regeneration rate increases the longer you rest.
- **Hunger Penalty**: Stamina consumption increases as your hunger level drops.

### Thirst System

- **Thirst Gauge**: Your thirst level is displayed using the vanilla oxygen bar. It decreases over time.
- **Dehydration**: When your thirst gauge is empty, you will start taking damage after a short delay.
- **Rehydration**: You can restore your thirst by drinking water bottles, stews, or soups.
- **Diving**: The thirst system is temporarily disabled while you are underwater, switching back to the default oxygen mechanics. Your thirst level will not decrease while diving.

### High Configurability

- **Toggle Systems**: Enable or disable the stamina and thirst systems independently in `config.yml`.
- **Full Control**: Almost every value, from consumption rates to damage amounts, can be customized.
- **Reset Options**: Configure whether player stats should be reset on join or respawn, perfect for various server game modes.

## Installation

1.  Download the latest `.jar` file from the Releases page.
2.  Place the downloaded `.jar` file into your server's `plugins` directory.
3.  Restart your server.

## Configuration

All settings can be adjusted in `config.yml`, which is generated in the `plugins/SurvivalMethod/` directory on the first run. The file is heavily commented to explain what each option does. Please refer to the `config.yml` file for detailed configuration.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
