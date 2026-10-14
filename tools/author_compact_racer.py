#!/usr/bin/env python3
"""Recreate the initial Compact Racer design. Normal edits belong in the .bbmodel."""
import base64
import json
import struct
import uuid
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SIZE = 128
PALETTE = [(244, 244, 240), (222, 226, 224), (46, 51, 54), (66, 71, 74),
           (27, 31, 34), (126, 136, 139), (191, 204, 206), (238, 241, 238)]


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'practicemod:compact_racer/' + name))


def texture():
    # Eight padded material tiles. Fine seams/tread are texture detail, not geometry.
    pixels = bytearray()
    for y in range(SIZE):
        pixels.append(0)
        for x in range(SIZE):
            tile = (y // 32) * 4 + x // 32
            r, g, b = PALETTE[tile % 8]
            xx, yy = x % 32, y % 32
            shade = 0
            if tile == 3:  # Saddle: small regular stitching, restrained cloth grain.
                shade = 9 if xx in (3, 28) and yy % 4 < 2 else ((x * 7 + y * 3) % 3 - 1)
            elif tile == 4:  # Rubber footbed ribs.
                shade = 10 if yy % 6 == 0 else 0
            elif tile == 5:
                shade = 4 if yy % 3 == 0 else -2
            elif tile in (0, 1, 7):
                shade = 1 if (x * 13 + y * 7) % 19 == 0 else 0
            pixels.extend([max(0, min(255, c + shade)) for c in (r, g, b)] + [255])

    def chunk(name, data):
        return struct.pack('>I', len(data)) + name + data + struct.pack('>I', zlib.crc32(name + data))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(pixels, 9)) + chunk(b'IEND', b''))


elements, groups, outliner = [], [], []


def group(name, origin=(0, 0, 0)):
    groups.append(dict(name=name, uuid=uid(name), origin=list(origin), rotation=[0, 0, 0],
                       export=True, visibility=True, isOpen=False, children=[]))
    outliner.append(dict(uuid=uid(name), isOpen=False, children=[]))


def cube(name, lo, hi, material=0, rotation=None, origin=None):
    tx, ty = (material % 4) * 32, (material // 4) * 32
    faces = {f: dict(uv=[tx + 2, ty + 2, tx + 30, ty + 30], texture=0)
             for f in ('north', 'east', 'south', 'west', 'up', 'down')}
    e = dict(name=name, uuid=uid(name), type='cube', from_=list(lo), to=list(hi),
             origin=list(origin or (0, 0, 0)), rotation=list(rotation or (0, 0, 0)),
             box_uv=False, autouv=0, color=material, export=True, visibility=True, faces=faces)
    e['from'] = e.pop('from_')
    elements.append(e)
    outliner[-1]['children'].append(e['uuid'])


def pair(name, x0, x1, y0, y1, z0, z1, material=0):
    cube(name + '_port', (-x1, y0, z0), (-x0, y1, z1), material)
    cube(name + '_starboard', (x0, y0, z0), (x1, y1, z1), material)


group('hull')
cube('keel', (-4.6, 0, -12), (4.6, 1.3, 11), 2)
cube('lower_chine', (-6.2, 1.3, -13.5), (6.2, 2.6, 12), 2)
cube('hull_belly', (-7.2, 2.6, -14), (7.2, 4, 10), 2)
cube('bow_chine', (-5.2, 2.6, 10), (5.2, 4, 14), 2)
cube('bow_chine_tip', (-3, 3.3, 14), (3, 4.4, 17), 2)
group('bumper')
cube('bumper_main', (-8, 4, -14.4), (8, 5.1, 9), 2)
cube('bumper_bow_step', (-6.5, 4, 9), (6.5, 5.1, 12), 2)
cube('bumper_bow_taper', (-4.8, 4, 12), (4.8, 5.1, 15), 2)
cube('bumper_nose', (-2.8, 4.2, 15), (2.8, 5.3, 17.5), 2)
group('white_shell')
cube('white_deck', (-7.8, 5.1, -14), (7.8, 6.3, 8.8))
cube('bow_shoulder', (-6.3, 5.1, 8.8), (6.3, 6.5, 11.8))
cube('bow_taper', (-4.6, 5.1, 11.8), (4.6, 6.2, 14.8))
cube('nose_cap', (-2.6, 5.3, 14.8), (2.6, 6.1, 17.2))
pair('side_rail', 6.65, 7.8, 6.3, 7.5, -12, 4.8)
pair('rear_shoulder', 4.5, 6.65, 6.3, 7.7, -11.8, -8.3)
pair('forward_shoulder', 4.2, 6.65, 6.3, 8.2, 2.3, 7.2)
cube('cowl_base', (-4.2, 6.3, 1), (4.2, 7.2, 9.7))
cube('cowl_mid', (-3.7, 7.2, 1.4), (3.7, 8.2, 6.5))
cube('cowl_top', (-3.1, 8.2, 1.8), (3.1, 9.5, 4.5))
cube('hood_slope', (-3.2, 8.8, 4.5), (3.2, 9.5, 14.8), 0, (18, 0, 0), (0, 9.5, 4.5))
cube('hood_stripe', (-.65, 9.51, 6), (.65, 9.58, 10.8), 2, (18, 0, 0), (0, 9.5, 4.5))
group('footwells')
pair('rubber_footbed', 3.25, 6.6, 6.3, 6.44, -11.5, 2.1, 4)
group('saddle')
cube('seat_pedestal', (-3.1, 6.3, -10.4), (3.1, 8.9, 1.1), 1)
cube('seat_lower', (-3.3, 8.8, -10.5), (3.3, 9.6, 1), 2)
cube('single_rider_cushion', (-3.2, 9.6, -8.1), (3.2, 10.56, .8), 3)
cube('seat_rear_rise', (-3.2, 9.6, -10.3), (3.2, 11.6, -8.1), 3)
cube('seat_rear_cap', (-2.8, 11.6, -10), (2.8, 12.1, -8.6), 3)
group('handlebars', (0, 10.2, 3.7))
cube('steering_column', (-1.25, 9.7, 3), (1.25, 14.1, 4.4), 2, (-15, 0, 0), (0, 9.7, 3.7))
cube('console', (-2.4, 12.4, 2.1), (2.4, 14, 4.5), 2)
cube('console_white_cap', (-2.45, 13.7, 2.1), (2.45, 14.2, 4.5), 0)
cube('instrument_screen', (-1.4, 14.21, 2.6), (1.4, 14.28, 3.8), 4)
cube('instrument_readout', (-.9, 14.29, 2.9), (.9, 14.34, 3.15), 6)
cube('bar_crossmember', (-5.4, 13.2, 2.7), (5.4, 14.25, 3.8), 5)
pair('rubber_grip', 4.3, 6.7, 13.05, 14.5, 2.5, 4, 4)
pair('grip_end', 6.7, 7, 13.05, 14.5, 2.5, 4, 2)
group('stern')
cube('rear_boarding_pad', (-5.8, 6.3, -13.8), (5.8, 6.46, -11.4), 4)
cube('rear_grab_bar', (-3.7, 7.2, -12.5), (3.7, 8, -11.7), 2)
pair('grab_bar_mount', 2.9, 3.7, 6.3, 7.2, -12.5, -11.7, 2)
cube('jet_nozzle_frame', (-1.9, 1.6, -15.2), (1.9, 3.8, -13.5), 5)
cube('jet_nozzle_mouth', (-1.3, 2.1, -15.24), (1.3, 3.3, -15.2), 4)

png = texture()
model = dict(meta=dict(format_version='5.0', model_format='free', box_uv=False),
             name='Compact Racer', model_identifier='compact_racer',
             resolution=dict(width=SIZE, height=SIZE), elements=elements, groups=groups,
             outliner=outliner, textures=[dict(name='compact_racer.png', id='0', uuid=uid('texture'),
             width=SIZE, height=SIZE, uv_width=SIZE, uv_height=SIZE, internal=True,
             source='data:image/png;base64,' + base64.b64encode(png).decode())])
path = ROOT / 'src/main/blockbench/compact_racer.bbmodel'
path.write_text(json.dumps(model, indent=2) + '\n')
print(f'Authored {len(elements)} cuboids in {len(groups)} groups: {path}')
