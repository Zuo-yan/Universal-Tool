# ADR 0002: Arcane-Tech 1× Texel Density Art Direction

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

An art audit across the mod's 9+ visual domains uncovered stark inconsistencies:
- Block textures ranged across four mismatched paradigms: flat 16×16 cubes (`Universal Polymerizer`), boxy 16×16 frames (`Phoenix Pod`), sparse 64×64 procedural XOR atlases (`Personal Space Gate`), and a 256×256 sheet (`Universal Lottery Machine`) with 87% unused space and 196 degenerate zero-area UV faces.
- Item art mixed crisp 16×16 Jappa-style pixel clusters with a LANCZOS-downsampled 32×32 `klein_bottle.png` (324 anti-aliased colors across 326 pixels) and sparse semi-transparent bio-items.
- `PhoenixPodRenderer` suffered a 180° door rotation mismatch (`-facing.toYRot()`), and `UniversalLotteryMachineRenderer` rendered non-emissive brass at full-bright while omitting top/bottom die trim faces.

## Decision

1. **Unified 1× Blockbench Texel Density (`16 px / block`):** Standardize world block and multiblock models on crisp `1×` texel density without anti-aliased gradients, Gaussian blur, or sub-pixel zero-area UV faces.
2. **Arcane-Tech Visual Language:** Harmonize machine palettes around dark slate/obsidian housings (`#16181E`–`#262A34`), antique brass/gold trim (`#DAA844`), and cyan/amethyst energy accents (`#30C2BC` / `#9B79DE`) across both blocks and screens.
3. **Custom BER & Model Alignment:** Upgrade `Universal Polymerizer` from a flat cube to a 3D astrolabe pedestal with item/particle BER support, sync `Phoenix Pod` orientation and doors cleanly, and fix `Lottery Machine` UVs, emissive separation, and 6-face die rendering.

## Why / Trade-offs

- **Pros:** Eliminates jarring resolution clashes with Vanilla+ Minecraft, fixes visual/orientation bugs in BERs, and dramatically reduces wasted texture atlas memory.
- **Trade-offs:** Rebuilding `.bbmodel` / JSON models and hand-clustered textures requires coordinated updates to `VoxelShape`, `.noOcclusion()` block properties, andBlockEntity client sync packets.
