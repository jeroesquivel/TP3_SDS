package simulation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Simulate {

    public static void main(String[] args) throws IOException {
        // Limpiar el directorio simulations antes de la ejecución
        Path simsDir = Paths.get("simulations");
        if (Files.exists(simsDir)) {
            Files.walk(simsDir)
                    .sorted((a, b) -> b.compareTo(a)) // Orden inverso para eliminar archivos antes de directorios
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }

        Files.createDirectories(simsDir);

        int[] nValues = {10, 50, 100, 200};
        double tf = 500.0;

        for (int n : nValues) {
            for (int run = 1; run <= 5; run++) {
                Simulator simulator = new Simulator(n, tf);
                String outputFile = "simulations/sim_N_" + n + "_run_" + run + ".txt";
                SimulationResult result = simulator.run(outputFile, (long) run, 1, 0);

                System.out.printf("N=%d, Run=%d: Eventos procesados: %d, Cfc final: %.0f%n",
                                  n, run, result.events,
                                  result.cfcHistory.get(result.cfcHistory.size() - 1)[1]);
            }
        }
    }

}
