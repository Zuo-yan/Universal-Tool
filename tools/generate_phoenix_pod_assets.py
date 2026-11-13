"""Generate Arcane-Tech textures, GUI, and synced .bbmodel for Operation Phoenix Pod."""

from __future__ import annotations

import json
import struct
import uuid
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEX_DIR = ROOT / "src/main/resources/assets/universal_tool/textures"
BB_ROOT = ROOT / "operation_phoenix_pod.bbmodel"
BB_ART = ROOT / "art/assets/operation_phoenix_pod.bbmodel"

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
    def __init__(self, width: int, height: int, fill=(0, 0, 0, 0)):
        self.w = width
        self.h = height
        self.px = [bytearray(fill) * width for _ in range(height)]

    def set(self, x: int, y: int, rgba: tuple[int, int, int, int]) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            off = x * 4
            self.px[y][off : off + 4] = bytes(rgba)

    def rect(self, x0: int, y0: int, x1: int, y1: int, rgba: tuple[int, int, int, int]) -> None:
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, rgba)

    def border(self, x0: int, y0: int, x1: int, y1: int, rgba: tuple[int, int, int, int]) -> None:
        self.rect(x0, y0, x1, y0 + 1, rgba)
        self.rect(x0, y1 - 1, x1, y1, rgba)
        self.rect(x0, y0, x0 + 1, y1, rgba)
        self.rect(x1 - 1, y0, x1, y1, rgba)

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = b"".join(b"\x00" + bytes(row) for row in self.px)

        def chunk(tag: bytes, data: bytes) -> bytes:
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        ihdr = struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0)
        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
        path.write_bytes(png)


def paint_pod_base() -> Canvas:
    c = Canvas(16, 16, DS_DARK)
    # Center chamber deck [1..15, 1..15]
    c.rect(1, 1, 15, 15, DS_VOID)
    c.border(2, 2, 14, 14, DS_MID)
    c.border(3, 3, 13, 13, GOLD_DARK)
    c.border(4, 4, 12, 12, CYAN_DARK)
    c.rect(5, 5, 11, 11, DS_DARK)
    c.border(5, 5, 11, 11, CYAN_MID)
    c.rect(6, 6, 10, 10, AME_DARK)
    c.rect(7, 7, 9, 9, CYAN_BRIGHT)

    # Pillars sampled on x=[0..1] and x=[14..15], y=[0..12]
    for px in (0, 14):
        c.rect(px, 0, px + 2, 13, DS_MID)
        c.rect(px + 1, 0, px + 2, 13, DS_DARK)
        c.rect(px, 1, px + 2, 3, GOLD_MID)
        c.set(px, 1, GOLD_BRIGHT)
        c.rect(px, 10, px + 2, 12, GOLD_MID)
        c.set(px, 10, GOLD_BRIGHT)
        c.set(px, 6, CYAN_BRIGHT)
        c.set(px + 1, 6, CYAN_DARK)

    # Central rear spine sampled on x=[6..9], y=[3..15]
    c.rect(6, 3, 10, 16, DS_DARK)
    c.rect(6, 3, 7, 16, DS_MID)
    c.rect(9, 3, 10, 16, DS_VOID)
    for y in range(4, 13):
        c.set(7, y, CYAN_BRIGHT if y % 2 == 0 else CYAN_MID)
        c.set(8, y, CYAN_MID if y % 2 == 0 else AME_MID)
    c.rect(6, 5, 10, 6, GOLD_MID)
    c.rect(6, 10, 10, 11, GOLD_MID)

    # Base plate side band sampled on x=[1..15], y=[13..15]
    c.rect(0, 13, 16, 14, GOLD_MID)
    c.rect(1, 13, 15, 14, GOLD_BRIGHT)
    c.rect(0, 14, 16, 16, DS_DARK)
    c.rect(0, 15, 16, 16, DS_VOID)
    for x in (2, 5, 10, 13):
        c.set(x, 14, DS_LIGHT)
    c.set(7, 14, CYAN_GLOW)
    c.set(8, 14, CYAN_BRIGHT)
    return c


def paint_pod_top() -> Canvas:
    c = Canvas(16, 16, DS_DARK)
    # Top cap interior [1..15, 1..15]
    c.rect(1, 1, 15, 15, DS_MID)
    c.border(2, 2, 14, 14, GOLD_MID)
    c.rect(3, 3, 13, 13, DS_VOID)
    c.border(4, 4, 12, 12, AME_DARK)
    c.border(5, 5, 11, 11, CYAN_MID)
    c.rect(6, 6, 10, 10, AME_MID)
    c.rect(7, 7, 9, 9, CYAN_GLOW)

    # Top cap sides sampled on x=[1..15], y=[0..2]
    c.rect(0, 0, 16, 1, DS_LIGHT)
    c.rect(0, 1, 16, 2, GOLD_BRIGHT)
    c.set(1, 1, GOLD_SHINE)
    c.set(14, 1, GOLD_MID)
    c.rect(0, 2, 16, 3, DS_VOID)
    c.set(7, 1, CYAN_GLOW)
    c.set(8, 1, CYAN_BRIGHT)

    # Upper pillars sampled on x=[0..1] and x=[14..15], y=[3..15]
    for px in (0, 14):
        c.rect(px, 3, px + 2, 16, DS_MID)
        c.rect(px + 1, 3, px + 2, 16, DS_DARK)
        c.rect(px, 4, px + 2, 6, GOLD_MID)
        c.set(px, 4, GOLD_BRIGHT)
        c.rect(px, 13, px + 2, 15, GOLD_MID)
        c.set(px, 9, AME_BRIGHT)
        c.set(px + 1, 9, AME_DARK)

    # Upper rear spine sampled on x=[6..9], y=[3..15]
    c.rect(6, 3, 10, 16, DS_DARK)
    c.rect(6, 3, 7, 16, DS_MID)
    c.rect(9, 3, 10, 16, DS_VOID)
    for y in range(4, 15):
        c.set(7, y, CYAN_BRIGHT if y % 3 else AME_BRIGHT)
        c.set(8, y, CYAN_MID if y % 2 else CYAN_DARK)
    c.rect(6, 7, 10, 8, GOLD_MID)
    c.rect(6, 12, 10, 13, GOLD_MID)
    return c


def paint_pod_glass(liquid: bool) -> Canvas:
    if not liquid:
        c = Canvas(16, 16, (0x17, 0x4B, 0x58, 0x6E))
        c.border(0, 0, 16, 16, (0x3E, 0x46, 0x59, 0xC8))
        # Subtle gilded corner pins and crisp specular streak
        for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
            c.set(x, y, (0xDF, 0xAD, 0x34, 0xE8))
        for i in range(3, 8):
            c.set(i, 10 - i, (0x9C, 0xF5, 0xF0, 0xB8))
        for i in range(9, 13):
            c.set(i, 21 - i, (0x42, 0xC6, 0xD2, 0xA0))
        return c

    # Bio-stasis liquid glass (also used on sliding glass doors by PhoenixPodRenderer)
    c = Canvas(16, 16, (0x22, 0x7A, 0x8C, 0x9E))
    c.border(0, 0, 16, 16, (0xDF, 0xAD, 0x34, 0xE6))
    c.border(1, 1, 15, 15, (0x42, 0xC6, 0xD2, 0xB8))
    # Vertical stasis energy conduits and bubbles
    for y in range(2, 14):
        if y % 3 == 0:
            c.set(4, y, (0x9C, 0xF5, 0xF0, 0xDC))
            c.set(11, y - 1, (0xD8, 0xA8, 0xFF, 0xD0))
    for bx, by in ((6, 4), (9, 7), (5, 11), (10, 12), (7, 9), (8, 3)):
        c.set(bx, by, (0x9C, 0xF5, 0xF0, 0xE8))
        c.set(bx + 1, by, (0x42, 0xC6, 0xD2, 0xC8))
    # Subtle horizontal meniscus lines
    c.rect(2, 5, 14, 6, (0x42, 0xC6, 0xD2, 0xB0))
    c.rect(2, 10, 14, 11, (0x77, 0x42, 0xB8, 0x90))
    return c


def draw_slot(c: Canvas, x: int, y: int) -> None:
    """18x18 Arcane-Tech recessed item slot matching polymerizer_gui.png."""
    c.rect(x, y, x + 18, y + 18, DS_VOID)
    c.rect(x + 1, y + 1, x + 17, y + 17, (0x18, 0x1B, 0x24, 0xFF))
    # Top/left inner shadow, bottom/right highlight rim
    c.rect(x, y, x + 18, y + 1, (0x0D, 0x0F, 0x15, 0xFF))
    c.rect(x, y, x + 1, y + 18, (0x0D, 0x0F, 0x15, 0xFF))
    c.rect(x + 1, y + 17, x + 18, y + 18, DS_LIGHT)
    c.rect(x + 17, y + 1, x + 18, y + 18, DS_LIGHT)


def paint_phoenix_gui() -> Canvas:
    c = Canvas(256, 256, (0, 0, 0, 0))
    w, h = 176, 166
    # Outer obsidian-slate frame
    c.rect(0, 0, w, h, DS_VOID)
    c.border(0, 0, w, h, (0x0A, 0x0C, 0x12, 0xFF))
    c.rect(2, 2, w - 2, h - 2, DS_DARK)
    c.border(2, 2, w - 2, h - 2, DS_MID)
    c.border(4, 4, w - 4, h - 4, DS_VOID)

    # Corner gilded brass brackets
    for bx, by in ((1, 1), (w - 8, 1), (1, h - 8), (w - 8, h - 8)):
        c.rect(bx, by, bx + 7, by + 7, GOLD_DARK)
        c.rect(bx + 1, by + 1, bx + 6, by + 6, GOLD_MID)
        c.set(bx + 2, by + 2, GOLD_BRIGHT)
        c.set(bx + 4, by + 4, CYAN_GLOW)

    # Header bar
    c.rect(12, 5, w - 12, 16, DS_VOID)
    c.border(12, 5, w - 12, 16, GOLD_MID)
    c.set(14, 10, CYAN_BRIGHT)
    c.set(w - 15, 10, CYAN_BRIGHT)

    # Bio-chamber banner well (status line at y=20, conduit box connecting slots)
    c.rect(10, 18, w - 10, 68, (0x19, 0x1D, 0x27, 0xFF))
    c.border(10, 18, w - 10, 68, DS_MID)
    c.rect(11, 18, w - 11, 19, GOLD_DARK)

    # Conduit lines connecting DNA slot (56,36) -> center (76,36) -> Nutrient slot (104,36)
    c.rect(72, 43, 76, 45, CYAN_DARK)
    c.rect(100, 43, 104, 45, AME_DARK)
    c.set(74, 44, CYAN_BRIGHT)
    c.set(102, 44, AME_BRIGHT)

    # Recessed slots for DNA (56,36) & Nutrient (104,36) with brass corner accents
    draw_slot(c, 55, 35)
    c.border(54, 34, 74, 54, GOLD_MID)
    draw_slot(c, 103, 35)
    c.border(102, 34, 122, 54, GOLD_MID)

    # Tiny DNA helix watermark inside DNA slot & flask watermark inside Nutrient slot
    for i in range(4):
        c.set(61 + i, 41 + i, (0x22, 0x35, 0x45, 0xFF))
        c.set(66 - i, 41 + i, (0x22, 0x35, 0x45, 0xFF))
    c.rect(110, 42, 114, 47, (0x2D, 0x22, 0x3C, 0xFF))
    c.rect(111, 40, 113, 42, (0x2D, 0x22, 0x3C, 0xFF))

    # Center progress / start button well at (76, 36, 24x16)
    c.rect(75, 35, 101, 53, DS_VOID)
    c.border(75, 35, 101, 53, DS_LIGHT)

    # Separator bar above player inventory
    c.rect(8, 70, w - 8, 71, DS_LIGHT)
    c.rect(8, 71, w - 8, 72, DS_VOID)

    # Player inventory (3x9 at 8,84) & hotbar (1x9 at 8,142)
    for row in range(3):
        for col in range(9):
            draw_slot(c, 7 + col * 18, 83 + row * 18)
    for col in range(9):
        draw_slot(c, 7 + col * 18, 141)

    # Off-panel sprites at u=176
    # 1. Cultivating progress fill (176, 0, 24x16): glowing soul-cyan energy arrow/bar
    c.rect(176, 0, 200, 16, DS_VOID)
    c.border(176, 0, 200, 16, CYAN_DARK)
    c.rect(177, 3, 199, 13, CYAN_MID)
    c.rect(177, 5, 199, 11, CYAN_BRIGHT)
    c.rect(177, 7, 199, 9, CYAN_GLOW)
    for x in range(179, 198, 4):
        c.set(x, 4, AME_GLOW)
        c.set(x, 11, AME_BRIGHT)

    # 2. Ready state indicator (176, 16, 24x16): bright gilded & cyan-emerald crest
    c.rect(176, 16, 200, 32, DS_VOID)
    c.border(176, 16, 200, 32, GOLD_BRIGHT)
    c.rect(178, 18, 198, 30, CYAN_DARK)
    c.rect(180, 20, 196, 28, CYAN_BRIGHT)
    c.rect(183, 22, 193, 26, CYAN_GLOW)
    c.set(188, 24, (0xFF, 0xFF, 0xFF, 0xFF))

    # 3. Start button idle (176, 32, 24x16)
    c.rect(176, 32, 200, 48, DS_MID)
    c.border(176, 32, 200, 48, GOLD_MID)
    c.rect(177, 33, 199, 34, GOLD_BRIGHT)
    # Play triangle glyph in cyan
    for dx in range(6):
        c.rect(185 + dx, 36 + dx // 2, 186 + dx, 44 - dx // 2, CYAN_BRIGHT)

    # 4. Start button hovered (176, 48, 24x16)
    c.rect(176, 48, 200, 64, DS_LIGHT)
    c.border(176, 48, 200, 64, GOLD_SHINE)
    c.rect(177, 49, 199, 50, (0xFF, 0xFA, 0xD0, 0xFF))
    for dx in range(6):
        c.rect(185 + dx, 52 + dx // 2, 186 + dx, 60 - dx // 2, CYAN_GLOW)

    return c


def build_synced_bbmodel() -> dict:
    """Create a java_block 2-block .bbmodel synced with the runtime JSON geometry + BER doors."""
    textures = [
        ("0", "phoenix_pod_base", TEX_DIR / "block/phoenix_pod_base.png"),
        ("1", "phoenix_pod_top", TEX_DIR / "block/phoenix_pod_top.png"),
        ("2", "phoenix_pod_glass", TEX_DIR / "block/phoenix_pod_glass.png"),
        ("3", "phoenix_pod_glass_liquid", TEX_DIR / "block/phoenix_pod_glass_liquid.png"),
    ]
    bb_textures = [
        {
            "path": str(path),
            "name": f"{name}.png",
            "folder": "block",
            "namespace": "universal_tool",
            "id": tid,
            "width": 16,
            "height": 16,
            "uv_width": 16,
            "uv_height": 16,
            "particle": tid == "0",
            "visible": True,
            "internal": True,
            "saved": True,
            "source": "data:image/png;base64," + __import__("base64").b64encode(path.read_bytes()).decode("ascii"),
            "uuid": str(uuid.uuid5(uuid.NAMESPACE_DNS, f"phoenix_pod.tex.{tid}")),
        }
        for tid, name, path in textures
    ]

    def el(name: str, fr: list[float], to: list[float], tex_id: int, faces_uv: dict[str, list[int]]):
        return {
            "name": name,
            "box_uv": False,
            "rescale": False,
            "locked": False,
            "from": fr,
            "to": to,
            "autouv": 0,
            "color": tex_id,
            "origin": [8, 0, 8],
            "faces": {fn: {"uv": uv, "texture": tex_id} for fn, uv in faces_uv.items()},
            "type": "cube",
            "uuid": str(uuid.uuid5(uuid.NAMESPACE_DNS, f"phoenix_pod.el.{name}")),
        }

    elements = [
        el("base_plate", [1, 0, 1], [15, 3, 15], 0, {
            "down": [1, 1, 15, 15], "up": [1, 1, 15, 15],
            "north": [1, 13, 15, 16], "south": [1, 13, 15, 16],
            "west": [1, 13, 15, 16], "east": [1, 13, 15, 16],
        }),
        el("lower_pillar_nw", [1, 3, 1], [3, 16, 3], 0, {
            "north": [0, 0, 2, 13], "south": [14, 0, 16, 13],
            "west": [0, 0, 2, 13], "east": [14, 0, 16, 13],
        }),
        el("lower_pillar_ne", [13, 3, 1], [15, 16, 3], 0, {
            "north": [14, 0, 16, 13], "south": [0, 0, 2, 13],
            "west": [14, 0, 16, 13], "east": [0, 0, 2, 13],
        }),
        el("lower_pillar_sw", [1, 3, 13], [3, 16, 15], 0, {
            "north": [0, 0, 2, 13], "south": [14, 0, 16, 13],
            "west": [0, 0, 2, 13], "east": [14, 0, 16, 13],
        }),
        el("lower_pillar_se", [13, 3, 13], [15, 16, 15], 0, {
            "north": [14, 0, 16, 13], "south": [0, 0, 2, 13],
            "west": [14, 0, 16, 13], "east": [0, 0, 2, 13],
        }),
        el("lower_spine", [6, 3, 13], [10, 16, 15], 0, {
            "north": [6, 3, 10, 16], "south": [6, 3, 10, 16],
            "west": [13, 3, 15, 16], "east": [13, 3, 15, 16],
        }),
        el("lower_glass_west", [2, 3, 3], [3, 16, 13], 2, {"west": [3, 0, 13, 13], "east": [3, 0, 13, 13]}),
        el("lower_glass_east", [13, 3, 3], [14, 16, 13], 2, {"west": [3, 0, 13, 13], "east": [3, 0, 13, 13]}),
        el("lower_glass_south", [3, 3, 13], [13, 16, 14], 2, {"north": [3, 0, 13, 13], "south": [3, 0, 13, 13]}),
        # Upper half (Y = 16..32)
        el("upper_pillar_nw", [1, 16, 1], [3, 29, 3], 1, {
            "north": [0, 3, 2, 16], "south": [14, 3, 16, 16],
            "west": [0, 3, 2, 16], "east": [14, 3, 16, 16],
        }),
        el("upper_pillar_ne", [13, 16, 1], [15, 29, 3], 1, {
            "north": [14, 3, 16, 16], "south": [0, 3, 2, 16],
            "west": [14, 3, 16, 16], "east": [0, 3, 2, 16],
        }),
        el("upper_pillar_sw", [1, 16, 13], [3, 29, 15], 1, {
            "north": [0, 3, 2, 16], "south": [14, 3, 16, 16],
            "west": [0, 3, 2, 16], "east": [14, 3, 16, 16],
        }),
        el("upper_pillar_se", [13, 16, 13], [15, 29, 15], 1, {
            "north": [14, 3, 16, 16], "south": [0, 3, 2, 16],
            "west": [14, 3, 16, 16], "east": [0, 3, 2, 16],
        }),
        el("upper_spine", [6, 16, 13], [10, 29, 15], 1, {
            "north": [6, 3, 10, 16], "south": [6, 3, 10, 16],
            "west": [13, 3, 15, 16], "east": [13, 3, 15, 16],
        }),
        el("upper_glass_west", [2, 16, 3], [3, 29, 13], 2, {"west": [3, 3, 13, 16], "east": [3, 3, 13, 16]}),
        el("upper_glass_east", [13, 16, 3], [14, 29, 13], 2, {"west": [3, 3, 13, 16], "east": [3, 3, 13, 16]}),
        el("upper_glass_south", [3, 16, 13], [13, 29, 14], 2, {"north": [3, 3, 13, 16], "south": [3, 3, 13, 16]}),
        el("top_cap", [1, 29, 1], [15, 32, 15], 1, {
            "up": [1, 1, 15, 15], "down": [1, 1, 15, 15],
            "north": [1, 0, 15, 3], "south": [1, 0, 15, 3],
            "west": [1, 0, 15, 3], "east": [1, 0, 15, 3],
        }),
        # Dynamic BER front sliding doors (facing North -Z at z=2.56, Y=3.2..28.5)
        el("ber_door_left", [2.88, 3.2, 2.3], [8.0, 28.5, 2.8], 3, {
            "north": [0, 0, 16, 16], "south": [0, 0, 16, 16],
        }),
        el("ber_door_right", [8.0, 3.2, 2.3], [13.12, 28.5, 2.8], 3, {
            "north": [0, 0, 16, 16], "south": [0, 0, 16, 16],
        }),
    ]

    return {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": "operation_phoenix_pod",
        "model_identifier": "operation_phoenix_pod",
        "visible_box": [1, 2, 1],
        "resolution": {"width": 16, "height": 16},
        "elements": elements,
        "outliner": [
            {
                "name": "lower_half",
                "origin": [8, 0, 8],
                "color": 0,
                "uuid": str(uuid.uuid5(uuid.NAMESPACE_DNS, "phoenix_pod.group.lower_half")),
                "export": True,
                "isOpen": True,
                "locked": False,
                "visibility": True,
                "autouv": 0,
                "children": [e["uuid"] for e in elements[:9]],
            },
            {
                "name": "upper_half",
                "origin": [8, 16, 8],
                "color": 0,
                "uuid": str(uuid.uuid5(uuid.NAMESPACE_DNS, "phoenix_pod.group.upper_half")),
                "export": True,
                "isOpen": True,
                "locked": False,
                "visibility": True,
                "autouv": 0,
                "children": [e["uuid"] for e in elements[9:18]],
            },
            {
                "name": "ber_doors",
                "origin": [8, 0, 2.5],
                "color": 0,
                "uuid": str(uuid.uuid5(uuid.NAMESPACE_DNS, "phoenix_pod.group.ber_doors")),
                "export": True,
                "isOpen": True,
                "locked": False,
                "visibility": True,
                "autouv": 0,
                "children": [e["uuid"] for e in elements[18:]],
            },
        ],
        "textures": bb_textures,
    }


def main() -> None:
    paint_pod_base().save(TEX_DIR / "block/phoenix_pod_base.png")
    paint_pod_top().save(TEX_DIR / "block/phoenix_pod_top.png")
    paint_pod_glass(False).save(TEX_DIR / "block/phoenix_pod_glass.png")
    paint_pod_glass(True).save(TEX_DIR / "block/phoenix_pod_glass_liquid.png")
    paint_phoenix_gui().save(TEX_DIR / "gui/container/phoenix_pod_gui.png")

    bb_json = json.dumps(build_synced_bbmodel(), indent=2) + "\n"
    BB_ROOT.write_text(bb_json, encoding="utf-8")
    BB_ART.parent.mkdir(parents=True, exist_ok=True)
    BB_ART.write_text(bb_json, encoding="utf-8")
    print("Generated Phoenix Pod Arcane-Tech textures, GUI, and synced .bbmodel")


if __name__ == "__main__":
    main()
