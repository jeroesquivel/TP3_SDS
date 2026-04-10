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
    private int          nUsed;

    // ── Observables ───────────────────────────────────────────────────────────
    private List<double[]> cfcHistory;
    private List<double[]> fuHistory;
    private List<Object[]> radialSnapshots;

    public Simulator(int N, double tf) {
        this.N  = N;
        this.tf = tf;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  API pública
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ejecuta la simulación y devuelve un SimulationResult.
     *
     * @param outputFile   ruta del archivo de trayectoria (null → no escribir)
     * @param seed         semilla aleatoria (null → no fijar)
     * @param writeEvery   escribir trayectoria cada W eventos
     * @param snapshotEvery guardar snapshot para perfiles radiales cada K eventos (≤0 = nunca)
     */
    public SimulationResult run(
            String  outputFile,
            Long    seed,
            int     writeEvery,
            int     snapshotEvery) throws IOException {

        particles = initParticles(seed);
        pq        = new PriorityQueue<>();
        tNow      = 0.0;
        nUsed     = 0;

        cfcHistory      = new ArrayList<>();
        fuHistory       = new ArrayList<>();
        radialSnapshots = new ArrayList<>();

        cfcHistory.add(new double[]{0.0, 0});
        fuHistory .add(new double[]{0.0, 0.0});

        // ── Cola inicial ──────────────────────────────────────────────────────
        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                pushPP(i, j);
            }
            pushObs(i);
            pushWall(i);
        }

        // ── Archivo de trayectoria ────────────────────────────────────────────
        PrintWriter writer = null;
        if (outputFile != null) {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
            writeFrame(writer, tNow);
        }

        int  eventCount = 0;
        int  cfc        = 0;
        long simStart   = System.nanoTime();

        // ── Bucle principal ───────────────────────────────────────────────────
        while (!pq.isEmpty()) {

            Event ev = pq.poll();

            if (ev.time > tf) break;

            // Invalidación lazy
            if (!ev.isValid(particles)) continue;

            // Vuelo libre: avanzar todas las partículas
            double dt = ev.time - tNow;
            if (dt > 0.0) {
                for (Particle p : particles) p.advance(dt);
            }
            tNow = ev.time;

            // ── Aplicar colisión ──────────────────────────────────────────────
            int[] involved;

            switch (ev.type) {

                case Event.PP: {
                    bouncePP(particles[ev.i], particles[ev.j]);
                    involved = new int[]{ev.i, ev.j};
                    break;
                }

                case Event.OBS: {
                    boolean changed = bounceObs(particles[ev.i]);
                    if (changed) {
                        nUsed++;
                        cfc++;
                        cfcHistory.add(new double[]{tNow, cfc});
                    }
                    involved = new int[]{ev.i};
                    break;
                }

                default: { // WALL
                    boolean changed = bounceWall(particles[ev.i]);
                    if (changed) nUsed--;
                    involved = new int[]{ev.i};
                    break;
                }
            }

            fuHistory.add(new double[]{tNow, (double) nUsed / N});
            eventCount++;

            // ── Reprogramar eventos de las partículas involucradas ────────────
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

            // ── Escribir trayectoria ──────────────────────────────────────────
            if (writer != null && eventCount % writeEvery == 0) {
                writeFrame(writer, tNow);
            }

            // ── Snapshot para perfiles radiales ───────────────────────────────
            if (snapshotEvery > 0 && eventCount % snapshotEvery == 0) {
                double[][] snap = new double[N][5];
                for (int k = 0; k < N; k++) {
                    Particle p = particles[k];
                    snap[k][0] = p.x;  snap[k][1] = p.y;
                    snap[k][2] = p.vx; snap[k][3] = p.vy;
                    snap[k][4] = p.state;
                }
                radialSnapshots.add(new Object[]{tNow, snap});
            }
        }

        double simTime = (System.nanoTime() - simStart) / 1e9;
        if (writer != null) writer.close();

        return new SimulationResult(
                particles, cfcHistory, fuHistory, radialSnapshots, simTime, eventCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tiempos de colisión
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tiempo hasta colisión entre las partículas i y j.
     * Fórmula analítica exacta; retorna Double.NaN si no hay colisión futura.
     */
    static double timePP(Particle pi, Particle pj) {
        double dx  = pj.x  - pi.x;
        double dy  = pj.y  - pi.y;
        double dvx = pj.vx - pi.vx;
        double dvy = pj.vy - pi.vy;

        double dvdr = dvx * dx  + dvy * dy;
        if (dvdr >= 0) return Double.NaN;            // se alejan

        double dvdv = dvx * dvx + dvy * dvy;
        if (dvdv == 0) return Double.NaN;

        double drdr = dx  * dx  + dy  * dy;
        double d    = dvdr * dvdr - dvdv * (drdr - SIGMA_PP * SIGMA_PP);
        if (d < 0) return Double.NaN;                // se esquivan

        return -(dvdr + Math.sqrt(d)) / dvdv;
    }

    /**
     * Tiempo hasta colisión de la partícula p con el obstáculo central fijo.
     * La geometría es idéntica a PP pero el obstáculo está en el origen (v=0).
     */
    static double timeObs(Particle p) {
        double dvdr = p.vx * p.x  + p.vy * p.y;
        if (dvdr >= 0) return Double.NaN;            // se aleja del origen

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
        // Para partícula dentro: drdr < σ² → d = dvdr² + dvdv·(σ²-drdr) > 0 siempre
        double d    = dvdr * dvdr - dvdv * (drdr - SIGMA_WALL * SIGMA_WALL);
        if (d < 0) return Double.NaN;   // no debería ocurrir si la partícula está dentro

        double tc = (-dvdr + Math.sqrt(d)) / dvdv;
        return tc > EPS ? tc : Double.NaN;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Operadores de colisión
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Colisión elástica entre dos partículas de masa igual (m=1).
     *   J = 2·mi·mj / (σ·(mi+mj)) · (Δv·Δr)  →  con mi=mj=1: J = (Δv·Δr)/σ
     */
    static void bouncePP(Particle pi, Particle pj) {
        double dx  = pj.x  - pi.x;
        double dy  = pj.y  - pi.y;
        double dvx = pj.vx - pi.vx;
        double dvy = pj.vy - pi.vy;

        double J  = (dvx * dx + dvy * dy) / SIGMA_PP;   // = dvdr / σ
        double Jx = J * dx / SIGMA_PP;
        double Jy = J * dy / SIGMA_PP;

        pi.vx += Jx;   pi.vy += Jy;    // vxi_nuevo = vxi + Jx/mi  (mi=1)
        pj.vx -= Jx;   pj.vy -= Jy;    // vxj_nuevo = vxj - Jx/mj  (mj=1)

        pi.count++;
        pj.count++;
    }

    /**
     * Colisión elástica de la partícula con el obstáculo central fijo (masa infinita).
     * Invierte la componente normal de la velocidad: v_nueva = v - 2·(v·en)·en
     * en = posición/|posición|  (apunta del centro al punto de contacto)
     *
     * @return true si el estado cambió (fresca → usada)
     */
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

    /**
     * Colisión elástica de la partícula con el borde circular interior.
     * Normal apunta hacia adentro del recinto: en = -posición/|posición|
     *
     * @return true si el estado cambió (usada → fresca)
     */
    static boolean bounceWall(Particle p) {
        double r   = Math.sqrt(p.x * p.x + p.y * p.y);
        double enx = -p.x / r;   // normal interior
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

    /**
     * Coloca N partículas aleatoriamente en el anillo (2, 39) sin superposición.
     * Se muestrea r² de forma uniforme para obtener distribución uniforme en área.
     */
    private Particle[] initParticles(Long seed) {
        Random rng = (seed != null) ? new Random(seed) : new Random();
        Particle[] ps = new Particle[N];

        double rMin2   = SIGMA_OBS  * SIGMA_OBS;    //  4.0
        double rMax2   = SIGMA_WALL * SIGMA_WALL;   // 1521.0
        double sigma2  = SIGMA_PP   * SIGMA_PP;     //  4.0

        for (int idx = 0; idx < N; idx++) {
            boolean placed = false;
            for (int attempt = 0; attempt < 500_000; attempt++) {
                // Muestreo uniforme en área del anillo
                double r2    = rMin2 + rng.nextDouble() * (rMax2 - rMin2);
                double r     = Math.sqrt(r2);
                double angle = rng.nextDouble() * 2.0 * Math.PI;
                double x     = r * Math.cos(angle);
                double y     = r * Math.sin(angle);

                // Verificar no superposición
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
