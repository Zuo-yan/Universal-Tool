"""Generates 32x32 8-frame animated spritesheets (.png + .png.mcmeta) and item models
for the Netherite Universal Tool: Base Morph Core + Pickaxe, Axe, Shovel, Hoe, Sword forms.
"""
import json
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEX_DIR = ROOT / "src/main/resources/assets/universal_tool/textures/item"
MODEL_DIR = ROOT / "src/main/resources/assets/universal_tool/models/item"
PREVIEW_PATH = ROOT / "art/previews/netherite_universal_tool_morphs_preview.png"

SIZE = 32
FRAMES = 8

OUTLINE = (16, 12, 22, 255)
NETHER_DARK = (38, 33, 44, 255)
NETHER_MID = (64, 57, 74, 255)
NETHER_LIGHT = (96, 86, 108, 255)
NETHER_EDGE = (128, 118, 140, 255)

SHAFT_DARK = (42, 25, 22, 255)
SHAFT_MID = (70, 42, 34, 255)
SHAFT_LIGHT = (98, 62, 46, 255)

GOLD_DARK = (146, 86, 18, 255)
GOLD_MID = (214, 148, 36, 255)
GOLD_BRIGHT = (255, 220, 96, 255)


def write_png(path: Path, width: int, height: int, grid: list[list[tuple[int, int, int, int]]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(grid[y][x])

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b""))


def energy_color(phase: float, intensity: float = 1.0) -> tuple[int, int, int, int]:
    """Smooth magenta-amethyst-cyan-white arcane plasma palette."""
    t = 0.5 + 0.5 * math.sin(phase)
    t = max(0.0, min(1.0, t * intensity))
    r = int(130 + 125 * t)
    g = int(38 + 185 * (t ** 1.7))
    b = int(205 + 50 * t)
    return (min(255, r), min(255, g), min(255, b), 255)


def add_outline(canvas: list[list[tuple[int, int, int, int]]]) -> None:
    opaque = {(x, y) for y in range(SIZE) for x in range(SIZE) if canvas[y][x][3] > 0}
    for x, y in opaque:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < SIZE and 0 <= ny < SIZE and canvas[ny][nx][3] == 0:
                canvas[ny][nx] = OUTLINE


def set_px(canvas, x: int, y: int, col) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE:
        canvas[y][x] = col


def draw_line(canvas, x0: float, y0: float, x1: float, y1: float, width: float, color_fn) -> None:
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
    for i in range(steps + 1):
        u = i / max(1, steps)
        cx = x0 + (x1 - x0) * u
        cy = y0 + (y1 - y0) * u
        for py in range(int(cy - width - 1), int(cy + width + 2)):
            for px in range(int(cx - width - 1), int(cx + width + 2)):
                d = math.hypot(px - cx, py - cy)
                if d <= width and 0 <= px < SIZE and 0 <= py < SIZE:
                    col = color_fn(px, py, u, d)
                    if col is not None:
                        canvas[py][px] = col


def draw_disc(canvas, cx: float, cy: float, radius: float, color_fn) -> None:
    for py in range(int(cy - radius - 1), int(cy + radius + 2)):
        for px in range(int(cx - radius - 1), int(cx + radius + 2)):
            d = math.hypot(px - cx, py - cy)
            if d <= radius and 0 <= px < SIZE and 0 <= py < SIZE:
                col = color_fn(px, py, d)
                if col is not None:
                    canvas[py][px] = col


def draw_shaft(canvas, x0: float, y0: float, x1: float, y1: float, radius: float = 1.45) -> None:
    def shaft_col(px, py, u, d):
        band = (px + py) % 3
        if d > radius * 0.65:
            return SHAFT_DARK if (px - py) > 0 else SHAFT_MID
        return SHAFT_LIGHT if band == 0 else SHAFT_MID
    draw_line(canvas, x0, y0, x1, y1, radius, shaft_col)


def draw_gold_collar(canvas, cx: float, cy: float, radius: float = 2.0) -> None:
    def gold_col(px, py, d):
        if (px + py) < (cx + cy) - 0.3:
            return GOLD_BRIGHT
        return GOLD_MID if d < radius * 0.7 else GOLD_DARK
    draw_disc(canvas, cx, cy, radius, gold_col)


def draw_core_gem(canvas, cx: float, cy: float, r: float, pulse: float) -> None:
    draw_disc(canvas, cx, cy, r + 1.1, lambda px, py, d: NETHER_DARK if d > r else GOLD_MID)
    for py in range(int(cy - r - 1), int(cy + r + 2)):
        for px in range(int(cx - r - 1), int(cx + r + 2)):
            manh = abs(px - cx) + abs(py - cy)
            if manh <= r * 1.35 and 0 <= px < SIZE and 0 <= py < SIZE:
                norm = 1.0 - manh / (r * 1.45)
                set_px(canvas, px, py, energy_color(pulse + norm * 2.2, 0.55 + 0.55 * norm))


def frame_base(frame_idx: int) -> list[list[tuple[int, int, int, int]]]:
    """Base Form: Compact folded Netherite & Gold Arcane Morph-Core Instrument."""
    canvas = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]
    pulse = frame_idx * (2.0 * math.pi / FRAMES)

    # Compact diagonal hilt from bottom-left into central housing
    draw_shaft(canvas, 5.0, 26.5, 13.5, 18.0, 1.5)
    draw_gold_collar(canvas, 5.0, 26.5, 2.2)
    set_px(canvas, 4, 27, energy_color(pulse + 1.5, 0.9))

    # Central octagon Netherite housing ring
    draw_disc(canvas, 17.5, 14.5, 7.6, lambda px, py, d:
              NETHER_LIGHT if (px + py) < 30.5 and d > 5.0
              else (NETHER_MID if d > 4.2 else NETHER_DARK))

    # Gold mechanical latch plates at 4 cardinal points around the morph ring
    for ax, ay in ((17.5, 7.0), (17.5, 22.0), (10.0, 14.5), (25.0, 14.5)):
        draw_gold_collar(canvas, ax, ay, 1.9)

    # Four folded Netherite wing-prongs (suggesting pick/axe/shovel/sword petals folded inward)
    wing_specs = [
        ((12, 8), (6, 5), (14, 11)),     # upper-left pick claw
        ((23, 8), (27, 6), (20, 11)),    # upper-right blade tip
        ((24, 18), (27, 23), (21, 16)),  # lower-right axe arc
        ((10, 17), (8, 22), (13, 16)),   # lower-left hoe hook
    ]
    for p0, p1, p2 in wing_specs:
        draw_line(canvas, p0[0], p0[1], p1[0], p1[1], 1.7, lambda px, py, u, d:
                  NETHER_EDGE if d < 0.7 and u > 0.4 else (NETHER_LIGHT if (px + py) < 32 else NETHER_MID))
        draw_line(canvas, p0[0], p0[1], p2[0], p2[1], 1.3, lambda px, py, u, d: NETHER_DARK)

    # Diagonal mechanical grooves linking core to the 4 folded latches
    for dxy in (-4, -3, 3, 4):
        set_px(canvas, int(17.5 + dxy), int(14.5 + dxy), energy_color(pulse + 1.0, 0.7))
        set_px(canvas, int(17.5 + dxy), int(14.5 - dxy), energy_color(pulse + 2.2, 0.7))

    # Orbiting energy rune pips inside ring (4 nodes cycling)
    for k in range(4):
        ang = pulse * 0.5 + k * (math.pi / 2.0)
        ox = int(round(17.5 + math.cos(ang) * 5.1))
        oy = int(round(14.5 + math.sin(ang) * 5.1))
        set_px(canvas, ox, oy, energy_color(pulse + k * 0.9, 0.95))

    # Luminous diamond plasma heart at (17.5, 14.5)
    draw_core_gem(canvas, 17.5, 14.5, 3.3, pulse)
    add_outline(canvas)
    return canvas


def frame_pickaxe(frame_idx: int) -> list[list[tuple[int, int, int, int]]]:
    canvas = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]
    pulse = frame_idx * (2.0 * math.pi / FRAMES)

    draw_shaft(canvas, 4.5, 27.0, 21.5, 10.0, 1.45)
    draw_gold_collar(canvas, 4.5, 27.0, 2.0)
    draw_gold_collar(canvas, 21.5, 10.0, 2.5)

    # Sweeping heavy curved pickaxe arch centered around (21.5, 10.0)
    for i in range(49):
        t = -1.0 + 2.0 * (i / 48.0)  # -1..+1 along arch
        ax = 21.5 + t * 9.0 - (t * t) * 3.8
        ay = 10.0 + t * 9.0 - (1.0 - t * t) * 5.0 + t * 1.1
        thick = 2.75 - 1.75 * (t * t)
        def pick_col(px, py, u, d, tt=abs(t)):
            if tt > 0.72 and d < 1.15:
                return energy_color(pulse - tt * 2.8, 0.98)
            if d < 0.75 and tt > 0.25:
                return energy_color(pulse - tt * 3.0, 0.85)
            if px + py < 32:
                return NETHER_EDGE if tt > 0.65 else NETHER_LIGHT
            return NETHER_MID if tt < 0.7 else NETHER_DARK
        draw_line(canvas, ax, ay, ax, ay, thick, pick_col)

    draw_core_gem(canvas, 21.5, 10.0, 2.5, pulse)
    add_outline(canvas)
    return canvas


def frame_axe(frame_idx: int) -> list[list[tuple[int, int, int, int]]]:
    canvas = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]
    pulse = frame_idx * (2.0 * math.pi / FRAMES)

    draw_shaft(canvas, 4.5, 27.0, 22.5, 9.0, 1.5)
    draw_gold_collar(canvas, 4.5, 27.0, 2.0)

    # Sharp Netherite counterweight tail-spike (pointed bevel)
    draw_line(canvas, 19.5, 12.0, 14.5, 7.0, 1.5, lambda px, py, u, d:
              NETHER_EDGE if u > 0.65 else (NETHER_LIGHT if d < 0.9 else NETHER_DARK))

    # Sweeping heavyweight crescent battle-axe blade
    for py in range(3, 22):
        for px in range(15, 30):
            dx = px - 20.0
            dy = py - 12.0
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            if -1.55 <= ang <= 0.95 and 2.0 <= r <= 9.8:
                edge_dist = 9.8 - r
                if edge_dist < 1.35:
                    set_px(canvas, px, py, energy_color(pulse + ang * 2.0, 1.0))
                elif edge_dist < 2.6:
                    set_px(canvas, px, py, NETHER_EDGE)
                elif px + py < 33:
                    set_px(canvas, px, py, NETHER_LIGHT)
                else:
                    set_px(canvas, px, py, NETHER_MID)

    draw_gold_collar(canvas, 20.8, 10.7, 2.3)
    draw_core_gem(canvas, 20.8, 10.7, 2.4, pulse)
    add_outline(canvas)
    return canvas


def frame_shovel(frame_idx: int) -> list[list[tuple[int, int, int, int]]]:
    canvas = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]
    pulse = frame_idx * (2.0 * math.pi / FRAMES)

    draw_shaft(canvas, 4.5, 27.0, 18.0, 13.5, 1.45)
    # T-pommel at bottom-left
    draw_line(canvas, 3.0, 25.5, 6.0, 28.5, 1.3, lambda px, py, u, d: GOLD_MID)
    draw_gold_collar(canvas, 17.0, 14.5, 2.3)

    # Sculpted spade head toward (27, 4.5)
    for py in range(3, 17):
        for px in range(15, 29):
            # Project into rotated diagonal coordinate along (+1, -1)
            ux = (px - 17.5) * 0.7071 - (py - 14.0) * 0.7071
            vy = (px - 17.5) * 0.7071 + (py - 14.0) * 0.7071
            # ux: distance along blade toward tip (0..13), vy: half-width across blade
            if 0.0 <= ux <= 13.2:
                half_w = 4.8 * (1.0 - max(0.0, (ux - 5.5) / 7.8) ** 1.6)
                if abs(vy) <= half_w:
                    if abs(vy) <= 0.9 and ux < 9.5:
                        set_px(canvas, px, py, energy_color(pulse - ux * 0.45, 0.95))
                    elif ux > 11.2 or abs(abs(vy) - half_w) < 1.1:
                        set_px(canvas, px, py, NETHER_EDGE)
                    elif vy < 0:
                        set_px(canvas, px, py, NETHER_LIGHT)
                    else:
                        set_px(canvas, px, py, NETHER_MID)

    draw_core_gem(canvas, 17.5, 14.0, 2.2, pulse)
    add_outline(canvas)
    return canvas


def frame_hoe(frame_idx: int) -> list[list[tuple[int, int, int, int]]]:
    canvas = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]
    pulse = frame_idx * (2.0 * math.pi / FRAMES)

    draw_shaft(canvas, 4.5, 27.0, 22.0, 9.5, 1.45)
    draw_gold_collar(canvas, 4.5, 27.0, 2.0)
    draw_gold_collar(canvas, 22.0, 9.5, 2.3)

    # Sweeping scythe/hoe blade arching toward upper-left (10, 4) -> (8, 12)
    draw_line(canvas, 22.0, 9.5, 14.0, 5.5, 2.3, lambda px, py, u, d:
              NETHER_LIGHT if d < 1.4 else NETHER_MID)
    draw_line(canvas, 14.0, 5.5, 9.5, 10.5, 1.8, lambda px, py, u, d:
              energy_color(pulse - u * 2.5, 0.95) if (px + py) > 19 else NETHER_EDGE)
    draw_line(canvas, 9.5, 10.5, 11.0, 13.5, 1.1, lambda px, py, u, d:
              energy_color(pulse - 3.0, 1.0))

    draw_core_gem(canvas, 21.5, 10.0, 2.4, pulse)
    add_outline(canvas)
    return canvas


def frame_sword(frame_idx: int) -> list[list[tuple[int, int, int, int]]]:
    canvas = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]
    pulse = frame_idx * (2.0 * math.pi / FRAMES)

    # Hilt
    draw_shaft(canvas, 4.5, 27.0, 10.5, 21.0, 1.45)
    draw_gold_collar(canvas, 4.5, 27.0, 2.1)

    # Netherite + gold winged crossguard symmetrically centered on hilt top (10.75, 20.75)
    draw_line(canvas, 7.0, 17.0, 14.5, 24.5, 1.55, lambda px, py, u, d:
              GOLD_BRIGHT if (0.18 < u < 0.34 or 0.66 < u < 0.82) else NETHER_LIGHT)

    # Broad diagonal Netherite sword blade up to (27.5, 4.0) with pulsing center fuller
    for py in range(3, 23):
        for px in range(10, 29):
            ux = (px - 10.5) * 0.7071 - (py - 21.0) * 0.7071
            vy = (px - 10.5) * 0.7071 + (py - 21.0) * 0.7071
            if 0.0 <= ux <= 24.5:
                half_w = 2.7 if ux < 20.5 else 2.7 * max(0.0, (24.5 - ux) / 4.0)
                if abs(vy) <= half_w:
                    if abs(vy) < 0.9 and ux < 21.0:
                        set_px(canvas, px, py, energy_color(pulse - ux * 0.35, 1.0))
                    elif abs(abs(vy) - half_w) < 0.9:
                        set_px(canvas, px, py, NETHER_EDGE)
                    elif vy < 0:
                        set_px(canvas, px, py, NETHER_LIGHT)
                    else:
                        set_px(canvas, px, py, NETHER_MID)

    draw_core_gem(canvas, 10.8, 20.7, 2.3, pulse)
    add_outline(canvas)
    return canvas


FORMS = [
    ("netherite_universal_tool", frame_base, None),
    ("netherite_universal_tool_pickaxe", frame_pickaxe, 0.15),
    ("netherite_universal_tool_axe", frame_axe, 0.35),
    ("netherite_universal_tool_shovel", frame_shovel, 0.55),
    ("netherite_universal_tool_hoe", frame_hoe, 0.75),
    ("netherite_universal_tool_sword", frame_sword, 0.95),
]


def main() -> None:
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)

    mcmeta = {
        "animation": {
            "interpolate": True,
            "frametime": 3
        }
    }

    first_frames = []
    for name, fn, _threshold in FORMS:
        sheet = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE * FRAMES)]
        for f in range(FRAMES):
            frame = fn(f)
            if f == 0:
                first_frames.append(frame)
            for y in range(SIZE):
                sheet[f * SIZE + y] = frame[y]

        write_png(TEX_DIR / f"{name}.png", SIZE, SIZE * FRAMES, sheet)
        (TEX_DIR / f"{name}.png.mcmeta").write_text(json.dumps(mcmeta, indent=2) + "\n", encoding="utf-8")

    # Build item sub-models using handheld parent so first/third person tool grip looks natural
    for name, _fn, threshold in FORMS:
        if threshold is None:
            continue
        sub_model = {
            "parent": "minecraft:item/handheld",
            "textures": {
                "layer0": f"universal_tool:item/{name}"
            }
        }
        (MODEL_DIR / f"{name}.json").write_text(json.dumps(sub_model, indent=2) + "\n", encoding="utf-8")

    # Root netherite_universal_tool.json keeps handheld grip + clean predicate overrides
    root_model = {
        "parent": "minecraft:item/handheld",
        "textures": {
            "layer0": "universal_tool:item/netherite_universal_tool"
        },
        "overrides": [
            {
                "predicate": {"universal_tool:tool_form": threshold},
                "model": f"universal_tool:item/{name}"
            }
            for name, _fn, threshold in FORMS if threshold is not None
        ]
    }
    (MODEL_DIR / "netherite_universal_tool.json").write_text(json.dumps(root_model, indent=2) + "\n", encoding="utf-8")

    # Save a scaled 4x preview strip of all 6 forms (dark slate background) for visual inspection
    scale = 4
    pad = 8
    cols = len(first_frames)
    pw = cols * (SIZE * scale + pad) + pad
    ph = SIZE * scale + pad * 2
    preview = [[(26, 24, 34, 255) for _ in range(pw)] for _ in range(ph)]
    for idx, frame in enumerate(first_frames):
        ox = pad + idx * (SIZE * scale + pad)
        oy = pad
        for y in range(SIZE):
            for x in range(SIZE):
                col = frame[y][x]
                if col[3] > 0:
                    for sy in range(scale):
                        for sx in range(scale):
                            preview[oy + y * scale + sy][ox + x * scale + sx] = col
    write_png(PREVIEW_PATH, pw, ph, preview)
    print(f"Generated {len(FORMS)} animated 32x32 Netherite tool forms + preview at {PREVIEW_PATH}")


if __name__ == "__main__":
    main()
