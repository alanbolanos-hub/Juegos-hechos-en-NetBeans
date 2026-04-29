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
    // SCREEN / SCALE — se ajusta al tamaño real de pantalla en main()
    // =====================================================================
    static int SW = 1280;  // ancho de pantalla (se fija en main)
    static int SH = 720;   // alto de pantalla  (se fija en main)
// drawmenu
    // Zonas relativas al tamaño de pantalla (recalculadas en recalcLayout)
    int PATH_TOP, PATH_BOT, UI_TOP_H, UI_BOT_Y, UI_BOT_H;
    int BASE_X;
    int TOWER_SIZE = 52;
    static final int PX = 4;

    void recalcLayout() {
        PATH_TOP = (int) (SH * 0.16);
        PATH_BOT = (int) (SH * 0.50);
        UI_TOP_H = (int) (SH * 0.07);
        UI_BOT_Y = (int) (SH * 0.84);
        UI_BOT_H = SH - UI_BOT_Y;
        BASE_X = (int) (SW * 0.92);
    }
    // =====================================================================
    // GAME STATE
    // =====================================================================
    enum GameState {
        MENU, SETTINGS, PLAYING
    }
    GameState gameState = GameState.MENU;

    enum Difficulty {
        EASY, NORMAL, HARD
    }
    Difficulty difficulty = Difficulty.NORMAL;

    boolean paused = false;
    boolean firstWave = true;   // solo pausa al inicio del juego
    boolean waitingToStart = true;   // true SOLO en el primer arranque

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
    int mouseX = 0, mouseY = 0;

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
                for (int n : new int[]{60, 55, 50, 45, 40, 35}) {
                    channels[1].noteOn(n, 110);
                    Thread.sleep(120);
                    channels[1].noteOff(n);
                }
                for (int n : new int[]{60, 64, 67, 72}) {
                    channels[0].noteOn(n, 90);
                    Thread.sleep(100);
                    channels[0].noteOff(n);
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
                for (int n : new int[]{72, 76, 79}) {
                    channels[1].noteOn(n, 80);
                    Thread.sleep(80);
                    channels[1].noteOff(n);
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
                for (int n : new int[]{60, 64, 67, 72, 71, 72, 76}) {
                    channels[0].noteOn(n, 100);
                    Thread.sleep(130);
                    channels[0].noteOff(n);
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
                for (int n : new int[]{55, 50, 45, 40}) {
                    channels[0].noteOn(n, 100);
                    Thread.sleep(200);
                    channels[0].noteOff(n);
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
    // COLORS
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

    // =====================================================================
    // CONSTRUCTOR
    // =====================================================================
    public TDX() {
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
    // DIFFICULTY
    // =====================================================================
    double enemyHealthMult() {
        switch (difficulty) {
            case EASY:
                return 0.6;
            case HARD:
                return 1.4;
            default:
                return 1.0;
        }
    }

    double enemySpeedMult() {
        switch (difficulty) {
            case EASY:
                return 0.75;
            case HARD:
                return 1.2;
            default:
                return 1.0;
        }
    }

    int startMoney() {
        switch (difficulty) {
            case EASY:
                return 250;
            case HARD:
                return 175;
            default:
                return 200;
        }
    }

    int waveBonus() {
        switch (difficulty) {
            case EASY:
                return 80;
            case HARD:
                return 40;
            default:
                return 60;
        }
    }

    // =====================================================================
    // GAME LOGIC
    // =====================================================================
    void startGame() {
        recalcLayout();
        money = startMoney();
        baseHealth = 100;
        wave = 1;
        gameOver = false;
        gameWon = false;
        paused = false;
        firstWave = true;    // primera oleada → pedir confirmacion
        waitingToStart = true;
        enemies.clear();
        towers.clear();
        selectedTower = "NORMAL";
        gameState = GameState.PLAYING;
        prepareWave();
        startBGMusic();
        if (!timer.isRunning()) {
            timer.restart();
        }
    }

    void prepareWave() {
        enemies.clear();
        int totalEnemies = wave * 5;
        int rows = 5;
        for (int i = 0; i < totalEnemies; i++) {
            int col = i / rows, row = i % rows;
            int startX = -40 - col * 60;
            int startY = PATH_TOP + 12 + row * ((PATH_BOT - PATH_TOP - 24) / rows);
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
            e.speed = (int) Math.max(1, Math.round(e.speed * enemySpeedMult()));
            enemies.add(e);
        }
        if (wave % 3 == 0) {
            Enemy boss = (wave == 15) ? new FinalBoss(-200, PATH_TOP + 40) : new BossEnemy(-200, PATH_TOP + 60);
            boss.maxHealth = (int) (boss.maxHealth * enemyHealthMult());
            boss.health = boss.maxHealth;
            enemies.add(boss);
        }
    }

    void launchWave() {
        sfxNewWave(wave);
        waitingToStart = false;
        firstWave = false;
    }

    // =====================================================================
    // TOWER PLACEMENT
    // =====================================================================
    boolean isValidTowerPosition(int x, int y) {
        if (x < 10 || x > SW - 30 || y < UI_TOP_H + 4 || y > UI_BOT_Y - 10) {
            return false;
        }
        if (y > PATH_TOP - 10 && y < PATH_BOT + 10) {
            return false;
        }
        if (x > BASE_X - 10) {
            return false;
        }
        return true;
    }

    boolean towerExistsAt(int x, int y) {
        for (Tower t : towers) {
            if (Math.abs(t.x - x) < TOWER_SIZE && Math.abs(t.y - y) < TOWER_SIZE) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    // DRAW HELPERS
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
        int[][] h = {{0, 1, 1, 0, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 0, 0, 0}};
        g.setColor(c);
        for (int r = 0; r < h.length; r++) {
            for (int col = 0; col < h[r].length; col++) {
                if (h[r][col] == 1) {
                    g.fillRect(x + col * 3, y + r * 3, 3, 3);
                }
            }
        }
    }

    void drawPixelCoin(Graphics2D g, int x, int y) {
        int[][] c = {{0, 1, 1, 1, 0}, {1, 1, 0, 1, 1}, {1, 0, 1, 0, 1}, {1, 1, 0, 1, 1}, {0, 1, 1, 1, 0}};
        g.setColor(COL_MONEY);
        for (int r = 0; r < c.length; r++) {
            for (int col = 0; col < c[r].length; col++) {
                if (c[r][col] == 1) {
                    g.fillRect(x + col * 4, y + r * 4, 4, 4);
                }
            }
        }
    }

    void drawPixelBio(Graphics2D g, int x, int y) {
        int[][] b = {{0, 0, 1, 1, 0, 0}, {0, 1, 0, 0, 1, 0}, {1, 0, 1, 1, 0, 1}, {1, 0, 1, 1, 0, 1}, {0, 1, 0, 0, 1, 0}, {0, 0, 1, 1, 0, 0}};
        g.setColor(new Color(80, 200, 120));
        for (int r = 0; r < b.length; r++) {
            for (int col = 0; col < b[r].length; col++) {
                if (b[r][col] == 1) {
                    g.fillRect(x + col * 4, y + r * 4, 4, 4);
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
        SW = getWidth();
        SH = getHeight();
        recalcLayout();
        
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
    // MENU
    // =====================================================================
    void drawMenu(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, SW, SH);
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }

        int cy = SH / 2;
        g2.setColor(new Color(20, 40, 35));
        g2.fillRect(0, cy - 60, SW, 120);
        g2.setColor(COL_PATH_EDGE);
        for (int px = 0; px < SW; px += PX * 4) {
            g2.fillRect(px, cy - 60, PX * 2, PX);
            g2.fillRect(px, cy + 56, PX * 2, PX);
        }

        int titleX = SW / 2 - 80;
        g2.setColor(new Color(0, 80, 40));
        g2.setFont(pixelFont(60));
        g2.drawString("TDX", titleX + 4, (int) (SH * 0.22) + 4);
        g2.setColor(COL_PATH_EDGE);
        g2.drawString("TDX", titleX, (int) (SH * 0.22));
        g2.setColor(new Color(180, 255, 200, 200));
        g2.setFont(pixelFont(15));
        g2.drawString("DEFENSA MEDICA", titleX - 30, (int) (SH * 0.22) + 30);

        drawMenuVirus(g2, (int) (SW * 0.08), (int) (SH * 0.25), 0);
        drawMenuVirus(g2, (int) (SW * 0.84), (int) (SH * 0.22), 1);
        drawMenuVirus(g2, (int) (SW * 0.14), (int) (SH * 0.70), 2);
        drawMenuVirus(g2, (int) (SW * 0.78), (int) (SH * 0.68), 3);

        String[] lbls = {"JUGAR", "AJUSTES"};
        int bw = 300, bh = 60;
        int bx0 = SW / 2 - bw / 2;
        int[] byArr = {(int) (SH * 0.38), (int) (SH * 0.53)};
        for (int i = 0; i < 2; i++) {
            boolean hover = mouseX >= bx0 && mouseX <= bx0 + bw && mouseY >= byArr[i] && mouseY <= byArr[i] + bh;
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRect(bx0 + 4, byArr[i] + 4, bw, bh);
            g2.setColor(hover ? new Color(0, 200, 100, 30) : new Color(10, 25, 18));
            g2.fillRect(bx0, byArr[i], bw, bh);
            g2.setColor(hover ? COL_PATH_EDGE : new Color(0, 120, 70));
            g2.setStroke(new BasicStroke(PX));
            g2.drawRect(bx0, byArr[i], bw, bh);
            g2.fillRect(bx0 - PX, byArr[i] - PX, PX * 2, PX * 2);
            g2.fillRect(bx0 + bw - PX, byArr[i] - PX, PX * 2, PX * 2);
            g2.fillRect(bx0 - PX, byArr[i] + bh - PX, PX * 2, PX * 2);
            g2.fillRect(bx0 + bw - PX, byArr[i] + bh - PX, PX * 2, PX * 2);
            g2.setColor(hover ? COL_BG : COL_UI_TEXT);
            g2.setFont(pixelFont(22));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(lbls[i], bx0 + (bw - fm.stringWidth(lbls[i])) / 2, byArr[i] + 38);
        }
        g2.setColor(new Color(100, 150, 120));
        g2.setFont(pixelFont(11));
        g2.drawString("DIFICULTAD: " + difficulty.name(), SW / 2 - 70, (int) (SH * 0.68));
        g2.setColor(new Color(60, 100, 80));
        g2.setFont(pixelFont(9));
        String hint = "Usa el raton para colocar torres  |  Teclas 1-4 para cambiar tipo";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(hint, SW / 2 - fm.stringWidth(hint) / 2, SH - 20);
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
        g2.setColor(new Color(cols[variant % cols.length].getRed(), cols[variant % cols.length].getGreen(), cols[variant % cols.length].getBlue(), 80));
        int cx = x + 16, cy = y + 12;
        int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        for (int[] sp : spikes) {
            g2.fillRect(cx + sp[0] * (10 + wobble) - PX / 2, cy + sp[1] * (10 + wobble) - PX / 2, PX, PX);
        }
    }

    // =====================================================================
    // SETTINGS
    // =====================================================================
    void drawSettings(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, SW, SH);
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }
        g2.setColor(COL_PATH_EDGE);
        g2.setFont(pixelFont(30));
        FontMetrics fmT = g2.getFontMetrics();
        g2.drawString("AJUSTES", SW / 2 - fmT.stringWidth("AJUSTES") / 2, (int) (SH * 0.13));
        g2.setColor(new Color(0, 80, 40));
        g2.fillRect(0, (int) (SH * 0.15), SW, PX);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(0, (int) (SH * 0.15), SW, PX);

        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(16));
        g2.drawString("DIFICULTAD", SW / 2 - 60, (int) (SH * 0.27));

        String[] descs = {
            "Enemigos con -40% vida, velocidad reducida. Dinero: $250, +$80 por oleada.",
            "Experiencia balanceada. Dinero inicial: $200, +$60 por oleada.",
            "Enemigos con +40% vida y velocidad. Dinero: $175, +$40 por oleada."
        };
        String[] diffNames = {"FACIL", "NORMAL", "DIFICIL"};
        Color[] diffColors = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60)};
        Difficulty[] diffs = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
        int btnW = 220, btnH = 70;
        int totalW = 3 * btnW + 2 * 30;
        int startBX = SW / 2 - totalW / 2;
        int[] bxArr = {startBX, startBX + btnW + 30, startBX + 2 * (btnW + 30)};
        int by = (int) (SH * 0.33);
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

        int descBoxY = (int) (SH * 0.55), descBoxH = 130;
        g2.setColor(new Color(10, 25, 18));
        g2.fillRect(startBX, descBoxY, totalW, descBoxH);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(startBX, descBoxY, totalW, descBoxH);
        int descIdx = difficulty.ordinal();
        Color[] dc2 = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60)};
        g2.setColor(dc2[descIdx]);
        g2.setFont(pixelFont(10));
        g2.drawString(descs[descIdx], startBX + 20, descBoxY + 24);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(10));
        g2.drawString("VIDA ENE:", startBX + 20, descBoxY + 55);
        g2.drawString("VEL ENE:", startBX + 20, descBoxY + 75);
        g2.drawString("DINERO:", startBX + 20, descBoxY + 95);
        double[] hM = {0.6, 1.0, 1.4}, sM = {0.75, 1.0, 1.2};
        int[] mI = {250, 200, 175};
        drawSettingBar(g2, startBX + 120, descBoxY + 44, 200, 16, hM[descIdx] / 1.4, dc2[descIdx]);
        drawSettingBar(g2, startBX + 120, descBoxY + 64, 200, 16, sM[descIdx] / 1.2, dc2[descIdx]);
        drawSettingBar(g2, startBX + 120, descBoxY + 84, 200, 16, mI[descIdx] / 250.0, dc2[descIdx]);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(9));
        g2.drawString(String.format("x%.2f", hM[descIdx]), startBX + 330, descBoxY + 55);
        g2.drawString(String.format("x%.2f", sM[descIdx]), startBX + 330, descBoxY + 75);
        g2.drawString("$" + mI[descIdx], startBX + 330, descBoxY + 95);

        int backX = SW / 2 - 110, backY = (int) (SH * 0.84), backW = 220, backH = 50;
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
        g2.fillRect(0, 0, SW, SH);
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }

        // Zone tints above/below path
        g2.setColor(new Color(0, 80, 40, 18));
        g2.fillRect(0, UI_TOP_H, BASE_X, PATH_TOP - UI_TOP_H);
        g2.fillRect(0, PATH_BOT, BASE_X, UI_BOT_Y - PATH_BOT);

        // Path
        g2.setColor(COL_PATH);
        g2.fillRect(0, PATH_TOP, SW, PATH_BOT - PATH_TOP);
        g2.setColor(COL_PATH_EDGE);
        g2.setStroke(new BasicStroke(PX));
        for (int px = 0; px < SW; px += PX * 4) {
            g2.fillRect(px, PATH_TOP, PX * 2, PX);
            g2.fillRect(px, PATH_BOT - PX, PX * 2, PX);
        }
        g2.setColor(new Color(0, 100, 60));
        int midPath = (PATH_TOP + PATH_BOT) / 2;
        for (int px = 0; px < SW; px += PX * 6) {
            g2.fillRect(px, midPath - PX / 2, PX * 3, PX);
        }

        // Base (right side)
        int baseW = 50, baseH = PATH_BOT - PATH_TOP;
        g2.setColor(new Color(15, 35, 30));
        g2.fillRect(BASE_X - 6, PATH_TOP + 4, baseW + 12, baseH - 8);
        g2.setColor(COL_BASE);
        g2.fillRect(BASE_X, PATH_TOP + 8, baseW, baseH - 16);
        g2.setColor(new Color(180, 255, 200));
        for (int wy = PATH_TOP + 20; wy < PATH_BOT - 20; wy += 22) {
            g2.fillRect(BASE_X + 8, wy, 8, 8);
            g2.fillRect(BASE_X + 24, wy, 8, 8);
        }
        drawMedCross(g2, BASE_X + 25, midPath, 28, new Color(220, 50, 60));
        g2.setColor(COL_PATH_EDGE);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(BASE_X, PATH_TOP + 8, baseW, baseH - 16);

        // Enemies & towers
        for (Enemy e : enemies) {
            e.draw(g2);
        }
        for (Tower t : towers) {
            t.draw(g2);
        }

        // Hover indicator
        if (!gameOver && !gameWon) {
            boolean valid = isValidTowerPosition(mouseX, mouseY) && !towerExistsAt(mouseX, mouseY);
            g2.setColor(valid ? new Color(0, 200, 100, 60) : new Color(200, 50, 50, 60));
            g2.fillRect(mouseX - TOWER_SIZE / 2, mouseY - TOWER_SIZE / 2, TOWER_SIZE, TOWER_SIZE);
            g2.setColor(valid ? new Color(0, 200, 100, 150) : new Color(200, 50, 50, 150));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(mouseX - TOWER_SIZE / 2, mouseY - TOWER_SIZE / 2, TOWER_SIZE, TOWER_SIZE);
        }

        // ── TOP UI BAR ──
        g2.setColor(COL_UI_BG);
        g2.fillRect(0, 0, SW, UI_TOP_H);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(0, UI_TOP_H, SW, UI_TOP_H);
        g2.setColor(new Color(100, 200, 150, 120));
        g2.fillRect(0, 0, SW, 8);

        int topY = UI_TOP_H / 2 + 5;
        // Health
        drawPixelHeart(g2, 10, UI_TOP_H / 2 - 9, COL_HEALTH);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(14));
        g2.drawString("" + baseHealth, 36, topY);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(85, 6, PX, UI_TOP_H - 12);
        // Money
        drawPixelCoin(g2, 96, UI_TOP_H / 2 - 10);
        g2.setColor(COL_MONEY);
        g2.setFont(pixelFont(14));
        g2.drawString("$" + money, 122, topY);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(200, 6, PX, UI_TOP_H - 12);
        // Wave
        drawPixelBio(g2, 212, UI_TOP_H / 2 - 10);
        g2.setColor(COL_WAVE);
        g2.setFont(pixelFont(14));
        g2.drawString("OLEADA " + wave + "/" + MAX_WAVE, 238, topY);
        // Difficulty tag
        Color[] dCols = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60)};
        String[] dNames = {"FACIL", "NORMAL", "DIFICIL"};
        g2.setColor(dCols[difficulty.ordinal()]);
        g2.setFont(pixelFont(9));
        g2.drawString("[" + dNames[difficulty.ordinal()] + "]", 400, topY);
        // Progress bar
        int progX = 470, progW = SW - 470 - 300, progH = 20;
        g2.setColor(new Color(10, 30, 20));
        g2.fillRect(progX, UI_TOP_H / 2 - progH / 2, progW, progH);
        g2.setColor(new Color(0, 180, 100));
        g2.fillRect(progX, UI_TOP_H / 2 - progH / 2, (int) (progW * (double) wave / MAX_WAVE), progH);
        g2.setColor(COL_UI_BORDER);
        g2.drawRect(progX, UI_TOP_H / 2 - progH / 2, progW, progH);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(9));
        g2.drawString("PROGRESO", progX + progW / 2 - 30, UI_TOP_H / 2 + 4);

        // ── PLAY/PAUSE BUTTON (top-right area) ──
        drawPlayPauseButton(g2);

        // ── MENU BUTTON (right of play/pause) ──
        drawMenuButton(g2);

        // ── BOTTOM UI BAR ──
        g2.setColor(COL_UI_BG);
        g2.fillRect(0, UI_BOT_Y, SW, UI_BOT_H);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(0, UI_BOT_Y, SW, UI_BOT_Y);
        g2.setColor(new Color(100, 200, 150, 120));
        g2.fillRect(0, UI_BOT_Y, SW, 10);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(10));
        g2.drawString("[ SELECCIONAR DEFENSA ]  —  Click en zona verde para colocar torres", 10, UI_BOT_Y + 16);

        String[] types = {"NORMAL", "FIRE", "ICE", "ELEC"};
        int[] costs = {50, 70, 60, 80};
        String[] labels = {"JERINGA", "IBUPROFENO", "ANALGESICO", "AMOXICILINA"};
        String[] keys = {"[1]", "[2]", "[3]", "[4]"};
        Color[] colors = {new Color(180, 255, 200), new Color(255, 120, 60), new Color(80, 200, 255), new Color(255, 230, 60)};
        int btnW = (SW - 40) / 4 - 10;
        int btnH = UI_BOT_H - 24;
        for (int i = 0; i < types.length; i++) {
            boolean selected = selectedTower.equals(types[i]);
            int bx = 10 + i * (btnW + 10), by2 = UI_BOT_Y + 20, bh = btnH;
            if (selected) {
                g2.setColor(new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 30));
                g2.fillRect(bx, by2, btnW, bh);
                g2.setColor(colors[i]);
                g2.setStroke(new BasicStroke(PX));
                g2.drawRect(bx, by2, btnW, bh);
                g2.fillRect(bx - PX, by2 - PX, PX * 2, PX * 2);
                g2.fillRect(bx + btnW - PX, by2 - PX, PX * 2, PX * 2);
                g2.fillRect(bx - PX, by2 + bh - PX, PX * 2, PX * 2);
                g2.fillRect(bx + btnW - PX, by2 + bh - PX, PX * 2, PX * 2);
            } else {
                g2.setColor(new Color(20, 40, 35));
                g2.fillRect(bx, by2, btnW, bh);
                g2.setColor(new Color(0, 80, 55));
                g2.setStroke(new BasicStroke(PX));
                g2.drawRect(bx, by2, btnW, bh);
            }
            drawTowerIcon(g2, bx + 8, by2 + 12, types[i], colors[i]);
            g2.setColor(selected ? colors[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(11));
            g2.drawString(labels[i], bx + 52, by2 + 22);
            g2.setColor(new Color(100, 150, 120));
            g2.setFont(pixelFont(9));
            g2.drawString(keys[i], bx + 52, by2 + 36);
            g2.setColor(COL_MONEY);
            g2.setFont(pixelFont(11));
            g2.drawString("$" + costs[i], bx + 52, by2 + 52);
            if (money < costs[i]) {
                g2.setColor(new Color(180, 50, 50, 150));
                g2.fillRect(bx, by2, btnW, bh);
                g2.setColor(new Color(220, 80, 80));
                g2.setFont(pixelFont(9));
                g2.drawString("SIN FONDOS", bx + btnW / 2 - 38, by2 + bh - 8);
            }
        }

        // ── "Waiting to start" overlay — SOLO primera oleada ──
        if (waitingToStart && !gameOver && !gameWon) {
            drawWaitingToStart(g2);
        }

        // ── Pause overlay ──
        if (paused && !gameOver && !gameWon && !waitingToStart) {
            drawPauseOverlay(g2);
        }

        // ── End screens ──
        if (gameOver) {
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, SW, SH);
            g2.setColor(new Color(220, 50, 60));
            g2.setFont(pixelFont(48));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("GAME OVER", SW / 2 - fm.stringWidth("GAME OVER") / 2, SH / 2 - 60);
            g2.setColor(COL_UI_TEXT);
            g2.setFont(pixelFont(18));
            String msg = "La base fue destruida en la oleada " + wave;
            fm = g2.getFontMetrics();
            g2.drawString(msg, SW / 2 - fm.stringWidth(msg) / 2, SH / 2);
            drawEndButton(g2, SW / 2 - 150, SH / 2 + 40, 300, 55, "VOLVER AL MENU");
        }
        if (gameWon) {
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, SW, SH);
            g2.setColor(new Color(0, 220, 120));
            g2.setFont(pixelFont(34));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("DEFENSA COMPLETADA!", SW / 2 - fm.stringWidth("DEFENSA COMPLETADA!") / 2, SH / 2 - 70);
            g2.setColor(COL_MONEY);
            g2.setFont(pixelFont(20));
            String s1 = "Sobreviviste las 15 oleadas";
            fm = g2.getFontMetrics();
            g2.drawString(s1, SW / 2 - fm.stringWidth(s1) / 2, SH / 2 - 20);
            g2.setColor(COL_UI_TEXT);
            g2.setFont(pixelFont(14));
            String s2 = "Puntuacion: $" + money + "  |  Vida: " + baseHealth;
            fm = g2.getFontMetrics();
            g2.drawString(s2, SW / 2 - fm.stringWidth(s2) / 2, SH / 2 + 20);
            drawEndButton(g2, SW / 2 - 150, SH / 2 + 60, 300, 55, "VOLVER AL MENU");
        }
    }

    // ── Play/Pause button ──
    // Solo se muestra como "JUGAR" al inicio del juego (waitingToStart).
    // Después funciona como pausa normal sin bloquear oleadas automáticamente.
    void drawPlayPauseButton(Graphics2D g2) {
        int bw = 100, bh = 26;
        int bx = SW - bw - 115, by = UI_TOP_H / 2 - bh / 2;
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        boolean active = waitingToStart || paused;

        if (active) {
            long t = System.currentTimeMillis();
            int glow = (int) (Math.abs(Math.sin(t / 400.0)) * 40) + 15;
            g2.setColor(new Color(0, 220, 100, glow));
            g2.fillRect(bx - 4, by - 4, bw + 8, bh + 8);
        }
        g2.setColor(hover ? new Color(0, 200, 100, 50) : new Color(10, 25, 18));
        g2.fillRect(bx, by, bw, bh);
        g2.setColor(hover ? COL_PATH_EDGE : (active ? new Color(0, 200, 80) : new Color(0, 140, 80)));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(bx, by, bw, bh);

        g2.setColor(active ? new Color(0, 255, 120) : COL_UI_TEXT);
        if (waitingToStart || paused) {
            // play triangle
            for (int row = 0; row < 10; row++) {
                int w2 = 10 - Math.abs(row - 5);
                g2.fillRect(bx + 8, by + 3 + row, w2, 1);
            }
            g2.setFont(pixelFont(10));
            g2.drawString("JUGAR", bx + 24, by + bh - 6);
        } else {
            // pause bars
            g2.setColor(COL_UI_TEXT);
            g2.fillRect(bx + 8, by + 7, 6, 12);
            g2.fillRect(bx + 18, by + 7, 6, 12);
            g2.setFont(pixelFont(10));
            g2.drawString("PAUSA", bx + 30, by + bh - 6);
        }
    }

    // ── Botón MENÚ en partida ──
    void drawMenuButton(Graphics2D g2) {
        int bw = 100, bh = 26;
        int bx = SW - bw - 10, by = UI_TOP_H / 2 - bh / 2;
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        g2.setColor(hover ? new Color(200, 100, 50, 50) : new Color(10, 25, 18));
        g2.fillRect(bx, by, bw, bh);
        g2.setColor(hover ? new Color(255, 150, 80) : new Color(180, 80, 30));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(bx, by, bw, bh);
        g2.setColor(hover ? COL_BG : new Color(255, 160, 100));
        g2.setFont(pixelFont(10));
        g2.drawString("< MENU", bx + 14, by + bh - 6);
    }

    // Coords helpers para los botones de la top-bar
    int ppBtnX() {
        return SW - 100 - 115;
    }

    int ppBtnY() {
        return UI_TOP_H / 2 - 13;
    }

    int menuBtnX() {
        return SW - 100 - 10;
    }

    int menuBtnY() {
        return UI_TOP_H / 2 - 13;
    }

    void drawWaitingToStart(Graphics2D g2) {
        int barY = (PATH_TOP + PATH_BOT) / 2 - 50;
        g2.setColor(new Color(0, 0, 0, 130));
        g2.fillRect(0, barY, SW, 100);
        long t = System.currentTimeMillis();
        int alpha = (int) (Math.abs(Math.sin(t / 600.0)) * 80) + 140;
        g2.setColor(new Color(0, 220, 100, alpha));
        g2.setFont(pixelFont(24));
        String msg = "Presiona  JUGAR  para iniciar la Oleada " + wave;
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, SW / 2 - fm.stringWidth(msg) / 2, barY + 50);
        g2.setColor(new Color(180, 255, 200, 160));
        g2.setFont(pixelFont(10));
        String sub = "(Coloca torres primero — gratis durante preparacion)";
        fm = g2.getFontMetrics();
        g2.drawString(sub, SW / 2 - fm.stringWidth(sub) / 2, barY + 72);
    }

    void drawPauseOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 130));
        g2.fillRect(0, UI_TOP_H, SW, UI_BOT_Y - UI_TOP_H);
        g2.setColor(new Color(0, 220, 100));
        g2.setFont(pixelFont(36));
        String msg = "-- PAUSA --";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, SW / 2 - fm.stringWidth(msg) / 2, SH / 2 - 10);
        g2.setColor(new Color(180, 255, 200, 200));
        g2.setFont(pixelFont(13));
        String sub = "Haz click en JUGAR para continuar";
        fm = g2.getFontMetrics();
        g2.drawString(sub, SW / 2 - fm.stringWidth(sub) / 2, SH / 2 + 28);
    }

    void drawEndButton(Graphics2D g2, int x, int y, int w, int h, String label) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g2.setColor(hover ? new Color(0, 200, 100, 40) : new Color(10, 25, 18));
        g2.fillRect(x, y, w, h);
        g2.setColor(hover ? COL_PATH_EDGE : new Color(0, 120, 70));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(x, y, w, h);
        g2.setColor(hover ? COL_BG : COL_UI_TEXT);
        g2.setFont(pixelFont(15));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h / 2 + 5);
    }

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
        if (gameState != GameState.PLAYING || gameOver || gameWon || paused || waitingToStart) {
            repaint();
            return;
        }

        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy en = it.next();
            en.move();
            if (en.x > BASE_X + 10) {
                baseHealth -= (en instanceof FinalBoss ? 40 : en instanceof TankEnemy ? 20 : en instanceof BossEnemy ? 25 : 10);
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
                money += waveBonus();
                // NO waitingToStart entre oleadas — el juego continúa solo
                prepareWave();
                sfxNewWave(wave);
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
            int bw = 300, bh = 60, bx0 = SW / 2 - bw / 2;
            int[] byArr = {(int) (SH * 0.38), (int) (SH * 0.53)};
            if (mx >= bx0 && mx <= bx0 + bw && my >= byArr[0] && my <= byArr[0] + bh) {
                sfxMenuClick();
                startGame();
            } else if (mx >= bx0 && mx <= bx0 + bw && my >= byArr[1] && my <= byArr[1] + bh) {
                sfxMenuClick();
                gameState = GameState.SETTINGS;
            }

        } else if (gameState == GameState.SETTINGS) {
            Difficulty[] diffs = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
            int btnW = 220, btnH = 70;
            int totalW = 3 * btnW + 60, startBX = SW / 2 - totalW / 2;
            int[] bxArr = {startBX, startBX + btnW + 30, startBX + 2 * (btnW + 30)};
            int by = (int) (SH * 0.33);
            for (int i = 0; i < 3; i++) {
                if (mx >= bxArr[i] && mx <= bxArr[i] + btnW && my >= by && my <= by + btnH) {
                    sfxMenuClick();
                    difficulty = diffs[i];
                }
            }
            int backX = SW / 2 - 110, backY = (int) (SH * 0.84), backW = 220, backH = 50;
            if (mx >= backX && mx <= backX + backW && my >= backY && my <= backY + backH) {
                sfxMenuClick();
                gameState = GameState.MENU;
            }

        } else if (gameState == GameState.PLAYING) {
            if (gameOver || gameWon) {
                // End screen "Volver al menu"
                if (mx >= SW / 2 - 150 && mx <= SW / 2 + 150 && (my >= SH / 2 + 40 && my <= SH / 2 + 95)) {
                    stopBGMusic();
                    gameState = GameState.MENU;
                }
                return;
            }

            // ── Botón MENU en partida ──
            int mbx = menuBtnX(), mby = menuBtnY(), mbw = 100, mbh = 26;
            if (mx >= mbx && mx <= mbx + mbw && my >= mby && my <= mby + mbh) {
                sfxMenuClick();
                stopBGMusic();
                gameState = GameState.MENU;
                return;
            }

            // ── Botón PLAY/PAUSE ──
            int pbx = ppBtnX(), pby = ppBtnY(), pbw = 100, pbh = 26;
            if (mx >= pbx && mx <= pbx + pbw && my >= pby && my <= pby + pbh) {
                sfxMenuClick();
                if (waitingToStart) {
                    launchWave();
                } else {
                    paused = !paused;
                }
                return;
            }

            // ── Colocar torre ──
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
            if (money >= cost && isValidTowerPosition(mx, my) && !towerExistsAt(mx, my)) {
                towers.add(new Tower(mx, my, selectedTower));
                money -= cost;
                sfxTowerPlaced();
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
        // Espacio = pausa/play
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "pause");
        getActionMap().put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (gameState == GameState.PLAYING && !gameOver && !gameWon) {
                    if (waitingToStart) {
                        launchWave();
                    } else {
                        paused = !paused;
                    }
                }
            }
        });
    }

    // =====================================================================
    // MAIN — pantalla completa
    // =====================================================================
    public static void main(String[] args) {
        JFrame frame = new JFrame("TDX - DEFENSA MEDICA");
        frame.getContentPane().setBackground(new Color(10, 18, 25));

        TDX game = new TDX();
        game.setKeyBindings();
        frame.add(game);

        // Pantalla completa
        frame.setUndecorated(true);
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (gd.isFullScreenSupported()) {
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            gd.setFullScreenWindow(frame);
        } else {
            // Fallback: ventana maximizada si fullscreen no está disponible
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            SW = screen.width;
            SH = screen.height;
            frame.setPreferredSize(screen);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setVisible(true);
        }

        // ESC para salir de pantalla completa (por si acaso)
        game.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        game.getActionMap().put("esc", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                stopBGMusic();
                gd.setFullScreenWindow(null);
                System.exit(0);
            }
        });
    }

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

        int x, y, maxHealth = 50, health = 50, speed = 2, animTick = 0, reward = 20;

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
            int wobble = (animTick / 6) % 2;
            int[][] virus = {{0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 0}};
            g2.setColor(new Color(0, 0, 0, 80));
            for (int r = 0; r < virus.length; r++) {
                for (int c = 0; c < virus[r].length; c++) {
                    if (virus[r][c] == 1) {
                        g2.fillRect(x + c * PX + 2, y + r * PX + 2, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(200, 40, 40));
            for (int r = 0; r < virus.length; r++) {
                for (int c = 0; c < virus[r].length; c++) {
                    if (virus[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
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
            maxHealth = (int) ((30 + wave * 5) * enemyHealthMult());
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
            for (int r = 0; r < fast.length; r++) {
                for (int c = 0; c < fast[r].length; c++) {
                    if (fast[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
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
            maxHealth = (int) ((80 + wave * 15) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = 40;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int[][] body = {{0, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 0}};
            g2.setColor(new Color(30, 120, 60));
            for (int r = 0; r < body.length; r++) {
                for (int c = 0; c < body[r].length; c++) {
                    if (body[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
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
            maxHealth = (int) ((300 + wave * 30) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = 80;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
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
            g2.drawString("TANK", x + 8, y - 18);
            drawHealthBar(g2, x, y - 12, PX * 12, PX, (double) health / maxHealth, new Color(60, 120, 220), new Color(10, 10, 25));
        }
    }

    class BossEnemy extends Enemy {

        BossEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((200 + wave * 20) * enemyHealthMult());
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
            for (int r = 0; r < boss.length; r++) {
                for (int c = 0; c < boss[r].length; c++) {
                    if (boss[r][c] == 1) {
                        g2.fillRect(x + c * PX + 3, y + r * PX + 3, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(140, 30, 160));
            for (int r = 0; r < boss.length; r++) {
                for (int c = 0; c < boss[r].length; c++) {
                    if (boss[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(200, 60, 220));
            int[][] core = {{0, 1, 1, 0}, {1, 1, 1, 1}, {1, 1, 1, 1}, {0, 1, 1, 0}};
            for (int r = 0; r < core.length; r++) {
                for (int c = 0; c < core[r].length; c++) {
                    if (core[r][c] == 1) {
                        g2.fillRect(x + 16 + c * PX, y + 16 + r * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(255, 60, 60));
            g2.fillRect(x + PX * 3, y + PX * 2, PX * 2, PX * 2);
            g2.fillRect(x + PX * 7, y + PX * 2, PX * 2, PX * 2);
            g2.setColor(new Color(220, 60, 220));
            g2.setFont(new Font("Monospaced", Font.BOLD, 9));
            g2.drawString("JEFE", x + 12, y - 20);
            drawHealthBar(g2, x, y - 14, 48, PX, (double) health / maxHealth, new Color(180, 50, 200), new Color(20, 10, 25));
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
            int[][] f = {{0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            g2.setColor(new Color(0, 0, 0, 120));
            for (int r = 0; r < f.length; r++) {
                for (int c = 0; c < f[r].length; c++) {
                    if (f[r][c] == 1) {
                        g2.fillRect(x + c * PX + 4, y + r * PX + 4, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(160, 20, 20));
            for (int r = 0; r < f.length; r++) {
                for (int c = 0; c < f[r].length; c++) {
                    if (f[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
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
            g2.drawString("!! JEFE FINAL !!", x - 4, y - 22);
            drawHealthBar(g2, x - 4, y - 16, 80, PX + 2, (double) health / maxHealth, new Color(255, 80, 0), new Color(30, 5, 5));
        }
    }

    // =====================================================================
    // TOWER
    // =====================================================================
    class Tower {

        int x, y, range = (int)(SW * 0.12), cooldown = 0, animTick = 0;
        String type;
        Enemy target = null;
        double aimAngle = 0;

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
            double bestDist = Double.MAX_VALUE;
            for (Enemy e : enemies) {
                double dist = Math.hypot(x + 14 - (e.x + 16), y + 10 - (e.y + 12));
                if (dist < range && dist < bestDist) {
                    bestDist = dist;
                    target = e;
                }
            }
            if (target != null) {
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
            g2.setColor(new Color(20, 50, 35));
            g2.fillRect(x - 2, y + 18, 32, 8);
            g2.setColor(new Color(0, 100, 60));
            g2.fillRect(x, y + 20, 28, 4);
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
            int bx = x + 14, by = y + 10;
            AffineTransform old = g2.getTransform();
            g2.rotate(aimAngle, bx, by);
            g2.setColor(accentColor);
            g2.fillRect(bx, by - 2, 18, 4);
            g2.setColor(mainColor);
            g2.fillRect(bx + 14, by - 1, 6, 2);
            g2.setTransform(old);
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
                int jitter = s % 2 == 0 ? -PX : PX;
                g.setColor(s % 2 == 0 ? mc : ac);
                g.fillRect(bx + jitter, by, PX * 2, PX);
            }
            g.setColor(ac);
            g.fillRect(x2 - PX, y2 - PX, PX * 3, PX * 3);
        }
    }
}
// class tower