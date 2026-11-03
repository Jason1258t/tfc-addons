#!/usr/bin/env python3
"""Создаёт листы для визуальной проверки, не изменяя исходники."""
from pathlib import Path
from PIL import Image, ImageDraw
import sys

ROOT = Path(__file__).resolve().parent
folder = Path(sys.argv[1])
output = Path(sys.argv[2])
paths = sorted(folder.glob("*.png"))
cell = 220
columns = 5
rows = (len(paths) + columns - 1) // columns
sheet = Image.new("RGB", (columns * cell, rows * (cell + 24)), "#d8d8d8")
draw = ImageDraw.Draw(sheet)
for index, path in enumerate(paths):
    image = Image.open(path).convert("RGBA")
    image.thumbnail((200, 200), Image.Resampling.LANCZOS)
    x = (index % columns) * cell + (cell - image.width) // 2
    y = (index // columns) * (cell + 24) + (200 - image.height) // 2
    sheet.paste(image, (x, y), image)
    draw.text(((index % columns) * cell + 6, (index // columns) * (cell + 24) + 204), path.stem, fill="#181818")
sheet.save(output)
print(f"{len(paths)} images -> {output}")
