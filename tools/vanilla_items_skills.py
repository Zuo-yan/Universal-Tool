"""
Generate Vanilla+ Jappa (1.14+) pixel-art textures for:
- 17 items (16x16 items + 32x32 1x-texel-density lightning_spear.png)
- 20 skill icons (16x16 transparent-background Vanilla status-effect style)
- 2 Counter Guard effect textures (32x32 seamless aegis lattice + 64x64 pixel rune ring)

Also updates models/item/lightning_spear.json UVs and texture_size to [32, 32]
for crisp 1x texel density.
"""

from __future__ import annotations

import json
import math
import os
from pathlib import Path
from typing import Dict, List, Tuple

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX_ROOT = ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "textures"
ITEM_DIR = TEX_ROOT / "item"
SKILL_DIR = TEX_ROOT / "gui" / "skills"
MISC_DIR = TEX_ROOT / "misc"
SPEAR_MODEL_PATH = (
    ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "models" / "item" / "lightning_spear.json"
)

RGBA = Tuple[int, int, int, int]


def hex_rgba(h: str, a: int = 255) -> RGBA:
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def from_grid(rows: List[str], palette: Dict[str, RGBA]) -> Image.Image:
    h = len(rows)
    w = len(rows[0])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        if len(row) != w:
            raise ValueError(f"Row {y} length {len(row)} != {w}")
        for x, ch in enumerate(row):
            if ch in (".", " "):
                continue
            if ch not in palette:
                raise KeyError(f"Unknown palette char {ch!r} at ({x}, {y})")
            c = palette[ch]
            if c[0] == 0 and c[1] == 0 and c[2] == 0 and c[3] > 0:
                raise ValueError(f"Pure black pixel forbidden at ({x}, {y})")
            px[x, y] = c
    return img


# ---------------------------------------------------------------------------
# Shared Jappa Vanilla+ Palettes (Zero #000000!)
# ---------------------------------------------------------------------------

OAK_HANDLE = {
    "k": hex_rgba("#281D0D"),  # dark oak outline
    "w": hex_rgba("#4A3516"),  # shadow
    "W": hex_rgba("#6E4F22"),  # mid
    "h": hex_rgba("#8E692E"),  # highlight
}

NETHERITE_HANDLE = {
    "k": hex_rgba("#1E1820"),  # dark obsidian outline
    "w": hex_rgba("#322A34"),  # shadow
    "W": hex_rgba("#4A3E4C"),  # mid
    "h": hex_rgba("#665668"),  # highlight
}

TOOL_HEAD_PALETTES: Dict[str, Dict[str, RGBA]] = {
    "wooden": {
        "o": hex_rgba("#382611"),  # dark wood outline
        "d": hex_rgba("#6B4E26"),  # shadow
        "m": hex_rgba("#946E3A"),  # mid
        "l": hex_rgba("#B38B52"),  # highlight
        "s": hex_rgba("#CFA86E"),  # specular edge
        "b": hex_rgba("#523919"),  # collar
    },
    "stone": {
        "o": hex_rgba("#303036"),  # slate outline
        "d": hex_rgba("#5E5E66"),  # shadow
        "m": hex_rgba("#82828A"),  # mid
        "l": hex_rgba("#A3A3AB"),  # highlight
        "s": hex_rgba("#C2C2C9"),  # specular edge
        "b": hex_rgba("#4A4A52"),  # collar
    },
    "iron": {
        "o": hex_rgba("#3E434C"),  # deep slate outline
        "d": hex_rgba("#7E8591"),  # shadow
        "m": hex_rgba("#B4BAC4"),  # mid
        "l": hex_rgba("#E1E5EB"),  # highlight
        "s": hex_rgba("#FFFFFF"),  # specular edge
        "b": hex_rgba("#636973"),  # collar
    },
    "golden": {
        "o": hex_rgba("#59350A"),  # warm sepia-brown outline
        "d": hex_rgba("#B56F10"),  # shadow
        "m": hex_rgba("#E6A81E"),  # mid
        "l": hex_rgba("#FCE158"),  # highlight
        "s": hex_rgba("#FFFAD1"),  # specular edge
        "b": hex_rgba("#8F540D"),  # collar
    },
    "diamond": {
        "o": hex_rgba("#0F4247"),  # dark teal-navy outline
        "d": hex_rgba("#1C8C8B"),  # shadow
        "m": hex_rgba("#32C7C0"),  # mid
        "l": hex_rgba("#7AF2EC"),  # highlight
        "s": hex_rgba("#DEFEFF"),  # specular edge
        "b": hex_rgba("#166669"),  # collar
    },
    "netherite": {
        "o": hex_rgba("#1E1821"),  # deep purple-obsidian outline
        "d": hex_rgba("#362E38"),  # shadow
        "m": hex_rgba("#4E4452"),  # mid
        "l": hex_rgba("#6E6273"),  # highlight
        "s": hex_rgba("#998B9E"),  # specular edge
        "b": hex_rgba("#8A5B2A"),  # gilded netherite rivet/collar
    },
}

# Classic paxel / universal tool 16x16 silhouette:
# Top-left curved axe blade, top-right adze/shovel ridge, right-down pick point, diagonal handle.
UNIVERSAL_TOOL_GRID = [
    "......oooooo....",
    "....oosllmmmo...",
    "...osllmmmdddo..",
    "..oslmmddbbbddo.",
    "..olmdoobWbbddo.",
    "..oodo.kWhWbooo.",
    "......kWhWk..o..",
    ".....kWhWk......",
    "....kWhWk.......",
    "...kWhWk........",
    "..kWhWk.........",
    ".kWhWk..........",
    ".kWWk...........",
    ".kWk............",
    "..k.............",
    "................",
]


def make_universal_tools() -> Dict[str, Image.Image]:
    out = {}
    for tier, head_pal in TOOL_HEAD_PALETTES.items():
        handle_pal = NETHERITE_HANDLE if tier == "netherite" else OAK_HANDLE
        pal = {**handle_pal, **head_pal}
        out[f"{tier}_universal_tool.png"] = from_grid(UNIVERSAL_TOOL_GRID, pal)
    return out


def make_items() -> Dict[str, Image.Image]:
    items = make_universal_tools()

    # 7. klein_bottle — Vanilla Potion/Bottle glass + cork + swirling amethyst/ender essence inside
    items["klein_bottle.png"] = from_grid(
        [
            ".....oooooo.....",
            ".....ocCCco.....",
            "......oCCo......",
            ".....ogGGgo.....",
            "....ogGssGgo....",
            "...ogGsmmsGgooo.",
            "..ogGspppGgoGGo.",
            "..ogGppPPpsGggo.",
            ".ogGspPPAApsGgo.",
            ".ogGsPAAaApsGgo.",
            ".ogGspAAaAPsGgo.",
            ".ogGsppppppsGgo.",
            "..ogGsppppsGgo..",
            "..oogGGGGGGgoo..",
            "...oooooooooo...",
            "................",
        ],
        {
            "o": hex_rgba("#224959"),  # dark sea-glass outline
            "g": hex_rgba("#5291A6"),  # glass shadow
            "G": hex_rgba("#8AD2E3"),  # glass mid
            "s": hex_rgba("#DCF8FF"),  # glass highlight / glint
            "c": hex_rgba("#8C5B2B"),  # cork shadow
            "C": hex_rgba("#BA864C"),  # cork highlight
            "p": hex_rgba("#B36CE8"),  # amethyst/ender fluid highlight
            "P": hex_rgba("#8238BA"),  # fluid mid
            "A": hex_rgba("#592287"),  # fluid deep
            "a": hex_rgba("#5CE3CA"),  # ender swirl glint
            "m": hex_rgba("#C295F2"),  # meniscus
        },
    )

    # 8. nutrient_solution — Vanilla Potion/Exp-Bottle style flask with vibrant kelp-emerald nutrient broth
    items["nutrient_solution.png"] = from_grid(
        [
            "................",
            ".....oooooo.....",
            ".....ocCCco.....",
            "......oCCo......",
            ".....ogGGgo.....",
            "....ogGssGgo....",
            "...ogGsGGsGgo...",
            "..ogGsllllsGgo..",
            "..ogGlLLLllGgo..",
            ".ogGslLLLddGggo.",
            ".ogGsLLLdddGggo.",
            ".ogGslLddddGggo.",
            "..ogGddddddGgo..",
            "..oogGGGGGGgoo..",
            "...oooooooooo...",
            "................",
        ],
        {
            "o": hex_rgba("#224857"),  # dark glass outline
            "g": hex_rgba("#4E8DA3"),  # glass shadow
            "G": hex_rgba("#89D0E0"),  # glass mid
            "s": hex_rgba("#E0FAFF"),  # glass specular
            "c": hex_rgba("#8C5B2B"),  # cork shadow
            "C": hex_rgba("#BD884E"),  # cork highlight
            "l": hex_rgba("#B6F783"),  # lime nutrient highlight
            "L": hex_rgba("#5CCC45"),  # emerald nutrient mid
            "d": hex_rgba("#2B8A35"),  # deep moss green
        },
    )

    # 9. dna_sample — Vanilla glass vial with beveled brass caps & red/lapis double-helix
    items["dna_sample.png"] = from_grid(
        [
            "................",
            "....oooooooo....",
            "...oyYYyyyyyo...",
            "....oGGGGGGgo...",
            "....oGsrBbgGo...",
            "....oGsRrbGgo...",
            "....oGsbRrgGo...",
            "....oGsBbrGgo...",
            "....oGsrBbgGo...",
            "....oGsRrbGgo...",
            "....oGsbRrgGo...",
            "....oGGGGGGgo...",
            "...oyYYyyyyyo...",
            "....oooooooo....",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#3D3120"),  # warm dark bronze outline
            "y": hex_rgba("#A87118"),  # brass shadow
            "Y": hex_rgba("#F2C249"),  # brass highlight
            "g": hex_rgba("#649EB0"),  # glass shadow
            "G": hex_rgba("#BFEAF2"),  # pale glass fill
            "s": hex_rgba("#FFFFFF"),  # crisp glass glint
            "r": hex_rgba("#E86A6A"),  # helix coral light
            "R": hex_rgba("#B82B2B"),  # helix coral dark
            "b": hex_rgba("#67A3F0"),  # helix lapis light
            "B": hex_rgba("#2D5AB3"),  # helix lapis dark
        },
    )

    # 10. dna_syringe — Diagonal Vanilla iron-needle + glass barrel + copper plunger
    items["dna_syringe.png"] = from_grid(
        [
            "................",
            "...........oooo.",
            "..........ocCCo.",
            ".........ocCco..",
            "........oGGGGgo.",
            ".......oGssggo..",
            "......oGsllGgo..",
            ".....oGsllLGgo..",
            "....oGslLLGgo...",
            "...oGsLLdGgo....",
            "...oGdddGgo.....",
            "..oiioGggo......",
            ".oSioooo........",
            ".oSo............",
            ".oo.............",
            "................",
        ],
        {
            "o": hex_rgba("#2B3D47"),  # cool slate-glass outline
            "c": hex_rgba("#9C542B"),  # copper plunger shadow
            "C": hex_rgba("#DE8C52"),  # copper plunger highlight
            "g": hex_rgba("#5B98AB"),  # glass shadow
            "G": hex_rgba("#A6E2EE"),  # glass mid
            "s": hex_rgba("#E8FCFF"),  # glass specular
            "l": hex_rgba("#8FF2A6"),  # emerald fluid highlight
            "L": hex_rgba("#3EC273"),  # fluid mid
            "d": hex_rgba("#207A4B"),  # fluid shadow
            "i": hex_rgba("#9DA4B0"),  # iron needle mid
            "S": hex_rgba("#FFFFFF"),  # needle edge glint
        },
    )

    # 11. teleport_codex — Vanilla Enchanted Book crimson leather + gold corners + ender inlay
    items["teleport_codex.png"] = from_grid(
        [
            "................",
            "..oooooooooooo..",
            ".oyYYrrrrrrYYyo.",
            ".oyYrrrrrrrrrYo.",
            ".orrRRRRRRRrrPo.",
            ".oyYRRrEErRRYPo.",
            ".orrRREeeERRrPo.",
            ".orrRREssERRrPo.",
            ".orrRREeeERRrPo.",
            ".oyYRRrEErRRYPo.",
            ".orrRRRRRRRrrPo.",
            ".oyYrrrrrrrrrYo.",
            ".oyYYrrrrrrYYyo.",
            "..oooPPPPPPooo..",
            "....oooooooo....",
            "................",
        ],
        {
            "o": hex_rgba("#3B141C"),  # deep crimson-umber outline
            "r": hex_rgba("#9C2331"),  # leather highlight
            "R": hex_rgba("#731622"),  # leather shadow
            "y": hex_rgba("#B57718"),  # gold clasp shadow
            "Y": hex_rgba("#F7CE46"),  # gold clasp highlight
            "P": hex_rgba("#E0D2B4"),  # parchment page edge
            "E": hex_rgba("#2C8A78"),  # ender-jade inlay shadow
            "e": hex_rgba("#67DEC2"),  # ender-jade mid
            "s": hex_rgba("#D6FFF4"),  # inlay glint
        },
    )

    # 12. teleport_scroll — Vanilla rolled parchment scroll with curled ends + crimson ribbon + amethyst seal
    items["teleport_scroll.png"] = from_grid(
        [
            "................",
            ".......oooooo...",
            "......opPssPmo..",
            ".....opPssPppo..",
            "....opPpppPmo...",
            "....opPppppo....",
            "...opPprRrrpo...",
            "...opPprRaaRpo..",
            "..opPprRaaRpo...",
            "..opPprrRRrpo...",
            "..opPppppppo....",
            ".ompPppPppo.....",
            ".opPssPppo......",
            "..ooooooo.......",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#594227"),  # warm sepia outline
            "p": hex_rgba("#C7AB7B"),  # parchment shadow
            "P": hex_rgba("#E8D6AC"),  # parchment mid
            "s": hex_rgba("#FAF0D2"),  # parchment highlight
            "m": hex_rgba("#99784F"),  # scroll spiral shadow
            "r": hex_rgba("#B82B36"),  # crimson ribbon highlight
            "R": hex_rgba("#7D1720"),  # crimson ribbon shadow
            "a": hex_rgba("#C27DF2"),  # amethyst wax seal
        },
    )

    # 13. spatial_page — Vanilla dog-eared map/parchment sheet with sepia runes & ender-teal glyph
    items["spatial_page.png"] = from_grid(
        [
            "................",
            "..ooooooooooo...",
            "..osssPPPPPPo...",
            "..osPPmPPmPPo...",
            "..osPPPeePPPo...",
            "..oPPPeEEePPo...",
            "..oPmeEsseEmo...",
            "..oPPPeEEePPo...",
            "..osPPPeePPPo...",
            "..osPPmPPmPPo...",
            "..oPPPmmmppso...",
            "..oPPPPpppoo....",
            "..oopPPPpo......",
            "....ooooo.......",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#594227"),  # sepia outline
            "s": hex_rgba("#FAF0D2"),  # paper highlight
            "P": hex_rgba("#E8D6AC"),  # paper mid
            "p": hex_rgba("#C7AB7B"),  # paper shadow
            "m": hex_rgba("#99784F"),  # sepia ink mark
            "e": hex_rgba("#45B8A0"),  # ender-teal glyph mid
            "E": hex_rgba("#226E5F"),  # ender-teal glyph dark
            "S": hex_rgba("#B3FFF0"),  # glyph glint
        },
    )

    # 14. void_essence — Crisp faceted Vanilla Amethyst/Ender crystal + crisp 1px sparkles
    items["void_essence.png"] = from_grid(
        [
            "................",
            ".......oo.......",
            "......olso......",
            ".....olsslo.....",
            "....olllmllo....",
            "...ollmMmmdo....",
            "..olmmMmmdddo...",
            "..olmmMaddddo...",
            "...ommMdddoo....",
            "....omddddo.....",
            "....oodddo......",
            ".....oodo.......",
            "..a...oo....l...",
            "................",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#29133D"),  # deep amethyst-obsidian outline
            "s": hex_rgba("#F7E6FF"),  # crystal specular
            "l": hex_rgba("#D696FF"),  # amethyst highlight
            "m": hex_rgba("#A34FE3"),  # amethyst mid
            "M": hex_rgba("#7629B5"),  # inner facet
            "d": hex_rgba("#4D187D"),  # shadow facet
            "a": hex_rgba("#6FE8D2"),  # ender-teal core/sparkle
        },
    )

    # 15. void_scrap — Vanilla Netherite-Scrap jagged curved metal plate in obsidian-amethyst & echo-teal vein
    items["void_scrap.png"] = from_grid(
        [
            "................",
            "......oooooo....",
            "....oolmmdddo...",
            "...olmmdddddo...",
            "..olmmdoooddo...",
            "..omddo..oddo...",
            ".olmdo....ooo...",
            ".omddo...oeeo...",
            ".omddeo..oEEo...",
            "..omddEoooodo...",
            "..ooddEdddddo...",
            "...ooddddddpo...",
            ".....ooooooo....",
            "................",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#1E1526"),  # deep void-obsidian outline
            "l": hex_rgba("#9C82BA"),  # metallic highlight
            "m": hex_rgba("#685182"),  # mid
            "d": hex_rgba("#403154"),  # shadow
            "p": hex_rgba("#2C213B"),  # deep shadow
            "e": hex_rgba("#6CE3D2"),  # echo shard bright vein
            "E": hex_rgba("#2C948A"),  # echo shard mid vein
        },
    )

    # 16. universal_repair_material — Vanilla Gilded Gold bezel + Echo-Teal/Amethyst faceted core
    items["universal_repair_material.png"] = from_grid(
        [
            ".......oo.......",
            "......oYYo......",
            ".....oYssYo.....",
            "....oYyeeyYo....",
            "...oYyeEEeyYo...",
            "..oYyeEsseEyYo..",
            ".oYyeEssaaEEyYo.",
            ".oYyeEaAAaEEyYo.",
            "..oYyeEAAEEyYo..",
            "...oYyeEEeyYo...",
            "....oYyeeyYo....",
            ".....oYyyYo.....",
            "......oYYo......",
            ".......oo.......",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#543309"),  # warm sepia-gold outline
            "y": hex_rgba("#BA7816"),  # gold shadow
            "Y": hex_rgba("#F7CA44"),  # gold mid/highlight
            "s": hex_rgba("#E8FFFF"),  # white-cyan crystal specular
            "e": hex_rgba("#6CE8D5"),  # echo-teal highlight
            "E": hex_rgba("#25998D"),  # echo-teal shadow
            "a": hex_rgba("#C27CF5"),  # amethyst highlight
            "A": hex_rgba("#7630B3"),  # amethyst shadow
        },
    )

    return items


# ---------------------------------------------------------------------------
# 17. lightning_spear (32x32 1x texel density + UV update for lightning_spear.json)
# ---------------------------------------------------------------------------

def make_lightning_spear() -> Image.Image:
    """
    32x32 1x-texel-density atlas matching lightning_spear.json elements:
    - [0..2, 0..22]   shaft_main (2x22) -> Dark spruce wood shaft with subtle copper-gold spiral rivets
    - [3..6, 0..6]    shaft_grip (3x6) -> Stitched leather grip
    - [3..6, 7..8]    shaft_ring_lower (3x1) -> Gilded copper collar
    - [3..6, 9..10]   shaft_ring_upper (3x1) -> Gilded copper collar
    - [3..7, 11..13]  spearhead_base (4x2) -> Beveled copper-gold crossguard collar
    - [3..6, 14..18]  pommel_crystal (3x4) -> Faceted sky-lightning crystal
    - [3..5, 19..21]  pommel_finial (2x2) -> Gold finial cap
    - [8..11, 0..10]  spearhead_core north/south (3x10) -> Gold-edged blade with bright white-cyan lightning spine
    - [12..14, 0..10] spearhead_core east/west/up/down (2x10) -> Blade edge
    - [15..17, 0..5]  spearhead_tip (2x5) -> White-hot lightning spear tip
    - [8..11, 11..16] wing_lower (3x5) -> Copper-gold & sky-lightning lower prong
    - [12..15, 11..17] wing_tip (3x6) -> Sharp gold & electric-cyan prong tip
    """
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()

    def paint_block(x0: int, y0: int, rows: List[str], pal: Dict[str, RGBA]) -> None:
        sub = from_grid(rows, pal)
        img.paste(sub, (x0, y0))

    # Shaft main (2x22): spruce wood + subtle gold-copper lightning inlay dots
    shaft_pal = {
        "d": hex_rgba("#3D2816"),
        "m": hex_rgba("#5C3D22"),
        "l": hex_rgba("#78522E"),
        "g": hex_rgba("#E8B438"),
    }
    shaft_rows = [
        "lm", "md", "lm", "mg",
        "ld", "md", "gm", "md",
        "lm", "md", "lm", "mg",
        "lm", "md", "lm", "md",
        "gm", "md", "lm", "mg",
        "lm", "md",
    ]
    paint_block(0, 0, shaft_rows, shaft_pal)

    # Shaft grip (3x6): stitched leather
    grip_pal = {
        "d": hex_rgba("#4A2414"),
        "m": hex_rgba("#733A1E"),
        "l": hex_rgba("#944D28"),
        "s": hex_rgba("#C78932"),
    }
    paint_block(3, 0, ["lmd", "msd", "lmd", "lmd", "msd", "mdd"], grip_pal)

    # Rings (3x1 each)
    gold_pal = {
        "d": hex_rgba("#8F5612"),
        "m": hex_rgba("#D69A24"),
        "l": hex_rgba("#FFE46B"),
        "s": hex_rgba("#FFFAD6"),
    }
    paint_block(3, 7, ["lmd"], gold_pal)
    paint_block(3, 9, ["lmd"], gold_pal)

    # Spearhead base (4x2): copper-gold crossguard
    guard_pal = {
        "d": hex_rgba("#6E3B16"),
        "m": hex_rgba("#BA6D2C"),
        "l": hex_rgba("#F2B83D"),
        "s": hex_rgba("#FFF3A8"),
    }
    paint_block(3, 11, ["slmd", "mddd"], guard_pal)

    # Pommel crystal (3x4)
    crys_pal = {
        "d": hex_rgba("#1E75A3"),
        "m": hex_rgba("#4EC2EB"),
        "l": hex_rgba("#A8F2FF"),
        "s": hex_rgba("#FFFFFF"),
    }
    paint_block(3, 14, ["slm", "lmd", "lmd", "mdd"], crys_pal)

    # Pommel finial (2x2)
    paint_block(3, 19, ["lm", "md"], gold_pal)

    # Spearhead core north/south (3x10): gold frame + electric sky/white core
    blade_pal = {
        "d": hex_rgba("#8A5410"),
        "m": hex_rgba("#DB9B23"),
        "l": hex_rgba("#FFE466"),
        "c": hex_rgba("#52D1F5"),
        "w": hex_rgba("#E8FCFF"),
        "s": hex_rgba("#FFFFFF"),
    }
    paint_block(
        8, 0,
        [
            "lsl",
            "lwl",
            "mcw",
            "mwm",
            "wcw",
            "mwm",
            "lcl",
            "lwm",
            "mwm",
            "dmd",
        ],
        blade_pal,
    )

    # Spearhead core side/edges (2x10)
    paint_block(
        12, 0,
        [
            "sw", "wc", "lc", "mc", "wc",
            "mc", "lc", "mc", "md", "dd",
        ],
        blade_pal,
    )

    # Spearhead tip (2x5)
    paint_block(15, 0, ["sw", "sw", "wc", "lc", "lm"], blade_pal)

    # Wing lower (3x5)
    paint_block(8, 11, ["lcl", "lwl", "mwm", "mmd", "ddd"], blade_pal)

    # Wing tip (3x6)
    paint_block(12, 11, ["ssl", "lwl", "mcw", "mwm", "lmd", "ddd"], blade_pal)

    return img


def update_lightning_spear_model() -> None:
    data = json.loads(SPEAR_MODEL_PATH.read_text(encoding="utf-8"))
    data["texture_size"] = [32, 32]

    # Pixel coordinates [x0, y0, x1, y1] on 32x32 texture -> Minecraft [0..16] UV space (divide by 2)
    uv_px_by_name = {
        "shaft_main": (0, 0, 2, 22),
        "shaft_grip": (3, 0, 6, 6),
        "shaft_ring_lower": (3, 7, 6, 8),
        "shaft_ring_upper": (3, 9, 6, 10),
        "spearhead_base": (3, 11, 7, 13),
        "pommel_crystal": (3, 14, 6, 18),
        "pommel_finial": (3, 19, 5, 21),
        "spearhead_tip": (15, 0, 17, 5),
        "wing_left_lower": (8, 11, 11, 16),
        "wing_right_lower": (8, 11, 11, 16),
        "wing_left_tip": (12, 11, 15, 17),
        "wing_right_tip": (12, 11, 15, 17),
    }

    for elem in data["elements"]:
        name = elem["name"]
        if name == "spearhead_core":
            ns_uv = [8 / 2, 0 / 2, 11 / 2, 10 / 2]
            ew_uv = [12 / 2, 0 / 2, 14 / 2, 10 / 2]
            for face_name, face in elem["faces"].items():
                face["uv"] = ns_uv if face_name in ("north", "south") else ew_uv
        elif name in uv_px_by_name:
            x0, y0, x1, y1 = uv_px_by_name[name]
            uv = [x0 / 2, y0 / 2, x1 / 2, y1 / 2]
            for face in elem["faces"].values():
                face["uv"] = uv

    SPEAR_MODEL_PATH.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------------------
# 20 Skill Icons (16x16 Vanilla Status Effect / Advancement Style)
# ---------------------------------------------------------------------------

def make_skills() -> Dict[str, Image.Image]:
    skills: Dict[str, Image.Image] = {}

    # 1. air_step — Crisp Winged Golden Boot over sky-wind swooshes
    skills["air_step.png"] = from_grid(
        [
            "................",
            "..ooo....oooo...",
            ".ossbo..osYYyo..",
            ".osbBbo.oYYYYo..",
            "..osbBboyYYsyo..",
            "...osbBoYYssyo..",
            "....oooyYYYYyo..",
            "......oYYYYYYyo.",
            ".....oYYYYyyyyo.",
            ".....oooooooooo.",
            "................",
            "..wwwwwwwwww....",
            "....wwwwwwwwww..",
            "..wwwww.........",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#2B3D54"),  # deep slate-navy outline
            "s": hex_rgba("#FFFFFF"),  # wing/gold specular
            "b": hex_rgba("#BAE8FF"),  # wing light
            "B": hex_rgba("#6BBDF2"),  # wing mid
            "w": hex_rgba("#99E0FF"),  # crisp wind streaks
            "y": hex_rgba("#B37214"),  # boot shadow
            "Y": hex_rgba("#F7C844"),  # boot highlight
        },
    )

    # 2. armor_piercing — Golden spear piercing cleanly through cracked iron shield
    skills["armor_piercing.png"] = from_grid(
        [
            ".............oo.",
            "..ooooooooo.osYo",
            ".oiIIIIIIiioyYo.",
            ".oiIsImmIiIyYo..",
            ".oiIImmmIoyYo...",
            ".oiImmmdoyYoo...",
            ".oimmdoyYmdo....",
            "..oidyYimddo....",
            "..oiyYoimddo....",
            "...oyYidddo.....",
            "..oyyooddo......",
            ".oyYo..oo.......",
            ".ooo............",
            "................",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#2E323D"),  # slate outline
            "i": hex_rgba("#5E6673"),  # iron rim
            "I": hex_rgba("#DCE2EB"),  # iron highlight
            "s": hex_rgba("#FFFFFF"),  # glint
            "m": hex_rgba("#8A93A1"),  # iron face
            "d": hex_rgba("#505763"),  # iron shadow
            "y": hex_rgba("#C47E14"),  # gold spear shaft
            "Y": hex_rgba("#FFE059"),  # gold spear bright
        },
    )

    # 3. battle_momentum — Ascending golden sword + crimson momentum chevrons
    skills["battle_momentum.png"] = from_grid(
        [
            "...........oooo.",
            "..........osssYo",
            ".........ossYYYo",
            "........ossYYYo.",
            "..oo...ossYYYo..",
            ".orRo.ossYYYo...",
            ".orRRoosYYYo....",
            "..oorRoyYoo.....",
            "..oooyYoyo......",
            ".orRoyYo........",
            ".orRyYo.........",
            "..oyYo..........",
            ".oyyyo..........",
            ".oyoo...........",
            "..o.............",
            "................",
        ],
        {
            "o": hex_rgba("#4A2B0A"),  # warm umber outline
            "s": hex_rgba("#FFFFFF"),  # blade edge
            "Y": hex_rgba("#F7C942"),  # golden blade
            "y": hex_rgba("#B37212"),  # hilt/guard shadow
            "r": hex_rgba("#FF7A59"),  # ember chevron light
            "R": hex_rgba("#D6362B"),  # ember chevron mid
        },
    )

    # 4. berserk — Blazing crimson-amber fury flame star
    skills["berserk.png"] = from_grid(
        [
            ".......oo.......",
            "......orRo......",
            ".....orYYRo.....",
            "..oo.orYYRo.oo..",
            ".orRorYssYRoRro.",
            ".oRYrYssssYrYRo.",
            "..oRYssssssYRo..",
            "...oRYssssYRo...",
            "..orRYssssYRro..",
            ".orRYsYYYYsYRro.",
            ".orRYsYooYsYRro.",
            "..oorRo..oRroo..",
            "....oo....oo....",
            "................",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#591111"),  # deep crimson outline
            "r": hex_rgba("#C92822"),  # outer flame crimson
            "R": hex_rgba("#F56C22"),  # orange flame
            "Y": hex_rgba("#FFD245"),  # bright yellow
            "s": hex_rgba("#FFFAD1"),  # white-hot core
        },
    )

    # 5. bloodthirst — Crisp Vanilla crimson blood droplet
    skills["bloodthirst.png"] = from_grid(
        [
            "................",
            ".......oo.......",
            "......oRro......",
            ".....oRsrro.....",
            "....oRssrrRo....",
            "...oRsrrrrRRo...",
            "...oRrrrrRRRo...",
            "..oRrrrrrRRRRo..",
            "..oRrrrrrRRRRo..",
            "..oRrrrrRRRRRo..",
            "..oRrrrRRRRRdo..",
            "...oRRRRRRRdo...",
            "...ooRRRRddoo...",
            ".....oooooo.....",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#4D090D"),  # deep blood-brown outline
            "s": hex_rgba("#FFD6D6"),  # crisp specular
            "r": hex_rgba("#F23838"),  # bright crimson
            "R": hex_rgba("#BD1920"),  # mid crimson
            "d": hex_rgba("#820F15"),  # shadow
        },
    )

    # 6. counter_guard — Vanilla Iron-Gold Shield with golden parry spark
    skills["counter_guard.png"] = from_grid(
        [
            "................",
            "..oooooooooooo..",
            ".oyYYsIIIIsYYyo.",
            ".oyYssImmIssYyo.",
            ".oiIsImYYmIsIio.",
            ".oiIImYssYmIIio.",
            ".oiImYYYYYYmIio.",
            ".oiImmYssYmmIio.",
            "..oiImmYYmmIio..",
            "..oiImddddmIio..",
            "...oiIddddmio...",
            "....oiIdddio....",
            ".....oiIIio.....",
            "......oooo......",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#323945"),  # iron-slate outline
            "s": hex_rgba("#FFFFFF"),  # specular glint
            "I": hex_rgba("#DAE1EB"),  # iron highlight
            "m": hex_rgba("#9AA3B0"),  # iron mid
            "d": hex_rgba("#646C7A"),  # iron shadow
            "i": hex_rgba("#525A66"),  # rim shadow
            "Y": hex_rgba("#FFE259"),  # gold parry crest
            "y": hex_rgba("#C28315"),  # gold trim
        },
    )

    # 7. damage_clamp — Crimson heart braced by golden-iron armor clamp
    skills["damage_clamp.png"] = from_grid(
        [
            "................",
            "..oooo....oooo..",
            ".orssro..orrrro.",
            "orssrrroorrrrRdo",
            "orsrrrrrrrrRRddo",
            "oooooooooooooodo",
            "oIIIIIIIIIIIIIIo",
            "oIsYYmmmmmmYYsIo",
            "oIiiiiiiiiiiiiIo",
            "oooooooooooooodo",
            ".orrrrrrrrrRRdo.",
            "..orrrrrrRRRdo..",
            "...orrrrrRRdo...",
            "....orrrRRdo....",
            ".....ooRdoo.....",
            ".......oo.......",
        ],
        {
            "o": hex_rgba("#45171C"),  # dark burgundy-iron outline
            "s": hex_rgba("#FFD9D9"),  # heart specular
            "r": hex_rgba("#ED3B41"),  # heart mid
            "R": hex_rgba("#B51B24"),  # heart shadow
            "d": hex_rgba("#82121A"),  # deep heart shadow
            "I": hex_rgba("#DCE2EB"),  # iron bar highlight
            "m": hex_rgba("#9BA4B3"),  # iron bar mid
            "i": hex_rgba("#5E6673"),  # iron bar shadow
            "Y": hex_rgba("#FFE059"),  # gold clamp rivets
        },
    )

    # 8. diamond_bulwark — Authentic Vanilla Diamond Chestplate
    skills["diamond_bulwark.png"] = from_grid(
        [
            "................",
            "..oooo....oooo..",
            ".oslllo..ollllo.",
            ".oslmlloollmmmo.",
            ".ollmmmmmmmmddo.",
            ".ommmsllllmdddo.",
            "..oollmmmmmdoo..",
            "...olmmmmmmmo...",
            "...olmmsllmdo...",
            "...olmmmmmddo...",
            "...ommmmmmddo...",
            "...ommmmmdddo...",
            "...ommmmddddo...",
            "...oooooooooo...",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#0E3D42"),  # dark teal-navy outline
            "s": hex_rgba("#E0FFFF"),  # specular
            "l": hex_rgba("#7CF2EC"),  # diamond highlight
            "m": hex_rgba("#30C4BE"),  # diamond mid
            "d": hex_rgba("#1A8785"),  # diamond shadow
        },
    )

    # 9. focus_mining — Vanilla Diamond Pickaxe striking sparking stone ore
    skills["focus_mining.png"] = from_grid(
        [
            "................",
            "......oooooo....",
            "....oosllmmmo...",
            "...osllmmwddo...",
            "..oslmookWo.....",
            "..olmo.kWhWk.ss.",
            "..ooo.kWhWk.sYY.",
            ".....kWhWk..oo..",
            "....kWhWk..oaa..",
            "...kWhWk..oaAAo.",
            "..kWhWk..oaAAAAo",
            ".kWhWk...oAAAAA.",
            ".kWWk....oAAAAo.",
            "..kk......oooo..",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#1E3B42"),  # dark outline
            "s": hex_rgba("#E6FFFF"),  # specular
            "l": hex_rgba("#7CF2EC"),  # diamond highlight
            "m": hex_rgba("#30C4BE"),  # diamond mid
            "d": hex_rgba("#198582"),  # diamond shadow
            "k": hex_rgba("#291E0D"),  # oak handle outline
            "w": hex_rgba("#4A3516"),  # oak shadow
            "W": hex_rgba("#7A5724"),  # oak mid
            "h": hex_rgba("#996F31"),  # oak highlight
            "Y": hex_rgba("#FFE259"),  # mining spark
            "a": hex_rgba("#A3A3AB"),  # stone highlight
            "A": hex_rgba("#686870"),  # stone mid
        },
    )

    # 10. frozen_pull — Crisp Vanilla Blue-Ice Snowflake Crystal
    skills["frozen_pull.png"] = from_grid(
        [
            ".......oo.......",
            "......oCCo......",
            "..oo..oCco..oo..",
            ".oCCo.oSSo.oCCo.",
            "..ocCoCSSoCcCo..",
            "...oCCSSSSCCo...",
            "oooCSSssssSSCooo",
            "oCCSSssWWssSSCCo",
            "oooCSSssssSSCooo",
            "...oCCSSSSCCo...",
            "..ocCoCSSoCcCo..",
            ".oCCo.oSSo.oCCo.",
            "..oo..oCco..oo..",
            "......oCCo......",
            ".......oo.......",
            "................",
        ],
        {
            "o": hex_rgba("#1A4F75"),  # deep frost-blue outline
            "C": hex_rgba("#4CB6EB"),  # ice mid
            "c": hex_rgba("#8CE2FF"),  # ice light
            "S": hex_rgba("#C7F4FF"),  # frost bright
            "s": hex_rgba("#EBFCFF"),  # pale frost
            "W": hex_rgba("#FFFFFF"),  # crisp core
        },
    )

    # 11. gravity_well — Swirling Amethyst/Ender vortex pulling inward to dense ender core
    skills["gravity_well.png"] = from_grid(
        [
            "................",
            ".....oooooo.....",
            "...oopPPPPss....",
            "..opPmmddddmo...",
            ".opPmdoeeoodpo..",
            ".opmdoeESeoPpo..",
            ".opmdoESSeoPpo..",
            ".opPdooeEomPpo..",
            "..opPmdooomdpo..",
            "...oopPPPPPpo...",
            "....ssmmddoo....",
            "......oooo......",
            "..ee........ee..",
            "...e........e...",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#29123D"),  # obsidian-amethyst outline
            "p": hex_rgba("#762BB3"),  # outer ring dark
            "P": hex_rgba("#AE57F0"),  # outer ring bright
            "s": hex_rgba("#F2D9FF"),  # swirl specular
            "m": hex_rgba("#9142CF"),  # swirl mid
            "d": hex_rgba("#4E1B7A"),  # deep well
            "e": hex_rgba("#5CE3CA"),  # ender core rim / pull motes
            "E": hex_rgba("#298F7F"),  # ender core mid
            "S": hex_rgba("#E0FFF9"),  # core glint
        },
    )

    # 12. iron_physique — Iron-rimmed rugged heart/chest emblem
    skills["iron_physique.png"] = from_grid(
        [
            "................",
            "..oooo....oooo..",
            ".oIIIIo..oIIIIo.",
            "oIsIIrIoIIrrIIIo",
            "oIirssrrIrrRRrIo",
            "oIirrrrrrrRRRRIo",
            "oIimrrrrrrrrRRIo",
            ".oIimrrrrrrRRIo.",
            "..oIimrrrrRRIo..",
            "...oIimrrRRIo...",
            "....oIimRRIo....",
            ".....oIiIIo.....",
            "......oooo......",
            "................",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#323740"),  # slate outline
            "I": hex_rgba("#D8DFE8"),  # iron rim highlight
            "i": hex_rgba("#7F8896"),  # iron rim shadow
            "m": hex_rgba("#A8B1BF"),  # iron mid
            "s": hex_rgba("#FFD9D9"),  # ruby core specular
            "r": hex_rgba("#ED3B41"),  # ruby core bright
            "R": hex_rgba("#AD1A22"),  # ruby core shadow
        },
    )

    # 13. item_magnet — Crisp Vanilla Red/Lapis-Blue Horseshoe Magnet
    skills["item_magnet.png"] = from_grid(
        [
            "................",
            "..oooo....oooo..",
            ".oIssIo..oIssIo.",
            ".oImmIo..oImmIo.",
            ".oRRRRo..oBBBBo.",
            ".oRrrRo..oBbbBo.",
            ".oRrrRo..oBbbBo.",
            ".oRrrRo..oBbbBo.",
            ".oRrrrroobbbbBo.",
            "..oRrrroobbbBo..",
            "..ooRRrmmmBBoo..",
            "...ooRrmmBBoo...",
            ".....oooooo.....",
            "................",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#2C2B3B"),  # dark indigo outline
            "s": hex_rgba("#FFFFFF"),  # iron tip glint
            "I": hex_rgba("#DCE2EB"),  # iron tip highlight
            "m": hex_rgba("#9AA3B0"),  # iron mid
            "r": hex_rgba("#F24444"),  # red highlight
            "R": hex_rgba("#B51D22"),  # red shadow
            "b": hex_rgba("#4A85EB"),  # lapis highlight
            "B": hex_rgba("#244CA6"),  # lapis shadow
        },
    )

    # 14. last_stand — Defiant Crimson-Gold Kite Shield with bright gold cross
    skills["last_stand.png"] = from_grid(
        [
            ".......oo.......",
            "..oooooyyooooo..",
            ".oyYYrrrrrrYYyo.",
            ".oyYrrsYYsrrYyo.",
            ".orRrrsYYsrrRro.",
            ".orRYYYYYYYYRro.",
            ".orRYssYYssYRro.",
            ".orRrrsYYsrrRro.",
            "..oRrRsYYsRrRo..",
            "..oRRrsYYsRRRo..",
            "...oRRrYYrRRo...",
            "....oRRYyRRo....",
            ".....oRYyRo.....",
            "......oyyo......",
            ".......oo.......",
            "................",
        ],
        {
            "o": hex_rgba("#4A1214"),  # deep crimson-umber outline
            "y": hex_rgba("#B87A14"),  # gold shadow
            "Y": hex_rgba("#FFE059"),  # gold highlight
            "s": hex_rgba("#FFFAD1"),  # specular gold
            "r": hex_rgba("#DE3333"),  # crimson highlight
            "R": hex_rgba("#99161B"),  # crimson shadow
        },
    )

    # 15. light_feather — Authentic Vanilla Feather silhouette + wind pixels
    skills["light_feather.png"] = from_grid(
        [
            "................",
            "..........oooo..",
            ".........ossbbo.",
            "........ossbbBo.",
            ".......ossbbBBo.",
            "......osscbBBo..",
            ".....ossbbBBo...",
            "....osscbBBo....",
            "...ossbbBBo.....",
            "...oscbBBo......",
            "..oqcBboo.......",
            "..oqoo..........",
            ".oqo............",
            ".oo.............",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#415266"),  # slate-blue outline
            "s": hex_rgba("#FFFFFF"),  # vane highlight
            "b": hex_rgba("#DCE8F2"),  # vane mid
            "B": hex_rgba("#9CB4C9"),  # vane shadow
            "c": hex_rgba("#E8DFC8"),  # shaft
            "q": hex_rgba("#B89E74"),  # quill tip
        },
    )

    # 16. lightning_spear — Crisp Gold & Electric Sky Lightning Bolt
    skills["lightning_spear.png"] = from_grid(
        [
            "........ooooo...",
            ".......ossYYYo..",
            "......ossYYYo...",
            ".....ossYYYo....",
            "....ossYYYYooo..",
            "...ossYYYYYYsso.",
            "...oooYYYYYYYo..",
            "......ooYYYYo...",
            ".......ossYo....",
            "......ossYo.....",
            ".....ossYo......",
            "....osYYo.......",
            "...osYYo..cc....",
            "..osYYo.........",
            "..ooo...........",
            "................",
        ],
        {
            "o": hex_rgba("#522E07"),  # warm amber-brown outline
            "s": hex_rgba("#FFFDE0"),  # white-gold flash
            "Y": hex_rgba("#FFD638"),  # bright lightning gold
            "c": hex_rgba("#6FE8FF"),  # sky spark accent
        },
    )

    # 17. oppressive_field — Heavy Netherite/Obsidian Anvil Weight crushing down with crimson shockwaves
    skills["oppressive_field.png"] = from_grid(
        [
            "................",
            "..oooooooooooo..",
            ".olMMMMMMMMMMdo.",
            ".oMMMMMMMMMMddo.",
            "..ooolMMMMdooo..",
            ".....oMMMMo.....",
            "....olMMMMdo....",
            "...oMMMMMMMMo...",
            "...oooooooooo...",
            "................",
            ".rrr..rrrr..rrr.",
            "rRrr.rRssRr.rrRr",
            "..rr..rrrr..rr..",
            ".ss..........ss.",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#23152E"),  # obsidian outline
            "d": hex_rgba("#3D2C4D"),  # dark obsidian-iron
            "M": hex_rgba("#644D78"),  # mid obsidian-iron
            "l": hex_rgba("#9880B0"),  # top bevel highlight
            "r": hex_rgba("#F25C4E"),  # shockwave coral-crimson
            "R": hex_rgba("#BA2727"),  # deep crimson
            "s": hex_rgba("#FFE3AD"),  # hot spark
        },
    )

    # 18. ore_sight — Vanilla Stone Block with Diamond, Gold & Emerald ore clusters
    skills["ore_sight.png"] = from_grid(
        [
            "................",
            "..oooooooooooo..",
            ".osaaaaaaaaaado.",
            ".oaCCaaYYaaaado.",
            ".oaCCsaYYsaaado.",
            ".oaaaaaYYaaaado.",
            ".oaaammmmmmaado.",
            ".oaaaGGaaaCCado.",
            ".oaasGGaaaCCsdo.",
            ".oaaaGGaaaaaddo.",
            ".oammmmmmmddddo.",
            ".oaddddddddddo..",
            "..oooooooooooo..",
            "................",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#34353B"),  # slate outline
            "s": hex_rgba("#E0FCFF"),  # ore glint
            "a": hex_rgba("#9A9AA3"),  # stone highlight
            "m": hex_rgba("#7C7C85"),  # stone mid
            "d": hex_rgba("#5C5C63"),  # stone shadow
            "C": hex_rgba("#49DED7"),  # diamond ore
            "Y": hex_rgba("#FCE04E"),  # gold ore
            "G": hex_rgba("#48CF6C"),  # emerald ore
        },
    )

    # 19. survival_flow — Vanilla Prismarine / Sea-Emerald Vitality Droplet
    skills["survival_flow.png"] = from_grid(
        [
            "................",
            ".......oo.......",
            "......oEEo......",
            ".....oEsEEo.....",
            "....oEssEEEo....",
            "...oEssEEEEdo...",
            "...oEsEEccEdo...",
            "..oEsEEcccEedo..",
            "..oEEEEcEEeedo..",
            "..oEEEEEEEdddo..",
            "...oEEEEEdddo...",
            "...ooEEEdddoo...",
            ".....oooooo.....",
            "................",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#12473E"),  # deep prismarine outline
            "s": hex_rgba("#D6FFF4"),  # seafoam specular
            "E": hex_rgba("#4AD4AF"),  # vitality emerald mid
            "c": hex_rgba("#8FF2D4"),  # inner swirl highlight
            "e": hex_rgba("#2C9C80"),  # deeper emerald
            "d": hex_rgba("#1B6B58"),  # shadow
        },
    )

    # 20. swift_pace — Vanilla Golden Boot with crisp sky-wind streaks
    skills["swift_pace.png"] = from_grid(
        [
            "................",
            ".......oooooo...",
            "......osYYYYyo..",
            "..www.oYYYYyyo..",
            "......oYYssyyo..",
            ".wwww.oYYYYyyo..",
            "......oYYYYyyoo.",
            "..www.oYYssYYYyo",
            "......oYYYYYYYyo",
            ".....oYYYYyyyyyo",
            ".....oDDDDDDDDDo",
            ".....ooooooooooo",
            "................",
            "................",
            "................",
            "................",
        ],
        {
            "o": hex_rgba("#523208"),  # warm sepia-gold outline
            "s": hex_rgba("#FFFAD1"),  # gold specular
            "Y": hex_rgba("#FAD046"),  # gold mid
            "y": hex_rgba("#B57414"),  # gold shadow
            "D": hex_rgba("#7A490D"),  # leather sole
            "w": hex_rgba("#A8E8FF"),  # crisp breeze streaks
        },
    )

    return skills


# ---------------------------------------------------------------------------
# Counter Guard Effect Textures (Crisp Pixel Art, No Smooth Blur)
# ---------------------------------------------------------------------------

def make_counter_guard_aegis() -> Image.Image:
    """
    32x32 seamless tileable pixel-art Vanilla Enchantment-Ward Lattice.
    Uses bright ivory-gold / pale-amethyst lines so CounterGuardEffectRenderer's
    vertex RGB tint (240, 205, 110) renders crisp golden runes on a translucent dome.
    """
    img = Image.new("RGBA", (32, 32), (235, 220, 255, 18))
    px = img.load()

    line_hi = hex_rgba("#FFF9E6", 200)
    line_mid = hex_rgba("#E8D6FF", 135)
    rune_col = hex_rgba("#FFFFFF", 225)

    # Seamless diamond ward grid with 16x16 period (2x2 diamonds across 32x32)
    for y in range(32):
        for x in range(32):
            lx = x % 16
            ly = y % 16
            d = abs(lx - 8) + abs(ly - 8)
            if d == 8:
                px[x, y] = line_hi
            elif d == 7 or d == 9:
                px[x, y] = line_mid

    # Crisp 5x5 Enchantment-Table rune glyphs inside the 4 diamond centers (8,8), (24,8), (8,24), (24,24)
    # and corners (0,0), (16,0), (0,16), (16,16)
    rune_a = [
        "..#..",
        ".#.#.",
        "#####",
        "..#..",
        ".#.#.",
    ]
    rune_b = [
        ".###.",
        "#...#",
        "..#..",
        "#...#",
        ".###.",
    ]
    centers = [(8, 8, rune_a), (24, 8, rune_b), (8, 24, rune_b), (24, 24, rune_a),
               (0, 0, rune_b), (16, 0, rune_a), (0, 16, rune_a), (16, 16, rune_b)]
    for cx, cy, glyph in centers:
        for gy, row in enumerate(glyph):
            for gx, ch in enumerate(row):
                if ch == "#":
                    px[(cx + gx - 2) % 32, (cy + gy - 2) % 32] = rune_col

    return img


def make_counter_guard_ring() -> Image.Image:
    """
    64x64 crisp nearest-neighbor pixel-art Enchantment Ward Ring.
    Zero gaussian blur: crisp concentric pixel circles, 8 diamond studs, and 8 pixel glyphs.
    """
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    cx = cy = 31.5

    outer_hi = hex_rgba("#FFFCEB", 235)
    outer_mid = hex_rgba("#F7E4A6", 170)
    inner_hi = hex_rgba("#F2D9FF", 190)
    inner_soft = hex_rgba("#E0C4FF", 75)
    stud_col = hex_rgba("#FFFFFF", 255)

    for y in range(size):
        for x in range(size):
            r = math.hypot(x - cx, y - cy)
            if 26.8 <= r <= 28.2:
                px[x, y] = outer_hi
            elif 25.5 <= r < 26.8 or 28.2 < r <= 29.2:
                px[x, y] = outer_mid
            elif 16.3 <= r <= 17.6:
                px[x, y] = inner_hi
            elif 17.6 < r < 25.5:
                px[x, y] = inner_soft

    # 8 crisp diamond studs on the outer ring
    for i in range(8):
        ang = i * math.pi / 4.0
        sx = int(round(cx + 27.5 * math.cos(ang)))
        sy = int(round(cy + 27.5 * math.sin(ang)))
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                if abs(dx) + abs(dy) <= 1:
                    nx, ny = sx + dx, sy + dy
                    if 0 <= nx < size and 0 <= ny < size:
                        px[nx, ny] = stud_col

    # 8 crisp 3x3 enchantment rune ticks in the middle band (radius ~21.5)
    glyphs = [
        ["###", ".#.", "###"],
        [".#.", "###", ".#."],
        ["#.#", ".#.", "#.#"],
        ["###", "#..", "###"],
    ]
    for i in range(8):
        ang = (i + 0.5) * math.pi / 4.0
        gx0 = int(round(cx + 21.5 * math.cos(ang)))
        gy0 = int(round(cy + 21.5 * math.sin(ang)))
        g = glyphs[i % len(glyphs)]
        for dy in range(3):
            for dx in range(3):
                if g[dy][dx] == "#":
                    nx, ny = gx0 + dx - 1, gy0 + dy - 1
                    if 0 <= nx < size and 0 <= ny < size:
                        px[nx, ny] = outer_hi

    return img


# ---------------------------------------------------------------------------
# Validation & Main
# ---------------------------------------------------------------------------

def validate_strict(img: Image.Image, name: str, require_binary_alpha: bool) -> None:
    pure_black = 0
    bad_alpha = 0
    for r, g, b, a in img.getdata():
        if a > 0 and r == 0 and g == 0 and b == 0:
            pure_black += 1
        if require_binary_alpha and a not in (0, 255):
            bad_alpha += 1
    if pure_black:
        raise AssertionError(f"{name}: found {pure_black} pure #000000 pixels!")
    if bad_alpha:
        raise AssertionError(f"{name}: found {bad_alpha} semi-transparent pixels!")


def main() -> None:
    items = make_items()
    items["lightning_spear.png"] = make_lightning_spear()
    for fname, im in items.items():
        validate_strict(im, f"item/{fname}", require_binary_alpha=True)
        im.save(ITEM_DIR / fname)

    update_lightning_spear_model()

    skills = make_skills()
    for fname, im in skills.items():
        assert im.size == (16, 16), f"{fname} size {im.size} != (16, 16)"
        validate_strict(im, f"skills/{fname}", require_binary_alpha=True)
        im.save(SKILL_DIR / fname)

    aegis = make_counter_guard_aegis()
    validate_strict(aegis, "misc/counter_guard_aegis.png", require_binary_alpha=False)
    aegis.save(MISC_DIR / "counter_guard_aegis.png")

    ring = make_counter_guard_ring()
    validate_strict(ring, "misc/counter_guard_ring.png", require_binary_alpha=False)
    ring.save(MISC_DIR / "counter_guard_ring.png")

    print(f"Generated {len(items)} items, {len(skills)} skills, 2 misc textures, and updated lightning_spear.json.")


if __name__ == "__main__":
    main()
