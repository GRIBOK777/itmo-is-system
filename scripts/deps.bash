#!/usr/bin/env bash

set -euo pipefail
set -xe

LIBS=(
)

LIB_DIR="$1"

for lib in "${LIBS[@]}"; do
    curl -L -o "$LIB_DIR/$(basename "$lib")" "$lib"
done
