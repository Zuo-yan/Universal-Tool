"""Draw the private-space pixel-art assets using only Python's standard library."""

from __future__ import annotations

import math
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/universal_tool/textures"


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
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">2I5B", self.width, self.height, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        path.write_bytes(png)


def gateway_stone():
    c = Canvas(16, 16)
    base = (31, 39, 57, 255)
    c.rect(0, 0, 16, 16, base)
    # Cut-stone plates with cool slate highlights and small mineral flecks.
    for y in range(1, 16, 5):
        offset = 0 if (y // 5) % 2 == 0 else 4
        c.rect(0, y, 16, y + 1, (20, 27, 43, 255))
        for x in range(offset, 16, 8):
            c.rect(x, y, x + 1, min(y + 5, 16), (18, 25, 40, 255))
    for x, y, color in [
        (2, 2, (55, 67, 91, 255)), (5, 3, (39, 49, 71, 255)), (11, 2, (46, 58, 82, 255)),
        (14, 4, (56, 64, 84, 255)), (3, 8, (45, 56, 80, 255)), (9, 7, (50, 60, 86, 255)),
        (13, 10, (42, 52, 76, 255)), (1, 13, (52, 63, 87, 255)), (7, 14, (43, 52, 76, 255)),
        (11, 13, (55, 66, 91, 255)),
    ]:
        c.set(x, y, color)
    # Two restrained rune seams make the block recognizable without noisy checker patterns.
    for x, y in [(7, 1), (8, 2), (8, 3), (7, 4), (6, 5), (6, 6), (7, 7), (8, 8), (9, 9), (9, 10), (8, 11), (7, 12), (6, 13), (6, 14)]:
        c.set(x, y, (92, 75, 178, 255))
    c.set(8, 5, (73, 188, 207, 255))
    c.set(7, 10, (73, 188, 207, 255))
    return c


def portal_core():
    c = Canvas(16, 16, (12, 20, 37, 255))
    c.rect(1, 1, 15, 15, (18, 31, 56, 255))
    c.border(1, 1, 15, 15, (55, 68, 111, 255))
    # Pixel-art elliptical aperture and its two-tone halo.
    for y in range(2, 14):
        for x in range(3, 13):
            d = ((x - 7.5) / 4.2) ** 2 + ((y - 7.5) / 5.4) ** 2
            if 0.58 < d < 1.22:
                c.set(x, y, (99, 77, 195, 255) if (x + y) % 3 else (66, 198, 209, 255))
            elif d <= 0.58:
                c.set(x, y, (22, 85, 119, 255) if (x + y) % 2 else (27, 57, 99, 255))
    for x, y in [(7, 2), (8, 2), (5, 4), (10, 5), (5, 10), (10, 11), (7, 13), (8, 13)]:
        c.set(x, y, (160, 134, 238, 255))
    c.set(7, 7, (114, 236, 227, 255))
    c.set(8, 8, (78, 188, 213, 255))
    return c


def return_stone():
    c = Canvas(16, 16, (21, 37, 53, 255))
    c.rect(1, 1, 15, 15, (26, 49, 65, 255))
    c.border(1, 1, 15, 15, (73, 101, 119, 255))
    # Octagonal cyan return sigil.
    ring = [(6, 3), (7, 2), (9, 2), (10, 3), (12, 5), (13, 6), (13, 9), (12, 10), (10, 12), (9, 13), (6, 13), (5, 12), (3, 10), (2, 9), (2, 6), (3, 5)]
    for i, (x, y) in enumerate(ring):
        c.set(x, y, (86, 217, 218, 255) if i % 4 else (162, 145, 239, 255))
    c.rect(6, 5, 10, 11, (45, 136, 161, 255))
    c.rect(7, 6, 9, 10, (115, 230, 218, 255))
    c.set(7, 7, (220, 249, 241, 255))
    return c


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
    # The panel's header mark resembles a small aperture, with restrained runic traces.
    c.rect(276, 14, 300, 38, (15, 22, 37, 255))
    c.border(276, 14, 300, 38, (91, 83, 153, 255), 1)
    for y in range(18, 35):
        x = 288 + (1 if y % 3 else -1)
        c.set(x, y, (89, 202, 205, 255) if y % 2 else (155, 121, 222, 255))
    c.set(288, 25, (221, 196, 247, 255))
    # Corner fasteners and quiet schematic tick marks.
    for x in (18, 302):
        for y in (18, 216):
            c.rect(x - 2, y - 2, x + 2, y + 2, (104, 108, 142, 255))
            c.set(x, y, (155, 222, 219, 255))
    for x in range(20, 64, 4):
        c.set(x, 40, (69, 177, 190, 255))
        c.set(320 - x, 40, (120, 92, 181, 255))
    # Slim inset rails give the content area its own frame.
    c.rect(18, 56, 19, 194, (38, 51, 69, 255))
    c.rect(301, 56, 302, 194, (38, 51, 69, 255))
    return c


# --------------------------------------------------------------------------- environment emblems
#
# Eight 16x16 marks for the environment selector row. They share the panel's cyan/purple palette and
# stay transparent outside the glyph so the chip's own fill and selection halo show through.
EMBLEM_DARK = (16, 24, 38, 255)
EMBLEM_CYAN = (99, 214, 216, 255)
EMBLEM_CYAN_DIM = (52, 132, 141, 255)
EMBLEM_PURPLE = (155, 121, 222, 255)
EMBLEM_PURPLE_DIM = (95, 70, 150, 255)
EMBLEM_LIGHT = (232, 244, 247, 255)


def emblem_base():
    return Canvas(16, 16, (0, 0, 0, 0))


def emblem_superflat():
    c = emblem_base()
    # Three stacked plates: the whole point of the flat prototype.
    c.rect(2, 5, 14, 6, EMBLEM_LIGHT)
    c.rect(2, 6, 14, 8, EMBLEM_CYAN)
    c.rect(2, 8, 14, 9, EMBLEM_CYAN_DIM)
    c.rect(3, 10, 13, 11, EMBLEM_PURPLE)
    c.rect(3, 11, 13, 12, EMBLEM_PURPLE_DIM)
    c.rect(4, 13, 12, 14, EMBLEM_CYAN_DIM)
    return c


def emblem_plains():
    c = emblem_base()
    # A low rolling horizon with one dandelion accent.
    for x in range(2, 14):
        y = 8 - round(1.4 * math.sin((x - 2) / 11 * math.tau))
        c.set(x, y, EMBLEM_LIGHT)
        c.rect(x, y + 1, x + 1, 13, EMBLEM_CYAN_DIM)
    c.rect(2, 12, 14, 13, EMBLEM_CYAN)
    c.set(11, 5, EMBLEM_PURPLE)
    c.set(11, 6, EMBLEM_PURPLE_DIM)
    return c


def emblem_cherry_grove():
    c = emblem_base()
    # A highland profile with a blossom canopy.
    for x in range(2, 14):
        y = 11 - round(3.0 * math.exp(-(((x - 8) / 4.5) ** 2)))
        c.set(x, y, EMBLEM_CYAN_DIM)
        c.rect(x, y + 1, x + 1, 13, EMBLEM_CYAN_DIM if x % 3 else EMBLEM_PURPLE_DIM)
    c.rect(3, 3, 13, 7, EMBLEM_PURPLE)
    c.rect(2, 4, 14, 6, EMBLEM_PURPLE)
    c.set(4, 3, EMBLEM_LIGHT)
    c.set(8, 2, EMBLEM_PURPLE)
    c.set(11, 3, EMBLEM_LIGHT)
    c.rect(7, 7, 9, 12, EMBLEM_DARK)
    c.set(7, 8, EMBLEM_CYAN)
    return c


def emblem_desert():
    c = emblem_base()
    # Sun over two dune ridges.
    c.rect(10, 2, 14, 6, EMBLEM_PURPLE)
    c.rect(11, 3, 13, 5, EMBLEM_LIGHT)
    for x in range(2, 14):
        y = 9 - round(2.0 * math.sin((x - 2) / 11 * math.tau))
        c.set(x, y, EMBLEM_LIGHT)
        c.rect(x, y + 1, x + 1, 13, EMBLEM_CYAN_DIM)
    c.rect(2, 11, 14, 13, EMBLEM_CYAN)
    return c


def emblem_snowy_plains():
    c = emblem_base()
    # Snowflake over a drift.
    c.rect(7, 1, 9, 10, EMBLEM_LIGHT)
    c.rect(3, 5, 13, 6, EMBLEM_LIGHT)
    for dx, dy in ((1, 1), (-1, -1), (-1, 1), (1, -1)):
        for step in range(1, 3):
            c.set(8 + dx * step, 5 + dy * step, EMBLEM_CYAN)
    c.rect(2, 11, 14, 12, EMBLEM_CYAN)
    c.rect(2, 12, 14, 13, EMBLEM_CYAN_DIM)
    c.set(3, 10, EMBLEM_LIGHT)
    c.set(12, 10, EMBLEM_LIGHT)
    return c


def emblem_birch_forest():
    c = emblem_base()
    # Two white trunks with dark lenticels, under a pale canopy.
    for x in (5, 10):
        c.rect(x, 3, x + 2, 13, EMBLEM_LIGHT)
        c.set(x + 1, 6, EMBLEM_DARK)
        c.set(x, 9, EMBLEM_DARK)
        c.set(x + 1, 11, EMBLEM_DARK)
    c.rect(3, 2, 13, 4, EMBLEM_CYAN)
    c.rect(4, 4, 12, 5, EMBLEM_CYAN_DIM)
    c.rect(2, 12, 14, 13, EMBLEM_CYAN_DIM)
    return c


def emblem_savanna():
    c = emblem_base()
    # A plateau slab carrying one flat-topped acacia.
    c.rect(2, 10, 14, 11, EMBLEM_LIGHT)
    c.rect(2, 11, 14, 13, EMBLEM_CYAN_DIM)
    c.rect(7, 5, 9, 10, EMBLEM_PURPLE_DIM)
    c.rect(4, 3, 13, 5, EMBLEM_PURPLE)
    c.rect(5, 2, 12, 3, EMBLEM_PURPLE)
    c.set(8, 2, EMBLEM_LIGHT)
    return c


def emblem_sky_islands():
    c = emblem_base()
    # A floating isle: grass cap, tapering underside, and a small satellite.
    c.rect(3, 4, 13, 6, EMBLEM_CYAN)
    c.rect(3, 5, 13, 7, EMBLEM_LIGHT)
    for step in range(4):
        c.rect(4 + step, 7 + step, 12 - step, 8 + step, EMBLEM_CYAN_DIM if step % 2 else EMBLEM_PURPLE_DIM)
    c.set(8, 10, EMBLEM_PURPLE)
    c.rect(11, 11, 14, 12, EMBLEM_CYAN)
    c.set(12, 13, EMBLEM_CYAN_DIM)
    return c


ENVIRONMENT_EMBLEMS = {
    "superflat": emblem_superflat,
    "plains": emblem_plains,
    "cherry_grove": emblem_cherry_grove,
    "desert": emblem_desert,
    "snowy_plains": emblem_snowy_plains,
    "birch_forest": emblem_birch_forest,
    "savanna": emblem_savanna,
    "sky_islands": emblem_sky_islands,
}


gateway_stone().save(ROOT / "block/personal_space_stone.png")
portal_core().save(ROOT / "block/personal_space_core.png")
return_stone().save(ROOT / "block/personal_space_return.png")
panel().save(ROOT / "gui/personal_space_panel.png")
for environment, draw in ENVIRONMENT_EMBLEMS.items():
    draw().save(ROOT / f"gui/environment_{environment}.png")
print("Wrote private-space pixel textures under", ROOT)


def portal_atlas(return_gate=False):
    c = Canvas(64, 64, (83, 75, 89, 255))
    stone = [(111, 103, 117, 255), (126, 117, 129, 255), (142, 132, 143, 255), (158, 148, 154, 255)]
    for y in range(64):
        for x in range(64):
            c.set(x, y, stone[((x * 13 + y * 7) ^ (x // 4) ^ (y // 3)) % 4])
    # Quiet masonry seams and highlights, with separate trim swatches.
    for y in range(0, 16, 5):
        c.rect(0, y, 16, y+1, (85, 79, 98, 255))
        c.rect(0, y+1, 16, y+2, (170, 156, 164, 255))
    c.rect(16, 0, 32, 16, (81, 53, 131, 255))
    for y in range(16):
        for x in range(16, 32):
            c.set(x, y, [(93, 62, 146, 255), (128, 86, 171, 255), (161, 121, 203, 255)][(x+y//2)%3])
    c.border(16, 0, 32, 16, (197, 160, 225, 255))
    c.rect(32, 0, 48, 16, (24, 69, 92, 255) if return_gate else (51, 33, 92, 255))
    for y in range(1, 15):
        for x in range(33, 47):
            wave = (x+y*2)%7
            palette = [(31, 91, 115, 255), (46, 136, 153, 255), (102, 212, 198, 255)] if return_gate else [(71, 47, 139, 255), (109, 77, 188, 255), (162, 126, 231, 255)]
            c.set(x, y, palette[0 if wave<4 else 1 if wave<6 else 2])
    c.border(33, 1, 47, 15, (172, 238, 219, 255) if return_gate else (217, 176, 248, 255))
    c.rect(0, 16, 16, 32, (70, 65, 83, 255))
    for y in range(17, 32, 4): c.rect(0, y, 16, y+1, (112, 104, 124, 255))
    return c

portal_atlas().save(ROOT / 'block/personal_space_gate_atlas.png')
portal_atlas(True).save(ROOT / 'block/personal_space_return_atlas.png')
