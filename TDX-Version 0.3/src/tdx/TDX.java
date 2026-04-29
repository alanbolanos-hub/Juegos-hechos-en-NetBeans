package tdx;

import javax.swing.*;
import javax.sound.midi.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TDX extends JPanel implements ActionListener, MouseListener {

    // =====================================================================
    // Escala de la pantalla
    // =====================================================================
    static int SW = 1280;
    static int SH = 720;
    static boolean isFullScreen = true;
    static GraphicsDevice gd;

    int PATH_TOP, PATH_BOT, UI_TOP_H, UI_BOT_Y, UI_BOT_H;
    int BASE_X;
    int TOWER_SIZE = 64;
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
    // Estado Del Juego
    // =====================================================================
    enum GameState {
        MENU, SETTINGS, PLAYING, SCORE_ENTRY, SCOREBOARD, MAP_SELECT
    }
    GameState gameState = GameState.MENU;

    enum Difficulty {
        EASY, NORMAL, HARD, NIGHTMARE
    }
    Difficulty difficulty = Difficulty.NORMAL;

    boolean paused = false;
    boolean waitingToStart = true;
    Timer timer;

    List<Enemy> enemies = new ArrayList<>();
    List<Tower> towers = new ArrayList<>();
    List<Projectile> projectiles = new ArrayList<>();

    int money = 200;
    int baseHealth = 100;
    int wave = 1;
    static final int MAX_WAVE = 25;
    int score = 0;
    int selectedMap = 0; // 0 = straight corridor, 1 = serpentine

    boolean gameOver = false;
    boolean gameWon = false;

    String selectedTower = "NORMAL";
    int mouseX = 0, mouseY = 0;

    // Score system
    static final int MAX_SCORES = 10;
    String[] scoreNames = new String[MAX_SCORES];
    int[] scoreValues = new int[MAX_SCORES];
    int scoreDifficulty[] = new int[MAX_SCORES];
    int scoreCount = 0;
    String currentName = "";
    boolean nameEntryDone = false;
    long nameCursorBlink = 0;

    // =====================================================================
    // Mapa de Puntos
    // =====================================================================
    int[][] mapWaypoints; // {x, y} pairs for enemy path

    void buildMapWaypoints() {
        if (selectedMap == 0) {
            // Straight corridor - enemies just go left to right
            mapWaypoints = null;
        } else {
            // Serpentine map - enemies weave through waypoints
            // These are set in actionPerformed/move relative to PATH_TOP/BOT
            mapWaypoints = new int[][]{
                {-50, PATH_TOP + (PATH_BOT - PATH_TOP) / 4},
                {(int) (SW * 0.25), PATH_BOT - 20},
                {(int) (SW * 0.45), PATH_TOP + 20},
                {(int) (SW * 0.65), PATH_BOT - 20},
                {(int) (SW * 0.80), PATH_TOP + (PATH_BOT - PATH_TOP) / 2},
                {BASE_X + 60, PATH_TOP + (PATH_BOT - PATH_TOP) / 2}
            };
        }
    }

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
                for (int i = 0; i < Math.min(wave, 5); i++) {
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
    // COLORES
    // =====================================================================
    static final Color COL_BG = new Color(8, 14, 20);
    static final Color COL_PATH = new Color(18, 36, 30);
    static final Color COL_PATH_EDGE = new Color(0, 200, 110);
    static final Color COL_GRID = new Color(0, 50, 38);
    static final Color COL_BASE = new Color(0, 220, 120);
    static final Color COL_UI_BG = new Color(4, 10, 16);
    static final Color COL_UI_BORDER = new Color(0, 180, 100);
    static final Color COL_UI_TEXT = new Color(180, 255, 200);
    static final Color COL_MONEY = new Color(255, 215, 60);
    static final Color COL_HEALTH = new Color(220, 50, 60);
    static final Color COL_WAVE = new Color(80, 180, 255);
    static final Color COL_SCORE = new Color(255, 200, 50);

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
        timer = new Timer(28, this);
        timer.start();
    }

    // =====================================================================
    // Dificultades Multiplicadores — 4 Niveles
    // =====================================================================
    double enemyHealthMult() {
        switch (difficulty) {
            case EASY:
                return 0.5;
            case HARD:
                return 1.5;
            case NIGHTMARE:
                return 2.2;
            default:
                return 1.0;
        }
    }

    double enemySpeedMult() {
        switch (difficulty) {
            case EASY:
                return 0.7;
            case HARD:
                return 1.25;
            case NIGHTMARE:
                return 1.6;
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
            case NIGHTMARE:
                return 100;
            default:
                return 200;
        }
    }

    int waveBonus() {
        switch (difficulty) {
            case EASY:
                return 100;
            case HARD:
                return 35;
            case NIGHTMARE:
                return 20;
            default:
                return 60;
        }
    }

    double scoreMult() {
        switch (difficulty) {
            case EASY:
                return 0.5;
            case HARD:
                return 2.0;
            case NIGHTMARE:
                return 4.0;
            default:
                return 1.0;
        }
    }

    // =====================================================================
    // Logica del Juego
    // =====================================================================
    void startGame() {
        recalcLayout();
        buildMapWaypoints();
        money = startMoney();
        baseHealth = 100;
        wave = 1;
        score = 0;
        gameOver = false;
        gameWon = false;
        paused = false;
        waitingToStart = true;
        enemies.clear();
        towers.clear();
        projectiles.clear();
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
        projectiles.clear();
        int totalEnemies = 5 + wave * 4;
        int rows = 6;
        for (int i = 0; i < totalEnemies; i++) {
            int col = i / rows, row = i % rows;
            int startX = -50 - col * 70;
            int startY = PATH_TOP + 14 + row * ((PATH_BOT - PATH_TOP - 28) / rows);
            Enemy e;
            // Nightmare-only enemy
            if (difficulty == Difficulty.NIGHTMARE && wave >= 8 && i % 6 == 0) {
                e = new MutantEnemy(startX, startY);
            } else if (wave >= 20 && i % 5 == 0) {
                e = new StealthEnemy(startX, startY);
            } else if (wave >= 15 && i % 6 == 0) {
                e = new HealerEnemy(startX, startY);
            } else if (wave >= 10 && i % 7 == 0) {
                e = new TankEnemy(startX, startY);
            } else if (wave >= 7 && i % 5 == 0) {
                e = new SpeedEnemy(startX, startY);
            } else if (wave >= 4 && i % 4 == 0) {
                e = new ShieldEnemy(startX, startY);
            } else {
                e = new Enemy(startX, startY);
                e.maxHealth = (int) ((50 + wave * 12) * enemyHealthMult());
                e.health = e.maxHealth;
            }
            e.speed = Math.max(1, (int) Math.round(e.speed * enemySpeedMult()));
            if (selectedMap == 1) {
                e.waypointIndex = 0;
            }
            enemies.add(e);
        }
        // Rondas del Jefe
        if (wave % 5 == 0) {
            Enemy boss;
            if (wave == 25) {
                boss = new FinalBoss(-250, PATH_TOP + 30);
            } else if (wave == 20) {
                boss = new ColossusEnemy(-250, PATH_TOP + 30);
            } else {
                boss = new BossEnemy(-200, PATH_TOP + 50);
            }
            boss.maxHealth = (int) (boss.maxHealth * enemyHealthMult());
            boss.health = boss.maxHealth;
            if (selectedMap == 1) {
                boss.waypointIndex = 0;
            }
            enemies.add(boss);
        }
    }

    void launchWave() {
        sfxNewWave(wave);
        waitingToStart = false;
    }

    // =====================================================================
    // Espacio de la Torre
    // =====================================================================
    boolean isValidTowerPosition(int x, int y) {
        if (x < 10 || x > SW - 30 || y < UI_TOP_H + 4 || y > UI_BOT_Y - 10) {
            return false;
        }
        if (selectedMap == 0) {
            if (y > PATH_TOP - 10 && y < PATH_BOT + 10) {
                return false;
            }
        } else {
            // Serpentine: block path segments
            if (isOnSerpenPath(x, y)) {
                return false;
            }
        }
        if (x > BASE_X - 10) {
            return false;
        }
        return true;
    }

    boolean isOnSerpenPath(int x, int y) {
        if (mapWaypoints == null) {
            return false;
        }
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            int x1 = mapWaypoints[i][0], y1 = mapWaypoints[i][1];
            int x2 = mapWaypoints[i + 1][0], y2 = mapWaypoints[i + 1][1];
            // Check if point is near the segment
            double len = Math.hypot(x2 - x1, y2 - y1);
            if (len == 0) {
                continue;
            }
            double t = Math.max(0, Math.min(1, ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / (len * len)));
            double px = x1 + t * (x2 - x1), py = y1 + t * (y2 - y1);
            if (Math.hypot(x - px, y - py) < 35) {
                return true;
            }
        }
        return false;
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
    // Sistema de Puntaje
    // =====================================================================
    void addScore(String name, int value, int diff) {
        if (scoreCount < MAX_SCORES) {
            scoreNames[scoreCount] = name;
            scoreValues[scoreCount] = value;
            scoreDifficulty[scoreCount] = diff;
            scoreCount++;
        } else {
            // Find lowest
            int minIdx = 0;
            for (int i = 1; i < MAX_SCORES; i++) {
                if (scoreValues[i] < scoreValues[minIdx]) {
                    minIdx = i;
                }
            }
            if (value > scoreValues[minIdx]) {
                scoreNames[minIdx] = name;
                scoreValues[minIdx] = value;
                scoreDifficulty[minIdx] = diff;
            }
        }
        // Bubble sort descending
        for (int i = 0; i < scoreCount - 1; i++) {
            for (int j = i + 1; j < scoreCount; j++) {
                if (scoreValues[j] > scoreValues[i]) {
                    int tv = scoreValues[i];
                    scoreValues[i] = scoreValues[j];
                    scoreValues[j] = tv;
                    String tn = scoreNames[i];
                    scoreNames[i] = scoreNames[j];
                    scoreNames[j] = tn;
                    int td = scoreDifficulty[i];
                    scoreDifficulty[i] = scoreDifficulty[j];
                    scoreDifficulty[j] = td;
                }
            }
        }
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

    void drawStar(Graphics2D g, int x, int y, Color c) {
        int[][] s = {{0, 0, 1, 0, 0}, {1, 1, 1, 1, 1}, {0, 1, 1, 1, 0}, {0, 1, 0, 1, 0}, {1, 0, 0, 0, 1}};
        g.setColor(c);
        for (int r = 0; r < s.length; r++) {
            for (int col = 0; col < s[r].length; col++) {
                if (s[r][col] == 1) {
                    g.fillRect(x + col * 4, y + r * 4, 4, 4);
                }
            }
        }
    }

    // =====================================================================
    // Diseño o Pintado
    // =====================================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        SW = getWidth();
        SH = getHeight();
        recalcLayout();
        if (selectedMap == 1) {
            buildMapWaypoints();
        }
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
            case MAP_SELECT:
                drawMapSelect(g2);
                break;
            case PLAYING:
                drawGame(g2);
                break;
            case SCORE_ENTRY:
                drawScoreEntry(g2);
                break;
            case SCOREBOARD:
                drawScoreboard(g2);
                break;
        }
    }

    // =====================================================================
    // MENU
    // =====================================================================
    void drawMenu(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, SW, SH);
        // Animated scan lines
        long t = System.currentTimeMillis();
        g2.setColor(new Color(0, 30, 20, 15));
        for (int y = 0; y < SH; y += 4) {
            g2.fillRect(0, y, SW, 2);
        }
        // Grid dots
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }
        // Center path deco
        int cy = SH / 2;
        g2.setColor(new Color(20, 40, 35));
        g2.fillRect(0, cy - 60, SW, 120);
        g2.setColor(COL_PATH_EDGE);
        for (int px = 0; px < SW; px += PX * 4) {
            g2.fillRect(px, cy - 60, PX * 2, PX);
            g2.fillRect(px, cy + 56, PX * 2, PX);
        }
        // Large glowing TITLE
        int titleX = SW / 2;
        String title = "TDX";
        g2.setFont(pixelFont(96));
        FontMetrics fm96 = g2.getFontMetrics();
        int tw = fm96.stringWidth(title);
        // Glow layers
        for (int glow = 6; glow >= 1; glow--) {
            int alpha = 12 + glow * 8;
            g2.setColor(new Color(0, 220, 100, Math.min(255, alpha)));
            g2.drawString(title, titleX - tw / 2 - glow, (int) (SH * 0.22) + glow);
            g2.drawString(title, titleX - tw / 2 + glow, (int) (SH * 0.22) - glow);
        }
        g2.setColor(new Color(0, 60, 35));
        g2.drawString(title, titleX - tw / 2 + 5, (int) (SH * 0.22) + 5);
        g2.setColor(COL_PATH_EDGE);
        g2.drawString(title, titleX - tw / 2, (int) (SH * 0.22));
        // Subtitle
        String sub = "DEFENSA MEDICA";
        g2.setFont(pixelFont(18));
        FontMetrics fmSub = g2.getFontMetrics();
        int subPulse = (int) (Math.abs(Math.sin(t / 800.0)) * 30) + 180;
        g2.setColor(new Color(180, 255, 200, subPulse));
        g2.drawString(sub, titleX - fmSub.stringWidth(sub) / 2, (int) (SH * 0.22) + 40);
        // Corner viruses
        drawMenuVirus(g2, (int) (SW * 0.06), (int) (SH * 0.22), 0);
        drawMenuVirus(g2, (int) (SW * 0.86), (int) (SH * 0.20), 1);
        drawMenuVirus(g2, (int) (SW * 0.10), (int) (SH * 0.72), 2);
        drawMenuVirus(g2, (int) (SW * 0.82), (int) (SH * 0.70), 3);

        String[] lbls = {"JUGAR", "AJUSTES", "PUNTAJES", "SALIR"};
        int bw = 320, bh = 58;
        int bx0 = SW / 2 - bw / 2;
        int[] byArr = {(int) (SH * 0.35), (int) (SH * 0.47), (int) (SH * 0.59), (int) (SH * 0.71)};
        Color[] btnCols = {new Color(0, 200, 100), new Color(80, 180, 255), new Color(255, 200, 50), new Color(220, 60, 60)};
        for (int i = 0; i < lbls.length; i++) {
            boolean hover = mouseX >= bx0 && mouseX <= bx0 + bw && mouseY >= byArr[i] && mouseY <= byArr[i] + bh;
            // Shadow
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRect(bx0 + 5, byArr[i] + 5, bw, bh);
            // Fill
            if (hover) {
                g2.setColor(new Color(btnCols[i].getRed(), btnCols[i].getGreen(), btnCols[i].getBlue(), 35));
            } else {
                g2.setColor(new Color(8, 20, 14));
            }
            g2.fillRect(bx0, byArr[i], bw, bh);
            // Border
            g2.setColor(hover ? btnCols[i] : new Color(btnCols[i].getRed() / 2, btnCols[i].getGreen() / 2, btnCols[i].getBlue() / 2));
            g2.setStroke(new BasicStroke(PX));
            g2.drawRect(bx0, byArr[i], bw, bh);
            // Corner pixels
            g2.fillRect(bx0 - PX, byArr[i] - PX, PX * 2, PX * 2);
            g2.fillRect(bx0 + bw - PX, byArr[i] - PX, PX * 2, PX * 2);
            g2.fillRect(bx0 - PX, byArr[i] + bh - PX, PX * 2, PX * 2);
            g2.fillRect(bx0 + bw - PX, byArr[i] + bh - PX, PX * 2, PX * 2);
            // Label
            g2.setColor(hover ? btnCols[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(24));
            FontMetrics fmb = g2.getFontMetrics();
            g2.drawString(lbls[i], bx0 + (bw - fmb.stringWidth(lbls[i])) / 2, byArr[i] + 37);
        }
        g2.setColor(new Color(100, 150, 120));
        g2.setFont(pixelFont(12));
        String diffStr = "DIFICULTAD: " + difficulty.name();
        g2.drawString(diffStr, SW / 2 - g2.getFontMetrics().stringWidth(diffStr) / 2, (int) (SH * 0.88));
        // Window mode toggle hint
        g2.setColor(new Color(60, 100, 80));
        g2.setFont(pixelFont(9));
        String hint = "[F11] Pantalla completa / ventana   |   [ESC] Salir";
        g2.drawString(hint, SW / 2 - g2.getFontMetrics().stringWidth(hint) / 2, SH - 14);
    }

    void drawMenuVirus(Graphics2D g2, int x, int y, int variant) {
        long t = System.currentTimeMillis();
        int wobble = (int) (t / 200 + variant * 17) % 2;
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
        Color vc = cols[variant % cols.length];
        g2.setColor(new Color(vc.getRed(), vc.getGreen(), vc.getBlue(), 80));
        int cx = x + 16, cy2 = y + 12;
        int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        for (int[] sp : spikes) {
            g2.fillRect(cx + sp[0] * (10 + wobble) - PX / 2, cy2 + sp[1] * (10 + wobble) - PX / 2, PX, PX);
        }
    }

    // =====================================================================
    // Seleccion de Mapa
    // =====================================================================
    void drawMapSelect(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, SW, SH);
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }
        g2.setColor(COL_PATH_EDGE);
        g2.setFont(pixelFont(32));
        FontMetrics fm = g2.getFontMetrics();
        String title = "SELECCIONAR MAPA";
        g2.drawString(title, SW / 2 - fm.stringWidth(title) / 2, (int) (SH * 0.12));
        g2.setColor(new Color(0, 80, 40));
        g2.fillRect(0, (int) (SH * 0.15), SW, PX);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(0, (int) (SH * 0.15), SW, PX);

        String[] mapNames = {"PASILLO RECTO", "SERPENTINA"};
        String[] mapDescs = {"Camino recto clasico. Torres a ambos lados.", "Camino en zigzag. Estrategia avanzada."};
        int mw = (int) (SW * 0.35), mh = (int) (SH * 0.45);
        int mx1 = (int) (SW * 0.08), mx2 = (int) (SW * 0.57);
        int my = (int) (SH * 0.22);
        for (int i = 0; i < 2; i++) {
            int bx = (i == 0) ? mx1 : mx2;
            boolean hover = mouseX >= bx && mouseX <= bx + mw && mouseY >= my && mouseY <= my + mh;
            boolean sel = selectedMap == i;
            g2.setColor(sel ? new Color(0, 180, 100, 25) : new Color(8, 18, 14));
            g2.fillRect(bx, my, mw, mh);
            g2.setColor(sel ? COL_PATH_EDGE : (hover ? new Color(0, 130, 80) : new Color(0, 70, 45)));
            g2.setStroke(new BasicStroke(sel ? PX : 2));
            g2.drawRect(bx, my, mw, mh);
            // Draw mini map preview
            drawMiniMap(g2, bx + 20, my + 20, mw - 40, (int) (mh * 0.55), i);
            g2.setColor(sel ? COL_PATH_EDGE : COL_UI_TEXT);
            g2.setFont(pixelFont(16));
            FontMetrics fmm = g2.getFontMetrics();
            g2.drawString(mapNames[i], bx + (mw - fmm.stringWidth(mapNames[i])) / 2, my + (int) (mh * 0.67));
            g2.setColor(new Color(120, 180, 140));
            g2.setFont(pixelFont(10));
            fmm = g2.getFontMetrics();
            g2.drawString(mapDescs[i], bx + (mw - fmm.stringWidth(mapDescs[i])) / 2, my + (int) (mh * 0.78));
            if (sel) {
                g2.setColor(COL_PATH_EDGE);
                g2.setFont(pixelFont(10));
                fmm = g2.getFontMetrics();
                g2.drawString("< SELECCIONADO >", bx + (mw - fmm.stringWidth("< SELECCIONADO >")) / 2, my + (int) (mh * 0.90));
            }
        }
        int startBtnW = 280, startBtnH = 55;
        int startBtnX = SW / 2 - startBtnW / 2, startBtnY = (int) (SH * 0.80);
        boolean startHover = mouseX >= startBtnX && mouseX <= startBtnX + startBtnW && mouseY >= startBtnY && mouseY <= startBtnY + startBtnH;
        g2.setColor(startHover ? new Color(0, 200, 100, 40) : new Color(8, 18, 14));
        g2.fillRect(startBtnX, startBtnY, startBtnW, startBtnH);
        g2.setColor(startHover ? COL_PATH_EDGE : new Color(0, 140, 80));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(startBtnX, startBtnY, startBtnW, startBtnH);
        g2.setColor(startHover ? COL_BG : COL_UI_TEXT);
        g2.setFont(pixelFont(20));
        FontMetrics fmBtn = g2.getFontMetrics();
        g2.drawString("INICIAR JUEGO", startBtnX + (startBtnW - fmBtn.stringWidth("INICIAR JUEGO")) / 2, startBtnY + 35);

        int backX = 20, backY = (int) (SH * 0.88), backW = 180, backH = 40;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        g2.setColor(backHover ? new Color(0, 180, 100, 30) : new Color(8, 18, 14));
        g2.fillRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_PATH_EDGE : new Color(0, 100, 60));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(backX, backY, backW, backH);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(14));
        g2.drawString("< VOLVER", backX + 30, backY + 27);
    }

    void drawMiniMap(Graphics2D g2, int x, int y, int w, int h, int mapIdx) {
        g2.setColor(new Color(15, 30, 25));
        g2.fillRect(x, y, w, h);
        g2.setColor(new Color(0, 60, 40));
        g2.drawRect(x, y, w, h);
        if (mapIdx == 0) {
            // Straight
            int py = y + h / 2 - 15;
            g2.setColor(COL_PATH);
            g2.fillRect(x + 5, py, w - 10, 30);
            g2.setColor(COL_PATH_EDGE);
            g2.fillRect(x + 5, py, w - 10, 3);
            g2.fillRect(x + 5, py + 27, w - 10, 3);
            // Little tower icons
            g2.setColor(new Color(0, 200, 100, 120));
            for (int tx = x + 20; tx < x + w - 20; tx += 30) {
                g2.fillRect(tx, py - 12, 10, 10);
                g2.fillRect(tx, py + 32, 10, 10);
            }
        } else {
            // Serpentine
            g2.setColor(COL_PATH);
            // Draw zigzag path
            int[] xs = {x + 5, (int) (x + w * 0.25), (int) (x + w * 0.45), (int) (x + w * 0.65), (int) (x + w * 0.82), x + w - 5};
            int[] ys = {y + h / 4, y + h * 3 / 4, y + h / 4, y + h * 3 / 4, y + h / 2, y + h / 2};
            for (int i = 0; i < xs.length - 1; i++) {
                g2.setStroke(new BasicStroke(12));
                g2.setColor(COL_PATH);
                g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
            }
            g2.setStroke(new BasicStroke(2));
            g2.setColor(COL_PATH_EDGE);
            for (int i = 0; i < xs.length - 1; i++) {
                g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
            }
        }
        // Base indicator
        g2.setColor(COL_BASE);
        g2.fillRect(x + w - 15, y + h / 2 - 12, 12, 24);
        drawMedCross(g2, x + w - 9, y + h / 2, 10, COL_HEALTH);
    }

    // =====================================================================
    // Ajustes
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
        g2.setFont(pixelFont(32));
        FontMetrics fmT = g2.getFontMetrics();
        g2.drawString("AJUSTES", SW / 2 - fmT.stringWidth("AJUSTES") / 2, (int) (SH * 0.11));
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(0, (int) (SH * 0.14), SW, PX);

        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(16));
        String diffLabel = "DIFICULTAD";
        FontMetrics fmL = g2.getFontMetrics();
        g2.drawString(diffLabel, SW / 2 - fmL.stringWidth(diffLabel) / 2, (int) (SH * 0.22));

        String[] descs = {
            "Vida enemi -50%, vel lenta. Dinero: $350, +$100/oleada.",
            "Balanceado. Dinero: $200, +$60/oleada.",
            "Vida enemi +50%, mas rapidos. $150, +$35/oleada.",
            "MODO PESADILLA: Vida x2.2, vel x1.6. $100, +$20/oleada."
        };
        String[] diffNames = {"FACIL", "NORMAL", "DIFICIL", "PESADILLA"};
        Color[] diffColors = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60), new Color(180, 40, 220)};
        Difficulty[] diffs = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE};
        int btnW = 165, btnH = 65;
        int totalW = 4 * btnW + 3 * 20;
        int startBX = SW / 2 - totalW / 2;
        int by = (int) (SH * 0.26);
        for (int i = 0; i < 4; i++) {
            int bx = startBX + i * (btnW + 20);
            boolean selected = difficulty == diffs[i];
            boolean hover = mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH;
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRect(bx + 4, by + 4, btnW, btnH);
            if (selected) {
                g2.setColor(new Color(diffColors[i].getRed(), diffColors[i].getGreen(), diffColors[i].getBlue(), 40));
            } else {
                g2.setColor(hover ? new Color(10, 30, 20) : new Color(8, 18, 14));
            }
            g2.fillRect(bx, by, btnW, btnH);
            Color borderC = selected ? diffColors[i] : (hover ? new Color(diffColors[i].getRed(), diffColors[i].getGreen(), diffColors[i].getBlue(), 150) : new Color(0, 80, 50));
            g2.setColor(borderC);
            g2.setStroke(new BasicStroke(selected ? PX : 2));
            g2.drawRect(bx, by, btnW, btnH);
            if (selected) {
                g2.setColor(diffColors[i]);
                g2.fillRect(bx - PX, by - PX, PX * 2, PX * 2);
                g2.fillRect(bx + btnW - PX, by - PX, PX * 2, PX * 2);
                g2.fillRect(bx - PX, by + btnH - PX, PX * 2, PX * 2);
                g2.fillRect(bx + btnW - PX, by + btnH - PX, PX * 2, PX * 2);
            }
            g2.setColor(selected ? diffColors[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(12));
            FontMetrics fmb = g2.getFontMetrics();
            g2.drawString(diffNames[i], bx + (btnW - fmb.stringWidth(diffNames[i])) / 2, by + 26);
            if (selected) {
                g2.setColor(new Color(diffColors[i].getRed(), diffColors[i].getGreen(), diffColors[i].getBlue(), 200));
                g2.setFont(pixelFont(8));
                fmb = g2.getFontMetrics();
                g2.drawString("< SEL >", bx + (btnW - fmb.stringWidth("< SEL >")) / 2, by + 46);
            }
        }
        // Description box
        int descBoxY = (int) (SH * 0.48), descBoxH = 120;
        g2.setColor(new Color(8, 18, 14));
        g2.fillRect(startBX, descBoxY, totalW, descBoxH);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(startBX, descBoxY, totalW, descBoxH);
        int descIdx = difficulty.ordinal();
        g2.setColor(diffColors[descIdx]);
        g2.setFont(pixelFont(10));
        g2.drawString(descs[descIdx], startBX + 16, descBoxY + 22);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(10));
        g2.drawString("VIDA ENE:", startBX + 16, descBoxY + 48);
        g2.drawString("VEL ENE:", startBX + 16, descBoxY + 65);
        g2.drawString("DINERO:", startBX + 16, descBoxY + 82);
        g2.drawString("MULT PUNT:", startBX + 16, descBoxY + 99);
        double[] hM = {0.5, 1.0, 1.5, 2.2}, sM = {0.7, 1.0, 1.25, 1.6};
        int[] mI = {350, 200, 150, 100};
        double[] pM = {0.5, 1.0, 2.0, 4.0};
        drawSettingBar(g2, startBX + 120, descBoxY + 37, 200, 14, hM[descIdx] / 2.2, diffColors[descIdx]);
        drawSettingBar(g2, startBX + 120, descBoxY + 54, 200, 14, sM[descIdx] / 1.6, diffColors[descIdx]);
        drawSettingBar(g2, startBX + 120, descBoxY + 71, 200, 14, mI[descIdx] / 350.0, diffColors[descIdx]);
        drawSettingBar(g2, startBX + 120, descBoxY + 88, 200, 14, pM[descIdx] / 4.0, diffColors[descIdx]);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(9));
        g2.drawString(String.format("x%.1f", hM[descIdx]), startBX + 330, descBoxY + 48);
        g2.drawString(String.format("x%.2f", sM[descIdx]), startBX + 330, descBoxY + 65);
        g2.drawString("$" + mI[descIdx], startBX + 330, descBoxY + 82);
        g2.drawString("x" + pM[descIdx], startBX + 330, descBoxY + 99);

        // Window size toggle
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(14));
        g2.drawString("PANTALLA:", SW / 2 - 200, (int) (SH * 0.73));
        int tbx = SW / 2 - 60, tby = (int) (SH * 0.72) - 20, tbw = 220, tbh = 40;
        boolean thover = mouseX >= tbx && mouseX <= tbx + tbw && mouseY >= tby && mouseY <= tby + tbh;
        g2.setColor(thover ? new Color(0, 200, 100, 30) : new Color(8, 18, 14));
        g2.fillRect(tbx, tby, tbw, tbh);
        g2.setColor(thover ? COL_PATH_EDGE : new Color(0, 120, 70));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(tbx, tby, tbw, tbh);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(12));
        String sizeLabel = isFullScreen ? "PANTALLA COMPLETA" : "MODO VENTANA";
        FontMetrics fmtl = g2.getFontMetrics();
        g2.drawString(sizeLabel, tbx + (tbw - fmtl.stringWidth(sizeLabel)) / 2, tby + 26);

        int backX = SW / 2 - 110, backY = (int) (SH * 0.86), backW = 220, backH = 48;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        g2.setColor(backHover ? new Color(0, 180, 100, 30) : new Color(8, 18, 14));
        g2.fillRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_PATH_EDGE : new Color(0, 100, 60));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_BG : COL_UI_TEXT);
        g2.setFont(pixelFont(16));
        g2.drawString("< VOLVER", backX + 62, backY + 31);
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
    // Entrada del Puntaje
    // =====================================================================
    void drawScoreEntry(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, SW, SH);
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }

        String header = gameWon ? "VICTORIA!" : "GAME OVER";
        g2.setColor(gameWon ? new Color(0, 220, 120) : new Color(220, 50, 60));
        g2.setFont(pixelFont(54));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(header, SW / 2 - fm.stringWidth(header) / 2, (int) (SH * 0.17));

        g2.setColor(COL_SCORE);
        g2.setFont(pixelFont(22));
        String scoreStr = "PUNTAJE FINAL: " + score;
        fm = g2.getFontMetrics();
        g2.drawString(scoreStr, SW / 2 - fm.stringWidth(scoreStr) / 2, (int) (SH * 0.30));

        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(16));
        String prompt = "INGRESA TU NOMBRE (5 LETRAS):";
        fm = g2.getFontMetrics();
        g2.drawString(prompt, SW / 2 - fm.stringWidth(prompt) / 2, (int) (SH * 0.42));

        // Name input box
        int nameBoxW = 320, nameBoxH = 70;
        int nameBoxX = SW / 2 - nameBoxW / 2, nameBoxY = (int) (SH * 0.46);
        g2.setColor(new Color(8, 18, 14));
        g2.fillRect(nameBoxX, nameBoxY, nameBoxW, nameBoxH);
        g2.setColor(COL_PATH_EDGE);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(nameBoxX, nameBoxY, nameBoxW, nameBoxH);

        // Display name with cursor
        long t = System.currentTimeMillis();
        boolean showCursor = (t / 500) % 2 == 0;
        String displayName = currentName;
        if (showCursor && currentName.length() < 5) {
            displayName += "_";
        }
        g2.setColor(COL_PATH_EDGE);
        g2.setFont(pixelFont(36));
        // Pad with underscores
        String padded = currentName;
        while (padded.length() < 5) {
            padded += (showCursor && padded.length() == currentName.length()) ? "_" : "-";
        }
        fm = g2.getFontMetrics();
        int charW = fm.stringWidth("X");
        int totalCharW = charW * 5 + 16 * 4;
        int startCharX = SW / 2 - totalCharW / 2;
        for (int i = 0; i < 5; i++) {
            char ch = i < padded.length() ? padded.charAt(i) : '-';
            boolean isActive = i == currentName.length();
            g2.setColor(isActive && showCursor ? COL_PATH_EDGE : (i < currentName.length() ? new Color(0, 255, 150) : new Color(0, 80, 50)));
            g2.drawString(String.valueOf(ch), startCharX + i * (charW + 16), nameBoxY + 48);
            g2.setColor(new Color(0, 100, 60));
            g2.fillRect(startCharX + i * (charW + 16), nameBoxY + 54, charW, 3);
        }

        // Confirm button
        if (currentName.length() == 5) {
            int confBtnW = 260, confBtnH = 52;
            int confBtnX = SW / 2 - confBtnW / 2, confBtnY = (int) (SH * 0.63);
            boolean chover = mouseX >= confBtnX && mouseX <= confBtnX + confBtnW && mouseY >= confBtnY && mouseY <= confBtnY + confBtnH;
            g2.setColor(chover ? new Color(0, 200, 100, 40) : new Color(8, 18, 14));
            g2.fillRect(confBtnX, confBtnY, confBtnW, confBtnH);
            g2.setColor(chover ? COL_PATH_EDGE : new Color(0, 140, 80));
            g2.setStroke(new BasicStroke(PX));
            g2.drawRect(confBtnX, confBtnY, confBtnW, confBtnH);
            g2.setColor(chover ? COL_BG : COL_UI_TEXT);
            g2.setFont(pixelFont(18));
            fm = g2.getFontMetrics();
            g2.drawString("CONFIRMAR [ENTER]", confBtnX + (confBtnW - fm.stringWidth("CONFIRMAR [ENTER]")) / 2, confBtnY + 34);
        }

        g2.setColor(new Color(100, 150, 120));
        g2.setFont(pixelFont(10));
        g2.drawString("Solo letras y numeros. Backspace para borrar.", SW / 2 - 160, (int) (SH * 0.80));
    }

    // =====================================================================
    // Puntaje de la Barra
    // =====================================================================
    void drawScoreboard(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, SW, SH);
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }

        // Title with stars
        drawStar(g2, SW / 2 - 160, (int) (SH * 0.04), COL_SCORE);
        drawStar(g2, SW / 2 + 140, (int) (SH * 0.04), COL_SCORE);
        g2.setColor(COL_SCORE);
        g2.setFont(pixelFont(36));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("TABLA DE PUNTAJES", SW / 2 - fm.stringWidth("TABLA DE PUNTAJES") / 2, (int) (SH * 0.12));
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(80, (int) (SH * 0.14), SW - 160, PX);

        // Column headers
        g2.setColor(new Color(0, 180, 100));
        g2.setFont(pixelFont(13));
        int ry = (int) (SH * 0.20);
        g2.drawString("#", 120, ry);
        g2.drawString("NOMBRE", 180, ry);
        g2.drawString("PUNTAJE", SW / 2 - 40, ry);
        g2.drawString("DIFIC", SW / 2 + 140, ry);
        g2.fillRect(80, ry + 6, SW - 160, 2);

        String[] dNames = {"FACIL", "NORMAL", "DIFICIL", "PESADILLA"};
        Color[] dCols = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60), new Color(180, 40, 220)};
        for (int i = 0; i < scoreCount; i++) {
            int rowY = ry + 30 + i * 34;
            // Alternating row bg
            g2.setColor(i % 2 == 0 ? new Color(10, 22, 16) : new Color(6, 14, 10));
            g2.fillRect(80, rowY - 20, SW - 160, 30);
            // Rank
            Color rankColor = i == 0 ? new Color(255, 200, 50) : i == 1 ? new Color(200, 200, 200) : i == 2 ? new Color(200, 120, 60) : new Color(100, 150, 120);
            g2.setColor(rankColor);
            g2.setFont(pixelFont(14));
            g2.drawString((i + 1) + ".", 120, rowY);
            // Name
            g2.setColor(COL_UI_TEXT);
            g2.drawString(scoreNames[i], 180, rowY);
            // Score
            g2.setColor(COL_SCORE);
            g2.drawString(String.valueOf(scoreValues[i]), SW / 2 - 40, rowY);
            // Difficulty
            int d = scoreDifficulty[i];
            if (d >= 0 && d < dNames.length) {
                g2.setColor(dCols[d]);
                g2.setFont(pixelFont(11));
                g2.drawString(dNames[d], SW / 2 + 140, rowY);
            }
        }
        if (scoreCount == 0) {
            g2.setColor(new Color(60, 100, 80));
            g2.setFont(pixelFont(14));
            g2.drawString("Sin puntajes aun. Juega y gana!", SW / 2 - 160, (int) (SH * 0.5));
        }

        int backX = SW / 2 - 110, backY = (int) (SH * 0.88), backW = 220, backH = 48;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        g2.setColor(backHover ? new Color(0, 180, 100, 30) : new Color(8, 18, 14));
        g2.fillRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_PATH_EDGE : new Color(0, 100, 60));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_BG : COL_UI_TEXT);
        g2.setFont(pixelFont(16));
        g2.drawString("< MENU", backX + 70, backY + 31);
    }

    // =====================================================================
    // Pantalla del Juego
    // =====================================================================
    void drawGame(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, SW, SH);
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }

        if (selectedMap == 0) {
            drawMapStraight(g2);
        } else {
            drawMapSerpentine(g2);
        }

        // Enemies & towers
        for (Enemy e : enemies) {
            e.draw(g2);
        }
        for (Tower t : towers) {
            t.draw(g2);
        }
        for (Projectile p : projectiles) {
            p.draw(g2);
        }

        // Hover
        if (!gameOver && !gameWon) {
            boolean valid = isValidTowerPosition(mouseX, mouseY) && !towerExistsAt(mouseX, mouseY);
            g2.setColor(valid ? new Color(0, 200, 100, 55) : new Color(200, 50, 50, 55));
            g2.fillRect(mouseX - TOWER_SIZE / 2, mouseY - TOWER_SIZE / 2, TOWER_SIZE, TOWER_SIZE);
            g2.setColor(valid ? new Color(0, 200, 100, 160) : new Color(200, 50, 50, 160));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(mouseX - TOWER_SIZE / 2, mouseY - TOWER_SIZE / 2, TOWER_SIZE, TOWER_SIZE);
        }

        drawTopUI(g2);
        drawBottomUI(g2);

        if (waitingToStart && !gameOver && !gameWon) {
            drawWaitingToStart(g2);
        }
        if (paused && !gameOver && !gameWon && !waitingToStart) {
            drawPauseOverlay(g2);
        }
        if (gameOver) {
            drawEndScreen(g2, false);
        }
        if (gameWon) {
            drawEndScreen(g2, true);
        }
    }

    void drawMapStraight(Graphics2D g2) {
        g2.setColor(new Color(0, 70, 35, 16));
        g2.fillRect(0, UI_TOP_H, BASE_X, PATH_TOP - UI_TOP_H);
        g2.fillRect(0, PATH_BOT, BASE_X, UI_BOT_Y - PATH_BOT);
        // Path glow
        g2.setColor(new Color(0, 40, 25));
        g2.fillRect(0, PATH_TOP - 3, SW, PATH_BOT - PATH_TOP + 6);
        g2.setColor(COL_PATH);
        g2.fillRect(0, PATH_TOP, SW, PATH_BOT - PATH_TOP);
        // Stripe detail
        g2.setColor(new Color(0, 55, 35));
        for (int sx = 0; sx < SW; sx += 32) {
            g2.fillRect(sx, PATH_TOP, 16, PATH_BOT - PATH_TOP);
        }
        // Edges
        g2.setColor(COL_PATH_EDGE);
        g2.setStroke(new BasicStroke(PX));
        for (int px = 0; px < SW; px += PX * 4) {
            g2.fillRect(px, PATH_TOP, PX * 2, PX);
            g2.fillRect(px, PATH_BOT - PX, PX * 2, PX);
        }
        // Center dashed
        int midPath = (PATH_TOP + PATH_BOT) / 2;
        g2.setColor(new Color(0, 90, 55));
        for (int px = 0; px < SW; px += PX * 6) {
            g2.fillRect(px, midPath - PX / 2, PX * 3, PX);
        }
        drawBase(g2);
    }

    void drawMapSerpentine(Graphics2D g2) {
        if (mapWaypoints == null) {
            buildMapWaypoints();
        }
        // Draw path segments between waypoints
        int pathW = 60;
        g2.setStroke(new BasicStroke(pathW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(COL_PATH);
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            g2.drawLine(mapWaypoints[i][0], mapWaypoints[i][1], mapWaypoints[i + 1][0], mapWaypoints[i + 1][1]);
        }
        // Edge glow
        g2.setStroke(new BasicStroke(pathW + 8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(0, 80, 45, 60));
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            g2.drawLine(mapWaypoints[i][0], mapWaypoints[i][1], mapWaypoints[i + 1][0], mapWaypoints[i + 1][1]);
        }
        // Edge lines
        g2.setStroke(new BasicStroke(2));
        g2.setColor(COL_PATH_EDGE);
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            int dx = mapWaypoints[i + 1][0] - mapWaypoints[i][0];
            int dy = mapWaypoints[i + 1][1] - mapWaypoints[i][1];
            double len = Math.hypot(dx, dy);
            int nx = (int) (-dy / len * pathW / 2);
            int ny = (int) (dx / len * pathW / 2);
            g2.drawLine(mapWaypoints[i][0] + nx, mapWaypoints[i][1] + ny, mapWaypoints[i + 1][0] + nx, mapWaypoints[i + 1][1] + ny);
            g2.drawLine(mapWaypoints[i][0] - nx, mapWaypoints[i][1] - ny, mapWaypoints[i + 1][0] - nx, mapWaypoints[i + 1][1] - ny);
        }
        g2.setStroke(new BasicStroke(PX));
        drawBase(g2);
    }

    void drawBase(Graphics2D g2) {
        int midPath = (PATH_TOP + PATH_BOT) / 2;
        int baseW = 55, baseH = PATH_BOT - PATH_TOP;
        g2.setColor(new Color(12, 30, 26));
        g2.fillRect(BASE_X - 8, PATH_TOP + 2, baseW + 16, baseH - 4);
        // Glow
        g2.setColor(new Color(0, 180, 100, 30));
        g2.fillRect(BASE_X - 12, PATH_TOP - 8, baseW + 24, baseH + 16);
        g2.setColor(COL_BASE);
        g2.fillRect(BASE_X, PATH_TOP + 6, baseW, baseH - 12);
        g2.setColor(new Color(180, 255, 200));
        for (int wy = PATH_TOP + 18; wy < PATH_BOT - 18; wy += 20) {
            g2.fillRect(BASE_X + 8, wy, 10, 8);
            g2.fillRect(BASE_X + 26, wy, 10, 8);
        }
        drawMedCross(g2, BASE_X + 27, midPath, 32, new Color(220, 50, 60));
        g2.setColor(COL_PATH_EDGE);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(BASE_X, PATH_TOP + 6, baseW, baseH - 12);
    }

    void drawTopUI(Graphics2D g2) {
        g2.setColor(COL_UI_BG);
        g2.fillRect(0, 0, SW, UI_TOP_H);
        // Top accent bar
        g2.setColor(new Color(0, 200, 110, 80));
        g2.fillRect(0, 0, SW, 5);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(0, UI_TOP_H, SW, UI_TOP_H);

        int topY = UI_TOP_H / 2 + 5;
        drawPixelHeart(g2, 10, UI_TOP_H / 2 - 9, COL_HEALTH);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(15));
        g2.drawString("" + baseHealth, 36, topY);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(90, 6, PX, UI_TOP_H - 12);
        drawPixelCoin(g2, 100, UI_TOP_H / 2 - 10);
        g2.setColor(COL_MONEY);
        g2.setFont(pixelFont(15));
        g2.drawString("$" + money, 126, topY);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(215, 6, PX, UI_TOP_H - 12);
        drawPixelBio(g2, 226, UI_TOP_H / 2 - 10);
        g2.setColor(COL_WAVE);
        g2.setFont(pixelFont(15));
        g2.drawString("OL " + wave + "/" + MAX_WAVE, 252, topY);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(355, 6, PX, UI_TOP_H - 12);
        // Score
        drawStar(g2, 364, UI_TOP_H / 2 - 10, COL_SCORE);
        g2.setColor(COL_SCORE);
        g2.setFont(pixelFont(14));
        g2.drawString("" + score, 388, topY);
        // Difficulty
        Color[] dCols = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60), new Color(180, 40, 220)};
        String[] dNames = {"FAC", "NOR", "DIF", "PES"};
        g2.setColor(dCols[difficulty.ordinal()]);
        g2.setFont(pixelFont(10));
        g2.drawString("[" + dNames[difficulty.ordinal()] + "]", 470, topY);
        // Map tag
        g2.setColor(new Color(120, 180, 150));
        g2.setFont(pixelFont(9));
        g2.drawString(selectedMap == 0 ? "[RECTO]" : "[SERP]", 530, topY);
        // Progress bar
        int progX = 590, progW = SW - 590 - 220, progH = 18;
        g2.setColor(new Color(8, 25, 16));
        g2.fillRect(progX, UI_TOP_H / 2 - progH / 2, progW, progH);
        // Segmented
        for (int seg = 0; seg < MAX_WAVE; seg++) {
            if (seg < wave - 1) {
                g2.setColor(new Color(0, 160, 90));
            } else if (seg == wave - 1) {
                g2.setColor(new Color(0, 220, 120));
            } else {
                g2.setColor(new Color(15, 35, 25));
            }
            g2.fillRect(progX + 2 + seg * (progW - 4) / MAX_WAVE, UI_TOP_H / 2 - progH / 2 + 2, (progW - 4) / MAX_WAVE - 1, progH - 4);
        }
        g2.setColor(COL_UI_BORDER);
        g2.drawRect(progX, UI_TOP_H / 2 - progH / 2, progW, progH);
        g2.setColor(new Color(0, 200, 100));
        g2.setFont(pixelFont(8));
        g2.drawString("PROGRESO", progX + progW / 2 - 28, UI_TOP_H / 2 + 3);
        drawPlayPauseButton(g2);
        drawMenuButton(g2);
    }

    void drawBottomUI(Graphics2D g2) {
        g2.setColor(COL_UI_BG);
        g2.fillRect(0, UI_BOT_Y, SW, UI_BOT_H);
        g2.setColor(new Color(0, 200, 110, 80));
        g2.fillRect(0, UI_BOT_Y, SW, 5);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(0, UI_BOT_Y, SW, UI_BOT_Y);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(10));
        g2.drawString("[ SELECCIONAR DEFENSA ]  —  Click en zona valida para colocar", 10, UI_BOT_Y + 16);

        // 8 tower types
        String[] types = {"NORMAL", "FIRE", "ICE", "ELEC", "SONIC", "NANO", "PLASMA", "ACID"};
        int[] costs = {50, 70, 60, 80, 90, 110, 130, 100};
        String[] labels = {"JERINGA", "IBUPROFEN", "ANALGESIC", "AMOXICILN", "ULTRASONC", "NANOTECN", "PLASMA RX", "ACIDO"};
        String[] keys = {"[1]", "[2]", "[3]", "[4]", "[5]", "[6]", "[7]", "[8]"};
        Color[] colors = {new Color(180, 255, 200), new Color(255, 120, 60), new Color(80, 200, 255), new Color(255, 230, 60),
            new Color(255, 80, 200), new Color(80, 255, 200), new Color(200, 80, 255), new Color(180, 255, 80)};
        int btnW = (SW - 20) / 8 - 6, btnH = UI_BOT_H - 22;
        for (int i = 0; i < types.length; i++) {
            boolean sel = selectedTower.equals(types[i]);
            int bx = 10 + i * (btnW + 6), by2 = UI_BOT_Y + 18, bh = btnH;
            if (sel) {
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
                g2.setColor(new Color(18, 36, 28));
                g2.fillRect(bx, by2, btnW, bh);
                g2.setColor(new Color(0, 70, 48));
                g2.setStroke(new BasicStroke(PX));
                g2.drawRect(bx, by2, btnW, bh);
            }
            drawTowerIcon(g2, bx + 4, by2 + 8, types[i], colors[i], 36);
            g2.setColor(sel ? colors[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(9));
            g2.drawString(labels[i], bx + 44, by2 + 20);
            g2.setColor(new Color(80, 130, 100));
            g2.setFont(pixelFont(8));
            g2.drawString(keys[i], bx + 44, by2 + 32);
            g2.setColor(COL_MONEY);
            g2.setFont(pixelFont(10));
            g2.drawString("$" + costs[i], bx + 44, by2 + 46);
            if (money < costs[i]) {
                g2.setColor(new Color(160, 40, 40, 140));
                g2.fillRect(bx, by2, btnW, bh);
                g2.setColor(new Color(200, 70, 70));
                g2.setFont(pixelFont(8));
                g2.drawString("SIN $", bx + btnW / 2 - 18, by2 + bh - 6);
            }
        }
    }

    void drawPlayPauseButton(Graphics2D g2) {
        int bw = 106, bh = 26;
        int bx = SW - bw - 120, by = UI_TOP_H / 2 - bh / 2;
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        boolean active = waitingToStart || paused;
        if (active) {
            long t = System.currentTimeMillis();
            int glow = (int) (Math.abs(Math.sin(t / 400.0)) * 40) + 15;
            g2.setColor(new Color(0, 220, 100, glow));
            g2.fillRect(bx - 4, by - 4, bw + 8, bh + 8);
        }
        g2.setColor(hover ? new Color(0, 200, 100, 50) : new Color(8, 20, 14));
        g2.fillRect(bx, by, bw, bh);
        g2.setColor(hover ? COL_PATH_EDGE : (active ? new Color(0, 200, 80) : new Color(0, 130, 75)));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(bx, by, bw, bh);
        g2.setColor(active ? new Color(0, 255, 120) : COL_UI_TEXT);
        if (waitingToStart || paused) {
            for (int row = 0; row < 10; row++) {
                int w2 = 10 - Math.abs(row - 5);
                g2.fillRect(bx + 6, by + 3 + row, w2, 1);
            }
            g2.setFont(pixelFont(10));
            g2.drawString("JUGAR", bx + 22, by + bh - 6);
        } else {
            g2.setColor(COL_UI_TEXT);
            g2.fillRect(bx + 6, by + 7, 6, 12);
            g2.fillRect(bx + 16, by + 7, 6, 12);
            g2.setFont(pixelFont(10));
            g2.drawString("PAUSA", bx + 28, by + bh - 6);
        }
    }

    void drawMenuButton(Graphics2D g2) {
        int bw = 106, bh = 26;
        int bx = SW - bw - 10, by = UI_TOP_H / 2 - bh / 2;
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        g2.setColor(hover ? new Color(200, 100, 50, 50) : new Color(8, 20, 14));
        g2.fillRect(bx, by, bw, bh);
        g2.setColor(hover ? new Color(255, 150, 80) : new Color(160, 70, 25));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(bx, by, bw, bh);
        g2.setColor(hover ? COL_BG : new Color(255, 160, 100));
        g2.setFont(pixelFont(10));
        g2.drawString("< MENU", bx + 14, by + bh - 6);
    }

    int ppBtnX() {
        return SW - 106 - 120;
    }

    int ppBtnY() {
        return UI_TOP_H / 2 - 13;
    }

    int menuBtnX() {
        return SW - 106 - 10;
    }

    int menuBtnY() {
        return UI_TOP_H / 2 - 13;
    }

    void drawWaitingToStart(Graphics2D g2) {
        int barY = (PATH_TOP + PATH_BOT) / 2 - 55;
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(0, barY, SW, 110);
        long t = System.currentTimeMillis();
        int alpha = (int) (Math.abs(Math.sin(t / 600.0)) * 80) + 140;
        g2.setColor(new Color(0, 220, 100, alpha));
        g2.setFont(pixelFont(26));
        String msg = ">>> Presiona JUGAR para Oleada " + wave + " <<<";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, SW / 2 - fm.stringWidth(msg) / 2, barY + 48);
        g2.setColor(new Color(180, 255, 200, 160));
        g2.setFont(pixelFont(11));
        String sub = "(Coloca torres ahora — gratis durante preparacion)";
        fm = g2.getFontMetrics();
        g2.drawString(sub, SW / 2 - fm.stringWidth(sub) / 2, barY + 74);
    }

    void drawPauseOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(0, UI_TOP_H, SW, UI_BOT_Y - UI_TOP_H);
        g2.setColor(new Color(0, 220, 100));
        g2.setFont(pixelFont(40));
        String msg = "-- PAUSA --";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, SW / 2 - fm.stringWidth(msg) / 2, SH / 2 - 12);
        g2.setColor(new Color(180, 255, 200, 200));
        g2.setFont(pixelFont(14));
        String sub = "Click en JUGAR o ESPACIO para continuar";
        fm = g2.getFontMetrics();
        g2.drawString(sub, SW / 2 - fm.stringWidth(sub) / 2, SH / 2 + 30);
    }

    void drawEndScreen(Graphics2D g2, boolean won) {
        g2.setColor(new Color(0, 0, 0, 190));
        g2.fillRect(0, 0, SW, SH);
        g2.setColor(won ? new Color(0, 220, 120) : new Color(220, 50, 60));
        g2.setFont(pixelFont(52));
        String header = won ? "DEFENSA COMPLETADA!" : "GAME OVER";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(header, SW / 2 - fm.stringWidth(header) / 2, SH / 2 - 80);
        g2.setColor(COL_SCORE);
        g2.setFont(pixelFont(22));
        String sc = "Puntaje: " + score;
        fm = g2.getFontMetrics();
        g2.drawString(sc, SW / 2 - fm.stringWidth(sc) / 2, SH / 2 - 30);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(15));
        String msg2 = won ? "Sobreviviste 25 oleadas  |  Vida: " + baseHealth : "Base destruida en oleada " + wave;
        fm = g2.getFontMetrics();
        g2.drawString(msg2, SW / 2 - fm.stringWidth(msg2) / 2, SH / 2 + 10);
        drawEndButton(g2, SW / 2 - 160, SH / 2 + 40, 320, 55, "INGRESAR PUNTAJE");
        drawEndButton2(g2, SW / 2 - 160, SH / 2 + 108, 320, 50, "VOLVER AL MENU");
    }

    void drawEndButton(Graphics2D g2, int x, int y, int w, int h, String label) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g2.setColor(hover ? new Color(0, 200, 100, 40) : new Color(8, 20, 14));
        g2.fillRect(x, y, w, h);
        g2.setColor(hover ? COL_PATH_EDGE : new Color(0, 120, 70));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(x, y, w, h);
        g2.setColor(hover ? COL_BG : COL_UI_TEXT);
        g2.setFont(pixelFont(16));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h / 2 + 6);
    }

    void drawEndButton2(Graphics2D g2, int x, int y, int w, int h, String label) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g2.setColor(hover ? new Color(200, 80, 30, 40) : new Color(8, 20, 14));
        g2.fillRect(x, y, w, h);
        g2.setColor(hover ? new Color(255, 150, 80) : new Color(180, 80, 30));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(x, y, w, h);
        g2.setColor(hover ? COL_BG : new Color(255, 160, 100));
        g2.setFont(pixelFont(15));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h / 2 + 5);
    }

    // Tower icons (bigger)
    void drawTowerIcon(Graphics2D g, int x, int y, String type, Color c, int size) {
        switch (type) {
            case "NORMAL":
                drawIconSyringe(g, x, y, c, size);
                break;
            case "FIRE":
                drawIconFire(g, x, y, c, size);
                break;
            case "ICE":
                drawIconCryo(g, x, y, c, size);
                break;
            case "ELEC":
                drawIconElec(g, x, y, c, size);
                break;
            case "SONIC":
                drawIconSonic(g, x, y, c, size);
                break;
            case "NANO":
                drawIconNano(g, x, y, c, size);
                break;
            case "PLASMA":
                drawIconPlasma(g, x, y, c, size);
                break;
            case "ACID":
                drawIconAcid(g, x, y, c, size);
                break;
        }
    }

    void drawIconSyringe(Graphics2D g, int x, int y, Color c, int s) {
        g.setColor(c);
        g.fillRect(x + 3, y + 4, s - 6, 8);
        g.fillRect(x + s - 3, y + 6, 4, 4);
        g.setColor(new Color(80, 200, 140));
        g.fillRect(x + 7, y + 6, s - 14, 4);
    }

    void drawIconFire(Graphics2D g, int x, int y, Color c, int s) {
        g.setColor(c);
        g.fillRect(x + s / 4, y, s / 2, s);
        g.setColor(new Color(255, 200, 50));
        g.fillRect(x + s / 4 + 3, y + 4, s / 2 - 6, s - 8);
    }

    void drawIconCryo(Graphics2D g, int x, int y, Color c, int s) {
        drawMedCross(g, x + s / 2, y + s / 2, s - 4, c);
    }

    void drawIconElec(Graphics2D g, int x, int y, Color c, int s) {
        int[][] b = {{0, 0, 1, 1}, {0, 1, 1, 0}, {1, 1, 0, 0}, {0, 1, 1, 1}};
        g.setColor(c);
        for (int r = 0; r < b.length; r++) {
            for (int col = 0; col < b[r].length; col++) {
                if (b[r][col] == 1) {
                    g.fillRect(x + col * (s / 4), y + r * (s / 4), s / 4, s / 4);
                }
            }
        }
    }

    void drawIconSonic(Graphics2D g, int x, int y, Color c, int s) {
        g.setColor(c);
        for (int i = 0; i < 4; i++) {
            int alpha = 200 - i * 40;
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            g.drawOval(x + i * 4, y + i * 4, s - i * 8, s - i * 8);
        }
    }

    void drawIconNano(Graphics2D g, int x, int y, Color c, int s) {
        g.setColor(c);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                g.fillRect(x + i * (s / 3) + 2, y + j * (s / 3) + 2, s / 3 - 2, s / 3 - 2);
            }
        }
    }

    void drawIconPlasma(Graphics2D g, int x, int y, Color c, int s) {
        g.setColor(c);
        g.fillOval(x + 2, y + 2, s - 4, s - 4);
        g.setColor(new Color(255, 255, 255, 100));
        g.fillOval(x + s / 4, y + s / 4, s / 4, s / 4);
    }

    void drawIconAcid(Graphics2D g, int x, int y, Color c, int s) {
        g.setColor(c);
        int[][] d = {{0, 1, 0}, {1, 1, 1}, {0, 1, 0}};
        for (int r = 0; r < d.length; r++) {
            for (int col = 0; col < d[r].length; col++) {
                if (d[r][col] == 1) {
                    g.fillRect(x + col * (s / 3), y + r * (s / 3), s / 3, s / 3);
                }
            }
        }
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 100));
        g.fillOval(x, y, s, s);
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

        // Move projectiles
        Iterator<Projectile> pit = projectiles.iterator();
        while (pit.hasNext()) {
            Projectile p = pit.next();
            p.move();
            if (p.done) {
                pit.remove();
            }
        }

        // Move enemies
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy en = it.next();
            en.move();
            if (en.x > BASE_X + 12) {
                baseHealth -= (en instanceof FinalBoss ? 50 : en instanceof ColossusEnemy ? 35 : en instanceof TankEnemy ? 20 : en instanceof BossEnemy ? 30 : 10);
                it.remove();
                if (baseHealth <= 0) {
                    baseHealth = 0;
                    gameOver = true;
                    timer.stop();
                    sfxGameOver();
                }
            }
        }

        // Healer enemies heal nearby allies
        for (Enemy en : enemies) {
            if (en instanceof HealerEnemy) {
                HealerEnemy h = (HealerEnemy) en;
                h.healTick++;
                if (h.healTick > 60) {
                    h.healTick = 0;
                    for (Enemy ally : enemies) {
                        if (ally != h && Math.hypot(h.x - ally.x, h.y - ally.y) < 120 && ally.health < ally.maxHealth) {
                            ally.health = Math.min(ally.maxHealth, ally.health + 15);
                        }
                    }
                }
            }
        }

        // Update towers
        for (Tower t : towers) {
            t.update(enemies, projectiles);
        }

        // Kill enemies
        List<Enemy> toRemove = new ArrayList<>();
        for (Enemy en : enemies) {
            if (en.health <= 0) {
                toRemove.add(en);
                int reward = en.reward;
                money += reward;
                score += (int) (reward * scoreMult());
                if (en instanceof BossEnemy || en instanceof FinalBoss || en instanceof ColossusEnemy) {
                    sfxBossDeath();
                } else {
                    sfxEnemyDeath();
                }
            }
        }
        enemies.removeAll(toRemove);

        if (enemies.isEmpty() && !gameOver) {
            if (wave >= MAX_WAVE) {
                score += (int) (baseHealth * 50 * scoreMult());
                gameWon = true;
                timer.stop();
                sfxVictory();
            } else {
                wave++;
                money += waveBonus();
                prepareWave();
                sfxNewWave(wave);
                waitingToStart = true;
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
            int bw = 320, bh = 58, bx0 = SW / 2 - bw / 2;
            int[] byArr = {(int) (SH * 0.35), (int) (SH * 0.47), (int) (SH * 0.59), (int) (SH * 0.71)};
            if (mx >= bx0 && mx <= bx0 + bw && my >= byArr[0] && my <= byArr[0] + bh) {
                sfxMenuClick();
                gameState = GameState.SETTINGS;/*then map select*/
                gameState = GameState.MAP_SELECT;
            } else if (mx >= bx0 && mx <= bx0 + bw && my >= byArr[1] && my <= byArr[1] + bh) {
                sfxMenuClick();
                gameState = GameState.SETTINGS;
            } else if (mx >= bx0 && mx <= bx0 + bw && my >= byArr[2] && my <= byArr[2] + bh) {
                sfxMenuClick();
                gameState = GameState.SCOREBOARD;
            } else if (mx >= bx0 && mx <= bx0 + bw && my >= byArr[3] && my <= byArr[3] + bh) {
                stopBGMusic();
                System.exit(0);
            }
        } else if (gameState == GameState.MAP_SELECT) {
            int mw = (int) (SW * 0.35), mh = (int) (SH * 0.45), mx1 = (int) (SW * 0.08), mx2 = (int) (SW * 0.57), myB = (int) (SH * 0.22);
            if (mx >= mx1 && mx <= mx1 + mw && my >= myB && my <= myB + mh) {
                sfxMenuClick();
                selectedMap = 0;
            } else if (mx >= mx2 && mx <= mx2 + mw && my >= myB && my <= myB + mh) {
                sfxMenuClick();
                selectedMap = 1;
            }
            int startBtnW = 280, startBtnH = 55, startBtnX = SW / 2 - startBtnW / 2, startBtnY = (int) (SH * 0.80);
            if (mx >= startBtnX && mx <= startBtnX + startBtnW && my >= startBtnY && my <= startBtnY + startBtnH) {
                sfxMenuClick();
                startGame();
            }
            int backX = 20, backY = (int) (SH * 0.88), backW = 180, backH = 40;
            if (mx >= backX && mx <= backX + backW && my >= backY && my <= backY + backH) {
                sfxMenuClick();
                gameState = GameState.MENU;
            }
        } else if (gameState == GameState.SETTINGS) {
            Difficulty[] diffs = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE};
            int btnW = 165, btnH = 65, totalW = 4 * btnW + 60, startBX = SW / 2 - totalW / 2;
            int by = (int) (SH * 0.26);
            for (int i = 0; i < 4; i++) {
                int bx = startBX + i * (btnW + 20);
                if (mx >= bx && mx <= bx + btnW && my >= by && my <= by + btnH) {
                    sfxMenuClick();
                    difficulty = diffs[i];
                }
            }
            // Window toggle button
            int tbx = SW / 2 - 60, tby = (int) (SH * 0.72) - 20, tbw = 220, tbh = 40;
            if (mx >= tbx && mx <= tbx + tbw && my >= tby && my <= tby + tbh) {
                sfxMenuClick();
                toggleWindowMode();
            }
            int backX = SW / 2 - 110, backY = (int) (SH * 0.86), backW = 220, backH = 48;
            if (mx >= backX && mx <= backX + backW && my >= backY && my <= backY + backH) {
                sfxMenuClick();
                gameState = GameState.MENU;
            }
        } else if (gameState == GameState.SCOREBOARD) {
            int backX = SW / 2 - 110, backY = (int) (SH * 0.88), backW = 220, backH = 48;
            if (mx >= backX && mx <= backX + backW && my >= backY && my <= backY + backH) {
                sfxMenuClick();
                gameState = GameState.MENU;
            }
        } else if (gameState == GameState.SCORE_ENTRY) {
            if (currentName.length() == 5) {
                int confBtnW = 260, confBtnH = 52, confBtnX = SW / 2 - confBtnW / 2, confBtnY = (int) (SH * 0.63);
                if (mx >= confBtnX && mx <= confBtnX + confBtnW && my >= confBtnY && my <= confBtnY + confBtnH) {
                    confirmScore();
                }
            }
        } else if (gameState == GameState.PLAYING) {
            if (gameOver || gameWon) {
                // "Ingresar puntaje" button
                if (mx >= SW / 2 - 160 && mx <= SW / 2 + 160 && my >= SH / 2 + 40 && my <= SH / 2 + 95) {
                    currentName = "";
                    gameState = GameState.SCORE_ENTRY;
                    return;
                }
                // "Volver al menu" button
                if (mx >= SW / 2 - 160 && mx <= SW / 2 + 160 && my >= SH / 2 + 108 && my <= SH / 2 + 158) {
                    stopBGMusic();
                    gameState = GameState.MENU;
                    return;
                }
                return;
            }
            int mbx = menuBtnX(), mby = menuBtnY(), mbw = 106, mbh = 26;
            if (mx >= mbx && mx <= mbx + mbw && my >= mby && my <= mby + mbh) {
                sfxMenuClick();
                stopBGMusic();
                gameState = GameState.MENU;
                return;
            }
            int pbx = ppBtnX(), pby = ppBtnY(), pbw = 106, pbh = 26;
            if (mx >= pbx && mx <= pbx + pbw && my >= pby && my <= pby + pbh) {
                sfxMenuClick();
                if (waitingToStart) {
                    launchWave();
                } else {
                    paused = !paused;
                }
                return;
            }

            int[] costs = {50, 70, 60, 80, 90, 110, 130, 100};
            String[] types = {"NORMAL", "FIRE", "ICE", "ELEC", "SONIC", "NANO", "PLASMA", "ACID"};
            // Check tower button clicks
            int btnW = (SW - 20) / 8 - 6, btnH = UI_BOT_H - 22;
            for (int i = 0; i < types.length; i++) {
                int bx = 10 + i * (btnW + 6), by2 = UI_BOT_Y + 18, bh = btnH;
                if (mx >= bx && mx <= bx + btnW && my >= by2 && my <= by2 + bh) {
                    selectedTower = types[i];
                    return;
                }
            }

            int[] costsMap = {50, 70, 60, 80, 90, 110, 130, 100};
            java.util.Map<String, Integer> costMap = new java.util.HashMap<>();
            for (int i = 0; i < types.length; i++) {
                costMap.put(types[i], costsMap[i]);
            }
            int cost = costMap.getOrDefault(selectedTower, 50);
            if (money >= cost && isValidTowerPosition(mx, my) && !towerExistsAt(mx, my)) {
                towers.add(new Tower(mx, my, selectedTower));
                money -= cost;
                sfxTowerPlaced();
            }
        }
    }

    void confirmScore() {
        addScore(currentName.toUpperCase(), score, difficulty.ordinal());
        gameState = GameState.SCOREBOARD;
    }

    static JFrame mainFrame;
    static TDX gameInstance;

    void toggleWindowMode() {
        if (isFullScreen) {
            gd.setFullScreenWindow(null);
            mainFrame.setUndecorated(false);
            mainFrame.setSize(1280, 720);
            mainFrame.setLocationRelativeTo(null);
            mainFrame.setVisible(true);
            isFullScreen = false;
        } else {
            mainFrame.setVisible(false);
            mainFrame.dispose();
            mainFrame.setUndecorated(true);
            if (gd.isFullScreenSupported()) {
                mainFrame.setVisible(true);
                gd.setFullScreenWindow(mainFrame);
            } else {
                mainFrame.setVisible(true);
                mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
            isFullScreen = true;
        }
    }

    public void setKeyBindings() {
        String[] types = {"NORMAL", "FIRE", "ICE", "ELEC", "SONIC", "NANO", "PLASMA", "ACID"};
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8"};
        for (int i = 0; i < types.length; i++) {
            final String t = types[i];
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keys[i]), t);
            getActionMap().put(t, new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    selectedTower = t;
                }
            });
        }
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
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "toggle");
        getActionMap().put("toggle", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                toggleWindowMode();
            }
        });
    }

    // Key typed for name entry
    public void addKeyTyped(KeyEvent e) {
        if (gameState == GameState.SCORE_ENTRY) {
            char c = e.getKeyChar();
            if (c == KeyEvent.VK_BACK_SPACE) {
                if (currentName.length() > 0) {
                    currentName = currentName.substring(0, currentName.length() - 1);
                }
            } else if (c == KeyEvent.VK_ENTER) {
                if (currentName.length() == 5) {
                    confirmScore();
                }
            } else if (currentName.length() < 5 && (Character.isLetterOrDigit(c))) {
                currentName += Character.toUpperCase(c);
            }
            repaint();
        }
    }

    // =====================================================================
    // MAIN
    // =====================================================================
    public static void main(String[] args) {
        mainFrame = new JFrame("TDX - DEFENSA MEDICA");
        mainFrame.getContentPane().setBackground(new Color(8, 14, 20));
        gameInstance = new TDX();
        gameInstance.setKeyBindings();
        // Key listener for name entry
        mainFrame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                gameInstance.addKeyTyped(e);
            }
        });
        mainFrame.add(gameInstance);
        mainFrame.setUndecorated(true);
        gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (gd.isFullScreenSupported()) {
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            gd.setFullScreenWindow(mainFrame);
        } else {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            SW = screen.width;
            SH = screen.height;
            mainFrame.setPreferredSize(screen);
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.pack();
            mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            mainFrame.setVisible(true);
        }
        gameInstance.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        gameInstance.getActionMap().put("esc", new AbstractAction() {
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
    // PROJECTILE
    // =====================================================================
    class Projectile {

        double x, y, tx, ty, speed = 10;
        Color col;
        boolean done = false;
        String type;
        Enemy target;

        Projectile(double x, double y, Enemy target, Color col, String type) {
            this.x = x;
            this.y = y;
            this.target = target;
            this.col = col;
            this.type = type;
        }

        void move() {
            if (target == null || target.health <= 0) {
                done = true;
                return;
            }
            tx = target.x + 16;
            ty = target.y + 12;
            double dx = tx - x, dy = ty - y, dist = Math.hypot(dx, dy);
            if (dist < speed + 2) {
                done = true;
                return;
            }
            x += dx / dist * speed;
            y += dy / dist * speed;
        }

        void draw(Graphics2D g) {
            g.setColor(col);
            g.fillOval((int) x - 4, (int) y - 4, 8, 8);
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 120));
            g.fillOval((int) x - 7, (int) y - 7, 14, 14);
        }
    }

    // =====================================================================
    // ENEMY BASE
    // =====================================================================
    class Enemy {

        int x, y, maxHealth = 50, health = 50, speed = 2, animTick = 0, reward = 20;
        int waypointIndex = 0;

        Enemy(int x, int y) {
            this.x = x;
            this.y = y;
        }

        void move() {
            if (selectedMap == 1 && mapWaypoints != null) {
                moveAlongWaypoints();
            } else {
                x += speed;
                animTick++;
            }
        }

        void moveAlongWaypoints() {
            animTick++;
            if (waypointIndex >= mapWaypoints.length) {
                x += speed;
                return;
            }
            int tx = mapWaypoints[waypointIndex][0], ty2 = mapWaypoints[waypointIndex][1];
            double dx = tx - x, dy = ty2 - y, dist = Math.hypot(dx, dy);
            if (dist < speed + 1) {
                waypointIndex++;
                if (waypointIndex >= mapWaypoints.length) {
                    x += speed;
                    return;
                }
            }
            x += (int) (dx / dist * speed);
            y += (int) (dy / dist * speed);
        }

        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 6) % 2;
            int[][] virus = {{0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 0}};
            g2.setColor(new Color(0, 0, 0, 90));
            for (int r = 0; r < virus.length; r++) {
                for (int c2 = 0; c2 < virus[r].length; c2++) {
                    if (virus[r][c2] == 1) {
                        g2.fillRect(x + c2 * PX + 2, y + r * PX + 2, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(210, 40, 40));
            for (int r = 0; r < virus.length; r++) {
                for (int c2 = 0; c2 < virus[r].length; c2++) {
                    if (virus[r][c2] == 1) {
                        g2.fillRect(x + c2 * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Highlight
            g2.setColor(new Color(255, 130, 130));
            g2.fillRect(x + PX, y, PX * 2, PX);
            g2.fillRect(x, y + PX, PX, PX);
            int cx2 = x + 16, cy2 = y + 12;
            int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
            g2.setColor(new Color(230, 60, 60));
            for (int[] sp : spikes) {
                g2.fillRect(cx2 + sp[0] * (12 + wobble) - PX / 2, cy2 + sp[1] * (12 + wobble) - PX / 2, PX, PX);
            }
            g2.setColor(new Color(255, 220, 220));
            g2.fillRect(x + PX * 2, y + PX, PX, PX);
            g2.fillRect(x + PX * 5, y + PX, PX, PX);
        }
    }

    class SpeedEnemy extends Enemy {

        SpeedEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((30 + wave * 5) * enemyHealthMult());
            health = maxHealth;
            speed = 5;
            reward = 35;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 3) % 2;
            int[][] fast = {{0, 1, 1, 1, 0}, {1, 1, 1, 1, 1}, {1, 1, 1, 1, 1}, {0, 1, 1, 1, 0}};
            g2.setColor(new Color(255, 160, 0));
            for (int r = 0; r < fast.length; r++) {
                for (int c = 0; c < fast[r].length; c++) {
                    if (fast[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(255, 200, 50, 110));
            for (int i = 1; i <= 4; i++) {
                g2.fillRect(x - i * 7 - wobble, y + PX, PX * 3, PX * 2);
            }
            g2.setColor(Color.WHITE);
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 3, y + PX, PX, PX);
            g2.setColor(new Color(255, 100, 0));
            g2.setFont(new Font("Monospaced", Font.BOLD, 9));
            g2.drawString(">>", x, y - 6);
        }
    }

    class ShieldEnemy extends Enemy {

        int shieldHP = 50;

        ShieldEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((80 + wave * 15) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = 45;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int[][] body = {{0, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 0}};
            g2.setColor(new Color(30, 130, 65));
            for (int r = 0; r < body.length; r++) {
                for (int c = 0; c < body[r].length; c++) {
                    if (body[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            if (shieldHP > 0) {
                g2.setColor(new Color(100, 200, 255, 190));
                g2.fillRect(x + PX * 5, y - PX, PX * 2, PX * 7);
                g2.setColor(new Color(200, 240, 255));
                g2.fillRect(x + PX * 5, y, PX, PX * 5);
            }
            g2.setColor(new Color(255, 240, 50));
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 4, y + PX, PX, PX);
        }
    }

    class TankEnemy extends Enemy {

        TankEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((300 + wave * 35) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = 90;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(new Color(80, 80, 105));
            g2.fillRect(x, y, PX * 12, PX * 10);
            g2.setColor(new Color(120, 120, 155));
            g2.fillRect(x + PX, y + PX, PX * 10, PX * 2);
            g2.fillRect(x + PX, y + PX * 7, PX * 10, PX * 2);
            g2.setColor(new Color(40, 40, 55));
            g2.fillRect(x, y + PX * 8, PX * 4, PX * 3);
            g2.fillRect(x + PX * 8, y + PX * 8, PX * 4, PX * 3);
            g2.setColor(new Color(60, 60, 85));
            g2.fillRect(x + PX * 10, y + PX * 4, PX * 5, PX * 2);
            g2.setColor(new Color(255, 55, 55));
            g2.fillRect(x + PX * 2, y + PX * 3, PX * 2, PX * 2);
            g2.fillRect(x + PX * 7, y + PX * 3, PX * 2, PX * 2);
            g2.setColor(new Color(200, 80, 80));
            g2.setFont(new Font("Monospaced", Font.BOLD, 8));
            g2.drawString("TANK", x + 8, y - 20);
        }
    }

    class BossEnemy extends Enemy {

        BossEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((200 + wave * 22) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = 160;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 8) % 2;
            int[][] boss = {{0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            g2.setColor(new Color(0, 0, 0, 110));
            for (int r = 0; r < boss.length; r++) {
                for (int c = 0; c < boss[r].length; c++) {
                    if (boss[r][c] == 1) {
                        g2.fillRect(x + c * PX + 3, y + r * PX + 3, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(140, 30, 165));
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
            g2.drawString("JEFE", x + 12, y - 22);
        }
    }

    // NEW ENEMIES
    class StealthEnemy extends Enemy {

        int visibleTick = 0;
        boolean visible = false;

        StealthEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((60 + wave * 8) * enemyHealthMult());
            health = maxHealth;
            speed = 3;
            reward = 60;
        }

        @Override
        void move() {
            super.move();
            visibleTick++;
            if (visibleTick > 40) {
                visibleTick = 0;
                visible = !visible;
            }
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int alpha = visible ? 200 : 55;
            int[][] virus = {{0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 0}};
            g2.setColor(new Color(60, 60, 200, alpha));
            for (int r = 0; r < virus.length; r++) {
                for (int c = 0; c < virus[r].length; c++) {
                    if (virus[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            if (visible) {
                g2.setColor(new Color(180, 180, 255, alpha));
                g2.drawString("???", x + 4, y - 8);
            }
        }
    }

    class HealerEnemy extends Enemy {

        int healTick = 0;

        HealerEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((80 + wave * 10) * enemyHealthMult());
            health = maxHealth;
            speed = 2;
            reward = 80;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int[][] healer = {{0, 1, 1, 1, 0}, {1, 1, 1, 1, 1}, {1, 1, 1, 1, 1}, {1, 1, 1, 1, 1}, {0, 1, 1, 1, 0}};
            g2.setColor(new Color(60, 200, 90));
            for (int r = 0; r < healer.length; r++) {
                for (int c = 0; c < healer[r].length; c++) {
                    if (healer[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            drawMedCross(g2, x + 10, y + 10, 12, new Color(255, 60, 60));
            g2.setColor(new Color(120, 255, 150));
            g2.setFont(new Font("Monospaced", Font.BOLD, 8));
            g2.drawString("+", x + 4, y - 6);
        }
    }

    class MutantEnemy extends Enemy {

        int mutantPhase = 0;

        MutantEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((400 + wave * 40) * enemyHealthMult());
            health = maxHealth;
            speed = 2;
            reward = 200;
        }

        @Override
        void move() {
            super.move();
            if (health < maxHealth / 2 && mutantPhase == 0) {
                mutantPhase = 1;
                speed += 2;
            }
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 4) % 3;
            Color baseCol = mutantPhase == 1 ? new Color(255, 100, 0) : new Color(100, 40, 180);
            int[][] mut = {{0, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 0}};
            g2.setColor(baseCol);
            for (int r = 0; r < mut.length; r++) {
                for (int c = 0; c < mut[r].length; c++) {
                    if (mut[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Spikes
            g2.setColor(mutantPhase == 1 ? new Color(255, 180, 0) : new Color(180, 80, 255));
            int cx2 = x + 14, cy2 = y + 12;
            for (int deg = 0; deg < 360; deg += 45) {
                double rad = Math.toRadians(deg);
                g2.fillRect(cx2 + (int) (Math.cos(rad) * (14 + wobble)) - 1, cy2 + (int) (Math.sin(rad) * (14 + wobble)) - 1, 3, 3);
            }
            g2.setColor(Color.WHITE);
            g2.fillRect(x + PX * 2, y + PX, PX, PX);
            g2.fillRect(x + PX * 5, y + PX, PX, PX);
            g2.setColor(mutantPhase == 1 ? new Color(255, 120, 0) : new Color(200, 100, 255));
            g2.setFont(new Font("Monospaced", Font.BOLD, 9));
            g2.drawString(mutantPhase == 1 ? "BERSERK!" : "MUTANTE", x - 4, y - 22);
        }
    }

    class ColossusEnemy extends Enemy {

        ColossusEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) (2500 * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = 400;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 6) % 2;
            int[][] f = {{0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            g2.setColor(new Color(0, 0, 0, 130));
            for (int r = 0; r < f.length; r++) {
                for (int c = 0; c < f[r].length; c++) {
                    if (f[r][c] == 1) {
                        g2.fillRect(x + c * PX + 4, y + r * PX + 4, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(50, 120, 180));
            for (int r = 0; r < f.length; r++) {
                for (int c = 0; c < f[r].length; c++) {
                    if (f[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Armor plates
            g2.setColor(new Color(80, 160, 220));
            g2.fillRect(x + PX * 2, y + PX * 2, PX * 4, PX * 4);
            g2.fillRect(x + PX * 8, y + PX * 2, PX * 4, PX * 4);
            g2.setColor(new Color(255, 200, 50));
            g2.fillRect(x + PX * 4, y + PX * 3, PX * 2, PX * 2);
            g2.fillRect(x + PX * 8, y + PX * 3, PX * 2, PX * 2);
            g2.setColor(Color.BLACK);
            g2.fillRect(x + PX * 5, y + PX * 4, PX, PX);
            g2.fillRect(x + PX * 9, y + PX * 4, PX, PX);
            g2.setColor(new Color(100, 200, 255));
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.drawString("!! COLOSO !!", x - 4, y - 24);
            drawHealthBar(g2, x - 4, y - 18, 64, PX + 2, (double) health / maxHealth, new Color(60, 150, 255), new Color(10, 10, 30));
        }
    }

    class FinalBoss extends Enemy {

        FinalBoss(int x, int y) {
            super(x, y);
            maxHealth = 2000;
            health = 2000;
            speed = 1;
            reward = 600;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 4) % 3;
            int[][] f = {{0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            g2.setColor(new Color(0, 0, 0, 130));
            for (int r = 0; r < f.length; r++) {
                for (int c = 0; c < f[r].length; c++) {
                    if (f[r][c] == 1) {
                        g2.fillRect(x + c * PX + 4, y + r * PX + 4, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(170, 20, 20));
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
            // Crown spikes
            g2.setColor(new Color(255, 180, 0));
            for (int i = 0; i < 5; i++) {
                g2.fillRect(x + PX * 2 + i * PX * 3, y - PX * (4 + i % 2), PX * 2, PX * (4 + i % 2));
            }
            // Pulse glow
            long t = System.currentTimeMillis();
            int glowA = (int) (Math.abs(Math.sin(t / 300.0)) * 60) + 20;
            g2.setColor(new Color(255, 60, 0, glowA));
            g2.fillOval(x - 10, y - 10, 80, 60);
            g2.setColor(new Color(255, 60, 0));
            g2.setFont(new Font("Monospaced", Font.BOLD, 12));
            g2.drawString("!! JEFE FINAL !!", x - 8, y - 28);
            drawHealthBar(g2, x - 6, y - 20, 84, PX + 3, (double) health / maxHealth, new Color(255, 80, 0), new Color(35, 5, 5));
        }
    }

    // =====================================================================
    // TOWER (8 types, bigger)
    // =====================================================================
    class Tower {

        int x, y, range, cooldown = 0, animTick = 0, cooldownMax;
        String type;
        Enemy target = null;
        double aimAngle = 0;

        Tower(int x, int y, String type) {
            this.x = x;
            this.y = y;
            this.type = type;
            range = (int) (SW * 0.14);
            cooldownMax = switch (type) {
                case "FIRE" ->
                    18;
                case "ICE" ->
                    22;
                case "ELEC" ->
                    16;
                case "SONIC" ->
                    30;
                case "NANO" ->
                    12;
                case "PLASMA" ->
                    25;
                case "ACID" ->
                    20;
                default ->
                    20;
            };
        }

        void update(List<Enemy> enemies, List<Projectile> projs) {
            if (cooldown > 0) {
                cooldown--;
            }
            animTick++;
            target = null;
            double bestDist = Double.MAX_VALUE;
            for (Enemy e : enemies) {
                double dist = Math.hypot(x + 20 - (e.x + 16), y + 16 - (e.y + 12));
                if (dist < range && dist < bestDist) {
                    bestDist = dist;
                    target = e;
                }
            }
            if (target != null) {
                aimAngle = Math.atan2((target.y + 12) - (y + 16), (target.x + 16) - (x + 20));
                if (cooldown == 0) {
                    int damage = switch (type) {
                        case "FIRE" ->
                            28;
                        case "ICE" ->
                            7;
                        case "ELEC" ->
                            22;
                        case "SONIC" ->
                            12;
                        case "NANO" ->
                            35;
                        case "PLASMA" ->
                            40;
                        case "ACID" ->
                            18;
                        default ->
                            16;
                    };
                    damage += wave * 2;
                    // Apply effects
                    if (type.equals("ICE") && target.speed > 1) {
                        target.speed = Math.max(1, target.speed - 1);
                    }
                    if (type.equals("SONIC")) {
                        for (Enemy e : enemies) {
                            if (Math.hypot(x - e.x, y - e.y) < range / 2 && e.speed > 1) {
                                e.speed = Math.max(1, e.speed - 1);
                            }
                        }
                    }
                    if (type.equals("ACID")) {
                        target.maxHealth = (int) (target.maxHealth * 0.97);
                    } // reduce max health
                    // Shield handling
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
                    } // Stealth detection
                    else if (target instanceof StealthEnemy) {
                        StealthEnemy st = (StealthEnemy) target;
                        st.visible = true;
                        st.visibleTick = 0;
                        target.health -= damage;
                    } else {
                        target.health -= damage;
                    }
                    // Elec chain
                    if (type.equals("ELEC")) {
                        List<Enemy> nearby = new ArrayList<>(enemies);
                        nearby.remove(target);
                        for (Enemy e : nearby) {
                            if (Math.hypot(target.x - e.x, target.y - e.y) < 80) {
                                e.health -= damage / 2;
                            }
                        }
                    }
                    // Plasma AOE
                    if (type.equals("PLASMA")) {
                        for (Enemy e : enemies) {
                            if (e != target && Math.hypot(target.x - e.x, target.y - e.y) < 60) {
                                e.health -= damage / 3;
                            }
                        }
                    }
                    // Shoot projectile
                    Color pCol = switch (type) {
                        case "FIRE" ->
                            new Color(255, 140, 60);
                        case "ICE" ->
                            new Color(80, 200, 255);
                        case "ELEC" ->
                            new Color(255, 230, 60);
                        case "SONIC" ->
                            new Color(255, 80, 200);
                        case "NANO" ->
                            new Color(80, 255, 200);
                        case "PLASMA" ->
                            new Color(200, 80, 255);
                        case "ACID" ->
                            new Color(180, 255, 80);
                        default ->
                            new Color(180, 255, 200);
                    };
                    projs.add(new Projectile(x + 20, y + 16, target, pCol, type));
                    cooldown = cooldownMax;
                }
            }
        }

        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            Color mainColor, accentColor;
            switch (type) {
                case "FIRE":
                    mainColor = new Color(230, 80, 30);
                    accentColor = new Color(255, 170, 60);
                    break;
                case "ICE":
                    mainColor = new Color(40, 165, 225);
                    accentColor = new Color(180, 235, 255);
                    break;
                case "ELEC":
                    mainColor = new Color(210, 185, 0);
                    accentColor = new Color(255, 245, 80);
                    break;
                case "SONIC":
                    mainColor = new Color(220, 60, 180);
                    accentColor = new Color(255, 150, 220);
                    break;
                case "NANO":
                    mainColor = new Color(60, 220, 190);
                    accentColor = new Color(180, 255, 240);
                    break;
                case "PLASMA":
                    mainColor = new Color(180, 60, 255);
                    accentColor = new Color(220, 160, 255);
                    break;
                case "ACID":
                    mainColor = new Color(140, 230, 40);
                    accentColor = new Color(210, 255, 120);
                    break;
                default:
                    mainColor = new Color(40, 185, 105);
                    accentColor = new Color(180, 255, 200);
                    break;
            }
            // Range circle (dotted)
            g2.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 16));
            g2.fillOval(x - range + 20, y - range + 16, range * 2, range * 2);
            g2.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 45));
            for (int deg = 0; deg < 360; deg += 10) {
                double rad = Math.toRadians(deg);
                int rx = (int) (x + 20 + (range - 5) * Math.cos(rad));
                int ry = (int) (y + 16 + (range - 5) * Math.sin(rad));
                g2.fillRect(rx, ry, PX, PX);
            }
            // Base plate
            g2.setColor(new Color(18, 45, 32));
            g2.fillRect(x - 4, y + 22, 48, 10);
            g2.setColor(new Color(0, 90, 55));
            g2.fillRect(x - 2, y + 24, 44, 6);
            // Tower body (larger)
            drawTowerBody(g2, x, y, mainColor, accentColor);
            // Barrel (rotated)
            int bx2 = x + 20, by2 = y + 16;
            AffineTransform old = g2.getTransform();
            g2.rotate(aimAngle, bx2, by2);
            g2.setColor(accentColor);
            g2.fillRect(bx2, by2 - 3, 24, 6);
            g2.setColor(mainColor);
            g2.fillRect(bx2 + 20, by2 - 2, 8, 4);
            // Barrel tip
            g2.setColor(new Color(255, 255, 255, 120));
            g2.fillRect(bx2 + 26, by2 - 1, 4, 2);
            g2.setTransform(old);
            // Firing beam
            if (target != null && cooldown > cooldownMax - 6) {
                drawPixelBeam(g2, bx2, by2, target.x + 16, target.y + 12, mainColor, accentColor);
            }
        }

        void drawTowerBody(Graphics2D g, int x, int y, Color mc, Color ac) {
            // Shared body structure
            g.setColor(new Color(mc.getRed() / 3, mc.getGreen() / 3, mc.getBlue() / 3));
            g.fillRect(x + 1, y + 1, 38, 28);
            g.setColor(mc);
            g.fillRect(x, y, 38, 28);
            // Type-specific detail
            switch (type) {
                case "NORMAL":
                    g.setColor(new Color(100, 225, 125));
                    g.fillRect(x + 5, y + 5, 20, 16);
                    g.setColor(new Color(60, 100, 80));
                    g.fillRect(x - 5, y + 3, 6, 22);
                    g.setColor(ac);
                    g.fillRect(x - 8, y + 1, 6, 26);
                    drawMedCross(g, x + 18, y - 8, 12, new Color(220, 50, 60));
                    break;
                case "FIRE":
                    g.setColor(new Color(255, 65, 30));
                    g.fillRect(x + 5, y + 5, 14, 18);
                    g.setColor(new Color(255, 150, 80));
                    g.fillRect(x + 8, y + 8, 8, 12);
                    g.setColor(ac);
                    g.setFont(new Font("Monospaced", Font.BOLD, 9));
                    g.drawString("UV", x + 24, y + 18);
                    break;
                case "ICE":
                    g.setColor(ac);
                    g.fillRect(x + 3, y + 7, 24, 5);
                    g.fillRect(x + 3, y + 18, 24, 5);
                    g.setColor(new Color(180, 235, 255));
                    g.fillRect(x + 12, y - 10, 5, PX * 4);
                    g.fillRect(x + 6, y - 6, PX * 4, PX);
                    g.fillRect(x + 18, y - 6, PX * 4, PX);
                    break;
                case "ELEC":
                    g.setColor(new Color(50, 45, 8));
                    for (int fy = y + 3; fy < y + 24; fy += 5) {
                        g.fillRect(x + 3, fy, 28, 2);
                    }
                    g.setColor(ac);
                    g.fillRect(x + 12, y - 10, 8, 10);
                    g.fillRect(x + 15, y - 14, 3, 5);
                    if (animTick % 6 < 3) {
                        g.setColor(new Color(255, 245, 80, 200));
                        g.fillRect(x + 20, y - 12, PX, PX);
                        g.fillRect(x + 9, y - 10, PX, PX);
                    } else {
                        g.setColor(new Color(255, 200, 40, 200));
                        g.fillRect(x + 16, y - 15, PX, PX);
                        g.fillRect(x + 11, y - 8, PX, PX);
                    }
                    break;
                case "SONIC":
                    g.setColor(ac);
                    for (int i = 1; i <= 3; i++) {
                        g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 200 - i * 50));
                        g.drawOval(x + i * 4, y + i * 4, 30 - i * 8, 20 - i * 5);
                    }
                    break;
                case "NANO":
                    g.setColor(ac);
                    for (int i = 0; i < 3; i++) {
                        for (int j = 0; j < 3; j++) {
                            g.fillRect(x + 4 + i * 11, y + 4 + j * 8, 8, 6);
                        }
                    }
                    break;
                case "PLASMA":
                    g.setColor(ac);
                    g.fillOval(x + 5, y + 4, 26, 20);
                    g.setColor(new Color(255, 255, 255, 100));
                    g.fillOval(x + 12, y + 8, 10, 8);
                    break;
                case "ACID":
                    g.setColor(ac);
                    int[][] d = {{0, 1, 0}, {1, 1, 1}, {0, 1, 0}};
                    for (int r = 0; r < d.length; r++) {
                        for (int col = 0; col < d[r].length; col++) {
                            if (d[r][col] == 1) {
                                g.fillRect(x + 4 + col * 11, y + 4 + r * 9, 10, 7);
                            }
                        }
                    }
                    g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 70));
                    g.fillOval(x, y, 36, 26);
                    break;
            }
        }

        void drawPixelBeam(Graphics2D g, int x1, int y1, int x2, int y2, Color mc, Color ac) {
            int steps = 12;
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
