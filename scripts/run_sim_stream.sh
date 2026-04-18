#!/usr/bin/env bash
# Corre una simulación en modo streaming (no acumula historias en memoria).
# Escribe un archivo liviano de análisis *.events.txt que alcanza para los
# puntos 1.2–1.4, además de la trayectoria completa si se quiere animar.
#
# Ejemplos:
#   # Run grande para análisis (sin trayectoria, sólo events file):
#   ./scripts/run_sim_stream.sh -N 800 -tf 1000 -s 42 \
#        --no-output --stream --analysis results/events_N_800_run_1.txt --snap-dt 0.5
#
#   # Run para animación (trayectoria + events):
#   ./scripts/run_sim_stream.sh -N 200 -tf 50 -s 1 \
#        -o simulations/anim_N_200.txt --stream --snap-dt 0.5 -w 20
#
# -Xmx:  podés subirlo con HEAP=8g (default 4g)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
HEAP="${HEAP:-4g}"
java -Xmx"$HEAP" -Duser.language=en -Duser.country=US \
     -cp out simulation.SimulationCli --stream "$@"
