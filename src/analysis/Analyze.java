package analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Analyze {
    public static void main(String[] args) throws IOException {
        // Limpiar el directorio results antes de la ejecución
        Path resultsDir = Paths.get("results");
        if (Files.exists(resultsDir)) {
            Files.walk(resultsDir)
                .sorted((a, b) -> b.compareTo(a)) // Orden inverso para eliminar archivos antes de directorios
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        }

        int[] nValues = {10, 50, 100, 200, 400, 800};
        for(int n : nValues) {
            for(int run = 1; run <= 5; run++) {
                SimulationAnalyzer.analyze("simulations/sim_N_" + n + "_run_" + run + ".txt");
            }
        }
    }
}
