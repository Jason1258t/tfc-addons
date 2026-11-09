"""Собирает модели Create 6.0.10 с палитрой воронёного железа."""
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile

import png

root = Path(__file__).resolve().parents[1]


def fill_strips(pixels):
    """Дорисовывает пустые клетки листа RECTANGLE: Create не рисует крышку бака шириной в один блок.

    Клетка (x, y) = x + 4·y: x — соседи слева/справа (0 нет, 1 справа, 2 оба, 3 слева),
    y — сверху/снизу (0 снизу, 1 оба, 2 сверху, 3 нет). Полоса без соседей по одной оси
    собирается из половинок крайних клеток рамки 3×3.
    """
    size = len(pixels) // 4

    def tile(x, y):
        return [row[x * size:(x + 1) * size] for row in pixels[y * size:(y + 1) * size]]

    def put(x, y, part):
        for j, row in enumerate(part):
            pixels[y * size + j][x * size:(x + 1) * size] = row

    def empty(x, y):
        return all(a == 0 for row in tile(x, y) for (_, _, _, a) in row)

    half = size // 2
    for y in range(3):
        if empty(0, y):
            put(0, y, [l[:half] + r[half:] for l, r in zip(tile(1, y), tile(3, y))])
    for x in range(1, 4):
        if empty(x, 3):
            put(x, 3, tile(x, 0)[:half] + tile(x, 2)[half:])

assets = root / 'src/main/resources/assets/better_fluids'
with ZipFile(root / 'libs/create-1.21.1-6.0.10.jar') as create, TemporaryDirectory() as temp:
    for name in create.namelist():
        path = name.removeprefix('assets/create/')
        if path == name or name.endswith('/'):
            continue
        if path.startswith(('models/block/fluid_tank/', 'models/item/fluid_tank.json', 'blockstates/fluid_tank.json')):
            target = assets / path.replace('blockstates/fluid_tank', 'blockstates/iron_tank').replace(
                'models/item/fluid_tank', 'models/item/iron_tank')
            target.parent.mkdir(parents=True, exist_ok=True)
            model = create.read(name).decode().replace('create:block/fluid_tank', 'better_fluids:block/fluid_tank')
            model = json.loads(model)
            if path.startswith('models/block/'):
                model = {
                    'parent': 'create:' + path.removeprefix('models/').removesuffix('.json'),
                    'textures': model['textures'],
                    'render_type': 'minecraft:cutout_mipped',
                }
            target.write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n')
        elif path.startswith('textures/block/fluid_tank'):
            target = assets / path
            target.parent.mkdir(parents=True, exist_ok=True)
            original = Path(temp) / 'original.png'
            original.write_bytes(create.read(name))
            pixels = png.read(original)
            for y, row in enumerate(pixels):
                for x, (r, g, b, a) in enumerate(row):
                    if r > g + 8 and r > b + 8:
                        value = (2 * r + 5 * g + b) // 8
                        row[x] = (value * 9 // 10, value, min(255, value + 12), a)
            if path.endswith('_connected.png'):
                fill_strips(pixels)
            png.write(target, pixels)

# Широкое окно: рамка окна Create по краям, середина растянута — стекло собирается в полосу любой ширины.
window = png.read(assets / 'textures/block/fluid_tank_window.png')
columns = [0, 1] + [3] * 12 + [6, 7]
rows = [0, 1] + [5] * 11 + [13, 14, 15]
png.write(assets / 'textures/block/fluid_tank_window_wide.png', [[window[y][x] for x in columns] for y in rows])
