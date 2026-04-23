package simulation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Entrada única para generar las simulaciones del TP:
 *   - Sección 1.1: timing vs N  → results/timing.csv
 *   - Secciones 1.2 – 1.4: trayectorias con distintos N → simulations/sim_N_*_run_*.txt
 *
 * Limpia simulations/ y results/ antes de arrancar. Después de correr esto
 * podés ejecutar analysis.Analyze y luego el plotter de Python.
 */
public class Simulate {

    // ── Configuración timing (sección 1.1) ───────────────────────────────────
    private static final int[]  TIMING_N     = {10, 20, 50, 100, 200, 400, 800};
    private static final int    TIMING_RUNS  = 10;
    private static final double TIMING_TF    = 500.0;

    // ── Configuración trayectorias (secciones 1.2 – 1.4) ─────────────────────
    private static final int[]  TRAJ_N       = {10, 50, 100, 200, 400, 800};
    private static final int    TRAJ_RUNS    = 1;
    private static final double TRAJ_TF      = 1500.0;
    private static final int    TRAJ_LIGHT_N = 300;   // N≥ esto → runLight

    public static void main(String[] args) throws IOException {
        Path simsDir    = Paths.get("simulations");
        Path resultsDir = Paths.get("results");
        cleanDir(simsDir);
        cleanDir(resultsDir);
        Files.createDirectories(simsDir);
        Files.createDirectories(resultsDir);

        runTiming(resultsDir);
        runTrajectories(simsDir);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Sección 1.1 — timing vs N
    // ─────────────────────────────────────────────────────────────────────────
    private static void runTiming(Path resultsDir) throws IOException {
        System.out.println("\n═══ 1.1) Tiempo de ejecución vs N ═══");

        Path csvPath = resultsDir.resolve("timing.csv");
        try (PrintWriter w = new PrintWriter(csvPath.toFile())) {
            w.println("N,avg_time_s,std_time_s,avg_events");

            for (int N : TIMING_N) {
                double[] times  = new double[TIMING_RUNS];
                long[]   events = new long[TIMING_RUNS];

                for (int r = 0; r < TIMING_RUNS; r++) {
                    Simulator sim = new Simulator(N, TIMING_TF);
                    SimulationResult result = sim.runStream(null, null, (long)(42 + r), 1, -1);
                    times[r]  = result.simTime;
                    events[r] = result.events;
                    System.out.printf("  N=%-4d run=%d  t=%.6fs  eventos=%d%n",
                            N, r + 1, result.simTime, result.events);
                }

                double avgT  = mean(times);
                double stdT  = std(times);
                double avgEv = mean(events);
                System.out.printf("  N=%-4d PROMEDIO: %.6fs ± %.6fs%n%n", N, avgT, stdT);
                w.printf("%d,%.6f,%.6f,%.1f%n", N, avgT, stdT, avgEv);
            }
        }
        System.out.println("  → Guardado en " + csvPath);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Secciones 1.2 – 1.4 — trayectorias para analizar
    // ─────────────────────────────────────────────────────────────────────────
    private static void runTrajectories(Path simsDir) throws IOException {
        System.out.println("\n═══ 1.2 – 1.4) Trayectorias para análisis ═══");

        for (int N : TRAJ_N) {
            for (int run = 1; run <= TRAJ_RUNS; run++) {
                Simulator sim = new Simulator(N, TRAJ_TF);
                String outputFile = simsDir.resolve("sim_N_" + N + "_run_" + run + ".txt").toString();
                SimulationResult result;
                if (N >= TRAJ_LIGHT_N) {
                    result = sim.runLight(outputFile, (long) run);
                } else {
                    result = sim.run(outputFile, (long) run, 1);
                }
                System.out.printf("  N=%-4d run=%d  eventos=%d  t=%.3fs%n",
                        N, run, result.events, result.simTime);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utilidades
    // ─────────────────────────────────────────────────────────────────────────
    private static void cleanDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); }
                    catch (IOException e) { throw new RuntimeException(e); }
                });
        }
    }

    private static double mean(double[] a) {
        double s = 0;
        for (double v : a) s += v;
        return s / a.length;
    }

    private static double mean(long[] a) {
        double s = 0;
        for (long v : a) s += v;
        return s / a.length;
    }

    private static double std(double[] a) {
        double m = mean(a);
        double s = 0;
        for (double v : a) s += (v - m) * (v - m);
        return Math.sqrt(s / Math.max(a.length - 1, 1));
    }
}