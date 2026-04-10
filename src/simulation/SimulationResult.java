package simulation;

import java.util.List;

/**
 * Contiene todos los resultados de una ejecución del simulador.
 */
public class SimulationResult {

    /** Estados finales de las partículas. */
    public final Particle[] particles;

    /**
     * Historial de colisiones acumuladas fresca→usada.
     * Cada entrada: { t, cfc_acumulado }
     */
    public final List<double[]> cfcHistory;

    /**
     * Fracción de partículas usadas en cada evento.
     * Cada entrada: { t, Fu }
     */
    public final List<double[]> fuHistory;

    /**
     * Snapshots del sistema para perfiles radiales.
     * Cada entrada: Object[]{ Double t, double[N][5] datos }
     * donde datos[k] = { x, y, vx, vy, state }
     */
    public final List<Object[]> radialSnapshots;

    /** Tiempo de pared de la simulación [s]. */
    public final double simTime;

    /** Número de eventos de colisión procesados. */
    public final int events;

    public SimulationResult(
            Particle[]      particles,
            List<double[]>  cfcHistory,
            List<double[]>  fuHistory,
            List<Object[]>  radialSnapshots,
            double          simTime,
            int             events) {
        this.particles        = particles;
        this.cfcHistory       = cfcHistory;
        this.fuHistory        = fuHistory;
        this.radialSnapshots  = radialSnapshots;
        this.simTime          = simTime;
        this.events           = events;
    }
}

