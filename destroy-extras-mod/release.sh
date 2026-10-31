#!/usr/bin/env bash
# Локальная сборка раннего прототипа; публикация зависимостей и сайтовый релиз сюда не входят.
set -euo pipefail
cd "$(dirname "$0")"
./libs.sh
./gradlew build --console=plain
cp build/libs/destroy-extras-0.1.0.jar .
shasum -a 256 destroy-extras-0.1.0.jar
