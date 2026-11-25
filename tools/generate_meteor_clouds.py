"""Bake the turbulent underside and incandescent edges of the meteor cloud funnel."""
from pathlib import Path

import numpy as np
from PIL import Image


def noise(x, y, frequency, seed):
    rng = np.random.default_rng(seed)
    values = rng.random((128, 128))
    x, y = x * frequency, y * frequency
    ix, iy = np.floor(x).astype(int), np.floor(y).astype(int)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = values[iy % 128, ix % 128] * (1 - fx) + values[iy % 128, (ix + 1) % 128] * fx
    b = values[(iy + 1) % 128, ix % 128] * (1 - fx) + values[(iy + 1) % 128, (ix + 1) % 128] * fx
    return a * (1 - fy) + b * fy


def fbm(x, y):
    return sum(noise(x, y, 3 * 2 ** i, i + 34) * .52 ** i for i in range(6)) / 2.04


size = 768
y, x = np.mgrid[-1:1:complex(size), -1:1:complex(size)]
r = np.hypot(x, y)
theta = np.arctan2(y, x)
twist = theta + 3.8 * r + .8 * fbm(x, y)
u, v = r * np.cos(twist), r * np.sin(twist)
billow = fbm(u * 1.8 + fbm(v, u), v * 1.8 - fbm(u, v))
fold = r + .20 * (billow - .5) + .025 * np.sin(4 * theta + 7 * r)
lip = np.exp(-abs(fold - .17) * 43)
rim = np.exp(-abs(np.sin(16 * fold + 1.4 * np.sin(3 * twist) + 5 * billow)) * 7)
hot = np.clip(lip + (.9 - .6 * r) * rim + np.maximum(.58 - billow, 0) * 1.7, 0, 1)
dense = np.clip(2 * billow - .46, 0, 1)
rgb = np.stack((32 + 64 * dense + 165 * hot, 20 + 24 * dense + 118 * hot ** 1.8,
                49 + 45 * dense + 39 * hot ** 3), axis=-1)
alpha = np.clip((.99 - fold) * 8, 0, 1) * np.clip((fold - .071) * 21, 0, 1) * (.74 + .26 * dense)
rgba = np.dstack((np.clip(rgb, 0, 255), 255 * alpha)).astype(np.uint8)
out = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/universal_tool/textures/misc'
Image.fromarray(rgba).save(out / 'phainon_meteor_clouds.png')
