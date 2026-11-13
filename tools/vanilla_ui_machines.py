"""Generate Vanilla+ #C6C6C6 beveled pixel-art GUI textures for the 4 machine containers.

Outputs:
- src/main/resources/assets/universal_tool/textures/gui/container/polymerizer_gui.png (256x256)
- src/main/resources/assets/universal_tool/textures/gui/container/phoenix_pod_gui.png (256x256)
- src/main/resources/assets/universal_tool/textures/gui/container/klein_terminal.png (512x512)
"""

from __future__ import annotations

import math
from pathlib import Path
from PIL import Image

OUT_DIR = Path("src/main/resources/assets/universal_tool/textures/gui/container")

# Classic Minecraft Vanilla GUI Palette
T = (0, 0, 0, 0)
BLK = (0, 0, 0, 255)
DARK = (55, 55, 55, 255)       # #373737 slot top/left shadow
SHAD = (85, 85, 85, 255)       # #555555 panel bottom/right shadow
SLOT_BG = (139, 139, 139, 255) # #8B8B8B recessed slot interior
PANEL = (198, 198, 198, 255)   # #C6C6C6 container panel face
PANEL_HI = (220, 220, 220, 255)
WHT = (255, 255, 255, 255)     # #FFFFFF highlight
TEXT_DARK = (64, 64, 64, 255)  # #404040

# Vanilla Stone Button Palette
BTN_BG = (126, 126, 126, 255)
BTN_HI = (176, 176, 176, 255)
BTN_SH = (84, 84, 84, 255)
BTN_HOV_BG = (125, 136, 189, 255)
BTN_HOV_HI = (180, 188, 226, 255)
BTN_HOV_SH = (79, 86, 120, 255)
BTN_ACT_BG = (108, 82, 150, 255)
BTN_ACT_HI = (156, 125, 204, 255)
BTN_ACT_SH = (68, 46, 99, 255)
BTN_EM_BG = (96, 138, 76, 255)
BTN_EM_HI = (146, 194, 118, 255)
BTN_EM_SH = (56, 86, 44, 255)

# Vanilla Material Accents (Copper, Amethyst, Gold, Oak, Iron, Flame, Potion)
COPPER_D = (124, 72, 34, 255)
COPPER_M = (184, 115, 51, 255)
COPPER_L = (218, 146, 88, 255)
AME_D = (100, 64, 146, 255)
AME_M = (156, 111, 214, 255)
AME_L = (196, 155, 255, 255)
GOLD_D = (138, 96, 20, 255)
GOLD_M = (212, 168, 56, 255)
GOLD_L = (245, 214, 120, 255)
OAK_D = (92, 62, 34, 255)
OAK_M = (138, 98, 56, 255)
OAK_L = (174, 130, 80, 255)
IRON_D = (94, 98, 104, 255)
IRON_M = (164, 170, 178, 255)
IRON_L = (224, 228, 234, 255)


def java_round(val: float) -> int:
    return math.floor(val + 0.5)


def put(px, x: int, y: int, c: tuple[int, int, int, int], w: int, h: int) -> None:
    if 0 <= x < w and 0 <= y < h:
        px[x, y] = c


def fill_rect(px, x0: int, y0: int, x1: int, y1: int, c: tuple[int, int, int, int]) -> None:
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = c


def draw_mc_panel(img: Image.Image, x0: int, y0: int, w: int, h: int) -> None:
    """Draw a classic Minecraft #C6C6C6 beveled container panel with 2px chamfered corners."""
    px = img.load()
    x1 = x0 + w - 1
    y1 = y0 + h - 1

    # Fill interior
    fill_rect(px, x0 + 1, y0 + 1, x1, y1, PANEL)

    # Outer 1px black border with 2px chamfered corners
    for x in range(x0 + 2, x1 - 1):
        px[x, y0] = BLK
        px[x, y1] = BLK
    for y in range(y0 + 2, y1 - 1):
        px[x0, y] = BLK
        px[x1, y] = BLK
    # Corner diagonals
    for cx, cy in [
        (x0 + 1, y0 + 1), (x1 - 1, y0 + 1),
        (x0 + 1, y1 - 1), (x1 - 1, y1 - 1),
    ]:
        px[cx, cy] = BLK
    # Transparent outer corner tips
    for cx, cy in [
        (x0, y0), (x0 + 1, y0), (x0, y0 + 1),
        (x1, y0), (x1 - 1, y0), (x1, y0 + 1),
        (x0, y1), (x0 + 1, y1), (x0, y1 - 1),
        (x1, y1), (x1 - 1, y1), (x1, y1 - 1),
    ]:
        px[cx, cy] = T

    # 2px #FFFFFF top & left bevel, 2px #555555 bottom & right bevel
    for x in range(x0 + 2, x1 - 1):
        px[x, y0 + 1] = WHT
        if x <= x1 - 2:
            px[x, y0 + 2] = WHT
        px[x, y1 - 1] = SHAD
        if x >= x0 + 2:
            px[x, y1 - 2] = SHAD

    for y in range(y0 + 2, y1 - 1):
        px[x0 + 1, y] = WHT
        if y <= y1 - 2:
            px[x0 + 2, y] = WHT
        px[x1 - 1, y] = SHAD
        if y >= y0 + 2:
            px[x1 - 2, y] = SHAD

    # Corner bevel transitions
    px[x0 + 2, y0 + 2] = WHT
    px[x1 - 2, y1 - 2] = SHAD
    px[x1 - 2, y0 + 1] = PANEL
    px[x1 - 2, y0 + 2] = PANEL
    px[x1 - 1, y0 + 2] = PANEL
    px[x0 + 1, y1 - 2] = PANEL
    px[x0 + 2, y1 - 2] = PANEL
    px[x0 + 2, y1 - 1] = PANEL


def draw_mc_slot(img: Image.Image, sx: int, sy: int, interior=SLOT_BG) -> None:
    """Draw a standard 18x18 recessed MC slot at (sx, sy)."""
    px = img.load()
    fill_rect(px, sx + 1, sy + 1, sx + 17, sy + 17, interior)
    # Top & Left #373737 shadow
    for x in range(sx, sx + 17):
        px[x, sy] = DARK
    for y in range(sy, sy + 17):
        px[sx, y] = DARK
    # Bottom & Right #FFFFFF highlight
    for x in range(sx + 1, sx + 18):
        px[x, sy + 17] = WHT
    for y in range(sy + 1, sy + 18):
        px[sx + 17, y] = WHT
    # Neutral corner pixels
    px[sx + 17, sy] = interior
    px[sx, sy + 17] = interior


def draw_groove(img: Image.Image, x0: int, x1: int, y: int) -> None:
    px = img.load()
    for x in range(x0, x1):
        px[x, y] = SHAD
        px[x, y + 1] = WHT


def draw_mc_button(
    img: Image.Image,
    x: int,
    y: int,
    w: int,
    h: int,
    bg,
    hi,
    sh,
    border=BLK,
    pressed: bool = False,
) -> None:
    px = img.load()
    fill_rect(px, x + 1, y + 1, x + w - 1, y + h - 1, bg)
    for ix in range(x, x + w):
        px[ix, y] = border
        px[ix, y + h - 1] = border
    for iy in range(y, y + h):
        px[x, iy] = border
        px[x + w - 1, iy] = border
    top_c = sh if pressed else hi
    bot_c = hi if pressed else sh
    for ix in range(x + 1, x + w - 1):
        px[ix, y + 1] = top_c
        px[ix, y + h - 2] = bot_c
    for iy in range(y + 1, y + h - 1):
        px[x + 1, iy] = top_c
        px[x + w - 2, iy] = bot_c
    px[x + w - 2, y + 1] = bg
    px[x + 1, y + h - 2] = bg


def draw_line_pixels(px, x0: int, y0: int, x1: int, y1: int, c, w: int, h: int) -> list[tuple[int, int]]:
    """Bresenham integer line returning plotted pixel coordinates."""
    pts = []
    dx = abs(x1 - x0)
    dy = -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    cx, cy = x0, y0
    while True:
        put(px, cx, cy, c, w, h)
        pts.append((cx, cy))
        if cx == x1 and cy == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            cx += sx
        if e2 <= dx:
            err += dx
            cy += sy
    return pts


# ---------------------------------------------------------------------------
# 1. Polymerizer GUI (256x256)
# ---------------------------------------------------------------------------
def build_polymerizer() -> None:
    img = Image.new("RGBA", (256, 256), T)
    draw_mc_panel(img, 0, 0, 192, 224)
    px = img.load()

    # Subtle copper/amethyst corner rivets in header (far from title text)
    for rx in (8, 181):
        fill_rect(px, rx, 7, rx + 3, 10, COPPER_D)
        px[rx + 1, 8] = AME_L

    # Divider groove between ring and inventory (clears slot 6 bottom y=123 & label y=127)
    draw_groove(img, 8, 184, 124)

    cx, cy, radius = 96, 70, 44

    # Outer circular conduit ring (r ~ 44) drawn before slots
    for py in range(18, 124):
        for pxx in range(42, 151):
            d = math.hypot(pxx - cx, py - cy)
            if 42.6 <= d <= 45.4:
                # 1px dark outer/inner casing, copper/amethyst core
                if 43.3 <= d <= 44.7:
                    ang = (math.degrees(math.atan2(py - cy, pxx - cx)) + 360.0) % 30.0
                    px[pxx, py] = AME_M if (12.0 <= ang <= 18.0) else COPPER_M
                else:
                    px[pxx, py] = SHAD if (pxx + py) % 2 == 0 else DARK

    # Inner alchemy ring around center slot (r ~ 14..18)
    for py in range(50, 91):
        for pxx in range(76, 117):
            d = math.hypot(pxx - cx, py - cy)
            if 13.0 <= d <= 15.2:
                px[pxx, py] = AME_M if ((pxx + py) % 3 == 0) else COPPER_M

    # Radial conduit spokes from the 12 slots inward toward center
    for i in range(12):
        rad = math.radians(i * 30.0 - 90.0)
        ox = java_round(cx + 35 * math.cos(rad))
        oy = java_round(cy + 35 * math.sin(rad))
        ix = java_round(cx + 15 * math.cos(rad))
        iy = java_round(cy + 15 * math.sin(rad))
        # Dark casing offset + core copper/amethyst conduit
        pts = draw_line_pixels(px, ox, oy, ix, iy, AME_M if i % 2 == 0 else COPPER_M, 192, 224)
        for lx, ly in pts:
            for nx, ny in ((lx + 1, ly), (lx - 1, ly), (lx, ly + 1), (lx, ly - 1)):
                if px[nx, ny] == PANEL:
                    px[nx, ny] = SHAD
        # Re-stamp crisp core over casing
        for idx, (lx, ly) in enumerate(pts):
            if idx == len(pts) // 2:
                px[lx, ly] = AME_L
            else:
                px[lx, ly] = COPPER_L if i % 2 == 1 else AME_M

    # Keep button dock (71..121, 81..95) clean #C6C6C6 with recessed 1px rim
    fill_rect(px, 71, 81, 121, 95, PANEL)
    for x in range(71, 121):
        px[x, 81] = SHAD
        px[x, 94] = WHT
    for y in range(81, 95):
        px[71, y] = SHAD
        px[120, y] = WHT

    # 12 outer slots (exact PolymerizerMenu math)
    for i in range(12):
        rad = math.radians(i * 30.0 - 90.0)
        sx = java_round(cx + radius * math.cos(rad)) - 8
        sy = java_round(cy + radius * math.sin(rad)) - 8
        draw_mc_slot(img, sx - 1, sy - 1)

    # Center result slot (88-1, 62-1) = (87, 61) with subtle copper/amethyst corner brackets
    draw_mc_slot(img, cx - 9, cy - 9)
    for bx, by in ((86, 60), (105, 60), (86, 79), (105, 79)):
        px[bx, by] = COPPER_L
    for bx, by in ((87, 60), (86, 61), (104, 60), (105, 61), (87, 79), (86, 78), (104, 79), (105, 78)):
        px[bx, by] = AME_M

    # Player inventory (16, 139) 3x9 + Hotbar (16, 199) 1x9
    for row in range(3):
        for col in range(9):
            draw_mc_slot(img, 15 + col * 18, 138 + row * 18)
    for col in range(9):
        draw_mc_slot(img, 15 + col * 18, 198)

    # Button sprites at u=196 (48x12): normal (v=0), hover (v=16), crafting (v=32)
    draw_mc_button(img, 196, 0, 48, 12, BTN_BG, BTN_HI, BTN_SH)
    draw_mc_button(img, 196, 16, 48, 12, BTN_HOV_BG, BTN_HOV_HI, BTN_HOV_SH)
    draw_mc_button(img, 196, 32, 48, 12, BTN_ACT_BG, BTN_ACT_HI, BTN_ACT_SH, pressed=True)

    # 4 crafting progress ring frames (18x18) at (196,48), (216,48), (196,68), (216,68)
    # Perimeter clockwise pixel list around 18x18 frame
    perim = (
        [(x, 0) for x in range(18)]
        + [(17, y) for y in range(1, 18)]
        + [(x, 17) for x in range(16, -1, -1)]
        + [(0, y) for y in range(16, 0, -1)]
    )
    n = len(perim)
    frames = [(196, 48), (216, 48), (196, 68), (216, 68)]
    for fi, (fu, fv) in enumerate(frames):
        offset = (fi * n) // 4
        # Two opposite orbiting amethyst/gold sparks along the perimeter
        for start in (offset, (offset + n // 2) % n):
            for step in range(12):
                x, y = perim[(start + step) % n]
                if step >= 9:
                    c = GOLD_L
                elif step >= 5:
                    c = AME_L
                else:
                    c = AME_M
                px[fu + x, fv + y] = c

    img.save(OUT_DIR / "polymerizer_gui.png")


# ---------------------------------------------------------------------------
# 2. Phoenix Pod GUI (256x256)
# ---------------------------------------------------------------------------
def build_phoenix_pod() -> None:
    img = Image.new("RGBA", (256, 256), T)
    draw_mc_panel(img, 0, 0, 176, 166)
    px = img.load()

    # Horizontal divider groove separating cultivation bay from Inventory (y=72)
    draw_groove(img, 7, 169, 69)

    # DNA slot (56, 36) -> box (55, 35)
    draw_mc_slot(img, 55, 35)
    # Subtle DNA helix silhouette inside empty slot (56..72, 36..52)
    helix_pts = [
        (59, 39), (60, 40), (61, 41), (63, 43), (64, 44), (65, 45), (66, 46), (67, 47),
        (67, 39), (66, 40), (65, 41), (63, 43), (62, 44), (61, 45), (60, 46), (59, 47),
        (61, 40), (65, 40), (61, 46), (65, 46),
    ]
    for hx, hy in helix_pts:
        px[hx, hy] = (116, 116, 116, 255)

    # Nutrient slot (104, 36) -> box (103, 35)
    draw_mc_slot(img, 103, 35)
    # Subtle potion bottle silhouette inside empty slot
    bottle_pts = [
        (110, 39), (111, 39), (112, 39),
        (110, 40), (112, 40),
        (109, 41), (113, 41),
        (108, 42), (114, 42),
        (108, 43), (114, 43),
        (108, 44), (114, 44),
        (108, 45), (114, 45),
        (109, 46), (113, 46),
        (110, 47), (111, 47), (112, 47),
    ]
    for bx, by in bottle_pts:
        px[bx, by] = (116, 116, 116, 255)

    # Brewing-Stand style copper/iron connector pipes into central tube (76..100, 36..52)
    for x in (73, 74, 75, 101, 102):
        px[x, 42] = SHAD
        px[x, 43] = COPPER_M
        px[x, 44] = COPPER_L
        px[x, 45] = DARK

    # Central glass-tube chamber base at (76, 36, 24x16)
    def draw_glass_tube_frame(ox: int, oy: int, liquid=None, bubble=None) -> None:
        # Outer recessed MC well
        fill_rect(px, ox, oy, ox + 24, oy + 16, (108, 112, 116, 255) if liquid is None else liquid[0])
        for x in range(ox, ox + 23):
            px[x, oy] = DARK
        for y in range(oy, oy + 15):
            px[ox, y] = DARK
        for x in range(ox + 1, ox + 24):
            px[x, oy + 15] = WHT
        for y in range(oy + 1, oy + 16):
            px[ox + 23, y] = WHT
        px[ox + 23, oy] = PANEL if ox < 176 else T
        px[ox, oy + 15] = PANEL if ox < 176 else T
        # Inner glass caps left/right
        for y in range(oy + 2, oy + 14):
            px[ox + 1, y] = IRON_D
            px[ox + 22, y] = IRON_D
        if liquid is None:
            # Empty glass interior + subtle tick marks & specular sheen
            fill_rect(px, ox + 2, oy + 2, ox + 22, oy + 14, (124, 130, 134, 255))
            for x in range(ox + 3, ox + 21):
                px[x, oy + 3] = (162, 174, 180, 255)
            for tick_x in (ox + 6, ox + 12, ox + 18):
                px[tick_x, oy + 12] = SHAD
                px[tick_x, oy + 13] = SHAD
        else:
            c_dark, c_mid, c_hi = liquid
            fill_rect(px, ox + 2, oy + 2, ox + 22, oy + 14, c_mid)
            for x in range(ox + 2, ox + 22):
                px[x, oy + 2] = c_dark
                px[x, oy + 13] = c_dark
                px[x, oy + 4] = c_hi
            if bubble is not None:
                for bx, by in bubble:
                    px[ox + bx, oy + by] = WHT

    draw_glass_tube_frame(76, 36)

    # Subtle Brewing-Stand / Furnace flame accent below tube (82..94, 54..64)
    flame_mask = [
        (88, 55),
        (87, 56), (88, 56), (89, 56),
        (86, 57), (87, 57), (88, 57), (89, 57),
        (85, 58), (86, 58), (87, 58), (88, 58), (89, 58), (90, 58),
        (85, 59), (86, 59), (88, 59), (90, 59),
        (85, 60), (86, 60), (89, 60), (90, 60),
        (86, 61), (87, 61), (88, 61), (89, 61),
    ]
    for fx, fy in flame_mask:
        px[fx, fy] = (158, 158, 158, 255)
    for fx, fy in ((87, 59), (88, 60), (87, 60)):
        px[fx, fy] = (212, 128, 64, 255)

    # Sprite (176, 0, 24x16): Cultivating flame/potion liquid bar
    draw_glass_tube_frame(
        176,
        0,
        liquid=((194, 72, 18, 255), (240, 140, 32, 255), (252, 224, 96, 255)),
        bubble=[(5, 8), (11, 6), (16, 10), (19, 7)],
    )

    # Sprite (176, 16, 24x16): Ready green potion bar with crisp checkmark
    draw_glass_tube_frame(
        176,
        16,
        liquid=((22, 114, 48, 255), (48, 186, 84, 255), (138, 236, 156, 255)),
    )
    check_pts = [(183, 24), (184, 25), (185, 26), (186, 27), (187, 26), (188, 25), (189, 24), (190, 23), (191, 22)]
    for cxp, cyp in check_pts:
        px[cxp, cyp + 1] = DARK
        px[cxp, cyp] = WHT

    # Sprite (176, 32, 24x16): Empty start button normal
    draw_mc_button(img, 176, 32, 24, 16, BTN_BG, BTN_HI, BTN_SH)
    for col_dx, half_h in enumerate((3, 3, 2, 2, 1, 1, 0)):
        for dy in range(-half_h, half_h + 1):
            px[185 + col_dx, 40 + dy + 1] = DARK
            px[185 + col_dx, 40 + dy] = (240, 240, 240, 255)

    # Sprite (176, 48, 24x16): Empty start button hovered
    draw_mc_button(img, 176, 48, 24, 16, BTN_HOV_BG, BTN_HOV_HI, BTN_HOV_SH)
    for col_dx, half_h in enumerate((3, 3, 2, 2, 1, 1, 0)):
        for dy in range(-half_h, half_h + 1):
            px[185 + col_dx, 56 + dy + 1] = DARK
            px[185 + col_dx, 56 + dy] = (255, 255, 160, 255)

    # Player inventory (8, 84) 3x9 + Hotbar (8, 142) 1x9
    for row in range(3):
        for col in range(9):
            draw_mc_slot(img, 7 + col * 18, 83 + row * 18)
    for col in range(9):
        draw_mc_slot(img, 7 + col * 18, 141)

    img.save(OUT_DIR / "phoenix_pod_gui.png")


# ---------------------------------------------------------------------------
# 3. Klein Terminal GUI (512x512)
# ---------------------------------------------------------------------------
def build_klein_terminal() -> None:
    img = Image.new("RGBA", (512, 512), T)
    draw_mc_panel(img, 0, 0, 194, 236)
    px = img.load()

    # Header Klein Bottle mini-emblem at (8, 6, 14x14)
    # Recessed small socket + purple/teal ender bottle glyph
    for y in range(7, 19):
        for x in range(9, 21):
            px[x, y] = SLOT_BG
    for x in range(9, 20):
        px[x, 7] = DARK
        px[x + 1, 18] = WHT
    for y in range(7, 18):
        px[9, y] = DARK
        px[20, y + 1] = WHT
    bottle = [
        (14, 9, GOLD_L), (15, 9, GOLD_M),
        (13, 10, IRON_L), (14, 10, AME_L), (15, 10, AME_M), (16, 10, IRON_M),
        (12, 11, IRON_M), (13, 11, AME_M), (14, 11, (82, 210, 200, 255)), (15, 11, AME_L), (16, 11, AME_D), (17, 11, IRON_D),
        (12, 12, IRON_M), (13, 12, AME_D), (14, 12, (82, 210, 200, 255)), (15, 12, AME_M), (16, 12, AME_D), (17, 12, IRON_D),
        (12, 13, IRON_M), (13, 13, AME_M), (14, 13, AME_L), (15, 13, AME_M), (16, 13, AME_D), (17, 13, IRON_D),
        (13, 14, IRON_D), (14, 14, AME_D), (15, 14, AME_D), (16, 14, IRON_D),
        (13, 15, IRON_D), (14, 15, IRON_M), (15, 15, IRON_M), (16, 15, IRON_D),
    ]
    for bx, by, bc in bottle:
        px[bx, by] = bc

    # Close button at (178, 7, 10x10)
    draw_mc_button(img, 178, 7, 10, 10, BTN_BG, BTN_HI, BTN_SH)
    for d in range(4):
        px[181 + d, 10 + d] = WHT
        px[184 - d, 10 + d] = WHT

    # Search EditBox well at (8, 24, 114x14) -> classic #000000 box with #373737/#FFFFFF recessed frame
    fill_rect(px, 9, 25, 121, 37, BLK)
    for x in range(8, 121):
        px[x, 24] = DARK
    for y in range(24, 37):
        px[8, y] = DARK
    for x in range(9, 122):
        px[x, 37] = WHT
    for y in range(25, 38):
        px[121, y] = WHT
    px[121, 24] = PANEL
    px[8, 37] = PANEL
    # Crisp 9x9 magnifying glass at (11, 27)
    glass_ring = [(13, 27), (14, 27), (15, 27), (12, 28), (16, 28), (12, 29), (16, 29), (12, 30), (16, 30), (13, 31), (14, 31), (15, 31)]
    for gx, gy in glass_ring:
        px[gx, gy] = IRON_L
    for gx, gy in ((16, 31), (17, 32), (18, 33)):
        px[gx, gy] = IRON_M

    # Divider groove between top controls and warehouse grid
    draw_groove(img, 8, 186, 42)

    # 5x9 Warehouse Grid at (8, 46)
    for row in range(5):
        for col in range(9):
            draw_mc_slot(img, 8 + col * 18, 46 + row * 18)

    # Scrollbar track at (174, 45) to (184, 137) -> inner track (175..183, 46..136)
    fill_rect(px, 175, 46, 183, 136, SLOT_BG)
    for x in range(174, 183):
        px[x, 45] = DARK
    for y in range(45, 136):
        px[174, y] = DARK
    for x in range(175, 184):
        px[x, 136] = WHT
    for y in range(46, 137):
        px[183, y] = WHT
    px[183, 45] = PANEL
    px[174, 136] = PANEL

    # Divider groove between warehouse grid and Inventory label (y=142)
    draw_groove(img, 8, 186, 138)

    # Player inventory (9, 154) 3x9 + Hotbar (9, 213) 1x9 -> boxes at (8, 153) & (8, 212)
    for row in range(3):
        for col in range(9):
            draw_mc_slot(img, 8 + col * 18, 153 + row * 18)
    for col in range(9):
        draw_mc_slot(img, 8 + col * 18, 212)

    # -----------------------------------------------------------------------
    # Atlas Sprites (x >= 200)
    # -----------------------------------------------------------------------
    # 1. Locked slot frame at (200, 0, 18x18)
    for x in range(200, 218):
        px[x, 0] = (150, 56, 56, 255)
        px[x, 17] = (150, 56, 56, 255)
    for y in range(0, 18):
        px[200, y] = (150, 56, 56, 255)
        px[217, y] = (150, 56, 56, 255)
    fill_rect(px, 201, 1, 217, 17, (80, 40, 40, 64))

    # 2. Padlock badge at (222, 0, 9x10)
    lock_pixels = [
        (225, 0, IRON_D), (226, 0, IRON_M), (227, 0, IRON_D),
        (224, 1, IRON_M), (228, 1, IRON_D),
        (224, 2, IRON_M), (228, 2, IRON_D),
        (224, 3, IRON_M), (228, 3, IRON_D),
    ]
    for lx, ly, lc in lock_pixels:
        px[lx, ly] = lc
    fill_rect(px, 223, 4, 230, 9, GOLD_M)
    for x in range(223, 230):
        px[x, 4] = GOLD_L
        px[x, 9] = GOLD_D
    for y in range(4, 9):
        px[223, y] = GOLD_L
        px[229, y] = GOLD_D
    px[226, 6] = BLK
    px[226, 7] = BLK

    # 3. Hovered warehouse slot highlight at (200, 24, 18x18)
    fill_rect(px, 201, 25, 217, 41, (255, 255, 255, 110))

    # 4. Drawer socket 18x18 at (268, 24, 18x18)
    draw_mc_slot(img, 268, 24)

    # 5. Classic MC Scrollbar thumb 3-slice at u=200:
    # Top cap (200, 48, 8x4), Middle (200, 52, 8x84), Bottom cap (200, 136, 8x4)
    for y in range(48, 140):
        px[200, y] = WHT
        for x in range(201, 206):
            px[x, y] = PANEL
        px[206, y] = SHAD
        px[207, y] = DARK
    for x in range(200, 207):
        px[x, 48] = WHT
        px[x, 49] = WHT
    px[207, 48] = DARK
    for x in range(200, 207):
        px[x, 138] = SHAD
        px[x, 139] = DARK
    px[200, 139] = DARK
    px[207, 139] = DARK

    # 6. Crafting / Anvil arrow (216, 48, 15x11)
    arrow_rows = [
        (8, 1),
        (8, 2),
        (0, 11),
        (0, 12),
        (0, 13),
        (0, 14),
        (0, 13),
        (0, 12),
        (0, 11),
        (8, 2),
        (8, 1),
    ]
    # Standardize crisp 15x11 vanilla arrow
    for dy in range(11):
        for dx in range(15):
            in_shaft = (3 <= dy <= 7) and (0 <= dx <= 9)
            in_head = (dx >= 9) and (abs(dy - 5) <= (14 - dx))
            if in_shaft or in_head:
                edge = (
                    (dy == 3 and dx < 9) or (dy == 7 and dx < 9) or dx == 0
                    or (dx == 9 and (dy == 0 or dy == 10))
                    or (dx >= 9 and abs(dy - 5) == (14 - dx))
                )
                px[216 + dx, 48 + dy] = SHAD if edge else SLOT_BG

    # 7. Anvil '+' sign (236, 48, 7x7)
    for d in range(7):
        px[239, 48 + d] = TEXT_DARK
        px[236 + d, 51] = TEXT_DARK
    px[239, 51] = SHAD

    # 8. Smelting flame (248, 48, 12x14)
    flame_rows = [
        (5, 6),
        (5, 7),
        (4, 7),
        (4, 8),
        (3, 8),
        (2, 9),
        (2, 9),
        (1, 10),
        (1, 10),
        (1, 10),
        (2, 9),
        (2, 9),
        (3, 8),
        (4, 7),
    ]
    for dy, (x0, x1) in enumerate(flame_rows):
        for dx in range(x0, x1 + 1):
            if dx == x0 or dx == x1 or dy in (0, 13):
                c = (180, 62, 16, 255)
            elif dy >= 8 and 4 <= dx <= 7:
                c = (254, 242, 130, 255)
            elif dy >= 5 and 3 <= dx <= 8:
                c = (245, 162, 24, 255)
            else:
                c = (232, 96, 20, 255)
            px[248 + dx, 48 + dy] = c

    # 9. Action button frames 14x14 at y=150: normal (200), hover (216), active (232)
    draw_mc_button(img, 200, 150, 14, 14, BTN_BG, BTN_HI, BTN_SH)
    draw_mc_button(img, 216, 150, 14, 14, BTN_HOV_BG, BTN_HOV_HI, BTN_HOV_SH)
    draw_mc_button(img, 232, 150, 14, 14, BTN_EM_BG, BTN_EM_HI, BTN_EM_SH, pressed=True)

    # 10. Action button glyphs 12x12 at (200 + i*16, 170)
    # 0: Quick Deposit (chest + down arrow)
    gx, gy = 200, 170
    fill_rect(px, gx + 2, gy + 6, gx + 10, gy + 11, OAK_M)
    for x in range(gx + 2, gx + 10):
        px[x, gy + 6] = OAK_L
        px[x, gy + 10] = OAK_D
    px[gx + 5, gy + 7] = GOLD_L
    px[gx + 6, gy + 7] = GOLD_L
    for y in range(gy + 1, gy + 5):
        px[gx + 5, y] = WHT
        px[gx + 6, y] = WHT
    for x in range(gx + 4, gx + 8):
        px[x, gy + 4] = WHT
    px[gx + 5, gy + 5] = GOLD_L
    px[gx + 6, gy + 5] = GOLD_L

    # 1: Crafting Table (mini 3x3 crafting grid)
    gx, gy = 216, 170
    fill_rect(px, gx + 1, gy + 1, gx + 11, gy + 11, OAK_D)
    fill_rect(px, gx + 2, gy + 2, gx + 10, gy + 10, OAK_L)
    for lx in (gx + 4, gx + 7):
        for y in range(gy + 2, gy + 10):
            px[lx, y] = OAK_D
    for ly in (gy + 4, gy + 7):
        for x in range(gx + 2, gx + 10):
            px[x, ly] = OAK_D

    # 2: Furnace (stone frame + lit fire mouth)
    gx, gy = 232, 170
    fill_rect(px, gx + 1, gy + 1, gx + 11, gy + 11, DARK)
    fill_rect(px, gx + 2, gy + 2, gx + 10, gy + 10, IRON_M)
    fill_rect(px, gx + 3, gy + 3, gx + 9, gy + 5, DARK)
    fill_rect(px, gx + 3, gy + 6, gx + 9, gy + 10, (218, 92, 18, 255))
    fill_rect(px, gx + 4, gy + 7, gx + 8, gy + 9, (254, 224, 88, 255))

    # 3: Anvil (classic dark iron anvil silhouette)
    gx, gy = 248, 170
    fill_rect(px, gx + 1, gy + 2, gx + 11, gy + 5, IRON_M)
    for x in range(gx + 1, gx + 11):
        px[x, gy + 2] = IRON_L
    fill_rect(px, gx + 4, gy + 5, gx + 8, gy + 8, DARK)
    fill_rect(px, gx + 2, gy + 8, gx + 10, gy + 10, IRON_D)
    for x in range(gx + 2, gx + 10):
        px[x, gy + 10] = BLK

    # 4: Auto Deposit (hopper/funnel icon)
    gx, gy = 264, 170
    fill_rect(px, gx + 1, gy + 2, gx + 11, gy + 4, IRON_L)
    fill_rect(px, gx + 2, gy + 4, gx + 10, gy + 6, IRON_M)
    fill_rect(px, gx + 4, gy + 6, gx + 8, gy + 8, IRON_D)
    fill_rect(px, gx + 5, gy + 8, gx + 7, gy + 11, DARK)

    # -----------------------------------------------------------------------
    # Left Drawer Panel at (300, 0, 118x88)
    # -----------------------------------------------------------------------
    draw_mc_panel(img, 300, 0, 118, 88)
    draw_groove(img, 306, 412, 16)

    img.save(OUT_DIR / "klein_terminal.png")


if __name__ == "__main__":
    build_polymerizer()
    build_phoenix_pod()
    build_klein_terminal()
    print("Generated polymerizer_gui.png, phoenix_pod_gui.png, klein_terminal.png successfully.")
