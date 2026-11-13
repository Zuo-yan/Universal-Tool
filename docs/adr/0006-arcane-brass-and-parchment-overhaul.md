# ADR 0006: Arcane Brass & Illuminated Parchment Overhaul

- **Status:** Accepted
- **Date:** 2026-11-13

## Context

In-game creative-tab inspection of the v1.6.0 strict-Vanilla pass revealed that forcing 16×16 Purpur/End-Stone checkerboard noise onto complex 3D block models and compact 16×16 tool/item grids caused muddy visual static and clumsy silhouettes.

## Decision

1. **32×32 High-Craft Pixel Art for Items & Skill Icons:** Upgrade all 17 item textures, all 20 skill icons, and all 8 Star Gate biome medallions (`environment_*.png`) to `32×32` crisp pixel art with coherent dark-tinted outlines, rich specular metal/glass highlights, and expressive silhouettes (Paxel-Halberd Universal Tools, looping 4D Klein Bottle, wax-sealed diagonal scroll, isometric grimoire).
2. **Architectural 3D Block Models & Low-Noise Materiality:** Rebuild block models (`models/block/*.json`), inventory `gui` display transforms, and block atlases around Dark Basalt Slate, Warm Alabaster, Walnut Timber, Brushed Brass, Clear Glass, and Astral Teal/Amethyst crystal focal points.
3. **Illuminated Parchment + Walnut & Brass UIs:** Unify all 7 UI screens and JEI recipe category around warm cream parchment backgrounds, walnut wood + burnished brass borders, `#C4B395` beveled slots, dark espresso ink typography, and dark-walnut input wells.

## Why / Trade-offs

- **Pros:** Cohesive, low-noise, flagship modded Minecraft aesthetic (inspired by Create and Ars Nouveau) across world blocks, inventory silhouettes, and every GUI screen.
- **Trade-offs:** Supersedes ADR 0005's strict 16×16 / `#C6C6C6` constraint while keeping all container menu slot layouts and BER animations intact.
