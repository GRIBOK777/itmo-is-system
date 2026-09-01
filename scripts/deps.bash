#!/usr/bin/env bash

set -euo pipefail
set -xe

LIBS=(
    https://github.com/google/google-java-format/releases/download/v1.36.1/google-java-format-1.36.1-all-deps.jar
)

LIB_DIR="$1"

for lib in "${LIBS[@]}"; do
    curl -L -o "$LIB_DIR/$(basename "$lib")" "$lib"
done
