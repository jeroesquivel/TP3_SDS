/**
 * Representa una partícula móvil en el recinto circular.
 * Mantiene posición, velocidad, estado (fresca/usada) y contador de colisiones
 * para la invalidación lazy de eventos en la priority queue.
 */
public class Particle {

    public static final int FRESH = 0;   // verde
    public static final int USED  = 1;   // violeta

    public double x, y;
    public double vx, vy;
    public int    state;   // FRESH o USED
    public int    count;   // se incrementa en cada colisión; invalida eventos viejos

    public Particle(double x, double y, double vx, double vy) {
        this.x     = x;
        this.y     = y;
        this.vx    = vx;
        this.vy    = vy;
        this.state = FRESH;
        this.count = 0;
    }

    /** Vuelo libre rectilíneo uniforme durante dt segundos. */
    public void advance(double dt) {
        x += vx * dt;
        y += vy * dt;
    }

    /** Distancia al cuadrado desde el origen. */
    public double r2() {
        return x * x + y * y;
    }

    /** Módulo de la velocidad (debe conservarse ≈ V0 en cada colisión). */
    public double speed() {
        return Math.sqrt(vx * vx + vy * vy);
    }
}
