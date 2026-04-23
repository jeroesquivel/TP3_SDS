#!/usr/bin/env bash
# Pipeline completo: compila, simula, analiza y grafica.
#
# Uso:
#   ./scripts/run.sh              # pipeline completo
#   ./scripts/run.sh --viz        # abre el visualizador al final
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

./scripts/compile.sh

echo
echo "▶ Simulando (1.1 timing + 1.2-1.4 trayectorias)…"
java -cp out simulation.Simulate

echo
echo "▶ Analizando trayectorias…"
java -cp out analysis.Analyze

echo
echo "▶ Graficando…"
python3 postprocess/plotter.py --all

if [[ "${1:-}" == "--viz" ]]; then
    echo
    echo "▶ Abriendo visualizador…"
    java -cp out Main
fi