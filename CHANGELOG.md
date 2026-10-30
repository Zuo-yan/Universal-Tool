# Changelog

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
