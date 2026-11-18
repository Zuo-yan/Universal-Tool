"""Generate the two parchment machine GUIs and the Klein Bottle terminal atlas:
- src/main/resources/assets/universal_tool/textures/gui/container/polymerizer_gui.png (256x256)
- src/main/resources/assets/universal_tool/textures/gui/container/phoenix_pod_gui.png (256x256)
- src/main/resources/assets/universal_tool/textures/gui/container/klein_terminal.png (512x512)
"""

from __future__ import annotations

import math
from pathlib import Path
from PIL import Image

OUT_DIR = Path("src/main/resources/assets/universal_tool/textures/gui/container")

# Transparent & Deep Umber Outline
T = (0, 0, 0, 0)
UMBER_BLK = (38, 24, 14, 255)      # #26180E crisp warm dark outline
BLK = (22, 14, 9, 255)             # #160E09 deepest accent shadow

# Walnut Timber Frame Palette
WALNUT_HI = (122, 86, 56, 255)     # #7A5638 top-left timber bevel
WALNUT_M = (92, 64, 40, 255)       # #5C4028 primary walnut frame
WALNUT_GR = (78, 52, 32, 255)      # #4E3420 subtle wood grain
WALNUT_SH = (61, 40, 24, 255)      # #3D2818 bottom-right timber shadow

# Burnished Brass Palette
BRASS_HI = (242, 216, 138, 255)    # #F2D88A specular brass highlight
BRASS_M = (214, 168, 78, 255)      # #D6A84E burnished brass
BRASS_D = (158, 115, 48, 255)      # #9E7330 aged brass shadow
BRASS_DK = (110, 76, 28, 255)      # #6E4C1C deep brass groove

# Warm Illuminated Parchment Palette (clean, subtle paper feel — NO noisy static)
PARCH_BANNER = (244, 236, 217, 255)  # #F4ECD9 header parchment banner
PARCH_M = (239, 229, 207, 255)       # #EFE5CF primary warm cream parchment
PARCH_ALT = (234, 223, 198, 255)     # #EADFC6 ultra-subtle paper fiber tone
PARCH_SH = (227, 213, 184, 255)      # #E3D5B8 soft parchment edge shading
PARCH_RULE = (198, 174, 134, 255)    # #C6AE86 delicate paper rule
PARCH_WHT = (250, 244, 231, 255)     # #FAF4E7 warm paper highlight

# 18x18 Recessed Warm-Stone / Vellum Slots
SLOT_BG = (196, 179, 149, 255)     # #C4B395 recessed warm-stone fill
SLOT_SH = (115, 95, 70, 255)       # #735F46 top-left inner shadow
SLOT_HI = (250, 242, 226, 255)     # #FAF2E2 bottom-right highlight
SLOT_CORNER = (178, 160, 130, 255) # #B2A082 neutral transition corner

# Espresso Ink & Wells
INK_DARK = (58, 40, 26, 255)       # #3A281A crisp espresso-ink
INK_MID = (92, 71, 53, 255)        # #5C4735 warm umber-ink
WELL_BG = (36, 27, 20, 255)        # #241B14 dark-espresso input well
WELL_RIM = (158, 115, 48, 255)     # #9E7330 brass inner rim

# Arcane Teal & Alchemical Amber/Emerald Accents
TEAL_DK = (28, 82, 86, 255)        # #1C5256
TEAL_M = (47, 168, 158, 255)       # #2FA89E
TEAL_L = (109, 224, 214, 255)      # #6DE0D6
TEAL_GLOW = (176, 255, 246, 255)   # #B0FFF6

EMBER_DK = (156, 62, 18, 255)
EMBER_M = (224, 128, 36, 255)
EMBER_L = (252, 214, 96, 255)

EMERALD_DK = (26, 98, 60, 255)
EMERALD_M = (52, 164, 98, 255)
EMERALD_L = (146, 234, 172, 255)

# Oak & Brass Button Palettes
OAK_BTN_BG = (132, 92, 52, 255)    # #845C34 warm oak-brass face
OAK_BTN_HI = (214, 168, 78, 255)   # #D6A84E burnished brass hi
OAK_BTN_SH = (76, 50, 26, 255)     # #4C321A walnut shadow

BRASS_BTN_BG = (188, 142, 56, 255) # #BC8E38 gilded hover brass
BRASS_BTN_HI = (246, 222, 144, 255)
BRASS_BTN_SH = (120, 84, 28, 255)

ACT_TEAL_BG = (36, 108, 104, 255)  # active arcane teal button
ACT_TEAL_HI = (104, 208, 198, 255)
ACT_TEAL_SH = (20, 62, 60, 255)

ACT_EM_BG = (48, 124, 84, 255)     # active emerald toggle
ACT_EM_HI = (128, 212, 156, 255)
ACT_EM_SH = (26, 74, 48, 255)

IRON_D = (84, 82, 82, 255)
IRON_M = (148, 146, 144, 255)
IRON_L = (214, 210, 204, 255)


def java_round(val: float) -> int:
    return math.floor(val + 0.5)


def put(px, x: int, y: int, c: tuple[int, int, int, int], w: int, h: int) -> None:
    if 0 <= x < w and 0 <= y < h:
        px[x, y] = c


def fill_rect(px, x0: int, y0: int, x1: int, y1: int, c: tuple[int, int, int, int]) -> None:
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = c


def draw_walnut_brass_panel(
    img: Image.Image,
    x0: int,
    y0: int,
    w: int,
    h: int,
    header_h: int = 13,
    corner_size: int = 6,
) -> None:
    """Draw a high-craft Walnut Timber + Burnished Brass frame with Warm Cream Parchment interior."""
    px = img.load()
    x1 = x0 + w - 1
    y1 = y0 + h - 1

    # 1. Parchment interior fill with ultra-subtle clean paper texture (no noisy static)
    for y in range(y0 + 5, y1 - 4):
        for x in range(x0 + 5, x1 - 4):
            if y < y0 + 5 + header_h:
                px[x, y] = PARCH_BANNER
            elif x == x0 + 5 or y == y0 + 5 or x == x1 - 5 or y == y1 - 5:
                px[x, y] = PARCH_SH
            elif (y % 6 == 0) and ((x + y // 6) % 7 == 0):
                px[x, y] = PARCH_ALT
            else:
                px[x, y] = PARCH_M

    # Subtle paper margin rule inside the header banner
    if header_h > 0:
        hb_y = y0 + 5 + header_h
        for x in range(x0 + 6, x1 - 5):
            px[x, hb_y] = PARCH_RULE
            px[x, hb_y + 1] = PARCH_WHT

    # 2. Inner 1px Burnished Brass rule (at offset 4 from outer edge)
    for x in range(x0 + 4, x1 - 3):
        px[x, y0 + 4] = BRASS_M
        px[x, y1 - 4] = BRASS_D
    for y in range(y0 + 4, y1 - 3):
        px[x0 + 4, y] = BRASS_M
        px[x1 - 4, y] = BRASS_D
    px[x0 + 4, y0 + 4] = BRASS_HI
    px[x1 - 4, y0 + 4] = BRASS_M
    px[x0 + 4, y1 - 4] = BRASS_M
    px[x1 - 4, y1 - 4] = BRASS_DK

    # 3. 3px Walnut Timber Border (offsets 1..3) with subtle wood grain
    for x in range(x0 + 2, x1 - 1):
        # Top walnut bar
        px[x, y0 + 1] = WALNUT_HI
        px[x, y0 + 2] = WALNUT_GR if (x % 9 in (2, 3)) else WALNUT_M
        px[x, y0 + 3] = WALNUT_SH if (x % 11 == 5) else WALNUT_M
        # Bottom walnut bar
        px[x, y1 - 1] = WALNUT_SH
        px[x, y1 - 2] = WALNUT_GR if (x % 8 in (1, 2)) else WALNUT_M
        px[x, y1 - 3] = WALNUT_HI if (x % 13 == 4) else WALNUT_M

    for y in range(y0 + 2, y1 - 1):
        # Left walnut bar
        px[x0 + 1, y] = WALNUT_HI
        px[x0 + 2, y] = WALNUT_GR if (y % 9 in (2, 3)) else WALNUT_M
        px[x0 + 3, y] = WALNUT_SH if (y % 11 == 6) else WALNUT_M
        # Right walnut bar
        px[x1 - 1, y] = WALNUT_SH
        px[x1 - 2, y] = WALNUT_GR if (y % 8 in (1, 2)) else WALNUT_M
        px[x1 - 3, y] = WALNUT_HI if (y % 13 == 5) else WALNUT_M

    # 4. Crisp 1px Umber outer outline with 2px chamfered corners
    for x in range(x0 + 2, x1 - 1):
        px[x, y0] = UMBER_BLK
        px[x, y1] = UMBER_BLK
    for y in range(y0 + 2, y1 - 1):
        px[x0, y] = UMBER_BLK
        px[x1, y] = UMBER_BLK
    for cx, cy in [
        (x0 + 1, y0 + 1), (x1 - 1, y0 + 1),
        (x0 + 1, y1 - 1), (x1 - 1, y1 - 1),
    ]:
        px[cx, cy] = UMBER_BLK
    for cx, cy in [
        (x0, y0), (x0 + 1, y0), (x0, y0 + 1),
        (x1, y0), (x1 - 1, y0), (x1, y0 + 1),
        (x0, y1), (x0 + 1, y1), (x0, y1 - 1),
        (x1, y1), (x1 - 1, y1), (x1, y1 - 1),
    ]:
        px[cx, cy] = T

    # 5. Ornate Beveled Burnished Brass Corner Brackets & Rivets
    # Top-Left bracket
    for d in range(1, corner_size + 1):
        px[x0 + d, y0 + 1] = BRASS_HI if d < corner_size else BRASS_D
        px[x0 + d, y0 + 2] = BRASS_M
        px[x0 + d, y0 + 3] = BRASS_D
        px[x0 + 1, y0 + d] = BRASS_HI if d < corner_size else BRASS_D
        px[x0 + 2, y0 + d] = BRASS_M
        px[x0 + 3, y0 + d] = BRASS_D
    px[x0 + 1, y0 + 1] = UMBER_BLK
    px[x0 + 2, y0 + 2] = BRASS_HI
    px[x0 + 3, y0 + 3] = BRASS_DK

    # Top-Right bracket
    for d in range(1, corner_size + 1):
        px[x1 - d, y0 + 1] = BRASS_HI if d < corner_size else BRASS_D
        px[x1 - d, y0 + 2] = BRASS_M
        px[x1 - d, y0 + 3] = BRASS_D
        px[x1 - 1, y0 + d] = BRASS_D
        px[x1 - 2, y0 + d] = BRASS_M
        px[x1 - 3, y0 + d] = BRASS_HI if d < corner_size else BRASS_D
    px[x1 - 1, y0 + 1] = UMBER_BLK
    px[x1 - 2, y0 + 2] = BRASS_HI
    px[x1 - 3, y0 + 3] = BRASS_DK

    # Bottom-Left bracket
    for d in range(1, corner_size + 1):
        px[x0 + d, y1 - 1] = BRASS_D
        px[x0 + d, y1 - 2] = BRASS_M
        px[x0 + d, y1 - 3] = BRASS_HI if d < corner_size else BRASS_D
        px[x0 + 1, y1 - d] = BRASS_HI if d < corner_size else BRASS_D
        px[x0 + 2, y1 - d] = BRASS_M
        px[x0 + 3, y1 - d] = BRASS_D
    px[x0 + 1, y1 - 1] = UMBER_BLK
    px[x0 + 2, y1 - 2] = BRASS_M
    px[x0 + 3, y1 - 3] = BRASS_DK

    # Bottom-Right bracket
    for d in range(1, corner_size + 1):
        px[x1 - d, y1 - 1] = BRASS_D
        px[x1 - d, y1 - 2] = BRASS_M
        px[x1 - d, y1 - 3] = BRASS_HI if d < corner_size else BRASS_M
        px[x1 - 1, y1 - d] = BRASS_D
        px[x1 - 2, y1 - d] = BRASS_M
        px[x1 - 3, y1 - d] = BRASS_HI if d < corner_size else BRASS_M
    px[x1 - 1, y1 - 1] = UMBER_BLK
    px[x1 - 2, y1 - 2] = BRASS_M
    px[x1 - 3, y1 - 3] = BRASS_HI


def draw_vellum_slot(img: Image.Image, sx: int, sy: int, interior=SLOT_BG) -> None:
    """Draw a crisp 18x18 recessed warm-stone/vellum slot."""
    px = img.load()
    fill_rect(px, sx + 1, sy + 1, sx + 17, sy + 17, interior)
    for x in range(sx, sx + 17):
        px[x, sy] = SLOT_SH
    for y in range(sy, sy + 17):
        px[sx, y] = SLOT_SH
    for x in range(sx + 1, sx + 18):
        px[x, sy + 17] = SLOT_HI
    for y in range(sy + 1, sy + 18):
        px[sx + 17, y] = SLOT_HI
    px[sx + 17, sy] = SLOT_CORNER
    px[sx, sy + 17] = SLOT_CORNER


def draw_brass_divider(img: Image.Image, x0: int, x1: int, y: int) -> None:
    """Draw a warm walnut & brass divider rule with tiny end studs."""
    px = img.load()
    for x in range(x0, x1):
        px[x, y] = BRASS_D
        px[x, y + 1] = PARCH_WHT
    # Tiny brass end studs
    px[x0, y] = BRASS_M
    px[x1 - 1, y] = BRASS_M


def draw_beveled_button(
    img: Image.Image,
    x: int,
    y: int,
    w: int,
    h: int,
    bg,
    hi,
    sh,
    border=UMBER_BLK,
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
    draw_walnut_brass_panel(img, 0, 0, 192, 224, header_h=10, corner_size=7)
    px = img.load()

    # Astrolabe header corner studs (far from centered title)
    for rx in (12, 177):
        fill_rect(px, rx, 7, rx + 3, 10, BRASS_D)
        px[rx + 1, 8] = TEAL_L

    # Divider rule between astrolabe & player inventory
    draw_brass_divider(img, 9, 183, 124)

    cx, cy, radius = 96, 70, 44

    # Subtle cream parchment astrolabe dial face behind the ring (r <= 46)
    for py in range(22, 118):
        for pxx in range(48, 145):
            d = math.hypot(pxx - cx, py - cy)
            if 16.0 < d < 42.6:
                # Subtle cardinal & intercardinal engraving lines
                if abs(pxx - cx) == 0 or abs(py - cy) == 0:
                    px[pxx, py] = PARCH_RULE
                elif abs(d - 29.0) <= 0.6:
                    px[pxx, py] = PARCH_RULE

    # Outer brass astrolabe ring (r ~ 44) with luminous teal arcane degree ticks
    for py in range(18, 124):
        for pxx in range(42, 151):
            d = math.hypot(pxx - cx, py - cy)
            if 42.3 <= d <= 45.7:
                if 43.2 <= d <= 44.8:
                    ang = (math.degrees(math.atan2(py - cy, pxx - cx)) + 360.0) % 30.0
                    if 13.0 <= ang <= 17.0:
                        px[pxx, py] = TEAL_L
                    elif 11.5 <= ang <= 18.5:
                        px[pxx, py] = TEAL_M
                    else:
                        px[pxx, py] = BRASS_M if (pxx + py) % 2 == 0 else BRASS_HI
                else:
                    px[pxx, py] = WALNUT_M if d > 44.8 else BRASS_D

    # Inner brass astrolabe collar around center slot (r ~ 13..16)
    for py in range(50, 91):
        for pxx in range(76, 117):
            d = math.hypot(pxx - cx, py - cy)
            if 13.0 <= d <= 15.6:
                if 13.7 <= d <= 14.9:
                    px[pxx, py] = TEAL_L if ((pxx + py) % 4 == 0) else BRASS_M
                else:
                    px[pxx, py] = BRASS_D

    # 12 radial luminous teal-gold arcane conduit spokes
    for i in range(12):
        rad = math.radians(i * 30.0 - 90.0)
        ox = java_round(cx + 35 * math.cos(rad))
        oy = java_round(cy + 35 * math.sin(rad))
        ix = java_round(cx + 15 * math.cos(rad))
        iy = java_round(cy + 15 * math.sin(rad))
        pts = draw_line_pixels(px, ox, oy, ix, iy, TEAL_M, 192, 224)
        for lx, ly in pts:
            for nx, ny in ((lx + 1, ly), (lx - 1, ly), (lx, ly + 1), (lx, ly - 1)):
                if px[nx, ny] in (PARCH_M, PARCH_ALT, PARCH_RULE):
                    px[nx, ny] = WALNUT_M
        for idx, (lx, ly) in enumerate(pts):
            if idx == len(pts) // 2:
                px[lx, ly] = TEAL_GLOW
            elif i % 2 == 0:
                px[lx, ly] = TEAL_L if idx % 3 == 0 else TEAL_M
            else:
                px[lx, ly] = BRASS_HI if idx % 2 == 0 else BRASS_M

    # Button dock at (71..121, 81..95) framed in walnut/brass
    fill_rect(px, 71, 81, 121, 95, WALNUT_SH)
    for x in range(71, 121):
        px[x, 81] = BRASS_D
        px[x, 94] = BRASS_HI
    for y in range(81, 95):
        px[71, y] = BRASS_D
        px[120, y] = BRASS_HI

    # 12 outer slots (exact PolymerizerMenu coordinates)
    for i in range(12):
        rad = math.radians(i * 30.0 - 90.0)
        sx = java_round(cx + radius * math.cos(rad)) - 8
        sy = java_round(cy + radius * math.sin(rad)) - 8
        draw_vellum_slot(img, sx - 1, sy - 1)

    # Gilded center synthesis slot (87, 61, 18x18) with ornate brass corner clasps
    draw_vellum_slot(img, cx - 9, cy - 9)
    for bx, by in ((86, 60), (105, 60), (86, 79), (105, 79)):
        px[bx, by] = BRASS_HI
    for bx, by in ((87, 60), (86, 61), (104, 60), (105, 61), (87, 79), (86, 78), (104, 79), (105, 78)):
        px[bx, by] = BRASS_M
    for bx, by in ((88, 60), (86, 62), (103, 60), (105, 62), (88, 79), (86, 77), (103, 79), (105, 77)):
        px[bx, by] = TEAL_L

    # Player inventory (16, 139) 3x9 + Hotbar (16, 199) 1x9 -> boxes at (15, 138) & (15, 198)
    for row in range(3):
        for col in range(9):
            draw_vellum_slot(img, 15 + col * 18, 138 + row * 18)
    for col in range(9):
        draw_vellum_slot(img, 15 + col * 18, 198)

    # Ornate brass "Polymerize" button sprites at u=196 (48x12): normal (v=0), hover (v=16), crafting (v=32)
    draw_beveled_button(img, 196, 0, 48, 12, OAK_BTN_BG, OAK_BTN_HI, OAK_BTN_SH)
    draw_beveled_button(img, 196, 16, 48, 12, BRASS_BTN_BG, BRASS_BTN_HI, BRASS_BTN_SH)
    draw_beveled_button(img, 196, 32, 48, 12, ACT_TEAL_BG, ACT_TEAL_HI, ACT_TEAL_SH, pressed=True)
    # Brass corner rivets on all 3 button states
    for v_off in (0, 16, 32):
        for rx in (198, 241):
            for ry in (v_off + 2, v_off + 9):
                px[rx, ry] = BRASS_HI if v_off != 32 else TEAL_GLOW

    # Smooth 4-frame astrolabe spinner (18x18) at (196,48), (216,48), (196,68), (216,68)
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
        # Base subtle brass ring outline
        for x, y in perim:
            px[fu + x, fv + y] = (158, 115, 48, 110)
        # Two opposite orbiting luminous teal-gold sparks
        for start in (offset, (offset + n // 2) % n):
            for step in range(14):
                x, y = perim[(start + step) % n]
                if step >= 11:
                    c = BRASS_HI
                elif step >= 7:
                    c = TEAL_GLOW
                elif step >= 3:
                    c = TEAL_L
                else:
                    c = TEAL_M
                px[fu + x, fv + y] = c

    img.save(OUT_DIR / "polymerizer_gui.png")


# ---------------------------------------------------------------------------
# 2. Phoenix Pod GUI (256x256)
# ---------------------------------------------------------------------------
def build_phoenix_pod() -> None:
    img = Image.new("RGBA", (256, 256), T)
    draw_walnut_brass_panel(img, 0, 0, 176, 166, header_h=11, corner_size=6)
    px = img.load()

    # Horizontal walnut-brass divider separating cultivation bay from Inventory (y=72)
    draw_brass_divider(img, 8, 168, 69)

    # DNA slot (56, 36) -> box (55, 35)
    draw_vellum_slot(img, 55, 35)
    # Subtle warm-umber DNA helix silhouette inside empty slot
    helix_pts = [
        (59, 39), (60, 40), (61, 41), (63, 43), (64, 44), (65, 45), (66, 46), (67, 47),
        (67, 39), (66, 40), (65, 41), (63, 43), (62, 44), (61, 45), (60, 46), (59, 47),
        (61, 40), (65, 40), (61, 46), (65, 46),
    ]
    for hx, hy in helix_pts:
        px[hx, hy] = (158, 138, 108, 255)

    # Nutrient slot (104, 36) -> box (103, 35)
    draw_vellum_slot(img, 103, 35)
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
        px[bx, by] = (158, 138, 108, 255)

    # Create-style burnished brass pipes connecting DNA & Nutrient slots into central stasis tube
    for x in (73, 74, 75, 101, 102):
        px[x, 42] = WALNUT_M
        px[x, 43] = BRASS_HI
        px[x, 44] = BRASS_M
        px[x, 45] = BRASS_DK

    def draw_brass_glass_tube(ox: int, oy: int, liquid=None, bubble=None) -> None:
        """Draw a brass-capped glass alchemy stasis tube (24x16) at (ox, oy)."""
        # Outer recessed bezel
        fill_rect(px, ox, oy, ox + 24, oy + 16, SLOT_BG)
        for x in range(ox, ox + 23):
            px[x, oy] = SLOT_SH
        for y in range(oy, oy + 15):
            px[ox, y] = SLOT_SH
        for x in range(ox + 1, ox + 24):
            px[x, oy + 15] = SLOT_HI
        for y in range(oy + 1, oy + 16):
            px[ox + 23, y] = SLOT_HI
        px[ox + 23, oy] = PARCH_M if ox < 176 else T
        px[ox, oy + 15] = PARCH_M if ox < 176 else T

        # Left & right burnished brass end-caps (2px wide)
        for y in range(oy + 1, oy + 15):
            px[ox + 1, y] = BRASS_D
            px[ox + 2, y] = BRASS_M
            px[ox + 21, y] = BRASS_M
            px[ox + 22, y] = BRASS_D
        for x in (ox + 1, ox + 2, ox + 21, ox + 22):
            px[x, oy + 2] = BRASS_HI
            px[x, oy + 13] = BRASS_DK

        if liquid is None:
            # Empty glass chamber with crisp specular streak and brass graduation ticks
            fill_rect(px, ox + 3, oy + 2, ox + 21, oy + 14, (170, 194, 192, 255))
            for x in range(ox + 3, ox + 21):
                px[x, oy + 2] = (124, 148, 150, 255)
                px[x, oy + 3] = (224, 240, 240, 255)
                px[x, oy + 13] = (134, 156, 156, 255)
            for tick_x in (ox + 7, ox + 12, ox + 17):
                px[tick_x, oy + 11] = BRASS_D
                px[tick_x, oy + 12] = BRASS_D
        else:
            c_dark, c_mid, c_hi = liquid
            fill_rect(px, ox + 3, oy + 2, ox + 21, oy + 14, c_mid)
            for x in range(ox + 3, ox + 21):
                px[x, oy + 2] = c_dark
                px[x, oy + 13] = c_dark
                px[x, oy + 4] = c_hi
            if bubble is not None:
                for bx, by in bubble:
                    px[ox + bx, oy + by] = PARCH_WHT

    draw_brass_glass_tube(76, 36)

    # Small brass alchemical brazier base below tube (82..94, 54..63)
    for x in range(84, 93):
        px[x, 62] = WALNUT_SH
        px[x, 61] = BRASS_D
    for x in range(85, 92):
        px[x, 60] = BRASS_M
    px[84, 59] = BRASS_HI
    px[92, 59] = BRASS_D
    flame_mask = [
        (88, 55),
        (87, 56), (88, 56), (89, 56),
        (86, 57), (87, 57), (88, 57), (89, 57),
        (85, 58), (86, 58), (87, 58), (88, 58), (89, 58), (90, 58),
        (86, 59), (87, 59), (88, 59), (89, 59),
    ]
    for fx, fy in flame_mask:
        px[fx, fy] = (176, 156, 128, 255)
    for fx, fy in ((87, 58), (88, 59), (87, 59)):
        px[fx, fy] = EMBER_M

    # Sprite (176, 0, 24x16): Warm amber/emerald cultivation liquid
    draw_brass_glass_tube(
        176,
        0,
        liquid=(EMBER_DK, EMBER_M, EMBER_L),
        bubble=[(6, 8), (11, 6), (15, 10), (18, 7)],
    )

    # Sprite (176, 16, 24x16): Ready emerald stasis tube + crisp ivory checkmark
    draw_brass_glass_tube(
        176,
        16,
        liquid=(EMERALD_DK, EMERALD_M, EMERALD_L),
    )
    check_pts = [(183, 24), (184, 25), (185, 26), (186, 27), (187, 26), (188, 25), (189, 24), (190, 23), (191, 22)]
    for cxp, cyp in check_pts:
        px[cxp, cyp + 1] = EMERALD_DK
        px[cxp, cyp] = PARCH_WHT

    # Sprite (176, 32, 24x16): Brass/Oak Play button normal
    draw_beveled_button(img, 176, 32, 24, 16, OAK_BTN_BG, OAK_BTN_HI, OAK_BTN_SH)
    for col_dx, half_h in enumerate((3, 3, 2, 2, 1, 1, 0)):
        for dy in range(-half_h, half_h + 1):
            px[185 + col_dx, 40 + dy + 1] = WALNUT_SH
            px[185 + col_dx, 40 + dy] = PARCH_WHT

    # Sprite (176, 48, 24x16): Brass Play button hovered
    draw_beveled_button(img, 176, 48, 24, 16, BRASS_BTN_BG, BRASS_BTN_HI, BRASS_BTN_SH)
    for col_dx, half_h in enumerate((3, 3, 2, 2, 1, 1, 0)):
        for dy in range(-half_h, half_h + 1):
            px[185 + col_dx, 56 + dy + 1] = BRASS_DK
            px[185 + col_dx, 56 + dy] = (255, 248, 196, 255)

    # Player inventory (8, 84) 3x9 + Hotbar (8, 142) 1x9
    for row in range(3):
        for col in range(9):
            draw_vellum_slot(img, 7 + col * 18, 83 + row * 18)
    for col in range(9):
        draw_vellum_slot(img, 7 + col * 18, 141)

    img.save(OUT_DIR / "phoenix_pod_gui.png")


# ---------------------------------------------------------------------------
# 3. Klein Terminal GUI (512x512) — Astral Void-Glass & Crystalline Cyan Theme
# ---------------------------------------------------------------------------
K_OUTLINE = (9, 17, 22, 255)
K_FRAME_HI = (63, 90, 94, 255)
K_FRAME_M = (32, 49, 54, 255)
K_FRAME_SH = (19, 33, 39, 255)
K_BG_HDR = (23, 44, 49, 255)
K_BG_MAIN = (22, 31, 37, 255)
K_CYAN_GLOW = (198, 244, 218, 255)
K_CYAN_L = (141, 224, 193, 255)
K_CYAN_M = (77, 160, 144, 255)
K_CYAN_D = (44, 89, 86, 255)
K_AMETHYST = (164, 145, 222, 255)
K_SLOT_BG = (12, 23, 28, 255)
K_PACK_BG = (17, 25, 32, 255)
K_SLOT_SH = (10, 19, 23, 255)
K_SLOT_HI = (53, 76, 82, 255)
K_WELL_BG = (12, 23, 26, 255)
K_BTN_N_BG = (29, 43, 49, 255)
K_BTN_N_HI = (53, 75, 81, 255)
K_BTN_N_SH = (11, 22, 26, 255)
K_BTN_A_BG = (32, 67, 61, 255)
K_BTN_A_HI = (107, 204, 174, 255)
K_BTN_A_SH = (18, 46, 43, 255)


def draw_klein_panel(img: Image.Image, x: int, y: int, w: int, h: int, header_h: int) -> None:
    px = img.load()
    fill_rect(px, x + 2, y, x + w - 2, y + h, K_OUTLINE)
    fill_rect(px, x, y + 2, x + w, y + h - 2, K_OUTLINE)
    fill_rect(px, x + 2, y + 1, x + w - 2, y + h - 1, K_FRAME_HI)
    fill_rect(px, x + 1, y + 2, x + w - 1, y + h - 2, K_FRAME_M)
    fill_rect(px, x + 2, y + 2, x + w - 2, y + h - 2, K_BG_MAIN)
    fill_rect(px, x + 3, y + 3, x + w - 3, y + 3 + header_h, K_BG_HDR)
    fill_rect(px, x + 3, y + 3, x + 31, y + 4, K_CYAN_M)
    fill_rect(px, x + 3, y + h - 4, x + w - 3, y + h - 2, K_FRAME_SH)


def draw_klein_divider(img: Image.Image, x0: int, x1: int, y: int) -> None:
    px = img.load()
    fill_rect(px, x0, y, x1, y + 1, K_FRAME_M)
    fill_rect(px, x0, y, x0 + 13, y + 1, K_CYAN_D)


def draw_klein_slot(img: Image.Image, x: int, y: int, fill=K_SLOT_BG) -> None:
    px = img.load()
    fill_rect(px, x, y, x + 18, y + 18, K_BG_MAIN)
    fill_rect(px, x + 1, y, x + 16, y + 17, K_SLOT_SH)
    fill_rect(px, x, y + 1, x + 17, y + 16, K_SLOT_SH)
    fill_rect(px, x + 1, y + 1, x + 16, y + 16, fill)
    fill_rect(px, x + 2, y + 16, x + 16, y + 17, K_SLOT_HI)
    px[x + 16, y + 15] = K_SLOT_HI


def build_klein_terminal() -> None:
    img = Image.new("RGBA", (512, 512), T)
    draw_klein_panel(img, 0, 0, 194, 236, header_h=18)
    px = img.load()

    # Klein bottle emblem on a recessed square in the header.
    fill_rect(px, 8, 6, 22, 20, K_WELL_BG)
    # Topological Klein bottle mini silhouette matching item texture
    bottle = [
        (14, 9, K_CYAN_GLOW), (15, 9, K_CYAN_L), (16, 9, K_AMETHYST),
        (13, 10, K_CYAN_L), (14, 10, K_CYAN_GLOW), (15, 10, K_CYAN_M), (16, 10, K_CYAN_L),
        (12, 11, K_CYAN_M), (13, 11, K_CYAN_L), (14, 11, K_CYAN_GLOW), (15, 11, K_CYAN_L), (16, 11, K_CYAN_D), (17, 11, K_CYAN_M),
        (12, 12, K_CYAN_M), (13, 12, K_CYAN_D), (14, 12, K_CYAN_GLOW), (15, 12, K_CYAN_L), (16, 12, K_CYAN_D), (17, 12, K_CYAN_M),
        (12, 13, K_CYAN_D), (13, 13, K_CYAN_M), (14, 13, K_CYAN_L), (15, 13, K_CYAN_M), (16, 13, K_CYAN_D), (17, 13, K_CYAN_D),
        (13, 14, K_CYAN_D), (14, 14, K_AMETHYST), (15, 14, K_CYAN_M), (16, 14, K_CYAN_D),
        (13, 15, K_CYAN_D), (14, 15, K_CYAN_M), (15, 15, K_CYAN_L), (16, 15, K_CYAN_D),
    ]
    for bx, by, bc in bottle:
        px[bx, by] = bc

    # Crystalline Close button at (178, 7, 10x10)
    draw_beveled_button(img, 178, 7, 10, 10, K_BTN_N_BG, K_BTN_N_HI, K_BTN_N_SH)
    for d in range(4):
        px[181 + d, 10 + d] = K_CYAN_GLOW
        px[184 - d, 10 + d] = K_CYAN_GLOW

    # Search well. The renderer adds the animated focus underline.
    fill_rect(px, 8, 24, 122, 38, K_WELL_BG)
    fill_rect(px, 8, 37, 122, 38, K_SLOT_HI)
    glass_ring = [(13, 27), (14, 27), (15, 27), (12, 28), (16, 28), (12, 29), (16, 29), (12, 30), (16, 30), (13, 31), (14, 31), (15, 31)]
    for gx, gy in glass_ring:
        px[gx, gy] = K_CYAN_GLOW
    for gx, gy in ((16, 31), (17, 32), (18, 33)):
        px[gx, gy] = K_CYAN_M

    # Divider rule between top controls and warehouse grid
    draw_klein_divider(img, 8, 186, 42)

    # 5x9 Warehouse Grid at (8, 46)
    for row in range(5):
        for col in range(9):
            draw_klein_slot(img, 8 + col * 18, 46 + row * 18)

    # Scrollbar track at (174, 45) to (184, 137) -> deep void channel
    fill_rect(px, 175, 46, 183, 136, K_WELL_BG)
    for x in range(174, 183):
        px[x, 45] = K_SLOT_SH
    for y in range(45, 136):
        px[174, y] = K_SLOT_SH
    for x in range(175, 184):
        px[x, 136] = K_SLOT_HI
    for y in range(46, 137):
        px[183, y] = K_SLOT_HI
    px[183, 45] = K_BG_MAIN
    px[174, 136] = K_BG_MAIN

    # Divider rule between warehouse grid and Inventory label (y=142)
    draw_klein_divider(img, 8, 186, 138)

    # Player inventory (9, 154) 3x9 + Hotbar (9, 213) 1x9 -> boxes at (8, 153) & (8, 212)
    for row in range(3):
        for col in range(9):
            draw_klein_slot(img, 8 + col * 18, 153 + row * 18, K_PACK_BG)
    for col in range(9):
        draw_klein_slot(img, 8 + col * 18, 212, K_PACK_BG)

    # -----------------------------------------------------------------------
    # Atlas Sprites (x >= 200) — exact UVs preserved!
    # -----------------------------------------------------------------------
    # 1. Locked slot frame at (200, 0, 18x18).
    for x in range(200, 218):
        px[x, 0] = K_AMETHYST
        px[x, 17] = K_AMETHYST
    for y in range(0, 18):
        px[200, y] = K_AMETHYST
        px[217, y] = K_AMETHYST
    fill_rect(px, 201, 1, 217, 17, (142, 119, 196, 49))

    # 2. Crystalline Padlock badge at (222, 0, 9x10)
    lock_pixels = [
        (225, 0, K_CYAN_M), (226, 0, K_CYAN_GLOW), (227, 0, K_CYAN_M),
        (224, 1, K_CYAN_L), (228, 1, K_CYAN_D),
        (224, 2, K_CYAN_L), (228, 2, K_CYAN_D),
        (224, 3, K_CYAN_L), (228, 3, K_CYAN_D),
    ]
    for lx, ly, lc in lock_pixels:
        px[lx, ly] = lc
    fill_rect(px, 223, 4, 230, 9, K_CYAN_M)
    for x in range(223, 230):
        px[x, 4] = K_CYAN_GLOW
        px[x, 9] = K_CYAN_D
    for y in range(4, 9):
        px[223, y] = K_CYAN_GLOW
        px[229, y] = K_CYAN_D
    px[226, 6] = K_OUTLINE
    px[226, 7] = K_OUTLINE

    # 3. Hovered warehouse slot highlight at (200, 24, 18x18)
    fill_rect(px, 201, 25, 217, 41, (141, 224, 193, 45))
    for cxp, cyp in ((200, 24), (217, 24), (200, 41), (217, 41)):
        px[cxp, cyp] = K_CYAN_GLOW

    # 4. Drawer socket 18x18 at (268, 24, 18x18)
    draw_klein_slot(img, 268, 24)

    # 5. Crystalline Cyan Scrollbar thumb 3-slice at u=200:
    # Top cap (200, 48, 8x4), Middle (200, 52, 8x84), Bottom cap (200, 136, 8x4)
    fill_rect(px, 202, 48, 206, 140, K_CYAN_D)
    fill_rect(px, 203, 49, 205, 139, K_CYAN_M)
    fill_rect(px, 203, 48, 205, 49, K_CYAN_L)

    # 6. Crafting / Anvil arrow (216, 48, 15x11)
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
                px[216 + dx, 48 + dy] = K_CYAN_D if edge else K_CYAN_L

    # 7. Anvil '+' sign (236, 48, 7x7)
    for d in range(7):
        px[239, 48 + d] = K_CYAN_L
        px[236 + d, 51] = K_CYAN_L
    px[239, 51] = K_CYAN_GLOW

    # 8. Smelting soul-cyan/astral flame (248, 48, 12x14)
    flame_rows = [
        (5, 6), (5, 7), (4, 7), (4, 8), (3, 8), (2, 9), (2, 9),
        (1, 10), (1, 10), (1, 10), (2, 9), (2, 9), (3, 8), (4, 7),
    ]
    for dy, (x0, x1) in enumerate(flame_rows):
        for dx in range(x0, x1 + 1):
            if dx == x0 or dx == x1 or dy in (0, 13):
                c = K_CYAN_D
            elif dy >= 8 and 4 <= dx <= 7:
                c = (216, 255, 255, 255)
            elif dy >= 5 and 3 <= dx <= 8:
                c = K_CYAN_GLOW
            else:
                c = K_CYAN_L
            px[248 + dx, 48 + dy] = c

    # 9. Action button frames 14x14 at y=150: normal (200), active (232)
    draw_beveled_button(img, 200, 150, 14, 14, K_BTN_N_BG, K_BTN_N_HI, K_BTN_N_SH)
    draw_beveled_button(img, 232, 150, 14, 14, K_BTN_A_BG, K_BTN_A_HI, K_BTN_A_SH, pressed=True)

    # 10. Detailed 12x12 Action button glyphs at (200 + i*16, 170)
    # 0: Quick Deposit (astral void chest + crisp cyan down arrow)
    gx, gy = 200, 170
    fill_rect(px, gx + 2, gy + 6, gx + 10, gy + 11, K_FRAME_M)
    for x in range(gx + 2, gx + 10):
        px[x, gy + 6] = K_CYAN_M
        px[x, gy + 10] = K_OUTLINE
    px[gx + 5, gy + 7] = K_CYAN_GLOW
    px[gx + 6, gy + 7] = K_CYAN_GLOW
    for y in range(gy + 1, gy + 5):
        px[gx + 4, y] = K_OUTLINE
        px[gx + 7, y] = K_OUTLINE
        px[gx + 5, y] = K_CYAN_GLOW
        px[gx + 6, y] = K_CYAN_GLOW
    for x in range(gx + 3, gx + 9):
        px[x, gy + 4] = K_OUTLINE if x in (gx + 3, gx + 8) else K_CYAN_GLOW
    px[gx + 4, gy + 5] = K_OUTLINE
    px[gx + 7, gy + 5] = K_OUTLINE
    px[gx + 5, gy + 5] = K_CYAN_L
    px[gx + 6, gy + 5] = K_CYAN_L

    # 1: Crafting Table (astral-crystal 3x3 grid)
    gx, gy = 216, 170
    fill_rect(px, gx + 1, gy + 1, gx + 11, gy + 11, K_OUTLINE)
    fill_rect(px, gx + 2, gy + 2, gx + 10, gy + 10, K_FRAME_HI)
    for lx in (gx + 4, gx + 7):
        for y in range(gy + 2, gy + 10):
            px[lx, y] = K_CYAN_M
    for ly in (gy + 4, gy + 7):
        for x in range(gx + 2, gx + 10):
            px[x, ly] = K_CYAN_M
    for cxp, cyp in ((gx + 1, gy + 1), (gx + 10, gy + 1), (gx + 1, gy + 10), (gx + 10, gy + 10)):
        px[cxp, cyp] = K_CYAN_GLOW

    # 2: Furnace (astral-iron frame + soul-cyan hearth)
    gx, gy = 232, 170
    fill_rect(px, gx + 1, gy + 1, gx + 11, gy + 11, K_OUTLINE)
    fill_rect(px, gx + 2, gy + 2, gx + 10, gy + 10, IRON_M)
    fill_rect(px, gx + 3, gy + 3, gx + 9, gy + 5, K_WELL_BG)
    fill_rect(px, gx + 3, gy + 6, gx + 9, gy + 10, K_CYAN_M)
    fill_rect(px, gx + 4, gy + 7, gx + 8, gy + 9, K_CYAN_GLOW)

    # 3: Anvil (crisp forged star-iron anvil)
    gx, gy = 248, 170
    fill_rect(px, gx + 1, gy + 2, gx + 11, gy + 5, IRON_M)
    for x in range(gx + 1, gx + 11):
        px[x, gy + 2] = IRON_L
    fill_rect(px, gx + 4, gy + 5, gx + 8, gy + 8, IRON_D)
    fill_rect(px, gx + 2, gy + 8, gx + 10, gy + 10, IRON_D)
    for x in range(gx + 2, gx + 10):
        px[x, gy + 10] = K_OUTLINE

    # 4: Auto Deposit (crystalline cyan-silver hopper funnel)
    gx, gy = 264, 170
    fill_rect(px, gx + 1, gy + 2, gx + 11, gy + 4, K_CYAN_GLOW)
    fill_rect(px, gx + 2, gy + 4, gx + 10, gy + 6, K_CYAN_L)
    fill_rect(px, gx + 4, gy + 6, gx + 8, gy + 8, K_CYAN_M)
    fill_rect(px, gx + 5, gy + 8, gx + 7, gy + 11, K_CYAN_D)

    # -----------------------------------------------------------------------
    # Left drawer panel at (300, 0, 118x88).
    # -----------------------------------------------------------------------
    draw_klein_panel(img, 300, 0, 118, 88, header_h=14)
    draw_klein_divider(img, 306, 412, 17)

    img.save(OUT_DIR / "klein_terminal.png")


if __name__ == "__main__":
    build_polymerizer()
    build_phoenix_pod()
    build_klein_terminal()
    print("Generated polymerizer_gui.png, phoenix_pod_gui.png, klein_terminal.png successfully.")
