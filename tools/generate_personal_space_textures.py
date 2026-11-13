"""Draw the Pocket Realm (Personal Space) pixel-art assets using only Python's standard library."""

from __future__ import annotations

import base64
import json
import math
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/universal_tool/textures"

# Unified Arcane-Tech Palette
DS_VOID = (0x14, 0x16, 0x1D, 0xFF)
DS_DARK = (0x1E, 0x22, 0x2D, 0xFF)
DS_MID = (0x2B, 0x31, 0x40, 0xFF)
DS_LIGHT = (0x3E, 0x46, 0x59, 0xFF)
DS_EDGE = (0x56, 0x60, 0x78, 0xFF)

GOLD_DARK = (0x7A, 0x54, 0x1E, 0xFF)
GOLD_MID = (0xB3, 0x80, 0x2C, 0xFF)
GOLD_BRIGHT = (0xDF, 0xAD, 0x34, 0xFF)
GOLD_SHINE = (0xF5, 0xD6, 0x78, 0xFF)

AME_DARK = (0x4A, 0x25, 0x74, 0xFF)
AME_MID = (0x77, 0x42, 0xB8, 0xFF)
AME_BRIGHT = (0xA4, 0x64, 0xE8, 0xFF)
AME_GLOW = (0xD8, 0xA8, 0xFF, 0xFF)

CYAN_DARK = (0x17, 0x4B, 0x58, 0xFF)
CYAN_MID = (0x28, 0x82, 0x94, 0xFF)
CYAN_BRIGHT = (0x42, 0xC6, 0xD2, 0xFF)
CYAN_GLOW = (0x9C, 0xF5, 0xF0, 0xFF)


class Canvas:
    def __init__(self, width: int, height: int, color=(0, 0, 0, 0)):
        self.width = width
        self.height = height
        self.pixels = [[color for _ in range(width)] for _ in range(height)]

    def set(self, x: int, y: int, color):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.pixels[y][x] = color

    def rect(self, x0: int, y0: int, x1: int, y1: int, color):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, color)

    def border(self, x0: int, y0: int, x1: int, y1: int, color, thickness=1):
        self.rect(x0, y0, x1, y0 + thickness, color)
        self.rect(x0, y1 - thickness, x1, y1, color)
        self.rect(x0, y0, x0 + thickness, y1, color)
        self.rect(x1 - thickness, y0, x1, y1, color)

    def save(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = bytearray()
        for row in self.pixels:
            raw.append(0)  # PNG filter: None
            for rgba in row:
                raw.extend(rgba)

        def chunk(kind: bytes, data: bytes):
            return (
                struct.pack(">I", len(data))
                + kind
                + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
            )

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">2I5B", self.width, self.height, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        path.write_bytes(png)


def panel():
    c = Canvas(320, 236, (0, 0, 0, 0))
    c.rect(0, 0, 320, 236, (8, 13, 23, 255))
    c.rect(4, 4, 316, 232, (21, 28, 42, 255))
    c.border(0, 0, 320, 236, (5, 8, 15, 255), 4)
    c.border(4, 4, 316, 232, (95, 92, 143, 255), 1)
    c.rect(8, 8, 312, 50, (29, 36, 55, 255))
    c.rect(8, 49, 312, 51, (78, 83, 123, 255))
    c.rect(8, 200, 312, 202, (63, 73, 98, 255))
    c.rect(8, 8, 12, 228, (67, 186, 192, 255))
    c.rect(308, 8, 312, 228, (119, 91, 181, 255))
    # Header aperture emblem
    c.rect(276, 14, 300, 38, (15, 22, 37, 255))
    c.border(276, 14, 300, 38, (91, 83, 153, 255), 1)
    for y in range(18, 35):
        x = 288 + (1 if y % 3 else -1)
        c.set(x, y, (89, 202, 205, 255) if y % 2 else (155, 121, 222, 255))
    c.set(288, 25, (221, 196, 247, 255))
    # Corner fasteners and quiet schematic tick marks
    for x in (18, 302):
        for y in (18, 216):
            c.rect(x - 2, y - 2, x + 2, y + 2, (104, 108, 142, 255))
            c.set(x, y, (155, 222, 219, 255))
    for x in range(20, 64, 4):
        c.set(x, 40, (69, 177, 190, 255))
        c.set(320 - x, 40, (120, 92, 181, 255))
    # Slim inset rails around content area
    c.rect(18, 56, 19, 194, (38, 51, 69, 255))
    c.rect(301, 56, 302, 194, (38, 51, 69, 255))
    return c


# --------------------------------------------------------------------------- environment emblems
#
# Eight 16x16 Pocket Realm emblems on transparent background with crisp Jappa clustering.
def emblem_base():
    return Canvas(16, 16, (0, 0, 0, 0))


def emblem_superflat():
    c = emblem_base()
    # Three geometric floating slate-and-gold calibration strata plates
    c.rect(2, 4, 14, 5, CYAN_GLOW)
    c.rect(2, 5, 14, 7, CYAN_BRIGHT)
    c.rect(2, 7, 14, 8, CYAN_DARK)
    c.rect(3, 9, 13, 10, GOLD_BRIGHT)
    c.rect(3, 10, 13, 11, GOLD_DARK)
    c.rect(4, 12, 12, 13, AME_BRIGHT)
    c.rect(4, 13, 12, 14, AME_DARK)
    return c


def emblem_sky_islands():
    c = emblem_base()
    # Floating arcane island with crystal tuft, tapered rock underside, hanging glow vine & satellite
    c.rect(3, 4, 12, 5, CYAN_GLOW)
    c.rect(2, 5, 13, 7, CYAN_BRIGHT)
    c.rect(3, 7, 12, 8, DS_LIGHT)
    c.rect(4, 8, 11, 9, DS_MID)
    c.rect(5, 9, 10, 10, DS_DARK)
    c.rect(6, 10, 9, 11, DS_VOID)
    c.set(7, 11, DS_VOID)
    # Hanging glow vine
    c.set(4, 9, CYAN_BRIGHT)
    c.set(4, 10, CYAN_GLOW)
    c.set(4, 11, CYAN_MID)
    # Tiny floating crystal spire on top
    c.set(6, 2, AME_GLOW)
    c.rect(6, 3, 8, 4, AME_BRIGHT)
    # Satellite isle
    c.rect(12, 10, 15, 11, CYAN_BRIGHT)
    c.rect(13, 11, 14, 12, DS_MID)
    return c


def emblem_cherry_grove():
    c = emblem_base()
    # Blossoming cherry tree atop a twilight slate mound with drifting petals
    cherry_hi = (0xFA, 0xC8, 0xEB, 0xFF)
    cherry_mid = (0xE5, 0x8B, 0xC8, 0xFF)
    cherry_dark = (0x9C, 0x4A, 0x89, 0xFF)
    bark = (0x4D, 0x2F, 0x3D, 0xFF)
    for x in range(2, 14):
        y = 12 - round(2.0 * math.exp(-(((x - 8) / 4.5) ** 2)))
        c.set(x, y, CYAN_BRIGHT if 4 <= x <= 11 else CYAN_MID)
        c.rect(x, y + 1, x + 1, 14, DS_MID)
    c.rect(7, 7, 9, 12, bark)
    c.rect(3, 3, 13, 7, cherry_mid)
    c.rect(2, 4, 14, 6, cherry_mid)
    c.rect(4, 2, 11, 4, cherry_hi)
    c.rect(3, 6, 13, 7, cherry_dark)
    c.set(5, 3, (0xFF, 0xEF, 0xF8, 0xFF))
    c.set(10, 4, cherry_hi)
    # Falling blossom petals
    c.set(3, 9, cherry_hi)
    c.set(13, 8, cherry_mid)
    c.set(11, 10, cherry_hi)
    return c


def emblem_amethyst_starfall():
    c = emblem_base()
    # Jagged amethyst crystal cluster beneath a 4-point starfall
    c.rect(2, 12, 14, 14, DS_MID)
    c.rect(3, 11, 13, 12, DS_LIGHT)
    # Left spire
    c.rect(3, 7, 5, 11, AME_MID)
    c.set(3, 6, AME_BRIGHT)
    c.set(4, 7, AME_GLOW)
    # Center tall spire
    c.rect(6, 5, 9, 11, AME_MID)
    c.rect(7, 4, 8, 10, AME_BRIGHT)
    c.set(7, 5, AME_GLOW)
    c.set(8, 6, AME_DARK)
    # Right spire
    c.rect(10, 8, 12, 11, AME_DARK)
    c.set(10, 7, AME_BRIGHT)
    c.set(11, 8, AME_MID)
    # Starfall spark in top right
    c.set(12, 2, CYAN_GLOW)
    c.set(12, 4, CYAN_GLOW)
    c.set(11, 3, CYAN_BRIGHT)
    c.set(13, 3, CYAN_BRIGHT)
    c.set(12, 3, (0xFF, 0xFF, 0xFF, 0xFF))
    c.set(10, 5, CYAN_MID)
    return c


def emblem_sculk_sanctum():
    c = emblem_base()
    # Obsidian-sculk obelisk arch housing a glowing soul lantern core
    c.rect(2, 12, 14, 14, DS_VOID)
    c.rect(3, 11, 13, 12, CYAN_DARK)
    c.rect(3, 4, 5, 11, DS_DARK)
    c.rect(11, 4, 13, 11, DS_DARK)
    c.rect(4, 2, 12, 4, DS_MID)
    c.set(4, 3, CYAN_MID)
    c.set(11, 3, CYAN_MID)
    # Soul lantern suspended in center
    c.set(7, 4, GOLD_MID)
    c.set(8, 4, GOLD_MID)
    c.rect(6, 5, 10, 9, CYAN_MID)
    c.rect(7, 6, 9, 8, CYAN_GLOW)
    c.rect(6, 9, 10, 10, GOLD_DARK)
    # Sculk speckles
    c.set(3, 6, CYAN_BRIGHT)
    c.set(12, 8, CYAN_BRIGHT)
    c.set(5, 12, CYAN_GLOW)
    c.set(10, 12, CYAN_BRIGHT)
    return c


def emblem_mushroom_wonderland():
    c = emblem_base()
    # Giant bioluminescent arcane mushroom + tiny glowing cap
    stem_hi = (0xE8, 0xEC, 0xF5, 0xFF)
    stem_sh = (0x9C, 0xB0, 0xC8, 0xFF)
    c.rect(2, 12, 14, 14, DS_MID)
    c.rect(6, 7, 9, 12, stem_hi)
    c.rect(8, 7, 9, 12, stem_sh)
    # Dome cap
    c.rect(4, 2, 11, 3, AME_BRIGHT)
    c.rect(3, 3, 12, 6, AME_MID)
    c.rect(2, 5, 13, 7, AME_DARK)
    c.rect(4, 6, 11, 7, AME_MID)
    # Glowing cyan spots
    c.set(4, 4, CYAN_GLOW)
    c.set(7, 3, CYAN_GLOW)
    c.set(10, 4, CYAN_BRIGHT)
    c.set(11, 5, CYAN_GLOW)
    # Mini shroom on right
    c.rect(12, 10, 13, 12, stem_hi)
    c.rect(11, 9, 14, 10, CYAN_BRIGHT)
    c.set(12, 8, CYAN_GLOW)
    return c


def emblem_obsidian_forge():
    c = emblem_base()
    # Gilded obsidian forge basin with molten amber-gold core
    lava_hi = (0xFF, 0xE2, 0x6A, 0xFF)
    lava_mid = (0xF2, 0x7A, 0x1A, 0xFF)
    lava_dark = (0xB8, 0x35, 0x14, 0xFF)
    c.rect(2, 12, 14, 14, DS_VOID)
    # Flanking obsidian forge pillars
    c.rect(2, 4, 5, 12, DS_DARK)
    c.rect(11, 4, 14, 12, DS_DARK)
    c.rect(2, 3, 5, 4, GOLD_BRIGHT)
    c.rect(11, 3, 14, 4, GOLD_BRIGHT)
    c.rect(3, 5, 4, 10, DS_MID)
    c.rect(12, 5, 13, 10, DS_MID)
    # Crucible & magma stream
    c.rect(5, 9, 11, 12, GOLD_MID)
    c.rect(6, 8, 10, 11, lava_mid)
    c.rect(7, 9, 9, 10, lava_hi)
    c.rect(7, 4, 9, 8, lava_dark)
    c.set(7, 6, lava_mid)
    c.set(8, 5, lava_hi)
    c.set(6, 3, GOLD_SHINE)
    c.set(9, 2, lava_hi)
    return c


def emblem_aurora_glacier():
    c = emblem_base()
    # Crystalline ice spires beneath a cyan-amethyst aurora ribbon
    ice_white = (0xEC, 0xFB, 0xFF, 0xFF)
    # Aurora ribbon across upper sky
    for x in range(2, 14):
        y = 3 + (1 if x in (5, 6, 7, 8) else 0)
        c.set(x, y, AME_BRIGHT if x % 2 == 0 else CYAN_GLOW)
        c.set(x, y + 1, CYAN_BRIGHT if x < 8 else AME_MID)
    # Glacier base
    c.rect(2, 12, 14, 14, CYAN_DARK)
    # Left spire
    c.rect(3, 8, 6, 12, CYAN_MID)
    c.rect(4, 7, 5, 11, CYAN_BRIGHT)
    c.set(4, 6, ice_white)
    # Center tall spire
    c.rect(6, 6, 10, 12, CYAN_MID)
    c.rect(7, 5, 9, 11, CYAN_GLOW)
    c.set(7, 4, ice_white)
    c.set(8, 6, ice_white)
    # Right spire
    c.rect(10, 9, 13, 12, CYAN_DARK)
    c.rect(11, 8, 12, 11, CYAN_BRIGHT)
    c.set(11, 7, ice_white)
    return c


ENVIRONMENT_EMBLEMS = {
    "superflat": emblem_superflat,
    "sky_islands": emblem_sky_islands,
    "cherry_grove": emblem_cherry_grove,
    "amethyst_starfall": emblem_amethyst_starfall,
    "sculk_sanctum": emblem_sculk_sanctum,
    "mushroom_wonderland": emblem_mushroom_wonderland,
    "obsidian_forge": emblem_obsidian_forge,
    "aurora_glacier": emblem_aurora_glacier,
}


def portal_atlas(return_gate: bool = False) -> Canvas:
    """64x64 Arcane-Tech atlas for Pocket Realm Gate / Return Gate (zero XOR noise)."""
    c = Canvas(64, 64, DS_VOID)

    # Quadrant (0, 0) [0..16, 0..16]: Deepslate cut bricks with subtle brass seam accents
    c.rect(0, 0, 16, 16, DS_MID)
    for y in (0, 5, 10, 15):
        c.rect(0, y, 16, y + 1, DS_VOID)
    for y_band, offset in ((1, 0), (6, 4), (11, 8)):
        c.rect(0, y_band, 16, y_band + 1, DS_LIGHT)
        for bx in range(offset, 16, 8):
            c.rect(bx, y_band, bx + 1, min(y_band + 4, 15), DS_VOID)
            c.set(bx + 1, y_band + 1, DS_EDGE)
    # Subtle Jappa bevel highlights and corner brass rivets
    for x, y, col in (
        (2, 2, DS_LIGHT),
        (5, 3, DS_DARK),
        (11, 2, DS_EDGE),
        (14, 3, DS_DARK),
        (3, 7, DS_EDGE),
        (9, 8, DS_DARK),
        (13, 7, DS_LIGHT),
        (2, 12, DS_LIGHT),
        (10, 13, DS_EDGE),
        (1, 1, GOLD_MID),
        (14, 1, GOLD_DARK),
    ):
        c.set(x, y, col)

    # Quadrant (0, 1) [0..16, 16..32]: Heavy foundation plinth with beveled brass strip
    c.rect(0, 16, 16, 32, DS_DARK)
    c.rect(0, 16, 16, 17, GOLD_MID)
    c.rect(0, 17, 16, 18, DS_LIGHT)
    for x in range(0, 16, 4):
        c.set(x, 16, GOLD_BRIGHT)
        c.rect(x, 18, x + 1, 32, DS_VOID)
    c.rect(0, 24, 16, 25, DS_VOID)
    c.rect(0, 25, 16, 26, DS_MID)

    # Quadrant (1, 0) [16..32, 0..16]: Gilded brass & rune inlay + crystal accent
    accent_dark = CYAN_DARK if return_gate else AME_DARK
    accent_mid = CYAN_MID if return_gate else AME_MID
    accent_bright = CYAN_BRIGHT if return_gate else AME_BRIGHT
    accent_glow = CYAN_GLOW if return_gate else AME_GLOW

    c.rect(16, 0, 32, 16, GOLD_DARK)
    c.rect(17, 1, 31, 15, GOLD_MID)
    c.border(16, 0, 32, 16, GOLD_BRIGHT, 1)
    # Crystal corner [16..18, 0..3] sampled by keystone/crystals
    c.rect(16, 0, 18, 3, accent_bright)
    c.set(16, 0, accent_glow)
    c.set(17, 1, accent_glow)
    c.set(17, 2, accent_mid)
    # Runic trim channels across [16..32, 0..16]
    for y in range(2, 14):
        for x in range(18, 30):
            if (x + y) % 4 == 0:
                c.set(x, y, accent_bright)
            elif (x * 2 + y) % 5 == 0:
                c.set(x, y, GOLD_SHINE)
            elif y % 3 == 0:
                c.set(x, y, accent_dark)

    # Quadrant (2, 0) [32..48, 0..16]: Stepped arcane portal vortex
    # aperture element samples [32..38, 0..9], so center bright spiral at (35, 4.5)
    c.rect(32, 0, 48, 16, DS_VOID)
    for y in range(16):
        for x in range(32, 48):
            dx = x - 35.0
            dy = y - 4.5
            r = math.hypot(dx / 3.0, dy / 4.2)
            ang = math.atan2(dy, dx) + r * 2.4
            arm = math.sin(ang * 2.0)
            if r < 0.38:
                c.set(x, y, CYAN_GLOW if return_gate else AME_GLOW)
            elif r < 0.78:
                c.set(x, y, accent_glow if arm > 0.1 else accent_bright)
            elif r < 1.15:
                c.set(x, y, accent_bright if arm > 0 else accent_mid)
            elif r < 1.6:
                c.set(x, y, accent_mid if arm > 0.2 else accent_dark)
            else:
                c.set(x, y, accent_dark if (x + y) % 3 == 0 else DS_VOID)
    # Complementary focal spark inside aperture center
    c.set(35, 4, AME_GLOW if return_gate else CYAN_GLOW)
    c.set(34, 5, CYAN_GLOW if return_gate else AME_GLOW)
    c.set(36, 3, GOLD_SHINE)

    # Quadrant (1, 1) [16..32, 16..32]: Clean dark deepslate trim
    c.rect(16, 16, 32, 32, DS_DARK)
    c.border(16, 16, 32, 32, DS_MID, 1)

    return c


def main() -> None:
    # Delete obsolete dead block textures and replaced environment emblems
    obsolete = [
        ROOT / "block/personal_space_stone.png",
        ROOT / "block/personal_space_core.png",
        ROOT / "block/personal_space_return.png",
        ROOT / "block/polymerizer_top.png",
        ROOT / "block/polymerizer_front.png",
        ROOT / "block/polymerizer_side.png",
        ROOT / "block/polymerizer_bottom.png",
        ROOT / "gui/environment_plains.png",
        ROOT / "gui/environment_desert.png",
        ROOT / "gui/environment_snowy_plains.png",
        ROOT / "gui/environment_birch_forest.png",
        ROOT / "gui/environment_savanna.png",
    ]
    for old_path in obsolete:
        if old_path.exists():
            old_path.unlink()
            print("Removed obsolete texture:", old_path.relative_to(ROOT))

    panel().save(ROOT / "gui/personal_space_panel.png")
    for environment, draw in ENVIRONMENT_EMBLEMS.items():
        draw().save(ROOT / f"gui/environment_{environment}.png")
    gate_atlas = ROOT / "block/personal_space_gate_atlas.png"
    return_atlas = ROOT / "block/personal_space_return_atlas.png"
    portal_atlas(False).save(gate_atlas)
    portal_atlas(True).save(return_atlas)

    art_dir = Path(__file__).resolve().parents[1] / "art/assets"
    for bb_name, atlas_path in (
        ("personal_space_gate.bbmodel", gate_atlas),
        ("personal_space_return.bbmodel", return_atlas),
    ):
        bb_path = art_dir / bb_name
        if bb_path.is_file():
            bb = json.loads(bb_path.read_text(encoding="utf-8"))
            b64 = base64.b64encode(atlas_path.read_bytes()).decode("ascii")
            for tex in bb.get("textures", []):
                tex["path"] = str(atlas_path)
                tex["source"] = f"data:image/png;base64,{b64}"
            bb_path.write_text(json.dumps(bb, indent=2) + "\n", encoding="utf-8")

    print("Wrote Pocket Realm Arcane-Tech atlases, synced .bbmodels, and 8 landscape emblems under", ROOT)


if __name__ == "__main__":
    main()
