"""Author the Bloodthorn cut and fracture meshes and their shared runtime textures."""

import base64
import io
import json
import math
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

import numpy as np
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/universal_tool'
ATLAS_SIZE = 512


def cut_texture():
    y, x = np.mgrid[0:128, 0:512] / np.array([[[127]], [[511]]])
    taper = np.sin(np.pi * x).clip(0, 1) ** 0.72
    grain = (np.sin(x * 218 + np.sin(x * 36) * 2.0) + np.sin(x * 73 + 1.6)) * 0.5
    center = 0.44 + 0.021 * grain + 0.045 * (x - 0.5)
    offset = y - center
    glow = np.exp(-abs(offset) / (0.13 * taper + 0.012))
    line = np.exp(-(offset / (0.024 * taper + 0.004)) ** 2)
    sparks = np.exp(-((y - center - 0.105 - 0.015 * grain) / 0.012) ** 2)
    sparks *= np.maximum(0, np.sin(x * 39 + 2.0)) ** 2
    alpha = taper * np.maximum(glow * 0.63, line * 0.99)
    alpha = np.maximum(alpha, sparks * taper * 0.48) * np.sin(np.pi * y).clip(0, 1) ** 0.6
    green = 28 + 218 * line + 49 * sparks
    blue = 38 + 174 * line + 45 * sparks
    return Image.fromarray(np.dstack([np.full_like(alpha, 255), green.clip(0, 255), blue.clip(0, 255),
                                      alpha.clip(0, 1) * 255]).astype('uint8'))


def shard_textures():
    y, x = np.mgrid[0:128, 0:128]
    rim = np.minimum.reduce([x, y, 127 - x, 127 - y])
    shine = np.exp(-((rim - 4) / 2.3) ** 2)
    glow = np.exp(-rim / 8.0)
    body = Image.fromarray(np.dstack([27 + 72 * glow + (x > y + 48) * 13,
                                     5 + 11 * glow, 19 + 28 * glow]).astype('uint8')).convert('RGBA')
    rim_texture = Image.fromarray(np.dstack([np.full_like(rim, 255), 25 + 160 * shine, 38 + 122 * shine,
                                           255 * np.maximum(0.86 * shine, 0.56 * glow)]).astype('uint8'))
    draw = ImageDraw.Draw(rim_texture)
    draw.line([(13, 47), (13, 96), (22, 96)], fill=(255, 49, 59, 112), width=2)
    draw.line([(104, 21), (113, 21), (113, 42)], fill=(255, 117, 82, 158), width=3)
    return body, rim_texture


def cut_mesh():
    points = []
    faces = []
    for i in range(15):
        u = i / 14
        x = 2 * u - 1
        width = 0.016 + 0.24 * math.sin(math.pi * u) ** 0.86
        center_y = 0.08 * x * x + 0.021 * math.sin(i * 2.7) - 0.025 * x
        center_z = 0.15 * (1 - x * x)
        points.extend([(x, center_y - width, center_z - 0.06 * (1 - abs(x))),
                       (x, center_y, center_z + 0.035),
                       (x, center_y + width, center_z - 0.09),
                       (x, center_y, center_z - 0.20 * (1 - abs(x)))])
    # The cut has both a bevel and a tail in depth, so it reads from the side.
    for i in range(14):
        for a, b, va, vb in [(0, 1, 1.0, 0.44), (1, 2, 0.44, 0.0),
                             (2, 3, 0.0, 0.63), (3, 0, 0.63, 1.0)]:
            u, next_u = i / 14, (i + 1) / 14
            faces.append(([i * 4 + a, (i + 1) * 4 + a, (i + 1) * 4 + b, i * 4 + b],
                          [(u, va), (next_u, va), (next_u, vb), (u, vb)]))
    return points, faces


def fracture_mesh():
    outline = [(-0.5, -0.45), (-0.38, -0.5), (0.42, -0.5), (0.5, -0.39),
               (0.5, 0.41), (0.38, 0.5), (-0.43, 0.5), (-0.5, 0.36)]
    points = [(x * scale, y * scale, z) for z, scale in [(-0.5, 0.90), (0.5, 1.0)] for x, y in outline]
    faces = []
    for i in range(8):
        j = (i + 1) % 8
        faces.append(([i, j, j + 8, i + 8], [(0, 1), (1, 1), (1, 0), (0, 0)]))
    face_uv = [(x + 0.5, 0.5 - y) for x, y in outline]
    for ring in [0, 8]:
        for i in range(1, 7):
            corners = [0, i, i + 1] if ring == 8 else [0, i + 1, i]
            faces.append(([ring + v for v in corners], [face_uv[v] for v in corners]))
    return points, faces


def identifier(name):
    return str(uuid5(NAMESPACE_URL, 'universal-tool:bloodthorn/' + name))


def export_mesh(name, mesh, atlas_uv, texture):
    vertices, faces = mesh
    obj = ['# Bloodthorn VFX, same coordinates and UVs as the Blockbench source.']
    obj.extend('v {:.6f} {:.6f} {:.6f}'.format(*v) for v in vertices)
    editor_faces = {}
    uv_index = 1
    for face_index, (corners, uvs) in enumerate(faces):
        a, b, c = (np.array(vertices[index]) for index in corners[:3])
        normal = np.cross(b - a, c - a)
        normal /= np.linalg.norm(normal)
        obj.append('vn {:.6f} {:.6f} {:.6f}'.format(*normal))
        for u, v in uvs:
            obj.append(f'vt {u:.6f} {1 - v:.6f}')
        obj.append('f ' + ' '.join(f'{index + 1}/{uv_index + i}/{face_index + 1}' for i, index in enumerate(corners)))
        uv_index += len(corners)
        keys = [f'v{index}' for index in corners]
        editor_faces[f'f{face_index}'] = {'vertices': keys, 'texture': texture,
                                          'uv': dict(zip(keys, map(atlas_uv, uvs)))}
    (ASSETS / 'models/vfx' / f'phainon_bloodthorn_{name}.obj').write_text('\n'.join(obj) + '\n', encoding='utf-8')
    return {'type': 'mesh', 'name': name, 'uuid': identifier(name), 'origin': [0, 0, 0], 'rotation': [0, 0, 0],
            'vertices': {f'v{i}': [16 * x for x in vertex] for i, vertex in enumerate(vertices)},
            'faces': editor_faces}


def main():
    cut = cut_texture()
    body, rim = shard_textures()
    for key, image in [('cut', cut), ('shard_body', body), ('shard_rim', rim)]:
        image.save(ASSETS / 'textures/misc' / f'phainon_bloodthorn_{key}.png', optimize=True)
    atlas = Image.new('RGBA', (ATLAS_SIZE, 256))
    atlas.paste(cut, (0, 0))
    atlas.paste(Image.alpha_composite(body, rim), (0, 128))
    cut_part = export_mesh('cut', cut_mesh(), lambda uv: [uv[0] * 511, uv[1] * 127], 0)
    fracture = export_mesh('fracture', fracture_mesh(), lambda uv: [uv[0] * 127, 128 + uv[1] * 127], 0)
    with io.BytesIO() as png:
        atlas.save(png, format='PNG')
        embedded = 'data:image/png;base64,' + base64.b64encode(png.getvalue()).decode()
    project = {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False},
               'name': 'Bloodthorn cut and fracture', 'resolution': {'width': ATLAS_SIZE, 'height': 256},
               'elements': [cut_part, fracture], 'outliner': [cut_part['uuid'], fracture['uuid']],
               'textures': [{'id': '0', 'uuid': identifier('atlas'), 'name': 'bloodthorn_atlas.png', 'mode': 'bitmap',
                             'width': ATLAS_SIZE, 'height': 256, 'saved': True, 'source': embedded}]}
    (ROOT / 'art/assets/phainon_bloodthorn_vfx.bbmodel').write_text(json.dumps(project, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
