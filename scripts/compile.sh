#!/usr/bin/env bash
# Compila todos los fuentes Java en src/ → out/
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
mkdir -p out
find src -name "*.java" -print0 | xargs -0 javac -d out -sourcepath src
echo "Compilación exitosa. Binarios en out/"