package analysis;

import simulation.Particle;
import simulation.SimulationResult;
import simulation.Simulator;

import java.io.*;
import java.util.*;

//TODO borrar esta clase

/**
 * Módulo de análisis de observables para el TP3.
 *
 * Análisis disponibles:
 *   A) Tiempo de ejecución vs N
 *   B) Cfc(t): colisiones acumuladas fresca→usada y pendiente J
 *   C) Fu(t):  fracción de partículas usadas y estado estacionario
 *   D) Perfiles radiales: ρ(S), |v_rad(S)|, Jin(S)
 *
 * Todos los resultados se escriben en la carpeta "results/" como archivos CSV.
 *
 * Uso:
 *   java Analysis [opciones]
 *   --timing              Ejecutar análisis A
 *   --cfc                 Ejecutar análisis B
 *   --fu                  Ejecutar análisis C
 *   --radial              Ejecutar análisis D
 *   --all                 Ejecutar todos
 *   -N    <int>           Partículas para B, C, D   (default: 100)
 *   -tf   <double>        Tiempo para B, C, D       (default: 10.0)
 *   --runs <int>          Realizaciones             (default: 5)
 *   --results-dir <dir>   Directorio de salida      (default: results)
 */
public class Analysis {

    private static final double DS     = 0.2;             // ancho de capa radial [m]
    private static final double S_MIN  = Simulator.SIGMA_OBS;   // 2.0
    private static final double S_MAX  = Simulator.SIGMA_WALL;  // 39.0

    public static void main(String[] args) throws Exception {

        // ── Defaults ──────────────────────────────────────────────────────────
        boolean doTiming = false, doCfc = false, doFu = false, doRadial = false;
        int    N          = 100;
        double tf         = 10.0;
        int    runs       = 5;
        String resultsDir = "results";

        // ── Parseo ────────────────────────────────────────────────────────────
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--timing":      doTiming = true;                             break;
                case "--cfc":         doCfc    = true;                             break;
                case "--fu":          doFu     = true;                             break;
                case "--radial":      doRadial = true;                             break;
                case "--all":         doTiming = doCfc = doFu = doRadial = true;  break;
                case "-N":            N          = Integer.parseInt(args[++i]);    break;
                case "-tf":           tf         = Double.parseDouble(args[++i]); break;
                case "--runs":        runs       = Integer.parseInt(args[++i]);    break;
                case "--results-dir": resultsDir = args[++i];                     break;
                default:
                    System.err.println("Opción desconocida: " + args[i]);
                    System.exit(1);
            }
        }

        if (!doTiming && !doCfc && !doFu && !doRadial) {
            doTiming = doCfc = doFu = doRadial = true;  // default: all
        }

        new File(resultsDir).mkdirs();

        if (doTiming) runTiming(resultsDir);
        if (doCfc)    runCfc(N, tf, runs, resultsDir);
        if (doFu)     runFu (N, tf, runs, resultsDir);
        if (doRadial) runRadial(N, tf * 2, runs, resultsDir);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  A) Tiempo de ejecución vs N
    // ─────────────────────────────────────────────────────────────────────────

    private static void runTiming(String dir) throws Exception {
        int[]  Nvalues = {10, 20, 50, 100, 200};
        int    runsT   = 3;
        double tfT     = 5.0;

        System.out.println("\n═══ A) Tiempo de ejecución vs N ═══");

        String csvPath = dir + "/timing.csv";
        try (PrintWriter w = new PrintWriter(csvPath)) {
            w.println("N,avg_time_s,std_time_s,avg_events");

            for (int N : Nvalues) {
                double[] times  = new double[runsT];
                long[]   events = new long[runsT];

                for (int r = 0; r < runsT; r++) {
                    Simulator        sim    = new Simulator(N, tfT);
                    SimulationResult result = sim.run(null, (long)(42 + r), 1, 0);
                    times[r]  = result.simTime;
                    events[r] = result.events;
                    System.out.printf("  N=%-4d run=%d  t=%.3fs  eventos=%d%n",
                                      N, r + 1, result.simTime, result.events);
                }

                double avgT  = mean(times);
                double stdT  = std(times);
                double avgEv = mean(events);
                System.out.printf("  N=%-4d PROMEDIO: %.4fs ± %.4fs%n%n", N, avgT, stdT);
                w.printf("%d,%.6f,%.6f,%.1f%n", N, avgT, stdT, avgEv);
            }
        }
        System.out.println("  → Guardado en " + csvPath);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  B) Cfc(t) y pendiente J
    // ─────────────────────────────────────────────────────────────────────────

    private static void runCfc(int N, double tf, int runs, String dir) throws Exception {
        System.out.printf("%n═══ B) Cfc(t) — N=%d  tf=%.1f  runs=%d ═══%n", N, tf, runs);

        double[] slopes = new double[runs];

        for (int r = 0; r < runs; r++) {
            Simulator        sim    = new Simulator(N, tf);
            SimulationResult result = sim.run(null, (long)(100 + r), 1, 0);

            List<double[]> hist = result.cfcHistory;
            String csvPath = String.format("%s/cfc_run%d.csv", dir, r + 1);

            try (PrintWriter w = new PrintWriter(csvPath)) {
                w.println("t,cfc");
                for (double[] row : hist) {
                    w.printf("%.8f,%.0f%n", row[0], row[1]);
                }
            }

            // Regresión lineal: cfc(t) = J·t + b
            slopes[r] = linearSlopeOrigin(hist);
            System.out.printf("  run=%d  Cfc_final=%.0f  J=%.4f  eventos=%d%n",
                              r + 1,
                              hist.get(hist.size() - 1)[1],
                              slopes[r], result.events);
        }

        double Jmean = mean(slopes);
        double Jstd  = std(slopes);
        System.out.printf("  J = %.4f ± %.4f  [colisiones/s]%n", Jmean, Jstd);

        // Resumen
        String sumPath = dir + "/cfc_summary.txt";
        try (PrintWriter w = new PrintWriter(sumPath)) {
            w.printf("N=%d  tf=%.1f  runs=%d%n", N, tf, runs);
            w.printf("J_mean=%.6f%n", Jmean);
            w.printf("J_std=%.6f%n",  Jstd);
        }
        System.out.println("  → Datos en " + dir + "/cfc_run*.csv");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  C) Fu(t) y estado estacionario
    // ─────────────────────────────────────────────────────────────────────────

    private static void runFu(int N, double tf, int runs, String dir) throws Exception {
        System.out.printf("%n═══ C) Fu(t) — N=%d  tf=%.1f  runs=%d ═══%n", N, tf, runs);

        double[] Fests  = new double[runs];
        double[] tStats = new double[runs];

        for (int r = 0; r < runs; r++) {
            Simulator        sim    = new Simulator(N, tf);
            SimulationResult result = sim.run(null, (long)(200 + r), 1, 0);

            List<double[]> hist = result.fuHistory;
            String csvPath = String.format("%s/fu_run%d.csv", dir, r + 1);

            try (PrintWriter w = new PrintWriter(csvPath)) {
                w.println("t,fu");
                for (double[] row : hist) {
                    w.printf("%.8f,%.6f%n", row[0], row[1]);
                }
            }

            // Valor estacionario: promedio del último 20 % del tiempo
            double F_est = stationaryValue(hist);
            double t_stat = timeToStationary(hist, F_est);
            Fests[r]  = F_est;
            tStats[r] = t_stat;

            System.out.printf("  run=%d  F_est=%.4f  t_stat≈%.3fs  eventos=%d%n",
                              r + 1, F_est, t_stat, result.events);
        }

        double F_mean = mean(Fests);
        double t_mean = mean(tStats);
        System.out.printf("  F_est = %.4f ± %.4f%n", F_mean, std(Fests));
        System.out.printf("  t_stat ≈ %.3f ± %.3f s%n", t_mean, std(tStats));

        String sumPath = dir + "/fu_summary.txt";
        try (PrintWriter w = new PrintWriter(sumPath)) {
            w.printf("N=%d  tf=%.1f  runs=%d%n", N, tf, runs);
            w.printf("F_est_mean=%.6f%n", F_mean);
            w.printf("F_est_std=%.6f%n",  std(Fests));
            w.printf("t_stat_mean=%.6f%n", t_mean);
            w.printf("t_stat_std=%.6f%n",  std(tStats));
        }
        System.out.println("  → Datos en " + dir + "/fu_run*.csv");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  D) Perfiles radiales
    // ─────────────────────────────────────────────────────────────────────────

    private static void runRadial(int N, double tf, int runs, String dir) throws Exception {
        System.out.printf("%n═══ D) Perfiles radiales — N=%d  tf=%.1f  runs=%d ═══%n",
                          N, tf, runs);

        int    nLayers    = (int) Math.ceil((S_MAX - S_MIN) / DS);
        double[] rhoAcc  = new double[nLayers];
        double[] vAcc    = new double[nLayers];
        int[]    cntAcc  = new int   [nLayers];
        int      nSnaps  = 0;

        int snapEvery = Math.max(1, N * 5);   // ≈ 1 snapshot cada 5N eventos

        for (int r = 0; r < runs; r++) {
            Simulator        sim    = new Simulator(N, tf);
            SimulationResult result = sim.run(null, (long)(300 + r), 1, snapEvery);

            System.out.printf("  run=%d  snapshots=%d  eventos=%d%n",
                              r + 1, result.radialSnapshots.size(), result.events);

            for (Object[] snapEntry : result.radialSnapshots) {
                double[][]  snap = (double[][]) snapEntry[1];
                nSnaps++;

                for (double[] pd : snap) {
                    if ((int) pd[4] != Particle.FRESH) continue;   // solo frescas
                    double x = pd[0], y = pd[1], vx = pd[2], vy = pd[3];
                    double rp  = Math.sqrt(x * x + y * y);
                    double vr  = (x * vx + y * vy) / rp;           // vel. radial
                    if (vr >= 0) continue;                          // solo hacia el centro

                    int layer = (int) ((rp - S_MIN) / DS);
                    if (layer >= 0 && layer < nLayers) {
                        rhoAcc[layer] += 1;
                        vAcc  [layer] += Math.abs(vr);
                        cntAcc[layer] += 1;
                    }
                }
            }
        }

        // Promediar y escribir CSV
        String csvPath = dir + "/radial.csv";
        try (PrintWriter w = new PrintWriter(csvPath)) {
            w.println("S,rho,v_mean,Jin");

            for (int k = 0; k < nLayers; k++) {
                double sInner = S_MIN + k * DS;
                double sOuter = sInner + DS;
                double sCenter = 0.5 * (sInner + sOuter);
                double area = Math.PI * (sOuter * sOuter - sInner * sInner);

                double rho   = (nSnaps > 0 && area > 0) ? rhoAcc[k] / (nSnaps * area) : 0;
                double vmean = (cntAcc[k] > 0) ? vAcc[k] / cntAcc[k] : 0;
                double jin   = rho * vmean;

                w.printf("%.4f,%.8f,%.8f,%.8f%n", sCenter, rho, vmean, jin);
            }
        }
        System.out.println("  → Guardado en " + csvPath);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utilidades estadísticas
    // ─────────────────────────────────────────────────────────────────────────

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

    /**
     * Pendiente de la regresión lineal por mínimos cuadrados de la lista
     * de puntos (t, valor). Se ignoran los primeros puntos si hay muy pocos.
     */
    private static double linearSlopeOrigin(List<double[]> hist) {
        if (hist.size() < 2) return 0;
        // Regresión: y = a + b*x  →  b = (n·Σxy - Σx·Σy) / (n·Σxx - (Σx)²)
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        int n = hist.size();
        for (double[] row : hist) {
            double x = row[0], y = row[1];
            sumX  += x;
            sumY  += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denom = n * sumXX - sumX * sumX;
        if (Math.abs(denom) < 1e-12) return 0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    /**
     * Valor estacionario de Fu: promedio del último 20 % de los datos.
     */
    private static double stationaryValue(List<double[]> hist) {
        int cut = (int) (hist.size() * 0.8);
        double sum = 0;
        int cnt = 0;
        for (int i = cut; i < hist.size(); i++) {
            sum += hist.get(i)[1];
            cnt++;
        }
        return (cnt > 0) ? sum / cnt : hist.get(hist.size() - 1)[1];
    }

    /**
     * Tiempo al que Fu alcanza por primera vez el 90 % del valor estacionario.
     */
    private static double timeToStationary(List<double[]> hist, double Fest) {
        double threshold = 0.9 * Fest;
        for (double[] row : hist) {
            if (row[1] >= threshold) return row[0];
        }
        return hist.get(hist.size() - 1)[0];
    }
}
