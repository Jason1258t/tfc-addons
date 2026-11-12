#!/usr/bin/env python3
"""Временная модель собранного электролизёра (OBJ + MTL) из готовых текстур Create, IE и ванилы.

Координаты: 1 единица = 1 блок, начало — угол мастер-клетки (ячейка, центр переднего ряда), лицом к +Z.
Габарит X −1…2, Y −1…2, Z −1…1. Чистовую модель делает художник по art/electrolyzer-model-prompt.md.
"""
import math
import pathlib
import sys

MATERIALS = {
    'casing': 'create:block/copper_casing',
    'steel': 'immersiveengineering:block/metal/sheetmetal_steel',
    'tank': 'create:block/fluid_tank',
    'tank_top': 'create:block/fluid_tank_top',
    'cell': 'destroy_extras:block/membrane_electrolyzer_front',
    'cathode': 'minecraft:block/light_blue_concrete',
    'anode': 'minecraft:block/lime_concrete',
    'catholyte': 'minecraft:block/purple_concrete',
    'inlet': 'minecraft:block/blue_concrete',
    'insulator': 'minecraft:block/white_concrete',
    'contact': 'minecraft:block/copper_block',
    'plus': 'minecraft:block/red_concrete',
    'minus': 'minecraft:block/black_concrete',
}

verts, uvs, faces = [], [], []


def quad(points, mat, axis_u, axis_v):
    """Грань из 4 точек; UV — дробная часть координат внутри клетки (16 текселей на блок)."""
    cell = [min(p[i] for p in points) for i in range(3)]
    base = [math.floor(c + 1e-6) for c in cell]
    idx = []
    for p in points:
        verts.append(p)
        u = p[axis_u] - base[axis_u]
        v = p[axis_v] - base[axis_v]
        uvs.append((u, v))
        idx.append(len(verts))
    faces.append((mat, idx))


def box(x0, y0, z0, x1, y1, z1, mat, top=None, bottom=None, front=None):
    top = top or mat
    bottom = bottom or mat
    front = front or mat
    # Обход против часовой стрелки при взгляде снаружи.
    quad([(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], top, 0, 2)
    quad([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], bottom, 0, 2)
    quad([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], front, 0, 1)
    quad([(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)], mat, 0, 1)
    quad([(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], mat, 2, 1)
    quad([(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)], mat, 2, 1)


def cell(tx, ty, tz):
    """Угол клетки шаблона (x 0 — слева, z 1 — передний ряд) в координатах модели."""
    return tx - 1, ty - 1, tz - 1


E = 1 / 16
# Средний столбец: медный корпус, ячейка, клеммная коробка.
for ty in range(3):
    for tz in range(2):
        x, y, z = cell(1, ty, tz)
        if (ty, tz) == (1, 1):
            box(x, y, z, x + 1, y + 1, z + 1, 'casing', front='cell')
        elif (ty, tz) == (2, 0):
            box(x, y, z, x + 1, y + 0.625, z + 1, 'steel')
            for cx, mark in ((0.3, 'plus'), (0.7, 'minus')):
                box(x + cx - 0.08, y + 0.625, z + 0.42, x + cx + 0.08, y + 0.85, z + 0.58, 'insulator')
                box(x + cx - 0.05, y + 0.85, z + 0.45, x + cx + 0.05, y + 0.9, z + 0.55, 'contact')
                box(x + cx - 0.12, y + 0.625, z + 0.66, x + cx + 0.12, y + 0.625 + E / 4, z + 0.8, mark)
        else:
            box(x, y, z, x + 1, y + 1, z + 1, 'casing')
# Баки по бокам (ярусы 0–1), насос сзади справа внизу.
for tx in (0, 2):
    for ty in (0, 1):
        for tz in (0, 1):
            x, y, z = cell(tx, ty, tz)
            if (tx, ty, tz) == (2, 0, 0):
                box(x, y, z, x + 1 - E, y + 1, z + 1, 'casing', top='steel')
                box(x + 1 - E, y + 0.25, z + 0.25, x + 1, y + 0.75, z + 0.75, 'inlet')
            elif (tx, ty, tz) == (0, 0, 1):
                box(x + E, y, z, x + 1, y + 1, z + 1, 'tank', top='tank_top', bottom='tank_top')
                box(x, y + 0.25, z + 0.25, x + E, y + 0.75, z + 0.75, 'catholyte')
            else:
                box(x, y, z, x + 1, y + 1, z + 1, 'tank', top='tank_top', bottom='tank_top')
# Верхний ярус по бокам: газовые коллекторы; передние — выходы газов вверх.
for tx in (0, 2):
    for tz in (0, 1):
        x, y, z = cell(tx, 2, tz)
        box(x, y, z, x + 1, y + 0.5, z + 1, 'steel')
        box(x + 0.3125, y + 0.5, z + 0.3125, x + 0.6875, y + 0.875, z + 0.6875, 'steel')
        port = {(0, 1): 'cathode', (2, 1): 'anode'}.get((tx, tz), 'steel')
        box(x + 0.25, y + 0.875, z + 0.25, x + 0.75, y + 1, z + 0.75, port)
        if port != 'steel':
            box(x + 0.3, y + 0.6, z + 0.6875, x + 0.7, y + 0.75, z + 0.6875 + E / 4, port)

out = pathlib.Path(sys.argv[1])
name = out.stem
with open(out, 'w') as f:
    f.write(f'# Временная модель: art/gen_electrolyzer_obj.py\nmtllib {name}.mtl\no {name}\n')
    for v in verts:
        f.write('v %.5f %.5f %.5f\n' % v)
    for u, v in uvs:
        f.write('vt %.5f %.5f\n' % (u, v))
    current = None
    for mat, idx in faces:
        if mat != current:
            f.write(f'usemtl {mat}\n')
            current = mat
        f.write('f ' + ' '.join(f'{i}/{i}' for i in idx) + '\n')
with open(out.with_suffix('.mtl'), 'w') as f:
    for mat, tex in MATERIALS.items():
        f.write(f'newmtl {mat}\nmap_Kd {tex}\n\n')
print(len(faces), 'граней')
