"""Generate the formal skill icons used by the skill screen.

The icons are deterministic pixel art rather than generated images: every active and passive in
ModSkills has its own 32x32 sprite, with transparent corners and a dark framed tile. Keeping them
code-generated makes their palette consistent and lets future skills reuse the same family.

Run from the repository root:
    python tools/generate_skill_icons.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/universal_tool/textures/gui/skills"
SIZE = 32

BG_TOP = (31, 24, 37, 255)
BG_BOTTOM = (12, 10, 16, 255)
FRAME = (116, 86, 44, 255)
FRAME_HI = (204, 156, 70, 255)
FRAME_DARK = (51, 36, 28, 255)


def frame(icon: Image.Image, accent: tuple[int, int, int, int]) -> Image.Image:
    """Frame a transparent glyph with a thin beveled tile."""
    tile = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(tile)
    draw.rectangle((1, 1, 30, 30), fill=FRAME_DARK)
    draw.rectangle((2, 2, 29, 29), fill=FRAME)
    draw.rectangle((3, 3, 28, 28), fill=BG_TOP)
    draw.rectangle((3, 12, 28, 28), fill=BG_BOTTOM)
    for x in range(4, 28, 4):
        draw.point((x, 3), fill=FRAME_HI)
        draw.point((x, 28), fill=FRAME_HI)
    draw.line((3, 3, 28, 3), fill=FRAME_HI)
    draw.line((3, 28, 28, 28), fill=accent)
    draw.line((3, 4, 3, 27), fill=FRAME_HI)
    draw.rectangle((5, 5, 26, 26), outline=(45, 33, 48, 255))
    tile.alpha_composite(icon)
    return tile


def glyph() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def counter_guard() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    d.polygon(((16, 8), (23, 11), (22, 20), (16, 25), (10, 20), (9, 11)), fill=(119, 189, 202, 255))
    d.polygon(((16, 11), (20, 13), (19, 19), (16, 22), (13, 19), (12, 13)), fill=(41, 90, 112, 255))
    d.line((16, 11, 16, 22), fill=(211, 250, 255, 255))
    d.line((12, 15, 20, 15), fill=(211, 250, 255, 255))
    d.ellipse((13, 3, 19, 9), fill=(240, 198, 99, 255))
    return frame(icon, (83, 184, 201, 255))


def gravity_well() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    for radius, color in ((9, (101, 54, 129, 255)), (7, (147, 77, 169, 255)),
                          (5, (202, 119, 209, 255)), (2, (245, 208, 255, 255))):
        cx = 16
        d.ellipse((cx - radius, 16 - radius, cx + radius, 16 + radius), outline=color, width=1)
    d.line((3, 16, 8, 16), fill=(217, 172, 230, 255))
    d.line((24, 16, 29, 16), fill=(217, 172, 230, 255))
    d.line((16, 3, 16, 8), fill=(217, 172, 230, 255))
    d.line((16, 24, 16, 29), fill=(217, 172, 230, 255))
    return frame(icon, (174, 104, 198, 255))


def frozen_pull() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    for start, end in (
        ((16, 5), (16, 27)), ((16, 5), (8, 10)), ((16, 5), (24, 10)),
        ((16, 27), (8, 22)), ((16, 27), (24, 22)),
    ):
        d.line((start[0], start[1], end[0], end[1]), fill=(105, 198, 230, 255))
    d.line((7, 16, 25, 16), fill=(178, 236, 249, 255))
    d.line((10, 12, 22, 20), fill=(54, 134, 168, 255))
    d.line((10, 20, 22, 12), fill=(54, 134, 168, 255))
    d.rectangle((14, 14, 18, 18), fill=(229, 253, 255, 255))
    return frame(icon, (94, 191, 220, 255))


def oppressive_field() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    d.ellipse((5, 8, 27, 24), outline=(97, 30, 52, 255), width=3)
    d.ellipse((9, 11, 23, 21), outline=(189, 62, 84, 255), width=2)
    for x, y in ((7, 7), (25, 8), (5, 23), (22, 25), (16, 4), (16, 27)):
        d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=(231, 145, 103, 255))
    d.line((12, 16, 20, 16), fill=(247, 214, 175, 255))
    d.line((16, 12, 16, 20), fill=(247, 214, 175, 255))
    return frame(icon, (197, 68, 90, 255))


def ore_sight() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    d.polygon(((9, 9), (23, 7), (26, 20), (20, 26), (9, 22), (6, 14)), fill=(80, 92, 96, 255))
    d.polygon(((20, 8), (26, 13), (23, 20), (16, 22), (12, 18)), fill=(52, 63, 70, 255))
    for x, y, c in ((14, 12, (97, 233, 244, 255)), (22, 15, (250, 214, 99, 255)),
                    (11, 19, (235, 104, 122, 255)), (17, 22, (136, 229, 129, 255))):
        d.rectangle((x - 2, y - 1, x + 1, y + 1), fill=c)
        d.point((x, y - 2), fill=(255, 255, 255, 255))
    return frame(icon, (135, 176, 185, 255))


def air_step() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    d.polygon(((6, 23), (13, 27), (20, 22), (27, 24), (27, 19), (21, 18), (18, 14),
               (15, 18), (11, 19)), fill=(151, 197, 227, 255))
    d.line((11, 16, 20, 11), fill=(226, 245, 255, 255), width=2)
    d.line((16, 8, 23, 9), fill=(237, 204, 116, 255))
    d.line((16, 8, 20, 5), fill=(237, 204, 116, 255))
    d.line((16, 8, 13, 5), fill=(237, 204, 116, 255))
    return frame(icon, (153, 197, 230, 255))


def lightning_spear() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    d.polygon(((19, 4), (10, 19), (16, 19), (12, 28), (24, 12), (18, 12), (24, 4)),
              fill=(248, 207, 83, 255))
    d.line((20, 6, 14, 18), fill=(255, 248, 190, 255), width=2)
    d.line((15, 20, 12, 27), fill=(215, 148, 45, 255))
    for x, y in ((7, 10), (25, 20), (8, 23)):
        d.rectangle((x, y, x + 1, y + 1), fill=(194, 226, 245, 255))
    return frame(icon, (234, 174, 65, 255))


def berserk() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    d.polygon(((16, 4), (22, 11), (27, 10), (24, 17), (25, 26), (16, 22), (8, 27),
               (8, 18), (4, 13), (10, 11)), fill=(196, 59, 47, 255))
    d.polygon(((16, 8), (20, 13), (22, 12), (20, 18), (21, 23), (16, 19), (12, 23),
               (12, 17), (10, 13), (15, 12)), fill=(246, 137, 55, 255))
    d.rectangle((14, 15, 18, 18), fill=(255, 232, 166, 255))
    return frame(icon, (216, 80, 53, 255))


def ranged_barrage() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    for dx, color in ((0, (230, 190, 104, 255)), (-4, (160, 101, 52, 255)),
                      (4, (160, 101, 52, 255))):
        d.line((6, 16 + dx, 25, 16 + dx), fill=color)
        d.line((22, 13 + dx, 26, 16 + dx), fill=color)
        d.line((22, 19 + dx, 26, 16 + dx), fill=color)
    d.rectangle((5, 13, 11, 19), outline=(206, 172, 88, 255))
    d.line((7, 11, 9, 13), fill=(239, 218, 156, 255))
    return frame(icon, (198, 151, 75, 255))


def battle_momentum() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    for x, height in ((9, 9), (14, 14), (19, 19), (24, 24)):
        d.rectangle((x - 2, 26 - height, x + 1, 25), fill=(214, 163, 70, 255))
        d.rectangle((x - 1, 26 - height, x, 25), fill=(255, 221, 138, 255))
    d.polygon(((8, 12), (13, 8), (18, 6), (22, 8), (18, 10), (14, 13)), fill=(243, 199, 91, 255))
    return frame(icon, (205, 153, 66, 255))


def focus_mining() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    d.line((8, 24, 23, 9), fill=(186, 149, 98, 255), width=3)
    d.line((6, 22, 12, 28), fill=(115, 80, 50, 255), width=2)
    d.polygon(((20, 5), (28, 9), (23, 14), (18, 10)), fill=(151, 191, 204, 255))
    d.rectangle((9, 12, 13, 16), fill=(238, 201, 112, 255))
    d.point((11, 14), fill=(255, 247, 201, 255))
    return frame(icon, (186, 148, 91, 255))


def last_stand() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    d.polygon(((16, 5), (26, 10), (25, 21), (16, 27), (7, 21), (6, 10)), fill=(118, 40, 47, 255))
    d.polygon(((16, 9), (22, 12), (21, 19), (16, 23), (11, 19), (10, 12)), fill=(209, 75, 72, 255))
    d.line((16, 11, 16, 20), fill=(255, 214, 173, 255))
    d.line((12, 15, 20, 15), fill=(255, 214, 173, 255))
    return frame(icon, (181, 68, 71, 255))


def survival_flow() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    d.polygon(((16, 5), (11, 14), (8, 18), (10, 24), (16, 27), (22, 23), (23, 17), (20, 12)),
              fill=(56, 139, 133, 255))
    d.polygon(((16, 9), (14, 15), (19, 18), (15, 22), (20, 20), (20, 15)), fill=(137, 228, 204, 255))
    d.point((16, 12), fill=(219, 255, 240, 255))
    return frame(icon, (82, 190, 169, 255))


def swift_pace() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    # Winged boot / swift trails
    d.polygon(((8, 22), (18, 22), (24, 25), (24, 27), (7, 27)), fill=(235, 195, 80, 255))
    d.polygon(((11, 15), (17, 15), (17, 22), (9, 22)), fill=(200, 150, 50, 255))
    d.line((5, 12, 13, 12), fill=(180, 230, 255, 255), width=2)
    d.line((3, 17, 8, 17), fill=(180, 230, 255, 255), width=1)
    d.line((6, 9, 16, 9), fill=(240, 248, 255, 255), width=1)
    return frame(icon, (230, 185, 70, 255))


def light_feather() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    # Feather quill
    d.line((8, 26, 24, 6), fill=(230, 245, 255, 255), width=2)
    d.polygon(((12, 22), (18, 10), (22, 7), (20, 16), (15, 23)), fill=(180, 225, 245, 255))
    d.polygon(((9, 25), (11, 18), (17, 13), (13, 21)), fill=(130, 185, 220, 255))
    d.point((17, 8), fill=(255, 255, 255, 255))
    return frame(icon, (150, 210, 245, 255))


def iron_physique() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    # Heart with golden iron rim
    d.polygon(((16, 27), (26, 17), (26, 11), (22, 7), (16, 11), (10, 7), (6, 11), (6, 17)), fill=(180, 40, 45, 255))
    d.polygon(((16, 24), (23, 16), (23, 12), (20, 9), (16, 12), (12, 9), (9, 12), (9, 16)), fill=(235, 65, 70, 255))
    d.rectangle((10, 11, 12, 13), fill=(255, 200, 200, 255))
    d.line((16, 12, 16, 24), fill=(255, 215, 100, 255))
    return frame(icon, (220, 60, 65, 255))


def armor_piercing() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    # Broken shield with piercing dagger
    d.polygon(((16, 6), (25, 9), (23, 18), (16, 25), (9, 18), (7, 9)), fill=(75, 85, 95, 255))
    d.line((16, 4, 16, 27), fill=(255, 230, 120, 255), width=2)
    d.line((11, 10, 21, 10), fill=(210, 170, 60, 255), width=2)
    d.polygon(((14, 23), (18, 23), (16, 28)), fill=(255, 255, 220, 255))
    d.line((19, 12, 23, 15), fill=(240, 90, 70, 255))
    return frame(icon, (220, 180, 80, 255))


def bloodthirst() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    # Crimson blood droplet with sharp fangs
    d.polygon(((16, 5), (23, 15), (23, 21), (19, 26), (13, 26), (9, 21), (9, 15)), fill=(160, 20, 30, 255))
    d.polygon(((16, 8), (21, 16), (20, 21), (16, 24), (12, 21), (11, 16)), fill=(230, 45, 55, 255))
    d.point((14, 12), fill=(255, 180, 180, 255))
    d.point((15, 11), fill=(255, 255, 255, 255))
    return frame(icon, (200, 30, 40, 255))


def item_magnet() -> Image.Image:
    icon = glyph()
    d = ImageDraw.Draw(icon)
    # U-shaped horseshoe magnet
    d.rectangle((7, 8, 11, 20), fill=(220, 50, 50, 255))
    d.rectangle((21, 8, 25, 20), fill=(50, 100, 220, 255))
    d.arc((7, 12, 25, 26), 0, 180, fill=(150, 150, 160, 255), width=4)
    d.rectangle((7, 6, 11, 8), fill=(230, 230, 240, 255))
    d.rectangle((21, 6, 25, 8), fill=(230, 230, 240, 255))
    d.arc((4, 2, 28, 16), 200, 340, fill=(180, 220, 255, 255), width=1)
    return frame(icon, (100, 150, 230, 255))


ICONS = {
    "counter_guard": counter_guard,
    "gravity_well": gravity_well,
    "frozen_pull": frozen_pull,
    "oppressive_field": oppressive_field,
    "ore_sight": ore_sight,
    "air_step": air_step,
    "lightning_spear": lightning_spear,
    "berserk": berserk,
    "battle_momentum": battle_momentum,
    "focus_mining": focus_mining,
    "last_stand": last_stand,
    "survival_flow": survival_flow,
    "swift_pace": swift_pace,
    "light_feather": light_feather,
    "iron_physique": iron_physique,
    "armor_piercing": armor_piercing,
    "bloodthirst": bloodthirst,
    "item_magnet": item_magnet,
}


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, factory in ICONS.items():
        factory().save(OUT / f"{name}.png", "PNG")
    print(f"Generated {len(ICONS)} icons in {OUT}")


if __name__ == "__main__":
    main()
