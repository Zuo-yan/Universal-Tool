"""
Generates the "Create / Ars Nouveau" Warm Illuminated Parchment, Walnut Wood &
Burnished Brass UI textures for the Star Gate (PersonalSpace) and Arcane Codex:
  - src/main/resources/assets/universal_tool/textures/gui/personal_space_panel.png (320x236)
  - src/main/resources/assets/universal_tool/textures/gui/codex_parchment.png (280x190)
  - src/main/resources/assets/universal_tool/textures/gui/environment_*.png (8 x 32x32)
"""

from pathlib import Path
import math
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
GUI_DIR = ROOT / "src/main/resources/assets/universal_tool/textures/gui"

# Shared Palette (Warm Walnut, Burnished Brass, Illuminated Parchment, Espresso Ink)
C_OUTLINE = (26, 17, 10, 255)        # #1A110A
C_WALNUT_DARK = (56, 36, 21, 255)    # #382415
C_WALNUT = (92, 64, 40, 255)         # #5C4028
C_WALNUT_HI = (125, 89, 58, 255)     # #7D593A
C_WALNUT_GRAIN_D = (74, 50, 31, 255) # #4A321F
C_WALNUT_GRAIN_L = (106, 74, 46, 255)# #6A4A2E

C_BRASS_DARK = (89, 62, 24, 255)     # #593E18
C_BRASS_MID = (158, 115, 48, 255)    # #9E7330
C_BRASS = (214, 168, 78, 255)        # #D6A84E
C_BRASS_HI = (242, 210, 122, 255)    # #F2D27A
C_BRASS_SPEC = (255, 242, 188, 255)  # #FFF2BC

C_PARCH_LIGHT = (244, 235, 214, 255) # #F4EBD6
C_PARCH_MAIN = (237, 226, 200, 255)  # #EDE2C8
C_PARCH_MID = (227, 213, 184, 255)   # #E3D5B8
C_PARCH_EDGE = (210, 191, 154, 255)  # #D2BF9A
C_PARCH_SHAD = (186, 163, 124, 255)  # #BAA37C
C_INK_FAINT = (164, 138, 98, 255)    # #A48A62


def lerp_rgb(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return (
        int(round(c1[0] + (c2[0] - c1[0]) * t)),
        int(round(c1[1] + (c2[1] - c1[1]) * t)),
        int(round(c1[2] + (c2[2] - c1[2]) * t)),
        255,
    )


def hash2d(x, y, seed=0):
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def set_px(px, w, h, x, y, col):
    if 0 <= x < w and 0 <= y < h:
        px[x, y] = col


def fill_rect(px, w, h, x0, y0, x1, y1, col):
    for y in range(max(0, y0), min(h, y1)):
        for x in range(max(0, x0), min(w, x1)):
            px[x, y] = col


def draw_brass_bracket(px, w, h, cx, cy, sx, sy):
    """Draws an ornate 15x15 burnished brass corner bracket from corner (cx, cy) inward along (sx, sy)."""
    # L-shaped plate arms (length 15, thickness 4 stepping to 2)
    for d in range(15):
        thick = 5 if d < 5 else (4 if d < 10 else (3 if d < 13 else 2))
        for t in range(thick):
            # Horizontal arm
            hx = cx + sx * d
            hy = cy + sy * t
            # Vertical arm
            vx = cx + sx * t
            vy = cy + sy * d

            for ax, ay in ((hx, hy), (vx, vy)):
                if not (0 <= ax < w and 0 <= ay < h):
                    continue
                is_outer_edge = (d == 0 or t == 0 or t == thick - 1 or d == 14)
                is_bevel_hi = (t == 1 and d < 13) or (d == 1 and t < thick - 1)
                if d == 14 or t == thick - 1:
                    col = C_BRASS_DARK
                elif d == 0 or t == 0:
                    col = C_OUTLINE
                elif is_bevel_hi and (sx > 0 or sy > 0):
                    col = C_BRASS_HI if (sx > 0 and sy > 0) else C_BRASS
                elif t == 1:
                    col = C_BRASS
                else:
                    col = C_BRASS_MID
                px[ax, ay] = col

    # Diagonal inner flange & corner finial
    for dy in range(2, 7):
        for dx in range(2, 7):
            if dx + dy <= 8:
                ax, ay = cx + sx * dx, cy + sy * dy
                if dx + dy == 8:
                    set_px(px, w, h, ax, ay, C_BRASS_DARK)
                elif dx == 3 and dy == 3:
                    set_px(px, w, h, ax, ay, C_BRASS_SPEC)
                elif dx + dy <= 5:
                    set_px(px, w, h, ax, ay, C_BRASS_HI)
                else:
                    set_px(px, w, h, ax, ay, C_BRASS)

    # Rivet studs along arms
    for rx, ry in ((2, 2), (7, 2), (2, 7)):
        px_x = cx + sx * rx
        px_y = cy + sy * ry
        set_px(px, w, h, px_x, px_y, C_BRASS_SPEC)
        set_px(px, w, h, px_x + sx, px_y + sy, C_BRASS_DARK)


def draw_engraved_divider(px, w, h, y, x_start=14, x_end=305):
    """Draws an engraved burnished brass divider at rows y..y+1 with center filigree diamond."""
    mid = (x_start + x_end) // 2
    for x in range(x_start, x_end + 1):
        dist_edge = min(x - x_start, x_end - x)
        dist_mid = abs(x - mid)
        if dist_edge < 4:
            col_top = C_BRASS_DARK if dist_edge == 0 else C_BRASS_MID
            col_bot = C_PARCH_LIGHT
        elif dist_mid <= 6:
            col_top = C_BRASS_HI if dist_mid <= 2 else C_BRASS
            col_bot = C_BRASS_DARK
        else:
            col_top = C_BRASS if (dist_edge < 24 or dist_mid < 24) else C_BRASS_MID
            col_bot = C_PARCH_LIGHT
        set_px(px, w, h, x, y, col_top)
        set_px(px, w, h, x, y + 1, col_bot)

    # Endcap studs
    for ex in (x_start, x_end):
        set_px(px, w, h, ex, y - 1, C_BRASS_DARK)
        set_px(px, w, h, ex, y, C_BRASS_HI)
        set_px(px, w, h, ex, y + 1, C_BRASS_DARK)

    # Center diamond filigree clasp
    for dy in range(-2, 3):
        span = 2 - abs(dy)
        for dx in range(-span, span + 1):
            col = C_BRASS_SPEC if (dx == 0 and dy == -1) else (C_BRASS_HI if abs(dx) + abs(dy) <= 1 else C_BRASS_DARK)
            set_px(px, w, h, mid + dx, y + dy, col)


def draw_astrolabe_badge(px, w, h, cx, cy):
    """Draws a crisp gilded astrolabe/compass seal badge centered at (cx, cy)."""
    r_outer = 13
    r_inner = 9
    for dy in range(-r_outer - 1, r_outer + 2):
        for dx in range(-r_outer - 1, r_outer + 2):
            x, y = cx + dx, cy + dy
            dist = math.hypot(dx, dy)
            if dist > r_outer + 0.6:
                continue
            if dist > r_outer - 0.5:
                # Drop shadow on bottom-right, outline elsewhere
                set_px(px, w, h, x, y, C_PARCH_SHAD if (dx + dy > 3) else C_BRASS_DARK)
            elif dist >= r_inner + 0.6:
                # Brass ring with top-left highlight & cardinal tick marks
                on_axis = (abs(dx) <= 1 and abs(dy) >= r_inner) or (abs(dy) <= 1 and abs(dx) >= r_inner)
                on_diag = abs(abs(dx) - abs(dy)) <= 1 and dist > r_inner + 1.2
                if on_axis:
                    col = C_BRASS_SPEC if (dx + dy < 0) else C_BRASS_HI
                elif on_diag:
                    col = C_BRASS_DARK
                elif dx + dy < -4:
                    col = C_BRASS_HI
                elif dx + dy > 5:
                    col = C_BRASS_DARK
                else:
                    col = C_BRASS
                set_px(px, w, h, x, y, col)
            elif dist >= r_inner - 0.4:
                set_px(px, w, h, x, y, C_OUTLINE)
            else:
                # Deep astral dial face (midnight indigo-walnut)
                dial_bg = (28, 34, 54, 255)
                dial_ring = (45, 58, 88, 255)
                col = dial_ring if dist > 5.5 else dial_bg
                # 4-point compass star needles
                if (abs(dx) == 0 and abs(dy) <= 8) or (abs(dy) == 0 and abs(dx) <= 8):
                    col = C_BRASS_HI if (dx <= 0 and dy <= 0) else C_BRASS
                elif (abs(dx) <= 1 and abs(dy) <= 5) or (abs(dy) <= 1 and abs(dx) <= 5):
                    col = C_BRASS_MID
                elif abs(dx) == abs(dy) and abs(dx) <= 5:
                    col = C_BRASS_MID
                # Center sapphire/aether gem
                if dist <= 2.6:
                    if dx == -1 and dy == -1:
                        col = (195, 248, 255, 255)
                    elif dist <= 1.5:
                        col = (78, 178, 198, 255)
                    else:
                        col = (34, 96, 118, 255)
                set_px(px, w, h, x, y, col)

    # Top hanging astrolabe ringlet
    for dx in range(-2, 3):
        set_px(px, w, h, cx + dx, cy - r_outer - 2, C_BRASS_DARK if abs(dx) == 2 else C_BRASS_HI)
    set_px(px, w, h, cx, cy - r_outer - 3, C_BRASS)


def generate_personal_space_panel():
    w, h = 320, 236
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()

    # 1. Warm illuminated parchment interior with subtle vignette & paper texture
    for y in range(6, h - 6):
        for x in range(6, w - 6):
            edge_dist = min(x - 6, w - 7 - x, y - 6, h - 7 - y)
            vig = max(0.0, 1.0 - edge_dist / 26.0)
            n = (hash2d(x, y, 7) - 0.5) * 0.06
            # Subtle header band tint above y=50
            header_t = 0.08 if y < 50 else 0.0
            base = lerp_rgb(C_PARCH_LIGHT, C_PARCH_MID, min(1.0, vig * 0.65 + header_t + n))
            if edge_dist <= 1:
                base = lerp_rgb(base, C_PARCH_SHAD, 0.55)
            px[x, y] = base

    # Delicate inner drafting border hairline
    for x in range(12, w - 12):
        set_px(px, w, h, x, 11, C_PARCH_EDGE)
        set_px(px, w, h, x, h - 12, C_PARCH_EDGE)
    for y in range(12, h - 12):
        set_px(px, w, h, 11, y, C_PARCH_EDGE)
        set_px(px, w, h, w - 12, y, C_PARCH_EDGE)

    # 2. Walnut Wood Outer Border (rows/cols 0..5) + Brass Inner Molding (6..7)
    for y in range(h):
        for x in range(w):
            d = min(x, w - 1 - x, y, h - 1 - y)
            if d == 0:
                px[x, y] = C_OUTLINE
            elif 1 <= d <= 5:
                # Directional walnut wood grain
                horiz = (y < 6 or y >= h - 6)
                g_coord = (x // 4 + y * 3) if horiz else (y // 4 + x * 3)
                grain = hash2d(g_coord, y if horiz else x, 19)
                if d == 1 and (x < w - 2 and y < h - 2):
                    col = C_WALNUT_HI if (y < 6 or x < 6) else C_WALNUT_DARK
                elif d == 5:
                    col = C_WALNUT_DARK if (y < 6 or x < 6) else C_WALNUT_HI
                else:
                    if grain < 0.25:
                        col = C_WALNUT_GRAIN_D
                    elif grain > 0.78:
                        col = C_WALNUT_GRAIN_L
                    else:
                        col = C_WALNUT
                px[x, y] = col
            elif d == 6:
                px[x, y] = C_BRASS_MID if (x + y) % 2 == 0 else C_BRASS
            elif d == 7:
                px[x, y] = C_BRASS_DARK

    # 3. Ornate Brass Corner Brackets at all 4 corners
    draw_brass_bracket(px, w, h, 0, 0, 1, 1)
    draw_brass_bracket(px, w, h, w - 1, 0, -1, 1)
    draw_brass_bracket(px, w, h, 0, h - 1, 1, -1)
    draw_brass_bracket(px, w, h, w - 1, h - 1, -1, -1)

    # 4. Engraved Brass Divider Lines at rows 50..51 and 200..201
    draw_engraved_divider(px, w, h, 50, 14, 305)
    draw_engraved_divider(px, w, h, 200, 14, 305)

    # 5. Gilded Astrolabe / Compass Seal in top-right header
    draw_astrolabe_badge(px, w, h, 291, 27)

    out = GUI_DIR / "personal_space_panel.png"
    img.save(out)
    print(f"Saved {out}")


def generate_codex_parchment():
    w, h = 280, 190
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()

    # Palette for Burgundy/Walnut Leather & Crimson Silk Ribbon
    C_LEATHER_OUT = (28, 10, 10, 255)      # #1C0A0A
    C_LEATHER_DARK = (54, 18, 17, 255)     # #361211
    C_LEATHER = (82, 29, 26, 255)          # #521D1A
    C_LEATHER_HI = (112, 44, 36, 255)      # #702C24
    C_STITCH = (205, 165, 92, 255)         # #CDA55C
    C_RIBBON_DARK = (102, 20, 24, 255)
    C_RIBBON = (158, 36, 40, 255)
    C_RIBBON_HI = (196, 62, 62, 255)

    C_VELLUM_HI = (250, 243, 224, 255)     # #FAF3E0
    C_VELLUM = (242, 231, 205, 255)        # #F2E7CD
    C_VELLUM_SHAD = (224, 207, 173, 255)   # #E0CFAD
    C_GUTTER = (128, 102, 70, 255)         # #806646
    C_GUTTER_DEEP = (74, 54, 35, 255)      # #4A3623

    # 1. Stitched Burgundy/Walnut Leather Cover (outer 6px)
    for y in range(h):
        for x in range(w):
            d = min(x, w - 1 - x, y, h - 1 - y)
            if d == 0:
                px[x, y] = C_LEATHER_OUT
            elif 1 <= d <= 5:
                n = hash2d(x, y, 31)
                if d == 1:
                    col = C_LEATHER_HI if (y < 6 or x < 6) else C_LEATHER_DARK
                elif d == 5:
                    col = C_LEATHER_DARK
                elif d == 3 and ((x < 6 or x >= w - 6) and y % 4 in (1, 2) or (y < 6 or y >= h - 6) and x % 4 in (1, 2)):
                    col = C_STITCH
                else:
                    col = C_LEATHER_DARK if n < 0.2 else (C_LEATHER_HI if n > 0.85 else C_LEATHER)
                px[x, y] = col

    # 2. Stacked Page Edges (6..9 around perimeter)
    for y in range(6, h - 6):
        for x in range(6, w - 6):
            d = min(x, w - 1 - x, y, h - 1 - y)
            if 6 <= d <= 9:
                if d == 6:
                    px[x, y] = C_BRASS_DARK
                elif d == 7:
                    px[x, y] = C_PARCH_MID
                elif d == 8:
                    px[x, y] = C_PARCH_SHAD
                else:
                    px[x, y] = C_VELLUM_HI

    # 3. Crisp Warm Cream Vellum Pages + Realistic Spine Shading
    spine_x = w // 2  # 140
    for y in range(10, h - 10):
        for x in range(10, w - 10):
            dx_spine = abs(x - spine_x)
            n = (hash2d(x, y, 47) - 0.5) * 0.04
            if dx_spine == 0:
                col = C_GUTTER_DEEP
            elif dx_spine <= 5:
                t = dx_spine / 5.0
                col = lerp_rgb(C_GUTTER, C_VELLUM_SHAD, t)
            elif dx_spine <= 12:
                t = (dx_spine - 5) / 7.0
                col = lerp_rgb(C_VELLUM_SHAD, C_VELLUM_HI, t)
            else:
                edge_dist = min(x - 10, w - 11 - x, y - 10, h - 11 - y)
                edge_t = max(0.0, 1.0 - edge_dist / 14.0) * 0.35
                col = lerp_rgb(C_VELLUM, C_VELLUM_SHAD, max(0.0, min(1.0, edge_t + n)))
            px[x, y] = col

    # Faint sepia rule on right page under "Record here" button (y=31, x=150..268)
    for x in range(150, 269):
        set_px(px, w, h, x, 31, (206, 186, 146, 255))

    # Delicate corner flourishes inside each page (small, never colliding with text)
    flourish_col = (192, 165, 114, 255)
    flourish_hi = (218, 194, 140, 255)
    for (fx, fy, sx, sy) in (
        (12, 12, 1, 1), (12, h - 13, 1, -1),
        (w - 13, 12, -1, 1), (w - 13, h - 13, -1, -1),
    ):
        for i in range(6):
            set_px(px, w, h, fx + sx * i, fy, flourish_col)
            set_px(px, w, h, fx, fy + sy * i, flourish_col)
        set_px(px, w, h, fx + sx * 2, fy + sy * 2, flourish_hi)
        set_px(px, w, h, fx + sx * 3, fy + sy * 1, flourish_col)
        set_px(px, w, h, fx + sx * 1, fy + sy * 3, flourish_col)

    # 4. Ornate Filigree Brass Corner Clasps on cover
    draw_brass_bracket(px, w, h, 0, 0, 1, 1)
    draw_brass_bracket(px, w, h, w - 1, 0, -1, 1)
    draw_brass_bracket(px, w, h, 0, h - 1, 1, -1)
    draw_brass_bracket(px, w, h, w - 1, h - 1, -1, -1)

    # Brass spine reinforcement plates at top and bottom center
    for sy_base in (0, h - 7):
        for y in range(sy_base, sy_base + 7):
            for x in range(spine_x - 7, spine_x + 8):
                if y == 0 or y == h - 1 or x == spine_x - 7 or x == spine_x + 7:
                    set_px(px, w, h, x, y, C_OUTLINE)
                elif y == sy_base + 1 or x == spine_x - 6:
                    set_px(px, w, h, x, y, C_BRASS_HI)
                elif y == sy_base + 6 or x == spine_x + 6:
                    set_px(px, w, h, x, y, C_BRASS_DARK)
                else:
                    set_px(px, w, h, x, y, C_BRASS if abs(x - spine_x) <= 2 else C_BRASS_MID)

    # 5. Crimson & Gold Silk Bookmark Ribbon (top spine head + forked tail at bottom-left of spine)
    for y in range(1, 6):
        for x in range(spine_x - 2, spine_x + 3):
            set_px(px, w, h, x, y, C_BRASS_HI if abs(x - spine_x) == 2 else C_RIBBON)
    # Forked tail neatly tucked over bottom border at x=122..129, y=177..189 (below left page text)
    for y in range(177, h):
        for x in range(122, 130):
            if y >= h - 4:
                # Swallowtail notch
                notch = 3 - abs(x - 125.5)
                if y - (h - 4) > notch:
                    continue
            if x == 122 or x == 129:
                set_px(px, w, h, x, y, C_RIBBON_DARK)
            elif x == 123 or x == 128:
                set_px(px, w, h, x, y, C_BRASS)
            elif x == 125:
                set_px(px, w, h, x, y, C_RIBBON_HI)
            else:
                set_px(px, w, h, x, y, C_RIBBON)

    out = GUI_DIR / "codex_parchment.png"
    img.save(out)
    print(f"Saved {out}")


# ---------------------------------------------------------------------------
# 8 Crisp 32x32 Miniature Landscape Medallions (environment_*.png)
# ---------------------------------------------------------------------------

def render_medallion(paint_fn):
    """Renders a crisp 32x32 chamfered brass-and-walnut medallion around paint_fn(x, y)."""
    w, h = 32, 32
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 15.5, 15.5

    for y in range(h):
        for x in range(w):
            # Rounded-square / octagonal medallion distance (Chebyshev + Euclidean blend)
            ax = abs(x - cx)
            ay = abs(y - cy)
            d_box = max(ax, ay)
            d_oct = (ax + ay) * 0.7071
            d = max(d_box, d_oct * 0.96)
            if d > 15.3:
                continue
            elif d > 14.3:
                px[x, y] = C_OUTLINE
            elif d > 13.2:
                # Thin burnished brass medallion bezel
                if (x - cx) + (y - cy) < -2:
                    px[x, y] = C_BRASS_HI
                elif (x - cx) + (y - cy) > 4:
                    px[x, y] = C_BRASS_DARK
                else:
                    px[x, y] = C_BRASS
            elif d > 12.4:
                px[x, y] = C_WALNUT_DARK
            else:
                px[x, y] = paint_fn(x, y)
    return img


def paint_superflat(x, y):
    # Deep starlit twilight sky transitioning to warm horizon, terraced grass plateau + celestial sun/star
    dx, dy = x - 16, y - 9
    dist_sun = math.hypot(dx, dy)
    if y < 17:
        sky = lerp_rgb((28, 46, 86, 255), (104, 158, 206, 255), (y - 3) / 14.0)
        if dist_sun <= 2.2:
            return (255, 244, 178, 255)
        if (abs(dx) == 0 and abs(dy) <= 4) or (abs(dy) == 0 and abs(dx) <= 4):
            return (242, 204, 102, 255)
        if (x, y) in ((7, 7), (24, 6), (10, 12), (26, 11)):
            return (235, 240, 255, 255)
        return sky
    if y == 17:
        return (126, 204, 88, 255) if (x % 3 != 0) else (106, 182, 72, 255)
    if 18 <= y <= 20:
        return (94, 166, 62, 255) if (y == 18 or (x + y) % 4 != 0) else (76, 138, 48, 255)
    if 21 <= y <= 24:
        return (134, 92, 58, 255) if (x + y) % 3 != 0 else (112, 74, 46, 255)
    # Stone & bedrock base
    if y >= 25:
        return (128, 132, 138, 255) if (x * 3 + y) % 5 != 0 else (98, 102, 108, 255)
    return (112, 74, 46, 255)


def paint_sky_islands(x, y):
    # Crisp floating islands in azure sky with waterfall & azalea canopy
    sky = lerp_rgb((56, 122, 198, 255), (156, 214, 245, 255), (y - 3) / 26.0)
    # Fluffy pixel cloud at top-left & right
    if (6 <= x <= 11 and y in (7, 8)) or (7 <= x <= 10 and y == 6):
        return (245, 250, 255, 255)
    if (22 <= x <= 26 and y in (10, 11)):
        return (238, 245, 255, 255)

    # Small satellite island on right
    if 21 <= x <= 26 and 16 <= y <= 19:
        if y == 16:
            return (104, 184, 72, 255)
        if abs(x - 23.5) <= 2.5 - (y - 17) * 0.7:
            return (128, 88, 56, 255) if y == 17 else (112, 116, 122, 255)

    # Central floating sanctuary island (x=8..21, y=12..25)
    # Tree on island
    if 10 <= x <= 16 and 9 <= y <= 13:
        if math.hypot(x - 13, (y - 11) * 1.4) <= 3.2:
            if (x, y) in ((12, 10), (15, 11)):
                return (236, 146, 196, 255)  # Flowering azalea blossoms
            return (78, 146, 62, 255) if y < 12 else (56, 116, 44, 255)
    if x == 13 and y in (13, 14):
        return (102, 68, 42, 255)

    # Grass cap y=15..16
    if 8 <= x <= 20 and y == 15:
        return (122, 200, 84, 255) if x != 17 else (92, 192, 245, 255)
    if 8 <= x <= 20 and y == 16:
        return (94, 166, 64, 255) if x != 17 else (72, 174, 238, 255)

    # Waterfall pouring off edge x=17..18
    if x == 17 and 17 <= y <= 27:
        return (165, 228, 255, 255) if (y % 3 == 0) else (82, 182, 242, 255)

    # Tapered stone/dirt underside
    if 17 <= y <= 24:
        half_w = max(0, 6.2 - (y - 16) * 0.8)
        if abs(x - 14) <= half_w:
            if y == 17:
                return (134, 92, 58, 255)
            return (122, 126, 134, 255) if (x + y) % 3 != 0 else (92, 96, 104, 255)

    return sky


def paint_cherry_grove(x, y):
    # Pastel sakura sky, rolling highland ridge, blooming cherry tree + falling petals
    sky = lerp_rgb((118, 152, 212, 255), (244, 206, 222, 255), (y - 3) / 18.0)

    # Highland ridge background
    hill_y = 20 - int(2.5 * math.exp(-((x - 9) / 5.0) ** 2) + 2.0 * math.exp(-((x - 23) / 5.0) ** 2))
    if y >= hill_y:
        if y == hill_y:
            return (242, 168, 202, 255) if (x % 4 == 1) else (112, 184, 82, 255)
        if y <= hill_y + 2:
            return (88, 156, 62, 255)
        return (122, 84, 56, 255) if y < 25 else (98, 98, 106, 255)

    # Cherry trunk & boughs
    if (x in (15, 16) and 14 <= y <= 21) or (y == 14 and 13 <= x <= 18) or (x == 12 and y == 13):
        return (72, 42, 46, 255) if x == 15 else (54, 30, 34, 255)

    # Layered cherry blossom canopy (organic dappled clusters, no diagonal striping)
    d1 = math.hypot((x - 15.5) * 0.85, (y - 10.0) * 1.15)
    d2 = math.hypot((x - 11.5), (y - 11.5) * 1.2)
    d3 = math.hypot((x - 19.5), (y - 11.0) * 1.2)
    if d1 <= 5.5 or d2 <= 3.6 or d3 <= 3.8:
        h = hash2d(x, y, 73)
        if y <= 8 or (y <= 10 and h > 0.62):
            return (255, 224, 238, 255)
        elif y >= 13 or d1 > 4.7 or h < 0.16:
            return (206, 102, 148, 255)
        return (242, 156, 196, 255)

    # Drifting petals
    if (x, y) in ((8, 13), (24, 14), (10, 16), (22, 17)):
        return (252, 188, 218, 255)

    return sky


def paint_amethyst_starfall(x, y):
    # Starlit indigo-violet night sky with falling stars + calcite ridge + glowing amethyst spires
    sky = lerp_rgb((18, 16, 46, 255), (58, 38, 92, 255), (y - 3) / 18.0)

    # Falling star streak
    if (x, y) in ((23, 6), (22, 7), (21, 8)):
        return (255, 238, 156, 255) if x == 21 else (188, 168, 236, 255)
    if (x, y) in ((8, 7), (14, 5), (25, 11), (10, 12)):
        return (246, 238, 255, 255)

    # Calcite & amethyst mountain silhouette
    peak_h = 22 - int(8 * math.exp(-((x - 16) / 3.0) ** 2) + 5 * math.exp(-((x - 9) / 2.2) ** 2) + 5 * math.exp(-((x - 23) / 2.2) ** 2))
    # Main crystal spire facet shading
    if y >= peak_h:
        if x < 16 and abs(x - 16) <= 4 and y < 22:
            return (198, 132, 248, 255) if (x + y) % 3 != 0 else (238, 206, 255, 255)
        if x >= 16 and abs(x - 16) <= 4 and y < 22:
            return (126, 62, 188, 255)
        if abs(x - 9) <= 2 and y < 22:
            return (182, 112, 236, 255) if x <= 9 else (112, 52, 172, 255)
        if abs(x - 23) <= 2 and y < 22:
            return (176, 108, 230, 255) if x <= 23 else (106, 48, 164, 255)
        # Calcite snow-white crust on ridge
        if y <= peak_h + 1 or y in (21, 22):
            return (232, 228, 220, 255)
        return (74, 48, 112, 255) if y < 26 else (46, 44, 62, 255)

    return sky


def paint_aurora_glacier(x, y):
    # Dark arctic sky with shimmering emerald-cyan-violet aurora ribbon over jagged packed-ice peaks
    sky = lerp_rgb((14, 24, 48, 255), (30, 56, 94, 255), (y - 3) / 18.0)

    # Aurora ribbon wave across upper sky
    aur_y = 8.5 + 2.0 * math.sin(x * 0.35)
    if abs(y - aur_y) <= 2.2:
        if abs(y - aur_y) <= 0.9:
            sky = (92, 242, 192, 255)
        elif y < aur_y:
            sky = (118, 142, 232, 255)
        else:
            sky = (62, 198, 178, 255)

    # 3 sharp ice spikes
    s1 = 21 - max(0, 9 - abs(x - 9) * 3)
    s2 = 21 - max(0, 12 - abs(x - 16) * 3)
    s3 = 21 - max(0, 8 - abs(x - 23) * 3)
    top_y = min(s1, s2, s3)

    if y >= top_y:
        if y == top_y and y <= 19:
            return (240, 252, 255, 255)
        if y >= 22:
            if y == 22:
                return (232, 246, 255, 255)
            return (86, 154, 228, 255) if (x + y) % 4 != 0 else (62, 122, 198, 255)
        # Faceted ice shading (left lit, right shadow)
        is_left_face = (x < 9 and top_y == s1) or (x < 16 and top_y == s2) or (x < 23 and top_y == s3)
        return (162, 224, 255, 255) if is_left_face else (88, 162, 232, 255)

    return sky


def paint_mushroom_wonderland(x, y):
    # Dusk violet-teal sky, rolling mycelium hills, giant red spotted mushroom + small brown mushroom
    sky = lerp_rgb((46, 36, 72, 255), (104, 84, 132, 255), (y - 3) / 18.0)

    # Spores
    if (x, y) in ((8, 9), (23, 8), (25, 14), (7, 15)):
        return (255, 224, 146, 255)

    # Giant red mushroom cap (cx=14, cy=12, rx=7, ry=4.5)
    cap_d = ((x - 14.0) / 6.8) ** 2 + ((y - 12.5) / 4.2) ** 2
    if cap_d <= 1.0 and y <= 14:
        # White toadstool spots
        for sx, sy in ((11, 10), (16, 10), (14, 12), (19, 12)):
            if math.hypot(x - sx, y - sy) <= 1.25:
                return (246, 238, 220, 255)
        return (226, 68, 62, 255) if y <= 11 else (178, 42, 40, 255)

    # Stem
    if 13 <= x <= 15 and 15 <= y <= 21:
        return (236, 224, 202, 255) if x == 13 else (196, 178, 152, 255)

    # Small companion mushroom on right (x=21..25)
    if 21 <= x <= 25 and y in (18, 19):
        if y == 18 and 22 <= x <= 24:
            return (178, 122, 74, 255)
        if y == 19:
            return (148, 96, 56, 255)
    if x == 23 and y in (20, 21):
        return (226, 212, 188, 255)

    # Mycelium & moss rolling terrain
    hill_y = 21 + int(1.5 * math.sin(x * 0.3))
    if y >= hill_y:
        if y == hill_y:
            return (148, 122, 146, 255) if x % 3 != 0 else (112, 152, 74, 255)
        if y <= hill_y + 2:
            return (122, 102, 122, 255)
        return (94, 78, 68, 255)

    return sky


def paint_obsidian_forge(x, y):
    # Smoky ember sky, obsidian caldera walls, glowing magma basin + volcanic pillar
    sky = lerp_rgb((36, 16, 18, 255), (108, 38, 22, 255), (y - 3) / 18.0)

    # Floating embers
    if (x, y) in ((10, 8), (21, 7), (15, 6), (24, 11), (8, 12)):
        return (255, 196, 78, 255)

    # Left & right obsidian caldera rims + central forge pillar
    rim_l = 14 + abs(x - 8) * 0.7
    rim_r = 14 + abs(x - 23) * 0.7
    caldera_y = min(rim_l, rim_r, 21.0)
    # Central obsidian spire at x=15..17
    if 15 <= x <= 16 and 11 <= y <= 22:
        if y in (13, 17) and x == 15:
            return (152, 68, 214, 255)  # Crying obsidian vein
        return (56, 38, 86, 255) if x == 15 else (32, 22, 48, 255)

    # Lava pool inside caldera at y=21..24, x=11..20
    if 11 <= x <= 20 and 21 <= y <= 23 and y >= caldera_y:
        if y == 21 or (x + y) % 4 == 0:
            return (255, 226, 98, 255)
        return (242, 114, 28, 255)

    if y >= caldera_y:
        if y <= caldera_y + 1:
            return (78, 68, 84, 255)
        # Organic cracked basalt with a couple of glowing magma fissures
        if (x, y) in ((10, 25), (11, 26), (19, 25), (20, 25), (15, 26)):
            return (224, 96, 28, 255)
        rock_n = hash2d(x, y, 91)
        if y < 25:
            return (54, 46, 58, 255) if rock_n > 0.65 else (42, 36, 46, 255)
        return (34, 28, 40, 255) if rock_n > 0.4 else (24, 19, 30, 255)

    return sky


def paint_sculk_sanctum(x, y):
    # Deep dark cavern void, stepped deepslate obelisk + glowing teal-cyan sculk veins & catalyst wisps
    bg = lerp_rgb((10, 14, 22, 255), (20, 32, 44, 255), (y - 3) / 26.0)

    # Bioluminescent sculk wisps
    if (x, y) in ((8, 8), (23, 9), (10, 14), (24, 15)):
        return (84, 242, 228, 255)

    # Central deepslate obelisk (x=13..18, y=7..21)
    if 13 <= x <= 18 and 7 <= y <= 21:
        if (y == 7 and x in (13, 18)):
            pass
        else:
            # Glowing cyan rune core in obelisk
            if x in (15, 16) and 11 <= y <= 14:
                return (124, 252, 242, 255) if y in (12, 13) else (36, 178, 176, 255)
            return (64, 70, 82, 255) if x <= 14 else (38, 42, 52, 255)

    # Stepped sanctum terraces
    dx = abs(x - 15.5)
    terrace_y = 21 if dx < 4.5 else (23 if dx < 8.5 else 25)
    if y >= terrace_y:
        if y == terrace_y:
            # Sculk teal carpet & highlights
            return (42, 212, 198, 255) if (x % 3 != 0) else (22, 118, 128, 255)
        if y == terrace_y + 1:
            return (16, 48, 62, 255)
        return (42, 46, 56, 255) if (x + y) % 4 != 0 else (26, 28, 36, 255)

    return bg


ENVIRONMENT_PAINTERS = {
    "superflat": paint_superflat,
    "sky_islands": paint_sky_islands,
    "cherry_grove": paint_cherry_grove,
    "amethyst_starfall": paint_amethyst_starfall,
    "aurora_glacier": paint_aurora_glacier,
    "mushroom_wonderland": paint_mushroom_wonderland,
    "obsidian_forge": paint_obsidian_forge,
    "sculk_sanctum": paint_sculk_sanctum,
}


def generate_environments():
    for env_id, fn in ENVIRONMENT_PAINTERS.items():
        img = render_medallion(fn)
        out = GUI_DIR / f"environment_{env_id}.png"
        img.save(out)
        print(f"Saved {out}")


if __name__ == "__main__":
    GUI_DIR.mkdir(parents=True, exist_ok=True)
    generate_personal_space_panel()
    generate_codex_parchment()
    generate_environments()
