#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=${1:-1.0.0}
./scripts/build.sh
mkdir -p dist
rm -rf build/app-image
jpackage --type app-image --dest build/app-image --input build/input --main-jar quallo.jar \
  --name Quallo --app-version "$version" --vendor Quallo --description "Offline game production planner" \
  --icon src/main/resources/quallo/quallo.png --add-modules java.desktop,java.xml,jdk.localedata
archive="quallo-$version-linux-x64.tar.gz"
tar -czf "dist/$archive" -C build/app-image Quallo
jpackage --type deb --dest dist --app-image build/app-image/Quallo --name Quallo --app-version "$version" \
  --vendor Quallo --description "Offline game production planner" --linux-package-name quallo \
  --linux-shortcut --linux-app-category "Office;ProjectManagement"
if command -v rpmbuild >/dev/null; then
  jpackage --type rpm --dest dist --app-image build/app-image/Quallo --name Quallo --app-version "$version" \
    --vendor Quallo --description "Offline game production planner" --linux-package-name quallo \
    --linux-shortcut --linux-app-category "Office;ProjectManagement"
fi
