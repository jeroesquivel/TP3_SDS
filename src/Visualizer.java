import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Animador de trayectorias EDMD usando Swing.
 *
 * Parsea el archivo de salida del simulador y muestra una animación
 * cuadro a cuadro con las partículas coloreadas por estado:
 *   Verde   (#2ecc71) = fresca
 *   Violeta (#9b59b6) = usada
 *
 * Uso:
 *   java Visualizer <archivo_trayectoria> [--interval <ms>] [--max-frames <n>]
 *
 * Controles de teclado:
 *   SPACE  — pausar / reanudar
 *   →      — avanzar un frame (en pausa)
 *   ←      — retroceder un frame (en pausa)
 *   R      — reiniciar
 *   Q      — cerrar
 */
public class Visualizer extends JPanel {

    // ── Colores y dimensiones ─────────────────────────────────────────────────
    private static final Color  COLOR_FRESH    = new Color(0x2ecc71);
    private static final Color  COLOR_USED     = new Color(0x9b59b6);
    private static final Color  COLOR_OBS      = new Color(0x95a5a6);
    private static final Color  COLOR_WALL     = new Color(0x2c3e50);
    private static final Color  COLOR_BG       = new Color(0xf5f6fa);
    private static final int    PANEL_SIZE     = 700;
    private static final double R_RECINTO      = 40.0;

    // ── Datos de la animación ─────────────────────────────────────────────────
    /** Cada frame: { t, double[N][6]{ x,y,vx,vy,state,_ } } */
    private final List<double[][]> frames;
    private final int N;
    private final double tStart;
    private final double tEnd;
    private final double playbackRate;

    private int currentFrame = 0;
    private double playheadTime;
    private long lastTickNanos;
    private boolean paused = false;
    private javax.swing.Timer timer;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Visualizer(List<double[][]> frames, int N, int intervalMs) {
        this.frames = frames;
        this.N = N;
        this.tStart = frames.get(0)[0][0];
        this.tEnd = frames.get(frames.size() - 1)[0][0];
        this.playheadTime = tStart;
        double avgFrameDt = (tEnd - tStart) / Math.max(1, frames.size() - 1);
        this.playbackRate = (intervalMs > 0) ? (avgFrameDt * 1000.0 / intervalMs) : 1.0;

        setPreferredSize(new Dimension(PANEL_SIZE, PANEL_SIZE));
        setBackground(COLOR_BG);
        setFocusable(true);

        // Controles de teclado
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_SPACE:
                        paused = !paused;
                        if (!paused) {
                            lastTickNanos = System.nanoTime();
                            if (!timer.isRunning()) timer.start();
                        }
                        break;
                    case KeyEvent.VK_RIGHT:
                        if (paused) {
                            advanceFrame(1);
                            repaint();
                        }
                        break;
                    case KeyEvent.VK_LEFT:
                        if (paused) {
                            advanceFrame(-1);
                            repaint();
                        }
                        break;
                    case KeyEvent.VK_R:
                        currentFrame = 0;
                        playheadTime = tStart;
                        paused = false;
                        lastTickNanos = System.nanoTime();
                        if (!timer.isRunning()) timer.start();
                        repaint();
                        break;
                    case KeyEvent.VK_Q:
                        System.exit(0);
                        break;
                }
            }
        });

        // Use a stable EDT tick and interpolate between event frames for smoother motion.
        int tickMs = Math.max(10, intervalMs / 3);
        lastTickNanos = System.nanoTime();
        timer = new javax.swing.Timer(tickMs, e -> {
            if (!paused) {
                long now = System.nanoTime();
                double dt = (now - lastTickNanos) / 1e9;
                lastTickNanos = now;

                playheadTime += dt * playbackRate;
                if (playheadTime >= tEnd) {
                    playheadTime = tEnd;
                }

                syncCurrentFrameWithPlayhead();
                repaint();
                if (playheadTime >= tEnd) {
                    timer.stop();
                }
            }
        });
        timer.start();
    }

    private void advanceFrame(int delta) {
        currentFrame = Math.max(0, Math.min(frames.size() - 1, currentFrame + delta));
        playheadTime = frames.get(currentFrame)[0][0];
    }

    private void syncCurrentFrameWithPlayhead() {
        while (currentFrame + 1 < frames.size()
                && frames.get(currentFrame + 1)[0][0] <= playheadTime) {
            currentFrame++;
        }
        while (currentFrame > 0 && frames.get(currentFrame)[0][0] > playheadTime) {
            currentFrame--;
        }
    }

    // ── Pintura ───────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (frames.isEmpty()) return;

        double[][] frameA = frames.get(currentFrame);
        double[][] frameB = (currentFrame + 1 < frames.size()) ? frames.get(currentFrame + 1) : frameA;
        double tA = frameA[0][0];
        double tB = frameB[0][0];
        double alpha = 0.0;
        if (tB > tA) {
            alpha = (playheadTime - tA) / (tB - tA);
            alpha = Math.max(0.0, Math.min(1.0, alpha));
        }

        // Interpolated simulation time shown in HUD.
        double t = tA + alpha * (tB - tA);

        int    cx   = PANEL_SIZE / 2;
        int    cy   = PANEL_SIZE / 2;
        double scale = (PANEL_SIZE / 2.0 - 10) / R_RECINTO;

        // ── Recinto ───────────────────────────────────────────────────────────
        g2.setColor(COLOR_WALL);
        g2.setStroke(new BasicStroke(2.5f));
        int wallR = (int) (Simulator.R_RECINTO * scale);
        g2.drawOval(cx - wallR, cy - wallR, 2 * wallR, 2 * wallR);

        // ── Obstáculo ─────────────────────────────────────────────────────────
        g2.setColor(COLOR_OBS);
        int obsR = Math.max(1, (int) (Simulator.R_OBS * scale));
        g2.fillOval(cx - obsR, cy - obsR, 2 * obsR, 2 * obsR);

        // ── Partículas ────────────────────────────────────────────────────────
        int pr = Math.max(1, (int) (Simulator.R_PARTICLE * scale));
        for (int k = 1; k <= N; k++) {  // frame[0] es el header
            if (k >= frameA.length || k >= frameB.length) break;

            // Linear interpolation reduces visible jumps when event spacing is uneven.
            double x = frameA[k][0] + alpha * (frameB[k][0] - frameA[k][0]);
            double y = frameA[k][1] + alpha * (frameB[k][1] - frameA[k][1]);
            int state = (alpha < 0.5) ? (int) frameA[k][4] : (int) frameB[k][4];

            int px = cx + (int) (x  * scale) - pr;
            int py = cy - (int) (y  * scale) - pr;   // y invertido en pantalla

            g2.setColor(state == 0 ? COLOR_FRESH : COLOR_USED);
            g2.fillOval(px, py, 2 * pr, 2 * pr);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(0.5f));
            g2.drawOval(px, py, 2 * pr, 2 * pr);
        }

        // ── HUD ───────────────────────────────────────────────────────────────
        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.drawString(String.format("t = %.4f s", t), 10, 18);
        g2.drawString(String.format("frame %d / %d", currentFrame + 1, frames.size()), 10, 34);
        g2.drawString(paused ? "[PAUSA]" : "[▶]", 10, 50);

        // Leyenda
        g2.setColor(COLOR_FRESH);
        g2.fillRect(PANEL_SIZE - 110, 10, 14, 14);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString("fresca", PANEL_SIZE - 92, 22);

        g2.setColor(COLOR_USED);
        g2.fillRect(PANEL_SIZE - 110, 30, 14, 14);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString("usada",  PANEL_SIZE - 92, 42);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Parseo del archivo de trayectoria
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lee el archivo de trayectoria y retorna una lista de frames.
     * Cada frame es un double[N+1][6]:
     *   frame[0]  = { t, N, 0, 0, 0, 0 }      (cabecera)
     *   frame[k]  = { x, y, vx, vy, state, 0 } (partícula k)
     *
     * Si el archivo tiene más frames que maxFrames, se submuestrea.
     */
    public static List<double[][]> parseFile(String path, int maxFrames) throws IOException {
        List<double[][]> all = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String headerLine;
            while ((headerLine = br.readLine()) != null) {
                headerLine = headerLine.trim();
                if (headerLine.isEmpty()) continue;

                String[] hParts = headerLine.split("\\s+");
                int    N = Integer.parseInt(hParts[0]);
                double t = Double.parseDouble(hParts[1]);

                double[][] frame = new double[N + 1][6];
                frame[0][0] = t;
                frame[0][1] = N;

                for (int k = 1; k <= N; k++) {
                    String line = br.readLine();
                    if (line == null) break;
                    String[] p = line.trim().split("\\s+");
                    // t x y vx vy state
                    frame[k][0] = Double.parseDouble(p[1]);  // x
                    frame[k][1] = Double.parseDouble(p[2]);  // y
                    frame[k][2] = Double.parseDouble(p[3]);  // vx
                    frame[k][3] = Double.parseDouble(p[4]);  // vy
                    frame[k][4] = Double.parseDouble(p[5]);  // state
                }
                all.add(frame);
            }
        }

        // Submuestrear si hay demasiados frames
        if (all.size() > maxFrames) {
            int step = all.size() / maxFrames;
            List<double[][]> sub = new ArrayList<>();
            for (int i = 0; i < all.size(); i += step) sub.add(all.get(i));
            return sub;
        }
        return all;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Main
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Uso: java Visualizer <archivo> [--interval <ms>] [--max-frames <n>]");
            System.exit(1);
        }

        String file      = args[0];
        int    interval  = 50;
        int    maxFrames = 3000;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--interval":   interval  = Integer.parseInt(args[++i]); break;
                case "--max-frames": maxFrames = Integer.parseInt(args[++i]); break;
            }
        }

        System.out.println("Leyendo " + file + "...");
        List<double[][]> frames = parseFile(file, maxFrames);
        System.out.println("Cargados " + frames.size() + " frames.");

        if (frames.isEmpty()) {
            System.err.println("Archivo vacío o formato incorrecto.");
            System.exit(1);
        }

        int N = (int) frames.get(0)[0][1];

        JFrame window = new JFrame("EDMD — Recinto Circular  (N=" + N + ")");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);

        Visualizer viz = new Visualizer(frames, N, interval);
        window.add(viz);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        viz.requestFocusInWindow();

        System.out.println("Controles: ESPACIO=pausa  ←/→=frame  R=reiniciar  Q=cerrar");
    }
}