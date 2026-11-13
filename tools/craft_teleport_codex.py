"""Builds a 32x32 8-frame animated mod-style Astral Tome Teleport Codex texture (.png + .png.mcmeta)
matching the mod's 32x32 beveled obsidian/gold/cyan-core art style while preserving the classic
Minecraft diagonal 3D bound-book silhouette and cream page block.
"""
import json
import math
import struct
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VANILLA_JAR = Path(r"D:\Caches\gradle-home\caches\neoformruntime\artifacts\minecraft_1.21.1_client.jar")
TEX_PATH = ROOT / "src/main/resources/assets/universal_tool/textures/item/teleport_codex.png"
MCMETA_PATH = ROOT / "src/main/resources/assets/universal_tool/textures/item/teleport_codex.png.mcmeta"
PREVIEW_PATH = ROOT / "art/previews/teleport_codex_preview.png"

SIZE = 32
FRAMES = 8


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

    decompressed = zlib.decompress(bytes(idat))
    if bit_depth < 8:
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


def _classify_vanilla_pixel(r: int, g: int, b: int, a: int) -> str:
    if a == 0:
        return "empty"
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    if lum < 45:
        return "outline"
    if r > 175 and g > 155 and b > 115:
        return "pages"
    return "cover"


def _upscale_mask_32(vanilla_book: list[list[tuple[int, int, int, int]]]) -> tuple[list[list[bool]], list[list[str]]]:
    """Upscale vanilla 16x16 book silhouette to 32x32 and smooth staircase corners."""
    solid = [[False] * SIZE for _ in range(SIZE)]
    kind = [["empty"] * SIZE for _ in range(SIZE)]

    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = vanilla_book[y // 2][x // 2]
            k = _classify_vanilla_pixel(r, g, b, a)
            if k != "empty":
                solid[y][x] = True
                kind[y][x] = k

    # Smooth staircase corners by trimming isolated outer 1px corners and filling 3-neighbor concave pockets
    smoothed = [row[:] for row in solid]
    for y in range(1, SIZE - 1):
        for x in range(1, SIZE - 1):
            n4 = sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if solid[y + dy][x + dx])
            if solid[y][x] and n4 <= 1:
                smoothed[y][x] = False
            elif not solid[y][x] and n4 >= 3:
                smoothed[y][x] = True
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if solid[y + dy][x + dx]:
                        kind[y][x] = kind[y + dy][x + dx]
                        break

    # Mark 1px outer border of smoothed silhouette as outline, and interior outline pixels near cover as cover
    final_kind = [["empty"] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if not smoothed[y][x]:
                continue
            on_edge = (
                x == 0 or y == 0 or x == SIZE - 1 or y == SIZE - 1
                or any(not smoothed[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            )
            if on_edge:
                final_kind[y][x] = "outline"
            else:
                k = kind[y][x]
                if k == "outline":
                    # Distinguish dark spine/back-cover lip from front cover
                    final_kind[y][x] = "spine_edge"
                else:
                    final_kind[y][x] = k
    return smoothed, final_kind


def render_mod_codex(vanilla_book: list[list[tuple[int, int, int, int]]], frame_idx: int) -> list[list[tuple[int, int, int, int]]]:
    """Render a 32x32 mod-style Astral Tome preserving the vanilla book silhouette and cream pages."""
    solid, kind = _upscale_mask_32(vanilla_book)
    out = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]

    sweep = -6.0 + (frame_idx / FRAMES) * 44.0
    pulse = 0.5 + 0.5 * math.sin(frame_idx * (2.0 * math.pi / FRAMES))

    # Palette matching Universal Tool's 32x32 beveled obsidian-indigo + brushed gold + cyan-amethyst core
    outline_col = (14, 14, 24, 255)
    gold_hi = (252, 222, 108, 255)
    gold_mid = (208, 152, 42, 255)
    gold_dark = (132, 86, 22, 255)

    # Find cover bounding center for bevel & gem placement
    cover_coords = [(x, y) for y in range(SIZE) for x in range(SIZE) if kind[y][x] == "cover"]
    cx = sum(x for x, _ in cover_coords) / max(1, len(cover_coords))
    cy = sum(y for _, y in cover_coords) / max(1, len(cover_coords))

    for y in range(SIZE):
        for x in range(SIZE):
            k = kind[y][x]
            if k == "empty":
                continue
            if k == "outline":
                out[y][x] = outline_col
                continue
            if k == "pages":
                # Layered cream parchment with subtle 1px page-leaf striations
                stripe = ((x + y) % 3 == 0)
                near_top = (y > 0 and kind[y - 1][x] != "pages")
                if near_top:
                    out[y][x] = (158, 140, 112, 255)
                elif stripe:
                    out[y][x] = (206, 192, 162, 255)
                else:
                    out[y][x] = (238, 228, 202, 255)
                continue

            if k == "spine_edge":
                # Recessed obsidian-slate binding crease / lower cover lip
                out[y][x] = (34, 30, 56, 255)
                continue

            # Front cover: beveled dark astral obsidian-indigo with subtle top-left lighting
            has_top_edge = (y > 0 and kind[y - 1][x] in ("outline", "spine_edge"))
            has_left_edge = (x > 0 and kind[y][x - 1] in ("outline", "spine_edge"))
            has_bottom_edge = (y + 1 < SIZE and kind[y + 1][x] in ("outline", "pages", "spine_edge"))
            has_right_edge = (x + 1 < SIZE and kind[y][x + 1] in ("outline", "pages", "spine_edge"))

            # Diagonal bevel gradient across the cover
            grad = max(0.0, min(1.0, 1.0 - ((x - 4) + (y - 4)) / 36.0))
            nr = int(round(36 + grad * 30))
            ng = int(round(32 + grad * 32))
            nb = int(round(72 + grad * 58))

            if has_top_edge or has_left_edge:
                nr, ng, nb = min(255, nr + 24), min(255, ng + 26), min(255, nb + 36)
            elif has_bottom_edge or has_right_edge:
                nr, ng, nb = max(16, nr - 16), max(16, ng - 16), max(28, nb - 22)

            # Diagonal astral shimmer sweep across the cover
            dist = abs((x + (SIZE - 1 - y)) - sweep)
            if dist < 4.0:
                glint = (1.0 - dist / 4.0) * 0.34
                nr = min(255, int(round(nr + 110 * glint)))
                ng = min(255, int(round(ng + 185 * glint)))
                nb = min(255, int(round(nb + 255 * glint)))

            out[y][x] = (nr, ng, nb, 255)

    # Brushed gold corner brackets & spine ribs on cover/spine pixels
    for y in range(SIZE):
        for x in range(SIZE):
            if kind[y][x] not in ("cover", "spine_edge"):
                continue
            near_edge = any(
                0 <= y + dy < SIZE and 0 <= x + dx < SIZE and kind[y + dy][x + dx] in ("outline", "pages")
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            )
            # Corner clasps: top-left, top-right, bottom-left, bottom-right cover corners
            if near_edge and (
                (x <= 16 and y <= 8)
                or (x >= 21 and y <= 14)
                or (x <= 8 and y >= 15)
                or (13 <= x <= 18 and y >= 21)
            ):
                out[y][x] = gold_hi if (x + y) % 2 == 0 else gold_mid

    # Glowing octagonal/diamond cyan-amethyst core emblem at center of front cover
    gx_c = int(round(cx))
    gy_c = int(round(cy))
    cyan_core = (int(round(170 + 80 * pulse)), 255, 255, 255)
    cyan_mid = (int(round(45 + 95 * pulse)), int(round(215 + 35 * pulse)), 255, 255)
    amethyst_ring = (int(round(138 + 40 * pulse)), 74, 214, 255)

    for dy in range(-4, 5):
        for dx in range(-4, 5):
            px, py = gx_c + dx, gy_c + dy
            if not (0 <= px < SIZE and 0 <= py < SIZE) or kind[py][px] != "cover":
                continue
            manhattan = abs(dx) + abs(dy)
            if manhattan <= 1:
                out[py][px] = cyan_core
            elif manhattan == 2:
                out[py][px] = cyan_mid
            elif manhattan == 3:
                out[py][px] = amethyst_ring if (abs(dx) == 0 or abs(dy) == 0) else gold_mid
            elif manhattan == 4 and (abs(dx) <= 1 or abs(dy) <= 1):
                out[py][px] = gold_dark

    return out


def main() -> None:
    with zipfile.ZipFile(VANILLA_JAR, "r") as zf:
        vanilla_book = read_png_rgba(zf.read("assets/minecraft/textures/item/book.png"))

    sheet = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE * FRAMES)]
    preview_frames = []
    for f in range(FRAMES):
        frame = render_mod_codex(vanilla_book, f)
        if f in (0, 2, 4, 6):
            preview_frames.append(frame)
        for y in range(SIZE):
            sheet[f * SIZE + y] = frame[y]

    write_png(TEX_PATH, SIZE, SIZE * FRAMES, sheet)
    MCMETA_PATH.write_text(json.dumps({"animation": {"interpolate": True, "frametime": 2}}, indent=2) + "\n", encoding="utf-8")

    # 4x scale preview of 4 animation frames
    scale = 4
    pad = 8
    pw = len(preview_frames) * (SIZE * scale + pad) + pad
    ph = SIZE * scale + pad * 2
    preview = [[(24, 22, 32, 255) for _ in range(pw)] for _ in range(ph)]
    for idx, frame in enumerate(preview_frames):
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
    print(f"Generated 32x32 mod-style Teleport Codex texture + preview -> {PREVIEW_PATH}")


if __name__ == "__main__":
    main()
