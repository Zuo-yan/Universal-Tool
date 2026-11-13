#!/usr/bin/env python3
"""
Dedicated 64x64 crisp pixel-art generator and validator for the 6 Universal Tools,
their upright/handheld item model JSONs, and the Klein Bottle, matching the user's
two reference sprites.
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Optional, Tuple

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
TEX_DIR = ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "textures" / "item"
MODEL_DIR = ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "models" / "item"
SCRATCH_PREVIEW = ROOT / "scratch" / "ref_items_preview.png"

RGBA = Tuple[int, int, int, int]
RGB = Tuple[int, int, int]

SIZE = 64
OUTLINE_DARK: RGBA = (42, 45, 54, 255)  # #2A2D36 rich dark charcoal (never #000000)


def rgba(hex_str: str, alpha: int = 255) -> RGBA:
    s = hex_str.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), alpha)


def lerp_rgba(c1: RGBA, c2: RGBA, t: float) -> RGBA:
    t = max(0.0, min(1.0, t))
    return (
        int(round(c1[0] + (c2[0] - c1[0]) * t)),
        int(round(c1[1] + (c2[1] - c1[1]) * t)),
        int(round(c1[2] + (c2[2] - c1[2]) * t)),
        255,
    )


class PixelCanvas:
    def __init__(self, width: int = SIZE, height: int = SIZE) -> None:
        self.w = width
        self.h = height
        self.pixels: List[List[RGBA]] = [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]

    def in_bounds(self, x: int, y: int) -> bool:
        return 0 <= x < self.w and 0 <= y < self.h

    def get(self, x: int, y: int) -> RGBA:
        if not self.in_bounds(x, y):
            return (0, 0, 0, 0)
        return self.pixels[y][x]

    def set(self, x: int, y: int, color: RGBA) -> None:
        if self.in_bounds(x, y):
            self.pixels[y][x] = color

    def is_opaque(self, x: int, y: int) -> bool:
        return self.get(x, y)[3] > 0

    def fill_rect(self, x0: int, y0: int, x1: int, y1: int, color: RGBA) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, color)

    def add_outline(self, outline_color: RGBA = OUTLINE_DARK, mask_fn: Optional[Callable[[int, int], bool]] = None) -> None:
        """Add a crisp 1px 4-connected outline around all currently opaque pixels."""
        targets: List[Tuple[int, int]] = []
        for y in range(self.h):
            for x in range(self.w):
                if self.is_opaque(x, y):
                    continue
                if mask_fn is not None and not mask_fn(x, y):
                    continue
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    if self.is_opaque(x + dx, y + dy):
                        targets.append((x, y))
                        break
        for x, y in targets:
            self.set(x, y, outline_color)

    def to_image(self) -> Image.Image:
        img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        flat = [px for row in self.pixels for px in row]
        img.putdata(flat)
        return img


# ---------------------------------------------------------------------------
# Tier Palettes for Universal Tools
# ---------------------------------------------------------------------------
TIER_PALETTES: Dict[str, Dict[str, object]] = {
    "diamond": {
        "outline": rgba("#2A2D36"),
        # Reference shows faceted silver/steel shovel & axe paired with radiant cyan diamond pick/sword/gems
        "metal_hi": rgba("#E2E6F0"),
        "metal_lt": rgba("#C4C9D6"),
        "metal_mid": rgba("#969CA9"),
        "metal_dk": rgba("#707684"),
        "metal_sh": rgba("#525866"),
        # Pickaxe & Sword diamond crystal
        "blade_spec": rgba("#D8FFFF"),
        "blade_hi": rgba("#78FAFF"),
        "blade_mid": rgba("#22DCEB"),
        "blade_dk": rgba("#129EB0"),
        "blade_sh": rgba("#0B6A7A"),
        # Gems
        "gem_spec": rgba("#E6FFFF"),
        "gem_hi": rgba("#6EFAFF"),
        "gem_mid": rgba("#1CE4F2"),
        "gem_dk": rgba("#0F9DB0"),
        # Hardware & Grip
        "iron_hi": rgba("#626878"),
        "iron_mid": rgba("#464B58"),
        "iron_dk": rgba("#323640"),
        "brass_hi": rgba("#E6AE56"),
        "brass_mid": rgba("#BE8032"),
        "brass_dk": rgba("#85541C"),
        "grip_hi": rgba("#9A633A"),
        "grip_mid": rgba("#764827"),
        "grip_dk": rgba("#543118"),
        "grip_gr": rgba("#3D2210"),
    },
    "wooden": {
        "outline": rgba("#2C221B"),
        "metal_hi": rgba("#C4945A"),
        "metal_lt": rgba("#AC7D48"),
        "metal_mid": rgba("#8E6134"),
        "metal_dk": rgba("#6E4824"),
        "metal_sh": rgba("#543518"),
        "blade_spec": rgba("#D4A66A"),
        "blade_hi": rgba("#B6854C"),
        "blade_mid": rgba("#926434"),
        "blade_dk": rgba("#6E4822"),
        "blade_sh": rgba("#4E3114"),
        "gem_spec": rgba("#FFF3BA"),
        "gem_hi": rgba("#FFC64E"),
        "gem_mid": rgba("#E08E22"),
        "gem_dk": rgba("#9C5810"),
        "iron_hi": rgba("#746252"),
        "iron_mid": rgba("#54463A"),
        "iron_dk": rgba("#3C3128"),
        "brass_hi": rgba("#DDA852"),
        "brass_mid": rgba("#B47E30"),
        "brass_dk": rgba("#7C521C"),
        "grip_hi": rgba("#8A5A38"),
        "grip_mid": rgba("#684124"),
        "grip_dk": rgba("#4A2C17"),
        "grip_gr": rgba("#361E0F"),
    },
    "stone": {
        "outline": rgba("#272A30"),
        "metal_hi": rgba("#C2C5CC"),
        "metal_lt": rgba("#A4A8B0"),
        "metal_mid": rgba("#82868E"),
        "metal_dk": rgba("#646870"),
        "metal_sh": rgba("#4B4F56"),
        "blade_spec": rgba("#D0D3DA"),
        "blade_hi": rgba("#A8ACB5"),
        "blade_mid": rgba("#80848E"),
        "blade_dk": rgba("#5E626B"),
        "blade_sh": rgba("#444850"),
        "gem_spec": rgba("#D2FFE0"),
        "gem_hi": rgba("#54EB88"),
        "gem_mid": rgba("#1EB854"),
        "gem_dk": rgba("#117835"),
        "iron_hi": rgba("#626670"),
        "iron_mid": rgba("#484C54"),
        "iron_dk": rgba("#34373E"),
        "brass_hi": rgba("#B89862"),
        "brass_mid": rgba("#8E7244"),
        "brass_dk": rgba("#644E2C"),
        "grip_hi": rgba("#8F5D3A"),
        "grip_mid": rgba("#6E4325"),
        "grip_dk": rgba("#4E2E18"),
        "grip_gr": rgba("#39200F"),
    },
    "iron": {
        "outline": rgba("#282C36"),
        "metal_hi": rgba("#EEF1F7"),
        "metal_lt": rgba("#D2D7E2"),
        "metal_mid": rgba("#A4AAB8"),
        "metal_dk": rgba("#7C8392"),
        "metal_sh": rgba("#5A606E"),
        "blade_spec": rgba("#F5F8FC"),
        "blade_hi": rgba("#D8DEE8"),
        "blade_mid": rgba("#AEB6C4"),
        "blade_dk": rgba("#808898"),
        "blade_sh": rgba("#5B6270"),
        "gem_spec": rgba("#D8FFF2"),
        "gem_hi": rgba("#60F0BE"),
        "gem_mid": rgba("#24B888"),
        "gem_dk": rgba("#137858"),
        "iron_hi": rgba("#6E7484"),
        "iron_mid": rgba("#4E5462"),
        "iron_dk": rgba("#373B46"),
        "brass_hi": rgba("#E2AE56"),
        "brass_mid": rgba("#B88032"),
        "brass_dk": rgba("#80531C"),
        "grip_hi": rgba("#966038"),
        "grip_mid": rgba("#724525"),
        "grip_dk": rgba("#502E17"),
        "grip_gr": rgba("#3B200F"),
    },
    "golden": {
        "outline": rgba("#332318"),
        "metal_hi": rgba("#FFF29E"),
        "metal_lt": rgba("#FAD65A"),
        "metal_mid": rgba("#DFAA28"),
        "metal_dk": rgba("#B27C16"),
        "metal_sh": rgba("#84560C"),
        "blade_spec": rgba("#FFF7BA"),
        "blade_hi": rgba("#FFE066"),
        "blade_mid": rgba("#E8B22A"),
        "blade_dk": rgba("#B67E14"),
        "blade_sh": rgba("#82540A"),
        "gem_spec": rgba("#FFD8E2"),
        "gem_hi": rgba("#FF5278"),
        "gem_mid": rgba("#D61A44"),
        "gem_dk": rgba("#8C0E2A"),
        "iron_hi": rgba("#7A6454"),
        "iron_mid": rgba("#58463B"),
        "iron_dk": rgba("#3D3028"),
        "brass_hi": rgba("#FFE074"),
        "brass_mid": rgba("#D89E28"),
        "brass_dk": rgba("#966412"),
        "grip_hi": rgba("#9A5B36"),
        "grip_mid": rgba("#744022"),
        "grip_dk": rgba("#522B16"),
        "grip_gr": rgba("#3B1E0E"),
    },
    "netherite": {
        "outline": rgba("#221E28"),
        "metal_hi": rgba("#847C8C"),
        "metal_lt": rgba("#6B6474"),
        "metal_mid": rgba("#524B5A"),
        "metal_dk": rgba("#3E3845"),
        "metal_sh": rgba("#2E2934"),
        "blade_spec": rgba("#B09AC4"),
        "blade_hi": rgba("#846E96"),
        "blade_mid": rgba("#5E4D6E"),
        "blade_dk": rgba("#443750"),
        "blade_sh": rgba("#30263A"),
        "gem_spec": rgba("#FFE0FF"),
        "gem_hi": rgba("#E86EFF"),
        "gem_mid": rgba("#B02CE6"),
        "gem_dk": rgba("#6C1498"),
        "iron_hi": rgba("#585262"),
        "iron_mid": rgba("#403B48"),
        "iron_dk": rgba("#2E2A34"),
        "brass_hi": rgba("#F0AC4E"),
        "brass_mid": rgba("#C47828"),
        "brass_dk": rgba("#884A14"),
        "grip_hi": rgba("#744634"),
        "grip_mid": rgba("#543022"),
        "grip_dk": rgba("#3B2016"),
        "grip_gr": rgba("#2A160E"),
    },
}


# ---------------------------------------------------------------------------
# Universal Tool 64x64 Builder (matches media_1794566626550_11025a96.jpg)
# ---------------------------------------------------------------------------
def build_universal_tool(tier: str) -> Image.Image:
    p = TIER_PALETTES[tier]
    c = PixelCanvas(SIZE, SIZE)

    outline: RGBA = p["outline"]  # type: ignore[assignment]
    m_hi: RGBA = p["metal_hi"]  # type: ignore[assignment]
    m_lt: RGBA = p["metal_lt"]  # type: ignore[assignment]
    m_mid: RGBA = p["metal_mid"]  # type: ignore[assignment]
    m_dk: RGBA = p["metal_dk"]  # type: ignore[assignment]
    m_sh: RGBA = p["metal_sh"]  # type: ignore[assignment]

    b_spec: RGBA = p["blade_spec"]  # type: ignore[assignment]
    b_hi: RGBA = p["blade_hi"]  # type: ignore[assignment]
    b_mid: RGBA = p["blade_mid"]  # type: ignore[assignment]
    b_dk: RGBA = p["blade_dk"]  # type: ignore[assignment]
    b_sh: RGBA = p["blade_sh"]  # type: ignore[assignment]

    g_spec: RGBA = p["gem_spec"]  # type: ignore[assignment]
    g_hi: RGBA = p["gem_hi"]  # type: ignore[assignment]
    g_mid: RGBA = p["gem_mid"]  # type: ignore[assignment]
    g_dk: RGBA = p["gem_dk"]  # type: ignore[assignment]

    i_hi: RGBA = p["iron_hi"]  # type: ignore[assignment]
    i_mid: RGBA = p["iron_mid"]  # type: ignore[assignment]
    i_dk: RGBA = p["iron_dk"]  # type: ignore[assignment]

    br_hi: RGBA = p["brass_hi"]  # type: ignore[assignment]
    br_mid: RGBA = p["brass_mid"]  # type: ignore[assignment]
    br_dk: RGBA = p["brass_dk"]  # type: ignore[assignment]

    gr_hi: RGBA = p["grip_hi"]  # type: ignore[assignment]
    gr_mid: RGBA = p["grip_mid"]  # type: ignore[assignment]
    gr_dk: RGBA = p["grip_dk"]  # type: ignore[assignment]
    gr_gr: RGBA = p["grip_gr"]  # type: ignore[assignment]

    def paint_gem_2x2(x0: int, y0: int) -> None:
        c.set(x0, y0, g_spec)
        c.set(x0 + 1, y0, g_hi)
        c.set(x0, y0 + 1, g_mid)
        c.set(x0 + 1, y0 + 1, g_dk)

    # 1. TOP SHOVEL SPADE (y = 3..13, sym around x = 31.5)
    # Tapered arch: rows 3..7 expand, rows 8..13 straight 10px wide (x=27..36)
    shovel_half_widths = {
        3: 1,   # x = 31..32
        4: 2,   # x = 30..33
        5: 3,   # x = 29..34
        6: 4,   # x = 28..35
        7: 5,   # x = 27..36
        8: 5,
        9: 5,
        10: 5,
        11: 5,
        12: 5,
        13: 5,
    }
    for y, hw in shovel_half_widths.items():
        x_min = 32 - hw
        x_max = 31 + hw
        for x in range(x_min, x_max + 1):
            is_left_half = x <= 31
            # Upper-left bevel & center ridge + 2x2 facet checkerboard
            checker = ((x // 2) + (y // 2)) % 2
            if y == 3 or (y <= 7 and x == x_min) or (x == x_min and y <= 11):
                col = m_hi
            elif x == x_max or y == 13:
                col = m_sh if not is_left_half else m_dk
            elif x in (31, 32) and y >= 6:
                col = m_lt if x == 31 else m_mid
            elif is_left_half:
                col = m_lt if checker == 0 else m_mid
            else:
                col = m_mid if checker == 0 else m_dk
            c.set(x, y, col)

    # 2. UPPER NECK & WOODEN SHOULDER (y = 14..16, x = 30..33)
    for y in (14, 15, 16):
        c.set(30, y, br_hi if y == 14 else gr_hi)
        c.set(31, y, br_mid if y == 14 else gr_mid)
        c.set(32, y, br_mid if y == 14 else gr_mid)
        c.set(33, y, br_dk if y == 14 else gr_dk)

    # 3. HORIZONTAL CROSS-ARMS (y = 20..23, x = 16..47)
    for y in range(20, 24):
        for x in range(16, 48):
            if y == 20:
                c.set(x, y, gr_hi)
            elif y in (21, 22):
                c.set(x, y, gr_mid)
            else:
                c.set(x, y, gr_dk)

    # Stepped shoulder brackets on top of cross-arms (like reference)
    for bx in (24, 25, 38, 39):
        c.set(bx, 18, i_hi)
        c.set(bx, 19, i_mid)

    # Left & Right brass/iron/gem collars on cross-arms
    # Left collar at x = 20..22, right collar at x = 41..43
    for cx in (20, 41):
        # Brass outer strips & dark iron bezel
        for y in range(19, 25):
            c.set(cx, y, br_hi if y < 22 else br_dk)
            c.set(cx + 2, y, br_mid if y < 22 else br_dk)
            c.set(cx + 1, y, i_hi if y in (19, 20) else i_dk)
        paint_gem_2x2(cx + 1 if cx == 20 else cx, 21)
        # Re-frame cleanly: 2px gem at (21..22, 21..22) and (41..42, 21..22)
    # Fix clean symmetric side collars:
    # Left side: socket ring at x=16..18, brass+gem collar at x=19..23
    for y in range(19, 25):
        c.set(16, y, i_hi if y < 22 else i_dk)
        c.set(17, y, i_mid if y < 22 else i_dk)
        c.set(18, y, br_hi if y < 22 else br_mid)
        c.set(19, y, br_mid if y < 22 else br_dk)
        c.set(20, y, i_hi if y < 21 else i_dk)
        c.set(21, y, i_hi if y < 21 else i_dk)
        c.set(22, y, i_mid if y < 21 else i_dk)
    paint_gem_2x2(20, 21)
    # Right side: brass+gem collar at x=40..44, socket ring at x=45..47
    for y in range(19, 25):
        c.set(41, y, i_mid if y < 21 else i_dk)
        c.set(42, y, i_hi if y < 21 else i_dk)
        c.set(43, y, i_hi if y < 21 else i_dk)
        c.set(44, y, br_mid if y < 22 else br_dk)
        c.set(45, y, br_hi if y < 22 else br_dk)
        c.set(46, y, i_mid if y < 22 else i_dk)
        c.set(47, y, i_hi if y < 22 else i_dk)
    paint_gem_2x2(42, 21)

    # 4. LEFT PICKAXE BLADE (x = 5..17, y = 13..33)
    # Anchored cleanly to socket at x=15..17, y=13..22; curves smoothly down-left to sharp tip at (6, 33)
    pick_spans = {
        13: (13, 16, True),   # dark iron top socket cap
        14: (11, 16, True),
        15: (9, 16, False),
        16: (8, 17, False),
        17: (7, 17, False),
        18: (6, 17, False),
        19: (6, 15, False),
        20: (5, 13, False),
        21: (5, 11, False),
        22: (5, 10, False),
        23: (5, 10, False),
        24: (5, 9, False),
        25: (5, 9, False),
        26: (5, 8, False),
        27: (5, 8, False),
        28: (6, 8, False),
        29: (6, 8, False),
        30: (6, 7, False),
        31: (6, 7, False),
        32: (6, 6, False),
        33: (6, 6, False),
    }
    for y, (x_out, x_in, is_cap) in pick_spans.items():
        for x in range(x_out, x_in + 1):
            if is_cap or (y <= 15 and x >= 13) or (y == 16 and x >= 15):
                c.set(x, y, i_hi if y <= 14 else i_mid)
                continue
            span = max(1.0, float(x_in - x_out))
            pos = (x - x_out) / span
            checker = (x + y) % 2
            if y >= 31:
                c.set(x, y, b_dk if y == 31 else b_sh)
            elif pos <= 0.25:
                c.set(x, y, b_spec if (y < 27 and checker == 0) else b_hi)
            elif pos <= 0.55:
                c.set(x, y, b_hi if checker == 0 else b_mid)
            elif pos <= 0.82:
                c.set(x, y, b_mid if checker == 0 else b_dk)
            else:
                c.set(x, y, b_dk if y < 28 else b_sh)

    # 5. RIGHT AXE BLADE (x = 46..58, y = 13..33)
    # Broad double-beveled bearded battle-axe crescent matching media_1794566626550_11025a96.jpg
    axe_spans = {
        13: (47, 51),  # upper flared horn
        14: (47, 53),
        15: (47, 54),
        16: (47, 55),
        17: (47, 56),
        18: (47, 57),
        19: (47, 57),
        20: (47, 58),
        21: (47, 58),
        22: (47, 58),
        23: (47, 58),
        24: (47, 58),
        25: (47, 57),
        26: (47, 57),
        27: (47, 56),
        28: (47, 55),
        29: (47, 54),
        30: (47, 53),
        31: (48, 52),
        32: (49, 51),
        33: (50, 51),  # crisp lower sweeping horn tip
    }
    # Socket neck connection at x=46..48, y=15..28
    for y in range(15, 29):
        c.set(46, y, i_hi if y < 20 else (i_mid if y < 26 else i_dk))
        c.set(47, y, i_mid if y < 21 else i_dk)
    # Upper and lower iron socket prongs at x=46..48
    for y in (13, 14):
        c.set(46, y, i_hi)
        c.set(47, y, i_mid)
    for y in (29, 30):
        c.set(46, y, i_dk)
        c.set(47, y, i_dk)

    for y, (x_in, x_out) in axe_spans.items():
        for x in range(max(48, x_in), x_out + 1):
            dist_edge = x_out - x
            checker = ((x // 2) + (y // 2)) % 2
            dither = (x + y) % 2
            if x == 48 and 16 <= y <= 27:
                c.set(x, y, i_dk if y > 22 else i_mid)
            elif dist_edge <= 1:
                # Crisp bright outer cutting bevel
                c.set(x, y, m_hi if y < 27 else m_lt)
            elif dist_edge == 2:
                c.set(x, y, m_lt if dither == 0 else m_mid)
            elif x <= 49 and (y <= 15 or y >= 28):
                c.set(x, y, m_sh)
            else:
                c.set(x, y, m_mid if checker == 0 else m_dk)

    # 6. CENTRAL DIAMOND HUB COLLAR & GLOWING CORE GEM (centered at 31.5, 21.5)
    # Painted over cross-arms so it sits cleanly in front!
    for y in range(16, 28):
        for x in range(26, 38):
            dist = abs(x - 31.5) + abs(y - 21.5)
            if dist <= 6.0:
                if dist > 4.0:
                    # Charcoal-iron beveled frame: upper-left lighter, lower-right darker
                    if (x - 31.5) + (y - 21.5) < 0:
                        c.set(x, y, i_hi)
                    elif (x - 31.5) + (y - 21.5) < 2:
                        c.set(x, y, i_mid)
                    else:
                        c.set(x, y, i_dk)
                elif dist > 3.0:
                    c.set(x, y, outline)
                else:
                    # Inner glowing core gem (dist <= 3.0 -> 6x6 diamond)
                    if (x - 31.5) + (y - 21.5) <= -1.0:
                        c.set(x, y, g_spec)
                    elif dist <= 1.5:
                        c.set(x, y, g_hi)
                    elif (x - 31.5) + (y - 21.5) <= 1.5:
                        c.set(x, y, g_mid)
                    else:
                        c.set(x, y, g_dk)

    # 7. UPPER NECK GEM + BRASS COLLAR + LEATHER-WRAPPED GRIP + LOWER IRON COLLAR (y = 27..47)
    # Upper neck jewel housing (y = 27..29, x = 30..33)
    for y in (27, 28, 29):
        c.set(30, y, i_hi if y == 27 else i_dk)
        c.set(33, y, i_mid if y == 27 else i_dk)
    paint_gem_2x2(31, 27)
    c.set(31, 29, i_mid)
    c.set(32, 29, i_dk)

    # Brass upper collar band (y = 30..31, x = 29..34)
    for x in range(29, 35):
        c.set(x, 30, br_hi if x <= 32 else br_mid)
        c.set(x, 31, br_mid if x <= 32 else br_dk)

    # Leather-wrapped diagonal-striped vertical grip (y = 32..42, x = 30..33)
    for y in range(32, 43):
        for x in range(30, 34):
            phase = (x + y) % 4
            if phase == 0:
                c.set(x, y, gr_gr)
            elif phase == 1:
                c.set(x, y, gr_hi if x <= 31 else gr_mid)
            elif phase == 2:
                c.set(x, y, gr_mid if x <= 32 else gr_dk)
            else:
                c.set(x, y, gr_dk)

    # Lower iron collar band (y = 43..44, x = 29..34)
    for x in range(29, 35):
        c.set(x, 43, m_lt if x <= 32 else m_mid)
        c.set(x, 44, m_dk if x <= 32 else m_sh)

    # Lower neck jewel housing (y = 45..47, x = 30..33)
    for y in (45, 46, 47):
        c.set(30, y, i_hi if y == 45 else i_dk)
        c.set(33, y, i_mid if y == 45 else i_dk)
    paint_gem_2x2(31, 45)
    c.set(31, 47, i_mid)
    c.set(32, 47, i_dk)

    # 8. MINIATURE CROSSGUARD WITH GLOWING SIDE STUDS (y = 47..51, x = 26..37)
    # Left & Right crossguard wings
    for x in (26, 27, 28, 29, 34, 35, 36, 37):
        c.set(x, 48, i_hi if x < 32 else i_mid)
        c.set(x, 49, i_mid if x < 32 else i_dk)
        c.set(x, 50, i_dk)
    # Glowing side studs on crossguard wings
    c.set(27, 49, g_spec)
    c.set(28, 49, g_hi)
    c.set(35, 49, g_hi)
    c.set(36, 49, g_mid)
    # Center V-chevron dipping onto sword blade
    c.set(30, 48, i_hi)
    c.set(33, 48, i_mid)
    c.set(30, 49, i_mid)
    c.set(33, 49, i_dk)
    c.set(31, 49, i_hi)
    c.set(32, 49, i_mid)
    c.set(31, 50, i_mid)
    c.set(32, 50, i_dk)
    c.set(31, 51, i_dk)
    c.set(32, 51, i_dk)

    # 9. INVERTED SWORD CRYSTAL/TIER POMMEL BLADE (y = 51..60)
    for y in range(51, 59):
        # Left bright facet, right darker facet, with diagonal crystal chevron shading
        chev = (y % 2 == 0)
        c.set(30, y, b_spec if chev else b_hi)
        c.set(31, y, b_hi if not chev else b_mid)
        c.set(32, y, b_mid if chev else b_dk)
        c.set(33, y, b_dk if not chev else b_sh)
    # Tapered tip
    c.set(31, 59, b_hi)
    c.set(32, 59, b_dk)
    c.set(31, 60, b_mid)
    c.set(32, 60, b_sh)

    # Re-apply V-chevron over top of sword blade at y=50..51 cleanly
    c.set(30, 50, i_dk)
    c.set(33, 50, i_dk)
    c.set(31, 50, i_mid)
    c.set(32, 50, i_dk)
    c.set(31, 51, i_dk)
    c.set(32, 51, i_dk)

    # 10. Crisp 1px dark charcoal outline around entire silhouette
    c.add_outline(outline)
    return c.to_image()


# ---------------------------------------------------------------------------
# Klein Bottle 64x64 Builder (matches media_1794566703054_5b3aa812.jpg)
# ---------------------------------------------------------------------------
def dist_point_to_segment(px: float, py: float, ax: float, ay: float, bx: float, by: float) -> Tuple[float, float]:
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    len2 = vx * vx + vy * vy
    if len2 <= 1e-6:
        return math.hypot(px - ax, py - ay), 0.0
    t = max(0.0, min(1.0, (wx * vx + wy * vy) / len2))
    cx, cy = ax + t * vx, ay + t * vy
    return math.hypot(px - cx, py - cy), t


def build_klein_bottle() -> Image.Image:
    c = PixelCanvas(SIZE, SIZE)

    outline = rgba("#2A2D36")
    # Aqua crystal glass palette (matching reference)
    gl_spec = rgba("#F2FFFF")
    gl_hi = rgba("#B2F2FA")
    gl_lt = rgba("#84D6E4")
    gl_mid = rgba("#5DB6C6")
    gl_dk = rgba("#3F8E9E")
    gl_sh = rgba("#2C6A78")
    gl_rim = rgba("#2E6D7C")

    # Shimmering cyan liquid pool
    liq_men = rgba("#98F4FF")
    liq_hi = rgba("#4CD0E0")
    liq_mid = rgba("#2EA6B8")
    liq_dk = rgba("#1D7A8A")

    # Dark charcoal-iron clasps & glowing cyan gems
    ir_hi = rgba("#5E6474")
    ir_mid = rgba("#464B58")
    ir_dk = rgba("#323640")
    gm_spec = rgba("#E2FFFF")
    gm_hi = rgba("#68FAFF")
    gm_mid = rgba("#1EE0F0")
    gm_dk = rgba("#0F96A8")

    # Centerline path of the looping neck (from left shoulder -> top apex -> right curve -> sealing collar -> internal tube -> liquid pool)
    loop_points: List[Tuple[float, float, float]] = [
        # (x, y, radius)
        (24.0, 27.5, 4.7),   # smoothly merges into upper-left bulb shoulder
        (23.8, 22.0, 4.4),   # left rising column (left clasp at y~15..18)
        (24.5, 16.0, 4.2),
        (28.0, 10.5, 4.1),
        (34.0, 7.2, 4.1),
        (38.0, 6.5, 4.2),    # top apex (peak diamond clasp above)
        (43.5, 7.8, 4.1),
        (48.0, 12.2, 4.1),
        (49.5, 17.5, 4.1),   # right curve (right clasp at y~15..18)
        (47.8, 22.8, 4.0),
        (43.5, 27.2, 3.9),   # slanted sealing collar sits right on the upper-right bulb shoulder!
        (37.5, 32.2, 3.7),   # internal tube diagonal inside bulb
        (31.2, 37.8, 3.6),
        (26.2, 43.5, 3.6),
        (25.2, 49.5, 3.8),   # enters bottom liquid pool vertically!
    ]

    def sample_tube(px: float, py: float, seg_start: int, seg_end: int) -> Tuple[float, float, float]:
        best_d = 999.0
        best_r = 4.0
        best_nx = 0.0
        for i in range(seg_start, seg_end):
            x0, y0, r0 = loop_points[i]
            x1, y1, r1 = loop_points[i + 1]
            d, t = dist_point_to_segment(px, py, x0, y0, x1, y1)
            r = r0 + (r1 - r0) * t
            sd = d - r
            if sd < best_d:
                best_d = sd
                best_r = r
                cx = x0 + (x1 - x0) * t
                best_nx = (px - cx) / max(0.5, r)
        return best_d, best_r, best_nx

    # Asymmetric pear-shaped bulb bounds x_left(y) .. x_right(y) for y in 23..55
    # Left wall flows smoothly from x=19.6 at y=23 -> x=11.0 at y=43 -> x=15.5 at y=54
    # Right wall flows smoothly from x=28.5 at y=23 -> slanted collar (43, 28) -> x=47.5 at y=43 -> x=43.5 at y=54
    def bulb_bounds(py: float) -> Optional[Tuple[float, float, float]]:
        if py < 23.0 or py > 56.0:
            return None
        if py <= 43.0:
            u = (py - 23.0) / 20.0  # 0 at neck-join y=23, 1 at belly y=43
            s_left = u * u * (3.0 - 2.0 * u)
            x_left = 19.6 - 8.6 * s_left
            if py <= 28.5:
                v = (py - 23.0) / 5.5
                x_right = 28.5 + 14.2 * (v ** 0.85)
            else:
                v = (py - 28.5) / 14.5
                s_right = v * (2.0 - v)  # smooth quadratic easing with zero cusp at y=43
                x_right = 42.7 + 4.8 * s_right
        else:
            u = (py - 43.0) / 12.0
            x_left = 11.0 + 4.8 * (u * u)
            x_right = 47.5 - 4.4 * (u * u)
        cx = 0.5 * (x_left + x_right)
        return x_left, x_right, cx

    def bulb_sdf(px: float, py: float) -> float:
        bounds = bulb_bounds(py)
        if bounds is None:
            return 999.0
        x_left, x_right, cx = bounds
        rx = max(1.0, 0.5 * (x_right - x_left))
        nx = (px - cx) / rx
        bottom_y = 54.6 - 2.3 * (nx * nx)
        if py > bottom_y:
            return py - bottom_y
        if px < x_left:
            return x_left - px
        if px > x_right:
            return px - x_right
        return -min(px - x_left, x_right - px, bottom_y - py)

    # First pass: render glass bulb, outer loop, internal tube, and shimmering liquid pool
    for y in range(4, 57):
        for x in range(10, 55):
            d_bulb = bulb_sdf(x, y)
            d_outer_loop, _, nx_loop = sample_tube(x, y, 0, 10)
            d_inner_tube, _, nx_tube = sample_tube(x, y, 10, len(loop_points) - 1)

            in_bulb = d_bulb <= 0.0
            in_loop = d_outer_loop <= 0.0
            if not (in_bulb or in_loop):
                continue

            checker = ((x // 2) + (y // 2)) % 2

            if in_loop and (not in_bulb or y < 24):
                # Upper loop tube shading
                if d_outer_loop > -1.05:
                    col = gl_sh if nx_loop > 0.25 else gl_dk
                elif -0.65 <= nx_loop <= -0.10 or (y < 13 and -0.4 <= nx_loop <= 0.25):
                    col = gl_spec if (checker == 0 or nx_loop < -0.20) else gl_hi
                elif nx_loop < 0.28:
                    col = gl_hi if checker == 0 else gl_lt
                else:
                    col = gl_mid if checker == 0 else gl_dk
                c.set(x, y, col)
                continue

            bounds = bulb_bounds(y)
            assert bounds is not None
            x_left, x_right, cx_b = bounds
            rx_b = max(1.0, 0.5 * (x_right - x_left))
            bnx = (x - cx_b) / rx_b
            dist_left = x - x_left
            dist_right = x_right - x

            # Liquid pool in bottom bowl (horizontal meniscus oval around y = 47.5..53.5)
            liq_top = 47.6 + 0.9 * (bnx * bnx)
            in_liquid = (y >= liq_top) and (d_bulb <= -0.8)

            # Internal curved glass tube inside bulb
            if d_inner_tube <= 0.0:
                if d_inner_tube > -1.05 and y >= 28:
                    col = gl_rim
                elif -0.60 <= nx_tube <= -0.05:
                    col = gl_spec if checker == 0 else gl_hi
                elif nx_tube < 0.35:
                    col = gl_hi if checker == 0 else gl_lt
                else:
                    col = gl_mid if checker == 0 else gl_dk
                if in_liquid and d_inner_tube <= -1.05:
                    col = lerp_rgba(col, liq_hi, 0.35)
                c.set(x, y, col)
                continue

            if in_liquid:
                if y - liq_top < 1.3:
                    col = liq_men if checker == 0 else liq_hi
                elif y < 51:
                    col = liq_hi if checker == 0 else liq_mid
                else:
                    col = liq_mid if checker == 0 else liq_dk
                c.set(x, y, col)
                continue

            # Contour-hugging white specular reflection streaks along left wall and lower-right wall
            if 2.0 <= dist_left <= 5.2 and 27 <= y <= 48:
                if 2.6 <= dist_left <= 4.3 and 29 <= y <= 46:
                    col = gl_spec if (checker == 0 or 31 <= y <= 44) else gl_hi
                else:
                    col = gl_hi
            elif 2.2 <= dist_right <= 4.6 and 37 <= y <= 46:
                col = gl_spec if (2.8 <= dist_right <= 3.9 and 39 <= y <= 44) else gl_hi
            elif d_bulb > -1.35:
                col = gl_dk if bnx < 0.15 else gl_sh
            else:
                col = gl_lt if checker == 0 else gl_mid
                if bnx > 0.38:
                    col = gl_mid if checker == 0 else gl_dk
            c.set(x, y, col)

    # 2. CURVED DARK CHARCOAL-IRON BASE COLLAR WITH GLOWING CYAN GEMS (y = 53..60)
    # Spans x = 15..44 following the curved bottom of the bulb
    for x in range(15, 45):
        nx = (x - 29.5) / 15.0
        y_top = int(round(55.0 - 2.3 * (nx * nx)))
        y_bot = y_top + 4
        for y in range(y_top, y_bot + 1):
            if y == y_top:
                c.set(x, y, ir_hi)
            elif y == y_bot:
                c.set(x, y, ir_dk)
            else:
                c.set(x, y, ir_mid if (x % 3 != 0) else ir_dk)

    # Base collar side square studs (at x=21..22 and x=37..38)
    for sx in (21, 37):
        nx = (sx - 29.5) / 15.0
        sy = int(round(55.0 - 2.3 * (nx * nx))) + 1
        c.set(sx, sy, gm_spec)
        c.set(sx + 1, sy, gm_hi)
        c.set(sx, sy + 1, gm_mid)
        c.set(sx + 1, sy + 1, gm_dk)

    # Base collar central diamond bezel + glowing cyan diamond gem (centered at x=29.5, y=57.5)
    for y in range(54, 62):
        for x in range(26, 34):
            d = abs(x - 29.5) + abs(y - 57.5)
            if d <= 4.0:
                if d > 2.5:
                    c.set(x, y, ir_hi if (x - 29.5 + y - 57.5) < 0 else ir_dk)
                elif d <= 2.0:
                    if (x - 29.5) + (y - 57.5) <= -0.5:
                        c.set(x, y, gm_spec)
                    elif d <= 1.1:
                        c.set(x, y, gm_hi)
                    else:
                        c.set(x, y, gm_mid if (x <= 29) else gm_dk)

    # 3. SLANTED DARK-IRON + CYAN-GEM SEALING COLLAR AT TUBE ENTRY (around (44, 27.5))
    # Tilted perpendicular to the diagonal tube (~ -45 deg slash from (40, 23) to (48, 31))
    for y in range(22, 33):
        for x in range(39, 50):
            # Local rotated coordinates along collar normal (u) and tangent (v)
            dx = x - 44.0
            dy = y - 27.5
            # Tube goes along (-0.707, +0.707), collar wraps along (+0.707, +0.707)
            along_tube = (-dx + dy) * 0.7071
            across_tube = (dx + dy) * 0.7071
            if abs(along_tube) <= 2.1 and abs(across_tube) <= 5.0:
                if abs(across_tube) <= 1.25 and abs(along_tube) <= 1.25:
                    # Center glowing cyan gem
                    c.set(x, y, gm_spec if along_tube < 0 else gm_hi)
                elif 2.4 <= abs(across_tube) <= 3.7 and abs(along_tube) <= 1.15:
                    # Flanking glowing cyan gem segments
                    c.set(x, y, gm_hi if across_tube < 0 else gm_mid)
                elif along_tube < -1.0:
                    c.set(x, y, ir_hi)
                elif along_tube > 1.0:
                    c.set(x, y, ir_dk)
                else:
                    c.set(x, y, ir_mid)

    # 4. TOP PEAK DIAMOND CLASP (centered at x=38, y=5.5)
    for y in range(2, 9):
        for x in range(35, 42):
            d = abs(x - 38.0) + abs(y - 5.5)
            if d <= 3.5:
                if d > 2.2:
                    c.set(x, y, ir_hi if (x - 38.0 + y - 5.5) < 0 else ir_dk)
                else:
                    if (x - 38.0) + (y - 5.5) <= -0.5:
                        c.set(x, y, gm_spec)
                    elif d <= 1.1:
                        c.set(x, y, gm_hi)
                    else:
                        c.set(x, y, gm_mid if x <= 38 else gm_dk)

    # 5. LEFT LOOP HORIZONTAL CLASP (x = 19..28, y = 15..18)
    for y in range(15, 19):
        for x in range(19, 29):
            if 22 <= x <= 23 and 16 <= y <= 17:
                c.set(x, y, gm_spec if (x == 22 and y == 16) else (gm_hi if y == 16 else gm_mid))
            else:
                c.set(x, y, ir_hi if y == 15 else (ir_dk if y == 18 else ir_mid))

    # 6. RIGHT LOOP HORIZONTAL CLASP (x = 45..54, y = 15..18)
    for y in range(15, 19):
        for x in range(45, 54):
            if 49 <= x <= 50 and 16 <= y <= 17:
                c.set(x, y, gm_spec if (x == 49 and y == 16) else (gm_hi if y == 16 else gm_mid))
            else:
                c.set(x, y, ir_hi if y == 15 else (ir_dk if y == 18 else ir_mid))

    # 7. Crisp 1px dark charcoal outline (automatically preserves open circular hole!)
    c.add_outline(outline)
    return c.to_image()


# ---------------------------------------------------------------------------
# Item Model JSON Generator (upright GUI + crisp halberd handheld orientation)
# ---------------------------------------------------------------------------
def build_tool_model_json(tier: str) -> dict:
    return {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"universal_tool:item/{tier}_universal_tool",
        },
        "display": {
            "thirdperson_righthand": {
                "rotation": [0, -90, 35],
                "translation": [0, 3.5, 0.5],
                "scale": [0.85, 0.85, 0.85],
            },
            "thirdperson_lefthand": {
                "rotation": [0, 90, -35],
                "translation": [0, 3.5, 0.5],
                "scale": [0.85, 0.85, 0.85],
            },
            "firstperson_righthand": {
                "rotation": [0, -90, 20],
                "translation": [1.13, 3.2, 1.13],
                "scale": [0.68, 0.68, 0.68],
            },
            "firstperson_lefthand": {
                "rotation": [0, 90, -20],
                "translation": [1.13, 3.2, 1.13],
                "scale": [0.68, 0.68, 0.68],
            },
            "gui": {
                "rotation": [0, 0, 0],
                "translation": [0, 0, 0],
                "scale": [1, 1, 1],
            },
            "ground": {
                "rotation": [0, 0, 0],
                "translation": [0, 3, 0],
                "scale": [0.5, 0.5, 0.5],
            },
            "fixed": {
                "rotation": [0, 0, 0],
                "translation": [0, 0, 0],
                "scale": [1, 1, 1],
            },
        },
    }


# ---------------------------------------------------------------------------
# Strict Validation & Contact-Sheet Preview
# ---------------------------------------------------------------------------
def validate_png(path: Path, expected_size: Tuple[int, int] = (64, 64)) -> None:
    im = Image.open(path).convert("RGBA")
    if im.size != expected_size:
        raise ValueError(f"{path.name}: expected {expected_size}, got {im.size}")
    pxs = list(im.get_flattened_data()) if hasattr(im, "get_flattened_data") else list(im.getdata())
    opaque = 0
    for idx, (r, g, b, a) in enumerate(pxs):
        if a not in (0, 255):
            raise ValueError(f"{path.name}: non-binary alpha {a} at pixel {idx}")
        if a == 255:
            opaque += 1
            if r == 0 and g == 0 and b == 0:
                raise ValueError(f"{path.name}: pure #000000 pixel at {idx}")
    if opaque < 400:
        raise ValueError(f"{path.name}: suspiciously sparse ({opaque} opaque pixels)")


def render_contact_sheet(items: List[Tuple[str, Image.Image]], out_path: Path) -> None:
    scale = 4
    tile = 64 * scale  # 256
    pad = 16
    label_h = 24
    cols = 4
    rows = math.ceil(len(items) / cols)
    sheet_w = pad + cols * (tile + pad)
    sheet_h = pad + rows * (tile + label_h + pad)
    sheet = Image.new("RGBA", (sheet_w, sheet_h), rgba("#3A3D45"))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()

    for i, (label, img) in enumerate(items):
        col = i % cols
        row = i // cols
        x0 = pad + col * (tile + pad)
        y0 = pad + row * (tile + label_h + pad)
        # Minecraft #8B8B8B GUI slot background
        draw.rectangle([x0, y0, x0 + tile - 1, y0 + tile - 1], fill=rgba("#8B8B8B"), outline=rgba("#52555E"))
        up = img.resize((tile, tile), Image.Resampling.NEAREST)
        sheet.paste(up, (x0, y0), up)
        draw.text((x0 + 4, y0 + tile + 4), label, fill=rgba("#F0F2F5"), font=font)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out_path)


def main() -> None:
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)

    rendered: List[Tuple[str, Image.Image]] = []
    tiers = ["wooden", "stone", "iron", "golden", "diamond", "netherite"]

    for tier in tiers:
        img = build_universal_tool(tier)
        tex_path = TEX_DIR / f"{tier}_universal_tool.png"
        img.save(tex_path)
        validate_png(tex_path)
        rendered.append((f"{tier}_universal_tool", img))

        model_path = MODEL_DIR / f"{tier}_universal_tool.json"
        model_data = build_tool_model_json(tier)
        model_path.write_text(json.dumps(model_data, indent=2) + "\n", encoding="utf-8")

    klein_img = build_klein_bottle()
    klein_path = TEX_DIR / "klein_bottle.png"
    klein_img.save(klein_path)
    validate_png(klein_path)
    rendered.append(("klein_bottle", klein_img))

    render_contact_sheet(rendered, SCRATCH_PREVIEW)
    print(f"Generated & validated {len(rendered)} PNGs + 6 item models. Preview: {SCRATCH_PREVIEW}")


if __name__ == "__main__":
    main()
