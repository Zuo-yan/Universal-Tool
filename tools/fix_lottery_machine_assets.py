"""
Fix Universal Lottery Machine .bbmodel, block/item JSON models, and repaint
universal_lottery_machine.png as a crisp 128x128 Arcane-Tech Jappa pixel-art atlas:
- Eliminates all 196 degenerate zero-area UV faces (every face >= 1x1 px).
- Clamps/adjusts all cubes cleanly into [0..16, 0..32, 0..16] (1x1x2 multiblock)
  while preserving the observatory chamber (Y=21.5, Z=3.6) and dial (Y=27.84, Z=1.2)
  alignment for UniversalLotteryMachineRenderer.
- Replaces blurry 256x256 gradient/noise texture with stepped Arcane-Tech pixel clusters
  plus dedicated 4x4 BER tint swatches.
"""

from __future__ import annotations

import base64
import json
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BB_PATH = ROOT / "art/assets/universal_lottery_machine.bbmodel"
MODELS_DIR = ROOT / "src/main/resources/assets/universal_tool/models/block"
TEX_PATH = ROOT / "src/main/resources/assets/universal_tool/textures/block/universal_lottery_machine.png"

ATLAS_SIZE = 128

ANIMATED_ELEMENT_NAMES = {
    "celestial_die_core_glow",
    "celestial_facet_x_glow",
    "celestial_facet_y_glow",
    "star_ring_orbit_glow",
    "celestial_satellite_glow",
    "gauge_needle",
    "gauge_needle_pivot",
}

# Unified Arcane-Tech Palette (RGBA)
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

RUBY_DARK = (0x88, 0x1B, 0x2A, 0xFF)
RUBY_BRIGHT = (0xEF, 0x44, 0x44, 0xFF)
EMERALD_DARK = (0x14, 0x53, 0x2D, 0xFF)
EMERALD_BRIGHT = (0x34, 0xD3, 0x99, 0xFF)
PURE_WHITE = (0xFF, 0xFF, 0xFF, 0xFF)


class Canvas:
    def __init__(self, width: int, height: int, fill=(0, 0, 0, 0)):
        self.w = width
        self.h = height
        self.px = [bytearray(fill) * width for _ in range(height)]

    def set(self, x: int, y: int, rgba: tuple[int, int, int, int]) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            off = x * 4
            self.px[y][off : off + 4] = bytes(rgba)

    def rect(self, x0: int, y0: int, w: int, h: int, rgba: tuple[int, int, int, int]) -> None:
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
        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
        path.write_bytes(png)


def sanitize_geometry(elements: list[dict]) -> None:
    """Keep the 2-block machine strictly inside [0..16, 0..32, 0..16] while preserving alignment."""
    # Explicit clean adjustments for parts that originally protruded outside [0..16, 0..32, 0..16]
    overrides = {
        # Bottom feet & waist bosses clamped to [0..16]
        "foot_screen_right_f": ([0.0, 0.0, 0.0], [2.8, 2.8, 2.8]),
        "foot_screen_left_f": ([13.2, 0.0, 0.0], [16.0, 2.8, 2.8]),
        "foot_screen_right_b": ([0.0, 0.0, 13.2], [2.8, 2.8, 16.0]),
        "foot_screen_left_b": ([13.2, 0.0, 13.2], [16.0, 2.8, 16.0]),
        "waist_boss_rf": ([0.0, 13.6, 0.0], [2.8, 15.4, 2.8]),
        "waist_boss_lf": ([13.2, 13.6, 0.0], [16.0, 15.4, 2.8]),
        "waist_boss_rb": ([0.0, 13.6, 13.2], [2.8, 15.4, 16.0]),
        "waist_boss_lb": ([13.2, 13.6, 13.2], [16.0, 15.4, 16.0]),
        # Front dispenser tray & scrap drawer shifted inside Z=[0..16]
        "chute_tray_floor": ([2.5, 0.8, 0.2], [13.5, 2.2, 3.0]),
        "chute_tray_lip": ([2.2, 2.2, 0.0], [13.8, 3.4, 0.8]),
        "chute_side_lip_r": ([2.2, 2.2, 0.6], [3.4, 5.5, 2.8]),
        "chute_side_lip_l": ([12.6, 2.2, 0.6], [13.8, 5.5, 2.8]),
        "scrap_drawer_face": ([3.4, 2.2, 0.7], [12.6, 5.6, 2.6]),
        "scrap_drawer_handle": ([6.2, 3.6, 0.3], [9.8, 4.4, 0.9]),
        "chute_interior_floor": ([3.4, 5.6, 0.6], [12.6, 6.2, 3.8]),
        "chute_interior_roof": ([3.4, 11.0, 0.6], [12.6, 11.6, 3.8]),
        "chute_arch_outer": ([3.0, 11.2, 0.4], [13.0, 12.8, 2.2]),
        # Flanking tubes & pipes tucked inside X=[0..16]
        "tube_lower_bracket_bot_r": ([0.0, 3.2, 7.6], [1.6, 4.4, 9.8]),
        "tube_lower_bracket_top_r": ([0.0, 14.2, 7.6], [1.6, 15.4, 9.8]),
        "tube_lower_glass_r": ([0.2, 4.4, 7.9], [1.4, 14.2, 9.5]),
        "tube_lower_core_r_glow": ([0.4, 4.8, 8.2], [1.2, 13.8, 9.2]),
        "tube_lower_bracket_bot_l": ([14.4, 3.2, 7.6], [16.0, 4.4, 9.8]),
        "tube_lower_bracket_top_l": ([14.4, 14.2, 7.6], [16.0, 15.4, 9.8]),
        "tube_lower_glass_l": ([14.6, 4.4, 7.9], [15.8, 14.2, 9.5]),
        "tube_lower_core_l_glow": ([14.8, 4.8, 8.2], [15.6, 13.8, 9.2]),
        "pipe_lower_loop_r": ([0.1, 2.2, 7.2], [1.5, 3.4, 9.2]),
        "pipe_lower_loop_l": ([14.5, 2.2, 7.2], [15.9, 3.4, 9.2]),
        # Upper console & controls inside Z=[0..16]
        "console_desk_slope": ([2.2, 14.8, 0.2], [13.8, 16.8, 3.2]),
        "button_pedestal": ([6.8, 16.2, 0.5], [9.2, 17.0, 2.3]),
        "button_housing_ring": ([6.6, 16.8, 0.6], [9.4, 17.5, 2.2]),
        "button_golden_cap_glow": ([7.0, 17.2, 0.8], [9.0, 18.4, 2.0]),
        "indicator_gem_red1_glow": ([10.2, 16.8, 0.5], [10.8, 17.5, 1.1]),
        "indicator_gem_red2_glow": ([5.2, 16.8, 0.5], [5.8, 17.5, 1.1]),
        "indicator_gem_red3_glow": ([4.2, 16.8, 0.5], [4.8, 17.5, 1.1]),
        # Side lever & upper tubes tucked inside X=[0..16]
        "lever_base_box": ([0.0, 14.5, 4.2], [1.4, 17.5, 7.8]),
        "lever_shaft": ([0.2, 16.8, 5.5], [1.0, 23.2, 6.3]),
        "lever_ball_knob_glow": ([0.0, 22.4, 4.8], [1.4, 24.0, 6.4]),
        "tube_upper_bracket_bot_r": ([0.0, 15.8, 7.6], [1.6, 17.0, 9.8]),
        "tube_upper_bracket_top_r": ([0.0, 26.5, 7.6], [1.6, 27.7, 9.8]),
        "tube_upper_glass_r": ([0.2, 16.8, 7.9], [1.4, 26.6, 9.5]),
        "tube_upper_core_r_glow": ([0.4, 17.2, 8.2], [1.2, 26.2, 9.2]),
        "tube_upper_bracket_bot_l": ([14.4, 15.8, 7.6], [16.0, 17.0, 9.8]),
        "tube_upper_bracket_top_l": ([14.4, 26.5, 7.6], [16.0, 27.7, 9.8]),
        "tube_upper_glass_l": ([14.6, 16.8, 7.9], [15.8, 26.6, 9.5]),
        "tube_upper_core_l_glow": ([14.8, 17.2, 8.2], [15.6, 26.2, 9.2]),
        "pipe_upper_side_l": ([14.4, 16.8, 6.2], [15.4, 23.5, 7.4]),
        "side_pressure_gauge_body": ([14.6, 19.8, 6.2], [15.7, 23.2, 9.4]),
        "side_pressure_gauge_face": ([15.7, 20.2, 6.6], [16.0, 22.8, 9.0]),
        # Compact roof & amethyst crest into Y=[28.0..32.0] while keeping dial Y<=28.0
        "upper_rear_housing": ([1.5, 16.2, 5.2], [14.5, 28.0, 14.5]),
        "upper_pillar_rf": ([0.5, 16.2, 0.5], [2.5, 28.0, 2.5]),
        "upper_pillar_lf": ([13.5, 16.2, 0.5], [15.5, 28.0, 2.5]),
        "upper_pillar_rb": ([0.5, 16.2, 13.5], [2.5, 28.0, 15.5]),
        "upper_pillar_lb": ([13.5, 16.2, 13.5], [15.5, 28.0, 15.5]),
        "upper_side_wall_r": ([1.2, 16.5, 2.0], [2.5, 27.8, 5.6]),
        "upper_side_wall_l": ([13.5, 16.5, 2.0], [14.8, 27.8, 5.6]),
        "chamber_starfield_wall_glow": ([2.5, 16.5, 4.8], [13.5, 27.8, 5.4]),
        "gauge_plate_base": ([4.6, 25.6, 1.2], [11.4, 27.8, 2.0]),
        "gauge_brass_arch": ([4.4, 27.6, 0.9], [11.6, 28.2, 2.1]),
        "roof_base_cornice": ([0.0, 28.0, 0.0], [16.0, 28.9, 16.0]),
        "roof_arch_center": ([3.0, 28.9, 0.8], [13.0, 30.1, 15.2]),
        "roof_arch_side_r": ([0.8, 28.9, 0.8], [3.0, 29.6, 15.2]),
        "roof_arch_side_l": ([13.0, 28.9, 0.8], [15.2, 29.6, 15.2]),
        "roof_rim_center": ([2.8, 29.8, 0.4], [13.2, 30.3, 1.6]),
        "roof_rim_side_r": ([0.6, 29.3, 0.4], [3.0, 29.9, 1.6]),
        "roof_rim_side_l": ([13.0, 29.3, 0.4], [15.4, 29.9, 1.6]),
        "roof_center_crest": ([6.0, 30.1, 6.0], [10.0, 30.5, 10.0]),
        "crystal_shard_center_glow": ([7.2, 30.5, 7.2], [8.8, 32.0, 8.8]),
        "crystal_shard_r_glow": ([5.7, 30.5, 7.4], [7.2, 31.8, 8.6]),
        "crystal_shard_l_glow": ([8.8, 30.5, 7.4], [10.3, 31.8, 8.6]),
        "crystal_shard_f_glow": ([7.4, 30.5, 5.7], [8.6, 31.8, 7.2]),
        "crystal_shard_b_glow": ([7.4, 30.5, 8.8], [8.6, 31.8, 10.3]),
    }

    for el in elements:
        name = el["name"]
        if name in overrides:
            el["from"], el["to"] = overrides[name]
        # Snap lever & crystal origins after shifting
        if name in ("lever_shaft", "lever_ball_knob_glow"):
            el["origin"] = [0.6, 16.8, 5.9]
            el["rotation"] = [-22.5, 0, 0]
        elif name == "crystal_shard_r_glow":
            el["origin"] = [6.45, 30.5, 8.0]
            el["rotation"] = [0, 0, -22.5]
        elif name == "crystal_shard_l_glow":
            el["origin"] = [9.55, 30.5, 8.0]
            el["rotation"] = [0, 0, 22.5]
        elif name == "crystal_shard_f_glow":
            el["origin"] = [8.0, 30.5, 6.45]
            el["rotation"] = [-22.5, 0, 0]
        elif name == "crystal_shard_b_glow":
            el["origin"] = [8.0, 30.5, 9.55]
            el["rotation"] = [22.5, 0, 0]

        # Clamp safety for every non-animated cube
        for i in range(3):
            max_b = 32.0 if i == 1 else 16.0
            el["from"][i] = round(max(0.0, min(max_b - 0.1, el["from"][i])), 2)
            el["to"][i] = round(max(el["from"][i] + 0.1, min(max_b, el["to"][i])), 2)


def classify_cube_material(name: str) -> str:
    n = name.lower()
    if "crystal" in n or "celestial" in n:
        return "amethyst_glow"
    if "starfield" in n:
        return "starfield"
    if "core_r_glow" in n or "core_l_glow" in n or "back_glow" in n or "light_" in n:
        return "cyan_glow"
    if "glass" in n:
        return "cyan_glass"
    if "green" in n:
        return "emerald"
    if "red" in n or "ball_knob" in n:
        return "ruby"
    if "gauge_face" in n or "gauge_plate" in n:
        return "dial_face"
    if (
        "trim" in n
        or "boss" in n
        or "brass" in n
        or "button" in n
        or "handle" in n
        or "frame" in n
        or "corner" in n
        or "bracket" in n
        or "ring" in n
        or "rim" in n
        or "crest" in n
        or "pipe" in n
        or "gauge" in n
        or "toggle" in n
        or "lever" in n
        or "lamp" in n
        or "foot" in n
    ):
        return "brass"
    return "deepslate"


def paint_rect_jappa(canvas: Canvas, x0: int, y0: int, w: int, h: int, mat: str, face_dir: str) -> None:
    """Paint crisp stepped Arcane-Tech pixel clusters into an integer (w x h) UV patch."""
    if mat == "deepslate":
        base = DS_MID if face_dir == "up" else (DS_VOID if face_dir == "down" else DS_DARK)
        canvas.rect(x0, y0, w, h, base)
        if w >= 2 and h >= 2:
            # Top/left crisp Jappa edge bevel + brick mortar rhythm
            for x in range(x0, x0 + w):
                canvas.set(x, y0, DS_EDGE if face_dir == "up" else DS_LIGHT)
                canvas.set(x, y0 + h - 1, DS_VOID)
            for y in range(y0 + 1, y0 + h - 1):
                canvas.set(x0, y, DS_LIGHT)
                canvas.set(x0 + w - 1, y, DS_VOID)
            for y in range(y0 + 1, y0 + h - 1):
                for x in range(x0 + 1, x0 + w - 1):
                    if y % 4 == 0:
                        canvas.set(x, y, DS_VOID)
                    elif (x + (y // 4) * 3) % 6 == 0:
                        canvas.set(x, y, DS_VOID)
                    elif (x + y) % 5 == 0:
                        canvas.set(x, y, DS_MID)
    elif mat == "brass":
        base = GOLD_BRIGHT if face_dir == "up" else (GOLD_DARK if face_dir == "down" else GOLD_MID)
        canvas.rect(x0, y0, w, h, base)
        if w >= 2 and h >= 2:
            for x in range(x0, x0 + w):
                canvas.set(x, y0, GOLD_SHINE if face_dir != "down" else GOLD_MID)
                canvas.set(x, y0 + h - 1, GOLD_DARK)
            for y in range(y0 + 1, y0 + h - 1):
                canvas.set(x0, y, GOLD_BRIGHT)
                canvas.set(x0 + w - 1, y, GOLD_DARK)
            # Rivets near corners when large enough
            if w >= 4 and h >= 4:
                canvas.set(x0 + 1, y0 + 1, GOLD_SHINE)
                canvas.set(x0 + w - 2, y0 + 1, GOLD_SHINE)
    elif mat == "amethyst_glow":
        canvas.rect(x0, y0, w, h, AME_MID)
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if x == x0 or y == y0:
                    canvas.set(x, y, AME_GLOW)
                elif x == x0 + w - 1 or y == y0 + h - 1:
                    canvas.set(x, y, AME_DARK)
                elif (x + y) % 3 == 0:
                    canvas.set(x, y, AME_BRIGHT)
    elif mat == "cyan_glow":
        canvas.rect(x0, y0, w, h, CYAN_BRIGHT)
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if (x + y) % 2 == 0:
                    canvas.set(x, y, CYAN_GLOW)
                elif y == y0 + h - 1:
                    canvas.set(x, y, CYAN_MID)
    elif mat == "cyan_glass":
        canvas.rect(x0, y0, w, h, CYAN_DARK)
        if w >= 2 and h >= 2:
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    if x == x0 or y == y0:
                        canvas.set(x, y, CYAN_MID)
                    elif (x - x0) == (y - y0):
                        canvas.set(x, y, CYAN_GLOW)
    elif mat == "starfield":
        canvas.rect(x0, y0, w, h, DS_VOID)
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                hval = ((x - x0) * 17 + (y - y0) * 31) % 19
                if hval == 0:
                    canvas.set(x, y, CYAN_GLOW)
                elif hval == 5:
                    canvas.set(x, y, AME_GLOW)
                elif hval == 11:
                    canvas.set(x, y, GOLD_SHINE)
    elif mat == "dial_face":
        canvas.rect(x0, y0, w, h, (0xE8, 0xDF, 0xC6, 0xFF))
        if w >= 2 and h >= 2:
            for x in range(x0, x0 + w):
                canvas.set(x, y0, GOLD_DARK)
                canvas.set(x, y0 + h - 1, GOLD_DARK)
            for y in range(y0, y0 + h):
                canvas.set(x0, y, GOLD_DARK)
                canvas.set(x0 + w - 1, y, GOLD_DARK)
            if w >= 4 and h >= 3:
                canvas.set(x0 + w // 2, y0 + 1, RUBY_BRIGHT)
    elif mat == "ruby":
        canvas.rect(x0, y0, w, h, RUBY_BRIGHT)
        if w >= 2 and h >= 2:
            canvas.set(x0, y0, (0xFF, 0xA8, 0xA8, 0xFF))
            canvas.set(x0 + w - 1, y0 + h - 1, RUBY_DARK)
    elif mat == "emerald":
        canvas.rect(x0, y0, w, h, EMERALD_BRIGHT)
        if w >= 2 and h >= 2:
            canvas.set(x0, y0, CYAN_GLOW)
            canvas.set(x0 + w - 1, y0 + h - 1, EMERALD_DARK)


def pack_and_paint(elements: list[dict]) -> Canvas:
    """Pack every cube face as an integer >=1x1 px rect inside 128x128 (with shared identical rects per cube family)."""
    canvas = Canvas(ATLAS_SIZE, ATLAS_SIZE, DS_VOID)

    # Reserve top-left 8x8 strip for BER tint swatches:
    # (0,0)-(4,4): pure white for BER tinted gems/pips/wheel/obsidian/laser (u: 0..0.03125, v: 0..0.03125)
    # (4,0)-(8,4): warm metallic noise white for BER brass/reels (u: 0.03125..0.0625, v: 0..0.03125)
    canvas.rect(0, 0, 4, 4, PURE_WHITE)
    for y in range(4):
        for x in range(4, 8):
            canvas.set(x, y, (245, 245, 245, 255) if (x + y) % 2 == 0 else PURE_WHITE)

    # Collect unique (material, face_dir, pw, ph) patches to maximize 128x128 density cleanly
    patches: dict[tuple[str, str, int, int], tuple[int, int]] = {}
    face_reqs: list[tuple[dict, str, tuple[str, str, int, int]]] = []

    for el in elements:
        fr, to = el["from"], el["to"]
        dx = to[0] - fr[0]
        dy = to[1] - fr[1]
        dz = to[2] - fr[2]
        mat = classify_cube_material(el["name"])
        dims = {
            "north": (dx, dy),
            "south": (dx, dy),
            "east": (dz, dy),
            "west": (dz, dy),
            "up": (dx, dz),
            "down": (dx, dz),
        }
        for fn in ("north", "east", "south", "west", "up", "down"):
            rw, rh = dims[fn]
            # Strict >= 1px width & height (1 px / model unit, rounded up for sub-1-unit details)
            pw = max(1, min(16, int(math.ceil(rw - 1e-6))))
            ph = max(1, min(16, int(math.ceil(rh - 1e-6))))
            key = (mat, fn, pw, ph)
            patches.setdefault(key, (0, 0))
            face_reqs.append((el, fn, key))

    # Shelf-pack unique patches starting at y=8 (below BER swatch row)
    sorted_keys = sorted(patches.keys(), key=lambda k: (-k[3], -k[2], k[0], k[1]))
    cx, cy, row_h = 0, 8, 0
    for key in sorted_keys:
        _, _, pw, ph = key
        if cx + pw > ATLAS_SIZE:
            cx = 0
            cy += row_h
            row_h = 0
        if cy + ph > ATLAS_SIZE:
            raise RuntimeError(f"128x128 atlas overflow at {key}, cy={cy}")
        patches[key] = (cx, cy)
        paint_rect_jappa(canvas, cx, cy, pw, ph, key[0], key[1])
        cx += pw
        row_h = max(row_h, ph)

    # Assign UV coordinates back onto every cube face
    for el, fn, key in face_reqs:
        px, py = patches[key]
        _, _, pw, ph = key
        el["faces"][fn] = {
            "uv": [px, py, px + pw, py + ph],
            "texture": 0,
        }

    return canvas


def sanitize_mc_rotation(rot: list[float] | None, origin: list[float]) -> dict | None:
    if not rot:
        return None
    rx, ry, rz = rot[0] or 0.0, rot[1] or 0.0, rot[2] or 0.0
    axis, angle = "x", rx
    if abs(ry) > abs(angle):
        axis, angle = "y", ry
    if abs(rz) > abs(angle):
        axis, angle = "z", rz
    if abs(angle) < 11.25:
        return None
    if angle <= -33.75:
        snapped = -45.0
    elif angle <= -11.25:
        snapped = -22.5
    elif angle < 33.75:
        snapped = 22.5
    else:
        snapped = 45.0
    return {
        "angle": snapped,
        "axis": axis,
        "origin": [round(origin[0], 2), round(origin[1], 2), round(origin[2], 2)],
    }


def export_mc_models(elements: list[dict]) -> None:
    scale = 16.0 / ATLAS_SIZE  # 128 -> UV in [0..16]

    def to_mc_faces(faces: dict) -> dict:
        out = {}
        for fn, f in faces.items():
            uv = f["uv"]
            out[fn] = {
                "uv": [round(v * scale, 4) for v in uv],
                "texture": "#0",
            }
        return out

    bottom_els = []
    top_els = []
    full_els = []

    for el in elements:
        name = el["name"]
        fr = list(el["from"])
        to = list(el["to"])
        rot = el.get("rotation")
        origin = el.get("origin", [8, 0, 8])
        mc_faces = to_mc_faces(el["faces"])

        mid_y = (fr[1] + to[1]) / 2.0
        if mid_y < 16.0:
            # Clamp bottom elements inside Y=[0..16]
            b_from = [fr[0], max(0.0, min(15.9, fr[1])), fr[2]]
            b_to = [to[0], max(b_from[1] + 0.1, min(16.0, to[1])), to[2]]
            mc_el = {"name": name, "from": b_from, "to": b_to, "faces": mc_faces}
            srot = sanitize_mc_rotation(rot, origin)
            if srot:
                mc_el["rotation"] = srot
            bottom_els.append(mc_el)
        elif name not in ANIMATED_ELEMENT_NAMES:
            # Shift top elements by Y - 16 so they sit inside [0..16, 0..16, 0..16]
            t_from = [fr[0], round(max(0.0, min(15.9, fr[1] - 16.0)), 2), fr[2]]
            t_to = [to[0], round(max(t_from[1] + 0.1, min(16.0, to[1] - 16.0)), 2), to[2]]
            mc_el = {"name": name, "from": t_from, "to": t_to, "faces": mc_faces}
            srot = sanitize_mc_rotation(rot, [origin[0], origin[1] - 16.0, origin[2]])
            if srot:
                mc_el["rotation"] = srot
            top_els.append(mc_el)

        # Full item model (0.5x centered in [0..16])
        f_from = [round(fr[0] * 0.5 + 4.0, 2), round(fr[1] * 0.5, 2), round(fr[2] * 0.5 + 4.0, 2)]
        f_to = [round(to[0] * 0.5 + 4.0, 2), round(to[1] * 0.5, 2), round(to[2] * 0.5 + 4.0, 2)]
        f_el = {"name": name, "from": f_from, "to": f_to, "faces": mc_faces}
        srot = sanitize_mc_rotation(rot, [origin[0] * 0.5 + 4.0, origin[1] * 0.5, origin[2] * 0.5 + 4.0])
        if srot:
            f_el["rotation"] = srot
        full_els.append(f_el)

    header = {
        "format_version": "1.21.1",
        "credit": "Universal Lottery Machine Arcane-Tech Remastered",
        "render_type": "minecraft:cutout",
        "texture_size": [ATLAS_SIZE, ATLAS_SIZE],
        "textures": {
            "0": "universal_tool:block/universal_lottery_machine",
            "particle": "universal_tool:block/universal_lottery_machine",
        },
    }
    (MODELS_DIR / "universal_lottery_machine_bottom.json").write_text(
        json.dumps({**header, "elements": bottom_els}, indent=2) + "\n", encoding="utf-8"
    )
    (MODELS_DIR / "universal_lottery_machine_top.json").write_text(
        json.dumps({**header, "elements": top_els}, indent=2) + "\n", encoding="utf-8"
    )
    (MODELS_DIR / "universal_lottery_machine_full.json").write_text(
        json.dumps({**header, "elements": full_els}, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Exported lottery JSONs: {len(bottom_els)} bottom, {len(top_els)} top, {len(full_els)} full")


def main() -> None:
    bb = json.loads(BB_PATH.read_text(encoding="utf-8"))
    elements = bb["elements"]
    sanitize_geometry(elements)
    canvas = pack_and_paint(elements)
    canvas.save_png(TEX_PATH)

    bb["resolution"] = {"width": ATLAS_SIZE, "height": ATLAS_SIZE}
    png_b64 = base64.b64encode(TEX_PATH.read_bytes()).decode("ascii")
    for tex in bb.get("textures", []):
        tex["width"] = ATLAS_SIZE
        tex["height"] = ATLAS_SIZE
        tex["uv_width"] = ATLAS_SIZE
        tex["uv_height"] = ATLAS_SIZE
        tex["path"] = str(TEX_PATH)
        tex["source"] = f"data:image/png;base64,{png_b64}"

    BB_PATH.write_text(json.dumps(bb, indent=2) + "\n", encoding="utf-8")
    export_mc_models(elements)
    print("Fixed universal_lottery_machine.bbmodel and saved crisp 128x128 atlas.")


if __name__ == "__main__":
    main()
