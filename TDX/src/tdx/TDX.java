package tdx;

import javax.swing.*;
import javax.sound.midi.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Iterator;

public class TDX extends JPanel implements ActionListener, MouseListener {

    // =====================================================================
    // GAME STATE
    // =====================================================================
    enum GameState {
        MENU, SETTINGS, PLAYING
    }
    GameState gameState = GameState.MENU;

    // Settings
    enum Difficulty {
        EASY, NORMAL, HARD
    }
    Difficulty difficulty = Difficulty.NORMAL;

    Timer timer;

    java.util.List<Enemy> enemies = new ArrayList<>();
    java.util.List<Tower> towers = new ArrayList<>();

    int money = 200;
    int baseHealth = 100;
    int wave = 1;
    static final int MAX_WAVE = 15;

    boolean gameOver = false;
    boolean gameWon = false;

    String selectedTower = "NORMAL";

    // UI hover/click tracking
    int mouseX = 0, mouseY = 0;
    int menuHover = -1;     // 0=PLAY, 1=SETTINGS
    int diffHover = -1;     // 0=EASY, 1=NORMAL, 2=HARD

    // =====================================================================
    // ZONE CONSTANTS
    // =====================================================================
    // Path: y=[100,280]. UI bottom: y=490
    // Valid tower placement: y < 100 OR y > 280, and y < 490
    static final int PATH_TOP = 100;
    static final int PATH_BOT = 280;
    static final int UI_BOTTOM = 490;
    static final int BASE_X = 840;   // base structure starts here
    static final int TOWER_SIZE = 36;    // collision radius for overlap check

    // =====================================================================
    // AUDIO (MIDI)
    // =====================================================================
    static Synthesizer synth;
    static MidiChannel[] channels;

    static {
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            channels = synth.getChannels();
            channels[0].programChange(80);
            channels[1].programChange(98);
            channels[2].programChange(47);
        } catch (Exception ex) {
            System.out.println("MIDI no disponible: " + ex.getMessage());
        }
    }

    static void playNote(int channel, int note, int velocity, int durationMs) {
        if (channels == null || channel >= channels.length) {
            return;
        }
        new Thread(() -> {
            try {
                channels[channel].noteOn(note, velocity);
                Thread.sleep(durationMs);
                channels[channel].noteOff(note);
            } catch (Exception ignored) {
            }
        }).start();
    }

    static boolean bgPlaying = false;
    static Thread bgThread;

    static void startBGMusic() {
        if (bgPlaying) {
            return;
        }
        bgPlaying = true;
        bgThread = new Thread(() -> {
            int[] melody = {60, 62, 64, 67, 69, 67, 64, 62, 60, 64, 67, 72, 71, 69, 67, 64, 60, 62, 64, 67, 64, 60, 62, 64};
            int[] bass = {36, 36, 43, 43, 41, 41, 38, 38};
            int noteDur = 180, bassDur = 360;
            while (bgPlaying) {
                try {
                    for (int i = 0; i < melody.length; i++) {
                        if (!bgPlaying) {
                            break;
                        }
                        playNote(0, melody[i], 65, noteDur);
                        if (i % 3 == 0) {
                            playNote(2, bass[i % bass.length], 50, bassDur);
                        }
                        Thread.sleep(noteDur);
                    }
                } catch (Exception ignored) {
                }
            }
        });
        bgThread.setDaemon(true);
        bgThread.start();
    }

    static void stopBGMusic() {
        bgPlaying = false;
        if (channels != null) {
            channels[0].allNotesOff();
            channels[2].allNotesOff();
        }
    }

    static void sfxEnemyDeath() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                channels[1].programChange(122);
                channels[1].noteOn(45, 90);
                Thread.sleep(60);
                channels[1].noteOff(45);
                channels[1].noteOn(40, 70);
                Thread.sleep(60);
                channels[1].noteOff(40);
                channels[1].programChange(98);
            } catch (Exception ignored) {
            }
        }).start();
    }

    static void sfxBossDeath() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                stopBGMusic();
                channels[1].programChange(100);
                for (int note : new int[]{60, 55, 50, 45, 40, 35}) {
                    channels[1].noteOn(note, 110);
                    Thread.sleep(120);
                    channels[1].noteOff(note);
                }
                for (int note : new int[]{60, 64, 67, 72}) {
                    channels[0].noteOn(note, 90);
                    Thread.sleep(100);
                    channels[0].noteOff(note);
                }
                channels[1].programChange(98);
                startBGMusic();
            } catch (Exception ignored) {
            }
        }).start();
    }

    static void sfxTowerPlaced() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                channels[1].programChange(11);
                for (int note : new int[]{72, 76, 79}) {
                    channels[1].noteOn(note, 80);
                    Thread.sleep(80);
                    channels[1].noteOff(note);
                }
                channels[1].programChange(98);
            } catch (Exception ignored) {
            }
        }).start();
    }

    static void sfxNewWave(int wave) {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                stopBGMusic();
                channels[0].programChange(80);
                for (int i = 0; i < wave && i < 5; i++) {
                    channels[0].noteOn(60 + i * 4, 100);
                    Thread.sleep(90);
                    channels[0].noteOff(60 + i * 4);
                }
                Thread.sleep(100);
                startBGMusic();
            } catch (Exception ignored) {
            }
        }).start();
    }

    static void sfxVictory() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                stopBGMusic();
                channels[0].programChange(14);
                for (int note : new int[]{60, 64, 67, 72, 71, 72, 76}) {
                    channels[0].noteOn(note, 100);
                    Thread.sleep(130);
                    channels[0].noteOff(note);
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    static void sfxGameOver() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                stopBGMusic();
                channels[0].programChange(70);
                for (int note : new int[]{55, 50, 45, 40}) {
                    channels[0].noteOn(note, 100);
                    Thread.sleep(200);
                    channels[0].noteOff(note);
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    static void sfxMenuClick() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                channels[1].programChange(11);
                channels[1].noteOn(74, 90);
                Thread.sleep(60);
                channels[1].noteOff(74);
                channels[1].noteOn(79, 100);
                Thread.sleep(80);
                channels[1].noteOff(79);
                channels[1].programChange(98);
            } catch (Exception ignored) {
            }
        }).start();
    }

    // =====================================================================
    // COLORS & CONSTANTS
    // =====================================================================
    static final Color COL_BG = new Color(10, 18, 25);
    static final Color COL_PATH = new Color(20, 40, 35);
    static final Color COL_PATH_EDGE = new Color(0, 180, 100);
    static final Color COL_GRID = new Color(0, 60, 45);
    static final Color COL_BASE = new Color(0, 220, 120);
    static final Color COL_UI_BG = new Color(5, 12, 18);
    static final Color COL_UI_BORDER = new Color(0, 180, 100);
    static final Color COL_UI_TEXT = new Color(180, 255, 200);
    static final Color COL_MONEY = new Color(255, 215, 60);
    static final Color COL_HEALTH = new Color(220, 50, 60);
    static final Color COL_WAVE = new Color(80, 180, 255);
    static final int PX = 4;

    // =====================================================================
    // CONSTRUCTOR
    // =====================================================================
    public TDX() {
        setPreferredSize(new Dimension(900, 600));
        setBackground(COL_BG);
        addMouseListener(this);
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                repaint();
            }
        });
        timer = new Timer(30, this);
        timer.start();
    }

    // =====================================================================
    // DIFFICULTY HELPERS
    // =====================================================================
    double enemyHealthMult() {
        switch (difficulty) {
            case EASY:
                return 0.7;
            case HARD:
                return 1.5;
            default:
                return 1.0;
        }
    }

    double enemySpeedMult() {
        switch (difficulty) {
            case EASY:
                return 0.8;
            case HARD:
                return 1.3;
            default:
                return 1.0;
        }
    }

    int startMoney() {
        switch (difficulty) {
            case EASY:
                return 350;
            case HARD:
                return 150;
            default:
                return 200;
        }
    }

    // =====================================================================
    // GAME LOGIC
    // =====================================================================
    void startGame() {
        money = startMoney();
        baseHealth = 100;
        wave = 1;
        gameOver = false;
        gameWon = false;
        enemies.clear();
        towers.clear();
        selectedTower = "NORMAL";
        gameState = GameState.PLAYING;
        startWave();
        startBGMusic();
    }

    void startWave() {
        enemies.clear();
        int totalEnemies = wave * 5;
        int rows = 5;

        for (int i = 0; i < totalEnemies; i++) {
            int col = i / rows;
            int row = i % rows;
            int startX = -40 - col * 60;
            int startY = 112 + row * 30;

            Enemy e;
            if (wave >= 10 && i % 7 == 0) {
                e = new TankEnemy(startX, startY);
            } else if (wave >= 7 && i % 5 == 0) {
                e = new SpeedEnemy(startX, startY);
            } else if (wave >= 4 && i % 4 == 0) {
                e = new ShieldEnemy(startX, startY);
            } else {
                e = new Enemy(startX, startY);
                e.maxHealth = (int) ((50 + wave * 10) * enemyHealthMult());
                e.health = e.maxHealth;
            }
            // Apply difficulty scaling to all enemies
            e.speed = (int) Math.max(1, Math.round(e.speed * enemySpeedMult()));
            enemies.add(e);
        }

        if (wave % 3 == 0) {
            Enemy boss = (wave == 15) ? new FinalBoss(-200, 140) : new BossEnemy(-200, 160);
            boss.maxHealth = (int) (boss.maxHealth * enemyHealthMult());
            boss.health = boss.maxHealth;
            enemies.add(boss);
        }

        sfxNewWave(wave);
    }

    // =====================================================================
    // TOWER PLACEMENT VALIDATION
    // =====================================================================
    /**
     * Returns true if position is valid for tower placement
     */
    boolean isValidTowerPosition(int x, int y) {
        // Must be within canvas bounds (with margin)
        if (x < 10 || x > 870 || y < 40 || y > UI_BOTTOM - 10) {
            return false;
        }
        // Must NOT be on path
        if (y > PATH_TOP - 10 && y < PATH_BOT + 10) {
            return false;
        }
        // Must NOT overlap base structure
        if (x > BASE_X - 10) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if another tower already occupies (x,y) area
     */
    boolean towerExistsAt(int x, int y) {
        for (Tower t : towers) {
            if (Math.abs(t.x - x) < TOWER_SIZE && Math.abs(t.y - y) < TOWER_SIZE) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    // DRAWING HELPERS
    // =====================================================================
    void drawHealthBar(Graphics2D g, int x, int y, int w, int h, double ratio, Color fg, Color bg) {
        g.setColor(bg);
        g.fillRect(x, y, w, h);
        g.setColor(fg);
        g.fillRect(x, y, (int) (w * Math.max(0, ratio)), h);
        g.setColor(COL_UI_BORDER);
        g.drawRect(x, y, w, h);
    }

    void drawMedCross(Graphics2D g, int cx, int cy, int size, Color c) {
        int t = Math.max(PX, size / 3);
        g.setColor(c);
        g.fillRect(cx - t / 2, cy - size / 2, t, size);
        g.fillRect(cx - size / 2, cy - t / 2, size, t);
    }

    Font pixelFont(int size) {
        return new Font("Monospaced", Font.BOLD, size);
    }

    void drawPixelHeart(Graphics2D g, int x, int y, Color c) {
        int[][] heart = {{0, 1, 1, 0, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 0, 0, 0}};
        g.setColor(c);
        for (int row = 0; row < heart.length; row++) {
            for (int col = 0; col < heart[row].length; col++) {
                if (heart[row][col] == 1) {
                    g.fillRect(x + col * 3, y + row * 3, 3, 3);
                }
            }
        }
    }

    void drawPixelCoin(Graphics2D g, int x, int y) {
        int[][] coin = {{0, 1, 1, 1, 0}, {1, 1, 0, 1, 1}, {1, 0, 1, 0, 1}, {1, 1, 0, 1, 1}, {0, 1, 1, 1, 0}};
        g.setColor(COL_MONEY);
        for (int row = 0; row < coin.length; row++) {
            for (int col = 0; col < coin[row].length; col++) {
                if (coin[row][col] == 1) {
                    g.fillRect(x + col * 4, y + row * 4, 4, 4);
                }
            }
        }
    }

    void drawPixelBio(Graphics2D g, int x, int y) {
        int[][] bio = {{0, 0, 1, 1, 0, 0}, {0, 1, 0, 0, 1, 0}, {1, 0, 1, 1, 0, 1}, {1, 0, 1, 1, 0, 1}, {0, 1, 0, 0, 1, 0}, {0, 0, 1, 1, 0, 0}};
        g.setColor(new Color(80, 200, 120));
        for (int row = 0; row < bio.length; row++) {
            for (int col = 0; col < bio[row].length; col++) {
                if (bio[row][col] == 1) {
                    g.fillRect(x + col * 4, y + row * 4, 4, 4);
                }
            }
        }
    }

    // =====================================================================
    // PAINT
    // =====================================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        switch (gameState) {
            case MENU:
                drawMenu(g2);
                break;
            case SETTINGS:
                drawSettings(g2);
                break;
            case PLAYING:
                drawGame(g2);
                break;
        }
    }

    // =====================================================================
    // MENU SCREEN
    // =====================================================================
    void drawMenu(Graphics2D g2) {
        // Background with grid
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, 900, 600);
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < 900; gx += 16) {
            for (int gy = 0; gy < 600; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }

        // Decorative path suggestion
        g2.setColor(new Color(20, 40, 35));
        g2.fillRect(0, 240, 900, 120);
        g2.setColor(COL_PATH_EDGE);
        for (int px = 0; px < 900; px += PX * 4) {
            g2.fillRect(px, 240, PX * 2, PX);
            g2.fillRect(px, 356, PX * 2, PX);
        }

        // Title with pixel shadow
        g2.setColor(new Color(0, 80, 40));
        g2.setFont(pixelFont(54));
        g2.drawString("TDX", 302, 128);
        g2.setColor(COL_PATH_EDGE);
        g2.drawString("TDX", 298, 124);

        g2.setColor(new Color(180, 255, 200, 200));
        g2.setFont(pixelFont(14));
        g2.drawString("DEFENSA MEDICA", 308, 152);

        // Animated decorative viruses
        drawMenuVirus(g2, 80, 160, 0);
        drawMenuVirus(g2, 760, 130, 1);
        drawMenuVirus(g2, 140, 420, 2);
        drawMenuVirus(g2, 700, 400, 3);

        // Buttons
        String[] labels = {"JUGAR", "AJUSTES"};
        int[] bx = {300, 300};
        int[] by = {190, 270};
        int bw = 300, bh = 60;

        for (int i = 0; i < 2; i++) {
            boolean hover = mouseX >= bx[i] && mouseX <= bx[i] + bw && mouseY >= by[i] && mouseY <= by[i] + bh;
            Color btnColor = hover ? COL_PATH_EDGE : new Color(0, 120, 70);
            Color textColor = hover ? COL_BG : COL_UI_TEXT;

            // Shadow
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRect(bx[i] + 4, by[i] + 4, bw, bh);
            // Fill
            g2.setColor(hover ? new Color(0, 200, 100, 30) : new Color(10, 25, 18));
            g2.fillRect(bx[i], by[i], bw, bh);
            // Border
            g2.setColor(btnColor);
            g2.setStroke(new BasicStroke(PX));
            g2.drawRect(bx[i], by[i], bw, bh);
            // Corner pixels
            g2.fillRect(bx[i] - PX, by[i] - PX, PX * 2, PX * 2);
            g2.fillRect(bx[i] + bw - PX, by[i] - PX, PX * 2, PX * 2);
            g2.fillRect(bx[i] - PX, by[i] + bh - PX, PX * 2, PX * 2);
            g2.fillRect(bx[i] + bw - PX, by[i] + bh - PX, PX * 2, PX * 2);
            // Label
            g2.setColor(textColor);
            g2.setFont(pixelFont(22));
            FontMetrics fm = g2.getFontMetrics();
            int lw = fm.stringWidth(labels[i]);
            g2.drawString(labels[i], bx[i] + (bw - lw) / 2, by[i] + 38);
        }

        // Difficulty indicator
        g2.setColor(new Color(100, 150, 120));
        g2.setFont(pixelFont(10));
        String dLabel = "DIFICULTAD: " + difficulty.name();
        g2.drawString(dLabel, 355, 350);

        // Footer
        g2.setColor(new Color(60, 100, 80));
        g2.setFont(pixelFont(9));
        g2.drawString("Usa el raton para colocar torres  |  Teclas 1-4 para cambiar tipo", 220, 580);
    }

    void drawMenuVirus(Graphics2D g2, int x, int y, int variant) {
        int t = (int) (System.currentTimeMillis() / 200) + variant * 17;
        int wobble = t % 2;
        int[][] virus = {{0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 0}};
        Color[] cols = {new Color(180, 30, 30, 120), new Color(140, 20, 160, 120), new Color(30, 120, 60, 120), new Color(200, 100, 20, 120)};
        g2.setColor(cols[variant % cols.length]);
        for (int r = 0; r < virus.length; r++) {
            for (int c = 0; c < virus[r].length; c++) {
                if (virus[r][c] == 1) {
                    g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                }
            }
        }
        // spikes
        g2.setColor(new Color(cols[variant % cols.length].getRed(), cols[variant % cols.length].getGreen(), cols[variant % cols.length].getBlue(), 80));
        int cx = x + 16, cy = y + 12;
        int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        for (int[] sp : spikes) {
            g2.fillRect(cx + sp[0] * (10 + wobble) - PX / 2, cy + sp[1] * (10 + wobble) - PX / 2, PX, PX);
        }
    }

    // =====================================================================
    // SETTINGS SCREEN
    // =====================================================================
    void drawSettings(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, 900, 600);
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < 900; gx += 16) {
            for (int gy = 0; gy < 600; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }

        // Title
        g2.setColor(COL_PATH_EDGE);
        g2.setFont(pixelFont(30));
        g2.drawString("AJUSTES", 330, 80);
        g2.setColor(new Color(0, 80, 40));
        g2.fillRect(0, 90, 900, PX);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(0, 90, 900, PX);

        // Difficulty section
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(16));
        g2.drawString("DIFICULTAD", 340, 160);

        // Description per difficulty
        String[] descs = {
            "Enemigos con -30% de vida y velocidad reducida. Mas dinero inicial ($350).",
            "Experiencia balanceada. Dinero inicial: $200.",
            "Enemigos con +50% de vida y velocidad aumentada. Poco dinero ($150)."
        };
        String[] diffNames = {"FACIL", "NORMAL", "DIFICIL"};
        Color[] diffColors = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60)};
        Difficulty[] diffs = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};

        int btnW = 220, btnH = 70;
        int[] bxArr = {60, 340, 620};
        int by = 200;

        for (int i = 0; i < 3; i++) {
            boolean selected = difficulty == diffs[i];
            boolean hover = mouseX >= bxArr[i] && mouseX <= bxArr[i] + btnW && mouseY >= by && mouseY <= by + btnH;

            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRect(bxArr[i] + 4, by + 4, btnW, btnH);

            if (selected) {
                g2.setColor(new Color(diffColors[i].getRed(), diffColors[i].getGreen(), diffColors[i].getBlue(), 40));
                g2.fillRect(bxArr[i], by, btnW, btnH);
            } else {
                g2.setColor(hover ? new Color(10, 30, 20) : new Color(8, 18, 14));
                g2.fillRect(bxArr[i], by, btnW, btnH);
            }

            Color borderC = selected ? diffColors[i] : (hover ? new Color(diffColors[i].getRed(), diffColors[i].getGreen(), diffColors[i].getBlue(), 150) : new Color(0, 80, 50));
            g2.setColor(borderC);
            g2.setStroke(new BasicStroke(selected ? PX : 2));
            g2.drawRect(bxArr[i], by, btnW, btnH);

            if (selected) {
                g2.setColor(diffColors[i]);
                g2.fillRect(bxArr[i] - PX, by - PX, PX * 2, PX * 2);
                g2.fillRect(bxArr[i] + btnW - PX, by - PX, PX * 2, PX * 2);
                g2.fillRect(bxArr[i] - PX, by + btnH - PX, PX * 2, PX * 2);
                g2.fillRect(bxArr[i] + btnW - PX, by + btnH - PX, PX * 2, PX * 2);
            }

            g2.setColor(selected ? diffColors[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(14));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(diffNames[i], bxArr[i] + (btnW - fm.stringWidth(diffNames[i])) / 2, by + 28);

            if (selected) {
                g2.setColor(new Color(diffColors[i].getRed(), diffColors[i].getGreen(), diffColors[i].getBlue(), 200));
                g2.setFont(pixelFont(9));
                g2.drawString("< SELECCIONADO >", bxArr[i] + (btnW - g2.getFontMetrics().stringWidth("< SELECCIONADO >")) / 2, by + 50);
            }
        }

        // Description box
        g2.setColor(new Color(10, 25, 18));
        g2.fillRect(60, 295, 780, 120);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(60, 295, 780, 120);

        int descIdx = difficulty.ordinal();
        g2.setColor(diffColors[descIdx]);
        g2.setFont(pixelFont(10));
        g2.drawString(descs[descIdx], 80, 320);

        // Stats visual
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(10));
        g2.drawString("VIDA ENE:", 80, 355);
        g2.drawString("VEL ENE:", 80, 375);
        g2.drawString("DINERO:", 80, 395);

        double[] healthM = {0.7, 1.0, 1.5};
        double[] speedM = {0.8, 1.0, 1.3};
        int[] moneyI = {350, 200, 150};

        drawSettingBar(g2, 180, 345, 200, 16, healthM[descIdx] / 1.5, diffColors[descIdx]);
        drawSettingBar(g2, 180, 365, 200, 16, speedM[descIdx] / 1.3, diffColors[descIdx]);
        drawSettingBar(g2, 180, 385, 200, 16, moneyI[descIdx] / 350.0, diffColors[descIdx]);

        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(9));
        g2.drawString(String.format("x%.1f", healthM[descIdx]), 390, 355);
        g2.drawString(String.format("x%.1f", speedM[descIdx]), 390, 375);
        g2.drawString("$" + moneyI[descIdx], 390, 395);

        // Back button
        int backX = 330, backY = 430, backW = 220, backH = 50;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        g2.setColor(backHover ? new Color(0, 180, 100, 30) : new Color(8, 18, 14));
        g2.fillRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_PATH_EDGE : new Color(0, 100, 60));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_BG : COL_UI_TEXT);
        g2.setFont(pixelFont(16));
        g2.drawString("< VOLVER", backX + 62, backY + 33);
    }

    void drawSettingBar(Graphics2D g2, int x, int y, int w, int h, double ratio, Color c) {
        g2.setColor(new Color(10, 25, 18));
        g2.fillRect(x, y, w, h);
        g2.setColor(c);
        g2.fillRect(x, y, (int) (w * Math.min(1, ratio)), h);
        g2.setColor(COL_UI_BORDER);
        g2.drawRect(x, y, w, h);
    }

    // =====================================================================
    // GAME SCREEN
    // =====================================================================
    void drawGame(Graphics2D g2) {
        // Background + grid
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, 900, 600);
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < 900; gx += 16) {
            for (int gy = 0; gy < 600; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }

        // Valid tower zones highlighted (subtle)
        g2.setColor(new Color(0, 80, 40, 18));
        g2.fillRect(0, 40, BASE_X, PATH_TOP);       // top zone
        g2.fillRect(0, PATH_BOT, BASE_X, UI_BOTTOM - PATH_BOT); // bottom zone

        // Path
        g2.setColor(COL_PATH);
        g2.fillRect(0, 100, 900, 180);
        g2.setColor(COL_PATH_EDGE);
        g2.setStroke(new BasicStroke(PX));
        for (int px = 0; px < 900; px += PX * 4) {
            g2.fillRect(px, 100, PX * 2, PX);
            g2.fillRect(px, 276, PX * 2, PX);
        }
        g2.setColor(new Color(0, 100, 60));
        for (int px = 0; px < 900; px += PX * 6) {
            g2.fillRect(px, 188, PX * 3, PX);
        }

        // Base
        g2.setColor(new Color(15, 35, 30));
        g2.fillRect(848, 108, 48, 164);
        g2.setColor(COL_BASE);
        g2.fillRect(852, 112, 40, 156);
        g2.setColor(new Color(180, 255, 200));
        for (int wy = 120; wy < 250; wy += 20) {
            g2.fillRect(858, wy, 8, 8);
            g2.fillRect(874, wy, 8, 8);
        }
        drawMedCross(g2, 872, 188, 24, new Color(220, 50, 60));
        g2.setColor(COL_PATH_EDGE);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(852, 112, 40, 156);

        // Enemies
        for (Enemy e : enemies) {
            e.draw(g2);
        }

        // Towers
        for (Tower t : towers) {
            t.draw(g2);
        }

        // Mouse hover indicator for tower placement
        if (!gameOver && !gameWon) {
            boolean valid = isValidTowerPosition(mouseX, mouseY) && !towerExistsAt(mouseX, mouseY);
            g2.setColor(valid ? new Color(0, 200, 100, 60) : new Color(200, 50, 50, 60));
            g2.fillRect(mouseX - TOWER_SIZE / 2, mouseY - TOWER_SIZE / 2, TOWER_SIZE, TOWER_SIZE);
            g2.setColor(valid ? new Color(0, 200, 100, 150) : new Color(200, 50, 50, 150));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(mouseX - TOWER_SIZE / 2, mouseY - TOWER_SIZE / 2, TOWER_SIZE, TOWER_SIZE);
        }

        // Top UI
        g2.setColor(COL_UI_BG);
        g2.fillRect(0, 0, 900, 36);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(0, 36, 900, 36);
        drawPixelHeart(g2, 8, 8, COL_HEALTH);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(13));
        g2.drawString("" + baseHealth, 32, 22);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(80, 6, PX, 24);
        drawPixelCoin(g2, 92, 8);
        g2.setColor(COL_MONEY);
        g2.setFont(pixelFont(13));
        g2.drawString("$" + money, 116, 22);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(180, 6, PX, 24);
        drawPixelBio(g2, 192, 8);
        g2.setColor(COL_WAVE);
        g2.setFont(pixelFont(13));
        g2.drawString("OLEADA " + wave + "/" + MAX_WAVE, 216, 22);

        // Difficulty badge
        Color[] dCols = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60)};
        String[] dNames = {"FACIL", "NORMAL", "DIFICIL"};
        g2.setColor(dCols[difficulty.ordinal()]);
        g2.setFont(pixelFont(9));
        g2.drawString("[" + dNames[difficulty.ordinal()] + "]", 380, 22);

        // Progress bar
        int progW = 360, progX = 500;
        g2.setColor(new Color(10, 30, 20));
        g2.fillRect(progX, 8, progW, 20);
        g2.setColor(new Color(0, 180, 100));
        g2.fillRect(progX, 8, (int) (progW * (double) wave / MAX_WAVE), 20);
        g2.setColor(COL_UI_BORDER);
        g2.drawRect(progX, 8, progW, 20);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(9));
        g2.drawString("PROGRESO DEFENSA", progX + 110, 22);

        // Bottom UI
        g2.setColor(COL_UI_BG);
        g2.fillRect(0, 490, 900, 110);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(0, 490, 900, 490);
        g2.setColor(new Color(100, 200, 150, 120));
        g2.fillRect(0, 490, 900, 16);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(10));
        g2.drawString("[ SELECCIONAR DEFENSA ]  —  Click en zona verde para colocar torres", 8, 502);

        String[] types = {"NORMAL", "FIRE", "ICE", "ELEC"};
        int[] costs = {50, 70, 60, 80};
        String[] labels = {"JERINGA", "IBUPROFENO", "ANALGESICO", "AMOXICILINA"};
        String[] keys = {"[1]", "[2]", "[3]", "[4]"};
        Color[] colors = {new Color(180, 255, 200), new Color(255, 120, 60), new Color(80, 200, 255), new Color(255, 230, 60)};
        int[] xPos = {20, 240, 460, 680};

        for (int i = 0; i < types.length; i++) {
            boolean selected = selectedTower.equals(types[i]);
            int bx = xPos[i], by = 506, bw = 200, bh = 84;
            if (selected) {
                g2.setColor(new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 30));
                g2.fillRect(bx, by, bw, bh);
                g2.setColor(colors[i]);
                g2.setStroke(new BasicStroke(PX));
                g2.drawRect(bx, by, bw, bh);
                g2.fillRect(bx - PX, by - PX, PX * 2, PX * 2);
                g2.fillRect(bx + bw - PX, by - PX, PX * 2, PX * 2);
                g2.fillRect(bx - PX, by + bh - PX, PX * 2, PX * 2);
                g2.fillRect(bx + bw - PX, by + bh - PX, PX * 2, PX * 2);
            } else {
                g2.setColor(new Color(20, 40, 35));
                g2.fillRect(bx, by, bw, bh);
                g2.setColor(new Color(0, 80, 55));
                g2.setStroke(new BasicStroke(PX));
                g2.drawRect(bx, by, bw, bh);
            }
            drawTowerIcon(g2, bx + 12, by + 16, types[i], colors[i]);
            g2.setColor(selected ? colors[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(11));
            g2.drawString(labels[i], bx + 52, by + 26);
            g2.setColor(new Color(100, 150, 120));
            g2.setFont(pixelFont(9));
            g2.drawString(keys[i], bx + 52, by + 40);
            g2.setColor(COL_MONEY);
            g2.setFont(pixelFont(11));
            g2.drawString("$" + costs[i], bx + 52, by + 58);
            if (money < costs[i]) {
                g2.setColor(new Color(180, 50, 50, 150));
                g2.fillRect(bx, by, bw, bh);
                g2.setColor(new Color(220, 80, 80));
                g2.setFont(pixelFont(9));
                g2.drawString("SIN FONDOS", bx + 42, by + 72);
            }
        }

        // End screens
        if (gameOver) {
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, 900, 600);
            g2.setColor(new Color(220, 50, 60));
            g2.setFont(pixelFont(36));
            g2.drawString("GAME OVER", 280, 220);
            g2.setColor(COL_UI_TEXT);
            g2.setFont(pixelFont(16));
            g2.drawString("La base fue destruida en la oleada " + wave, 220, 280);
            // Back to menu button
            drawEndButton(g2, 300, 320, 300, 50, "VOLVER AL MENU");
        }

        if (gameWon) {
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, 900, 600);
            g2.setColor(new Color(0, 220, 120));
            g2.setFont(pixelFont(28));
            g2.drawString("DEFENSA COMPLETADA!", 190, 220);
            g2.setColor(COL_MONEY);
            g2.setFont(pixelFont(18));
            g2.drawString("Sobreviviste las 15 oleadas", 255, 270);
            g2.setColor(COL_UI_TEXT);
            g2.setFont(pixelFont(13));
            g2.drawString("Puntuacion: $" + money + "  |  Vida: " + baseHealth, 270, 310);
            drawEndButton(g2, 300, 340, 300, 50, "VOLVER AL MENU");
        }
    }

    void drawEndButton(Graphics2D g2, int x, int y, int w, int h, String label) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g2.setColor(hover ? new Color(0, 200, 100, 40) : new Color(10, 25, 18));
        g2.fillRect(x, y, w, h);
        g2.setColor(hover ? COL_PATH_EDGE : new Color(0, 120, 70));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(x, y, w, h);
        g2.setColor(hover ? COL_BG : COL_UI_TEXT);
        g2.setFont(pixelFont(14));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h / 2 + 5);
    }

    // Tower icons for bottom UI
    void drawTowerIcon(Graphics2D g, int x, int y, String type, Color c) {
        switch (type) {
            case "NORMAL":
                drawIconSyringe(g, x, y, c);
                break;
            case "FIRE":
                drawIconLaser(g, x, y, c);
                break;
            case "ICE":
                drawIconCryo(g, x, y, c);
                break;
            case "ELEC":
                drawIconElec(g, x, y, c);
                break;
        }
    }

    void drawIconSyringe(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        g.fillRect(x + 4, y + 4, 24, 8);
        g.fillRect(x + 28, y + 7, 8, 2);
        g.fillRect(x, y + 2, 4, 12);
        g.setColor(new Color(100, 200, 150));
        g.fillRect(x + 8, y + 6, 16, 4);
    }

    void drawIconLaser(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        g.fillRect(x + 8, y + 8, 20, 12);
        g.fillRect(x + 28, y + 10, 8, 4);
        g.setColor(new Color(255, 60, 60));
        g.fillRect(x + 4, y + 10, 8, 8);
    }

    void drawIconCryo(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        drawMedCross(g, x + 20, y + 12, 20, c);
    }

    void drawIconElec(Graphics2D g, int x, int y, Color c) {
        int[][] bolt = {{0, 0, 1, 1, 1}, {0, 1, 1, 0, 0}, {1, 1, 1, 0, 0}, {0, 1, 0, 0, 0}, {1, 0, 0, 0, 0}};
        g.setColor(c);
        for (int r = 0; r < bolt.length; r++) {
            for (int col = 0; col < bolt[r].length; col++) {
                if (bolt[r][col] == 1) {
                    g.fillRect(x + 8 + col * 5, y + 4 + r * 5, 5, 5);
                }
            }
        }
    }

    // =====================================================================
    // GAME LOOP
    // =====================================================================
    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState == GameState.MENU || gameState == GameState.SETTINGS) {
            repaint();
            return;
        }
        if (gameOver || gameWon) {
            return;
        }

        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy en = it.next();
            en.move();
            if (en.x > 850) {
                baseHealth -= (en instanceof BossEnemy ? 25 : (en instanceof TankEnemy ? 20 : 10));
                it.remove();
                if (baseHealth <= 0) {
                    baseHealth = 0;
                    gameOver = true;
                    timer.stop();
                    sfxGameOver();
                }
            }
        }

        for (Tower t : towers) {
            t.update(enemies);
        }

        java.util.List<Enemy> toRemove = new ArrayList<>();
        for (Enemy en : enemies) {
            if (en.health <= 0) {
                toRemove.add(en);
                money += en.reward;
                if (en instanceof BossEnemy || en instanceof FinalBoss) {
                    sfxBossDeath();
                } else {
                    sfxEnemyDeath();
                }
            }
        }
        enemies.removeAll(toRemove);

        if (enemies.isEmpty() && !gameOver) {
            if (wave >= MAX_WAVE) {
                gameWon = true;
                timer.stop();
                sfxVictory();
            } else {
                wave++;
                money += 100;
                startWave();
            }
        }

        repaint();
    }

    // =====================================================================
    // MOUSE
    // =====================================================================
    @Override
    public void mouseClicked(MouseEvent e) {
        int mx = e.getX(), my = e.getY();

        if (gameState == GameState.MENU) {
            // JUGAR button
            if (mx >= 300 && mx <= 500 && my >= 190 && my <= 250) {
                sfxMenuClick();
                startGame();
            } // AJUSTES button
            else if (mx >= 300 && mx <= 500 && my >= 270 && my <= 330) {
                sfxMenuClick();
                gameState = GameState.SETTINGS;
            }
        } else if (gameState == GameState.SETTINGS) {
            Difficulty[] diffs = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
            int[] bxArr = {60, 340, 620};
            int btnW = 220, btnH = 70, by = 200;
            for (int i = 0; i < 3; i++) {
                if (mx >= bxArr[i] && mx <= bxArr[i] + btnW && my >= by && my <= by + btnH) {
                    sfxMenuClick();
                    difficulty = diffs[i];
                }
            }
            // VOLVER button
            if (mx >= 330 && mx <= 570 && my >= 430 && my <= 480) {
                sfxMenuClick();
                gameState = GameState.MENU;
            }
        } else if (gameState == GameState.PLAYING) {
            if (gameOver || gameWon) {
                // VOLVER AL MENU button
                if (mx >= 300 && mx <= 600 && my >= 320 && my <= 370) {
                    stopBGMusic();
                    gameState = GameState.MENU;
                }
                return;
            }

            int cost = switch (selectedTower) {
                case "FIRE" ->
                    70;
                case "ICE" ->
                    60;
                case "ELEC" ->
                    80;
                default ->
                    50;
            };

            if (money >= cost) {
                if (isValidTowerPosition(mx, my) && !towerExistsAt(mx, my)) {
                    towers.add(new Tower(mx, my, selectedTower));
                    money -= cost;
                    sfxTowerPlaced();
                }
                // If invalid position: silently ignore (player gets visual feedback from hover)
            }
        }
    }

    public void setKeyBindings() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("1"), "n");
        getActionMap().put("n", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                selectedTower = "NORMAL";
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("2"), "f");
        getActionMap().put("f", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                selectedTower = "FIRE";
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("3"), "i");
        getActionMap().put("i", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                selectedTower = "ICE";
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("4"), "e");
        getActionMap().put("e", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                selectedTower = "ELEC";
            }
        });
    }

    // =====================================================================
    // MAIN
    // =====================================================================
    public static void main(String[] args) {
        JFrame frame = new JFrame("TDX - DEFENSA MEDICA");
        frame.getContentPane().setBackground(new Color(10, 18, 25));
        TDX game = new TDX();
        game.setKeyBindings();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // MouseListener stubs
    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    // =====================================================================
    // ENEMY BASE
    // =====================================================================
    class Enemy {

        int x, y;
        int maxHealth = 50;
        int health = 50;
        int speed = 2;
        int animTick = 0;
        int reward = 20;

        Enemy(int x, int y) {
            this.x = x;
            this.y = y;
        }

        void move() {
            x += speed;
            animTick++;
        }

        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int wobble = (animTick / 6) % 2 == 0 ? 0 : 1;
            int[][] virus = {{0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 0}};
            g2.setColor(new Color(0, 0, 0, 80));
            for (int row = 0; row < virus.length; row++) {
                for (int col = 0; col < virus[row].length; col++) {
                    if (virus[row][col] == 1) {
                        g2.fillRect(x + col * PX + 2, y + row * PX + 2, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(200, 40, 40));
            for (int row = 0; row < virus.length; row++) {
                for (int col = 0; col < virus[row].length; col++) {
                    if (virus[row][col] == 1) {
                        g2.fillRect(x + col * PX, y + row * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(255, 120, 120));
            g2.fillRect(x + PX, y, PX * 2, PX);
            g2.fillRect(x, y + PX, PX, PX);
            int cx = x + 16, cy = y + 12;
            int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
            g2.setColor(new Color(220, 60, 60));
            for (int[] sp : spikes) {
                g2.fillRect(cx + sp[0] * (12 + wobble) - PX / 2, cy + sp[1] * (12 + wobble) - PX / 2, PX, PX);
            }
            g2.setColor(new Color(255, 220, 220));
            g2.fillRect(x + PX * 2, y + PX, PX, PX);
            g2.fillRect(x + PX * 5, y + PX, PX, PX);
            drawHealthBar(g2, x, y - 8, 32, PX, (double) health / maxHealth, health > maxHealth / 2 ? new Color(60, 200, 80) : new Color(220, 60, 60), new Color(20, 40, 30));
        }
    }

    class SpeedEnemy extends Enemy {

        SpeedEnemy(int x, int y) {
            super(x, y);
            maxHealth = 30 + wave * 5;
            health = maxHealth;
            speed = 4;
            reward = 30;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 3) % 2;
            int[][] fast = {{0, 1, 1, 1, 0}, {1, 1, 1, 1, 1}, {1, 1, 1, 1, 1}, {0, 1, 1, 1, 0}};
            g2.setColor(new Color(255, 150, 0));
            for (int row = 0; row < fast.length; row++) {
                for (int col = 0; col < fast[row].length; col++) {
                    if (fast[row][col] == 1) {
                        g2.fillRect(x + col * PX, y + row * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(255, 200, 50, 100));
            for (int i = 1; i <= 3; i++) {
                g2.fillRect(x - i * 6 - wobble, y + PX, PX * 3, PX * 2);
            }
            g2.setColor(Color.WHITE);
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 3, y + PX, PX, PX);
            g2.setColor(new Color(255, 80, 0));
            g2.setFont(new Font("Monospaced", Font.BOLD, 8));
            g2.drawString(">>", x, y - 6);
            drawHealthBar(g2, x, y - 8, 20, PX, (double) health / maxHealth, new Color(255, 160, 0), new Color(20, 20, 10));
        }
    }

    class ShieldEnemy extends Enemy {

        int shieldHP = 40;

        ShieldEnemy(int x, int y) {
            super(x, y);
            maxHealth = 80 + wave * 15;
            health = maxHealth;
            speed = 1;
            reward = 40;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int[][] body = {{0, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 0}};
            g2.setColor(new Color(30, 120, 60));
            for (int row = 0; row < body.length; row++) {
                for (int col = 0; col < body[row].length; col++) {
                    if (body[row][col] == 1) {
                        g2.fillRect(x + col * PX, y + row * PX, PX, PX);
                    }
                }
            }
            if (shieldHP > 0) {
                g2.setColor(new Color(100, 200, 255, 180));
                g2.fillRect(x + PX * 5, y - PX, PX * 2, PX * 7);
                g2.setColor(new Color(200, 240, 255));
                g2.fillRect(x + PX * 5, y, PX, PX * 5);
            }
            g2.setColor(new Color(255, 240, 50));
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 4, y + PX, PX, PX);
            drawHealthBar(g2, x, y - 8, 32, PX, (double) health / maxHealth, new Color(30, 200, 80), new Color(10, 30, 15));
        }
    }

    class TankEnemy extends Enemy {

        TankEnemy(int x, int y) {
            super(x, y);
            maxHealth = 300 + wave * 30;
            health = maxHealth;
            speed = 1;
            reward = 80;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 10) % 2;
            g2.setColor(new Color(80, 80, 100));
            g2.fillRect(x, y, PX * 12, PX * 10);
            g2.setColor(new Color(120, 120, 150));
            g2.fillRect(x + PX, y + PX, PX * 10, PX * 2);
            g2.fillRect(x + PX, y + PX * 7, PX * 10, PX * 2);
            g2.setColor(new Color(40, 40, 50));
            g2.fillRect(x, y + PX * 8, PX * 4, PX * 3);
            g2.fillRect(x + PX * 8, y + PX * 8, PX * 4, PX * 3);
            g2.setColor(new Color(60, 60, 80));
            g2.fillRect(x + PX * 10, y + PX * 4, PX * 5, PX * 2);
            g2.setColor(new Color(255, 50, 50));
            g2.fillRect(x + PX * 2, y + PX * 3, PX * 2, PX * 2);
            g2.fillRect(x + PX * 7, y + PX * 3, PX * 2, PX * 2);
            g2.setColor(new Color(200, 80, 80));
            g2.setFont(new Font("Monospaced", Font.BOLD, 8));
            g2.drawString("TANK", x + 8, y - 6);
            drawHealthBar(g2, x, y - 8, PX * 12, PX, (double) health / maxHealth, new Color(60, 120, 220), new Color(10, 10, 25));
        }
    }

    class BossEnemy extends Enemy {

        BossEnemy(int x, int y) {
            super(x, y);
            maxHealth = 200 + wave * 20;
            health = maxHealth;
            speed = 1;
            reward = 150;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 8) % 2;
            int[][] boss = {{0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            g2.setColor(new Color(0, 0, 0, 100));
            for (int row = 0; row < boss.length; row++) {
                for (int col = 0; col < boss[row].length; col++) {
                    if (boss[row][col] == 1) {
                        g2.fillRect(x + col * PX + 3, y + row * PX + 3, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(140, 30, 160));
            for (int row = 0; row < boss.length; row++) {
                for (int col = 0; col < boss[row].length; col++) {
                    if (boss[row][col] == 1) {
                        g2.fillRect(x + col * PX, y + row * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(200, 60, 220));
            int[][] core = {{0, 1, 1, 0}, {1, 1, 1, 1}, {1, 1, 1, 1}, {0, 1, 1, 0}};
            for (int row = 0; row < core.length; row++) {
                for (int col = 0; col < core[row].length; col++) {
                    if (core[row][col] == 1) {
                        g2.fillRect(x + 16 + col * PX, y + 16 + row * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(255, 60, 60));
            g2.fillRect(x + PX * 3, y + PX * 2, PX * 2, PX * 2);
            g2.fillRect(x + PX * 7, y + PX * 2, PX * 2, PX * 2);
            g2.setColor(new Color(220, 60, 220));
            g2.setFont(new Font("Monospaced", Font.BOLD, 9));
            g2.drawString("JEFE", x + 12, y - 10);
            drawHealthBar(g2, x, y - 6, 48, PX, (double) health / maxHealth, new Color(180, 50, 200), new Color(20, 10, 25));
        }
    }

    class FinalBoss extends Enemy {

        FinalBoss(int x, int y) {
            super(x, y);
            maxHealth = 1500;
            health = 1500;
            speed = 1;
            reward = 500;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 4) % 3;
            int[][] final_ = {{0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            g2.setColor(new Color(0, 0, 0, 120));
            for (int row = 0; row < final_.length; row++) {
                for (int col = 0; col < final_[row].length; col++) {
                    if (final_[row][col] == 1) {
                        g2.fillRect(x + col * PX + 4, y + row * PX + 4, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(160, 20, 20));
            for (int row = 0; row < final_.length; row++) {
                for (int col = 0; col < final_[row].length; col++) {
                    if (final_[row][col] == 1) {
                        g2.fillRect(x + col * PX, y + row * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(255, 200, 0));
            g2.fillRect(x + PX * 3, y + PX * 3, PX * 3, PX * 3);
            g2.fillRect(x + PX * 10, y + PX * 3, PX * 3, PX * 3);
            g2.fillRect(x + PX * 7, y + PX * 2, PX * 2, PX * 2);
            g2.setColor(Color.BLACK);
            g2.fillRect(x + PX * 4, y + PX * 4, PX, PX);
            g2.fillRect(x + PX * 11, y + PX * 4, PX, PX);
            g2.setColor(new Color(255, 60, 0));
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.drawString("!! JEFE FINAL !!", x - 4, y - 12);
            drawHealthBar(g2, x - 4, y - 8, 80, PX + 2, (double) health / maxHealth, new Color(255, 80, 0), new Color(30, 5, 5));
        }
    }

    // =====================================================================
    // TOWER — with aiming
    // =====================================================================
    class Tower {

        int x, y;
        int range = 120;
        int cooldown = 0;
        String type;
        Enemy target = null;
        int animTick = 0;
        double aimAngle = 0; // radians, angle toward target

        Tower(int x, int y, String type) {
            this.x = x;
            this.y = y;
            this.type = type;
        }

        void update(java.util.List<Enemy> enemies) {
            if (cooldown > 0) {
                cooldown--;
            }
            animTick++;
            target = null;
            // Find nearest enemy in range
            double bestDist = Double.MAX_VALUE;
            for (Enemy e : enemies) {
                double dist = Math.hypot(x + 14 - (e.x + 16), y + 10 - (e.y + 12));
                if (dist < range && dist < bestDist) {
                    bestDist = dist;
                    target = e;
                }
            }
            if (target != null) {
                // Always update aim angle toward target
                aimAngle = Math.atan2((target.y + 12) - (y + 10), (target.x + 16) - (x + 14));

                if (cooldown == 0) {
                    int damage = switch (type) {
                        case "FIRE" ->
                            25;
                        case "ICE" ->
                            6;
                        case "ELEC" ->
                            18;
                        default ->
                            15;
                    };
                    damage += wave * 2;

                    if (type.equals("ICE") && target.speed > 1) {
                        target.speed--;
                    }

                    if (target instanceof ShieldEnemy) {
                        ShieldEnemy se = (ShieldEnemy) target;
                        if (se.shieldHP > 0) {
                            se.shieldHP -= damage;
                            if (se.shieldHP < 0) {
                                target.health += se.shieldHP;
                                se.shieldHP = 0;
                            }
                        } else {
                            target.health -= damage;
                        }
                    } else {
                        target.health -= damage;
                    }
                    cooldown = 20;
                }
            }
        }

        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            Color mainColor, accentColor;
            switch (type) {
                case "FIRE":
                    mainColor = new Color(220, 80, 30);
                    accentColor = new Color(255, 160, 60);
                    break;
                case "ICE":
                    mainColor = new Color(40, 160, 220);
                    accentColor = new Color(180, 230, 255);
                    break;
                case "ELEC":
                    mainColor = new Color(200, 180, 0);
                    accentColor = new Color(255, 240, 80);
                    break;
                default:
                    mainColor = new Color(40, 180, 100);
                    accentColor = new Color(180, 255, 200);
                    break;
            }

            // Range circle (dotted)
            g2.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 18));
            g2.fillOval(x - range + 12, y - range + 12, range * 2, range * 2);
            g2.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 40));
            g2.setStroke(new BasicStroke(PX));
            for (int deg = 0; deg < 360; deg += 12) {
                double rad = Math.toRadians(deg);
                int rx = (int) (x + 12 + (range - 4) * Math.cos(rad));
                int ry = (int) (y + 12 + (range - 4) * Math.sin(rad));
                g2.fillRect(rx, ry, PX, PX);
            }

            // Base
            g2.setColor(new Color(20, 50, 35));
            g2.fillRect(x - 2, y + 18, 32, 8);
            g2.setColor(new Color(0, 100, 60));
            g2.fillRect(x, y + 20, 28, 4);

            // Body (fixed)
            switch (type) {
                case "NORMAL":
                    drawTowerBody_Normal(g2, x, y, mainColor, accentColor);
                    break;
                case "FIRE":
                    drawTowerBody_Fire(g2, x, y, mainColor, accentColor);
                    break;
                case "ICE":
                    drawTowerBody_Ice(g2, x, y, mainColor, accentColor);
                    break;
                case "ELEC":
                    drawTowerBody_Elec(g2, x, y, mainColor, accentColor);
                    break;
            }

            // === ROTATING BARREL toward target ===
            int bx = x + 14, by = y + 10; // center of tower
            AffineTransform old = g2.getTransform();
            g2.rotate(aimAngle, bx, by);
            g2.setColor(accentColor);
            g2.fillRect(bx, by - 2, 18, 4); // barrel pointing right, rotated
            g2.setColor(mainColor);
            g2.fillRect(bx + 14, by - 1, 6, 2); // tip
            g2.setTransform(old);

            // Projectile beam
            if (target != null && cooldown > 15) {
                drawPixelBeam(g2, bx, by, target.x + 16, target.y + 12, mainColor, accentColor);
            }
        }

        void drawTowerBody_Normal(Graphics2D g, int x, int y, Color mc, Color ac) {
            g.setColor(new Color(30, 60, 50));
            g.fillRect(x + 2, y + 2, 26, 20);
            g.setColor(mc);
            g.fillRect(x, y, 26, 20);
            g.setColor(new Color(100, 220, 120));
            g.fillRect(x + 4, y + 4, 14, 12);
            g.setColor(new Color(60, 100, 80));
            g.fillRect(x - 4, y + 2, 4, 16);
            g.setColor(ac);
            g.fillRect(x - 6, y, 4, 20);
            drawMedCross(g, x + 13, y - 6, 8, new Color(220, 50, 60));
        }

        void drawTowerBody_Fire(Graphics2D g, int x, int y, Color mc, Color ac) {
            g.setColor(new Color(40, 15, 10));
            g.fillRect(x + 2, y + 2, 26, 20);
            g.setColor(mc);
            g.fillRect(x, y, 26, 20);
            g.setColor(new Color(255, 60, 30));
            g.fillRect(x + 4, y + 4, 10, 12);
            g.setColor(new Color(255, 140, 80));
            g.fillRect(x + 6, y + 6, 6, 8);
            g.setColor(ac);
            g.setFont(new Font("Monospaced", Font.BOLD, 8));
            g.drawString("UV", x + 18, y + 14);
        }

        void drawTowerBody_Ice(Graphics2D g, int x, int y, Color mc, Color ac) {
            g.setColor(new Color(10, 40, 60));
            g.fillRect(x + 2, y + 2, 20, 22);
            g.setColor(mc);
            g.fillRect(x, y, 20, 22);
            g.setColor(ac);
            g.fillRect(x + 2, y + 6, 16, 4);
            g.fillRect(x + 2, y + 14, 16, 4);
            g.setColor(new Color(180, 230, 255));
            g.fillRect(x + 8, y - 8, PX, PX * 3);
            g.fillRect(x + 4, y - 4, PX * 3, PX);
            g.fillRect(x + 12, y - 4, PX * 3, PX);
        }

        void drawTowerBody_Elec(Graphics2D g, int x, int y, Color mc, Color ac) {
            g.setColor(new Color(40, 35, 5));
            g.fillRect(x + 2, y + 2, 22, 20);
            g.setColor(mc);
            g.fillRect(x, y, 22, 20);
            g.setColor(new Color(60, 55, 10));
            for (int fy = y + 2; fy < y + 18; fy += 4) {
                g.fillRect(x + 2, fy, 18, 2);
            }
            g.setColor(ac);
            g.fillRect(x + 8, y - 8, 6, 8);
            g.fillRect(x + 10, y - 12, 2, 4);
            if (animTick % 6 < 3) {
                g.setColor(new Color(255, 240, 80, 200));
                g.fillRect(x + 14, y - 10, PX, PX);
                g.fillRect(x + 6, y - 8, PX, PX);
            } else {
                g.setColor(new Color(255, 200, 40, 200));
                g.fillRect(x + 12, y - 12, PX, PX);
                g.fillRect(x + 8, y - 6, PX, PX);
            }
        }

        void drawPixelBeam(Graphics2D g, int x1, int y1, int x2, int y2, Color mc, Color ac) {
            int steps = 10;
            for (int s = 0; s < steps; s++) {
                float t = (float) s / steps;
                int bx = (int) (x1 + (x2 - x1) * t);
                int by = (int) (y1 + (y2 - y1) * t);
                int jitter = (s % 2 == 0) ? -PX : PX;
                g.setColor(s % 2 == 0 ? mc : ac);
                g.fillRect(bx + jitter, by, PX * 2, PX);
            }
            g.setColor(ac);
            g.fillRect(x2 - PX, y2 - PX, PX * 3, PX * 3);
        }
    }
}
