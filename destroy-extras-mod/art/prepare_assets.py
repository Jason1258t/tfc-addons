from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / 'src/main/resources/assets/destroy_extras/textures'

source = Image.open(ROOT / 'art/membrane-source.png').convert('RGBA')
membrane = source.resize((32, 32), Image.Resampling.NEAREST)
membrane.save(TEXTURES / 'item/membrane.png')

PALETTE = {
    '.': (0, 0, 0, 0),
    'd': (40, 44, 47, 255),
    's': (83, 91, 94, 255),
    'l': (119, 130, 131, 255),
    'c': (177, 96, 66, 255),
    'b': (71, 107, 124, 255),
    'g': (119, 132, 78, 255),
    'w': (150, 159, 158, 255),
}

PATTERNS = {
    'block/membrane_electrolyzer_side': [
        'dddddddddddddddd',
        'dlllllllllllllld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlllllllllllllld',
        'dddddddddddddddd',
    ],
    'block/membrane_electrolyzer_top': [
        'dddddddddddddddd',
        'dlllllllllllllld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlsccccssccccsld'.replace('s', 's'),
        'dlsccccssccccsld',
        'dlsccccssccccsld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlssssssssssssld',
        'dlllllllllllllld',
        'dddddddddddddddd',
    ],
}

for name, rows in PATTERNS.items():
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            draw.point((x, y), fill=PALETTE[key])
    image.save(TEXTURES / f'{name}.png')

for name, color in [('hydrogen', 'b'), ('chlorine', 'g'), ('sodium_hydroxide', 'w')]:
    image = Image.open(TEXTURES / 'block/membrane_electrolyzer_side.png').convert('RGBA')
    draw = ImageDraw.Draw(image)
    draw.rectangle((4, 4, 11, 11), fill=PALETTE['d'])
    draw.rectangle((5, 5, 10, 10), fill=PALETTE[color])
    draw.rectangle((6, 6, 9, 9), fill=PALETTE['d'])
    image.save(TEXTURES / f'block/membrane_electrolyzer_{name}.png')

preview = Image.new('RGBA', (256, 64), (230, 230, 230, 255))
preview.alpha_composite(membrane.resize((64, 64), Image.Resampling.NEAREST), (32, 0))
preview.alpha_composite(membrane.resize((32, 32), Image.Resampling.NEAREST), (128, 16))
preview.save(ROOT / 'art/membrane-preview.png')
