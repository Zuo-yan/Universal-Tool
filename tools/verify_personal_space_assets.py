"""Verify exported Java model dimensions, UV density and resource references."""
import json
import struct
from pathlib import Path

root = Path(__file__).resolve().parents[1]
assets = root / "src/main/resources/assets/universal_tool"
for path in (root / "src/main/resources").rglob("*.json"):
    json.loads(path.read_text(encoding="utf-8"))
for name in ("personal_space_gate", "personal_space_return"):
    model = json.loads((assets / f"models/block/{name}.json").read_text())
    assert len(model["elements"]) <= 24
    faces = 0
    for location in model["textures"].values():
        namespace, texture = location.split(":", 1)
        image = root / f"src/main/resources/assets/{namespace}/textures/{texture}.png"
        data = image.read_bytes()
        assert data[:8] == b"\x89PNG\r\n\x1a\n"
        assert struct.unpack(">II", data[16:24]) == (64, 64)
    for element in model["elements"]:
        start, end = element["from"], element["to"]
        assert all(0 <= a < b <= 16 for a, b in zip(start, end))
        dx, dy, dz = [b-a for a, b in zip(start, end)]
        for direction, face in element["faces"].items():
            u0, v0, u1, v1 = face["uv"]
            assert all(0 <= uv <= 16 for uv in face["uv"])
            width, height = (dx, dy) if direction in ("north", "south") else (dz, dy) if direction in ("east", "west") else (dx, dz)
            assert abs(abs(u1-u0)*4/width - 1) < 1e-6
            assert abs(abs(v1-v0)*4/height - 1) < 1e-6
            faces += 1
    states = json.loads((assets / f"blockstates/{name}.json").read_text())["variants"]
    assert set(states) == {f"facing={d}" for d in ("north", "south", "east", "west")}
    assert (root / f"art/assets/{name}.bbmodel").is_file()
    print(f"{name}: {len(model['elements'])} cubes, {faces} faces, 64x64 atlas, 1 pixel/model unit; passed")
print("Resource JSON and geometry/UV gates passed")
