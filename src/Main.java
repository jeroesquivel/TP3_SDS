import simulation.SimulationCli;
import visualization.Visualizer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Launcher gráfico principal del proyecto.
 *
 * Permite configurar corridas de simulación, perfiles predefinidos,
 * ejecutar análisis y abrir visualizaciones desde una sola interfaz.
 */
public class Main {

    private static final String DEFAULT_RESULTS_DIR = "results";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LauncherFrame().setVisible(true));
    }

    private static final class LauncherFrame extends JFrame {
        private final JTextArea console = new JTextArea(24, 140);
        private final JTabbedPane tabs = new JTabbedPane();
        private JButton backButton;

        private final JTextField simN = new JTextField("100", 8);
        private final JTextField simTf = new JTextField("5.0", 8);
        private final JTextField simOut = new JTextField("output.txt", 18);
        private final JTextField simSeed = new JTextField("", 10);
        private final JTextField simWriteEvery = new JTextField("1", 6);
        private final JTextField simSnapEvery = new JTextField("0", 6);
        private final JCheckBox simNoOutput = new JCheckBox("--no-output");

        private final JCheckBox anTiming = new JCheckBox("--timing");
        private final JCheckBox anCfc = new JCheckBox("--cfc");
        private final JCheckBox anFu = new JCheckBox("--fu");
        private final JCheckBox anRadial = new JCheckBox("--radial");
        private final JTextField anN = new JTextField("100", 8);
        private final JTextField anTf = new JTextField("1000", 8);
        private final JTextField anRuns = new JTextField("5", 6);
        private final JTextField anResultsDir = new JTextField(DEFAULT_RESULTS_DIR, 16);

        private final JTextField vizFile = new JTextField("output.txt", 24);
        private final JTextField vizInterval = new JTextField("50", 6);
        private final JTextField vizMaxFrames = new JTextField("3000", 8);

        private JButton simRunButton;
        private JButton anRunButton;
        private JButton vizOpenButton;


        LauncherFrame() {
            super("TP3_SDS — Launcher gráfico");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout(10, 10));
//            openVisualizer();
            buildTabs();
            JPanel tabsPanel = new JPanel(new BorderLayout());
            tabsPanel.add(tabs, BorderLayout.CENTER);

            backButton = new JButton("← Volver");
            backButton.setVisible(false);
            backButton.addActionListener(e -> {
                tabs.setSelectedIndex(0);
                backButton.setVisible(false);
            });
            JPanel tabHeader = new JPanel(new BorderLayout());
            tabHeader.add(backButton, BorderLayout.WEST);
            tabHeader.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            tabsPanel.add(tabHeader, BorderLayout.NORTH);
            add(tabsPanel, BorderLayout.CENTER);
            add(buildConsolePanel(), BorderLayout.SOUTH);

            setJMenuBar(buildMenuBar());
            pack();
            setLocationRelativeTo(null);
        }

        private JMenuBar buildMenuBar() {
            JMenuBar bar = new JMenuBar();
            JMenu file = new JMenu("Archivo");
            JMenuItem clear = new JMenuItem("Limpiar consola");
            clear.addActionListener(e -> console.setText(""));
            file.add(clear);
            bar.add(file);
            return bar;
        }

        private void buildTabs() {
            tabs.addTab("Simulación", buildSimulationPanel());
//            tabs.addTab("Análisis", buildAnalysisPanel());
            tabs.addTab("Visualización", buildVisualizationPanel());
        }

        private JComponent buildSimulationPanel() {
            JPanel root = new JPanel(new BorderLayout(10, 10));
            root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(new TitledBorder("Parámetros del simulador"));
            GridBagConstraints c = baseGbc();

            int row = 0;
            addField(form, c, row++, "N", simN);
            addField(form, c, row++, "tf [s]", simTf);
            addField(form, c, row++, "output", simOut);
            addField(form, c, row++, "seed", simSeed);
            addField(form, c, row++, "writeEvery", simWriteEvery);
            addField(form, c, row++, "snapEvery", simSnapEvery);
            c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.anchor = GridBagConstraints.WEST;
            form.add(simNoOutput, c);

            JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT));
            presets.setBorder(new TitledBorder("Perfiles de simulación"));
            presets.add(makePresetButton("Tipo 1", () -> applySimulationProfile(50, 50.0, "results/traj_tipo1.txt", "42", 1, 0, false)));
            presets.add(makePresetButton("Tipo 2", () -> applySimulationProfile(100, 200.0, "results/traj_tipo2.txt", "42", 10, 0, false)));
            presets.add(makePresetButton("Tipo 3", () -> applySimulationProfile(100, 1000.0, "results/traj_tipo3.txt", "42", 25, 0, false)));
            presets.add(makePresetButton("Benchmark", () -> applySimulationProfile(200, 5.0, "output.txt", "", 1, 0, true)));

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
            simRunButton = new JButton("Correr simulación");
//            simRunButton.addActionListener(e -> runSimulation());
            JButton vizButton = new JButton("Abrir visualización");
            vizButton.addActionListener(e -> openVisualizer());
            actions.add(simRunButton);
            actions.add(vizButton);

            root.add(form, BorderLayout.NORTH);
            root.add(presets, BorderLayout.CENTER);
            root.add(actions, BorderLayout.SOUTH);
            return root;
        }

//        private JComponent buildAnalysisPanel() {
//            JPanel root = new JPanel(new BorderLayout(10, 10));
//            root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//
//            JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT));
//            flags.setBorder(new TitledBorder("Selectores"));
//            flags.add(anTiming);
//            flags.add(anCfc);
//            flags.add(anFu);
//            flags.add(anRadial);
//            JButton all = new JButton("--all");
//            all.addActionListener(e -> {
//                anTiming.setSelected(true);
//                anCfc.setSelected(true);
//                anFu.setSelected(true);
//                anRadial.setSelected(true);
//            });
//            flags.add(all);
//
//            JPanel form = new JPanel(new GridBagLayout());
//            form.setBorder(new TitledBorder("Parámetros del análisis"));
//            GridBagConstraints c = baseGbc();
//            int row = 0;
//            addField(form, c, row++, "N", anN);
//            addField(form, c, row++, "tf [s]", anTf);
//            addField(form, c, row++, "runs", anRuns);
//            addField(form, c, row++, "results dir", anResultsDir);
//
//            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
//            JButton presetAll = new JButton("Perfil análisis completo");
//            presetAll.addActionListener(e -> applyAnalysisProfile(true, true, true, true, 100, 1000.0, 5, DEFAULT_RESULTS_DIR));
//            JButton presetTiming = new JButton("Timing");
//            presetTiming.addActionListener(e -> applyAnalysisProfile(true, false, false, false, 100, 5.0, 3, DEFAULT_RESULTS_DIR));
//            JButton presetStationary = new JButton("Estacionario");
//            presetStationary.addActionListener(e -> applyAnalysisProfile(false, true, true, true, 100, 1000.0, 5, DEFAULT_RESULTS_DIR));
//            anRunButton = new JButton("Correr análisis");
//            anRunButton.addActionListener(e -> runAnalysis());
//            actions.add(presetAll);
//            actions.add(presetTiming);
//            actions.add(presetStationary);
//            actions.add(anRunButton);
//
//            root.add(flags, BorderLayout.NORTH);
//            root.add(form, BorderLayout.CENTER);
//            root.add(actions, BorderLayout.SOUTH);
//            return root;
//        }

        private JComponent buildVisualizationPanel() {
            JPanel root = new JPanel(new BorderLayout(10, 10));
            root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(new TitledBorder("Abrir visualización"));
            GridBagConstraints c = baseGbc();
            int row = 0;
            addField(form, c, row++, "trajectory file", vizFile);
            addField(form, c, row++, "interval [ms]", vizInterval);
            addField(form, c, row++, "max frames", vizMaxFrames);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
            vizOpenButton = new JButton("Abrir visualizador");
            vizOpenButton.addActionListener(e -> openVisualizer());
            JButton useLast = new JButton("Usar última trayectoria");
            useLast.addActionListener(e -> {
                if (simOut.getText().trim().isEmpty()) {
                    log("No hay trayectoria previa para reutilizar.");
                    return;
                }
                vizFile.setText(simNoOutput.isSelected() ? "output.txt" : simOut.getText().trim());
            });
            actions.add(useLast);
            actions.add(vizOpenButton);

            root.add(form, BorderLayout.NORTH);
            root.add(actions, BorderLayout.SOUTH);
            return root;
        }

        private JComponent buildConsolePanel() {
            console.setEditable(false);
            console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            console.setLineWrap(true);
            console.setWrapStyleWord(true);
            JScrollPane scroll = new JScrollPane(console);
            scroll.setBorder(new TitledBorder("Salida / consola"));
            scroll.setPreferredSize(new Dimension(1400, 280));

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(scroll, BorderLayout.CENTER);

            JButton clear = new JButton("Limpiar consola");
            clear.addActionListener(e -> console.setText(""));
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            footer.add(clear);
            panel.add(footer, BorderLayout.SOUTH);

            return panel;
        }

        private GridBagConstraints baseGbc() {
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 6, 4, 6);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;
            return c;
        }

        private void addField(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
            c.gridx = 0;
            c.gridy = row;
            c.weightx = 0;
            panel.add(new JLabel(label + ":"), c);
            c.gridx = 1;
            c.weightx = 1.0;
            panel.add(field, c);
        }

        private JButton makePresetButton(String label, Runnable action) {
            JButton b = new JButton(label);
            b.addActionListener(e -> action.run());
            return b;
        }

        private void applySimulationProfile(int N, double tf, String output, String seed, int writeEvery, int snapEvery, boolean noOutput) {
            simN.setText(String.valueOf(N));
            simTf.setText(String.valueOf(tf));
            simOut.setText(output);
            simSeed.setText(seed == null ? "" : seed);
            simWriteEvery.setText(String.valueOf(writeEvery));
            simSnapEvery.setText(String.valueOf(snapEvery));
            simNoOutput.setSelected(noOutput);
            log("Perfil de simulación aplicado: N=" + N + ", tf=" + tf + ", output=" + output);
        }

//        private void applyAnalysisProfile(boolean timing, boolean cfc, boolean fu, boolean radial,
//                                          int N, double tf, int runs, String dir) {
//            anTiming.setSelected(timing);
//            anCfc.setSelected(cfc);
//            anFu.setSelected(fu);
//            anRadial.setSelected(radial);
//            anN.setText(String.valueOf(N));
//            anTf.setText(String.valueOf(tf));
//            anRuns.setText(String.valueOf(runs));
//            anResultsDir.setText(dir);
//            log("Perfil de análisis aplicado.");
//        }

//        private void runSimulation() {
//            final List<String> args = new ArrayList<>();
//            try {
//                args.add("-N"); args.add(Integer.toString(Integer.parseInt(simN.getText().trim())));
//                args.add("-tf"); args.add(Double.toString(Double.parseDouble(simTf.getText().trim())));
//                args.add("-o"); args.add(simOut.getText().trim());
//                String seedText = simSeed.getText().trim();
//                if (!seedText.isEmpty()) {
//                    args.add("-s"); args.add(Long.toString(Long.parseLong(seedText)));
//                }
//                args.add("-w"); args.add(Integer.toString(Integer.parseInt(simWriteEvery.getText().trim())));
//                args.add("--snap-every"); args.add(Integer.toString(Integer.parseInt(simSnapEvery.getText().trim())));
//                if (simNoOutput.isSelected()) args.add("--no-output");
//            } catch (NumberFormatException ex) {
//                log("Error: revisá los valores numéricos de la simulación.");
//                return;
//            }
//
//            final String outputFile = simNoOutput.isSelected() ? null : simOut.getText().trim();
//            runBackground("Simulación", () -> SimulationCli.main(args.toArray(new String[0])), () -> {
//                if (outputFile != null && !outputFile.isEmpty()) {
//                    vizFile.setText(outputFile);
//                }
//            }, simRunButton);
//        }

//        private void runAnalysis() {
//            final List<String> args = new ArrayList<>();
//            try {
//                if (anTiming.isSelected()) args.add("--timing");
//                if (anCfc.isSelected()) args.add("--cfc");
//                if (anFu.isSelected()) args.add("--fu");
//                if (anRadial.isSelected()) args.add("--radial");
//                if (!anTiming.isSelected() && !anCfc.isSelected() && !anFu.isSelected() && !anRadial.isSelected()) {
//                    args.add("--all");
//                }
//                args.add("-N"); args.add(Integer.toString(Integer.parseInt(anN.getText().trim())));
//                args.add("-tf"); args.add(Double.toString(Double.parseDouble(anTf.getText().trim())));
//                args.add("--runs"); args.add(Integer.toString(Integer.parseInt(anRuns.getText().trim())));
//                args.add("--results-dir"); args.add(anResultsDir.getText().trim());
//            } catch (NumberFormatException ex) {
//                log("Error: revisá los valores numéricos del análisis.");
//                return;
//            }
//
//            runBackground("Análisis", () -> Analysis.main(args.toArray(new String[0])), null, anRunButton);
//        }

        private void openVisualizer() {
            final String file = "simulations/sim_N_800_run_1_light.txt"; //vizFile.getText().trim();
            final int interval;
            final int maxFrames;
            try {
                interval = Integer.parseInt(vizInterval.getText().trim());
                maxFrames = Integer.parseInt(vizMaxFrames.getText().trim());
            } catch (NumberFormatException ex) {
                log("Error: revisá los valores numéricos de la visualización.");
                return;
            }
            if (file.isEmpty()) {
                log("Seleccioná un archivo de trayectoria para visualizar.");
                return;
            }

            SwingUtilities.invokeLater(() -> {
                try {
                    Visualizer.main(new String[]{file, "--interval", String.valueOf(interval), "--max-frames", String.valueOf(maxFrames)});
                    log("Visualizador abierto con " + file);
                } catch (Exception ex) {
                    log("Error al abrir visualizador: " + ex.getMessage());
                }
            });
        }

        private void runBackground(String title, ThrowingRunnable action, Runnable onSuccess, JButton sourceButton) {
            sourceButton.setEnabled(false);
            backButton.setVisible(true);
            log("=== " + title + " ===");

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override protected Void doInBackground() throws Exception {
                    PrintStream oldOut = System.out;
                    PrintStream oldErr = System.err;
                    try (PrintStream psOut = new PrintStream(new TextAreaOutputStream(console), true, StandardCharsets.UTF_8);
                         PrintStream psErr = new PrintStream(new TextAreaOutputStream(console), true, StandardCharsets.UTF_8)) {
                        System.setOut(psOut);
                        System.setErr(psErr);
                        action.run();
                    } finally {
                        System.setOut(oldOut);
                        System.setErr(oldErr);
                    }
                    return null;
                }

                @Override protected void done() {
                    sourceButton.setEnabled(true);
                    if (onSuccess != null) onSuccess.run();
                    log("=== " + title + " finalizado ===");
                }
            };
            worker.execute();
        }

        private void log(String message) {
            SwingUtilities.invokeLater(() -> {
                console.append(message + "\n");
                console.setCaretPosition(console.getDocument().getLength());
            });
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TextAreaOutputStream extends OutputStream {
        private final JTextArea area;
        private final StringBuilder buffer = new StringBuilder();

        private TextAreaOutputStream(JTextArea area) {
            this.area = area;
        }

        @Override
        public void write(int b) throws IOException {
            if (b == '\r') return;
            if (b == '\n') {
                flushBuffer();
            } else {
                buffer.append((char) b);
            }
        }

        @Override
        public void flush() throws IOException {
            flushBuffer();
        }

        private void flushBuffer() {
            if (buffer.length() == 0) return;
            String text = buffer.toString();
            buffer.setLength(0);
            SwingUtilities.invokeLater(() -> {
                area.append(text + "\n");
                area.setCaretPosition(area.getDocument().getLength());
            });
        }
    }
}