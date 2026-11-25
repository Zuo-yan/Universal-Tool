"""Bake tileable basalt/fissure and flame-strip maps for the meteor meshes."""
from math import cos, exp, pi, sin
from pathlib import Path
from random import Random

from PIL import Image

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/universal_tool/textures/misc"
rng = Random(419)
cells = [(i + rng.uniform(.12, .88), j + rng.uniform(.12, .88))
         for i in range(10) for j in range(7)]


def surface(u, v):
    x = 10 * u + .18 * sin(8 * pi * u + v * 13) + .12 * sin(v * 37)
    y = 7 * v + .15 * sin(u * 12 * pi + v * 5)
    distances = sorted(((x - cx + 5) % 10 - 5) ** 2 + ((y - cy + 3.5) % 7 - 3.5) ** 2
                       for cx, cy in cells)
    edge = distances[1] - distances[0]
    ember = exp(-edge * 7)
    vein = exp(-edge * 28)
    grain = .5 + .5 * sin(x * 17 + y * 21 + 2 * cos(x * 9))
    return (37 + 35 * grain + 177 * ember, 15 + 14 * grain + 130 * ember + 85 * vein,
            20 + 8 * grain + 116 * vein)


def plume(u, v):
    a = u * 2 * pi
    fold = .49 + .12 * sin(3 * a + 1.2 * sin(2 * a)) + .055 * cos(9 * a)
    d = abs(v - fold)
    edge = exp(-d * 28)
    smoke = exp(-d * 7) * (.69 + .21 * sin(5 * a + v * 8) + .1 * cos(14 * a - v * 13))
    return (255, 87 + 159 * edge, 20 + 176 * edge, 250 * (edge * .43 + smoke * .57)
            * min(1, 16 * v, 16 * (1 - v)))


for name, painter, mode in [('crust', surface, 'RGB'), ('flame', plume, 'RGBA')]:
    w, h = 256, 128
    image = Image.new(mode, (w, h))
    image.putdata([tuple(max(0, min(255, round(c))) for c in painter(x / w, y / (h - 1)))
                   for y in range(h) for x in range(w)])
    image.save(OUT / f'phainon_meteor_{name}.png')
