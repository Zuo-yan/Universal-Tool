"""Generate Vanilla+ Minecraft 1.21.1 pixel-art GUI textures for PersonalSpace & Codex.

Outputs (only files owned by ui-stargate-codex):
- src/main/resources/assets/universal_tool/textures/gui/personal_space_panel.png (320x236)
- src/main/resources/assets/universal_tool/textures/gui/environment_*.png (8x 16x16)
- src/main/resources/assets/universal_tool/textures/gui/codex_parchment.png (280x190)
"""

from __future__ import annotations

from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/universal_tool/textures/gui"


def new_img(w: int, h: int, color=(0, 0, 0, 0)) -> Image.Image:
    return Image.new("RGBA", (w, h), color)


def hex_rgba(h: str, a: int = 255) -> tuple[int, int, int, int]:
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def put(px, w: int, h: int, x: int, y: int, c):
    if 0 <= x < w and 0 <= y < h:
        px[x, y] = c


def fill_rect(px, w: int, h: int, x0: int, y0: int, x1: int, y1: int, c):
    for y in range(max(0, y0), min(h, y1)):
        for x in range(max(0, x0), min(w, x1)):
            px[x, y] = c


def draw_mc_window(w: int, h: int) -> Image.Image:
    """Create a classic Minecraft #C6C6C6 beveled stone-gray container window."""
    img = new_img(w, h, (0, 0, 0, 0))
    px = img.load()

    BLACK = (0x00, 0x00, 0x00, 255)
    WHITE = (0xFF, 0xFF, 0xFF, 255)
    GRAY = (0xC6, 0xC6, 0xC6, 255)
    DARK = (0x55, 0x55, 0x55, 255)

    # Interior fill (inset by 1px; corners carved below)
    fill_rect(px, w, h, 1, 1, w - 1, h - 1, GRAY)

    # Top and left 2px white highlight; bottom and right 2px #555555 shadow
    fill_rect(px, w, h, 3, 1, w - 3, 3, WHITE)
    fill_rect(px, w, h, 1, 3, 3, h - 3, WHITE)
    fill_rect(px, w, h, 3, h - 3, w - 3, h - 1, DARK)
    fill_rect(px, w, h, w - 3, 3, w - 1, h - 3, DARK)

    # Outer 1px #000000 edges
    fill_rect(px, w, h, 3, 0, w - 3, 1, BLACK)
    fill_rect(px, w, h, 3, h - 1, w - 3, h, BLACK)
    fill_rect(px, w, h, 0, 3, 1, h - 3, BLACK)
    fill_rect(px, w, h, w - 1, 3, w, h - 3, BLACK)

    # Classic MC stepped 3px rounded corners:
    # Top-left
    for c in ((0, 0), (1, 0), (2, 0), (0, 1), (0, 2)):
        put(px, w, h, c[0], c[1], (0, 0, 0, 0))
    for c in ((1, 1), (2, 1), (1, 2)):
        put(px, w, h, c[0], c[1], BLACK)
    put(px, w, h, 2, 2, WHITE)
    put(px, w, h, 3, 1, WHITE)
    put(px, w, h, 1, 3, WHITE)
    put(px, w, h, 3, 2, WHITE)
    put(px, w, h, 2, 3, WHITE)

    # Top-right
    for c in ((w - 1, 0), (w - 2, 0), (w - 3, 0), (w - 1, 1), (w - 1, 2)):
        put(px, w, h, c[0], c[1], (0, 0, 0, 0))
    for c in ((w - 2, 1), (w - 3, 1), (w - 2, 2)):
        put(px, w, h, c[0], c[1], BLACK)
    put(px, w, h, w - 3, 2, GRAY)
    put(px, w, h, w - 4, 2, WHITE)
    put(px, w, h, w - 3, 3, DARK)

    # Bottom-left
    for c in ((0, h - 1), (1, h - 1), (2, h - 1), (0, h - 2), (0, h - 3)):
        put(px, w, h, c[0], c[1], (0, 0, 0, 0))
    for c in ((1, h - 2), (2, h - 2), (1, h - 3)):
        put(px, w, h, c[0], c[1], BLACK)
    put(px, w, h, 2, h - 3, GRAY)
    put(px, w, h, 3, h - 3, DARK)
    put(px, w, h, 2, h - 4, WHITE)

    # Bottom-right
    for c in ((w - 1, h - 1), (w - 2, h - 1), (w - 3, h - 1), (w - 1, h - 2), (w - 1, h - 3)):
        put(px, w, h, c[0], c[1], (0, 0, 0, 0))
    for c in ((w - 2, h - 2), (w - 3, h - 2), (w - 2, h - 3)):
        put(px, w, h, c[0], c[1], BLACK)
    put(px, w, h, w - 3, h - 3, DARK)
    put(px, w, h, w - 4, h - 2, DARK)
    put(px, w, h, w - 2, h - 4, DARK)

    return img


def draw_recessed_slot(px, w: int, h: int, x0: int, y0: int, size: int):
    """Draw a standard MC #8B8B8B recessed slot (#373737 top/left, #FFFFFF bottom/right)."""
    SHADOW = (0x37, 0x37, 0x37, 255)
    HI = (0xFF, 0xFF, 0xFF, 255)
    WELL = (0x8B, 0x8B, 0x8B, 255)
    CORNER = (0x8B, 0x8B, 0x8B, 255)

    fill_rect(px, w, h, x0 + 1, y0 + 1, x0 + size - 1, y0 + size - 1, WELL)
    fill_rect(px, w, h, x0, y0, x0 + size - 1, y0 + 1, SHADOW)
    fill_rect(px, w, h, x0, y0, x0 + 1, y0 + size - 1, SHADOW)
    fill_rect(px, w, h, x0 + 1, y0 + size - 1, x0 + size, y0 + size, HI)
    fill_rect(px, w, h, x0 + size - 1, y0 + 1, x0 + size, y0 + size, HI)
    put(px, w, h, x0 + size - 1, y0, CORNER)
    put(px, w, h, x0, y0 + size - 1, CORNER)


def generate_personal_space_panel():
    w, h = 320, 236
    img = draw_mc_window(w, h)
    px = img.load()

    SHADOW = (0x55, 0x55, 0x55, 255)
    HI = (0xFF, 0xFF, 0xFF, 255)

    # Etched Vanilla stone separator grooves at rows 50..51 and 200..201
    fill_rect(px, w, h, 8, 50, w - 8, 51, SHADOW)
    fill_rect(px, w, h, 8, 51, w - 8, 52, HI)

    fill_rect(px, w, h, 8, 200, w - 8, 201, SHADOW)
    fill_rect(px, w, h, 8, 201, w - 8, 202, HI)

    # Top-right decorative 18x18 Vanilla recessed slot holding an Eye of Ender emblem
    sx, sy = 286, 16
    draw_recessed_slot(px, w, h, sx, sy, 18)

    # Crisp 16x16 Eye of Ender pixel art inside (sx+1, sy+1)
    eye_grid = [
        "................",
        "......OOOO......",
        "....OOLLLLOO....",
        "...OLMMMMMMLO...",
        "..OLMMTTTTMMLO..",
        "..OMMTWWWWTTMO..",
        ".OLMTWPPPPWTMLO.",
        ".OMMTWPDKPWTMMO.",
        ".OMMTWPDKPWTMMO.",
        ".OLMTWPDDPWTMLO.",
        "..OMMTWWWWTTMO..",
        "..OLMMTTTTMMLO..",
        "...OLMMMMMMLO...",
        "....OODDDDOO....",
        "......OOOO......",
        "................",
    ]
    eye_pal = {
        "O": hex_rgba("#12362C"),
        "D": hex_rgba("#1B5A49"),
        "M": hex_rgba("#277D65"),
        "L": hex_rgba("#46B292"),
        "T": hex_rgba("#8AE6C3"),
        "W": hex_rgba("#D6FFF0"),
        "P": hex_rgba("#8B3AD6"),
        "K": hex_rgba("#24123D"),
    }
    for ey, row in enumerate(eye_grid):
        for ex, ch in enumerate(row):
            if ch != ".":
                put(px, w, h, sx + 1 + ex, sy + 1 + ey, eye_pal[ch])

    img.save(ROOT / "personal_space_panel.png")


def save_pixel_grid(filename: str, grid: list[str], palette: dict[str, tuple[int, int, int, int]]):
    assert len(grid) == 16 and all(len(r) == 16 for r in grid), f"{filename} must be 16x16"
    img = new_img(16, 16, (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = palette[ch]
    img.save(ROOT / filename)


def generate_environment_icons():
    # 1. superflat: Plains Grass + Dirt + Stone slab with subtle starlight glint
    save_pixel_grid(
        "environment_superflat.png",
        [
            "................",
            ".......Y........",
            "......YLY.......",
            ".......Y........",
            "..TTTTTTTTTTTT..",
            ".OHHHHHHHHHHHHO.",
            ".OGMMGGMGGGMMGO.",
            ".OGGDDDDDDDDGGO.",
            ".ODDDDPPPDDDDDO.",
            ".ODDBDDDDDDBDBO.",
            ".OBBBBBBBBBBBBO.",
            ".OSSSSSSSSSSSSO.",
            ".OSSQQSSSSSSSSO.",
            ".OSSSSSSSSKKSSO.",
            "..OOOOOOOOOOOO..",
            "................",
        ],
        {
            "Y": hex_rgba("#E6B83A"),
            "L": hex_rgba("#FFF7B2"),
            "T": hex_rgba("#437D2A"),
            "H": hex_rgba("#8CD663"),
            "M": hex_rgba("#71BA49"),
            "G": hex_rgba("#599E38"),
            "O": hex_rgba("#38281C"),
            "D": hex_rgba("#866043"),
            "P": hex_rgba("#9B7454"),
            "B": hex_rgba("#6A4A33"),
            "S": hex_rgba("#7F7F7F"),
            "Q": hex_rgba("#999999"),
            "K": hex_rgba("#5C5C5C"),
        },
    )

    # 2. sky_islands: Floating Grass/Stone island + Oak tree + cloud + mini satellite
    save_pixel_grid(
        "environment_sky_islands.png",
        [
            "....LLLL........",
            "...LFFFFL...CC..",
            "...LFFFGL..CCCC.",
            "....LFFG........",
            ".....BB.........",
            "..OOOBBOOOOO....",
            ".OHHHHHHHHHHO...",
            ".OGGGDDDDGGGO...",
            "..ODDDDDDDDO....",
            "..OSSSSSSSSO.OO.",
            "...OSSKKSSO.OHHO",
            "....OSKSSO..ODDO",
            ".....OSSO....OO.",
            "......OO........",
            "..CCC...........",
            ".CCCCC..........",
        ],
        {
            "O": hex_rgba("#313D2B"),
            "L": hex_rgba("#70BA46"),
            "F": hex_rgba("#529632"),
            "G": hex_rgba("#3D7424"),
            "B": hex_rgba("#6E4F34"),
            "H": hex_rgba("#8CD663"),
            "D": hex_rgba("#866043"),
            "S": hex_rgba("#858585"),
            "K": hex_rgba("#616161"),
            "C": hex_rgba("#EEF6FF"),
        },
    )

    # 3. cherry_grove: Vanilla Cherry Blossom tree + pink petals on lush grass
    save_pixel_grid(
        "environment_cherry_grove.png",
        [
            "....PPPPPPPP....",
            "..PPLLLLLLLPPP..",
            ".PPLLLMMMMLLPPP.",
            ".PLMMMMMMMMMMPP.",
            ".PMMMDDDDDMMMMP.",
            "..PPDDDDDDDDPP..",
            "....PPPBBPPP....",
            "..L....BB.......",
            ".......BB....P..",
            "......BBB.......",
            "..OOOBBBBBOOO...",
            ".OGGGGGPGGGGGO..",
            ".OGGDDDDDDGGGO..",
            "..OOOOOOOOOOO...",
            "................",
            "................",
        ],
        {
            "P": hex_rgba("#D16A97"),
            "L": hex_rgba("#FCE0EC"),
            "M": hex_rgba("#F4A8C7"),
            "D": hex_rgba("#DF7BA6"),
            "B": hex_rgba("#4A2830"),
            "O": hex_rgba("#35281C"),
            "G": hex_rgba("#76BD54"),
        },
    )

    # 4. amethyst_starfall: Amethyst crystal shards on Calcite/Basalt + star sparkle
    save_pixel_grid(
        "environment_amethyst_starfall.png",
        [
            "............S...",
            "...........SLS..",
            "......OO....S...",
            ".....OLLO.......",
            "..OO.OLMO.OO....",
            ".OLMOOLMOOMMO...",
            ".OLMOOMMOOMDO...",
            ".OMDOOMDOODDO...",
            ".OMDOODDOODDO...",
            "..OOOOOOOOOO....",
            ".OCCCCCCCCCCO...",
            ".OCCWWWWWWCCO...",
            ".OBBBBBBBBBBO...",
            ".OBBKKKKKKBBO...",
            "..OOOOOOOOOO....",
            "................",
        ],
        {
            "S": hex_rgba("#FFE580"),
            "L": hex_rgba("#FFFAD1"),
            "O": hex_rgba("#361C57"),
            "M": hex_rgba("#B476F2"),
            "D": hex_rgba("#7D42C4"),
            "C": hex_rgba("#E0DDD5"),
            "W": hex_rgba("#F5F3EE"),
            "B": hex_rgba("#4A4652"),
            "K": hex_rgba("#33303B"),
        },
    )

    # 5. aurora_glacier: MC Ice Spikes + Snow ridge + subtle Aurora arc
    save_pixel_grid(
        "environment_aurora_glacier.png",
        [
            "...AAA..........",
            "..AAABBBAA......",
            "......AABBBAA...",
            ".......OO.......",
            "......OLLO......",
            "..OO..OLIO..OO..",
            ".OLLO.OIIO.OLLO.",
            ".OLIO.OIID.OLIO.",
            ".OIID.OIDD.OIID.",
            ".OIIDOOIDDOOIDD.",
            ".OWWWWWWWWWWWWO.",
            ".OWWSSSSSSSSWWO.",
            ".OIIIIIIIIIIIIO.",
            ".OIIIDDDDDDIIIO.",
            "..OOOOOOOOOOOO..",
            "................",
        ],
        {
            "A": hex_rgba("#64E09F"),
            "B": hex_rgba("#4FC4D9"),
            "O": hex_rgba("#274475"),
            "L": hex_rgba("#D6F3FF"),
            "I": hex_rgba("#87BBFA"),
            "D": hex_rgba("#5286D6"),
            "W": hex_rgba("#F5FCFF"),
            "S": hex_rgba("#CFE7FA"),
        },
    )

    # 6. mushroom_wonderland: Giant Red Mushroom + tiny Brown Mushroom on Mycelium
    save_pixel_grid(
        "environment_mushroom_wonderland.png",
        [
            "................",
            "....OOOOOOOO....",
            "..OORWWRRRRROO..",
            ".ORRWWRRRRRWWRO.",
            ".ORRRRRRWRRWWRO.",
            ".ORRWWWRRRRRRRO.",
            ".ODDDDDDDDDDDDO.",
            "..OOOOOSSSOOOO..",
            "......OSSKO.....",
            "......OSSKO.OO..",
            "......OSSKOOBBO.",
            "..OOOOSKKSOOSSO.",
            ".OMMMMMMMMMMMMO.",
            ".OMMDDMMMMDDMMO.",
            "..OOOOOOOOOOOO..",
            "................",
        ],
        {
            "O": hex_rgba("#4D181A"),
            "R": hex_rgba("#DC3B33"),
            "D": hex_rgba("#A82420"),
            "W": hex_rgba("#F4EFE6"),
            "S": hex_rgba("#DDD4C6"),
            "K": hex_rgba("#B8AD9E"),
            "B": hex_rgba("#9B6C47"),
            "M": hex_rgba("#8E7A8D"),
        },
    )

    # 7. obsidian_forge: Obsidian caldera pillars with crying tears & glowing lava basin
    save_pixel_grid(
        "environment_obsidian_forge.png",
        [
            "................",
            ".OOO........OOO.",
            ".OMKO..EE..OKMO.",
            ".OKPO......OKKO.",
            ".OKKO..LL..OPKO.",
            ".OPKO.LOOL.OKKO.",
            ".OKKO.OOOO.OKMO.",
            ".OKKOOOOOOOOKKO.",
            ".OKKOBBBBBBOKKO.",
            ".OKKOBLYYYBOKKO.",
            ".OKKOBYYYYBOKKO.",
            ".OMKOBBBBBBOKKO.",
            ".OKKKKKKKKKKKKO.",
            "..OOOOOOOOOOOO..",
            "................",
            "................",
        ],
        {
            "O": hex_rgba("#181124"),
            "K": hex_rgba("#2B1E3D"),
            "M": hex_rgba("#412E59"),
            "P": hex_rgba("#9E42FF"),
            "E": hex_rgba("#FF9B26"),
            "B": hex_rgba("#C73B12"),
            "L": hex_rgba("#FF7D1A"),
            "Y": hex_rgba("#FFE96B"),
        },
    )

    # 8. sculk_sanctum: Deep Dark Sculk obelisk with glowing cyan veins & bone catalyst top
    save_pixel_grid(
        "environment_sculk_sanctum.png",
        [
            "................",
            "....OO....OO....",
            "...OBBO..OBBO...",
            "...OBBBOOBBBO...",
            "...OCCCCCCCCO...",
            "...OCCCCCCCCO...",
            "...OCSSSSSSCO...",
            "...OCSVSSVSCO...",
            "...OCSVVVVSCO...",
            "...OCSSVVSSCO...",
            "..OOCSVSSVSCOO..",
            ".ODDCSSSSSSCDDO.",
            ".ODDDDDDDDDDDDO.",
            ".ODDDKKDDKKDDDO.",
            "..OOOOOOOOOOOO..",
            "................",
        ],
        {
            "O": hex_rgba("#091C22"),
            "B": hex_rgba("#EAE2C8"),
            "C": hex_rgba("#3B3B46"),
            "D": hex_rgba("#2B2B34"),
            "K": hex_rgba("#1E1E26"),
            "S": hex_rgba("#0C2B33"),
            "V": hex_rgba("#26E0D8"),
        },
    )


def generate_codex_parchment():
    """Authentic Vanilla book.png style 280x190 double-page spread."""
    w, h = 280, 190
    img = new_img(w, h, (0, 0, 0, 0))
    px = img.load()

    # Leather cover colors
    COVER_OUT = hex_rgba("#24140A")
    COVER_HI = hex_rgba("#7A4A2A")
    COVER_MID = hex_rgba("#5C351C")
    COVER_SHADE = hex_rgba("#482814")
    COVER_LO = hex_rgba("#361C0D")

    # Brass corner clasps
    GOLD_OUT = hex_rgba("#543A10")
    GOLD_HI = hex_rgba("#FFE794")
    GOLD_MID = hex_rgba("#D6A238")
    GOLD_SH = hex_rgba("#9B6E1D")

    # Page stack edges
    EDGE_1 = hex_rgba("#E4D4B0")
    EDGE_2 = hex_rgba("#C8B38B")
    EDGE_3 = hex_rgba("#AA936A")
    EDGE_4 = hex_rgba("#8A734D")

    # Main parchment pages (clean, warm Vanilla paper — no noisy checkerboard)
    PAGE_MAIN = hex_rgba("#F4E7C8")
    PAGE_MARGIN = hex_rgba("#EAD9B3")
    PAGE_RULE = hex_rgba("#E0CEA5")

    # Spine fold gradient
    SPINE_1 = hex_rgba("#E5D3AD")
    SPINE_2 = hex_rgba("#CEB88E")
    SPINE_3 = hex_rgba("#B0986D")
    SPINE_4 = hex_rgba("#8C744E")

    # 1. Leather cover base with 2px rounded corners
    fill_rect(px, w, h, 1, 1, w - 1, h - 1, COVER_MID)
    # Subtle textured leather dithering only on the outer leather border (x<6 or x>=w-6 or y<6 or y>=h-6)
    for y in range(2, h - 2):
        for x in range(2, w - 2):
            if x < 6 or x >= w - 6 or y < 6 or y >= h - 6:
                if (x * 7 + y * 13) % 11 == 0:
                    put(px, w, h, x, y, COVER_SHADE)

    # Outer cover border + bevel
    fill_rect(px, w, h, 2, 0, w - 2, 1, COVER_OUT)
    fill_rect(px, w, h, 2, h - 1, w - 2, h, COVER_OUT)
    fill_rect(px, w, h, 0, 2, 1, h - 2, COVER_OUT)
    fill_rect(px, w, h, w - 1, 2, w, h - 2, COVER_OUT)
    for c in ((0, 0), (1, 0), (0, 1), (w - 1, 0), (w - 2, 0), (w - 1, 1),
              (0, h - 1), (1, h - 1), (0, h - 2), (w - 1, h - 1), (w - 2, h - 1), (w - 1, h - 2)):
        put(px, w, h, c[0], c[1], (0, 0, 0, 0))
    for c in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        put(px, w, h, c[0], c[1], COVER_OUT)

    # Top/left leather highlight & bottom/right shadow
    fill_rect(px, w, h, 2, 1, w - 2, 2, COVER_HI)
    fill_rect(px, w, h, 1, 2, 2, h - 2, COVER_HI)
    fill_rect(px, w, h, 2, h - 2, w - 2, h - 1, COVER_LO)
    fill_rect(px, w, h, w - 2, 2, w - 1, h - 2, COVER_LO)

    # 2. Bound page stack edges inside leather cover
    fill_rect(px, w, h, 5, 5, w - 5, h - 5, EDGE_4)
    fill_rect(px, w, h, 6, 6, w - 6, h - 6, EDGE_3)
    fill_rect(px, w, h, 7, 6, w - 7, h - 7, EDGE_2)
    fill_rect(px, w, h, 8, 7, w - 8, h - 8, EDGE_1)

    # 3. Clean warm cream parchment pages
    fill_rect(px, w, h, 9, 7, w - 9, h - 9, PAGE_MARGIN)
    fill_rect(px, w, h, 11, 9, w - 11, h - 11, PAGE_MAIN)

    # Subtle page-edge highlight on top of page stack
    fill_rect(px, w, h, 9, 7, w - 9, 8, hex_rgba("#FAF1DA"))

    # 4. Crisp book spine shadow down center (x = 136..143)
    cx = w // 2
    spine_cols = [
        (cx - 4, SPINE_1),
        (cx - 3, SPINE_2),
        (cx - 2, SPINE_3),
        (cx - 1, SPINE_4),
        (cx,     SPINE_4),
        (cx + 1, SPINE_3),
        (cx + 2, SPINE_2),
        (cx + 3, SPINE_1),
    ]
    for sx, col in spine_cols:
        fill_rect(px, w, h, sx, 7, sx + 1, h - 9, col)

    # Top and bottom leather spine caps
    fill_rect(px, w, h, cx - 5, 1, cx + 5, 7, COVER_SHADE)
    fill_rect(px, w, h, cx - 4, 1, cx + 4, 6, COVER_MID)
    fill_rect(px, w, h, cx - 4, 1, cx + 4, 2, COVER_HI)
    fill_rect(px, w, h, cx - 5, h - 9, cx + 5, h - 1, COVER_SHADE)
    fill_rect(px, w, h, cx - 4, h - 8, cx + 4, h - 2, COVER_MID)
    fill_rect(px, w, h, cx - 4, h - 2, cx + 4, h - 1, COVER_LO)

    # Subtle header divider rule on right page under "Record Here" button
    fill_rect(px, w, h, 148, 30, 266, 31, PAGE_RULE)

    # 5. Subtle Vanilla brass/gold book corner clasps
    def corner_clasp(cx0: int, cy0: int, dx: int, dy: int):
        # 5x5 L-shaped brass bracket
        for i in range(6):
            put(px, w, h, cx0 + i * dx, cy0, GOLD_MID)
            put(px, w, h, cx0, cy0 + i * dy, GOLD_MID)
            put(px, w, h, cx0 + i * dx, cy0 + dy, GOLD_SH)
            put(px, w, h, cx0 + dx, cy0 + i * dy, GOLD_SH)
        put(px, w, h, cx0, cy0, GOLD_HI)
        put(px, w, h, cx0 + dx, cy0, GOLD_HI)
        put(px, w, h, cx0, cy0 + dy, GOLD_HI)
        put(px, w, h, cx0 + 2 * dx, cy0 + 2 * dy, GOLD_OUT)

    corner_clasp(2, 2, 1, 1)
    corner_clasp(w - 3, 2, -1, 1)
    corner_clasp(2, h - 3, 1, -1)
    corner_clasp(w - 3, h - 3, -1, -1)

    img.save(ROOT / "codex_parchment.png")


if __name__ == "__main__":
    generate_personal_space_panel()
    generate_environment_icons()
    generate_codex_parchment()
    print("Generated personal_space_panel.png, 8 environment_*.png, and codex_parchment.png")
