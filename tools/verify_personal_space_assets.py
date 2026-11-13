"""Verify exported Java model dimensions, UV density, textures, and resource references."""
import base64
import io
import json
import struct
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
assets = root / "src/main/resources/assets/universal_tool"

for path in (root / "src/main/resources").rglob("*.json"):
    json.loads(path.read_text(encoding="utf-8"))

# 1. Verify 64x64 1px/unit block models (personal_space_gate, personal_space_return, universal_polymerizer)
for name in ("personal_space_gate", "personal_space_return", "universal_polymerizer"):
    model = json.loads((assets / f"models/block/{name}.json").read_text(encoding="utf-8"))
    assert len(model["elements"]) <= 24, f"{name}: too many cubes ({len(model['elements'])})"
    faces = 0
    for location in model["textures"].values():
        namespace, texture = location.split(":", 1)
        image = root / f"src/main/resources/assets/{namespace}/textures/{texture}.png"
        data = image.read_bytes()
        assert data[:8] == b"\x89PNG\r\n\x1a\n"
        assert struct.unpack(">II", data[16:24]) == (64, 64), f"{image}: expected 64x64"
    for element in model["elements"]:
        start, end = element["from"], element["to"]
        assert all(0 <= a < b <= 16 for a, b in zip(start, end)), f"{name}: invalid bounds {start} -> {end}"
        dx, dy, dz = [b - a for a, b in zip(start, end)]
        for direction, face in element["faces"].items():
            u0, v0, u1, v1 = face["uv"]
            assert all(0 <= uv <= 16 for uv in face["uv"]), f"{name}: UV OOB {face['uv']}"
            width, height = (
                (dx, dy) if direction in ("north", "south")
                else (dz, dy) if direction in ("east", "west")
                else (dx, dz)
            )
            assert abs(abs(u1 - u0) * 4 / width - 1) < 1e-6, f"{name} {direction}: U density mismatch"
            assert abs(abs(v1 - v0) * 4 / height - 1) < 1e-6, f"{name} {direction}: V density mismatch"
            faces += 1
    if name != "universal_polymerizer":
        states = json.loads((assets / f"blockstates/{name}.json").read_text(encoding="utf-8"))["variants"]
        assert set(states) == {f"facing={d}" for d in ("north", "south", "east", "west")}
    assert (root / f"art/assets/{name}.bbmodel").is_file()
    print(f"{name}: {len(model['elements'])} cubes, {faces} faces, 64x64 atlas, 1 pixel/model unit; passed")

# 2. Verify Universal Lottery Machine block models & 128x128 opaque UV coverage
ALLOWED_ANGLES = {-45.0, -22.5, 0.0, 22.5, 45.0}
lottery_tex_path = assets / "textures/block/universal_lottery_machine.png"
with Image.open(lottery_tex_path) as im:
    assert im.size == (128, 128), f"{lottery_tex_path}: expected 128x128, got {im.size}"
    lottery_rgba = im.convert("RGBA")
    lottery_pixels = lottery_rgba.load()

for variant, y_max in (
    ("universal_lottery_machine_bottom", 16),
    ("universal_lottery_machine_top", 16),
    ("universal_lottery_machine_full", 32),
):
    model = json.loads((assets / f"models/block/{variant}.json").read_text(encoding="utf-8"))
    assert len(model["elements"]) <= 120, f"{variant}: too many cubes"
    tex_loc = model["textures"]["0"]
    namespace, texture = tex_loc.split(":", 1)
    image = root / f"src/main/resources/assets/{namespace}/textures/{texture}.png"
    assert image.resolve() == lottery_tex_path.resolve()
    faces = 0
    for element in model["elements"]:
        start, end = element["from"], element["to"]
        assert 0 <= start[0] < end[0] <= 16, f"{variant}: X OOB {start} -> {end}"
        assert 0 <= start[1] < end[1] <= y_max, f"{variant}: Y OOB {start} -> {end}"
        assert 0 <= start[2] < end[2] <= 16, f"{variant}: Z OOB {start} -> {end}"
        if "rotation" in element:
            angle = float(element["rotation"]["angle"])
            assert angle in ALLOWED_ANGLES, f"{variant}: invalid rotation angle {angle}"
        for direction, face in element["faces"].items():
            u0, v0, u1, v1 = face["uv"]
            assert all(0 <= uv <= 16 for uv in (u0, v0, u1, v1)), f"{variant}: UV OOB {face['uv']}"
            assert abs(u1 - u0) > 1e-4 and abs(v1 - v0) > 1e-4, f"{variant} {direction}: zero-area UV {face['uv']}"
            # Check no pixel sampled inside this UV rectangle is transparent (alpha == 0 or < 255)
            px0 = max(0, min(127, int(min(u0, u1) * 8.0)))
            py0 = max(0, min(127, int(min(v0, v1) * 8.0)))
            px1 = max(px0 + 1, min(128, int(round(max(u0, u1) * 8.0))))
            py1 = max(py0 + 1, min(128, int(round(max(v0, v1) * 8.0))))
            for py in range(py0, py1):
                for px in range(px0, px1):
                    assert lottery_pixels[px, py][3] == 255, (
                        f"{variant} {element.get('name')} {direction}: UV {face['uv']} touches non-opaque pixel ({px},{py})"
                    )
            faces += 1
    print(f"{variant}: {len(model['elements'])} cubes, {faces} faces, zero-degenerate-UVs, 100% opaque UVs; passed")

# Verify .bbmodel embedded sources & outliner UUIDs
lottery_bb = json.loads((root / "art/assets/universal_lottery_machine.bbmodel").read_text(encoding="utf-8"))
assert lottery_bb["resolution"] == {"width": 128, "height": 128}
emb_b64 = lottery_bb["textures"][0]["source"].split(",", 1)[1]
with Image.open(io.BytesIO(base64.b64decode(emb_b64))) as emb_im:
    assert emb_im.size == (128, 128), f"universal_lottery_machine.bbmodel embedded source size {emb_im.size} != (128, 128)"
    emb_px = emb_im.convert("RGBA").load()
    for el in lottery_bb["elements"]:
        for direction, face in el["faces"].items():
            u0, v0, u1, v1 = face["uv"]
            px0, py0 = max(0, int(min(u0, u1))), max(0, int(min(v0, v1)))
            px1, py1 = min(128, max(px0 + 1, int(round(max(u0, u1))))), min(128, max(py0 + 1, int(round(max(v0, v1)))))
            for py in range(py0, py1):
                for px in range(px0, px1):
                    assert emb_px[px, py][3] == 255, f"lottery.bbmodel {el['name']} {direction}: transparent embedded px ({px},{py})"

for gate_name in ("personal_space_gate", "personal_space_return"):
    gbb = json.loads((root / f"art/assets/{gate_name}.bbmodel").read_text(encoding="utf-8"))
    disk_bytes = (assets / f"textures/block/{gate_name}_atlas.png").read_bytes()
    assert gbb["textures"][0]["source"] == "data:image/png;base64," + base64.b64encode(disk_bytes).decode("ascii")

for phoenix_bb_path in (root / "operation_phoenix_pod.bbmodel", root / "art/assets/operation_phoenix_pod.bbmodel"):
    pbb = json.loads(phoenix_bb_path.read_text(encoding="utf-8"))
    for node in pbb["outliner"]:
        assert isinstance(node, dict) and node.get("uuid"), f"{phoenix_bb_path}: outliner group missing uuid"
print("bbmodel sync & outliner UUID gates passed")

# 3. Verify 16x16 Jappa item textures (binary alpha 0/255, excluding lightning_spear.png)
item_dir = assets / "textures/item"
checked_items = 0
for png in sorted(item_dir.glob("*.png")):
    if png.name == "lightning_spear.png":
        continue
    with Image.open(png) as im:
        rgba = im.convert("RGBA")
        assert rgba.size == (16, 16), f"{png.name}: expected 16x16, got {rgba.size}"
        alphas = {px[3] for px in rgba.get_flattened_data()}
        assert alphas.issubset({0, 255}), f"{png.name}: semi-transparent pixels found: {sorted(alphas)}"
        checked_items += 1
print(f"item textures: {checked_items} 16x16 sprites with binary alpha; passed")

# 4. Verify 8 new Pocket Realm environment emblems & deletion of dead assets
gui_dir = assets / "textures/gui"
new_envs = (
    "superflat", "sky_islands", "cherry_grove", "amethyst_starfall",
    "sculk_sanctum", "mushroom_wonderland", "obsidian_forge", "aurora_glacier",
)
for env_id in new_envs:
    env_png = gui_dir / f"environment_{env_id}.png"
    assert env_png.is_file(), f"Missing environment emblem: {env_png}"
    with Image.open(env_png) as im:
        assert im.size == (16, 16), f"{env_png.name}: expected 16x16, got {im.size}"
print(f"environment emblems: {len(new_envs)} 16x16 PNGs present; passed")

dead_files = [
    gui_dir / f"environment_{old}.png"
    for old in ("plains", "desert", "snowy_plains", "birch_forest", "savanna")
] + [
    assets / f"textures/block/{old}"
    for old in (
        "personal_space_stone.png", "personal_space_core.png", "personal_space_return.png",
        "polymerizer_base.png", "polymerizer_top.png", "polymerizer_glass.png", "polymerizer_side.png",
    )
]
for dead in dead_files:
    assert not dead.exists(), f"Dead asset still exists: {dead}"
print("dead asset cleanup: 12 removed files absent; passed")

print("Resource JSON, geometry/UV, item sprite, and environment emblem gates passed")
