from pathlib import Path
import base64, json, uuid
root = Path('.')
texture = root / 'art/assets/counter_guard_shield_v2.png'
if not texture.exists():
    raise SystemExit(f'Missing texture: {texture}')
tex_data = base64.b64encode(texture.read_bytes()).decode('ascii')
elements = []
def cube(name, a, b, face_uv, origin=(0,32,0), rotation=(0,0,0)):
    item = {
        'name': name, 'box_uv': False, 'rescale': False, 'locked': False,
        'render_order': 'default', 'allow_mirror_modeling': True,
        'from': list(a), 'to': list(b), 'autouv': 0, 'color': 0,
        'origin': list(origin), 'rotation': list(rotation), 'export': True,
        'scope': 0, 'light_emission': 0, 'shade_direction_override': '',
        'uuid': str(uuid.uuid4()), 'type': 'cube', 'faces': {}
    }
    north, side, top = face_uv
    for face in ('north','south'):
        item['faces'][face] = {'uv': list(north), 'texture': 0}
    for face in ('east','west'):
        item['faces'][face] = {'uv': list(side), 'texture': 0}
    for face in ('up','down'):
        item['faces'][face] = {'uv': list(top), 'texture': 0}
    elements.append(item)
    return item['uuid']
def uv_face(): return ([0,0,32,32], [32,0,48,16], [48,0,64,16])
def uv_side(): return ([32,0,48,16], [32,0,48,16], [48,0,64,16])
def uv_emblem(): return ([0,32,32,64], [32,16,48,32], [48,16,64,32])
# Rear shell and front layers. Z increases toward the viewed/front face.
body = []
body.append(cube('diamond_frame', (-15,17,0), (15,47,2), uv_face(), rotation=(0,0,45)))
body.append(cube('diamond_back', (-15,21,0), (15,43,2), uv_face()))
body.append(cube('diamond_plate', (-9.9,21.9,2), (9.9,42.1,4), uv_face(), rotation=(0,0,45)))
for i in range(4):
    off=(i-1.5)*2
    body.append(cube(f'cardinal_plate_{i}', (-6+off,18.8,2.7), (6+off,20.7,4.0), uv_face()))
    body.append(cube(f'cardinal_bottom_{i}', (-6+off,43.3,2.7), (6+off,45.2,4.0), uv_face()))
body.append(cube('crimson_inner', (-7.2,24.6,4), (7.2,39.4,6), uv_emblem(), rotation=(0,0,45)))
body.append(cube('rune_plate', (-4.5,27.3,6), (4.5,36.7,8), uv_emblem(), rotation=(0,0,45)))
body.append(cube('center_core', (-3,29,8), (3,35,10), uv_emblem()))
# Four cardinal wings and their gemstone caps.
wings=[]
wings.append(cube('top_wing', (-2,46,2), (2,55,5), uv_side()))
wings.append(cube('bottom_wing', (-2,9,2), (2,18,5), uv_side()))
wings.append(cube('left_wing', (-21,30,2), (-12,34,5), uv_side()))
wings.append(cube('right_wing', (12,30,2), (21,34,5), uv_side()))
wings.append(cube('top_ward', (-4,52,3), (4,58,7), uv_face(), rotation=(0,0,45)))
wings.append(cube('upper_left_ward', (-15,40,3), (-10,45,6), uv_face(), rotation=(0,0,45)))
wings.append(cube('upper_right_ward', (10,40,3), (15,45,6), uv_face(), rotation=(0,0,45)))
wings.append(cube('lower_left_ward', (-15,19,3), (-10,24,6), uv_face(), rotation=(0,0,45)))
wings.append(cube('lower_right_ward', (10,19,3), (15,24,6), uv_face(), rotation=(0,0,45)))
wings.append(cube('bottom_ward', (-4,6,3), (4,12,7), uv_face(), rotation=(0,0,45)))
wings.append(cube('left_ward', (-24,28,3), (-18,36,7), uv_face(), rotation=(0,0,45)))
wings.append(cube('right_ward', (18,28,3), (24,36,7), uv_face(), rotation=(0,0,45)))
# Reflected arrows, central emblem layers, and little cardinal corner ornaments.
detail=[]
detail.append(cube('reflect_left', (-11,31,8), (-3,33,10), uv_emblem()))
detail.append(cube('reflect_right', (3,31,8), (11,33,10), uv_emblem()))
detail.append(cube('top_spike', (-1,56,3), (1,61,6), uv_side()))
detail.append(cube('bottom_spike', (-1,3,3), (1,8,6), uv_side()))
detail.append(cube('left_spike', (-27,31,3), (-24,33,6), uv_side()))
detail.append(cube('right_spike', (24,31,3), (27,33,6), uv_side()))
def group(name, children, parent='root'):
    return {'name': name, 'uuid': str(uuid.uuid4()), 'origin': [0,32,0],
            'color': 0, 'export': True, 'mirror_uv': False,
            'visibility': True, 'locked': False, 'shade': True,
            'children': children, 'resolution': [64,64], 'parent': parent}
rig_uuid = str(uuid.uuid4())
groups = [group('shield_core', body + detail, rig_uuid), group('shield_wings', wings, rig_uuid), group('shield_rig', [rig_uuid])]
# Simple nested group: fix parent links; outliner contains top group only.
groups[0]['parent'] = rig_uuid
groups[1]['parent'] = rig_uuid
groups[2]['children'] = [rig_uuid]
model = {
  'meta': {'format_version':'5.0','model_format':'java_block','box_uv':False},
  'name':'counter_guard_shield_v2','parent':'','java_block_version':'1.21.1',
  'ambientocclusion':True,'front_gui_light':False,'visible_box':[1,1,0],
  'resolution':{'width':64,'height':64},'elements':elements,'groups':groups,
  'outliner':[groups[2]['uuid']],
  'textures':[{'name':'shield_atlas','path':'','folder':'','namespace':'',
      'id':'0','width':64,'height':64,'uv_width':64,'uv_height':64,
      'particle':False,'use_as_default':True,'source':'data:image/png;base64,'+tex_data,
      'internal':True,'visible':True,'saved':False,'render_mode':'default',
      'render_sides':'auto','wrap_mode':'limited','pbr_channel':'color',
      'uuid':str(uuid.uuid4())}],
  'variable_placeholders':'','variable_placeholder_buttons':[],
  'timeline_setups':[],'unhandled_root_fields':{},'ai_used':True,
  'ai_agents':'Codex + Blockbench MCP'
}
out = root / 'art/assets/counter_guard_shield_v2.bbmodel'
out.write_text(json.dumps(model, ensure_ascii=False, indent=2), encoding='utf-8')
print(out, len(elements), len(groups), out.stat().st_size)
