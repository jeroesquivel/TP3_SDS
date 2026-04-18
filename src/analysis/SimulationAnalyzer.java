package analysis;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SimulationAnalyzer {
    private static final double OBSTACLE_RADIUS = 1.0;   // r0 [m]
    private static final double PARTICLE_RADIUS  = 1.0;  // r  [m]
    /** Default radial shell width [m] as required by section 1.4 */
    private static final double DEFAULT_DS = 0.2;

    public static void analyze(String inputFilePath) throws IOException {
        analyze(inputFilePath, DEFAULT_DS);
    }

    /**
     * Full analysis pipeline.
     * <p>
     * Streaming implementation: the input file is read snapshot-by-snapshot and
     * Cfc, Fu and radial outputs are emitted as we go. Nothing is accumulated
     * in memory except the previous-snapshot colour array (length N) and a
     * small bucket map per snapshot. This is necessary for large runs
     * (e.g. N=800, tf=1000) where the full trajectory does not fit in RAM.
     *
     * @param inputFilePath path to the simulator output file
     * @param dS            radial shell width in metres (section 1.4)
     */
    public static void analyze(String inputFilePath, double dS) throws IOException {

        Path inputPath = Paths.get(inputFilePath);
        String baseName = inputPath.getFileName().toString();
        // Strip extension if present
        if (baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
        }

        // Make sure results/ directory exists
        Files.createDirectories(Paths.get("results"));

        // Output file paths
        String cfcPath     = "results/cfc_"     + baseName + ".txt";
        String fuPath      = "results/fu_"      + baseName + ".txt";
        String radialPath  = "results/radial_"  + baseName + ".txt";

        System.out.println("File: " + inputFilePath);
        System.out.println("  Shell width: " + dS + " m  (streaming mode)");

        // -----------------------------------------------------------------------
        // Single streaming pass: read snapshot by snapshot; write Cfc, Fu and
        // radial outputs concurrently. Only one Snapshot lives in memory at a
        // time, plus a prevColor[] array of length N.
        // -----------------------------------------------------------------------
        double S_min = OBSTACLE_RADIUS + PARTICLE_RADIUS; // 2.0 m

        int[] prevColor = null;
        long cumulativeCfc = 0;
        int  snapshotsSeen = 0;
        int  N             = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath), 1 << 20);
             PrintWriter cfcW = new PrintWriter(new BufferedWriter(new FileWriter(cfcPath)));
             PrintWriter fuW  = new PrintWriter(new BufferedWriter(new FileWriter(fuPath)));
             PrintWriter radW = new PrintWriter(new BufferedWriter(new FileWriter(radialPath)))) {

            cfcW.println("# t\tCfc");
            fuW.println("# t\tNu\tFu");
            radW.println("# Radial profiles of inward-moving fresh particles");
            radW.println("# Shell width dS = " + dS + " m");
            radW.println("# Columns: S_center  count  rho_fin[1/m^2]  v_fin[m/s]  J_in[1/(m^2*s)]");
            radW.println("#");

            Snapshot snap;
            while ((snap = readSnapshot(br)) != null) {
                if (snapshotsSeen == 0) {
                    N = snap.n;
                    prevColor = new int[N];
                    for (int i = 0; i < Math.min(N, snap.color.length); i++) {
                        prevColor[i] = snap.color[i];
                    }
                    // 1.2 — initial Cfc row (0)
                    cfcW.printf(Locale.US, "%.8f\t%d%n", snap.time, cumulativeCfc);
                } else {
                    // 1.2 — count fresh→used transitions vs. previous snapshot
                    int size = Math.min(N, snap.color.length);
                    for (int i = 0; i < size; i++) {
                        if (prevColor[i] == 0 && snap.color[i] == 1) {
                            cumulativeCfc++;
                        }
                        prevColor[i] = snap.color[i];
                    }
                    cfcW.printf(Locale.US, "%.8f\t%d%n", snap.time, cumulativeCfc);
                }

                // 1.3 — Fu(t)
                int nu = 0;
                for (int i = 0; i < snap.n; i++) {
                    if (snap.color[i] == 1) nu++;
                }
                double fu = (N > 0) ? (double) nu / N : 0.0;
                fuW.printf(Locale.US, "%.8f\t%d\t%.8f%n", snap.time, nu, fu);

                // 1.4 — radial profile for this snapshot
                writeRadialSnapshot(radW, snap, dS, S_min);

                snapshotsSeen++;
            }
        }

        System.out.println("  Time steps : " + snapshotsSeen);
        System.out.println("  Particles  : " + N);
        System.out.println("  Written: " + cfcPath);
        System.out.println("  Written: " + fuPath);
        System.out.println("  Written: " + radialPath);

        if (snapshotsSeen == 0) {
            throw new IOException("No time steps found in " + inputFilePath);
        }
    }

    // -----------------------------------------------------------------------
    // Streaming parsing — one snapshot at a time
    // -----------------------------------------------------------------------

    /** A single snapshot. Arrays of length n (not full history). */
    private static class Snapshot {
        double time;
        int    n;
        double[] x, y, vx, vy;
        int[]    color;

        Snapshot(int n, double time) {
            this.n     = n;
            this.time  = time;
            this.x     = new double[n];
            this.y     = new double[n];
            this.vx    = new double[n];
            this.vy    = new double[n];
            this.color = new int[n];
        }
    }

    /**
     * Reads the next snapshot from the trajectory file. Returns null at EOF.
     * Expected repeating block:
     *   N  t
     *   t  x  y  vx  vy  color    ×N
     */
    private static Snapshot readSnapshot(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] header = line.split("\\s+");
            if (header.length != 2) continue; // skip unexpected lines

            int n;
            double t;
            try {
                n = Integer.parseInt(header[0]);
                t = Double.parseDouble(header[1]);
            } catch (NumberFormatException e) {
                continue; // not a header line
            }

            Snapshot snap = new Snapshot(n, t);
            int read = 0;
            for (int i = 0; i < n; i++) {
                String pLine = br.readLine();
                if (pLine == null) break;
                pLine = pLine.trim();
                if (pLine.isEmpty()) { i--; continue; }
                String[] tok = pLine.split("\\s+");
                if (tok.length < 6) continue;
                snap.x[read]     = Double.parseDouble(tok[1]);
                snap.y[read]     = Double.parseDouble(tok[2]);
                snap.vx[read]    = Double.parseDouble(tok[3]);
                snap.vy[read]    = Double.parseDouble(tok[4]);
                snap.color[read] = Integer.parseInt(tok[5]);
                read++;
            }
            snap.n = read; // actual particles parsed
            return snap;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // 1.4 — per-snapshot radial profile writer
    // -----------------------------------------------------------------------

    /**
     * Writes the radial profile block for a single snapshot. Only particles
     * with color=0 (fresh) and R·v < 0 (inward) participate.
     *
     * Shell area = 2π · S_center · dS.
     */
    private static void writeRadialSnapshot(PrintWriter pw, Snapshot snap,
                                            double dS, double S_min) {
        TreeMap<Integer, ShellAccumulator> shells = new TreeMap<>();

        int freshInCount = 0;
        for (int i = 0; i < snap.n; i++) {
            if (snap.color[i] != 0) continue; // only fresh
            double x = snap.x[i], y = snap.y[i];
            double R = Math.sqrt(x * x + y * y);
            if (R == 0) continue;

            double RdotV = x * snap.vx[i] + y * snap.vy[i];
            if (RdotV >= 0) continue; // not inward

            freshInCount++;

            int shellIdx = (int) Math.floor((R - S_min) / dS);
            if (shellIdx < 0) shellIdx = 0;

            double v_normal = RdotV / R; // negative inward component
            shells.computeIfAbsent(shellIdx, k -> new ShellAccumulator()).add(v_normal);
        }

        pw.printf(Locale.US, "# STEP t=%.8f  N_fresh_in=%d%n", snap.time, freshInCount);

        if (shells.isEmpty()) {
            pw.println("# (no inward fresh particles at this time step)");
            pw.println();
            return;
        }

        for (Map.Entry<Integer, ShellAccumulator> entry : shells.entrySet()) {
            int idx = entry.getKey();
            ShellAccumulator acc = entry.getValue();

            double S_inner  = S_min + idx * dS;
            double S_outer  = S_inner + dS;
            double S_center = (S_inner + S_outer) / 2.0;

            double shellArea = 2.0 * Math.PI * S_center * dS;
            double rho_fin   = acc.count / shellArea;
            double v_fin     = acc.sumV / acc.count;
            double J_in      = rho_fin * Math.abs(v_fin);

            pw.printf(Locale.US, "%.4f\t%d\t%.8f\t%.8f\t%.8f%n",
                    S_center, acc.count, rho_fin, v_fin, J_in);
        }
        pw.println();
    }

    /** Accumulates particle count and sum of inward radial velocities for one shell. */
    private static class ShellAccumulator {
        int    count = 0;
        double sumV  = 0.0;

        void add(double v) {
            count++;
            sumV += v;
        }
    }

    // -----------------------------------------------------------------------
    // Main — direct invocation
    // -----------------------------------------------------------------------

    /**
     * Can be called directly:
     *   java SimulationAnalyzer path/to/outputFile.txt [dS]
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java SimulationAnalyzer <outputFile> [dS]");
            System.exit(1);
        }
        String file = args[0];
        double dS   = args.length >= 2 ? Double.parseDouble(args[1]) : DEFAULT_DS;
        try {
            analyze(file, dS);
            System.out.println("Analysis complete.");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }
}
