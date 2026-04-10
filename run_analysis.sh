#!/usr/bin/env bash
# Ejecuta los análisis de observables.
# Uso: ./run_analysis.sh [opciones de Analysis]
# Ejemplo: ./run_analysis.sh --all -N 100 -tf 10.0 --runs 5
# Los resultados quedan en results/
set -e

mkdir -p results
java -cp out Analysis "$@"