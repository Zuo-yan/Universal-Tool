# Changelog

## 1.9.1 - 2026-11-13
- **Cloud-Free Dense Starry Sky for Personal Starry Space (`StarryPocketDimensionEffects`)**:
  - Replaced default Overworld sky/clouds in `universal_tool:private_pocket/<uuid>` with custom `universal_tool:starry_pocket` `DimensionSpecialEffects`.
  - Completely suppressed clouds (`cloudLevel = Float.NaN` and `renderClouds` override) and rendered a deep midnight-indigo sky dome, smooth radial-falloff galactic nebula band, and 2,800 multi-tiered twinkling stars in starlight white, ice-cyan, soft violet, and pale gold.

## 1.9.0 - 2026-11-13
- **Eight Realm World Traits (`PrivateSpaceWorldTraits` & `PrivateSpaceWorldTraitHandler`)**:
  - Added distinct, documented environmental rules across all 8 realms: fixed day/night clocks (`6000L` eternal noon, `18000L` eternal starry night, `13000L` dusk, ambient skylight modulation), tailored weather (`ALWAYS_CLEAR`, `SNOW_FLURRY`), custom natural mob rosters and spawn multipliers (e.g., Superflat zero natural spawns while preserving spawners/eggs/breeding; Mushroom Wonderland mooshroom-only; Obsidian Forge `2.0x` Nether-tier spawns with zombification immunity; Amethyst Starfall passive/neutral roster + Night Vision), geological ore tables (0 ores in Superflat; `2.5x` Overworld ores incl. diamond/emerald/redstone in Amethyst Starfall; XP/lapis/redstone focus in Cherry Grove; emerald/coal/iron in Sky Islands; quartz/gold/ancient debris in Obsidian Forge), and ambient protections against griefing/explosions.
  - Updated `PersonalSpaceScreen` to replace generic terrain blurbs with crisp `世界特性 / World Traits` summaries and rich hover tooltips covering time/weather, natural spawns, underground ores, and environmental buffs.
- **Free Realm Switching & Password-Free Single-Page Personal Starry Space (`25×25`)**:
  - Removed Star Gate realm lock-in and all password hashing/gate UI (`ACTION_ENTER_REALM`, `ACTION_ENTER_PRIVATE_SPACE`): clicking any `PersonalSpaceGateBlock` lets any player freely switch between all 8 shared global exploration realms (`universal_tool:realm/<env>`) while preserving their pre-realm Overworld/origin return coordinate across realm jumps.
  - Replaced the secondary submenu with a single-click cosmic bar directly on `PersonalSpaceScreen` (`✦ 进入个人空间 · 25×25` / `按玩家区分 · 不自然刷怪 · 潜行右键星空地板返回`), routing each player to their own UUID-keyed dimension (`universal_tool:private_pocket/<uuid>`).
  - Implemented `StarryPocketChunkGenerator`, `StarryVoidBlock`, and `StarryVoidBlockEntityRenderer`: places a `25×25` open-sky `StarryVoidBlock` beveled indigo-slate masonry floor (`y=63`, `x,z ∈ [-12..12]`) layered with a smooth translucent scrolling starlight/nebula veil (`RenderType.entityTranslucent`) for high spatial legibility, enclosed by transparent `Blocks.BARRIER` perimeter bounds and cap (`x,z=±13`, `y=89`) with midnight sky light (`hasSkyLight=true`, `hasCeiling=false`). Excluded `StarryVoidBlock` from `BlockItem` and creative tabs; Shift + Right-Clicking any `StarryVoidBlock` returns the player to their saved pre-entry location.

## 1.8.0 - 2026-11-13
- **Reference-Aligned Universal Tools & Klein Bottle (`64×64`)**:
  - Redrew all 6 `{wooden,stone,iron,golden,diamond,netherite}_universal_tool.png` sprites at `64×64` following the upright symmetrical cross-halberd reference silhouette (top diamond-spade spadehead, left pickaxe head, right axe blade, central core gem, brown leather-wrapped grip, and bottom dagger pommel) and updated `models/item/*_universal_tool.json` display transforms for upright GUI framing.
  - Redrew `klein_bottle.png` at `64×64` as a translucent crystal-glass looping Klein Bottle with dark collared junctions, cyan crystal rivets, and a crystal-inlaid base ring.
- **Star Gate Real-World Previews & Live Base Snapshots**:
  - Enlarged `PersonalSpaceScreen`'s brass-framed viewport (`64px` high) and rendered crisp real Minecraft in-game panoramic screenshots (`textures/gui/previews/env_*.png`) for all 8 `PrivateSpaceEnvironment` realms, auto-captured via `PrivateIslandVisualTest` (`runIslandVisual`).
  - Added `PersonalSpaceSnapshotHandler` + `PhotoManager` base-snapshot persistence so visiting or managing an existing private dimension displays the player's own captured base scene first and cleanly falls back to the selected realm's real-world screenshot.

## 1.7.0 - 2026-11-13

- **Arcane Brass & Illuminated Parchment Overhaul (Create / Ars Nouveau Aesthetic)**:
  - **32×32 High-Craft Items & Skill Icons**: Upgraded all 17 items and 20 skill icons to `32×32` hand-crafted pixel art (`tools/craft_items_skills.py`) — reshaping the 6 Universal Tools into diagonal Paxel-Halberds, `klein_bottle` into a true 4D looping crystal bottle, `teleport_codex` into an isometric gilded grimoire, `teleport_scroll` into a wax-sealed diagonal scroll, `void_scrap` into a forged void-obsidian ingot chunk, and `counter_guard_aegis`/`counter_guard_ring` into `64×64` golden-brass ward runes.
  - **Architectural 3D Block Models & Clean Textures**: Rebuilt all 11 `models/block/*.json`, 4 block-item `gui` display transforms, and 8 block texture atlases (`tools/craft_blocks.py`) around Dark Basalt Slate, Warm Alabaster, Walnut Timber, Create-style Brushed Brass, Clear Crystal Glass, and Astral Teal/Amethyst focal cores (eliminating noisy checkerboard static). Updated `UniversalLotteryMachineRenderer` atlas swatches to match.
  - **Warm Parchment, Walnut & Brass UIs**: Upgraded all 7 UI screens (`PolymerizerScreen`, `PhoenixPodScreen`, `KleinScreen`, `UniversalLotteryMachineScreen`, `PersonalSpaceScreen` Star Gate + 8 `32×32` biome medallions, `CodexScreen`, `SkillScreen` & `SkillWheelScreen`) and JEI `PolymerizerRecipeCategory` to a unified illuminated parchment, walnut wood, and burnished brass workshop aesthetic.

## 1.6.0 - 2026-11-13

- **Vanilla+ Textures & UI Overhaul**:
  - Rebuilt all 7 UI screens (`PolymerizerScreen`, `PhoenixPodScreen`, `KleinScreen`, `UniversalLotteryMachineScreen`, `PersonalSpaceScreen` Star Gate GUI, `CodexScreen`, `SkillScreen` & `SkillWheelScreen`) and JEI `PolymerizerRecipeCategory` into classic Minecraft `#C6C6C6` beveled stone-gray panels, `#8B8B8B` recessed 18×18 slots, `#404040` headers, and authentic Vanilla `book.png` leather+parchment styling for `CodexScreen`.
  - Redrew all 8 Star Gate biome emblems (`environment_*.png`), all 20 skill icons (`textures/gui/skills/*.png`) at crisp `16×16` Vanilla status-effect / advancement pixel art, and `counter_guard_aegis.png` / `counter_guard_ring.png` as crisp pixelated ward rune textures.
  - Redrew all 17 item textures (`textures/item/*.png`) in Jappa 1.14+ pixel art (tinted outlines with zero `#000000` borders, classic oak/netherite handles, Vanilla glass/book/crystal palettes) and scaled `lightning_spear` to `32×32` 1× texel density with aligned model UVs.
  - Retextured all 5 blocks (`Universal Polymerizer`, `Universal Lottery Machine`, `Personal Space Gate`, `Personal Space Return`, `Operation Phoenix Pod`) around Vanilla End & Magic block materials (Purpur, End Stone Bricks, Obsidian/Crying Obsidian, Enchanting Table crimson/gold trim, Bookshelf wood, and Vanilla glass) while preserving 3D model geometry and UV layouts.

## 1.5.2 - 2026-11-13

- Fixed client crash when opening the creative inventory (`IllegalArgumentException: Itemstack 1 universal_tool:void_essence already exists in the tab's list`) by removing duplicate `VOID_ESSENCE` entries in `ModCreativeTabs`.

## 1.5.1 - 2026-11-13

- Merged remote `origin/main` (`1.5.0`) with local Void Essence polymerizing feature (`1.4.1`).

## 1.5.0 - 2026-11-13

- **Architecture & Dedicated Server Side Isolation**:
  - Removed all client-only imports and registrations from `UniversalToolMod`, `network/` payload classes, `space/`, and `TeleportCodexItem`, routing client screens, BERs, entity renderers, keybinds, and S2C payload handlers through dedicated client-only entrypoints.
  - Introduced `AbstractDoubleEntityBlock` for shared two-block-tall `BaseEntityBlock` placement and cleanup across Operation Phoenix Pod and Universal Lottery Machine.
  - Decomposed `SkillManager` into focused combat, active-skill execution, and passive-skill handlers, and internationalized skill stat formatting.
- **Configuration, Data Components & Attachments**:
  - Added NeoForge `COMMON` config (`ModConfigs`) externalizing `polymerizerDefaultTicks`, `teleportCooldownTicks`, `phoenixCultivationTicks`, `skillXpMultiplier`, and `lotteryBaseScrapChance`.
  - Added typed Minecraft 1.21.1 `ModDataComponents` (`DNA_OWNER`, `NUTRIENT_SNAPSHOT`, `TELEPORT_TARGET`) with backward-compatible `DataComponents.CUSTOM_DATA` fallback readers.
  - Consolidated attachment registration in `ModAttachments` while preserving existing `waypoint_data` and `skill_data` keys.
- **Pocket Realm 8 Fantastical Landscapes**:
  - Replaced the 5 mundane overworld-style realm presets with 5 fantastical landscapes alongside upgraded Starlit Superflat, Celestial Sky Archipelago, and Mystic Cherry Sanctuary: `AMETHYST_STARFALL`, `SCULK_SANCTUM`, `MUSHROOM_WONDERLAND`, `OBSIDIAN_FORGE`, and `AURORA_GLACIER`.
  - Added tapered stalactite undersides, multi-layered geological strata, and bounded procedural landmarks, with seamless legacy ID mapping (`plains`, `desert`, `snowy_plains`, `birch_forest`, `savanna`).
- **Art, Models & Rendering Overhaul**:
  - Unified world assets around a 1× texel-density Arcane-Tech style, upgrading the Universal Polymerizer to a 3D astrolabe pedestal with BER item/particle rendering, fixing Phoenix Pod 180° door orientation, and resolving Lottery Machine UV/emissive/die-face rendering issues.
- **Klein Hyper-Storage, Void Lottery & JEI Integration**:
  - Documented Klein Bottle Hyper-Storage and Universal Lottery Machine (Void Dice, Cosmic Roulette, Void Slots 777) in `README.md` and `GLOSSARY.md`, and added JEI `addIngredientInfo` pages for core machines and items.
  - Hardened server authority and persistence: permission checks on `/ut` commands, server-authoritative waypoint coordinates and item checks, path-traversal sanitization in `PhotoManager`, missing block loot tables, container/extra-reward drops on block removal, Phoenix rebirth inventory duplication prevention, Polymerizer recipe duration/crafting remainder support, and Klein scroll-wheel inventory insert.

## 1.4.1 - 2026-11-07

- Added Void Essence (`void_essence`), an Epic material forged from condensed void energy.
- Added Universal Polymerizer recipe: 12 Void Scraps (`void_scrap`) fill the 12 outer clock slots to synthesize 1 Void Essence in the center slot.
- Added Void Essence to the Universal Tool creative tab and ingredients list.
- Integrated Void Essence evaluation into the Universal Lottery Machine (1800 points, Epic tier).
- Added GameTests for the 12-scrap Void Essence polymerizing recipe.

## 1.3.1 - 2026-10-31

- Added the Universal Repair Material and its anvil integration, including the Klein Bottle anvil.
- Redesigned the Universal Polymerizer interface and moved the Klein Bottle recipe to it.
- Updated Counter Guard projectile deflection, damage reflection, and first-person effects.

## 1.3.0 - 2026-10-31

- Added Shift-right-click container transfer while holding a Klein Bottle, including NeoForge item-handler storage from modded chest-like containers.
- Added optional pickup auto-deposit for item types already stocked in the terminal; it is per-player and off by default.
- Replaced the terminal's clear-search action button with the auto-deposit toggle while preserving right-click search clearing; enabled buttons now change color without shifting their icons.
- Added persistent preference storage, protocol 14, and GameTests for held-item container transfer, processing-container exclusion, matching rules, and preference persistence.

## 1.2.7 - 2026-10-27

- Show a lock badge on every inventory item that cannot enter Klein Bottle storage, including other Klein Bottles.
- Apply paged warehouse snapshots only after their final page, preserving scrolling beyond 64 entries without snapback.
- Preserve the scrollbar grab offset and support accumulated wheel scrolling through large warehouses.
- Updated the network protocol; multiplayer clients and servers must use the matching mod version.

## 1.2.6 - 2026-10-27

- Moved the Klein Bottle recipe to the Universal Polymerizer: four purpur blocks, four glass, three ender pearls, and one nether star.
- Added the Universal Repair Material, crafted in the Universal Polymerizer from netherite scraps, amethyst shards, and diamonds.
- An anvil repair consumes one material and three experience levels to restore any damageable item to full durability while preserving components.
- Added GameTests for full repair, component preservation, and invalid-item rejection.

## 1.2.5 - 2026-10-26

- Gravity Pull radius doubled to 28/32/36 blocks and duration now scales from 5s to 11s across ranks.
- Frozen Pull now scales freeze duration with rank: 5s at Lv.1, 6s at Lv.2, and 7s at Lv.3 (+1s per rank).
- Scaled Lightning Spear uncharged base damage and fully-charged damage bonus by 2.5x.
- Replaced Berserk damage resistance effect with a 20% base attack damage bonus for 10 seconds.
- Reworked Counter Guard visuals: removed circling shield quads and internal shield item; added a translucent hexagonal aegis dome, orbiting gyro rings, and explosive parry shockwaves.

## 1.2.4 - 2026-10-20

- Hid the internal Lightning Spear model item from the creative inventory; skill projectile rendering remains available.

## 1.2.3 - 2026-10-19

- Added Diamond Bulwark and Damage Clamp passives, with equipment-aware armor and final-hit damage limits.
- Raised the level cap to 50 and balanced the full skill budget at 68 points.
- Quadrupled linear XP requirements; migrated old progress without reclaiming earned skills or points.
- Level-up notifications display the current skill-menu key binding and play a sound.

## 1.2.2 - 2026-10-19

- Counter Guard now renders three independently orbiting ward shields around the protected player.
- Added a readable cast expansion, hit-trigger contract/flash/expiry animation, and cleanup on unequip, respec, logout, respawn, and dimension changes.
- Added a dedicated 78-cube Blockbench shield and synchronized its visual lifecycle to nearby clients.

## 1.2.1 - 2026-10-18

- Oppressive Field scales from a 12-block radius and 6-second Weakness III to 18 blocks and 10 seconds.
- Air Step now propels continuously while held, with a 1/2/3-second rank-based limit and cooldown on completion.
- Ore Sight now synchronizes private ore positions and draws through-wall HUD markers, with expiry and dimension cleanup.


## 1.2.0 - 2026-10-18

- Added persistent player growth with independent XP, levels, and skill points.
- Added the skill management screen and in-game active-skill wheel.
- Added nine actives: Counter Guard, Gravity Well, Frozen Pull, Oppressive Field, Ore Sight, Air Step, Lightning Spear, Berserk, and Ranged Barrage.
- Added four passives: Battle Momentum, Focused Mining, Last Stand, and Survival Flow, all driven by the equipped loadout.
- Added three levels per skill, active-slot expansion from 3 to 6 at level 10, and 4 passive slots.
- Added a level-40 cap whose total point budget exactly covers learning every skill to max level.
- Added per-level milestone points, so a single large award cannot skip a level-5 or level-10 bonus.
- Added wall-clock cooldowns that keep recovering while the server is offline.
- Added charged casting with press/release input, charge cancellation, and a short cancel lockout.
- Added loadout slot removal and replacement from the management screen.
- Added combat XP sharing within 32 blocks, crop-harvest XP, and an exclusion for blocks the player placed.
- Added target protection limited to hostile creatures, plus players and pets under server PVP rules.
- Added GameTests for milestone points, budget balance, respec refunds, offline cooldown recovery, slot expansion, and placed-then-mined exclusion.
- Gravity Well uses a 12-block base radius, growing to 16 with skill level.
- Reworked the skill management screen into a responsive game UI: a three-column desktop layout and compact Skills/Details/Loadout pages for small GUI-scaled windows.
- Added 13 deterministic pixel-art skill icons and a reusable visual style layer.
- Reworked the in-game HUD indicator into a compact 22x22 hotbar companion slot on the right side of slot 9, displaying skill icon, cooldown overlay, cast keybind, and charge bar.
- Refactored Frozen Pull targeting to acquire living entities directly under the crosshair ray, pulling them 2.2 blocks in front of the player with 5-second freeze stun and frost particles.
- Fixed repetitive freezing overriding Mob NoAI state, preventing AI from being permanently lost.
- Blocked frozen players from casting active skills or releasing charges.
- Protected the player's own pets from friendly skill targeting during PVP.
- Reinstated entity protections in canAffect: ArmorStands, Villagers, and non-hostile IronGolems are immune to offensive skills.
- Attributed direct caster source to Lightning Spear and Ranged Barrage via indirectMagic, ensuring kills award growth XP, discoveries, and combat stats.
- Fixed Battle Momentum to properly process and enhance damage against enemy players during PVP.
- Prevented player-placed blocks from granting mining XP even after 6000 ticks.
- Fixed combat XP distribution to strictly calculate true 32-block spherical distance instead of cubic AABB.
- Fixed respec (RESET) to preserve unrelated potion or beacon Haste effects.
- Fixed SkillScreen dialog dimensions at 320px minimum GUI width to prevent negative offset coordinates.
