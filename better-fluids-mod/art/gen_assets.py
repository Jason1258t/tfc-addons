"""Собирает модели Create 6.0.10 с палитрой воронёного железа."""
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile

import png

root = Path(__file__).resolve().parents[1]
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
            png.write(target, pixels)
