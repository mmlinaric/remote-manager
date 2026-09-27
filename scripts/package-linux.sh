#!/usr/bin/env bash
set -euo pipefail

project_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$project_dir"

version=$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)
if [[ "$version" == *-SNAPSHOT ]]; then
  echo "Release packaging requires a non-SNAPSHOT Maven version (found $version)." >&2
  exit 1
fi

input_dir="$project_dir/target/package-input"
dist_dir="$project_dir/target/dist"
rm -rf "$input_dir" "$dist_dir"

./mvnw -q clean package
mkdir -p "$input_dir/lib" "$dist_dir"
./mvnw -q dependency:copy-dependencies -DincludeScope=runtime \
  -DoutputDirectory="$input_dir/lib"
cp "$project_dir/target/remote-manager-$version.jar" "$input_dir/"

common=(
  --name "Remote Manager"
  --app-version "$version"
  --vendor "Remote Manager"
  --description "SSH connection manager backed by a KeePass vault"
  --input "$input_dir"
  --main-jar "remote-manager-$version.jar"
  --main-class "com.mmlinaric.remotemanager.app.Main"
  --java-options "--enable-native-access=ALL-UNNAMED"
  --icon "$project_dir/src/main/resources/icons/app/remote-manager.png"
  --dest "$dist_dir"
)
if [[ -n "${JPACKAGE_RUNTIME_IMAGE:-}" ]]; then
  common+=(--runtime-image "$JPACKAGE_RUNTIME_IMAGE")
fi

jpackage "${common[@]}" --type app-image
tar -C "$dist_dir" -czf "$dist_dir/Remote-Manager-$version-linux-x64.tar.gz" "Remote Manager"
rm -rf "$dist_dir/Remote Manager"

jpackage "${common[@]}" --type rpm \
  --linux-package-name remote-manager \
  --linux-menu-group Network \
  --linux-shortcut

(
  cd "$dist_dir"
  sha256sum ./*.rpm ./*.tar.gz > SHA256SUMS-linux-x64.txt
)
