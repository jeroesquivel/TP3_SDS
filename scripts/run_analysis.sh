#!/usr/bin/env bash
# Ejecuta los análisis de observables.
# Uso: ./scripts/run_analysis.sh [opciones de Analysis]
# Ejemplo: ./scripts/run_analysis.sh --all -N 100 -tf 10.0 --runs 5
# Los resultados quedan en results/
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
mkdir -p results
java -cp out analysis.Analysis "$@"
