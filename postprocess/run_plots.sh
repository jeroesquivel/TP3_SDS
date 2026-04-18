#!/usr/bin/env bash
# Genera los gráficos del TP3 a partir de los .txt en simulations/ y results/.
# Uso: ./postprocess/run_plots.sh [opciones de plotter.py]
# Ejemplo: ./postprocess/run_plots.sh --all
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
mkdir -p figures
python3 postprocess/plotter.py --sim-dir simulations --results-dir results --out figures "$@"
