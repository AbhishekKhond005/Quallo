#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/build.sh
mkdir -p build/tests
javac --release 21 -encoding UTF-8 -cp build/classes -d build/tests src/test/java/quallo/*.java
java -cp build/classes:build/tests quallo.StoreTest
if [[ "${1:-}" == "--desktop" ]]; then
  if [[ "$#" -gt 1 ]]; then
    java -cp build/classes:build/tests quallo.DesktopTest "$2"
  else
    java -cp build/classes:build/tests quallo.DesktopTest
  fi
fi
