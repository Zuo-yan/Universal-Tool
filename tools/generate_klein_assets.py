"""Generate the original 16x16 Klein bottle icon from the approved visual palette."""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "src/main/resources/assets/universal_tool/textures/item/klein_bottle.png"
image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)
brass = "#b29a61"
glass = "#275c61"
cyan = "#73e0d4"
shine = "#c5fff0"
ink = "#071416"
draw.rectangle((6, 0, 8, 1), fill=brass)
draw.rectangle((5, 2, 9, 3), fill=ink, outline=brass)
draw.line((6, 4, 6, 6, 4, 8, 3, 11, 4, 14, 10, 14, 12, 11, 11, 8, 8, 6), fill=glass, width=2)
draw.line((8, 4, 8, 6, 10, 8, 11, 11, 10, 13, 5, 13, 4, 11, 5, 9, 7, 7), fill=cyan)
draw.line((6, 8, 9, 11, 10, 10, 7, 7, 6, 8), fill=shine)
draw.point((5, 11), fill=shine)
draw.point((10, 12), fill=glass)
TARGET.parent.mkdir(parents=True, exist_ok=True)
image.save(TARGET)
print(TARGET)
