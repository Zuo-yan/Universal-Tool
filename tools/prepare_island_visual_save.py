"""Create an isolated copy for runIslandVisual; never modifies the source save."""
import argparse
import shutil
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("source", type=Path, help="Existing Minecraft save folder containing level.dat")
args = parser.parse_args()
source = args.source.resolve()
assert (source / "level.dat").is_file(), "Source is not a Minecraft save"
root = Path(__file__).resolve().parents[1]
target = root / "build/island-visual/saves/island-visual"
if target.exists():
    raise SystemExit("An isolated copy already exists; retained without overwriting it.")
shutil.copytree(source, target)
print("Copied save to", target)
