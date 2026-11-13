"""
Generate all 5 Vanilla+ Minecraft End & Magic block texture sets (8 PNG files)
for the universal_tool 1.21.1 NeoForge mod, strictly preserving every existing
3D model geometry and UV mapping.

Owned outputs:
  1. src/main/resources/assets/universal_tool/textures/block/universal_polymerizer_atlas.png (64x64)
  2. src/main/resources/assets/universal_tool/textures/block/universal_lottery_machine.png (128x128)
  3. src/main/resources/assets/universal_tool/textures/block/personal_space_gate_atlas.png (64x64)
  4. src/main/resources/assets/universal_tool/textures/block/personal_space_return_atlas.png (64x64)
  5. src/main/resources/assets/universal_tool/textures/block/phoenix_pod_base.png (16x16)
  6. src/main/resources/assets/universal_tool/textures/block/phoenix_pod_glass.png (16x16)
  7. src/main/resources/assets/universal_tool/textures/block/phoenix_pod_glass_liquid.png (16x16)
  8. src/main/resources/assets/universal_tool/textures/block/phoenix_pod_top.png (16x16)
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from PIL import Image

REPO_ROOT = Path(__file__).resolve().parent.parent
TEX_BLOCK_DIR = REPO_ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "textures" / "block"
MODEL_BLOCK_DIR = REPO_ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "models" / "block"


# ==============================================================================
# Deterministic pixel-art helpers & Jappa 1.14+ Vanilla End & Magic Palettes
# ==============================================================================

def h2rgba(hex_str: str, alpha: int = 255) -> tuple[int, int, int, int]:
    s = hex_str.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), alpha)


def phash(x: int, y: int, seed: int = 0) -> int:
    """Deterministic integer pixel hash for crisp 1x Jappa-style noise."""
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return (n ^ (n >> 16)) & 0xFF


def put(img: Image.Image, x: int, y: int, col: tuple[int, int, int, int]) -> None:
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), col)


def fill_rect(img: Image.Image, x0: int, y0: int, x1: int, y1: int, col: tuple[int, int, int, int]) -> None:
    for yy in range(y0, y1):
        for xx in range(x0, x1):
            put(img, xx, yy, col)


# --- Purpur Blocks & Pillars ---
PURPUR_HI = h2rgba("#C69FC6")
PURPUR_BASE = h2rgba("#A97DA9")
PURPUR_MID = h2rgba("#9B6F9B")
PURPUR_SH = h2rgba("#8E638E")
PURPUR_MORTAR = h2rgba("#6E486E")
PURPUR_DEEP = h2rgba("#583658")

# --- End Stone & End Stone Bricks ---
END_HI = h2rgba("#EFF2B8")
END_BASE = h2rgba("#DBE19B")
END_MID = h2rgba("#CDD48C")
END_SH = h2rgba("#BFC47E")
END_MORTAR = h2rgba("#999E63")
END_DEEP = h2rgba("#7E844D")

# --- Obsidian & Crying Obsidian ---
OBS_DEEP = h2rgba("#0F0A18")
OBS_BASE = h2rgba("#1B1229")
OBS_MID = h2rgba("#2A1C3F")
OBS_HI = h2rgba("#3B2754")
OBS_EDGE = h2rgba("#4F356E")
CRY_OUTER = h2rgba("#6A1ED4")
CRY_TEAR = h2rgba("#8C33FF")
CRY_CORE = h2rgba("#D099FF")

# --- Enchanting Table Crimson Cloth & Gold Trim ---
CRIMSON_DEEP = h2rgba("#5E1117")
CRIMSON_BASE = h2rgba("#8F1D24")
CRIMSON_MID = h2rgba("#AA242D")
CRIMSON_HI = h2rgba("#C4363F")

GOLD_DEEP = h2rgba("#8E5B08")
GOLD_SH = h2rgba("#BD820E")
GOLD_BASE = h2rgba("#E9B115")
GOLD_HI = h2rgba("#FAF07B")
GOLD_SPARK = h2rgba("#FFFAD0")

# --- Vanilla Oak / Dark Oak Wood & Bookshelf Accents ---
DOAK_DEEP = h2rgba("#28180C")
DOAK_BASE = h2rgba("#3C2513")
DOAK_MID = h2rgba("#4F321B")
DOAK_HI = h2rgba("#644125")

OAK_DEEP = h2rgba("#6B4C26")
OAK_BASE = h2rgba("#8F6A38")
OAK_MID = h2rgba("#B08448")
OAK_HI = h2rgba("#CFA56A")

# --- Vanilla Glass, Amethyst, Ender Teal, Emerald, Redstone ---
GLASS_EDGE = h2rgba("#95C9D4")
GLASS_MID = h2rgba("#BCE4EC")
GLASS_GLINT = h2rgba("#D8F2F6")
GLASS_WHITE = h2rgba("#FFFFFF")

AMETHYST_DEEP = h2rgba("#4B1775")
AMETHYST_BASE = h2rgba("#7B2CBF")
AMETHYST_MID = h2rgba("#A452E8")
AMETHYST_HI = h2rgba("#D39BFF")
AMETHYST_SPEC = h2rgba("#F5D6FF")

ENDER_DEEP = h2rgba("#0C2B27")
ENDER_BASE = h2rgba("#185C50")
ENDER_MID = h2rgba("#2CA388")
ENDER_HI = h2rgba("#6BE0BC")
ENDER_SPEC = h2rgba("#C5FFE8")

EMERALD_DEEP = h2rgba("#115923")
EMERALD_BASE = h2rgba("#1C963D")
EMERALD_HI = h2rgba("#56DF70")
EMERALD_SPEC = h2rgba("#C2FFD0")

RUBY_DEEP = h2rgba("#6D1010")
RUBY_BASE = h2rgba("#B81E1E")
RUBY_HI = h2rgba("#EE4C3B")
RUBY_SPEC = h2rgba("#FFBCA8")


def paint_purpur_bricks(
    img: Image.Image, x0: int, y0: int, x1: int, y1: int, seed: int = 11,
    tile_w: int = 4, tile_h: int = 4, border_gold: bool = False
) -> None:
    """Paint staggered Jappa Purpur Block tiles with crisp mortar and top-left bevels."""
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            row = dy // tile_h
            ly = dy % tile_h
            off = (tile_w // 2) if (row % 2 == 1) else 0
            lx = (dx + off) % tile_w
            hv = phash(dx, dy, seed)

            if border_gold and (dx == 0 or dy == 0 or dx == w - 1 or dy == h - 1):
                col = GOLD_HI if (dx == 0 or dy == 0) else GOLD_SH
            elif (lx == tile_w - 1 and tile_w >= 3) or (ly == tile_h - 1 and tile_h >= 3):
                col = PURPUR_MORTAR if hv > 40 else PURPUR_DEEP
            elif lx == 0 or ly == 0:
                col = PURPUR_HI if hv > 65 else PURPUR_BASE
            else:
                if hv < 45:
                    col = PURPUR_SH
                elif hv < 130:
                    col = PURPUR_MID
                elif hv < 220:
                    col = PURPUR_BASE
                else:
                    col = PURPUR_HI
            put(img, x0 + dx, y0 + dy, col)


def paint_purpur_pillar(img: Image.Image, x0: int, y0: int, x1: int, y1: int, seed: int = 23) -> None:
    """Paint vertically fluted Purpur Pillar with crisp top/bottom collars."""
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            hv = phash(dx, dy, seed)
            if h >= 6 and (dy == 0 or dy == h - 1):
                col = PURPUR_HI if dy == 0 else PURPUR_MORTAR
            elif h >= 6 and (dy == 1 or dy == h - 2):
                col = PURPUR_MORTAR if dy == 1 else PURPUR_SH
            else:
                if w <= 2:
                    col = (PURPUR_HI if hv > 70 else PURPUR_BASE) if dx == 0 else (PURPUR_SH if hv > 60 else PURPUR_MORTAR)
                else:
                    flute = dx % 2
                    if flute == 0:
                        col = PURPUR_HI if hv > 155 else PURPUR_BASE
                    else:
                        col = PURPUR_MORTAR if hv < 75 else PURPUR_SH
            put(img, x0 + dx, y0 + dy, col)


def paint_end_stone_bricks(
    img: Image.Image, x0: int, y0: int, x1: int, y1: int, seed: int = 37,
    brick_w: int = 6, brick_h: int = 4, top_bevel: bool = True
) -> None:
    """Paint staggered Jappa End Stone Bricks with pale porous speckle & crisp mortar."""
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            row = dy // brick_h
            ly = dy % brick_h
            off = (brick_w // 2) if (row % 2 == 1) else 0
            lx = (dx + off) % brick_w
            hv = phash(dx, dy, seed)

            if top_bevel and dy == 0 and h >= 2:
                col = END_HI if hv > 55 else END_BASE
            elif (lx == brick_w - 1 and brick_w >= 3) or (ly == brick_h - 1 and brick_h >= 3):
                col = END_MORTAR if hv > 35 else END_DEEP
            elif lx == 0 or ly == 0:
                col = END_HI if hv > 75 else END_BASE
            else:
                # Subtle porous End Stone dimples
                if hv < 32:
                    col = END_MORTAR
                elif hv < 95:
                    col = END_SH
                elif hv < 175:
                    col = END_MID
                elif hv < 238:
                    col = END_BASE
                else:
                    col = END_HI
            put(img, x0 + dx, y0 + dy, col)


def paint_obsidian(
    img: Image.Image, x0: int, y0: int, x1: int, y1: int, seed: int = 53,
    crying: bool = False, bevel: bool = True
) -> None:
    """Paint dark angular Jappa Obsidian, optionally with glowing purple crying tears."""
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            hv = phash(dx, dy, seed)
            diag = phash(dx + dy, dx - dy, seed + 7)
            if bevel and w >= 4 and h >= 4 and (dx == 0 or dy == 0):
                col = OBS_EDGE if hv > 90 else OBS_HI
            elif bevel and w >= 4 and h >= 4 and (dx == w - 1 or dy == h - 1):
                col = OBS_DEEP if hv > 70 else OBS_BASE
            elif diag < 26:
                col = OBS_HI if hv > 110 else OBS_MID
            elif hv < 65:
                col = OBS_DEEP
            elif hv < 170:
                col = OBS_BASE
            elif hv < 230:
                col = OBS_MID
            else:
                col = OBS_HI
            put(img, x0 + dx, y0 + dy, col)

    if crying and w >= 3 and h >= 2:
        # Sparse crisp 1-2px crying obsidian veins
        num_tears = max(1, (w * h) // 24)
        for i in range(num_tears):
            tx = 1 + (phash(i, 1, seed + 99) % max(1, w - 2))
            ty = phash(i, 2, seed + 99) % max(1, h - 1)
            put(img, x0 + tx, y0 + ty, CRY_OUTER)
            if ty + 1 < h:
                put(img, x0 + tx, y0 + ty + 1, CRY_TEAR)
            if i % 2 == 0 and tx + 1 < w - 1 and h >= 4:
                put(img, x0 + tx, y0 + ty, CRY_CORE if ty > 0 else CRY_TEAR)


def paint_crimson_cloth(
    img: Image.Image, x0: int, y0: int, x1: int, y1: int, seed: int = 71,
    gold_trim: bool = True
) -> None:
    """Paint Enchanting Table crimson woven cloth, optionally framed by 1px gold trim."""
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            hv = phash(dx, dy, seed)
            if gold_trim and w >= 4 and h >= 4 and (dx == 0 or dy == 0 or dx == w - 1 or dy == h - 1):
                if (dx == 0 and dy == 0) or (dx == 0 and dy < h - 1) or (dy == 0 and dx < w - 1):
                    col = GOLD_HI if hv > 120 else GOLD_BASE
                else:
                    col = GOLD_SH if hv > 90 else GOLD_DEEP
            else:
                weave = (dx + dy) % 2
                if hv < 35:
                    col = CRIMSON_DEEP
                elif hv < 130 or weave == 1:
                    col = CRIMSON_BASE
                elif hv < 225:
                    col = CRIMSON_MID
                else:
                    col = CRIMSON_HI
            put(img, x0 + dx, y0 + dy, col)


def paint_gold_trim(img: Image.Image, x0: int, y0: int, x1: int, y1: int, seed: int = 89) -> None:
    """Paint beveled Minecraft gold block / trim."""
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            hv = phash(dx, dy, seed)
            if dx == 0 or dy == 0:
                col = GOLD_SPARK if (dx == 0 and dy == 0 and w >= 2 and h >= 2) else GOLD_HI
            elif (dx == w - 1 and w >= 2) or (dy == h - 1 and h >= 2):
                col = GOLD_DEEP if hv < 90 else GOLD_SH
            else:
                if hv < 45:
                    col = GOLD_SH
                elif hv < 185:
                    col = GOLD_BASE
                else:
                    col = GOLD_HI
            put(img, x0 + dx, y0 + dy, col)


def paint_wood_planks(
    img: Image.Image, x0: int, y0: int, x1: int, y1: int, seed: int = 103,
    dark: bool = True, frame_bevel: bool = True
) -> None:
    """Paint Vanilla Dark Oak or Oak plank woodwork with horizontal wood grain & seams."""
    w, h = x1 - x0, y1 - y0
    c_deep = DOAK_DEEP if dark else OAK_DEEP
    c_base = DOAK_BASE if dark else OAK_BASE
    c_mid = DOAK_MID if dark else OAK_MID
    c_hi = DOAK_HI if dark else OAK_HI
    plank_h = 3 if h >= 6 else max(2, h)

    for dy in range(h):
        for dx in range(w):
            hv = phash(dx, dy, seed)
            row_h = phash(0, dy, seed + 5)
            if frame_bevel and w >= 5 and h >= 5 and (dx == 0 or dy == 0 or dx == w - 1 or dy == h - 1):
                col = c_mid if (dx == 0 or dy == 0) else c_deep
            elif dy % plank_h == plank_h - 1 and h >= 4:
                col = c_deep
            elif (dx + (dy // plank_h) * 4) % 7 == 0 and h >= 6 and hv < 90:
                col = c_deep
            else:
                # Horizontal fiber streak bias
                if row_h < 70 and hv < 160:
                    col = c_base
                elif hv < 45:
                    col = c_deep
                elif hv < 150:
                    col = c_base
                elif hv < 225:
                    col = c_mid
                else:
                    col = c_hi
            put(img, x0 + dx, y0 + dy, col)


def paint_amethyst(img: Image.Image, x0: int, y0: int, x1: int, y1: int, seed: int = 131) -> None:
    """Paint faceted glowing Vanilla Amethyst crystal surface."""
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            hv = phash(dx, dy, seed)
            if dx == 0 and dy == 0:
                col = AMETHYST_SPEC
            elif dx == 0 or dy == 0:
                col = AMETHYST_HI
            elif (dx == w - 1 and w >= 2) or (dy == h - 1 and h >= 2):
                col = AMETHYST_DEEP if hv < 130 else AMETHYST_BASE
            elif (dx + dy) == max(1, (w + h) // 3):
                col = AMETHYST_HI
            elif hv < 70:
                col = AMETHYST_BASE
            elif hv < 180:
                col = AMETHYST_MID
            else:
                col = AMETHYST_HI
            put(img, x0 + dx, y0 + dy, col)


def paint_gem(img: Image.Image, x0: int, y0: int, x1: int, y1: int, palette: str = "emerald") -> None:
    """Paint small faceted Emerald or Redstone indicator gem."""
    if palette == "emerald":
        deep, base, hi, spec = EMERALD_DEEP, EMERALD_BASE, EMERALD_HI, EMERALD_SPEC
    else:
        deep, base, hi, spec = RUBY_DEEP, RUBY_BASE, RUBY_HI, RUBY_SPEC
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            if dx == 0 and dy == 0:
                col = spec
            elif dx == 0 or dy == 0:
                col = hi
            elif dx == w - 1 or dy == h - 1:
                col = deep
            else:
                col = base
            put(img, x0 + dx, y0 + dy, col)


# ==============================================================================
# 1. universal_polymerizer_atlas.png (64x64, cutout)
# ==============================================================================

def generate_polymerizer_atlas() -> Image.Image:
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    # 1) Plinth center top (0,0,12,12): Obsidian altar deck + End Stone border + Gold corners
    paint_obsidian(img, 0, 0, 12, 12, seed=101, crying=True, bevel=True)
    for i in range(1, 11):
        put(img, i, 1, END_HI if i % 2 == 0 else END_BASE)
        put(img, i, 10, END_MORTAR if i % 2 == 0 else END_SH)
        put(img, 1, i, END_HI if i % 2 == 0 else END_BASE)
        put(img, 10, i, END_MORTAR if i % 2 == 0 else END_SH)
    # Gold rune corner studs on plinth top
    for cx, cy in ((0, 0), (1, 1), (11, 0), (10, 1), (0, 11), (1, 10), (11, 11), (10, 10)):
        put(img, cx, cy, GOLD_HI if cx < 6 and cy < 6 else GOLD_BASE)
    for cx, cy in ((5, 0), (6, 0), (5, 11), (6, 11), (0, 5), (0, 6), (11, 5), (11, 6)):
        put(img, cx, cy, CRY_TEAR)

    # 2) Plinth center bottom (0,26,12,38): Obsidian + Purpur underside
    paint_obsidian(img, 0, 26, 12, 38, seed=102, crying=False, bevel=True)
    for i in range(3, 9):
        put(img, i, 29, PURPUR_SH)
        put(img, i, 34, PURPUR_MORTAR)
        put(img, 3, i + 26, PURPUR_SH)
        put(img, 8, i + 26, PURPUR_MORTAR)

    # 3) Plinth center sides (12,0,24,2): Obsidian base + Gold center clasp + Crying accents
    paint_obsidian(img, 12, 0, 24, 2, seed=103, crying=False, bevel=False)
    for dx in range(12):
        put(img, 12 + dx, 0, OBS_HI if dx % 3 else OBS_EDGE)
        put(img, 12 + dx, 1, OBS_DEEP if dx % 2 else OBS_BASE)
    put(img, 17, 0, GOLD_HI)
    put(img, 18, 0, GOLD_BASE)
    put(img, 17, 1, GOLD_SH)
    put(img, 18, 1, GOLD_DEEP)
    for cx in (14, 21):
        put(img, cx, 0, CRY_TEAR)
        put(img, cx, 1, CRY_OUTER)

    # 4) Plinth directional extensions N/S/W/E
    # (12,2,20,3): N/S step up/down (8x1) -> End Stone lip + gold centerline
    paint_end_stone_bricks(img, 12, 2, 20, 3, seed=104, brick_w=4, brick_h=1, top_bevel=True)
    put(img, 15, 2, GOLD_HI)
    put(img, 16, 2, GOLD_BASE)
    # (12,3,20,5): N/S/W/E step outer side (8x2) -> Obsidian + Gold center pip
    paint_obsidian(img, 12, 3, 20, 5, seed=105, crying=False, bevel=False)
    for dx in range(8):
        put(img, 12 + dx, 3, OBS_HI if dx % 2 == 0 else OBS_MID)
    put(img, 15, 3, GOLD_HI)
    put(img, 16, 3, GOLD_BASE)
    put(img, 15, 4, GOLD_SH)
    put(img, 16, 4, GOLD_DEEP)
    # (20,3,21,5): step end caps (1x2)
    put(img, 20, 3, OBS_EDGE)
    put(img, 20, 4, OBS_BASE)
    # (22,2,23,10): W/E step up/down (1x8) -> End Stone lip + gold centerline
    paint_end_stone_bricks(img, 22, 2, 23, 10, seed=106, brick_w=1, brick_h=4, top_bevel=False)
    put(img, 22, 5, GOLD_HI)
    put(img, 22, 6, GOLD_BASE)

    # 5) Pedestal Core Top/Bottom (24,0,34,10): Enchanting Table Crimson Cloth + Gold trim + Amethyst center
    paint_crimson_cloth(img, 24, 0, 34, 10, seed=107, gold_trim=True)
    # Inner obsidian & diamond embroidery motif
    for i in range(3, 7):
        put(img, 24 + i, 2, GOLD_BASE)
        put(img, 24 + i, 7, GOLD_SH)
        put(img, 26, i, GOLD_BASE)
        put(img, 31, i, GOLD_SH)
    paint_amethyst(img, 28, 4, 30, 6, seed=108)

    # 6) Pedestal Core Sides (34,0,44,8): Obsidian altar sides + Crimson draped banner + Gold trim + Amethyst eye
    paint_obsidian(img, 34, 0, 44, 8, seed=109, crying=True, bevel=True)
    # Top gold cornice lip across 10px
    for dx in range(10):
        put(img, 34 + dx, 0, GOLD_HI if dx % 2 == 0 else GOLD_BASE)
        put(img, 34 + dx, 7, OBS_DEEP)
    # Central crimson altar cloth tabard (36..42, 1..7)
    paint_crimson_cloth(img, 36, 1, 42, 7, seed=110, gold_trim=True)
    # Tiny amethyst gem recessed in center of crimson cloth
    put(img, 38, 3, AMETHYST_HI)
    put(img, 39, 3, AMETHYST_MID)
    put(img, 38, 4, AMETHYST_BASE)
    put(img, 39, 4, AMETHYST_DEEP)

    # 7) Pillars & Finials
    # (44,0,46,11): Purpur Pillars (2x11) with gold base & capital collars
    paint_purpur_pillar(img, 44, 0, 46, 11, seed=111)
    put(img, 44, 0, GOLD_HI)
    put(img, 45, 0, GOLD_BASE)
    put(img, 44, 1, GOLD_SH)
    put(img, 45, 1, GOLD_DEEP)
    put(img, 44, 9, GOLD_HI)
    put(img, 45, 9, GOLD_BASE)
    put(img, 44, 10, GOLD_SH)
    put(img, 45, 10, GOLD_DEEP)

    # (46,0,48,2): Pillar caps / finial bottom (2x2)
    put(img, 46, 0, PURPUR_HI)
    put(img, 47, 0, PURPUR_BASE)
    put(img, 46, 1, PURPUR_SH)
    put(img, 47, 1, PURPUR_MORTAR)

    # (46,2,48,4): Finial sides (2x2) -> Gold collar bottom, glowing Amethyst tip top
    put(img, 46, 2, AMETHYST_SPEC)
    put(img, 47, 2, AMETHYST_MID)
    put(img, 46, 3, GOLD_HI)
    put(img, 47, 3, GOLD_SH)

    # (48,2,50,4): Finial top (2x2)
    paint_amethyst(img, 48, 2, 50, 4, seed=112)

    # 8) Upper 12-hour Astrolabe Clock Ring
    # Ring N/S top/down: (0,12,10,15) and (10,12,20,15)
    for rx, is_north in ((0, True), (10, False)):
        paint_end_stone_bricks(img, rx, 12, rx + 10, 15, seed=113 + rx, brick_w=5, brick_h=3, top_bevel=True)
        # Gold edge rim
        for dx in range(10):
            put(img, rx + dx, 12, GOLD_HI if dx % 2 == 0 else GOLD_BASE)
            put(img, rx + dx, 14, PURPUR_MORTAR if dx % 2 else PURPUR_SH)
        # 3 hour markers: hours 11/12/1 (north) and 5/6/7 (south)
        for hx in (1, 4, 8):
            put(img, rx + hx, 13, AMETHYST_SPEC if hx == 4 else GOLD_SPARK)
            if hx == 4:
                put(img, rx + 5, 13, AMETHYST_HI)
            else:
                put(img, rx + hx + 1, 13, GOLD_BASE)

    # Ring W/E top/down: (20,12,23,18) and (23,12,26,18)
    for rx in (20, 23):
        paint_end_stone_bricks(img, rx, 12, rx + 3, 18, seed=120 + rx, brick_w=3, brick_h=3, top_bevel=True)
        for dy in range(6):
            put(img, rx, 12 + dy, GOLD_HI if dy % 2 == 0 else GOLD_BASE)
            put(img, rx + 2, 12 + dy, PURPUR_SH if dy % 2 == 0 else PURPUR_MORTAR)
        # 3 hour markers: hours 8/9/10 and 2/3/4
        for hy in (0, 2, 5):
            put(img, rx + 1, 12 + hy, AMETHYST_SPEC if hy == 2 else GOLD_SPARK)

    # Ring outer/inner walls
    # (0,16,10,18): Ring N/S north/south (10x2) -> Purpur & Gold carved band
    paint_purpur_bricks(img, 0, 16, 10, 18, seed=130, tile_w=5, tile_h=2)
    for dx in range(10):
        if dx in (0, 4, 5, 9):
            put(img, dx, 16, GOLD_HI)
            put(img, dx, 17, GOLD_SH)
    # (10,16,16,18): Ring W/E east/west (6x2)
    paint_purpur_bricks(img, 10, 16, 16, 18, seed=131, tile_w=3, tile_h=2)
    for dx in (10, 12, 13, 15):
        put(img, dx, 16, GOLD_HI)
        put(img, dx, 17, GOLD_SH)
    # (16,16,19,18): Ring end caps (3x2)
    paint_purpur_bricks(img, 16, 16, 19, 18, seed=132, tile_w=3, tile_h=2)
    put(img, 17, 16, GOLD_HI)
    put(img, 17, 17, GOLD_SH)

    # 9) Central Core Well & Crystal
    # (0,20,6,26): Core well top/down (6x6) -> Obsidian/Gold bezel + glowing End-Void socket
    paint_obsidian(img, 0, 20, 6, 26, seed=140, crying=False, bevel=True)
    for i in range(1, 5):
        put(img, i, 21, GOLD_HI if i < 4 else GOLD_BASE)
        put(img, i, 24, GOLD_SH)
        put(img, 1, 20 + i, GOLD_HI if i < 4 else GOLD_BASE)
        put(img, 4, 20 + i, GOLD_SH)
    paint_amethyst(img, 2, 22, 4, 24, seed=141)

    # (6,20,12,21): Core well sides (6x1)
    for dx in range(6):
        put(img, 6 + dx, 20, GOLD_HI if dx in (1, 4) else (GOLD_BASE if dx in (2, 3) else OBS_HI))

    # (12,20,16,24): Core crystal top/down (4x4) -> Radiant End-Crystal / Amethyst heart
    paint_amethyst(img, 12, 20, 16, 24, seed=142)
    put(img, 13, 21, AMETHYST_SPEC)
    put(img, 14, 21, GLASS_WHITE)
    put(img, 13, 22, AMETHYST_HI)
    put(img, 14, 22, AMETHYST_MID)

    # (16,20,20,21): Core crystal sides (4x1)
    put(img, 16, 20, AMETHYST_HI)
    put(img, 17, 20, AMETHYST_SPEC)
    put(img, 18, 20, AMETHYST_MID)
    put(img, 19, 20, AMETHYST_BASE)

    return img


# ==============================================================================
# 2 & 3. personal_space_gate_atlas.png & personal_space_return_atlas.png (64x64)
# ==============================================================================

def _paint_personal_space_atlas(is_return: bool) -> Image.Image:
    """
    Shared UV geometry for both Personal Space Gate & Return blocks (64x64 cutout):
      [0..16, 0..16]  : Arch & step stonework (Purpur for Gate, End Stone Bricks for Return)
      [0..16, 16..32] : Heavy Foundation plinth (Crying Obsidian)
      [16..24, 0..8]  : Runic inlays, keystone & threshold trim (Gold + Amethyst/Crimson)
      [32..48, 0..16] : Portal aperture (6x9 centered around (35,4.5))
    """
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    # 1) Arch & step stonework [0..16, 0..16]
    # Note: y=0 is sampled by `step` sides (1px tall), so keep y=0 crisp top-bevel highlight!
    if not is_return:
        paint_purpur_bricks(img, 0, 0, 16, 16, seed=201, tile_w=4, tile_h=4)
        for dx in range(16):
            put(img, dx, 0, PURPUR_HI if dx % 4 != 3 else PURPUR_BASE)
            # Decorative gold keystone/step studs along middle line
            if dx in (2, 7, 8, 13):
                put(img, dx, 1, GOLD_BASE)
    else:
        paint_end_stone_bricks(img, 0, 0, 16, 16, seed=202, brick_w=4, brick_h=4, top_bevel=True)
        for dx in range(16):
            put(img, dx, 0, END_HI if dx % 4 != 3 else END_BASE)
            if dx in (2, 7, 8, 13):
                put(img, dx, 1, GOLD_BASE)

    # 2) Foundation plinth [0..16, 16..32]: Heavy Obsidian + Crying Obsidian + Gold rivets
    paint_obsidian(img, 0, 16, 16, 32, seed=203 if not is_return else 204, crying=True, bevel=True)
    # 2px tall side band at y=16..18 is used by foundation north/south/east/west
    for dx in range(16):
        put(img, dx, 16, OBS_EDGE if dx % 4 == 0 else OBS_HI)
        put(img, dx, 17, OBS_DEEP if dx % 2 else OBS_BASE)
        if dx in (2, 6, 9, 13):
            put(img, dx, 16, GOLD_BASE if not is_return else CRIMSON_HI)
            put(img, dx, 17, GOLD_DEEP if not is_return else CRIMSON_DEEP)
    # Tears inside foundation bottom y=19..25
    for tx, ty in ((3, 20), (10, 22), (5, 24), (12, 19)):
        put(img, tx, ty, CRY_TEAR if not is_return else ENDER_MID)
        put(img, tx, ty + 1, CRY_CORE if not is_return else ENDER_HI)

    # 3) Inlays, Keystone, Crystal & Threshold [16..24, 0..8]
    # Note: keystone (16..18, 0..3), left/right inlays (16..17, 0..8), upper_inlay (16..22, 0..1), threshold (16..24, 0..6)
    if not is_return:
        # Gate: Gold-framed Amethyst rune strip + gold threshold
        paint_gold_trim(img, 16, 0, 24, 8, seed=205)
        # Amethyst inlay stripe along x=16 (used by 1px wide vertical inlays) and y=0..2 keystone
        for dy in range(8):
            put(img, 16, dy, AMETHYST_SPEC if dy % 3 == 0 else (AMETHYST_HI if dy % 2 else AMETHYST_MID))
            put(img, 17, dy, GOLD_HI if dy % 2 == 0 else AMETHYST_BASE)
        for dx in range(18, 24):
            put(img, dx, 0, GOLD_SPARK if dx % 2 == 0 else AMETHYST_HI)
            put(img, dx, 1, GOLD_BASE)
            put(img, dx, 2, AMETHYST_MID if dx % 2 == 0 else GOLD_SH)
            for dy in range(3, 6):
                put(img, dx, dy, CRIMSON_MID if (dx + dy) % 2 == 0 else CRIMSON_BASE)
    else:
        # Return: Enchanting Crimson Cloth & Gold filigree inlays + Ender pearl accent
        paint_gold_trim(img, 16, 0, 24, 8, seed=206)
        for dy in range(8):
            put(img, 16, dy, ENDER_SPEC if dy % 3 == 0 else (ENDER_HI if dy % 2 else CRIMSON_HI))
            put(img, 17, dy, GOLD_HI if dy % 2 == 0 else ENDER_MID)
        for dx in range(18, 24):
            put(img, dx, 0, GOLD_SPARK if dx % 2 == 0 else ENDER_HI)
            put(img, dx, 1, GOLD_BASE)
            put(img, dx, 2, CRIMSON_MID if dx % 2 == 0 else GOLD_SH)
            for dy in range(3, 6):
                put(img, dx, dy, CRIMSON_HI if (dx + dy) % 2 == 0 else CRIMSON_BASE)

    # 4) Portal aperture [32..48, 0..16] — specifically UV [32..38, 0..9] is the 6x9 opening!
    # Paint entire 16x16 quadrant cleanly while centering the vortex jewel at (35, 4.5) in [32..38, 0..9].
    cx, cy = 34.5, 4.0
    for dy in range(16):
        for dx in range(16):
            dist = math.hypot(dx - (cx - 32), dy - cy)
            ang = math.atan2(dy - cy, dx - (cx - 32))
            swirl = math.sin(ang * 2.0 - dist * 1.15)
            hv = phash(dx, dy, 210 if not is_return else 211)

            if not is_return:
                # Gate: Deep End Void + Amethyst/Purpur Dimensional Vortex
                if dist < 1.2:
                    col = AMETHYST_SPEC
                elif dist < 2.3:
                    col = AMETHYST_HI if swirl > -0.2 else CRY_TEAR
                elif dist < 3.6:
                    col = CRY_TEAR if swirl > 0.1 else AMETHYST_BASE
                elif dist < 4.9:
                    col = OBS_EDGE if swirl > 0.2 else OBS_MID
                else:
                    col = OBS_BASE if hv > 90 else OBS_DEEP
                # Crisp End-Portal star flecks
                if hv > 236 and dist > 1.4:
                    col = AMETHYST_SPEC if hv > 248 else PURPUR_HI
            else:
                # Return: Ender-Eye Emerald-Teal + Warm Gold Homing Vortex
                if dist < 1.2:
                    col = GOLD_SPARK
                elif dist < 2.2:
                    col = ENDER_SPEC if swirl > -0.2 else ENDER_HI
                elif dist < 3.5:
                    col = ENDER_MID if swirl > 0.0 else ENDER_BASE
                elif dist < 4.8:
                    col = ENDER_DEEP if swirl > 0.25 else OBS_MID
                else:
                    col = OBS_BASE if hv > 90 else ENDER_DEEP
                if hv > 236 and dist > 1.4:
                    col = GOLD_HI if hv > 248 else ENDER_SPEC

            # Subtle outer rim along 6x9 opening border (x=0 or x=5, y=0 or y=8)
            if dx < 6 and dy < 9 and (dx in (0, 5) or dy in (0, 8)):
                if not is_return:
                    col = OBS_EDGE if (dx + dy) % 2 == 0 else CRY_OUTER
                else:
                    col = ENDER_MID if (dx + dy) % 2 == 0 else ENDER_BASE

            put(img, 32 + dx, dy, col)

    return img


# ==============================================================================
# 4. phoenix_pod_{base,top,glass,glass_liquid}.png (16x16, translucent)
# ==============================================================================

def generate_phoenix_pod_textures() -> dict[str, Image.Image]:
    # --- phoenix_pod_base.png ---
    # UVs:
    #   base_plate up/down (1..15, 1..15), sides (1..15, 13..16)
    #   p_nw/ne/sw/se (0..2, 0..13) & (14..16, 0..13)
    #   spine north/south (6..10, 3..16), east/west (13..15, 3..16)
    base = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paint_obsidian(base, 1, 1, 15, 15, seed=301, crying=False, bevel=True)
    # End Stone inner recessed floor + Crimson/Gold stasis rune circle
    paint_end_stone_bricks(base, 2, 2, 14, 14, seed=302, brick_w=4, brick_h=4, top_bevel=True)
    paint_crimson_cloth(base, 4, 4, 12, 12, seed=303, gold_trim=True)
    paint_amethyst(base, 6, 6, 10, 10, seed=304)
    # Base plate sides (1..15, 13..16): Obsidian + gold trim top lip
    paint_obsidian(base, 1, 13, 15, 16, seed=305, crying=True, bevel=False)
    for dx in range(1, 15):
        put(base, dx, 13, GOLD_HI if dx % 2 else GOLD_BASE)
        put(base, dx, 15, OBS_DEEP)
    # Corner Purpur Pillars (0..2, 0..13) & (14..16, 0..13) with gold brackets
    for px in (0, 14):
        paint_purpur_pillar(base, px, 0, px + 2, 13, seed=306 + px)
        for gy in (0, 1, 11, 12):
            put(base, px, gy, GOLD_HI if gy in (0, 11) else GOLD_SH)
            put(base, px + 1, gy, GOLD_BASE if gy in (0, 11) else GOLD_DEEP)
    # Rear spine conduit (6..10, 3..16) + side face (13..15, 3..16)
    paint_obsidian(base, 6, 3, 10, 13, seed=307, crying=False, bevel=True)
    for dy in range(4, 13):
        put(base, 7, dy, AMETHYST_SPEC if dy % 3 == 0 else AMETHYST_HI)
        put(base, 8, dy, AMETHYST_MID if dy % 3 == 0 else AMETHYST_BASE)
    put(base, 6, 6, GOLD_BASE)
    put(base, 9, 6, GOLD_SH)
    put(base, 6, 10, GOLD_BASE)
    put(base, 9, 10, GOLD_SH)
    # Re-apply base side lip cleanly across bottom (y=13..15), keeping pillar corners gold/purpur
    for dy in range(13, 16):
        put(base, 0, dy, OBS_BASE)
        put(base, 15, dy, OBS_DEEP)

    # --- phoenix_pod_top.png ---
    # UVs:
    #   top_cap up/down (1..15, 1..15), sides (1..15, 0..3)
    #   p_nw/ne/sw/se (0..2, 3..16) & (14..16, 3..16)
    #   spine north/south (6..10, 3..16), east/west (13..15, 3..16)
    #   mini_clone in full item (6..10, 4..11) & (6..10, 6..10)
    top = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paint_obsidian(top, 1, 1, 15, 15, seed=310, crying=False, bevel=True)
    paint_purpur_bricks(top, 2, 2, 14, 14, seed=311, tile_w=4, tile_h=4, border_gold=True)
    # End Stone & Crimson medallion around mini-clone stasis area (6..10, 4..11)
    paint_crimson_cloth(top, 4, 4, 12, 12, seed=312, gold_trim=True)
    # Subtle stylized emerald/ender stasis figure in (6..10, 4..11) for item form `mini_clone`
    paint_amethyst(top, 6, 4, 10, 11, seed=313)
    put(top, 7, 5, ENDER_SPEC)
    put(top, 8, 5, ENDER_HI)
    put(top, 7, 6, GOLD_SPARK)
    put(top, 8, 6, GOLD_HI)
    put(top, 7, 7, ENDER_HI)
    put(top, 8, 7, ENDER_MID)
    # Top cap sides (1..15, 0..3): End Stone cornice + gold rim
    paint_end_stone_bricks(top, 1, 0, 15, 3, seed=314, brick_w=4, brick_h=3, top_bevel=True)
    for dx in range(1, 15):
        put(top, dx, 2, GOLD_BASE if dx % 2 == 0 else GOLD_SH)
    # Rear spine conduit on top half: (6..10, 3..16) and east/west (13..15, 3..16)
    paint_obsidian(top, 6, 12, 10, 16, seed=316, crying=False, bevel=False)
    for dy in range(11, 16):
        put(top, 7, dy, AMETHYST_SPEC if dy % 3 == 0 else AMETHYST_HI)
        put(top, 8, dy, AMETHYST_MID if dy % 3 == 0 else AMETHYST_BASE)
    paint_obsidian(top, 13, 12, 15, 16, seed=317, crying=False, bevel=False)
    for dy in range(3, 16):
        put(top, 13, dy, OBS_EDGE if dy % 2 == 0 else OBS_HI)
        put(top, 14, dy, GOLD_BASE if dy in (3, 9, 15) else OBS_BASE)
    # Corner Purpur Pillars (0..2, 3..16) & (14..16, 3..16)
    for px in (0, 14):
        paint_purpur_pillar(top, px, 3, px + 2, 16, seed=315 + px)
        for gy in (3, 4, 14, 15):
            put(top, px, gy, GOLD_HI if gy in (3, 14) else GOLD_SH)
            put(top, px + 1, gy, GOLD_BASE if gy in (3, 14) else GOLD_DEEP)
    for dy in range(0, 3):
        put(top, 0, dy, GOLD_HI if dy == 0 else PURPUR_BASE)
        put(top, 15, dy, GOLD_SH if dy == 0 else PURPUR_MORTAR)

    # --- phoenix_pod_glass.png ---
    # UVs: bottom_empty (3..13, 0..13), top_empty (3..13, 3..16), full (5..11, 2..14)
    # Vanilla Glass style: crisp 1px #D8F2F6 / #8EBEC9 rim + diagonal 1px pixel glint streaks, nearly clear center
    glass = Image.new("RGBA", (16, 16), (200, 238, 245, 36))
    for dy in range(16):
        for dx in range(16):
            if dx in (0, 15) or dy in (0, 15):
                put(glass, dx, dy, h2rgba("#87BAC4", 215))
            elif dx in (3, 12) and dy in (3, 12):
                put(glass, dx, dy, h2rgba("#D8F2F6", 190))
            elif (dx + dy) in (6, 7, 17, 23) and 4 <= dx <= 11 and 4 <= dy <= 11:
                if (dx == 4 and dy in (3, 4)) or (dx + dy == 7 and dx in (3, 4, 5)):
                    put(glass, dx, dy, h2rgba("#FFFFFF", 220))
                else:
                    put(glass, dx, dy, h2rgba("#D8F2F6", 170))
    for gx, gy in ((4, 4), (5, 4), (4, 5), (10, 10), (11, 10), (10, 11), (5, 9), (6, 8)):
        put(glass, gx, gy, h2rgba("#FFFFFF", 215))

    # --- phoenix_pod_glass_liquid.png ---
    # Used by bottom_liquid (3..13, 0..13), top_liquid (3..13, 3..16),
    # and PhoenixPodRenderer sliding doors (0..16, 0..16) with alpha * 130/255 (~0.51).
    liquid = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for dy in range(16):
        for dx in range(16):
            hv = phash(dx, dy, 333)
            # Outer gold-trimmed glass door frame
            if dx in (0, 15) or dy in (0, 15):
                col = h2rgba("#E9B115", 235) if (dx + dy) % 3 == 0 else h2rgba("#9CCCD6", 225)
            else:
                # Layered Enchanted Amethyst-Regeneration Potion fluid (no distracting inner crossbars)
                wave = 1 if ((dx // 2 + dy // 3) % 2 == 0) else 0
                if dy <= 2:
                    col = h2rgba("#F2A8FF", 190) if hv > 90 else h2rgba("#C770FF", 185)
                elif hv > 234:
                    col = h2rgba("#FFFAD0", 235) if hv > 246 else h2rgba("#F8B8FF", 225)
                elif wave == 0:
                    col = h2rgba("#B342EA", 180) if hv > 110 else h2rgba("#962CD1", 176)
                else:
                    col = h2rgba("#8A24C2", 174) if hv > 100 else h2rgba("#721BA3", 172)
            put(liquid, dx, dy, col)

    # Crisp diagonal Vanilla glass reflection ticks across the chamber glass
    for gx, gy in ((4, 4), (5, 4), (4, 5), (6, 8), (5, 9), (7, 7), (10, 10), (11, 10), (10, 11)):
        put(liquid, gx, gy, h2rgba("#FFFFFF", 235))

    return {
        "phoenix_pod_base.png": base,
        "phoenix_pod_top.png": top,
        "phoenix_pod_glass.png": glass,
        "phoenix_pod_glass_liquid.png": liquid,
    }


# ==============================================================================
# 5. universal_lottery_machine.png (128x128, cutout)
# ==============================================================================

def _classify_lottery_cube_group(cube_names: set[str]) -> str:
    """
    Map a set of cube names sharing a UV rect into a cohesive Vanilla End & Magic material role.
    Precedence resolves mixed-cube shared edge/soffit UV rects harmoniously.
    """
    joined = " ".join(sorted(cube_names))

    # 1. Glass tubes (verified exclusive in models!)
    if all("glass" in n for n in cube_names):
        return "cutout_glass"

    # 2. Glowing gems & indicators (all exclusive or purely glowing)
    if all("indicator_gem_green" in n for n in cube_names):
        return "emerald_gem"
    if all("indicator_gem_red" in n for n in cube_names):
        return "ruby_gem"
    if all("tube_" in n and "core" in n for n in cube_names):
        return "tube_potion_core"
    if any("celestial_" in n for n in cube_names):
        return "celestial_core"
    if any("crystal_shard" in n for n in cube_names):
        return "amethyst_crystal"
    if any("lever_ball_knob" in n for n in cube_names):
        return "crimson_orb"
    if any("starfield_wall" in n for n in cube_names):
        return "end_portal_starfield"
    if any("star_ring_orbit" in n for n in cube_names):
        return "gold_trim"
    if any("chute_interior_back_glow" in n for n in cube_names):
        return "blaze_chute_glow"

    # 3. Cutout gauges & dial faces
    if cube_names == {"side_pressure_gauge_face"}:
        return "dial_face"
    if any("gauge_needle" in n for n in cube_names):
        return "crimson_cloth"

    # 4. Crimson cloth & Gold trim accents
    if any("button_golden_cap" in n or "gauge_brass_arch" in n or "roof_center_crest" in n or "bracket" in n or "pipe_" in n or "handle" in n for n in cube_names):
        return "gold_trim"
    if any("button_housing" in n or "button_pedestal" in n or "roof_rim" in n for n in cube_names):
        return "crimson_cloth"

    # 5. End Stone Bricks (pedestal, rear housing, desk, roof cornice, drawer, chute tray)
    if any("pedestal_plate" in n or "roof_base_cornice" in n or "upper_rear_housing" in n or "console_desk_slope" in n or "scrap_drawer_face" in n or "chute_tray" in n for n in cube_names):
        return "end_stone_bricks"

    # 6. Purpur Pillars & Arches
    if any("pillar" in n for n in cube_names):
        return "purpur_pillar"
    if any("roof_arch" in n or "chute_arch" in n or "chute_side_lip" in n for n in cube_names):
        return "purpur_bricks"

    # 7. Bookshelf / Dark Oak Wood cabinet panels
    if any("lower_side_panel" in n or "upper_side_wall" in n for n in cube_names):
        return "bookshelf_wood"
    if any("lower_body_core" in n or "waist_trim_belt" in n or "chute_interior_" in n for n in cube_names):
        return "dark_oak_cabinet"

    # 8. Obsidian & Crying Obsidian frames, screens, vents, conduits, gauge housings
    if any("foot_screen" in n or "waist_boss" in n for n in cube_names):
        return "crying_obsidian"
    if any("back_" in n or "chamber_frame" in n or "chamber_corner" in n or "lever_" in n or "gauge_" in n or "indicator_toggle" in n or "side_pressure_gauge_body" in n for n in cube_names):
        return "obsidian_trimmed"

    # Fallback
    return "obsidian_trimmed"


def _paint_bookshelf_panel(img: Image.Image, x0: int, y0: int, x1: int, y1: int, seed: int) -> None:
    """Paint Oak/Dark Oak wooden frame with miniature Enchanting Bookshelf spines inside."""
    w, h = x1 - x0, y1 - y0
    paint_wood_planks(img, x0, y0, x1, y1, seed=seed, dark=True, frame_bevel=True)
    if w >= 8 and h >= 8:
        book_cols = [
            (CRIMSON_HI, CRIMSON_BASE),
            (GOLD_HI, GOLD_BASE),
            (ENDER_HI, ENDER_BASE),
            (AMETHYST_HI, AMETHYST_BASE),
            (OAK_HI, OAK_BASE),
        ]
        # Two miniature book rows inside a 1px dark oak frame
        for shelf_y0 in (2, h // 2 + 1):
            shelf_y1 = min(h - 2, shelf_y0 + max(2, (h - 5) // 2))
            for dx in range(2, w - 2):
                b_idx = phash(dx, shelf_y0, seed + 17) % len(book_cols)
                hi, base = book_cols[b_idx]
                for dy in range(shelf_y0, shelf_y1):
                    col = GOLD_BASE if (dy == shelf_y0 and dx % 3 == 0) else (hi if dy == shelf_y0 else base)
                    put(img, x0 + dx, y0 + dy, col)
            # Wooden shelf divider bar under row
            if shelf_y1 < h - 1:
                for dx in range(1, w - 1):
                    put(img, x0 + dx, shelf_y1, OAK_BASE if dx % 2 == 0 else OAK_DEEP)


def _paint_cutout_glass(img: Image.Image, x0: int, y0: int, x1: int, y1: int) -> None:
    """
    Paint cutout-mode Vanilla Glass (`alpha=0` interior so inner potion cores show through!).
    Handles narrow 2px-wide tube faces by keeping middle rows open (`alpha=0`) with
    crisp 1px top/bottom rims and diagonal glint pixels.
    """
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            if w <= 2 and h <= 2:
                # 2x2 tube end-caps: crisp glass glint corner
                col = GLASS_GLINT if (dx == 0 or dy == 0) else GLASS_EDGE
            elif w <= 2:
                # Narrow 2x10 vertical tube wall: top/bottom glass collar + crisp diagonal glints, open center!
                if dy == 0:
                    col = GLASS_WHITE if dx == 0 else GLASS_GLINT
                elif dy == h - 1:
                    col = GLASS_EDGE
                elif (dx == 0 and dy in (2, 6)) or (dx == 1 and dy in (3, 7)):
                    col = GLASS_WHITE if dy == 2 else GLASS_GLINT
                else:
                    col = (0, 0, 0, 0)
            elif dx == 0 or dy == 0 or dx == w - 1 or dy == h - 1:
                if (dx == 0 and dy == 0) or (dx == 0 and dy < h // 2) or (dy == 0 and dx < w // 2):
                    col = GLASS_GLINT
                else:
                    col = GLASS_EDGE
            elif ((dx + dy) % 5 == 2 and dy in (1, 2, h - 3, h - 2)) or (dx == 1 and dy in (1, 2)):
                col = GLASS_WHITE if (dx == 1 and dy == 1) else GLASS_GLINT
            else:
                col = (0, 0, 0, 0)
            put(img, x0 + dx, y0 + dy, col)


def _paint_end_starfield(img: Image.Image, x0: int, y0: int, x1: int, y1: int, seed: int) -> None:
    """Paint Minecraft End Portal cosmic void studded with End-green, amethyst & gold stars."""
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            hv = phash(dx, dy, seed)
            if hv > 246:
                col = GOLD_SPARK
            elif hv > 234:
                col = ENDER_SPEC
            elif hv > 220:
                col = AMETHYST_HI
            elif hv > 195:
                col = h2rgba("#2B3D63")
            elif hv > 130:
                col = h2rgba("#141930")
            else:
                col = h2rgba("#090714")
            put(img, x0 + dx, y0 + dy, col)


def _paint_dial_face(img: Image.Image, x0: int, y0: int, x1: int, y1: int) -> None:
    """Paint cream End Stone clock/gauge dial with gold ticks and redstone center pip."""
    w, h = x1 - x0, y1 - y0
    for dy in range(h):
        for dx in range(w):
            if dx == 0 or dy == 0 or dx == w - 1 or dy == h - 1:
                col = GOLD_SH if (dx == w - 1 or dy == h - 1) else GOLD_HI
            elif dx == w // 2 and dy == h // 2:
                col = RUBY_HI
            else:
                col = END_HI if (dx + dy) % 2 == 0 else END_BASE
            put(img, x0 + dx, y0 + dy, col)


def generate_lottery_machine_atlas() -> Image.Image:
    """
    Read exact UV pixel rectangles from universal_lottery_machine_{bottom,top,full}.json
    and paint every mapped island + both BER tint swatches at (0..8, 0..4).
    """
    uv_map: dict[tuple[int, int, int, int], set[str]] = {}
    for fname in (
        "universal_lottery_machine_bottom.json",
        "universal_lottery_machine_top.json",
        "universal_lottery_machine_full.json",
    ):
        data = json.loads((MODEL_BLOCK_DIR / fname).read_text(encoding="utf-8"))
        # Compute UV scale factor (texture_size is [128,128], Minecraft UVs are 0..16 -> x8)
        sx = data["texture_size"][0] / 16.0
        sy = data["texture_size"][1] / 16.0
        for el in data["elements"]:
            cname = el.get("name", "unnamed")
            for face in el.get("faces", {}).values():
                u0, v0, u1, v1 = face["uv"]
                px0, py0 = int(round(u0 * sx)), int(round(v0 * sy))
                px1, py1 = int(round(u1 * sx)), int(round(v1 * sy))
                rect = (min(px0, px1), min(py0, py1), max(px0, px1), max(py0, py1))
                uv_map.setdefault(rect, set()).add(cname)

    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))

    # 1. CRITICAL BER Swatches (UniversalLotteryMachineRenderer.java samples U/V inside these!)
    # (0, 0, 4, 4): U0_SOLID..V1_SOLID -> pure white so Java vertex RGB tinting stays true
    fill_rect(img, 0, 0, 4, 4, (255, 255, 255, 255))
    # (4, 0, 8, 4): U0_METAL..V1_METAL -> crisp bright neutral metallic pixel noise
    for dy in range(4):
        for dx in range(4):
            v = 242 + (phash(dx, dy, 404) % 14)
            put(img, 4 + dx, dy, (v, v, v, 255))

    # 2. Paint every UV rect based on its exact model cube names
    for idx, (rect, cubes) in enumerate(sorted(uv_map.items())):
        x0, y0, x1, y1 = rect
        w, h = x1 - x0, y1 - y0
        role = _classify_lottery_cube_group(cubes)
        seed = 500 + idx * 7

        if role == "cutout_glass":
            _paint_cutout_glass(img, x0, y0, x1, y1)
        elif role == "emerald_gem":
            paint_gem(img, x0, y0, x1, y1, "emerald")
        elif role == "ruby_gem":
            paint_gem(img, x0, y0, x1, y1, "ruby")
        elif role == "tube_potion_core":
            # Warm Enchanted Amethyst-Blaze potion core inside glass tubes
            for dy in range(h):
                for dx in range(w):
                    hv = phash(dx, dy, seed)
                    if dy == 0 or hv > 215:
                        col = GOLD_SPARK if dy < h // 2 else AMETHYST_SPEC
                    elif dy < h // 2:
                        col = AMETHYST_HI if hv > 90 else AMETHYST_MID
                    else:
                        col = GOLD_HI if hv > 120 else AMETHYST_BASE
                    put(img, x0 + dx, y0 + dy, col)
        elif role == "celestial_core":
            # Celestial Die & facets: Gold filigree + radiant Amethyst/Gold crystal
            if w >= 3 and h >= 3:
                paint_gold_trim(img, x0, y0, x1, y1, seed=seed)
                paint_amethyst(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, seed=seed + 1)
                put(img, x0 + 1, y0 + 1, GOLD_SPARK)
            else:
                paint_amethyst(img, x0, y0, x1, y1, seed=seed)
        elif role == "amethyst_crystal":
            paint_amethyst(img, x0, y0, x1, y1, seed=seed)
        elif role == "crimson_orb":
            # Redstone/Crimson lever knob with specular highlight
            paint_gem(img, x0, y0, x1, y1, "ruby")
            put(img, x0, y0, GOLD_SPARK)
        elif role == "end_portal_starfield":
            _paint_end_starfield(img, x0, y0, x1, y1, seed=seed)
        elif role == "blaze_chute_glow":
            # Warm enchanting hearth glow inside dispenser chute
            for dy in range(h):
                for dx in range(w):
                    hv = phash(dx, dy, seed)
                    if dx in (0, w - 1) or dy in (0, h - 1):
                        col = CRIMSON_DEEP if hv < 120 else CRIMSON_BASE
                    elif hv > 195:
                        col = GOLD_SPARK
                    elif hv > 110:
                        col = GOLD_HI
                    else:
                        col = GOLD_BASE
                    put(img, x0 + dx, y0 + dy, col)
        elif role == "dial_face":
            _paint_dial_face(img, x0, y0, x1, y1)
        elif role == "gold_trim":
            paint_gold_trim(img, x0, y0, x1, y1, seed=seed)
        elif role == "crimson_cloth":
            paint_crimson_cloth(img, x0, y0, x1, y1, seed=seed, gold_trim=(w >= 4 and h >= 4))
        elif role == "end_stone_bricks":
            paint_end_stone_bricks(img, x0, y0, x1, y1, seed=seed, brick_w=5, brick_h=3, top_bevel=True)
            # Clean gold corner studs on the 15x15 pedestal plate only
            if "pedestal_plate" in cubes and w >= 15 and h >= 15:
                for gx, gy in ((x0, y0), (x1 - 1, y0), (x0, y1 - 1), (x1 - 1, y1 - 1)):
                    put(img, gx, gy, GOLD_BASE)
        elif role == "purpur_pillar":
            paint_purpur_pillar(img, x0, y0, x1, y1, seed=seed)
            if h >= 10:
                for dx in range(w):
                    put(img, x0 + dx, y0, GOLD_HI)
                    put(img, x0 + dx, y1 - 1, GOLD_SH)
        elif role == "purpur_bricks":
            paint_purpur_bricks(img, x0, y0, x1, y1, seed=seed, tile_w=4, tile_h=3, border_gold=(w >= 8 and h >= 8))
        elif role == "bookshelf_wood":
            _paint_bookshelf_panel(img, x0, y0, x1, y1, seed=seed)
        elif role == "dark_oak_cabinet":
            paint_wood_planks(img, x0, y0, x1, y1, seed=seed, dark=True, frame_bevel=True)
            # Waist trim belt / large panels get oak inlay line + gold clasp accent
            if w >= 12 and h >= 12:
                for i in range(2, w - 2):
                    put(img, x0 + i, y0 + 2, GOLD_BASE if i in (2, w - 3) else OAK_MID)
                    put(img, x0 + i, y1 - 3, GOLD_SH if i in (2, w - 3) else OAK_DEEP)
            elif w >= 14 and h == 2:
                # Waist belt side strip: Dark Oak + Gold center clasp
                put(img, x0 + w // 2 - 1, y0, GOLD_HI)
                put(img, x0 + w // 2, y0, GOLD_BASE)
                put(img, x0 + w // 2 - 1, y0 + 1, GOLD_SH)
                put(img, x0 + w // 2, y0 + 1, GOLD_DEEP)
        elif role == "crying_obsidian":
            paint_obsidian(img, x0, y0, x1, y1, seed=seed, crying=True, bevel=True)
            if w >= 3 and h >= 3:
                put(img, x0, y0, GOLD_HI)
                put(img, x0 + w - 1, y0 + h - 1, GOLD_SH)
        else:  # obsidian_trimmed
            paint_obsidian(img, x0, y0, x1, y1, seed=seed, crying=(w >= 8 and h >= 8), bevel=True)
            if w >= 10 and h >= 9:
                # Subtle brass corner rivets & recessed louver shadows for back frames/vents
                put(img, x0 + 1, y0 + 1, GOLD_BASE)
                put(img, x1 - 2, y0 + 1, GOLD_BASE)
                put(img, x0 + 1, y1 - 2, GOLD_SH)
                put(img, x1 - 2, y1 - 2, GOLD_SH)
                if any("vent" in n or "slat" in n for n in cubes):
                    for sy in range(y0 + 2, y1 - 2, 2):
                        for sx in range(x0 + 2, x1 - 2):
                            put(img, sx, sy, PURPUR_MORTAR if (sx + sy) % 3 == 0 else OBS_EDGE)
                            if sy + 1 < y1 - 2:
                                put(img, sx, sy + 1, OBS_DEEP)

    return img


# ==============================================================================
# Driver & Verification
# ==============================================================================

def main() -> None:
    TEX_BLOCK_DIR.mkdir(parents=True, exist_ok=True)

    outputs: dict[str, Image.Image] = {
        "universal_polymerizer_atlas.png": generate_polymerizer_atlas(),
        "personal_space_gate_atlas.png": _paint_personal_space_atlas(is_return=False),
        "personal_space_return_atlas.png": _paint_personal_space_atlas(is_return=True),
        "universal_lottery_machine.png": generate_lottery_machine_atlas(),
    }
    outputs.update(generate_phoenix_pod_textures())

    expected_sizes = {
        "universal_polymerizer_atlas.png": (64, 64),
        "universal_lottery_machine.png": (128, 128),
        "personal_space_gate_atlas.png": (64, 64),
        "personal_space_return_atlas.png": (64, 64),
        "phoenix_pod_base.png": (16, 16),
        "phoenix_pod_top.png": (16, 16),
        "phoenix_pod_glass.png": (16, 16),
        "phoenix_pod_glass_liquid.png": (16, 16),
    }

    for name, img in outputs.items():
        assert img.size == expected_sizes[name], f"{name}: bad size {img.size}"
        assert img.mode == "RGBA", f"{name}: bad mode {img.mode}"
        out_path = TEX_BLOCK_DIR / name
        img.save(out_path)
        print(f"Wrote {out_path.relative_to(REPO_ROOT)} ({img.size[0]}x{img.size[1]})")


if __name__ == "__main__":
    main()
