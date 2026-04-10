#!/usr/bin/env bash
# Ejecuta una simulación individual.
# Uso: ./run_sim.sh [opciones de Main]
# Ejemplo: ./run_sim.sh -N 100 -tf 5.0 -s 42 -w 10 -o results/trayectoria.txt
set -e

java -cp out Main "$@"