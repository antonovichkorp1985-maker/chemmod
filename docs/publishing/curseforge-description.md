# ChemMod — CurseForge page copy

## Summary

Structure-based chemistry, exact substance quantities, and interactive laboratory equipment for Minecraft.

## Description

# ChemMod

**Build a laboratory. Work with molecular structures. Keep track of what is actually inside your vessels.**

ChemMod is an experimental chemistry mod for **Minecraft 1.21.1 on NeoForge**. It represents substances using molecular structures and tracks the quantities in your containers. Its focus is physical laboratory interactions and data-defined chemical processes rather than a catalogue of unrelated crafting recipes.

## Available now

### Substances and mixtures

- Vessels containing individual substances or explicit mixtures.
- Molecular identity based on structure, with exact stored quantities.
- Distinct vessel labels for empty containers, individual substances, and mixtures.

Label colours identify the **type of contents**, not the actual colour, physical phase, or temperature of a chemical.

### A working prototype reactor

- Separate physical slots for the starting substance, additional reactants, products, and catalyst.
- Data-defined reaction conditions and timed processing.
- A readable interface with progress, status messages, and explanations of slot roles.
- World records of first syntheses from successful reactor processing.

The current reactor processes **pure-substance vessels**. Mixture processing in the reactor is not yet implemented. Its furnace-based heat input is a temporary prototype, not the final laboratory heating system.

### Placeable laboratory holders

- A **test tube rack with six positions** for ChemMod vessels.
- A **laboratory tray with four positions**.
- A rack and tray can share one block space.
- Right-click a position with a vessel to insert it; right-click with an empty main hand to retrieve it.
- Stored vessel contents persist through saving and loading, and are dropped when the holder is destroyed.
- Crafting recipes, original models, and English and Russian names and tooltips.

This first implementation uses fixed holder positions and requires a full-height sturdy supporting surface. Breaking the shared holder block removes both installed parts. Carrying a filled tray as one item is not yet supported.

### Material processing

ChemMod also includes a copper material-processing route and optional integrations with **Create** and **TerraFirmaCraft**.

## Requirements

- **Minecraft 1.21.1**
- **NeoForge 21.1.251 or newer compatible with Minecraft 1.21.1**
- **Java 21**
- Install the **same ChemMod version on the server and all clients**.

Create and TerraFirmaCraft are optional. Macaw’s Furniture and Chisels & Bits are not required by the current build. No separate ChemMod core download is needed.

## Development status: Alpha

ChemMod is actively being developed. **Back up your worlds before updating.** Expect unfinished features, visual changes, and possible compatibility issues.

Automated tests cover core logic, server startup, and selected in-world interactions. They do not replace client, multiplayer, or modpack testing. Compatibility with arbitrary carved surfaces or specific furniture blocks has not yet been established.

### Planned — not included yet

- Dedicated laboratory heaters, spirit lamps, and gas burners with functional connections.
- Vacuum pumps and connected vacuum apparatus.
- Graduated and heat-rated laboratory vessels.
- More flexible equipment placement and support for custom work surfaces.

These are development goals, not features of the current release.

## Links and feedback

- [Source repository](https://github.com/antonovichkorp1985-maker/chemmod)
- [Report a bug](https://github.com/antonovichkorp1985-maker/chemmod/issues)
- [Releases](https://github.com/antonovichkorp1985-maker/chemmod/releases)
- [Test.7 setup and testing guide — Russian](https://github.com/antonovichkorp1985-maker/chemmod/blob/v0.9.0-test.7/docs/testing/v0.9.0-test.7.md)

When reporting an issue, include the ChemMod, Minecraft, and NeoForge versions, reproduction steps, and relevant logs. Screenshots are helpful for rendering or interface problems.

## License

**All Rights Reserved.** Source visibility does not make ChemMod open source. Please see the repository’s [LICENSE](https://github.com/antonovichkorp1985-maker/chemmod/blob/v0.9.0-test.7/LICENSE) for the actual permissions. Modpack inclusion and redistribution require permission under the current license.
