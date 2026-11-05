#!/usr/bin/env python3
"""Экспорт сохранённых исходников электроники; старые процедурные иконки не восстанавливаются."""

from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent / "electronics-v2"
for script in ("export_items.py", "export_blocks.py"):
    subprocess.run([sys.executable, str(root / script)], check=True)
