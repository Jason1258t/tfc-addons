#!/usr/bin/env python3
"""Механически собирает референс-лист из уже осмотренных текстур MCP."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
names = ["basic_ic", "advanced_ic", "inductor", "computer_normal", "disk"]
canvas = Image.new("RGB", (1280, 288), "#d8d8d8")
draw = ImageDraw.Draw(canvas)
for index, name in enumerate(names):
    image = Image.open(ROOT / "references" / f"{name}.png").convert("RGBA")
    canvas.paste(image, (index * 256, 0), image)
    draw.text((index * 256 + 8, 264), name, fill="#202020")
canvas.save(ROOT / "references" / "pack-reference-sheet.png")
