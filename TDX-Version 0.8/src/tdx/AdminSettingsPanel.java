package tdx;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * AdminSettingsPanel — Panel de configuración exclusivo para administradores.
 *
 *  ┌─────────────────────────────────────────────────┐
 *  │  ⚙  AJUSTES DE ADMINISTRADOR                    │
 *  │                                                 │
 *  │  [🌊 Ronda inicial]   [  1  ◂  ▸  ]            │
 *  │   Dinero bonus: $10,000                         │
 *  │                                                 │
 *  │  [📢 Anuncio de virus en ronda]  [ 3  ◂  ▸ ]  │
 *  │                                                 │
 *  │            [ GUARDAR ]   [ CANCELAR ]           │
 *  └─────────────────────────────────────────────────┘
 *
 *  Uso desde el menú de ajustes:
 *      AdminSettingsPanel panel = new AdminSettingsPanel(owner, adminName, config);
 *      panel.showDialog();
 *      if (panel.isSaved()) { ... usar panel.getStartWave(), panel.getVirusAnnounceWave() }
 */
public class AdminSettingsPanel extends JDialog {

    // ── Paleta de colores del juego (oscuro / neón) ──────────────────────
    private static final Color BG_DARK       = new Color(10,  14,  26);
    private static final Color BG_PANEL      = new Color(18,  24,  42);
    private static final Color BG_CARD       = new Color(24,  34,  58);
    private static final Color ACCENT_BLUE   = new Color(0,  170, 255);
    private static final Color ACCENT_GOLD   = new Color(255, 200,  50);
    private static final Color ACCENT_GREEN  = new Color( 50, 220, 120);
    private static final Color ACCENT_RED    = new Color(220,  60,  60);
    private static final Color TEXT_PRIMARY  = new Color(220, 230, 255);
    private static final Color TEXT_MUTED    = new Color(100, 120, 160);
    private static final Color BORDER_GLOW   = new Color(0,  170, 255, 80);

    // ── Estado ────────────────────────────────────────────────────────────
    private int  startWave          = 1;
    private int  virusAnnounceWave  = 3;
    private boolean saved           = false;

    private final String adminName;
    private final AdminConfig config;   // objeto compartido con el juego

    // ── Componentes UI ────────────────────────────────────────────────────
    private SpinnerLabel startWaveSpinner;
    private SpinnerLabel virusWaveSpinner;

    // ─────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────
    public AdminSettingsPanel(Frame owner, String adminName, AdminConfig config) {
        super(owner, "Ajustes — Administrador", true);
        this.adminName = adminName;
        this.config    = config;

        // Cargar valores existentes
        if (config != null) {
            startWave         = config.startWave;
            virusAnnounceWave = config.virusAnnounceWave;
        }

        buildUI();
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    // ── Mostrar diálogo ───────────────────────────────────────────────────
    public void showDialog() { setVisible(true); }

    // ── Getters de resultado ──────────────────────────────────────────────
    public boolean isSaved()              { return saved; }
    public int     getStartWave()         { return startWave; }
    public int     getVirusAnnounceWave() { return virusAnnounceWave; }

    // ─────────────────────────────────────────────────────────────────────
    // Construcción de la UI
    // ─────────────────────────────────────────────────────────────────────
    private void buildUI() {
        // Fondo principal
        JPanel root = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                // degradado oscuro de arriba a abajo
                GradientPaint gp = new GradientPaint(
                    0, 0, BG_DARK,
                    0, getHeight(), BG_PANEL
                );
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setBorder(BorderFactory.createLineBorder(BORDER_GLOW, 2));
        root.setPreferredSize(new Dimension(480, 400));

        // ── Título ────────────────────────────────────────────────────────
        JPanel header = buildHeader();
        root.add(header, BorderLayout.NORTH);

        // ── Cuerpo ────────────────────────────────────────────────────────
        JPanel body = buildBody();
        root.add(body, BorderLayout.CENTER);

        // ── Botones ───────────────────────────────────────────────────────
        JPanel footer = buildFooter();
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        getContentPane().setBackground(BG_DARK);
    }

    // ── Encabezado ────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(BG_CARD);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // línea inferior brillante
                g2.setColor(ACCENT_BLUE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(480, 70));
        p.setBorder(new EmptyBorder(12, 20, 12, 20));

        // Ícono de tuerca + texto
        JLabel icon = makeLabel("⚙", 28, ACCENT_BLUE);
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(makeLabel("AJUSTES DE ADMINISTRADOR", 15, TEXT_PRIMARY, Font.BOLD));
        titles.add(makeLabel("Admin: " + adminName, 11, ACCENT_GOLD, Font.PLAIN));

        p.add(icon,   BorderLayout.WEST);
        p.add(titles, BorderLayout.CENTER);
        return p;
    }

    // ── Cuerpo de ajustes ─────────────────────────────────────────────────
    private JPanel buildBody() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(20, 24, 10, 24));

        // ── Sección 1: Ronda inicial ───────────────────────────────────
        p.add(buildSectionCard(
            "🌊  RONDA DE INICIO",
            ACCENT_BLUE,
            "Elige la ronda en la que comenzará tu partida.",
            () -> {
                startWaveSpinner = new SpinnerLabel(startWave, 1, 30, ACCENT_BLUE);
                return startWaveSpinner;
            },
            "💰  Dinero bonus al iniciar:  $10,000",
            ACCENT_GOLD
        ));

        p.add(Box.createVerticalStrut(16));

        // ── Sección 2: Anuncio de virus ───────────────────────────────
        p.add(buildSectionCard(
            "🦠  ANUNCIO DE VIRUS",
            ACCENT_GREEN,
            "El Dr. Byte aparecerá en esta ronda con información del virus.",
            () -> {
                virusWaveSpinner = new SpinnerLabel(virusAnnounceWave, 1, 30, ACCENT_GREEN);
                return virusWaveSpinner;
            },
            "📋  Muestra: forma, datos clínicos y tratamiento",
            TEXT_MUTED
        ));

        return p;
    }

    // ── Card de sección reutilizable ──────────────────────────────────────
    private JPanel buildSectionCard(
        String title, Color accent,
        String subtitle,
        java.util.function.Supplier<JComponent> spinnerFactory,
        String note, Color noteColor
    ) {
        JPanel card = new JPanel(new BorderLayout(12, 6)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(accent.darker());
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 12, 12);
                // borde izquierdo de color
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(3f));
                g2.drawLine(2, 10, 2, getHeight()-10);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(14, 18, 14, 18));
        card.setMaximumSize(new Dimension(600, 120));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Textos izquierda
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.add(makeLabel(title, 13, accent, Font.BOLD));
        left.add(Box.createVerticalStrut(4));
        left.add(makeLabel(subtitle, 11, TEXT_MUTED, Font.PLAIN));
        left.add(Box.createVerticalStrut(6));
        left.add(makeLabel(note, 11, noteColor, Font.PLAIN));

        // Spinner derecha
        JComponent spinner = spinnerFactory.get();

        card.add(left,    BorderLayout.CENTER);
        card.add(spinner, BorderLayout.EAST);
        return card;
    }

    // ── Botones inferiores ────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 14));
        p.setOpaque(false);

        JButton btnSave   = buildButton("GUARDAR",   ACCENT_GREEN, BG_DARK);
        JButton btnCancel = buildButton("CANCELAR",  ACCENT_RED,   BG_DARK);

        btnSave.addActionListener(e -> {
            // Recoger valores de los spinners
            startWave         = startWaveSpinner.getValue();
            virusAnnounceWave = virusWaveSpinner.getValue();
            // Validar coherencia
            if (startWave > virusAnnounceWave) {
                showWarning("La ronda del anuncio de virus debe ser\n" +
                            "mayor o igual a la ronda de inicio.\n\n" +
                            "(Si la ronda inicio > ronda anuncio, el anuncio\n" +
                            " ya habrá pasado y no se mostrará)");
            }
            // Persistir en el config compartido
            if (config != null) {
                config.startWave         = startWave;
                config.virusAnnounceWave = virusAnnounceWave;
            }
            saved = true;
            dispose();
        });

        btnCancel.addActionListener(e -> { saved = false; dispose(); });

        p.add(btnSave);
        p.add(btnCancel);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers de UI
    // ─────────────────────────────────────────────────────────────────────
    private static JLabel makeLabel(String text, int size, Color color) {
        return makeLabel(text, size, color, Font.PLAIN);
    }
    private static JLabel makeLabel(String text, int size, Color color, int style) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    private static JButton buildButton(String text, Color border, Color bg) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = getModel().isPressed()
                    ? border.darker()
                    : getModel().isRollover()
                        ? new Color(border.getRed(), border.getGreen(), border.getBlue(), 60)
                        : new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 200);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                // texto
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(border);
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setPreferredSize(new Dimension(140, 36));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void showWarning(String msg) {
        JOptionPane.showMessageDialog(this, msg, "⚠ Aviso", JOptionPane.WARNING_MESSAGE);
    }

    // ─────────────────────────────────────────────────────────────────────
    // SpinnerLabel — selector numérico personalizado ◂  N  ▸
    // ─────────────────────────────────────────────────────────────────────
    private static class SpinnerLabel extends JPanel {
        private int value;
        private final int min, max;
        private final Color accent;
        private final JLabel valueLabel;

        SpinnerLabel(int initial, int min, int max, Color accent) {
            this.value  = initial;
            this.min    = min;
            this.max    = max;
            this.accent = accent;

            setLayout(new FlowLayout(FlowLayout.CENTER, 6, 0));
            setOpaque(false);
            setPreferredSize(new Dimension(110, 48));

            JButton dec = arrow("◂");
            valueLabel  = makeValueLabel();
            JButton inc = arrow("▸");

            dec.addActionListener(e -> { if (value > min) { value--; refresh(); } });
            inc.addActionListener(e -> { if (value < max) { value++; refresh(); } });

            add(dec);
            add(valueLabel);
            add(inc);
        }

        private JLabel makeValueLabel() {
            JLabel l = new JLabel(String.format("%02d", value), SwingConstants.CENTER);
            l.setFont(new Font("Monospaced", Font.BOLD, 22));
            l.setForeground(accent);
            l.setPreferredSize(new Dimension(46, 36));
            return l;
        }

        private JButton arrow(String sym) {
            JButton b = new JButton(sym) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color fill = getModel().isRollover()
                        ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 50)
                        : new Color(0, 0, 0, 0);
                    g2.setColor(fill);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                    g2.setColor(accent);
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                    int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                    g2.drawString(getText(), tx, ty);
                    g2.dispose();
                }
            };
            b.setFont(new Font("SansSerif", Font.BOLD, 16));
            b.setPreferredSize(new Dimension(28, 36));
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return b;
        }

        private void refresh() {
            valueLabel.setText(String.format("%02d", value));
            valueLabel.repaint();
        }

        int getValue() { return value; }
    }

    // ─────────────────────────────────────────────────────────────────────
    // AdminConfig — objeto de datos que se comparte con el juego
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Mantén UNA instancia de esta clase en tu clase principal TDX
     * y pásala a AdminSettingsPanel y al motor del juego.
     *
     *   AdminConfig adminCfg = new AdminConfig();
     *   // En el botón de ajustes del menú (solo visible si loggedInAsAdmin):
     *   AdminSettingsPanel dlg = new AdminSettingsPanel(frame, adminName, adminCfg);
     *   dlg.showDialog();
     *   // El juego lee adminCfg.startWave y adminCfg.virusAnnounceWave al iniciar
     */
    public static class AdminConfig {
        /** Ronda en la que empieza la partida del admin (1–30). */
        public int startWave         = 1;
        /** Dinero inicial si startWave > 1. */
        public int bonusMoney        = 10_000;
        /** Ronda en la que aparece el anuncio de virus con el Dr. Byte. */
        public int virusAnnounceWave = 3;
        /** ¿Se aplicará la configuración en la próxima partida? */
        public boolean active        = false;
    }

    // ── Demo rápido ───────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}

            AdminConfig cfg = new AdminConfig();
            AdminSettingsPanel dlg = new AdminSettingsPanel(null, "admin", cfg);
            dlg.showDialog();

            if (dlg.isSaved()) {
                System.out.println("Ronda inicio  : " + dlg.getStartWave());
                System.out.println("Ronda anuncio : " + dlg.getVirusAnnounceWave());
                System.out.println("Dinero bonus  : $" + cfg.bonusMoney);
            } else {
                System.out.println("Cancelado.");
            }
            System.exit(0);
        });
    }
}