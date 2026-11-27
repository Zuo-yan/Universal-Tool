#!/usr/bin/env python3
"""
白厄觉醒战技「支柱·死星天裁」高精度 5 张 VFX 贴图程序精绘脚本（精修版）。

严格遵循 minecraft-skill-vfx v2.0 防粗糙铁律：
- 球壳/彗尾/爆炸穹顶全部从周期噪声 periodic_fbm + wrap_x=True 出发，保证 100% 水平无缝；
- 周期 Voronoi 采用上下幽灵晶胞扩展，彻底杜绝上下边界重复晶胞伪影；
- 死星焦岩核 body 与岩浆裂纹 glow 由同一份周期 Voronoi 裂隙母版派生，像素级精准对齐；
- 所有幂运算前对底数做 np.maximum(0.0, ...) 防止 float32 边界 NaN；
- 柔和 HDR 色调映射保留高饱和金红层次，杜绝死白糊块与极点捏缩。

输出目录：src/main/resources/assets/universal_tool/textures/misc/
1. phainon_meteor_core_body.png   (1024x512) 暗黑焦炭死星陨石岩体 + 深裂缝（水平无缝，实体/半透明压暗）
2. phainon_meteor_core_glow.png   (1024x512) 同 UV 对齐的炽熔岩浆裂纹与烈焰流纹（水平无缝，Additive）
3. phainon_meteor_trail.png       (1024x512) 超长炽金-炎橙-白炽彗尾焰幔（水平无缝，Additive）
4. phainon_meteor_sky_vortex.png  (1024x1024) 高空云层破洞漩涡与金红天幕裂隙（径向居中，暗紫雷暴云+炽金内唇）
5. phainon_meteor_blast_dome.png  (1024x512) 命中后烈金白炽爆炸云穹与放射光浪（水平无缝，Additive）
"""

from __future__ import annotations

import math
import random
from pathlib import Path
from typing import Sequence, Tuple

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "textures" / "misc"


def periodic_fbm(
    h: int,
    w: int,
    octaves: Sequence[Tuple[int, int, float]],
    seed: int = 42,
    wrap_y: bool = False,
) -> np.ndarray:
    """生成严格水平无缝（可选垂直无缝）的多八度平滑周期噪声场。"""
    rng = np.random.default_rng(seed)
    acc = np.zeros((h, w), dtype=np.float32)
    weight_sum = 0.0

    y_lin = np.linspace(0.0, 1.0, h, endpoint=False, dtype=np.float32)
    x_lin = np.linspace(0.0, 1.0, w, endpoint=False, dtype=np.float32)

    for nx, ny, amp in octaves:
        grid = rng.random((ny if wrap_y else ny + 1, nx), dtype=np.float32)
        gx = x_lin * nx
        gy = y_lin * ny
        ix0 = np.floor(gx).astype(np.int32) % nx
        ix1 = (ix0 + 1) % nx
        if wrap_y:
            iy0 = np.floor(gy).astype(np.int32) % ny
            iy1 = (iy0 + 1) % ny
        else:
            iy0 = np.floor(gy).astype(np.int32)
            iy1 = np.minimum(iy0 + 1, ny)

        fx = gx - np.floor(gx)
        fy = gy - np.floor(gy)
        sx = fx * fx * (3.0 - 2.0 * fx)
        sy = fy * fy * (3.0 - 2.0 * fy)

        v00 = grid[iy0[:, None], ix0[None, :]]
        v10 = grid[iy0[:, None], ix1[None, :]]
        v01 = grid[iy1[:, None], ix0[None, :]]
        v11 = grid[iy1[:, None], ix1[None, :]]

        top = v00 * (1.0 - sx[None, :]) + v10 * sx[None, :]
        bot = v01 * (1.0 - sx[None, :]) + v11 * sx[None, :]
        val = top * (1.0 - sy[:, None]) + bot * sy[:, None]
        acc += val * amp
        weight_sum += amp

    return acc / max(1e-5, weight_sum)


def cartesian_fbm(
    xx: np.ndarray,
    yy: np.ndarray,
    octaves: Sequence[Tuple[float, float]],
    seed: int = 42,
) -> np.ndarray:
    """在二维笛卡尔坐标 (xx, yy) 上采样无极点伪影的平滑 FBM 噪声。"""
    rng = np.random.default_rng(seed)
    acc = np.zeros_like(xx, dtype=np.float32)
    weight_sum = 0.0
    gs = 128

    for freq, amp in octaves:
        grid = rng.random((gs, gs), dtype=np.float32)
        gx = (xx * freq + 64.0)
        gy = (yy * freq + 64.0)
        ix0 = np.floor(gx).astype(np.int32) % gs
        iy0 = np.floor(gy).astype(np.int32) % gs
        ix1 = (ix0 + 1) % gs
        iy1 = (iy0 + 1) % gs

        fx = gx - np.floor(gx)
        fy = gy - np.floor(gy)
        sx = fx * fx * (3.0 - 2.0 * fx)
        sy = fy * fy * (3.0 - 2.0 * fy)

        v00 = grid[iy0, ix0]
        v10 = grid[iy0, ix1]
        v01 = grid[iy1, ix0]
        v11 = grid[iy1, ix1]

        top = v00 * (1.0 - sx) + v10 * sx
        bot = v01 * (1.0 - sx) + v11 * sx
        acc += (top * (1.0 - sy) + bot * sy) * amp
        weight_sum += amp

    return acc / max(1e-5, weight_sum)


def periodic_voronoi_edges(
    h: int,
    w: int,
    cells_x: int,
    cells_y: int,
    seed: int,
    warp_u: np.ndarray,
    warp_v: np.ndarray,
) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    计算严格水平无缝（U 周期）且上下边缘完全无重复晶胞伪影的 Voronoi 晶胞边界距离场。
    上下各扩充 1 行独立幽灵晶胞（row -1 与 row cells_y），保证全图 [0, 1] 裂隙连续自然。
    """
    rng = np.random.default_rng(seed)
    total_y = cells_y + 2  # 索引 0 对应逻辑行 -1，索引 cells_y+1 对应逻辑行 cells_y
    pts_x = (
        np.arange(cells_x, dtype=np.float32)[None, :]
        + rng.uniform(0.14, 0.86, (total_y, cells_x))
    ).astype(np.float32)
    pts_y = (
        (np.arange(total_y, dtype=np.float32) - 1.0)[:, None]
        + rng.uniform(0.14, 0.86, (total_y, cells_x))
    ).astype(np.float32)
    cell_val = rng.uniform(0.0, 1.0, (total_y, cells_x)).astype(np.float32)

    u = (np.linspace(0.0, 1.0, w, endpoint=False, dtype=np.float32)[None, :] + warp_u) % 1.0
    v = np.clip(np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None] + warp_v, 0.0, 0.9999)

    gx = u * cells_x
    gy = v * cells_y
    ix = np.floor(gx).astype(np.int32)
    iy = np.floor(gy).astype(np.int32)  # [0, cells_y - 1]

    d1 = np.full((h, w), 1e6, dtype=np.float32)
    d2 = np.full((h, w), 1e6, dtype=np.float32)
    best_hash = np.zeros((h, w), dtype=np.float32)

    for dy in (-1, 0, 1):
        # 偏移 +1 映射到 [0, cells_y + 1]，绝不与自身重叠
        ny = iy + dy + 1
        for dx in (-1, 0, 1):
            nx = (ix + dx) % cells_x
            px = pts_x[ny, nx]
            py = pts_y[ny, nx]

            ddx = gx - px
            ddx = (ddx + cells_x * 0.5) % cells_x - cells_x * 0.5
            ddy = gy - py
            dist = np.sqrt(ddx * ddx + ddy * ddy + 1e-8)

            mask1 = dist < d1
            d2 = np.where(mask1, d1, np.minimum(d2, dist))
            d1 = np.where(mask1, dist, d1)
            best_hash = np.where(mask1, cell_val[ny, nx], best_hash)

    edge_dist = np.maximum(0.0, d2 - d1)
    return edge_dist, d1, best_hash


def add_hdr_bloom(
    img_f: np.ndarray,
    radii_weights: Sequence[Tuple[float, float]],
    threshold: float = 0.24,
    wrap_x: bool = False,
) -> np.ndarray:
    """对浮点 RGBA 图层施加多层 HDR 高斯辉光（支持 wrap_x 水平无缝卷积）。"""
    h, w, _ = img_f.shape
    rgb = np.maximum(0.0, img_f[..., :3])
    lum = 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]
    extract = np.clip((lum - threshold) / max(1e-4, 1.0 - threshold), 0.0, 2.0)[..., None]
    bright = np.clip(rgb * extract * 255.0 / 1.5, 0.0, 255.0).astype(np.uint8)

    acc = np.zeros_like(rgb)
    for r, weight in radii_weights:
        if wrap_x:
            pad = min(w // 2, int(math.ceil(r * 3.0)))
            padded = np.concatenate([bright[:, -pad:, :], bright, bright[:, :pad, :]], axis=1)
            pil_p = Image.fromarray(padded, mode="RGB").filter(ImageFilter.GaussianBlur(radius=r))
            blurred = np.asarray(pil_p, dtype=np.float32)[:, pad:pad + w, :] / 255.0 * 1.5
        else:
            pil_b = Image.fromarray(bright, mode="RGB").filter(ImageFilter.GaussianBlur(radius=r))
            blurred = np.asarray(pil_b, dtype=np.float32) / 255.0 * 1.5
        acc += blurred * weight

    out = img_f.copy()
    out[..., :3] = np.clip(rgb + acc, 0.0, 2.3)
    bloom_lum = np.max(acc, axis=-1)
    out[..., 3] = np.clip(np.maximum(out[..., 3], bloom_lum * 0.85), 0.0, 1.0)
    return out


def finalize_additive_rgba(img_f: np.ndarray, exposure: float = 1.15) -> Image.Image:
    """将浮点发光图层转为 8-bit RGBA（柔光色调映射，高饱和不过曝、零 NaN）。"""
    rgb = np.nan_to_num(img_f[..., :3], nan=0.0, posinf=2.4, neginf=0.0)
    rgb = np.clip(rgb, 0.0, 2.8)
    mapped = (rgb / (1.0 + 0.42 * rgb)) * exposure
    mapped = np.clip(mapped, 0.0, 1.0)
    alpha_in = np.nan_to_num(img_f[..., 3], nan=0.0, posinf=1.0, neginf=0.0)
    alpha = np.clip(np.maximum(alpha_in, np.max(mapped, axis=-1) * 0.88), 0.0, 1.0)
    # 纯暗背景区域干净归零，避免 Additive 叠加发灰
    black_cut = np.clip((np.max(mapped, axis=-1) - 0.012) / 0.04, 0.0, 1.0)
    alpha *= black_cut
    rgba = np.zeros((img_f.shape[0], img_f.shape[1], 4), dtype=np.uint8)
    rgba[..., :3] = np.clip(mapped * 255.0, 0, 255).astype(np.uint8)
    rgba[..., 3] = np.clip(alpha * 255.0, 0, 255).astype(np.uint8)
    return Image.fromarray(rgba, mode="RGBA")


def scatter_embers(
    canvas: np.ndarray,
    count: int,
    seed: int,
    x_range: Tuple[float, float],
    y_range: Tuple[float, float],
    col_core: Tuple[float, float, float],
    col_halo: Tuple[float, float, float],
    angle_range: Tuple[float, float] = (-0.35, 0.35),
    elong_range: Tuple[float, float] = (2.0, 4.8),
    rad_range: Tuple[float, float] = (1.5, 4.5),
    wrap_x: bool = True,
) -> None:
    """撒布发光火星与拉丝余烬（支持 wrap_x 水平无缝跨边）。"""
    rng = random.Random(seed)
    h, w, _ = canvas.shape
    c_core = np.array(col_core, dtype=np.float32)
    c_halo = np.array(col_halo, dtype=np.float32)

    for _ in range(count):
        cx = rng.uniform(*x_range) * w
        cy = rng.uniform(*y_range) * h
        rad = rng.uniform(*rad_range)
        ang = rng.uniform(*angle_range)
        elong = rng.uniform(*elong_range)
        intensity = rng.uniform(0.55, 1.25)
        pad = int(rad * elong * 2.4) + 3

        offsets = [0, -w, w] if wrap_x else [0]
        for ox in offsets:
            cx_o = cx + ox
            x0, x1 = max(0, int(cx_o) - pad), min(w, int(cx_o) + pad + 1)
            y0, y1 = max(0, int(cy) - pad), min(h, int(cy) + pad + 1)
            if x1 <= x0 or y1 <= y0:
                continue
            gy, gx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
            dx, dy = gx - cx_o, gy - cy
            ca, sa = math.cos(ang), math.sin(ang)
            u = (dx * ca + dy * sa) / (rad * elong)
            v = (-dx * sa + dy * ca) / rad
            d2 = u * u + v * v
            core = np.exp(-d2 * 4.5) * intensity
            halo = np.exp(-d2 * 1.15) * intensity * 0.48
            add_rgb = core[..., None] * c_core + halo[..., None] * c_halo
            canvas[y0:y1, x0:x1, :3] = np.clip(canvas[y0:y1, x0:x1, :3] + add_rgb, 0.0, 2.3)
            canvas[y0:y1, x0:x1, 3] = np.clip(canvas[y0:y1, x0:x1, 3] + np.clip(core + halo * 0.6, 0.0, 1.0), 0.0, 1.0)


# =====================================================================
# 1 & 2. 死星陨石焦岩壳体 + 同 UV 炽熔岩浆裂纹双贴图
#    phainon_meteor_core_body.png & phainon_meteor_core_glow.png
# =====================================================================
def generate_core_pair() -> Tuple[Image.Image, Image.Image]:
    w, h = 1024, 512
    v_norm = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None]

    # 共享周期噪声与裂隙扭曲场，确保 body 与 glow 像素级精准对齐
    n_warp_u = periodic_fbm(h, w, [(6, 4, 0.55), (14, 8, 0.30), (28, 16, 0.15)], seed=101)
    n_warp_v = periodic_fbm(h, w, [(6, 4, 0.55), (14, 8, 0.30), (28, 16, 0.15)], seed=102)
    n_rock = periodic_fbm(h, w, [(8, 5, 0.32), (18, 10, 0.30), (40, 22, 0.23), (88, 44, 0.15)], seed=103)
    n_micro = periodic_fbm(h, w, [(36, 18, 0.45), (72, 36, 0.35), (144, 72, 0.20)], seed=104)
    n_heat = periodic_fbm(h, w, [(5, 3, 0.50), (12, 7, 0.32), (24, 14, 0.18)], seed=105)

    # 极点附近平滑衰减垂直扭曲，杜绝 v=0/1 边界拉伸伪影
    pole_guard = np.maximum(0.0, np.sin(v_norm * math.pi))
    warp_u = (n_warp_u - 0.5) * 0.08
    warp_v = (n_warp_v - 0.5) * 0.065 * pole_guard

    # 主焦岩板块裂隙（深熔岩沟壑）+ 次级碎裂脉络（精细火脉）
    edge_major, d1_major, plate_id = periodic_voronoi_edges(h, w, 10, 6, seed=201, warp_u=warp_u, warp_v=warp_v)
    edge_minor, d1_minor, sub_id = periodic_voronoi_edges(h, w, 24, 13, seed=202, warp_u=warp_u * 0.6, warp_v=warp_v * 0.6)

    # 迎风底部（v 靠近 1.0 的撞击前锋面）灼烧熔蚀加剧
    nose_burn = np.exp(-(((v_norm - 0.92) / 0.14) ** 2)) * (0.45 + 0.55 * n_heat)

    # 主熔岩峡谷与核心亮丝
    crack_w = 0.062 + 0.044 * n_heat
    major_canyon = np.exp(-((edge_major / crack_w) ** 1.85))
    major_core = np.exp(-((edge_major / (crack_w * 0.32)) ** 1.95))
    major_shoulder = np.exp(-((edge_major / (crack_w * 2.10)) ** 1.5))

    # 次级熔金微裂纹（只在热区板块高亮，保持黑焦岩块对比度）
    minor_mask = np.clip((n_heat - 0.28) * 2.1, 0.0, 1.0)
    minor_vein = np.exp(-((edge_minor / 0.045) ** 1.9)) * (0.28 + 0.72 * minor_mask)
    minor_core = np.exp(-((edge_minor / 0.017) ** 2.0)) * minor_mask

    # ---------- A. 焦黑死星陨石实体层 (phainon_meteor_core_body.png) ----------
    # 崎岖玄武岩浮雕 + 陨石坑凹陷 + 板块高低差
    crater = np.clip(1.0 - np.exp(-((d1_major / 0.24) ** 2)) * 0.36 + np.exp(-(((d1_major - 0.28) / 0.08) ** 2)) * 0.16, 0.58, 1.18)
    facet = 0.72 + 0.36 * plate_id + 0.16 * sub_id
    rock_h = np.clip((n_rock * 0.58 + n_micro * 0.42) * crater * facet, 0.0, 1.35)

    # 强化岩石法线立体感（强硬斜切光影）
    gx = np.roll(rock_h, -1, axis=1) - np.roll(rock_h, 1, axis=1)
    gy = np.roll(rock_h, -1, axis=0) - np.roll(rock_h, 1, axis=0)
    relief = np.clip(0.86 + gx * 3.4 - gy * 2.5, 0.44, 1.55)

    # 深邃暗黑焦炭玄武岩底色（RGB ~ 18..64，白昼下压迫感极强）
    base_r = (20.0 + 38.0 * rock_h) * relief
    base_g = (14.0 + 25.0 * rock_h) * relief
    base_b = (19.0 + 30.0 * rock_h) * relief

    # 裂缝深处的暗红焦岩受热面
    ember_under = np.clip(major_canyon * 0.92 + minor_vein * 0.38 + nose_burn * 0.42 * major_shoulder, 0.0, 1.0)
    ember_rim = np.clip(major_shoulder * 0.48, 0.0, 1.0) * (1.0 - major_canyon * 0.55)

    body_r = base_r * (1.0 - ember_under * 0.52) + 188.0 * ember_under + 62.0 * ember_rim
    body_g = base_g * (1.0 - ember_under * 0.72) + 58.0 * np.power(np.maximum(0.0, ember_under), 1.45) + 14.0 * ember_rim
    body_b = base_b * (1.0 - ember_under * 0.88) + 12.0 * np.power(np.maximum(0.0, ember_under), 2.0)

    body_rgba = np.zeros((h, w, 4), dtype=np.uint8)
    body_rgba[..., 0] = np.clip(body_r, 0, 255).astype(np.uint8)
    body_rgba[..., 1] = np.clip(body_g, 0, 255).astype(np.uint8)
    body_rgba[..., 2] = np.clip(body_b, 0, 255).astype(np.uint8)
    body_rgba[..., 3] = 255
    body_img = Image.fromarray(body_rgba, mode="RGBA")

    # ---------- B. 同 UV 炽熔岩浆裂纹叠加层 (phainon_meteor_core_glow.png) ----------
    glow = np.zeros((h, w, 4), dtype=np.float32)

    # 板块内部严格保持漆黑透明（Additive 叠上去不冲淡焦岩暗面），仅在裂隙与迎风火前沿炽烈燃烧
    magma = np.clip(
        major_canyon * (0.92 + 0.52 * n_heat)
        + minor_vein * 0.62
        + major_shoulder * 0.14 * n_heat
        + nose_burn * (0.55 * major_shoulder + 0.35 * n_rock),
        0.0,
        1.75,
    )
    white_hot = np.clip(major_core * 1.25 + minor_core * 0.72 + nose_burn * major_canyon * 0.85, 0.0, 1.65)

    # 极顶（尾部上方）平滑略压暗，避免顶极汇聚过亮
    top_cool = 0.45 + 0.55 * np.clip(v_norm / 0.16, 0.0, 1.0)
    magma *= top_cool
    white_hot *= top_cool

    glow[..., 0] = np.clip(magma * 1.48 + white_hot * 0.90, 0.0, 2.4)
    glow[..., 1] = np.clip(np.power(np.maximum(0.0, magma - 0.08), 1.25) * 0.94 + white_hot * 1.08, 0.0, 2.2)
    glow[..., 2] = np.clip(np.power(np.maximum(0.0, magma - 0.48), 1.90) * 0.48 + np.power(np.maximum(0.0, white_hot), 1.32) * 0.90, 0.0, 1.85)
    glow[..., 3] = np.clip(np.power(np.maximum(0.0, magma), 0.88) * 0.96 + white_hot * 0.62, 0.0, 1.0)

    scatter_embers(
        glow,
        count=64,
        seed=209,
        x_range=(0.0, 1.0),
        y_range=(0.10, 0.94),
        col_core=(1.65, 1.50, 1.05),
        col_halo=(1.38, 0.44, 0.06),
        angle_range=(-0.45, 0.45),
        elong_range=(1.4, 2.6),
        rad_range=(1.3, 3.2),
        wrap_x=True,
    )
    glow = add_hdr_bloom(glow, [(3.0, 0.30), (9.0, 0.20), (18.0, 0.10)], threshold=0.24, wrap_x=True)
    glow_img = finalize_additive_rgba(glow, exposure=1.20)
    return body_img, glow_img


# =====================================================================
# 3. 超长炽金-炎橙-白炽彗尾焰幔 (phainon_meteor_trail.png)
#    U 水平无缝，v=0 为高空拖尾尖端，v=1 为紧贴陨石前锋的炽白包络
# =====================================================================
def generate_meteor_trail() -> Image.Image:
    w, h = 1024, 512
    v = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None]
    u = np.linspace(0.0, 1.0, w, endpoint=False, dtype=np.float32)[None, :]

    n_flow1 = periodic_fbm(h, w, [(8, 3, 0.45), (16, 6, 0.30), (32, 12, 0.16), (64, 24, 0.09)], seed=301)
    n_flow2 = periodic_fbm(h, w, [(12, 4, 0.48), (24, 8, 0.32), (48, 16, 0.20)], seed=302)
    n_lick = periodic_fbm(h, w, [(18, 5, 0.55), (36, 10, 0.30), (72, 20, 0.15)], seed=303)

    # 水平无缝螺旋剪切
    twist_u = (u + (1.0 - v) * 0.46 + (n_flow1 - 0.5) * 0.11) % 1.0
    twist_v = np.clip(v + (n_flow2 - 0.5) * 0.14, 0.0, 0.999)
    ui = (twist_u * w).astype(np.int32) % w
    vi = (twist_v * h).astype(np.int32)

    turb = n_flow1[vi, ui] * 0.58 + n_flow2[vi, ui] * 0.42
    sharp_licks = n_lick[vi, ui]

    # 高对比炽烈二次元火焰长带
    ribbon_a = np.power(np.maximum(0.0, 0.5 + 0.5 * np.sin((twist_u * 6.0 + v * 1.6) * math.tau + turb * 4.4)), 2.8)
    ribbon_b = np.power(np.maximum(0.0, 0.5 + 0.5 * np.sin((twist_u * 11.0 - v * 2.5) * math.tau + sharp_licks * 5.2)), 4.2)
    ribbon_c = np.power(np.maximum(0.0, 0.5 + 0.5 * np.sin((twist_u * 19.0 + v * 3.8) * math.tau + n_flow2 * 3.5)), 5.8)

    head_dense = np.power(np.clip(v, 0.0, 1.0), 0.75)
    tail_cut = np.clip((v - (0.05 + 0.25 * (1.0 - turb) + 0.14 * (1.0 - ribbon_a))) / 0.20, 0.0, 1.0)
    # 极顶与极底充分羽化，防止底部出现水平亮线
    edge_soft = np.clip(v / 0.06, 0.0, 1.0) * np.power(np.clip((1.0 - v) / 0.085, 0.0, 1.0), 1.35)

    bow_shock = np.exp(-(((v - 0.85) / 0.11) ** 2)) * (0.65 + 0.35 * turb)

    flame_body = (0.38 * turb + 0.54 * ribbon_a + 0.40 * ribbon_b + 0.25 * ribbon_c) * (0.45 + 0.78 * head_dense) * tail_cut
    flame_core = (0.44 * ribbon_a * ribbon_b + 0.52 * ribbon_b + 0.38 * ribbon_c) * np.power(np.clip(v, 0.0, 1.0), 1.15) * tail_cut + bow_shock * 0.90

    flame_body *= edge_soft
    flame_core *= edge_soft

    canvas = np.zeros((h, w, 4), dtype=np.float32)
    # 增强赤橙外焰饱和度 + 耀目烈金白炽核
    canvas[..., 0] = np.clip(flame_body * 1.62 + flame_core * 0.90, 0.0, 2.5)
    canvas[..., 1] = np.clip(np.power(np.maximum(0.0, flame_body - 0.08), 1.28) * 0.95 + flame_core * 1.12, 0.0, 2.25)
    canvas[..., 2] = np.clip(np.power(np.maximum(0.0, flame_body - 0.48), 1.95) * 0.40 + np.power(np.maximum(0.0, flame_core), 1.30) * 0.92, 0.0, 1.95)
    canvas[..., 3] = np.clip(np.power(np.maximum(0.0, flame_body), 0.76) * 0.94 + flame_core * 0.62, 0.0, 1.0)

    scatter_embers(
        canvas,
        count=135,
        seed=309,
        x_range=(0.0, 1.0),
        y_range=(0.07, 0.88),
        col_core=(1.68, 1.52, 1.08),
        col_halo=(1.48, 0.46, 0.06),
        angle_range=(math.pi * 0.40, math.pi * 0.56),
        elong_range=(2.6, 5.8),
        rad_range=(1.4, 3.6),
        wrap_x=True,
    )
    canvas = add_hdr_bloom(canvas, [(4.0, 0.30), (12.0, 0.22), (24.0, 0.12)], threshold=0.25, wrap_x=True)
    return finalize_additive_rgba(canvas, exposure=1.18)


# =====================================================================
# 4. 高空云层破洞漩涡与金红天幕裂隙 (phainon_meteor_sky_vortex.png)
#    1024x1024 径向居中：笛卡尔域扭曲旋涡（零中心极点捏缩！），外暗紫卷云 + 内烈金赤红火唇
# =====================================================================
def generate_sky_vortex() -> Image.Image:
    size = 1024
    yy, xx = np.mgrid[-1.0:1.0:complex(size), -1.0:1.0:complex(size)].astype(np.float32)
    r = np.hypot(xx, yy)
    theta = np.arctan2(yy, xx)

    # 旋涡扭转后的笛卡尔坐标（在中心处连续光滑，完全避免极坐标星形捏缩）
    swirl_angle = 2.65 * np.power(np.clip(1.08 - r, 0.0, 1.1), 1.35)
    ca, sa = np.cos(swirl_angle), np.sin(swirl_angle)
    sx = xx * ca - yy * sa
    sy = xx * sa + yy * ca

    # 两级笛卡尔域扭曲 FBM，雕刻出厚重翻涌的雷暴暗紫云墙（呼应关键帧 04/05）
    n1 = cartesian_fbm(sx, sy, [(2.4, 0.46), (5.0, 0.28), (10.5, 0.16), (22.0, 0.10)], seed=411)
    n2 = cartesian_fbm(sx + 2.7, sy - 1.9, [(3.2, 0.48), (7.0, 0.30), (15.0, 0.22)], seed=412)
    wx = sx + (n1 - 0.5) * 0.42
    wy = sy + (n2 - 0.5) * 0.42
    billow = cartesian_fbm(wx, wy, [(2.8, 0.44), (6.0, 0.28), (13.0, 0.17), (28.0, 0.11)], seed=413)
    fine = cartesian_fbm(wx * 1.4, wy * 1.4, [(8.0, 0.55), (18.0, 0.30), (36.0, 0.15)], seed=414)

    # 有机不规则破洞边缘半径（无周期对称星芒）
    r_warp = r + (billow - 0.5) * 0.15 + (n1 - 0.5) * 0.06

    # 中心破洞炽烧内唇（r_warp ~ 0.20..0.32）+ 螺旋层叠受光云脊
    eye_lip = np.exp(-(((r_warp - 0.23) / 0.052) ** 2)) * (0.72 + 0.42 * fine)
    inner_backlit = np.exp(-(((r_warp - 0.32) / 0.19) ** 2)) * np.clip(1.22 - billow * 0.95, 0.0, 1.0)
    # 螺旋翻涌云纹高光（沿着旋涡云墙流动，不再是呆板 14 条直线）
    spiral_folds = np.power(
        np.maximum(0.0, 0.5 + 0.5 * np.sin(13.5 * r_warp + 2.0 * (theta + swirl_angle) + 5.8 * billow)),
        3.2,
    ) * np.exp(-(((r_warp - 0.40) / 0.28) ** 2)) * (0.55 + 0.45 * n2)

    # 少量破云金焰裂丝（被云团打断，自然融入旋涡）
    flame_filaments = np.power(
        np.maximum(0.0, 0.5 + 0.5 * np.sin(5.0 * (theta + swirl_angle * 0.8) + 6.5 * n2 + 4.5 * r)),
        7.5,
    ) * np.exp(-(((r_warp - 0.29) / 0.18) ** 2)) * (0.45 + 0.55 * fine)

    # 暗紫黑曜石雷暴云主体（外围沉重压暗白天蓝天）
    cloud_dense = np.clip((billow - 0.24) * 1.55, 0.0, 1.0)
    cloud_ridge = np.clip((fine - 0.45) * 1.8, 0.0, 1.0) * cloud_dense
    dark_r = 0.08 + 0.13 * cloud_dense + 0.05 * cloud_ridge
    dark_g = 0.04 + 0.06 * cloud_dense + 0.02 * cloud_ridge
    dark_b = 0.14 + 0.16 * cloud_dense + 0.06 * cloud_ridge

    # 炽红-炎橙-烈金内唇光照
    heat = np.clip(eye_lip * 1.25 + inner_backlit * 0.92 + spiral_folds * 0.72 + flame_filaments * 0.65, 0.0, 1.85)
    white_lip = np.clip(eye_lip * 0.90 + flame_filaments * 0.42, 0.0, 1.35)

    rgb = np.zeros((size, size, 3), dtype=np.float32)
    rgb[..., 0] = dark_r * (1.0 - np.clip(heat * 0.52, 0.0, 0.85)) + heat * 1.12 + white_lip * 0.52
    rgb[..., 1] = dark_g * (1.0 - np.clip(heat * 0.64, 0.0, 0.88)) + np.power(np.maximum(0.0, heat), 1.38) * 0.60 + white_lip * 0.64
    rgb[..., 2] = dark_b * (1.0 - np.clip(heat * 0.78, 0.0, 0.92)) + np.power(np.maximum(0.0, heat - 0.36), 1.95) * 0.24 + white_lip * 0.36

    rgb = rgb / (1.0 + 0.30 * rgb) * 1.14
    rgb = np.clip(rgb, 0.0, 1.0)

    # Alpha：中心清晰破洞（r_warp < 0.135 干净淡出至 0，无任何星形捏缩！），外缘平滑消融
    outer_fade = np.clip((0.96 - r_warp) / 0.20, 0.0, 1.0) * np.clip((0.992 - r) / 0.06, 0.0, 1.0)
    inner_hole = np.clip((r_warp - 0.135) / 0.085, 0.0, 1.0)
    alpha = np.clip(outer_fade * inner_hole * (0.76 + 0.24 * cloud_dense), 0.0, 0.96)
    # 洞口灼烧薄焰略微向内溢出但不触及圆心
    lip_spill = np.exp(-(((r_warp - 0.19) / 0.045) ** 2)) * 0.42 * np.clip((r - 0.08) / 0.06, 0.0, 1.0)
    alpha = np.clip(np.maximum(alpha, lip_spill * outer_fade), 0.0, 0.96)

    rgba = np.zeros((size, size, 4), dtype=np.uint8)
    rgba[..., :3] = np.clip(rgb * 255.0, 0, 255).astype(np.uint8)
    rgba[..., 3] = np.clip(alpha * 255.0, 0, 255).astype(np.uint8)
    return Image.fromarray(rgba, mode="RGBA")


# =====================================================================
# 5. 命中后烈金白炽爆炸云穹与放射光浪 (phainon_meteor_blast_dome.png)
#    1024x512 水平无缝：v=0 穹顶天顶，v=1 地面冲击前沿
#    有机爆炸火云冠 + 错落高能放射强光束 + 撕裂电弧熔金脉络（还原关键帧 07/08）
# =====================================================================
def generate_blast_dome() -> Image.Image:
    w, h = 1024, 512
    v = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None]
    u = np.linspace(0.0, 1.0, w, endpoint=False, dtype=np.float32)[None, :]

    # 多层翻涌蘑菇爆炸云噪声
    n_cloud = periodic_fbm(h, w, [(5, 3, 0.44), (11, 6, 0.29), (24, 12, 0.17), (52, 24, 0.10)], seed=511)
    n_detail = periodic_fbm(h, w, [(12, 5, 0.48), (26, 10, 0.32), (56, 22, 0.20)], seed=512)
    n_streak = periodic_fbm(h, w, [(18, 2, 0.55), (38, 4, 0.30), (76, 8, 0.15)], seed=513)

    warp_u = (n_cloud - 0.5) * 0.085
    warp_v = (n_detail - 0.5) * 0.075

    # 修复后的全图无缝 Voronoi 锯齿熔金电弧裂脉（还原关键帧 08 蘑菇火云穹顶的亮金脉络）
    edge_vein, _, _ = periodic_voronoi_edges(h, w, 9, 5, seed=515, warp_u=warp_u, warp_v=warp_v)
    dome_cracks = np.exp(-((edge_vein / 0.038) ** 1.8)) * (0.48 + 0.52 * n_detail)
    dome_crack_core = np.exp(-((edge_vein / 0.013) ** 2.0)) * (0.55 + 0.45 * n_detail)

    # 有机错落的高能放射光束（由随机宽度与强度的多个周期高斯扇面合成，杜绝机械斑马纹！）
    rng = random.Random(519)
    rays = np.zeros((h, w), dtype=np.float32)
    u_turb = (u + (n_detail - 0.5) * 0.022 + (v - 0.5) * (n_cloud - 0.5) * 0.05) % 1.0
    for _ in range(26):
        center_u = rng.uniform(0.0, 1.0)
        width_u = rng.uniform(0.008, 0.036)
        amp = rng.uniform(0.28, 0.95)
        peak_v = rng.uniform(0.32, 0.78)
        span_v = rng.uniform(0.22, 0.42)
        du = np.abs(((u_turb - center_u + 0.5) % 1.0) - 0.5)
        beam = np.exp(-((du / width_u) ** 2)) * np.exp(-(((v - peak_v) / span_v) ** 2)) * amp
        rays += beam
    rays = np.clip(rays * (0.55 + 0.55 * n_streak), 0.0, 1.45)

    # 翻涌蘑菇火云冠（中上部赤红-炎橙翻腾云团）+ 底部冲击白炽光环（v ~ 0.72..0.92）
    billow_dome = np.exp(-(((v - 0.46) / 0.34) ** 2)) * np.power(np.clip(n_cloud * 1.15, 0.0, 1.0), 1.15)
    shock_belt = np.exp(-(((v - 0.80) / 0.13) ** 2)) * (0.62 + 0.38 * n_detail)

    # 上下边界大范围平滑羽化，彻底无硬边与横条
    top_fade = np.power(np.clip((v - 0.03) / 0.19, 0.0, 1.0), 1.35)
    bot_fade = np.power(np.clip((0.97 - v) / 0.12, 0.0, 1.0), 1.35)
    envelope = top_fade * bot_fade

    fire_mass = (billow_dome * 0.88 + rays * 0.72 + dome_cracks * 0.95 + shock_belt * 0.98) * envelope
    white_core = (dome_crack_core * 1.02 + rays * shock_belt * 0.75 + shock_belt * 0.65 + np.power(np.clip(rays - 0.45, 0.0, 1.0), 1.4) * 0.55) * envelope

    canvas = np.zeros((h, w, 4), dtype=np.float32)
    # 关键帧 07/08：深绯赤红爆炸云体 -> 炽橙 -> 耀目烈金白炽核
    canvas[..., 0] = np.clip(fire_mass * 1.56 + white_core * 0.92, 0.0, 2.5)
    canvas[..., 1] = np.clip(np.power(np.maximum(0.0, fire_mass - 0.12), 1.32) * 0.92 + white_core * 1.08, 0.0, 2.25)
    canvas[..., 2] = np.clip(np.power(np.maximum(0.0, fire_mass - 0.50), 2.0) * 0.40 + np.power(np.maximum(0.0, white_core), 1.32) * 0.94, 0.0, 1.95)
    canvas[..., 3] = np.clip(np.power(np.maximum(0.0, fire_mass), 0.80) * 0.94 + white_core * 0.60, 0.0, 1.0)

    scatter_embers(
        canvas,
        count=135,
        seed=529,
        x_range=(0.0, 1.0),
        y_range=(0.14, 0.88),
        col_core=(1.68, 1.54, 1.16),
        col_halo=(1.44, 0.42, 0.06),
        angle_range=(math.pi * 0.36, math.pi * 0.64),
        elong_range=(2.2, 5.2),
        rad_range=(1.4, 3.6),
        wrap_x=True,
    )
    canvas = add_hdr_bloom(canvas, [(4.0, 0.30), (14.0, 0.22), (26.0, 0.12)], threshold=0.24, wrap_x=True)
    return finalize_additive_rgba(canvas, exposure=1.18)


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    core_body, core_glow = generate_core_pair()
    outputs = {
        "phainon_meteor_core_body.png": core_body,
        "phainon_meteor_core_glow.png": core_glow,
        "phainon_meteor_trail.png": generate_meteor_trail(),
        "phainon_meteor_sky_vortex.png": generate_sky_vortex(),
        "phainon_meteor_blast_dome.png": generate_blast_dome(),
    }

    for name, img in outputs.items():
        path = OUT_DIR / name
        img.save(path, format="PNG")
        arr = np.asarray(img)
        seam_diff = float(np.mean(np.abs(arr[:, 0, :].astype(np.float32) - arr[:, -1, :].astype(np.float32))))
        has_nan = bool(np.isnan(arr).any())
        print(f"[OK] {path.name:32s} {img.width}x{img.height} mode={img.mode} seam_diff={seam_diff:.2f} nan={has_nan}")


if __name__ == "__main__":
    main()
