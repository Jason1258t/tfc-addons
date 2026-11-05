#!/usr/bin/env python3
"""Механическая подготовка игровых PNG из выбранных исходников ImageGen."""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ART = Path(__file__).resolve().parent
ITEMS = ("abs_sheet", "pet_film", "abs_keyboard_blank", "rgb_phosphor", "shadow_mask")


def main() -> None:
    previews = []
    for name in ITEMS:
        source = Image.open(ART / "src" / f"{name}.png").convert("RGBA")
        alpha = source.getchannel("A")
        bounds = alpha.getbbox()
        if bounds is None:
            raise ValueError(f"Пустой исходник: {name}")
        # Сохраняем исходные пропорции и небольшой прозрачный отступ.
        cropped = source.crop(bounds)
        cropped.thumbnail((60, 60), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        canvas.alpha_composite(cropped, ((64 - cropped.width) // 2, (64 - cropped.height) // 2))
        target = ROOT / "src" / "main" / "resources" / "assets" / "ee_extras" / "textures" / "item" / f"{name}.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        canvas.save(target)
        previews.append((name, canvas))

    sheet = Image.new("RGB", (5 * 320, 192), "#e5e1d8")
    draw = ImageDraw.Draw(sheet)
    for index, (name, image) in enumerate(previews):
        x = index * 320
        draw.rectangle((x + 160, 0, x + 319, 191), fill="#24262b")
        for bgx in (x, x + 160):
            for size, y in ((16, 16), (32, 64), (64, 112)):
                enlarged = image.resize((size, size), Image.Resampling.NEAREST)
                sheet.paste(enlarged, (bgx + 12, y), enlarged)
        draw.text((x + 12, 174), name, fill="#111111")
        draw.text((x + 172, 174), name, fill="#eeeeee")
    sheet.save(ART / "preview.png")


if __name__ == "__main__":
    main()
