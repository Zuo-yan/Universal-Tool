"""白厄「侵晨」普通战技「黎明创世，地辟天开」VFX 贴图后处理与生成脚本。

使用 uv + Pillow 执行：
    uv run --with pillow python tools/generate_phainon_skill_textures.py

输出 4 张贴图至 src/main/resources/assets/universal_tool/textures/misc/：
1. phainon_sunburst_sigil.png (512x512 RGBA): AI 关键帧风格金色太阳圣印后处理
2. phainon_slash_arc.png (1024x512 RGBA): AI 关键帧风格蓝金弯月剑气 UV 带后处理
3. phainon_ground_dust.png (512x256 RGBA): 贴地白色冲击气浪与金星划痕
4. phainon_debris_rock.png (64x64 RGBA): 3D 飞石玄武岩暗调贴图
"""

from __future__ import annotations

import math
import shutil
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
MISC_DIR = ROOT / "src/main/resources/assets/universal_tool/textures/misc"

SCRATCH_DIR = Path(
    r"C:\Users\lkuvi\.gemini\antigravity\brain\8dfe68d6-f8bb-4192-9dcc-242cd76d88c0\scratch"
)
SUNBURST_SRC = Path(
    r"C:\Users\lkuvi\.gemini\antigravity\brain\8dfe68d6-f8bb-4192-9dcc-242cd76d88c0\sunburst_raw_v2_1795504852159.jpg"
)
SLASH_SRC = Path(
    r"C:\Users\lkuvi\.gemini\antigravity\brain\8dfe68d6-f8bb-4192-9dcc-242cd76d88c0\slash_arc_raw_v3_1795504825567.jpg"
)


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    if edge1 == edge0:
        return 0.0
    t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)


def clamp_byte(v: float) -> int:
    return max(0, min(255, int(round(v))))


def process_sunburst_sigil() -> Path:
    """将生图太阳圣印压净黑底噪、转为 512x512 RGBA 并计算平滑发光 Alpha。"""
    SCRATCH_DIR.mkdir(parents=True, exist_ok=True)
    raw_png = SCRATCH_DIR / "sunburst_raw.png"
    with Image.open(SUNBURST_SRC) as src:
        rgb_img = src.convert("RGB")
        rgb_img.resize((1024, 1024), Image.Resampling.BICUBIC).save(raw_png)
        resized = rgb_img.resize((512, 512), Image.Resampling.LANCZOS)

    out = Image.new("RGBA", (512, 512))
    src_px = resized.load()
    dst_px = out.load()
    half = 255.5

    for y in range(512):
        ny = (y - half) / half
        for x in range(512):
            nx = (x - half) / half
            r_dist = math.hypot(nx, ny)
            radial_mask = 1.0 - smoothstep(0.92, 0.995, r_dist)
            if radial_mask <= 0.001:
                dst_px[x, y] = (0, 0, 0, 0)
                continue

            r, g, b = src_px[x, y]
            lum = max(r, g, b)
            if lum <= 12:
                dst_px[x, y] = (0, 0, 0, 0)
                continue

            # 去黑底微噪并提升高亮锐度
            strength = smoothstep(10.0, 220.0, float(lum))
            alpha = min(1.0, (lum / 235.0) ** 0.85) * radial_mask
            boost = 1.0 + 0.12 * strength
            dst_px[x, y] = (
                clamp_byte(r * boost * radial_mask),
                clamp_byte(g * boost * radial_mask),
                clamp_byte(b * boost * radial_mask),
                clamp_byte(alpha * 255.0),
            )

    MISC_DIR.mkdir(parents=True, exist_ok=True)
    out_path = MISC_DIR / "phainon_sunburst_sigil.png"
    out.save(out_path)
    return out_path


def process_slash_arc() -> Path:
    """将生图蓝金剑气带规范化为 1024x512 RGBA，压净黑底并羽化两端收束。"""
    SCRATCH_DIR.mkdir(parents=True, exist_ok=True)
    raw_png = SCRATCH_DIR / "slash_arc_raw.png"
    with Image.open(SLASH_SRC) as src:
        rgb_img = src.convert("RGB")
        w, h = rgb_img.size
        # 裁去顶部与底部多余的纯黑留白，聚焦剑气主带
        cropped = rgb_img.crop((0, int(h * 0.07), w, int(h * 0.87)))
        cropped.resize((1024, 512), Image.Resampling.BICUBIC).save(raw_png)
        resized = cropped.resize((1024, 512), Image.Resampling.LANCZOS)

    out = Image.new("RGBA", (1024, 512))
    src_px = resized.load()
    dst_px = out.load()

    for y in range(512):
        v = y / 511.0
        # 顶边留极窄防溢边羽化，底边柔和淡出
        v_mask = smoothstep(0.0, 0.025, v) * (1.0 - smoothstep(0.76, 0.99, v))
        for x in range(1024):
            u = x / 1023.0
            # 左端柔和起势，右端较宽羽化让能量波尾自然收束融入黑底
            u_mask = smoothstep(0.0, 0.09, u) * (1.0 - smoothstep(0.76, 0.99, u))
            mask = u_mask * v_mask
            if mask <= 0.001:
                dst_px[x, y] = (0, 0, 0, 0)
                continue

            r, g, b = src_px[x, y]
            lum = max(r, g, b)
            if lum <= 14:
                dst_px[x, y] = (0, 0, 0, 0)
                continue

            alpha = min(1.0, ((lum - 10.0) / 225.0) ** 0.82) * mask
            dst_px[x, y] = (
                clamp_byte(r * mask),
                clamp_byte(g * mask),
                clamp_byte(b * mask),
                clamp_byte(alpha * 255.0),
            )

    MISC_DIR.mkdir(parents=True, exist_ok=True)
    out_path = MISC_DIR / "phainon_slash_arc.png"
    out.save(out_path)
    return out_path


def generate_ground_dust() -> Path:
    """生成 512x256 贴地白色冲击气浪与细金划痕贴图。"""
    width, height = 512, 256
    out = Image.new("RGBA", (width, height))
    px = out.load()

    for y in range(height):
        v = y / (height - 1)
        # 主气浪集中在 v = 0.15 .. 0.85
        v_band = smoothstep(0.02, 0.25, v) * (1.0 - smoothstep(0.55, 0.96, v))
        for x in range(width):
            u = x / (width - 1)
            u_env = math.sin(u * math.pi) ** 0.65
            streak1 = 0.55 + 0.45 * math.sin(u * 26.0 * math.pi + v * 6.0)
            streak2 = 0.50 + 0.50 * math.cos(u * 42.0 * math.pi - v * 9.0)
            dust = v_band * (0.55 + 0.45 * streak1 * streak2) * u_env

            # 两道贴地炽金剑气划痕细线
            gold_scar1 = math.exp(-((v - 0.18 - 0.02 * math.sin(u * 8.0 * math.pi)) / 0.022) ** 2) * u_env
            gold_scar2 = math.exp(-((v - 0.34 + 0.015 * math.cos(u * 10.0 * math.pi)) / 0.016) ** 2) * u_env
            gold_mix = min(1.0, gold_scar1 * 0.95 + gold_scar2 * 0.75)

            r = 235.0 * (1.0 - gold_mix) + 255.0 * gold_mix
            g = 244.0 * (1.0 - gold_mix) + 216.0 * gold_mix
            b = 255.0 * (1.0 - gold_mix) + 110.0 * gold_mix
            alpha = min(1.0, dust * 0.78 + gold_mix * 0.95)

            px[x, y] = (
                clamp_byte(r),
                clamp_byte(g),
                clamp_byte(b),
                clamp_byte(alpha * 255.0),
            )

    out_path = MISC_DIR / "phainon_ground_dust.png"
    out.save(out_path)
    return out_path


def generate_debris_rock() -> Path:
    """生成 64x64 深玄武岩碎石暗色贴图（不透明实心，与蓝金波形成明暗反差）。"""
    size = 64
    out = Image.new("RGBA", (size, size))
    px = out.load()

    for y in range(size):
        for x in range(size):
            n1 = math.sin(x * 0.42 + y * 0.27)
            n2 = math.cos(x * 0.19 - y * 0.53)
            grain = 0.5 + 0.25 * n1 + 0.25 * n2
            ridge = 1.0 if abs(n1 + n2) < 0.25 else 0.0
            base = 28.0 + 26.0 * grain + 18.0 * ridge
            px[x, y] = (
                clamp_byte(base * 0.96),
                clamp_byte(base * 0.94),
                clamp_byte(base * 1.05),
                255,
            )

    out_path = MISC_DIR / "phainon_debris_rock.png"
    out.save(out_path)
    return out_path


def main() -> None:
    p1 = process_sunburst_sigil()
    p2 = process_slash_arc()
    p3 = generate_ground_dust()
    p4 = generate_debris_rock()
    for p in (p1, p2, p3, p4):
        print(f"Generated: {p} ({p.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
