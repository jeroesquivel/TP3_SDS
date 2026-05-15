package visualization;

import simulation.Particle;
import simulation.Simulator;

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
    private static final int    TIMELINE_STEPS = 10_000;

    // ── Datos de la animación ─────────────────────────────────────────────────
    /** Cada frame: { t, double[N][6]{ x,y,vx,vy,state,_ } } */
    private final List<double[][]> frames;
    private final int N;
    private final double tStart;
    private final double tEnd;
    private final double basePlaybackRate;

    private int currentFrame = 0;
    private double playheadTime;
    private long lastTickNanos;
    private boolean paused = false;
    private double playbackMultiplier = 1.0;
    private int tickMs;
    private boolean timelineAdjusting = false;

    private javax.swing.Timer timer;
    private JSlider timelineSlider;
    private JButton pauseButton;
    private JButton fastForwardButton;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Visualizer(List<double[][]> frames, int N, int intervalMs) {
        this.frames = frames;
        this.N = N;
        this.tStart = frames.get(0)[0][0];
        this.tEnd = frames.get(frames.size() - 1)[0][0];
        this.playheadTime = tStart;
        double avgFrameDt = (tEnd - tStart) / Math.max(1, frames.size() - 1);
        this.basePlaybackRate = (intervalMs > 0) ? (avgFrameDt * 1000.0 / intervalMs) : 1.0;
        this.tickMs = Math.max(10, intervalMs / 3);

        setPreferredSize(new Dimension(PANEL_SIZE, PANEL_SIZE));
        setBackground(COLOR_BG);
        setFocusable(true);

        setupKeyBindings();

        // Use a stable EDT tick and interpolate between event frames for smoother motion.
        lastTickNanos = System.nanoTime();
        timer = new javax.swing.Timer(tickMs, e -> {
            if (!paused) {
                long now = System.nanoTime();
                double dt = (now - lastTickNanos) / 1e9;
                lastTickNanos = now;

                playheadTime += dt * basePlaybackRate * playbackMultiplier;
                if (playheadTime >= tEnd) {
                    playheadTime = tEnd;
                }

                syncCurrentFrameWithPlayhead();
                syncTimelineFromPlayhead();
                repaint();
                if (playheadTime >= tEnd) {
                    timer.stop();
                    paused = true;
                    refreshButtonLabels();
                }
            }
        });
        timer.start();
    }

    private void setupKeyBindings() {
        bindKey("SPACE", () -> togglePause());
        bindKey("RIGHT", () -> {
            if (paused) {
                advanceFrame(1);
                syncTimelineFromPlayhead();
                repaint();
            }
        });
        bindKey("LEFT", () -> {
            if (paused) {
                advanceFrame(-1);
                syncTimelineFromPlayhead();
                repaint();
            }
        });
        bindKey("R", this::restartPlayback);
        bindKey("Q", () -> System.exit(0));
    }

    private void bindKey(String key, Runnable action) {
        String actionKey = "viz." + key;
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), actionKey);
        getActionMap().put(actionKey, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    public JPanel createControlBar(int intervalMs, int maxFrames) {
        JPanel controls = new JPanel(new BorderLayout(8, 6));
        controls.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        timelineSlider = new JSlider(0, TIMELINE_STEPS, timeToSlider(playheadTime));
        timelineSlider.addChangeListener(e -> {
            if (timelineAdjusting) return;
            if (timelineSlider.getValueIsAdjusting()) {
                seekToTime(sliderToTime(timelineSlider.getValue()), true);
            } else {
                seekToTime(sliderToTime(timelineSlider.getValue()), false);
            }
        });
        controls.add(timelineSlider, BorderLayout.NORTH);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        pauseButton = new JButton("Pausa");
        pauseButton.addActionListener(e -> togglePause());
        row.add(pauseButton);

        JButton restartButton = new JButton("Reiniciar");
        restartButton.addActionListener(e -> restartPlayback());
        row.add(restartButton);

        fastForwardButton = new JButton("FF x1");
        fastForwardButton.addActionListener(e -> cycleFastForward());
        row.add(fastForwardButton);

        JTextField timeField = new JTextField(7);
        JButton seekButton = new JButton("Ir a t");
        seekButton.addActionListener(e -> {
            try {
                double t = Double.parseDouble(timeField.getText().trim());
                seekToTime(t, false);
            } catch (NumberFormatException ex) {
                // Ignorar valores invalidos para no cortar la reproduccion.
            }
        });
        row.add(new JLabel("t [s]:"));
        row.add(timeField);
        row.add(seekButton);

        JTextField rateField = new JTextField("1.0", 4);
        JButton setRateButton = new JButton("Set rate");
        setRateButton.addActionListener(e -> {
            try {
                double rate = Double.parseDouble(rateField.getText().trim());
                if (rate > 0) {
                    playbackMultiplier = rate;
                    refreshButtonLabels();
                }
            } catch (NumberFormatException ex) {
                // Ignorar valores invalidos.
            }
        });
        row.add(new JLabel("rate x:"));
        row.add(rateField);
        row.add(setRateButton);

        JTextField tickField = new JTextField(String.valueOf(intervalMs), 4);
        JButton setTickButton = new JButton("Set interval");
        setTickButton.addActionListener(e -> {
            try {
                int interval = Integer.parseInt(tickField.getText().trim());
                if (interval > 0) {
                    applyIntervalMs(interval);
                }
            } catch (NumberFormatException ex) {
                // Ignorar valores invalidos.
            }
        });
        row.add(new JLabel("interval [ms]:"));
        row.add(tickField);
        row.add(setTickButton);

        row.add(new JLabel("maxFrames=" + maxFrames));

        controls.add(row, BorderLayout.CENTER);
        refreshButtonLabels();
        return controls;
    }

    private void togglePause() {
        paused = !paused;
        if (!paused) {
            lastTickNanos = System.nanoTime();
            if (!timer.isRunning()) timer.start();
        }
        refreshButtonLabels();
    }

    private void restartPlayback() {
        currentFrame = 0;
        playheadTime = tStart;
        paused = false;
        lastTickNanos = System.nanoTime();
        if (!timer.isRunning()) timer.start();
        syncTimelineFromPlayhead();
        refreshButtonLabels();
        repaint();
    }

    private void cycleFastForward() {
        if (playbackMultiplier < 1.5) playbackMultiplier = 2.0;
        else if (playbackMultiplier < 3.5) playbackMultiplier = 4.0;
        else if (playbackMultiplier < 7.5) playbackMultiplier = 8.0;
        else playbackMultiplier = 1.0;
        refreshButtonLabels();
    }

    private void applyIntervalMs(int intervalMs) {
        tickMs = Math.max(10, intervalMs / 3);
        timer.setDelay(tickMs);
        timer.setInitialDelay(tickMs);
        lastTickNanos = System.nanoTime();
    }

    private void seekToTime(double targetTime, boolean pauseDuringDrag) {
        if (pauseDuringDrag) paused = true;
        playheadTime = Math.max(tStart, Math.min(tEnd, targetTime));
        syncCurrentFrameWithPlayhead();
        syncTimelineFromPlayhead();
        refreshButtonLabels();
        repaint();
    }

    private void syncTimelineFromPlayhead() {
        if (timelineSlider == null) return;
        timelineAdjusting = true;
        timelineSlider.setValue(timeToSlider(playheadTime));
        timelineAdjusting = false;
    }

    private int timeToSlider(double time) {
        if (tEnd <= tStart) return 0;
        double p = (time - tStart) / (tEnd - tStart);
        p = Math.max(0.0, Math.min(1.0, p));
        return (int) Math.round(p * TIMELINE_STEPS);
    }

    private double sliderToTime(int sliderValue) {
        if (tEnd <= tStart) return tStart;
        double p = sliderValue / (double) TIMELINE_STEPS;
        return tStart + p * (tEnd - tStart);
    }

    private void refreshButtonLabels() {
        if (pauseButton != null) {
            pauseButton.setText(paused ? "Reanudar" : "Pausa");
        }
        if (fastForwardButton != null) {
            fastForwardButton.setText(String.format("FF x%.1f", playbackMultiplier));
        }
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

        int    canvasW = getWidth();
        int    canvasH = getHeight();
        int    cx      = canvasW / 2;
        int    cy      = canvasH / 2;
        double scale   = (Math.min(canvasW, canvasH) / 2.0 - 10) / R_RECINTO;

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
        g2.drawString(paused ? "[PAUSA]" : "[> ]", 10, 50);
        g2.drawString(String.format("rate x%.1f", playbackMultiplier), 10, 66);

        // Leyenda
        g2.setColor(COLOR_FRESH);
        g2.fillRect(canvasW - 110, 10, 14, 14);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString("fresca", canvasW - 92, 22);

        g2.setColor(COLOR_USED);
        g2.fillRect(canvasW - 110, 30, 14, 14);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString("usada",  canvasW - 92, 42);
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

        JFrame window = new JFrame("EDMD - Recinto Circular  (N=" + N + ")");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setLayout(new BorderLayout());

        Visualizer viz = new Visualizer(frames, N, interval);
        window.add(viz, BorderLayout.CENTER);
        window.add(viz.createControlBar(interval, maxFrames), BorderLayout.SOUTH);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        viz.requestFocusInWindow();

        System.out.println("Controles: barra de timeline + botones; teclado: ESPACIO, <-/->, R, Q");
    }
}
