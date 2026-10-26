import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageEnhance

ROOT = Path(__file__).resolve().parents[1]


def generate_klein_terminal_gui(output_path: Path):
    W, H = 512, 512
    PW, PH = 194, 216
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)

    # 1. Base Cosmic Nebula Gradient (194x216)
    for y in range(PH):
        for x in range(PW):
            dx = (x - 97) / 97.0
            dy = (y - 108) / 108.0
            d = math.sqrt(dx * dx + dy * dy)
            r = int(6 + 2 * math.sin(x * 0.04) - d * 2)
            g = int(12 + 4 * math.cos(y * 0.04) - d * 3)
            b = int(18 + 5 * math.sin((x + y) * 0.03) - d * 4)
            im.putpixel((x, y), (max(3, min(255, r)), max(6, min(255, g)), max(10, min(255, b)), 255))

    # Astrolabe celestial rings
    chart = Image.new("RGBA", (PW, PH), (0, 0, 0, 0))
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

    # 2. Outer antique brass double border
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

    # 3. Header: Emblem & Close Button
    draw.ellipse((8, 6, 18, 16), outline=(184, 155, 92, 255), width=1)
    im.putpixel((13, 11), (112, 219, 230, 255))

    # Close button at (178, 6) to (188, 16)
    draw.rectangle((178, 6, 188, 16), fill=(10, 20, 25, 255), outline=(140, 113, 59, 255))
    for d in range(0, 5):
        im.putpixel((181 + d, 9 + d), (196, 85, 77, 255))
        im.putpixel((181 + d, 13 - d), (196, 85, 77, 255))

    # 4. Search bar & Sort Controls (y=19..33)
    draw.rounded_rectangle((8, 19, 116, 33), radius=3, fill=(6, 14, 18, 255), outline=(24, 62, 70, 255))
    draw.line((10, 20, 114, 20), fill=(3, 7, 9, 255))
    # Magnifying glass at (12, 22)
    draw.ellipse((12, 22, 17, 27), outline=(112, 219, 230, 255), width=1)
    draw.line((16, 26, 18, 28), fill=(112, 219, 230, 255), width=1)

    # Sort buttons: (120..158, 162..184)
    draw.rectangle((120, 19, 158, 33), fill=(12, 26, 32, 255), outline=(184, 155, 92, 255))
    draw.rectangle((162, 19, 184, 33), fill=(12, 26, 32, 255), outline=(36, 80, 90, 255))

    # 5. Divider Line at y=38
    def draw_divider(y_pos):
        gold = (184, 155, 92, 255)
        gold_shadow = (70, 55, 28, 255)
        bright = (255, 245, 200, 255)
        draw.line((8, y_pos, 184, y_pos), fill=gold)
        draw.line((8, y_pos + 1, 184, y_pos + 1), fill=gold_shadow)
        im.putpixel((PW // 2, y_pos), bright)
        im.putpixel((PW // 2 - 1, y_pos), gold)
        im.putpixel((PW // 2 + 1, y_pos), gold)
        im.putpixel((PW // 2, y_pos - 1), gold)
        im.putpixel((PW // 2, y_pos + 1), gold)

    draw_divider(38)

    # 6. Slot drawing helper
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

    # Warehouse 4 rows x 9 cols at (8, 42) -> 36 slots, height=72 (y=42..114)
    for r in range(4):
        for c in range(9):
            draw_slot(8 + c * 18, 42 + r * 18)

    # Scrollbar track at (174, 42, w=10, h=72)
    draw.rectangle((174, 42, 184, 113), fill=(5, 12, 16, 255), outline=(16, 40, 48, 255))
    im.putpixel((179, 44), (112, 219, 230, 255))
    im.putpixel((179, 111), (112, 219, 230, 255))

    # Lower divider at y=119
    draw_divider(119)

    # Player Inventory 3 rows x 9 cols at (8, 133) -> 27 slots (y=133..187)
    for r in range(3):
        for c in range(9):
            draw_slot(8 + c * 18, 133 + r * 18)

    # Hotbar 1 row x 9 cols at (8, 192) -> 9 slots (y=192..210)
    for c in range(9):
        draw_slot(8 + c * 18, 192)

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

    # Scrollbar thumb (200, 48, 6x24)
    for y in range(48, 48 + 24):
        for x in range(200, 206):
            im.putpixel((x, y), (112, 219, 230, 255) if x in (201, 202, 203, 204) else (42, 115, 127, 255))

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
