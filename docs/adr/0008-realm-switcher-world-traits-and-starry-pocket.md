# ADR-0008: Realm Switcher World Traits, Single-Page Star Gate UI & 25x25 Starry Pocket Space

- **Status**: Accepted (v1.9.0, 2026-11-13)
- **Context**:
  - Previously, the Star Gate locked a gate block to one password-protected base per owner (`SpaceRecord` per gate/UUID), displayed generic visual blurbs, and lacked distinct gameplay mechanics per environment.
  - Players requested: (1) distinct world traits per realm (custom spawn pools, spawn multipliers, ore distributions, fixed clocks, weather, and hazards) surfaced cleanly as World Traits in the GUI; (2) free switching among all 8 shared exploration realms from any Star Gate without lock-in or passwords; (3) a single-page direct-entry cosmic bar leading to a per-player UUID-isolated `25×25` personal pocket whose starry floor alone renders flowing starlight, keeping an open night sky and transparent perimeter barriers.

## Decision

1. **Declarative World Traits (`PrivateSpaceWorldTraits`) + Runtime Enforcement (`PrivateSpaceWorldTraitHandler` & `FiniteIslandChunkGenerator`)**:
   - Each `PrivateSpaceEnvironment` declares `TimeRule`, `WeatherRule`, `SpawnRule` (mode, multiplier, allowed entity pool), `OreProfile` (multiplier & ore weights), and environmental flags (beds/water/piglin zombification immunity, mob griefing protection, Night Vision aura).
   - `FiniteIslandChunkGenerator` consults `OreProfile` during chunk decoration, allowing deterministic ore-rate tests (`Superflat = 0`, `Amethyst Starfall = 2.5×`).
   - `PrivateSpaceWorldTraitHandler` intercepts `FinalizeSpawn` and `MobSpawnEvent.SpawnPlacementCheck` ONLY for `MobSpawnType.NATURAL` / `CHUNK_GENERATION`, explicitly preserving spawners, spawn eggs, breeding, and scripted spawns while enforcing per-realm multipliers and entity filters.

2. **Dual-Mode Star Gate & Return-Anchor Separation (`PersonalSpaceSavedData`, `PrivateSpaceManager`)**:
   - Separated saved data into shared global exploration `Realms` (`universal_tool:realm/<env>`), per-player UUID-bound `PrivatePockets` (`universal_tool:private_pocket/<uuid>`), and independent `ReturnPositions` vs `PrivateReturnPositions`.
   - Switching freely between shared exploration realms preserves the player's pre-realm `ReturnPosition`, so stepping through a `PersonalSpaceReturnBlock` always returns to the player's pre-realm origin rather than an intermediate realm.
   - Removed passwords, `PasswordHasher`, and `PrivateSpaceRules`; streamlined `PersonalSpaceScreen` into a single-page terminal with a direct full-width cosmic entry bar.

3. **Readable Flowing Starlight Floor & Open-Sky Barrier Pocket (`StarryVoidBlock`, `StarryVoidBlockEntityRenderer`, `StarryPocketChunkGenerator`)**:
   - Rejected vanilla `RenderType.endPortal()` (which collapsed face shading and corner edges into unreadable depthless black) in favor of a beveled `16×16` baked block model (`RenderShape.MODEL` for full block AO and edge definition) paired with `StarryVoidBlockEntityRenderer` drawing a `64×64` seamless translucent nebula/starlight overlay (`RenderType.entityTranslucent`) within `42` blocks.
   - `StarryPocketChunkGenerator` places `StarryVoidBlock` exclusively across the `25×25` floor (`y=63`, `x,z ∈ [-12..12]`), uses transparent `Blocks.BARRIER` only on outer boundary walls/cap (`x,z=±13`, `y=89`), keeps `hasSkyLight=true` and `hasCeiling=false` for open night sky visibility, omits any `BlockItem`/creative tab entry, blocks survival breakage and explosions, and handles `Shift + Right-Click` directly to return to `PrivateReturnPositions`.

## Verification
- `.\gradlew.bat runIslandGameTest --no-daemon`: 62 / 62 GameTests passed (`PrivateSpaceGameTests`, covering all 8 island generators, world traits, free realm switching, pre-realm Overworld return preservation, UUID-isolated `25×25` starry floor + barrier envelope, itemless exclusion, and protection hooks).
- `.\gradlew.bat runIslandVisual --no-daemon`: Captured and inspected single-page Star Gate UI, trait tooltips, and live `private_pocket_world.png` rendering.
