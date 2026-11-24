#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf build/classes
mkdir -p build/classes build/input
javac --release 21 -encoding UTF-8 -d build/classes src/main/java/quallo/*.java
cp -R src/main/resources/. build/classes/
jar --create --file build/input/quallo.jar --main-class quallo.Main -C build/classes .
