#!/usr/bin/env python3
"""Модели модулей CC и эталонные JSON для сайт-датапака M12 и devtest.

Рецепты не включаются в release jar. Публикация — только карточкой сайта через MCP datapack_put;
src/devtest/resources — автоматическая копия для проверок на dedicated server.
"""
from __future__ import annotations

import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "recipes" / "cc-cbr"
DEVTEST = ROOT / "src" / "devtest" / "resources"

ITEMS = [
    "abs_sheet", "abs_keyboard_blank", "pet_film", "rgb_phosphor", "shadow_mask",
    "permanent_magnet", "speaker_cone", "battery_electrolyte", "ink_cartridge",
    "control_board_l1", "computer_motherboard", "advanced_motherboard", "turtle_controller",
    "advanced_turtle_controller", "keyboard", "pocket_input", "display_driver", "advanced_display_driver",
    "display_crt", "display_advanced", "pocket_display", "network_port", "computer_case",
    "advanced_computer_case", "pocket_case", "peripheral_case", "small_motor", "precision_actuator",
    "storage_bay", "robot_chassis", "disk_transport", "print_mechanism", "network_transceiver",
    "ender_transceiver", "speaker_driver", "audio_amplifier", "power_supply", "battery_module",
    "magnetic_disk_medium",
]

recipes: dict[str, dict] = {}
data_files: dict[str, dict] = {
    # В Destroy нет терефталевой кислоты и этиленгликоля: суммарный процесс без новых веществ.
    "data/ee_extras/destroy/reactions/pet_overall_synthesis.json": {
        "reactants": [
            {"molecule": "destroy:paraxylene", "ratio": 1, "order": 1},
            {"molecule": "destroy:ethene", "ratio": 1, "order": 1},
            {"molecule": "destroy:oxygen", "ratio": 3, "order": 1},
        ],
        "products": [{"molecule": "destroy:water", "ratio": 3}],
        "result": {"type": "destroy:precipitate_item", "moles": 1.0,
                   "item": "destroy:polyethene_terephthalate", "count": 1},
        "kinetics": {"activation_energy": 50.0, "preexponential_factor": 1.0e10, "enthalpy_change": -100.0},
    },
}
audit: list[dict] = []


def ingredient(value: str, count: int = 1) -> dict:
    result = {"tag": value[1:]} if value.startswith("#") else {"item": value}
    if count != 1:
        result["count"] = count
    return result


def result(item: str, count: int = 1) -> dict:
    return {"id": item, "count": count}


def add(path: str, recipe: dict, status: str = "new", note: str = "") -> None:
    if path in recipes:
        raise ValueError(f"duplicate {path}")
    recipes[path] = recipe
    audit.append({"path": path, "status": status, "note": note})


def solder(name: str, inputs: list[tuple[str, int] | str], output: str | None = None, count: int = 1) -> None:
    add(f"data/ee_extras/recipe/cc/{name}.json", {
        "type": "morered:soldering",
        "ingredients": [ingredient(*x) if isinstance(x, tuple) else ingredient(x) for x in inputs],
        "result": result(output or f"ee_extras:{name}", count),
    })


def mechanical(path: str, pattern: list[str], key: dict[str, str], output: str, count: int = 1,
               status: str = "new", note: str = "", recipe_type: str = "create:mechanical_crafting") -> None:
    add(path, {
        "type": recipe_type,
        "accept_mirrored": False,
        "pattern": pattern,
        "key": {letter: ingredient(value) for letter, value in key.items()},
        "result": result(output, count),
    }, status, note)


def module(name: str, pattern: list[str], key: dict[str, str], count: int = 1) -> None:
    mechanical(f"data/ee_extras/recipe/cc/{name}.json", pattern, key, f"ee_extras:{name}", count)


# Электронные платы и вычислительные модули.
solder("control_board_l1", ["ee_extras:l1_board", "ee_extras:resistor", "ee_extras:capacitor", "ee_extras:connector"])
solder("computer_motherboard", ["ee_extras:l2_board", "ccbr:basic_integrated_circuit", ("ee_extras:resistor", 2),
       ("ee_extras:capacitor", 2), "ee_extras:connector"])
solder("advanced_motherboard", ["ee_extras:l3_board", "ccbr:integrated_circuit", ("ee_extras:transistor", 2),
       ("ee_extras:capacitor", 2), "ee_extras:connector"])
solder("turtle_controller", ["ee_extras:computer_motherboard", ("ee_extras:transistor", 2), "ee_extras:inductor",
       "ee_extras:connector"])
solder("advanced_turtle_controller", ["ee_extras:advanced_motherboard", ("ee_extras:transistor", 4),
       ("ee_extras:inductor", 2), "ee_extras:connector"])
solder("pocket_input", ["ee_extras:control_board_l1", "ee_extras:connector", ("ee_extras:copper_foil", 2)])
solder("display_driver", ["ee_extras:l1_board", "ee_extras:silicon_diode", ("ee_extras:resistor", 2),
       "ee_extras:connector"])
solder("advanced_display_driver", ["ee_extras:l3_board", ("ee_extras:basic_ic", 2), ("ee_extras:transistor", 2),
       "ee_extras:connector"])
solder("network_port", ["ee_extras:control_board_l1", ("ee_extras:connector", 2), "ee_extras:inductor",
       "ee_extras:copper_foil"])
solder("network_transceiver", ["ee_extras:l2_board", "ee_extras:basic_ic", "ee_extras:inductor", "create:transmitter",
       "ee_extras:network_port", ("ee_extras:copper_foil", 2)])
solder("ender_transceiver", ["ee_extras:network_transceiver", "ee_extras:l3_board", "ccbr:integrated_circuit",
       "ee_extras:inductor", "minecraft:ghast_tear", ("ee_extras:copper_foil", 2)])
solder("audio_amplifier", ["ee_extras:control_board_l1", "ee_extras:transistor", "ee_extras:capacitor",
       "ee_extras:inductor", ("ee_extras:resistor", 2)])

# Полимеры Destroy используются напрямую, без дублирования гранул в EE Extras.
add("data/ee_extras/recipe/cc/materials/abs_sheet.json", {
    "type": "create:compacting", "ingredients": [ingredient("destroy:abs", 2)],
    "heat_requirement": "heated", "results": [result("ee_extras:abs_sheet")],
})
add("data/ee_extras/recipe/cc/materials/abs_keyboard_blank.json", {
    "type": "create:compacting", "ingredients": [ingredient("destroy:abs", 3)],
    "heat_requirement": "heated", "results": [result("ee_extras:abs_keyboard_blank")],
})
add("data/ee_extras/recipe/cc/materials/pet_film.json", {
    "type": "create:compacting", "ingredients": [ingredient("destroy:polyethene_terephthalate")],
    "heat_requirement": "heated", "results": [result("ee_extras:pet_film", 2)],
})
add("data/ee_extras/recipe/cc/materials/rgb_phosphor.json", {
    "type": "create:mixing", "ingredients": [ingredient("tfc_aeronautics:powder/zinc", 2),
        ingredient("tfc:powder/sulfur"), ingredient("tfcorewashing:chromium_powder"),
        ingredient("tfc_aeronautics:powder/copper"), ingredient("tfc_aeronautics:powder/silver")],
    "heat_requirement": "superheated", "results": [result("ee_extras:rgb_phosphor", 3)],
})
add("data/ee_extras/recipe/cc/materials/shadow_mask.json", {
    "type": "create:pressing", "ingredients": [ingredient("#c:plates/steel")],
    "results": [result("ee_extras:shadow_mask")],
})

# Ввод, экраны и корпуса.
module("keyboard", ["PPP", "PPP", "CFC"], {"P": "ee_extras:abs_keyboard_blank", "C": "ee_extras:control_board_l1",
       "F": "ee_extras:copper_foil"})
module("display_crt", ["GGG", "TIT", "CFC"], {"G": "#c:glass_panes", "T": "create:electron_tube",
       "I": "ee_extras:inductor", "C": "ee_extras:display_driver", "F": "ee_extras:copper_foil"})
module("display_advanced", ["PPP", "LS ", "CFC"], {"P": "#c:glass_panes", "L": "ee_extras:rgb_phosphor",
       "S": "ee_extras:shadow_mask", "C": "ee_extras:advanced_display_driver", "F": "ee_extras:copper_foil"})
module("pocket_display", ["PPP", "DF ", " C "], {"P": "#c:glass_panes", "D": "ee_extras:display_driver",
       "F": "ee_extras:copper_foil", "C": "ee_extras:connector"})
module("computer_case", ["SPS", "S S", "SAS"], {"S": "#c:plates/steel", "P": "ee_extras:abs_sheet",
       "A": "ee_extras:abs_sheet"})
module("advanced_computer_case", ["ABA", "A A", "APA"], {"A": "#c:plates/aluminum", "B": "#c:plates/brass",
       "P": "ee_extras:abs_sheet"})
module("pocket_case", ["APA", "P P", "AAA"], {"A": "#c:plates/aluminum", "P": "ee_extras:abs_sheet"})
module("peripheral_case", ["S S", "SPS", "SAS"], {"S": "#c:plates/steel", "P": "ee_extras:abs_sheet",
       "A": "ee_extras:abs_sheet"})

# Расходные материалы и детали для динамика, аккумулятора и принтера.
add("data/ee_extras/recipe/cc/materials/permanent_magnet.json", {
    "type": "create:deploying",
    "ingredients": [ingredient("#c:rods/steel"), ingredient("ee_extras:inductor")],
    "keep_held_item": True,
    "results": [result("ee_extras:permanent_magnet")],
})
add("data/ee_extras/recipe/cc/materials/speaker_cone.json", {
    "type": "create:compacting",
    "ingredients": [ingredient("minecraft:paper", 2),
                    {"type": "neoforge:single", "fluid": "minecraft:water", "amount": 50}],
    "heat_requirement": "heated",
    "results": [result("ee_extras:speaker_cone")],
})
add("data/ee_extras/recipe/cc/materials/battery_electrolyte.json", {
    "type": "create:filling",
    "ingredients": [ingredient("minecraft:glass_bottle"),
                    {"type": "destroy:mixture_with_molecule", "molecule": "destroy:sulfuric_acid",
                     "amount": 125, "min_concentration": 4.0, "max_concentration": 5.0}],
    "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "destroy"}],
    "results": [result("ee_extras:battery_electrolyte")],
})
mechanical("data/ee_extras/recipe/cc/materials/ink_cartridge.json", [" P ", "AIA"],
           {"P": "ee_extras:pet_film", "A": "ee_extras:abs_sheet", "I": "minecraft:ink_sac"},
           "ee_extras:ink_cartridge")

# Электромеханика, связь, звук и питание.
module("small_motor", [" C ", "RMR", " S "], {"C": "ee_extras:inductor", "R": "#c:rods/steel",
       "M": "ee_extras:permanent_magnet", "S": "#c:plates/steel"})
module("precision_actuator", ["G R", "MC ", "D T"], {"G": "create:cogwheel", "R": "#c:rods/steel",
       "M": "ee_extras:small_motor", "C": "ee_extras:control_board_l1", "D": "ee_extras:silicon_diode", "T": "ee_extras:abs_sheet"})
module("storage_bay", ["R R", "SAS", "CCT"], {"R": "#c:rods/steel", "S": "#c:plates/steel", "A": "#c:chests/wooden",
       "C": "ee_extras:connector", "T": "#tfc:insulation"})
module("robot_chassis", ["SSSSS", "S M S", "SMBCS", "S M S", "SSSSS"], {"S": "#c:plates/steel",
       "M": "ee_extras:small_motor", "B": "#c:plates/brass", "C": "ee_extras:control_board_l1"})
module("disk_transport", ["M R", "HAC", "M T"], {"M": "ee_extras:small_motor", "R": "#c:rods/steel",
       "H": "ee_extras:inductor", "A": "ee_extras:precision_actuator", "C": "ee_extras:control_board_l1",
       "T": "ee_extras:connector"})
module("print_mechanism", ["M R", "KAC", " T "], {"M": "ee_extras:small_motor", "R": "#c:rods/steel",
       "K": "ee_extras:ink_cartridge", "A": "ee_extras:precision_actuator", "C": "ee_extras:control_board_l1", "T": "ee_extras:connector"})
module("speaker_driver", ["SPS", "IMI", " T "], {"S": "#c:plates/steel", "P": "ee_extras:speaker_cone",
       "I": "ee_extras:inductor", "M": "ee_extras:permanent_magnet", "T": "#c:rods/steel"})
module("power_supply", ["S S", "ICI", "DCT"], {"S": "#c:plates/steel", "I": "ee_extras:inductor",
       "C": "ee_extras:capacitor", "D": "ee_extras:silicon_diode", "T": "ee_extras:connector"})
module("battery_module", ["LEL", "APA", " DT"], {"L": "#c:plates/lead", "E": "ee_extras:battery_electrolyte",
       "A": "ee_extras:abs_sheet", "P": "ee_extras:pet_film", "D": "ee_extras:silicon_diode", "T": "ee_extras:connector"})
module("magnetic_disk_medium", ["ASA", "IMI", "PPP"], {"S": "#c:plates/steel", "I": "minecraft:iron_nugget",
       "M": "tfc:powder/magnetite", "P": "ee_extras:pet_film", "A": "ee_extras:abs_sheet"}, 4)

# Полная замена базовых устройств CC: выходы и ID рецептов сохраняются.
CC = "data/computercraft/recipe"
module_keys = {
    "normal": {"K": "ee_extras:keyboard", "D": "ee_extras:display_crt", "M": "ee_extras:computer_motherboard",
               "C": "ee_extras:computer_case", "P": "ee_extras:power_supply", "T": "ee_extras:network_port"},
    "advanced": {"K": "ee_extras:keyboard", "D": "ee_extras:display_advanced", "M": "ee_extras:advanced_motherboard",
                 "C": "ee_extras:advanced_computer_case", "P": "ee_extras:power_supply", "T": "ee_extras:network_port"},
}
for tier in ("normal", "advanced"):
    mechanical(f"{CC}/computer_{tier}.json", ["KKK", "DMD", "CPT"], module_keys[tier],
               f"computercraft:computer_{tier}", status="changed", note="L2/L3 motherboard, keyboard, display, case, PSU and network")

for tier in ("normal", "advanced"):
    prefix = "advanced_" if tier == "advanced" else ""
    mechanical(f"{CC}/turtle_{tier}.json", ["RCR", "SMT", " A "], {
        "R": "ee_extras:robot_chassis", "C": f"computercraft:computer_{tier}", "S": "ee_extras:storage_bay",
        "M": f"ee_extras:{prefix}turtle_controller", "T": "ee_extras:network_port", "A": "ee_extras:precision_actuator",
    }, f"computercraft:turtle_{tier}", status="changed", note="Transform keeps computer data; real chassis, storage and drives",
       recipe_type="computercraft:transform_shaped")
    recipes[f"{CC}/turtle_{tier}.json"]["function"] = [{
        "type": "computercraft:copy_components", "from": {"item": f"computercraft:computer_{tier}"},
    }]
    recipes[f"{CC}/turtle_{tier}.json"].pop("accept_mirrored", None)
    recipes[f"{CC}/turtle_{tier}.json"]["category"] = "redstone"

for tier in ("normal", "advanced"):
    motherboard = "advanced_motherboard" if tier == "advanced" else "computer_motherboard"
    mechanical(f"{CC}/pocket_computer_{tier}.json", ["PDP", "IMI", "CBT"], {
        "P": "ee_extras:pocket_case", "D": "ee_extras:pocket_display", "I": "ee_extras:pocket_input",
        "M": f"ee_extras:{motherboard}", "C": "ee_extras:connector", "B": "ee_extras:battery_module",
        "T": "ee_extras:network_port",
    }, f"computercraft:pocket_computer_{tier}", status="changed", note="Pocket display/input, battery and L2/L3 motherboard")

mechanical(f"{CC}/monitor_normal.json", [" D ", "CPT", " S "], {"D": "ee_extras:display_crt",
       "C": "ee_extras:peripheral_case", "P": "ee_extras:power_supply", "T": "ee_extras:network_port",
       "S": "#c:plates/steel"}, "computercraft:monitor_normal", status="changed", note="CRT panel without a computer motherboard")
mechanical(f"{CC}/monitor_advanced.json", [" D ", "CPT", " S "], {"D": "ee_extras:display_advanced",
       "C": "ee_extras:peripheral_case", "P": "ee_extras:power_supply", "T": "ee_extras:network_port",
       "S": "#c:plates/aluminum"}, "computercraft:monitor_advanced", 4, "changed", "Colour display and driver, not an advanced computer")
mechanical(f"{CC}/disk_drive.json", [" D ", "MNC", "P T"], {"D": "ee_extras:disk_transport",
       "M": "ee_extras:computer_motherboard", "N": "ee_extras:network_port", "C": "ee_extras:peripheral_case",
       "P": "ee_extras:power_supply", "T": "ee_extras:connector"}, "computercraft:disk_drive", status="changed",
       note="Two motors, head, actuator and L2 controller in the transport")
mechanical(f"{CC}/printer.json", [" M ", "BNC", "P T"], {"M": "ee_extras:print_mechanism",
       "B": "ee_extras:computer_motherboard", "N": "ee_extras:network_port", "C": "ee_extras:peripheral_case",
       "P": "ee_extras:power_supply", "T": "ee_extras:connector"}, "computercraft:printer", status="changed",
       note="Mechanism and starter cartridge; printing still consumes ink and paper")
mechanical(f"{CC}/speaker.json", [" D ", "ANC", " T "], {"D": "ee_extras:speaker_driver",
       "A": "ee_extras:audio_amplifier", "N": "ee_extras:network_port", "C": "ee_extras:peripheral_case",
       "T": "ee_extras:connector"}, "computercraft:speaker", status="changed", note="Real driver and amplifier")
mechanical(f"{CC}/wired_modem.json", [" N ", "BCB", " T "], {"N": "ee_extras:network_port",
       "B": "ee_extras:control_board_l1", "C": "ee_extras:peripheral_case", "T": "ee_extras:connector"},
       "computercraft:wired_modem", status="changed", note="Network port and L1 communication board")
mechanical(f"{CC}/wireless_modem_normal.json", [" R ", "NCE", " T "], {"R": "ee_extras:network_transceiver",
       "N": "ee_extras:network_port", "C": "ee_extras:peripheral_case", "E": "minecraft:ender_pearl", "T": "ee_extras:connector"},
       "computercraft:wireless_modem_normal", status="changed", note="L2 transceiver, antenna and ender pearl")
mechanical(f"{CC}/wireless_modem_advanced.json", [" R ", "NC ", " T "], {"R": "ee_extras:ender_transceiver",
       "N": "ee_extras:network_port", "C": "ee_extras:peripheral_case", "T": "ee_extras:connector"},
       "computercraft:wireless_modem_advanced", status="changed", note="L3 ender transceiver retains CC range magic")
add(f"{CC}/cable.json", {"type": "create:compacting", "ingredients": [ingredient("#c:wires/copper", 2),
       ingredient("#tfc:insulation")], "results": [result("computercraft:cable", 6)]}, "changed",
       "Copper conductors and insulation, six cables per cycle")
mechanical(f"{CC}/redstone_relay.json", ["TRT", "BMB", " C "], {"T": "ee_extras:connector", "R": "minecraft:redstone",
       "B": "ee_extras:control_board_l1", "M": "computercraft:wired_modem", "C": "ee_extras:peripheral_case"},
       "computercraft:redstone_relay", status="changed", note="Wired modem and separate L1 redstone interface")

# Апгрейды остаются настоящими transform-рецептами CC и копируют все data components.
upgrades = {
    "computer_advanced_upgrade": (["AMA", "DCD", "KPK"], {
        "A": "ee_extras:advanced_computer_case", "M": "ee_extras:advanced_motherboard", "D": "ee_extras:display_advanced",
        "C": "computercraft:computer_normal", "K": "ee_extras:keyboard", "P": "ee_extras:power_supply",
    }, "computercraft:computer_normal", "computercraft:computer_advanced"),
    "pocket_computer_advanced_upgrade": (["AMA", "DCD", " B "], {
        "A": "ee_extras:pocket_case", "M": "ee_extras:advanced_motherboard", "D": "ee_extras:pocket_display",
        "C": "computercraft:pocket_computer_normal", "B": "ee_extras:battery_module",
    }, "computercraft:pocket_computer_normal", "computercraft:pocket_computer_advanced"),
    "turtle_advanced_upgrade": ([" R ", "ACP", " S "], {
        "R": "ee_extras:robot_chassis", "A": "ee_extras:advanced_turtle_controller", "C": "computercraft:turtle_normal",
        "P": "ee_extras:precision_actuator", "S": "ee_extras:storage_bay",
    }, "computercraft:turtle_normal", "computercraft:turtle_advanced"),
}
for name, (pattern, key, source, output) in upgrades.items():
    add(f"{CC}/{name}.json", {"type": "computercraft:transform_shaped", "category": "redstone",
        "function": [{"type": "computercraft:copy_components", "from": {"item": source}}], "pattern": pattern,
        "key": {letter: ingredient(value) for letter, value in key.items()}, "result": result(output)}, "changed",
        "Normal → Advanced with a real L3 motherboard/controller and copied device data")

# Шестнадцать impostor-рецептов теперь показывают окраску готовой дискеты. Настоящая colour/clear_colour
# механика CC сохраняет все компоненты, включая файлы; производство носителя — отдельный рецепт выше.
dyes = [
    ("black", 1118481), ("red", 13388876), ("green", 5744206), ("brown", 8349260),
    ("blue", 3368652), ("purple", 11691749), ("cyan", 5020082), ("light_gray", 10066329),
    ("gray", 5000268), ("pink", 15905484), ("lime", 8375321), ("yellow", 14605932),
    ("light_blue", 10072818), ("magenta", 15040472), ("orange", 15905331), ("white", 15790320),
]
for index, (dye, rgb) in enumerate(dyes, 1):
    add(f"{CC}/disk_{index}.json", {"type": "computercraft:impostor_shapeless", "category": "redstone",
        "group": "computercraft:disk", "ingredients": [ingredient("computercraft:disk"), ingredient(f"minecraft:{dye}_dye")],
        "result": {"components": {"minecraft:dyed_color": {"rgb": rgb, "show_in_tooltip": False}},
                   "count": 1, "id": "computercraft:disk"}}, "changed", "Dye an existing disk without deleting its files")

# CCBR сохраняет ID микросхем, но производство переходит на платы M10/M11.
solder("../ccbr/recipe/basic_integrated_circuit_crafting", ["ee_extras:l2_board", "ee_extras:basic_ic",
       "ee_extras:resistor", "ee_extras:capacitor", "ee_extras:connector"], "ccbr:basic_integrated_circuit")
# Переносим путь, который solder() создаёт от ee_extras, в точный ID CCBR.
path = "data/ee_extras/recipe/cc/../ccbr/recipe/basic_integrated_circuit_crafting.json"
recipe = recipes.pop(path)
recipes["data/ccbr/recipe/basic_integrated_circuit_crafting.json"] = recipe
for entry in audit:
    if entry["path"] == path:
        entry.update(path="data/ccbr/recipe/basic_integrated_circuit_crafting.json", status="changed",
                     note="Old gold/redstone shortcut removed; L2 board and silicon IC")
solder("../ccbr/recipe/mechanical_crafting/integrated_circuit", ["ee_extras:l3_board", "ee_extras:advanced_ic",
       ("ee_extras:transistor", 2), "ee_extras:connector"], "ccbr:integrated_circuit")
path = "data/ee_extras/recipe/cc/../ccbr/recipe/mechanical_crafting/integrated_circuit.json"
recipe = recipes.pop(path)
recipes["data/ccbr/recipe/mechanical_crafting/integrated_circuit.json"] = recipe
for entry in audit:
    if entry["path"] == path:
        entry.update(path="data/ccbr/recipe/mechanical_crafting/integrated_circuit.json", status="changed",
                     note="Old lapis/redstone shortcut removed; L3 board and advanced silicon IC")
add("data/ccbr/recipe/pressing/lapis_block.json", {"neoforge:conditions": [{"type": "neoforge:false"}]}, "removed",
    "After the IC replacement the lapis plate has no remaining active consumers")

# Полный аудит неизменённых 40 файлов CC: special/output-only/cosmetic/creative и upgrades.
unchanged = {
    "clear_colour.json": "special: clears colour without deleting disk data",
    "colour.json": "special: dyes an existing disk/device and preserves components",
    "computer_command.json": "creative/admin-only: requires a command block; no Survival command-block recipe added",
    "disk.json": "special: manufactures a disk from the new magnetic medium through the EE Extras mixin",
    "pocket_computer_upgrade.json": "special pocket upgrade mechanics",
    "printed_book.json": "output-only printed-product assembly",
    "printed_pages.json": "output-only printed-product assembly",
    "skull_cloudy.json": "easter egg, outside progression",
    "skull_dan200.json": "easter egg, outside progression",
    "turtle_upgrade.json": "special turtle upgrade mechanics",
    "wired_modem_full_from.json": "form conversion, no new modem production",
    "wired_modem_full_to.json": "form conversion, no new modem production",
}
for filename, note in unchanged.items():
    audit.append({"path": f"{CC}/{filename}", "status": "unchanged", "note": note})
for tier in ("normal", "advanced"):
    for kind in ("speaker", "wireless_modem_normal", "wireless_modem_advanced"):
        audit.append({"path": f"{CC}/pocket_{tier}/computercraft/{kind}.json", "status": "unchanged",
                      "note": "Pocket impostor upgrade; special recipe preserves data and upgrades"})
    for kind in ("speaker", "wireless_modem_normal", "wireless_modem_advanced", "minecraft/crafting_table"):
        path_kind = kind if "/" in kind else f"computercraft/{kind}"
        audit.append({"path": f"{CC}/turtle_{tier}/{path_kind}.json", "status": "unchanged",
                      "note": "Existing Turtle upgrade/display handling remains"})
    for tool in ("axe", "hoe", "pickaxe", "shovel", "sword"):
        audit.append({"path": f"{CC}/turtle_{tier}/minecraft/diamond_{tool}.json", "status": "unchanged",
                      "note": "Pack override maps Normal to red steel and Advanced to blue steel"})
    for overlay in ("rainbow_flag", "trans_flag"):
        audit.append({"path": f"{CC}/turtle_{tier}_overlays/{overlay}.json", "status": "unchanged",
                      "note": "Cosmetic transform recipe"})

# Общий тег изоляции: доступный IE/AFC базовый вариант + необязательные полимеры Destroy M9.
tag = {"replace": False, "values": ["#c:plates/plastic", {"id": "afc:rubber_bar", "required": False},
       {"id": "destroy:polystyrene_butadiene", "required": False},
       {"id": "destroy:polyethene", "required": False}, {"id": "destroy:polypropene", "required": False},
       {"id": "destroy:polyvinyl_chloride", "required": False}]}


def main() -> None:
    if len([x for x in audit if x["path"].startswith(CC)]) != 75:
        raise ValueError("CC audit must contain exactly 75 files")
    if len(ITEMS) != 39 or len(recipes) != 77 or len(data_files) != 1:
        raise ValueError(f"unexpected size: {len(ITEMS)} items, {len(recipes)} recipes, {len(data_files)} data files")
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    for item in ITEMS:
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"ee_extras:item/{item}"}}
        path = ROOT / "src" / "main" / "resources" / "assets" / "ee_extras" / "models" / "item" / f"{item}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    entries = []
    for path, recipe in sorted(recipes.items()):
        target = OUT / path
        target.parent.mkdir(parents=True, exist_ok=True)
        text = json.dumps(recipe, ensure_ascii=False, indent=2) + "\n"
        target.write_text(text, encoding="utf-8")
        disabled = {"type": "neoforge:false"} in recipe.get("neoforge:conditions", [])
        kind = "remove" if disabled else "add"
        if not path.startswith("data/ee_extras/"):
            kind = "remove" if kind == "remove" else "replace"
        entries.append({"path": path, "content": recipe, "kind": kind})
    for path, data in data_files.items():
        target = OUT / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        entries.append({"path": path, "content": data, "kind": "file"})
    tag_path = "data/tfc/tags/item/insulation.json"
    target = OUT / tag_path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(tag, indent=2) + "\n", encoding="utf-8")
    entries.append({"path": tag_path, "content": tag, "kind": "file"})
    (OUT / "entries.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (OUT / "audit.json").write_text(json.dumps(sorted(audit, key=lambda x: x["path"]), ensure_ascii=False, indent=2) + "\n",
                                   encoding="utf-8")
    # Копия в devtest-мод: release jar её не содержит.
    for path in [p for p in DEVTEST.rglob("*.json") if p.is_file()]:
        relative = path.relative_to(DEVTEST).as_posix()
        if relative.startswith("data/computercraft/") or relative.startswith("data/ccbr/") or relative == tag_path \
                or relative.startswith("data/ee_extras/recipe/cc/") or relative in data_files:
            path.unlink()
    for path, recipe in (recipes | data_files).items():
        target = DEVTEST / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(recipe, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    target = DEVTEST / tag_path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(tag, indent=2) + "\n", encoding="utf-8")
    manifest = []
    for path, recipe in sorted(recipes.items()):
        namespace, relative = path.removeprefix("data/").split("/recipe/", 1)
        output = recipe.get("result", {})
        if "results" in recipe:
            output = recipe["results"][0]
        manifest.append({"id": f"{namespace}:{relative.removesuffix('.json')}", "output": output.get("id", "")})
    (DEVTEST / "cc-test-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Сформировано: {len(ITEMS)} моделей, {len(entries)} записей, аудит CC {sum(x['path'].startswith(CC) for x in audit)}/75")


if __name__ == "__main__":
    main()
