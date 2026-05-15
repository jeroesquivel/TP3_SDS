package simulation;

import java.io.*;
import java.util.*;

/**
 * Motor de simulación EDMD para el sistema de N partículas
 * en un recinto circular con obstáculo central fijo.
 *
 * Algoritmo: priority queue (min-heap) con invalidación lazy por contador de colisiones.
 *
 * Tipos de colisión:
 *   1) Partícula–Partícula    (colisión elástica, masas iguales)
 *   2) Partícula–Obstáculo    (obstáculo fijo en el origen, radio r0=1)
 *   3) Partícula–Borde        (borde circular interior, radio efectivo 39)
 */
public class Simulator {

    // ── Constantes físicas ────────────────────────────────────────────────────
    public static final double R_RECINTO  = 40.0;
    public static final double R_OBS      = 1.0;
    public static final double R_PARTICLE = 1.0;
    public static final double V0         = 1.0;

    public static final double SIGMA_PP   = R_PARTICLE + R_PARTICLE;   // 2.0
    public static final double SIGMA_OBS  = R_PARTICLE + R_OBS;        // 2.0
    public static final double SIGMA_WALL = R_RECINTO  - R_PARTICLE;   // 39.0

    /** Umbral mínimo de tiempo para evitar re-procesar el evento inmediato. */
    private static final double EPS = 1e-10;

    // ── Estado de la simulación ───────────────────────────────────────────────
    private final int    N;
    private final double tf;
    private Particle[]   particles;
    private PriorityQueue<Event> pq;
    private double       tNow;

    public Simulator(int N, double tf) {
        this.N  = N;
        this.tf = tf;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  API pública
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ejecuta la simulación volcando todos los frames al archivo de trayectoria.
     * Pensado para N y tf chicos/medios donde el archivo denso es manejable.
     */
    public SimulationResult run(
            String outputFile,
            Long   seed,
            int    writeEvery) throws IOException {
        return runInternal(outputFile, seed, writeEvery,
                           null, Double.POSITIVE_INFINITY, /*stream=*/false);
    }

    /**
     * Ejecuta la simulación en modo STREAMING: escribe directamente a disco
     * sin acumular historia en memoria. Recomendado para benchmarks.
     *
     * @param outputFile       archivo de trayectoria (null → no se escribe).
     * @param analysisFile     archivo ligero de análisis (null → no se escribe).
     *                         Contiene registros EVT y SNAP para reconstruir
     *                         Cfc(t), Fu(t) y perfiles radiales.
     * @param seed             semilla (null → aleatorio)
     * @param writeEvery       frecuencia (en eventos) de escritura de trayectoria
     * @param radialSnapDt     intervalo de tiempo (en s) entre snapshots
     *                         radiales dentro del analysisFile. ≤0 desactiva.
     */
    public SimulationResult runStream(
            String outputFile,
            String analysisFile,
            Long   seed,
            int    writeEvery,
            double radialSnapDt) throws IOException {
        return runInternal(outputFile, seed, writeEvery,
                           analysisFile, radialSnapDt, /*stream=*/true);
    }

    /**
     * Ejecuta la simulación en modo LIGERO: sólo escribe un frame cada 1000
     * eventos. El nombre de salida se deriva insertando "_light" antes de la
     * extensión. Recomendado para simulaciones largas con N grande donde el
     * archivo denso resulta inmanejable.
     */
    public SimulationResult runLight(
            String outputFile,
            Long   seed) throws IOException {

        String lightFile = null;
        if (outputFile != null) {
            int dot = outputFile.lastIndexOf('.');
            if (dot >= 0) {
                lightFile = outputFile.substring(0, dot) + "_light" + outputFile.substring(dot);
            } else {
                lightFile = outputFile + "_light";
            }
        }

        return runInternal(lightFile, seed, /*writeEvery=*/1000,
                /*analysisFile=*/null, Double.POSITIVE_INFINITY, /*stream=*/false);
    }

    private SimulationResult runInternal(
            String  outputFile,
            Long    seed,
            int     writeEvery,
            String  analysisFile,
            double  radialSnapDt,
            boolean stream) throws IOException {

        particles = initParticles(seed);
        pq        = new PriorityQueue<>();
        tNow      = 0.0;

        // ── Cola inicial ──────────────────────────────────────────────────────
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                pushPP(i, j);
            }
            pushObs(i);
            pushWall(i);
        }

        // ── Archivo de trayectoria completo (animaciones) ─────────────────────
        PrintWriter writer = null;
        if (outputFile != null) {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
            writeFrame(writer, tNow);
        }

        // ── Archivo liviano de análisis ───────────────────────────────────────
        PrintWriter aWriter = null;
        double nextSnap = Double.POSITIVE_INFINITY;
        if (analysisFile != null) {
            aWriter = new PrintWriter(new BufferedWriter(new FileWriter(analysisFile)));
            aWriter.printf(Locale.US, "# EDMD analysis file%n");
            aWriter.printf(Locale.US, "# N %d%n", N);
            aWriter.printf(Locale.US, "# tf %.8f%n", tf);
            aWriter.printf(Locale.US, "# seed %s%n", seed == null ? "random" : seed);
            aWriter.printf(Locale.US, "# radialSnapDt %.8f%n", radialSnapDt);
            aWriter.printf(Locale.US, "# Registros:%n");
            aWriter.printf(Locale.US, "#   EVT OBS  t pid    (colision con obstaculo, cambia fresca->usada)%n");
            aWriter.printf(Locale.US, "#   EVT WALL t pid    (colision con borde, cambia usada->fresca)%n");
            aWriter.printf(Locale.US, "#   SNAP t N_fresh_in%n");
            aWriter.printf(Locale.US, "#     x y vx vy state   (solo particulas frescas con R.v<0)%n");

            if (radialSnapDt > 0) {
                writeAnalysisSnap(aWriter, tNow);    // snapshot inicial
                nextSnap = radialSnapDt;
            }
        }

        int  eventCount = 0;
        long simStart   = System.nanoTime();

        // ── Bucle principal ───────────────────────────────────────────────────
        while (!pq.isEmpty()) {

            Event ev = pq.poll();

            if (ev.time > tf) break;

            if (!ev.isValid(particles)) continue;

            // Snapshots de análisis periódicos: se emiten ANTES de procesar el
            // evento, si el tiempo del evento cruza el próximo tick.
            if (aWriter != null) {
                while (nextSnap <= ev.time && nextSnap <= tf) {
                    double dtSnap = nextSnap - tNow;
                    if (dtSnap > 0) for (Particle p : particles) p.advance(dtSnap);
                    tNow = nextSnap;
                    writeAnalysisSnap(aWriter, tNow);
                    nextSnap += radialSnapDt;
                }
            }

            double dt = ev.time - tNow;
            if (dt > 0.0) {
                for (Particle p : particles) p.advance(dt);
            }
            tNow = ev.time;

            int[] involved;
            switch (ev.type) {
                case Event.PP: {
                    bouncePP(particles[ev.i], particles[ev.j]);
                    involved = new int[]{ev.i, ev.j};
                    break;
                }
                case Event.OBS: {
                    boolean changed = bounceObs(particles[ev.i]);
                    if (changed && stream && aWriter != null) {
                        aWriter.printf(Locale.US, "EVT OBS %.8f %d%n", tNow, ev.i);
                    }
                    involved = new int[]{ev.i};
                    break;
                }
                default: { // WALL
                    boolean changed = bounceWall(particles[ev.i]);
                    if (changed && stream && aWriter != null) {
                        aWriter.printf(Locale.US, "EVT WALL %.8f %d%n", tNow, ev.i);
                    }
                    involved = new int[]{ev.i};
                    break;
                }
            }

            eventCount++;

            // Reprogramación
            Set<Long> pairsDone = new HashSet<>();
            for (int idx : involved) {
                for (int k = 0; k < N; k++) {
                    if (k == idx) continue;
                    int  ii      = Math.min(idx, k);
                    int  jj      = Math.max(idx, k);
                    long pairKey = (long) ii * N + jj;
                    if (pairsDone.add(pairKey)) {
                        pushPP(ii, jj);
                    }
                }
                pushObs(idx);
                pushWall(idx);
            }

            if (writer != null && eventCount % writeEvery == 0) {
                writeFrame(writer, tNow);
            }
        }

        // Completar snapshots pendientes hasta tf
        if (aWriter != null) {
            while (nextSnap <= tf) {
                double dtSnap = nextSnap - tNow;
                if (dtSnap > 0) for (Particle p : particles) p.advance(dtSnap);
                tNow = nextSnap;
                writeAnalysisSnap(aWriter, tNow);
                nextSnap += radialSnapDt;
            }
        }

        double simTime = (System.nanoTime() - simStart) / 1e9;
        if (writer  != null) writer.close();
        if (aWriter != null) aWriter.close();

        return new SimulationResult(simTime, eventCount);
    }

    /**
     * Emite un snapshot radial en el archivo de análisis. Se escribe SÓLO
     * la información necesaria para la sección 1.4: partículas frescas con
     * velocidad radial hacia el centro (R·v &lt; 0). El resto se omite.
     */
    private void writeAnalysisSnap(PrintWriter w, double t) {
        int nFreshIn = 0;
        for (Particle p : particles) {
            if (p.state != Particle.FRESH) continue;
            double rdotv = p.x * p.vx + p.y * p.vy;
            if (rdotv < 0) nFreshIn++;
        }
        w.printf(Locale.US, "SNAP %.8f %d%n", t, nFreshIn);
        for (Particle p : particles) {
            if (p.state != Particle.FRESH) continue;
            double rdotv = p.x * p.vx + p.y * p.vy;
            if (rdotv >= 0) continue;
            w.printf(Locale.US, "%.6f %.6f %.6f %.6f %d%n",
                     p.x, p.y, p.vx, p.vy, p.state);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tiempos de colisión
    // ─────────────────────────────────────────────────────────────────────────

    static double timePP(Particle pi, Particle pj) {
        double dx  = pj.x  - pi.x;
        double dy  = pj.y  - pi.y;
        double dvx = pj.vx - pi.vx;
        double dvy = pj.vy - pi.vy;

        double dvdr = dvx * dx  + dvy * dy;
        if (dvdr >= 0) return Double.NaN;

        double dvdv = dvx * dvx + dvy * dvy;
        if (dvdv == 0) return Double.NaN;

        double drdr = dx  * dx  + dy  * dy;
        double d    = dvdr * dvdr - dvdv * (drdr - SIGMA_PP * SIGMA_PP);
        if (d < 0) return Double.NaN;

        return -(dvdr + Math.sqrt(d)) / dvdv;
    }

    static double timeObs(Particle p) {
        double dvdr = p.vx * p.x  + p.vy * p.y;
        if (dvdr >= 0) return Double.NaN;

        double dvdv = p.vx * p.vx + p.vy * p.vy;
        double drdr = p.x  * p.x  + p.y  * p.y;
        double d    = dvdr * dvdr - dvdv * (drdr - SIGMA_OBS * SIGMA_OBS);
        if (d < 0) return Double.NaN;

        return -(dvdr + Math.sqrt(d)) / dvdv;
    }

    /**
     * Tiempo hasta colisión de la partícula p con el borde circular interior.
     *
     * Se usa la fórmula completa sin filtrar por signo de dvdr: tc = (-dvdr + √d) / dvdv.
     * Para una partícula dentro del recinto, d > 0 siempre y tc > 0 siempre, lo que
     * garantiza que toda partícula tenga un evento de pared programado incluso cuando
     * dvdr < 0 (se acerca al centro antes de rebotar hacia la pared).
     */
    static double timeWall(Particle p) {
        double dvdv = p.vx * p.vx + p.vy * p.vy;
        if (dvdv == 0) return Double.NaN;

        double dvdr = p.vx * p.x  + p.vy * p.y;
        double drdr = p.x  * p.x  + p.y  * p.y;
        double d    = dvdr * dvdr - dvdv * (drdr - SIGMA_WALL * SIGMA_WALL);
        if (d < 0) return Double.NaN;

        double tc = (-dvdr + Math.sqrt(d)) / dvdv;
        return tc > EPS ? tc : Double.NaN;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Operadores de colisión
    // ─────────────────────────────────────────────────────────────────────────

    static void bouncePP(Particle pi, Particle pj) {
        double dx  = pj.x  - pi.x;
        double dy  = pj.y  - pi.y;
        double dvx = pj.vx - pi.vx;
        double dvy = pj.vy - pi.vy;

        double J  = (dvx * dx + dvy * dy) / SIGMA_PP;
        double Jx = J * dx / SIGMA_PP;
        double Jy = J * dy / SIGMA_PP;

        pi.vx += Jx;   pi.vy += Jy;
        pj.vx -= Jx;   pj.vy -= Jy;

        pi.count++;
        pj.count++;
    }

    static boolean bounceObs(Particle p) {
        double r   = Math.sqrt(p.x * p.x + p.y * p.y);
        double enx = p.x / r;
        double eny = p.y / r;

        double vn = p.vx * enx + p.vy * eny;
        p.vx -= 2.0 * vn * enx;
        p.vy -= 2.0 * vn * eny;

        boolean changed = (p.state == Particle.FRESH);
        if (changed) p.state = Particle.USED;

        p.count++;
        return changed;
    }

    static boolean bounceWall(Particle p) {
        double r   = Math.sqrt(p.x * p.x + p.y * p.y);
        double enx = -p.x / r;
        double eny = -p.y / r;

        double vn = p.vx * enx + p.vy * eny;
        p.vx -= 2.0 * vn * enx;
        p.vy -= 2.0 * vn * eny;

        boolean changed = (p.state == Particle.USED);
        if (changed) p.state = Particle.FRESH;

        p.count++;
        return changed;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers de la priority queue
    // ─────────────────────────────────────────────────────────────────────────

    private void pushPP(int i, int j) {
        double tc = timePP(particles[i], particles[j]);
        if (!Double.isNaN(tc) && tc > EPS) {
            pq.add(new Event(tNow + tc, Event.PP, i, j,
                             particles[i].count, particles[j].count));
        }
    }

    private void pushObs(int i) {
        double tc = timeObs(particles[i]);
        if (!Double.isNaN(tc) && tc > EPS) {
            pq.add(new Event(tNow + tc, Event.OBS, i, -1,
                             particles[i].count, -1));
        }
    }

    private void pushWall(int i) {
        double tc = timeWall(particles[i]);
        if (!Double.isNaN(tc) && tc > EPS) {
            pq.add(new Event(tNow + tc, Event.WALL, i, -1,
                             particles[i].count, -1));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Inicialización
    // ─────────────────────────────────────────────────────────────────────────

    private Particle[] initParticles(Long seed) {
        Random rng = (seed != null) ? new Random(seed) : new Random();
        Particle[] ps = new Particle[N];

        double rMin2   = SIGMA_OBS  * SIGMA_OBS;
        double rMax2   = SIGMA_WALL * SIGMA_WALL;
        double sigma2  = SIGMA_PP   * SIGMA_PP;

        for (int idx = 0; idx < N; idx++) {
            boolean placed = false;
            for (int attempt = 0; attempt < 500_000; attempt++) {
                double r2    = rMin2 + rng.nextDouble() * (rMax2 - rMin2);
                double r     = Math.sqrt(r2);
                double angle = rng.nextDouble() * 2.0 * Math.PI;
                double x     = r * Math.cos(angle);
                double y     = r * Math.sin(angle);

                boolean ok = true;
                for (int k = 0; k < idx; k++) {
                    double ddx = x - ps[k].x;
                    double ddy = y - ps[k].y;
                    if (ddx * ddx + ddy * ddy <= sigma2) {
                        ok = false;
                        break;
                    }
                }

                if (ok) {
                    double theta = rng.nextDouble() * 2.0 * Math.PI;
                    ps[idx] = new Particle(x, y,
                                           V0 * Math.cos(theta),
                                           V0 * Math.sin(theta));
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                throw new RuntimeException(
                    "No se pudo colocar la partícula " + (idx + 1) + "/" + N +
                    " tras 500 000 intentos. Reducí N.");
            }
        }
        return ps;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Escritura del archivo de salida
    // ─────────────────────────────────────────────────────────────────────────

    private void writeFrame(PrintWriter w, double t) {
        w.printf(Locale.US, "%d %.8f%n", N, t);
        for (Particle p : particles) {
            w.printf(Locale.US, "%.8f %.8f %.8f %.8f %.8f %d%n",
                     t, p.x, p.y, p.vx, p.vy, p.state);
        }
    }
}