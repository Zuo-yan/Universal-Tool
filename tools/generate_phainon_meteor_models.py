#!/usr/bin/env python3
"""
白厄觉醒战技「支柱·死星天裁」4 套 3D 特效自由网格模型生成脚本。

同时导出：
- 可在 Blockbench 5.x 打开编辑的 free 格式工程：art/assets/phainon_meteor_*.bbmodel
- NeoForge 客户端运行时预烘焙的 Wavefront OBJ：src/main/resources/assets/universal_tool/models/vfx/phainon_meteor_*.obj

坐标与 UV 严格对齐 Blockbench 5.2.2 导出规范：
- OBJ 坐标单位为方块（1.0 基准尺度），BBModel 顶点为 OBJ × 16.0；
- OBJ `vt` 行写出 `(u, 1.0 - v)`（与 Blockbench 导出一致，Java 侧 `PhainonVfxModelData.loadObjMesh` 再以 `1 - vt.y` 还原顶零 V 轴）；
- 所有网格均为闭合流形或光滑参数壳体，无退化法线、无 NaN。

生成清单：
1. phainon_meteor_star_core   — 不规则多面隆起与陨石坑棱角的死星陨石球核（半径 ~1.0，迎风下尖）
2. phainon_meteor_flame_tail  — 包裹陨石向后拉出的双层螺旋烈焰彗尾巨锥壳体（长度 ~9.8 基准尺度）
3. phainon_meteor_sky_rift    — 高空立体漏斗形破云漩涡穹环（含内唇下探厚度层）
4. phainon_meteor_blast_dome  — 命中瞬间的双层蘑菇火云爆炸穹顶 + 地面扩散冲击波裙环
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Dict, List, Sequence, Tuple
from uuid import NAMESPACE_URL, uuid5

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
ART_DIR = ROOT / "art" / "assets"
VFX_DIR = ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "models" / "vfx"

Vec3 = Tuple[float, float, float]
UV = Tuple[float, float]
QuadFace = Tuple[Tuple[int, int, int, int], Tuple[UV, UV, UV, UV]]


class SubMesh:
    """单个自由网格部件，保存方块尺度顶点、四边形/三角面与 UV。"""

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
        i0: int,
        i1: int,
        i2: int,
        i3: int,
        uv0: UV,
        uv1: UV,
        uv2: UV,
        uv3: UV,
    ) -> None:
        self.faces.append(([i0, i1, i2, i3], [uv0, uv1, uv2, uv3]))


def compute_vertex_normals(sub: SubMesh) -> List[Vec3]:
    """根据相邻面法线面积加权计算平滑顶点法线（flat 着色时仍可用于兜底）。"""
    acc = np.zeros((len(sub.vertices), 3), dtype=np.float64)
    verts = np.asarray(sub.vertices, dtype=np.float64)
    for corners, _ in sub.faces:
        if len(corners) < 3:
            continue
        p0, p1, p2 = verts[corners[0]], verts[corners[1]], verts[corners[2]]
        n = np.cross(p1 - p0, p2 - p0)
        norm = np.linalg.norm(n)
        if norm > 1e-9:
            n /= norm
            for ci in corners:
                acc[ci] += n
    out: List[Vec3] = []
    for row in acc:
        length = float(np.linalg.norm(row))
        if length > 1e-8:
            out.append((float(row[0] / length), float(row[1] / length), float(row[2] / length)))
        else:
            out.append((0.0, 1.0, 0.0))
    return out


def stable_uuid(tag: str) -> str:
    return str(uuid5(NAMESPACE_URL, f"universal-tool:phainon-meteor/{tag}"))


def export_model(model_name: str, submeshes: Sequence[SubMesh]) -> Tuple[int, int]:
    """同时写出 Blockbench 5.0 free 格式 .bbmodel 与运行时 .obj，返回 (总顶点数, 总面数)。"""
    ART_DIR.mkdir(parents=True, exist_ok=True)
    VFX_DIR.mkdir(parents=True, exist_ok=True)

    obj_lines: List[str] = [
        f"# Phainon Meteor VFX 3D Mesh: {model_name}",
        "# Generated for Blockbench 5.0 free format & NeoForge PhainonVfxModelData",
        "mtllib none",
        "",
    ]

    v_offset = 1
    vt_offset = 1
    vn_offset = 1
    bb_elements: List[Dict] = []
    group_uuid = stable_uuid(f"{model_name}/root")
    child_uuids: List[str] = []

    total_verts = 0
    total_faces = 0

    for sub in submeshes:
        total_verts += len(sub.vertices)
        total_faces += len(sub.faces)
        obj_lines.append(f"o {sub.name}")

        for vx, vy, vz in sub.vertices:
            obj_lines.append(f"v {vx:.6f} {vy:.6f} {vz:.6f}")

        normals = compute_vertex_normals(sub)
        for nx, ny, nz in normals:
            obj_lines.append(f"vn {nx:.6f} {ny:.6f} {nz:.6f}")

        bb_verts: Dict[str, List[float]] = {}
        for i, (vx, vy, vz) in enumerate(sub.vertices):
            bb_verts[f"v{i}"] = [round(vx * 16.0, 5), round(vy * 16.0, 5), round(vz * 16.0, 5)]

        bb_faces: Dict[str, Dict] = {}
        for fi, (corners, uvs) in enumerate(sub.faces):
            f_tokens: List[str] = []
            bb_uv_map: Dict[str, List[float]] = {}
            bb_corner_keys: List[str] = []
            for ci, (u, v) in zip(corners, uvs):
                # Blockbench OBJ 导出标准：vt 写出 (u, 1 - v)
                obj_lines.append(f"vt {u:.6f} {1.0 - v:.6f}")
                f_tokens.append(f"{v_offset + ci}/{vt_offset}/{vn_offset + ci}")
                vt_offset += 1
                key = f"v{ci}"
                bb_corner_keys.append(key)
                bb_uv_map[key] = [round(u * 16.0, 5), round(v * 16.0, 5)]
            obj_lines.append("f " + " ".join(f_tokens))
            bb_faces[f"f{fi}"] = {
                "uv": bb_uv_map,
                "vertices": bb_corner_keys,
                "texture": False,
            }

        v_offset += len(sub.vertices)
        vn_offset += len(normals)
        obj_lines.append("")

        elem_uuid = stable_uuid(f"{model_name}/{sub.name}")
        child_uuids.append(elem_uuid)
        bb_elements.append(
            {
                "name": sub.name,
                "color": 0,
                "origin": [0, 0, 0],
                "rotation": [0, 0, 0],
                "shading": sub.shading,
                "export": True,
                "visibility": True,
                "locked": False,
                "render_order": "default",
                "scope": 0,
                "allow_mirror_modeling": False,
                "vertices": bb_verts,
                "faces": bb_faces,
                "type": "mesh",
                "uuid": elem_uuid,
            }
        )

    obj_path = VFX_DIR / f"{model_name}.obj"
    obj_path.write_text("\n".join(obj_lines) + "\n", encoding="utf-8")

    bb_project = {
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
                "name": f"{model_name}_root",
                "origin": [0, 0, 0],
                "color": 0,
                "uuid": group_uuid,
                "export": True,
                "mirror_uv": False,
                "isOpen": True,
                "locked": False,
                "visibility": True,
                "autouv": 0,
            }
        ],
        "outliner": [{"uuid": group_uuid, "isOpen": True, "children": child_uuids}],
        "textures": [],
    }
    bb_path = ART_DIR / f"{model_name}.bbmodel"
    bb_path.write_text(json.dumps(bb_project, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return total_verts, total_faces


# =====================================================================
# 1. 不规则多面隆起死星陨石球核 (phainon_meteor_star_core)
# =====================================================================
def build_star_core() -> List[SubMesh]:
    sub = SubMesh("dead_star_core", shading="smooth")
    u_segs = 32
    v_segs = 18

    # 5 个预设陨石撞击坑方向 (ux, uy, uz, 半径, 深度)
    craters = [
        (0.74, 0.36, 0.56, 0.52, 0.14),
        (-0.68, -0.22, 0.70, 0.48, 0.13),
        (0.18, 0.65, -0.74, 0.55, 0.15),
        (-0.55, -0.58, -0.60, 0.44, 0.12),
        (0.82, -0.45, -0.35, 0.42, 0.11),
    ]

    grid: List[List[int]] = []
    for j in range(v_segs + 1):
        vf = j / v_segs  # 0=顶部(+Y), 1=迎风底部(-Y)
        # 极点留极小开口防止法线退化与 UV 拉伸
        theta = 0.045 + vf * (math.pi - 0.090)
        sin_t = math.sin(theta)
        cos_t = math.cos(theta)

        row: List[int] = []
        for i in range(u_segs + 1):
            uf = i / u_segs
            phi = uf * math.tau
            dx = sin_t * math.cos(phi)
            dy = cos_t
            dz = sin_t * math.sin(phi)

            # 3D 连续多阶球谐棱角扰动（仅依赖单位方向 dx,dy,dz，天然 100% 闭合无缝！）
            ridge_a = 0.095 * math.sin(dx * 3.8 + dz * 2.9 + 0.5) * math.cos(dy * 3.4 - dz * 2.3)
            ridge_b = 0.068 * math.cos(dx * 6.2 - dy * 5.1 + dz * 4.4)
            crag = 0.045 * abs(math.sin(dx * 8.4 + dy * 7.2 - dz * 6.8))
            radius = 0.96 + ridge_a + ridge_b + crag

            # 陨石坑凹陷 + 坑缘微隆起
            for cx, cy, cz, cr, depth in craters:
                dot = max(-1.0, min(1.0, dx * cx + dy * cy + dz * cz))
                ang_d = math.acos(dot)
                if ang_d < cr * 1.45:
                    q = ang_d / cr
                    if q < 1.0:
                        radius -= depth * ((1.0 - q * q) ** 1.6)
                    else:
                        rim = math.exp(-(((q - 1.12) / 0.18) ** 2))
                        radius += depth * 0.34 * rim

            # 轻微迎风拉长：下坠底部（-Y）略呈压迫尖棱，顶部略收敛，呼应关键帧 06 巨陨石廓形
            prow_stretch = 1.18 if dy < 0 else 1.06
            taper_xz = 1.0 - 0.10 * max(0.0, -dy) ** 1.6
            vx = dx * radius * taper_xz
            vy = dy * radius * prow_stretch
            vz = dz * radius * taper_xz
            row.append(sub.add_vertex(vx, vy, vz))
        grid.append(row)

    for j in range(v_segs):
        v0 = j / v_segs
        v1 = (j + 1) / v_segs
        for i in range(u_segs):
            u0 = i / u_segs
            u1 = (i + 1) / u_segs
            sub.add_quad(
                grid[j][i],
                grid[j + 1][i],
                grid[j + 1][i + 1],
                grid[j][i + 1],
                (u0, v0),
                (u0, v1),
                (u1, v1),
                (u1, v0),
            )
    return [sub]


# =====================================================================
# 2. 双层螺旋烈焰彗尾巨锥壳体 (phainon_meteor_flame_tail)
#    包裹陨石前锋 (y=-1.35) 并向上拉出超长螺旋火焰尾羽 (y=+8.6)
# =====================================================================
def build_flame_tail() -> List[SubMesh]:
    def make_sheath(
        name: str,
        y_bottom: float,
        y_top: float,
        r_belly: float,
        r_tip: float,
        twist_turns: float,
        ripple_amp: float,
        ripple_freq: int,
        phase: float,
    ) -> SubMesh:
        sub = SubMesh(name, shading="smooth")
        u_segs = 32
        v_segs = 18

        grid: List[List[int]] = []
        for j in range(v_segs + 1):
            vf = j / v_segs  # 0=高空尾梢(y_top), 1=陨石前锋底部(y_bottom)
            # 非线性纵向采样：陨石头部曲面加密，尾部舒展拉长
            s = 1.0 - vf  # 0=头部, 1=尾梢
            y = y_bottom + (y_top - y_bottom) * (s ** 1.28)

            # 剖面半径包络：迎风前锋收束包覆 -> 陨石腹腰膨胀形成烈焰激波 -> 高空收尖拖尾
            if s < 0.16:
                t = s / 0.16
                base_r = 0.18 + (r_belly - 0.18) * math.sin(t * math.pi * 0.5) ** 0.72
            else:
                t = (s - 0.16) / 0.84
                flare = 0.14 * r_belly * math.exp(-(((t - 0.14) / 0.14) ** 2))
                base_r = r_belly * ((1.0 - t) ** 0.82) + r_tip * (t ** 0.75) + flare

            twist = twist_turns * math.tau * s + phase
            row: List[int] = []
            for i in range(u_segs + 1):
                uf = i / u_segs
                ang = uf * math.tau + twist
                # 周期性火焰舌片起伏（严格 U 闭合）
                wave = (
                    math.sin(ripple_freq * uf * math.tau + s * 6.5 + phase)
                    + 0.45 * math.cos((ripple_freq + 3) * uf * math.tau - s * 8.0)
                )
                r = base_r * (1.0 + ripple_amp * wave * (0.35 + 0.65 * s))
                # 火焰边缘略带纵向飘逸波纹
                dy = ripple_amp * 0.45 * base_r * wave * s
                row.append(sub.add_vertex(r * math.cos(ang), y + dy, r * math.sin(ang)))
            grid.append(row)

        for j in range(v_segs):
            v0 = j / v_segs
            v1 = (j + 1) / v_segs
            for i in range(u_segs):
                u0 = i / u_segs
                u1 = (i + 1) / u_segs
                sub.add_quad(
                    grid[j][i],
                    grid[j + 1][i],
                    grid[j + 1][i + 1],
                    grid[j][i + 1],
                    (u0, v0),
                    (u0, v1),
                    (u1, v1),
                    (u1, v0),
                )
        return sub

    # 内层白炽高压激波彗尾（紧贴陨石，拉得极高，呼应关键帧 05 贯天火柱）
    inner = make_sheath(
        name="flame_tail_inner",
        y_bottom=-1.42,
        y_top=8.60,
        r_belly=1.36,
        r_tip=0.28,
        twist_turns=0.45,
        ripple_amp=0.065,
        ripple_freq=5,
        phase=0.0,
    )
    # 外层反向螺旋撕裂烈焰披风（略宽、锯齿翻卷，呼应关键帧 06 两侧翻腾火翼）
    outer = make_sheath(
        name="flame_tail_outer",
        y_bottom=-1.18,
        y_top=6.40,
        r_belly=1.68,
        r_tip=0.62,
        twist_turns=-0.38,
        ripple_amp=0.115,
        ripple_freq=7,
        phase=1.35,
    )
    return [inner, outer]


# =====================================================================
# 3. 高空立体漏斗形破云漩涡穹环 (phainon_meteor_sky_rift)
#    归一化半径 1.0，径向平面 UV 对应 phainon_meteor_sky_vortex.png (1024x1024)
# =====================================================================
def build_sky_rift() -> List[SubMesh]:
    def make_funnel(
        name: str,
        r_min: float,
        r_max: float,
        y_inner: float,
        y_outer: float,
        bow: float,
        wave_amp: float,
        rings: int,
        sides: int,
        phase: float,
    ) -> SubMesh:
        sub = SubMesh(name, shading="smooth")
        grid: List[List[int]] = []
        uvs: List[List[UV]] = []
        for j in range(rings + 1):
            rf = j / rings
            r = r_min + (r_max - r_min) * (rf ** 1.15)
            # 漏斗下探弧线：中心漩涡眼向下低垂压迫地面，外围向高空铺展
            base_y = y_inner + (y_outer - y_inner) * (rf ** 0.85) + bow * math.sin(rf * math.pi)
            row: List[int] = []
            uv_row: List[UV] = []
            for i in range(sides + 1):
                ang = (i / sides) * math.tau
                ca, sa = math.cos(ang), math.sin(ang)
                # 雷暴云墙三维立体波浪起伏
                ripple = (
                    math.sin(4.0 * ang + rf * 7.5 + phase)
                    + 0.45 * math.cos(7.0 * ang - rf * 5.0)
                ) * wave_amp * math.sin(rf * math.pi)
                x = r * ca
                z = r * sa
                row.append(sub.add_vertex(x, base_y + ripple, z))
                # 径向居中 UV，严格映射 [0, 1]^2
                u = 0.5 + 0.5 * x
                v = 0.5 + 0.5 * z
                uv_row.append((u, v))
            grid.append(row)
            uvs.append(uv_row)

        for j in range(rings):
            for i in range(sides):
                sub.add_quad(
                    grid[j][i],
                    grid[j + 1][i],
                    grid[j + 1][i + 1],
                    grid[j][i + 1],
                    uvs[j][i],
                    uvs[j + 1][i],
                    uvs[j + 1][i + 1],
                    uvs[j][i + 1],
                )
        return sub

    # 主高空巨型破云漏斗云壁（水平覆盖广、下探感强烈）
    main_canopy = make_funnel(
        name="sky_rift_canopy",
        r_min=0.095,
        r_max=1.0,
        y_inner=-0.16,
        y_outer=0.28,
        bow=-0.045,
        wave_amp=0.026,
        rings=14,
        sides=48,
        phase=0.0,
    )
    # 次层漩涡眼灼烧内唇（下沉略低形成立体云层厚度与视差）
    inner_lip = make_funnel(
        name="sky_rift_inner_lip",
        r_min=0.11,
        r_max=0.42,
        y_inner=-0.23,
        y_outer=-0.02,
        bow=-0.03,
        wave_amp=0.018,
        rings=8,
        sides=36,
        phase=1.8,
    )
    return [main_canopy, inner_lip]


# =====================================================================
# 4. 多层半球爆炸火云穹顶 + 地面扩散冲击环 (phainon_meteor_blast_dome)
#    归一化基础半径 1.0，水平无缝 UV 对应 phainon_meteor_blast_dome.png
# =====================================================================
def build_blast_dome() -> List[SubMesh]:
    def make_canopy(
        name: str,
        r_scale: float,
        h_scale: float,
        mushroom_bulge: float,
        ripple_amp: float,
        u_segs: int,
        v_segs: int,
        phase: float,
    ) -> SubMesh:
        sub = SubMesh(name, shading="smooth")
        grid: List[List[int]] = []
        for j in range(v_segs + 1):
            vf = j / v_segs  # 0=天顶, 1=地面底座
            phi_lat = 0.07 + vf * (math.pi * 0.5 - 0.07)
            sin_p = math.sin(phi_lat)
            cos_p = math.cos(phi_lat)

            # 蘑菇爆炸云轮廓：中上部大幅向外鼓胀，靠近地面略微收腰再展开
            bulge = 1.0 + mushroom_bulge * math.exp(-(((vf - 0.52) / 0.28) ** 2))
            base_r = r_scale * sin_p * bulge
            base_y = h_scale * cos_p

            row: List[int] = []
            for i in range(u_segs + 1):
                uf = i / u_segs
                ang = uf * math.tau
                # 翻涌爆炸火云的立体球面起伏
                wave = (
                    math.sin(6.0 * ang + vf * 4.5 + phase)
                    + 0.45 * math.cos(11.0 * ang - vf * 6.2)
                ) * ripple_amp * math.sin(vf * math.pi)
                r = base_r * (1.0 + wave)
                y = max(0.0, base_y + h_scale * wave * 0.35)
                row.append(sub.add_vertex(r * math.cos(ang), y, r * math.sin(ang)))
            grid.append(row)

        for j in range(v_segs):
            v0 = j / v_segs
            v1 = (j + 1) / v_segs
            for i in range(u_segs):
                u0 = i / u_segs
                u1 = (i + 1) / u_segs
                sub.add_quad(
                    grid[j][i],
                    grid[j + 1][i],
                    grid[j + 1][i + 1],
                    grid[j][i + 1],
                    (u0, v0),
                    (u0, v1),
                    (u1, v1),
                    (u1, v0),
                )
        return sub

    def make_shock_ring(name: str) -> SubMesh:
        """地面高速横扫的立体冲击波裙环（呼应关键帧 07/08 贴地激波浪墙）。"""
        sub = SubMesh(name, shading="smooth")
        u_segs = 40
        v_segs = 6
        grid: List[List[int]] = []
        for j in range(v_segs + 1):
            vf = j / v_segs  # 0=外缘锋面(r=1.36), 1=内侧贴地基部(r=0.58)
            s = 1.0 - vf
            r_base = 0.58 + 0.80 * s
            # 冲击波前沿微微翘起呈激波弧面
            y_base = 0.02 + 0.16 * math.sin(s * math.pi * 0.88)
            row: List[int] = []
            for i in range(u_segs + 1):
                uf = i / u_segs
                ang = uf * math.tau
                ripple = 0.045 * (math.sin(8.0 * ang + s * 4.0) + 0.4 * math.cos(13.0 * ang))
                r = r_base * (1.0 + ripple * s)
                y = y_base + 0.045 * ripple * s
                row.append(sub.add_vertex(r * math.cos(ang), y, r * math.sin(ang)))
            grid.append(row)

        for j in range(v_segs):
            # 采样爆炸贴图中下半高能白金冲击环带 (v in [0.55, 0.94])
            v0 = 0.55 + 0.39 * (j / v_segs)
            v1 = 0.55 + 0.39 * ((j + 1) / v_segs)
            for i in range(u_segs):
                u0 = i / u_segs
                u1 = (i + 1) / u_segs
                sub.add_quad(
                    grid[j][i],
                    grid[j + 1][i],
                    grid[j + 1][i + 1],
                    grid[j][i + 1],
                    (u0, v0),
                    (u0, v1),
                    (u1, v1),
                    (u1, v0),
                )
        return sub

    # 外层巨型爆炸蘑菇火云穹顶
    outer_dome = make_canopy(
        name="blast_dome_outer",
        r_scale=1.0,
        h_scale=0.72,
        mushroom_bulge=0.16,
        ripple_amp=0.055,
        u_segs=36,
        v_segs=14,
        phase=0.0,
    )
    # 内层白炽核爆火穹（增强体积厚度与中心耀斑感）
    inner_dome = make_canopy(
        name="blast_dome_inner",
        r_scale=0.66,
        h_scale=0.52,
        mushroom_bulge=0.08,
        ripple_amp=0.035,
        u_segs=28,
        v_segs=10,
        phase=1.7,
    )
    # 地面扩散冲击波裙环
    shock_ring = make_shock_ring("blast_shock_ring")
    return [outer_dome, inner_dome, shock_ring]


def main() -> None:
    builders = [
        ("phainon_meteor_star_core", build_star_core),
        ("phainon_meteor_flame_tail", build_flame_tail),
        ("phainon_meteor_sky_rift", build_sky_rift),
        ("phainon_meteor_blast_dome", build_blast_dome),
    ]
    for name, fn in builders:
        verts, faces = export_model(name, fn())
        print(f"[OK] {name:28s} verts={verts:4d} faces={faces:4d} -> .bbmodel & .obj")


if __name__ == "__main__":
    main()
