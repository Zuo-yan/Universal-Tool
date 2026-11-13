"""Generates the pointed-oval stone/moss/cyan-rune Stargate textures and 2-block-tall voxel models."""
import json
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEX_DIR = ROOT / "src/main/resources/assets/universal_tool/textures/block"
MODEL_DIR = ROOT / "src/main/resources/assets/universal_tool/models/block"
ITEM_MODEL_DIR = ROOT / "src/main/resources/assets/universal_tool/models/item"


def write_png(path: Path, width: int, height: int, rgba_grid: list[list[tuple[int, int, int, int]]]) -> None:
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(rgba_grid[y][x])

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b""))


def make_arch_texture() -> None:
    """64x64 4-tile atlas:
    (0,0)-(32,32): carved weathered stone brick
    (32,0)-(64,32): mossy carved stone brick
    (0,32)-(32,64): stone with glowing cyan rune vein
    (32,32)-(64,64): carved node housing with glowing cyan cross emblem
    """
    px = [[(0, 0, 0, 255) for _ in range(64)] for _ in range(64)]

    def fill_stone_tile(ox: int, oy: int, mossy: bool) -> None:
        for y in range(32):
            for x in range(32):
                row = y // 8
                bx = (x + (8 if row % 2 else 0)) % 16
                by = y % 8
                is_mortar = (by == 0) or (bx == 0)
                n = ((x * 37 + y * 91 + ox * 13 + oy * 7) % 19) - 9
                base = 112 if not is_mortar else 78
                if by == 1 or bx == 1:
                    base += 10
                r = max(45, min(175, base + n))
                g = max(48, min(180, base + 2 + n))
                b = max(55, min(190, base + 8 + n))
                if mossy:
                    moss_noise = ((x * 53 + y * 29) % 31)
                    if (y < 10 and moss_noise < 18) or (x < 9 and y > 14 and moss_noise < 16):
                        r = max(40, min(120, 74 + n))
                        g = max(85, min(165, 128 + n))
                        b = max(28, min(70, 44 + (n // 2)))
                px[oy + y][ox + x] = (r, g, b, 255)

    fill_stone_tile(0, 0, mossy=False)
    fill_stone_tile(32, 0, mossy=True)
    fill_stone_tile(0, 32, mossy=False)
    fill_stone_tile(32, 32, mossy=False)

    for y in range(32):
        for x in range(12, 20):
            dist = abs(x - 15.5)
            if dist < 1.3:
                px[32 + y][x] = (180, 255, 255, 255)
            elif dist < 2.6:
                px[32 + y][x] = (45, 225, 255, 255)
            elif dist < 3.6:
                px[32 + y][x] = (28, 120, 155, 255)

    for y in range(32):
        for x in range(32):
            if x in (2, 29) or y in (2, 29):
                px[32 + y][32 + x] = (68, 72, 82, 255)
            elif x in (3, 28) or y in (3, 28):
                px[32 + y][32 + x] = (142, 148, 162, 255)
    for dy in range(-7, 8):
        for dx in range(-7, 8):
            in_cross = (abs(dx) <= 2 and abs(dy) <= 6) or (abs(dy) <= 2 and abs(dx) <= 6)
            if in_cross:
                core = (abs(dx) <= 1 and abs(dy) <= 4) or (abs(dy) <= 1 and abs(dx) <= 4)
                px[48 + dy][48 + dx] = (185, 255, 255, 255) if core else (35, 225, 255, 255)

    write_png(TEX_DIR / "stargate_arch.png", 64, 64, px)


def make_portal_vortex_texture() -> None:
    """64x64 vibrant electric-cyan & azure pixel-art spiral vortex with soft oval alpha edge."""
    px = [[(0, 0, 0, 0) for _ in range(64)] for _ in range(64)]
    cx, cy = 31.5, 31.5
    for y in range(64):
        for x in range(64):
            dx = (x - cx) / 30.0
            dy = (y - cy) / 30.0
            r = math.hypot(dx, dy)
            if r > 1.0:
                continue
            angle = math.atan2(dy, dx)
            spiral = math.sin(angle * 3.0 - r * 11.5)
            ring = math.sin(r * 14.0) * 0.18
            val = max(0.0, min(1.0, 0.52 + 0.42 * spiral + ring + (1.0 - r) * 0.25))

            if val > 0.84:
                cr, cg, cb = 190, 255, 255
            elif val > 0.62:
                cr, cg, cb = 45, 230, 255
            elif val > 0.36:
                cr, cg, cb = 12, 145, 255
            else:
                cr, cg, cb = 8, 65, 195

            edge_alpha = 1.0 if r < 0.82 else max(0.0, (1.0 - r) / 0.18)
            alpha = int(round(235 * edge_alpha))
            px[y][x] = (cr, cg, cb, alpha)

    write_png(TEX_DIR / "stargate_portal_vortex.png", 64, 64, px)


def uv_rect(tile_x: int, tile_y: int) -> list[float]:
    # 64x64 texture -> Minecraft UV is 0..16
    u0 = tile_x * 8.0
    v0 = tile_y * 8.0
    return [u0, v0, u0 + 8.0, v0 + 8.0]


def make_box(name: str, bounds: list[float], tile_x: int, tile_y: int) -> dict:
    uv = uv_rect(tile_x, tile_y)
    return {
        "name": name,
        "from": bounds[:3],
        "to": bounds[3:],
        "faces": {
            d: {"uv": uv, "texture": "#0"}
            for d in ("north", "south", "east", "west", "up", "down")
        }
    }


def build_models() -> None:
    # Lower block (y: 0..16): stepped stone plinth + lower curved arch + bottom node + lower cyan rune strips
    lower_elements = [
        # Stepped plinth base
        make_box("plinth_bottom", [-4, 0, 2, 20, 2, 14], 0, 0),
        make_box("plinth_moss_l", [-3, 0, 3, 2, 3, 13], 1, 0),
        make_box("plinth_mid", [-1, 2, 3, 17, 4, 13], 0, 0),
        # Bottom center cyan rune node
        make_box("node_bottom", [5, 2, 3.5, 11, 7, 12.5], 1, 1),
        # Lower left/right curved stone arch steps
        make_box("arch_l1", [-2, 4, 5, 3, 8, 11], 0, 0),
        make_box("arch_r1", [13, 4, 5, 18, 8, 11], 0, 0),
        make_box("arch_l2", [-4, 8, 5, 1, 14, 11], 1, 0),
        make_box("arch_r2", [15, 8, 5, 20, 14, 11], 0, 0),
        make_box("arch_l3", [-5, 13, 5, 0, 16, 11], 0, 0),
        make_box("arch_r3", [16, 13, 5, 21, 16, 11], 1, 0),
        # Inner cyan energy rune trims along lower curve
        make_box("rune_l1", [2.2, 4.5, 6.5, 3.4, 8, 9.5], 0, 1),
        make_box("rune_r1", [12.6, 4.5, 6.5, 13.8, 8, 9.5], 0, 1),
        make_box("rune_l2", [0.2, 8, 6.5, 1.4, 14, 9.5], 0, 1),
        make_box("rune_r2", [14.6, 8, 6.5, 15.8, 14, 9.5], 0, 1),
    ]

    # Upper block (y: 0..24 relative to pos.above(), i.e. world y = 1.0 .. 2.5):
    # Mid side nodes + upper pointed-oval curve + top keystone node + inner cyan runes
    upper_elements = [
        # Side pillar continuation + left/right cyan cross-nodes (world y = 1.0..1.5)
        make_box("upper_l1", [-5, 0, 5, 0, 8, 11], 0, 0),
        make_box("upper_r1", [16, 0, 5, 21, 8, 11], 0, 0),
        make_box("node_left", [-6, 1.5, 4, -0.5, 7, 12], 1, 1),
        make_box("node_right", [16.5, 1.5, 4, 22, 7, 12], 1, 1),
        # Upper tapering oval arch
        make_box("upper_l2", [-4, 8, 5, 1, 14, 11], 1, 0),
        make_box("upper_r2", [15, 8, 5, 20, 14, 11], 0, 0),
        make_box("upper_l3", [-2, 14, 5, 3, 18, 11], 0, 0),
        make_box("upper_r3", [13, 14, 5, 18, 18, 11], 1, 0),
        make_box("upper_l4", [1, 18, 5, 6, 21, 11], 0, 0),
        make_box("upper_r4", [10, 18, 5, 15, 21, 11], 0, 0),
        make_box("arch_crown", [5, 20, 5, 11, 23.5, 11], 0, 0),
        # Top center cyan rune node
        make_box("node_top", [5, 19.5, 4, 11, 24.5, 12], 1, 1),
        # Inner cyan energy rune trims along upper curve
        make_box("rune_u_l1", [-0.6, 0, 6.5, 0.6, 8, 9.5], 0, 1),
        make_box("rune_u_r1", [15.4, 0, 6.5, 16.6, 8, 9.5], 0, 1),
        make_box("rune_u_l2", [0.5, 8, 6.5, 1.7, 14, 9.5], 0, 1),
        make_box("rune_u_r2", [14.3, 8, 6.5, 15.5, 14, 9.5], 0, 1),
        make_box("rune_u_l3", [2.5, 14, 6.5, 3.7, 18, 9.5], 0, 1),
        make_box("rune_u_r3", [12.3, 14, 6.5, 13.5, 18, 9.5], 0, 1),
    ]

    for suffix, elements in (("lower", lower_elements), ("upper", upper_elements)):
        model = {
            "format_version": "1.21.1",
            "render_type": "minecraft:cutout",
            "textures": {
                "0": "universal_tool:block/stargate_arch",
                "particle": "universal_tool:block/stargate_arch"
            },
            "elements": elements,
            "display": {
                "gui": {
                    "rotation": [24, 135, 0],
                    "translation": [0, -3.5, 0],
                    "scale": [0.42, 0.42, 0.42]
                },
                "ground": {
                    "translation": [0, 2, 0],
                    "scale": [0.25, 0.25, 0.25]
                },
                "fixed": {
                    "translation": [0, -2, 0],
                    "scale": [0.45, 0.45, 0.45]
                }
            }
        }
        (MODEL_DIR / f"personal_space_gate_{suffix}.json").write_text(
            json.dumps(model, indent=2) + "\n", encoding="utf-8"
        )

    # Item model combines full silhouette via lower model scaled nicely (or keep item model pointing to lower)
    item_model = {
        "parent": "universal_tool:block/personal_space_gate_lower"
    }
    (ITEM_MODEL_DIR / "personal_space_gate.json").write_text(
        json.dumps(item_model, indent=2) + "\n", encoding="utf-8"
    )


if __name__ == "__main__":
    make_arch_texture()
    make_portal_vortex_texture()
    build_models()
    print("Generated stargate textures and 2-block-tall arch models.")
