# TP3_SDS

Simulador EDMD para recinto circular con obstáculo central, ahora organizado por paquetes y con un launcher gráfico en `Main`.

- Java 11+
- Sin dependencias externas de runtime
- Compatible con scripts, `java -cp`, Maven e IntelliJ IDEA

## 1) Estructura nueva

```text
src/
  Main.java                    # launcher gráfico
  simulation/
    Particle.java
    Event.java
    SimulationResult.java
    Simulator.java
    SimulationCli.java
  analysis/
    Analysis.java
  visualization/
    Visualizer.java

scripts/                        # Todos los scripts aquí
  compile.sh
  run_sim.sh
  run_analysis.sh
  run_viz.sh

pom.xml
results/
out/
```

## 2) Launcher gráfico (`Main`)

Abrí el proyecto en IntelliJ o ejecutá:

```bash
./scripts/compile.sh
java -cp out Main
```

La UI incluye:

- pestaña de **simulación** con todos los inputs del simulador
- **perfiles predefinidos**: Tipo 1, Tipo 2, Tipo 3 y Benchmark
- pestaña de **análisis** con selectores y parámetros
- pestaña de **visualización** con archivo, intervalo y max frames
- **consola embebida** para ver la salida textual del proceso
- **botón Volver** cuando ejecutas simulación/análisis

## 3) Cómo correr cada parte

### A. Scripts (desde scripts/)

```bash
./scripts/compile.sh
./scripts/run_sim.sh -N 100 -tf 200 -s 42 -w 50 -o results/traj.txt
./scripts/run_analysis.sh --all -N 100 -tf 1000 --runs 5
./scripts/run_viz.sh results/traj.txt --interval 50 --max-frames 4000
```

### B. Java directo

```bash
mkdir -p out
find src -name "*.java" -print0 | xargs -0 javac -d out -sourcepath src

java -cp out Main
java -cp out simulation.SimulationCli -N 100 -tf 200 -s 42 -w 50 -o results/traj.txt
java -cp out analysis.Analysis --all -N 100 -tf 1000 --runs 5
java -cp out visualization.Visualizer results/traj.txt --interval 50 --max-frames 4000
```

### C. Maven

```bash
mvn compile
mvn exec:java -Dexec.mainClass=Main
mvn exec:java -Dexec.mainClass=simulation.SimulationCli -Dexec.args="-N 100 -tf 200 -s 42 -w 50 -o results/traj.txt"
mvn exec:java -Dexec.mainClass=analysis.Analysis -Dexec.args="--all -N 100 -tf 1000 --runs 5"
mvn exec:java -Dexec.mainClass=visualization.Visualizer -Dexec.args="results/traj.txt --interval 50 --max-frames 4000"
```

### D. IntelliJ IDEA

1. `File -> Open...` y elegir la carpeta del repo.
2. Aceptar `Load Maven Project`.
3. Verificar JDK 11+.
4. Correr `Main` desde el gutter o crear Run Configurations.

## 4) Parámetros de la simulación

El launcher usa los mismos flags que la CLI:

- `-N <int>`: número de partículas
- `-tf <double>`: tiempo final
- `-o <file>`: archivo de trayectoria
- `-s <long>`: semilla opcional
- `-w <int>`: escribir cada W eventos
- `--snap-every <int>`: snapshots para perfiles radiales
- `--no-output`: no escribir trayectoria

Los botones de perfil cargan combinaciones rápidas de esos valores.

## 5) Análisis

Flags disponibles:

- `--timing`
- `--cfc`
- `--fu`
- `--radial`
- `--all`
- `-N <int>`
- `-tf <double>`
- `--runs <int>`
- `--results-dir <dir>`

Ejemplo:

```bash
java -cp out analysis.Analysis --cfc --fu -N 100 -tf 1000 --runs 5 --results-dir results
```

## 6) Visualización

Uso:

```bash
java -cp out visualization.Visualizer <archivo> [--interval <ms>] [--max-frames <n>]
```

El intervalo por defecto se mantiene en `50 ms`.

## 7) Formato de trayectoria

```text
N t_actual
t x y vx vy estado
...
```

## 8) Salidas en `results/`

- `timing.csv`
- `cfc_run{k}.csv`
- `cfc_summary.txt`
- `fu_run{k}.csv`
- `fu_summary.txt`
- `radial.csv`

## 9) Troubleshooting

### IntelliJ no reconoce el proyecto

- abrir la raíz del repo, no `src/`
- recargar Maven
- verificar JDK 11+

### `run_*.sh` falla

- correr `./scripts/compile.sh`
- dar permisos si hace falta:

```bash
chmod +x scripts/compile.sh scripts/run_sim.sh scripts/run_analysis.sh scripts/run_viz.sh
```

### El visualizador muestra pocos frames

Eso depende del archivo generado y de `-w`:

- si `-w 50`, solo se escribe un frame cada 50 eventos
- si querés más frames, usá `-w 1` o un valor más chico
- `--max-frames` también puede submuestrear si es muy bajo

### La animación se ve robótica

- mantener `--interval 50`
- bajar `-w`
- subir `--max-frames`

## 10) Flujos recomendados

### Simulación rápida

```bash
./scripts/compile.sh
./scripts/run_sim.sh -N 100 -tf 5 --no-output
```

### Simulación para visualización

```bash
./scripts/compile.sh
./scripts/run_sim.sh -N 100 -tf 400 -w 1 -o results/traj_smooth.txt
./scripts/run_viz.sh results/traj_smooth.txt --interval 50 --max-frames 12000
```

### Análisis completo

```bash
./scripts/compile.sh
./scripts/run_analysis.sh --all -N 100 -tf 1000 --runs 5
```

