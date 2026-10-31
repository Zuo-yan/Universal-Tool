from pathlib import Path
import math
from PIL import Image, ImageDraw

root = Path(r"A:\Game\Code\Minecraft-Mod\万能工具")
out_tex = root / "src/main/resources/assets/universal_tool/textures/gui/container/polymerizer_gui.png"
preview_dir = root / "art/previews"
out_preview = preview_dir / "polymerizer_gui_pixel_preview.png"

W, H = 256, 256
im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(im)

C_BLACK = (10, 11, 14, 255)
C_DARK_BG = (18, 20, 26, 255)
C_PANEL_DEEP = (28, 30, 38, 255)
C_PANEL_MID = (38, 42, 52, 255)
C_PANEL_LIGHT = (56, 62, 76, 255)
C_PANEL_HL = (84, 92, 110, 255)
C_PANEL_TOP_HL = (118, 128, 150, 255)

C_GOLD_DEEP = (64, 44, 18, 255)
C_GOLD_DARK = (102, 74, 28, 255)
C_GOLD_MID = (168, 124, 46, 255)
C_GOLD_MAIN = (218, 172, 68, 255)
C_GOLD_LIGHT = (248, 214, 112, 255)
C_GOLD_HL = (255, 244, 178, 255)

C_CYAN_DEEP = (8, 36, 42, 255)
C_CYAN_DARK = (16, 72, 80, 255)
C_CYAN_MID = (28, 126, 134, 255)
C_CYAN_MAIN = (48, 194, 188, 255)
C_CYAN_LIGHT = (82, 246, 234, 255)
C_CYAN_HL = (220, 255, 252, 255)

C_SLOT_DEEP = (14, 15, 20, 255)
C_SLOT_BG = (22, 24, 30, 255)
C_SLOT_HL = (62, 68, 84, 255)

PW, PH = 192, 224

# 1. Base Panel Shell
for y in range(4, PH):
    for x in range(4, PW - 4):
        im.putpixel((x, y), C_PANEL_MID)

d.rectangle((4, 4, PW - 5, PH - 1), outline=C_BLACK)
d.rectangle((5, 5, PW - 6, PH - 2), outline=C_PANEL_LIGHT)
d.rectangle((6, 6, PW - 7, PH - 3), outline=C_PANEL_DEEP)

# Left & Right architectural columns
for y in range(8, PH - 8):
    for x in range(7, 14):
        col = C_PANEL_LIGHT if x in (7, 13) else C_PANEL_DEEP if x == 10 else C_PANEL_MID
        im.putpixel((x, y), col)
    for x in range(PW - 14, PW - 7):
        col = C_PANEL_LIGHT if x in (PW - 14, PW - 8) else C_PANEL_DEEP if x == PW - 11 else C_PANEL_MID
        im.putpixel((x, y), col)

for ry in range(24, PH - 24, 22):
    for rx in (10, PW - 11):
        d.rectangle((rx - 1, ry - 1, rx + 1, ry + 1), fill=C_GOLD_MID, outline=C_GOLD_DEEP)
        im.putpixel((rx, ry), C_GOLD_HL)

def draw_sculpted_corner(x0, y0, flip_x, flip_y):
    for dx in range(12):
        for dy in range(12):
            if dx + dy < 5 or (dx > 7 and dy > 7):
                continue
            col = C_GOLD_MAIN
            if dx + dy == 5:
                col = C_GOLD_HL
            elif dx >= 10 or dy >= 10:
                col = C_GOLD_DEEP
            elif dx <= 3 or dy <= 3:
                col = C_GOLD_LIGHT
            elif (dx, dy) in ((4, 4), (5, 5), (6, 6)):
                col = C_GOLD_HL
            else:
                col = C_GOLD_MID
            px = x0 + (11 - dx if flip_x else dx)
            py = y0 + (11 - dy if flip_y else dy)
            im.putpixel((px, py), col)

draw_sculpted_corner(4, 4, False, False)
draw_sculpted_corner(PW - 16, 4, True, False)
draw_sculpted_corner(4, PH - 16, False, True)
draw_sculpted_corner(PW - 16, PH - 16, True, True)

def draw_prominent_star(cx, cy):
    d.polygon([(cx, cy - 8), (cx + 6, cy), (cx, cy + 8), (cx - 6, cy)], fill=C_GOLD_MID, outline=C_BLACK)
    d.polygon([(cx, cy - 6), (cx + 4, cy), (cx, cy + 6), (cx - 4, cy)], fill=C_GOLD_MAIN)
    d.line((cx, cy - 6, cx, cy + 6), fill=C_GOLD_HL)
    d.line((cx - 4, cy, cx + 4, cy), fill=C_GOLD_HL)
    d.rectangle((cx - 2, cy - 2, cx + 2, cy + 2), fill=C_CYAN_MAIN, outline=C_CYAN_DEEP)
    d.rectangle((cx - 1, cy - 1, cx + 1, cy + 1), fill=C_CYAN_LIGHT)
    im.putpixel((cx, cy), C_CYAN_HL)
    for gdx, gdy in [(-3, 0), (3, 0), (0, -3), (0, 3)]:
        if 0 <= cx + gdx < W and 0 <= cy + gdy < H:
            im.putpixel((cx + gdx, cy + gdy), C_CYAN_MID)

draw_prominent_star(3, 70)
draw_prominent_star(PW - 4, 70)
draw_prominent_star(10, 30)
draw_prominent_star(PW - 11, 30)

# 2. Top Header Plaque (Expanded vertical depth for two lines of text: Y=3..24)
banner_poly = [
    (38, 3), (153, 3),
    (160, 10), (160, 17),
    (153, 24), (38, 24),
    (31, 17), (31, 10)
]
d.polygon(banner_poly, fill=C_BLACK)
d.polygon([(p[0], p[1] + 1) for p in banner_poly], outline=C_GOLD_MAIN)
d.polygon([(p[0], p[1] + 2) for p in banner_poly], outline=C_GOLD_DARK)
d.rectangle((38, 6, 153, 21), fill=(20, 22, 28, 255))
d.line((38, 6, 153, 6), fill=C_GOLD_HL)
d.line((38, 21, 153, 21), fill=C_GOLD_DEEP)

for sx in (35, 156):
    d.rectangle((sx - 1, 12, sx + 1, 15), fill=C_CYAN_LIGHT, outline=C_CYAN_DEEP)
    im.putpixel((sx, 13), C_CYAN_HL)

# 3. Dial Center and Radial Geometry
CENTER_X, CENTER_Y = 96, 70
RADIUS = 44

for y in range(CENTER_Y - 58, CENTER_Y + 59):
    for x in range(CENTER_X - 58, CENTER_X + 59):
        dist_sq = (x - CENTER_X)**2 + (y - CENTER_Y)**2
        if 54**2 <= dist_sq <= 58**2:
            im.putpixel((x, y), C_PANEL_DEEP)
        elif dist_sq < 54**2:
            if dist_sq > 40**2:
                im.putpixel((x, y), (32, 35, 44, 255))

for a in range(360):
    rad = math.radians(a)
    px = int(round(CENTER_X + 55 * math.cos(rad)))
    py = int(round(CENTER_Y + 55 * math.sin(rad)))
    if 5 < px < PW - 6 and 5 < py < 128:
        im.putpixel((px, py), C_GOLD_DARK if a % 15 != 0 else C_GOLD_MAIN)

for a in range(0, 360, 30):
    rad = math.radians(a)
    col = C_GOLD_HL if a % 90 == 0 else C_GOLD_LIGHT
    for r in range(51, 56):
        px = int(round(CENTER_X + r * math.cos(rad)))
        py = int(round(CENTER_Y + r * math.sin(rad)))
        if 5 < px < PW - 6 and 5 < py < 128:
            im.putpixel((px, py), col)

# 4. Cyan Energy Conduit Lines
for i in range(12):
    angle = i * 30.0 - 90.0
    rad = math.radians(angle)
    for r in range(15, 36):
        px = int(round(CENTER_X + r * math.cos(rad)))
        py = int(round(CENTER_Y + r * math.sin(rad)))
        im.putpixel((px, py), C_CYAN_MAIN)
        if r % 2 == 0:
            im.putpixel((px, py), C_CYAN_LIGHT)
    vx = int(round(CENTER_X + 25 * math.cos(rad)))
    vy = int(round(CENTER_Y + 25 * math.sin(rad)))
    d.rectangle((vx - 1, vy - 1, vx + 1, vy + 1), fill=C_GOLD_MAIN, outline=C_GOLD_DEEP)
    im.putpixel((vx, vy), C_GOLD_HL)

# 5. Slot function
def draw_crafting_slot(x0, y0, is_center=False):
    fx0, fy0 = x0 - 1, y0 - 1
    fx1, fy1 = x0 + 18, y0 + 18
    d.rectangle((fx0, fy0, fx1, fy1), outline=C_BLACK)
    d.line((fx0 + 1, fy0 + 1, fx1 - 1, fy0 + 1), fill=C_PANEL_HL if not is_center else C_GOLD_LIGHT)
    d.line((fx0 + 1, fy0 + 1, fx0 + 1, fy1 - 1), fill=C_PANEL_HL if not is_center else C_GOLD_LIGHT)
    d.line((fx0 + 1, fy1 - 1, fx1 - 1, fy1 - 1), fill=C_PANEL_DEEP if not is_center else C_GOLD_DEEP)
    d.line((fx1 - 1, fy0 + 1, fx1 - 1, fy1 - 1), fill=C_PANEL_DEEP if not is_center else C_GOLD_DEEP)
    d.rectangle((x0, y0, x0 + 17, y0 + 17), fill=C_SLOT_BG)
    d.line((x0, y0, x0 + 17, y0), fill=C_SLOT_DEEP)
    d.line((x0, y0, x0, y0 + 17), fill=C_SLOT_DEEP)
    d.line((x0 + 1, y0 + 17, x0 + 17, y0 + 17), fill=C_SLOT_HL)
    d.line((x0 + 17, y0 + 1, x0 + 17, y0 + 17), fill=C_SLOT_HL)

    corner_col = C_GOLD_MAIN if not is_center else C_GOLD_HL
    for (cx, cy) in [(fx0, fy0), (fx1, fy0), (fx0, fy1), (fx1, fy1)]:
        im.putpixel((cx, cy), corner_col)

for i in range(12):
    angle = i * 30.0 - 90.0
    rad = math.radians(angle)
    sx = int(round(CENTER_X + RADIUS * math.cos(rad))) - 9
    sy = int(round(CENTER_Y + RADIUS * math.sin(rad))) - 9
    draw_crafting_slot(sx, sy, is_center=False)

# 6. Central Core & Output Slot
for a in range(360):
    rad = math.radians(a)
    for r in (13, 14, 15):
        col = C_CYAN_HL if r == 14 and a % 20 < 10 else C_CYAN_LIGHT if r == 14 else C_CYAN_MAIN
        px = int(round(CENTER_X + r * math.cos(rad)))
        py = int(round(CENTER_Y + r * math.sin(rad)))
        im.putpixel((px, py), col)

for a in (0, 90, 180, 270):
    rad = math.radians(a)
    px = int(round(CENTER_X + 16 * math.cos(rad)))
    py = int(round(CENTER_Y + 16 * math.sin(rad)))
    d.rectangle((px - 2, py - 2, px + 2, py + 2), fill=C_GOLD_MAIN, outline=C_GOLD_DEEP)
    im.putpixel((px, py), C_GOLD_HL)

draw_crafting_slot(CENTER_X - 9, CENTER_Y - 9, is_center=True)
d.rectangle((CENTER_X - 11, CENTER_Y - 11, CENTER_X + 10, CENTER_Y + 10), outline=C_GOLD_DEEP)
d.rectangle((CENTER_X - 10, CENTER_Y - 10, CENTER_X + 9, CENTER_Y + 9), outline=C_GOLD_MAIN)
im.putpixel((CENTER_X - 10, CENTER_Y - 10), C_GOLD_HL)
im.putpixel((CENTER_X + 9, CENTER_Y - 10), C_GOLD_HL)
im.putpixel((CENTER_X - 10, CENTER_Y + 9), C_GOLD_HL)
im.putpixel((CENTER_X + 9, CENTER_Y + 9), C_GOLD_HL)

# 7. Start Crafting Pill Capsule Button
BTN_X0, BTN_Y0 = 72, 82
BTN_W, BTN_H = 48, 12

def render_pill_button_clean(x0, y0, state="idle"):
    if state == "idle":
        bg = (14, 42, 46, 255)
        border = C_CYAN_MAIN
        hl = C_CYAN_LIGHT
    elif state == "hover":
        bg = (24, 82, 88, 255)
        border = C_CYAN_LIGHT
        hl = C_CYAN_HL
    else:
        bg = (38, 130, 138, 255)
        border = C_CYAN_HL
        hl = (255, 255, 255, 255)

    d.rectangle((x0 + 2, y0, x0 + BTN_W - 3, y0 + BTN_H - 1), fill=bg, outline=C_BLACK)
    d.line((x0 + 1, y0 + 2, x0 + 1, y0 + BTN_H - 3), fill=border)
    d.line((x0 + BTN_W - 2, y0 + 2, x0 + BTN_W - 2, y0 + BTN_H - 3), fill=border)
    d.rectangle((x0 + 2, y0 + 1, x0 + BTN_W - 3, y0 + BTN_H - 2), outline=border)
    d.line((x0 + 3, y0 + 1, x0 + BTN_W - 4, y0 + 1), fill=hl)
    d.point((x0 + 2, y0 + BTN_H//2), fill=C_GOLD_MAIN)
    d.point((x0 + BTN_W - 3, y0 + BTN_H//2), fill=C_GOLD_MAIN)

render_pill_button_clean(BTN_X0, BTN_Y0, "idle")

# 8. Lower Inventory Section
d.line((12, 126, PW - 13, 126), fill=C_BLACK)
d.line((12, 127, PW - 13, 127), fill=C_PANEL_LIGHT)
d.line((12, 128, PW - 13, 128), fill=C_PANEL_TOP_HL)
d.line((12, 129, PW - 13, 129), fill=C_PANEL_DEEP)

d.polygon([(CENTER_X, 124), (CENTER_X + 4, 127), (CENTER_X, 130), (CENTER_X - 4, 127)],
          fill=C_GOLD_MAIN, outline=C_BLACK)
d.polygon([(CENTER_X, 125), (CENTER_X + 2, 127), (CENTER_X, 129), (CENTER_X - 2, 127)],
          fill=C_GOLD_HL)
im.putpixel((CENTER_X, 127), C_CYAN_LIGHT)

d.rectangle((13, 132, PW - 14, 218), fill=C_DARK_BG, outline=C_BLACK)
d.rectangle((14, 133, PW - 15, 217), outline=(24, 26, 34, 255))

for row in range(3):
    for col in range(9):
        sx = 15 + col * 18
        sy = 138 + row * 18
        d.rectangle((sx, sy, sx + 17, sy + 17), fill=C_SLOT_BG)
        d.line((sx, sy, sx + 17, sy), fill=C_SLOT_DEEP)
        d.line((sx, sy, sx, sy + 17), fill=C_SLOT_DEEP)
        d.line((sx + 1, sy + 17, sx + 17, sy + 17), fill=C_SLOT_HL)
        d.line((sx + 17, sy + 1, sx + 17, sy + 17), fill=C_SLOT_HL)

d.line((14, 194, PW - 15, 194), fill=C_BLACK)
d.line((14, 195, PW - 15, 195), fill=C_PANEL_MID)
d.line((14, 196, PW - 15, 196), fill=C_DARK_BG)

for col in range(9):
    sx = 15 + col * 18
    sy = 198
    d.rectangle((sx, sy, sx + 17, sy + 17), fill=C_SLOT_BG)
    d.line((sx, sy, sx + 17, sy), fill=C_SLOT_DEEP)
    d.line((sx, sy, sx, sy + 17), fill=C_SLOT_DEEP)
    d.line((sx + 1, sy + 17, sx + 17, sy + 17), fill=C_SLOT_HL)
    d.line((sx + 17, sy + 1, sx + 17, sy + 17), fill=C_SLOT_HL)

render_pill_button_clean(196, 0, "idle")
render_pill_button_clean(196, 16, "hover")
render_pill_button_clean(196, 32, "active")

for idx, (fx, fy) in enumerate([(196, 48), (216, 48), (196, 68), (216, 68)]):
    base_a = idx * 90
    for a in range(base_a, base_a + 270, 10):
        rad = math.radians(a)
        r = 7.5
        px = int(round(fx + 9 + r * math.cos(rad)))
        py = int(round(fy + 9 + r * math.sin(rad)))
        col = C_CYAN_HL if a % 30 == 0 else C_CYAN_LIGHT if a % 20 == 0 else C_CYAN_MAIN
        im.putpixel((px, py), col)

im.save(out_tex, "PNG")
preview = im.resize((1024, 1024), Image.Resampling.NEAREST)
preview.save(out_preview, "PNG")
print(f"Refined texture generated: {out_tex}")
