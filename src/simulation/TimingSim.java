package simulation;

import java.io.PrintWriter;



public class TimingSim {

    public static void main(String[] args) throws Exception {
        runTiming();
    }

    private static void runTiming() throws Exception {
        int[]  Nvalues = {10, 20, 50, 100, 200, 400, 800};
        int    runsT   = 10;
        double tfT     = 5.0;

        System.out.println("\n═══ A) Tiempo de ejecución vs N ═══");



        String csvPath = "results/timing.csv";
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
