#!/usr/bin/env python3
"""
High-craft Create / Ars Nouveau / Calamity-grade fantasy-arcane pixel-art generator
for Universal Tool items (32x32), skill icons (32x32), and Counter Guard effects (64x64).

Owned outputs:
- src/main/resources/assets/universal_tool/textures/item/*.png (all 17 items, 32x32, binary alpha)
- src/main/resources/assets/universal_tool/models/item/lightning_spear.json (UV verified)
- src/main/resources/assets/universal_tool/textures/gui/skills/*.png (all 20 skill icons, 32x32)
- src/main/resources/assets/universal_tool/textures/misc/counter_guard_aegis.png (64x64)
- src/main/resources/assets/universal_tool/textures/misc/counter_guard_ring.png (64x64)
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "universal_tool"
ITEM_DIR = ASSETS / "textures" / "item"
SKILL_DIR = ASSETS / "textures" / "gui" / "skills"
MISC_DIR = ASSETS / "textures" / "misc"
SPEAR_MODEL_PATH = ASSETS / "models" / "item" / "lightning_spear.json"

RGBA = Tuple[int, int, int, int]
TRANSPARENT: RGBA = (0, 0, 0, 0)


def hex_rgba(h: str, a: int = 255) -> RGBA:
    h = h.lstrip("#")
    if len(h) == 8:
        return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16)
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def blend(c1: RGBA, c2: RGBA, t: float) -> RGBA:
    t = max(0.0, min(1.0, t))
    return (
        int(round(c1[0] + (c2[0] - c1[0]) * t)),
        int(round(c1[1] + (c2[1] - c1[1]) * t)),
        int(round(c1[2] + (c2[2] - c1[2]) * t)),
        int(round(c1[3] + (c2[3] - c1[3]) * t)),
    )


def darken(c: RGBA, factor: float = 0.28, min_rgb: int = 18) -> RGBA:
    if c[3] == 0:
        return TRANSPARENT
    return (
        max(min_rgb, int(c[0] * factor)),
        max(min_rgb, int(c[1] * factor)),
        max(min_rgb, int(c[2] * factor)),
        255,
    )


def from_grid(rows: Sequence[str], palette: Dict[str, RGBA], expected_w: int = 32, expected_h: int = 32) -> Image.Image:
    h = len(rows)
    assert h == expected_h, f"Expected {expected_h} rows, got {h}"
    w = len(rows[0])
    assert w == expected_w, f"Expected width {expected_w}, got {w}"
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    px = img.load()
    for y, row in enumerate(rows):
        assert len(row) == w, f"Row {y} has length {len(row)}, expected {w}"
        for x, ch in enumerate(row):
            if ch in (".", " "):
                continue
            assert ch in palette, f"Unknown palette char {ch!r} at ({x}, {y})"
            px[x, y] = palette[ch]
    return img


def apply_auto_outline(img: Image.Image, dark_factor: float = 0.28, outline_override: RGBA | None = None) -> Image.Image:
    """Adds a crisp 1px 4-connected tinted dark outline around opaque pixels."""
    w, h = img.size
    src = img.load()
    out = img.copy()
    dst = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] > 0:
                continue
            neighbors = []
            for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] > 0:
                    neighbors.append(src[nx, ny])
            if neighbors:
                if outline_override is not None:
                    dst[x, y] = outline_override
                else:
                    # Pick darkest neighbor for cohesive hue-shifted outline
                    best = min(neighbors, key=lambda c: c[0] + c[1] + c[2])
                    dst[x, y] = darken(best, dark_factor, min_rgb=18)
    return out


class PixelCanvas:
    """Crisp 32x32 pixel drawing surface with helper primitives and exact binary alpha."""

    def __init__(self, w: int = 32, h: int = 32) -> None:
        self.w = w
        self.h = h
        self.img = Image.new("RGBA", (w, h), TRANSPARENT)
        self.px = self.img.load()

    def set(self, x: int, y: int, col: RGBA) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = col

    def get(self, x: int, y: int) -> RGBA:
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[x, y]
        return TRANSPARENT

    def fill_rect(self, x0: int, y0: int, x1: int, y1: int, col: RGBA) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, col)

    def line(self, x0: int, y0: int, x1: int, y1: int, col: RGBA) -> None:
        dx = abs(x1 - x0)
        dy = -abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx + dy
        while True:
            self.set(x0, y0, col)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def fill_ellipse(self, cx: float, cy: float, rx: float, ry: float, col: RGBA, angle_deg: float = 0.0) -> None:
        ang = math.radians(angle_deg)
        ca, sa = math.cos(ang), math.sin(ang)
        for y in range(self.h):
            for x in range(self.w):
                dx = x - cx
                dy = y - cy
                u = dx * ca + dy * sa
                v = -dx * sa + dy * ca
                if (u / rx) ** 2 + (v / ry) ** 2 <= 1.0:
                    self.set(x, y, col)

    def fill_polygon(self, pts: Sequence[Tuple[float, float]], col: RGBA) -> None:
        n = len(pts)
        for y in range(self.h):
            py = y + 0.5
            for x in range(self.w):
                px = x + 0.5
                inside = False
                j = n - 1
                for i in range(n):
                    xi, yi = pts[i]
                    xj, yj = pts[j]
                    if ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / ((yj - yi) or 1e-9) + xi):
                        inside = not inside
                    j = i
                if inside:
                    self.set(x, y, col)

    def paste_grid(self, x0: int, y0: int, rows: Sequence[str], pal: Dict[str, RGBA]) -> None:
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch not in (".", " "):
                    self.set(x0 + dx, y0 + dy, pal[ch])

    def finalize(self, outline: bool = True, dark_factor: float = 0.28, outline_col: RGBA | None = None) -> Image.Image:
        if outline:
            return apply_auto_outline(self.img, dark_factor=dark_factor, outline_override=outline_col)
        return self.img


# ---------------------------------------------------------------------------
# 1. Six Universal Tools (32x32 Heroic Paxel-Halberd Silhouette)
# ---------------------------------------------------------------------------

UNIVERSAL_TOOL_GRID = [
    "................................",
    "................................",
    "...........mmmmmmm..........ss..",
    ".........mmMHHHHHMm........sMMs.",
    "........mMHHMMMMMMMm......sMHMs.",
    ".......mMHMMmmmmMMMMm....sMHMds.",
    ".......mHMMm....mMMMMm..sMHMMds.",
    ".......mHMm......mMMMMmsMHMMdds.",
    ".......mMd........mMMMMHMMddd...",
    "........d..........cCCCMMMd.....",
    "..................cCGGCMMmd.....",
    ".................dCCGCdmMMm.....",
    "................rRMdcc..mMMm....",
    "...............rRGMd.....mMMm...",
    "..............wWRMd.......mMMm..",
    ".............wWWRd.........mMMm.",
    "............lLWWd...........mMM.",
    "...........dLLLd............mMM.",
    "..........wWWd...............MM.",
    ".........lLLd................mM.",
    "........wWWd.................md.",
    ".......lLLd..................d..",
    "......wWWd......................",
    ".....lLLd.......................",
    "....wWWd........................",
    "...gGGd.........................",
    "..gGGd..........................",
    "..gGpg..........................",
    "..gpdd..........................",
    "...dd...........................",
    "................................",
    "................................",
]

# Legend for UNIVERSAL_TOOL_GRID:
# Blade / Head: H=edge highlight, M=head midtone, m=head dark, d=deep shadow, s=spear edge glint
# Crossguard / Brass / Rivets: C=collar bright, c=collar mid, G=gem/inlay bright, g=pommel/guard gold
# Shaft & Leather Grip: W=wood/steel highlight, w=wood/steel mid, L=leather light, l=leather dark, R/r=upper shaft, p=pommel core

TOOL_PALETTES: Dict[str, Dict[str, RGBA]] = {
    "wooden": {
        "H": hex_rgba("#D6A868"), "M": hex_rgba("#A87742"), "m": hex_rgba("#7D5229"), "d": hex_rgba("#523217"), "s": hex_rgba("#F2D29B"),
        "C": hex_rgba("#C2C7CC"), "c": hex_rgba("#8A9199"), "G": hex_rgba("#E8C468"), "g": hex_rgba("#99A0A8"),
        "W": hex_rgba("#9E6F3F"), "w": hex_rgba("#754E28"), "L": hex_rgba("#8C4A2B"), "l": hex_rgba("#5E2D18"),
        "R": hex_rgba("#9E6F3F"), "r": hex_rgba("#754E28"), "p": hex_rgba("#D6B15E"),
    },
    "stone": {
        "H": hex_rgba("#C7CCD1"), "M": hex_rgba("#8F959E"), "m": hex_rgba("#646973"), "d": hex_rgba("#42464F"), "s": hex_rgba("#E4E8ED"),
        "C": hex_rgba("#E0B65A"), "c": hex_rgba("#A67C2E"), "G": hex_rgba("#F2D479"), "g": hex_rgba("#B58936"),
        "W": hex_rgba("#8F623B"), "w": hex_rgba("#664224"), "L": hex_rgba("#6E4832"), "l": hex_rgba("#4A2D1E"),
        "R": hex_rgba("#8F623B"), "r": hex_rgba("#664224"), "p": hex_rgba("#E0B65A"),
    },
    "iron": {
        "H": hex_rgba("#F5FAFF"), "M": hex_rgba("#B8C4D4"), "m": hex_rgba("#7A8799"), "d": hex_rgba("#4B5566"), "s": hex_rgba("#FFFFFF"),
        "C": hex_rgba("#E8BA58"), "c": hex_rgba("#A67729"), "G": hex_rgba("#FFE58F"), "g": hex_rgba("#D19E3E"),
        "W": hex_rgba("#7D5233"), "w": hex_rgba("#57351E"), "L": hex_rgba("#944D2D"), "l": hex_rgba("#632E18"),
        "R": hex_rgba("#8A97A8"), "r": hex_rgba("#5D6878"), "p": hex_rgba("#7EE8FF"),
    },
    "golden": {
        "H": hex_rgba("#FFF7B2"), "M": hex_rgba("#F0BD3C"), "m": hex_rgba("#C4821B"), "d": hex_rgba("#80490D"), "s": hex_rgba("#FFFFFF"),
        "C": hex_rgba("#FFF2A6"), "c": hex_rgba("#D49524"), "G": hex_rgba("#FF4265"), "g": hex_rgba("#F0BD3C"),
        "W": hex_rgba("#874C2A"), "w": hex_rgba("#5C2F18"), "L": hex_rgba("#A8243B"), "l": hex_rgba("#6E1324"),
        "R": hex_rgba("#D99E2B"), "r": hex_rgba("#996414"), "p": hex_rgba("#FF4265"),
    },
    "diamond": {
        "H": hex_rgba("#D6FFFF"), "M": hex_rgba("#4CE8E8"), "m": hex_rgba("#1BA3B5"), "d": hex_rgba("#11647A"), "s": hex_rgba("#FFFFFF"),
        "C": hex_rgba("#F5D376"), "c": hex_rgba("#BA8B2C"), "G": hex_rgba("#FFF7B2"), "g": hex_rgba("#4CE8E8"),
        "W": hex_rgba("#52687A"), "w": hex_rgba("#354554"), "L": hex_rgba("#2B7D8C"), "l": hex_rgba("#1A4E59"),
        "R": hex_rgba("#78E8F0"), "r": hex_rgba("#2B8FA3"), "p": hex_rgba("#A8FFFF"),
    },
    "netherite": {
        "H": hex_rgba("#D69BFF"), "M": hex_rgba("#60556B"), "m": hex_rgba("#3F374A"), "d": hex_rgba("#26202E"), "s": hex_rgba("#F4DEFF"),
        "C": hex_rgba("#F2C45A"), "c": hex_rgba("#AD7926"), "G": hex_rgba("#C86BFF"), "g": hex_rgba("#F2C45A"),
        "W": hex_rgba("#4A3B4F"), "w": hex_rgba("#2F2433"), "L": hex_rgba("#6B244B"), "l": hex_rgba("#451530"),
        "R": hex_rgba("#F2C45A"), "r": hex_rgba("#9E6C20"), "p": hex_rgba("#D982FF"),
    },
}


def make_universal_tools() -> Dict[str, Image.Image]:
    out: Dict[str, Image.Image] = {}
    for tier, pal in TOOL_PALETTES.items():
        base = from_grid(UNIVERSAL_TOOL_GRID, pal)
        # Add subtle tier-specific polish on the blade fuller/sparkle
        px = base.load()
        if tier == "diamond":
            px[25, 5] = hex_rgba("#FFFFFF")
            px[15, 4] = hex_rgba("#FFFFFF")
        elif tier == "netherite":
            # Gilded gold trim along beard & pick shoulder
            px[18, 8] = hex_rgba("#F2C45A")
            px[22, 11] = hex_rgba("#F2C45A")
            px[27, 17] = hex_rgba("#C86BFF")
        elif tier == "golden":
            px[16, 4] = hex_rgba("#FFFFFF")
        out[f"{tier}_universal_tool.png"] = apply_auto_outline(base, dark_factor=0.25)
    return out


# ---------------------------------------------------------------------------
# 2. Custom 32x32 High-Craft Items
# ---------------------------------------------------------------------------

def make_klein_bottle() -> Image.Image:
    c = PixelCanvas()
    g_edge = hex_rgba("#4AB0C7")
    g_mid = hex_rgba("#8AE6F2")
    g_hi = hex_rgba("#D9FFFF")
    g_spec = hex_rgba("#FFFFFF")
    g_deep = hex_rgba("#256880")
    brass_l = hex_rgba("#F2C96B")
    brass_m = hex_rgba("#C48D31")
    brass_d = hex_rgba("#7D5117")
    fl_deep = hex_rgba("#49298A")
    fl_mid = hex_rgba("#7D4BE0")
    fl_teal = hex_rgba("#3CE0D0")
    fl_glow = hex_rgba("#D6A8FF")

    # 1. Looping topological glass neck (arching top-right and plunging back into right side of bulb)
    loop_pts = [
        (14, 10), (14, 6), (16, 3), (20, 3), (24, 5), (27, 10),
        (27, 15), (25, 19), (21, 21), (18, 21),
    ]
    for i in range(len(loop_pts) - 1):
        x0, y0 = loop_pts[i]
        x1, y1 = loop_pts[i + 1]
        steps = 12
        for s in range(steps + 1):
            t = s / steps
            px_c = x0 + (x1 - x0) * t
            py_c = y0 + (y1 - y0) * t
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    d = math.hypot(dx, dy)
                    if d <= 2.3:
                        ix, iy = int(round(px_c + dx)), int(round(py_c + dy))
                        if d >= 1.45:
                            c.set(ix, iy, g_edge)
                        elif dx < 0 and dy < 0:
                            c.set(ix, iy, g_hi)
                        else:
                            c.set(ix, iy, g_mid)

    # 2. Main crystal flask bulb at bottom-left/center
    c.fill_ellipse(13.0, 21.0, 8.2, 7.2, g_edge)
    c.fill_ellipse(13.0, 21.0, 7.0, 6.0, g_deep)

    # 3. Swirling cosmic violet-teal fluid inside bulb
    for y in range(17, 27):
        for x in range(6, 21):
            if ((x - 13.0) / 6.8) ** 2 + ((y - 21.0) / 5.8) ** 2 <= 1.0:
                wave = 19.2 + 1.1 * math.sin((x - 7) * 0.55)
                if y >= wave:
                    # Swirling violet to luminous teal gradient
                    swirl = math.sin((x - 13) * 0.5 + (y - 22) * 0.6)
                    if swirl > 0.35:
                        c.set(x, y, fl_teal)
                    elif swirl > -0.2:
                        c.set(x, y, fl_mid)
                    else:
                        c.set(x, y, fl_deep)
                elif y >= wave - 1.0:
                    c.set(x, y, fl_glow)

    # 4. Internal topological tube entry funnel (where the neck threads back inside!)
    for y in range(19, 24):
        for x in range(15, 22):
            if abs(y - 21) <= 1 and x >= 17:
                c.set(x, y, g_hi if y == 20 else g_mid)
    c.set(16, 20, g_spec)
    c.set(16, 21, fl_teal)
    c.set(15, 21, fl_glow)

    # 5. Upper vertical glass neck & brass collar
    c.fill_rect(11, 6, 16, 14, g_edge)
    c.fill_rect(12, 6, 15, 14, g_mid)
    c.fill_rect(12, 7, 13, 13, g_hi)
    # Flared lip at top
    c.fill_rect(10, 4, 17, 5, g_hi)
    c.fill_rect(11, 5, 16, 5, g_edge)
    # Brass collar around neck
    c.fill_rect(10, 11, 17, 13, brass_d)
    c.fill_rect(10, 11, 17, 12, brass_m)
    c.fill_rect(11, 11, 14, 11, brass_l)

    # 6. Specular glass gleams & rising cosmic bubbles
    for px_x, px_y in ((8, 18), (8, 19), (7, 20), (9, 17), (10, 17), (24, 7), (25, 8), (25, 9)):
        c.set(px_x, px_y, g_spec)
    c.set(11, 23, g_spec)
    c.set(13, 25, fl_teal)
    c.set(10, 21, g_hi)
    # Highlight outer rim of loop
    c.set(18, 3, g_spec)
    c.set(19, 3, g_spec)

    return c.finalize(outline=True, dark_factor=0.25)


def make_teleport_codex() -> Image.Image:
    # Crisp angled 3/4 ornate leather Grimoire (rectangular isometric cover, spine ribs, gold corners, cream pages, astrolabe gem)
    c = PixelCanvas()
    g_hi = hex_rgba("#FFE58F")
    g_m = hex_rgba("#D69E36")
    g_d = hex_rgba("#875714")
    cov_hi = hex_rgba("#8F264A")
    cov_m = hex_rgba("#661632")
    cov_d = hex_rgba("#400B1E")
    sp_m = hex_rgba("#4D1126")
    sp_hi = hex_rgba("#A8385C")
    pg_hi = hex_rgba("#FAF0D7")
    pg_m = hex_rgba("#DECAA0")
    pg_d = hex_rgba("#B39A6D")
    cy_hi = hex_rgba("#FFFFFF")
    cy_m = hex_rgba("#A8FFFF")
    cy_d = hex_rgba("#32C7C7")
    rib = hex_rgba("#D62B45")

    # Isometric book page block (bottom thickness)
    c.fill_polygon([(5, 19), (15, 29), (27, 17), (17, 7)], pg_m)
    # Layered cream page edge at bottom-right
    c.fill_polygon([(14, 24), (15, 28), (27, 16), (26, 12)], pg_hi)
    c.line(15, 26, 26, 15, pg_m)
    c.line(15, 27, 26, 16, pg_d)

    # Bottom back cover edge
    c.line(14, 29, 27, 16, cov_d)
    c.line(15, 29, 27, 17, g_d)

    # Front leather cover (crisp angled rectangle!)
    cover_pts = [(4, 15), (17, 2), (27, 12), (14, 25)]
    c.fill_polygon(cover_pts, cov_m)
    c.line(5, 14, 17, 2, cov_hi)
    c.line(17, 3, 26, 12, cov_hi)

    # Spine along bottom-left edge + 3 raised leather & gold spine ribs
    c.fill_polygon([(4, 15), (14, 25), (14, 29), (4, 19)], sp_m)
    for rx, ry in ((6, 18), (9, 21), (12, 24)):
        c.set(rx, ry, sp_hi)
        c.set(rx, ry + 1, g_m)
        c.set(rx + 1, ry + 1, g_hi)

    # Crimson silk bookmark ribbon peeking out bottom
    c.fill_rect(18, 25, 19, 29, rib)
    c.set(18, 29, hex_rgba("#FF6B81"))

    # Filigree gold corner brackets on front cover
    c.line(15, 4, 17, 2, g_hi)
    c.line(17, 2, 19, 4, g_hi)
    c.set(17, 4, g_m)
    c.line(24, 10, 26, 12, g_m)
    c.line(26, 12, 24, 14, g_d)
    c.line(12, 22, 14, 24, g_m)
    c.line(14, 24, 16, 22, g_d)
    c.set(5, 14, g_hi)

    # Central brass astrolabe bezel & glowing cyan-aqua star jewel
    cx, cy = 15.5, 13.0
    for deg in range(0, 360, 12):
        rad = math.radians(deg)
        bx = int(round(cx + 4.3 * math.cos(rad)))
        by = int(round(cy + 4.3 * math.sin(rad)))
        c.set(bx, by, g_hi if bx + by < 28 else g_m)
    c.fill_ellipse(cx, cy, 3.0, 3.0, cy_d)
    c.fill_ellipse(cx, cy, 2.0, 2.0, cy_m)
    c.set(15, 12, cy_hi)
    c.set(15, 13, cy_hi)

    return c.finalize(outline=True, dark_factor=0.25)


def make_teleport_scroll() -> Image.Image:
    # Crisp straight 45-degree rolled parchment scroll with turned brass rod ends, crimson ribbon & wax seal
    rows = [
        "................................",
        "................................",
        ".......................bb.......",
        "......................bBBb......",
        "....................ppBBbp......",
        "...................pPPppppp.....",
        "..................pPPPPPsPPp....",
        ".................pPPPPpsSPPp....",
        "................pPPPpppsPPp.....",
        "...............pPPPcPPPPpp......",
        "..............pPPPccPPPpp.......",
        ".............pPPPpPPPPpp........",
        "............pPPrrPPPPpp.........",
        "...........pPPrRRrPPpp..........",
        "..........pPPrRYYRrpp...........",
        ".........pPPrRYGGYrp............",
        "........pPPprRYGGYrp............",
        ".......pPPpprrRRRRrp............",
        "......pPPpppppprRrrp............",
        ".....pPPpccPPPPppppp............",
        "....pPPpccPPPPpppp..............",
        "...pPPppPPPPppp.................",
        "...pPPsPPPPpp...................",
        "..ppPsPPPPpp....................",
        ".bBBpPPPPp......................",
        "bBBbpppp........................",
        ".bb.............................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ]
    pal = {
        "P": hex_rgba("#F5E9CE"),
        "p": hex_rgba("#CBB68E"),
        "s": hex_rgba("#9E8660"),
        "b": hex_rgba("#9E6D24"),
        "B": hex_rgba("#F2C96B"),
        "r": hex_rgba("#8A1728"),
        "R": hex_rgba("#D62B45"),
        "Y": hex_rgba("#FFE27A"),
        "G": hex_rgba("#FF4F6D"),
        "c": hex_rgba("#3CE0D0"),
        "S": hex_rgba("#6E5636"),
    }
    return apply_auto_outline(from_grid(rows, pal), dark_factor=0.26)


def make_spatial_page() -> Image.Image:
    # Crisp tilted illuminated parchment folio with gilded compass border & cyan star constellation
    c = PixelCanvas()
    vellum_hi = hex_rgba("#FAF1DC")
    vellum_mid = hex_rgba("#EBDAB8")
    vellum_sh = hex_rgba("#C7B087")
    gold_l = hex_rgba("#F5CF73")
    gold_d = hex_rgba("#B5802B")
    ink = hex_rgba("#59412A")
    cyan_l = hex_rgba("#A8FFFF")
    cyan_m = hex_rgba("#3CE0D0")
    cyan_d = hex_rgba("#1D8FA3")

    # Tilted folio quad with folded top-right dog-ear
    poly = [(7.0, 5.0), (22.0, 3.0), (26.0, 7.0), (24.0, 26.0), (5.0, 28.0)]
    c.fill_polygon(poly, vellum_mid)

    # Top-left bright bevel, bottom/right parchment shadow
    for y in range(3, 29):
        for x in range(5, 27):
            if c.get(x, y)[3] == 0:
                continue
            if x <= 10 or y <= 7:
                c.set(x, y, vellum_hi)
            elif x >= 22 or y >= 24:
                c.set(x, y, vellum_sh)

    # Folded dog-ear at top-right (22,3)-(26,7)
    c.fill_polygon([(21.5, 3.0), (26.0, 7.0), (21.5, 7.5)], vellum_sh)
    c.set(22, 4, vellum_hi)
    c.set(22, 5, vellum_hi)
    c.set(23, 5, vellum_hi)

    # Gilded compass border inset
    c.line(8, 7, 20, 6, gold_l)
    c.line(7, 25, 22, 24, gold_d)
    c.line(8, 7, 7, 25, gold_l)
    c.line(23, 9, 22, 24, gold_d)
    # Corner compass pips
    for px_x, px_y in ((8, 7), (20, 6), (7, 25), (22, 24)):
        c.set(px_x, px_y, gold_l)

    # Arcane astrolabe circle & cyan constellation diagram in the middle
    cx, cy = 15.0, 15.5
    for deg in range(0, 360, 10):
        rad = math.radians(deg)
        c.set(int(round(cx + 5.2 * math.cos(rad))), int(round(cy + 5.2 * math.sin(rad))), gold_d if deg % 30 else gold_l)

    # Constellation lines
    stars = [(11, 12), (16, 10), (19, 15), (14, 19), (11, 17)]
    for i in range(len(stars) - 1):
        c.line(stars[i][0], stars[i][1], stars[i + 1][0], stars[i + 1][1], cyan_d)
    c.line(stars[0][0], stars[0][1], stars[2][0], stars[2][1], cyan_m)
    for sx, sy in stars:
        c.set(sx, sy, cyan_l)
    # Central bright star
    c.set(15, 15, hex_rgba("#FFFFFF"))
    c.set(15, 14, cyan_m)
    c.set(15, 16, cyan_m)
    c.set(14, 15, cyan_m)
    c.set(16, 15, cyan_m)
    # Tiny ink caption lines at bottom
    c.line(10, 22, 18, 21, ink)

    return c.finalize(outline=True, dark_factor=0.30)


def make_universal_repair_material() -> Image.Image:
    # Radiant faceted prismatic anvil-star crystal suspended inside a tilted filigree gold diamond frame!
    c = PixelCanvas()
    gold_hi = hex_rgba("#FFF2A6")
    gold_m = hex_rgba("#E0AD42")
    gold_d = hex_rgba("#8F5E1B")

    # Outer rotated filigree diamond frame (16-pixel radius)
    for y in range(2, 30):
        for x in range(2, 30):
            d = abs(x - 15.5) + abs(y - 15.5)
            if 11.2 <= d <= 13.6:
                if x + y < 31:
                    c.set(x, y, gold_hi if d < 12.4 else gold_m)
                else:
                    c.set(x, y, gold_m if d < 12.4 else gold_d)
            elif 7.5 <= d < 11.2:
                # Deep cosmic backing inside the frame so crystal pops
                c.set(x, y, hex_rgba("#241B3B"))

    # 4 cardinal filigree finials on the diamond tips
    for fx, fy in ((15, 2), (16, 2), (15, 29), (16, 29), (2, 15), (2, 16), (29, 15), (29, 16)):
        c.set(fx, fy, gold_hi)

    # Central radiant prismatic anvil-star crystal (symmetric faceted shape)
    c_top = hex_rgba("#A8FFFF")
    c_left = hex_rgba("#4AE0E0")
    c_right = hex_rgba("#E07BFF")
    c_bot = hex_rgba("#7B4CE0")
    c_core = hex_rgba("#FFFFFF")
    c_gold = hex_rgba("#FFE57A")

    # Anvil-star crystal silhouette inside center
    anvil_rows = [
        "......TT......",
        "....TTTTTT....",
        "..TTCCCCCCtT..",
        ".LLLLCCCCRRRR.",
        ".LLLLCCCCRRRR.",
        "...LLCCCCRR...",
        "....LCCCCR....",
        "....LBBBBb....",
        "...LLBBBBbb...",
        "..LLLBBBBbbb..",
        ".LLLLGGGGbbbb.",
    ]
    anvil_pal = {
        "T": c_top,
        "t": hex_rgba("#E0B8FF"),
        "C": c_core,
        "L": c_left,
        "R": c_right,
        "B": c_bot,
        "b": hex_rgba("#552CA8"),
        "G": c_gold,
    }
    c.paste_grid(9, 10, anvil_rows, anvil_pal)
    # Sparkle cross glints
    c.set(15, 8, c_core)
    c.set(16, 8, c_core)
    c.set(15, 22, c_gold)
    c.set(16, 22, c_gold)

    return c.finalize(outline=True, dark_factor=0.25)


def make_void_scrap() -> Image.Image:
    # Jagged forged void-obsidian metal ingot chunk with razor bevels and glowing violet-amethyst crystal fissures
    c = PixelCanvas()
    ob_hi = hex_rgba("#8A80B5")
    ob_m = hex_rgba("#463E66")
    ob_d = hex_rgba("#241E38")
    ob_top = hex_rgba("#62578C")
    v_deep = hex_rgba("#7B2CE0")
    v_mid = hex_rgba("#C27BFF")
    v_hi = hex_rgba("#FFE8FF")

    poly = [(5, 16), (13, 8), (25, 7), (27, 13), (23, 18), (26, 21), (19, 25), (6, 22)]
    c.fill_polygon(poly, ob_m)
    c.fill_polygon([(6, 15), (13, 8), (25, 7), (22, 13), (12, 16)], ob_top)
    c.fill_polygon([(6, 22), (19, 25), (25, 21), (21, 18), (11, 20)], ob_d)
    c.line(6, 15, 13, 8, ob_hi)
    c.line(13, 8, 24, 7, ob_hi)
    c.line(12, 16, 22, 13, ob_hi)

    fissures = [
        (10, 14), (11, 14), (12, 13), (13, 12), (14, 12), (15, 11), (16, 11),
        (14, 13), (15, 14), (16, 15), (17, 16), (18, 16), (19, 17),
        (16, 12), (17, 12), (18, 11), (19, 10), (20, 10),
    ]
    for fx, fy in fissures:
        for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            if c.get(fx + dx, fy + dy)[3] > 0:
                c.set(fx + dx, fy + dy, v_deep)
    for fx, fy in fissures:
        c.set(fx, fy, v_mid)
    for fx, fy in ((13, 12), (15, 11), (15, 14), (17, 16), (18, 11)):
        c.set(fx, fy, v_hi)

    c.set(24, 15, v_deep)
    c.set(25, 15, v_mid)
    c.set(26, 15, v_hi)
    c.set(24, 16, v_mid)

    return c.finalize(outline=True, dark_factor=0.24)


def make_void_essence() -> Image.Image:
    # Luminous faceted cosmic-violet star pearl orb with tilted golden orbital ring & starlight glints
    c = PixelCanvas()
    cx, cy = 15.5, 15.5

    # Back half of tilted golden orbital ring (above/behind orb)
    gold_hi = hex_rgba("#FFF0A6")
    gold_m = hex_rgba("#D6A038")
    gold_d = hex_rgba("#875714")
    ang = math.radians(-28.0)
    ca, sa = math.cos(ang), math.sin(ang)

    def draw_ring(front_only: bool) -> None:
        for deg in range(0, 360, 2):
            rad = math.radians(deg)
            rx = 12.5 * math.cos(rad)
            ry = 4.2 * math.sin(rad)
            # In local ring coords, ry < 0 is behind the orb, ry >= 0 is in front
            if front_only and ry < 0:
                continue
            if (not front_only) and ry >= 0:
                continue
            x = int(round(cx + rx * ca - ry * sa))
            y = int(round(cy + rx * sa + ry * ca))
            col = gold_hi if (front_only and deg % 20 < 8) else (gold_m if front_only else gold_d)
            c.set(x, y, col)
            if front_only:
                c.set(x, y + 1, gold_d)

    draw_ring(front_only=False)

    # Central cosmic-violet star pearl orb
    for y in range(7, 25):
        for x in range(7, 25):
            r = math.hypot(x - cx, y - cy)
            if r <= 7.8:
                # Top-left highlight offset
                dl = math.hypot(x - 13.0, y - 12.5)
                if dl < 2.1:
                    c.set(x, y, hex_rgba("#FFFFFF"))
                elif dl < 4.2:
                    c.set(x, y, hex_rgba("#E0B8FF"))
                elif r < 5.8:
                    c.set(x, y, hex_rgba("#9B59F5"))
                elif r < 7.0:
                    c.set(x, y, hex_rgba("#622BB8"))
                else:
                    c.set(x, y, hex_rgba("#3B1978"))

    # Crisp 4-point star glint inside orb core
    c.set(13, 12, hex_rgba("#FFFFFF"))
    c.set(12, 12, hex_rgba("#FFF0FF"))
    c.set(14, 12, hex_rgba("#FFF0FF"))
    c.set(13, 11, hex_rgba("#FFF0FF"))
    c.set(13, 13, hex_rgba("#FFF0FF"))

    draw_ring(front_only=True)

    # Two floating golden star motes
    c.set(6, 9, gold_hi)
    c.set(25, 22, gold_hi)

    return c.finalize(outline=True, dark_factor=0.26)


def make_dna_syringe() -> Image.Image:
    # Sleek straight 45-degree diagonal brass-and-glass bio-injector
    c = PixelCanvas()
    needle_hi = hex_rgba("#F2FAFF")
    needle_m = hex_rgba("#A6B8CC")
    drop = hex_rgba("#6BFFCC")
    brass_hi = hex_rgba("#FFE58F")
    brass_m = hex_rgba("#D19A36")
    brass_d = hex_rgba("#7D5117")
    glass_hi = hex_rgba("#FFFFFF")
    glass_m = hex_rgba("#B5F5FF")
    glass_edge = hex_rgba("#4CA6B8")
    el_m = hex_rgba("#1CC995")
    el_hi = hex_rgba("#82FFE0")

    c.line(21, 10, 29, 2, needle_hi)
    c.set(22, 10, needle_m)
    c.set(29, 2, drop)

    for y in range(8, 24):
        for x in range(8, 24):
            s = x - y
            d = (x + y) - 31
            if -8 <= s <= 8 and -2 <= d <= 2:
                if abs(d) == 2:
                    c.set(x, y, glass_edge)
                elif d == -1:
                    c.set(x, y, glass_hi if s % 4 == 0 else glass_m)
                else:
                    wave = math.sin(s * 0.9)
                    if (d == 0 and wave > 0.2) or (d == 1 and wave < -0.2):
                        c.set(x, y, el_hi)
                    else:
                        c.set(x, y, el_m)

    for y in range(7, 25):
        for x in range(7, 25):
            s = x - y
            d = (x + y) - 31
            if s in (9, 10) and -2 <= d <= 2:
                c.set(x, y, brass_hi if d < 0 else (brass_m if d == 0 else brass_d))
            elif s in (-9, -10) and -3 <= d <= 3:
                c.set(x, y, brass_hi if d < 0 else (brass_m if d == 0 else brass_d))
            elif s == 11 and -1 <= d <= 1:
                c.set(x, y, brass_m)

    c.line(5, 26, 10, 21, needle_m)
    c.line(5, 25, 9, 21, needle_hi)
    c.line(3, 24, 7, 28, brass_m)
    c.line(3, 23, 6, 26, brass_hi)

    return c.finalize(outline=True, dark_factor=0.25)


def make_dna_sample() -> Image.Image:
    # Straight tilted corked & brass-capped crystal vial with glowing magenta-gold double-helix strand
    c = PixelCanvas()
    cork_hi = hex_rgba("#D69E66")
    cork_d = hex_rgba("#87532B")
    brass_hi = hex_rgba("#FFE58F")
    brass_m = hex_rgba("#D19A36")
    brass_d = hex_rgba("#7D5117")
    glass_edge = hex_rgba("#4CA6B8")
    glass_hi = hex_rgba("#FFFFFF")
    glass_m = hex_rgba("#BDF5FF")
    fluid = hex_rgba("#241645")
    dna_m = hex_rgba("#FF4DA6")
    dna_g = hex_rgba("#FFE57A")

    for y in range(4, 28):
        for x in range(4, 28):
            dx = x - 15.0
            dy = y - 16.0
            along = (dx - dy) * 0.7071
            perp = (dx + dy) * 0.7071

            in_cap_bottom = along < -6.0 and math.hypot(along + 6.0, perp) <= 4.3
            in_body = -6.0 <= along <= 5.5 and abs(perp) <= 4.3
            if in_cap_bottom or in_body:
                edge_dist = 4.3 - (math.hypot(along + 6.0, perp) if along < -6.0 else abs(perp))
                if edge_dist <= 1.0:
                    c.set(x, y, glass_edge)
                elif perp <= -2.1 and -5.0 <= along <= 4.5:
                    c.set(x, y, glass_hi if int(along) % 3 != 0 else glass_m)
                else:
                    c.set(x, y, fluid)
                    s1 = 2.0 * math.sin(along * 0.75)
                    s2 = -s1
                    if abs(perp - s1) <= 0.75:
                        c.set(x, y, dna_m)
                    elif abs(perp - s2) <= 0.75:
                        c.set(x, y, dna_g)
                    elif abs(math.sin(along * 0.75)) < 0.35 and abs(perp) < 1.8:
                        c.set(x, y, hex_rgba("#FFA8D6"))
            elif 5.5 < along <= 7.8 and abs(perp) <= 4.6:
                c.set(x, y, brass_hi if perp < -1.0 else (brass_m if perp < 1.8 else brass_d))
            elif 7.8 < along <= 10.8 and abs(perp) <= 3.2:
                c.set(x, y, cork_hi if perp < 0.5 else cork_d)

    return c.finalize(outline=True, dark_factor=0.25)


def make_nutrient_solution() -> Image.Image:
    # Ornate rounded alchemical flask with brass collar, cork & bioluminescent emerald-aqua elixir + rising bubbles
    c = PixelCanvas()
    cork_l = hex_rgba("#C99056")
    cork_d = hex_rgba("#7D4E28")
    brass_l = hex_rgba("#F2C96B")
    brass_m = hex_rgba("#C48D31")
    brass_d = hex_rgba("#7D5117")
    g_edge = hex_rgba("#4CA6B8")
    g_hi = hex_rgba("#D6FFFF")
    g_spec = hex_rgba("#FFFFFF")
    el_deep = hex_rgba("#0E6650")
    el_mid = hex_rgba("#24C998")
    el_hi = hex_rgba("#82FFE0")

    # Cork stopper (y=3..6, x=13..18)
    c.fill_rect(13, 3, 18, 6, cork_l)
    c.fill_rect(17, 3, 18, 6, cork_d)
    # Flared glass neck & brass collar (y=6..12)
    c.fill_rect(11, 6, 20, 7, g_hi)
    c.fill_rect(12, 8, 19, 12, g_edge)
    c.fill_rect(13, 8, 18, 12, hex_rgba("#99ECF5"))
    # Brass neck ring
    c.fill_rect(11, 9, 20, 11, brass_m)
    c.fill_rect(12, 9, 16, 10, brass_l)
    c.fill_rect(18, 9, 20, 11, brass_d)

    # Rounded belly flask (cx=15.5, cy=20.5, rx=9.0, ry=8.0)
    c.fill_ellipse(15.5, 20.5, 9.0, 8.0, g_edge)
    c.fill_ellipse(15.5, 20.5, 7.8, 6.8, hex_rgba("#163842"))

    # Bioluminescent emerald-aqua elixir filling bottom 70%
    for y in range(15, 28):
        for x in range(7, 25):
            if ((x - 15.5) / 7.6) ** 2 + ((y - 20.5) / 6.6) ** 2 <= 1.0:
                if y >= 18:
                    # Meniscus glow at y=18
                    if y == 18:
                        c.set(x, y, el_hi)
                    elif x < 14 and y < 24:
                        c.set(x, y, el_mid)
                    elif x > 18 or y > 24:
                        c.set(x, y, el_deep)
                    else:
                        c.set(x, y, el_mid)

    # Ornate brass shoulder strap accents on left & right of belly
    for px_x, px_y in ((7, 19), (7, 20), (8, 18), (24, 19), (24, 20), (23, 18)):
        c.set(px_x, px_y, brass_l if px_x < 16 else brass_m)

    # Rising luminous bubbles & crisp specular glass gleam
    c.set(14, 22, el_hi)
    c.set(17, 20, g_spec)
    c.set(15, 16, el_hi)
    for gx, gy in ((10, 16), (9, 17), (9, 18), (9, 19), (10, 15)):
        c.set(gx, gy, g_spec)

    return c.finalize(outline=True, dark_factor=0.25)


# ---------------------------------------------------------------------------
# 3. Lightning Spear 32x32 Atlas + Model UV Verification
# ---------------------------------------------------------------------------

def make_lightning_spear() -> Image.Image:
    """
    32x32 1x-texel-density atlas matching lightning_spear.json elements:
    - [0..2, 0..22]   shaft_main (2x22)
    - [3..6, 0..6]    shaft_grip (3x6)
    - [3..6, 7..8]    shaft_ring_lower (3x1)
    - [3..6, 9..10]   shaft_ring_upper (3x1)
    - [3..7, 11..13]  spearhead_base (4x2)
    - [3..6, 14..18]  pommel_crystal (3x4)
    - [3..5, 19..21]  pommel_finial (2x2)
    - [8..11, 0..10]  spearhead_core north/south (3x10)
    - [12..14, 0..10] spearhead_core east/west/up/down (2x10)
    - [15..17, 0..5]  spearhead_tip (2x5)
    - [8..11, 11..16] wing_lower (3x5)
    - [12..15, 11..17] wing_tip (3x6)
    """
    img = Image.new("RGBA", (32, 32), TRANSPARENT)

    def paint_block(x0: int, y0: int, rows: Sequence[str], pal: Dict[str, RGBA]) -> None:
        sub = from_grid(rows, pal, expected_w=len(rows[0]), expected_h=len(rows))
        img.paste(sub, (x0, y0))

    # Rich walnut shaft with inlaid brass spiral conductors
    shaft_pal = {
        "d": hex_rgba("#382214"),
        "m": hex_rgba("#5C3A21"),
        "l": hex_rgba("#7D5232"),
        "g": hex_rgba("#F2C96B"),
        "b": hex_rgba("#BD7E2B"),
    }
    paint_block(0, 0, [
        "lm", "md", "gb", "md",
        "lm", "bg", "lm", "md",
        "gb", "md", "lm", "bg",
        "lm", "md", "gb", "md",
        "lm", "bg", "lm", "md",
        "gb", "md",
    ], shaft_pal)

    # Stitched leather grip (3x6)
    grip_pal = {
        "d": hex_rgba("#401C12"),
        "m": hex_rgba("#6E3220"),
        "l": hex_rgba("#944830"),
        "s": hex_rgba("#E0B14C"),
    }
    paint_block(3, 0, ["lmd", "msd", "lmd", "msd", "lmd", "mdd"], grip_pal)

    # Burnished storm-gold rings & bronze spearhead crossguard
    gold_pal = {
        "d": hex_rgba("#875414"),
        "m": hex_rgba("#D69E36"),
        "l": hex_rgba("#FFE580"),
        "s": hex_rgba("#FFFAD6"),
    }
    paint_block(3, 7, ["smd"], gold_pal)
    paint_block(3, 9, ["smd"], gold_pal)

    guard_pal = {
        "d": hex_rgba("#5E3214"),
        "m": hex_rgba("#A86028"),
        "l": hex_rgba("#F0BA46"),
        "s": hex_rgba("#FFF2A6"),
    }
    paint_block(3, 11, ["slmd", "mmdd"], guard_pal)

    # Crackling sky-cyan storm crystal pommel (3x4) + gold finial (2x2)
    crys_pal = {
        "d": hex_rgba("#18698F"),
        "m": hex_rgba("#3CE0F5"),
        "l": hex_rgba("#A8FFFF"),
        "s": hex_rgba("#FFFFFF"),
    }
    paint_block(3, 14, ["slm", "msd", "lmd", "mdd"], crys_pal)
    paint_block(3, 19, ["sl", "md"], gold_pal)

    # Spearhead core & storm wings: storm-gold frame + electric sky-cyan/white lightning core
    blade_pal = {
        "d": hex_rgba("#7D4A14"),
        "m": hex_rgba("#CC8C28"),
        "l": hex_rgba("#FFE26B"),
        "c": hex_rgba("#3CE0F5"),
        "w": hex_rgba("#B8FFFF"),
        "s": hex_rgba("#FFFFFF"),
    }
    paint_block(8, 0, [
        "lsl", "swl", "mcw", "lsm", "wcw",
        "lsm", "lcl", "swm", "mwm", "dmd",
    ], blade_pal)
    paint_block(12, 0, [
        "sw", "wc", "lc", "mc", "wc",
        "mc", "lc", "mc", "md", "dd",
    ], blade_pal)
    paint_block(15, 0, ["ss", "sw", "wc", "lc", "lm"], blade_pal)
    paint_block(8, 11, ["lcl", "lwl", "msw", "mmd", "ddd"], blade_pal)
    paint_block(12, 11, ["ssl", "lwl", "mcw", "swm", "lmd", "ddd"], blade_pal)

    return img


def verify_and_update_lightning_spear_model() -> None:
    data = json.loads(SPEAR_MODEL_PATH.read_text(encoding="utf-8"))
    data["texture_size"] = [32, 32]

    uv_px_by_name = {
        "shaft_main": (0, 0, 2, 22),
        "shaft_grip": (3, 0, 6, 6),
        "shaft_ring_lower": (3, 7, 6, 8),
        "shaft_ring_upper": (3, 9, 6, 10),
        "spearhead_base": (3, 11, 7, 13),
        "pommel_crystal": (3, 14, 6, 18),
        "pommel_finial": (3, 19, 5, 21),
        "spearhead_tip": (15, 0, 17, 5),
        "wing_left_lower": (8, 11, 11, 16),
        "wing_right_lower": (8, 11, 11, 16),
        "wing_left_tip": (12, 11, 15, 17),
        "wing_right_tip": (12, 11, 15, 17),
    }

    for elem in data["elements"]:
        name = elem["name"]
        if name == "spearhead_core":
            ns_uv = [8 / 2, 0 / 2, 11 / 2, 10 / 2]
            ew_uv = [12 / 2, 0 / 2, 14 / 2, 10 / 2]
            for face_name, face in elem["faces"].items():
                face["uv"] = ns_uv if face_name in ("north", "south") else ew_uv
        elif name in uv_px_by_name:
            x0, y0, x1, y1 = uv_px_by_name[name]
            uv = [x0 / 2, y0 / 2, x1 / 2, y1 / 2]
            for face in elem["faces"].values():
                face["uv"] = uv

    SPEAR_MODEL_PATH.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------------------
# 4. Twenty High-Craft 32x32 Fantasy Skill Icons
# ---------------------------------------------------------------------------

def make_skills() -> Dict[str, Image.Image]:
    skills: Dict[str, Image.Image] = {}

    # 1. air_step: Winged sky-cyan & brass wind boot with soaring wing & cloud-stream swirl
    skills["air_step.png"] = apply_auto_outline(from_grid([
        "................................",
        "................................",
        ".........wwwwwww................",
        "........wWWWWWWww...............",
        "........wWWwwwwWWw..............",
        ".......wWWw...wWWw..............",
        "......wWWw...wWWw..ggggg........",
        ".....wWWw...wWWw..gGGGGg........",
        "....wWWw...wWWw..gGCCCCg........",
        "....ww....wWWw...gGCCCCg........",
        "..........ww.....gGCCCCg........",
        "................gGCCCCCCg.......",
        "...............gGCCCCCCCCgg.....",
        "..............gGCCCCCCCCCCCGg...",
        ".............gGCCCCCCCCCCCCCGg..",
        ".............gGGGGGGGGGGGGGGGg..",
        "..............ddddddddddddddd...",
        ".....cccc.......................",
        "...ccCCCCcc......cccccc.........",
        "..cCCccccCCc...ccCCCCCCcc.......",
        "..cCc....cCCc..cc......cc.......",
        "...cc.....cCCc..................",
        "...........cc...................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "W": hex_rgba("#FFFFFF"), "w": hex_rgba("#A8F2FF"),
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"),
        "C": hex_rgba("#3CE0E0"), "d": hex_rgba("#1A4252"),
        "c": hex_rgba("#7BEFFF"),
    }), dark_factor=0.25)

    # 2. armor_piercing: Steel & gold spearhead piercing diagonally through a cracked bronze/iron armor plate
    c = PixelCanvas()
    # Curved armor plate in center-left (from x=6..20, y=7..24)
    c.fill_polygon([(7, 9), (18, 6), (22, 16), (16, 25), (6, 21)], hex_rgba("#7A8699"))
    c.fill_polygon([(8, 10), (17, 7), (20, 15), (15, 22), (7, 19)], hex_rgba("#A6B4C8"))
    # Brass rim along armor plate top/bottom
    c.line(7, 9, 18, 6, hex_rgba("#F2C96B"))
    c.line(6, 21, 16, 25, hex_rgba("#C48D31"))
    # Diagonal steel & gold spear shaft piercing straight through!
    c.line(3, 28, 12, 19, hex_rgba("#875414"))
    c.line(4, 28, 13, 19, hex_rgba("#F2C96B"))
    # Impact fracture cracks & bright sparks on plate
    c.line(13, 13, 19, 11, hex_rgba("#242936"))
    c.line(15, 16, 17, 22, hex_rgba("#242936"))
    # Gleaming spearhead emerging through top-right!
    c.fill_polygon([(16, 13), (26, 4), (27, 5), (18, 15)], hex_rgba("#E6F2FF"))
    c.line(17, 14, 27, 4, hex_rgba("#FFFFFF"))
    # Orange-gold shatter sparks
    for sx, sy in ((16, 11), (19, 16), (14, 17), (20, 9)):
        c.set(sx, sy, hex_rgba("#FFE57A"))
        c.set(sx + 1, sy, hex_rgba("#FF7B29"))
    skills["armor_piercing.png"] = c.finalize(outline=True, dark_factor=0.25)

    # 3. battle_momentum: Twin dramatic crossed sweeping sword-slash crescents with blazing motion trails
    c = PixelCanvas()
    # Sweeping crescent slash 1 (upper-left to right)
    for deg in range(-140, 25, 3):
        rad = math.radians(deg)
        t = (deg + 140) / 165.0
        thick = 2.6 * math.sin(t * math.pi) + 0.5
        for dr in (-thick, 0.0, thick * 0.6):
            r = 10.5 + dr
            x = int(round(15.5 + r * math.cos(rad)))
            y = int(round(15.5 + r * math.sin(rad)))
            col = hex_rgba("#FFFFFF") if abs(dr) < 0.4 else (hex_rgba("#FFE27A") if t > 0.35 else hex_rgba("#FF5A2B"))
            c.set(x, y, col)
    # Sweeping crescent slash 2 (lower-right to left)
    for deg in range(40, 205, 3):
        rad = math.radians(deg)
        t = (deg - 40) / 165.0
        thick = 2.6 * math.sin(t * math.pi) + 0.5
        for dr in (-thick, 0.0, thick * 0.6):
            r = 10.5 + dr
            x = int(round(15.5 + r * math.cos(rad)))
            y = int(round(15.5 + r * math.sin(rad)))
            col = hex_rgba("#FFFFFF") if abs(dr) < 0.4 else (hex_rgba("#A8FFFF") if t > 0.35 else hex_rgba("#D62B45"))
            c.set(x, y, col)
    # Central crossed sword blades
    c.line(8, 23, 23, 8, hex_rgba("#FFE27A"))
    c.line(9, 23, 23, 9, hex_rgba("#FFFFFF"))
    c.line(23, 23, 8, 8, hex_rgba("#D62B45"))
    c.line(22, 23, 8, 9, hex_rgba("#FF8A65"))
    c.fill_ellipse(15.5, 15.5, 2.2, 2.2, hex_rgba("#F2C96B"))
    skills["battle_momentum.png"] = c.finalize(outline=True, dark_factor=0.25)

    # 4. berserk: Double-headed crimson-gold war-axe wreathed in fierce scarlet flames
    skills["berserk.png"] = apply_auto_outline(from_grid([
        "................................",
        "........ff............ff........",
        ".......fFFf..........fFFf.......",
        "......fFYFFf...gg...fFFYFf......",
        ".....fFYYMMmf.gGGg.fmMMYYFf.....",
        "....fFYMMHHMm.gSSg.mMHHMMYFf....",
        "....fRMHHHHMMmgGGgmMMHHHHRRf....",
        "....rRMHHMMRRggggggRRMMHHMRr....",
        "....rRMHMRRRGGGGGGGGGRRRHMRr....",
        "....rRMHMRRGGGGWWGGGGRRMHMRr....",
        "....rRMHMRRRGGGGGGGGRRRHMHRr....",
        ".....rRMHMMRRggggggRRMMHMRr.....",
        ".....rRMMHHMmgGGGGgmMHHMMRr.....",
        "......rRMmmf..gGGg..fmmMRr......",
        ".......rr.....gGGg.....rr.......",
        "..............gGGg..............",
        "..............gGGg..............",
        "..............gGGg..............",
        "..............gGGg..............",
        "..............gGGg..............",
        "..............gGGg..............",
        "..............gGGg..............",
        "..............gGGg..............",
        "..............gGGg..............",
        ".............gGGGGg.............",
        ".............gGYYGg.............",
        "..............gggg..............",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "F": hex_rgba("#FF4F38"), "f": hex_rgba("#A81820"), "Y": hex_rgba("#FFE27A"),
        "R": hex_rgba("#D6223C"), "r": hex_rgba("#7A1022"),
        "M": hex_rgba("#525E70"), "m": hex_rgba("#38404F"), "H": hex_rgba("#D6E4F5"),
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"), "W": hex_rgba("#FFFFFF"), "S": hex_rgba("#FFF5B8"),
    }), dark_factor=0.25)

    # 5. bloodthirst: Ornate ruby-crimson chalice / fang crest dripping scarlet essence
    skills["bloodthirst.png"] = apply_auto_outline(from_grid([
        "................................",
        "................................",
        "........gg............gg........",
        ".......gGGggggggggggggGGg.......",
        ".......gGGGGGGGGGGGGGGGGg.......",
        ".......gGRRRRRRRRRRRRRRGg.......",
        "........gRRRRSSSSSSRRRRg........",
        "........gRRRRRSSSSRRRRRg........",
        ".........gRRRRRRRRRRRRg.........",
        ".........gRRRRRRRRRRRRg.........",
        "..........gRRRRRRRRRRg..........",
        "...........gGGGGGGGGg...........",
        "............gGGGGGGg............",
        ".............gGGGGg.............",
        ".............gGGGGg.............",
        "......rr.....gGGGGg.....rr......",
        ".....rRRr....gGGGGg....rRRr.....",
        ".....rRSR....gGGGGg....rRSR.....",
        "......rr....gGGGGGGg....rr......",
        "...........gGGGGGGGGg...........",
        "..........gGGGSSSSGGGg..........",
        ".........gGGGGGGGGGGGGg.........",
        "........gggggggggggggggg........",
        ".............rr.................",
        "............rRRr................",
        "............rRSR................",
        ".............rr.................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"),
        "R": hex_rgba("#D6223C"), "r": hex_rgba("#7A1022"), "S": hex_rgba("#FF8FA3"),
    }), dark_factor=0.25)

    # 6. counter_guard: Brass-rimmed tilted aegis shield deflecting a bright diagonal blade-parry spark!
    c = PixelCanvas()
    # Shield body
    c.fill_polygon([(6, 8), (19, 8), (19, 17), (12.5, 25), (6, 17)], hex_rgba("#2B485E"))
    c.fill_polygon([(7, 9), (18, 9), (18, 16), (12.5, 23), (7, 16)], hex_rgba("#3CE0D0"))
    # Brass rim
    c.line(6, 8, 19, 8, hex_rgba("#FFE58F"))
    c.line(6, 8, 6, 17, hex_rgba("#F2C96B"))
    c.line(19, 8, 19, 17, hex_rgba("#C48D31"))
    c.line(6, 17, 12, 25, hex_rgba("#C48D31"))
    c.line(19, 17, 13, 25, hex_rgba("#8F5E1B"))
    # Deflected incoming blade & bright parry shockwave arc on top-right!
    c.line(18, 14, 27, 5, hex_rgba("#E6F2FF"))
    c.line(19, 14, 27, 6, hex_rgba("#A6B8CC"))
    # Bright cyan-white parry flash burst at impact point (18, 14)
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            if abs(dx) + abs(dy) <= 3:
                c.set(17 + dx, 14 + dy, hex_rgba("#FFFFFF") if abs(dx) + abs(dy) <= 1 else hex_rgba("#8CF2FF"))
    c.line(15, 7, 23, 3, hex_rgba("#FFE58F"))
    c.line(22, 17, 26, 21, hex_rgba("#FFE58F"))
    skills["counter_guard.png"] = c.finalize(outline=True, dark_factor=0.25)

    # 7. damage_clamp: Heavy brass & obsidian-steel clamp lockplate holding back red impact sparks
    skills["damage_clamp.png"] = apply_auto_outline(from_grid([
        "................................",
        ".............gggggg.............",
        "...........ggGGGGGGgg...........",
        "..........gGGbbbbbbGGg..........",
        ".........gGGb......bGGg.........",
        ".........gGb........bGg.........",
        ".........gGb........bGg.........",
        "........gggggggggggggggg........",
        ".......gGGGGGGGGGGGGGGGGg.......",
        "......gGMMMMCCCCCCCCMMMMGg......",
        "......gGMMCCSSSSSSSSCCMMGg......",
        "......gGMMCSGGGGGGGGSCMMGg......",
        "......gGMMCSGWWWWWWGSCMMGg......",
        "......gGMMCSGGGGGGGGSCMMGg......",
        "......gGMMCCSSSSSSSSCCMMGg......",
        "......gGMMMMCCCCCCCCMMMMGg......",
        "......gGMMMMMMMMMMMMMMMMGg......",
        "......gGMMMMMMMMMMMMMMMMGg......",
        ".......gGGGGGGGGGGGGGGGGg.......",
        "........gggggggggggggggg........",
        "...rr......................rr...",
        "..rRRr....................rRRr..",
        "...rr......................rr...",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"),
        "b": hex_rgba("#3D4559"), "M": hex_rgba("#2A2F3D"),
        "C": hex_rgba("#3CE0D0"), "S": hex_rgba("#A8FFFF"), "W": hex_rgba("#FFFFFF"),
        "R": hex_rgba("#FF5252"), "r": hex_rgba("#B81D2B"),
    }), dark_factor=0.25)

    # 8. diamond_bulwark: Faceted radiant diamond hexagonal crystal tower-shield (ice-cyan crystal facets!)
    skills["diamond_bulwark.png"] = apply_auto_outline(from_grid([
        "................................",
        "..............DDDD..............",
        "............DDSSSSDD............",
        "..........DDSSWWWWSSDD..........",
        "........DDSSWWCCCCWWSSDD........",
        "......DDSSWWCCCCCCCCWWSSDD......",
        ".....DSSWWCCCCWWWWCCCCWWSSD.....",
        ".....DSDCCCCWWWWWWWWCCCCDSD.....",
        ".....DSDCCCWWWWSSWWWWCCCDSD.....",
        ".....DSDCCCWWSSSSSSWWCCCDSD.....",
        ".....DSDCCCWWSSSSSSWWCCCDSD.....",
        ".....DSDCCCWWSSSSSSWWCCCDSD.....",
        ".....DSDCCCWWWWSSWWWWCCCDSD.....",
        ".....DSDCCCCWWWWWWWWCCCCDSD.....",
        ".....DSDDCCCCWWWWWWCCCCDDSD.....",
        "......DSDDCCCCCCCCCCCCDDSD......",
        ".......DSDDCCCCCCCCCCDDSD.......",
        "........DSDDCCCCCCCCDDSD........",
        ".........DSDDCCCCCCDDSSD........",
        "..........DSDDCCCCDDSSD.........",
        "...........DSDDDDDDSSD..........",
        "............DDDDDDDDD...........",
        "..............DDDDD.............",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "S": hex_rgba("#B8FFFF"), "W": hex_rgba("#FFFFFF"),
        "C": hex_rgba("#3CE0E8"), "D": hex_rgba("#127599"),
    }), dark_factor=0.25)

    # 9. focus_mining: Gold-and-steel pickaxe striking a faceted emerald-diamond crystal with sparks
    skills["focus_mining.png"] = apply_auto_outline(from_grid([
        "................................",
        ".........gggggg.................",
        ".......ggGGGGGGgg...............",
        "......gGGSSSSSSGGgg.............",
        ".....gGss......ssGGg............",
        "....gGs..........sGGg...........",
        "..................sGGg..........",
        "..................wwGGg.........",
        ".................wWWwGGg........",
        "................wWWw..gGg.......",
        "...............wWWw...sGg.......",
        "..............wWWw....sGg.......",
        ".............wWWw......sg.......",
        "............wWWw................",
        "...........wWWw......YY.........",
        "..........wWWw......yYYy........",
        ".........wWWw........YY..ee.....",
        "........wWWw............eEEe....",
        ".......wWWw............eESSEe...",
        "......wWWw............eEESSEEe..",
        ".....wWWw............eEESSSSEEe.",
        "....gGGw.............eEEEEEEDEe.",
        "...gGGg...............eEEDDDDe..",
        "....gg.................eEDDDe...",
        "........................eeee....",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"),
        "S": hex_rgba("#FFFFFF"), "s": hex_rgba("#9DB2C7"),
        "W": hex_rgba("#9C6B3B"), "w": hex_rgba("#5E3A1E"),
        "Y": hex_rgba("#FFE57A"), "y": hex_rgba("#FF9E38"),
        "e": hex_rgba("#14785E"), "E": hex_rgba("#2CE0A6"), "D": hex_rgba("#1B9E7B"),
    }), dark_factor=0.25)

    # 10. frozen_pull: Six-pointed glacial snowflake / frost vortex pulling inward
    c = PixelCanvas()
    cx, cy = 15.5, 15.5
    ice_hi = hex_rgba("#FFFFFF")
    ice_mid = hex_rgba("#8CF2FF")
    ice_deep = hex_rgba("#2B8EC7")
    for k in range(6):
        ang = math.radians(k * 60.0 - 90.0)
        ex = int(round(cx + 12.0 * math.cos(ang)))
        ey = int(round(cy + 12.0 * math.sin(ang)))
        c.line(15, 15, ex, ey, ice_mid)
        # Side chevrons at radius 7
        bx = cx + 7.0 * math.cos(ang)
        by = cy + 7.0 * math.sin(ang)
        for sign in (-1, 1):
            a2 = ang + sign * math.radians(50.0)
            cx2 = int(round(bx + 3.5 * math.cos(a2)))
            cy2 = int(round(by + 3.5 * math.sin(a2)))
            c.line(int(round(bx)), int(round(by)), cx2, cy2, ice_deep)
        c.set(ex, ey, ice_hi)
    # Concentric frost ring + bright center crystal
    for deg in range(0, 360, 12):
        rad = math.radians(deg)
        c.set(int(round(cx + 5.5 * math.cos(rad))), int(round(cy + 5.5 * math.sin(rad))), ice_hi)
    c.fill_ellipse(cx, cy, 2.5, 2.5, ice_hi)
    skills["frozen_pull.png"] = c.finalize(outline=True, dark_factor=0.25)

    # 11. gravity_well: Cosmic violet-indigo singularity orb with tilted golden accretion rings
    c = PixelCanvas()
    cx, cy = 15.5, 15.5
    ang = math.radians(24.0)
    ca, sa = math.cos(ang), math.sin(ang)
    for front in (False, True):
        for deg in range(0, 360, 2):
            rad = math.radians(deg)
            rx = 12.8 * math.cos(rad)
            ry = 4.4 * math.sin(rad)
            if front and ry < 0:
                continue
            if (not front) and ry >= 0:
                continue
            x = int(round(cx + rx * ca - ry * sa))
            y = int(round(cy + rx * sa + ry * ca))
            col = hex_rgba("#FFE58F") if front else hex_rgba("#9E54FF")
            c.set(x, y, col)
        if not front:
            # Draw singularity sphere in between back and front rings
            c.fill_ellipse(cx, cy, 6.8, 6.8, hex_rgba("#9E54FF"))
            c.fill_ellipse(cx, cy, 5.2, 5.2, hex_rgba("#381B6E"))
            c.fill_ellipse(cx, cy, 3.0, 3.0, hex_rgba("#180C33"))
            c.set(13, 13, hex_rgba("#E0B8FF"))
    skills["gravity_well.png"] = c.finalize(outline=True, dark_factor=0.25)

    # 12. iron_physique: Forged steel & brass knight's cuirass & pauldrons chestplate
    skills["iron_physique.png"] = apply_auto_outline(from_grid([
        "................................",
        "................................",
        "..........ggg......ggg..........",
        "......mmmgGGg......gGGgmmm......",
        ".....mHHMgGGggggggggGGgMHHm.....",
        "....mHHMMgGGGGGGGGGGGGgMMHHm....",
        "...mHHMMMgGMMMMMMMMMMGgMMMHHm...",
        "...mHMMmmgGMHHHHHHHHMGgmmMMHm...",
        "....mmmmmMHHMMMMMMMMHHMmmmmmm...",
        ".......mMHHMMMGGGGMMMHHMm.......",
        ".......mMHHMMMGSSGMMMHHMm.......",
        ".......mMHHMMMGGGGMMMHHMm.......",
        ".......mMHHMMMdMMdMMMHHMm.......",
        "........mMHMMMdMMdMMMHMMm.......",
        "........mMHMMMdMMdMMMHMMm.......",
        "........mMddddddddddddMMm.......",
        "........mMGGGGGGGGGGGGMMm.......",
        "........mMdddgGGGGgdddMMm.......",
        ".......mMMMMddGGGGddMMMMMm......",
        ".......mHMMMdGGGGGGdMMMMHm......",
        ".......mdddd........ddddm.......",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "H": hex_rgba("#E6F0FA"), "M": hex_rgba("#94A3B8"), "m": hex_rgba("#526073"), "d": hex_rgba("#333D4D"),
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"), "S": hex_rgba("#FFFFFF"),
    }), dark_factor=0.25)

    # 13. item_magnet: Connected U-shaped crimson-and-silver horseshoe magnet with brass tips & gold nugget
    skills["item_magnet.png"] = apply_auto_outline(from_grid([
        "................................",
        "................................",
        "..........rrrrrrrrrrrr..........",
        "........rrRRRRRRRRRRRRrr........",
        ".......rRRRRSSSSSSSSRRRRr.......",
        "......rRRRSSrrrrrrrrSSRRRr......",
        "......rRRRr..........rRRRr......",
        "......rRRRr..........rRRRr......",
        "......rRRRr..........rRRRr......",
        "......rRRRr..........rRRRr......",
        "......gGGGg..........gGGGg......",
        "......gGSSg..........gGSSg......",
        "......gGGGg..........gGGGg......",
        ".......ddd............ddd.......",
        "................................",
        ".......cc..............cc.......",
        "......cCCc....gggg....cCCc......",
        ".......cc....gGGGGg....cc.......",
        "............gGSSGGGg............",
        ".............gGGGGg.............",
        "..............gggg..............",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "R": hex_rgba("#D62B45"), "r": hex_rgba("#7A1220"), "S": hex_rgba("#FFFFFF"),
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"), "d": hex_rgba("#B0C2D6"),
        "c": hex_rgba("#3CE0D0"), "C": hex_rgba("#A8FFFF"),
    }), dark_factor=0.25)

    # 14. last_stand: Blazing golden phoenix-heart & cracked shield blazing with defiant embers
    skills["last_stand.png"] = apply_auto_outline(from_grid([
        "................................",
        "...............FF...............",
        "........ff....fFFf....ff........",
        ".......fFFf..fFYYFf..fFFf.......",
        "......fFYFFffFYWWYFffFFYFf......",
        "......fFYYFFFFYWWYFFFFYYFf......",
        ".......fFRRRRRGGGGGRRRRRf.......",
        "......rRRRRRRGGGGGGGRRRRRr......",
        ".....rRRRGGGGGGGGGGGGGGRRRr.....",
        ".....rRRGGSSGGGGGGGGGGSSGRr.....",
        ".....rRRGGSSSWWWWWWWWSSSGRr.....",
        ".....rRRRGGSSWWWWWWWWSSGGRr.....",
        "......rRRRGGSSWWWWWWSSGGRr......",
        ".......rRRRGGSSWWWWSSGGRr.......",
        "........rRRRGGSSWWSSGGRr........",
        ".........rRRRGGSSSSGGRr.........",
        "..........rRRRGGGGGGGRr.........",
        "...........rRRRGGGGGRr..........",
        "............rRRRGGGRr...........",
        ".............rRRRRRr............",
        "..............rRRRr.............",
        "...............rRr..............",
        "................r...............",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "F": hex_rgba("#FF5A2B"), "f": hex_rgba("#A82018"),
        "Y": hex_rgba("#FFE57A"), "W": hex_rgba("#FFFFFF"),
        "R": hex_rgba("#D62B45"), "r": hex_rgba("#7A1022"),
        "G": hex_rgba("#F2C96B"), "S": hex_rgba("#FFF5B8"),
    }), dark_factor=0.25)

    # 15. light_feather: Graceful curved ivory-gold & sky-cyan celestial swan feather + star motes
    skills["light_feather.png"] = apply_auto_outline(from_grid([
        "................................",
        "................................",
        ".......................gggg.....",
        ".....................ggWWWWg....",
        "...................ggWWWWWWWg...",
        "..................gWWWWSSSSWg...",
        "................ggWWWWWSSWWWg...",
        "...............gWWWWWSSWWWWg....",
        "..............gWWWWSSWWWWgg.....",
        ".............gWWWSSWWWWgg.......",
        "............gWWWSSWWWgg.........",
        "...........gWWSSWWWgg...........",
        "..........gWWSSWWgg....cc.......",
        ".........gWWSSWgg.....cCCc......",
        "........gWWSSWg........cc.......",
        ".......gWWSSWg..................",
        "......gWWSSWg...................",
        ".....gGWSSWg....................",
        "....gGGSSWg.....................",
        "...gGGSWgg......................",
        "..gGGWgg........................",
        "..gGg....GG.....................",
        "..gg....gGGg....................",
        ".........GG.....................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "g": hex_rgba("#52B8CC"), "W": hex_rgba("#F5FAFF"), "S": hex_rgba("#A8FFFF"),
        "G": hex_rgba("#F2C96B"), "c": hex_rgba("#FFE57A"), "C": hex_rgba("#FFFFFF"),
    }), dark_factor=0.25)

    # 16. lightning_spear: Dramatic diagonal winged storm-gold spearhead crackling with sky-cyan lightning
    skills["lightning_spear.png"] = apply_auto_outline(from_grid([
        "................................",
        "................................",
        "...........................SS...",
        "..........................SSWs..",
        ".........................SSWWs..",
        "........................SSWWs...",
        "................gg.....SSWWs....",
        "...............gGGg...SSWWs.....",
        "..............gGGGGg.SSWWs......",
        ".............gGGCCGGSSWWs.......",
        "............gGGCCCCSWWs.........",
        "............ggCCCCSWs...........",
        ".............gGGCSWs............",
        "............gGGCSWGGg...........",
        "...........gGGCSWGGGGg..........",
        "..........gGGCSWggGGGGg.........",
        ".........gGGCSWg..gGGg..........",
        "........gGGCSWg....gg...........",
        ".......wWWCSWg..................",
        "......wWWg.gg...................",
        ".....wWWg.......................",
        "....wWWg........................",
        "...wWWg.........................",
        "..gGGg..........................",
        "..gg............................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "S": hex_rgba("#FFFFFF"), "W": hex_rgba("#8CF2FF"), "s": hex_rgba("#3CE0F5"),
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"),
        "C": hex_rgba("#3CE0F5"), "w": hex_rgba("#7D522D"),
    }), dark_factor=0.25)

    # 17. oppressive_field: Heavy amethyst-and-brass anvil/weight pressing down with concentric purple shockwaves
    skills["oppressive_field.png"] = apply_auto_outline(from_grid([
        "................................",
        "................................",
        ".........gggggggggggggg.........",
        "........gGGGGGGGGGGGGGGg........",
        "........gGAAAAAAAAAAAAAg........",
        "........gGAAWWWWWWWWAAAg........",
        "........gGAAAAAAAAAAAAAg........",
        ".........gGGGGGGGGGGGGg.........",
        "..........gGAAAAAAAAAg..........",
        "..........gGAAAWWWAAAg..........",
        ".........gGAAAAAAAAAAAgg........",
        "........gGGGGGGGGGGGGGGg........",
        ".........gggggggggggggg.........",
        "................................",
        ".............vvVVvv.............",
        "............vvVVVVvv............",
        ".............vvVVvv.............",
        "..............vVVv..............",
        ".......pppppppppppppppppp.......",
        ".....ppPPPPPPPPPPPPPPPPPPpp.....",
        "....pPPpp..............ppPPp....",
        ".....pp....pppppppppp....pp.....",
        ".........ppPPPPPPPPPPpp.........",
        "........pPPppppppppppPPp........",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"),
        "A": hex_rgba("#7D3CE0"), "W": hex_rgba("#E0B8FF"),
        "v": hex_rgba("#7D3CE0"), "V": hex_rgba("#FFFFFF"),
        "p": hex_rgba("#522494"), "P": hex_rgba("#C48CFF"),
    }), dark_factor=0.25)

    # 18. ore_sight: Ornate brass arcane eye / monocle with glowing emerald-diamond iris & ore sparkles
    skills["ore_sight.png"] = apply_auto_outline(from_grid([
        "................................",
        "................................",
        "...........gggggggggg...........",
        "........gggGGGGGGGGGGggg........",
        "......ggGGWWWWWWWWWWWWGGgg......",
        ".....gGGWWWWWCCCCCCWWWWWGGg.....",
        "....gGGWWWWCCSSSSSSCCWWWWGGg....",
        "...gGGWWWCCSSSEEEESSSCCWWWGGg...",
        "..gGGWWWCCSSSEEEEEESSSCCWWWGGg..",
        "..gGWWWCCSSSEEEWWEEESSSCCWWWGg..",
        "..gGWWWCCSSSEEEWWEEESSSCCWWWGg..",
        "..gGGWWWCCSSSEEEEEESSSCCWWWGGg..",
        "...gGGWWWCCSSSEEEESSSCCWWWGGg...",
        "....gGGWWWWCCSSSSSSCCWWWWGGg....",
        ".....gGGWWWWWCCCCCCWWWWWGGg.....",
        "......ggGGWWWWWWWWWWWWGGgg......",
        "........gggGGGGGGGGGGggg........",
        "...........gggggggggg...........",
        "....cc....................YY....",
        "...cCCc..................yYYy...",
        "....cc....................YY....",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"),
        "W": hex_rgba("#F5FAFF"), "C": hex_rgba("#3CE0D0"),
        "S": hex_rgba("#8CF2FF"), "E": hex_rgba("#1AA67A"),
        "c": hex_rgba("#3CE0E8"), "Y": hex_rgba("#FFE57A"), "y": hex_rgba("#F2C96B"),
    }), dark_factor=0.25)

    # 19. survival_flow: Swirling emerald-life & aqua-mana spiral orb cradled in golden leaves
    c = PixelCanvas()
    cx, cy = 15.5, 15.5
    for y in range(6, 26):
        for x in range(6, 26):
            r = math.hypot(x - cx, y - cy)
            if r <= 9.2:
                if r >= 7.8:
                    c.set(x, y, hex_rgba("#F2C96B") if (x + y) < 31 else hex_rgba("#9E6D24"))
                else:
                    ang = math.atan2(y - cy, x - cx) + r * 0.25
                    if math.sin(ang) >= 0:
                        c.set(x, y, hex_rgba("#82FFE0") if r < 4.5 else hex_rgba("#24C998"))
                    else:
                        c.set(x, y, hex_rgba("#B8FFFF") if r < 4.5 else hex_rgba("#2B98D6"))
    c.fill_ellipse(cx, cy, 2.0, 2.0, hex_rgba("#FFFFFF"))
    # Golden leaf wings on bottom-left & bottom-right
    for lx, ly in ((6, 21), (7, 22), (8, 23), (25, 21), (24, 22), (23, 23)):
        c.set(lx, ly, hex_rgba("#FFE57A"))
    skills["survival_flow.png"] = c.finalize(outline=True, dark_factor=0.25)

    # 20. swift_pace: Sleek winged brass & emerald traveler's running boot with crisp speed streaks
    skills["swift_pace.png"] = apply_auto_outline(from_grid([
        "................................",
        "................................",
        "..............gggggg............",
        ".............gGGGGGGg...........",
        "............gGeeeeeeGg..........",
        "......www...gGeEEEEeGg..........",
        ".....wWWWw..gGeEEEEeGg..........",
        ".....wWWw...gGeEEEEeGg..........",
        "....wWWw....gGeEEEEeGg..........",
        "....wWw.....gGeEEEEeGg..........",
        ".....w.....gGeEEEEEEeGg.........",
        "..yyyyy...gGeEEEEEEEEeGgg.......",
        ".yyYYYY..gGeEEEEEEEEEEEEEGgg....",
        "........gGeEEEEEEEEEEEEEEEGGg...",
        "..ccccc.gGeEEEEEEEEEEEEEEEEGg...",
        ".ccCCCC.gGGGGGGGGGGGGGGGGGGg....",
        ".........ddddddd...dddddddd.....",
        "................................",
        "...yyyy.........................",
        "..yyYYY.........................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
    ], {
        "g": hex_rgba("#8F5E1B"), "G": hex_rgba("#F2C96B"),
        "e": hex_rgba("#186950"), "E": hex_rgba("#2CC494"),
        "d": hex_rgba("#3D2B1F"), "Y": hex_rgba("#FFE57A"), "y": hex_rgba("#F2A838"),
        "C": hex_rgba("#A8FFFF"), "c": hex_rgba("#3CE0D0"),
        "W": hex_rgba("#FFFFFF"), "w": hex_rgba("#C8F5FF"),
    }), dark_factor=0.25)

    return skills


# ---------------------------------------------------------------------------
# 5. Counter Guard 64x64 Arcane Brass & Aqua-Crystal Effects
# ---------------------------------------------------------------------------

def make_counter_guard_aegis() -> Image.Image:
    """
    64x64 seamless tileable hexagonal arcane ward lattice.
    Bright ivory-gold and pale aqua-crystal lines over faint translucent veil so
    CounterGuardEffectRenderer's warm gold tint renders crisp luminous ward cells.
    """
    w, h = 64, 64
    img = Image.new("RGBA", (w, h), (225, 245, 255, 22))
    px = img.load()

    line_hi = hex_rgba("#FFFDF2", 230)
    line_mid = hex_rgba("#B8FFF5", 150)
    node_col = hex_rgba("#FFFFFF", 250)

    # Seamless 64x64 staggered hex lattice using periodic toroidal wrapping
    for y in range(h):
        for x in range(w):
            best_d = 999.0
            second_d = 999.0
            for ci in range(4):
                for cj in range(2):
                    for cx0, cy0 in ((8 + 16 * ci, 8 + 32 * cj), (16 * ci, 24 + 32 * cj)):
                        dx = abs(x - cx0)
                        dx = min(dx, w - dx)
                        dy = abs(y - cy0)
                        dy = min(dy, h - dy)
                        d = max(dx, dx * 0.5 + dy * 0.866)
                        if d < best_d:
                            second_d = best_d
                            best_d = d
                        elif d < second_d:
                            second_d = d

            diff = second_d - best_d
            if diff <= 1.05:
                px[x, y] = line_hi
            elif diff <= 2.05:
                px[x, y] = line_mid

            # Crisp rune cross inside each hex center
            if (x % 16 == 8 and y % 32 == 8) or (x % 16 == 0 and y % 32 == 24):
                for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
                    px[(x + dx) % w, (y + dy) % h] = node_col

    return img


def make_counter_guard_ring() -> Image.Image:
    """
    64x64 crisp arcane brass & aqua-crystal mandala ring (zero blur).
    """
    size = 64
    img = Image.new("RGBA", (size, size), TRANSPARENT)
    px = img.load()
    cx = cy = 31.5

    outer_hi = hex_rgba("#FFFDF2", 245)
    outer_mid = hex_rgba("#FFE58F", 185)
    aqua_hi = hex_rgba("#C8FFF8", 220)
    inner_veil = hex_rgba("#B8FFF5", 52)
    node_col = hex_rgba("#FFFFFF", 255)

    for y in range(size):
        for x in range(size):
            r = math.hypot(x - cx, y - cy)
            if 28.0 <= r <= 29.4:
                px[x, y] = outer_hi
            elif 26.5 <= r < 28.0 or 29.4 < r <= 30.3:
                px[x, y] = outer_mid
            elif 21.8 <= r <= 23.0:
                px[x, y] = aqua_hi
            elif 15.0 <= r <= 16.3:
                px[x, y] = outer_hi
            elif 16.3 < r < 26.5:
                px[x, y] = inner_veil

    # 8-fold octagram star geometry between r=15.5 and r=28.5
    for k in range(8):
        ang0 = k * math.pi / 4.0
        ang1 = ((k + 3) % 8) * math.pi / 4.0
        x0 = cx + 22.4 * math.cos(ang0)
        y0 = cy + 22.4 * math.sin(ang0)
        x1 = cx + 22.4 * math.cos(ang1)
        y1 = cy + 22.4 * math.sin(ang1)
        for s in range(40):
            t = s / 39.0
            nx = int(round(x0 + (x1 - x0) * t))
            ny = int(round(y0 + (y1 - y0) * t))
            if 0 <= nx < size and 0 <= ny < size:
                r = math.hypot(nx - cx, ny - cy)
                if 16.3 < r < 26.5:
                    px[nx, ny] = outer_mid

    # 8 cardinal & diagonal diamond studs on outer ring
    for i in range(8):
        ang = i * math.pi / 4.0
        sx = int(round(cx + 28.5 * math.cos(ang)))
        sy = int(round(cy + 28.5 * math.sin(ang)))
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                if abs(dx) + abs(dy) <= 1:
                    nx, ny = sx + dx, sy + dy
                    if 0 <= nx < size and 0 <= ny < size:
                        px[nx, ny] = node_col

    # 8 crisp rune glyphs in outer band (r ~ 25.2)
    for i in range(8):
        ang = (i + 0.5) * math.pi / 4.0
        gx = int(round(cx + 25.0 * math.cos(ang)))
        gy = int(round(cy + 25.0 * math.sin(ang)))
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
            if (dx == 0 or dy == 0) and 0 <= gx + dx < size and 0 <= gy + dy < size:
                px[gx + dx, gy + dy] = aqua_hi

    return img


# ---------------------------------------------------------------------------
# 6. Validation & Contact Sheet Preview
# ---------------------------------------------------------------------------

def validate_texture(img: Image.Image, name: str, expected_size: Tuple[int, int], binary_alpha: bool) -> None:
    assert img.size == expected_size, f"{name}: expected {expected_size}, got {img.size}"
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if binary_alpha:
                assert a in (0, 255), f"{name}: non-binary alpha {a} at ({x}, {y})"
            if a > 0:
                assert not (r == 0 and g == 0 and b == 0), f"{name}: pure #000000 pixel at ({x}, {y})"


def save_contact_sheet(images: List[Tuple[str, Image.Image]], out_path: Path, cols: int = 6, scale: int = 3) -> None:
    cell_px = 36
    pad = 8
    rows = (len(images) + cols - 1) // cols
    canvas_w = pad + cols * (cell_px * scale + pad)
    canvas_h = pad + rows * (cell_px * scale + 18 + pad)
    sheet = Image.new("RGBA", (canvas_w, canvas_h), hex_rgba("#383430"))
    draw = ImageDraw.Draw(sheet)

    for idx, (label, im) in enumerate(images):
        r = idx // cols
        c = idx % cols
        x0 = pad + c * (cell_px * scale + pad)
        y0 = pad + r * (cell_px * scale + 18 + pad)
        slot_sz = cell_px * scale
        # Classic #8B8B8B Minecraft slot square
        draw.rectangle([x0, y0, x0 + slot_sz - 1, y0 + slot_sz - 1], fill=hex_rgba("#8B8B8B"), outline=hex_rgba("#373737"))
        iw, ih = im.size
        target_s = max(1, (slot_sz - 12) // iw) if iw == 64 else scale
        scaled = im.resize((iw * target_s, ih * target_s), Image.Resampling.NEAREST)
        px_x = x0 + (slot_sz - scaled.width) // 2
        px_y = y0 + (slot_sz - scaled.height) // 2
        sheet.paste(scaled, (px_x, px_y), scaled)
        draw.text((x0, y0 + slot_sz + 3), label[:18], fill=hex_rgba("#EFE5CF"))

    out_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out_path)


def main() -> None:
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    SKILL_DIR.mkdir(parents=True, exist_ok=True)
    MISC_DIR.mkdir(parents=True, exist_ok=True)

    items: Dict[str, Image.Image] = {}
    items.update(make_universal_tools())
    items["klein_bottle.png"] = make_klein_bottle()
    items["teleport_codex.png"] = make_teleport_codex()
    items["teleport_scroll.png"] = make_teleport_scroll()
    items["spatial_page.png"] = make_spatial_page()
    items["universal_repair_material.png"] = make_universal_repair_material()
    items["void_scrap.png"] = make_void_scrap()
    items["void_essence.png"] = make_void_essence()
    items["dna_syringe.png"] = make_dna_syringe()
    items["dna_sample.png"] = make_dna_sample()
    items["nutrient_solution.png"] = make_nutrient_solution()
    items["lightning_spear.png"] = make_lightning_spear()
    verify_and_update_lightning_spear_model()

    for fname, im in items.items():
        validate_texture(im, f"item/{fname}", (32, 32), binary_alpha=True)
        im.save(ITEM_DIR / fname)

    skills = make_skills()
    for fname, im in skills.items():
        validate_texture(im, f"gui/skills/{fname}", (32, 32), binary_alpha=True)
        im.save(SKILL_DIR / fname)

    aegis = make_counter_guard_aegis()
    validate_texture(aegis, "misc/counter_guard_aegis.png", (64, 64), binary_alpha=False)
    aegis.save(MISC_DIR / "counter_guard_aegis.png")

    ring = make_counter_guard_ring()
    validate_texture(ring, "misc/counter_guard_ring.png", (64, 64), binary_alpha=False)
    ring.save(MISC_DIR / "counter_guard_ring.png")

    # Contact sheets in /tmp-style scratch (art/previews, cleaned before final commit or kept in brain scratch)
    preview_dir = Path("C:/Users/lkuvi/.gemini/antigravity/brain/3f07dcfc-af12-4b94-8825-bb9f2f319f6f/scratch")
    save_contact_sheet(
        [(k.replace(".png", ""), v) for k, v in items.items()],
        preview_dir / "items_preview.png",
        cols=6,
        scale=3,
    )
    save_contact_sheet(
        [(k.replace(".png", ""), v) for k, v in skills.items()],
        preview_dir / "skills_preview.png",
        cols=5,
        scale=3,
    )
    save_contact_sheet(
        [("aegis", aegis), ("ring", ring)],
        preview_dir / "misc_preview.png",
        cols=2,
        scale=2,
    )
    print(f"Generated {len(items)} items, {len(skills)} skills, 2 misc textures.")


if __name__ == "__main__":
    main()
