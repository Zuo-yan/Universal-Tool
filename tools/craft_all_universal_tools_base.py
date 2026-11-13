"""Builds all 6 Universal Tool Base Core textures (32x32 8-frame animated sprites inspired by
the Mace/Heavy-Core mechanical morph handle) AND all 30 vanilla-silhouette + shimmer tool forms.
"""
import json
import math
import struct
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VANILLA_JAR = Path(r"D:\Caches\gradle-home\caches\neoformruntime\artifacts\minecraft_1.21.1_client.jar")
TEX_DIR = ROOT / "src/main/resources/assets/universal_tool/textures/item"
MODEL_DIR = ROOT / "src/main/resources/assets/universal_tool/models/item"
BASE_PREVIEW_PATH = ROOT / "art/previews/all_universal_tools_base_preview.png"

OUTLINE = (16, 14, 20, 255)

BASE_PALETTES = {
    "wooden": {
        "dark": (78, 56, 26),
        "mid": (122, 88, 42),
        "light": (164, 122, 64),
        "edge": (196, 152, 88),
        "core_lo": (34, 195, 98),
        "core_hi": (155, 255, 190),
    },
    "stone": {
        "dark": (66, 68, 74),
        "mid": (108, 110, 118),
        "light": (150, 154, 162),
        "edge": (184, 188, 196),
        "core_lo": (34, 178, 235),
        "core_hi": (160, 245, 255),
    },
    "iron": {
        "dark": (56, 62, 72),
        "mid": (118, 126, 138),
        "light": (188, 196, 208),
        "edge": (236, 242, 250),
        "core_lo": (24, 210, 235),
        "core_hi": (175, 255, 255),
    },
    "golden": {
        "dark": (126, 76, 14),
        "mid": (196, 136, 24),
        "light": (246, 196, 56),
        "edge": (255, 240, 140),
        "core_lo": (220, 42, 74),
        "core_hi": (255, 150, 175),
    },
    "diamond": {
        "dark": (20, 92, 94),
        "mid": (38, 168, 164),
        "light": (88, 232, 224),
        "edge": (190, 255, 250),
        "core_lo": (80, 235, 255),
        "core_hi": (235, 255, 255),
    },
    "netherite": {
        "dark": (34, 29, 38),
        "mid": (62, 54, 68),
        "light": (98, 86, 108),
        "edge": (222, 160, 46),  # molten gold trim on Netherite brackets
        "core_lo": (168, 48, 245),
        "core_hi": (252, 188, 255),
    },
}

TIERS_ORDER = ["wooden", "stone", "iron", "golden", "diamond", "netherite"]
OVERRIDES_SPEC = [
    ("pickaxe", 0.15),
    ("axe", 0.35),
    ("shovel", 0.55),
    ("hoe", 0.75),
    ("sword", 0.95),
]


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


def lerp_rgb(c0: tuple[int, int, int], c1: tuple[int, int, int], t: float) -> tuple[int, int, int, int]:
    t = max(0.0, min(1.0, t))
    return (
        int(round(c0[0] + (c1[0] - c0[0]) * t)),
        int(round(c0[1] + (c1[1] - c0[1]) * t)),
        int(round(c0[2] + (c1[2] - c0[2]) * t)),
        255,
    )


def render_base_core_frame(pal: dict, frame_idx: int, frames: int = 8) -> list[list[tuple[int, int, int, int]]]:
    """32x32 diagonal Heavy-Core / Mace-inspired Mechanical Universal Tool Base."""
    size = 32
    canvas = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]
    pulse = 0.5 + 0.5 * math.sin(frame_idx * (2.0 * math.pi / frames))
    sweep = -6.0 + (frame_idx / frames) * 44.0

    dark = pal["dark"] + (255,)
    mid = pal["mid"] + (255,)
    light = pal["light"] + (255,)
    edge = pal["edge"] + (255,)
    core_mid = lerp_rgb(pal["core_lo"], pal["core_hi"], 0.45 + 0.45 * pulse)
    core_bright = lerp_rgb(pal["core_lo"], pal["core_hi"], 0.8 + 0.2 * pulse)

    shaft_dark = (68, 36, 18, 255)
    shaft_mid = (108, 58, 26, 255)
    shaft_hi = (144, 80, 38, 255)

    def put(px: int, py: int, col: tuple[int, int, int, int]) -> None:
        if 0 <= px < size and 0 <= py < size:
            canvas[py][px] = col

    # 1. Diagonal wrapped wooden grip (5, 26) -> (14, 17)
    for step in range(18):
        u = step / 17.0
        cx = 5.5 + u * 9.0
        cy = 26.5 - u * 9.0
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if abs(dx) + abs(dy) <= 2:
                    px, py = int(round(cx + dx)), int(round(cy + dy))
                    band = (px - py) % 3
                    if dx - dy > 1:
                        put(px, py, shaft_dark)
                    elif band == 0:
                        put(px, py, shaft_hi)
                    else:
                        put(px, py, shaft_mid)

    # 2. Heavy metallic pommel at bottom-left (4, 27) with glowing core pixel
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            d = math.hypot(dx, dy)
            if d <= 2.9:
                px, py = 4 + dx, 27 + dy
                if d < 1.1:
                    put(px, py, core_mid)
                elif dx - dy < -0.5:
                    put(px, py, light)
                else:
                    put(px, py, dark)

    # 3. Neck collar (14, 17) linking handle to Heavy Core head
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            if abs(dx + dy) <= 2 and abs(dx - dy) <= 3:
                px, py = 14 + dx, 17 + dy
                put(px, py, edge if (dx - dy < 0) else mid)

    # 4. Chunky square Heavy-Core Head centered at integer (20, 12)
    hx, hy = 20, 12
    for py in range(4, 21):
        for px in range(12, 29):
            dx = px - hx
            dy = py - hy
            cheb = max(abs(dx), abs(dy))
            manh = abs(dx) + abs(dy)
            if cheb <= 7 and manh <= 12:
                if cheb >= 6 or manh >= 11:
                    put(px, py, light if (dx + dy < 0) else dark)
                else:
                    put(px, py, dark if (abs(dx) <= 4 and abs(dy) <= 4) else mid)

    # 5. Four bold raised corner armor brackets (matches Heavy-Core mace latches)
    for bx, by in ((16, 8), (24, 8), (16, 16), (24, 16)):
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                put(bx + dx, by + dy, edge if (dx + dy <= 0) else light)

    # 6. Side glowing energy vents (2px bars on left/right/top)
    for vx, vy in ((13, 12), (27, 12), (20, 5)):
        put(vx, vy, core_mid)

    # 7. Solid glowing diamond energy crystal at integer (20, 12)
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            manh = abs(dx) + abs(dy)
            if manh <= 3:
                put(hx + dx, hy + dy, core_bright if manh <= 1 else core_mid)

    # 8. Diagonal shimmer glint across the metallic head
    for py in range(size):
        for px in range(size):
            r, g, b, a = canvas[py][px]
            if a == 0 or (px < 12 and py > 18):
                continue
            dist = abs((px + (size - 1 - py)) - sweep)
            if dist < 3.2:
                glint = (1.0 - dist / 3.2) * 0.28
                nr = min(255, int(round(r + (pal["core_hi"][0] - r * 0.3) * glint + 30 * glint)))
                ng = min(255, int(round(g + (pal["core_hi"][1] - g * 0.3) * glint + 30 * glint)))
                nb = min(255, int(round(b + (pal["core_hi"][2] - b * 0.3) * glint + 30 * glint)))
                canvas[py][px] = (nr, ng, nb, a)

    # 9. Crisp 1px dark outline
    opaque = {(x, y) for y in range(size) for x in range(size) if canvas[y][x][3] > 0}
    for x, y in opaque:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < size and 0 <= ny < size and canvas[ny][nx][3] == 0:
                canvas[ny][nx] = OUTLINE

    return canvas


def main() -> None:
    mcmeta = {"animation": {"interpolate": True, "frametime": 2}}
    base_preview_frames = []

    for tier in TIERS_ORDER:
        pal = BASE_PALETTES[tier]
        base_name = f"{tier}_universal_tool"

        # Generate 32x32 8-frame animated Heavy-Core Base Form
        sheet = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32 * 8)]
        for f in range(8):
            frame = render_base_core_frame(pal, f, 8)
            if f == 2:
                base_preview_frames.append(frame)
            for y in range(32):
                sheet[f * 32 + y] = frame[y]

        write_png(TEX_DIR / f"{base_name}.png", 32, 32 * 8, sheet)
        (TEX_DIR / f"{base_name}.png.mcmeta").write_text(json.dumps(mcmeta, indent=2) + "\n", encoding="utf-8")

        # Root model uses handheld grip + overrides to the 5 vanilla morph forms
        root_model = {
            "parent": "minecraft:item/handheld",
            "textures": {
                "layer0": f"universal_tool:item/{base_name}"
            },
            "overrides": [
                {
                    "predicate": {"universal_tool:tool_form": threshold},
                    "model": f"universal_tool:item/{base_name}_{form_name}"
                }
                for form_name, threshold in OVERRIDES_SPEC
            ]
        }
        (MODEL_DIR / f"{base_name}.json").write_text(json.dumps(root_model, indent=2) + "\n", encoding="utf-8")

    # 4x scaled preview of all 6 Base Cores
    scale = 4
    pad = 8
    pw = len(base_preview_frames) * (32 * scale + pad) + pad
    ph = 32 * scale + pad * 2
    preview = [[(24, 22, 30, 255) for _ in range(pw)] for _ in range(ph)]
    for idx, frame in enumerate(base_preview_frames):
        ox = pad + idx * (32 * scale + pad)
        oy = pad
        for y in range(32):
            for x in range(32):
                col = frame[y][x]
                if col[3] > 0:
                    for sy in range(scale):
                        for sx in range(scale):
                            preview[oy + y * scale + sy][ox + x * scale + sx] = col
    write_png(BASE_PREVIEW_PATH, pw, ph, preview)
    print(f"Generated all 6 animated Heavy-Core Base Universal Tool textures -> {BASE_PREVIEW_PATH}")


if __name__ == "__main__":
    main()
