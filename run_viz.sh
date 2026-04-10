#!/usr/bin/env bash
# Abre el visualizador de trayectorias.
# Uso: ./run_viz.sh <archivo.txt> [--interval <ms>] [--max-frames <n>]
# Ejemplo: ./run_viz.sh output.txt --interval 50 --max-frames 2000
set -e

if [ $# -eq 0 ]; then
    echo "Uso: ./run_viz.sh <archivo_trayectoria.txt> [--interval <ms>] [--max-frames <n>]"
    exit 1
fi

java -cp out Visualizer "$@"