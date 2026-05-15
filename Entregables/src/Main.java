import visualization.Visualizer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Launcher: lista las trayectorias disponibles en simulations/ y abre el
 * visualizador con la que se seleccione.
 */
public class Main {

    private static final Path SIM_DIR = Paths.get("simulations");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LauncherFrame().setVisible(true));
    }

    private static final class LauncherFrame extends JFrame {
        private final DefaultListModel<String> listModel = new DefaultListModel<>();
        private final JList<String> fileList = new JList<>(listModel);
        private final JTextField intervalField = new JTextField("50", 6);
        private final JTextField maxFramesField = new JTextField("3000", 8);

        LauncherFrame() {
            super("TP3_SDS — Visualizador de simulaciones");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout(10, 10));

            add(buildListPanel(), BorderLayout.CENTER);
            add(buildSidePanel(),  BorderLayout.EAST);

            refreshList();

            pack();
            setMinimumSize(new Dimension(700, 420));
            setLocationRelativeTo(null);
        }

        private JComponent buildListPanel() {
            fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            fileList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            fileList.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) openSelected();
                }
            });

            JScrollPane scroll = new JScrollPane(fileList);
            scroll.setBorder(new TitledBorder("Trayectorias disponibles (simulations/)"));
            scroll.setPreferredSize(new Dimension(480, 360));
            return scroll;
        }

        private JComponent buildSidePanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4, 6, 4, 6);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;

            int row = 0;
            c.gridx = 0; c.gridy = row;   panel.add(new JLabel("intervalo [ms]:"), c);
            c.gridx = 1;                   panel.add(intervalField, c);
            row++;
            c.gridx = 0; c.gridy = row;   panel.add(new JLabel("max frames:"), c);
            c.gridx = 1;                   panel.add(maxFramesField, c);
            row++;

            JButton refresh = new JButton("Refrescar lista");
            refresh.addActionListener(e -> refreshList());
            JButton open = new JButton("Abrir visualizador");
            open.addActionListener(e -> openSelected());

            c.gridx = 0; c.gridy = row; c.gridwidth = 2;
            panel.add(refresh, c);
            row++;
            c.gridy = row;
            panel.add(open, c);

            return panel;
        }

        private void refreshList() {
            listModel.clear();
            if (!Files.exists(SIM_DIR)) {
                listModel.addElement("(no existe la carpeta simulations/)");
                fileList.setEnabled(false);
                return;
            }
            List<String> names;
            try (Stream<Path> s = Files.list(SIM_DIR)) {
                names = s.filter(p -> p.toString().endsWith(".txt"))
                         .map(p -> p.getFileName().toString())
                         .sorted()
                         .collect(Collectors.toList());
            } catch (IOException ex) {
                listModel.addElement("(error leyendo simulations/: " + ex.getMessage() + ")");
                fileList.setEnabled(false);
                return;
            }
            if (names.isEmpty()) {
                listModel.addElement("(sin simulaciones — corré Simulate primero)");
                fileList.setEnabled(false);
                return;
            }
            fileList.setEnabled(true);
            for (String n : names) listModel.addElement(n);
            fileList.setSelectedIndex(0);
        }

        private void openSelected() {
            if (!fileList.isEnabled()) return;
            String name = fileList.getSelectedValue();
            if (name == null) return;
            String path = SIM_DIR.resolve(name).toString();

            int interval;
            int maxFrames;
            try {
                interval  = Integer.parseInt(intervalField.getText().trim());
                maxFrames = Integer.parseInt(maxFramesField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Valores numéricos inválidos.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                Visualizer.main(new String[]{path,
                        "--interval",   String.valueOf(interval),
                        "--max-frames", String.valueOf(maxFrames)});
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error al abrir visualizador: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}