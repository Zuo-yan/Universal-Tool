"""
High-craft Create / Ars Nouveau block model & texture generator for Universal Tool.

Generates:
- 8 painterly, low-noise block PNGs in src/main/resources/assets/universal_tool/textures/block/
- 11 architectural 3D block models in src/main/resources/assets/universal_tool/models/block/
- 4 block-item models with tuned GUI/hand display transforms in src/main/resources/assets/universal_tool/models/item/
- Validates all JSON, element bounds, rotation angles, UV bounds, and BER swatches.
"""

import json
import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX_DIR = ROOT / "src/main/resources/assets/universal_tool/textures/block"
BLOCK_MODEL_DIR = ROOT / "src/main/resources/assets/universal_tool/models/block"
ITEM_MODEL_DIR = ROOT / "src/main/resources/assets/universal_tool/models/item"

# ==============================================================================
# Shared "Create / Ars Nouveau" Palette
# ==============================================================================
SLATE_DEEP = (25, 27, 36, 255)
SLATE_DARK = (35, 38, 50, 255)
SLATE_MID = (45, 49, 63, 255)
SLATE_LIGHT = (58, 63, 80, 255)
SLATE_BEVEL = (76, 83, 104, 255)

ALABASTER_SHADOW = (165, 155, 136, 255)
ALABASTER_MID = (196, 187, 168, 255)
ALABASTER_BASE = (218, 210, 194, 255)
ALABASTER_LIGHT = (234, 228, 215, 255)

WALNUT_DEEP = (62, 41, 24, 255)
WALNUT_DARK = (82, 56, 34, 255)
WALNUT_MID = (102, 71, 44, 255)
WALNUT_LIGHT = (123, 88, 59, 255)
WALNUT_HIGHLIGHT = (142, 104, 72, 255)

BRASS_SHADOW = (118, 82, 26, 255)
BRASS_DARK = (150, 106, 40, 255)
BRASS_MID = (188, 141, 58, 255)
BRASS_BASE = (214, 168, 78, 255)
BRASS_LIGHT = (240, 202, 115, 255)
BRASS_SPEC = (252, 232, 168, 255)

IRON_DEEP = (28, 31, 38, 255)
IRON_DARK = (44, 48, 58, 255)
IRON_MID = (60, 66, 78, 255)
IRON_LIGHT = (82, 89, 104, 255)
IRON_SPEC = (108, 117, 134, 255)

TEAL_DEEP = (24, 104, 102, 255)
TEAL_MID = (42, 166, 158, 255)
TEAL_BRIGHT = (66, 217, 208, 255)
TEAL_GLOW = (158, 248, 240, 255)
TEAL_SPEC = (228, 255, 252, 255)

AMETHYST_DEEP = (88, 52, 158, 255)
AMETHYST_MID = (128, 84, 214, 255)
AMETHYST_BRIGHT = (156, 107, 255, 255)
AMETHYST_GLOW = (208, 178, 255, 255)

VELVET_DEEP = (20, 18, 32, 255)
VELVET_MID = (28, 26, 46, 255)
VELVET_CRIMSON_DARK = (72, 22, 30, 255)
VELVET_CRIMSON = (104, 34, 44, 255)


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(round(v))))


def lerp_color(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))


def add_lum(c, delta):
    return (clamp(c[0] + delta), clamp(c[1] + delta), clamp(c[2] + delta), c[3])


def fill_rect(img, x0, y0, x1, y1, color):
    for y in range(max(0, y0), min(img.height, y1)):
        for x in range(max(0, x0), min(img.width, x1)):
            img.putpixel((x, y), color)


def bevel_rect(img, x0, y0, x1, y1, base, hi=None, lo=None, thickness=1):
    fill_rect(img, x0, y0, x1, y1, base)
    if hi is None:
        hi = add_lum(base, 22)
    if lo is None:
        lo = add_lum(base, -24)
    for t in range(thickness):
        # Top & Left highlight
        for x in range(x0 + t, x1 - t):
            img.putpixel((x, y0 + t), hi)
            img.putpixel((x, y1 - 1 - t), lo)
        for y in range(y0 + t, y1 - t):
            img.putpixel((x0 + t, y), hi)
            img.putpixel((x1 - 1 - t, y), lo)


def draw_rivet(img, x, y, dark=BRASS_SHADOW, light=BRASS_SPEC):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), dark)
    if 0 <= x - 1 < img.width and 0 <= y - 1 < img.height:
        img.putpixel((x - 1, y - 1), light)


def paint_slate_tile(img, ox, oy, w=32, h=32, engraved_ring=False):
    for y in range(h):
        for x in range(w):
            # Smooth subtle dressed stone wave (low contrast, zero confetti)
            wave = math.sin(x * 0.22 + y * 0.14) * 3.0 + math.cos((x - y) * 0.18) * 2.0
            c = add_lum(SLATE_MID, wave)
            if x == 0 or y == 0:
                c = SLATE_BEVEL
            elif x == w - 1 or y == h - 1:
                c = SLATE_DEEP
            elif x == 1 or y == 1:
                c = SLATE_LIGHT
            elif x == w - 2 or y == h - 2:
                c = SLATE_DARK
            img.putpixel((ox + x, oy + y), c)
    # Subtle ashlar joint line
    for x in range(2, w - 2):
        img.putpixel((ox + x, oy + h // 2), SLATE_DARK)
        img.putpixel((ox + x, oy + h // 2 + 1), add_lum(SLATE_MID, 6))
    for y in range(2, h // 2):
        img.putpixel((ox + w // 2, oy + y), SLATE_DARK)
    if engraved_ring:
        cx, cy = w / 2.0 - 0.5, h / 2.0 - 0.5
        for y in range(h):
            for x in range(w):
                d = math.hypot(x - cx, y - cy)
                if 10.2 <= d <= 11.4:
                    img.putpixel((ox + x, oy + y), BRASS_MID)
                elif 6.8 <= d <= 7.6:
                    img.putpixel((ox + x, oy + y), TEAL_MID)


def paint_alabaster_tile(img, ox, oy, w=32, h=32, brass_border=True, conduit=False):
    for y in range(h):
        for x in range(w):
            vein = math.sin(x * 0.18 + math.sin(y * 0.25) * 1.6) * 5.0
            c = add_lum(ALABASTER_BASE, vein)
            if x in (0, w - 1) or y in (0, h - 1):
                c = BRASS_DARK if brass_border else ALABASTER_SHADOW
            elif brass_border and (x in (1, w - 2) or y in (1, h - 2)):
                c = BRASS_BASE if (x == 1 or y == 1) else BRASS_MID
            elif brass_border and (x == 2 or y == 2):
                c = ALABASTER_LIGHT
            elif brass_border and (x == w - 3 or y == h - 3):
                c = ALABASTER_MID
            img.putpixel((ox + x, oy + y), c)
    if brass_border:
        for rx, ry in [(3, 3), (w - 4, 3), (3, h - 4), (w - 4, h - 4)]:
            img.putpixel((ox + rx, oy + ry), BRASS_SHADOW)
            img.putpixel((ox + rx - 1, oy + ry - 1), BRASS_SPEC)
    if conduit:
        mid = w // 2
        bevel_rect(img, ox + mid - 4, oy + 4, ox + mid + 4, oy + h - 4, BRASS_MID, BRASS_LIGHT, BRASS_SHADOW)
        for y in range(6, h - 6):
            for x in range(mid - 2, mid + 2):
                glow = 1.0 - abs(x - (mid - 0.5)) / 2.2
                c = lerp_color(TEAL_DEEP, TEAL_GLOW, glow * (0.75 + 0.25 * math.sin(y * 0.4)))
                img.putpixel((ox + x, oy + y), c)


def paint_walnut_tile(img, ox, oy, w=32, h=32, panel=False, vertical=False):
    for y in range(h):
        for x in range(w):
            u, v = (y, x) if vertical else (x, y)
            grain = math.sin(v * 0.65 + math.sin(u * 0.15) * 1.4) * 5.0
            c = add_lum(WALNUT_MID, grain)
            if v % 8 == 0:
                c = WALNUT_DARK
            elif v % 8 == 1:
                c = WALNUT_LIGHT
            if x == 0 or y == 0:
                c = WALNUT_HIGHLIGHT
            elif x == w - 1 or y == h - 1:
                c = WALNUT_DEEP
            img.putpixel((ox + x, oy + y), c)
    if panel:
        # Chamfered recessed walnut frame with brass corner brackets
        for x in range(2, w - 2):
            img.putpixel((ox + x, oy + 2), WALNUT_DEEP)
            img.putpixel((ox + x, oy + h - 3), WALNUT_HIGHLIGHT)
            img.putpixel((ox + x, oy + 3), WALNUT_DARK)
            img.putpixel((ox + x, oy + h - 4), WALNUT_LIGHT)
        for y in range(2, h - 2):
            img.putpixel((ox + 2, oy + y), WALNUT_DEEP)
            img.putpixel((ox + w - 3, oy + y), WALNUT_HIGHLIGHT)
        # Ornate brass corner L-brackets
        for d in range(6):
            img.putpixel((ox + 1 + d, oy + 1), BRASS_LIGHT)
            img.putpixel((ox + 1, oy + 1 + d), BRASS_LIGHT)
            img.putpixel((ox + w - 2 - d, oy + 1), BRASS_BASE)
            img.putpixel((ox + w - 2, oy + 1 + d), BRASS_MID)
            img.putpixel((ox + 1 + d, oy + h - 2), BRASS_MID)
            img.putpixel((ox + 1, oy + h - 2 - d), BRASS_BASE)
            img.putpixel((ox + w - 2 - d, oy + h - 2), BRASS_DARK)
            img.putpixel((ox + w - 2, oy + h - 2 - d), BRASS_DARK)


def paint_brass_tile(img, ox, oy, w=32, h=32, rivets=True):
    for y in range(h):
        for x in range(w):
            # Clean Create-style brushed brass sheen
            sheen = math.sin((x + y * 0.6) * 0.16) * 8.0 + math.sin(y * 0.45) * 3.0
            c = add_lum(BRASS_BASE, sheen)
            if x == 0 or y == 0:
                c = BRASS_SPEC
            elif x == w - 1 or y == h - 1:
                c = BRASS_SHADOW
            elif x == 1 or y == 1:
                c = BRASS_LIGHT
            elif x == w - 2 or y == h - 2:
                c = BRASS_DARK
            img.putpixel((ox + x, oy + y), c)
    if rivets and w >= 16 and h >= 16:
        for rx, ry in [(3, 3), (w - 4, 3), (3, h - 4), (w - 4, h - 4)]:
            img.putpixel((ox + rx, oy + ry), BRASS_SHADOW)
            img.putpixel((ox + rx - 1, oy + ry - 1), BRASS_SPEC)


def paint_iron_tile(img, ox, oy, w=32, h=32, rivets=True):
    for y in range(h):
        for x in range(w):
            sheen = math.sin((x * 0.25 + y * 0.35)) * 3.5
            c = add_lum(IRON_MID, sheen)
            if x == 0 or y == 0:
                c = IRON_SPEC
            elif x == w - 1 or y == h - 1:
                c = IRON_DEEP
            elif x == 1 or y == 1:
                c = IRON_LIGHT
            elif x == w - 2 or y == h - 2:
                c = IRON_DARK
            img.putpixel((ox + x, oy + y), c)
    if rivets and w >= 16 and h >= 16:
        for rx, ry in [(3, 3), (w - 4, 3), (3, h - 4), (w - 4, h - 4)]:
            img.putpixel((ox + rx, oy + ry), IRON_DEEP)
            img.putpixel((ox + rx - 1, oy + ry - 1), IRON_SPEC)


def paint_crystal_tile(img, ox, oy, w=32, h=32, palette="teal"):
    if palette == "teal":
        c_deep, c_mid, c_hi, c_spec = TEAL_DEEP, TEAL_MID, TEAL_BRIGHT, TEAL_SPEC
    elif palette == "amethyst":
        c_deep, c_mid, c_hi, c_spec = AMETHYST_DEEP, AMETHYST_MID, AMETHYST_BRIGHT, AMETHYST_GLOW
    else:
        c_deep, c_mid, c_hi, c_spec = BRASS_DARK, BRASS_BASE, BRASS_LIGHT, BRASS_SPEC
    cx, cy = w / 2.0 - 0.5, h / 2.0 - 0.5
    for y in range(h):
        for x in range(w):
            dx = abs(x - cx) / (w * 0.5)
            dy = abs(y - cy) / (h * 0.5)
            facet = max(dx, dy)
            diag = ((x - y) / float(w)) * 0.35
            t = max(0.0, min(1.0, 1.0 - facet * 0.85 - diag))
            if t > 0.78:
                c = lerp_color(c_hi, c_spec, (t - 0.78) / 0.22)
            elif t > 0.35:
                c = lerp_color(c_mid, c_hi, (t - 0.35) / 0.43)
            else:
                c = lerp_color(c_deep, c_mid, t / 0.35)
            # Clean diagonal facet ridge
            if abs((x - cx) - (y - cy)) < 0.8 and dx < 0.65:
                c = lerp_color(c, c_spec, 0.55)
            img.putpixel((ox + x, oy + y), c)


# ==============================================================================
# 1. Universal Polymerizer Texture & Model
# ==============================================================================
def build_polymerizer_atlas():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    # (0,0)-(32,32): Slate dressed stone
    paint_slate_tile(img, 0, 0, 32, 32, engraved_ring=False)
    # (32,0)-(64,32): Slate top with engraved compass ring
    paint_slate_tile(img, 32, 0, 32, 32, engraved_ring=True)
    # (64,0)-(96,32): Warm Alabaster inlay
    paint_alabaster_tile(img, 64, 0, 32, 32, brass_border=True, conduit=False)
    # (96,0)-(128,32): Alabaster conduit panel
    paint_alabaster_tile(img, 96, 0, 32, 32, brass_border=True, conduit=True)
    # (0,32)-(32,64): Brushed Brass
    paint_brass_tile(img, 0, 32, 32, 32, rivets=True)
    # (32,32)-(64,48): Brass horizontal trim molding with ticks
    paint_brass_tile(img, 32, 32, 32, 16, rivets=False)
    for x in range(2, 30, 4):
        fill_rect(img, 32 + x, 32 + 5, 32 + x + 2, 32 + 11, BRASS_DARK)
        img.putpixel((32 + x, 32 + 5), BRASS_SPEC)
    # (32,48)-(64,64): Vertical fluted brass & dark iron column
    for y in range(16):
        for x in range(32):
            if x < 5 or x >= 27:
                c = BRASS_LIGHT if x in (0, 27) else (BRASS_DARK if x in (4, 31) else BRASS_BASE)
            else:
                flute = math.sin((x - 5) * 0.85) * 6.0
                c = add_lum(IRON_MID, flute)
                if y in (0, 15):
                    c = BRASS_MID
            img.putpixel((32 + x, 48 + y), c)
    # (64,32)-(96,64): Engraved Brass & Slate Astrolabe Ring segment
    paint_slate_tile(img, 64, 32, 32, 32, engraved_ring=False)
    for y in range(32):
        for x in range(32):
            if y < 5 or y >= 27 or x < 3 or x >= 29:
                c = BRASS_LIGHT if (y < 2 or x < 2) else (BRASS_DARK if (y >= 30 or x >= 30) else BRASS_BASE)
                img.putpixel((64 + x, 32 + y), c)
    for x in range(6, 26, 5):
        fill_rect(img, 64 + x, 32 + 14, 64 + x + 2, 32 + 18, TEAL_BRIGHT)
    # (96,32)-(128,64): Astral Teal Crystal
    paint_crystal_tile(img, 96, 32, 32, 32, "teal")
    # (0,64)-(32,96): Amethyst Crystal
    paint_crystal_tile(img, 0, 64, 32, 32, "amethyst")
    # (32,64)-(64,96): Recessed Dark Basin with Arcane Rune Circle
    paint_slate_tile(img, 32, 64, 32, 32, engraved_ring=False)
    fill_rect(img, 36, 68, 60, 92, SLATE_DEEP)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5)
            if 8.2 <= d <= 9.6:
                img.putpixel((32 + x, 64 + y), TEAL_BRIGHT)
            elif 4.5 <= d <= 5.5:
                img.putpixel((32 + x, 64 + y), BRASS_BASE)
    # (64,64)-(96,96): Forged Wrought Iron
    paint_iron_tile(img, 64, 64, 32, 32, rivets=True)

    out = TEX_DIR / "universal_polymerizer_atlas.png"
    img.save(out)
    return out


def make_box(name, f, t, face_uvs, rot=None, shade=True):
    el = {"name": name, "from": f, "to": t, "faces": {}}
    if rot is not None:
        el["rotation"] = rot
    if not shade:
        el["shade"] = False
    for face, spec in face_uvs.items():
        if isinstance(spec, tuple):
            uv, tex = spec[0], spec[1]
            fobj = {"uv": uv, "texture": tex}
            if len(spec) > 2 and spec[2]:
                fobj["cullface"] = spec[2]
            if len(spec) > 3 and spec[3] is not None:
                fobj["rotation"] = spec[3]
        else:
            fobj = spec
        el["faces"][face] = fobj
    return el


def all_faces(uv, tex, cull_bottom=False):
    d = {
        "north": (uv, tex),
        "south": (uv, tex),
        "east": (uv, tex),
        "west": (uv, tex),
        "up": (uv, tex),
        "down": (uv, tex, "down" if cull_bottom else None),
    }
    return d


def build_polymerizer_models():
    UV_SLATE = [0, 0, 4, 4]
    UV_SLATE_TOP = [4, 0, 8, 4]
    UV_ALABASTER = [8, 0, 12, 4]
    UV_CONDUIT = [12, 0, 16, 4]
    UV_BRASS = [0, 4, 4, 8]
    UV_BRASS_TRIM = [4, 4, 8, 6]
    UV_PILLAR = [4, 6, 8, 8]
    UV_ASTRO_RING = [8, 4, 12, 8]
    UV_TEAL = [12, 4, 16, 8]
    UV_AMETHYST = [0, 8, 4, 12]
    UV_BASIN = [4, 8, 8, 12]
    UV_IRON = [8, 8, 12, 12]

    elements = []
    # Stepped octagonal dark basalt plinth (y = 0..3)
    elements.append(make_box("plinth_lower_main", [1.0, 0.0, 1.0], [15.0, 1.5, 15.0],
                             all_faces(UV_SLATE, "#0", cull_bottom=True)))
    elements.append(make_box("plinth_lower_diag", [2.5, 0.0, 2.5], [13.5, 1.8, 13.5],
                             all_faces(UV_SLATE_TOP, "#0", cull_bottom=True),
                             rot={"angle": 45, "axis": "y", "origin": [8, 0.9, 8]}))
    elements.append(make_box("plinth_upper_collar", [2.2, 1.5, 2.2], [13.8, 2.6, 13.8],
                             {**all_faces(UV_SLATE_TOP, "#0"),
                              "north": (UV_BRASS_TRIM, "#0"), "south": (UV_BRASS_TRIM, "#0"),
                              "east": (UV_BRASS_TRIM, "#0"), "west": (UV_BRASS_TRIM, "#0")}))
    elements.append(make_box("plinth_brass_ring", [3.0, 2.5, 3.0], [13.0, 3.1, 13.0],
                             all_faces(UV_BRASS, "#0")))

    # Warm Alabaster core with brass trim & recessed teal conduits (y = 3..10.5)
    elements.append(make_box("alabaster_core", [3.6, 3.0, 3.6], [12.4, 10.6, 12.4],
                             {**all_faces(UV_ALABASTER, "#0"),
                              "up": (UV_BASIN, "#0"), "down": (UV_SLATE, "#0")}))
    elements.append(make_box("alabaster_chamfer", [4.2, 3.2, 4.2], [11.8, 10.4, 11.8],
                             all_faces(UV_ALABASTER, "#0"),
                             rot={"angle": 45, "axis": "y", "origin": [8, 6.8, 8]}))
    # Cardinal recessed brass-and-teal energy conduit faces
    elements.append(make_box("conduit_ns", [5.5, 3.5, 3.3], [10.5, 10.0, 12.7],
                             {**all_faces(UV_CONDUIT, "#0"),
                              "east": (UV_BRASS, "#0"), "west": (UV_BRASS, "#0")}))
    elements.append(make_box("conduit_ew", [3.3, 3.5, 5.5], [12.7, 10.0, 10.5],
                             {**all_faces(UV_CONDUIT, "#0"),
                              "north": (UV_BRASS, "#0"), "south": (UV_BRASS, "#0")}))

    # 4 Slender tapered brass & dark-iron corner pillars with floating crystal tips
    corners = [("nw", 1.2, 1.2), ("ne", 13.0, 1.2), ("sw", 1.2, 13.0), ("se", 13.0, 13.0)]
    for tag, cx, cz in corners:
        # Brass foot socket
        elements.append(make_box(f"pillar_foot_{tag}", [cx - 0.2, 1.5, cz - 0.2], [cx + 2.0, 3.6, cz + 2.0],
                                 all_faces(UV_BRASS, "#0")))
        # Slender fluted shaft
        elements.append(make_box(f"pillar_shaft_{tag}", [cx + 0.15, 3.6, cz + 0.15], [cx + 1.65, 11.4, cz + 1.65],
                                 all_faces(UV_PILLAR, "#0")))
        # Flared brass capital
        elements.append(make_box(f"pillar_cap_{tag}", [cx - 0.15, 11.4, cz - 0.15], [cx + 1.95, 12.6, cz + 1.95],
                                 all_faces(UV_BRASS, "#0")))
        # Floating crystal tip (kept cleanly at corner radius >= 7.8 from center so orbit items at r=5.76 clear it)
        ox, oz = cx + 0.9, cz + 0.9
        elements.append(make_box(f"pillar_crystal_{tag}", [ox - 0.55, 13.1, oz - 0.55], [ox + 0.55, 15.1, oz + 0.55],
                                 all_faces(UV_TEAL, "#0"),
                                 rot={"angle": 45, "axis": "y", "origin": [ox, 14.1, oz]},
                                 shade=False))

    # Beveled Brass & Slate Astrolabe Halo Ring (y = 10.5..12.5, hollow center for basin)
    ring_segs = [
        ("halo_n", [4.2, 10.6, 1.8], [11.8, 12.2, 4.4]),
        ("halo_s", [4.2, 10.6, 11.6], [11.8, 12.2, 14.2]),
        ("halo_w", [1.8, 10.6, 4.2], [4.4, 12.2, 11.8]),
        ("halo_e", [11.6, 10.6, 4.2], [14.2, 12.2, 11.8]),
    ]
    for rname, rf, rt in ring_segs:
        elements.append(make_box(rname, rf, rt, {
            "up": (UV_ASTRO_RING, "#0"),
            "down": (UV_SLATE, "#0"),
            "north": (UV_BRASS_TRIM, "#0"),
            "south": (UV_BRASS_TRIM, "#0"),
            "east": (UV_BRASS_TRIM, "#0"),
            "west": (UV_BRASS_TRIM, "#0"),
        }))
    # 45-deg octagonal corner bridges for the astrolabe ring
    for bname, bf, bt in [
        ("halo_diag_n", [5.2, 10.7, 2.2], [10.8, 12.1, 4.6]),
        ("halo_diag_s", [5.2, 10.7, 11.4], [10.8, 12.1, 13.8]),
        ("halo_diag_w", [2.2, 10.7, 5.2], [4.6, 12.1, 10.8]),
        ("halo_diag_e", [11.4, 10.7, 5.2], [13.8, 12.1, 10.8]),
    ]:
        elements.append(make_box(bname, bf, bt, all_faces(UV_BRASS, "#0"),
                                 rot={"angle": 45, "axis": "y", "origin": [8, 11.4, 8]}))

    # Central crystal basin & faceted focal crystal (top at y = 13.1, leaving y = 14.4..16 clean for BER!)
    elements.append(make_box("basin_rim", [4.6, 10.6, 4.6], [11.4, 11.6, 11.4],
                             {**all_faces(UV_BRASS, "#0"), "up": (UV_BASIN, "#0")}))
    elements.append(make_box("basin_crystal_base", [6.6, 11.5, 6.6], [9.4, 12.5, 9.4],
                             all_faces(UV_TEAL, "#0"), shade=False))
    elements.append(make_box("basin_crystal_spire", [7.0, 11.7, 7.0], [9.0, 13.2, 9.0],
                             all_faces(UV_AMETHYST, "#0"),
                             rot={"angle": 45, "axis": "y", "origin": [8, 12.4, 8]},
                             shade=False))

    block_json = {
        "format_version": "1.21.1",
        "render_type": "minecraft:cutout",
        "texture_size": [128, 128],
        "textures": {
            "0": "universal_tool:block/universal_polymerizer_atlas",
            "particle": "universal_tool:block/universal_polymerizer_atlas"
        },
        "elements": elements
    }
    (BLOCK_MODEL_DIR / "universal_polymerizer.json").write_text(
        json.dumps(block_json, indent=2) + "\n", encoding="utf-8"
    )

    item_json = {
        "parent": "universal_tool:block/universal_polymerizer",
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.68, 0.68, 0.68]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2.5, 0], "scale": [0.42, 0.42, 0.42]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_lefthand": {"rotation": [75, -45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 1.5, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_lefthand": {"rotation": [0, -135, 0], "translation": [0, 1.5, 0], "scale": [0.45, 0.45, 0.45]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 13.5, 0], "scale": [0.85, 0.85, 0.85]}
        }
    }
    (ITEM_MODEL_DIR / "universal_polymerizer.json").write_text(
        json.dumps(item_json, indent=2) + "\n", encoding="utf-8"
    )


# ==============================================================================
# 2. Operation Phoenix Pod Textures & Models
# ==============================================================================
def build_phoenix_pod_textures():
    # 1) phoenix_pod_base.png (64x64)
    base = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # (0,0)-(32,32): Top/bottom stasis deck plate (iron + brass ring + bio-drain grate)
    paint_iron_tile(base, 0, 0, 32, 32, rivets=True)
    bevel_rect(base, 4, 4, 28, 28, BRASS_MID, BRASS_LIGHT, BRASS_SHADOW)
    bevel_rect(base, 7, 7, 25, 25, IRON_DEEP, IRON_DARK, IRON_DEEP)
    for y in range(9, 23, 3):
        fill_rect(base, 9, y, 23, y + 1, TEAL_MID)
    # (32,0)-(64,32): Side plinth armor (iron + brass trim bands)
    paint_iron_tile(base, 32, 0, 32, 32, rivets=False)
    bevel_rect(base, 32, 0, 64, 6, BRASS_BASE, BRASS_SPEC, BRASS_DARK)
    bevel_rect(base, 32, 26, 64, 32, BRASS_BASE, BRASS_LIGHT, BRASS_SHADOW)
    for x in range(36, 62, 6):
        draw_rivet(base, x, 3)
        draw_rivet(base, x, 29)
    # (0,32)-(32,64): Vertical brass & iron corner strut
    for y in range(32, 64):
        for x in range(32):
            if x < 6 or x >= 26:
                c = BRASS_LIGHT if x in (0, 26) else (BRASS_DARK if x in (5, 31) else BRASS_BASE)
            else:
                c = add_lum(IRON_MID, math.sin((x - 6) * 0.5) * 4.0)
            if y in (32, 33, 62, 63):
                c = BRASS_MID
            base.putpixel((x, y), c)
    # (32,32)-(64,64): Rear spine housing with glowing emerald-teal conduit
    paint_iron_tile(base, 32, 32, 32, 32, rivets=True)
    bevel_rect(base, 42, 32, 54, 64, BRASS_MID, BRASS_LIGHT, BRASS_SHADOW)
    for y in range(32, 64):
        for x in range(45, 51):
            glow = 1.0 - abs(x - 47.5) / 3.0
            pulse = 0.8 + 0.2 * math.sin(y * 0.35)
            c = lerp_color(TEAL_DEEP, TEAL_GLOW, glow * pulse)
            base.putpixel((x, y), c)
    base.save(TEX_DIR / "phoenix_pod_base.png")

    # 2) phoenix_pod_top.png (64x64)
    top = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # (0,0)-(32,32): Top cap crown with central recessed emerald-teal crystal gauge
    paint_iron_tile(top, 0, 0, 32, 32, rivets=True)
    bevel_rect(top, 5, 5, 27, 27, BRASS_BASE, BRASS_SPEC, BRASS_DARK)
    bevel_rect(top, 8, 8, 24, 24, IRON_DEEP, IRON_DARK, IRON_DEEP)
    paint_crystal_tile(top, 10, 10, 12, 12, "teal")
    # (32,0)-(64,32): Top cap side band + front gauge indicator strip
    paint_iron_tile(top, 32, 0, 32, 32, rivets=False)
    bevel_rect(top, 32, 0, 64, 6, BRASS_BASE, BRASS_SPEC, BRASS_DARK)
    bevel_rect(top, 32, 26, 64, 32, BRASS_BASE, BRASS_LIGHT, BRASS_SHADOW)
    bevel_rect(top, 40, 10, 56, 22, BRASS_MID, BRASS_LIGHT, BRASS_SHADOW)
    bevel_rect(top, 42, 12, 54, 20, IRON_DEEP, IRON_DARK, IRON_DEEP)
    fill_rect(top, 44, 14, 52, 18, TEAL_BRIGHT)
    fill_rect(top, 46, 15, 50, 17, TEAL_SPEC)
    # (0,32)-(32,64) and (32,32)-(64,64): Match struts & rear spine seamlessly
    top.paste(base.crop((0, 32, 64, 64)), (0, 32))
    top.save(TEX_DIR / "phoenix_pod_top.png")

    # 3) phoenix_pod_glass.png (64x64): Crystal-clear beveled aqua glass
    glass = Image.new("RGBA", (64, 64), (216, 250, 248, 18))
    for y in range(64):
        for x in range(64):
            # Subtle 1px outer bevel rim
            if x == 0 or y == 0:
                glass.putpixel((x, y), (190, 248, 244, 170))
            elif x == 63 or y == 63:
                glass.putpixel((x, y), (110, 208, 202, 150))
            elif x == 1 or y == 1 or x == 62 or y == 62:
                glass.putpixel((x, y), (180, 245, 240, 75))
            # Crisp thin diagonal specular glints
            elif (10 <= x <= 22 and abs((x - 10) - (y - 8)) <= 1) or (42 <= x <= 52 and abs((x - 42) - (y - 40)) <= 1):
                glass.putpixel((x, y), (235, 255, 254, 65))
    # Subtle brushed-brass corner clips (3x3)
    for cx, cy in [(0, 0), (61, 0), (0, 61), (61, 61)]:
        fill_rect(glass, cx, cy, cx + 3, cy + 3, (214, 168, 78, 225))
    glass.save(TEX_DIR / "phoenix_pod_glass.png")

    # 4) phoenix_pod_glass_liquid.png (64x64): Translucent emerald-aqua bio-fluid (alpha ~95..118)
    liq = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for y in range(64):
        for x in range(64):
            # Gentle vertical gradient + subtle vertical caustic bands
            v_t = y / 63.0
            caustic = math.sin(x * 0.22 + y * 0.08) * 6.0
            r = clamp(36 + (1.0 - v_t) * 18 + caustic)
            g = clamp(210 - v_t * 28 + caustic)
            b = clamp(188 - v_t * 22 + caustic)
            a = clamp(98 + (1.0 - v_t) * 12)  # 98..110 alpha so player clone inside is crisp!
            # Subtle vertical specular highlight ribbon
            if x in (9, 10) and 4 <= y <= 59:
                r, g, b, a = 195, 255, 245, 132
            elif x in (0, 63) or y in (0, 63):
                r, g, b, a = 142, 232, 220, 185
            liq.putpixel((x, y), (r, g, b, a))
    # Crisp rising micro-bubbles
    for bx, by, br in [(18, 48, 2), (44, 38, 2), (28, 22, 1), (48, 16, 2), (20, 12, 1), (36, 52, 1)]:
        for dy in range(-br, br + 1):
            for dx in range(-br, br + 1):
                if dx * dx + dy * dy <= br * br:
                    px, py = bx + dx, by + dy
                    if 2 <= px < 62 and 2 <= py < 62:
                        liq.putpixel((px, py), (186, 255, 244, 155))
    # Clean brass corner brackets
    for cx, cy in [(0, 0), (61, 0), (0, 61), (61, 61)]:
        fill_rect(liq, cx, cy, cx + 3, cy + 3, (214, 168, 78, 230))
    liq.save(TEX_DIR / "phoenix_pod_glass_liquid.png")


def build_phoenix_pod_models():
    # UV zones on 64x64 textures (normalized 0..16)
    UV_DECK = [0, 0, 8, 8]
    UV_SIDE = [8, 0, 16, 8]
    UV_SIDE_BAND = [8, 0, 16, 2]
    UV_STRUT = [0, 8, 8, 16]
    UV_SPINE = [8, 8, 16, 16]
    UV_GAUGE_FRONT = [10, 2, 14, 6]
    UV_GLASS_WALL = [0, 0, 16, 16]

    def make_bottom(liquid):
        gtex = "#glass_liquid" if liquid else "#glass"
        els = [
            make_box("base_plate", [1.5, 0, 1.5], [14.5, 3, 14.5], {
                "down": (UV_DECK, "#base", "down"),
                "up": (UV_DECK, "#base"),
                "north": (UV_SIDE, "#base"),
                "south": (UV_SIDE, "#base"),
                "west": (UV_SIDE, "#base"),
                "east": (UV_SIDE, "#base"),
            }),
            make_box("base_oct_ns", [3.5, 0, 1.0], [12.5, 2.6, 15.0], {
                "down": (UV_DECK, "#base", "down"),
                "up": (UV_DECK, "#base"),
                "north": (UV_SIDE, "#base"),
                "south": (UV_SIDE, "#base"),
                "west": (UV_SIDE_BAND, "#base"),
                "east": (UV_SIDE_BAND, "#base"),
            }),
            make_box("base_oct_ew", [1.0, 0, 3.5], [15.0, 2.6, 12.5], {
                "down": (UV_DECK, "#base", "down"),
                "up": (UV_DECK, "#base"),
                "north": (UV_SIDE_BAND, "#base"),
                "south": (UV_SIDE_BAND, "#base"),
                "west": (UV_SIDE, "#base"),
                "east": (UV_SIDE, "#base"),
            }),
            make_box("strut_nw", [1.6, 3, 1.6], [3.2, 16, 3.2], all_faces(UV_STRUT, "#base")),
            make_box("strut_ne", [12.8, 3, 1.6], [14.4, 16, 3.2], all_faces(UV_STRUT, "#base")),
            make_box("strut_sw", [1.6, 3, 12.8], [3.2, 16, 14.4], all_faces(UV_STRUT, "#base")),
            make_box("strut_se", [12.8, 3, 12.8], [14.4, 16, 14.4], all_faces(UV_STRUT, "#base")),
            make_box("spine_lower", [6.2, 3, 13.0], [9.8, 16, 14.8], all_faces(UV_SPINE, "#base")),
            # Fixed W, E, S glass walls (North left open for BER sliding doors at z=2.56)
            make_box("glass_west", [2.1, 3, 3.2], [2.8, 16, 12.8], {
                "west": (UV_GLASS_WALL, gtex), "east": (UV_GLASS_WALL, gtex)
            }),
            make_box("glass_east", [13.2, 3, 3.2], [13.9, 16, 12.8], {
                "west": (UV_GLASS_WALL, gtex), "east": (UV_GLASS_WALL, gtex)
            }),
            make_box("glass_south", [3.2, 3, 13.1], [12.8, 16, 13.8], {
                "north": (UV_GLASS_WALL, gtex), "south": (UV_GLASS_WALL, gtex)
            }),
        ]
        return {
            "render_type": "minecraft:translucent",
            "textures": {
                "base": "universal_tool:block/phoenix_pod_base",
                "glass": "universal_tool:block/phoenix_pod_glass",
                "glass_liquid": "universal_tool:block/phoenix_pod_glass_liquid",
                "particle": "universal_tool:block/phoenix_pod_base"
            },
            "elements": els
        }

    def make_top(liquid):
        gtex = "#glass_liquid" if liquid else "#glass"
        els = [
            make_box("strut_nw", [1.6, 0, 1.6], [3.2, 13, 3.2], all_faces(UV_STRUT, "#top")),
            make_box("strut_ne", [12.8, 0, 1.6], [14.4, 13, 3.2], all_faces(UV_STRUT, "#top")),
            make_box("strut_sw", [1.6, 0, 12.8], [3.2, 13, 14.4], all_faces(UV_STRUT, "#top")),
            make_box("strut_se", [12.8, 0, 12.8], [14.4, 13, 14.4], all_faces(UV_STRUT, "#top")),
            make_box("spine_upper", [6.2, 0, 13.0], [9.8, 13, 14.8], all_faces(UV_SPINE, "#top")),
            make_box("glass_west", [2.1, 0, 3.2], [2.8, 13, 12.8], {
                "west": (UV_GLASS_WALL, gtex), "east": (UV_GLASS_WALL, gtex)
            }),
            make_box("glass_east", [13.2, 0, 3.2], [13.9, 13, 12.8], {
                "west": (UV_GLASS_WALL, gtex), "east": (UV_GLASS_WALL, gtex)
            }),
            make_box("glass_south", [3.2, 0, 13.1], [12.8, 13, 13.8], {
                "north": (UV_GLASS_WALL, gtex), "south": (UV_GLASS_WALL, gtex)
            }),
            # Octagonal dark-iron & brushed-brass top cap with recessed glowing gauge
            make_box("top_cap_main", [1.5, 13.0, 1.5], [14.5, 15.2, 14.5], {
                "up": (UV_DECK, "#top"),
                "down": (UV_DECK, "#top"),
                "north": (UV_SIDE, "#top"),
                "south": (UV_SIDE, "#top"),
                "west": (UV_SIDE, "#top"),
                "east": (UV_SIDE, "#top"),
            }),
            make_box("top_oct_ns", [3.5, 13.2, 1.0], [12.5, 15.0, 15.0], all_faces(UV_SIDE, "#top")),
            make_box("top_oct_ew", [1.0, 13.2, 3.5], [15.0, 15.0, 12.5], all_faces(UV_SIDE, "#top")),
            make_box("top_crown", [3.2, 15.2, 3.2], [12.8, 15.9, 12.8], {
                **all_faces(UV_SIDE_BAND, "#top"),
                "up": (UV_DECK, "#top", "up"),
            }),
            make_box("top_front_gauge", [5.8, 13.5, 0.8], [10.2, 14.8, 1.3], {
                **all_faces(UV_SIDE_BAND, "#top"),
                "north": (UV_GAUGE_FRONT, "#top"),
            }),
        ]
        return {
            "render_type": "minecraft:translucent",
            "textures": {
                "top": "universal_tool:block/phoenix_pod_top",
                "glass": "universal_tool:block/phoenix_pod_glass",
                "glass_liquid": "universal_tool:block/phoenix_pod_glass_liquid",
                "particle": "universal_tool:block/phoenix_pod_top"
            },
            "elements": els
        }

    bot_empty = make_bottom(False)
    bot_liq = make_bottom(True)
    top_empty = make_top(False)
    top_liq = make_top(True)

    (BLOCK_MODEL_DIR / "operation_phoenix_pod_bottom_empty.json").write_text(json.dumps(bot_empty, indent=2) + "\n", encoding="utf-8")
    (BLOCK_MODEL_DIR / "operation_phoenix_pod_bottom_liquid.json").write_text(json.dumps(bot_liq, indent=2) + "\n", encoding="utf-8")
    (BLOCK_MODEL_DIR / "operation_phoenix_pod_top_empty.json").write_text(json.dumps(top_empty, indent=2) + "\n", encoding="utf-8")
    (BLOCK_MODEL_DIR / "operation_phoenix_pod_top_liquid.json").write_text(json.dumps(top_liq, indent=2) + "\n", encoding="utf-8")

    # Full 2-block pod (y = 0..32) for inventory item model
    full_els = []
    for e in bot_liq["elements"]:
        ec = json.loads(json.dumps(e))
        ec["name"] = "bot_" + ec["name"]
        for fobj in ec["faces"].values():
            fobj.pop("cullface", None)
        full_els.append(ec)
    for e in top_liq["elements"]:
        ec = json.loads(json.dumps(e))
        ec["name"] = "top_" + ec["name"]
        ec["from"][1] += 16.0
        ec["to"][1] += 16.0
        for fobj in ec["faces"].values():
            fobj.pop("cullface", None)
        full_els.append(ec)
    # Front translucent bio-glass door panel & subtle luminous stasis core in item preview
    full_els.append(make_box("front_glass_door", [3.0, 3.0, 2.3], [13.0, 29.0, 2.8], {
        "north": (UV_GLASS_WALL, "#glass_liquid"),
        "south": (UV_GLASS_WALL, "#glass_liquid"),
    }))
    full_els.append(make_box("stasis_core_glow", [6.6, 8.0, 6.6], [9.4, 24.0, 9.4],
                             all_faces([10, 2, 14, 6], "#top"), shade=False))

    full_json = {
        "render_type": "minecraft:translucent",
        "textures": {
            "base": "universal_tool:block/phoenix_pod_base",
            "top": "universal_tool:block/phoenix_pod_top",
            "glass": "universal_tool:block/phoenix_pod_glass",
            "glass_liquid": "universal_tool:block/phoenix_pod_glass_liquid",
            "particle": "universal_tool:block/phoenix_pod_base"
        },
        "elements": full_els
    }
    (BLOCK_MODEL_DIR / "operation_phoenix_pod_full.json").write_text(json.dumps(full_json, indent=2) + "\n", encoding="utf-8")

    item_json = {
        "parent": "universal_tool:block/operation_phoenix_pod_full",
        "display": {
            "gui": {"rotation": [25, 225, 0], "translation": [0, -3.5, 0], "scale": [0.44, 0.44, 0.44]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 1.5, 0], "scale": [0.35, 0.35, 0.35]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -3.5, 0], "scale": [0.44, 0.44, 0.44]},
            "thirdperson_righthand": {"rotation": [70, 45, 0], "translation": [0, 1.5, 0], "scale": [0.32, 0.32, 0.32]},
            "thirdperson_lefthand": {"rotation": [70, -45, 0], "translation": [0, 1.5, 0], "scale": [0.32, 0.32, 0.32]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 0.5, 0], "scale": [0.36, 0.36, 0.36]},
            "firstperson_lefthand": {"rotation": [0, -135, 0], "translation": [0, 0.5, 0], "scale": [0.36, 0.36, 0.36]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 10.0, 0], "scale": [0.7, 0.7, 0.7]}
        }
    }
    (ITEM_MODEL_DIR / "operation_phoenix_pod.json").write_text(json.dumps(item_json, indent=2) + "\n", encoding="utf-8")


# ==============================================================================
# 3. Universal Lottery Machine Texture & Models
# ==============================================================================
def build_lottery_atlas():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))

    # BER Swatches in top-left:
    # (0,0)-(8,8): Pure white solid swatch for BER dice/gems/pointers
    fill_rect(img, 0, 0, 8, 8, (255, 255, 255, 255))
    # (8,0)-(16,8): Clean brushed metallic highlight swatch for BER pendulum/trim/spokes
    for y in range(8):
        for x in range(8, 16):
            v = clamp(244 + math.sin(x * 0.7 + y * 0.4) * 8)
            img.putpixel((x, y), (v, clamp(v - 2), clamp(v - 6), 255))

    # (16,0)-(48,32): Rich Walnut Wood planks
    paint_walnut_tile(img, 16, 0, 32, 32, panel=False)
    # (48,0)-(80,32): Framed Walnut Cabinet Panel with brass corner filigree
    paint_walnut_tile(img, 48, 0, 32, 32, panel=True)
    # (80,0)-(112,32): Brushed Create Brass plate
    paint_brass_tile(img, 80, 0, 32, 32, rivets=True)
    # (112,0)-(128,32): Brushed Brass molding strip
    paint_brass_tile(img, 112, 0, 16, 32, rivets=False)
    for y in range(4, 28, 6):
        fill_rect(img, 114, y, 126, y + 1, BRASS_DARK)
        fill_rect(img, 114, y + 1, 126, y + 2, BRASS_SPEC)

    # (0,32)-(32,64): Dark Basalt Plinth
    paint_slate_tile(img, 0, 32, 32, 32, engraved_ring=False)
    # (32,32)-(64,64): Deep Velvet Starlight Backdrop (dark & crisp so BER pops!)
    for y in range(32):
        for x in range(32):
            vign = math.hypot(x - 15.5, y - 15.5) / 22.0
            c = lerp_color(VELVET_MID, VELVET_DEEP, vign)
            img.putpixel((32 + x, 32 + y), c)
    for sx, sy, col in [(6, 7, BRASS_SPEC), (24, 9, TEAL_GLOW), (11, 22, BRASS_LIGHT), (21, 25, TEAL_BRIGHT), (16, 13, BRASS_SPEC)]:
        img.putpixel((32 + sx, 32 + sy), col)

    # (64,32)-(96,64): Arched Clockwork Header Dial (behind BER pendulum)
    bevel_rect(img, 64, 32, 96, 64, BRASS_BASE, BRASS_SPEC, BRASS_SHADOW, thickness=2)
    bevel_rect(img, 67, 35, 93, 61, SLATE_DEEP, SLATE_DARK, SLATE_DEEP, thickness=1)
    cx, cy = 79.5, 47.5
    for y in range(36, 60):
        for x in range(68, 92):
            d = math.hypot(x - cx, y - cy)
            if 8.5 <= d <= 10.2:
                img.putpixel((x, y), BRASS_BASE)
            elif d < 8.5:
                img.putpixel((x, y), ALABASTER_LIGHT if d > 2.2 else BRASS_MID)
    for ang in range(0, 360, 30):
        rx = int(round(cx + math.cos(math.radians(ang)) * 7.2))
        ry = int(round(cy + math.sin(math.radians(ang)) * 7.2))
        img.putpixel((rx, ry), SLATE_DEEP)

    # (96,32)-(128,64): Create-style Brass Pressure Gauge
    paint_brass_tile(img, 96, 32, 32, 32, rivets=False)
    cx, cy = 111.5, 47.5
    for y in range(32, 64):
        for x in range(96, 128):
            d = math.hypot(x - cx, y - cy)
            if 11.0 <= d <= 13.5:
                img.putpixel((x, y), BRASS_DARK if y > cy else BRASS_SPEC)
            elif d < 11.0:
                img.putpixel((x, y), ALABASTER_LIGHT)
    for i in range(9):
        img.putpixel((112 + i // 2, 47 - i), (180, 42, 42, 255))
    fill_rect(img, 110, 46, 114, 50, BRASS_SHADOW)

    # (0,64)-(32,96): Crimson Velvet Chute Liner & Brass Rim
    bevel_rect(img, 0, 64, 32, 96, BRASS_BASE, BRASS_LIGHT, BRASS_SHADOW, thickness=2)
    bevel_rect(img, 3, 67, 29, 93, VELVET_CRIMSON, VELVET_CRIMSON_DARK, VELVET_DEEP, thickness=2)

    # (32,64)-(64,96): Walnut & Brass Control Console Desk
    paint_walnut_tile(img, 32, 64, 32, 32, panel=True)
    bevel_rect(img, 38, 72, 58, 88, BRASS_MID, BRASS_LIGHT, BRASS_SHADOW, thickness=1)
    fill_rect(img, 41, 75, 55, 85, SLATE_DEEP)

    # (64,64)-(96,96): Clear Aqua Conduit Glass
    fill_rect(img, 64, 64, 96, 96, (182, 238, 236, 255))
    bevel_rect(img, 64, 64, 96, 96, (195, 245, 242, 255), (236, 255, 254, 255), (120, 195, 192, 255), thickness=2)
    fill_rect(img, 70, 68, 73, 92, (240, 255, 255, 255))

    # (96,64)-(128,96): Luminous Astral Teal Aether Fluid
    for y in range(64, 96):
        for x in range(96, 128):
            glow = 1.0 - abs((x - 96) - 15.5) / 16.0
            wave = 0.8 + 0.2 * math.sin(y * 0.35)
            img.putpixel((x, y), lerp_color(TEAL_DEEP, TEAL_GLOW, glow * wave))

    # (0,96)-(32,128): Forged Wrought Iron
    paint_iron_tile(img, 0, 96, 32, 32, rivets=True)
    # (32,96)-(64,128): Luminous Astral Teal Crystal / Jewels
    paint_crystal_tile(img, 32, 96, 32, 32, "teal")
    # (64,96)-(96,128): Walnut & Brass Louvered Vent Grille
    paint_walnut_tile(img, 64, 96, 32, 32, panel=False)
    bevel_rect(img, 66, 98, 94, 126, WALNUT_DARK, WALNUT_DEEP, WALNUT_HIGHLIGHT, thickness=1)
    for y in range(101, 124, 4):
        fill_rect(img, 68, y, 92, y + 2, BRASS_MID)
        fill_rect(img, 68, y + 2, 92, y + 3, SLATE_DEEP)
    # (96,96)-(128,128): Amethyst Crystal / Ruby accent
    paint_crystal_tile(img, 96, 96, 32, 32, "amethyst")

    out = TEX_DIR / "universal_lottery_machine.png"
    img.save(out)
    return out


def build_lottery_models():
    UV_WALNUT = [2, 0, 6, 4]
    UV_WALNUT_PANEL = [6, 0, 10, 4]
    UV_BRASS = [10, 0, 14, 4]
    UV_BRASS_TRIM = [14, 0, 16, 4]
    UV_SLATE = [0, 4, 4, 8]
    UV_VELVET = [4, 4, 8, 8]
    UV_DIAL = [8, 4, 12, 8]
    UV_GAUGE = [12, 4, 16, 8]
    UV_CHUTE = [0, 8, 4, 12]
    UV_CONSOLE = [4, 8, 8, 12]
    UV_AETHER = [12, 8, 16, 12]
    UV_IRON = [0, 12, 4, 16]
    UV_TEAL = [4, 12, 8, 16]
    UV_VENT = [8, 12, 12, 16]
    UV_AMETHYST = [12, 12, 16, 16]

    # --- Bottom Half (y = 0..16) ---
    bot_els = [
        # Stepped dark basalt & brass plinth
        make_box("plinth_base", [0.5, 0.0, 0.5], [15.5, 1.8, 15.5], all_faces(UV_SLATE, "#0", cull_bottom=True)),
        make_box("plinth_brass_trim", [0.8, 1.6, 0.8], [15.2, 2.2, 15.2], all_faces(UV_BRASS_TRIM, "#0")),
    ]
    # 4 Brass & iron corner feet
    for tag, fx, fz in [("rf", 0.2, 0.2), ("lf", 13.4, 0.2), ("rb", 0.2, 13.4), ("lb", 13.4, 13.4)]:
        bot_els.append(make_box(f"foot_{tag}", [fx, 0.0, fz], [fx + 2.4, 2.5, fz + 2.4], all_faces(UV_BRASS, "#0", cull_bottom=True)))
    # 4 Walnut & brass-framed lower corner pilasters
    for tag, px, pz in [("rf", 0.6, 0.6), ("lf", 13.4, 0.6), ("rb", 0.6, 13.4), ("lb", 13.4, 13.4)]:
        bot_els.append(make_box(f"lower_pilaster_{tag}", [px, 2.2, pz], [px + 2.0, 14.4, pz + 2.0], all_faces(UV_IRON, "#0")))

    # Rich Walnut lower cabinet body
    bot_els.append(make_box("lower_cabinet_core", [1.6, 2.2, 1.8], [14.4, 14.4, 14.4], {
        **all_faces(UV_WALNUT, "#0"),
        "east": (UV_WALNUT_PANEL, "#0"),
        "west": (UV_WALNUT_PANEL, "#0"),
        "south": (UV_VENT, "#0"),
    }))
    # Walnut side relief panels
    bot_els.append(make_box("side_panel_r", [1.0, 3.4, 3.0], [1.7, 13.2, 13.0], all_faces(UV_WALNUT_PANEL, "#0")))
    bot_els.append(make_box("side_panel_l", [14.3, 3.4, 3.0], [15.0, 13.2, 13.0], all_faces(UV_WALNUT_PANEL, "#0")))

    # Brass Coin & Prize Tray + Velvet Chute
    bot_els.append(make_box("coin_tray_floor", [2.6, 1.0, 0.3], [13.4, 2.2, 2.6], all_faces(UV_BRASS, "#0")))
    bot_els.append(make_box("coin_tray_velvet", [3.2, 2.1, 0.6], [12.8, 2.3, 2.2], all_faces(UV_CHUTE, "#0")))
    bot_els.append(make_box("coin_tray_lip", [2.4, 2.0, 0.1], [13.6, 3.0, 0.7], all_faces(UV_BRASS_TRIM, "#0")))
    bot_els.append(make_box("coin_drawer", [3.2, 2.4, 0.8], [12.8, 5.4, 2.2], all_faces(UV_WALNUT_PANEL, "#0")))
    bot_els.append(make_box("coin_drawer_handle", [6.2, 3.5, 0.4], [9.8, 4.3, 0.9], all_faces(UV_BRASS, "#0")))

    # Recessed Velvet Prize Chute Window
    bot_els.append(make_box("chute_back", [3.2, 5.5, 1.6], [12.8, 11.4, 2.2], all_faces(UV_CHUTE, "#0")))
    bot_els.append(make_box("chute_frame_l", [2.6, 5.4, 0.8], [3.4, 11.6, 1.8], all_faces(UV_BRASS_TRIM, "#0")))
    bot_els.append(make_box("chute_frame_r", [12.6, 5.4, 0.8], [13.4, 11.6, 1.8], all_faces(UV_BRASS_TRIM, "#0")))
    bot_els.append(make_box("chute_arch", [2.6, 11.4, 0.6], [13.4, 12.6, 2.0], all_faces(UV_BRASS, "#0")))

    # Side Aether Glass Tubes (R and L)
    for tag, bx in [("r", 0.2), ("l", 14.5)]:
        bot_els.append(make_box(f"tube_bot_cap_{tag}", [bx, 3.2, 7.6], [bx + 1.3, 4.4, 9.6], all_faces(UV_BRASS, "#0")))
        bot_els.append(make_box(f"tube_top_cap_{tag}", [bx, 13.2, 7.6], [bx + 1.3, 14.4, 9.6], all_faces(UV_BRASS, "#0")))
        bot_els.append(make_box(f"tube_aether_{tag}", [bx + 0.25, 4.4, 7.9], [bx + 1.05, 13.2, 9.3], all_faces(UV_AETHER, "#0"), shade=False))

    # Waist belt & front angled control console desk
    bot_els.append(make_box("waist_molding", [0.3, 14.2, 0.3], [15.7, 15.6, 15.7], all_faces(UV_BRASS, "#0")))
    bot_els.append(make_box("waist_walnut_cap", [0.8, 15.4, 0.8], [15.2, 16.0, 15.2], {
        **all_faces(UV_WALNUT, "#0"), "up": (UV_CONSOLE, "#0")
    }))
    bot_els.append(make_box("console_desk", [2.2, 14.8, 0.2], [13.8, 16.0, 2.8], {
        **all_faces(UV_BRASS_TRIM, "#0"), "up": (UV_CONSOLE, "#0")
    }))

    # --- Top Half (y = 0..16, aligns with BER chamber at y=5.5, z=3.6 and pendulum at y=11.84, z=1.2) ---
    top_els = [
        # Console deck buttons & indicator jewels (y = 0..2.0)
        make_box("btn_pedestal", [6.6, 0.0, 0.5], [9.4, 0.8, 2.2], all_faces(UV_BRASS, "#0")),
        make_box("btn_cap_glow", [7.1, 0.8, 0.8], [8.9, 1.6, 1.9], all_faces(UV_TEAL, "#0"), shade=False),
        make_box("gem_left_glow", [4.4, 0.2, 0.8], [5.4, 1.0, 1.6], all_faces(UV_AMETHYST, "#0"), shade=False),
        make_box("gem_right_glow", [10.6, 0.2, 0.8], [11.6, 1.0, 1.6], all_faces(UV_TEAL, "#0"), shade=False),

        # Right-side Clockwork Pull Lever (at x = 0..1.4)
        make_box("lever_housing", [0.1, 0.0, 4.4], [1.4, 1.8, 7.6], all_faces(UV_BRASS, "#0")),
        make_box("lever_arm", [0.4, 1.0, 5.6], [1.1, 7.4, 6.3], all_faces(UV_IRON, "#0"),
                 rot={"angle": -22.5, "axis": "x", "origin": [0.75, 1.0, 5.95]}),
        make_box("lever_pommel", [0.15, 6.8, 5.1], [1.35, 8.2, 6.3], all_faces(UV_BRASS, "#0"),
                 rot={"angle": -22.5, "axis": "x", "origin": [0.75, 1.0, 5.95]}),
        make_box("lever_jewel", [0.3, 7.1, 5.25], [1.2, 7.9, 6.15], all_faces(UV_AMETHYST, "#0"),
                 rot={"angle": -22.5, "axis": "x", "origin": [0.75, 1.0, 5.95]}, shade=False),

        # Upper Rear Walnut Housing & 4 Corner Pilasters (leaving chamber open x=3.2..12.8, y=1.8..9.2, z=1.4..4.8)
        make_box("upper_rear_housing", [1.6, 0.0, 5.0], [14.4, 12.2, 14.4], {
            **all_faces(UV_WALNUT, "#0"),
            "south": (UV_VENT, "#0"),
            "north": (UV_VELVET, "#0"),
        }),
        make_box("upper_pilaster_rf", [0.6, 0.0, 0.8], [2.6, 12.2, 2.8], all_faces(UV_WALNUT_PANEL, "#0")),
        make_box("upper_pilaster_lf", [13.4, 0.0, 0.8], [15.4, 12.2, 2.8], all_faces(UV_WALNUT_PANEL, "#0")),
        make_box("upper_pilaster_rb", [0.6, 0.0, 13.4], [2.6, 12.2, 15.4], all_faces(UV_IRON, "#0")),
        make_box("upper_pilaster_lb", [13.4, 0.0, 13.4], [15.4, 12.2, 15.4], all_faces(UV_IRON, "#0")),
        make_box("upper_side_cheek_r", [1.4, 0.2, 2.4], [2.6, 12.0, 5.2], all_faces(UV_WALNUT_PANEL, "#0")),
        make_box("upper_side_cheek_l", [13.4, 0.2, 2.4], [14.6, 12.0, 5.2], all_faces(UV_WALNUT_PANEL, "#0")),

        # Dark Velvet Display Chamber Backdrop & Crisp Brass Bezel Frame (x=3.2..12.8, y=1.8..9.2)
        make_box("chamber_velvet_back", [2.6, 0.8, 4.6], [13.4, 11.4, 5.1], all_faces(UV_VELVET, "#0"), shade=False),
        make_box("chamber_velvet_floor", [2.6, 0.8, 2.2], [13.4, 1.8, 4.8], all_faces(UV_VELVET, "#0")),
        make_box("chamber_frame_bot", [2.6, 1.4, 1.3], [13.4, 2.3, 2.4], all_faces(UV_BRASS_TRIM, "#0")),
        make_box("chamber_frame_top", [2.6, 8.7, 1.3], [13.4, 9.6, 2.4], all_faces(UV_BRASS_TRIM, "#0")),
        make_box("chamber_frame_r", [2.6, 2.3, 1.3], [3.6, 8.7, 2.4], all_faces(UV_BRASS, "#0")),
        make_box("chamber_frame_l", [12.4, 2.3, 1.3], [13.4, 8.7, 2.4], all_faces(UV_BRASS, "#0")),

        # Arched Clockwork Header Dial directly behind BER pendulum (pivot at x=8, y=11.84, z=1.2)
        make_box("clock_header_plate", [4.6, 9.6, 1.35], [11.4, 12.1, 2.2], {
            **all_faces(UV_BRASS, "#0"), "north": (UV_DIAL, "#0")
        }),
        make_box("clock_arch_crown", [4.2, 11.9, 0.9], [11.8, 12.6, 2.3], all_faces(UV_BRASS_TRIM, "#0")),
        make_box("clock_sconce_r", [3.0, 10.2, 1.3], [4.2, 11.5, 2.0], all_faces(UV_TEAL, "#0"), shade=False),
        make_box("clock_sconce_l", [11.8, 10.2, 1.3], [13.0, 11.5, 2.0], all_faces(UV_TEAL, "#0"), shade=False),

        # Stepped Walnut & Brass Cornice Roof + Astral Crystal Spire
        make_box("roof_cornice_brass", [0.2, 12.2, 0.2], [15.8, 13.0, 15.8], all_faces(UV_BRASS, "#0")),
        make_box("roof_walnut_tier", [1.8, 13.0, 1.6], [14.2, 14.0, 14.4], all_faces(UV_WALNUT_PANEL, "#0")),
        make_box("roof_brass_crest", [5.2, 14.0, 5.2], [10.8, 14.6, 10.8], all_faces(UV_BRASS, "#0")),
        make_box("crown_crystal", [7.1, 14.6, 7.1], [8.9, 16.0, 8.9], all_faces(UV_TEAL, "#0"),
                 rot={"angle": 45, "axis": "y", "origin": [8, 15.3, 8]}, shade=False),

        # Side Pressure Gauge & Upper Conduit Tubes
        make_box("upper_tube_bot_r", [0.2, 0.4, 7.6], [1.5, 1.4, 9.6], all_faces(UV_BRASS, "#0")),
        make_box("upper_tube_top_r", [0.2, 10.2, 7.6], [1.5, 11.2, 9.6], all_faces(UV_BRASS, "#0")),
        make_box("upper_tube_aether_r", [0.45, 1.4, 7.9], [1.25, 10.2, 9.3], all_faces(UV_AETHER, "#0"), shade=False),
        make_box("upper_tube_bot_l", [14.5, 0.4, 7.6], [15.8, 1.4, 9.6], all_faces(UV_BRASS, "#0")),
        make_box("upper_tube_top_l", [14.5, 10.2, 7.6], [15.8, 11.2, 9.6], all_faces(UV_BRASS, "#0")),
        make_box("upper_tube_aether_l", [14.75, 1.4, 7.9], [15.55, 10.2, 9.3], all_faces(UV_AETHER, "#0"), shade=False),
        make_box("side_gauge_body", [14.4, 4.0, 6.2], [15.7, 7.4, 9.4], {
            **all_faces(UV_BRASS, "#0"), "east": (UV_GAUGE, "#0")
        }),
    ]

    def wrap_model(els):
        return {
            "format_version": "1.21.1",
            "render_type": "minecraft:cutout",
            "texture_size": [128, 128],
            "textures": {
                "0": "universal_tool:block/universal_lottery_machine",
                "particle": "universal_tool:block/universal_lottery_machine"
            },
            "elements": els
        }

    (BLOCK_MODEL_DIR / "universal_lottery_machine_bottom.json").write_text(
        json.dumps(wrap_model(bot_els), indent=2) + "\n", encoding="utf-8"
    )
    (BLOCK_MODEL_DIR / "universal_lottery_machine_top.json").write_text(
        json.dumps(wrap_model(top_els), indent=2) + "\n", encoding="utf-8"
    )

    # Full 2-block assembly (y = 0..32) + decorative miniature chamber jewel for inventory display
    full_els = []
    for e in bot_els:
        ec = json.loads(json.dumps(e))
        for fobj in ec["faces"].values():
            fobj.pop("cullface", None)
        full_els.append(ec)
    for e in top_els:
        ec = json.loads(json.dumps(e))
        ec["from"][1] += 16.0
        ec["to"][1] += 16.0
        if "rotation" in ec:
            ec["rotation"]["origin"][1] += 16.0
        for fobj in ec["faces"].values():
            fobj.pop("cullface", None)
        full_els.append(ec)
    # Miniature gilded astral core inside chamber for Creative Tab / Item rendering
    full_els.append(make_box("item_chamber_emblem", [6.6, 20.2, 2.8], [9.4, 23.0, 4.2],
                             all_faces(UV_BRASS, "#0"),
                             rot={"angle": 45, "axis": "z", "origin": [8.0, 21.6, 3.5]}, shade=False))
    full_els.append(make_box("item_chamber_core", [7.2, 20.8, 2.6], [8.8, 22.4, 4.4],
                             all_faces(UV_TEAL, "#0"), shade=False))

    (BLOCK_MODEL_DIR / "universal_lottery_machine_full.json").write_text(
        json.dumps(wrap_model(full_els), indent=2) + "\n", encoding="utf-8"
    )

    item_json = {
        "parent": "universal_tool:block/universal_lottery_machine_full",
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -3.5, 0], "scale": [0.44, 0.44, 0.44]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 1.5, 0], "scale": [0.35, 0.35, 0.35]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -3.5, 0], "scale": [0.44, 0.44, 0.44]},
            "thirdperson_righthand": {"rotation": [70, 45, 0], "translation": [0, 1.5, 0], "scale": [0.32, 0.32, 0.32]},
            "thirdperson_lefthand": {"rotation": [70, -45, 0], "translation": [0, 1.5, 0], "scale": [0.32, 0.32, 0.32]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 0.5, 0], "scale": [0.36, 0.36, 0.36]},
            "firstperson_lefthand": {"rotation": [0, -135, 0], "translation": [0, 0.5, 0], "scale": [0.36, 0.36, 0.36]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 10.0, 0], "scale": [0.7, 0.7, 0.7]}
        }
    }
    (ITEM_MODEL_DIR / "universal_lottery_machine.json").write_text(
        json.dumps(item_json, indent=2) + "\n", encoding="utf-8"
    )


# ==============================================================================
# 4 & 5. Personal Space Gate & Return Obelisk
# ==============================================================================
def paint_portal_octagon(img, ox, oy, size=64, variant="gate"):
    cx = (size - 1) / 2.0
    cy = (size - 1) / 2.0
    apothem = size * 0.47
    for y in range(size):
        for x in range(size):
            nx = abs(x - cx)
            ny = abs(y - cy)
            # Crisp octagonal clip mask (transparent outside octagon so zero corner poke)
            if nx > apothem or ny > apothem or (nx + ny) * 0.70710678 > apothem:
                img.putpixel((ox + x, oy + y), (0, 0, 0, 0))
                continue
            r = math.hypot(x - cx, y - cy) / apothem
            ang = math.atan2(y - cy, x - cx)
            swirl = math.sin(ang * 4.0 + r * 6.5) * 0.5 + 0.5
            if variant == "gate":
                c_outer = (16, 22, 54, 255)
                c_mid = (26, 78, 114, 255)
                c_core = (66, 217, 208, 255)
            else:
                c_outer = (18, 36, 48, 255)
                c_mid = (30, 112, 118, 255)
                c_core = (158, 248, 240, 255)
            c = lerp_color(c_core, c_mid, min(1.0, r * 1.15))
            c = lerp_color(c, c_outer, max(0.0, (r - 0.45) / 0.55) * (0.7 + 0.3 * swirl))
            # Subtle inner energy ring
            if 0.68 <= r <= 0.75:
                c = lerp_color(c, TEAL_BRIGHT, 0.45)
            img.putpixel((ox + x, oy + y), c)
    # Crisp starry diamond glints
    stars = [(20, 18), (44, 21), (16, 40), (46, 42), (32, 14), (31, 49), (24, 30), (39, 34)]
    for sx, sy in stars:
        img.putpixel((ox + sx, oy + sy), TEAL_SPEC)
        for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            if img.getpixel((ox + sx + dx, oy + sy + dy))[3] > 0:
                img.putpixel((ox + sx + dx, oy + sy + dy), TEAL_BRIGHT)


def paint_rune_band(img, ox, oy, w=32, h=32, accent=TEAL_BRIGHT):
    paint_slate_tile(img, ox, oy, w, h, engraved_ring=False)
    bevel_rect(img, ox, oy, ox + w, oy + 4, BRASS_BASE, BRASS_SPEC, BRASS_DARK)
    bevel_rect(img, ox, oy + h - 4, ox + w, oy + h, BRASS_BASE, BRASS_LIGHT, BRASS_SHADOW)
    # Clean etched geometric glyph runes (2 crisp 8x12 glyphs side by side)
    glyphs = [
        [(3, 0), (3, 1), (3, 2), (3, 3), (3, 4), (3, 5), (1, 2), (2, 1), (4, 1), (5, 2), (1, 4), (5, 4)],
        [(2, 0), (3, 0), (4, 0), (1, 1), (5, 1), (3, 2), (3, 3), (1, 4), (5, 4), (2, 5), (3, 5), (4, 5)],
    ]
    for gi, pts in enumerate(glyphs):
        gx, gy = ox + 5 + gi * 14, oy + 10
        for px, py in pts:
            fill_rect(img, gx + px, gy + py * 2, gx + px + 1, gy + py * 2 + 2, accent)


def build_personal_space_atlases():
    for variant, filename in [("gate", "personal_space_gate_atlas.png"), ("return", "personal_space_return_atlas.png")]:
        img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
        paint_slate_tile(img, 0, 0, 32, 32, engraved_ring=False)
        paint_alabaster_tile(img, 32, 0, 32, 32, brass_border=True, conduit=False)
        paint_brass_tile(img, 64, 0, 32, 32, rivets=True)
        paint_rune_band(img, 96, 0, 32, 32, accent=TEAL_BRIGHT if variant == "gate" else BRASS_SPEC)
        paint_portal_octagon(img, 0, 32, 64, variant=variant)
        paint_crystal_tile(img, 64, 32, 32, 32, "teal" if variant == "gate" else "amethyst")
        # Chevron / Keystone plate
        paint_brass_tile(img, 96, 32, 32, 32, rivets=True)
        bevel_rect(img, 103, 39, 121, 57, SLATE_DEEP, SLATE_DARK, SLATE_DEEP)
        paint_crystal_tile(img, 106, 42, 12, 12, "teal" if variant == "gate" else "gold")
        # Obelisk shaft texture (32x64)
        paint_slate_tile(img, 64, 64, 32, 64, engraved_ring=False)
        bevel_rect(img, 64, 64, 68, 128, BRASS_BASE, BRASS_SPEC, BRASS_DARK)
        bevel_rect(img, 92, 64, 96, 128, BRASS_BASE, BRASS_LIGHT, BRASS_SHADOW)
        for y in range(72, 120, 8):
            fill_rect(img, 76, y, 84, y + 4, ALABASTER_BASE)
            fill_rect(img, 78, y + 1, 82, y + 3, TEAL_BRIGHT)
        # Armillary brass dial (32x32)
        paint_brass_tile(img, 96, 64, 32, 32, rivets=False)
        for ang in range(0, 360, 45):
            rx = int(round(111.5 + math.cos(math.radians(ang)) * 10))
            ry = int(round(79.5 + math.sin(math.radians(ang)) * 10))
            fill_rect(img, rx - 1, ry - 1, rx + 2, ry + 2, TEAL_BRIGHT)
        img.save(TEX_DIR / filename)


def build_personal_space_models():
    UV_SLATE = [0, 0, 4, 4]
    UV_ALABASTER = [4, 0, 8, 4]
    UV_BRASS = [8, 0, 12, 4]
    UV_RUNE = [12, 0, 16, 4]
    UV_PORTAL = [0, 4, 8, 12]
    UV_CRYSTAL = [8, 4, 12, 8]
    UV_CHEVRON = [12, 4, 16, 8]
    UV_OBELISK = [8, 8, 12, 16]
    UV_ARMILLARY = [12, 8, 16, 12]

    # --- 1) Personal Space Gate: Architectural Astral Brass Stargate ---
    gate_els = [
        # Wide stepped basalt & warm alabaster plinth (y = 0..3.0, aligned with PersonalGateGeometry)
        make_box("plinth_lower", [0.8, 0.0, 2.5], [15.2, 1.6, 13.5], all_faces(UV_SLATE, "#0", cull_bottom=True)),
        make_box("plinth_upper", [1.6, 1.6, 3.6], [14.4, 2.8, 12.4], {
            **all_faces(UV_ALABASTER, "#0"), "up": (UV_SLATE, "#0")
        }),
        make_box("threshold_brass", [4.2, 2.8, 5.2], [11.8, 3.6, 10.8], all_faces(UV_BRASS, "#0")),
    ]
    # Seamless Octagonal Stargate Ring centered at (8.0, 9.0, 8.0)
    # 8 ring segments rotated around Z at 0, 45, 90, 135 deg (two opposing segments per angle)
    # Inner apothem = 4.4, outer apothem = 6.1, segment width = 5.05 so corners overlap cleanly!
    ring_angles = [
        ("top", 0.0, [5.45, 13.4, 6.2], [10.55, 15.1, 9.8]),
        ("bot", 0.0, [5.45, 2.9, 6.2], [10.55, 4.6, 9.8]),
        ("right", 0.0, [1.9, 6.45, 6.2], [3.6, 11.55, 9.8]),
        ("left", 0.0, [12.4, 6.45, 6.2], [14.1, 11.55, 9.8]),
        ("diag_tr", 45.0, [5.45, 13.4, 6.25], [10.55, 15.1, 9.75]),
        ("diag_bl", 45.0, [5.45, 2.9, 6.25], [10.55, 4.6, 9.75]),
        ("diag_tl", -45.0, [5.45, 13.4, 6.25], [10.55, 15.1, 9.75]),
        ("diag_br", -45.0, [5.45, 2.9, 6.25], [10.55, 4.6, 9.75]),
    ]
    for rname, ang, rf, rt in ring_angles:
        rot = {"angle": ang, "axis": "z", "origin": [8.0, 9.0, 8.0]} if ang != 0.0 else None
        gate_els.append(make_box(f"ring_{rname}", rf, rt, {
            **all_faces(UV_BRASS, "#0"),
            "north": (UV_RUNE, "#0"),
            "south": (UV_RUNE, "#0"),
        }, rot=rot))

    # Brushed brass front keystone chevron on North (-Z) & 2 flanking crystal pylons
    gate_els.append(make_box("keystone_top", [6.6, 13.6, 5.2], [9.4, 15.8, 10.8], {
        **all_faces(UV_BRASS, "#0"), "north": (UV_CHEVRON, "#0"), "south": (UV_CHEVRON, "#0")
    }))
    gate_els.append(make_box("keystone_crystal", [7.3, 14.2, 4.8], [8.7, 15.4, 5.4],
                             all_faces(UV_CRYSTAL, "#0"), shade=False))
    for tag, px in [("r", 1.0), ("l", 13.4)]:
        gate_els.append(make_box(f"pylon_base_{tag}", [px, 1.6, 4.8], [px + 1.6, 6.0, 6.4], all_faces(UV_SLATE, "#0")))
        gate_els.append(make_box(f"pylon_cap_{tag}", [px - 0.1, 6.0, 4.7], [px + 1.7, 7.0, 6.5], all_faces(UV_BRASS, "#0")))
        gate_els.append(make_box(f"pylon_crystal_{tag}", [px + 0.3, 7.2, 5.1], [px + 1.3, 9.2, 6.1],
                                 all_faces(UV_CRYSTAL, "#0"),
                                 rot={"angle": 45, "axis": "y", "origin": [px + 0.8, 8.2, 5.6]},
                                 shade=False))

    # Octagonal Starry Dimensional Portal Veil (masked by alpha=0 outside octagon)
    gate_els.append(make_box("portal_veil", [3.2, 4.2, 7.8], [12.8, 13.8, 8.2], {
        "north": (UV_PORTAL, "#0"),
        "south": (UV_PORTAL, "#0"),
    }, shade=False))

    gate_json = {
        "format_version": "1.21.1",
        "render_type": "minecraft:cutout",
        "texture_size": [128, 128],
        "textures": {
            "0": "universal_tool:block/personal_space_gate_atlas",
            "particle": "universal_tool:block/personal_space_gate_atlas"
        },
        "elements": gate_els
    }
    (BLOCK_MODEL_DIR / "personal_space_gate.json").write_text(json.dumps(gate_json, indent=2) + "\n", encoding="utf-8")

    gate_item = {
        "parent": "universal_tool:block/personal_space_gate",
        "display": {
            "gui": {"rotation": [25, 225, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_lefthand": {"rotation": [75, -45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 1.5, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_lefthand": {"rotation": [0, -135, 0], "translation": [0, 1.5, 0], "scale": [0.45, 0.45, 0.45]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 13.5, 0], "scale": [0.85, 0.85, 0.85]}
        }
    }
    (ITEM_MODEL_DIR / "personal_space_gate.json").write_text(json.dumps(gate_item, indent=2) + "\n", encoding="utf-8")

    # --- 2) Personal Space Return: Sleek Waystone Obelisk & Brass Armillary Ring ---
    ret_els = [
        # Matching stepped basalt & warm alabaster plinth
        make_box("plinth_lower", [1.0, 0.0, 2.5], [15.0, 1.6, 13.5], all_faces(UV_SLATE, "#0", cull_bottom=True)),
        make_box("plinth_upper", [2.2, 1.6, 3.6], [13.8, 2.8, 12.4], {
            **all_faces(UV_ALABASTER, "#0"), "up": (UV_SLATE, "#0")
        }),
        make_box("obelisk_collar", [4.4, 2.8, 5.0], [11.6, 3.8, 11.0], all_faces(UV_BRASS, "#0")),
        # Tapered two-stage Waystone Obelisk shaft
        make_box("obelisk_lower", [5.0, 3.8, 5.6], [11.0, 8.6, 10.4], {
            **all_faces(UV_OBELISK, "#0"), "up": (UV_BRASS, "#0")
        }),
        make_box("obelisk_band", [4.7, 8.2, 5.3], [11.3, 9.0, 10.7], all_faces(UV_BRASS, "#0")),
        make_box("obelisk_upper", [5.6, 9.0, 6.2], [10.4, 12.4, 9.8], all_faces(UV_OBELISK, "#0")),
        # Front compass crest on North (-Z)
        make_box("front_compass_crest", [6.6, 4.8, 4.8], [9.4, 7.6, 5.7], {
            **all_faces(UV_BRASS, "#0"), "north": (UV_CHEVRON, "#0")
        }),
        # Flanking slender brass buttresses
        make_box("buttress_w", [3.4, 2.8, 6.8], [4.8, 8.6, 9.2], all_faces(UV_ALABASTER, "#0")),
        make_box("buttress_e", [11.2, 2.8, 6.8], [12.6, 8.6, 9.2], all_faces(UV_ALABASTER, "#0")),
        make_box("buttress_cap_w", [3.3, 8.6, 6.7], [4.9, 9.3, 9.3], all_faces(UV_BRASS, "#0")),
        make_box("buttress_cap_e", [11.1, 8.6, 6.7], [12.7, 9.3, 9.3], all_faces(UV_BRASS, "#0")),
        # Crowning Brass Armillary Ring & Floating Homing Crystal (y = 12.0..15.8)
        make_box("armillary_base", [5.2, 12.0, 5.8], [10.8, 12.8, 10.2], all_faces(UV_ARMILLARY, "#0")),
        make_box("armillary_ring_outer", [4.6, 13.6, 7.4], [11.4, 14.3, 8.6], all_faces(UV_BRASS, "#0"),
                 rot={"angle": 22.5, "axis": "z", "origin": [8.0, 14.0, 8.0]}),
        make_box("armillary_ring_cross", [7.4, 12.4, 5.2], [8.6, 15.6, 10.8], all_faces(UV_RUNE, "#0"),
                 rot={"angle": 22.5, "axis": "x", "origin": [8.0, 14.0, 8.0]}),
        make_box("homing_crystal", [6.9, 13.0, 6.9], [9.1, 15.6, 9.1], all_faces(UV_CRYSTAL, "#0"),
                 rot={"angle": 45, "axis": "y", "origin": [8.0, 14.3, 8.0]}, shade=False),
    ]

    ret_json = {
        "format_version": "1.21.1",
        "render_type": "minecraft:cutout",
        "texture_size": [128, 128],
        "textures": {
            "0": "universal_tool:block/personal_space_return_atlas",
            "particle": "universal_tool:block/personal_space_return_atlas"
        },
        "elements": ret_els
    }
    (BLOCK_MODEL_DIR / "personal_space_return.json").write_text(json.dumps(ret_json, indent=2) + "\n", encoding="utf-8")


# ==============================================================================
# Validation
# ==============================================================================
VALID_ANGLES = {-45.0, -22.5, 0.0, 22.5, 45.0}


def validate_all():
    errors = []
    # 1. Validate all 11 block models
    block_files = sorted(BLOCK_MODEL_DIR.glob("*.json"))
    assert len(block_files) == 11, f"Expected 11 block models, found {len(block_files)}"
    for bf in block_files:
        data = json.loads(bf.read_text(encoding="utf-8"))
        textures = data.get("textures", {})
        for key, ref in textures.items():
            if ref.startswith("universal_tool:block/"):
                png = TEX_DIR / (ref.split("/")[-1] + ".png")
                if not png.exists():
                    errors.append(f"{bf.name}: missing texture {png}")
        for idx, el in enumerate(data.get("elements", [])):
            for c in el["from"] + el["to"]:
                if not (-16.0 <= c <= 32.0):
                    errors.append(f"{bf.name} el[{idx}] {el.get('name')}: coord {c} out of [-16,32]")
            for axis in range(3):
                if el["from"][axis] >= el["to"][axis]:
                    errors.append(f"{bf.name} el[{idx}] {el.get('name')}: degenerate axis {axis}")
            if "rotation" in el:
                ang = float(el["rotation"]["angle"])
                if ang not in VALID_ANGLES:
                    errors.append(f"{bf.name} el[{idx}] {el.get('name')}: invalid angle {ang}")
            for fname, fobj in el.get("faces", {}).items():
                tex_ref = fobj["texture"]
                if not tex_ref.startswith("#") or tex_ref[1:] not in textures:
                    errors.append(f"{bf.name} el[{idx}] face {fname}: bad texture {tex_ref}")
                for u in fobj["uv"]:
                    if not (0.0 <= u <= 16.0):
                        errors.append(f"{bf.name} el[{idx}] face {fname}: uv {u} out of [0,16]")

    # 2. Validate 4 block-item models
    for name in ["operation_phoenix_pod.json", "personal_space_gate.json", "universal_lottery_machine.json", "universal_polymerizer.json"]:
        itf = ITEM_MODEL_DIR / name
        data = json.loads(itf.read_text(encoding="utf-8"))
        parent = data.get("parent", "")
        if not parent.startswith("universal_tool:block/"):
            errors.append(f"item/{name}: unexpected parent {parent}")
        else:
            pfile = BLOCK_MODEL_DIR / (parent.split("/")[-1] + ".json")
            if not pfile.exists():
                errors.append(f"item/{name}: missing parent {pfile}")
        if "gui" not in data.get("display", {}):
            errors.append(f"item/{name}: missing gui display transform")

    # 3. Validate phoenix_pod_glass_liquid.png interior alpha range (~95..120)
    liq = Image.open(TEX_DIR / "phoenix_pod_glass_liquid.png")
    alphas = [liq.getpixel((x, y))[3] for y in range(6, 58) for x in range(14, 58)]
    avg_alpha = sum(alphas) / len(alphas)
    if not (90 <= avg_alpha <= 125):
        errors.append(f"phoenix_pod_glass_liquid.png interior avg alpha {avg_alpha:.1f} outside [90,125]")

    # 4. Validate lottery BER swatches (solid & metal)
    lot = Image.open(TEX_DIR / "universal_lottery_machine.png")
    for y in range(1, 7):
        for x in range(1, 7):
            if lot.getpixel((x, y)) != (255, 255, 255, 255):
                errors.append(f"lottery solid swatch pixel ({x},{y}) != white")
        for x in range(9, 15):
            r, g, b, a = lot.getpixel((x, y))
            if a != 255 or min(r, g, b) < 225:
                errors.append(f"lottery metal swatch pixel ({x},{y}) too dark/transparent: {(r,g,b,a)}")

    if errors:
        raise RuntimeError("Validation failed:\n" + "\n".join(errors))
    print(f"Validated {len(block_files)} block models, 4 item models, 8 block PNGs (liquid avg alpha={avg_alpha:.1f}). OK!")


def main():
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    BLOCK_MODEL_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_MODEL_DIR.mkdir(parents=True, exist_ok=True)

    build_polymerizer_atlas()
    build_polymerizer_models()
    build_phoenix_pod_textures()
    build_phoenix_pod_models()
    build_lottery_atlas()
    build_lottery_models()
    build_personal_space_atlases()
    build_personal_space_models()
    validate_all()


if __name__ == "__main__":
    main()
