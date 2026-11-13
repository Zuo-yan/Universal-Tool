# ADR 0001: Domain-Driven Package Layout and Physical Side Isolation

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

`UniversalToolMod` previously imported client-only classes (`Screen`, `BlockEntityRenderer`, `EntityRenderer`, keybinding handlers) directly inside the common `@Mod` entrypoint and registered clientbound packet handlers directly on payload records in `network/` that imported `client.*` types. Likewise, `PrivateSpaceManager` and `TeleportCodexItem` referenced client screens directly, and a client-only visual test sat inside `space/`. On a Dedicated Server, loading `@OnlyIn(Dist.CLIENT)` classes risks `NoClassDefFoundError` during class verification.

Meanwhile, newer features (`klein/`, `lottery/`, `phoenix/`, `space/`, `skill/`) adopted domain-sliced packages, while earlier features remained split across horizontal packages (`item/`, `block/`, `menu/`, `recipe/`, `client/`).

## Decision

1. **Strict Physical Side Isolation:**
   - Remove all `net.minecraft.client.*` and `org.gwfx.universaltool.client.*` references from `UniversalToolMod`, `network/` payload records, `space/`, and common item/block logic.
   - Route client-only registration (`RegisterMenuScreensEvent`, `EntityRenderersEvent.RegisterRenderers`, `RegisterKeyMappingsEvent`) through a dedicated `@EventBusSubscriber(value = Dist.CLIENT)` entrypoint (`client/UniversalToolClientMod.java`).
   - Route clientbound S2C packet execution through `client/ClientPayloadHandlers.java`, keeping `network/` payload records pure data + `StreamCodec` definitions.
2. **Cohesive Domain Collaboration & Shared Abstractions:**
   - Keep existing public registry/packet identifiers stable while consolidating shared multiblock lifecycle logic (`AbstractDoubleEntityBlock`) and decomposing oversized domain managers (`SkillManager` into combat tracker, active executor, and passive handler).
   - Keep `init/` as the central registry hub (`ModBlocks`, `ModItems`, `ModDataComponents`, `ModAttachments`, etc.).

## Why / Trade-offs

- **Pros:** Guarantees Dedicated Server safety, eliminates implicit classloader coupling, reduces God-class complexity in `skill/`, and avoids duplicate two-block multiblock placement/cleanup bugs.
- **Trade-offs:** Moving client classes out of existing common packages must be done selectively (e.g., relocating `PrivateIslandVisualTest` to `client/`) without breaking existing serialized IDs, network protocol version `"14"`, or GameTest entrypoints.
