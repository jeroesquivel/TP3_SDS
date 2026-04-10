package simulation;

/**
 * Entrada de línea de comandos para ejecutar una simulación individual.
 */
public class SimulationCli {

    public static void main(String[] args) throws Exception {
        int    N          = 100;
        double tf         = 5.0;
        String outputFile = "output.txt";
        Long   seed       = null;
        int    writeEvery = 1;
        int    snapEvery  = 0;
        boolean noOutput  = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-N":           N          = Integer.parseInt(args[++i]);  break;
                case "-tf":          tf         = Double.parseDouble(args[++i]); break;
                case "-o":           outputFile = args[++i];                    break;
                case "-s":           seed       = Long.parseLong(args[++i]);    break;
                case "-w":           writeEvery = Integer.parseInt(args[++i]);  break;
                case "--snap-every": snapEvery  = Integer.parseInt(args[++i]);  break;
                case "--no-output":  noOutput   = true;                         break;
                default:
                    System.err.println("Opción desconocida: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        String out = noOutput ? null : outputFile;

        System.out.printf("N=%-6d tf=%.2f  seed=%-10s  output=%s%n",
                          N, tf, (seed == null ? "aleatorio" : seed),
                          (out == null ? "ninguno" : out));

        Simulator sim = new Simulator(N, tf);
        SimulationResult result = sim.run(out, seed, writeEvery, snapEvery);

        System.out.printf("Eventos procesados  : %d%n",   result.events);
        System.out.printf("Tiempo de cómputo   : %.4f s%n", result.simTime);
        System.out.printf("Cfc final           : %.0f%n",
                          result.cfcHistory.get(result.cfcHistory.size() - 1)[1]);
        System.out.printf("Fu final            : %.4f%n",
                          result.fuHistory.get(result.fuHistory.size() - 1)[1]);
        if (out != null) {
            System.out.printf("Trayectoria en      : %s%n", out);
        }

        boolean ok = true;
        double totalKE = 0;
        for (Particle p : result.particles) {
            double r = Math.sqrt(p.r2());
            if (r > Simulator.SIGMA_WALL + 1e-4) {
                System.err.printf("ERROR: partícula fuera del borde (r=%.6f > %.1f)%n",
                                  r, Simulator.SIGMA_WALL);
                ok = false;
            }
            if (r < Simulator.SIGMA_OBS - 1e-4) {
                System.err.printf("ERROR: partícula dentro del obstáculo (r=%.6f < %.1f)%n",
                                  r, Simulator.SIGMA_OBS);
                ok = false;
            }
            totalKE += 0.5 * (p.vx * p.vx + p.vy * p.vy);
        }
        double expectedKE = 0.5 * N * Simulator.V0 * Simulator.V0;
        if (Math.abs(totalKE - expectedKE) > 1e-6 * expectedKE) {
            System.err.printf("ERROR: energía cinética total alterada: %.6f (esperado %.6f)%n",
                              totalKE, expectedKE);
            ok = false;
        }
        if (ok) {
            System.out.printf("Verificación OK: contención y KE_total=%.4f (esperado %.4f)%n",
                              totalKE, expectedKE);
        }
    }

    private static void printUsage() {
        System.out.println("Uso: java simulation.SimulationCli [opciones]");
        System.out.println("  -N    <int>     Número de partículas          (default: 100)");
        System.out.println("  -tf   <double>  Tiempo final [s]              (default: 5.0)");
        System.out.println("  -o    <file>    Archivo de trayectoria        (default: output.txt)");
        System.out.println("  -s    <long>    Semilla aleatoria");
        System.out.println("  -w    <int>     Escribir cada W eventos       (default: 1)");
        System.out.println("  --snap-every <int>  Snapshot cada K eventos para perfiles radiales");
        System.out.println("  --no-output     No escribir trayectoria (benchmarks)");
    }
}

