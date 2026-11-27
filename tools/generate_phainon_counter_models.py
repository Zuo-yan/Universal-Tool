#!/usr/bin/env python3
"""
白厄变身弹反「灾厄·弑魂焚诏」4 套 3D 自由网格模型生成脚本。

同时导出：
- 可在 Blockbench 5.x 中直接编辑的 `.bbmodel`（format_version 5.0, model_format free）至 `art/assets/`
- 运行时预烘焙加载的 Wavefront `.obj`（基准单位 = 1 方块，vt 为 (u, 1-v) 与 Blockbench 导出规范完全一致）至
  `src/main/resources/assets/universal_tool/models/vfx/`
- 多角度软件渲染预览图至 `art/assets/_preview_counter_models.png` 供目检确认轮廓与比例。

模型清单：
1. phainon_counter_greatsword      黑金/炽红巨型裁决巨剑立体多面模型（含剑身血槽、翼状日轮护手、浮空能量剑脊）
2. phainon_counter_cleave_arc      竖直重劈裂空斩击弧面壳体 + 双侧撕裂烈焰副翼
3. phainon_counter_edict_sigil     架势期间立体纷争护盾法环（斜切外环 + 12 枚弧形棘刺 + 内冠 + 能量穹盾）
4. phainon_counter_crater_eruption 下劈落地裂地岩浆喷涌光锥 + 内核光柱 + 贴地冲击波领环
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Dict, List, Sequence, Tuple
from uuid import NAMESPACE_URL, uuid5

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ART_DIR = ROOT / "art" / "assets"
OBJ_DIR = ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "models" / "vfx"

Vec3 = Tuple[float, float, float]
UV = Tuple[float, float]


class SubMesh:
    """单个自由网格子构件（导出为 Blockbench Mesh 元素及 OBJ 对象组）。"""

    def __init__(self, name: str, shading: str = "smooth") -> None:
        self.name = name
        self.shading = shading
        self.vertices: List[Vec3] = []
        self.faces: List[Tuple[List[int], List[UV]]] = []

    def add_vertex(self, x: float, y: float, z: float) -> int:
        idx = len(self.vertices)
        self.vertices.append((float(x), float(y), float(z)))
        return idx

    def add_quad(
        self,
        p0: Vec3,
        p1: Vec3,
        p2: Vec3,
        p3: Vec3,
        uv0: UV,
        uv1: UV,
        uv2: UV,
        uv3: UV,
    ) -> None:
        i0 = self.add_vertex(*p0)
        i1 = self.add_vertex(*p1)
        i2 = self.add_vertex(*p2)
        i3 = self.add_vertex(*p3)
        self.faces.append(([i0, i1, i2, i3], [uv0, uv1, uv2, uv3]))

    def add_tri(self, p0: Vec3, p1: Vec3, p2: Vec3, uv0: UV, uv1: UV, uv2: UV) -> None:
        i0 = self.add_vertex(*p0)
        i1 = self.add_vertex(*p1)
        i2 = self.add_vertex(*p2)
        self.faces.append(([i0, i1, i2], [uv0, uv1, uv2]))

    def add_indexed_quad(self, indices: Sequence[int], uvs: Sequence[UV]) -> None:
        self.faces.append((list(indices), list(uvs)))

    def add_indexed_tri(self, indices: Sequence[int], uvs: Sequence[UV]) -> None:
        self.faces.append((list(indices), list(uvs)))


def make_uuid(key: str) -> str:
    return str(uuid5(NAMESPACE_URL, f"universal-tool:phainon-counter/{key}"))


def compute_normal(a: Vec3, b: Vec3, c: Vec3) -> Vec3:
    ab = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    ac = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
    nx = ab[1] * ac[2] - ab[2] * ac[1]
    ny = ab[2] * ac[0] - ab[0] * ac[2]
    nz = ab[0] * ac[1] - ab[1] * ac[0]
    length = math.hypot(nx, ny, nz)
    if length < 1e-8:
        return (0.0, 1.0, 0.0)
    return (nx / length, ny / length, nz / length)


def export_model(model_name: str, parts: Sequence[SubMesh]) -> Dict[str, int]:
    """同时写出标准 Blockbench 5.0 free 格式 .bbmodel 与 Wavefront .obj。"""
    ART_DIR.mkdir(parents=True, exist_ok=True)
    OBJ_DIR.mkdir(parents=True, exist_ok=True)

    obj_lines: List[str] = [
        f"# Phainon Counter VFX 3D Mesh ({model_name}) - Blockbench 5.0 compatible",
        "mtllib none",
        "",
    ]

    bb_elements = []
    child_uuids = []
    v_offset = 1
    vt_offset = 1
    vn_offset = 1
    total_verts = 0
    total_faces = 0

    for part in parts:
        obj_lines.append(f"o {part.name}")
        for x, y, z in part.vertices:
            obj_lines.append(f"v {x:.6f} {y:.6f} {z:.6f}")

        part_uvs: List[UV] = []
        part_normals: List[Vec3] = []
        face_records: List[Tuple[List[int], List[int], int]] = []

        bb_verts: Dict[str, List[float]] = {
            f"v{i}": [round(x * 16.0, 4), round(y * 16.0, 4), round(z * 16.0, 4)]
            for i, (x, y, z) in enumerate(part.vertices)
        }
        bb_faces: Dict[str, dict] = {}

        for f_idx, (corners, uvs) in enumerate(part.faces):
            if len(corners) < 3:
                continue
            p0 = part.vertices[corners[0]]
            p1 = part.vertices[corners[1]]
            p2 = part.vertices[corners[2]]
            n = compute_normal(p0, p1, p2)
            n_id = vn_offset + len(part_normals)
            part_normals.append(n)

            uv_ids: List[int] = []
            bb_uv_map: Dict[str, List[float]] = {}
            for c_idx, (u, v) in zip(corners, uvs):
                uv_ids.append(vt_offset + len(part_uvs))
                part_uvs.append((u, v))
                bb_uv_map[f"v{c_idx}"] = [round(u * 16.0, 4), round(v * 16.0, 4)]

            face_records.append((corners, uv_ids, n_id))
            bb_faces[f"f{f_idx}"] = {
                "uv": bb_uv_map,
                "vertices": [f"v{c}" for c in corners],
            }

        for u, v in part_uvs:
            # Blockbench OBJ 导出标准：vt 写出 (u, 1 - v)，Java 侧读取时做 mcV = 1 - vtY 还原
            obj_lines.append(f"vt {u:.6f} {1.0 - v:.6f}")
        for nx, ny, nz in part_normals:
            obj_lines.append(f"vn {nx:.6f} {ny:.6f} {nz:.6f}")

        obj_lines.append("usemtl none")
        for corners, uv_ids, n_id in face_records:
            tokens = [
                f"{v_offset + c}/{uv_i}/{n_id}" for c, uv_i in zip(corners, uv_ids)
            ]
            obj_lines.append("f " + " ".join(tokens))

        v_offset += len(part.vertices)
        vt_offset += len(part_uvs)
        vn_offset += len(part_normals)
        total_verts += len(part.vertices)
        total_faces += len(face_records)

        part_uuid = make_uuid(f"{model_name}/{part.name}")
        child_uuids.append(part_uuid)
        bb_elements.append(
            {
                "name": part.name,
                "color": 0,
                "origin": [0, 0, 0],
                "rotation": [0, 0, 0],
                "shading": part.shading,
                "export": True,
                "visibility": True,
                "locked": False,
                "render_order": "default",
                "scope": 0,
                "allow_mirror_modeling": True,
                "vertices": bb_verts,
                "faces": bb_faces,
                "type": "mesh",
                "uuid": part_uuid,
            }
        )

    group_uuid = make_uuid(f"{model_name}/root_group")
    bb_doc = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": model_name,
        "model_identifier": "",
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "multi_file_ruleset": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "unhandled_root_fields": {},
        "resolution": {"width": 16, "height": 16},
        "elements": bb_elements,
        "groups": [
            {
                "name": model_name,
                "uuid": group_uuid,
                "export": True,
                "locked": False,
                "scope": 0,
                "selected": False,
                "visibility": True,
                "_static": {"properties": {}, "temp_data": {}},
                "origin": [0, 0, 0],
                "rotation": [0, 0, 0],
                "color": 0,
                "children": [],
                "reset": False,
                "shade": True,
                "mirror_uv": False,
                "autouv": 0,
                "isOpen": False,
                "primary_selected": False,
            }
        ],
        "outliner": [
            {
                "uuid": group_uuid,
                "isOpen": False,
                "children": child_uuids,
            }
        ],
        "textures": [],
    }

    (ART_DIR / f"{model_name}.bbmodel").write_text(
        json.dumps(bb_doc, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    (OBJ_DIR / f"{model_name}.obj").write_text("\n".join(obj_lines) + "\n", encoding="utf-8")
    return {"vertices": total_verts, "faces": total_faces}


# ---------------------------------------------------------------------------
# 1. phainon_counter_greatsword : 黑金/炽红巨型裁决巨剑（双 Pass 对齐 UV）
# ---------------------------------------------------------------------------
# 统一平面 UV 映射：与贴图 phainon_counter_greatsword_body / glow 严格同源
SWORD_X_HALF = 1.05
SWORD_Y_MIN = -0.50
SWORD_Y_MAX = 4.65


def sword_uv(x: float, y: float) -> UV:
    u = max(0.0, min(1.0, 0.5 + x / (2.0 * SWORD_X_HALF)))
    v = max(0.0, min(1.0, 1.0 - (y - SWORD_Y_MIN) / (SWORD_Y_MAX - SWORD_Y_MIN)))
    return (u, v)


def build_counter_greatsword() -> List[SubMesh]:
    parts: List[SubMesh] = []

    # 1A. 主巨剑立体八棱修长裁决重剑（参考 Preview_t3.5s / Preview_2_t1.0s：修长凌厉、阶梯破甲齿、深邃血槽）
    blade = SubMesh("greatsword_blade", shading="flat")
    # 每截面：(y, outer_w, ridge_w, thick_ridge, thick_center)
    stations = [
        (0.32, 0.24, 0.10, 0.110, 0.068),
        (0.72, 0.30, 0.12, 0.118, 0.070),
        (1.06, 0.23, 0.10, 0.105, 0.064),  # 下段锯齿收束
        (1.48, 0.36, 0.14, 0.125, 0.074),  # 第一阶梯破甲翼锋
        (1.96, 0.30, 0.12, 0.115, 0.068),
        (2.48, 0.39, 0.15, 0.122, 0.072),  # 中上段斩马重锋主宽处
        (3.08, 0.33, 0.13, 0.105, 0.062),
        (3.64, 0.23, 0.09, 0.080, 0.048),
        (4.12, 0.12, 0.05, 0.048, 0.030),
        (4.42, 0.045, 0.018, 0.020, 0.014),
    ]

    ring_indices: List[List[int]] = []
    for y, ow, rw, tr, tc in stations:
        ring = [
            blade.add_vertex(ow, y, 0.0),
            blade.add_vertex(rw, y, tr),
            blade.add_vertex(0.0, y, tc),
            blade.add_vertex(-rw, y, tr),
            blade.add_vertex(-ow, y, 0.0),
            blade.add_vertex(-rw, y, -tr),
            blade.add_vertex(0.0, y, -tc),
            blade.add_vertex(rw, y, -tr),
        ]
        ring_indices.append(ring)

    for s in range(len(ring_indices) - 1):
        r0 = ring_indices[s]
        r1 = ring_indices[s + 1]
        for k in range(8):
            kn = (k + 1) % 8
            idxs = [r0[k], r0[kn], r1[kn], r1[k]]
            uvs = [sword_uv(blade.vertices[i][0], blade.vertices[i][1]) for i in idxs]
            blade.add_indexed_quad(idxs, uvs)

    # 针锋长剑尖收敛锥
    tip_idx = blade.add_vertex(0.0, 4.62, 0.0)
    top_ring = ring_indices[-1]
    for k in range(8):
        kn = (k + 1) % 8
        idxs = [top_ring[k], top_ring[kn], tip_idx]
        uvs = [sword_uv(blade.vertices[i][0], blade.vertices[i][1]) for i in idxs]
        blade.add_indexed_tri(idxs, uvs)

    bot_idx = blade.add_vertex(0.0, 0.24, 0.0)
    bot_ring = ring_indices[0]
    for k in range(8):
        kn = (k + 1) % 8
        idxs = [bot_ring[kn], bot_ring[k], bot_idx]
        uvs = [sword_uv(blade.vertices[i][0], blade.vertices[i][1]) for i in idxs]
        blade.add_indexed_tri(idxs, uvs)
    parts.append(blade)

    # 1B. 日轮十字翼状护手 + 中央立体日核（更修长锐利的上扬双翼）
    guard = SubMesh("sun_crossguard", shading="flat")
    for side in (-1.0, 1.0):
        wing_profile = [
            (0.12, 0.22, 0.13, 0.13),
            (0.38, 0.35, 0.15, 0.11),
            (0.66, 0.62, 0.11, 0.08),
            (0.90, 0.96, 0.05, 0.036),
        ]
        w_rings: List[List[int]] = []
        for x, y, h, zt in wing_profile:
            sx = side * x
            ring = [
                guard.add_vertex(sx, y + h, 0.0),
                guard.add_vertex(sx, y, zt),
                guard.add_vertex(sx, y - h * 0.68, 0.0),
                guard.add_vertex(sx, y, -zt),
            ]
            w_rings.append(ring)
        for s in range(len(w_rings) - 1):
            r0, r1 = w_rings[s], w_rings[s + 1]
            for k in range(4):
                kn = (k + 1) % 4
                idxs = [r0[k], r0[kn], r1[kn], r1[k]] if side > 0 else [r0[kn], r0[k], r1[k], r1[kn]]
                uvs = [sword_uv(guard.vertices[i][0], guard.vertices[i][1]) for i in idxs]
                guard.add_indexed_quad(idxs, uvs)
        tip = guard.add_vertex(side * 1.01, 1.15, 0.0)
        last_r = w_rings[-1]
        for k in range(4):
            kn = (k + 1) % 4
            idxs = [last_r[k], last_r[kn], tip] if side > 0 else [last_r[kn], last_r[k], tip]
            uvs = [sword_uv(guard.vertices[i][0], guard.vertices[i][1]) for i in idxs]
            guard.add_indexed_tri(idxs, uvs)

        # 下掠副爪
        spur_pts = [
            (side * 0.28, 0.12, 0.0),
            (side * 0.56, -0.09, 0.0),
            (side * 0.38, 0.24, 0.07),
            (side * 0.38, 0.24, -0.07),
        ]
        sp_ids = [guard.add_vertex(*p) for p in spur_pts]
        for tri in [(0, 1, 2), (1, 0, 3), (0, 2, 3), (1, 3, 2)]:
            idxs = [sp_ids[t] for t in tri]
            uvs = [sword_uv(guard.vertices[i][0], guard.vertices[i][1]) for i in idxs]
            guard.add_indexed_tri(idxs, uvs)

    # 中央日轮八面菱核（前后凸起 z=±0.19）
    core_cy = 0.24
    core_r = 0.24
    c_front = guard.add_vertex(0.0, core_cy, 0.19)
    c_back = guard.add_vertex(0.0, core_cy, -0.19)
    c_ring = []
    for k in range(8):
        ang = k * math.pi * 0.25
        rad = core_r * (1.18 if k % 2 == 0 else 0.86)
        c_ring.append(guard.add_vertex(math.cos(ang) * rad, core_cy + math.sin(ang) * rad, 0.0))
    for k in range(8):
        kn = (k + 1) % 8
        for apex, a, b in [(c_front, c_ring[k], c_ring[kn]), (c_back, c_ring[kn], c_ring[k])]:
            idxs = [apex, a, b]
            uvs = [sword_uv(guard.vertices[i][0], guard.vertices[i][1]) for i in idxs]
            guard.add_indexed_tri(idxs, uvs)
    parts.append(guard)

    # 1C. 双侧修长流线烈焰剑脊副翼（与修长主刃无缝融合）
    spines = SubMesh("energy_spine_fins", shading="smooth")
    for side in (-1.0, 1.0):
        for z_sign in (-1.0, 1.0):
            fin_stations = [
                (1.12, 0.32, 0.08, 0.20),
                (1.56, 0.48, 0.14, 0.26),
                (2.22, 0.54, 0.16, 0.30),
                (2.92, 0.46, 0.13, 0.25),
                (3.52, 0.30, 0.07, 0.16),
                (3.98, 0.15, 0.02, 0.08),
            ]
            for i in range(len(fin_stations) - 1):
                y0, x0_out, z0_out, x0_in = fin_stations[i]
                y1, x1_out, z1_out, x1_in = fin_stations[i + 1]
                p0 = (side * x0_in, y0, z_sign * 0.04)
                p1 = (side * x0_out, y0 + 0.09, z_sign * z0_out)
                p2 = (side * x1_out, y1 + 0.09, z_sign * z1_out)
                p3 = (side * x1_in, y1, z_sign * 0.04)
                spines.add_quad(p0, p1, p2, p3, sword_uv(p0[0], p0[1]), sword_uv(p1[0], p1[1]),
                                sword_uv(p2[0], p2[1]), sword_uv(p3[0], p3[1]))
    parts.append(spines)

    # 1D. 剑柄与锥形配重尾棘
    hilt = SubMesh("hilt_pommel", shading="flat")
    hilt_stations = [
        (0.06, 0.085),
        (-0.22, 0.068),
        (-0.35, 0.115),
        (-0.48, 0.012),
    ]
    h_rings: List[List[int]] = []
    for y, r in hilt_stations:
        ring = []
        for k in range(6):
            ang = k * math.pi / 3.0
            ring.append(hilt.add_vertex(math.cos(ang) * r, y, math.sin(ang) * r))
        h_rings.append(ring)
    for s in range(len(h_rings) - 1):
        r0, r1 = h_rings[s], h_rings[s + 1]
        for k in range(6):
            kn = (k + 1) % 6
            idxs = [r0[k], r0[kn], r1[kn], r1[k]]
            uvs = [sword_uv(hilt.vertices[i][0], hilt.vertices[i][1]) for i in idxs]
            hilt.add_indexed_quad(idxs, uvs)
    parts.append(hilt)

    return parts


# ---------------------------------------------------------------------------
# 2. phainon_counter_cleave_arc : 竖直重劈裂空斩击弧面壳体 + 双侧撕裂副翼
# ---------------------------------------------------------------------------
def build_counter_cleave_arc() -> List[SubMesh]:
    parts: List[SubMesh] = []

    # 2A. 主斩弧立体 V/C 形刀浪壳体（YZ 平面内由头顶后上方劈向正前下方，共 156°）
    main_arc = SubMesh("cleave_main_shell", shading="smooth")
    segs = 32
    ang_start = math.radians(-115.0)  # 后上方起势
    ang_end = math.radians(42.0)      # 正前方接地劈落

    def arc_point(theta: float, r: float, x_off: float) -> Vec3:
        # theta = -90° 为正上方 (+Y)，0° 为正前方 (+Z)，+42° 扎入前下方
        y = -math.sin(theta) * r
        z = math.cos(theta) * r
        return (x_off, y, z)

    # 截面 5 点：左尾缘 -> 左脊凸 -> 外锋锐刃 -> 右脊凸 -> 右尾缘
    profile_rows: List[List[int]] = []
    for i in range(segs + 1):
        u = i / segs
        theta = ang_start + (ang_end - ang_start) * u
        env = math.sin(math.pi * (u ** 0.78)) ** 0.65
        width_r = 0.22 + 0.36 * env
        r_outer = 1.04 + 0.06 * math.sin(u * math.pi)
        r_ridge = r_outer - width_r * 0.34
        r_tail = r_outer - width_r
        x_ridge = 0.06 + 0.14 * env
        x_tail = 0.02 + 0.06 * env
        row = [
            main_arc.add_vertex(*arc_point(theta, r_tail, -x_tail)),
            main_arc.add_vertex(*arc_point(theta, r_ridge, -x_ridge)),
            main_arc.add_vertex(*arc_point(theta, r_outer, 0.0)),
            main_arc.add_vertex(*arc_point(theta, r_ridge, x_ridge)),
            main_arc.add_vertex(*arc_point(theta, r_tail, x_tail)),
        ]
        profile_rows.append(row)

    v_coords = [1.0, 0.36, 0.02, 0.36, 1.0]
    for i in range(segs):
        u0 = i / segs
        u1 = (i + 1) / segs
        r0 = profile_rows[i]
        r1 = profile_rows[i + 1]
        for k in range(4):
            idxs = [r0[k], r1[k], r1[k + 1], r0[k + 1]]
            uvs = [(u0, v_coords[k]), (u1, v_coords[k]), (u1, v_coords[k + 1]), (u0, v_coords[k + 1])]
            main_arc.add_indexed_quad(idxs, uvs)
    parts.append(main_arc)

    # 2B. 双侧外翻撕裂烈焰副翼（形成强烈立体气浪拖尾）
    wings = SubMesh("cleave_flame_wings", shading="smooth")
    wing_segs = 26
    for side in (-1.0, 1.0):
        w_rows: List[List[int]] = []
        for i in range(wing_segs + 1):
            t = i / wing_segs
            u = 0.08 + 0.86 * t
            theta = ang_start + (ang_end - ang_start) * u
            flare = math.sin(math.pi * t) ** 0.72
            wave = 0.035 * math.sin(t * math.pi * 4.0)
            p_in = arc_point(theta, 0.96 - 0.14 * (1.0 - flare), side * (0.12 + 0.04 * flare))
            p_mid = arc_point(theta, 0.88 - 0.08 * flare, side * (0.28 + 0.16 * flare + wave))
            p_out = arc_point(theta, 0.62 - 0.10 * flare, side * (0.40 + 0.24 * flare - wave))
            w_rows.append([
                wings.add_vertex(*p_in),
                wings.add_vertex(*p_mid),
                wings.add_vertex(*p_out),
            ])
        for i in range(wing_segs):
            u0 = 0.08 + 0.86 * (i / wing_segs)
            u1 = 0.08 + 0.86 * ((i + 1) / wing_segs)
            r0, r1 = w_rows[i], w_rows[i + 1]
            wings.add_indexed_quad([r0[0], r1[0], r1[1], r0[1]],
                                   [(u0, 0.18), (u1, 0.18), (u1, 0.56), (u0, 0.56)])
            wings.add_indexed_quad([r0[1], r1[1], r1[2], r0[2]],
                                   [(u0, 0.56), (u1, 0.56), (u1, 0.98), (u0, 0.98)])
    parts.append(wings)

    return parts


# ---------------------------------------------------------------------------
# 3. phainon_counter_edict_sigil : 立体纷争护盾法环 / 威压棘轮（XY 平面）
# ---------------------------------------------------------------------------
SIGIL_UV_RAD = 1.42


def sigil_uv(x: float, y: float) -> UV:
    u = max(0.0, min(1.0, 0.5 + 0.5 * (x / SIGIL_UV_RAD)))
    v = max(0.0, min(1.0, 0.5 - 0.5 * (y / SIGIL_UV_RAD)))
    return (u, v)


def build_counter_edict_sigil() -> List[SubMesh]:
    parts: List[SubMesh] = []

    # 3A. 斜切外威压法环（带前后双斜面立体厚度）
    outer = SubMesh("edict_outer_ring", shading="smooth")
    segs = 36
    o_rings: List[List[int]] = []
    for i in range(segs + 1):
        ang = (i / segs) * math.tau
        ca, sa = math.cos(ang), math.sin(ang)
        row = [
            outer.add_vertex(ca * 0.72, sa * 0.72, 0.02),
            outer.add_vertex(ca * 0.86, sa * 0.86, 0.09),
            outer.add_vertex(ca * 1.02, sa * 1.02, 0.00),
            outer.add_vertex(ca * 0.86, sa * 0.86, -0.07),
        ]
        o_rings.append(row)
    for i in range(segs):
        r0, r1 = o_rings[i], o_rings[i + 1]
        for k in range(4):
            kn = (k + 1) % 4
            idxs = [r0[k], r1[k], r1[kn], r0[kn]]
            uvs = [sigil_uv(outer.vertices[idx][0], outer.vertices[idx][1]) for idx in idxs]
            outer.add_indexed_quad(idxs, uvs)
    parts.append(outer)

    # 3B. 12 枚定向弯曲纷争棘刺（4 主芒长 1.38，8 副棘长 1.20，均带前后脊线）
    spurs = SubMesh("edict_strife_spurs", shading="flat")
    for k in range(12):
        base_ang = k * (math.tau / 12.0)
        is_major = (k % 3 == 0)
        r_base = 0.88
        r_mid = 1.12 if is_major else 1.04
        r_tip = 1.39 if is_major else 1.21
        half_w = 0.11 if is_major else 0.078
        hook = 0.14 if is_major else 0.18  # 弧形倒钩偏角

        p_bl = (math.cos(base_ang - half_w) * r_base, math.sin(base_ang - half_w) * r_base, 0.0)
        p_br = (math.cos(base_ang + half_w) * r_base, math.sin(base_ang + half_w) * r_base, 0.0)
        p_mf = (math.cos(base_ang + hook * 0.45) * r_mid, math.sin(base_ang + hook * 0.45) * r_mid, 0.075)
        p_mb = (math.cos(base_ang + hook * 0.45) * r_mid, math.sin(base_ang + hook * 0.45) * r_mid, -0.075)
        p_tip = (math.cos(base_ang + hook) * r_tip, math.sin(base_ang + hook) * r_tip, 0.02)

        for tri in [
            (p_bl, p_mf, p_tip),
            (p_mf, p_br, p_tip),
            (p_br, p_mb, p_tip),
            (p_mb, p_bl, p_tip),
        ]:
            spurs.add_tri(tri[0], tri[1], tri[2],
                          sigil_uv(tri[0][0], tri[0][1]),
                          sigil_uv(tri[1][0], tri[1][1]),
                          sigil_uv(tri[2][0], tri[2][1]))
    parts.append(spurs)

    # 3C. 中央微隆能源护盾穹面（0..0.76 半径，形成立体防反光罩）
    dome = SubMesh("edict_aegis_dome", shading="smooth")
    d_segs = 28
    d_rings = [0.0, 0.24, 0.50, 0.74]
    center_idx = dome.add_vertex(0.0, 0.0, 0.16)
    ring_ids: List[List[int]] = []
    for r in d_rings[1:]:
        z_h = 0.16 * (1.0 - (r / 0.78) ** 2)
        row = []
        for i in range(d_segs + 1):
            ang = (i / d_segs) * math.tau
            row.append(dome.add_vertex(math.cos(ang) * r, math.sin(ang) * r, z_h))
        ring_ids.append(row)

    for i in range(d_segs):
        idxs = [center_idx, ring_ids[0][i], ring_ids[0][i + 1]]
        uvs = [sigil_uv(dome.vertices[idx][0], dome.vertices[idx][1]) for idx in idxs]
        dome.add_indexed_tri(idxs, uvs)
    for r_i in range(len(ring_ids) - 1):
        r0, r1 = ring_ids[r_i], ring_ids[r_i + 1]
        for i in range(d_segs):
            idxs = [r0[i], r1[i], r1[i + 1], r0[i + 1]]
            uvs = [sigil_uv(dome.vertices[idx][0], dome.vertices[idx][1]) for idx in idxs]
            dome.add_indexed_quad(idxs, uvs)
    parts.append(dome)

    return parts


# ---------------------------------------------------------------------------
# 4. phainon_counter_crater_eruption : 裂地岩浆喷涌光锥 + 冲击波领环（水平无缝 UV）
# ---------------------------------------------------------------------------
def build_counter_crater_eruption() -> List[SubMesh]:
    parts: List[SubMesh] = []

    # 4A. 外层沟槽火山熔岩喷涌漏斗（Y: 0 -> 2.55，7 道棱脊起伏）
    funnel = SubMesh("eruption_magma_funnel", shading="smooth")
    segs = 28
    y_levels = [
        (0.00, 0.36, 1.00),
        (0.32, 0.50, 0.84),
        (0.75, 0.64, 0.66),
        (1.30, 0.88, 0.44),
        (1.92, 1.18, 0.22),
        (2.55, 1.52, 0.00),
    ]
    f_rows: List[List[int]] = []
    for y, r_base, _ in y_levels:
        row = []
        for i in range(segs + 1):
            u = i / segs
            ang = u * math.tau
            flute = 1.0 + 0.09 * math.sin(ang * 7.0 + y * 0.8) * (y / 2.55)
            r = r_base * flute
            row.append(funnel.add_vertex(math.cos(ang) * r, y, math.sin(ang) * r))
        f_rows.append(row)

    for j in range(len(y_levels) - 1):
        v0 = y_levels[j][2]
        v1 = y_levels[j + 1][2]
        r0, r1 = f_rows[j], f_rows[j + 1]
        for i in range(segs):
            u0 = i / segs
            u1 = (i + 1) / segs
            funnel.add_indexed_quad([r0[i], r0[i + 1], r1[i + 1], r1[i]],
                                    [(u0, v0), (u1, v0), (u1, v1), (u0, v1)])
    parts.append(funnel)

    # 4B. 内核炽白光柱（Y: 0 -> 2.90，收束强光核心）
    core = SubMesh("eruption_core_pillar", shading="smooth")
    c_segs = 20
    c_levels = [
        (0.00, 0.20, 1.00),
        (0.60, 0.26, 0.78),
        (1.45, 0.35, 0.48),
        (2.25, 0.46, 0.20),
        (2.90, 0.56, 0.00),
    ]
    c_rows: List[List[int]] = []
    for y, r, _ in c_levels:
        row = []
        for i in range(c_segs + 1):
            ang = (i / c_segs) * math.tau
            row.append(core.add_vertex(math.cos(ang) * r, y, math.sin(ang) * r))
        c_rows.append(row)
    for j in range(len(c_levels) - 1):
        v0, v1 = c_levels[j][2], c_levels[j + 1][2]
        r0, r1 = c_rows[j], c_rows[j + 1]
        for i in range(c_segs):
            u0, u1 = i / c_segs, (i + 1) / c_segs
            core.add_indexed_quad([r0[i], r0[i + 1], r1[i + 1], r1[i]],
                                  [(u0, v0), (u1, v0), (u1, v1), (u0, v1)])
    parts.append(core)

    # 4C. 贴地爆炸冲击波浪环（R: 0.42 -> 1.78，呈翻卷火浪拱起）
    collar = SubMesh("eruption_shock_collar", shading="smooth")
    k_segs = 32
    k_levels = [
        (0.42, 0.04, 0.98),
        (0.82, 0.34, 0.62),
        (1.26, 0.26, 0.30),
        (1.78, 0.04, 0.02),
    ]
    k_rows: List[List[int]] = []
    for r, y, _ in k_levels:
        row = []
        for i in range(k_segs + 1):
            ang = (i / k_segs) * math.tau
            ripple = 1.0 + 0.06 * math.cos(ang * 8.0)
            row.append(collar.add_vertex(math.cos(ang) * r * ripple, y, math.sin(ang) * r * ripple))
        k_rows.append(row)
    for j in range(len(k_levels) - 1):
        v0, v1 = k_levels[j][2], k_levels[j + 1][2]
        r0, r1 = k_rows[j], k_rows[j + 1]
        for i in range(k_segs):
            u0, u1 = i / k_segs, (i + 1) / k_segs
            collar.add_indexed_quad([r0[i], r0[i + 1], r1[i + 1], r1[i]],
                                    [(u0, v0), (u1, v0), (u1, v1), (u0, v1)])
    parts.append(collar)

    return parts


def render_wireframe_preview(all_models: Dict[str, List[SubMesh]]) -> None:
    """将 4 套 3D 模型按 3/4 透视投影绘制为线框/着色检查图，便于目检轮廓与拓扑。"""
    cell_w, cell_h = 460, 460
    canvas = Image.new("RGB", (cell_w * 2, cell_h * 2), (16, 14, 22))
    draw = ImageDraw.Draw(canvas)

    configs = [
        ("phainon_counter_greatsword", 0, 0, 28.0, 14.0),
        ("phainon_counter_cleave_arc", 1, 0, 48.0, 18.0),
        ("phainon_counter_edict_sigil", 0, 1, 24.0, 18.0),
        ("phainon_counter_crater_eruption", 1, 1, 32.0, 24.0),
    ]
    palette = [(255, 206, 86), (255, 118, 52), (145, 205, 255), (225, 130, 255)]

    for name, gx, gy, yaw_deg, pitch_deg in configs:
        parts = all_models[name]
        ox, oy = gx * cell_w, gy * cell_h
        draw.rectangle([ox + 4, oy + 4, ox + cell_w - 4, oy + cell_h - 4], outline=(55, 50, 72))

        all_v = np.array([v for p in parts for v in p.vertices], dtype=np.float32)
        vmin, vmax = all_v.min(axis=0), all_v.max(axis=0)
        center = (vmin + vmax) * 0.5
        span = float(np.max(vmax - vmin)) + 1e-5
        scale = (cell_w * 0.72) / span

        ry, rx = math.radians(yaw_deg), math.radians(pitch_deg)
        cy, sy = math.cos(ry), math.sin(ry)
        cx, sx = math.cos(rx), math.sin(rx)

        def proj(pt: Vec3) -> Tuple[float, float, float]:
            x, y, z = pt[0] - center[0], pt[1] - center[1], pt[2] - center[2]
            x1 = x * cy + z * sy
            z1 = -x * sy + z * cy
            y2 = y * cx - z1 * sx
            z2 = y * sx + z1 * cx
            return (ox + cell_w * 0.5 + x1 * scale, oy + cell_h * 0.52 - y2 * scale, z2)

        for p_idx, part in enumerate(parts):
            col = palette[p_idx % len(palette)]
            for corners, _ in part.faces:
                pts2 = [proj(part.vertices[c]) for c in corners]
                poly = [(p[0], p[1]) for p in pts2]
                fill = (col[0] // 5, col[1] // 5, col[2] // 5)
                draw.polygon(poly, fill=fill, outline=col)

        dims = vmax - vmin
        draw.text(
            (ox + 14, oy + 12),
            f"{name}\nsize=({dims[0]:.2f}, {dims[1]:.2f}, {dims[2]:.2f}) blk",
            fill=(245, 236, 210),
        )

    out_path = ART_DIR / "_preview_counter_models.png"
    canvas.save(out_path)
    print(f"Saved model preview: {out_path}")


def main() -> None:
    builders = {
        "phainon_counter_greatsword": build_counter_greatsword,
        "phainon_counter_cleave_arc": build_counter_cleave_arc,
        "phainon_counter_edict_sigil": build_counter_edict_sigil,
        "phainon_counter_crater_eruption": build_counter_crater_eruption,
    }
    all_models: Dict[str, List[SubMesh]] = {}
    for name, fn in builders.items():
        parts = fn()
        all_models[name] = parts
        stats = export_model(name, parts)
        print(f"Exported {name}: {stats['vertices']} verts, {stats['faces']} faces")
    render_wireframe_preview(all_models)


if __name__ == "__main__":
    main()
