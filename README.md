# TP3_SDS — EDMD en recinto circular

Simulador de dinámica molecular basada en eventos (EDMD) para un recinto
circular con obstáculo central fijo.

- Java 11+
- Python 3 + numpy + matplotlib (sólo para los gráficos)
- Sin dependencias Java externas

## Flujo

```
Simulate  →  simulations/sim_N_*_run_*.txt  +  results/timing.csv
Analyze   →  results/cfc_*.txt, fu_*.txt, radial_*.txt
plotter.py → figures/*.png
Main       → visualizar una trayectoria de simulations/
```

`Simulate` limpia `simulations/` y `results/` antes de correr. `Analyze`
sólo agrega los observables a `results/` (no borra `timing.csv`).

## Estructura

```
src/
  Main.java                     # selector + visualizador
  simulation/
    Particle.java
    Event.java
    SimulationResult.java
    Simulator.java              # motor EDMD (run, runStream, runLight)
    Simulate.java               # entrypoint: 1.1 timing + 1.2-1.4 trayectorias
  analysis/
    Analyze.java                # entrypoint: recorre simulations/
    SimulationAnalyzer.java     # cfc, fu, radial por archivo
  visualization/
    Visualizer.java             # animador Swing

postprocess/
  plotter.py                    # figuras 1.1 – 1.4

scripts/
  compile.sh
  run.sh                        # pipeline completo

simulations/                    # generado por Simulate
results/                        # generado por Simulate (timing.csv) + Analyze
figures/                        # generado por plotter.py
```

## Uso rápido

```bash
./scripts/compile.sh
./scripts/run.sh                # compila, simula, analiza y grafica
./scripts/run.sh --viz          # idem + abre el visualizador al final
```

## Paso a paso

```bash
./scripts/compile.sh

# 1) Simular (limpia simulations/ y results/, corre timing + trayectorias)
java -cp out simulation.Simulate

# 2) Analizar todas las trayectorias generadas
java -cp out analysis.Analyze

# 3) Graficar
python3 postprocess/plotter.py --all

# 4) (Opcional) Visualizar una trayectoria existente
java -cp out Main
```

## Configuración de las corridas

Los parámetros viven dentro de `simulation.Simulate`:

| Bloque          | Ns                         | Runs | tf [s] | Notas                     |
|-----------------|----------------------------|------|--------|---------------------------|
| Timing (1.1)    | 10,20,50,100,200,400,800   | 10   | 500    | usa `runStream` (sin I/O) |
| Trayectorias    | 10,50,100,200,400,800      | 1    | 1500   | N≥300 → `runLight`        |

Para cambiar Ns, runs o tf editá las constantes al principio de `Simulate.java`.

## Main (visualizador)

Lista las trayectorias encontradas en `simulations/`. Doble-click o
"Abrir visualizador" para animarlas.

Controles del visualizador:

| Tecla   | Acción                      |
|---------|-----------------------------|
| ESPACIO | Pausar / reanudar           |
| ← / →   | Frame anterior / siguiente  |
| R       | Reiniciar                   |
| Q       | Cerrar                      |

## Formato de trayectoria

```
N  t
t  x  y  vx  vy  estado
...
```

- `estado`: 0 = fresca (verde), 1 = usada (violeta).
- En modo `runLight` sólo se escribe un frame cada 1000 eventos y el nombre
  lleva sufijo `_light`.

## Salidas

| Archivo                               | Origen     | Contenido                          |
|---------------------------------------|------------|-----------------------------------|
| `simulations/sim_N_*_run_*.txt`       | Simulate   | trayectoria densa                 |
| `simulations/sim_N_*_run_*_light.txt` | Simulate   | trayectoria ligera (N≥300)        |
| `results/timing.csv`                  | Simulate   | N, avg_time_s, std_time_s, events |
| `results/cfc_*.txt`                   | Analyze    | colisiones fresca→usada acumuladas|
| `results/fu_*.txt`                    | Analyze    | fracción de usadas                |
| `results/radial_*.txt`                | Analyze    | perfiles radiales                 |
| `figures/*.png`                       | plotter.py | gráficos 1.1 – 1.4                |