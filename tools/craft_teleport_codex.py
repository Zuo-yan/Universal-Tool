"""Builds a vanilla Minecraft 16x16 book-silhouette Teleport Codex texture (.png + .png.mcmeta)
directly from minecraft_1.21.1_client.jar's book.png with astral indigo cover, gold trim, and shimmer.
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

SIZE = 16
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


def restyle_vanilla_book(base: list[list[tuple[int, int, int, int]]], frame_idx: int) -> list[list[tuple[int, int, int, int]]]:
    """Recolor vanilla book.png leather cover to astral indigo + gold clasp + cyan rune bookmark + shimmer."""
    out = [row[:] for row in base]
    sweep = -4.0 + (frame_idx / FRAMES) * 24.0
    pulse = 0.5 + 0.5 * math.sin(frame_idx * (2.0 * math.pi / FRAMES))

    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = base[y][x]
            if a == 0:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            # Keep dark outline and bright parchment pages intact
            is_parchment = (r > 175 and g > 155 and b > 115)
            is_outline = (lum < 45)
            if is_outline:
                out[y][x] = (14, 16, 32, a)
                continue
            if is_parchment:
                continue

            # Brown leather cover -> deep astral navy/indigo
            if r > g and r > b:
                t = max(0.0, min(1.0, (lum - 45.0) / 135.0))
                nr = int(round(26 + t * 44))
                ng = int(round(34 + t * 62))
                nb = int(round(78 + t * 116))
            else:
                nr, ng, nb = r, g, b

            # Diagonal shimmer sweep across the indigo cover
            dist = abs((x + (SIZE - 1 - y)) - sweep)
            if dist < 2.5:
                glint = (1.0 - dist / 2.5) * 0.38
                nr = min(255, int(round(nr + 115 * glint)))
                ng = min(255, int(round(ng + 185 * glint)))
                nb = min(255, int(round(nb + 255 * glint)))

            out[y][x] = (nr, ng, nb, a)

    # Subtle gold clasp & glowing cyan-aqua bookmark/star emblem on the cover center (vanilla book style!)
    gold_hi = (248, 212, 88, 255)
    gold_mid = (204, 146, 36, 255)
    cyan_glow = (
        int(round(65 + 145 * pulse)),
        int(round(220 + 35 * pulse)),
        255,
        255,
    )
    # Gold clasp on top-right edge & spine studs
    for gx, gy, col in (
        (8, 3, gold_hi), (7, 4, gold_mid),
        (10, 7, gold_hi), (11, 6, gold_mid),
        (4, 10, gold_mid), (6, 12, gold_mid),
        (7, 6, cyan_glow), (8, 6, cyan_glow), (7, 7, cyan_glow),
    ):
        if base[gy][gx][3] > 0:
            out[gy][gx] = col

    return out


def main() -> None:
    with zipfile.ZipFile(VANILLA_JAR, "r") as zf:
        vanilla_book = read_png_rgba(zf.read("assets/minecraft/textures/item/book.png"))

    sheet = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE * FRAMES)]
    preview_frames = []
    for f in range(FRAMES):
        frame = restyle_vanilla_book(vanilla_book, f)
        if f in (0, 2, 4, 6):
            preview_frames.append(frame)
        for y in range(SIZE):
            sheet[f * SIZE + y] = frame[y]

    write_png(TEX_PATH, SIZE, SIZE * FRAMES, sheet)
    MCMETA_PATH.write_text(json.dumps({"animation": {"interpolate": True, "frametime": 2}}, indent=2) + "\n", encoding="utf-8")

    # 6x scale preview of 4 animation frames
    scale = 6
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
    print(f"Generated vanilla-book Teleport Codex texture + preview -> {PREVIEW_PATH}")


if __name__ == "__main__":
    main()
