# ADR 0005: Vanilla+ Textures and UI Overhaul

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

Following ADR 0002 (Arcane-Tech 1× Texel Density), the mod's visuals were unified around dark slate/plum/navy UI panels, glowing cyan/amethyst accents, 32×32 framed skill icons, and heavy black item outlines. While internally consistent, this aesthetic stood out sharply against Vanilla Minecraft's `#C6C6C6` beveled containers, Jappa (1.14+) tinted-outline pixel art, and standard Vanilla End/Magic block materials.

## Decision

1. **Classic `#C6C6C6` Vanilla Beveled UIs with Material Accents:** Convert all container and modal UIs (`PolymerizerScreen`, `PhoenixPodScreen`, `KleinScreen`, `UniversalLotteryMachineScreen`, `PersonalSpaceScreen` Star Gate terminal, `SkillScreen`, `SkillWheelScreen`, and JEI `PolymerizerRecipeCategory`) to standard `#C6C6C6` beveled stone panels, `#8B8B8B` 18×18 recessed slots, `#404040` labels, and classic MC widgets/buttons/scrollbars, retaining subtle Vanilla material cues (copper/amethyst conduits, furnace flames, oak/gold casino trim, End-eye Star Gate emblem). Convert `CodexScreen` (`codex_parchment.png`) to classic `book.png` leather-bound warm pixel parchment.
2. **16×16 Jappa Item & Skill Pixel Art:** Redraw all 17 items and all 20 skill icons (`16×16` transparent-background status-effect/advancement style) with material-tinted dark outlines (zero `#000000` outlines, zero airbrushed blur) and Vanilla wood/ore/glass/book palettes. Align `lightning_spear` to `32×32` 1× texel density with updated model UVs.
3. **End & Magic Vanilla Block Materials:** Retexture all 5 block sets (preserving 3D model geometries and UV maps) using Purpur, End Stone Bricks, Obsidian/Crying Obsidian, Enchanting Table crimson/gold trim, Bookshelf wood, and Vanilla glass.

## Why / Trade-offs

- **Pros:** Seamless visual integration with Vanilla Minecraft 1.21.1 and Vanilla+ modpacks while keeping all existing 3D block silhouettes, container menus, and slot coordinates intact.
- **Trade-offs:** Supersedes the dark Arcane-Tech palette from ADR 0002; future UI additions must follow `#C6C6C6` / `#8B8B8B` / `#404040` conventions and Jappa 1× pixel discipline.
