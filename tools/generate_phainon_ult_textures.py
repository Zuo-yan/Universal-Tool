#!/usr/bin/env python3
"""
白厄终结技「永劫燔世，其将背负」高精度 7 张 VFX 贴图生成脚本（精修版）。

输出目录：src/main/resources/assets/universal_tool/textures/misc/
生成清单：
1. phainon_ult_seraph_wing.png         (1024x512) 幕 1 白蓝圣洁天翼（层次分明不死白，Additive）
2. phainon_ult_wing_gold.png           (1024x512) 幕 3 画面左翼：炽烈黄金神翼（羽片叠压雕刻感，Additive）
3. phainon_ult_wing_obsidian_body.png  (1024x512) 幕 3 画面右翼：黑曜石暗紫残翼实体剪影（Translucent）
4. phainon_ult_wing_obsidian_glow.png  (1024x512) 幕 3 画面右翼：同轮廓锐利炽白金焰羽缘与裂纹（Additive）
5. phainon_ult_solar_cocoon.png        (1024x512) 幕 2 旋转金色焚世茧球与彗尾环（真正水平无缝 Additive）
6. phainon_ult_titan_runes.png         (512x512)  幕 3 12 枚黄金泰坦神徽 4x3 图集（Additive）
7. phainon_ult_sky_veil.png            (512x256)  全幕 26 格暗紫金红焚世苍穹穹顶（水平无缝 Translucent）
"""

from __future__ import annotations

import math
import random
from pathlib import Path
from typing import List, Sequence, Tuple

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "textures" / "misc"


def cubic_bezier(
    p0: Tuple[float, float],
    p1: Tuple[float, float],
    p2: Tuple[float, float],
    p3: Tuple[float, float],
    steps: int = 44,
) -> List[Tuple[float, float]]:
    """采样三次贝塞尔曲线。"""
    pts: List[Tuple[float, float]] = []
    for i in range(steps + 1):
        t = i / steps
        mt = 1.0 - t
        x = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def build_feather_polygon(
    spine: Sequence[Tuple[float, float]],
    base_w: float,
    mid_w: float,
    tip_sharpness: float = 1.75,
    jagged: float = 0.0,
    seed: int = 0,
    asymmetry: float = 0.22,
    taper_base: bool = False,
) -> Tuple[List[Tuple[float, float]], List[Tuple[float, float]]]:
    """根据羽轴曲线构造饱满、锋利且可带锯齿缺口的二次元羽片多边形。"""
    rng = random.Random(seed)
    n = len(spine)
    upper: List[Tuple[float, float]] = []
    lower: List[Tuple[float, float]] = []

    notches: List[Tuple[float, float, float]] = []
    if jagged > 0.0:
        count = rng.randint(2, 4)
        for _ in range(count):
            notches.append((rng.uniform(0.26, 0.84), rng.uniform(0.03, 0.065), rng.uniform(0.35, 0.78) * jagged))

    for i, (x, y) in enumerate(spine):
        t = i / max(1, n - 1)
        if i == 0:
            dx, dy = spine[1][0] - x, spine[1][1] - y
        elif i == n - 1:
            dx, dy = x - spine[i - 1][0], y - spine[i - 1][1]
        else:
            dx, dy = spine[i + 1][0] - spine[i - 1][0], spine[i + 1][1] - spine[i - 1][1]
        length = math.hypot(dx, dy) + 1e-6
        nx, ny = -dy / length, dx / length

        if taper_base:
            # 脱离主翼的飘落残羽：两头尖锐、中间修长的剑刃柳叶形
            bell = math.sin(t * math.pi) ** 0.85
            half_w = base_w * (1.0 - t) * t * 4.0 + mid_w * bell * (1.0 - 0.35 * (t**1.6))
        else:
            bell = math.sin(min(1.0, t**0.72) * math.pi)
            taper = max(0.0, 1.0 - t**tip_sharpness)
            half_w = (base_w * (1.0 - t) + mid_w * (bell**0.85)) * (0.20 + 0.80 * taper)

        if t > 0.96:
            half_w *= max(0.0, (1.0 - t) / 0.04)
        if taper_base and t < 0.05:
            half_w *= t / 0.05

        upper_scale = 1.0 - asymmetry
        lower_scale = 1.0 + asymmetry

        notch_cut_low = 1.0
        notch_cut_up = 1.0
        for pos, width, depth in notches:
            d = abs(t - pos) / width
            if d < 1.0:
                cut = 1.0 - depth * (1.0 - d)
                notch_cut_low = min(notch_cut_low, cut)
                if pos > 0.52:
                    notch_cut_up = min(notch_cut_up, 0.55 + 0.45 * cut)

        serrate = 1.0
        if jagged > 0.35 and 0.16 < t < 0.92:
            serrate = 1.0 + jagged * 0.13 * math.sin(t * 44.0 + seed * 3.1)

        wu = half_w * upper_scale * notch_cut_up * serrate
        wl = half_w * lower_scale * notch_cut_low * serrate

        upper.append((x - nx * wu, y - ny * wu))
        lower.append((x + nx * wl, y + ny * wl))

    poly = upper + list(reversed(lower))
    return poly, list(spine)


def paint_feather_layer(
    canvas: np.ndarray,
    poly: Sequence[Tuple[float, float]],
    spine: Sequence[Tuple[float, float]],
    col_root: Tuple[float, float, float],
    col_mid: Tuple[float, float, float],
    col_tip: Tuple[float, float, float],
    col_shaft: Tuple[float, float, float],
    col_rim: Tuple[float, float, float] | None = None,
    alpha_scale: float = 1.0,
    barb_strength: float = 0.28,
    rim_boost: float = 0.38,
    shadow_edge: float = 0.25,
    mode: str = "layered",
) -> None:
    """绘制单片高精羽片（使用连续线性插值羽轴，彻底杜绝虚线点状瑕疵，羽片间清晰叠压）。"""
    h, w, _ = canvas.shape
    xs = [p[0] for p in poly]
    ys = [p[1] for p in poly]
    x0 = max(0, int(math.floor(min(xs))) - 3)
    x1 = min(w, int(math.ceil(max(xs))) + 4)
    y0 = max(0, int(math.floor(min(ys))) - 3)
    y1 = min(h, int(math.ceil(max(ys))) + 4)
    if x1 <= x0 or y1 <= y0:
        return

    bw, bh = x1 - x0, y1 - y0
    mask_img = Image.new("L", (bw, bh), 0)
    draw = ImageDraw.Draw(mask_img)
    draw.polygon([(px - x0, py - y0) for px, py in poly], fill=255)
    soft_mask = np.asarray(mask_img.filter(ImageFilter.GaussianBlur(radius=0.75)), dtype=np.float32) / 255.0
    if soft_mask.max() < 1e-4:
        return

    eroded = np.asarray(mask_img.filter(ImageFilter.MinFilter(size=5)).filter(ImageFilter.GaussianBlur(radius=1.0)), dtype=np.float32) / 255.0
    rim = np.clip(soft_mask - eroded, 0.0, 1.0)

    # 精确连续投影：先按起止弦长估算参数 t，再对羽轴点列连续线性插值，平滑无噪点
    sp_arr = np.array(spine, dtype=np.float32)
    sp0 = sp_arr[0]
    sp1 = sp_arr[-1]
    axis = sp1 - sp0
    axis_len = float(np.hypot(axis[0], axis[1])) + 1e-5
    ax_u = axis / axis_len
    perp_u = np.array([-ax_u[1], ax_u[0]], dtype=np.float32)

    gy, gx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
    rel_x = gx - sp0[0]
    rel_y = gy - sp0[1]
    t = np.clip((rel_x * ax_u[0] + rel_y * ax_u[1]) / axis_len, 0.0, 1.0)
    d_perp = rel_x * perp_u[0] + rel_y * perp_u[1]

    # 连续线性插值求羽轴精确坐标 cx, cy
    sp_t = np.linspace(0.0, 1.0, len(spine), dtype=np.float32)
    t_flat = t.ravel()
    cx = np.interp(t_flat, sp_t, sp_arr[:, 0]).reshape(t.shape)
    cy = np.interp(t_flat, sp_t, sp_arr[:, 1]).reshape(t.shape)
    dist_spine = np.hypot(gx - cx, gy - cy)

    # 三段颜色插值
    t_mid = 0.50
    c_root = np.array(col_root, dtype=np.float32)
    c_mid = np.array(col_mid, dtype=np.float32)
    c_tip = np.array(col_tip, dtype=np.float32)
    c_shaft = np.array(col_shaft, dtype=np.float32)
    c_rim = np.array(col_rim if col_rim is not None else col_shaft, dtype=np.float32)

    w1 = np.clip(t / t_mid, 0.0, 1.0)[..., None]
    w2 = np.clip((t - t_mid) / (1.0 - t_mid), 0.0, 1.0)[..., None]
    col = np.where(t[..., None] < t_mid, c_root * (1.0 - w1) + c_mid * w1, c_mid * (1.0 - w2) + c_tip * w2)

    # 细腻羽支斜纹（模拟手绘二次元羽丝笔触）
    barb_coord = (rel_x * ax_u[0] + rel_y * ax_u[1]) * 0.28 - np.abs(d_perp) * 0.48
    barbs = 0.5 + 0.5 * np.sin(barb_coord * 2.2) * np.cos(barb_coord * 0.67 + 0.5)
    col = col * (1.0 - barb_strength + barb_strength * (0.68 + 0.64 * barbs[..., None]))

    # 羽片自身明暗立体感：一侧微暗阴影，另一侧与羽轴亮起，防止相邻羽片融成白块
    side_shade = np.clip(0.84 + 0.24 * np.tanh(-d_perp / 14.0), 0.62, 1.08)
    root_depth = 1.0 - shadow_edge * np.exp(-((t / 0.22) ** 2))
    col = col * side_shade[..., None] * root_depth[..., None]

    # 光滑连续羽轴高光脊线
    shaft_w = np.maximum(1.3, 3.6 * (1.0 - 0.70 * t))
    shaft_glow = np.exp(-((dist_spine / shaft_w) ** 2)) * (1.0 - 0.20 * t)
    col = col * (1.0 - 0.55 * shaft_glow[..., None]) + c_shaft * (0.78 * shaft_glow[..., None])

    # 纤细锐利的羽缘辉光
    col = col + c_rim * (rim[..., None] * rim_boost)

    alpha = np.clip(soft_mask * alpha_scale, 0.0, 1.0)
    region = canvas[y0:y1, x0:x1]
    a3 = alpha[..., None]

    if mode == "layered":
        # 物理叠压混合：下层羽片略微压暗形成清晰缝隙投影，彻底保留根根分明的羽毛层次！
        inv_a = 1.0 - a3
        region[..., :3] = col * a3 + region[..., :3] * inv_a * (1.0 - 0.16 * a3)
        region[..., 3] = alpha + region[..., 3] * (1.0 - alpha)
    else:
        inv_a = 1.0 - a3
        region[..., :3] = col * a3 + region[..., :3] * inv_a
        region[..., 3] = alpha + region[..., 3] * (1.0 - alpha)


def add_hdr_bloom(
    img_f: np.ndarray,
    radii_weights: Sequence[Tuple[float, float]],
    threshold: float = 0.22,
    wrap_x: bool = False,
) -> np.ndarray:
    """对浮点 RGBA 图层施加多层 HDR 高斯辉光（支持 wrap_x 水平无缝卷积）。"""
    h, w, _ = img_f.shape
    rgb = img_f[..., :3]
    lum = 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]
    extract = np.clip((lum - threshold) / max(1e-4, 1.0 - threshold), 0.0, 2.0)[..., None]
    bright = np.clip(rgb * extract * 255.0 / 1.5, 0.0, 255.0).astype(np.uint8)

    acc = np.zeros_like(rgb)
    for r, weight in radii_weights:
        if wrap_x:
            pad = int(math.ceil(r * 3.0))
            padded = np.concatenate([bright[:, -pad:, :], bright, bright[:, :pad, :]], axis=1)
            pil_p = Image.fromarray(padded, mode="RGB").filter(ImageFilter.GaussianBlur(radius=r))
            blurred = np.asarray(pil_p, dtype=np.float32)[:, pad:pad + w, :] / 255.0 * 1.5
        else:
            pil_b = Image.fromarray(bright, mode="RGB").filter(ImageFilter.GaussianBlur(radius=r))
            blurred = np.asarray(pil_b, dtype=np.float32) / 255.0 * 1.5
        acc += blurred * weight

    out = img_f.copy()
    out[..., :3] = np.clip(rgb + acc, 0.0, 1.8)
    bloom_lum = np.max(acc, axis=-1)
    out[..., 3] = np.clip(np.maximum(out[..., 3], bloom_lum * 0.85), 0.0, 1.0)
    return out


def finalize_additive_rgba(img_f: np.ndarray, exposure: float = 1.12) -> Image.Image:
    """将浮点发光图层转为 8-bit RGBA（柔光色调映射，高饱和不过曝）。"""
    rgb = np.clip(img_f[..., :3], 0.0, 2.4)
    mapped = (rgb / (1.0 + 0.45 * rgb)) * exposure
    mapped = np.clip(mapped, 0.0, 1.0)
    alpha = np.clip(np.maximum(img_f[..., 3], np.max(mapped, axis=-1) * 0.92), 0.0, 1.0)
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
    streak: bool = True,
    wrap_x: bool = False,
) -> None:
    """撒布发光火星与星尘微粒（支持 wrap_x 水平无缝跨边）。"""
    rng = random.Random(seed)
    h, w, _ = canvas.shape
    c_core = np.array(col_core, dtype=np.float32)
    c_halo = np.array(col_halo, dtype=np.float32)

    for _ in range(count):
        cx = rng.uniform(*x_range) * w
        cy = rng.uniform(*y_range) * h
        rad = rng.uniform(1.6, 5.2)
        ang = rng.uniform(-0.48, 0.22)
        elong = rng.uniform(2.2, 4.4) if streak else 1.2
        intensity = rng.uniform(0.50, 1.15)
        pad = int(rad * elong * 2.5) + 3

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
            core = np.exp(-d2 * 4.8) * intensity
            halo = np.exp(-d2 * 1.25) * intensity * 0.48
            add_rgb = core[..., None] * c_core + halo[..., None] * c_halo
            canvas[y0:y1, x0:x1, :3] = np.clip(canvas[y0:y1, x0:x1, :3] + add_rgb, 0.0, 2.0)
            canvas[y0:y1, x0:x1, 3] = np.clip(canvas[y0:y1, x0:x1, 3] + np.clip(core + halo * 0.65, 0.0, 1.0), 0.0, 1.0)


# =====================================================================
# 1. 幕 1：圣洁白蓝天使光翼 (phainon_ult_seraph_wing.png)
# =====================================================================
def generate_seraph_wing() -> Image.Image:
    w, h = 1024, 512
    canvas = np.zeros((h, w, 4), dtype=np.float32)
    ridge = cubic_bezier((26, 286), (255, 76), (620, 44), (968, 92), steps=44)

    # 外层 16 根冰蓝-月白修长主飞羽（从后往前按真实物理层级叠压）
    n_primary = 16
    for i in range(n_primary):
        u = i / (n_primary - 1)
        rx, ry = ridge[int(u * (len(ridge) - 1))]
        length = (355.0 - 90.0 * abs(u - 0.62)) * (0.74 + 0.32 * math.sin(u * math.pi * 0.85 + 0.25))
        angle = math.radians(52.0 - 66.0 * (u**0.88))
        bend = math.radians(15.0 - 18.0 * u)
        tx = min(w - 18, max(20, rx + math.cos(angle) * length))
        ty = min(h - 18, max(18, ry + math.sin(angle) * length))
        p1 = (rx + math.cos(angle - bend) * length * 0.36, ry + math.sin(angle - bend) * length * 0.36)
        p2 = (rx + math.cos(angle + bend * 0.38) * length * 0.74, ry + math.sin(angle + bend * 0.38) * length * 0.74)
        spine = cubic_bezier((rx, ry), p1, p2, (tx, ty), steps=36)
        poly, sp = build_feather_polygon(spine, base_w=12.0, mid_w=25.0 + 6.0 * (1.0 - u), tip_sharpness=1.75, seed=100 + i)
        paint_feather_layer(
            canvas,
            poly,
            sp,
            col_root=(0.24, 0.44, 0.76),
            col_mid=(0.56, 0.80, 1.06),
            col_tip=(0.82, 0.96, 1.18),
            col_shaft=(1.18, 1.24, 1.30),
            col_rim=(0.95, 1.08, 1.28),
            alpha_scale=0.92,
            barb_strength=0.30,
            rim_boost=0.34,
            shadow_edge=0.32,
            mode="layered",
        )

    # 中层 13 根圣白微金覆羽
    n_mid = 13
    for i in range(n_mid):
        u = 0.04 + 0.78 * (i / (n_mid - 1))
        rx, ry = ridge[int(u * (len(ridge) - 1))]
        length = (220.0 - 45.0 * u) * (0.85 + 0.18 * math.sin(u * math.pi))
        angle = math.radians(48.0 - 58.0 * u)
        tx = min(w - 28, rx + math.cos(angle) * length)
        ty = min(h - 28, ry + math.sin(angle) * length)
        p1 = (rx + math.cos(angle - 0.16) * length * 0.35, ry + math.sin(angle - 0.16) * length * 0.35)
        p2 = (rx + math.cos(angle + 0.10) * length * 0.72, ry + math.sin(angle + 0.10) * length * 0.72)
        spine = cubic_bezier((rx, ry), p1, p2, (tx, ty), steps=28)
        poly, sp = build_feather_polygon(spine, base_w=10.5, mid_w=21.5, tip_sharpness=1.6, seed=200 + i)
        paint_feather_layer(
            canvas,
            poly,
            sp,
            col_root=(0.48, 0.62, 0.86),
            col_mid=(0.82, 0.92, 1.06),
            col_tip=(1.06, 1.08, 1.14),
            col_shaft=(1.28, 1.24, 1.12),
            col_rim=(1.18, 1.14, 1.02),
            alpha_scale=0.95,
            barb_strength=0.26,
            rim_boost=0.30,
            shadow_edge=0.28,
            mode="layered",
        )

    # 内层 9 根肩部精细淡金白羽
    n_inner = 9
    for i in range(n_inner):
        u = 0.02 + 0.56 * (i / (n_inner - 1))
        rx, ry = ridge[int(u * (len(ridge) - 1))]
        length = 128.0 - 24.0 * u
        angle = math.radians(44.0 - 50.0 * u)
        tx = rx + math.cos(angle) * length
        ty = ry + math.sin(angle) * length
        p1 = (rx + math.cos(angle - 0.12) * length * 0.35, ry + math.sin(angle - 0.12) * length * 0.35)
        p2 = (rx + math.cos(angle + 0.08) * length * 0.72, ry + math.sin(angle + 0.08) * length * 0.72)
        spine = cubic_bezier((rx, ry), p1, p2, (tx, ty), steps=20)
        poly, sp = build_feather_polygon(spine, base_w=8.5, mid_w=17.0, tip_sharpness=1.5, seed=250 + i)
        paint_feather_layer(
            canvas,
            poly,
            sp,
            col_root=(0.68, 0.74, 0.88),
            col_mid=(0.98, 0.98, 1.02),
            col_tip=(1.16, 1.10, 0.94),
            col_shaft=(1.32, 1.26, 1.06),
            alpha_scale=0.96,
            barb_strength=0.20,
            rim_boost=0.28,
            shadow_edge=0.22,
            mode="layered",
        )

    # 翼骨前缘淡金白圣光流脊
    poly_ridge, sp_ridge = build_feather_polygon(ridge, base_w=20.0, mid_w=15.0, tip_sharpness=1.35, asymmetry=0.05)
    paint_feather_layer(
        canvas,
        poly_ridge,
        sp_ridge,
        col_root=(0.92, 0.88, 0.78),
        col_mid=(1.08, 1.12, 1.18),
        col_tip=(0.82, 1.04, 1.28),
        col_shaft=(1.36, 1.32, 1.18),
        alpha_scale=0.98,
        barb_strength=0.10,
        rim_boost=0.35,
        shadow_edge=0.12,
        mode="layered",
    )

    scatter_embers(canvas, 44, seed=101, x_range=(0.12, 0.96), y_range=(0.08, 0.88),
                   col_core=(1.15, 1.22, 1.32), col_halo=(0.38, 0.74, 1.22), streak=True)
    canvas = add_hdr_bloom(canvas, [(5.0, 0.22), (16.0, 0.16), (32.0, 0.10)], threshold=0.38)
    return finalize_additive_rgba(canvas, exposure=1.18)


# =====================================================================
# 2. 幕 3 画面左翼：炽烈黄金神翼 (phainon_ult_wing_gold.png)
# =====================================================================
def generate_gold_wing() -> Image.Image:
    w, h = 1024, 512
    canvas = np.zeros((h, w, 4), dtype=np.float32)
    ridge = cubic_bezier((22, 294), (240, 62), (610, 26), (986, 78), steps=48)

    # 第一层：18 根巨型锋利黄金主飞羽（深琥珀暗根 -> 璀璨熔金身 -> 烈焰金橙羽尖，根根分明）
    n_primary = 18
    for i in range(n_primary):
        u = i / (n_primary - 1)
        rx, ry = ridge[int(u * (len(ridge) - 1))]
        length = (395.0 - 92.0 * abs(u - 0.66)) * (0.76 + 0.30 * math.sin(u * math.pi * 0.88 + 0.22))
        angle = math.radians(56.0 - 70.0 * (u**0.84))
        bend = math.radians(14.0 - 17.0 * u)

        tx = min(w - 14, max(20, rx + math.cos(angle) * length))
        ty = min(h - 14, max(16, ry + math.sin(angle) * length))
        p1 = (rx + math.cos(angle - bend) * length * 0.35, ry + math.sin(angle - bend) * length * 0.35)
        p2 = (rx + math.cos(angle + bend * 0.35) * length * 0.72, ry + math.sin(angle + bend * 0.35) * length * 0.72)
        spine = cubic_bezier((rx, ry), p1, p2, (tx, ty), steps=36)
        poly, sp = build_feather_polygon(spine, base_w=13.0, mid_w=27.0 + 6.0 * (1.0 - u), tip_sharpness=1.85, jagged=0.10, seed=300 + i)
        paint_feather_layer(
            canvas,
            poly,
            sp,
            col_root=(0.62, 0.28, 0.04),
            col_mid=(1.04, 0.62, 0.12),
            col_tip=(1.18, 0.86, 0.28),
            col_shaft=(1.42, 1.24, 0.68),
            col_rim=(1.38, 1.02, 0.34),
            alpha_scale=0.95,
            barb_strength=0.34,
            rim_boost=0.36,
            shadow_edge=0.36,
            mode="layered",
        )

    # 第二层：14 根中层炽金覆羽
    n_mid = 14
    for i in range(n_mid):
        u = 0.03 + 0.80 * (i / (n_mid - 1))
        rx, ry = ridge[int(u * (len(ridge) - 1))]
        length = (242.0 - 48.0 * u) * (0.86 + 0.18 * math.sin(u * math.pi))
        angle = math.radians(50.0 - 60.0 * u)
        tx = min(w - 26, rx + math.cos(angle) * length)
        ty = min(h - 24, ry + math.sin(angle) * length)
        p1 = (rx + math.cos(angle - 0.16) * length * 0.35, ry + math.sin(angle - 0.16) * length * 0.35)
        p2 = (rx + math.cos(angle + 0.08) * length * 0.72, ry + math.sin(angle + 0.08) * length * 0.72)
        spine = cubic_bezier((rx, ry), p1, p2, (tx, ty), steps=28)
        poly, sp = build_feather_polygon(spine, base_w=11.0, mid_w=22.5, tip_sharpness=1.65, seed=400 + i)
        paint_feather_layer(
            canvas,
            poly,
            sp,
            col_root=(0.76, 0.38, 0.06),
            col_mid=(1.16, 0.76, 0.18),
            col_tip=(1.30, 1.04, 0.42),
            col_shaft=(1.48, 1.36, 0.88),
            col_rim=(1.44, 1.18, 0.50),
            alpha_scale=0.96,
            barb_strength=0.28,
            rim_boost=0.32,
            shadow_edge=0.30,
            mode="layered",
        )

    # 第三层：10 根肩部白金绒羽
    n_inner = 10
    for i in range(n_inner):
        u = 0.02 + 0.58 * (i / (n_inner - 1))
        rx, ry = ridge[int(u * (len(ridge) - 1))]
        length = 136.0 - 24.0 * u
        angle = math.radians(46.0 - 52.0 * u)
        tx = rx + math.cos(angle) * length
        ty = ry + math.sin(angle) * length
        p1 = (rx + math.cos(angle - 0.12) * length * 0.35, ry + math.sin(angle - 0.12) * length * 0.35)
        p2 = (rx + math.cos(angle + 0.08) * length * 0.72, ry + math.sin(angle + 0.08) * length * 0.72)
        spine = cubic_bezier((rx, ry), p1, p2, (tx, ty), steps=20)
        poly, sp = build_feather_polygon(spine, base_w=9.0, mid_w=17.5, tip_sharpness=1.55, seed=460 + i)
        paint_feather_layer(
            canvas,
            poly,
            sp,
            col_root=(0.94, 0.56, 0.12),
            col_mid=(1.28, 0.94, 0.32),
            col_tip=(1.42, 1.24, 0.68),
            col_shaft=(1.56, 1.48, 1.14),
            alpha_scale=0.97,
            barb_strength=0.22,
            rim_boost=0.30,
            shadow_edge=0.24,
            mode="layered",
        )

    # 第四层：熔金白炽翼骨光刃
    poly_ridge, sp_ridge = build_feather_polygon(ridge, base_w=22.0, mid_w=16.0, tip_sharpness=1.35, asymmetry=0.04)
    paint_feather_layer(
        canvas,
        poly_ridge,
        sp_ridge,
        col_root=(1.18, 0.78, 0.24),
        col_mid=(1.38, 1.08, 0.44),
        col_tip=(1.48, 1.32, 0.72),
        col_shaft=(1.64, 1.56, 1.28),
        alpha_scale=0.99,
        barb_strength=0.10,
        rim_boost=0.38,
        shadow_edge=0.10,
        mode="layered",
    )

    scatter_embers(canvas, 78, seed=303, x_range=(0.08, 0.98), y_range=(0.06, 0.94),
                   col_core=(1.40, 1.30, 0.82), col_halo=(1.18, 0.48, 0.08), streak=True)
    canvas = add_hdr_bloom(canvas, [(5.0, 0.24), (16.0, 0.18), (34.0, 0.11)], threshold=0.36)
    return finalize_additive_rgba(canvas, exposure=1.18)


# =====================================================================
# 3 & 4. 幕 3 画面右翼：黑曜石暗紫魔翼实体 + 同轮廓锐利金焰羽缘
# =====================================================================
def generate_obsidian_wing_pair() -> Tuple[Image.Image, Image.Image]:
    """同时生成严格对齐的黑曜石残翼 body（Translucent）与 glow（Additive）。"""
    w, h = 1024, 512
    body_canvas = np.zeros((h, w, 4), dtype=np.float32)
    glow_canvas = np.zeros((h, w, 4), dtype=np.float32)
    union_mask = Image.new("L", (w, h), 0)
    union_draw = ImageDraw.Draw(union_mask)

    ridge = cubic_bezier((20, 312), (210, 74), (585, 22), (995, 42), steps=48)
    feathers_data: List[Tuple[List[Tuple[float, float]], List[Tuple[float, float]], float]] = []

    # 第一层：17 根破碎高扬的黑曜石巨刃飞羽
    n_primary = 17
    rng = random.Random(777)
    for i in range(n_primary):
        u = i / (n_primary - 1)
        if i == 10:
            continue
        rx, ry = ridge[int(u * (len(ridge) - 1))]
        jag_len = rng.uniform(0.84, 1.15)
        length = (385.0 - 74.0 * abs(u - 0.72)) * jag_len
        angle = math.radians(52.0 - 72.0 * (u**0.82) + rng.uniform(-3.0, 3.0))
        bend = math.radians(14.0 - 18.0 * u)

        tx = min(w - 12, max(20, rx + math.cos(angle) * length))
        ty = min(h - 14, max(12, ry + math.sin(angle) * length))
        p1 = (rx + math.cos(angle - bend) * length * 0.34, ry + math.sin(angle - bend) * length * 0.34)
        p2 = (rx + math.cos(angle + bend * 0.35) * length * 0.72, ry + math.sin(angle + bend * 0.35) * length * 0.72)
        spine = cubic_bezier((rx, ry), p1, p2, (tx, ty), steps=36)
        poly, sp = build_feather_polygon(
            spine,
            base_w=13.0,
            mid_w=26.5 + 6.5 * (1.0 - u),
            tip_sharpness=2.15,
            jagged=0.62,
            seed=500 + i,
            asymmetry=0.28,
        )
        feathers_data.append((poly, sp, 1.0))

    # 第二层：13 根中层暗紫骨翼覆羽
    n_mid = 13
    for i in range(n_mid):
        u = 0.04 + 0.80 * (i / (n_mid - 1))
        rx, ry = ridge[int(u * (len(ridge) - 1))]
        length = (228.0 - 38.0 * u) * rng.uniform(0.88, 1.12)
        angle = math.radians(46.0 - 60.0 * u)
        tx = min(w - 22, rx + math.cos(angle) * length)
        ty = min(h - 22, ry + math.sin(angle) * length)
        p1 = (rx + math.cos(angle - 0.16) * length * 0.35, ry + math.sin(angle - 0.16) * length * 0.35)
        p2 = (rx + math.cos(angle + 0.10) * length * 0.72, ry + math.sin(angle + 0.10) * length * 0.72)
        spine = cubic_bezier((rx, ry), p1, p2, (tx, ty), steps=28)
        poly, sp = build_feather_polygon(
            spine,
            base_w=10.5,
            mid_w=21.5,
            tip_sharpness=1.95,
            jagged=0.50,
            seed=600 + i,
            asymmetry=0.24,
        )
        feathers_data.append((poly, sp, 0.95))

    # 5 片脱离主翼、两头尖锐的柳叶剑刃燃烧残羽（彻底消除圆钝感）
    detached_specs = [
        ((715, 318), (925, 355), 11.5),
        ((580, 396), (768, 462), 10.5),
        ((805, 224), (998, 228), 11.0),
        ((430, 405), (568, 492), 9.5),
        ((795, 148), (986, 128), 10.0),
    ]
    for idx, (s0, s1, mw) in enumerate(detached_specs):
        mid1 = (s0[0] + (s1[0] - s0[0]) * 0.35, s0[1] + (s1[1] - s0[1]) * 0.35 - 8.0)
        mid2 = (s0[0] + (s1[0] - s0[0]) * 0.70, s0[1] + (s1[1] - s0[1]) * 0.70 - 4.0)
        sp = cubic_bezier(s0, mid1, mid2, s1, steps=26)
        poly, sp = build_feather_polygon(
            sp,
            base_w=1.2,
            mid_w=mw,
            tip_sharpness=1.8,
            jagged=0.45,
            seed=700 + idx,
            asymmetry=0.18,
            taper_base=True,
        )
        feathers_data.append((poly, sp, 0.96))

    # 骨刺翼脊
    poly_ridge, sp_ridge = build_feather_polygon(ridge, base_w=22.0, mid_w=16.5, tip_sharpness=1.4, jagged=0.32, seed=750, asymmetry=0.08)
    feathers_data.append((poly_ridge, sp_ridge, 1.0))

    # 绘制黑曜石实体剪影（深暗紫黑 + 金属棱面羽纹 + 连续暗金灼脉）
    for poly, sp, ascale in feathers_data:
        union_draw.polygon(poly, fill=255)
        paint_feather_layer(
            body_canvas,
            poly,
            sp,
            col_root=(0.05, 0.02, 0.10),
            col_mid=(0.12, 0.06, 0.20),
            col_tip=(0.24, 0.09, 0.17),
            col_shaft=(0.44, 0.20, 0.12),
            col_rim=(0.32, 0.14, 0.16),
            alpha_scale=0.98 * ascale,
            barb_strength=0.38,
            rim_boost=0.22,
            shadow_edge=0.26,
            mode="alpha",
        )

        # 提取纤细锐利的单羽金焰刃缘（收窄线宽，像刀锋燃烧）
        xs = [p[0] for p in poly]
        ys = [p[1] for p in poly]
        x0, x1 = max(0, int(min(xs)) - 3), min(w, int(max(xs)) + 4)
        y0, y1 = max(0, int(min(ys)) - 3), min(h, int(max(ys)) + 4)
        if x1 > x0 and y1 > y0:
            bw, bh = x1 - x0, y1 - y0
            fm = Image.new("L", (bw, bh), 0)
            ImageDraw.Draw(fm).polygon([(px - x0, py - y0) for px, py in poly], fill=255)
            outer = np.asarray(fm.filter(ImageFilter.MaxFilter(size=3)).filter(ImageFilter.GaussianBlur(radius=0.9)), dtype=np.float32) / 255.0
            inner = np.asarray(fm.filter(ImageFilter.MinFilter(size=3)).filter(ImageFilter.GaussianBlur(radius=0.8)), dtype=np.float32) / 255.0
            edge = np.clip(outer - inner, 0.0, 1.0)

            sp0 = np.array(sp[0], dtype=np.float32)
            sp1 = np.array(sp[-1], dtype=np.float32)
            ax = sp1 - sp0
            alen = float(np.hypot(ax[0], ax[1])) + 1e-5
            gy, gx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
            t = np.clip(((gx - sp0[0]) * ax[0] + (gy - sp0[1]) * ax[1]) / (alen * alen), 0.0, 1.0)

            flame = edge * (0.28 + 1.05 * (t**0.95))
            reg = glow_canvas[y0:y1, x0:x1]
            reg[..., 0] = np.clip(reg[..., 0] + flame * 1.38, 0.0, 2.2)
            reg[..., 1] = np.clip(reg[..., 1] + flame * (0.64 + 0.48 * t), 0.0, 2.1)
            reg[..., 2] = np.clip(reg[..., 2] + flame * (0.10 + 0.36 * (t**2)), 0.0, 1.9)
            reg[..., 3] = np.clip(reg[..., 3] + flame * 0.92, 0.0, 1.0)

    # 整翼外轮廓刀锋黄金轮廓光
    u_outer = np.asarray(union_mask.filter(ImageFilter.MaxFilter(size=5)).filter(ImageFilter.GaussianBlur(radius=1.5)), dtype=np.float32) / 255.0
    u_inner = np.asarray(union_mask.filter(ImageFilter.MinFilter(size=5)).filter(ImageFilter.GaussianBlur(radius=1.0)), dtype=np.float32) / 255.0
    silhouette_rim = np.clip(u_outer - u_inner, 0.0, 1.0)
    glow_canvas[..., 0] = np.clip(glow_canvas[..., 0] + silhouette_rim * 1.42, 0.0, 2.3)
    glow_canvas[..., 1] = np.clip(glow_canvas[..., 1] + silhouette_rim * 0.92, 0.0, 2.2)
    glow_canvas[..., 2] = np.clip(glow_canvas[..., 2] + silhouette_rim * 0.30, 0.0, 1.9)
    glow_canvas[..., 3] = np.clip(glow_canvas[..., 3] + silhouette_rim * 0.96, 0.0, 1.0)

    # 翼内熔金岩浆裂纹
    crack_img = Image.new("L", (w, h), 0)
    cdraw = ImageDraw.Draw(crack_img)
    cracks = [
        [(65, 290), (165, 238), (245, 212), (340, 175), (430, 185)],
        [(185, 226), (255, 275), (345, 298), (435, 345)],
        [(310, 168), (420, 130), (545, 118), (665, 92)],
        [(465, 160), (555, 215), (655, 232)],
        [(610, 108), (745, 86), (860, 74)],
    ]
    for path in cracks:
        cdraw.line(path, fill=255, width=3, joint="curve")
    crack_mask = (np.asarray(crack_img.filter(ImageFilter.GaussianBlur(radius=0.9)), dtype=np.float32) / 255.0) * (
        np.asarray(union_mask, dtype=np.float32) / 255.0
    )
    glow_canvas[..., 0] = np.clip(glow_canvas[..., 0] + crack_mask * 1.50, 0.0, 2.3)
    glow_canvas[..., 1] = np.clip(glow_canvas[..., 1] + crack_mask * 1.12, 0.0, 2.3)
    glow_canvas[..., 2] = np.clip(glow_canvas[..., 2] + crack_mask * 0.42, 0.0, 2.0)
    glow_canvas[..., 3] = np.clip(glow_canvas[..., 3] + crack_mask * 0.96, 0.0, 1.0)

    scatter_embers(glow_canvas, 96, seed=778, x_range=(0.08, 0.99), y_range=(0.04, 0.96),
                   col_core=(1.48, 1.36, 0.88), col_halo=(1.32, 0.46, 0.08), streak=True)
    glow_canvas = add_hdr_bloom(glow_canvas, [(4.0, 0.34), (14.0, 0.24), (28.0, 0.14)], threshold=0.22)

    body_rgba = np.zeros((h, w, 4), dtype=np.uint8)
    body_rgba[..., :3] = np.clip(body_canvas[..., :3] * 255.0, 0, 255).astype(np.uint8)
    body_rgba[..., 3] = np.clip(body_canvas[..., 3] * 255.0, 0, 255).astype(np.uint8)
    return Image.fromarray(body_rgba, mode="RGBA"), finalize_additive_rgba(glow_canvas, exposure=1.18)


# =====================================================================
# 5. 幕 2：旋转金色焚世茧球与彗尾环贴图 (phainon_ult_solar_cocoon.png)
# =====================================================================
def periodic_fbm(h: int, w: int, octaves: Sequence[Tuple[int, int, float]], seed: int = 42) -> np.ndarray:
    """生成严格水平无缝（U 方向周期连续）的多八度平滑噪声场。"""
    rng = np.random.default_rng(seed)
    acc = np.zeros((h, w), dtype=np.float32)
    weight_sum = 0.0

    y_lin = np.linspace(0.0, 1.0, h, endpoint=False, dtype=np.float32)
    x_lin = np.linspace(0.0, 1.0, w, endpoint=False, dtype=np.float32)

    for nx, ny, amp in octaves:
        grid = rng.random((ny + 1, nx), dtype=np.float32)
        gx = x_lin * nx
        gy = y_lin * ny
        ix0 = np.floor(gx).astype(np.int32) % nx
        ix1 = (ix0 + 1) % nx
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


def generate_solar_cocoon() -> Image.Image:
    w, h = 1024, 512
    octaves = [(6, 4, 0.45), (12, 8, 0.28), (24, 16, 0.16), (48, 28, 0.11)]
    n1 = periodic_fbm(h, w, octaves, seed=501)
    n2 = periodic_fbm(h, w, octaves, seed=502)

    y_norm = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None]
    x_norm = np.linspace(0.0, 1.0, w, endpoint=False, dtype=np.float32)[None, :]

    # 水平严格周期连续的螺旋剪切扭曲（无任何右缘接缝！）
    shear = (y_norm - 0.5) * 1.45 + (n1 - 0.5) * 0.24
    u_warped = (x_norm + shear) % 1.0
    v_warped = np.clip(y_norm + (n2 - 0.5) * 0.14, 0.0, 0.999)

    ui = (u_warped * w).astype(np.int32) % w
    vi = (v_warped * h).astype(np.int32)
    plasma = n1[vi, ui] * 0.62 + n2[vi, ui] * 0.38

    band1 = np.power(0.5 + 0.5 * np.sin((u_warped * 4.0 + y_norm * 3.0) * math.tau + n1 * 4.2), 4.0)
    band2 = np.power(0.5 + 0.5 * np.sin((u_warped * 7.0 - y_norm * 5.0) * math.tau + n2 * 5.5), 6.0)
    equator_heat = np.exp(-(((y_norm - 0.5) / 0.34) ** 2))

    energy = np.clip(plasma * 0.85 + band1 * 0.54 + band2 * 0.42 + equator_heat * 0.32 - 0.18, 0.0, 1.6)
    pole_fade = np.maximum(0.0, np.sin(y_norm * math.pi)) ** 0.45
    energy *= (0.35 + 0.65 * pole_fade)

    canvas = np.zeros((h, w, 4), dtype=np.float32)
    e = np.clip(energy, 0.0, 1.45)
    canvas[..., 0] = np.clip(e * 1.38 + 0.14 * np.power(e, 2.0), 0.0, 1.9)
    canvas[..., 1] = np.clip(np.power(np.clip(e - 0.10, 0.0, 1.5), 1.25) * 1.12, 0.0, 1.75)
    canvas[..., 2] = np.clip(np.power(np.clip(e - 0.48, 0.0, 1.2), 1.85) * 1.35, 0.0, 1.55)
    canvas[..., 3] = np.clip(np.power(e, 0.85) * 0.96, 0.0, 1.0)

    # 水平无缝火星与环绕卷积 Bloom
    scatter_embers(canvas, 95, seed=505, x_range=(0.0, 1.0), y_range=(0.12, 0.88),
                   col_core=(1.5, 1.45, 1.15), col_halo=(1.3, 0.65, 0.12), streak=True, wrap_x=True)
    canvas = add_hdr_bloom(canvas, [(6.0, 0.28), (16.0, 0.20)], threshold=0.28, wrap_x=True)
    return finalize_additive_rgba(canvas, exposure=1.22)


# =====================================================================
# 6. 幕 3：12 枚黄金泰坦神徽 4x3 图集 (phainon_ult_titan_runes.png)
# =====================================================================
def draw_star(draw: ImageDraw.ImageDraw, cx: float, cy: float, r_out: float, r_in: float, pts: int, fill: int, width: int = 0) -> None:
    coords: List[Tuple[float, float]] = []
    for i in range(pts * 2):
        ang = -math.pi / 2 + i * math.pi / pts
        r = r_out if i % 2 == 0 else r_in
        coords.append((cx + math.cos(ang) * r, cy + math.sin(ang) * r))
    if width > 0:
        draw.polygon(coords, outline=fill, width=width)
    else:
        draw.polygon(coords, fill=fill)


def draw_regular_poly(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float, sides: int, rot_deg: float, width: int, fill: int = 255) -> None:
    coords: List[Tuple[float, float]] = []
    for i in range(sides):
        ang = math.radians(rot_deg) + i * math.tau / sides
        coords.append((cx + math.cos(ang) * r, cy + math.sin(ang) * r))
    coords.append(coords[0])
    draw.line(coords, fill=fill, width=width, joint="curve")


def draw_single_titan_rune(idx: int, size: int = 256) -> Image.Image:
    """以 256x256 超采样绘制第 idx (0..11) 枚独特翁法罗斯黄金泰坦神徽。"""
    img = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(img)
    cx = cy = size * 0.5
    r = size * 0.34
    lw_main = 8
    lw_fine = 5

    for k in range(4):
        ang = k * math.pi / 2
        px = cx + math.cos(ang) * (r * 1.12)
        py = cy + math.sin(ang) * (r * 1.12)
        draw_star(d, px, py, 9, 3, 4, fill=215)

    if idx == 0:
        d.ellipse((cx - r * 0.62, cy - r * 0.62, cx + r * 0.62, cy + r * 0.62), outline=255, width=lw_main)
        for i in range(12):
            ang = i * math.tau / 12
            r0 = r * 0.66
            r1 = r * (1.06 if i % 2 == 0 else 0.88)
            d.line([(cx + math.cos(ang) * r0, cy + math.sin(ang) * r0),
                    (cx + math.cos(ang) * r1, cy + math.sin(ang) * r1)], fill=255, width=lw_fine if i % 2 else lw_main)
        draw_star(d, cx, cy, r * 0.42, r * 0.14, 4, fill=255)
    elif idx == 1:
        d.ellipse((cx - r * 0.78, cy - r * 0.78, cx + r * 0.78, cy + r * 0.78), outline=245, width=lw_fine)
        d.line([(cx, cy - r * 1.08), (cx, cy + r * 1.08)], fill=255, width=lw_main + 2)
        d.line([(cx - r * 0.52, cy - r * 0.28), (cx + r * 0.52, cy - r * 0.28)], fill=255, width=lw_main)
        draw_regular_poly(d, cx, cy - r * 0.28, r * 0.32, 4, 45, lw_fine)
        draw_star(d, cx, cy - r * 0.96, 18, 7, 4, fill=255)
    elif idx == 2:
        draw_regular_poly(d, cx, cy, r * 0.76, 4, 45, lw_main)
        d.arc((cx - r * 1.02, cy - r * 0.72, cx - r * 0.10, cy + r * 0.72), 110, 250, fill=255, width=lw_main)
        d.arc((cx + r * 0.10, cy - r * 0.72, cx + r * 1.02, cy + r * 0.72), 290, 70, fill=255, width=lw_main)
        draw_star(d, cx, cy, r * 0.40, r * 0.14, 8, fill=255)
    elif idx == 3:
        d.line([(cx - r * 0.68, cy - r * 0.86), (cx + r * 0.68, cy - r * 0.86),
                (cx - r * 0.68, cy + r * 0.86), (cx + r * 0.68, cy + r * 0.86),
                (cx - r * 0.68, cy - r * 0.86)], fill=255, width=lw_main, joint="curve")
        d.ellipse((cx - r * 0.92, cy - r * 0.92, cx + r * 0.92, cy + r * 0.92), outline=210, width=lw_fine)
        draw_star(d, cx, cy, r * 0.26, r * 0.10, 4, fill=255)
    elif idx == 4:
        d.line([(cx - r * 0.85, cy - r * 0.85), (cx + r * 0.85, cy + r * 0.85)], fill=255, width=lw_main)
        d.line([(cx + r * 0.85, cy - r * 0.85), (cx - r * 0.85, cy + r * 0.85)], fill=255, width=lw_main)
        draw_regular_poly(d, cx, cy, r * 0.68, 4, 0, lw_main)
        draw_regular_poly(d, cx, cy, r * 0.34, 4, 45, lw_fine)
    elif idx == 5:
        d.arc((cx - r * 0.92, cy - r * 0.92, cx + r * 0.92, cy + r * 0.92), 205, 335, fill=255, width=lw_main + 1)
        d.arc((cx - r * 0.92, cy - r * 0.92, cx + r * 0.92, cy + r * 0.92), 25, 155, fill=255, width=lw_main + 1)
        d.line([(cx, cy - r * 0.92), (cx, cy + r * 0.92)], fill=255, width=lw_fine)
        d.polygon([(cx, cy), (cx - r * 0.62, cy - r * 0.35), (cx - r * 0.48, cy + r * 0.32)], outline=255, width=lw_fine)
        d.polygon([(cx, cy), (cx + r * 0.62, cy - r * 0.35), (cx + r * 0.48, cy + r * 0.32)], outline=255, width=lw_fine)
    elif idx == 6:
        d.arc((cx - r * 0.96, cy - r * 0.96, cx + r * 0.96, cy + r * 0.56), 25, 155, fill=255, width=lw_main)
        d.arc((cx - r * 0.96, cy - r * 0.56, cx + r * 0.96, cy + r * 0.96), 205, 335, fill=255, width=lw_main)
        d.ellipse((cx - r * 0.34, cy - r * 0.34, cx + r * 0.34, cy + r * 0.34), outline=255, width=lw_main)
        draw_star(d, cx, cy, r * 0.24, r * 0.08, 4, fill=255)
    elif idx == 7:
        d.line([(cx, cy - r * 0.98), (cx, cy + r * 0.96)], fill=255, width=lw_main)
        d.arc((cx - r * 0.72, cy - r * 0.78, cx + r * 0.72, cy + r * 0.46), 0, 180, fill=255, width=lw_main)
        d.line([(cx - r * 0.72, cy - r * 0.16), (cx - r * 0.72, cy - r * 0.72)], fill=255, width=lw_main)
        d.line([(cx + r * 0.72, cy - r * 0.16), (cx + r * 0.72, cy - r * 0.72)], fill=255, width=lw_main)
        d.arc((cx - r * 0.88, cy + r * 0.25, cx + r * 0.88, cy + r * 1.15), 200, 340, fill=225, width=lw_fine)
    elif idx == 8:
        d.ellipse((cx - r * 0.88, cy - r * 0.88, cx + r * 0.88, cy + r * 0.88), outline=255, width=lw_main)
        d.ellipse((cx - r * 0.88, cy - r * 0.36, cx + r * 0.88, cy + r * 0.36), outline=235, width=lw_fine)
        d.ellipse((cx - r * 0.36, cy - r * 0.88, cx + r * 0.36, cy + r * 0.88), outline=235, width=lw_fine)
        draw_star(d, cx, cy, r * 0.32, r * 0.12, 6, fill=255)
    elif idx == 9:
        d.arc((cx - r * 0.78, cy - r * 0.92, cx + r * 0.78, cy + r * 0.62), 180, 360, fill=255, width=lw_main)
        d.line([(cx - r * 0.78, cy - r * 0.15), (cx - r * 0.78, cy + r * 0.85),
                (cx + r * 0.78, cy + r * 0.85), (cx + r * 0.78, cy - r * 0.15)], fill=255, width=lw_main)
        d.arc((cx - r * 0.44, cy - r * 0.58, cx + r * 0.44, cy + r * 0.32), 180, 360, fill=230, width=lw_fine)
        d.line([(cx - r * 0.44, cy - r * 0.12), (cx - r * 0.44, cy + r * 0.85)], fill=230, width=lw_fine)
        d.line([(cx + r * 0.44, cy - r * 0.12), (cx + r * 0.44, cy + r * 0.85)], fill=230, width=lw_fine)
        draw_star(d, cx, cy - r * 0.05, r * 0.26, r * 0.10, 4, fill=255)
    elif idx == 10:
        d.ellipse((cx - r * 0.92, cy - r * 0.62, cx + r * 0.32, cy + r * 0.62), outline=255, width=lw_main)
        d.ellipse((cx - r * 0.32, cy - r * 0.62, cx + r * 0.92, cy + r * 0.62), outline=255, width=lw_main)
        draw_regular_poly(d, cx, cy, r * 0.86, 6, 30, lw_fine, fill=220)
        draw_star(d, cx, cy, r * 0.30, r * 0.11, 6, fill=255)
    else:
        draw_regular_poly(d, cx, cy + r * 0.08, r * 0.88, 3, -90, lw_main)
        draw_regular_poly(d, cx, cy + r * 0.18, r * 0.52, 3, -90, lw_fine)
        d.line([(cx - r * 0.86, cy + r * 0.78), (cx + r * 0.86, cy + r * 0.78)], fill=255, width=lw_main)
        draw_star(d, cx, cy - r * 0.78, 16, 6, 4, fill=255)

    return img


def generate_titan_runes_atlas() -> Image.Image:
    w, h = 512, 512
    cols, rows = 4, 3
    cw = w / cols
    ch = h / rows

    canvas = np.zeros((h, w, 4), dtype=np.float32)

    for idx in range(12):
        col = idx % cols
        row = idx // cols
        x0 = int(round(col * cw))
        x1 = int(round((col + 1) * cw))
        y0 = int(round(row * ch))
        y1 = int(round((row + 1) * ch))
        box_w, box_h = x1 - x0, y1 - y0
        side = min(box_w, box_h) - 18

        hi = draw_single_titan_rune(idx, size=256)
        core_img = hi.resize((side, side), Image.Resampling.LANCZOS)
        halo_s = hi.filter(ImageFilter.GaussianBlur(radius=9.0)).resize((side, side), Image.Resampling.LANCZOS)
        halo_l = hi.filter(ImageFilter.GaussianBlur(radius=22.0)).resize((side, side), Image.Resampling.LANCZOS)

        core = np.asarray(core_img, dtype=np.float32) / 255.0
        hs = np.asarray(halo_s, dtype=np.float32) / 255.0
        hl = np.asarray(halo_l, dtype=np.float32) / 255.0

        yy = np.linspace(-1.0, 1.0, side, dtype=np.float32)[:, None]
        xx = np.linspace(-1.0, 1.0, side, dtype=np.float32)[None, :]
        edge_win = np.clip((1.0 - np.maximum(np.abs(xx), np.abs(yy))) / 0.14, 0.0, 1.0)
        hl *= edge_win

        r_ch = core * 1.45 + hs * 1.10 + hl * 0.75
        g_ch = core * 1.32 + hs * 0.82 + hl * 0.36
        b_ch = core * 0.92 + hs * 0.28 + hl * 0.06
        a_ch = np.clip(core * 1.0 + hs * 0.85 + hl * 0.55, 0.0, 1.0)

        px = x0 + (box_w - side) // 2
        py = y0 + (box_h - side) // 2
        canvas[py:py + side, px:px + side, 0] = r_ch
        canvas[py:py + side, px:px + side, 1] = g_ch
        canvas[py:py + side, px:px + side, 2] = b_ch
        canvas[py:py + side, px:px + side, 3] = a_ch

    return finalize_additive_rgba(canvas, exposure=1.28)


# =====================================================================
# 7. 全幕：世界空间 26 格暗紫金红焚世苍穹穹顶 (phainon_ult_sky_veil.png)
# =====================================================================
def generate_sky_veil() -> Image.Image:
    w, h = 512, 256
    n_cloud = periodic_fbm(h, w, [(4, 3, 0.48), (8, 6, 0.28), (16, 10, 0.15), (32, 18, 0.09)], seed=901)
    n_detail = periodic_fbm(h, w, [(10, 6, 0.55), (20, 12, 0.30), (40, 22, 0.15)], seed=902)

    v = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None]

    zenith_col = np.array([0.04, 0.02, 0.09], dtype=np.float32)
    cloud_dark = np.array([0.14, 0.05, 0.16], dtype=np.float32)
    horizon_glow = np.array([0.62, 0.26, 0.10], dtype=np.float32)
    gold_streak = np.array([0.92, 0.58, 0.18], dtype=np.float32)

    horizon_band = np.exp(-(((v - 0.56) / 0.17) ** 2))
    cloud_factor = np.clip((n_cloud - 0.28) * 1.55, 0.0, 1.0)
    streak_factor = np.clip((n_detail - 0.52) * 2.2, 0.0, 1.0) * horizon_band

    rgb = (
        zenith_col * (1.0 - cloud_factor[..., None])
        + cloud_dark * cloud_factor[..., None]
        + horizon_glow * (horizon_band * (0.45 + 0.55 * n_cloud))[..., None]
        + gold_streak * (streak_factor * 0.55)[..., None]
    )

    rng = np.random.default_rng(909)
    stars = (rng.random((h, w)) > 0.9965).astype(np.float32) * np.clip((0.50 - v) / 0.35, 0.0, 1.0)
    rgb += stars[..., None] * np.array([0.85, 0.78, 0.58], dtype=np.float32)

    alpha = np.where(v < 0.78, 0.92, 0.92 * np.clip((1.0 - v) / 0.22, 0.0, 1.0))

    rgba = np.zeros((h, w, 4), dtype=np.uint8)
    rgba[..., :3] = np.clip(rgb * 255.0, 0, 255).astype(np.uint8)
    rgba[..., 3] = np.clip(alpha * 255.0, 0, 255).astype(np.uint8)
    return Image.fromarray(rgba, mode="RGBA")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    outputs = {
        "phainon_ult_seraph_wing.png": generate_seraph_wing(),
        "phainon_ult_wing_gold.png": generate_gold_wing(),
        "phainon_ult_solar_cocoon.png": generate_solar_cocoon(),
        "phainon_ult_titan_runes.png": generate_titan_runes_atlas(),
        "phainon_ult_sky_veil.png": generate_sky_veil(),
    }
    obs_body, obs_glow = generate_obsidian_wing_pair()
    outputs["phainon_ult_wing_obsidian_body.png"] = obs_body
    outputs["phainon_ult_wing_obsidian_glow.png"] = obs_glow

    for name, img in outputs.items():
        path = OUT_DIR / name
        img.save(path, format="PNG")
        print(f"[OK] {path} ({img.width}x{img.height}, mode={img.mode})")


if __name__ == "__main__":
    main()
