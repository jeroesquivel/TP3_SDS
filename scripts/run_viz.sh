#!/usr/bin/env bash
# Abre el visualizador de trayectorias.
# Uso: ./scripts/run_viz.sh <archivo.txt> [--interval <ms>] [--max-frames <n>]
# Ejemplo: ./scripts/run_viz.sh output.txt --interval 50 --max-frames 2000
set -e

if [ $# -eq 0 ]; then
    echo "Uso: ./scripts/run_viz.sh <archivo_trayectoria.txt> [--interval <ms>] [--max-frames <n>]"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
java -cp out visualization.Visualizer "$@"
