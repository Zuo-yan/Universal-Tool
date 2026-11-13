# ADR 0004: Typed Data Components and Common Config Externalization

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

1. **Untyped Item NBT:** In Minecraft 1.21.1, `DnaSampleItem`, `DnaSyringeItem`, `NutrientSolutionItem`, and `TeleportScrollItem` mutated raw `CompoundTag` entries inside `DataComponents.CUSTOM_DATA` rather than using registered `DataComponentType<T>` records with `Codec` and `StreamCodec`.
2. **Fragmented Attachment Registries:** `WaypointData` and `SkillAttachments` each instantiated independent `DeferredRegister<AttachmentType<?>>` instances instead of registering through a unified `init/ModAttachments` hub.
3. **Hardcoded Balance Constants:** Core balance numbers (polymerizer crafting ticks, teleport cooldown ticks, Phoenix cultivation ticks, skill XP multiplier, lottery base scrap chance) were hardcoded across disparate classes, preventing server administrators or pack authors from tuning progression.

## Decision

1. **Typed `ModDataComponents` with Legacy Fallback:** Register `DNA_OWNER`, `NUTRIENT_SNAPSHOT`, and `TELEPORT_TARGET` via NeoForge's `DeferredRegister.DataComponents` (`init/ModDataComponents.java`), pairing immutable Java records (`Codec` + `StreamCodec`) with read helpers that fall back to legacy `DataComponents.CUSTOM_DATA` tags for seamless migration.
2. **Consolidated `ModAttachments`:** Introduce `init/ModAttachments.java` as the single entrypoint for mod attachment registration while preserving existing `waypoint_data` and `skill_data` keys and holder references.
3. **Centralized `ModConfigs` (`COMMON` Spec):** Introduce `config/ModConfigs.java` using `ModConfigSpec` with safe default-fallback getters for `polymerizerDefaultTicks` (30), `teleportCooldownTicks` (100), `phoenixCultivationTicks` (1200), `skillXpMultiplier` (1.0), and `lotteryBaseScrapChance` (0.35).

## Why / Trade-offs

- **Pros:** Aligns item metadata with 1.21.1 data-component best practices (network-synced, immutable, codec-validated), centralizes attachment wiring, and exposes clean server/pack tuning without breaking existing saves or items.
- **Trade-offs:** Legacy `CUSTOM_DATA` fallback readers must remain supported during the transition window so items crafted in earlier 1.4.x saves continue functioning.
