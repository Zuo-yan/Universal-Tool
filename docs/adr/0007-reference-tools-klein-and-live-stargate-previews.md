# ADR 0007: Reference-Aligned 64×64 Tools/Klein Bottle & Live Star Gate World Previews

## Status
Accepted (`v1.8.0`)

## Context
After the `v1.7.0` Arcane Brass & Parchment visual overhaul:
1. The user supplied explicit silhouette/material references for the 6 Universal Tools (`*_universal_tool.png`: upright symmetrical cross-halberd multi-tool combining top spade, left curved pickaxe, right curved axe blade, leather-wrapped shaft, core gem, and bottom spear/dagger tip) and the Klein Bottle (`klein_bottle.png`: asymmetrical translucent cyan crystal glass loop with dark iron collar rings and base pedestal).
2. The Star Gate terminal (`PersonalSpaceScreen`) previously rendered only a thin procedural strip banner instead of showing real Minecraft terrain and the player's own constructed base.

## Decision
1. **Reference-Aligned `64×64` Item Sprites & Upright Display Transforms**:
   - Generated `64×64` crisp RGBA sprites (binary alpha `{0, 255}`, zero `#000000` black pixels) via `tools/craft_ref_tools_klein.py` for all 6 Universal Tool tiers and `klein_bottle.png`.
   - Updated all 6 `models/item/*_universal_tool.json` item models (`parent: "minecraft:item/generated"`) with upright GUI display framing and natural `handheld`-style in-hand transforms.
2. **Real-World Realm Previews (`env_*.png`) & Live Base Snapshots**:
   - Extended `PrivateIslandVisualTest` (`runIslandVisual`) to generate/load all 8 `PrivateSpaceEnvironment` realms in-engine and capture panoramic `544×136` screenshots into `src/main/resources/assets/universal_tool/textures/gui/previews/env_<id>.png`.
   - Enlarged `PersonalSpaceScreen`'s brass-framed viewport to `64px` tall (with `48px` in compact layouts), rendering the saved base snapshot (`PhotoManager` + `PersonalSpaceSnapshotHandler`) first in `VISIT`/`CHANGE_PASSWORD` modes and falling back to the realm's real-world screenshot (`env_<id>.png`).
   - Added automatic client-side snapshot capture when leaving a private realm or shortly after entering it once chunks finish rendering, and wired `PrivateSpaceEnvironment` IDs through `PersonalSpaceScreenPayload` / `PrivateSpaceManager`.

## Consequences
- Every realm preview in the Star Gate UI displays authentic Minecraft world terrain rather than synthetic color bands, and existing player bases automatically show their own latest in-world capture.
- Keeping screenshots generated deterministically via `runIslandVisual` makes future realm additions straightforward to refresh.
