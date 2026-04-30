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

    // Game speed multiplier: 1, 2, 3, 4
    int gameSpeed = 1;

    List<Enemy> enemies = new ArrayList<>();
    List<Tower> towers = new ArrayList<>();
    List<Projectile> projectiles = new ArrayList<>();

    int money = 200;
    int baseHealth = 100;
    int wave = 1;
    static final int MAX_WAVE = 25;
    int score = 0;
    int selectedMap = 0;

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

    // =====================================================================
    // Mapa de Puntos - FIXED serpentine waypoints
    // =====================================================================
    int[][] mapWaypoints;

    void buildMapWaypoints() {
        if (selectedMap == 0) {
            mapWaypoints = null;
        } else {
            // Fixed serpentine path with proper spacing to avoid off-screen issues
            int midLane = PATH_TOP + (PATH_BOT - PATH_TOP) / 2;
            int topLane = PATH_TOP + (int) ((PATH_BOT - PATH_TOP) * 0.18);
            int botLane = PATH_TOP + (int) ((PATH_BOT - PATH_TOP) * 0.82);
            mapWaypoints = new int[][]{
                {-60, midLane},
                {(int) (SW * 0.12), botLane},
                {(int) (SW * 0.28), topLane},
                {(int) (SW * 0.44), botLane},
                {(int) (SW * 0.58), topLane},
                {(int) (SW * 0.72), botLane},
                {(int) (SW * 0.84), midLane},
                {BASE_X + 80, midLane}
            };
        }
    }

    // =====================================================================
    // AUDIO (MIDI) - Improved with map-specific music
    // =====================================================================
    static Synthesizer synth;
    static MidiChannel[] channels;

    static {
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            channels = synth.getChannels();
            channels[0].programChange(80);  // lead melody
            channels[1].programChange(98);  // sfx
            channels[2].programChange(47);  // bass
            channels[3].programChange(11);  // vibraphone / menu
            channels[4].programChange(9);   // percussion channel
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

    // Current music context
    static volatile boolean bgPlaying = false;
    static volatile String currentMusicContext = "";
    static Thread bgThread;

    static void stopBGMusic() {
        bgPlaying = false;
        if (bgThread != null) {
            bgThread.interrupt();
            bgThread = null;
        }
        if (channels != null) {
            for (int c = 0; c < 5; c++) {
                channels[c].allNotesOff();
            }
        }
        currentMusicContext = "";
    }

    static void startMenuMusic() {
        if (bgPlaying && currentMusicContext.equals("menu")) {
            return;
        }
        stopBGMusic();
        currentMusicContext = "menu";
        bgPlaying = true;
        bgThread = new Thread(() -> {
            // Upbeat 8-bit medical theme for menu
            // Melody in C major - cheerful medical jingle
            int[] melody = {
                60, 64, 67, 72, 71, 69, 67, 64,
                65, 69, 72, 77, 76, 74, 72, 69,
                60, 62, 64, 65, 67, 69, 71, 72,
                72, 71, 69, 67, 65, 64, 62, 60,
                64, 67, 71, 74, 72, 71, 69, 67,
                65, 67, 69, 71, 72, 74, 76, 77,
                72, 71, 69, 67, 65, 64, 62, 60
            };
            int[] bass = {36, 43, 36, 43, 37, 44, 37, 44, 38, 45, 38, 45, 36, 43, 36, 43};
            int[] chords = {60, 64, 67, 65, 69, 72, 62, 65, 69, 60, 64, 67};
            int noteDur = 150, bassDur = 300;
            if (channels != null) {
                channels[0].programChange(9);   // Glockenspiel - medical/bright
                channels[2].programChange(33);  // Finger bass
                channels[3].programChange(12);  // Marimba for chords
            }
            int tick = 0;
            while (bgPlaying) {
                try {
                    int i = tick % melody.length;
                    playNote(0, melody[i], 72, noteDur);
                    if (i % 2 == 0) {
                        playNote(2, bass[(i / 2) % bass.length], 55, bassDur);
                    }
                    if (i % 4 == 0) {
                        playNote(3, chords[(i / 4) % chords.length], 45, bassDur * 2);
                    }
                    Thread.sleep(noteDur);
                    tick++;
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                }
            }
        });
        bgThread.setDaemon(true);
        bgThread.start();
    }

    static void startSettingsMusic() {
        if (bgPlaying && currentMusicContext.equals("settings")) {
            return;
        }
        stopBGMusic();
        currentMusicContext = "settings";
        bgPlaying = true;
        bgThread = new Thread(() -> {
            // Calm ambient settings music
            int[] melody = {60, 62, 64, 60, 62, 65, 64, 62, 60, 64, 67, 65, 64, 62, 60, 62};
            int[] pads = {48, 52, 55, 48, 50, 53, 52, 50, 48, 52, 55, 53};
            if (channels != null) {
                channels[0].programChange(89); // Pad warm
                channels[2].programChange(44); // Tremolo strings
            }
            int tick = 0;
            while (bgPlaying) {
                try {
                    int i = tick % melody.length;
                    playNote(0, melody[i] + 12, 55, 350);
                    if (i % 3 == 0) {
                        playNote(2, pads[i % pads.length], 40, 700);
                    }
                    Thread.sleep(350);
                    tick++;
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                }
            }
        });
        bgThread.setDaemon(true);
        bgThread.start();
    }

    static void startScoreboardMusic() {
        if (bgPlaying && currentMusicContext.equals("scoreboard")) {
            return;
        }
        stopBGMusic();
        currentMusicContext = "scoreboard";
        bgPlaying = true;
        bgThread = new Thread(() -> {
            // Victory fanfare loop for scoreboard
            int[] melody = {60, 64, 67, 72, 71, 72, 74, 72, 71, 69, 67, 69, 71, 72, 74, 72};
            int[] bass = {36, 36, 43, 43, 41, 41, 38, 38};
            if (channels != null) {
                channels[0].programChange(14); // Tubular bells
                channels[2].programChange(58); // Tuba
            }
            int tick = 0;
            while (bgPlaying) {
                try {
                    int i = tick % melody.length;
                    playNote(0, melody[i], 70, 200);
                    if (i % 2 == 0) {
                        playNote(2, bass[(i / 2) % bass.length], 55, 400);
                    }
                    Thread.sleep(200);
                    tick++;
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                }
            }
        });
        bgThread.setDaemon(true);
        bgThread.start();
    }

    static void startMap1Music() {
        if (bgPlaying && currentMusicContext.equals("map1")) {
            return;
        }
        stopBGMusic();
        currentMusicContext = "map1";
        bgPlaying = true;
        bgThread = new Thread(() -> {
            // Map 1 - Straight corridor: Steady, determined battle march
            int[] melody = {60, 62, 64, 67, 69, 67, 64, 62, 60, 64, 67, 72, 71, 69, 67, 64, 60, 62, 64, 67, 64, 60, 62, 64};
            int[] bass = {36, 36, 43, 43, 41, 41, 38, 38};
            int[] perc = {35, 0, 35, 38, 35, 0, 38, 35};
            if (channels != null) {
                channels[0].programChange(80);  // Synth lead
                channels[2].programChange(34);  // Electric bass
                channels[4].programChange(0);
            }
            int tick = 0;
            while (bgPlaying) {
                try {
                    int i = tick % melody.length;
                    playNote(0, melody[i], 65, 180);
                    if (i % 3 == 0) {
                        playNote(2, bass[i % bass.length], 50, 360);
                    }
                    if (i % 2 == 0 && perc[i % perc.length] != 0) {
                        playNote(9, perc[i % perc.length], 80, 50); // drum channel
                    }
                    Thread.sleep(180);
                    tick++;
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                }
            }
        });
        bgThread.setDaemon(true);
        bgThread.start();
    }

    static void startMap2Music() {
        if (bgPlaying && currentMusicContext.equals("map2")) {
            return;
        }
        stopBGMusic();
        currentMusicContext = "map2";
        bgPlaying = true;
        bgThread = new Thread(() -> {
            // Map 2 - Serpentine: Tense, winding, chromatic
            int[] melody = {62, 65, 63, 60, 62, 67, 65, 62, 63, 65, 67, 70, 68, 65, 63, 62};
            int[] bass = {38, 38, 43, 43, 37, 37, 41, 41};
            int[] harm = {65, 69, 72, 70, 67, 65, 63, 65};
            if (channels != null) {
                channels[0].programChange(82); // Synth voice
                channels[2].programChange(39); // Synth bass
                channels[3].programChange(91); // Pad
            }
            int tick = 0;
            while (bgPlaying) {
                try {
                    int i = tick % melody.length;
                    playNote(0, melody[i], 70, 200);
                    if (i % 2 == 0) {
                        playNote(2, bass[i % bass.length], 55, 400);
                    }
                    if (i % 4 == 0) {
                        playNote(3, harm[i % harm.length], 38, 800);
                    }
                    Thread.sleep(200);
                    tick++;
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                }
            }
        });
        bgThread.setDaemon(true);
        bgThread.start();
    }

    static void sfxEnemyDeath() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                channels[1].programChange(122);
                channels[1].noteOn(45, 90);
                Thread.sleep(50);
                channels[1].noteOff(45);
                channels[1].noteOn(40, 70);
                Thread.sleep(50);
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
                String ctx = currentMusicContext;
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
                if (ctx.equals("map1")) {
                    startMap1Music();
                } else if (ctx.equals("map2")) {
                    startMap2Music();
                }
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

    static void sfxTowerDestroyed() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                channels[1].programChange(122);
                for (int n : new int[]{55, 52, 48, 44}) {
                    channels[1].noteOn(n, 100);
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
                String ctx = currentMusicContext;
                stopBGMusic();
                channels[0].programChange(80);
                for (int i = 0; i < Math.min(wave, 5); i++) {
                    channels[0].noteOn(60 + i * 4, 100);
                    Thread.sleep(90);
                    channels[0].noteOff(60 + i * 4);
                }
                Thread.sleep(100);
                if (ctx.equals("map1")) {
                    startMap1Music();
                } else if (ctx.equals("map2")) {
                    startMap2Music();
                }
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
    static final Color COL_BG = new Color(6, 10, 16);
    static final Color COL_PATH = new Color(15, 32, 26);
    static final Color COL_PATH_EDGE = new Color(0, 220, 120);
    static final Color COL_GRID = new Color(0, 40, 30);
    static final Color COL_BASE = new Color(0, 220, 120);
    static final Color COL_UI_BG = new Color(4, 8, 14);
    static final Color COL_UI_BORDER = new Color(0, 180, 100);
    static final Color COL_UI_TEXT = new Color(180, 255, 200);
    static final Color COL_MONEY = new Color(255, 215, 60);
    static final Color COL_HEALTH = new Color(220, 50, 60);
    static final Color COL_WAVE = new Color(80, 180, 255);
    static final Color COL_SCORE = new Color(255, 200, 50);

    // Map 2 special colors
    static final Color COL_PATH2 = new Color(12, 25, 40);
    static final Color COL_PATH2_EDGE = new Color(0, 160, 255);

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
    // Dificultades - REBALANCED
    // =====================================================================
    double enemyHealthMult() {
        switch (difficulty) {
            case EASY:
                return 0.6;
            case HARD:
                return 1.6;
            case NIGHTMARE:
                return 2.5;
            default:
                return 1.0;
        }
    }

    double enemySpeedMult() {
        switch (difficulty) {
            case EASY:
                return 0.75;
            case HARD:
                return 1.3;
            case NIGHTMARE:
                return 1.7;
            default:
                return 1.0;
        }
    }

    int startMoney() {
        switch (difficulty) {
            case EASY:
                return 300;
            case HARD:
                return 175;
            case NIGHTMARE:
                return 120;
            default:
                return 220;
        }
    }

    int waveBonus() {
        switch (difficulty) {
            case EASY:
                return 75;
            case HARD:
                return 45;
            case NIGHTMARE:
                return 28;
            default:
                return 55;
        }
    }

    double scoreMult() {
        switch (difficulty) {
            case EASY:
                return 0.6;
            case HARD:
                return 2.2;
            case NIGHTMARE:
                return 4.5;
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
        gameSpeed = 1;
        enemies.clear();
        towers.clear();
        projectiles.clear();
        selectedTower = "NORMAL";
        gameState = GameState.PLAYING;
        prepareWave();
        if (selectedMap == 0) {
            startMap1Music();
        } else {
            startMap2Music();
        }
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
            int startX = -55 - col * 75;
            int startY;
            if (selectedMap == 0) {
                startY = PATH_TOP + 14 + row * ((PATH_BOT - PATH_TOP - 28) / rows);
            } else {
                // Map2: all enemies start on the entry waypoint Y
                int midLane = PATH_TOP + (PATH_BOT - PATH_TOP) / 2;
                startY = midLane - 10 + (row - rows / 2) * 8;
            }

            Enemy e;
            if (selectedMap == 1) {
                // Map 2 exclusive enemies
                e = spawnMap2Enemy(i, startX, startY);
            } else {
                // Map 1 enemies
                e = spawnMap1Enemy(i, startX, startY);
            }
            e.speed = Math.max(1, (int) Math.round(e.speed * enemySpeedMult()));
            if (selectedMap == 1) {
                e.waypointIndex = 0;
            }
            enemies.add(e);
        }
        // Boss rounds
        if (wave % 5 == 0) {
            Enemy boss;
            if (wave == 25) {
                boss = new FinalBoss(-300, PATH_TOP + 30);
            } else if (wave == 20) {
                boss = new ColossusEnemy(-300, PATH_TOP + 30);
            } else {
                boss = new BossEnemy(-250, PATH_TOP + 50);
            }
            boss.maxHealth = (int) (boss.maxHealth * enemyHealthMult());
            boss.health = boss.maxHealth;
            if (selectedMap == 1) {
                boss.waypointIndex = 0;
            }
            enemies.add(boss);
        }
    }

    Enemy spawnMap1Enemy(int i, int sx, int sy) {
        Enemy e;
        if (difficulty == Difficulty.NIGHTMARE && wave >= 8 && i % 6 == 0) {
            e = new MutantEnemy(sx, sy);
        } else if (wave >= 20 && i % 5 == 0) {
            e = new StealthEnemy(sx, sy);
        } else if (wave >= 15 && i % 6 == 0) {
            e = new HealerEnemy(sx, sy);
        } else if (wave >= 10 && i % 7 == 0) {
            e = new TankEnemy(sx, sy);
        } else if (wave >= 7 && i % 5 == 0) {
            e = new SpeedEnemy(sx, sy);
        } else if (wave >= 4 && i % 4 == 0) {
            e = new ShieldEnemy(sx, sy);
        } else {
            e = new Enemy(sx, sy);
            e.maxHealth = (int) ((50 + wave * 12) * enemyHealthMult());
            e.health = e.maxHealth;
        }
        return e;
    }

    Enemy spawnMap2Enemy(int i, int sx, int sy) {
        Enemy e;
        // Map 2 has unique enemy composition
        if (difficulty == Difficulty.NIGHTMARE && wave >= 6 && i % 5 == 0) {
            e = new MutantEnemy(sx, sy);
        } else if (wave >= 5 && i % 7 == 0) {
            e = new TowerDestroyerEnemy(sx, sy); // NEW
        } else if (wave >= 18 && i % 4 == 0) {
            e = new SwarmEnemy(sx, sy);         // NEW
        } else if (wave >= 12 && i % 5 == 0) {
            e = new PhaserEnemy(sx, sy);        // NEW
        } else if (wave >= 8 && i % 6 == 0) {
            e = new ArmoredEnemy(sx, sy);       // NEW
        } else if (wave >= 5 && i % 4 == 0) {
            e = new SpeedEnemy(sx, sy);
        } else if (wave >= 3 && i % 3 == 0) {
            e = new ShieldEnemy(sx, sy);
        } else {
            e = new Enemy(sx, sy);
            e.maxHealth = (int) ((60 + wave * 14) * enemyHealthMult());
            e.health = e.maxHealth;
            e.reward = 25;
        }
        return e;
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
            // Map 1: block the path band with some margin
            if (y > PATH_TOP - 14 && y < PATH_BOT + 14) {
                return false;
            }
        } else {
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
            double len = Math.hypot(x2 - x1, y2 - y1);
            if (len == 0) {
                continue;
            }
            double t = Math.max(0, Math.min(1, ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / (len * len)));
            double px = x1 + t * (x2 - x1), py = y1 + t * (y2 - y1);
            if (Math.hypot(x - px, y - py) < 42) {
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
    // PAINT
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
    // MENU - IMPROVED DESIGN
    // =====================================================================
    void drawMenu(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, SW, SH);
        // Deep scanlines
        for (int y = 0; y < SH; y += 3) {
            g2.setColor(new Color(0, 20, 12, 18));
            g2.fillRect(0, y, SW, 1);
        }
        // Animated grid
        long t = System.currentTimeMillis();
        int gridOff = (int) ((t / 80) % 32);
        g2.setColor(COL_GRID);
        for (int gx = -gridOff; gx < SW; gx += 32) {
            for (int gy = 0; gy < SH; gy += 32) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }

        // Animated DNA/biohazard strip top and bottom
        drawMenuBioStrip(g2, 0, t);
        drawMenuBioStrip(g2, SH - 24, t);

        // Central glowing panel
        int panH = (int) (SH * 0.72);
        int panY = (SH - panH) / 2;
        g2.setColor(new Color(0, 20, 14, 80));
        g2.fillRect(SW / 2 - 220, panY, 440, panH);
        g2.setColor(new Color(0, 180, 100, 35));
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(SW / 2 - 220, panY, 440, panH);
        // Corner pixel decorations
        Color pec = new Color(0, 220, 120);
        drawPixelCorner(g2, SW / 2 - 220, panY, pec, false, false);
        drawPixelCorner(g2, SW / 2 + 220 - 12, panY, pec, true, false);
        drawPixelCorner(g2, SW / 2 - 220, panY + panH - 12, pec, false, true);
        drawPixelCorner(g2, SW / 2 + 220 - 12, panY + panH - 12, pec, true, true);

        // Side viruses (bigger, animated)
        drawMenuVirus(g2, (int) (SW * 0.06), (int) (SH * 0.20), 0, t);
        drawMenuVirus(g2, (int) (SW * 0.84), (int) (SH * 0.18), 1, t);
        drawMenuVirus(g2, (int) (SW * 0.08), (int) (SH * 0.70), 2, t);
        drawMenuVirus(g2, (int) (SW * 0.82), (int) (SH * 0.68), 3, t);
        // Extra smaller ones
        drawMenuVirusSmall(g2, (int) (SW * 0.25), (int) (SH * 0.08), 2, t);
        drawMenuVirusSmall(g2, (int) (SW * 0.65), (int) (SH * 0.88), 0, t);

        // TITLE with multi-layer glow
        String title = "TDX";
        g2.setFont(pixelFont(100));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(title);
        int titleX = SW / 2 - tw / 2, titleY = (int) (SH * 0.24);
        // Shadow layers
        for (int gl = 8; gl >= 1; gl--) {
            int alpha = 8 + gl * 10;
            g2.setColor(new Color(0, 255, 120, Math.min(255, alpha)));
            g2.drawString(title, titleX - gl, titleY + gl);
            g2.drawString(title, titleX + gl, titleY - gl);
        }
        // Dark shadow
        g2.setColor(new Color(0, 40, 20));
        g2.drawString(title, titleX + 6, titleY + 6);
        // Main text
        g2.setColor(COL_PATH_EDGE);
        g2.drawString(title, titleX, titleY);
        // Highlight pixels on top of T
        g2.setColor(new Color(180, 255, 200));
        g2.fillRect(titleX + 2, titleY - 98, 8, 4);

        // Subtitle with pulse
        String sub = "DEFENSA  MEDICA  2048";
        g2.setFont(pixelFont(16));
        FontMetrics fmS = g2.getFontMetrics();
        int subAlpha = (int) (Math.abs(Math.sin(t / 700.0)) * 60) + 160;
        g2.setColor(new Color(100, 200, 140, subAlpha));
        g2.drawString(sub, SW / 2 - fmS.stringWidth(sub) / 2, (int) (SH * 0.24) + 30);

        // Separator line with cross
        g2.setColor(new Color(0, 150, 80, 120));
        g2.fillRect(SW / 2 - 200, (int) (SH * 0.30), 160, 2);
        g2.fillRect(SW / 2 + 40, (int) (SH * 0.30), 160, 2);
        drawMedCross(g2, SW / 2, (int) (SH * 0.295), 14, new Color(0, 200, 100, 180));

        // Buttons
        String[] lbls = {"JUGAR", "AJUSTES", "PUNTAJES", "SALIR"};
        String[] subs2 = {"Seleccionar mapa y combatir", "Dificultad y pantalla", "Ver tabla de records", "Cerrar el juego"};
        int bw = 340, bh = 62;
        int bx0 = SW / 2 - bw / 2;
        int[] byArr = {(int) (SH * 0.34), (int) (SH * 0.46), (int) (SH * 0.58), (int) (SH * 0.70)};
        Color[] btnCols = {new Color(0, 220, 100), new Color(80, 180, 255), new Color(255, 200, 50), new Color(220, 60, 60)};
        String[] icons = {">", "*", "#", "X"};
        for (int i = 0; i < lbls.length; i++) {
            boolean hover = mouseX >= bx0 && mouseX <= bx0 + bw && mouseY >= byArr[i] && mouseY <= byArr[i] + bh;
            // Shadow
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(bx0 + 6, byArr[i] + 6, bw, bh);
            // Fill
            if (hover) {
                g2.setColor(new Color(btnCols[i].getRed(), btnCols[i].getGreen(), btnCols[i].getBlue(), 28));
            } else {
                g2.setColor(new Color(6, 15, 10));
            }
            g2.fillRect(bx0, byArr[i], bw, bh);
            // Left color accent bar
            g2.setColor(hover ? btnCols[i] : new Color(btnCols[i].getRed() / 2, btnCols[i].getGreen() / 2, btnCols[i].getBlue() / 2));
            g2.fillRect(bx0, byArr[i], 5, bh);
            // Border
            g2.setColor(hover ? btnCols[i] : new Color(btnCols[i].getRed() / 3, btnCols[i].getGreen() / 3, btnCols[i].getBlue() / 3));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(bx0, byArr[i], bw, bh);
            // Pixel corners
            g2.setColor(hover ? btnCols[i] : new Color(btnCols[i].getRed() / 2, btnCols[i].getGreen() / 2, btnCols[i].getBlue() / 2));
            g2.fillRect(bx0 - 3, byArr[i] - 3, 7, 7);
            g2.fillRect(bx0 + bw - 4, byArr[i] - 3, 7, 7);
            g2.fillRect(bx0 - 3, byArr[i] + bh - 4, 7, 7);
            g2.fillRect(bx0 + bw - 4, byArr[i] + bh - 4, 7, 7);
            // Icon box
            g2.setColor(hover ? new Color(btnCols[i].getRed(), btnCols[i].getGreen(), btnCols[i].getBlue(), 60) : new Color(10, 22, 16));
            g2.fillRect(bx0 + 10, byArr[i] + 12, 36, 36);
            g2.setColor(hover ? btnCols[i] : new Color(btnCols[i].getRed() / 2, btnCols[i].getGreen() / 2, btnCols[i].getBlue() / 2));
            g2.drawRect(bx0 + 10, byArr[i] + 12, 36, 36);
            g2.setColor(hover ? btnCols[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(18));
            FontMetrics fmI = g2.getFontMetrics();
            g2.drawString(icons[i], bx0 + 10 + (36 - fmI.stringWidth(icons[i])) / 2, byArr[i] + 36);
            // Main label
            g2.setColor(hover ? btnCols[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(22));
            g2.drawString(lbls[i], bx0 + 58, byArr[i] + 30);
            // Sub-label
            g2.setColor(new Color(100, 160, 120));
            g2.setFont(pixelFont(9));
            g2.drawString(subs2[i], bx0 + 58, byArr[i] + 48);
            // Arrow on hover
            if (hover) {
                g2.setColor(btnCols[i]);
                g2.setFont(pixelFont(14));
                g2.drawString(">>", bx0 + bw - 40, byArr[i] + bh / 2 + 5);
            }
        }
        // Bottom info strip
        g2.setColor(new Color(0, 140, 70, 120));
        g2.fillRect(0, SH - 30, SW, 30);
        Color[] dCols = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60), new Color(180, 40, 220)};
        g2.setColor(dCols[difficulty.ordinal()]);
        g2.setFont(pixelFont(10));
        String diffStr = "DIFICULTAD ACTIVA: " + difficulty.name() + "   |   [F11] Fullscreen/Ventana   |   [ESC] Salir";
        g2.drawString(diffStr, SW / 2 - g2.getFontMetrics().stringWidth(diffStr) / 2, SH - 10);
    }

    void drawMenuBioStrip(Graphics2D g2, int y, long t) {
        g2.setColor(new Color(0, 100, 50, 60));
        g2.fillRect(0, y, SW, 20);
        int offset = (int) ((t / 60) % 40);
        for (int x = -offset; x < SW; x += 40) {
            g2.setColor(new Color(0, 200, 100, 40));
            g2.fillRect(x, y + 2, 20, 4);
            drawMedCross(g2, x + 10, y + 10, 8, new Color(0, 180, 100, 60));
        }
    }

    void drawPixelCorner(Graphics2D g2, int x, int y, Color c, boolean flipX, boolean flipY) {
        g2.setColor(c);
        int ox = flipX ? -1 : 1, oy = flipY ? -1 : 1;
        g2.fillRect(x, y, 2, 10 * oy);
        g2.fillRect(x, y, 10 * ox, 2);
        g2.fillRect(x + 2 * ox, y + 2 * oy, 2, 2);
    }

    void drawMenuVirus(Graphics2D g2, int x, int y, int variant, long t) {
        int wobble = (int) (t / 220 + variant * 17) % 3;
        int[][] virus = {{0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 0}};
        Color[] cols = {new Color(200, 30, 30, 140), new Color(160, 20, 180, 140), new Color(30, 140, 60, 140), new Color(200, 120, 20, 140)};
        Color vc = cols[variant % cols.length];
        // Glow behind
        g2.setColor(new Color(vc.getRed(), vc.getGreen(), vc.getBlue(), 30));
        g2.fillOval(x - 8, y - 8, 56, 44);
        g2.setColor(vc);
        for (int r = 0; r < virus.length; r++) {
            for (int c = 0; c < virus[r].length; c++) {
                if (virus[r][c] == 1) {
                    g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                }
            }
        }
        // Spikes
        int cx = x + 16, cy2 = y + 12;
        int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        for (int[] sp : spikes) {
            g2.setColor(new Color(vc.getRed(), vc.getGreen(), vc.getBlue(), 100));
            g2.fillRect(cx + sp[0] * (14 + wobble) - PX / 2, cy2 + sp[1] * (14 + wobble) - PX / 2, PX + 1, PX + 1);
        }
        // Eyes
        g2.setColor(new Color(255, 255, 255, 180));
        g2.fillRect(x + PX, y + PX, PX, PX);
        g2.fillRect(x + PX * 5, y + PX, PX, PX);
    }

    void drawMenuVirusSmall(Graphics2D g2, int x, int y, int variant, long t) {
        int wobble = (int) (t / 300 + variant * 11) % 2;
        Color[] cols = {new Color(200, 30, 30, 80), new Color(160, 20, 180, 80), new Color(30, 140, 60, 80), new Color(200, 120, 20, 80)};
        Color vc = cols[variant % cols.length];
        g2.setColor(vc);
        g2.fillOval(x, y, 18, 14);
        int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] sp : spikes) {
            g2.fillRect(x + 9 + sp[0] * (10 + wobble) - 1, y + 7 + sp[1] * (10 + wobble) - 1, 3, 3);
        }
    }

    // =====================================================================
    // MAP SELECT - IMPROVED
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

        // Header
        g2.setColor(new Color(0, 30, 20, 120));
        g2.fillRect(0, 0, SW, (int) (SH * 0.14));
        g2.setColor(COL_PATH_EDGE);
        g2.setFont(pixelFont(28));
        FontMetrics fm = g2.getFontMetrics();
        String title = "SELECCIONAR MAPA";
        g2.drawString(title, SW / 2 - fm.stringWidth(title) / 2, (int) (SH * 0.10));
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(80, (int) (SH * 0.12), SW - 80, (int) (SH * 0.12));

        String[] mapNames = {"PASILLO RECTO", "SERPENTINA BIOLAB"};
        String[] mapDescs = {"Camino recto clasico.", "Zigzag de alta tensión."};
        String[] mapFlavors = {
            "Coloca torres a ambos lados del corredor.",
            "Ruta compleja. Enemigos especiales."
        };
        Color[] mapColors = {COL_PATH_EDGE, COL_PATH2_EDGE};

        int mw = (int) (SW * 0.36), mh = (int) (SH * 0.54);
        int mx1 = (int) (SW * 0.07), mx2 = (int) (SW * 0.57);
        int my = (int) (SH * 0.16);

        for (int i = 0; i < 2; i++) {
            int bx = i == 0 ? mx1 : mx2;
            boolean hover = mouseX >= bx && mouseX <= bx + mw && mouseY >= my && mouseY <= my + mh;
            boolean sel = selectedMap == i;

            // Card shadow
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(bx + 8, my + 8, mw, mh);

            // Card fill
            g2.setColor(sel ? new Color(mapColors[i].getRed(), mapColors[i].getGreen(), mapColors[i].getBlue(), 18)
                    : new Color(8, 16, 12));
            g2.fillRect(bx, my, mw, mh);

            // Card border
            g2.setColor(sel ? mapColors[i] : (hover ? new Color(mapColors[i].getRed(), mapColors[i].getGreen(), mapColors[i].getBlue(), 160) : new Color(0, 60, 40)));
            g2.setStroke(new BasicStroke(sel ? PX : 2));
            g2.drawRect(bx, my, mw, mh);

            // Top color bar
            g2.setColor(sel ? mapColors[i] : new Color(mapColors[i].getRed() / 3, mapColors[i].getGreen() / 3, mapColors[i].getBlue() / 3));
            g2.fillRect(bx, my, mw, 5);

            // Mini map preview
            drawMiniMap(g2, bx + 16, my + 16, mw - 32, (int) (mh * 0.52), i);

            // Map name
            g2.setColor(sel ? mapColors[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(16));
            FontMetrics fmm = g2.getFontMetrics();
            g2.drawString(mapNames[i], bx + (mw - fmm.stringWidth(mapNames[i])) / 2, my + (int) (mh * 0.63));

            // Desc
            g2.setColor(new Color(130, 190, 150));
            g2.setFont(pixelFont(10));
            fmm = g2.getFontMetrics();
            g2.drawString(mapDescs[i], bx + (mw - fmm.stringWidth(mapDescs[i])) / 2, my + (int) (mh * 0.73));
            g2.setColor(new Color(100, 150, 120));
            g2.drawString(mapFlavors[i], bx + (mw - fmm.stringWidth(mapFlavors[i])) / 2, my + (int) (mh * 0.82));

            if (sel) {
                g2.setColor(mapColors[i]);
                g2.setFont(pixelFont(11));
                fmm = g2.getFontMetrics();
                g2.drawString("[SELECCIONADO]", bx + (mw - fmm.stringWidth("[SELECCIONADO]")) / 2, my + (int) (mh * 0.92));
            }
        }

        // Start button
        int sbW = 300, sbH = 58, sbX = SW / 2 - sbW / 2, sbY = (int) (SH * 0.78);
        boolean sHover = mouseX >= sbX && mouseX <= sbX + sbW && mouseY >= sbY && mouseY <= sbY + sbH;
        g2.setColor(sHover ? new Color(0, 220, 100, 40) : new Color(8, 18, 14));
        g2.fillRect(sbX, sbY, sbW, sbH);
        g2.setColor(sHover ? COL_PATH_EDGE : new Color(0, 150, 80));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(sbX, sbY, sbW, sbH);
        g2.setColor(sHover ? COL_BG : COL_UI_TEXT);
        g2.setFont(pixelFont(20));
        FontMetrics fmB = g2.getFontMetrics();
        g2.drawString(">> INICIAR JUEGO <<", sbX + (sbW - fmB.stringWidth(">> INICIAR JUEGO <<")) / 2, sbY + 37);

        // Back
        int backX = 20, backY = (int) (SH * 0.90), backW = 180, backH = 40;
        boolean backH2 = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        g2.setColor(backH2 ? new Color(0, 180, 100, 30) : new Color(8, 18, 14));
        g2.fillRect(backX, backY, backW, backH);
        g2.setColor(backH2 ? COL_PATH_EDGE : new Color(0, 100, 60));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(backX, backY, backW, backH);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(13));
        g2.drawString("< VOLVER", backX + 28, backY + 26);
    }

    void drawMiniMap(Graphics2D g2, int x, int y, int w, int h, int mapIdx) {
        g2.setColor(new Color(10, 22, 18));
        g2.fillRect(x, y, w, h);
        g2.setColor(new Color(0, 50, 32));
        g2.drawRect(x, y, w, h);
        if (mapIdx == 0) {
            // Straight - cleaner look
            int py = y + h / 2 - 16;
            g2.setColor(new Color(20, 45, 35));
            g2.fillRect(x + 5, py, w - 10, 32);
            g2.setColor(COL_PATH_EDGE);
            g2.fillRect(x + 5, py, w - 10, 3);
            g2.fillRect(x + 5, py + 29, w - 10, 3);
            // Center dashes
            g2.setColor(new Color(0, 80, 50));
            for (int dx = x + 10; dx < x + w - 15; dx += 20) {
                g2.fillRect(dx, py + 14, 10, 3);
            }
            // Tower icons
            Color tc = new Color(0, 200, 100, 140);
            for (int tx = x + 22; tx < x + w - 22; tx += 34) {
                g2.setColor(tc);
                g2.fillRect(tx, py - 14, 12, 12);
                g2.setColor(new Color(0, 220, 120));
                g2.drawRect(tx, py - 14, 12, 12);
                g2.setColor(tc);
                g2.fillRect(tx, py + 34, 12, 12);
                g2.setColor(new Color(0, 220, 120));
                g2.drawRect(tx, py + 34, 12, 12);
            }
        } else {
            // Serpentine - draw zigzag path
            int midy = y + h / 2;
            int topY = y + (int) (h * 0.18);
            int botY = y + (int) (h * 0.82);
            int[] xs2 = {x + 5, (int) (x + w * 0.15), (int) (x + w * 0.30), (int) (x + w * 0.45), (int) (x + w * 0.60), (int) (x + w * 0.75), (int) (x + w * 0.88), x + w - 5};
            int[] ys2 = {midy, botY, topY, botY, topY, botY, midy, midy};
            g2.setStroke(new BasicStroke(14, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(COL_PATH2);
            for (int i = 0; i < xs2.length - 1; i++) {
                g2.drawLine(xs2[i], ys2[i], xs2[i + 1], ys2[i + 1]);
            }
            g2.setStroke(new BasicStroke(2));
            g2.setColor(COL_PATH2_EDGE);
            for (int i = 0; i < xs2.length - 1; i++) {
                g2.drawLine(xs2[i], ys2[i], xs2[i + 1], ys2[i + 1]);
            }
            g2.setStroke(new BasicStroke(PX));
        }
        // Base
        g2.setColor(COL_BASE);
        g2.fillRect(x + w - 18, y + h / 2 - 14, 14, 28);
        drawMedCross(g2, x + w - 11, y + h / 2, 10, COL_HEALTH);
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

        g2.setColor(new Color(0, 30, 20, 120));
        g2.fillRect(0, 0, SW, (int) (SH * 0.14));
        g2.setColor(COL_PATH_EDGE);
        g2.setFont(pixelFont(28));
        FontMetrics fmT = g2.getFontMetrics();
        g2.drawString("AJUSTES", SW / 2 - fmT.stringWidth("AJUSTES") / 2, (int) (SH * 0.09));
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(80, (int) (SH * 0.12), SW - 80, (int) (SH * 0.12));

        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(14));
        String dl = "DIFICULTAD";
        FontMetrics fmL = g2.getFontMetrics();
        g2.drawString(dl, SW / 2 - fmL.stringWidth(dl) / 2, (int) (SH * 0.20));

        String[] descs = {
            "Enemigos debiles y lentos. Ideal para aprender.",
            "Experiencia balanceada. Desafiante pero justo.",
            "Enemigos resistentes y agresivos. Para expertos.",
            "PESADILLA: Enemigos brutales. Solo los mejores."
        };
        String[] diffNames = {"FACIL", "NORMAL", "DIFICIL", "PESADILLA"};
        Color[] diffColors = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60), new Color(180, 40, 220)};
        Difficulty[] diffs = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE};
        int btnW = 165, btnH = 72;
        int totalW = 4 * btnW + 3 * 20;
        int startBX = SW / 2 - totalW / 2;
        int by = (int) (SH * 0.24);
        for (int i = 0; i < 4; i++) {
            int bx = startBX + i * (btnW + 20);
            boolean selected = difficulty == diffs[i];
            boolean hover = mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH;
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRect(bx + 4, by + 4, btnW, btnH);
            if (selected) {
                g2.setColor(new Color(diffColors[i].getRed(), diffColors[i].getGreen(), diffColors[i].getBlue(), 35));
            } else {
                g2.setColor(hover ? new Color(10, 25, 18) : new Color(8, 16, 12));
            }
            g2.fillRect(bx, by, btnW, btnH);
            // Top accent
            g2.setColor(selected ? diffColors[i] : new Color(diffColors[i].getRed() / 3, diffColors[i].getGreen() / 3, diffColors[i].getBlue() / 3));
            g2.fillRect(bx, by, btnW, 4);
            Color borderC = selected ? diffColors[i] : (hover ? new Color(diffColors[i].getRed(), diffColors[i].getGreen(), diffColors[i].getBlue(), 160) : new Color(0, 70, 45));
            g2.setColor(borderC);
            g2.setStroke(new BasicStroke(selected ? PX : 2));
            g2.drawRect(bx, by, btnW, btnH);
            g2.setColor(selected ? diffColors[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(14));
            FontMetrics fmb = g2.getFontMetrics();
            g2.drawString(diffNames[i], bx + (btnW - fmb.stringWidth(diffNames[i])) / 2, by + 28);
            if (selected) {
                g2.setColor(new Color(diffColors[i].getRed(), diffColors[i].getGreen(), diffColors[i].getBlue(), 200));
                g2.setFont(pixelFont(9));
                fmb = g2.getFontMetrics();
                g2.drawString("< ACTIVO >", bx + (btnW - fmb.stringWidth("< ACTIVO >")) / 2, by + 48);
            }
        }
        // Desc box
        int descBoxY = (int) (SH * 0.48), descBoxH = 130;
        g2.setColor(new Color(6, 14, 10));
        g2.fillRect(startBX, descBoxY, totalW, descBoxH);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(startBX, descBoxY, totalW, descBoxH);
        int di = difficulty.ordinal();
        g2.setColor(diffColors[di]);
        g2.setFont(pixelFont(11));
        g2.drawString(descs[di], startBX + 16, descBoxY + 24);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(10));
        String[] lblsS = {"VIDA ENE:", "VEL ENE:", "DINERO INICIO:", "BONUS OLEADA:", "MULT PUNT:"};
        for (int i = 0; i < lblsS.length; i++) {
            g2.drawString(lblsS[i], startBX + 16, descBoxY + 44 + i * 17);
        }
        double[] hM = {0.6, 1.0, 1.6, 2.5}, sM = {0.75, 1.0, 1.3, 1.7};
        int[] mI = {300, 220, 175, 120}, wb = {75, 55, 45, 28};
        double[] pM = {0.6, 1.0, 2.2, 4.5};
        drawSettingBar(g2, startBX + 150, descBoxY + 32, 220, 12, hM[di] / 2.5, diffColors[di]);
        drawSettingBar(g2, startBX + 150, descBoxY + 49, 220, 12, sM[di] / 1.7, diffColors[di]);
        drawSettingBar(g2, startBX + 150, descBoxY + 66, 220, 12, mI[di] / 300.0, diffColors[di]);
        drawSettingBar(g2, startBX + 150, descBoxY + 83, 220, 12, wb[di] / 75.0, diffColors[di]);
        drawSettingBar(g2, startBX + 150, descBoxY + 100, 220, 12, pM[di] / 4.5, diffColors[di]);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(9));
        g2.drawString(String.format("x%.1f", hM[di]), startBX + 380, descBoxY + 44);
        g2.drawString(String.format("x%.2f", sM[di]), startBX + 380, descBoxY + 61);
        g2.drawString("$" + mI[di], startBX + 380, descBoxY + 78);
        g2.drawString("$" + wb[di], startBX + 380, descBoxY + 95);
        g2.drawString("x" + pM[di], startBX + 380, descBoxY + 112);

        // Window toggle
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(13));
        g2.drawString("PANTALLA:", SW / 2 - 210, (int) (SH * 0.75));
        int tbx = SW / 2 - 60, tby = (int) (SH * 0.725) - 18, tbw = 240, tbh = 42;
        boolean thover = mouseX >= tbx && mouseX <= tbx + tbw && mouseY >= tby && mouseY <= tby + tbh;
        g2.setColor(thover ? new Color(0, 200, 100, 30) : new Color(8, 16, 12));
        g2.fillRect(tbx, tby, tbw, tbh);
        g2.setColor(thover ? COL_PATH_EDGE : new Color(0, 120, 70));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(tbx, tby, tbw, tbh);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(12));
        String sizeLabel = isFullScreen ? ">> PANTALLA COMPLETA <<" : ">> MODO VENTANA <<";
        FontMetrics fmtl = g2.getFontMetrics();
        g2.drawString(sizeLabel, tbx + (tbw - fmtl.stringWidth(sizeLabel)) / 2, tby + 27);

        // Back
        int backX = SW / 2 - 110, backY = (int) (SH * 0.87), backW = 220, backH = 48;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        g2.setColor(backHover ? new Color(0, 180, 100, 30) : new Color(8, 16, 12));
        g2.fillRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_PATH_EDGE : new Color(0, 100, 60));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_BG : COL_UI_TEXT);
        g2.setFont(pixelFont(16));
        g2.drawString("< VOLVER", backX + 62, backY + 31);
    }

    void drawSettingBar(Graphics2D g2, int x, int y, int w, int h, double ratio, Color c) {
        g2.setColor(new Color(8, 20, 14));
        g2.fillRect(x, y, w, h);
        g2.setColor(c);
        g2.fillRect(x, y, (int) (w * Math.min(1, ratio)), h);
        g2.setColor(COL_UI_BORDER);
        g2.drawRect(x, y, w, h);
    }

    // =====================================================================
    // SCORE ENTRY
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

        int nameBoxW = 320, nameBoxH = 70, nameBoxX = SW / 2 - nameBoxW / 2, nameBoxY = (int) (SH * 0.46);
        g2.setColor(new Color(8, 16, 12));
        g2.fillRect(nameBoxX, nameBoxY, nameBoxW, nameBoxH);
        g2.setColor(COL_PATH_EDGE);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(nameBoxX, nameBoxY, nameBoxW, nameBoxH);

        long t = System.currentTimeMillis();
        boolean showCursor = (t / 500) % 2 == 0;
        g2.setFont(pixelFont(36));
        fm = g2.getFontMetrics();
        int charW = fm.stringWidth("X"), totalCharW = charW * 5 + 16 * 4;
        int startCharX = SW / 2 - totalCharW / 2;
        for (int i = 0; i < 5; i++) {
            char ch = i < currentName.length() ? currentName.charAt(i) : (i == currentName.length() && showCursor ? '_' : '-');
            boolean isActive = i == currentName.length();
            g2.setColor(isActive && showCursor ? COL_PATH_EDGE : (i < currentName.length() ? new Color(0, 255, 150) : new Color(0, 70, 45)));
            g2.drawString(String.valueOf(ch), startCharX + i * (charW + 16), nameBoxY + 48);
            g2.setColor(new Color(0, 90, 55));
            g2.fillRect(startCharX + i * (charW + 16), nameBoxY + 54, charW, 3);
        }
        if (currentName.length() == 5) {
            int cbW = 260, cbH = 52, cbX = SW / 2 - cbW / 2, cbY = (int) (SH * 0.63);
            boolean chover = mouseX >= cbX && mouseX <= cbX + cbW && mouseY >= cbY && mouseY <= cbY + cbH;
            g2.setColor(chover ? new Color(0, 200, 100, 40) : new Color(8, 16, 12));
            g2.fillRect(cbX, cbY, cbW, cbH);
            g2.setColor(chover ? COL_PATH_EDGE : new Color(0, 140, 80));
            g2.setStroke(new BasicStroke(PX));
            g2.drawRect(cbX, cbY, cbW, cbH);
            g2.setColor(chover ? COL_BG : COL_UI_TEXT);
            g2.setFont(pixelFont(18));
            fm = g2.getFontMetrics();
            g2.drawString("CONFIRMAR [ENTER]", cbX + (cbW - fm.stringWidth("CONFIRMAR [ENTER]")) / 2, cbY + 34);
        }
        g2.setColor(new Color(100, 150, 120));
        g2.setFont(pixelFont(10));
        g2.drawString("Solo letras y numeros. Backspace para borrar.", SW / 2 - 160, (int) (SH * 0.80));
    }

    // =====================================================================
    // SCOREBOARD
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

        drawStar(g2, SW / 2 - 170, (int) (SH * 0.04), COL_SCORE);
        drawStar(g2, SW / 2 + 148, (int) (SH * 0.04), COL_SCORE);
        g2.setColor(COL_SCORE);
        g2.setFont(pixelFont(32));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("TABLA DE PUNTAJES", SW / 2 - fm.stringWidth("TABLA DE PUNTAJES") / 2, (int) (SH * 0.11));
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(80, (int) (SH * 0.13), SW - 160, PX);

        g2.setColor(new Color(0, 180, 100));
        g2.setFont(pixelFont(12));
        int ry = (int) (SH * 0.19);
        g2.drawString("#", 120, ry);
        g2.drawString("NOMBRE", 180, ry);
        g2.drawString("PUNTAJE", SW / 2 - 40, ry);
        g2.drawString("DIFIC", SW / 2 + 140, ry);
        g2.fillRect(80, ry + 5, SW - 160, 2);

        String[] dNames = {"FACIL", "NORMAL", "DIFICIL", "PESADILLA"};
        Color[] dCols = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60), new Color(180, 40, 220)};
        for (int i = 0; i < scoreCount; i++) {
            int rowY = ry + 28 + i * 32;
            g2.setColor(i % 2 == 0 ? new Color(8, 20, 14) : new Color(5, 12, 8));
            g2.fillRect(80, rowY - 18, SW - 160, 28);
            Color rankColor = i == 0 ? new Color(255, 200, 50) : i == 1 ? new Color(200, 200, 200) : i == 2 ? new Color(200, 120, 60) : new Color(100, 150, 120);
            g2.setColor(rankColor);
            g2.setFont(pixelFont(13));
            g2.drawString((i + 1) + ".", 120, rowY);
            g2.setColor(COL_UI_TEXT);
            g2.drawString(scoreNames[i], 180, rowY);
            g2.setColor(COL_SCORE);
            g2.drawString(String.valueOf(scoreValues[i]), SW / 2 - 40, rowY);
            int d = scoreDifficulty[i];
            if (d >= 0 && d < dNames.length) {
                g2.setColor(dCols[d]);
                g2.setFont(pixelFont(10));
                g2.drawString(dNames[d], SW / 2 + 140, rowY);
            }
        }
        if (scoreCount == 0) {
            g2.setColor(new Color(60, 100, 80));
            g2.setFont(pixelFont(13));
            g2.drawString("Sin puntajes aun. Juega y gana!", SW / 2 - 160, (int) (SH * 0.5));
        }
        int backX = SW / 2 - 110, backY = (int) (SH * 0.88), backW = 220, backH = 48;
        boolean backHover = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        g2.setColor(backHover ? new Color(0, 180, 100, 30) : new Color(8, 16, 12));
        g2.fillRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_PATH_EDGE : new Color(0, 100, 60));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(backX, backY, backW, backH);
        g2.setColor(backHover ? COL_BG : COL_UI_TEXT);
        g2.setFont(pixelFont(16));
        g2.drawString("< MENU", backX + 70, backY + 31);
    }

    // =====================================================================
    // GAME SCREEN
    // =====================================================================
    void drawGame(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, SW, SH);

        // Background grid (map-colored)
        if (selectedMap == 0) {
            g2.setColor(COL_GRID);
        } else {
            g2.setColor(new Color(0, 20, 40));
        }
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

        for (Enemy e : enemies) {
            e.draw(g2);
        }
        for (Tower t : towers) {
            t.draw(g2);
        }
        for (Projectile p : projectiles) {
            p.draw(g2);
        }

        // Tower placement hover
        if (!gameOver && !gameWon) {
            boolean valid = isValidTowerPosition(mouseX, mouseY) && !towerExistsAt(mouseX, mouseY);
            g2.setColor(valid ? new Color(0, 200, 100, 50) : new Color(200, 50, 50, 50));
            g2.fillRect(mouseX - TOWER_SIZE / 2, mouseY - TOWER_SIZE / 2, TOWER_SIZE, TOWER_SIZE);
            g2.setColor(valid ? new Color(0, 200, 100, 160) : new Color(200, 50, 50, 160));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(mouseX - TOWER_SIZE / 2, mouseY - TOWER_SIZE / 2, TOWER_SIZE, TOWER_SIZE);
            // Show range preview
            if (valid) {
                int previewRange = (int) (SW * 0.14);
                g2.setColor(new Color(0, 200, 100, 12));
                g2.fillOval(mouseX - previewRange, mouseY - previewRange, previewRange * 2, previewRange * 2);
                g2.setColor(new Color(0, 200, 100, 40));
                g2.drawOval(mouseX - previewRange, mouseY - previewRange, previewRange * 2, previewRange * 2);
            }
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

    // =====================================================================
    // MAP DRAWING
    // =====================================================================
    void drawMapStraight(Graphics2D g2) {
        // Side zones
        g2.setColor(new Color(0, 55, 28, 12));
        g2.fillRect(0, UI_TOP_H, BASE_X, PATH_TOP - UI_TOP_H);
        g2.fillRect(0, PATH_BOT, BASE_X, UI_BOT_Y - PATH_BOT);

        // Path glow halo
        g2.setColor(new Color(0, 35, 20));
        g2.fillRect(0, PATH_TOP - 4, SW, PATH_BOT - PATH_TOP + 8);

        // Main path
        g2.setColor(COL_PATH);
        g2.fillRect(0, PATH_TOP, SW, PATH_BOT - PATH_TOP);

        // Stripe texture
        for (int sx = 0; sx < SW; sx += 28) {
            g2.setColor(new Color(0, 48, 30));
            g2.fillRect(sx, PATH_TOP, 14, PATH_BOT - PATH_TOP);
        }
        // Lane marks
        int midPath = (PATH_TOP + PATH_BOT) / 2;
        g2.setColor(new Color(0, 75, 45));
        for (int px2 = 0; px2 < SW; px2 += PX * 6) {
            g2.fillRect(px2, midPath - PX / 2, PX * 3, PX);
        }

        // Edge dashes
        g2.setColor(COL_PATH_EDGE);
        for (int px2 = 0; px2 < SW; px2 += PX * 4) {
            g2.fillRect(px2, PATH_TOP, PX * 2, PX);
            g2.fillRect(px2, PATH_BOT - PX, PX * 2, PX);
        }
        // Edge glow
        g2.setColor(new Color(0, 220, 120, 18));
        g2.fillRect(0, PATH_TOP - 2, SW, 5);
        g2.fillRect(0, PATH_BOT - 3, SW, 5);

        drawBase(g2);
    }

    void drawMapSerpentine(Graphics2D g2) {
        if (mapWaypoints == null) {
            buildMapWaypoints();
        }
        // Dark sci-fi background for map 2
        g2.setColor(new Color(6, 12, 24));
        g2.fillRect(0, UI_TOP_H, SW, UI_BOT_Y - UI_TOP_H);

        // Hex grid overlay for map 2
        g2.setColor(new Color(0, 40, 80, 40));
        for (int gx = 0; gx < SW; gx += 24) {
            for (int gy = UI_TOP_H; gy < UI_BOT_Y; gy += 20) {
                g2.drawRect(gx, gy, 18, 16);
            }
        }

        int pathW = 58;
        // Outer glow
        g2.setStroke(new BasicStroke(pathW + 16, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(0, 60, 120, 45));
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            g2.drawLine(mapWaypoints[i][0], mapWaypoints[i][1], mapWaypoints[i + 1][0], mapWaypoints[i + 1][1]);
        }

        // Main path
        g2.setStroke(new BasicStroke(pathW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(COL_PATH2);
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            g2.drawLine(mapWaypoints[i][0], mapWaypoints[i][1], mapWaypoints[i + 1][0], mapWaypoints[i + 1][1]);
        }

        // Path texture stripes
        g2.setStroke(new BasicStroke(pathW - 16, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(10, 20, 38));
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            g2.drawLine(mapWaypoints[i][0], mapWaypoints[i][1], mapWaypoints[i + 1][0], mapWaypoints[i + 1][1]);
        }

        // Edge lines
        g2.setStroke(new BasicStroke(2));
        g2.setColor(COL_PATH2_EDGE);
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            int dx = mapWaypoints[i + 1][0] - mapWaypoints[i][0], dy = mapWaypoints[i + 1][1] - mapWaypoints[i][1];
            double len = Math.hypot(dx, dy);
            if (len == 0) {
                continue;
            }
            int nx = (int) (-dy / len * pathW / 2), ny = (int) (dx / len * pathW / 2);
            g2.drawLine(mapWaypoints[i][0] + nx, mapWaypoints[i][1] + ny, mapWaypoints[i + 1][0] + nx, mapWaypoints[i + 1][1] + ny);
            g2.drawLine(mapWaypoints[i][0] - nx, mapWaypoints[i][1] - ny, mapWaypoints[i + 1][0] - nx, mapWaypoints[i + 1][1] - ny);
        }

        // Waypoint nodes
        g2.setStroke(new BasicStroke(PX));
        for (int[] wp : mapWaypoints) {
            g2.setColor(new Color(0, 100, 200, 80));
            g2.fillOval(wp[0] - 10, wp[1] - 10, 20, 20);
            g2.setColor(COL_PATH2_EDGE);
            g2.drawOval(wp[0] - 10, wp[1] - 10, 20, 20);
            g2.fillRect(wp[0] - 2, wp[1] - 2, 4, 4);
        }

        // Direction arrows on path
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            int mx2 = (mapWaypoints[i][0] + mapWaypoints[i + 1][0]) / 2;
            int my2 = (mapWaypoints[i][1] + mapWaypoints[i + 1][1]) / 2;
            double angle = Math.atan2(mapWaypoints[i + 1][1] - mapWaypoints[i][1], mapWaypoints[i + 1][0] - mapWaypoints[i][0]);
            g2.setColor(new Color(0, 160, 255, 60));
            int ax = (int) (mx2 + Math.cos(angle) * 8), ay = (int) (my2 + Math.sin(angle) * 8);
            int bx2 = (int) (mx2 + Math.cos(angle + 2.5) * 5), by2 = (int) (my2 + Math.sin(angle + 2.5) * 5);
            int cx2 = (int) (mx2 + Math.cos(angle - 2.5) * 5), cy2 = (int) (my2 + Math.sin(angle - 2.5) * 5);
            g2.fillPolygon(new int[]{ax, bx2, cx2}, new int[]{ay, by2, cy2}, 3);
        }

        g2.setStroke(new BasicStroke(PX));
        drawBase(g2);
    }

    void drawBase(Graphics2D g2) {
        int midPath = (PATH_TOP + PATH_BOT) / 2;
        int baseW = 55, baseH = PATH_BOT - PATH_TOP;
        g2.setColor(new Color(10, 26, 22));
        g2.fillRect(BASE_X - 8, PATH_TOP + 2, baseW + 16, baseH - 4);
        g2.setColor(new Color(0, 180, 100, 25));
        g2.fillRect(BASE_X - 14, PATH_TOP - 10, baseW + 28, baseH + 20);
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

    // =====================================================================
    // TOP UI - with game speed button
    // =====================================================================
    void drawTopUI(Graphics2D g2) {
        g2.setColor(COL_UI_BG);
        g2.fillRect(0, 0, SW, UI_TOP_H);
        g2.setColor(new Color(0, 200, 110, 70));
        g2.fillRect(0, 0, SW, 4);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(0, UI_TOP_H, SW, UI_TOP_H);

        int topY = UI_TOP_H / 2 + 5;
        drawPixelHeart(g2, 10, UI_TOP_H / 2 - 9, COL_HEALTH);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(14));
        g2.drawString("" + baseHealth, 36, topY);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(85, 5, PX, UI_TOP_H - 10);
        drawPixelCoin(g2, 95, UI_TOP_H / 2 - 10);
        g2.setColor(COL_MONEY);
        g2.setFont(pixelFont(14));
        g2.drawString("$" + money, 120, topY);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(205, 5, PX, UI_TOP_H - 10);
        drawPixelBio(g2, 215, UI_TOP_H / 2 - 10);
        g2.setColor(COL_WAVE);
        g2.setFont(pixelFont(14));
        g2.drawString("OL " + wave + "/" + MAX_WAVE, 240, topY);
        g2.setColor(COL_UI_BORDER);
        g2.fillRect(340, 5, PX, UI_TOP_H - 10);
        drawStar(g2, 350, UI_TOP_H / 2 - 10, COL_SCORE);
        g2.setColor(COL_SCORE);
        g2.setFont(pixelFont(13));
        g2.drawString("" + score, 374, topY);
        Color[] dCols2 = {new Color(60, 200, 80), new Color(80, 180, 255), new Color(220, 60, 60), new Color(180, 40, 220)};
        String[] dNames2 = {"FAC", "NOR", "DIF", "PES"};
        g2.setColor(dCols2[difficulty.ordinal()]);
        g2.setFont(pixelFont(9));
        g2.drawString("[" + dNames2[difficulty.ordinal()] + "]", 460, topY);
        g2.setColor(selectedMap == 0 ? COL_PATH_EDGE : COL_PATH2_EDGE);
        g2.setFont(pixelFont(8));
        g2.drawString(selectedMap == 0 ? "[M1]" : "[M2]", 510, topY);

        // Progress bar
        int progX = 540, progW = SW - 540 - 380, progH = 16;
        g2.setColor(new Color(6, 20, 12));
        g2.fillRect(progX, UI_TOP_H / 2 - progH / 2, progW, progH);
        for (int seg = 0; seg < MAX_WAVE; seg++) {
            g2.setColor(seg < wave - 1 ? new Color(0, 150, 85) : seg == wave - 1 ? new Color(0, 220, 110) : new Color(12, 30, 20));
            g2.fillRect(progX + 2 + seg * (progW - 4) / MAX_WAVE, UI_TOP_H / 2 - progH / 2 + 2, (progW - 4) / MAX_WAVE - 1, progH - 4);
        }
        g2.setColor(COL_UI_BORDER);
        g2.drawRect(progX, UI_TOP_H / 2 - progH / 2, progW, progH);
        g2.setColor(new Color(0, 200, 100));
        g2.setFont(pixelFont(7));
        g2.drawString("PROGRESO", progX + progW / 2 - 24, UI_TOP_H / 2 + 3);

        // Speed button
        drawSpeedButton(g2);
        drawPlayPauseButton(g2);
        drawMenuButton(g2);
    }

    void drawSpeedButton(Graphics2D g2) {
        int bw = 96, bh = 26;
        int bx = SW - bw - 238, by = UI_TOP_H / 2 - bh / 2;
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        Color[] speedCols = {COL_UI_TEXT, new Color(100, 220, 100), new Color(255, 200, 50), new Color(255, 80, 80)};
        Color sc = speedCols[gameSpeed - 1];
        g2.setColor(hover ? new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), 40) : new Color(8, 18, 12));
        g2.fillRect(bx, by, bw, bh);
        g2.setColor(sc);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(bx, by, bw, bh);
        g2.setFont(pixelFont(10));
        FontMetrics fm = g2.getFontMetrics();
        String spLabel = "x" + gameSpeed + " VEL";
        g2.drawString(spLabel, bx + (bw - fm.stringWidth(spLabel)) / 2, by + bh - 6);
    }

    void drawPlayPauseButton(Graphics2D g2) {
        int bw = 106, bh = 26;
        int bx = SW - bw - 126, by = UI_TOP_H / 2 - bh / 2;
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        boolean active = waitingToStart || paused;
        if (active) {
            long t = System.currentTimeMillis();
            int glow = (int) (Math.abs(Math.sin(t / 400.0)) * 40) + 15;
            g2.setColor(new Color(0, 220, 100, glow));
            g2.fillRect(bx - 4, by - 4, bw + 8, bh + 8);
        }
        g2.setColor(hover ? new Color(0, 200, 100, 45) : new Color(8, 18, 12));
        g2.fillRect(bx, by, bw, bh);
        g2.setColor(hover ? COL_PATH_EDGE : (active ? new Color(0, 200, 80) : new Color(0, 120, 70)));
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
        int bw = 106, bh = 26, bx = SW - bw - 10, by = UI_TOP_H / 2 - bh / 2;
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
        g2.setColor(hover ? new Color(200, 100, 50, 45) : new Color(8, 18, 12));
        g2.fillRect(bx, by, bw, bh);
        g2.setColor(hover ? new Color(255, 150, 80) : new Color(160, 70, 25));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(bx, by, bw, bh);
        g2.setColor(hover ? COL_BG : new Color(255, 160, 100));
        g2.setFont(pixelFont(10));
        g2.drawString("< MENU", bx + 14, by + bh - 6);
    }

    int speedBtnX() {
        return SW - 96 - 238;
    }

    int ppBtnX() {
        return SW - 106 - 126;
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

    // =====================================================================
    // BOTTOM UI - with tower descriptions
    // =====================================================================
    void drawBottomUI(Graphics2D g2) {
        g2.setColor(COL_UI_BG);
        g2.fillRect(0, UI_BOT_Y, SW, UI_BOT_H);
        g2.setColor(new Color(0, 200, 110, 70));
        g2.fillRect(0, UI_BOT_Y, SW, 4);
        g2.setColor(COL_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(0, UI_BOT_Y, SW, UI_BOT_Y);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(9));
        g2.drawString("[ DEFENSAS ]  —  Click en zona valida para colocar", 10, UI_BOT_Y + 15);

        String[] types = {"NORMAL", "FIRE", "ICE", "ELEC", "SONIC", "NANO", "PLASMA", "ACID"};
        int[] costs = {50, 70, 60, 80, 90, 110, 130, 100};
        String[] labels = {"JERINGA", "IBUPROFEN", "ANALGESIC", "AMOXICILN", "ULTRASONC", "NANOTECN", "PLASMA RX", "ACIDO"};
        String[] keys = {"[1]", "[2]", "[3]", "[4]", "[5]", "[6]", "[7]", "[8]"};
        String[] descs = {
            "Balanceado. Daño normal.",
            "Quema AoE. Daño alto.",
            "Ralentiza enemi.",
            "Cadena elec 2-3.",
            "Onda lenta masiva.",
            "Disparo rapido.",
            "AoE explosivo.",
            "Reduce vida max."
        };
        Color[] colors = {
            new Color(180, 255, 200), new Color(255, 120, 60), new Color(80, 200, 255),
            new Color(255, 230, 60), new Color(255, 80, 200), new Color(80, 255, 200),
            new Color(200, 80, 255), new Color(180, 255, 80)
        };
        int btnW = (SW - 20) / 8 - 6, btnH = UI_BOT_H - 22;
        for (int i = 0; i < types.length; i++) {
            boolean sel = selectedTower.equals(types[i]);
            int bx = 10 + i * (btnW + 6), by2 = UI_BOT_Y + 18, bh = btnH;
            boolean btnHover = mouseX >= bx && mouseX <= bx + btnW && mouseY >= by2 && mouseY <= by2 + bh;
            if (sel) {
                g2.setColor(new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 25));
                g2.fillRect(bx, by2, btnW, bh);
                g2.setColor(colors[i]);
                g2.setStroke(new BasicStroke(PX));
                g2.drawRect(bx, by2, btnW, bh);
                // Corner pixels
                g2.fillRect(bx - PX, by2 - PX, PX * 2, PX * 2);
                g2.fillRect(bx + btnW - PX, by2 - PX, PX * 2, PX * 2);
                g2.fillRect(bx - PX, by2 + bh - PX, PX * 2, PX * 2);
                g2.fillRect(bx + btnW - PX, by2 + bh - PX, PX * 2, PX * 2);
                // Top accent bar
                g2.fillRect(bx, by2, btnW, 3);
            } else {
                g2.setColor(btnHover ? new Color(20, 40, 28) : new Color(14, 28, 20));
                g2.fillRect(bx, by2, btnW, bh);
                g2.setColor(btnHover ? new Color(colors[i].getRed() / 2, colors[i].getGreen() / 2, colors[i].getBlue() / 2) : new Color(0, 55, 36));
                g2.setStroke(new BasicStroke(btnHover ? PX : 1));
                g2.drawRect(bx, by2, btnW, bh);
            }
            // Icon
            drawTowerIcon(g2, bx + 4, by2 + 6, types[i], colors[i], 34);
            // Name
            g2.setColor(sel ? colors[i] : COL_UI_TEXT);
            g2.setFont(pixelFont(8));
            g2.drawString(labels[i], bx + 42, by2 + 16);
            // Key
            g2.setColor(new Color(70, 120, 90));
            g2.setFont(pixelFont(7));
            g2.drawString(keys[i], bx + 42, by2 + 27);
            // Cost
            g2.setColor(COL_MONEY);
            g2.setFont(pixelFont(9));
            g2.drawString("$" + costs[i], bx + 42, by2 + 40);
            // Description
            g2.setColor(new Color(120, 180, 140, 220));
            g2.setFont(pixelFont(7));
            g2.drawString(descs[i], bx + 4, by2 + bh - 6);

            if (money < costs[i]) {
                g2.setColor(new Color(140, 30, 30, 130));
                g2.fillRect(bx, by2, btnW, bh);
                g2.setColor(new Color(200, 60, 60));
                g2.setFont(pixelFont(7));
                g2.drawString("SIN $", bx + btnW / 2 - 16, by2 + bh - 8);
            }
        }
    }

    void drawWaitingToStart(Graphics2D g2) {
        int barY = (PATH_TOP + PATH_BOT) / 2 - 55;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, barY, SW, 110);
        long t = System.currentTimeMillis();
        int alpha = (int) (Math.abs(Math.sin(t / 600.0)) * 80) + 140;
        g2.setColor(new Color(0, 220, 100, alpha));
        g2.setFont(pixelFont(24));
        String msg = ">>> Presiona JUGAR para Oleada " + wave + " <<<";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, SW / 2 - fm.stringWidth(msg) / 2, barY + 46);
        g2.setColor(new Color(180, 255, 200, 160));
        g2.setFont(pixelFont(10));
        String sub = "(Coloca torres ahora)";
        fm = g2.getFontMetrics();
        g2.drawString(sub, SW / 2 - fm.stringWidth(sub) / 2, barY + 70);
    }

    void drawPauseOverlay(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, UI_TOP_H, SW, UI_BOT_Y - UI_TOP_H);
        g2.setColor(new Color(0, 220, 100));
        g2.setFont(pixelFont(40));
        String msg = "-- PAUSA --";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, SW / 2 - fm.stringWidth(msg) / 2, SH / 2 - 12);
        g2.setColor(new Color(180, 255, 200, 200));
        g2.setFont(pixelFont(13));
        String sub = "Click JUGAR o ESPACIO para continuar";
        fm = g2.getFontMetrics();
        g2.drawString(sub, SW / 2 - fm.stringWidth(sub) / 2, SH / 2 + 28);
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
        g2.drawString(sc, SW / 2 - fm.stringWidth(sc) / 2, SH / 2 - 28);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(14));
        String msg2 = won ? "Sobreviviste 25 oleadas  |  Vida: " + baseHealth : "Base destruida en oleada " + wave;
        fm = g2.getFontMetrics();
        g2.drawString(msg2, SW / 2 - fm.stringWidth(msg2) / 2, SH / 2 + 8);
        drawEndButton(g2, SW / 2 - 160, SH / 2 + 38, 320, 55, "INGRESAR PUNTAJE");
        drawEndButton2(g2, SW / 2 - 160, SH / 2 + 105, 320, 50, "VOLVER AL MENU");
    }

    void drawEndButton(Graphics2D g2, int x, int y, int w, int h, String label) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g2.setColor(hover ? new Color(0, 200, 100, 40) : new Color(8, 18, 12));
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
        g2.setColor(hover ? new Color(200, 80, 30, 40) : new Color(8, 18, 12));
        g2.fillRect(x, y, w, h);
        g2.setColor(hover ? new Color(255, 150, 80) : new Color(180, 80, 30));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(x, y, w, h);
        g2.setColor(hover ? COL_BG : new Color(255, 160, 100));
        g2.setFont(pixelFont(14));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h / 2 + 5);
    }

    // =====================================================================
    // TOWER ICONS
    // =====================================================================
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
        g.fillRect(x + 3, y + s / 3, s - 6, 8);
        g.fillRect(x + s - 4, y + s / 3 + 2, 5, 4);
        g.setColor(new Color(80, 200, 140));
        g.fillRect(x + 6, y + s / 3 + 2, s - 14, 4);
        g.setColor(new Color(200, 255, 220));
        g.fillRect(x + 3, y + s / 3, 3, 3);
    }

    void drawIconFire(Graphics2D g, int x, int y, Color c, int s) {
        g.setColor(new Color(200, 60, 0));
        g.fillRect(x + s / 4, y, s / 2, s);
        g.setColor(c);
        g.fillRect(x + s / 4 + 2, y + 3, s / 2 - 4, s - 6);
        g.setColor(new Color(255, 220, 60));
        g.fillRect(x + s / 3, y + s / 4, s / 3, s / 2);
    }

    void drawIconCryo(Graphics2D g, int x, int y, Color c, int s) {
        drawMedCross(g, x + s / 2, y + s / 2, s - 2, c);
        g.setColor(new Color(200, 240, 255));
        g.fillRect(x + s / 2 - 2, y + s / 2 - 2, 5, 5);
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
        g.setColor(new Color(255, 255, 200));
        g.fillRect(x + s / 4, y, 3, 6);
    }

    void drawIconSonic(Graphics2D g, int x, int y, Color c, int s) {
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200 - i * 45));
            g.drawOval(x + i * 4, y + i * 4, s - i * 8, s - i * 8);
        }
        g.setColor(c);
        g.fillOval(x + s / 2 - 3, y + s / 2 - 3, 7, 7);
    }

    void drawIconNano(Graphics2D g, int x, int y, Color c, int s) {
        g.setColor(c);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                g.fillRect(x + i * (s / 3) + 2, y + j * (s / 3) + 2, s / 3 - 2, s / 3 - 2);
            }
        }
        g.setColor(new Color(255, 255, 255, 100));
        g.fillRect(x + 2, y + 2, s / 3 - 2, s / 3 - 2);
    }

    void drawIconPlasma(Graphics2D g, int x, int y, Color c, int s) {
        g.setColor(c);
        g.fillOval(x + 2, y + 2, s - 4, s - 4);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 80));
        g.fillOval(x, y, s, s);
        g.setColor(new Color(255, 255, 255, 150));
        g.fillOval(x + s / 4, y + s / 4, s / 4, s / 4);
    }

    void drawIconAcid(Graphics2D g, int x, int y, Color c, int s) {
        int[][] d = {{0, 1, 0}, {1, 1, 1}, {0, 1, 0}};
        g.setColor(c);
        for (int r = 0; r < d.length; r++) {
            for (int col = 0; col < d[r].length; col++) {
                if (d[r][col] == 1) {
                    g.fillRect(x + col * (s / 3), y + r * (s / 3), s / 3, s / 3);
                }
            }
        }
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 80));
        g.fillOval(x, y, s, s);
    }

    // =====================================================================
    // GAME LOOP - Fixed with gameSpeed
    // =====================================================================
    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState != GameState.PLAYING || gameOver || gameWon || paused || waitingToStart) {
            repaint();
            return;
        }
        // Run multiple ticks per frame based on speed
        for (int tick = 0; tick < gameSpeed; tick++) {
            gameTick();
            if (gameOver || gameWon) {
                break;
            }
        }
        repaint();
    }

    void gameTick() {
        Iterator<Projectile> pit = projectiles.iterator();
        while (pit.hasNext()) {
            Projectile p = pit.next();
            p.move();
            if (p.done) {
                pit.remove();
            }
        }

        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy en = it.next();
            en.move();
            if (en.x > BASE_X + 12) {
                baseHealth -= (en instanceof FinalBoss ? 50 : en instanceof ColossusEnemy ? 35 : en instanceof TankEnemy || en instanceof ArmoredEnemy ? 20 : en instanceof BossEnemy ? 30 : 10);
                it.remove();
                if (baseHealth <= 0) {
                    baseHealth = 0;
                    gameOver = true;
                    sfxGameOver();
                    return;
                }
            }
        }

        // Healer tick
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

        // Tower destroyer tick
        for (Enemy en : enemies) {
            if (en instanceof TowerDestroyerEnemy) {
                TowerDestroyerEnemy td = (TowerDestroyerEnemy) en;
                td.destroyTick++;
                if (td.destroyTick > 90) {
                    td.destroyTick = 0;
                    Tower closest = null;
                    double bestD = Double.MAX_VALUE;
                    for (Tower tw : towers) {
                        double d = Math.hypot(td.x - tw.x, td.y - tw.y);
                        if (d < 120 && d < bestD) {
                            bestD = d;
                            closest = tw;
                        }
                    }
                    if (closest != null) {
                        towers.remove(closest);
                        sfxTowerDestroyed();
                    }
                }
            }
        }

        for (Tower tw : towers) {
            tw.update(enemies, projectiles);
        }

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
                sfxVictory();
            } else {
                wave++;
                money += waveBonus();
                prepareWave();
                sfxNewWave(wave);
                waitingToStart = true;
            }
        }
    }

    // =====================================================================
    // MOUSE
    // =====================================================================
    @Override
    public void mouseClicked(MouseEvent e) {
        int mx = e.getX(), my = e.getY();

        if (gameState == GameState.MENU) {
            int bw = 340, bh = 62, bx0 = SW / 2 - bw / 2;
            int[] byArr = {(int) (SH * 0.34), (int) (SH * 0.46), (int) (SH * 0.58), (int) (SH * 0.70)};
            if (mx >= bx0 && mx <= bx0 + bw && my >= byArr[0] && my <= byArr[0] + bh) {
                sfxMenuClick();
                gameState = GameState.MAP_SELECT;
            } else if (mx >= bx0 && mx <= bx0 + bw && my >= byArr[1] && my <= byArr[1] + bh) {
                sfxMenuClick();
                gameState = GameState.SETTINGS;
                startSettingsMusic();
            } else if (mx >= bx0 && mx <= bx0 + bw && my >= byArr[2] && my <= byArr[2] + bh) {
                sfxMenuClick();
                gameState = GameState.SCOREBOARD;
                startScoreboardMusic();
            } else if (mx >= bx0 && mx <= bx0 + bw && my >= byArr[3] && my <= byArr[3] + bh) {
                stopBGMusic();
                System.exit(0);
            }
        } else if (gameState == GameState.MAP_SELECT) {
            int mw = (int) (SW * 0.36), mh = (int) (SH * 0.54), mx1 = (int) (SW * 0.07), mx2 = (int) (SW * 0.57), myB = (int) (SH * 0.16);
            if (mx >= mx1 && mx <= mx1 + mw && my >= myB && my <= myB + mh) {
                sfxMenuClick();
                selectedMap = 0;
            } else if (mx >= mx2 && mx <= mx2 + mw && my >= myB && my <= myB + mh) {
                sfxMenuClick();
                selectedMap = 1;
            }
            int sbW = 300, sbH = 58, sbX = SW / 2 - sbW / 2, sbY = (int) (SH * 0.78);
            if (mx >= sbX && mx <= sbX + sbW && my >= sbY && my <= sbY + sbH) {
                sfxMenuClick();
                startGame();
            }
            int backX = 20, backY = (int) (SH * 0.90), backW = 180, backH = 40;
            if (mx >= backX && mx <= backX + backW && my >= backY && my <= backY + backH) {
                sfxMenuClick();
                gameState = GameState.MENU;
            }
        } else if (gameState == GameState.SETTINGS) {
            Difficulty[] diffs = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE};
            int btnW = 165, btnH = 72, totalW = 4 * btnW + 60, startBX = SW / 2 - totalW / 2, by = (int) (SH * 0.24);
            for (int i = 0; i < 4; i++) {
                int bx = startBX + i * (btnW + 20);
                if (mx >= bx && mx <= bx + btnW && my >= by && my <= by + btnH) {
                    sfxMenuClick();
                    difficulty = diffs[i];
                }
            }
            int tbx = SW / 2 - 60, tby = (int) (SH * 0.725) - 18, tbw = 240, tbh = 42;
            if (mx >= tbx && mx <= tbx + tbw && my >= tby && my <= tby + tbh) {
                sfxMenuClick();
                toggleWindowMode();
            }
            int backX = SW / 2 - 110, backY = (int) (SH * 0.87), backW = 220, backH = 48;
            if (mx >= backX && mx <= backX + backW && my >= backY && my <= backY + backH) {
                sfxMenuClick();
                gameState = GameState.MENU;
                startMenuMusic();
            }
        } else if (gameState == GameState.SCOREBOARD) {
            int backX = SW / 2 - 110, backY = (int) (SH * 0.88), backW = 220, backH = 48;
            if (mx >= backX && mx <= backX + backW && my >= backY && my <= backY + backH) {
                sfxMenuClick();
                gameState = GameState.MENU;
                startMenuMusic();
            }
        } else if (gameState == GameState.SCORE_ENTRY) {
            if (currentName.length() == 5) {
                int cbW = 260, cbH = 52, cbX = SW / 2 - cbW / 2, cbY = (int) (SH * 0.63);
                if (mx >= cbX && mx <= cbX + cbW && my >= cbY && my <= cbY + cbH) {
                    confirmScore();
                }
            }
        } else if (gameState == GameState.PLAYING) {
            if (gameOver || gameWon) {
                if (mx >= SW / 2 - 160 && mx <= SW / 2 + 160 && my >= SH / 2 + 38 && my <= SH / 2 + 93) {
                    currentName = "";
                    gameState = GameState.SCORE_ENTRY;
                    return;
                }
                if (mx >= SW / 2 - 160 && mx <= SW / 2 + 160 && my >= SH / 2 + 105 && my <= SH / 2 + 155) {
                    stopBGMusic();
                    gameState = GameState.MENU;
                    startMenuMusic();
                    return;
                }
                return;
            }
            // Menu button
            int mbx = menuBtnX(), mby = menuBtnY(), mbw = 106, mbh = 26;
            if (mx >= mbx && mx <= mbx + mbw && my >= mby && my <= mby + mbh) {
                sfxMenuClick();
                stopBGMusic();
                gameState = GameState.MENU;
                startMenuMusic();
                return;
            }
            // Play/pause button
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
            // Speed button
            int sbx = speedBtnX(), sby = ppBtnY(), sbw = 96, sbh = 26;
            if (mx >= sbx && mx <= sbx + sbw && my >= sby && my <= sby + sbh) {
                gameSpeed = (gameSpeed % 4) + 1;
                return;
            }
            // Tower buttons
            String[] types = {"NORMAL", "FIRE", "ICE", "ELEC", "SONIC", "NANO", "PLASMA", "ACID"};
            int[] costs = {50, 70, 60, 80, 90, 110, 130, 100};
            int btnW = (SW - 20) / 8 - 6, btnH = UI_BOT_H - 22;
            for (int i = 0; i < types.length; i++) {
                int bx = 10 + i * (btnW + 6), by2 = UI_BOT_Y + 18;
                if (mx >= bx && mx <= bx + btnW && my >= by2 && my <= by2 + btnH) {
                    selectedTower = types[i];
                    return;
                }
            }
            // Place tower
            java.util.Map<String, Integer> costMap = new java.util.HashMap<>();
            for (int i = 0; i < types.length; i++) {
                costMap.put(types[i], costs[i]);
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
        startScoreboardMusic();
    }

    // =====================================================================
    // WINDOW TOGGLE - FIXED
    // =====================================================================
    static JFrame mainFrame;
    static TDX gameInstance;

    void toggleWindowMode() {
        if (isFullScreen) {
            // Switch to windowed
            gd.setFullScreenWindow(null);
            mainFrame.dispose();
            mainFrame.setUndecorated(false);
            mainFrame.setSize(1280, 720);
            mainFrame.setLocationRelativeTo(null);
            mainFrame.setVisible(true);
            isFullScreen = false;
        } else {
            // Switch to fullscreen
            mainFrame.dispose();
            mainFrame.setUndecorated(true);
            mainFrame.setVisible(true);
            if (gd.isFullScreenSupported()) {
                gd.setFullScreenWindow(mainFrame);
            } else {
                mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
            isFullScreen = true;
        }
        mainFrame.requestFocusInWindow();
        gameInstance.requestFocusInWindow();
    }

    public void setKeyBindings() {
        String[] types = {"NORMAL", "FIRE", "ICE", "ELEC", "SONIC", "NANO", "PLASMA", "ACID"};
        String[] keys2 = {"1", "2", "3", "4", "5", "6", "7", "8"};
        for (int i = 0; i < types.length; i++) {
            final String t = types[i];
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keys2[i]), t);
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
        // Speed shortcut
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "speed");
        getActionMap().put("speed", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (gameState == GameState.PLAYING && !gameOver && !gameWon && !paused) {
                    gameSpeed = (gameSpeed % 4) + 1;
                }
            }
        });
    }

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
            } else if (currentName.length() < 5 && Character.isLetterOrDigit(c)) {
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
        mainFrame.getContentPane().setBackground(new Color(6, 10, 16));
        gameInstance = new TDX();
        gameInstance.setKeyBindings();
        mainFrame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                gameInstance.addKeyTyped(e);
            }
        });
        mainFrame.add(gameInstance);
        mainFrame.setUndecorated(true);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (gd.isFullScreenSupported()) {
            gd.setFullScreenWindow(mainFrame);
        } else {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            SW = screen.width;
            SH = screen.height;
            mainFrame.setPreferredSize(screen);
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
        // Start menu music
        startMenuMusic();
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

        double x, y, tx, ty, speed = 11;
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
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 100));
            g.fillOval((int) x - 7, (int) y - 7, 14, 14);
            g.setColor(col);
            g.fillOval((int) x - 4, (int) y - 4, 8, 8);
            g.setColor(new Color(255, 255, 255, 180));
            g.fillRect((int) x - 1, (int) y - 1, 3, 3);
        }
    }

    // =====================================================================
    // ENEMY BASE - IMPROVED ANIMATIONS
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
            // FIXED: Use a generous threshold and integer-safe movement
            if (dist <= speed + 2) {
                x = tx;
                y = ty2;  // snap to waypoint
                waypointIndex++;
                return;
            }
            double ratio = speed / dist;
            x += (int) Math.round(dx * ratio);
            y += (int) Math.round(dy * ratio);
        }

        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 6) % 2;
            // Shadow
            g2.setColor(new Color(0, 0, 0, 80));
            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < 8; c++) {
                    if (virusShape[r][c] == 1) {
                        g2.fillRect(x + c * PX + 3, y + r * PX + 3, PX, PX);
                    }
                }
            }
            // Body
            g2.setColor(new Color(210, 40, 40));
            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < 8; c++) {
                    if (virusShape[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Sheen
            g2.setColor(new Color(255, 120, 120));
            g2.fillRect(x + PX, y, PX * 2, PX);
            g2.fillRect(x, y + PX, PX, PX);
            // Spikes with wobble animation
            int cx2 = x + 16, cy2 = y + 12;
            int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
            g2.setColor(new Color(230, 60, 60));
            for (int[] sp : spikes) {
                g2.fillRect(cx2 + sp[0] * (12 + wobble) - PX / 2, cy2 + sp[1] * (12 + wobble) - PX / 2, PX, PX);
            }
            // Eyes
            g2.setColor(new Color(255, 220, 220));
            g2.fillRect(x + PX * 2, y + PX, PX, PX);
            g2.fillRect(x + PX * 5, y + PX, PX, PX);
            // Health bar (only when damaged)
            if (health < maxHealth) {
                drawHealthBar(g2, x - 2, y - 8, 36, 4, (double) health / maxHealth, new Color(200, 60, 60), new Color(20, 5, 5));
            }
        }
    }

    static final int[][] virusShape = {{0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 0}};

    // =====================================================================
    // MAP 1 ENEMIES
    // =====================================================================
    class SpeedEnemy extends Enemy {

        SpeedEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((35 + wave * 6) * enemyHealthMult());
            health = maxHealth;
            speed = 5;
            reward = 38;
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
            // Motion blur trail
            for (int i = 1; i <= 5; i++) {
                g2.setColor(new Color(255, 200, 50, 80 - i * 14));
                g2.fillRect(x - i * 8 - wobble, y + PX, PX * 3, PX * 2);
            }
            g2.setColor(Color.WHITE);
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 3, y + PX, PX, PX);
            g2.setColor(new Color(255, 140, 0));
            g2.setFont(pixelFont(8));
            g2.drawString(">>", x, y - 7);
            if (health < maxHealth) {
                drawHealthBar(g2, x - 2, y - 12, 32, 4, (double) health / maxHealth, new Color(255, 160, 0), new Color(20, 10, 0));
            }
        }
    }

    class ShieldEnemy extends Enemy {

        int shieldHP = 60;

        ShieldEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((90 + wave * 18) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = 50;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int[][] body = {{0, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 0}};
            g2.setColor(new Color(30, 140, 70));
            for (int r = 0; r < body.length; r++) {
                for (int c = 0; c < body[r].length; c++) {
                    if (body[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Sheen
            g2.setColor(new Color(100, 220, 140));
            g2.fillRect(x + PX, y, PX, PX);
            g2.fillRect(x, y + PX, PX, PX);
            if (shieldHP > 0) {
                int sAlpha = 120 + (int) (shieldHP / 60.0 * 100);
                g2.setColor(new Color(100, 200, 255, Math.min(255, sAlpha)));
                g2.fillRect(x + PX * 5, y - PX, PX * 2, PX * 7);
                g2.setColor(new Color(200, 240, 255));
                g2.fillRect(x + PX * 5, y, PX, PX * 5);
                // Shield glow
                g2.setColor(new Color(100, 200, 255, 40));
                g2.fillOval(x - 4, y - 4, 32, 28);
            }
            g2.setColor(new Color(255, 240, 50));
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 4, y + PX, PX, PX);
            if (health < maxHealth) {
                drawHealthBar(g2, x - 2, y - 8, 36, 4, (double) health / maxHealth, new Color(30, 200, 80), new Color(5, 15, 5));
            }
        }
    }

    class TankEnemy extends Enemy {

        TankEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((350 + wave * 40) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = 100;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 10) % 2;
            g2.setColor(new Color(70, 70, 95));
            g2.fillRect(x, y, PX * 12, PX * 10);
            // Armor plates
            g2.setColor(new Color(100, 100, 130));
            g2.fillRect(x + PX, y + PX, PX * 10, PX * 2);
            g2.fillRect(x + PX, y + PX * 7, PX * 10, PX * 2);
            g2.setColor(new Color(50, 50, 70));
            g2.fillRect(x, y + PX * 8, PX * 4, PX * 3);
            g2.fillRect(x + PX * 8, y + PX * 8, PX * 4, PX * 3);
            // Treads animation
            g2.setColor(new Color(30, 30, 45));
            for (int tx = 0; tx < 4; tx++) {
                g2.fillRect(x + tx * PX * 3 + wobble, y + PX * 9, PX * 2, PX * 2);
            }
            // Turret
            g2.setColor(new Color(55, 55, 80));
            g2.fillRect(x + PX * 3, y - PX * 2, PX * 6, PX * 4);
            g2.setColor(new Color(70, 70, 100));
            g2.fillRect(x + PX * 9, y + PX * 4, PX * 5, PX * 2);
            // Eyes
            g2.setColor(new Color(255, 55, 55));
            g2.fillRect(x + PX * 3, y - PX, PX * 2, PX * 2);
            g2.fillRect(x + PX * 7, y - PX, PX * 2, PX * 2);
            g2.setColor(new Color(200, 80, 80));
            g2.setFont(pixelFont(7));
            g2.drawString("TANK", x + 8, y - 18);
            drawHealthBar(g2, x - 2, y - 14, 52, 4, (double) health / maxHealth, new Color(100, 100, 200), new Color(8, 8, 20));
        }
    }

    class BossEnemy extends Enemy {

        BossEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((220 + wave * 25) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = 170;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 8) % 2;
            int[][] boss = {{0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            // Pulse glow
            long t = System.currentTimeMillis();
            int glowA = (int) (Math.abs(Math.sin(t / 500.0)) * 50) + 15;
            g2.setColor(new Color(140, 30, 165, glowA));
            g2.fillOval(x - 8, y - 8, 64, 52);
            // Shadow
            g2.setColor(new Color(0, 0, 0, 100));
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
            // Core
            int[][] core = {{0, 1, 1, 0}, {1, 1, 1, 1}, {1, 1, 1, 1}, {0, 1, 1, 0}};
            g2.setColor(new Color(200, 60, 220));
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
            int cx2 = x + 24, cy2 = y + 18;
            g2.setColor(new Color(220, 60, 220, 150));
            for (int[] sp : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
                g2.fillRect(cx2 + sp[0] * (16 + wobble) - 2, cy2 + sp[1] * (16 + wobble) - 2, 4, 4);
            }
            g2.setColor(new Color(220, 60, 220));
            g2.setFont(pixelFont(8));
            g2.drawString("JEFE", x + 14, y - 22);
            drawHealthBar(g2, x - 4, y - 16, 56, 4, (double) health / maxHealth, new Color(180, 40, 200), new Color(15, 5, 20));
        }
    }

    class StealthEnemy extends Enemy {

        int visibleTick = 0;
        boolean visible = false;

        StealthEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((65 + wave * 9) * enemyHealthMult());
            health = maxHealth;
            speed = 3;
            reward = 65;
        }

        @Override
        void move() {
            super.move();
            visibleTick++;
            if (visibleTick > 45) {
                visibleTick = 0;
                visible = !visible;
            }
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int alpha = visible ? 210 : 50;
            g2.setColor(new Color(60, 60, 200, alpha));
            for (int r = 0; r < virusShape.length; r++) {
                for (int c = 0; c < virusShape[r].length; c++) {
                    if (virusShape[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            if (visible) {
                g2.setColor(new Color(180, 180, 255, alpha));
                g2.setFont(pixelFont(8));
                g2.drawString("???", x + 4, y - 8);
                if (health < maxHealth) {
                    drawHealthBar(g2, x - 2, y - 12, 36, 4, (double) health / maxHealth, new Color(80, 80, 220), new Color(5, 5, 20));
                }
            }
        }
    }

    class HealerEnemy extends Enemy {

        int healTick = 0;

        HealerEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((90 + wave * 12) * enemyHealthMult());
            health = maxHealth;
            speed = 2;
            reward = 85;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int pulse = (int) (Math.abs(Math.sin(animTick / 12.0)) * 20);
            int[][] healer = {{0, 1, 1, 1, 0}, {1, 1, 1, 1, 1}, {1, 1, 1, 1, 1}, {1, 1, 1, 1, 1}, {0, 1, 1, 1, 0}};
            g2.setColor(new Color(60, 200, 90));
            for (int r = 0; r < healer.length; r++) {
                for (int c = 0; c < healer[r].length; c++) {
                    if (healer[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            drawMedCross(g2, x + 10, y + 10, 14, new Color(255, 80, 80));
            // Heal aura pulse
            g2.setColor(new Color(60, 220, 100, 30 + pulse));
            g2.drawOval(x - 16, y - 14, 64, 50);
            g2.setColor(new Color(120, 255, 150));
            g2.setFont(pixelFont(9));
            g2.drawString("+", x + 4, y - 7);
            if (health < maxHealth) {
                drawHealthBar(g2, x - 2, y - 12, 32, 4, (double) health / maxHealth, new Color(60, 200, 90), new Color(5, 15, 5));
            }
        }
    }

    class MutantEnemy extends Enemy {

        int mutantPhase = 0;

        MutantEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((450 + wave * 45) * enemyHealthMult());
            health = maxHealth;
            speed = 2;
            reward = 220;
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
            // Outer glow
            long t = System.currentTimeMillis();
            int glowA = (int) (Math.abs(Math.sin(t / 300.0)) * 60) + 20;
            g2.setColor(new Color(baseCol.getRed(), baseCol.getGreen(), baseCol.getBlue(), glowA));
            g2.fillOval(x - 6, y - 6, 44, 36);
            g2.setColor(baseCol);
            for (int r = 0; r < mut.length; r++) {
                for (int c = 0; c < mut[r].length; c++) {
                    if (mut[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Rotating spikes
            g2.setColor(mutantPhase == 1 ? new Color(255, 180, 0) : new Color(180, 80, 255));
            int cx2 = x + 14, cy2 = y + 12;
            for (int deg = 0; deg < 360; deg += 45) {
                double rad = Math.toRadians(deg + (animTick * 3));
                g2.fillRect(cx2 + (int) (Math.cos(rad) * (14 + wobble)) - 1, cy2 + (int) (Math.sin(rad) * (14 + wobble)) - 1, 3, 3);
            }
            g2.setColor(Color.WHITE);
            g2.fillRect(x + PX * 2, y + PX, PX, PX);
            g2.fillRect(x + PX * 5, y + PX, PX, PX);
            g2.setColor(mutantPhase == 1 ? new Color(255, 140, 0) : new Color(200, 100, 255));
            g2.setFont(pixelFont(8));
            g2.drawString(mutantPhase == 1 ? "BERSERK!" : "MUTANTE", x - 4, y - 20);
            drawHealthBar(g2, x - 2, y - 14, 36, 4, (double) health / maxHealth, mutantPhase == 1 ? new Color(255, 140, 0) : new Color(180, 40, 220), new Color(10, 5, 15));
        }
    }

    class ColossusEnemy extends Enemy {

        ColossusEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) (2800 * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = 450;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 8) % 2;
            int[][] f = {{0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            long t = System.currentTimeMillis();
            int glowA = (int) (Math.abs(Math.sin(t / 400.0)) * 50) + 15;
            g2.setColor(new Color(50, 120, 180, glowA));
            g2.fillOval(x - 10, y - 10, 76, 58);
            g2.setColor(new Color(0, 0, 0, 120));
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
            g2.setColor(new Color(80, 170, 230));
            g2.fillRect(x + PX * 2, y + PX * 2, PX * 4, PX * 4);
            g2.fillRect(x + PX * 8, y + PX * 2, PX * 4, PX * 4);
            g2.setColor(new Color(255, 200, 50));
            g2.fillRect(x + PX * 4, y + PX * 3, PX * 2, PX * 2);
            g2.fillRect(x + PX * 8, y + PX * 3, PX * 2, PX * 2);
            g2.setColor(Color.BLACK);
            g2.fillRect(x + PX * 5, y + PX * 4, PX, PX);
            g2.fillRect(x + PX * 9, y + PX * 4, PX, PX);
            g2.setColor(new Color(100, 200, 255));
            g2.setFont(pixelFont(9));
            g2.drawString("!! COLOSO !!", x - 4, y - 22);
            drawHealthBar(g2, x - 4, y - 16, 64, 5, (double) health / maxHealth, new Color(60, 150, 255), new Color(8, 8, 28));
        }
    }

    class FinalBoss extends Enemy {

        FinalBoss(int x, int y) {
            super(x, y);
            maxHealth = 2200;
            health = 2200;
            speed = 1;
            reward = 650;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 4) % 3;
            int[][] f = {{0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            long t = System.currentTimeMillis();
            int glowA = (int) (Math.abs(Math.sin(t / 250.0)) * 70) + 25;
            g2.setColor(new Color(255, 60, 0, glowA));
            g2.fillOval(x - 16, y - 16, 100, 75);
            g2.setColor(new Color(0, 0, 0, 120));
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
            // Crown
            g2.setColor(new Color(255, 180, 0));
            for (int i = 0; i < 5; i++) {
                g2.fillRect(x + PX * 2 + i * PX * 3, y - PX * (4 + i % 2), PX * 2, PX * (4 + i % 2));
            }
            // Animated crown gems
            g2.setColor(new Color(255, 80, 80, (int) (Math.abs(Math.sin(t / 200.0)) * 200) + 55));
            g2.fillOval(x + PX * 4, y - PX * 4 - 2, 6, 6);
            g2.setColor(new Color(255, 60, 0));
            g2.setFont(pixelFont(10));
            g2.drawString("!! JEFE FINAL !!", x - 10, y - 26);
            drawHealthBar(g2, x - 8, y - 18, 88, 6, (double) health / maxHealth, new Color(255, 80, 0), new Color(30, 4, 4));
        }
    }

    // =====================================================================
    // MAP 2 EXCLUSIVE ENEMIES - NEW
    // =====================================================================
    class ArmoredEnemy extends Enemy {

        int armorHP = 120;

        ArmoredEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((250 + wave * 30) * enemyHealthMult());
            health = maxHealth;
            speed = 2;
            reward = 120;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int[][] body = {{0, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 0}};
            g2.setColor(new Color(140, 140, 180));
            for (int r = 0; r < body.length; r++) {
                for (int c = 0; c < body[r].length; c++) {
                    if (body[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Armor plates
            if (armorHP > 0) {
                g2.setColor(new Color(180, 180, 220));
                g2.fillRect(x + PX, y + PX, PX * 4, PX * 3);
                g2.setColor(new Color(100, 100, 140));
                g2.drawRect(x + PX, y + PX, PX * 4, PX * 3);
            }
            g2.setColor(new Color(200, 200, 255));
            g2.fillRect(x + PX, y, PX * 2, PX);
            g2.setColor(new Color(255, 100, 100));
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 4, y + PX, PX, PX);
            g2.setColor(new Color(180, 180, 255));
            g2.setFont(pixelFont(7));
            g2.drawString("ARM", x + 3, y - 7);
            if (health < maxHealth) {
                drawHealthBar(g2, x - 2, y - 12, 32, 4, (double) health / maxHealth, new Color(180, 180, 255), new Color(10, 10, 20));
            }
        }
    }

    class PhaserEnemy extends Enemy {

        int phaseTick = 0;
        boolean phased = false;

        PhaserEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((120 + wave * 15) * enemyHealthMult());
            health = maxHealth;
            speed = 4;
            reward = 100;
        }

        @Override
        void move() {
            super.move();
            phaseTick++;
            if (phaseTick > 60) {
                phaseTick = 0;
                phased = !phased;
            }
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int alpha = phased ? 30 : 190;
            long t = System.currentTimeMillis();
            int shimmer = (int) (Math.abs(Math.sin(t / 200.0)) * 40) + alpha;
            g2.setColor(new Color(120, 60, 220, Math.min(255, shimmer)));
            for (int r = 0; r < virusShape.length; r++) {
                for (int c = 0; c < virusShape[r].length; c++) {
                    if (virusShape[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            if (!phased) {
                g2.setColor(new Color(200, 160, 255, 180));
                g2.setFont(pixelFont(7));
                g2.drawString("PHZ", x + 4, y - 7);
                if (health < maxHealth) {
                    drawHealthBar(g2, x - 2, y - 12, 36, 4, (double) health / maxHealth, new Color(180, 100, 255), new Color(10, 5, 20));
                }
            }
        }
    }

    class SwarmEnemy extends Enemy {

        SwarmEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((20 + wave * 3) * enemyHealthMult());
            health = maxHealth;
            speed = 6;
            reward = 15;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 2) % 3;
            // Tiny fast virus
            int[][] tiny = {{0, 1, 1, 0}, {1, 1, 1, 1}, {1, 1, 1, 1}, {0, 1, 1, 0}};
            g2.setColor(new Color(220, 100, 20));
            for (int r = 0; r < tiny.length; r++) {
                for (int c = 0; c < tiny[r].length; c++) {
                    if (tiny[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Mini trail
            for (int i = 1; i <= 3; i++) {
                g2.setColor(new Color(255, 140, 0, 60 - i * 15));
            }
            // Eyes
            g2.setColor(Color.WHITE);
            g2.fillRect(x + PX, y, PX, PX);
            g2.fillRect(x + PX * 2, y, PX, PX);
        }
    }

    // TOWER DESTROYER ENEMY - NEW (Map 2, wave 5+)
    class TowerDestroyerEnemy extends Enemy {

        int destroyTick = 0;

        TowerDestroyerEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((180 + wave * 20) * enemyHealthMult());
            health = maxHealth;
            speed = 2;
            reward = 150;
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wobble = (animTick / 5) % 2;
            // Distinctive red-black body
            int[][] body = {{0, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 0}};
            long t = System.currentTimeMillis();
            int flashA = (int) (Math.abs(Math.sin(t / 300.0)) * 60) + 120;
            g2.setColor(new Color(200, 20, 20, flashA));
            for (int r = 0; r < body.length; r++) {
                for (int c = 0; c < body[r].length; c++) {
                    if (body[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Wrenches / destruction icon
            g2.setColor(new Color(40, 40, 40));
            g2.fillRect(x + PX * 2, y + PX * 2, PX * 3, PX * 2);
            g2.setColor(new Color(255, 200, 50));
            g2.fillRect(x + PX * 2, y + PX, PX, PX * 4);
            g2.fillRect(x + PX * 4, y + PX, PX, PX * 4);
            // Eyes - x marks (angry)
            g2.setColor(new Color(255, 255, 0));
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 2, y + PX * 2, PX, PX);
            g2.fillRect(x + PX * 4, y + PX, PX, PX);
            g2.fillRect(x + PX * 5, y + PX * 2, PX, PX);
            // Range indicator when near tower
            boolean nearTower = false;
            for (Tower tw : towers) {
                if (Math.hypot(x - tw.x, y - tw.y) < 130) {
                    nearTower = true;
                    break;
                }
            }
            if (nearTower) {
                g2.setColor(new Color(255, 50, 50, 60));
                g2.fillOval(x - 20, y - 18, 68, 52);
                g2.setColor(new Color(255, 50, 50, 120));
                g2.drawOval(x - 20, y - 18, 68, 52);
            }
            g2.setColor(new Color(255, 60, 60));
            g2.setFont(pixelFont(7));
            g2.drawString("DESTRUCTOR", x - 10, y - 20);
            drawHealthBar(g2, x - 2, y - 14, 40, 4, (double) health / maxHealth, new Color(255, 50, 50), new Color(20, 5, 5));
        }
    }

    // =====================================================================
    // TOWER - IMPROVED DESIGN
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
                // Phased enemies can't be targeted unless visible
                if (e instanceof PhaserEnemy && ((PhaserEnemy) e).phased) {
                    continue;
                }
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
                            30;
                        case "ICE" ->
                            8;
                        case "ELEC" ->
                            24;
                        case "SONIC" ->
                            13;
                        case "NANO" ->
                            38;
                        case "PLASMA" ->
                            44;
                        case "ACID" ->
                            20;
                        default ->
                            17;
                    };
                    damage += wave * 2;
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
                    }
                    // ArmoredEnemy: hit armor first
                    if (target instanceof ArmoredEnemy) {
                        ArmoredEnemy ae = (ArmoredEnemy) target;
                        if (ae.armorHP > 0) {
                            ae.armorHP -= damage;
                            if (ae.armorHP < 0) {
                                target.health += ae.armorHP;
                                ae.armorHP = 0;
                            }
                        } else {
                            target.health -= damage;
                        }
                    } else if (target instanceof ShieldEnemy) {
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
                    } else if (target instanceof StealthEnemy) {
                        StealthEnemy st = (StealthEnemy) target;
                        st.visible = true;
                        st.visibleTick = 0;
                        target.health -= damage;
                    } else if (target instanceof PhaserEnemy) {
                        PhaserEnemy ph = (PhaserEnemy) target;
                        if (!ph.phased) {
                            target.health -= damage;
                        }
                    } else {
                        target.health -= damage;
                    }
                    // Chain/AoE effects
                    if (type.equals("ELEC")) {
                        List<Enemy> nearby = new ArrayList<>(enemies);
                        nearby.remove(target);
                        for (Enemy e : nearby) {
                            if (Math.hypot(target.x - e.x, target.y - e.y) < 90) {
                                e.health -= damage / 2;
                            }
                        }
                    }
                    if (type.equals("PLASMA")) {
                        for (Enemy e : enemies) {
                            if (e != target && Math.hypot(target.x - e.x, target.y - e.y) < 70) {
                                e.health -= damage / 3;
                            }
                        }
                    }

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
            // Range ring (only when selected tower type matches or hovered)
            g2.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 12));
            g2.fillOval(x - range + 20, y - range + 16, range * 2, range * 2);
            g2.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), 35));
            for (int deg = 0; deg < 360; deg += 8) {
                double rad = Math.toRadians(deg + (animTick * 0.5));
                int rx = (int) (x + 20 + (range - 4) * Math.cos(rad)), ry = (int) (y + 16 + (range - 4) * Math.sin(rad));
                g2.fillRect(rx, ry, PX, PX);
            }
            // Base platform
            g2.setColor(new Color(14, 36, 26));
            g2.fillRect(x - 5, y + 24, 48, 10);
            g2.setColor(new Color(0, 80, 50));
            g2.fillRect(x - 3, y + 26, 44, 6);
            // Animated energy at base
            long t = System.currentTimeMillis();
            int pulseA = (int) (Math.abs(Math.sin(t / 400.0 + x)) * 30) + 10;
            g2.setColor(new Color(mainColor.getRed(), mainColor.getGreen(), mainColor.getBlue(), pulseA));
            g2.fillRect(x - 5, y + 24, 48, 10);

            // Tower body
            drawTowerBody(g2, x, y, mainColor, accentColor);

            // Barrel
            int bx2 = x + 20, by2 = y + 16;
            AffineTransform old = g2.getTransform();
            g2.rotate(aimAngle, bx2, by2);
            // Barrel shadow
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRect(bx2 + 1, by2 - 2, 27, 7);
            g2.setColor(accentColor);
            g2.fillRect(bx2, by2 - 3, 24, 6);
            g2.setColor(mainColor);
            g2.fillRect(bx2 + 20, by2 - 2, 8, 4);
            g2.setColor(new Color(255, 255, 255, 150));
            g2.fillRect(bx2 + 26, by2 - 1, 4, 2);
            // Barrel rings
            g2.setColor(new Color(mainColor.getRed() / 2, mainColor.getGreen() / 2, mainColor.getBlue() / 2));
            g2.fillRect(bx2 + 6, by2 - 3, 3, 6);
            g2.fillRect(bx2 + 13, by2 - 3, 3, 6);
            g2.setTransform(old);

            // Firing beam
            if (target != null && cooldown > cooldownMax - 7) {
                drawPixelBeam(g2, bx2, by2, target.x + 16, target.y + 12, mainColor, accentColor);
            }
        }

        void drawTowerBody(Graphics2D g, int x, int y, Color mc, Color ac) {
            // Shadow
            g.setColor(new Color(0, 0, 0, 60));
            g.fillRect(x + 3, y + 3, 38, 28);
            // Dark base
            g.setColor(new Color(mc.getRed() / 4, mc.getGreen() / 4, mc.getBlue() / 4));
            g.fillRect(x, y, 38, 28);
            // Main body gradient effect
            g.setColor(mc);
            g.fillRect(x, y, 38, 28);
            // Light top face
            g.setColor(new Color(mc.getRed() * 2 / 3 + 60, mc.getGreen() * 2 / 3 + 60, mc.getBlue() * 2 / 3 + 60, 80));
            g.fillRect(x, y, 38, 10);
            // Border
            g.setColor(new Color(mc.getRed() / 2, mc.getGreen() / 2, mc.getBlue() / 2));
            g.drawRect(x, y, 38, 28);

            switch (type) {
                case "NORMAL":
                    g.setColor(new Color(90, 210, 115));
                    g.fillRect(x + 5, y + 4, 18, 18);
                    g.setColor(new Color(50, 90, 70));
                    g.fillRect(x - 6, y + 2, 7, 24);
                    g.setColor(ac);
                    g.fillRect(x - 9, y, 7, 28);
                    drawMedCross(g, x + 18, y - 9, 13, new Color(220, 50, 60));
                    // Cross glow
                    g.setColor(new Color(220, 50, 60, 60));
                    g.fillOval(x + 10, y - 16, 16, 16);
                    break;
                case "FIRE":
                    g.setColor(new Color(255, 50, 20));
                    g.fillRect(x + 4, y + 4, 14, 20);
                    g.setColor(new Color(255, 160, 80));
                    g.fillRect(x + 7, y + 7, 8, 14);
                    // Flame flicker
                    long t2 = System.currentTimeMillis();
                    int flicker = (int) (Math.abs(Math.sin(t2 / 100.0 + x)) * 6);
                    g.setColor(new Color(255, 220, 60, 180));
                    g.fillRect(x + 9, y - flicker, 4, flicker + 4);
                    g.setColor(ac);
                    g.setFont(new Font("Monospaced", Font.BOLD, 8));
                    g.drawString("UV", x + 24, y + 18);
                    break;
                case "ICE":
                    g.setColor(ac);
                    g.fillRect(x + 3, y + 8, 25, 5);
                    g.fillRect(x + 3, y + 18, 25, 5);
                    // Ice crystal on top
                    int[] cxs = {x + 16, x + 10, x + 12, x + 16, x + 20, x + 22, x + 16};
                    int[] cys = {y - 12, y - 4, y - 8, y - 14, y - 8, y - 4, y - 12};
                    g.setColor(new Color(180, 235, 255));
                    g.fillPolygon(cxs, cys, 7);
                    g.setColor(new Color(220, 250, 255));
                    g.drawPolygon(cxs, cys, 7);
                    break;
                case "ELEC":
                    g.setColor(new Color(40, 35, 5));
                    for (int fy = y + 3; fy < y + 25; fy += 5) {
                        g.fillRect(x + 3, fy, 28, 2);
                    }
                    g.setColor(ac);
                    g.fillRect(x + 12, y - 10, 8, 10);
                    g.fillRect(x + 15, y - 14, 3, 6);
                    // Lightning sparks
                    if (animTick % 5 < 2) {
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
                    for (int i = 1; i <= 3; i++) {
                        g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 200 - i * 50));
                        g.drawOval(x + i * 4, y + i * 4, 30 - i * 8, 20 - i * 5);
                    }
                    // Animated wave rings
                    int waveOff = (animTick * 2) % 20;
                    g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 80));
                    g.drawOval(x - waveOff, y - waveOff / 2, 38 + waveOff * 2, 28 + waveOff);
                    break;
                case "NANO":
                    g.setColor(ac);
                    for (int i = 0; i < 3; i++) {
                        for (int j = 0; j < 3; j++) {
                            g.fillRect(x + 4 + i * 11, y + 4 + j * 8, 8, 5);
                        }
                    }
                    // Nano grid lines
                    g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 60));
                    for (int i = 0; i < 3; i++) {
                        g.drawLine(x, y + 4 + i * 8, x + 38, y + 4 + i * 8);
                    }
                    for (int i = 0; i < 3; i++) {
                        g.drawLine(x + 3 + i * 12, y, x + 3 + i * 12, y + 28);
                    }
                    break;
                case "PLASMA":
                    int plasOff = (animTick * 4) % 360;
                    g.setColor(ac);
                    g.fillOval(x + 5, y + 4, 26, 20);
                    g.setColor(new Color(255, 255, 255, 80));
                    g.fillOval(x + 12, y + 7, 10, 8);
                    // Orbiting particle
                    double plasRad = Math.toRadians(plasOff);
                    g.setColor(new Color(255, 200, 255));
                    g.fillRect(x + 19 + (int) (Math.cos(plasRad) * 10), y + 14 + (int) (Math.sin(plasRad) * 7), 4, 4);
                    break;
                case "ACID":
                    int[][] d = {{0, 1, 0}, {1, 1, 1}, {0, 1, 0}};
                    g.setColor(ac);
                    for (int r = 0; r < d.length; r++) {
                        for (int col = 0; col < d[r].length; col++) {
                            if (d[r][col] == 1) {
                                g.fillRect(x + 4 + col * 11, y + 4 + r * 9, 10, 7);
                            }
                        }
                    }
                    g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 60));
                    g.fillOval(x, y, 36, 26);
                    // Drip animation
                    if (animTick % 20 < 5) {
                        g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 160));
                        g.fillRect(x + 16, y + 26, 4, 4 + animTick % 5);
                    }
                    break;
            }
        }

        void drawPixelBeam(Graphics2D g, int x1, int y1, int x2, int y2, Color mc, Color ac) {
            int steps = 14;
            for (int s = 0; s < steps; s++) {
                float t = (float) s / steps;
                int bx = (int) (x1 + (x2 - x1) * t), by = (int) (y1 + (y2 - y1) * t);
                int jitter = s % 2 == 0 ? -PX : PX;
                g.setColor(s % 2 == 0 ? mc : ac);
                g.fillRect(bx + jitter, by, PX * 2, PX);
            }
            // Impact flash
            g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 160));
            g.fillOval(x2 - 6, y2 - 6, 12, 12);
            g.setColor(ac);
            g.fillRect(x2 - PX, y2 - PX, PX * 3, PX * 3);
        }
    }
}
