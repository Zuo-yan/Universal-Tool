"""
Deterministic generator for all 16x16 Jappa Arcane-Tech item textures.
Every sprite uses strict binary alpha (0 or 255) and a unified Arcane-Tech palette.

Run from the repository root:
    python tools/generate_all_assets.py
"""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ITEM_DIR = ROOT / "src/main/resources/assets/universal_tool/textures/item"

# Unified Arcane-Tech Palette (RGBA)
TRANSPARENT = (0, 0, 0, 0)

DS_VOID = (0x14, 0x16, 0x1D, 0xFF)
DS_DARK = (0x1E, 0x22, 0x2D, 0xFF)
DS_MID = (0x2B, 0x31, 0x40, 0xFF)
DS_LIGHT = (0x3E, 0x46, 0x59, 0xFF)
DS_EDGE = (0x56, 0x60, 0x78, 0xFF)

GOLD_DARK = (0x7A, 0x54, 0x1E, 0xFF)
GOLD_MID = (0xB3, 0x80, 0x2C, 0xFF)
GOLD_BRIGHT = (0xDF, 0xAD, 0x34, 0xFF)
GOLD_SHINE = (0xF5, 0xD6, 0x78, 0xFF)

AME_DARK = (0x4A, 0x25, 0x74, 0xFF)
AME_MID = (0x77, 0x42, 0xB8, 0xFF)
AME_BRIGHT = (0xA4, 0x64, 0xE8, 0xFF)
AME_GLOW = (0xD8, 0xA8, 0xFF, 0xFF)

CYAN_DARK = (0x17, 0x4B, 0x58, 0xFF)
CYAN_MID = (0x28, 0x82, 0x94, 0xFF)
CYAN_BRIGHT = (0x42, 0xC6, 0xD2, 0xFF)
CYAN_GLOW = (0x9C, 0xF5, 0xF0, 0xFF)
ICE_WHITE = (0xE6, 0xFC, 0xFF, 0xFF)


class Sprite16:
    def __init__(self) -> None:
        self.px = [[TRANSPARENT for _ in range(16)] for _ in range(16)]

    def set(self, x: int, y: int, rgba: tuple[int, int, int, int]) -> None:
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = rgba

    def paint_grid(self, rows: list[str], legend: dict[str, tuple[int, int, int, int]]) -> "Sprite16":
        assert len(rows) == 16, f"Expected 16 rows, got {len(rows)}"
        for y, row in enumerate(rows):
            assert len(row) == 16, f"Row {y} length {len(row)} != 16"
            for x, ch in enumerate(row):
                if ch != ".":
                    self.px[y][x] = legend[ch]
        return self

    def opaque_count(self) -> int:
        return sum(1 for row in self.px for _, _, _, a in row if a == 255)

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for rgba in row:
                assert rgba[3] in (0, 255), f"Non-binary alpha {rgba} in {path.name}"
                raw.extend(rgba)

        def chunk(tag: bytes, data: bytes) -> bytes:
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        ihdr = struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)
        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
        path.write_bytes(png)


def make_klein_bottle() -> Sprite16:
    # 16x16 self-looping Klein Bottle with brass collar, cyan glass rim, and amethyst vortex core
    rows = [
        "......OOOO......",
        ".....OGYYGO.....",
        "....OCGYYGCO....",
        "....OCB..BCOO...",
        "....OCB..OCBBO..",
        "...OOCM..OCB.BO.",
        "..OCBBM..OCM.BO.",
        ".OCBMMAAAAC..BO.",
        ".OCMAAVVPPCOBOO.",
        ".OCMAVWWVVCCBO..",
        ".OCMAVPVPPAMO...",
        ".OCMMAAVAAMMO...",
        "..OCMMAAAAMCO...",
        "...OCCMMMMCO....",
        "....OOOOOOOO....",
        "................",
    ]
    legend = {
        "O": DS_VOID,
        "G": GOLD_MID,
        "Y": GOLD_SHINE,
        "C": CYAN_MID,
        "B": CYAN_BRIGHT,
        "M": CYAN_DARK,
        "A": AME_MID,
        "V": AME_BRIGHT,
        "P": AME_GLOW,
        "W": CYAN_GLOW,
    }
    return Sprite16().paint_grid(rows, legend)


def make_universal_tool(tier: str) -> Sprite16:
    # Tier-evolving composite halberd-paxel silhouettes (wooden -> netherite)
    palettes = {
        "wooden": {
            "O": (0x24, 0x17, 0x0C, 0xFF),
            "S": (0x6B, 0x48, 0x26, 0xFF),
            "H": (0x9C, 0x6D, 0x3B, 0xFF),
            "D": (0x54, 0x38, 0x1D, 0xFF),
            "M": (0x8F, 0x62, 0x33, 0xFF),
            "L": (0xB8, 0x84, 0x4B, 0xFF),
            "E": (0xD4, 0xA2, 0x66, 0xFF),
            "C": (0x7A, 0x54, 0x1E, 0xFF),
            "G": (0xB3, 0x80, 0x2C, 0xFF),
            "J": (0x42, 0xC6, 0xD2, 0xFF),
        },
        "stone": {
            "O": (0x1B, 0x1D, 0x22, 0xFF),
            "S": (0x5B, 0x3E, 0x22, 0xFF),
            "H": (0x82, 0x5B, 0x34, 0xFF),
            "D": (0x4A, 0x4E, 0x57, 0xFF),
            "M": (0x6E, 0x73, 0x7E, 0xFF),
            "L": (0x93, 0x99, 0xA6, 0xFF),
            "E": (0xB8, 0xBF, 0xCC, 0xFF),
            "C": (0x5C, 0x46, 0x28, 0xFF),
            "G": (0x99, 0x77, 0x3B, 0xFF),
            "J": (0x42, 0xC6, 0xD2, 0xFF),
        },
        "iron": {
            "O": (0x18, 0x1B, 0x22, 0xFF),
            "S": (0x54, 0x39, 0x20, 0xFF),
            "H": (0x7C, 0x56, 0x30, 0xFF),
            "D": (0x69, 0x72, 0x80, 0xFF),
            "M": (0x9A, 0xA4, 0xB4, 0xFF),
            "L": (0xCC, 0xD6, 0xE6, 0xFF),
            "E": (0xF0, 0xF5, 0xFC, 0xFF),
            "C": GOLD_DARK,
            "G": GOLD_MID,
            "J": CYAN_BRIGHT,
        },
        "golden": {
            "O": (0x26, 0x18, 0x0C, 0xFF),
            "S": (0x5E, 0x33, 0x24, 0xFF),
            "H": (0x8A, 0x4E, 0x34, 0xFF),
            "D": GOLD_DARK,
            "M": GOLD_MID,
            "L": GOLD_BRIGHT,
            "E": GOLD_SHINE,
            "C": AME_DARK,
            "G": AME_BRIGHT,
            "J": CYAN_GLOW,
        },
        "diamond": {
            "O": (0x12, 0x1B, 0x26, 0xFF),
            "S": (0x3A, 0x2B, 0x48, 0xFF),
            "H": (0x5C, 0x42, 0x70, 0xFF),
            "D": CYAN_DARK,
            "M": CYAN_MID,
            "L": CYAN_BRIGHT,
            "E": CYAN_GLOW,
            "C": GOLD_MID,
            "G": GOLD_BRIGHT,
            "J": AME_GLOW,
        },
        "netherite": {
            "O": DS_VOID,
            "S": DS_DARK,
            "H": DS_MID,
            "D": DS_MID,
            "M": DS_LIGHT,
            "L": AME_MID,
            "E": AME_GLOW,
            "C": GOLD_MID,
            "G": GOLD_SHINE,
            "J": CYAN_GLOW,
        },
    }

    if tier == "wooden":
        rows = [
            "................",
            "........OOOOO...",
            ".......OMLLEEO..",
            "......OMLDDMEO..",
            ".....OMLDOMLO...",
            ".....OLD.OCHO...",
            ".....OO..OSHO...",
            "........OSHO....",
            ".......OSHO.....",
            "......OSHO......",
            ".....OSHO.......",
            "....OSHO........",
            "...OSHO.........",
            "..OSHO..........",
            "..OCO...........",
            "..OO............",
        ]
    elif tier == "stone":
        rows = [
            "................",
            ".......OOOOOO...",
            "......OMLLEEEO..",
            ".....OMLDDMMEO..",
            "....OMLDOOMLO...",
            "....OLDO.OCHO...",
            "....OMDOOSHO....",
            ".....OOOSHO.....",
            ".......OSHO.....",
            "......OSHO......",
            ".....OSHO.......",
            "....OSHO........",
            "...OSHO.........",
            "..OSHO..........",
            "..OGO...........",
            "..OO............",
        ]
    elif tier == "iron":
        rows = [
            "............OOO.",
            "......OOOOOOEEO.",
            ".....OMLLLEEEO..",
            "....OMLDDMMLEO..",
            "....OMLOOOMLO...",
            "...OMLDO.OGGO...",
            "...OLDO.OSJGO...",
            "...OMDOOSHO.....",
            "....OOOSHO......",
            "......OSHO......",
            ".....OSHO.......",
            "....OSHO........",
            "...OSHO.........",
            "..OSHO..........",
            ".OGGO...........",
            ".OOO............",
        ]
    elif tier == "golden":
        rows = [
            "............OOO.",
            "......OOOOOOEEO.",
            ".....OMLLLEEEO..",
            "....OMLDDMMLEEO.",
            "...OMLDOOOMLLO..",
            "...OLDO..OGJGO..",
            "...OMDO.OGJSO...",
            "....OO.OSHO.....",
            "......OSHO......",
            ".....OSHO.......",
            "....OSHO........",
            "...OSHO.........",
            "..OSHO..........",
            ".OGHO...........",
            ".OCO............",
            ".OO.............",
        ]
    elif tier == "diamond":
        rows = [
            "...........OOOO.",
            ".....OOOOOOEEEO.",
            "....OMLLLEEEEO..",
            "...OMLDDMMMLEEO.",
            "...OMLOOOMMLLO..",
            "..OMLDO.OGJJGO..",
            "..OLDO..OGJSO...",
            "..OMDO.OSHO.....",
            "...OO..OSHO.....",
            "......OSHO......",
            ".....OSHO.......",
            "....OSHO........",
            "...OSHO.........",
            "..OGHO..........",
            ".OGCO...........",
            ".OOO............",
        ]
    else:  # netherite
        rows = [
            "...........OOOO.",
            "....OOOOOOOEEEO.",
            "...OMLLLLEEEEEO.",
            "...OMLDDDMMLEEO.",
            "..OMLDOOOMMLLO..",
            "..OMLDO.OGJJGO..",
            "..OLLDOOGJGSO...",
            "..OMDDOOSHO.....",
            "...OOOOOSHO.....",
            "......OSHO......",
            ".....OSHO.......",
            "....OSHO........",
            "...OSHO.........",
            "..OGHO..........",
            ".OGJCO..........",
            ".OOOO...........",
        ]

    return Sprite16().paint_grid(rows, palettes[tier])


def make_dna_syringe() -> Sprite16:
    # Fuller >70 opaque px silhouette: brass plunger top-right, glowing cyan-amethyst barrel, steel needle bottom-left
    rows = [
        "...........OOOO.",
        "..........OGYYO.",
        "..........OGGO..",
        "........OOGGGOO.",
        ".......OGGYGGYO.",
        "......OCBBWWBO..",
        ".....OCBWWAVBO..",
        "....OCBWAABBO...",
        "...OCBBAAWBBO...",
        "..OCBWWABBMO....",
        "..OCBBWBMO......",
        "..OGGGMOO.......",
        "..OSSO..........",
        ".OSSO...........",
        "OSSO............",
        "OO..............",
    ]
    legend = {
        "O": DS_VOID,
        "G": GOLD_MID,
        "Y": GOLD_SHINE,
        "C": CYAN_MID,
        "B": CYAN_BRIGHT,
        "W": CYAN_GLOW,
        "A": AME_BRIGHT,
        "V": AME_GLOW,
        "M": CYAN_DARK,
        "S": (0xC4, 0xD2, 0xE2, 0xFF),
    }
    s = Sprite16().paint_grid(rows, legend)
    assert s.opaque_count() > 70, f"dna_syringe opaque count {s.opaque_count()} <= 70"
    return s


def make_dna_sample() -> Sprite16:
    rows = [
        "................",
        ".....OOOOOO.....",
        "....OGYYYYGO....",
        "....OGGGGGGO....",
        "....OCBBBBCO....",
        "....OCWAAWCO....",
        "....OCBAWBCO....",
        "....OCAABWCO....",
        "....OCWBABCO....",
        "....OCAAWBCO....",
        "....OCBWWACO....",
        "....OCMMMMCO....",
        "....OGGGGGGO....",
        ".....OOOOOO.....",
        "................",
        "................",
    ]
    legend = {
        "O": DS_VOID,
        "G": GOLD_MID,
        "Y": GOLD_SHINE,
        "C": CYAN_MID,
        "B": CYAN_DARK,
        "W": CYAN_GLOW,
        "A": AME_GLOW,
        "M": CYAN_BRIGHT,
    }
    return Sprite16().paint_grid(rows, legend)


def make_nutrient_solution() -> Sprite16:
    rows = [
        "................",
        "......OOOO......",
        ".....OGYYGO.....",
        ".....OGGGGO.....",
        "......OCCO......",
        ".....OCWWCO.....",
        "....OCBWWBCO....",
        "...OCBBAAABCO...",
        "..OCBAAVVVABCO..",
        "..OCBWAVVVWBCO..",
        "..OCBWWAAWWBCO..",
        "..OCBBBBBBBBCO..",
        "...OCMMMMMMCO...",
        "....OGGGGGGO....",
        ".....OOOOOO.....",
        "................",
    ]
    legend = {
        "O": DS_VOID,
        "G": GOLD_MID,
        "Y": GOLD_SHINE,
        "C": CYAN_MID,
        "B": CYAN_BRIGHT,
        "W": CYAN_GLOW,
        "A": AME_BRIGHT,
        "V": AME_GLOW,
        "M": CYAN_DARK,
    }
    return Sprite16().paint_grid(rows, legend)


def make_teleport_codex() -> Sprite16:
    # Dark deepslate grimoire with gilded brass corners and glowing Void/Astrolabe eye
    rows = [
        "................",
        "..OOOOOOOOOOOO..",
        ".OYYDDDDDDDDYYO.",
        ".OGGDMMMMMMDGGO.",
        ".ODDMMAAAAMMDDDO",
        ".ODDMAVCCVAMDDDO",
        ".ODDMVCWWCVMDDDO",
        ".OGGMVWWWWVMGGO.",
        ".OGGMVCWWCVMGGO.",
        ".ODDMAVCCVAMDDDO",
        ".ODDMMAAAAMMDDDO",
        ".ODDMMMMMMMMDDDO",
        ".OGGDMMMMMMDGGO.",
        ".OYYGGGGGGGGYYO.",
        "..OOOOOOOOOOOO..",
        "................",
    ]
    legend = {
        "O": DS_VOID,
        "D": DS_DARK,
        "M": DS_MID,
        "G": GOLD_MID,
        "Y": GOLD_SHINE,
        "A": AME_MID,
        "V": AME_BRIGHT,
        "C": CYAN_BRIGHT,
        "W": CYAN_GLOW,
    }
    return Sprite16().paint_grid(rows, legend)


def make_spatial_page() -> Sprite16:
    rows = [
        "................",
        "...OOOOOOOOO....",
        "..OPPPPPPGGPO...",
        "..OPCCPPPPPPPO..",
        "..OPCPPPPPWPPPO.",
        "..OPPPPAAAPPPPO.",
        "..OPPPACWCAPPPPO",
        "..OPPPWWWWPPPPO.",
        "..OPPPACWCAPPPPO",
        "..OPPPPAAAPPPPO.",
        "..OPWPPPPPPPCCO.",
        "..OPPPPPPPPPCCO.",
        "..OPGGGGGGGGGO..",
        "...OOOOOOOOOO...",
        "................",
        "................",
    ]
    legend = {
        "O": DS_VOID,
        "P": (0xEA, 0xDA, 0xBE, 0xFF),
        "G": GOLD_BRIGHT,
        "C": CYAN_MID,
        "W": CYAN_GLOW,
        "A": AME_BRIGHT,
    }
    return Sprite16().paint_grid(rows, legend)


def make_teleport_scroll() -> Sprite16:
    rows = [
        "................",
        ".......OOOOOO...",
        "......OGPPPPGO..",
        ".....OPPWWPPPO..",
        "....OPPPCCPPPO..",
        "....OPPPPPPPO...",
        "...OGGYVVYGGO...",
        "...OGYVWVVYGO...",
        "...OPPPGGPPPO...",
        "..OPPPPPPPPO....",
        "..OPPPCCPPPO....",
        ".OGPPPPPPGO.....",
        ".OOGGGGGGO......",
        "...OOOOOO.......",
        "................",
        "................",
    ]
    legend = {
        "O": DS_VOID,
        "P": (0xEA, 0xDA, 0xBE, 0xFF),
        "G": GOLD_MID,
        "Y": GOLD_SHINE,
        "C": CYAN_BRIGHT,
        "W": CYAN_GLOW,
        "V": AME_BRIGHT,
    }
    return Sprite16().paint_grid(rows, legend)


def make_universal_repair_material() -> Sprite16:
    # Balanced Arcane-Tech prism crystal in gilded housing (no harsh pure #FFFFFF)
    rows = [
        ".......OO.......",
        "......OGYO......",
        ".....OGCCGO.....",
        "....OGCWWCGO....",
        "...OGCBWWBCGO...",
        "..OGCBAVVABCGO..",
        ".OGCBAVWWVABCGO.",
        ".OYCBWAAAAWBCYO.",
        ".OGCBAVWWVABCGO.",
        "..OGCBAVVABCGO..",
        "...OGCBMMBCGO...",
        "....OGCMMCGO....",
        ".....OGMMGO.....",
        "......OGGO......",
        ".......OO.......",
        "................",
    ]
    legend = {
        "O": DS_VOID,
        "G": GOLD_MID,
        "Y": GOLD_SHINE,
        "C": CYAN_BRIGHT,
        "B": CYAN_MID,
        "M": CYAN_DARK,
        "W": CYAN_GLOW,
        "A": AME_BRIGHT,
        "V": AME_GLOW,
    }
    return Sprite16().paint_grid(rows, legend)


def make_void_scrap() -> Sprite16:
    rows = [
        "................",
        ".....OOO........",
        "....ODMMO.......",
        "...ODMAAMO..OO..",
        "..ODMAVVMO.OGYO.",
        "..ODMAVMO.OGMDO.",
        "...ODMMO.OCMDO..",
        "..OODMMODCMDO...",
        ".ODDDMMMAAMO....",
        ".ODGDMAAVMO.....",
        ".OGYDDMMMO......",
        "..OODDMDO.......",
        "....OOOO........",
        "................",
        "................",
        "................",
    ]
    legend = {
        "O": DS_VOID,
        "D": DS_DARK,
        "M": DS_LIGHT,
        "A": AME_MID,
        "V": AME_GLOW,
        "G": GOLD_MID,
        "Y": GOLD_BRIGHT,
        "C": CYAN_BRIGHT,
    }
    return Sprite16().paint_grid(rows, legend)


def main() -> None:
    generators = {
        "klein_bottle.png": make_klein_bottle,
        "wooden_universal_tool.png": lambda: make_universal_tool("wooden"),
        "stone_universal_tool.png": lambda: make_universal_tool("stone"),
        "iron_universal_tool.png": lambda: make_universal_tool("iron"),
        "golden_universal_tool.png": lambda: make_universal_tool("golden"),
        "diamond_universal_tool.png": lambda: make_universal_tool("diamond"),
        "netherite_universal_tool.png": lambda: make_universal_tool("netherite"),
        "dna_syringe.png": make_dna_syringe,
        "dna_sample.png": make_dna_sample,
        "nutrient_solution.png": make_nutrient_solution,
        "teleport_codex.png": make_teleport_codex,
        "spatial_page.png": make_spatial_page,
        "teleport_scroll.png": make_teleport_scroll,
        "universal_repair_material.png": make_universal_repair_material,
        "void_scrap.png": make_void_scrap,
    }
    for filename, fn in generators.items():
        sprite = fn()
        out_path = ITEM_DIR / filename
        sprite.save(out_path)
        print(f"Generated 16x16 {filename} (opaque={sprite.opaque_count()} px)")


if __name__ == "__main__":
    main()
