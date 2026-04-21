package simulation;

import java.io.IOException;

//TODO esta se puede borrar

public class Test {
    public static void main(String[] args) throws IOException {
        Simulator sim = new Simulator(400, 1000.0);
        SimulationResult result = sim.run("simulations/test_400_1000.txt", null, 1, 0);

        System.out.printf("N=400: Eventos procesados: %d, Cfc final: %.0f%n",
                result.events,
                result.cfcHistory.get(result.cfcHistory.size() - 1)[1]);
    }
}
