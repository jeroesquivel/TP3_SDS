# TP3_SDS

Simulador EDMD para TP3 (SDS), en Java 11+, sin dependencias externas.

## IntelliJ IDEA (import directo)

El proyecto ahora incluye `pom.xml`, por lo que IntelliJ lo reconoce como proyecto Java/Maven.

1. Abrir IntelliJ IDEA.
2. `File -> Open...` y seleccionar la carpeta del repo.
3. Cuando IDEA detecte Maven, aceptar `Load Maven Project`.
4. Esperar la sincronizacion.
5. Ejecutar `Main`, `Analysis` o `Visualizer` desde el gutter o creando una Run Configuration tipo `Application`.

## Maven rapido

```bash
mvn compile
mvn exec:java -Dexec.mainClass=Main -Dexec.args="-N 100 -tf 200 -s 42 -w 50 -o results/traj.txt"
```

Para correr otros entrypoints:

```bash
mvn exec:java -Dexec.mainClass=Analysis -Dexec.args="--all -N 100 -tf 1000 --runs 5"
mvn exec:java -Dexec.mainClass=Visualizer -Dexec.args="results/traj.txt --interval 50 --max-frames 2000"
```
