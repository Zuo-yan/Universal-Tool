"""Builds all 30 animated morph textures (6 tiers x 5 tool forms) directly from
Minecraft 1.21.1 vanilla tool textures with a diagonal energy shimmer & core accent.
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
PREVIEW_PATH = ROOT / "art/previews/all_universal_tools_preview.png"

SIZE = 16
FRAMES = 8

TIERS = {
    "wooden":    (70, 235, 130),
    "stone":     (75, 205, 255),
    "iron":      (90, 245, 210),
    "golden":    (255, 105, 130),
    "diamond":   (150, 255, 255),
    "netherite": (215, 95, 255),
}

OVERRIDES_SPEC = [
    ("pickaxe", 0.15),
    ("axe", 0.35),
    ("shovel", 0.55),
    ("hoe", 0.75),
    ("sword", 0.95),
]

# Collar/crossguard accent pixel per tool form (16x16 coords)
CORE_ACCENTS = {
    "pickaxe": [(10, 4), (11, 5)],
    "axe":     [(10, 4), (11, 5)],
    "shovel":  [(9, 6)],
    "hoe":     [(10, 4)],
    "sword":   [(4, 11), (5, 10)],
}


def paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def read_png_rgba(raw_bytes: bytes) -> list[list[tuple[int, int, int, int]]]:
    assert raw_bytes[:8] == b"\x89PNG\r\n\x1a\n"
    pos = 8
    width = height = bit_depth = color_type = 0
    palette: list[tuple[int, int, int]] = []
    trns: bytes = b""
    idat = bytearray()

    while pos < len(raw_bytes):
        length = struct.unpack(">I", raw_bytes[pos:pos + 4])[0]
        tag = raw_bytes[pos + 4:pos + 8]
        data = raw_bytes[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, bit_depth, color_type, _, _, _ = struct.unpack(">IIBBBBB", data)
        elif tag == b"PLTE":
            palette = [(data[i], data[i + 1], data[i + 2]) for i in range(0, len(data), 3)]
        elif tag == b"tRNS":
            trns = data
        elif tag == b"IDAT":
            idat.extend(data)
        elif tag == b"IEND":
            break

    assert bit_depth in (1, 2, 4, 8), f"Unsupported bit depth {bit_depth}"
    decompressed = zlib.decompress(bytes(idat))
    if bit_depth < 8:
        assert color_type == 3, f"Sub-byte bit_depth {bit_depth} only supported for indexed color"
        stride = (width * bit_depth + 7) // 8
        recon = bytearray(height * stride)
        in_idx = 0
        for y in range(height):
            flt = decompressed[in_idx]
            in_idx += 1
            row_start = y * stride
            for x in range(stride):
                val = decompressed[in_idx]
                in_idx += 1
                a = recon[row_start + x - 1] if x >= 1 else 0
                b = recon[row_start - stride + x] if y > 0 else 0
                c = recon[row_start - stride + x - 1] if (y > 0 and x >= 1) else 0
                if flt == 0:
                    recon[row_start + x] = val
                elif flt == 1:
                    recon[row_start + x] = (val + a) & 0xFF
                elif flt == 2:
                    recon[row_start + x] = (val + b) & 0xFF
                elif flt == 3:
                    recon[row_start + x] = (val + ((a + b) >> 1)) & 0xFF
                elif flt == 4:
                    recon[row_start + x] = (val + paeth(a, b, c)) & 0xFF
        grid = [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]
        ppb = 8 // bit_depth
        mask = (1 << bit_depth) - 1
        for y in range(height):
            row_start = y * stride
            for x in range(width):
                byte_val = recon[row_start + x // ppb]
                shift = 8 - bit_depth * (1 + (x % ppb))
                pidx = (byte_val >> shift) & mask
                r, g, b = palette[pidx]
                a = trns[pidx] if pidx < len(trns) else 255
                grid[y][x] = (r, g, b, a)
        return grid

    bpp = {2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    stride = width * bpp
    recon = bytearray(height * stride)

    in_idx = 0
    for y in range(height):
        flt = decompressed[in_idx]
        in_idx += 1
        row_start = y * stride
        for x in range(stride):
            val = decompressed[in_idx]
            in_idx += 1
            a = recon[row_start + x - bpp] if x >= bpp else 0
            b = recon[row_start - stride + x] if y > 0 else 0
            c = recon[row_start - stride + x - bpp] if (y > 0 and x >= bpp) else 0
            if flt == 0:
                recon[row_start + x] = val
            elif flt == 1:
                recon[row_start + x] = (val + a) & 0xFF
            elif flt == 2:
                recon[row_start + x] = (val + b) & 0xFF
            elif flt == 3:
                recon[row_start + x] = (val + ((a + b) >> 1)) & 0xFF
            elif flt == 4:
                recon[row_start + x] = (val + paeth(a, b, c)) & 0xFF

    grid = [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]
    for y in range(height):
        for x in range(width):
            idx = y * stride + x * bpp
            if color_type == 6:
                grid[y][x] = (recon[idx], recon[idx + 1], recon[idx + 2], recon[idx + 3])
            elif color_type == 2:
                grid[y][x] = (recon[idx], recon[idx + 1], recon[idx + 2], 255)
            elif color_type == 3:
                pidx = recon[idx]
                r, g, b = palette[pidx]
                a = trns[pidx] if pidx < len(trns) else 255
                grid[y][x] = (r, g, b, a)
            elif color_type == 4:
                v = recon[idx]
                grid[y][x] = (v, v, v, recon[idx + 1])
    return grid


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


def apply_glint_frame(base: list[list[tuple[int, int, int, int]]], accent_rgb: tuple[int, int, int],
                      accents: list[tuple[int, int]], frame_idx: int) -> list[list[tuple[int, int, int, int]]]:
    out = [row[:] for row in base]
    # Sweeps diagonally across the tool from bottom-left to top-right
    sweep_pos = -6.0 + (frame_idx / FRAMES) * 28.0
    pulse = 0.45 + 0.55 * (0.5 + 0.5 * math.sin(frame_idx * (2.0 * math.pi / FRAMES)))

    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = base[y][x]
            if a == 0:
                continue
            # Skip dark outline pixels & wooden stick in lower-left so shimmer highlights blade/head cleanly
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            is_handle = (x < 9 and y > 6 and abs((15 - x) - y) <= 2 and (r > b * 1.15))
            if lum < 38 or is_handle:
                continue
            diag = x + (SIZE - 1 - y)
            dist = abs(diag - sweep_pos)
            if dist < 2.8:
                glint = (1.0 - dist / 2.8) * 0.36
                nr = min(255, int(round(r + (accent_rgb[0] - r * 0.4) * glint + 48 * glint)))
                ng = min(255, int(round(g + (accent_rgb[1] - g * 0.4) * glint + 48 * glint)))
                nb = min(255, int(round(b + (accent_rgb[2] - b * 0.4) * glint + 48 * glint)))
                out[y][x] = (nr, ng, nb, a)

    # Subtle pulsing energy gem accent at the collar
    for ax, ay in accents:
        if 0 <= ax < SIZE and 0 <= ay < SIZE and base[ay][ax][3] > 0:
            br, bg, bb, ba = out[ay][ax]
            mix = 0.45 + 0.45 * pulse
            nr = min(255, int(round(br * (1 - mix) + accent_rgb[0] * mix + 35 * pulse)))
            ng = min(255, int(round(bg * (1 - mix) + accent_rgb[1] * mix + 35 * pulse)))
            nb = min(255, int(round(bb * (1 - mix) + accent_rgb[2] * mix + 35 * pulse)))
            out[ay][ax] = (nr, ng, nb, ba)

    return out


def main() -> None:
    assert VANILLA_JAR.exists(), f"Missing vanilla jar: {VANILLA_JAR}"
    mcmeta = {"animation": {"interpolate": True, "frametime": 2}}
    preview_rows: list[list[list[tuple[int, int, int, int]]]] = []

    with zipfile.ZipFile(VANILLA_JAR, "r") as zf:
        for tier, accent_rgb in TIERS.items():
            base_name = f"{tier}_universal_tool"
            tier_frames = []

            for form_name, _threshold in OVERRIDES_SPEC:
                vanilla_path = f"assets/minecraft/textures/item/{tier}_{form_name}.png"
                vanilla_grid = read_png_rgba(zf.read(vanilla_path))
                tex_name = f"{base_name}_{form_name}"

                sheet = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE * FRAMES)]
                accents = CORE_ACCENTS[form_name]
                for f in range(FRAMES):
                    frame = apply_glint_frame(vanilla_grid, accent_rgb, accents, f)
                    if f == 2:
                        tier_frames.append(frame)  # show mid-glint frame in preview
                    for y in range(SIZE):
                        sheet[f * SIZE + y] = frame[y]

                write_png(TEX_DIR / f"{tex_name}.png", SIZE, SIZE * FRAMES, sheet)
                (TEX_DIR / f"{tex_name}.png.mcmeta").write_text(json.dumps(mcmeta, indent=2) + "\n", encoding="utf-8")

                sub_model = {
                    "parent": "minecraft:item/handheld",
                    "textures": {"layer0": f"universal_tool:item/{tex_name}"}
                }
                (MODEL_DIR / f"{tex_name}.json").write_text(json.dumps(sub_model, indent=2) + "\n", encoding="utf-8")

            preview_rows.append(tier_frames)

            root_path = MODEL_DIR / f"{base_name}.json"
            root_model = json.loads(root_path.read_text(encoding="utf-8"))
            root_model["overrides"] = [
                {
                    "predicate": {"universal_tool:tool_form": threshold},
                    "model": f"universal_tool:item/{base_name}_{form_name}"
                }
                for form_name, threshold in OVERRIDES_SPEC
            ]
            root_path.write_text(json.dumps(root_model, indent=2) + "\n", encoding="utf-8")

    # Save 4x preview of all 30 vanilla-silhouette + glint sprites
    scale = 4
    pad = 6
    cols = len(OVERRIDES_SPEC)
    rows = len(TIERS)
    pw = cols * (SIZE * scale + pad) + pad
    ph = rows * (SIZE * scale + pad) + pad
    preview = [[(24, 22, 30, 255) for _ in range(pw)] for _ in range(ph)]
    for r_idx, row_frames in enumerate(preview_rows):
        for c_idx, frame in enumerate(row_frames):
            ox = pad + c_idx * (SIZE * scale + pad)
            oy = pad + r_idx * (SIZE * scale + pad)
            for y in range(SIZE):
                for x in range(SIZE):
                    col = frame[y][x]
                    if col[3] > 0:
                        for sy in range(scale):
                            for sx in range(scale):
                                preview[oy + y * scale + sy][ox + x * scale + sx] = col
    write_png(PREVIEW_PATH, pw, ph, preview)
    print(f"Built 30 vanilla-based animated textures from {VANILLA_JAR.name} -> {PREVIEW_PATH}")


if __name__ == "__main__":
    main()
