import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageEnhance

ROOT = Path(__file__).resolve().parents[1]


def generate_klein_terminal_gui(output_path: Path):
    W, H = 512, 512
    PW, PH = 260, 286
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)

    # 1. Base Cosmic Nebula Gradient
    for y in range(PH):
        for x in range(PW):
            dx = (x - 130) / 130.0
            dy = (y - 140) / 140.0
            d = math.sqrt(dx * dx + dy * dy)
            r = int(6 + 2 * math.sin(x * 0.03) - d * 2)
            g = int(12 + 4 * math.cos(y * 0.03) - d * 3)
            b = int(18 + 5 * math.sin((x + y) * 0.02) - d * 4)
            im.putpixel((x, y), (max(3, min(255, r)), max(6, min(255, g)), max(10, min(255, b)), 255))

    # 2. Concentric Astrolabe Rings & Coordinate Rays
    chart = Image.new("RGBA", (PW, PH), (0, 0, 0, 0))
    cdraw = ImageDraw.Draw(chart)

    # Top-Left Armillary Center (-10, -10)
    for r in [40, 75, 120, 175, 235, 300]:
        cdraw.ellipse((-10 - r, -10 - r, -10 + r, -10 + r), outline=(38, 92, 102, 55), width=1)
        for deg in range(0, 95, 5):
            rad = math.radians(deg)
            tx = -10 + r * math.cos(rad)
            ty = -10 + r * math.sin(rad)
            tick_len = 2 if deg % 15 == 0 else 1
            x2 = tx + tick_len * math.cos(rad)
            y2 = ty + tick_len * math.sin(rad)
            if 0 <= tx < PW and 0 <= ty < PH:
                cdraw.line((tx, ty, x2, y2), fill=(55, 130, 140, 75 if deg % 15 == 0 else 45))

    for deg in range(0, 95, 10):
        rad = math.radians(deg)
        cdraw.line((-10, -10, int(-10 + 350 * math.cos(rad)), int(-10 + 350 * math.sin(rad))), fill=(30, 75, 85, 30), width=1)

    # Bottom-Right Armillary Center (PW+15, PH+15)
    for r in [50, 95, 150, 215, 285]:
        cdraw.ellipse((PW + 15 - r, PH + 15 - r, PW + 15 + r, PH + 15 + r), outline=(35, 85, 95, 50), width=1)
        for deg in range(180, 275, 5):
            rad = math.radians(deg)
            tx = PW + 15 + r * math.cos(rad)
            ty = PH + 15 + r * math.sin(rad)
            tick_len = 2 if deg % 15 == 0 else 1
            x2 = tx - tick_len * math.cos(rad)
            y2 = ty - tick_len * math.sin(rad)
            if 0 <= tx < PW and 0 <= ty < PH:
                cdraw.line((tx, ty, x2, y2), fill=(50, 120, 130, 70 if deg % 15 == 0 else 40))

    for deg in range(180, 275, 10):
        rad = math.radians(deg)
        cdraw.line((PW + 15, PH + 15, int(PW + 15 + 350 * math.cos(rad)), int(PH + 15 + 350 * math.sin(rad))), fill=(28, 70, 80, 25), width=1)

    cdraw.ellipse((-40, 70, 320, 240), outline=(45, 105, 115, 38), width=1)
    cdraw.ellipse((-20, 50, 340, 260), outline=(35, 85, 95, 32), width=1)

    stars = [
        (45, 18), (110, 15), (200, 18), (230, 42), (18, 110), (242, 130),
        (25, 210), (235, 205), (15, 155), (130, 45), (130, 164),
        (75, 82), (185, 88), (62, 238), (198, 238)
    ]
    for sx, sy in stars:
        cdraw.point((sx, sy), fill=(160, 230, 240, 180))
        cdraw.point((sx + 1, sy), fill=(75, 150, 170, 75))
        cdraw.point((sx - 1, sy), fill=(75, 150, 170, 75))
        cdraw.point((sx, sy + 1), fill=(75, 150, 170, 75))
        cdraw.point((sx, sy - 1), fill=(75, 150, 170, 75))

    im.alpha_composite(chart, (0, 0))

    # 3. Double Antique Brass Border & Astrolabe Corner Stars
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
        for d in range(1, 4):
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

    # 4. Header Bar: Astrolabe Emblem & Close Button
    gold = (184, 155, 92, 255)
    cyan = (112, 219, 230, 255)
    bright = (200, 255, 250, 255)
    draw.ellipse((8, 6, 22, 20), outline=gold, width=1)
    draw.polygon([(15, 9), (13, 13), (12, 17), (18, 17), (17, 13)], outline=(30, 70, 80, 255), fill=(8, 20, 25, 255))
    draw.ellipse((13, 12, 17, 16), outline=cyan)
    im.putpixel((15, 14), bright)

    # Close Button at (241, 5) to (255, 19)
    draw.rectangle((241, 5, 255, 19), fill=(10, 20, 25, 255), outline=(140, 113, 59, 255))
    for d in range(0, 7):
        im.putpixel((245 + d, 9 + d), (196, 85, 77, 255))
        im.putpixel((245 + d, 15 - d), (196, 85, 77, 255))
        im.putpixel((244 + d, 9 + d), (222, 185, 122, 180))
        im.putpixel((244 + d, 15 - d), (222, 185, 122, 180))

    # 5. Search Bar & Sort Control Button Frames (y = 24..40)
    # Search Pill Box: (9, 24, 163, 40)
    draw.rounded_rectangle((9, 24, 163, 40), radius=3, fill=(6, 14, 18, 255), outline=(24, 62, 70, 255))
    draw.line((11, 25, 161, 25), fill=(3, 7, 9, 255))
    # Magnifying Glass at (14, 27)
    draw.ellipse((14, 27, 20, 33), outline=(112, 219, 230, 255), width=1)
    draw.line((19, 32, 22, 35), fill=(112, 219, 230, 255), width=1)

    # Sort Button Frames (168..196, 200..238, 242..256)
    draw.rectangle((168, 24, 196, 40), fill=(12, 26, 32, 255), outline=(36, 80, 90, 255))
    draw.rectangle((200, 24, 238, 40), fill=(12, 26, 32, 255), outline=(184, 155, 92, 255))
    draw.rectangle((242, 24, 256, 40), fill=(12, 26, 32, 255), outline=(36, 80, 90, 255))

    # 6. Filigree Section Dividers (y=56 for warehouse, y=183 for inventory)
    def draw_divider_line(y_pos, x_start=9, x_end=244):
        gold = (184, 155, 92, 255)
        gold_shadow = (70, 55, 28, 255)
        bright = (255, 245, 200, 255)
        draw.line((x_start, y_pos, x_end, y_pos), fill=gold)
        draw.line((x_start, y_pos + 1, x_end, y_pos + 1), fill=gold_shadow)
        im.putpixel((x_start + 2, y_pos), bright)
        im.putpixel((x_start + 3, y_pos - 1), bright)
        im.putpixel((x_start + 3, y_pos + 1), bright)
        im.putpixel((x_start + 4, y_pos), bright)
        draw.line((x_end - 2, y_pos - 1, x_end - 2, y_pos + 2), fill=gold)

    draw_divider_line(56, 9, 244)
    draw_divider_line(183, 9, 244)

    # 7. Slot Wells (18x18 Beveled Wells)
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

    # Warehouse: 13 cols x 6 rows = 78 slots at y=60..168
    for row in range(6):
        for col in range(13):
            draw_slot(9 + col * 18, 60 + row * 18)

    # Scrollbar Track at (246, 60, w=6, h=108)
    draw.rectangle((246, 60, 251, 167), fill=(5, 12, 16, 255), outline=(16, 40, 48, 255))
    im.putpixel((248, 62), (112, 219, 230, 255))
    im.putpixel((247, 63), (112, 219, 230, 255))
    im.putpixel((249, 63), (112, 219, 230, 255))
    im.putpixel((248, 165), (112, 219, 230, 255))
    im.putpixel((247, 164), (112, 219, 230, 255))
    im.putpixel((249, 164), (112, 219, 230, 255))

    # Player Main Inventory: 9 cols x 3 rows at y=188..242
    for row in range(3):
        for col in range(9):
            draw_slot(49 + col * 18, 188 + row * 18)

    # Player Hotbar: 9 cols at y=246..264
    for col in range(9):
        draw_slot(49 + col * 18, 246)

    # Hotbar Side Brackets
    draw.line((45, 244, 45, 265), fill=(184, 155, 92, 255), width=1)
    draw.line((45, 244, 48, 244), fill=(184, 155, 92, 255), width=1)
    draw.line((45, 265, 48, 265), fill=(184, 155, 92, 255), width=1)
    draw.line((214, 244, 214, 265), fill=(184, 155, 92, 255), width=1)
    draw.line((211, 244, 214, 244), fill=(184, 155, 92, 255), width=1)
    draw.line((211, 265, 214, 265), fill=(184, 155, 92, 255), width=1)

    # 8. Side Texture Atlas Elements (x >= 280)
    # Locked slot overlay (280, 0, 18x18)
    draw.rectangle((280, 0, 297, 17), outline=(222, 185, 122, 255))
    draw.rectangle((281, 1, 296, 16), outline=(90, 70, 35, 255))

    # Padlock badge (304, 0, 9x10)
    draw.arc((305, 0, 311, 6), 180, 0, fill=(222, 185, 122, 255), width=1)
    draw.rectangle((304, 4, 312, 9), fill=(184, 155, 92, 255), outline=(90, 70, 35, 255))
    im.putpixel((308, 6), (40, 30, 15, 255))
    im.putpixel((308, 7), (40, 30, 15, 255))

    # Glowing cyan hover outline (280, 24, 18x18)
    draw.rectangle((280, 24, 297, 41), outline=(112, 219, 230, 255))
    im.putpixel((280, 24), (200, 255, 255, 255))
    im.putpixel((297, 24), (200, 255, 255, 255))
    im.putpixel((280, 41), (200, 255, 255, 255))
    im.putpixel((297, 41), (200, 255, 255, 255))

    # Glowing cyan scrollbar thumb (280, 48, 4x32)
    for y in range(48, 48 + 32):
        im.putpixel((280, y), (42, 115, 127, 255))
        im.putpixel((281, y), (112, 219, 230, 255))
        im.putpixel((282, y), (180, 255, 255, 255))
        im.putpixel((283, y), (42, 115, 127, 255))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    im.save(output_path)
    print(f"Saved Klein Terminal GUI texture to {output_path}")


def generate_klein_bottle_icon(output_path: Path):
    prototype_path = ROOT / "art/references/klein-bottle-item-prototype-v1.png"
    if prototype_path.exists():
        src = Image.open(prototype_path).convert("RGBA")
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

        final_32 = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        ox = (32 - nw) // 2
        oy = (32 - nh) // 2
        final_32.paste(sprite, (ox, oy), sprite)

        out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        for y in range(32):
            for x in range(32):
                r, g, b, a = final_32.getpixel((x, y))
                if a < 60:
                    out.putpixel((x, y), (0, 0, 0, 0))
                else:
                    out.putpixel((x, y), (r, g, b, 255))

        output_path.parent.mkdir(parents=True, exist_ok=True)
        out.save(output_path)
        print(f"Saved Klein Bottle item icon to {output_path}")
        return


def main():
    gui_target = ROOT / "src/main/resources/assets/universal_tool/textures/gui/container/klein_terminal.png"
    gui_preview = ROOT / "art/previews/gui_master_final.png"
    generate_klein_terminal_gui(gui_target)
    generate_klein_terminal_gui(gui_preview)

    item_target = ROOT / "src/main/resources/assets/universal_tool/textures/item/klein_bottle.png"
    item_preview = ROOT / "art/previews/bottle_icon32_clean.png"
    generate_klein_bottle_icon(item_target)
    generate_klein_bottle_icon(item_preview)


if __name__ == "__main__":
    main()
