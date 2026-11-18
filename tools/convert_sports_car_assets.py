"""Convert the Gulf ROFGO GT Racecar .bbmodel into mod entity textures and compact part-local JSON geometry."""

from __future__ import annotations

import base64
import io
import json
import math
from collections import Counter
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BBMODEL_PATH = Path(r"f:\Downloads\跑车（鱼某帮做）.bbmodel")

OUT_MAIN_TEX = ROOT / "src/main/resources/assets/universal_tool/textures/entity/gulf_gt_car.png"
OUT_GLASS_TEX = ROOT / "src/main/resources/assets/universal_tool/textures/entity/gulf_gt_car_glass.png"
OUT_MODEL_JSON = ROOT / "src/main/resources/assets/universal_tool/models/entity/gulf_gt_car.json"

UNITS_PER_BLOCK = 16.0
ATLAS_W = 2048
ATLAS_H = 2560
SWATCH_EXPAND_SIZE = 8
ROUND_DIGITS = 5

# Part order matches client renderer articulation order
PART_SPECS: list[dict[str, Any]] = [
    {"name": "body", "texture": "main"},
    {"name": "headlights", "texture": "main"},
    {"name": "taillights", "texture": "main"},
    {"name": "glass", "texture": "glass"},
    {"name": "wheel_fl_steer", "texture": "main"},
    {"name": "wheel_fl_spin", "texture": "main"},
    {"name": "wheel_fr_steer", "texture": "main"},
    {"name": "wheel_fr_spin", "texture": "main"},
    {"name": "wheel_rl_spin", "texture": "main"},
    {"name": "wheel_rr_spin", "texture": "main"},
    {"name": "steering_wheel", "texture": "main"},
    {"name": "door_left", "texture": "main"},
    {"name": "door_left_glass", "texture": "glass"},
    {"name": "door_right", "texture": "main"},
    {"name": "door_right_glass", "texture": "glass"},
]

# Mirrored/open shells identified during topology audit whose faces point inward in the source .bbmodel
FORCE_FLIP_OPEN_MESHES = {
    "右前轮_深筒合金轮辋",
    "右后轮_深筒合金轮辋",
    "尾翼_下翼型",
}


def decode_png_from_data_uri(source: str) -> Image.Image:
    _, b64_data = source.split(",", 1)
    return Image.open(io.BytesIO(base64.b64decode(b64_data))).convert("RGBA")


def build_textures(bb: dict[str, Any]) -> tuple[dict[str, tuple[str, float, float, float, float, float, float]], tuple[int, int]]:
    """
    Pack opaque textures and 2x2 swatches losslessly into gulf_gt_car.png (2048x2560),
    and export glass texture separately as gulf_gt_car_glass.png (512x256).

    Returns:
      uv_map[tex_id] = (target_key, rx, ry, rw, rh, uv_w, uv_h)
      where target_key is "main" or "glass", (rx, ry, rw, rh) is pixel placement on target image.
    """
    textures = {str(t["id"]): t for t in bb["textures"]}

    atlas = Image.new("RGBA", (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    uv_map: dict[str, tuple[str, float, float, float, float, float, float]] = {}

    # 0: reference_livery.png (1792x1344 native, uv_size 2048x1536) -> (0, 0)
    t0 = textures["0"]
    img0 = decode_png_from_data_uri(t0["source"])
    assert img0.size == (1792, 1344), f"Unexpected texture 0 size: {img0.size}"
    atlas.paste(img0, (0, 0))
    uv_map["0"] = ("main", 0.0, 0.0, float(img0.width), float(img0.height), float(t0["uv_width"]), float(t0["uv_height"]))

    # 4: 座舱_碳纤维与仪表.png (1024x1024) -> (0, 1344)
    t4 = textures["4"]
    img4 = decode_png_from_data_uri(t4["source"])
    assert img4.size == (1024, 1024), f"Unexpected texture 4 size: {img4.size}"
    atlas.paste(img4, (0, 1344))
    uv_map["4"] = ("main", 0.0, 1344.0, float(img4.width), float(img4.height), float(t4["uv_width"]), float(t4["uv_height"]))

    # 2: brake-disc.png (256x256) -> (1024, 1344)
    t2 = textures["2"]
    img2 = decode_png_from_data_uri(t2["source"])
    assert img2.size == (256, 256), f"Unexpected texture 2 size: {img2.size}"
    atlas.paste(img2, (1024, 1344))
    uv_map["2"] = ("main", 1024.0, 1344.0, float(img2.width), float(img2.height), float(t2["uv_width"]), float(t2["uv_height"]))

    # 5..23: 19 solid 2x2 swatches -> expanded to 8x8 blocks at y=1600, x=1024 + idx*8
    for idx, tid_num in enumerate(range(5, 24)):
        tid = str(tid_num)
        t_sw = textures[tid]
        sw_img = decode_png_from_data_uri(t_sw["source"])
        rgba = sw_img.getpixel((0, 0))
        block = Image.new("RGBA", (SWATCH_EXPAND_SIZE, SWATCH_EXPAND_SIZE), rgba)
        px = 1024 + idx * SWATCH_EXPAND_SIZE
        py = 1600
        atlas.paste(block, (px, py))
        # Mark rw=0, rh=0 to signal exact center sampling (px + 4.0, py + 4.0)
        uv_map[tid] = ("main", float(px + SWATCH_EXPAND_SIZE * 0.5), float(py + SWATCH_EXPAND_SIZE * 0.5), 0.0, 0.0, 1.0, 1.0)

    # 3: 座舱_透明玻璃.png (512x256 RGBA) -> standalone gulf_gt_car_glass.png
    t3 = textures["3"]
    img3 = decode_png_from_data_uri(t3["source"])
    assert img3.size == (512, 256), f"Unexpected texture 3 size: {img3.size}"
    uv_map["3"] = ("glass", 0.0, 0.0, float(img3.width), float(img3.height), float(t3["uv_width"]), float(t3["uv_height"]))

    OUT_MAIN_TEX.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(OUT_MAIN_TEX, format="PNG")
    img3.save(OUT_GLASS_TEX, format="PNG")

    return uv_map, (img3.width, img3.height)


def map_uv(tex_id: str, u_bb: float, v_bb: float, uv_map: dict[str, tuple[str, float, float, float, float, float, float]], glass_size: tuple[int, int]) -> tuple[float, float]:
    target, rx, ry, rw, rh, uv_w, uv_h = uv_map[tex_id]
    tw, th = (ATLAS_W, ATLAS_H) if target == "main" else glass_size
    if rw == 0.0 and rh == 0.0:
        return rx / tw, ry / th
    u_px = rx + (u_bb / uv_w) * rw
    v_px = ry + (v_bb / uv_h) * rh
    return u_px / tw, v_px / th


def compute_steering_axis_and_pivot(elements: list[dict[str, Any]]) -> tuple[list[float], list[float]]:
    """Compute exact steering column center pivot and unit axis pointing toward the driver (+Z/+Y)."""
    col_elem = next(e for e in elements if e["name"] == "方向盘_转向柱")
    pts = np.array(list(col_elem["vertices"].values()), dtype=float)
    # 方向盘_转向柱 has back ring (z < -10) and front ring (z > -10)
    back_ring = pts[pts[:, 2] < -10.0]
    front_ring = pts[pts[:, 2] > -10.0]
    back_center = back_ring.mean(axis=0)
    front_center = front_ring.mean(axis=0)
    axis_vec = front_center - back_center
    axis_unit = axis_vec / np.linalg.norm(axis_vec)
    # Pivot right at the quick-release hub / front end of steering column
    return front_center.tolist(), axis_unit.tolist()


def pos_key(pt: np.ndarray) -> tuple[float, float, float]:
    return (round(float(pt[0]), 3), round(float(pt[1]), 3), round(float(pt[2]), 3))


def should_flip_mesh(elem: dict[str, Any]) -> bool:
    """Detect closed meshes whose signed volume is negative (inverted winding), plus known mirrored open shells."""
    if elem["name"] in FORCE_FLIP_OPEN_MESHES:
        return True

    vs = {k: np.array(v, dtype=float) for k, v in elem["vertices"].items()}
    edge_counts: Counter[tuple[tuple[float, float, float], tuple[float, float, float]]] = Counter()
    signed_vol = 0.0

    for face in elem["faces"].values():
        vids = face["vertices"]
        pts = [vs[vid] for vid in vids]
        for i in range(len(pts)):
            a = pos_key(pts[i])
            b = pos_key(pts[(i + 1) % len(pts)])
            if a != b:
                edge = (a, b) if a <= b else (b, a)
                edge_counts[edge] += 1
        if len(pts) == 3:
            signed_vol += float(np.dot(pts[0], np.cross(pts[1], pts[2]))) / 6.0
        elif len(pts) == 4:
            signed_vol += float(np.dot(pts[0], np.cross(pts[1], pts[2]))) / 6.0
            signed_vol += float(np.dot(pts[0], np.cross(pts[2], pts[3]))) / 6.0

    is_closed = len(edge_counts) > 0 and all(c == 2 for c in edge_counts.values())
    return is_closed and signed_vol < -1e-5


def compute_face_normal(pts: list[np.ndarray]) -> np.ndarray | None:
    if len(pts) == 3:
        n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
    elif len(pts) == 4:
        n = np.cross(pts[2] - pts[0], pts[3] - pts[1])
    else:
        return None
    norm = float(np.linalg.norm(n))
    if norm < 1e-7:
        return None
    return n / norm


def classify_meshes(bb: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    elems = {e["uuid"]: e for e in bb["elements"]}
    groups = {g["uuid"]: g for g in bb["groups"]}

    grouped: dict[str, list[dict[str, Any]]] = {spec["name"]: [] for spec in PART_SPECS}
    assigned_uuids: set[str] = set()

    def walk(node: Any, group_path: list[str]) -> None:
        if isinstance(node, str):
            elem = elems[node]
            assert node not in assigned_uuids, f"Duplicate element assignment: {elem['name']}"
            assigned_uuids.add(node)
            name = elem["name"]
            tex_ids = {str(f.get("texture")) for f in elem["faces"].values()}
            assert "1" not in tex_ids and "None" not in tex_ids, f"Unexpected texture {tex_ids} on {name}"

            if "左前轮_固定制动系统" in group_path:
                part = "wheel_fl_steer"
            elif "右前轮_固定制动系统" in group_path:
                part = "wheel_fr_steer"
            elif "左前轮_滚动" in group_path:
                part = "wheel_fl_spin"
            elif "右前轮_滚动" in group_path:
                part = "wheel_fr_spin"
            elif "左后轮_滚动" in group_path:
                part = "wheel_rl_spin"
            elif "右后轮_滚动" in group_path:
                part = "wheel_rr_spin"
            elif "左车门_前铰链" in group_path:
                part = "door_left_glass" if tex_ids == {"3"} else "door_left"
            elif "右车门_前铰链" in group_path:
                part = "door_right_glass" if tex_ids == {"3"} else "door_right"
            elif "灯组_前灯与尾灯" in group_path:
                zs = [v[2] for v in elem["vertices"].values()]
                avg_z = sum(zs) / len(zs)
                if "前灯" in name and avg_z < 0.0:
                    part = "headlights"
                elif ("尾灯" in name or "制动灯" in name) and avg_z > 0.0:
                    part = "taillights"
                else:
                    raise ValueError(f"Ambiguous light mesh: {name} avg_z={avg_z}")
            elif name == "前风挡_透光夹层玻璃" or tex_ids == {"3"}:
                part = "glass"
            elif name.startswith("方向盘_"):
                part = "steering_wheel"
            else:
                part = "body"

            grouped[part].append(elem)
        else:
            g = groups[node["uuid"]]
            next_path = group_path + [g["name"]]
            for child in node.get("children", []):
                walk(child, next_path)

    for root_node in bb["outliner"]:
        walk(root_node, [])

    assert len(assigned_uuids) == len(bb["elements"]), (
        f"Element count mismatch: assigned={len(assigned_uuids)} total={len(bb['elements'])}"
    )
    return grouped


def round_num(val: float) -> float:
    r = round(float(val), ROUND_DIGITS)
    return 0.0 if abs(r) < 1e-9 else r


def convert() -> None:
    if not BBMODEL_PATH.exists():
        raise FileNotFoundError(f"Source bbmodel not found: {BBMODEL_PATH}")

    with BBMODEL_PATH.open("r", encoding="utf-8") as f:
        bb = json.load(f)

    # 1. Build textures
    uv_map, glass_size = build_textures(bb)

    # 2. Compute Y-grounding offset (min_y = -0.08 -> 0.0)
    all_y = [v[1] for e in bb["elements"] for v in e["vertices"].values()]
    min_y_bb = min(all_y)
    y_offset_bb = -min_y_bb  # +0.08

    # 3. Extract pivots from groups + steering column
    groups_by_name = {g["name"]: g for g in bb["groups"]}
    steer_pivot_bb, steer_axis = compute_steering_axis_and_pivot(bb["elements"])

    def bb_to_block_vec(vec_bb: list[float] | tuple[float, float, float]) -> list[float]:
        return [
            vec_bb[0] / UNITS_PER_BLOCK,
            (vec_bb[1] + y_offset_bb) / UNITS_PER_BLOCK,
            vec_bb[2] / UNITS_PER_BLOCK,
        ]

    pivots_blocks: dict[str, list[float]] = {
        "body": [0.0, 0.0, 0.0],
        "headlights": [0.0, 0.0, 0.0],
        "taillights": [0.0, 0.0, 0.0],
        "glass": [0.0, 0.0, 0.0],
        "wheel_fl_steer": bb_to_block_vec(groups_by_name["左前轮_滚动"]["origin"]),
        "wheel_fl_spin": bb_to_block_vec(groups_by_name["左前轮_滚动"]["origin"]),
        "wheel_fr_steer": bb_to_block_vec(groups_by_name["右前轮_滚动"]["origin"]),
        "wheel_fr_spin": bb_to_block_vec(groups_by_name["右前轮_滚动"]["origin"]),
        "wheel_rl_spin": bb_to_block_vec(groups_by_name["左后轮_滚动"]["origin"]),
        "wheel_rr_spin": bb_to_block_vec(groups_by_name["右后轮_滚动"]["origin"]),
        "steering_wheel": bb_to_block_vec(steer_pivot_bb),
        "door_left": bb_to_block_vec(groups_by_name["左车门_前铰链"]["origin"]),
        "door_left_glass": bb_to_block_vec(groups_by_name["左车门_前铰链"]["origin"]),
        "door_right": bb_to_block_vec(groups_by_name["右车门_前铰链"]["origin"]),
        "door_right_glass": bb_to_block_vec(groups_by_name["右车门_前铰链"]["origin"]),
    }

    # 4. Classify meshes and build part-local geometry
    grouped_meshes = classify_meshes(bb)

    parts_out: dict[str, Any] = {}
    global_min = [math.inf, math.inf, math.inf]
    global_max = [-math.inf, -math.inf, -math.inf]
    total_faces = 0
    total_tris = 0
    total_quads = 0
    flipped_meshes_count = 0
    skipped_degenerate_faces = 0

    for spec in PART_SPECS:
        part_name = spec["name"]
        expected_tex = spec["texture"]
        pivot = pivots_blocks[part_name]
        faces_out: list[dict[str, Any]] = []

        for elem in grouped_meshes[part_name]:
            flip = should_flip_mesh(elem)
            if flip:
                flipped_meshes_count += 1

            verts_bb = elem["vertices"]
            for face in elem["faces"].values():
                tex_id = str(face["texture"])
                assert uv_map[tex_id][0] == expected_tex, (
                    f"Texture mismatch in {part_name}/{elem['name']}: tex {tex_id} is {uv_map[tex_id][0]}, expected {expected_tex}"
                )

                vids = list(face["vertices"])
                if flip:
                    # Reverse winding while keeping v0 as anchor
                    vids = [vids[0]] + list(reversed(vids[1:]))

                pts_bb = [np.array(verts_bb[vid], dtype=float) for vid in vids]
                normal = compute_face_normal(pts_bb)
                if normal is None:
                    skipped_degenerate_faces += 1
                    continue

                uvs_bb = face["uv"]
                verts_out = []
                for vid, pt_bb in zip(vids, pts_bb):
                    wx = pt_bb[0] / UNITS_PER_BLOCK
                    wy = (pt_bb[1] + y_offset_bb) / UNITS_PER_BLOCK
                    wz = pt_bb[2] / UNITS_PER_BLOCK

                    global_min[0] = min(global_min[0], wx)
                    global_min[1] = min(global_min[1], wy)
                    global_min[2] = min(global_min[2], wz)
                    global_max[0] = max(global_max[0], wx)
                    global_max[1] = max(global_max[1], wy)
                    global_max[2] = max(global_max[2], wz)

                    lx = round_num(wx - pivot[0])
                    ly = round_num(wy - pivot[1])
                    lz = round_num(wz - pivot[2])

                    u_bb, v_bb = uvs_bb[vid]
                    u_norm, v_norm = map_uv(tex_id, u_bb, v_bb, uv_map, glass_size)
                    verts_out.append([lx, ly, lz, round_num(u_norm), round_num(v_norm)])

                faces_out.append({
                    "vertices": verts_out,
                    "normal": [round_num(normal[0]), round_num(normal[1]), round_num(normal[2])],
                })
                total_faces += 1
                if len(verts_out) == 3:
                    total_tris += 1
                else:
                    total_quads += 1

        part_obj: dict[str, Any] = {
            "texture": expected_tex,
            "pivot": [round_num(c) for c in pivot],
        }
        if part_name == "steering_wheel":
            part_obj["axis"] = [round_num(c) for c in steer_axis]
        part_obj["faces"] = faces_out
        parts_out[part_name] = part_obj

    model_json = {
        "format_version": 1,
        "units_per_block": UNITS_PER_BLOCK,
        "coordinate_space": "pivot_local_blocks",
        "forward_axis": "-Z",
        "y_offset_bb": round_num(y_offset_bb),
        "bounds_blocks": {
            "min": [round_num(v) for v in global_min],
            "max": [round_num(v) for v in global_max],
        },
        "textures": {
            "main": "universal_tool:textures/entity/gulf_gt_car.png",
            "glass": "universal_tool:textures/entity/gulf_gt_car_glass.png",
        },
        "atlas_size": [ATLAS_W, ATLAS_H],
        "glass_size": list(glass_size),
        "seats_blocks": {
            "driver": [round_num(-6.5 / 16.0), round_num((5.78 + y_offset_bb) / 16.0), round_num(1.85 / 16.0)],
            "passenger": [round_num(6.5 / 16.0), round_num((5.78 + y_offset_bb) / 16.0), round_num(1.85 / 16.0)],
        },
        "parts": parts_out,
    }

    OUT_MODEL_JSON.parent.mkdir(parents=True, exist_ok=True)
    with OUT_MODEL_JSON.open("w", encoding="utf-8") as f:
        json.dump(model_json, f, ensure_ascii=False, separators=(",", ":"))

    size_kb = OUT_MODEL_JSON.stat().st_size / 1024.0
    print(f"Exported {OUT_MAIN_TEX} ({ATLAS_W}x{ATLAS_H}) and {OUT_GLASS_TEX} ({glass_size[0]}x{glass_size[1]})")
    print(f"Exported {OUT_MODEL_JSON} ({size_kb:.1f} KB)")
    print(f"Total faces: {total_faces} (quads={total_quads}, tris={total_tris}, skipped_degenerate={skipped_degenerate_faces})")
    print(f"Flipped inverted meshes: {flipped_meshes_count}")
    print(f"Bounds (blocks): min={model_json['bounds_blocks']['min']} max={model_json['bounds_blocks']['max']}")
    for name, p in parts_out.items():
        extra = f" axis={p['axis']}" if "axis" in p else ""
        print(f"  part {name:18s}: tex={p['texture']:5s} faces={len(p['faces']):5d} pivot={p['pivot']}{extra}")


if __name__ == "__main__":
    convert()
