import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageEnhance

ROOT = Path(__file__).resolve().parents[1]

def generate_klein_terminal_gui(output_path: Path):
    W, H = 512, 512
    PW, PH = 194, 236
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)

    # 1. Base Cosmic Nebula Gradient (194x236)
    for y in range(PH):
        for x in range(PW):
            dx = (x - 97) / 97.0
            dy = (y - 118) / 118.0
            d = math.sqrt(dx * dx + dy * dy)
            r = int(6 + 2 * math.sin(x * 0.04) - d * 2)
            g = int(12 + 4 * math.cos(y * 0.04) - d * 3)
            b = int(18 + 5 * math.sin((x + y) * 0.03) - d * 4)
            im.putpixel((x, y), (max(3, min(255, r)), max(6, min(255, g)), max(10, min(255, b)), 255))

    # Astrolabe celestial rings
    chart = Image.new('RGBA', (PW, PH), (0, 0, 0, 0))
    cdraw = ImageDraw.Draw(chart)
    for r in [30, 60, 95, 140, 190]:
        cdraw.ellipse((-10 - r, -10 - r, -10 + r, -10 + r), outline=(38, 92, 102, 55), width=1)
        for deg in range(0, 95, 10):
            rad = math.radians(deg)
            tx = -10 + r * math.cos(rad)
            ty = -10 + r * math.sin(rad)
            if 0 <= tx < PW and 0 <= ty < PH:
                cdraw.point((tx, ty), fill=(55, 130, 140, 75))

    for r in [40, 80, 125, 175]:
        cdraw.ellipse((PW + 10 - r, PH + 10 - r, PW + 10 + r, PH + 10 + r), outline=(35, 85, 95, 50), width=1)

    im.alpha_composite(chart, (0, 0))

    # 2. Outer antique brass double border (194x236)
    draw.rectangle((0, 0, PW - 1, PH - 1), outline=(4, 8, 12, 255))
    draw.rectangle((1, 1, PW - 2, PH - 2), outline=(90, 70, 35, 255))
    draw.rectangle((2, 2, PW - 3, PH - 3), outline=(14, 24, 28, 255))
    draw.rectangle((3, 3, PW - 4, PH - 4), outline=(184, 155, 92, 255))
    draw.rectangle((4, 4, PW - 5, PH - 5), outline=(5, 11, 15, 255))

    def draw_corner_star(cx, cy):
        gold = (222, 185, 122, 255)
        bright = (255, 245, 200, 255)
        cyan = (112, 219, 230, 255)
        im.putpixel((cx, cy), bright)
        for d in range(1, 3):
            im.putpixel((cx + d, cy), gold)
            im.putpixel((cx - d, cy), gold)
            im.putpixel((cx, cy + d), gold)
            im.putpixel((cx, cy - d), gold)
        im.putpixel((cx + 1, cy + 1), cyan)
        im.putpixel((cx - 1, cy + 1), cyan)
        im.putpixel((cx + 1, cy - 1), cyan)
        im.putpixel((cx - 1, cy - 1), cyan)

    draw_corner_star(4, 4)
    draw_corner_star(PW - 5, 4)
    draw_corner_star(4, PH - 5)
    draw_corner_star(PW - 5, PH - 5)

    # 3. Spacious Header Bar (y=5..22, height=18):
    # Bottle emblem at (7, 6..20) (15x15 px)
    draw.ellipse((7, 6, 21, 20), outline=(140, 115, 65, 255), width=1)
    im.putpixel((14, 5), (222, 185, 122, 255))
    im.putpixel((14, 21), (222, 185, 122, 255))
    im.putpixel((6, 13), (222, 185, 122, 255))
    im.putpixel((22, 13), (222, 185, 122, 255))
    im.putpixel((14, 8), (180, 140, 80, 255))   # cork
    draw.rectangle((13, 9, 15, 10), fill=(40, 100, 115, 255)) # neck
    draw.ellipse((10, 11, 18, 18), fill=(10, 30, 38, 255), outline=(50, 130, 145, 255))
    im.putpixel((14, 14), (200, 255, 255, 255))
    im.putpixel((13, 15), (112, 219, 230, 255))
    im.putpixel((15, 15), (112, 219, 230, 255))
    im.putpixel((14, 16), (42, 150, 165, 255))

    # Close button at (178, 7) to (188, 17) (11x11 px)
    draw.rectangle((178, 7, 188, 17), fill=(10, 20, 25, 255), outline=(184, 155, 92, 255))
    for d in range(0, 5):
        im.putpixel((181 + d, 10 + d), (196, 85, 77, 255))
        im.putpixel((181 + d, 14 - d), (196, 85, 77, 255))
        im.putpixel((181 + d, 11 + d if d < 2 else 9 + d), (230, 120, 110, 255))

    # 4. Search & Sort Row (y=24..38, height=15):
    # Search pill (8, 24) to (122, 38) -> w=115, h=15
    draw.rounded_rectangle((8, 24, 122, 38), radius=3, fill=(6, 14, 18, 255), outline=(24, 62, 70, 255))
    draw.line((10, 25, 120, 25), fill=(3, 7, 9, 255))
    draw.line((10, 37, 120, 37), fill=(12, 32, 38, 255))
    # Magnifying glass at (11, 27)
    draw.ellipse((11, 27, 16, 32), outline=(112, 219, 230, 255), width=1)
    draw.line((15, 31, 18, 34), fill=(112, 219, 230, 255), width=1)

    # Sort Button: (126, 24) to (162, 38) -> w=37, h=15
    draw.rectangle((126, 24, 162, 38), fill=(10, 22, 28, 255), outline=(140, 115, 65, 255))
    draw.line((127, 25, 161, 25), fill=(190, 160, 95, 200))
    draw.line((127, 37, 161, 37), fill=(40, 32, 16, 255))

    # Direction Button: (166, 24) to (186, 38) -> w=21, h=15
    draw.rectangle((166, 24, 186, 38), fill=(10, 22, 28, 255), outline=(36, 80, 90, 255))
    draw.line((167, 25, 185, 25), fill=(70, 160, 180, 180))
    draw.line((167, 37, 185, 37), fill=(15, 38, 44, 255))

    # 5. Divider Line at y=42
    def draw_divider(y_pos):
        gold = (184, 155, 92, 255)
        gold_shadow = (70, 55, 28, 255)
        bright = (255, 245, 200, 255)
        draw.line((8, y_pos, 186, y_pos), fill=gold)
        draw.line((8, y_pos + 1, 186, y_pos + 1), fill=gold_shadow)
        im.putpixel((PW // 2, y_pos), bright)
        im.putpixel((PW // 2 - 1, y_pos), gold)
        im.putpixel((PW // 2 + 1, y_pos), gold)
        im.putpixel((PW // 2, y_pos - 1), gold)
        im.putpixel((PW // 2, y_pos + 1), gold)

    draw_divider(42)

    # 6. Slot drawing helper (18x18)
    def draw_slot(sx, sy):
        draw.line((sx, sy, sx + 17, sy), fill=(4, 9, 12, 255))
        draw.line((sx, sy, sx, sy + 17), fill=(4, 9, 12, 255))
        draw.line((sx, sy + 17, sx + 17, sy + 17), fill=(27, 59, 71, 255))
        draw.line((sx + 17, sy, sx + 17, sy + 17), fill=(27, 59, 71, 255))
        draw.rectangle((sx + 1, sy + 1, sx + 16, sy + 16), fill=(8, 19, 24, 255))
        im.putpixel((sx + 2, sy + 2), (18, 45, 52, 255))
        im.putpixel((sx + 15, sy + 2), (18, 45, 52, 255))
        im.putpixel((sx + 2, sy + 15), (18, 45, 52, 255))
        im.putpixel((sx + 15, sy + 15), (25, 65, 75, 255))

    # 5 FULL ROWS WAREHOUSE STORAGE (5 rows x 9 cols at y=46..135, height=90px, 45 slots!)
    for r in range(5):
        for c in range(9):
            draw_slot(8 + c * 18, 46 + r * 18)

    # Scrollbar track at (174, 46, w=10, h=90) -> x: 174..183, y: 46..135
    draw.rectangle((174, 46, 183, 135), fill=(5, 12, 16, 255), outline=(16, 40, 48, 255))
    draw.line((175, 47, 182, 47), fill=(3, 7, 10, 255))
    draw.line((175, 47, 175, 134), fill=(3, 7, 10, 255))
    draw.line((182, 48, 182, 134), fill=(22, 52, 62, 255))
    draw.line((175, 134, 182, 134), fill=(22, 52, 62, 255))

    # Lower divider at y=139
    draw_divider(139)

    # Player Inventory 3 rows x 9 cols at (8, 153) -> 27 slots (y=153..206)
    for r in range(3):
        for c in range(9):
            draw_slot(8 + c * 18, 153 + r * 18)

    # Hotbar 1 row x 9 cols at (8, 212) -> 9 slots (y=212..229)
    for c in range(9):
        draw_slot(8 + c * 18, 212)

    # Right action buttons: five 15x14 sockets (x=172..186, y=153..227).
    for button_index in range(5):
        button_y = 153 + button_index * 15
        draw.rectangle((172, button_y, 186, button_y + 13), fill=(8, 18, 22, 255), outline=(90, 70, 35, 255))
        draw.line((173, button_y + 1, 185, button_y + 1), fill=(140, 110, 55, 180))
        draw.line((173, button_y + 1, 173, button_y + 12), fill=(140, 110, 55, 180))
        draw.line((173, button_y + 12, 185, button_y + 12), fill=(35, 28, 14, 255))
        draw.line((185, button_y + 1, 185, button_y + 12), fill=(35, 28, 14, 255))

    # 7. Atlas elements (x >= 200)
    # Lock frame (200, 0, 18x18)
    draw.rectangle((200, 0, 217, 17), outline=(222, 185, 122, 255))
    draw.rectangle((201, 1, 216, 16), outline=(90, 70, 35, 255))

    # Padlock badge (222, 0, 9x10)
    draw.arc((223, 0, 229, 6), 180, 0, fill=(222, 185, 122, 255), width=1)
    draw.rectangle((222, 4, 230, 9), fill=(184, 155, 92, 255), outline=(90, 70, 35, 255))
    im.putpixel((226, 6), (40, 30, 15, 255))

    # Hover frame (200, 24, 18x18)
    draw.rectangle((200, 24, 217, 41), outline=(112, 219, 230, 255))
    im.putpixel((200, 24), (200, 255, 255, 255))
    im.putpixel((217, 24), (200, 255, 255, 255))
    im.putpixel((200, 41), (200, 255, 255, 255))
    im.putpixel((217, 41), (200, 255, 255, 255))

    # Standard Slot Frame in atlas (268, 24, 18x18)
    draw_slot(268, 24)

    # Scrollbar thumb (200, 48, 8x92) - Top cap at 48..51, Body at 52..135, Bottom cap at 136..139
    for y in range(48, 140):
        for x in range(200, 208):
            rx = x - 200
            ry = y - 48
            if (rx in (0, 7) and ry in (0, 91)) or (rx in (0, 7) and ry in (1, 90)):
                continue
            if ry == 0:
                col = (210, 255, 255, 255) if 2 <= rx <= 5 else (140, 230, 240, 255)
            elif ry == 1:
                col = (180, 255, 255, 255) if 1 <= rx <= 6 else (42, 115, 127, 255)
            elif ry == 91:
                col = (18, 55, 65, 255)
            elif ry == 90:
                col = (28, 80, 92, 255)
            elif rx == 0:
                col = (70, 180, 195, 255)
            elif rx == 7:
                col = (28, 75, 88, 255)
            elif ry in (44, 46, 48) and 2 <= rx <= 5:
                col = (25, 70, 80, 255)
            elif ry in (45, 47, 49) and 2 <= rx <= 5:
                col = (180, 255, 255, 255)
            elif rx in (1, 2):
                col = (140, 235, 245, 255)
            else:
                col = (112, 219, 230, 255)
            im.putpixel((x, y), col)

    # Pristine Minecraft Pixel Art Arrow (15x11) at (216, 48)
    arrow_matrix = [
        "..........#....",
        "..........##...",
        "..........#*#..",
        "..........#**#.",
        "###########***#",
        "#*************#",
        "###########***#",
        "..........#**#.",
        "..........#*#..",
        "..........##...",
        "..........#....",
    ]
    for py, row in enumerate(arrow_matrix):
        for px, ch in enumerate(row):
            if ch == "#":
                col = (18, 52, 60, 255) if py >= 6 else (45, 120, 135, 255)
                im.putpixel((216 + px, 48 + py), col)
            elif ch == "*":
                if py in (4, 5) and px < 11:
                    col = (180, 255, 255, 255) if py == 4 else (112, 219, 230, 255)
                elif py == 5:
                    col = (200, 255, 255, 255)
                elif py < 5:
                    col = (140, 235, 245, 255)
                else:
                    col = (70, 185, 200, 255)
                im.putpixel((216 + px, 48 + py), col)

    # Antique Gold Plus Sign '+' (7x7) at (236, 48)
    plus_matrix = [
        "..#.#..",
        "..#*#..",
        "###*###",
        "#*****#",
        "###*###",
        "..#*#..",
        "..#.#..",
    ]
    for py, row in enumerate(plus_matrix):
        for px, ch in enumerate(row):
            if ch == "#":
                col = (140, 110, 55, 255) if py < 4 else (70, 50, 20, 255)
                im.putpixel((236 + px, 48 + py), col)
            elif ch == "*":
                col = (255, 245, 200, 255) if py in (2, 3) else (222, 185, 122, 255)
                im.putpixel((236 + px, 48 + py), col)

    # Blazing Furnace Flame (12x14) at (248, 48)
    flame_matrix = [
        ".....#......",
        "....#*#..#..",
        "...#***##*#.",
        "...#****#**#",
        "..#********#",
        "..#***##***#",
        ".#****##****#",
        ".#***@##@***#",
        ".#**@@##@@**#",
        ".#**@@@@@@**#",
        "..#*@@@@@@*#",
        "..#**@@@@**#",
        "...#******#.",
        "....######..",
    ]
    for py, row in enumerate(flame_matrix):
        for px, ch in enumerate(row):
            if ch == "#":
                im.putpixel((248 + px, 48 + py), (160, 45, 20, 255))
            elif ch == "*":
                col = (255, 150, 30, 255) if py > 7 else (255, 190, 50, 255)
                im.putpixel((248 + px, 48 + py), col)
            elif ch == "@":
                col = (255, 255, 210, 255) if py in (8, 9) else (255, 230, 120, 255)
                im.putpixel((248 + px, 48 + py), col)

    # Button Frames (14x14) in Atlas at y=150 (completely separate from scrollbar!):
    draw.rectangle((200, 150, 213, 163), fill=(10, 22, 28, 255), outline=(140, 115, 65, 255))
    draw.line((201, 151, 212, 151), fill=(190, 160, 95, 255))
    draw.line((201, 151, 201, 162), fill=(190, 160, 95, 255))
    draw.line((202, 162, 212, 162), fill=(55, 42, 20, 255))
    draw.line((212, 152, 212, 162), fill=(55, 42, 20, 255))

    draw.rectangle((216, 150, 229, 163), fill=(16, 38, 46, 255), outline=(225, 195, 125, 255))
    draw.line((217, 151, 228, 151), fill=(255, 235, 175, 255))
    draw.line((217, 151, 217, 162), fill=(255, 235, 175, 255))
    draw.line((218, 162, 228, 162), fill=(80, 65, 30, 255))
    draw.line((228, 152, 228, 162), fill=(80, 65, 30, 255))

    draw.rectangle((232, 150, 245, 163), fill=(12, 45, 55, 255), outline=(112, 219, 230, 255))
    draw.line((233, 151, 244, 151), fill=(18, 55, 65, 255))
    draw.line((233, 151, 233, 162), fill=(18, 55, 65, 255))
    draw.line((234, 162, 244, 162), fill=(80, 180, 195, 255))
    draw.line((244, 152, 244, 162), fill=(80, 180, 195, 255))

    # Five Action Glyphs (12x12) in Atlas at y=170:
    dep_x, dep_y = 200, 170
    for px in range(12):
        for py in range(12):
            im.putpixel((dep_x + px, dep_y + py), (0, 0, 0, 0))
    draw.line((dep_x + 3, dep_y + 1, dep_x + 5, dep_y + 3), fill=(180, 255, 255, 255))
    draw.line((dep_x + 8, dep_y + 1, dep_x + 6, dep_y + 3), fill=(180, 255, 255, 255))
    im.putpixel((dep_x + 5, dep_y + 4), (200, 255, 255, 255))
    im.putpixel((dep_x + 6, dep_y + 4), (200, 255, 255, 255))
    draw.line((dep_x + 2, dep_y + 5, dep_x + 5, dep_y + 8), fill=(112, 219, 230, 255))
    draw.line((dep_x + 9, dep_y + 5, dep_x + 6, dep_y + 8), fill=(112, 219, 230, 255))
    im.putpixel((dep_x + 5, dep_y + 9), (180, 255, 255, 255))
    im.putpixel((dep_x + 6, dep_y + 9), (180, 255, 255, 255))
    draw.line((dep_x + 1, dep_y + 9, dep_x + 1, dep_y + 11), fill=(184, 155, 92, 255))
    draw.line((dep_x + 10, dep_y + 9, dep_x + 10, dep_y + 11), fill=(184, 155, 92, 255))
    draw.line((dep_x + 1, dep_y + 11, dep_x + 10, dep_y + 11), fill=(184, 155, 92, 255))

    cr_x, cr_y = 216, 170
    draw.rectangle((cr_x + 1, cr_y + 1, cr_x + 10, cr_y + 6), fill=(168, 122, 70, 255), outline=(90, 58, 25, 255))
    draw.line((cr_x + 5, cr_y + 2, cr_x + 5, cr_y + 5), fill=(70, 45, 20, 255))
    draw.line((cr_x + 6, cr_y + 2, cr_x + 6, cr_y + 5), fill=(70, 45, 20, 255))
    draw.line((cr_x + 2, cr_y + 3, cr_x + 9, cr_y + 3), fill=(70, 45, 20, 255))
    im.putpixel((cr_x + 3, cr_y + 2), (210, 165, 105, 255))
    im.putpixel((cr_x + 8, cr_y + 2), (210, 165, 105, 255))
    draw.rectangle((cr_x + 1, cr_y + 7, cr_x + 3, cr_y + 10), fill=(110, 75, 40, 255))
    draw.rectangle((cr_x + 8, cr_y + 7, cr_x + 10, cr_y + 10), fill=(80, 50, 25, 255))
    draw.line((cr_x + 4, cr_y + 7, cr_x + 7, cr_y + 7), fill=(60, 38, 18, 255))
    im.putpixel((cr_x + 5, cr_y + 4), (112, 219, 230, 255))

    sm_x, sm_y = 232, 170
    flame_mini = [
        "....#....",
        "...#*#...",
        "..#***#.#",
        "..#***#*#",
        ".#*******#",
        ".#**@#@**#",
        ".#*@@@@**#",
        "..#@@@@*#.",
        "...#**##..",
        "....##....",
    ]
    for py, row in enumerate(flame_mini):
        for px, ch in enumerate(row):
            if ch == "#":
                im.putpixel((sm_x + 1 + px, sm_y + 1 + py), (170, 45, 20, 255))
            elif ch == "*":
                im.putpixel((sm_x + 1 + px, sm_y + 1 + py), (255, 165, 35, 255))
            elif ch == "@":
                im.putpixel((sm_x + 1 + px, sm_y + 1 + py), (255, 245, 180, 255))

    an_x, an_y = 248, 170
    anvil_mini = [
        "............",
        ".##########.",
        ".#********#.",
        "..#******#..",
        "...#****#...",
        "...#****#...",
        "..#******#..",
        ".##########.",
        "############",
        "............",
    ]
    for py, row in enumerate(anvil_mini):
        for px, ch in enumerate(row):
            if ch == "#":
                col = (60, 68, 76, 255) if py > 5 else (120, 130, 142, 255)
                im.putpixel((an_x + px, an_y + 1 + py), col)
            elif ch == "*":
                col = (205, 215, 225, 255) if py in (2, 3) and px < 6 else (155, 165, 175, 255)
                im.putpixel((an_x + px, an_y + 1 + py), col)
    im.putpixel((an_x, an_y + 2), (170, 180, 190, 255))
    im.putpixel((an_x, an_y + 3), (110, 120, 130, 255))

    cl_x, cl_y = 264, 170
    for px in range(12):
        for py in range(12):
            im.putpixel((cl_x + px, cl_y + py), (0, 0, 0, 0))
    hopper_mini = [
        "............",
        ".##########.",
        ".#========#.",
        "..#******#..",
        "..#*@!!@*#..",
        "...#*!!*#...",
        "....#!!#....",
        "....#@@#....",
        "....#**#....",
        "....####....",
        ".....##.....",
        "............",
    ]
    pal_hp = {
        '#': (35, 42, 50, 255),
        '=': (184, 155, 92, 255),
        '*': (120, 130, 142, 255),
        '@': (112, 219, 230, 255),
        '!': (220, 255, 255, 255),
    }
    for py, row in enumerate(hopper_mini):
        for px, ch in enumerate(row):
            if ch in pal_hp:
                im.putpixel((cl_x + px, cl_y + py), pal_hp[ch])

    # Symmetrical Left Drawer Background (118 x 88) at (300, 0, 417, 87)
    dw_x, dw_y = 300, 0
    dw_w, dw_h = 118, 88
    for y in range(dw_h):
        for x in range(dw_w):
            dx = (x - dw_w / 2) / (dw_w / 2)
            dy = (y - dw_h / 2) / (dw_h / 2)
            d = math.sqrt(dx * dx + dy * dy)
            r = max(4, min(255, int(6 - d * 2)))
            g = max(8, min(255, int(13 - d * 3)))
            b = max(14, min(255, int(20 - d * 4)))
            im.putpixel((dw_x + x, dw_y + y), (r, g, b, 255))

    draw.rectangle((dw_x, dw_y, dw_x + dw_w - 1, dw_y + dw_h - 1), outline=(4, 8, 12, 255))
    draw.rectangle((dw_x + 1, dw_y + 1, dw_x + dw_w - 2, dw_y + dw_h - 2), outline=(90, 70, 35, 255))
    draw.rectangle((dw_x + 2, dw_y + 2, dw_x + dw_w - 3, dw_y + dw_h - 3), outline=(14, 24, 28, 255))
    draw.rectangle((dw_x + 3, dw_y + 3, dw_x + dw_w - 4, dw_y + dw_h - 4), outline=(184, 155, 92, 255))
    draw.rectangle((dw_x + 4, dw_y + 4, dw_x + dw_w - 5, dw_y + dw_h - 5), outline=(5, 11, 15, 255))

    for corner in ((dw_x + 4, dw_y + 4), (dw_x + dw_w - 5, dw_y + 4),
                   (dw_x + 4, dw_y + dw_h - 5), (dw_x + dw_w - 5, dw_y + dw_h - 5)):
        im.putpixel(corner, (255, 245, 200, 255))

    draw.line((dw_x + 6, dw_y + 16, dw_x + dw_w - 7, dw_y + 16), fill=(184, 155, 92, 255))
    draw.line((dw_x + 6, dw_y + 17, dw_x + dw_w - 7, dw_y + 17), fill=(70, 55, 28, 255))
    im.putpixel((dw_x + dw_w // 2, dw_y + 16), (255, 245, 200, 255))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    im.save(output_path)
    print(f'Saved Klein Terminal GUI texture to {output_path}')


def generate_klein_bottle_icon(output_path: Path):
    prototype_path = ROOT / 'art/references/klein-bottle-item-prototype-v1.png'
    if prototype_path.exists():
        src = Image.open(prototype_path).convert('RGBA')
        bbox = src.getbbox()
        cropped = src.crop(bbox)
        W, H = cropped.size
        target_size = 28
        if W > H:
            nw = target_size
            nh = int(H * target_size / W)
        else:
            nh = target_size
            nw = int(W * target_size / H)
        sprite = cropped.resize((nw, nh), Image.Resampling.LANCZOS)
        enhancer = ImageEnhance.Color(sprite)
        sprite = enhancer.enhance(1.25)
        enhancer_c = ImageEnhance.Contrast(sprite)
        sprite = enhancer_c.enhance(1.15)

        final_32 = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        ox = (32 - nw) // 2
        oy = (32 - nh) // 2
        final_32.paste(sprite, (ox, oy), sprite)

        out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        for y in range(32):
            for x in range(32):
                r, g, b, a = final_32.getpixel((x, y))
                if a < 60:
                    out.putpixel((x, y), (0, 0, 0, 0))
                else:
                    out.putpixel((x, y), (r, g, b, 255))

        output_path.parent.mkdir(parents=True, exist_ok=True)
        out.save(output_path)
        print(f'Saved Klein Bottle item icon to {output_path}')
        return


def main():
    gui_target = ROOT / 'src/main/resources/assets/universal_tool/textures/gui/container/klein_terminal.png'
    gui_preview = ROOT / 'art/previews/gui_master_final.png'
    generate_klein_terminal_gui(gui_target)
    generate_klein_terminal_gui(gui_preview)

    item_target = ROOT / 'src/main/resources/assets/universal_tool/textures/item/klein_bottle.png'
    item_preview = ROOT / 'art/previews/bottle_icon32_clean.png'
    generate_klein_bottle_icon(item_target)
    generate_klein_bottle_icon(item_preview)


if __name__ == '__main__':
    main()
