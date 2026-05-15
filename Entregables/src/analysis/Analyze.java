package analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Recorre todos los archivos de trayectoria en simulations/ y genera los
 * observables (cfc, fu, radial) en results/. No borra results/: lo limpia
 * Simulate antes de arrancar.
 */
public class Analyze {
    public static void main(String[] args) throws IOException {
        Path simulationsDir = Paths.get("simulations");
        Path resultsDir     = Paths.get("results");
        Files.createDirectories(resultsDir);

        if (!Files.exists(simulationsDir)) {
            System.err.println("No se encontró la carpeta simulations/. Corré Simulate primero.");
            return;
        }

        List<Path> files;
        try (Stream<Path> s = Files.list(simulationsDir)) {
            files = s.filter(p -> p.toString().endsWith(".txt"))
                     .sorted()
                     .collect(Collectors.toList());
        }

        if (files.isEmpty()) {
            System.out.println("No se encontraron trayectorias en simulations/");
            return;
        }

        for (Path file : files) {
            System.out.println("Analizando: " + file.getFileName());
            SimulationAnalyzer.analyze(file.toString());
        }
    }
}