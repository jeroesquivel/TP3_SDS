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

        // -----------------------------------------------------------------------
        // Parse all time steps from the input file
        // -----------------------------------------------------------------------
        List<TimeStep> timeSteps = parseFile(inputFilePath);
        if (timeSteps.isEmpty()) {
            throw new IOException("No time steps found in " + inputFilePath);
        }

        int N = timeSteps.get(0).particles.size();
        System.out.println("File: " + inputFilePath);
        System.out.println("  Time steps : " + timeSteps.size());
        System.out.println("  Particles  : " + N);
        System.out.println("  Shell width: " + dS + " m");

        // -----------------------------------------------------------------------
        // 1.2 — Cumulative fresh→used transitions  Cfc(t)
        //
        // A fresh particle (color=0) that appears as used (color=1) in the next
        // time step has just collided with the central obstacle.
        // We track each particle's color across consecutive snapshots.
        // -----------------------------------------------------------------------
        writeCfc(timeSteps, N, cfcPath);
        System.out.println("  Written: " + cfcPath);

        // -----------------------------------------------------------------------
        // 1.3 — Fraction of used particles  Fu(t) = Nu(t)/N
        // -----------------------------------------------------------------------
        writeFu(timeSteps, N, fuPath);
        System.out.println("  Written: " + fuPath);

        // -----------------------------------------------------------------------
        // 1.4 — Radial profiles of inward-moving fresh particles
        //
        // For every snapshot, consider only fresh particles (color=0) whose
        // radial velocity points inward (R·v < 0).  Bin them by distance from
        // the origin S = |R|.  For each shell:
        //   rho_fin(S)  = count / shellArea
        //   v_fin(S)    = mean( R·v / |R| )   (component along -R, i.e. inward)
        //   J_in(S)     = rho_fin * |v_fin|
        //
        // The file contains one block per snapshot so the calling code can
        // average across time steps and/or realizations externally if needed.
        // -----------------------------------------------------------------------
        writeRadial(timeSteps, dS, radialPath);
        System.out.println("  Written: " + radialPath);
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    /** Represents all particles at a single recorded time. */
    private static class Particle {
        final double x, y, vx, vy;
        final int color; // 0 = fresh, 1 = used

        Particle(double x, double y, double vx, double vy, int color) {
            this.x = x; this.y = y;
            this.vx = vx; this.vy = vy;
            this.color = color;
        }
    }

    private static class TimeStep {
        final double time;
        final List<Particle> particles;

        TimeStep(double time, List<Particle> particles) {
            this.time = time;
            this.particles = particles;
        }
    }

    /**
     * Parses the output file.
     *
     * Expected format (repeated for each event):
     *   N  t
     *   t  x  y  vx  vy  color
     *   ... (N lines)
     */
    private static List<TimeStep> parseFile(String filePath) throws IOException {
        List<TimeStep> steps = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Header line: two tokens — N and t
                String[] headerTokens = line.split("\\s+");
                if (headerTokens.length != 2) continue; // skip unexpected lines

                int n;
                double t;
                try {
                    n = Integer.parseInt(headerTokens[0]);
                    t = Double.parseDouble(headerTokens[1]);
                } catch (NumberFormatException e) {
                    continue; // not a header line
                }

                List<Particle> particles = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    String pLine = br.readLine();
                    if (pLine == null) break;
                    pLine = pLine.trim();
                    String[] tok = pLine.split("\\s+");
                    if (tok.length < 6) continue;
                    // tok[0] = time (redundant), tok[1..4] = x y vx vy, tok[5] = color
                    double px  = Double.parseDouble(tok[1]);
                    double py  = Double.parseDouble(tok[2]);
                    double pvx = Double.parseDouble(tok[3]);
                    double pvy = Double.parseDouble(tok[4]);
                    int    col = Integer.parseInt(tok[5]);
                    particles.add(new Particle(px, py, pvx, pvy, col));
                }
                steps.add(new TimeStep(t, particles));
            }
        }
        return steps;
    }

    // -----------------------------------------------------------------------
    // 1.2 — Cfc(t)
    // -----------------------------------------------------------------------

    /**
     * Writes cumulative fresh→used transitions over time.
     *
     * A transition is detected when particle i changes from color=0 to color=1
     * between two consecutive snapshots.  This corresponds to a collision with
     * the central obstacle (radius r0=1, particle radius r=1; contact distance=2).
     *
     * Output format (tab-separated, one row per snapshot):
     *   t   Cfc
     */
    private static void writeCfc(List<TimeStep> steps, int N, String outPath) throws IOException {
        // Track previous colors; initialize from first snapshot
        int[] prevColor = new int[N];
        List<Particle> first = steps.get(0).particles;
        for (int i = 0; i < Math.min(N, first.size()); i++) {
            prevColor[i] = first.get(i).color;
        }

        long cumulativeCount = 0;

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(outPath)))) {
            pw.println("# t\tCfc");
            // Write initial state
            pw.printf("%.8f\t%d%n", steps.get(0).time, cumulativeCount);

            for (int s = 1; s < steps.size(); s++) {
                TimeStep step = steps.get(s);
                List<Particle> parts = step.particles;
                int size = Math.min(N, parts.size());

                for (int i = 0; i < size; i++) {
                    int curColor = parts.get(i).color;
                    // Transition: was fresh (0) → now used (1) means it just hit the center
                    if (prevColor[i] == 0 && curColor == 1) {
                        cumulativeCount++;
                    }
                    prevColor[i] = curColor;
                }
                pw.printf("%.8f\t%d%n", step.time, cumulativeCount);
            }
        }
    }

    // -----------------------------------------------------------------------
    // 1.3 — Fu(t)
    // -----------------------------------------------------------------------

    /**
     * Writes the fraction of used particles over time.
     *
     * Output format (tab-separated):
     *   t   Nu   Fu
     */
    private static void writeFu(List<TimeStep> steps, int N, String outPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(outPath)))) {
            pw.println("# t\tNu\tFu");
            for (TimeStep step : steps) {
                int nu = 0;
                for (Particle p : step.particles) {
                    if (p.color == 1) nu++;
                }
                double fu = (N > 0) ? (double) nu / N : 0.0;
                pw.printf("%.8f\t%d\t%.8f%n", step.time, nu, fu);
            }
        }
    }

    // -----------------------------------------------------------------------
    // 1.4 — Radial profiles
    // -----------------------------------------------------------------------

    /**
     * Writes radial density and velocity profiles for inward-moving fresh particles.
     *
     * For each snapshot, only fresh particles (color=0) with R·v < 0 (inward) are
     * considered.  They are binned by S = |R| into shells of width dS starting from
     * the obstacle surface (S_min = OBSTACLE_RADIUS + PARTICLE_RADIUS = 2 m).
     *
     * Shell area = π((S + dS/2)² − (S − dS/2)²) = 2π·S·dS
     *
     * Variables per shell:
     *   rho_fin  = count / shellArea                      [1/m²]
     *   v_fin    = mean inward radial speed = mean(R·v/|R|) (negative since inward)
     *   J_in     = rho_fin * |v_fin|
     *
     * Output format — one header, then blocks separated by blank line:
     *   # STEP t=...   N_fresh_in=...
     *   S_center   count   rho_fin   v_fin   J_in
     */
    private static void writeRadial(List<TimeStep> steps, double dS, String outPath) throws IOException {

        // S starts from the contact distance between a particle and the obstacle
        double S_min = OBSTACLE_RADIUS + PARTICLE_RADIUS; // 2.0 m

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(outPath)))) {
            pw.println("# Radial profiles of inward-moving fresh particles");
            pw.println("# Shell width dS = " + dS + " m");
            pw.println("# Columns: S_center  count  rho_fin[1/m^2]  v_fin[m/s]  J_in[1/(m^2*s)]");
            pw.println("#");

            for (TimeStep step : steps) {
                // Collect inward-moving fresh particles
                // Key: shell index (S_min + k*dS to S_min + (k+1)*dS)
                // Using a TreeMap to keep shells sorted
                TreeMap<Integer, ShellAccumulator> shells = new TreeMap<>();

                int freshInCount = 0;
                for (Particle p : step.particles) {
                    if (p.color != 0) continue; // only fresh

                    double R = Math.sqrt(p.x * p.x + p.y * p.y);
                    if (R == 0) continue;

                    double RdotV = p.x * p.vx + p.y * p.vy;
                    if (RdotV >= 0) continue; // not inward

                    freshInCount++;

                    // Shell index: 0 corresponds to [S_min, S_min+dS)
                    int shellIdx = (int) Math.floor((R - S_min) / dS);
                    if (shellIdx < 0) shellIdx = 0; // clamp to surface shell

                    double v_normal = RdotV / R; // inward component (negative)
                    shells.computeIfAbsent(shellIdx, k -> new ShellAccumulator()).add(v_normal);
                }

                pw.printf("# STEP t=%.8f  N_fresh_in=%d%n", step.time, freshInCount);

                if (shells.isEmpty()) {
                    pw.println("# (no inward fresh particles at this time step)");
                    pw.println();
                    continue;
                }

                for (Map.Entry<Integer, ShellAccumulator> entry : shells.entrySet()) {
                    int idx = entry.getKey();
                    ShellAccumulator acc = entry.getValue();

                    // Shell centre
                    double S_inner  = S_min + idx * dS;
                    double S_outer  = S_inner + dS;
                    double S_center = (S_inner + S_outer) / 2.0;

                    // Shell area = π(R_outer² - R_inner²) = 2π * S_center * dS
                    double shellArea = 2.0 * Math.PI * S_center * dS;

                    double rho_fin = acc.count / shellArea;
                    double v_fin   = acc.sumV / acc.count; // mean (negative)
                    double J_in    = rho_fin * Math.abs(v_fin);

                    pw.printf("%.4f\t%d\t%.8f\t%.8f\t%.8f%n",
                            S_center, acc.count, rho_fin, v_fin, J_in);
                }
                pw.println(); // blank line between snapshots
            }
        }
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
