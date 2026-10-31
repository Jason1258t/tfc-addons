#!/usr/bin/env bash
# Общий стенд живёт в мосте: там находится devtest-мод /destroytest.
set -euo pipefail
cd "$(dirname "$0")"
exec ../destroy-bridge-mod/test-server.sh "$@"
