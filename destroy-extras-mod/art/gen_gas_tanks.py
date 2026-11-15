#!/usr/bin/env python3
"""Собрать шаблоны IE и простые модели газовых баков."""
import gzip
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/destroy_extras"
DATA = ROOT / "data/destroy_extras"
SIZES = {
    "compact": (1, 2, "Компактный", "Compact"),
    "medium": (2, 3, "Средний", "Medium"),
    "large": (3, 5, "Большой", "Large"),
}
MATERIALS = {
    "steel": ("immersiveengineering:sheetmetal_steel", "tfc", "сталь", "Steel"),
    "stainless_steel": ("firmalife:metal/block/stainless_steel", "firmalife", "нержавеющая сталь", "Stainless Steel"),
    "blue_steel": ("tfc:metal/block/blue_steel", "tfc", "синяя сталь", "Blue Steel"),
}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def named(kind, name, data):
    return bytes([kind]) + string(name) + data


def string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def integer(value):
    return struct.pack(">i", value)


def nbt_list(kind, items):
    return bytes([kind]) + integer(len(items)) + b"".join(items)


def compound(*tags):
    return b"".join(tags) + b"\0"


def template(width, height, controller, hull):
    control_pos = ((width - 1) // 2, 0, width - 1)
    cells = []
    for y in range(height):
        for z in range(width):
            for x in range(width):
                pos = (x, y, z)
                interior = 0 < y < height - 1 and 0 < x < width - 1 and 0 < z < width - 1
                state = 0 if pos == control_pos else 2 if interior else 1
                cells.append(compound(
                    named(9, "pos", nbt_list(3, list(map(integer, pos)))),
                    named(3, "state", integer(state)),
                ))
    palette = [compound(named(8, "Name", string(block))) for block in (
        f"destroy_extras:{controller}", hull, "minecraft:air"
    )]
    data = compound(
        named(3, "DataVersion", integer(3955)),
        named(9, "size", nbt_list(3, [integer(width), integer(height), integer(width)])),
        named(9, "palette", nbt_list(10, palette)),
        named(9, "blocks", nbt_list(10, cells)),
        named(9, "entities", nbt_list(10, [])),
    )
    return gzip.compress(named(10, "", data), mtime=0)


def model(model_id, textures, parent="minecraft:block/cube_all"):
    dump(ASSETS / f"models/block/{model_id}.json", {"parent": parent, "textures": textures})


def block_assets(block_id, variants, item_model):
    dump(ASSETS / f"blockstates/{block_id}.json", {"variants": variants})
    dump(ASSETS / f"models/item/{block_id}.json", {"parent": f"destroy_extras:block/{item_model}"})


def pipe_assets(tag, lang_ru, lang_en):
    rotations = {
        "north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270},
        "up": {"x": 270}, "down": {"x": 90},
    }

    def element(start, end, texture):
        return {"from": start, "to": end, "faces": {
            side: {"texture": texture} for side in rotations
        }}

    for gas, color, ru, en in (
        ("hydrogen", "light_blue", "Водородная трубка", "Hydrogen Pipe"),
        ("chlorine", "yellow", "Хлорная трубка", "Chlorine Pipe"),
    ):
        block_id = f"{gas}_pipe"
        center = f"destroy_extras:block/gas_pipe/{gas}_center"
        arm = f"destroy_extras:block/gas_pipe/{gas}_arm"
        textures = {
            "particle": "tfc:block/metal/block/steel",
            "metal": "tfc:block/metal/block/steel",
            "stripe": f"minecraft:block/{color}_concrete",
        }
        hub = element([5.5, 5.5, 5.5], [10.5, 10.5, 10.5], "#stripe")
        run = element([6.5, 6.5, 0], [9.5, 9.5, 5.5], "#metal")
        tail = element([6.5, 6.5, 10.5], [9.5, 9.5, 16], "#metal")
        for suffix, elements in (("center", [hub]), ("arm", [run]), ("item", [run, hub, tail])):
            dump(ASSETS / f"models/block/gas_pipe/{gas}_{suffix}.json", {
                "parent": "minecraft:block/block", "textures": textures, "elements": elements,
            })
        parts = [{"apply": {"model": center}}]
        for side, rotation in rotations.items():
            parts.append({"when": {side: "true"}, "apply": {"model": arm, **rotation}})
        dump(ASSETS / f"blockstates/{block_id}.json", {"multipart": parts})
        dump(ASSETS / f"models/item/{block_id}.json", {"parent": f"destroy_extras:block/gas_pipe/{gas}_item"})
        dump(DATA / f"loot_table/blocks/{block_id}.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"destroy_extras:{block_id}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
        key = f"destroy_extras:{block_id}"
        if key not in tag["values"]:
            tag["values"].append(key)
        lang_ru[f"block.destroy_extras.{block_id}"] = ru
        lang_en[f"block.destroy_extras.{block_id}"] = en


def main():
    ru = ASSETS / "lang/ru_ru.json"
    en = ASSETS / "lang/en_us.json"
    lang_ru = json.loads(ru.read_text())
    lang_en = json.loads(en.read_text())
    tag_path = ROOT / "data/minecraft/tags/block/mineable/pickaxe.json"
    tag = json.loads(tag_path.read_text())
    common = "gas_tank_controller"
    model(common, {"all": "minecraft:block/smithing_table_side"})

    for material, (_, namespace, _, _) in MATERIALS.items():
        texture = f"{namespace}:block/metal/block/{material}"
        model(f"gas_tank/{material}", {"all": texture})
        model(f"gas_tank/{material}_inlet", {
            "side": texture,
            "end": texture,
            "top": texture,
            "bottom": texture,
            "up": texture,
            "down": texture,
            "front": "minecraft:block/smithing_table_front",
        }, "minecraft:block/orientable")

    for size, (width, height, size_ru, size_en) in SIZES.items():
        controller = f"gas_tank_controller_{size}"
        block_assets(controller, {"": {"model": f"destroy_extras:block/{common}"}}, common)
        lang_ru[f"block.destroy_extras.{controller}"] = f"{size_ru} контроллер газового бака"
        lang_en[f"block.destroy_extras.{controller}"] = f"{size_en} Gas Tank Controller"
        dump(DATA / f"loot_table/blocks/{controller}.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"destroy_extras:{controller}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
        tank_ids = [controller]

        for material, (hull, _, material_ru, material_en) in MATERIALS.items():
            block_id = f"gas_tank_{size}_{material}"
            structure = DATA / f"structure/multiblocks/{block_id}.nbt"
            structure.parent.mkdir(parents=True, exist_ok=True)
            structure.write_bytes(template(width, height, controller, hull))

            shell = f"gas_tank/{material}"
            variants = {"multiblockslave=true": {"model": f"destroy_extras:block/{shell}"}}
            for facing, angle in zip(("north", "east", "south", "west"), (180, 270, 0, 90)):
                variants[f"facing={facing},multiblockslave=false"] = {
                    "model": f"destroy_extras:block/{shell}_inlet", "y": angle,
                }
            block_assets(block_id, variants, shell)
            lang_ru[f"block.destroy_extras.{block_id}"] = f"{size_ru} газовый бак ({material_ru})"
            lang_en[f"block.destroy_extras.{block_id}"] = f"{size_en} {material_en} Gas Tank"
            dump(DATA / f"loot_table/blocks/{block_id}.json", {
                "type": "minecraft:block",
                "pools": [{
                    "rolls": 1,
                    "entries": [{"type": "immersiveengineering:multiblock"}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }],
            })
            tank_ids.append(block_id)

        for block_id in tank_ids:
            key = f"destroy_extras:{block_id}"
            value = {"id": key, "required": False} if block_id.endswith("_stainless_steel") else key
            if value not in tag["values"]:
                tag["values"].append(value)

    lang_ru["message.destroy_extras.gas_tank"] = "%s · %s/%s ммоль · %s/%s бар (абс.) · %s л"
    lang_en["message.destroy_extras.gas_tank"] = "%s · %s/%s mmol · %s/%s bar (abs.) · %s L"
    lang_ru["message.destroy_extras.gas_tank.empty"] = "Газовый бак пуст"
    lang_en["message.destroy_extras.gas_tank.empty"] = "Gas tank empty"
    pipe_assets(tag, lang_ru, lang_en)
    dump(ru, lang_ru)
    dump(en, lang_en)
    dump(tag_path, tag)


if __name__ == "__main__":
    main()
