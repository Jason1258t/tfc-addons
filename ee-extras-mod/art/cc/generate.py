#!/usr/bin/env python3
"""Запускает оригинальную генерацию иконок; не рисует игровые текстуры кодом."""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LAUNCHER = Path("/Users/ivan/Library/Application Support/kimi-desktop/daimon-share/daimon/runtime/kimi-code/home/plugins/managed/image_generation/scripts/image_generation_launch.py")
REFERENCES = [
    "https://www.kimi.com/apiv2-files/sign-obj/kimi-fs%2Ffiles%2Fblob%2Fb74ddeb2385b1c6fd2939de2a547388aaa231d361f75c025d98ca5f2106c0f7b?filename=pack-reference-sheet.png&sig=-xur-jlwIX17Mg1oKVwPDm5LuBrxYwsfh9GspXZvySo=&t=o",
    "https://www.kimi.com/apiv2-files/sign-obj/kimi-fs%2Ffiles%2Fblob%2F2519c65d44bec61e429213cc53b143805565173adc75d87afb3e846d3d7cb1f7?filename=approved-modules.png&sig=Jutn2CRWxQ6vMtOxfYVE-h78tKFQltVgSIvTtc_nI08=&t=o",
]
SUBJECTS = {
    "control_board_l1": "a small populated dark-green printed circuit control board, one simple black chip, two pale connectors, a resistor and copper tracks",
    "computer_motherboard": "a rectangular dark-green computer motherboard, central black processor package, two memory slots, several small capacitors and edge connector",
    "advanced_motherboard": "a rectangular dark desaturated-purple advanced computer motherboard, central black processor with restrained gold contacts, two slots and small capacitors",
    "turtle_controller": "a rugged dark-green robot motor controller circuit board, two black driver chips, copper coil and four output terminals",
    "advanced_turtle_controller": "a rugged dark-purple advanced robot controller circuit board, two black driver chips with gold pins, copper coil and four output terminals",
    "keyboard": "a compact rectangular dark-gray computer keyboard module viewed straight from above, three rows of small square charcoal keys and a steel frame",
    "pocket_input": "a very small handheld input panel, dark-green contact board with six dark square buttons and a short copper ribbon connector",
    "display_driver": "a small dark-green display driver circuit board, black controller chip, two capacitors and a wide pale ribbon-cable connector",
    "advanced_display_driver": "a small dark-purple colour display driver circuit board, black controller chip with gold pins and three tiny red green blue output components",
    "display_crt": "a compact gray cathode-ray computer display module, rectangular dark black screen, thick steel bezel and two small electron-tube details at the back",
    "display_advanced": "a thin rectangular advanced colour LCD module, dark screen with three tiny restrained red green blue pixels, light aluminum frame and flat black back",
    "pocket_display": "a tiny handheld LCD module, dark rectangular screen, narrow light aluminum frame and short copper ribbon cable",
    "network_port": "a small gray rectangular wired network socket on a dark-green circuit board, dark square RJ45 opening and eight tiny copper contacts",
    "computer_case": "an empty rectangular steel computer chassis viewed slightly from above, open top, straight gray panels and dark interior, no screen and no text",
    "advanced_computer_case": "an empty rectangular light-aluminum advanced computer chassis viewed slightly from above, open top, dark interior and restrained brass corner reinforcements",
    "pocket_case": "a small empty handheld computer frame, light aluminum rectangular rim, dark interior and tiny glass front panel, no screen content",
    "peripheral_case": "a compact empty steel peripheral housing viewed slightly from above, open top, straight dark-gray panels and a single rear connector hole",
    "small_motor": "a small rectangular industrial electric motor, dark steel housing, visible copper winding, straight silver shaft and two simple mounting feet",
    "precision_actuator": "a compact precision electromechanical actuator, small steel motor, one restrained brass cogwheel, straight silver sliding rod and dark-green control board",
    "storage_bay": "a compact steel storage bay module, rectangular open dark slot, simple gray frame and two pale electrical connectors",
    "robot_chassis": "a square open industrial robot chassis viewed from above, straight steel outer frame, four small motor housings and brass cross reinforcement, empty centre",
    "disk_transport": "a floppy-disk drive transport mechanism without outer case, two straight steel guide rails, tiny read head, dark rectangular slot and small copper motor",
    "print_mechanism": "a compact printer mechanism without outer case, straight steel roller, small ink carriage, two guide rails and tiny copper motor",
    "network_transceiver": "a dark-green wireless network transceiver circuit board, black radio chip, small copper coil antenna and short straight metal aerial",
    "ender_transceiver": "a dark-purple advanced ender transceiver circuit board, black gold-pin chip, small muted-green ender pearl centre and copper coil antenna",
    "speaker_driver": "a single small industrial speaker driver, square dark-steel mounting frame, simple dark circular cone, copper winding detail and two terminals",
    "audio_amplifier": "a small dark-green audio amplifier circuit board, one black transistor, two cylindrical capacitors, copper inductor and pale output terminals",
    "power_supply": "a compact open steel power-supply module, copper transformer winding, two dark cylindrical capacitors, small dark-green board and output terminals",
    "battery_module": "a compact rectangular lead-acid battery module, dark-gray straight case, visible lead-gray plates, muted red and black terminals and small brass contacts",
    "magnetic_disk_medium": "a square magnetic floppy-disk storage medium, dark charcoal rigid frame, central dark magnetic circular disc visible through a simple opening and small steel shutter",
}
COMMON = """Create one original Minecraft inventory item texture: {subject}.
References supplied as images: the first image shows the actual EE Extras integrated circuits and inductor plus CC: Tweaked computer and disk; match their restrained palette, simple silhouettes and functional detail. The second image is the approved addon module style; match its straight rectangular construction and readability, but do not copy its brass palette.
View the object straight from above or at a very slight three-quarter angle only where needed to show housing depth. Use simple pixel-art forms, clear dark outline, flat planes and a small palette of material shades. Use steel gray for normal housings, light aluminum for advanced housings, dark green for ordinary circuitry, dark desaturated purple and restrained gold for advanced circuitry. The item must remain readable when reduced to 32x32 pixels.
No words, letters, numbers, logos or watermark. No glossy bevels, rounded illustrative shoulders, ornamental trim, noise, dense tiny highlights or ground shadow. One complete isolated object, centered with a transparent margin of about 10 percent on every side, no cropped edges. True transparent background; do not draw a checkerboard or scene. Use a consistent logical pixel grid and deliver an enlarged source suitable for mechanical reduction."""


def main() -> None:
    if len(sys.argv) != 4 or sys.argv[1] != "launch":
        raise SystemExit("usage: generate.py launch START END")
    start, end = int(sys.argv[2]), int(sys.argv[3])
    names = list(SUBJECTS)
    selected = names[start:end]
    (ROOT / "src").mkdir(exist_ok=True)
    jobs = []
    for name in selected:
        description = COMMON.format(subject=SUBJECTS[name])
        output = ROOT / "src" / f"{name}.png"
        command = ["python3", str(LAUNCHER), "generate", "--description", description, "--size", "1024x1024",
                   "--background", "transparent", "--output", str(output)]
        for reference in REFERENCES:
            command.extend(["--reference-image", reference])
        result = subprocess.run(command, check=True, capture_output=True, text=True)
        print(f"### {name}\n{result.stdout}", flush=True)
        jobs.append({"name": name, "description": description, "output": str(output), "references": REFERENCES,
                     "size": "1024x1024", "background": "transparent", "launcher_stdout": result.stdout})
    jobs_path = ROOT / f"jobs-{start}-{end}.json"
    jobs_path.write_text(json.dumps(jobs, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"JOB_FILE={jobs_path}")


if __name__ == "__main__":
    main()
