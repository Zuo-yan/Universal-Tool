#!/usr/bin/env python3
"""
白厄变身弹反「灾厄·弑魂焚诏」高精度 5 张 VFX 贴图生成脚本（精修 v2）。

严格遵循 minecraft-skill-vfx v2.0 防粗糙铁律：
- 暗色黑曜石实体剪影与 Additive 炽焰高光由同一几何母版派生，像素级对齐；
- 竖劈刀光采用各向异性拉伸湍流与炽焰笔触，杜绝规则斑马横纹；
- 火山喷涌贴图采用强纵向拉伸 periodic_fbm + wrap_x=True 水平无缝 Bloom，熔金到猩红自然分层不过曝；
- 所有幂运算前对底数做 np.maximum(0.0, ...) 防止 float32 边界 NaN。

输出清单（src/main/resources/assets/universal_tool/textures/misc/）：
1. phainon_counter_greatsword_body.png (512x1024) 黑曜石/暗铁裁决巨剑实体剪影（Translucent）
2. phainon_counter_greatsword_glow.png (512x1024) 同轮廓炽金/烈焰巨剑边缘、日核与熔金血槽（Additive）
3. phainon_counter_cleave_flame.png    (1024x512) 竖劈裂空熔金烈焰刀光展开带（Additive）
4. phainon_counter_edict_sigil.png     (512x512)  纷争神纹护盾法环与 12 枚弧形棘轮（Additive）
5. phainon_counter_crater_lava.png     (1024x512) 水平无缝裂地岩浆喷涌与熔岩流光（Additive）
"""

from __future__ import annotations

import math
import random
from pathlib import Path
from typing import List, Sequence, Tuple

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "src" / "main" / "resources" / "assets" / "universal_tool" / "textures" / "misc"

# 与 generate_phainon_counter_models.py 中的巨剑 UV 空间严格对齐
SWORD_X_HALF = 1.05
SWORD_Y_MIN = -0.50
SWORD_Y_MAX = 4.65


def periodic_fbm(
    h: int,
    w: int,
    base_freq_x: int = 4,
    base_freq_y: int = 3,
    octaves: int = 5,
    seed: int = 42,
    wrap_y: bool = False,
) -> np.ndarray:
    """生成真正水平无缝（可选垂直无缝）的多倍频值噪声，杜绝拼接竖线。"""
    rng = np.random.default_rng(seed)
    acc = np.zeros((h, w), dtype=np.float32)
    amp = 0.55
    amp_sum = 0.0

    yy = np.linspace(0.0, 1.0, h, endpoint=False, dtype=np.float32)[:, None]
    xx = np.linspace(0.0, 1.0, w, endpoint=False, dtype=np.float32)[None, :]

    for oct_i in range(octaves):
        fx = max(1, base_freq_x * (2 ** oct_i))
        fy = max(1, base_freq_y * (2 ** oct_i))
        grid = rng.random((fy + (0 if wrap_y else 1), fx), dtype=np.float32)

        gx = xx * fx
        gy = yy * fy
        x0 = np.floor(gx).astype(np.int32) % fx
        x1 = (x0 + 1) % fx
        tx = gx - np.floor(gx)
        tx = tx * tx * (3.0 - 2.0 * tx)

        if wrap_y:
            y0 = np.floor(gy).astype(np.int32) % fy
            y1 = (y0 + 1) % fy
        else:
            y0 = np.clip(np.floor(gy).astype(np.int32), 0, fy - 1)
            y1 = np.clip(y0 + 1, 0, fy)
        ty = gy - np.floor(gy)
        ty = ty * ty * (3.0 - 2.0 * ty)

        v00 = grid[y0, x0]
        v10 = grid[y0, x1]
        v01 = grid[y1, x0]
        v11 = grid[y1, x1]
        vx0 = v00 * (1.0 - tx) + v10 * tx
        vx1 = v01 * (1.0 - tx) + v11 * tx
        val = vx0 * (1.0 - ty) + vx1 * ty

        acc += val * amp
        amp_sum += amp
        amp *= 0.52

    return np.clip(acc / max(amp_sum, 1e-5), 0.0, 1.0)


def add_hdr_bloom(
    rgb: np.ndarray,
    radii_weights: Sequence[Tuple[float, float]],
    threshold: float = 0.24,
    wrap_x: bool = False,
) -> np.ndarray:
    """对线性 RGB 浮点图施加多层 HDR 高斯辉光（支持 wrap_x 水平无缝卷积）。"""
    h, w, _ = rgb.shape
    lum = 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]
    extract = np.clip((lum - threshold) / max(1e-4, 1.0 - threshold), 0.0, 2.0)[..., None]
    bright = np.clip(rgb * extract * (255.0 / 1.5), 0.0, 255.0).astype(np.uint8)

    acc = np.zeros_like(rgb)
    for r, weight in radii_weights:
        if wrap_x:
            pad = int(math.ceil(r * 3.0))
            padded = np.concatenate([bright[:, -pad:, :], bright, bright[:, :pad, :]], axis=1)
            pil_p = Image.fromarray(padded, mode="RGB").filter(ImageFilter.GaussianBlur(radius=r))
            blurred = np.asarray(pil_p, dtype=np.float32)[:, pad:pad + w, :] * (1.5 / 255.0)
        else:
            pil_b = Image.fromarray(bright, mode="RGB").filter(ImageFilter.GaussianBlur(radius=r))
            blurred = np.asarray(pil_b, dtype=np.float32) * (1.5 / 255.0)
        acc += blurred * weight
    return rgb + acc


def tonemap_additive(rgb: np.ndarray, alpha_boost: float = 1.15) -> Image.Image:
    """对 Additive 发光贴图进行柔和 HDR 色调映射，保留炽金/绯红饱和度。"""
    rgb = np.maximum(0.0, rgb)
    mapped = rgb / (1.0 + 0.38 * rgb)
    peak = np.max(mapped, axis=-1)
    alpha = np.clip(peak * alpha_boost, 0.0, 1.0)
    rgba = np.dstack([np.clip(mapped, 0.0, 1.0), alpha])
    return Image.fromarray((rgba * 255.0).astype(np.uint8), mode="RGBA")


def sword_to_px(x: float, y: float, w: int, h: int) -> Tuple[float, float]:
    u = 0.5 + x / (2.0 * SWORD_X_HALF)
    v = 1.0 - (y - SWORD_Y_MIN) / (SWORD_Y_MAX - SWORD_Y_MIN)
    return (u * (w - 1), v * (h - 1))


# ---------------------------------------------------------------------------
# 1 & 2. 巨剑双 Pass 对齐贴图（body 实体剪影 + glow 炽金烈焰高光）
# ---------------------------------------------------------------------------
def generate_greatsword_pair() -> Tuple[Image.Image, Image.Image]:
    w, h = 512, 1024

    # 与修长版 OBJ 完全一致的轮廓坐标
    blade_right = [
        (0.24, 0.32), (0.30, 0.72), (0.23, 1.06), (0.36, 1.48),
        (0.30, 1.96), (0.39, 2.48), (0.33, 3.08), (0.23, 3.64),
        (0.12, 4.12), (0.045, 4.42), (0.0, 4.62),
    ]
    blade_poly_w = blade_right + [(-x, y) for x, y in reversed(blade_right[:-1])]

    guard_polys_w: List[List[Tuple[float, float]]] = []
    for side in (-1.0, 1.0):
        wing = [
            (side * 0.12, 0.35), (side * 0.38, 0.50), (side * 0.66, 0.73),
            (side * 0.90, 1.01), (side * 1.01, 1.15), (side * 0.90, 0.91),
            (side * 0.66, 0.54), (side * 0.38, 0.24), (side * 0.12, 0.12),
        ]
        spur = [
            (side * 0.28, 0.12), (side * 0.56, -0.09), (side * 0.38, 0.24),
        ]
        fin = [
            (side * 0.20, 1.12), (side * 0.32, 1.21), (side * 0.48, 1.65),
            (side * 0.54, 2.31), (side * 0.46, 3.01), (side * 0.30, 3.61),
            (side * 0.15, 4.07), (side * 0.08, 3.98), (side * 0.16, 3.52),
            (side * 0.25, 2.92), (side * 0.30, 2.22), (side * 0.26, 1.56),
        ]
        guard_polys_w.extend([wing, spur, fin])

    hilt_poly_w = [
        (0.085, 0.08), (0.068, -0.22), (0.115, -0.35), (0.0, -0.48),
        (-0.115, -0.35), (-0.068, -0.22), (-0.085, 0.08),
    ]

    mask_all = Image.new("L", (w, h), 0)
    mask_blade = Image.new("L", (w, h), 0)
    mask_guard = Image.new("L", (w, h), 0)
    mask_fins = Image.new("L", (w, h), 0)

    def fill_world_poly(img: Image.Image, pts: Sequence[Tuple[float, float]], val: int = 255) -> None:
        ImageDraw.Draw(img).polygon([sword_to_px(x, y, w, h) for x, y in pts], fill=val)

    fill_world_poly(mask_blade, blade_poly_w)
    fill_world_poly(mask_all, blade_poly_w)
    fill_world_poly(mask_guard, hilt_poly_w)
    fill_world_poly(mask_all, hilt_poly_w)

    for idx, poly in enumerate(guard_polys_w):
        if idx % 3 == 2:
            fill_world_poly(mask_fins, poly)
        else:
            fill_world_poly(mask_guard, poly)
        fill_world_poly(mask_all, poly)

    core_pts = []
    for k in range(8):
        ang = k * math.pi * 0.25
        rad = 0.24 * (1.18 if k % 2 == 0 else 0.86)
        core_pts.append((math.cos(ang) * rad, 0.24 + math.sin(ang) * rad))
    fill_world_poly(mask_guard, core_pts)
    fill_world_poly(mask_all, core_pts)

    all_soft = np.asarray(mask_all.filter(ImageFilter.GaussianBlur(radius=1.0)), dtype=np.float32) / 255.0
    blade_m = np.asarray(mask_blade.filter(ImageFilter.GaussianBlur(radius=0.8)), dtype=np.float32) / 255.0
    guard_m = np.asarray(mask_guard.filter(ImageFilter.GaussianBlur(radius=0.8)), dtype=np.float32) / 255.0
    fins_m = np.asarray(mask_fins.filter(ImageFilter.GaussianBlur(radius=0.9)), dtype=np.float32) / 255.0

    # 分离主剑身描边与副翼柔和外缘，避免副翼内接边发硬
    solid_mask = Image.fromarray(np.clip((blade_m + guard_m) * 255.0, 0, 255).astype(np.uint8), mode="L")
    solid_eroded = np.asarray(
        solid_mask.filter(ImageFilter.MinFilter(size=7)).filter(ImageFilter.GaussianBlur(radius=1.1)),
        dtype=np.float32,
    ) / 255.0
    solid_bevel = np.asarray(
        solid_mask.filter(ImageFilter.MinFilter(size=17)).filter(ImageFilter.GaussianBlur(radius=2.0)),
        dtype=np.float32,
    ) / 255.0
    rim_solid = np.clip(np.clip(blade_m + guard_m, 0.0, 1.0) - solid_eroded, 0.0, 1.0)
    bevel_solid = np.clip(np.clip(blade_m + guard_m, 0.0, 1.0) - solid_bevel, 0.0, 1.0)

    fin_eroded = np.asarray(
        mask_fins.filter(ImageFilter.MinFilter(size=7)).filter(ImageFilter.GaussianBlur(radius=1.2)),
        dtype=np.float32,
    ) / 255.0
    rim_fin = np.clip(fins_m - fin_eroded, 0.0, 1.0) * (1.0 - blade_m * 0.85)

    gy, gx = np.mgrid[0:h, 0:w].astype(np.float32)
    wx = (gx / (w - 1) - 0.5) * (2.0 * SWORD_X_HALF)
    wy = SWORD_Y_MIN + (1.0 - gy / (h - 1)) * (SWORD_Y_MAX - SWORD_Y_MIN)

    noise_fine = periodic_fbm(h, w, base_freq_x=6, base_freq_y=14, octaves=5, seed=101)
    noise_vein = periodic_fbm(h, w, base_freq_x=5, base_freq_y=11, octaves=4, seed=207)

    # --- Pass 2: 黑曜石/暗锻钢实体剪影 body ---
    bevel_shade = np.clip(0.56 + 0.36 * np.tanh(wx * 5.0) + 0.34 * bevel_solid, 0.30, 1.24)
    forged = 0.82 + 0.34 * noise_fine
    chevron = 0.5 + 0.5 * np.sin((wy * 5.0 - np.abs(wx) * 8.0) * math.pi)

    obs_r = (20.0 + 26.0 * bevel_solid + 18.0 * guard_m) * bevel_shade * forged
    obs_g = (14.0 + 18.0 * bevel_solid + 13.0 * guard_m) * bevel_shade * forged
    obs_b = (30.0 + 24.0 * bevel_solid + 6.0 * blade_m) * bevel_shade * forged

    # 护手暗鎏金浮雕折面
    bronze = guard_m * bevel_solid * (0.65 + 0.35 * chevron)
    obs_r += bronze * 56.0
    obs_g += bronze * 38.0
    obs_b += bronze * 10.0

    # 中央血槽阴影压深，雕刻感拉满
    fuller_dark = np.exp(-((wx / 0.095) ** 2)) * np.clip((wy - 0.45) / 0.35, 0.0, 1.0) * np.clip((4.35 - wy) / 0.35, 0.0, 1.0)
    obs_r *= (1.0 - 0.40 * fuller_dark)
    obs_g *= (1.0 - 0.42 * fuller_dark)
    obs_b *= (1.0 - 0.32 * fuller_dark)

    # 浮空副翼半透黑金刃影（柔和渐隐避免灰硬块）
    fin_grad = fins_m * np.clip((wy - 1.15) / 1.4, 0.35, 1.0) * (1.0 - blade_m)
    body_alpha = np.clip(blade_m * 0.97 + guard_m * 0.97 + fin_grad * 0.48, 0.0, 1.0) * all_soft
    body_rgba = np.dstack([
        np.clip(obs_r, 0.0, 255.0),
        np.clip(obs_g, 0.0, 255.0),
        np.clip(obs_b, 0.0, 255.0),
        np.clip(body_alpha * 255.0, 0.0, 255.0),
    ]).astype(np.uint8)
    body_img = Image.fromarray(body_rgba, mode="RGBA")

    # --- Pass 3: 同轮廓炽金/熔红烈焰高光 glow（Additive）---
    glow = np.zeros((h, w, 3), dtype=np.float32)

    # 1) 贯穿全刃的炽白-熔金血槽能量核心 + 阶梯折光菱纹
    y_norm = np.clip((wy - 0.26) / 4.28, 0.0, 1.0)
    fuller_w = 0.018 + 0.082 * (np.maximum(0.0, np.sin(y_norm * math.pi)) ** 0.55) * (1.0 - 0.32 * y_norm)
    fuller_core = np.exp(-((wx / np.maximum(fuller_w, 0.012)) ** 2)) * blade_m
    fuller_hot = np.exp(-((wx / np.maximum(fuller_w * 0.40, 0.006)) ** 2)) * blade_m
    flow_band = 0.78 + 0.32 * np.sin(wy * 6.8 - noise_fine * 4.5)

    glow[..., 0] += fuller_core * (1.25 * flow_band) + fuller_hot * 1.15
    glow[..., 1] += fuller_core * (0.76 * flow_band) + fuller_hot * 1.02
    glow[..., 2] += fuller_core * (0.18 * flow_band) + fuller_hot * 0.72

    # 2) 剑柄中央烈阳神核（严格限制在护手范围内，绝不向外拉出水平横线！）
    d_core = np.hypot(wx, wy - 0.24)
    core_gate = np.exp(-((d_core / 0.42) ** 2.4))
    sun_orb = np.exp(-((d_core / 0.135) ** 2))
    sun_ring = np.exp(-(((d_core - 0.175) / 0.018) ** 2))
    star_d1 = np.exp(-(((wx + (wy - 0.24)) / 0.028) ** 2)) * core_gate
    star_d2 = np.exp(-(((wx - (wy - 0.24)) / 0.028) ** 2)) * core_gate
    sun_energy = sun_orb * 1.95 + sun_ring * 0.95 + (star_d1 + star_d2) * 0.75
    glow[..., 0] += sun_energy * 1.20
    glow[..., 1] += sun_energy * 0.94
    glow[..., 2] += sun_energy * 0.48 + sun_orb * 0.58

    # 3) 锐利炽金/烈焰刃缘描边
    edge_flicker = 0.76 + 0.44 * noise_vein
    glow[..., 0] += rim_solid * 1.38 * edge_flicker + rim_fin * 1.22 * edge_flicker + bevel_solid * 0.22
    glow[..., 1] += (rim_solid * (0.86 * blade_m + 0.68 * guard_m) + rim_fin * 0.54) * edge_flicker + bevel_solid * 0.09
    glow[..., 2] += (rim_solid * (0.32 * blade_m + 0.18 * guard_m) + rim_fin * 0.10) * edge_flicker

    # 4) 剑身内部熔金裂纹 + 浮空副翼炽焰流体
    cracks = np.exp(-(((noise_vein - 0.50) / 0.022) ** 2)) * (blade_m * 0.75 + guard_m * 0.55)
    fin_flame = fin_grad * (0.35 + 0.75 * noise_fine) * (0.55 + 0.45 * np.sin(wy * 5.2 + noise_vein * 4.0))
    glow[..., 0] += cracks * 1.10 + fin_flame * 1.15
    glow[..., 1] += cracks * 0.42 + fin_flame * 0.48
    glow[..., 2] += cracks * 0.08 + fin_flame * 0.10

    # 5) 伴随巨剑升腾的锋利炽金余烬（带柔光晕）
    rng = random.Random(779)
    spark_layer = np.zeros((h, w, 3), dtype=np.float32)
    for _ in range(42):
        sy = rng.uniform(0.55, 4.35)
        sx = rng.choice([-1.0, 1.0]) * rng.uniform(0.28, 0.76)
        px0, py0 = sword_to_px(sx, sy, w, h)
        length = rng.uniform(14.0, 36.0)
        angle = -math.pi * 0.5 + (0.16 if sx > 0 else -0.16) + rng.uniform(-0.14, 0.14)
        width = rng.uniform(1.6, 3.0)
        bx0 = max(0, int(px0 - length - 6))
        bx1 = min(w, int(px0 + length + 7))
        by0 = max(0, int(py0 - length - 6))
        by1 = min(h, int(py0 + length + 7))
        if bx1 <= bx0 or by1 <= by0:
            continue
        ly, lx = np.mgrid[by0:by1, bx0:bx1].astype(np.float32)
        dx, dy = lx - px0, ly - py0
        ca, sa = math.cos(angle), math.sin(angle)
        along = dx * ca + dy * sa
        perp = -dx * sa + dy * ca
        t_s = np.clip((along / length) + 0.5, 0.0, 1.0)
        env = np.maximum(0.0, np.sin(t_s * math.pi)) ** 1.1
        core_sp = np.exp(-((perp / np.maximum(width * env, 0.5)) ** 2)) * env
        halo_sp = np.exp(-((perp / np.maximum(width * 2.8 * env, 1.4)) ** 2)) * env * 0.45
        hot = rng.uniform(0.70, 1.15)
        spark_layer[by0:by1, bx0:bx1, 0] += (core_sp * 1.25 + halo_sp * 0.95) * hot
        spark_layer[by0:by1, bx0:bx1, 1] += (core_sp * 0.85 + halo_sp * 0.42) * hot
        spark_layer[by0:by1, bx0:bx1, 2] += (core_sp * 0.35 + halo_sp * 0.08) * hot

    glow += spark_layer
    glow = add_hdr_bloom(glow, [(3.8, 0.32), (10.5, 0.22), (22.0, 0.10)], threshold=0.26, wrap_x=False)

    edge_x = np.clip(np.minimum(gx, w - 1 - gx) / 14.0, 0.0, 1.0)
    edge_y = np.clip(np.minimum(gy, h - 1 - gy) / 16.0, 0.0, 1.0)
    glow *= (edge_x * edge_y)[..., None]

    glow_img = tonemap_additive(glow, alpha_boost=1.20)
    return body_img, glow_img


# ---------------------------------------------------------------------------
# 3. phainon_counter_cleave_flame.png (1024x512) 竖劈裂空熔金烈焰刀光展开带
# ---------------------------------------------------------------------------
def generate_cleave_flame() -> Image.Image:
    w, h = 1024, 512
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    u = xx / (w - 1)
    v = yy / (h - 1)

    # 各向异性拉伸湍流（沿斩击方向 u 拉伸 4~5 倍，彻底告别均匀平行横纹！）
    n_flow = periodic_fbm(h, w, base_freq_x=3, base_freq_y=12, octaves=5, seed=313)
    n_tear = periodic_fbm(h, w, base_freq_x=6, base_freq_y=22, octaves=4, seed=419)
    n_warp = periodic_fbm(h, w, base_freq_x=4, base_freq_y=5, octaves=4, seed=521)

    # 沿 u 方向包络：后端尖尾拉入，前锋 u ~ 0.82 最炽烈，u -> 1.0 锋利收口
    u_env = (np.maximum(0.0, np.sin(np.clip(u, 0.0, 1.0) * math.pi)) ** 0.52) * (0.40 + 0.72 * (u ** 0.72))
    u_mask = u_env * np.clip((1.0 - u) / 0.065, 0.0, 1.0) * np.clip(u / 0.085, 0.0, 1.0)

    # 刀锋中心曲线随弧长自然前冲并受湍流扭曲
    blade_v = 0.13 + 0.055 * (1.0 - u) + 0.045 * (n_warp - 0.5)
    dv = v - blade_v

    # 1) 极致锐利的炽白金刃锋
    razor_white = np.exp(-((dv / 0.028) ** 2))
    gold_sheath = np.exp(-((dv / 0.085) ** 2)) * (0.72 + 0.55 * n_flow)

    # 2) 二次元手绘感高速撕裂流光焰带（用幂次湍流谷峰替代规则正弦波！）
    tail_dist = np.maximum(0.0, dv)
    tail_decay = np.exp(-tail_dist / (0.20 + 0.24 * n_warp)) * np.clip((v - (blade_v - 0.06)) / 0.08, 0.0, 1.0)
    # 细长撕裂光带（非线性锐化 n_flow & n_tear 形成飘逸笔触）
    brush_gold = (np.maximum(0.0, n_flow * 1.15 - 0.22) ** 1.8) * (0.55 + 0.65 * n_tear)
    brush_crimson = (np.maximum(0.0, n_tear * 1.10 - 0.15) ** 1.55) * (0.60 + 0.60 * n_warp)

    # 尾焰外沿的炽红熔岩碎焰
    crimson_tail = tail_decay * np.clip((dv - 0.04) / 0.16, 0.0, 1.0) * (0.35 + 1.15 * brush_crimson)
    gold_tail = tail_decay * (0.28 + 1.25 * brush_gold)

    # 上下边距平滑归零
    v_fade = np.clip(v / 0.055, 0.0, 1.0) * np.clip((1.0 - v) / 0.14, 0.0, 1.0)

    rgb = np.zeros((h, w, 3), dtype=np.float32)
    # 炽白核心刀刃
    rgb[..., 0] += (razor_white * 1.75 + gold_sheath * 1.25) * u_mask
    rgb[..., 1] += (razor_white * 1.55 + gold_sheath * 0.80) * u_mask
    rgb[..., 2] += (razor_white * 1.15 + gold_sheath * 0.24) * u_mask

    # 熔金烈焰流光带
    rgb[..., 0] += gold_tail * 1.25 * u_mask
    rgb[..., 1] += gold_tail * 0.56 * u_mask
    rgb[..., 2] += gold_tail * 0.10 * u_mask

    # 炽红焦焰拖尾
    rgb[..., 0] += crimson_tail * 1.30 * u_mask
    rgb[..., 1] += crimson_tail * 0.22 * u_mask
    rgb[..., 2] += crimson_tail * 0.04 * u_mask

    # 柔和光晕的高速金橙火花针
    rng = random.Random(523)
    for _ in range(36):
        su = rng.uniform(0.14, 0.90)
        sv = rng.uniform(0.20, 0.78)
        px0, py0 = su * w, sv * h
        slen = rng.uniform(32.0, 88.0)
        sw = rng.uniform(1.6, 3.0)
        ang = rng.uniform(-0.08, 0.10)
        x0, x1 = max(0, int(px0 - slen)), min(w, int(px0 + slen))
        y0, y1 = max(0, int(py0 - 18)), min(h, int(py0 + 18))
        if x1 <= x0 or y1 <= y0:
            continue
        ly, lx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
        dx, dy = lx - px0, ly - py0
        along = dx * math.cos(ang) + dy * math.sin(ang)
        perp = -dx * math.sin(ang) + dy * math.cos(ang)
        ts = np.clip(along / slen + 0.5, 0.0, 1.0)
        env = np.maximum(0.0, np.sin(ts * math.pi)) ** 1.15
        sp = np.exp(-((perp / np.maximum(sw * env, 0.5)) ** 2)) * env
        halo = np.exp(-((perp / np.maximum(sw * 2.8 * env, 1.6)) ** 2)) * env * 0.5
        rgb[y0:y1, x0:x1, 0] += sp * 1.35 + halo * 1.05
        rgb[y0:y1, x0:x1, 1] += sp * 0.92 + halo * 0.45
        rgb[y0:y1, x0:x1, 2] += sp * 0.36 + halo * 0.08

    rgb *= v_fade[..., None]
    rgb = add_hdr_bloom(rgb, [(4.0, 0.30), (11.5, 0.22), (24.0, 0.10)], threshold=0.24, wrap_x=False)
    rgb *= v_fade[..., None]
    return tonemap_additive(rgb, alpha_boost=1.20)


# ---------------------------------------------------------------------------
# 4. phainon_counter_edict_sigil.png (512x512) 纷争神纹护盾法环与 12 枚弧形棘轮
# ---------------------------------------------------------------------------
def generate_edict_sigil() -> Image.Image:
    size = 512
    cx = cy = (size - 1) * 0.5
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    nx = (xx - cx) / (size * 0.5)
    ny = (cy - yy) / (size * 0.5)
    r = np.hypot(nx, ny)
    theta = np.arctan2(ny, nx)

    rgb = np.zeros((size, size, 3), dtype=np.float32)

    # 双层威压金环 + 精细内冠环
    ring_outer = np.exp(-((r - 0.69) / 0.015) ** 2)
    ring_mid = np.exp(-((r - 0.57) / 0.019) ** 2)
    ring_inner = np.exp(-((r - 0.36) / 0.013) ** 2)
    ring_fine = np.exp(-((r - 0.465) / 0.007) ** 2) * (0.45 + 0.55 * (np.maximum(0.0, np.cos(theta * 12.0)) ** 2.0))

    # 外带 12 组纷争神纹双刻槽
    glyph_band = np.exp(-((r - 0.63) / 0.034) ** 2)
    glyph_pat = (np.maximum(0.0, np.cos(theta * 12.0)) ** 3.2) * 0.90 + (np.maximum(0.0, np.cos(theta * 24.0 + math.pi)) ** 7.0) * 0.60

    # 内冠 8 芒锋利金纹（替代单调灰雾，展现精致神圣几何）
    spoke8 = (np.maximum(0.0, np.cos(theta * 4.0)) ** 20.0 + np.maximum(0.0, np.sin(theta * 4.0)) ** 20.0)
    spoke_mask = np.clip((r - 0.14) / 0.06, 0.0, 1.0) * np.clip((0.56 - r) / 0.06, 0.0, 1.0)
    inner_lattice = spoke8 * spoke_mask * 0.85

    # 护盾穹面深绯红威压底韵（色相纯正偏深绯金，绝不发灰）
    aegis_crimson = np.exp(-((r - 0.48) / 0.18) ** 2) * 0.34 + np.exp(-((r - 0.63) / 0.10) ** 2) * 0.48

    # 中心四芒+八芒烈日神星
    star4 = (np.maximum(0.0, np.cos(theta * 2.0)) ** 12.0 + np.maximum(0.0, np.sin(theta * 2.0)) ** 12.0)
    star8 = (np.maximum(0.0, np.cos(theta * 4.0 + math.pi * 0.25)) ** 14.0)
    center_star = star4 * np.exp(-r / 0.26) * 1.25 + star8 * np.exp(-r / 0.18) * 0.72
    center_orb = np.exp(-((r / 0.088) ** 2)) * 1.45 + np.exp(-((r - 0.135) / 0.012) ** 2) * 0.92

    # 12 枚定向弯曲纷争棘刺（与 3D 模型 edict_strife_spurs 严格对齐）
    spur_mask = Image.new("L", (size, size), 0)
    spur_core_mask = Image.new("L", (size, size), 0)
    sd = ImageDraw.Draw(spur_mask)
    scd = ImageDraw.Draw(spur_core_mask)
    for k in range(12):
        base_ang = k * (math.tau / 12.0)
        is_major = (k % 3 == 0)
        r_b = 0.88 / 1.42
        r_m = (1.12 if is_major else 1.04) / 1.42
        r_t = (1.39 if is_major else 1.21) / 1.42
        hw = 0.105 if is_major else 0.072
        hook = 0.14 if is_major else 0.18

        def to_pt(ang_v: float, rad_v: float) -> Tuple[float, float]:
            return (cx + math.cos(ang_v) * rad_v * (size * 0.5),
                    cy - math.sin(ang_v) * rad_v * (size * 0.5))

        sd.polygon([
            to_pt(base_ang - hw, r_b),
            to_pt(base_ang + hook * 0.35, r_m),
            to_pt(base_ang + hook, r_t),
            to_pt(base_ang + hw, r_b),
        ], fill=255)
        scd.polygon([
            to_pt(base_ang - hw * 0.30, r_b + 0.02),
            to_pt(base_ang + hook * 0.42, r_m),
            to_pt(base_ang + hook, r_t - 0.008),
            to_pt(base_ang + hw * 0.30, r_b + 0.02),
        ], fill=255)

    spur_f = np.asarray(spur_mask.filter(ImageFilter.GaussianBlur(radius=1.0)), dtype=np.float32) / 255.0
    spur_c = np.asarray(spur_core_mask.filter(ImageFilter.GaussianBlur(radius=0.7)), dtype=np.float32) / 255.0
    spur_eroded = np.asarray(
        spur_mask.filter(ImageFilter.MinFilter(size=5)).filter(ImageFilter.GaussianBlur(radius=0.9)),
        dtype=np.float32,
    ) / 255.0
    spur_rim = np.clip(spur_f - spur_eroded, 0.0, 1.0)

    gold_rings = ring_outer * 1.22 + ring_mid * 0.96 + ring_inner * 0.92 + ring_fine * 0.78 + glyph_band * glyph_pat * 1.0 + inner_lattice
    rgb[..., 0] += gold_rings * 1.22 + aegis_crimson * 1.05 + spur_f * 0.55 + spur_rim * 1.32 + spur_c * 1.15 + (center_star + center_orb) * 1.20
    rgb[..., 1] += gold_rings * 0.84 + aegis_crimson * 0.16 + spur_f * 0.22 + spur_rim * 0.76 + spur_c * 0.96 + (center_star + center_orb) * 0.98
    rgb[..., 2] += gold_rings * 0.24 + aegis_crimson * 0.04 + spur_f * 0.05 + spur_rim * 0.22 + spur_c * 0.52 + center_orb * 0.78 + center_star * 0.46

    rgb = add_hdr_bloom(rgb, [(3.5, 0.30), (9.5, 0.20), (18.0, 0.10)], threshold=0.25, wrap_x=False)
    radial_fade = np.clip((0.99 - r) / 0.08, 0.0, 1.0)
    rgb *= radial_fade[..., None]
    return tonemap_additive(rgb, alpha_boost=1.22)


# ---------------------------------------------------------------------------
# 5. phainon_counter_crater_lava.png (1024x512) 水平无缝裂地岩浆喷涌贴图（纵向喷涌精修）
# ---------------------------------------------------------------------------
def generate_crater_lava() -> Image.Image:
    w, h = 1024, 512
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    u = xx / w
    v = yy / (h - 1)  # v=0 顶缘渐隐，v=1 地面火山爆心

    # 强纵向拉伸周期噪声（水平频率高、垂直频率低 -> 呈现暴烈冲天喷涌火焰柱！）
    n_col = periodic_fbm(h, w, base_freq_x=16, base_freq_y=3, octaves=5, seed=617)
    n_jet = periodic_fbm(h, w, base_freq_x=28, base_freq_y=4, octaves=4, seed=733)
    n_curl = periodic_fbm(h, w, base_freq_x=8, base_freq_y=5, octaves=4, seed=859)

    # 周期整数倍喷涌光柱（严格水平无缝）
    pillar_waves = (
        0.52 * (0.5 + 0.5 * np.sin((u * 7.0 + 0.14 * (n_curl - 0.5)) * math.tau))
        + 0.33 * (0.5 + 0.5 * np.sin((u * 14.0 - 0.18 * (n_col - 0.5)) * math.tau))
        + 0.15 * (0.5 + 0.5 * np.sin((u * 28.0 + 0.12 * (n_jet - 0.5)) * math.tau))
    )
    pillar_waves = np.maximum(0.0, pillar_waves) ** 1.35

    # 冲天锯齿烈焰轮廓（尖锐舌状火焰向上舔舐，顶缘彻底熄灭）
    tongue_cut = 0.08 + 0.36 * (1.0 - n_col) + 0.14 * (1.0 - pillar_waves)
    flame_mask = np.clip((v - tongue_cut) / 0.52, 0.0, 1.0)
    top_fade = np.clip(v / 0.09, 0.0, 1.0)

    # 垂直高压熔岩喷流丝（替代此前像等高线的环状轮廓）
    jet_streaks = (np.maximum(0.0, n_jet * 1.15 - 0.25) ** 1.85) * (0.55 + 0.75 * pillar_waves)
    # 地面爆心熔金高能带（控制亮度层次，保留炽金橙色相不死白）
    base_glow = np.clip((v - 0.52) / 0.48, 0.0, 1.0) ** 1.45
    white_veins = (np.maximum(0.0, n_jet - 0.58) / 0.42) ** 1.6 * np.clip((v - 0.38) / 0.62, 0.0, 1.0)

    crimson_body = (flame_mask ** 0.75) * (0.52 + 0.85 * n_col) * top_fade
    orange_jets = flame_mask * (0.35 + 1.40 * jet_streaks) * top_fade + base_glow * 0.75
    gold_core = (base_glow * (0.52 + 0.55 * pillar_waves) + white_veins * 1.25) * top_fade

    rgb = np.zeros((h, w, 3), dtype=np.float32)
    # 深烈红火山外焰
    rgb[..., 0] += crimson_body * 1.15
    rgb[..., 1] += crimson_body * 0.20
    rgb[..., 2] += crimson_body * 0.03

    # 炽橙熔金冲天流束
    rgb[..., 0] += orange_jets * 1.10
    rgb[..., 1] += orange_jets * 0.54
    rgb[..., 2] += orange_jets * 0.08

    # 爆心炽白金核（保留浓郁金黄饱和感）
    rgb[..., 0] += gold_core * 1.15
    rgb[..., 1] += gold_core * 0.90
    rgb[..., 2] += gold_core * 0.38

    # 水平环绕冲天火星针（严格 wrap_x 防边界断裂）
    rng = random.Random(947)
    for _ in range(52):
        cx0 = rng.uniform(0.0, w)
        cy0 = rng.uniform(h * 0.16, h * 0.88)
        slen = rng.uniform(22.0, 62.0)
        sw = rng.uniform(1.6, 3.0)
        for shift in (-w, 0, w):
            px0 = cx0 + shift
            x0, x1 = max(0, int(px0 - 16)), min(w, int(px0 + 16))
            y0, y1 = max(0, int(cy0 - slen)), min(h, int(cy0 + slen))
            if x1 <= x0 or y1 <= y0:
                continue
            ly, lx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
            dx, dy = lx - px0, ly - cy0
            ts = np.clip(dy / slen + 0.5, 0.0, 1.0)
            env = np.maximum(0.0, np.sin(ts * math.pi)) ** 1.1
            sp = np.exp(-((dx / np.maximum(sw * env, 0.5)) ** 2)) * env
            halo = np.exp(-((dx / np.maximum(sw * 2.8 * env, 1.5)) ** 2)) * env * 0.45
            rgb[y0:y1, x0:x1, 0] += sp * 1.30 + halo * 0.95
            rgb[y0:y1, x0:x1, 1] += sp * 0.84 + halo * 0.38
            rgb[y0:y1, x0:x1, 2] += sp * 0.28 + halo * 0.06

    rgb = add_hdr_bloom(rgb, [(4.0, 0.28), (11.0, 0.20), (22.0, 0.10)], threshold=0.28, wrap_x=True)
    rgb *= np.clip(v / 0.07, 0.0, 1.0)[..., None]
    return tonemap_additive(rgb, alpha_boost=1.16)


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    body_img, glow_img = generate_greatsword_pair()
    outputs = {
        "phainon_counter_greatsword_body.png": body_img,
        "phainon_counter_greatsword_glow.png": glow_img,
        "phainon_counter_cleave_flame.png": generate_cleave_flame(),
        "phainon_counter_edict_sigil.png": generate_edict_sigil(),
        "phainon_counter_crater_lava.png": generate_crater_lava(),
    }
    for filename, img in outputs.items():
        path = OUT_DIR / filename
        img.save(path)
        print(f"Saved {path} ({img.size[0]}x{img.size[1]}, {img.mode})")


if __name__ == "__main__":
    main()
