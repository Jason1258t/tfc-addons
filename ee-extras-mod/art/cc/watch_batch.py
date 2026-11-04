#!/usr/bin/env python3
"""Ждёт уже запущенные задания через штатный watcher, не создавая повторных генераций."""
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import re
import subprocess
import sys

WATCHER = "/Users/ivan/Library/Application Support/kimi-desktop/daimon-share/daimon/runtime/kimi-code/home/plugins/managed/image_generation/scripts/image_generation_watch.py"


def watch(job: dict) -> tuple[str, int, str]:
    stdout = job["launcher_stdout"]
    log = re.search(r"Job log: (.+)", stdout).group(1)
    pid = re.search(r"Job PID file: (.+)", stdout).group(1)
    result = subprocess.run(["python3", WATCHER, "--log-file", log, "--pid-file", pid,
                             "--interval-seconds", "15"], capture_output=True, text=True)
    return job["name"], result.returncode, (result.stdout + result.stderr)[-3000:]


def main() -> None:
    path = Path(sys.argv[1])
    jobs = json.loads(path.read_text(encoding="utf-8"))
    failed = 0
    with ThreadPoolExecutor(max_workers=len(jobs)) as pool:
        for name, code, output in pool.map(watch, jobs):
            print(f"### {name}: exit {code}\n{output}", flush=True)
            failed += code != 0
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()
