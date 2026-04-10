#!/usr/bin/env bash
# Ejecuta una simulación individual.
# Uso: ./scripts/run_sim.sh [opciones de Main]
# Ejemplo: ./scripts/run_sim.sh -N 100 -tf 5.0 -s 42 -w 10 -o results/trayectoria.txt
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
java -cp out simulation.SimulationCli "$@"
