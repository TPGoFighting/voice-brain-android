#!/bin/sh
set -e
cd "$(dirname "$0")/.."
if [ -f ci/binaries.tar.b64 ]; then
  base64 -d ci/binaries.tar.b64 | tar -xzf -
fi
chmod +x gradlew || true
