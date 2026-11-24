#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=${1:-1.0.2}
./scripts/build.sh
mkdir -p dist
rm -rf build/app-image
jpackage --type app-image --dest build/app-image --input build/input --main-jar quallo.jar \
  --name Quallo --app-version "$version" --vendor Quallo --description "Offline game production planner" \
  --icon src/main/resources/quallo/quallo.png --add-modules java.desktop,java.xml,jdk.localedata
tar -czf "dist/quallo-$version-linux-x64.tar.gz" -C build/app-image Quallo

jpackage --type deb --dest dist --app-image build/app-image/Quallo --name Quallo --app-version "$version" \
  --vendor Quallo --description "Offline game production planner" --linux-package-name quallo \
  --linux-app-category utils
deb="dist/quallo_${version}_$(dpkg --print-architecture).deb"
stage=$(mktemp -d build/deb.XXXXXX)
trap 'rm -rf "$stage"' EXIT
dpkg-deb --raw-extract "$deb" "$stage/payload"
install -Dm644 packaging/quallo.desktop "$stage/payload/usr/share/applications/quallo.desktop"
dpkg-deb --root-owner-group --build "$stage/payload" "$stage/quallo.deb"
mv "$stage/quallo.deb" "$deb"

if command -v rpmbuild >/dev/null; then
  rpmbuild -bb packaging/quallo.spec --define "_topdir $PWD/build/rpm" \
    --define "_rpmdir $PWD/dist" --define "_rpmfilename %%{NAME}-%%{VERSION}-%%{RELEASE}.%%{ARCH}.rpm" \
    --define "quallo_version $version" --define "quallo_image $PWD/build/app-image/Quallo" \
    --define "quallo_desktop $PWD/packaging/quallo.desktop"
fi
