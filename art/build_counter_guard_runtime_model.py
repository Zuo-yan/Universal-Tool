import base64
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "art" / "models" / "counter_guard_shield_v3.bbmodel"
TEXTURE = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "universal_tool"
    / "textures"
    / "item"
    / "counter_guard_shield.png"
)
OUTPUT = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "universal_tool"
    / "models"
    / "item"
    / "counter_guard_shield.json"
)


def main() -> None:
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    TEXTURE.write_bytes(
        base64.b64decode(source["textures"][0]["source"].split(",", 1)[1])
    )
    runtime = {
        "credit": "Counter Guard shield render proxy",
        "parent": "builtin/entity",
        "textures": {
            "0": "universal_tool:item/counter_guard_shield",
            "particle": "universal_tool:item/counter_guard_shield",
        },
    }
    OUTPUT.write_text(
        json.dumps(runtime, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
