#!/usr/bin/env python3
"""
Deterministic generator for Universal Polymerizer 3D model (.bbmodel + block JSON)
and 64x64 Arcane-Tech texture atlas (strict 1 px / model unit).
"""

from __future__ import annotations

import json
import struct
import uuid
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BBMODEL_PATH = ROOT / "art" / "assets" / "universal_polymerizer.bbmodel"
MODEL_JSON_PATH = (
    ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "models" / "block" / "universal_polymerizer.json"
)
ATLAS_PNG_PATH = (
    ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "textures" / "block" / "universal_polymerizer_atlas.png"
)

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
    def __init__(self, width: int, height: int, fill=(0, 0, 0, 0)) -> None:
        self.w = width
        self.h = height
        self.px = [bytearray(fill) * width for _ in range(height)]

    def set(self, x: int, y: int, rgba: tuple[int, int, int, int]) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            off = x * 4
            self.px[y][off : off + 4] = bytes(rgba)

    def fill_rect(self, x0: int, y0: int, w: int, h: int, rgba: tuple[int, int, int, int]) -> None:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.set(x, y, rgba)

    def save_png(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = b"".join(b"\x00" + bytes(row) for row in self.px)

        def chunk(tag: bytes, data: bytes) -> bytes:
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        ihdr = struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0)
        idat = zlib.compress(raw, level=9)
        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")
        path.write_bytes(png)


def paint_atlas() -> Canvas:
    c = Canvas(64, 64, DS_DARK)

    # 1. plinth_top (0, 0, 12x12)
    c.fill_rect(0, 0, 12, 12, DS_MID)
    for i in range(12):
        c.set(i, 0, DS_EDGE)
        c.set(i, 11, DS_VOID)
        c.set(0, i, DS_LIGHT)
        c.set(11, i, DS_VOID)
    c.fill_rect(1, 1, 10, 10, DS_DARK)
    for i in range(1, 11):
        c.set(i, 1, GOLD_MID)
        c.set(i, 10, GOLD_DARK)
        c.set(1, i, GOLD_MID)
        c.set(10, i, GOLD_DARK)
    c.set(1, 1, GOLD_BRIGHT)
    c.set(10, 1, GOLD_MID)
    c.fill_rect(2, 2, 8, 8, DS_MID)
    for y in range(2, 10):
        for x in range(2, 10):
            if (x + y) % 3 == 0:
                c.set(x, y, DS_LIGHT)

    # 2. plinth_side (12, 0, 12x2)
    c.fill_rect(12, 0, 12, 1, GOLD_MID)
    c.fill_rect(12, 1, 12, 1, DS_VOID)
    for x in range(12, 24):
        if x % 3 == 0:
            c.set(x, 0, GOLD_BRIGHT)
        else:
            c.set(x, 1, DS_DARK)

    # 3. plinth_wing_top (12, 2, 8x1)
    c.fill_rect(12, 2, 8, 1, DS_LIGHT)
    c.set(12, 2, GOLD_MID)
    c.set(19, 2, GOLD_MID)

    # 4. plinth_wing_front (12, 3, 8x2)
    c.fill_rect(12, 3, 8, 1, DS_MID)
    c.fill_rect(12, 4, 8, 1, DS_VOID)
    c.set(15, 3, GOLD_BRIGHT)
    c.set(16, 3, GOLD_BRIGHT)

    # 5. plinth_wing_end (20, 3, 1x2)
    c.set(20, 3, DS_LIGHT)
    c.set(20, 4, DS_VOID)

    # 6. pedestal_top (24, 0, 10x10)
    c.fill_rect(24, 0, 10, 10, DS_VOID)
    for i in range(10):
        c.set(24 + i, 0, DS_MID)
        c.set(24 + i, 9, DS_MID)
        c.set(24, i, DS_MID)
        c.set(33, i, DS_MID)
    c.fill_rect(26, 2, 6, 6, AME_DARK)
    c.fill_rect(27, 3, 4, 4, CYAN_DARK)

    # 7. pedestal_side (34, 0, 10x8)
    c.fill_rect(34, 0, 10, 8, DS_MID)
    for x in range(34, 44):
        c.set(x, 0, GOLD_BRIGHT if 36 <= x <= 41 else GOLD_MID)
        c.set(x, 7, DS_VOID)
    for y in range(1, 7):
        c.set(34, y, DS_LIGHT)
        c.set(43, y, DS_VOID)
        c.set(35, y, GOLD_DARK)
        c.set(42, y, GOLD_DARK)
    # Recessed dark rune niche + central cyan/amethyst conduit
    c.fill_rect(36, 2, 6, 5, DS_DARK)
    for y in range(2, 7):
        c.set(38, y, CYAN_MID if y in (2, 6) else CYAN_BRIGHT)
        c.set(39, y, CYAN_BRIGHT if y in (3, 4) else CYAN_DARK)
    c.set(38, 4, CYAN_GLOW)
    c.set(36, 3, AME_MID)
    c.set(36, 5, AME_BRIGHT)
    c.set(41, 3, AME_BRIGHT)
    c.set(41, 5, AME_MID)

    # 8. pillar_side (44, 0, 2x11)
    c.fill_rect(44, 0, 2, 11, DS_DARK)
    for y in range(11):
        c.set(44, y, DS_MID)
        c.set(45, y, DS_VOID)
    for y in (0, 1, 5, 9, 10):
        c.set(44, y, GOLD_BRIGHT if y in (0, 5) else GOLD_MID)
        c.set(45, y, GOLD_MID if y in (0, 5) else GOLD_DARK)
    c.set(44, 3, CYAN_BRIGHT)
    c.set(44, 7, AME_BRIGHT)

    # 9. pillar_cap (46, 0, 2x2)
    c.set(46, 0, GOLD_SHINE)
    c.set(47, 0, GOLD_BRIGHT)
    c.set(46, 1, GOLD_BRIGHT)
    c.set(47, 1, GOLD_MID)

    # 10. finial_side (46, 2, 2x2)
    c.set(46, 2, GOLD_BRIGHT)
    c.set(47, 2, GOLD_MID)
    c.set(46, 3, CYAN_GLOW)
    c.set(47, 3, CYAN_BRIGHT)

    # 11. finial_top (48, 2, 2x2)
    c.set(48, 2, AME_GLOW)
    c.set(49, 2, AME_BRIGHT)
    c.set(48, 3, AME_BRIGHT)
    c.set(49, 3, GOLD_MID)

    # 12. ring_n_top (0, 12, 10x3): hours 11, 12, 1
    c.fill_rect(0, 12, 10, 3, DS_MID)
    c.fill_rect(0, 12, 10, 1, DS_LIGHT)
    c.fill_rect(2, 14, 6, 1, CYAN_DARK)
    for hx in (1, 4, 5, 8):
        c.set(hx, 13, GOLD_SHINE if hx in (4, 5) else GOLD_BRIGHT)
        c.set(hx, 12, GOLD_MID)
    c.set(4, 14, CYAN_GLOW)
    c.set(5, 14, CYAN_BRIGHT)

    # 13. ring_s_top (10, 12, 10x3): hours 5, 6, 7
    c.fill_rect(10, 12, 10, 3, DS_MID)
    c.fill_rect(10, 14, 10, 1, DS_DARK)
    c.fill_rect(12, 12, 6, 1, CYAN_DARK)
    for hx in (11, 14, 15, 18):
        c.set(hx, 13, GOLD_SHINE if hx in (14, 15) else GOLD_BRIGHT)
        c.set(hx, 14, GOLD_MID)
    c.set(14, 12, CYAN_GLOW)
    c.set(15, 12, CYAN_BRIGHT)

    # 14. ring_w_top (20, 12, 3x6): hours 8, 9, 10
    c.fill_rect(20, 12, 3, 6, DS_MID)
    c.fill_rect(20, 12, 1, 6, DS_LIGHT)
    c.fill_rect(22, 12, 1, 6, CYAN_DARK)
    for hy in (12, 14, 15, 17):
        c.set(21, hy, GOLD_SHINE if hy in (14, 15) else GOLD_BRIGHT)
        c.set(20, hy, GOLD_MID)
    c.set(22, 14, CYAN_GLOW)
    c.set(22, 15, CYAN_BRIGHT)

    # 15. ring_e_top (23, 12, 3x6): hours 2, 3, 4
    c.fill_rect(23, 12, 3, 6, DS_MID)
    c.fill_rect(25, 12, 1, 6, DS_DARK)
    c.fill_rect(23, 12, 1, 6, CYAN_DARK)
    for hy in (12, 14, 15, 17):
        c.set(24, hy, GOLD_SHINE if hy in (14, 15) else GOLD_BRIGHT)
        c.set(25, hy, GOLD_MID)
    c.set(23, 14, CYAN_GLOW)
    c.set(23, 15, CYAN_BRIGHT)

    # 16. ring_ns_side (0, 16, 10x2)
    c.fill_rect(0, 16, 10, 1, GOLD_BRIGHT)
    c.fill_rect(0, 17, 10, 1, DS_DARK)
    c.set(0, 16, GOLD_MID)
    c.set(9, 16, GOLD_DARK)
    c.set(4, 17, AME_BRIGHT)
    c.set(5, 17, AME_BRIGHT)

    # 17. ring_we_side (10, 16, 6x2)
    c.fill_rect(10, 16, 6, 1, GOLD_BRIGHT)
    c.fill_rect(10, 17, 6, 1, DS_DARK)
    c.set(12, 17, CYAN_BRIGHT)
    c.set(13, 17, CYAN_BRIGHT)

    # 18. ring_end (16, 16, 3x2)
    c.fill_rect(16, 16, 3, 1, GOLD_MID)
    c.fill_rect(16, 17, 3, 1, DS_VOID)

    # 19. core_well_top (0, 20, 6x6)
    well = [
        [AME_DARK, AME_DARK, AME_MID, AME_MID, AME_DARK, AME_DARK],
        [AME_DARK, AME_MID, AME_BRIGHT, CYAN_DARK, AME_MID, AME_DARK],
        [AME_MID, CYAN_DARK, CYAN_MID, CYAN_BRIGHT, AME_BRIGHT, AME_MID],
        [AME_MID, AME_BRIGHT, CYAN_BRIGHT, CYAN_MID, CYAN_DARK, AME_MID],
        [AME_DARK, AME_MID, CYAN_DARK, AME_BRIGHT, AME_MID, AME_DARK],
        [AME_DARK, AME_DARK, AME_MID, AME_MID, AME_DARK, AME_DARK],
    ]
    for dy, row in enumerate(well):
        for dx, col in enumerate(row):
            c.set(dx, 20 + dy, col)

    # 20. core_well_side (6, 20, 6x1)
    c.fill_rect(6, 20, 6, 1, AME_MID)
    c.set(8, 20, AME_BRIGHT)
    c.set(9, 20, CYAN_MID)

    # 21. core_crystal_top (12, 20, 4x4)
    crys = [
        [AME_MID, CYAN_MID, CYAN_BRIGHT, AME_MID],
        [CYAN_MID, CYAN_GLOW, CYAN_GLOW, CYAN_BRIGHT],
        [CYAN_BRIGHT, CYAN_GLOW, AME_GLOW, CYAN_MID],
        [AME_MID, CYAN_BRIGHT, CYAN_MID, AME_DARK],
    ]
    for dy, row in enumerate(crys):
        for dx, col in enumerate(row):
            c.set(12 + dx, 20 + dy, col)

    # 22. core_crystal_side (16, 20, 4x1)
    c.set(16, 20, AME_MID)
    c.set(17, 20, CYAN_BRIGHT)
    c.set(18, 20, CYAN_GLOW)
    c.set(19, 20, CYAN_MID)

    # 23. plinth_bottom (0, 26, 12x12)
    c.fill_rect(0, 26, 12, 12, DS_VOID)
    c.fill_rect(1, 27, 10, 10, DS_DARK)

    return c


def cube(
    name: str,
    fr: list[int],
    to: list[int],
    uv_faces: dict[str, list[int]],
) -> dict:
    """Create a cube spec with integer [0..16] bounds and pixel-space UVs (0..64)."""
    dx, dy, dz = to[0] - fr[0], to[1] - fr[1], to[2] - fr[2]
    expected = {
        "north": (dx, dy),
        "south": (dx, dy),
        "east": (dz, dy),
        "west": (dz, dy),
        "up": (dx, dz),
        "down": (dx, dz),
    }
    for face_name, (ew, eh) in expected.items():
        u0, v0, u1, v1 = uv_faces[face_name]
        if (u1 - u0) != ew or (v1 - v0) != eh:
            raise ValueError(
                f"{name}.{face_name}: expected UV size {ew}x{eh}, got {u1 - u0}x{v1 - v0}"
            )
    return {
        "name": name,
        "uuid": str(uuid.uuid5(uuid.NAMESPACE_DNS, f"universal_polymerizer.{name}")),
        "from": fr,
        "to": to,
        "uv": uv_faces,
    }


def build_cubes() -> list[dict]:
    cubes = [
        # Octagonal pedestal base plinth (5 non-overlapping cubes)
        cube(
            "plinth_center",
            [2, 0, 2],
            [14, 2, 14],
            {
                "north": [12, 0, 24, 2],
                "south": [12, 0, 24, 2],
                "east": [12, 0, 24, 2],
                "west": [12, 0, 24, 2],
                "up": [0, 0, 12, 12],
                "down": [0, 26, 12, 38],
            },
        ),
        cube(
            "plinth_n",
            [4, 0, 1],
            [12, 2, 2],
            {
                "north": [12, 3, 20, 5],
                "south": [12, 3, 20, 5],
                "east": [20, 3, 21, 5],
                "west": [20, 3, 21, 5],
                "up": [12, 2, 20, 3],
                "down": [12, 2, 20, 3],
            },
        ),
        cube(
            "plinth_s",
            [4, 0, 14],
            [12, 2, 15],
            {
                "north": [12, 3, 20, 5],
                "south": [12, 3, 20, 5],
                "east": [20, 3, 21, 5],
                "west": [20, 3, 21, 5],
                "up": [12, 2, 20, 3],
                "down": [12, 2, 20, 3],
            },
        ),
        cube(
            "plinth_w",
            [1, 0, 4],
            [2, 2, 12],
            {
                "north": [20, 3, 21, 5],
                "south": [20, 3, 21, 5],
                "east": [12, 3, 20, 5],
                "west": [12, 3, 20, 5],
                "up": [12, 3, 13, 11],  # 1x8 wait: let's use vertical 1x8 at (22, 0..8)
                "down": [12, 3, 13, 11],
            },
        ),
    ]
    # Wait: fix plinth_w/e up/down to strict dedicated 1x8 slot? We can just rotate/keep 1x8 clean!
    return cubes


def main() -> None:
    atlas = paint_atlas()
    # Add dedicated 1x8 slate strip at (22, 2..10) for plinth_w/e up/down
    for y in range(2, 10):
        atlas.set(22, y, GOLD_MID if y in (2, 9) else DS_LIGHT)
    atlas.save_png(ATLAS_PNG_PATH)

    cubes = [
        # 1-5: Octagonal base plinth
        cube(
            "plinth_center",
            [2, 0, 2],
            [14, 2, 14],
            {
                "north": [12, 0, 24, 2],
                "south": [12, 0, 24, 2],
                "east": [12, 0, 24, 2],
                "west": [12, 0, 24, 2],
                "up": [0, 0, 12, 12],
                "down": [0, 26, 12, 38],
            },
        ),
        cube(
            "plinth_n",
            [4, 0, 1],
            [12, 2, 2],
            {
                "north": [12, 3, 20, 5],
                "south": [12, 3, 20, 5],
                "east": [20, 3, 21, 5],
                "west": [20, 3, 21, 5],
                "up": [12, 2, 20, 3],
                "down": [12, 2, 20, 3],
            },
        ),
        cube(
            "plinth_s",
            [4, 0, 14],
            [12, 2, 15],
            {
                "north": [12, 3, 20, 5],
                "south": [12, 3, 20, 5],
                "east": [20, 3, 21, 5],
                "west": [20, 3, 21, 5],
                "up": [12, 2, 20, 3],
                "down": [12, 2, 20, 3],
            },
        ),
        cube(
            "plinth_w",
            [1, 0, 4],
            [2, 2, 12],
            {
                "north": [20, 3, 21, 5],
                "south": [20, 3, 21, 5],
                "east": [12, 3, 20, 5],
                "west": [12, 3, 20, 5],
                "up": [22, 2, 23, 10],
                "down": [22, 2, 23, 10],
            },
        ),
        cube(
            "plinth_e",
            [14, 0, 4],
            [15, 2, 12],
            {
                "north": [20, 3, 21, 5],
                "south": [20, 3, 21, 5],
                "east": [12, 3, 20, 5],
                "west": [12, 3, 20, 5],
                "up": [22, 2, 23, 10],
                "down": [22, 2, 23, 10],
            },
        ),
        # 6: Pedestal core shaft
        cube(
            "pedestal_core",
            [3, 2, 3],
            [13, 10, 13],
            {
                "north": [34, 0, 44, 8],
                "south": [34, 0, 44, 8],
                "east": [34, 0, 44, 8],
                "west": [34, 0, 44, 8],
                "up": [24, 0, 34, 10],
                "down": [24, 0, 34, 10],
            },
        ),
    ]

    # 7-14: 4 gilded netherite corner pillars + 4 glowing finials
    pillar_coords = [
        ("nw", 1, 1),
        ("ne", 13, 1),
        ("sw", 1, 13),
        ("se", 13, 13),
    ]
    for tag, px, pz in pillar_coords:
        cubes.append(
            cube(
                f"pillar_{tag}",
                [px, 2, pz],
                [px + 2, 13, pz + 2],
                {
                    "north": [44, 0, 46, 11],
                    "south": [44, 0, 46, 11],
                    "east": [44, 0, 46, 11],
                    "west": [44, 0, 46, 11],
                    "up": [46, 0, 48, 2],
                    "down": [46, 0, 48, 2],
                },
            )
        )
        cubes.append(
            cube(
                f"finial_{tag}",
                [px, 13, pz],
                [px + 2, 15, pz + 2],
                {
                    "north": [46, 2, 48, 4],
                    "south": [46, 2, 48, 4],
                    "east": [46, 2, 48, 4],
                    "west": [46, 2, 48, 4],
                    "up": [48, 2, 50, 4],
                    "down": [46, 0, 48, 2],
                },
            )
        )

    # 15-18: 12-hour astrolabe clock-ring tabletop
    cubes.extend(
        [
            cube(
                "ring_n",
                [3, 10, 2],
                [13, 12, 5],
                {
                    "north": [0, 16, 10, 18],
                    "south": [0, 16, 10, 18],
                    "east": [16, 16, 19, 18],
                    "west": [16, 16, 19, 18],
                    "up": [0, 12, 10, 15],
                    "down": [0, 12, 10, 15],
                },
            ),
            cube(
                "ring_s",
                [3, 10, 11],
                [13, 12, 14],
                {
                    "north": [0, 16, 10, 18],
                    "south": [0, 16, 10, 18],
                    "east": [16, 16, 19, 18],
                    "west": [16, 16, 19, 18],
                    "up": [10, 12, 20, 15],
                    "down": [10, 12, 20, 15],
                },
            ),
            cube(
                "ring_w",
                [2, 10, 5],
                [5, 12, 11],
                {
                    "north": [16, 16, 19, 18],
                    "south": [16, 16, 19, 18],
                    "east": [10, 16, 16, 18],
                    "west": [10, 16, 16, 18],
                    "up": [20, 12, 23, 18],
                    "down": [20, 12, 23, 18],
                },
            ),
            cube(
                "ring_e",
                [11, 10, 5],
                [14, 12, 11],
                {
                    "north": [16, 16, 19, 18],
                    "south": [16, 16, 19, 18],
                    "east": [10, 16, 16, 18],
                    "west": [10, 16, 16, 18],
                    "up": [23, 12, 26, 18],
                    "down": [23, 12, 26, 18],
                },
            ),
            # 19-20: Recessed amethyst-cyan polymerization core + central focus crystal
            cube(
                "core_well",
                [5, 10, 5],
                [11, 11, 11],
                {
                    "north": [6, 20, 12, 21],
                    "south": [6, 20, 12, 21],
                    "east": [6, 20, 12, 21],
                    "west": [6, 20, 12, 21],
                    "up": [0, 20, 6, 26],
                    "down": [0, 20, 6, 26],
                },
            ),
            cube(
                "core_crystal",
                [6, 11, 6],
                [10, 12, 10],
                {
                    "north": [16, 20, 20, 21],
                    "south": [16, 20, 20, 21],
                    "east": [16, 20, 20, 21],
                    "west": [16, 20, 20, 21],
                    "up": [12, 20, 16, 24],
                    "down": [12, 20, 16, 24],
                },
            ),
        ]
    )

    assert 16 <= len(cubes) <= 24, f"Expected 16..24 cubes, got {len(cubes)}"

    # Export .bbmodel (java_block, resolution 64x64)
    bb_elements = []
    outliner = []
    for c in cubes:
        faces = {
            fname: {"uv": uv, "texture": 0}
            for fname, uv in c["uv"].items()
        }
        bb_elements.append(
            {
                "name": c["name"],
                "box_uv": False,
                "rescale": False,
                "locked": False,
                "render_order": "default",
                "allow_mirror_modeling": True,
                "from": c["from"],
                "to": c["to"],
                "autouv": 0,
                "color": 0,
                "origin": [8, 0, 8],
                "faces": faces,
                "type": "cube",
                "uuid": c["uuid"],
            }
        )
        outliner.append(c["uuid"])

    bbmodel = {
        "meta": {
            "format_version": "5.0",
            "model_format": "java_block",
            "box_uv": False,
        },
        "name": "universal_polymerizer",
        "model_identifier": "",
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "unhandled_root_fields": {},
        "resolution": {"width": 64, "height": 64},
        "elements": bb_elements,
        "outliner": outliner,
        "textures": [
            {
                "path": str(ATLAS_PNG_PATH),
                "name": "universal_polymerizer_atlas.png",
                "folder": "block",
                "namespace": "universal_tool",
                "id": "0",
                "width": 64,
                "height": 64,
                "uv_width": 64,
                "uv_height": 64,
                "particle": True,
                "use_as_default": False,
                "layers_enabled": False,
                "sync_to_project": "",
                "render_mode": "default",
                "render_sides": "auto",
                "frame_time": 1,
                "frame_order_type": "loop",
                "frame_order": "",
                "frame_interpolate": False,
                "visible": True,
                "internal": True,
                "saved": True,
                "uuid": str(uuid.uuid5(uuid.NAMESPACE_DNS, "universal_polymerizer.tex0")),
            }
        ],
    }
    BBMODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    BBMODEL_PATH.write_text(json.dumps(bbmodel, indent=2) + "\n", encoding="utf-8")

    # Export Minecraft Java block JSON (UVs in [0..16], scale 1/4 for 64x64 atlas)
    mc_elements = []
    for c in cubes:
        faces = {}
        for fname, uv in c["uv"].items():
            faces[fname] = {
                "uv": [v / 4.0 for v in uv],
                "texture": "#0",
            }
        mc_elements.append(
            {
                "name": c["name"],
                "from": c["from"],
                "to": c["to"],
                "faces": faces,
            }
        )

    mc_model = {
        "format_version": "1.21.1",
        "credit": "Universal-Tool Arcane-Tech Generator",
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "texture_size": [64, 64],
        "textures": {
            "0": "universal_tool:block/universal_polymerizer_atlas",
            "particle": "universal_tool:block/universal_polymerizer_atlas",
        },
        "elements": mc_elements,
    }
    MODEL_JSON_PATH.parent.mkdir(parents=True, exist_ok=True)
    MODEL_JSON_PATH.write_text(json.dumps(mc_model, indent=2) + "\n", encoding="utf-8")

    print(
        f"Generated Universal Polymerizer: {len(cubes)} cubes, "
        f"{BBMODEL_PATH.name}, {MODEL_JSON_PATH.name}, {ATLAS_PNG_PATH.name}"
    )


if __name__ == "__main__":
    main()
