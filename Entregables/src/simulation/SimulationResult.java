package simulation;

/**
 * Resultado mínimo de una corrida del simulador.
 * Las trayectorias y los observables detallados se escriben a disco durante la
 * corrida; este objeto sólo expone las métricas agregadas para logging.
 */
public class SimulationResult {

    /** Tiempo de pared de la simulación [s]. */
    public final double simTime;

    /** Número de eventos de colisión procesados. */
    public final int events;

    public SimulationResult(double simTime, int events) {
        this.simTime = simTime;
        this.events  = events;
    }
}