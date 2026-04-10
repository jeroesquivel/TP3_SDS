/**
 * Representa un evento de colisión en la priority queue.
 *
 * Tipos:
 *   PP   (0) — partícula i con partícula j
 *   OBS  (1) — partícula i con el obstáculo central
 *   WALL (2) — partícula i con el borde circular
 *
 * La validez se comprueba comparando los contadores guardados con los actuales
 * de las partículas involucradas (invalidación lazy).
 */
public class Event implements Comparable<Event> {

    public static final int PP   = 0;
    public static final int OBS  = 1;
    public static final int WALL = 2;

    public final double time;
    public final int    type;
    public final int    i;       // índice de la primera partícula
    public final int    j;       // índice de la segunda (–1 si no aplica)
    public final int    countI;  // count de i en el momento de creación
    public final int    countJ;  // count de j (–1 si no aplica)

    public Event(double time, int type, int i, int j, int countI, int countJ) {
        this.time   = time;
        this.type   = type;
        this.i      = i;
        this.j      = j;
        this.countI = countI;
        this.countJ = countJ;
    }

    /**
     * Comprueba si el evento sigue siendo válido consultando los contadores
     * actuales de las partículas involucradas.
     */
    public boolean isValid(Particle[] particles) {
        if (particles[i].count != countI) return false;
        if (type == PP && particles[j].count != countJ) return false;
        return true;
    }

    @Override
    public int compareTo(Event other) {
        return Double.compare(this.time, other.time);
    }
}