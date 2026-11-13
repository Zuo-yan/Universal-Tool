# ADR 0003: Pocket Realm Fantastical Island Generation

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

The Pocket Realm dimension previously offered five mundane overworld biomes (`plains`, `desert`, `snowy_plains`, `birch_forest`, `savanna`) alongside `superflat`, `cherry_grove`, and `sky_islands`. Players entering a bespoke pocket dimension expect fantastical, high-fantasy landscapes rather than ordinary overworld patches, while existing worlds and `PrivateSpaceGameTests` rely on deterministic surface/column contracts and persisted environment IDs.

## Decision

1. **Replace Mundane Biomes with Five Fantastical Landscapes (8 Total):**
   - Retain & elevate `SUPERFLAT` (Starlit Superflat), `SKY_ISLANDS` (Celestial Sky Archipelago), and `CHERRY_GROVE` (Mystic Cherry Sanctuary).
   - Replace the 5 mundane environments with: `AMETHYST_STARFALL`, `SCULK_SANCTUM`, `MUSHROOM_WONDERLAND`, `OBSIDIAN_FORGE`, and `AURORA_GLACIER`.
2. **Backward-Compatible ID Migration:** Map legacy persisted IDs in `PrivateSpaceEnvironment.byId(String)` (`"plains"` → `AMETHYST_STARFALL`, `"desert"` → `OBSIDIAN_FORGE`, `"snowy_plains"` → `AURORA_GLACIER`, `"birch_forest"` → `MUSHROOM_WONDERLAND`, `"savanna"` → `SCULK_SANCTUM`) so existing saves load without errors.
3. **Sculptural Undersides & Bounded Landmarks:** Enhance `FiniteIslandChunkGenerator` with tapered stalactite undersides, multi-layered geological strata, and procedural fantastical landmarks strictly bounded within `containsTerrain(x, z)`, chunk boundaries, and protected zones around `(0, 0)` spawn and `(40, -40)` test anchor.

## Why / Trade-offs

- **Pros:** Gives every Pocket Realm archetype a distinct silhouette, palette, and landmark identity while guaranteeing 100% save compatibility and GameTest contract stability.
- **Trade-offs:** Mapping old IDs to new fantastical themes changes the appearance of newly generated chunks in legacy realms if ungenerated chunks remain, which is acceptable for a 512×512 finite island.
