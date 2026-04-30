package tdx;

import javax.swing.*;
import javax.sound.midi.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * CELLS AT WORK - Tower Defense Medical themed tower defense game
 */
public class TDX extends JPanel implements ActionListener, MouseListener {

    // =====================================================================
    // SCREEN
    // =====================================================================
    static int SW = 1280, SH = 720;
    static boolean isFullScreen = true;
    static GraphicsDevice gd;

    int PATH_TOP, PATH_BOT, UI_TOP_H, UI_BOT_Y, UI_BOT_H, BASE_X;
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
    // GAME STATE
    // =====================================================================
    enum GameState {
        MENU, SETTINGS, PLAYING, SCORE_ENTRY, SCOREBOARD, MAP_SELECT, PAUSE_MENU, BESTIARY
    }
    GameState gameState = GameState.MENU;
    GameState prevState = GameState.MENU;

    enum Difficulty {
        EASY, NORMAL, HARD, NIGHTMARE
    }
    Difficulty difficulty = Difficulty.NORMAL;

    boolean paused = false;
    boolean waitingToStart = true;
    boolean autoStartRounds = false;
    Timer timer;
    int gameSpeed = 1;
    int volume = 100; // 0-100
    boolean soundEnabled = true;

    List<Enemy> enemies = new ArrayList<>();
    List<Tower> towers = new ArrayList<>();
    List<Projectile> projectiles = new ArrayList<>();

    int money = 200, baseHealth = 100, wave = 1;
    static final int MAX_WAVE = 35;
    int score = 0, selectedMap = 0;
    boolean gameOver = false, gameWon = false;
    String selectedTower = "NORMAL";
    int mouseX = 0, mouseY = 0;

    // Bestiary scroll
    int bestiaryScroll = 0;
    int bestiarySelected = 0;

    // Upgrade system: track upgrade level per tower
    // Key = tower index in towers list, value = upgrade level (0,1,2)
    // Upgrades only for first 4 towers (NORMAL, FIRE, ICE, ELEC)
    // Tower unlock: HARD unlocks ELEC+SONIC+NANO+PLASMA, NIGHTMARE unlocks all
    boolean[] towerUnlocked = new boolean[8];

    // Auto-wave timer
    int autoWaveTimer = 0;
    static final int AUTO_WAVE_DELAY = 300; // ticks (~8 seconds)

    // Score
    static final int MAX_SCORES = 10;
    String[] scoreNames = new String[MAX_SCORES];
    int[] scoreValues = new int[MAX_SCORES];
    int[] scoreDifficulty = new int[MAX_SCORES];
    int scoreCount = 0;
    String currentName = "";

    // =====================================================================
    // WAYPOINTS
    // =====================================================================
    int[][] mapWaypoints;

    void buildMapWaypoints() {
        if (selectedMap == 0) {
            mapWaypoints = null;
            return;
        }
        int mid = PATH_TOP + (PATH_BOT - PATH_TOP) / 2;
        int top = PATH_TOP + (int) ((PATH_BOT - PATH_TOP) * 0.15);
        int bot = PATH_TOP + (int) ((PATH_BOT - PATH_TOP) * 0.85);
        mapWaypoints = new int[][]{
            {-60, mid},
            {(int) (SW * 0.10), bot},
            {(int) (SW * 0.22), top},
            {(int) (SW * 0.36), bot},
            {(int) (SW * 0.50), top},
            {(int) (SW * 0.64), bot},
            {(int) (SW * 0.78), top},
            {(int) (SW * 0.88), mid},
            {BASE_X + 80, mid}
        };
    }

    // =====================================================================
    // AUDIO - MIDI with volume control
    // =====================================================================
    static Synthesizer synth;
    static MidiChannel[] channels;

    static {
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            channels = synth.getChannels();
            channels[0].programChange(9);
            channels[1].programChange(98);
            channels[2].programChange(33);
            channels[3].programChange(12);
            channels[4].programChange(0);
        } catch (Exception ignored) {
        }
    }

    void setChannelVolumes() {
        if (channels == null) {
            return;
        }
        int v = soundEnabled ? (int) (volume * 1.27) : 0; // 0-127
        for (int c = 0; c < 5; c++) {
            try {
                channels[c].controlChange(7, v);
            } catch (Exception ignored) {
            }
        }
    }

    void playNote(int ch, int note, int vel, int dur) {
        if (!soundEnabled || channels == null || ch >= channels.length) {
            return;
        }
        int scaledVel = (int) (vel * volume / 100.0);
        new Thread(() -> {
            try {
                channels[ch].noteOn(note, Math.max(1, scaledVel));
                Thread.sleep(dur);
                channels[ch].noteOff(note);
            } catch (Exception ignored) {
            }
        }).start();
    }

    static volatile boolean bgPlaying = false;
    static volatile String currentMusicCtx = "";
    static Thread bgThread;
    TDX musicOwner; // reference for volume

    static void stopBGMusic() {
        bgPlaying = false;
        if (bgThread != null) {
            bgThread.interrupt();
            bgThread = null;
        }
        if (channels != null) {
            for (int c = 0; c < 5; c++) try {
                channels[c].allNotesOff();
            } catch (Exception ignored) {
            }
        }
        currentMusicCtx = "";
    }

    void startMenuMusic() {
        if (bgPlaying && currentMusicCtx.equals("menu")) {
            return;
        }
        stopBGMusic();
        currentMusicCtx = "menu";
        bgPlaying = true;
        final TDX self = this;
        bgThread = new Thread(() -> {
            // Upbeat medical jingle - C major, bright glockenspiel
            int[] mel = {72, 74, 76, 77, 76, 74, 72, 69, 71, 72, 74, 76, 77, 76, 74, 72, 67, 69, 71, 72, 74, 76, 77, 79, 76, 74, 72, 71, 69, 67, 65, 64};
            int[] bass = {36, 43, 36, 43, 38, 45, 38, 45, 37, 44, 37, 44, 36, 43, 36, 43};
            int[] chrd = {60, 64, 67, 65, 69, 72, 62, 65, 60, 64, 67, 69, 72, 74, 72, 69};
            if (channels != null) {
                channels[0].programChange(9);
                channels[2].programChange(33);
                channels[3].programChange(12);
            }
            int tick = 0;
            while (bgPlaying) {
                try {
                    int i = tick % mel.length;
                    self.playNote(0, mel[i], 72, 140);
                    if (i % 2 == 0) {
                        self.playNote(2, bass[(i / 2) % bass.length], 55, 280);
                    }
                    if (i % 4 == 0) {
                        self.playNote(3, chrd[(i / 4) % chrd.length], 42, 560);
                    }
                    Thread.sleep(145);
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

    void startMap1Music() {
        if (bgPlaying && currentMusicCtx.equals("map1")) {
            return;
        }
        stopBGMusic();
        currentMusicCtx = "map1";
        bgPlaying = true;
        final TDX self = this;
        bgThread = new Thread(() -> {
            // Tense battle march - minor key
            int[] mel = {60, 62, 63, 65, 67, 65, 63, 60, 58, 60, 63, 67, 65, 63, 60, 58, 60, 63, 67, 70, 68, 65, 63, 60, 58, 60, 62, 63, 65, 67, 68, 67};
            int[] bass = {36, 36, 43, 43, 41, 41, 39, 39, 36, 36, 43, 43, 41, 41, 38, 38};
            if (channels != null) {
                channels[0].programChange(80);
                channels[2].programChange(34);
            }
            int tick = 0;
            while (bgPlaying) {
                try {
                    int i = tick % mel.length;
                    self.playNote(0, mel[i], 68, 175);
                    if (i % 2 == 0) {
                        self.playNote(2, bass[i % bass.length], 52, 350);
                    }
                    if (i % 4 == 0) {
                        self.playNote(9, i % 8 == 0 ? 35 : 38, 85, 50);
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

    void startMap2Music() {
        if (bgPlaying && currentMusicCtx.equals("map2")) {
            return;
        }
        stopBGMusic();
        currentMusicCtx = "map2";
        bgPlaying = true;
        final TDX self = this;
        bgThread = new Thread(() -> {
            // Tense chromatic synth - sci-fi lab feel
            int[] mel = {62, 65, 63, 62, 60, 63, 65, 67, 68, 67, 65, 63, 62, 60, 63, 65, 60, 62, 63, 65, 67, 68, 67, 65, 63, 62, 60, 58, 57, 58, 60, 62};
            int[] pad = {38, 38, 41, 41, 37, 37, 43, 43};
            if (channels != null) {
                channels[0].programChange(82);
                channels[2].programChange(39);
                channels[3].programChange(91);
            }
            int tick = 0;
            while (bgPlaying) {
                try {
                    int i = tick % mel.length;
                    self.playNote(0, mel[i], 72, 195);
                    if (i % 2 == 0) {
                        self.playNote(2, pad[(i / 2) % pad.length], 55, 390);
                    }
                    if (i % 4 == 0) {
                        self.playNote(3, pad[i % pad.length] + 12, 35, 780);
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

    void startScoreboardMusic() {
        if (bgPlaying && currentMusicCtx.equals("score")) {
            return;
        }
        stopBGMusic();
        currentMusicCtx = "score";
        bgPlaying = true;
        final TDX self = this;
        bgThread = new Thread(() -> {
            int[] mel = {60, 64, 67, 72, 71, 72, 74, 72, 71, 69, 67, 69, 71, 72, 74, 72};
            if (channels != null) {
                channels[0].programChange(14);
                channels[2].programChange(58);
            }
            int tick = 0;
            while (bgPlaying) {
                try {
                    int i = tick % mel.length;
                    self.playNote(0, mel[i], 70, 195);
                    if (i % 2 == 0) {
                        self.playNote(2, 36 + (i % 4) * 2, 52, 390);
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

    void sfxShoot() {
        playNote(1, 74, 70, 50);
    }

    void sfxEnemyDeath() {
        playNote(1, 45, 85, 55);
        playNote(1, 40, 65, 55);
    }

    void sfxBossDeath() {
        new Thread(() -> {
            try {
                stopBGMusic();
                for (int n : new int[]{60, 55, 50, 45, 40, 35}) {
                    playNote(1, n, 105, 110);
                    Thread.sleep(110);
                }
                for (int n : new int[]{60, 64, 67, 72}) {
                    playNote(0, n, 88, 100);
                    Thread.sleep(100);
                }
                if (selectedMap == 0) {
                    startMap1Music();
                } else {
                    startMap2Music();
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    void sfxTowerPlaced() {
        playNote(1, 72, 75, 70);
        playNote(1, 76, 80, 70);
        playNote(1, 79, 85, 70);
    }

    void sfxTowerDestroyed() {
        playNote(1, 55, 95, 80);
        playNote(1, 48, 90, 80);
    }

    void sfxNewWave(int w) {
        new Thread(() -> {
            try {
                String ctx = currentMusicCtx;
                stopBGMusic();
                for (int i = 0; i < Math.min(w, 5); i++) {
                    playNote(0, 60 + i * 4, 100, 85);
                    Thread.sleep(90);
                }
                Thread.sleep(80);
                if (ctx.equals("map1")) {
                    startMap1Music();
                } else if (ctx.equals("map2")) {
                    startMap2Music();
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    void sfxVictory() {
        new Thread(() -> {
            try {
                stopBGMusic();
                for (int n : new int[]{60, 64, 67, 72, 71, 72, 76}) {
                    playNote(0, n, 95, 125);
                    Thread.sleep(130);
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    void sfxGameOver() {
        new Thread(() -> {
            try {
                stopBGMusic();
                for (int n : new int[]{55, 50, 45, 40}) {
                    playNote(0, n, 95, 195);
                    Thread.sleep(200);
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    void sfxMenuClick() {
        playNote(1, 74, 85, 55);
        playNote(1, 79, 95, 75);
    }

    void sfxUpgrade() {
        playNote(1, 79, 90, 60);
        playNote(1, 83, 95, 70);
        playNote(1, 86, 100, 80);
    }

    // =====================================================================
    // MEDICAL COLOR PALETTE
    // =====================================================================
    static final Color C_BG = new Color(4, 10, 20);
    static final Color C_PATH = new Color(8, 28, 22);
    static final Color C_PATH_EDGE = new Color(0, 210, 150);
    static final Color C_GRID = new Color(0, 35, 28);
    static final Color C_BASE = new Color(0, 210, 150);
    static final Color C_UI_BG = new Color(3, 8, 18);
    static final Color C_UI_BORDER = new Color(0, 175, 120);
    static final Color C_UI_TEXT = new Color(190, 255, 220);
    static final Color C_MONEY = new Color(255, 220, 60);
    static final Color C_HEALTH = new Color(230, 55, 70);
    static final Color C_WAVE = new Color(80, 200, 255);
    static final Color C_SCORE = new Color(255, 210, 55);
    // Medical accent colors
    static final Color C_MED_RED = new Color(220, 45, 60);
    static final Color C_MED_BLUE = new Color(40, 160, 240);
    static final Color C_MED_GREEN = new Color(0, 200, 130);
    static final Color C_MED_WHITE = new Color(230, 245, 240);
    // Map 2
    static final Color C_PATH2 = new Color(8, 20, 44);
    static final Color C_PATH2_EDGE = new Color(0, 160, 255);

    // =====================================================================
    // CONSTRUCTOR
    // =====================================================================
    public TDX() {
        setBackground(C_BG);
        musicOwner = this;
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
        unlockTowers();
    }

    void unlockTowers() {
        // Default: first 4 unlocked always
        towerUnlocked[0] = true; // NORMAL
        towerUnlocked[1] = true; // FIRE
        towerUnlocked[2] = true; // ICE
        towerUnlocked[3] = false; // ELEC - locked on EASY/NORMAL
        towerUnlocked[4] = false; // SONIC
        towerUnlocked[5] = false; // NANO
        towerUnlocked[6] = false; // PLASMA
        towerUnlocked[7] = false; // ACID

        if (difficulty == Difficulty.HARD || difficulty == Difficulty.NIGHTMARE) {
            towerUnlocked[3] = true;
            towerUnlocked[4] = true;
            towerUnlocked[5] = true;
            towerUnlocked[6] = true;
        }
        if (difficulty == Difficulty.NIGHTMARE) {
            towerUnlocked[7] = true;
            // all 8 unlocked already above but set explicitly:
            for (int i = 0; i < 8; i++) {
                towerUnlocked[i] = true;
            }
        }
        if (difficulty == Difficulty.EASY || difficulty == Difficulty.NORMAL) {
            towerUnlocked[3] = false;
            towerUnlocked[4] = false;
            towerUnlocked[5] = false;
            towerUnlocked[6] = false;
            towerUnlocked[7] = false;
        }
    }

    // =====================================================================
    // DIFFICULTY BALANCING - reduced money rewards
    // =====================================================================
    double enemyHealthMult() {
        switch (difficulty) {
            case EASY:
                return 0.55;
            case HARD:
                return 1.7;
            case NIGHTMARE:
                return 2.8;
            default:
                return 1.0;
        }
    }

    double enemySpeedMult() {
        switch (difficulty) {
            case EASY:
                return 0.7;
            case HARD:
                return 1.35;
            case NIGHTMARE:
                return 1.8;
            default:
                return 1.0;
        }
    }

    int startMoney() {
        switch (difficulty) {
            case EASY:
                return 220;
            case HARD:
                return 150;
            case NIGHTMARE:
                return 100;
            default:
                return 180;
        }
    }

    int waveBonus() {
        switch (difficulty) {
            case EASY:
                return 55;
            case HARD:
                return 30;
            case NIGHTMARE:
                return 18;
            default:
                return 38;
        }
    }

    double scoreMult() {
        switch (difficulty) {
            case EASY:
                return 0.55;
            case HARD:
                return 2.4;
            case NIGHTMARE:
                return 5.0;
            default:
                return 1.0;
        }
    }

    // Enemy money reward multiplier (reduced)
    double rewardMult() {
        switch (difficulty) {
            case EASY:
                return 0.7;
            case HARD:
                return 1.0;
            case NIGHTMARE:
                return 0.85;
            default:
                return 0.85;
        }
    }

    // =====================================================================
    // GAME LOGIC
    // =====================================================================
    void startGame() {
        recalcLayout();
        buildMapWaypoints();
        unlockTowers();
        money = startMoney();
        baseHealth = 100;
        wave = 1;
        score = 0;
        gameOver = false;
        gameWon = false;
        paused = false;
        waitingToStart = true;
        gameSpeed = 1;
        autoWaveTimer = 0;
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
        int total = 6 + wave * 4;
        int rows = 6;
        for (int i = 0; i < total; i++) {
            int col = i / rows, row = i % rows;
            int sx = -55 - col * 75;
            int sy;
            if (selectedMap == 0) {
                sy = PATH_TOP + 14 + row * ((PATH_BOT - PATH_TOP - 28) / rows);
            } else {
                int mid = PATH_TOP + (PATH_BOT - PATH_TOP) / 2;
                sy = mid - 10 + (row - rows / 2) * 8;
            }
            Enemy e = selectedMap == 0 ? spawnMap1Enemy(i, sx, sy) : spawnMap2Enemy(i, sx, sy);
            e.speed = Math.max(1, (int) Math.round(e.speed * enemySpeedMult()));
            if (selectedMap == 1) {
                e.waypointIndex = 0;
            }
            enemies.add(e);
        }
        if (wave % 5 == 0) {
            Enemy boss;
            if (wave == 35) {
                boss = new FinalBoss(-300, PATH_TOP + 30);
            } else if (wave == 30) {
                boss = new ColossusEnemy(-300, PATH_TOP + 30);
            } else if (wave == 25) {
                boss = new UltraVirus(-300, PATH_TOP + 30);
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
        if (difficulty == Difficulty.NIGHTMARE && wave >= 8 && i % 6 == 0) {
            return mkEnemy(new MutantEnemy(sx, sy));
        }
        if (wave >= 25 && i % 5 == 0) {
            return mkEnemy(new StealthEnemy(sx, sy));
        }
        if (wave >= 18 && i % 6 == 0) {
            return mkEnemy(new HealerEnemy(sx, sy));
        }
        if (wave >= 12 && i % 7 == 0) {
            return mkEnemy(new TankEnemy(sx, sy));
        }
        if (wave >= 7 && i % 5 == 0) {
            return mkEnemy(new SpeedEnemy(sx, sy));
        }
        if (wave >= 4 && i % 4 == 0) {
            return mkEnemy(new ShieldEnemy(sx, sy));
        }
        Enemy e = new Enemy(sx, sy);
        e.maxHealth = (int) ((50 + wave * 12) * enemyHealthMult());
        e.health = e.maxHealth;
        return e;
    }

    Enemy spawnMap2Enemy(int i, int sx, int sy) {
        if (difficulty == Difficulty.NIGHTMARE && wave >= 6 && i % 5 == 0) {
            return mkEnemy(new MutantEnemy(sx, sy));
        }
        if (wave >= 22 && i % 4 == 0) {
            return mkEnemy(new SwarmEnemy(sx, sy));
        }
        if (wave >= 5 && i % 7 == 0) {
            return mkEnemy(new TowerDestroyerEnemy(sx, sy));
        }
        if (wave >= 15 && i % 5 == 0) {
            return mkEnemy(new PhaserEnemy(sx, sy));
        }
        if (wave >= 8 && i % 6 == 0) {
            return mkEnemy(new ArmoredEnemy(sx, sy));
        }
        if (wave >= 5 && i % 4 == 0) {
            return mkEnemy(new SpeedEnemy(sx, sy));
        }
        if (wave >= 3 && i % 3 == 0) {
            return mkEnemy(new ShieldEnemy(sx, sy));
        }
        Enemy e = new Enemy(sx, sy);
        e.maxHealth = (int) ((60 + wave * 14) * enemyHealthMult());
        e.health = e.maxHealth;
        e.reward = 22;
        return e;
    }

    Enemy mkEnemy(Enemy e) {
        e.reward = (int) (e.reward * rewardMult());
        return e;
    }

    void launchWave() {
        sfxNewWave(wave);
        waitingToStart = false;
        autoWaveTimer = 0;
    }

    // =====================================================================
    // TOWER PLACEMENT VALIDATION
    // =====================================================================
    boolean isValidTowerPosition(int x, int y) {
        if (x < 10 || x > SW - 30 || y < UI_TOP_H + 4 || y > UI_BOT_Y - 10) {
            return false;
        }
        if (selectedMap == 0) {
            if (y > PATH_TOP - 14 && y < PATH_BOT + 14) {
                return false;
            }
        } else {
            if (isOnSerpenPath(x, y)) {
                return false;
            }
        }
        return x <= BASE_X - 10;
    }

    boolean isOnSerpenPath(int x, int y) {
        if (mapWaypoints == null) {
            return false;
        }
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            int x1 = mapWaypoints[i][0], y1 = mapWaypoints[i][1], x2 = mapWaypoints[i + 1][0], y2 = mapWaypoints[i + 1][1];
            double len = Math.hypot(x2 - x1, y2 - y1);
            if (len == 0) {
                continue;
            }
            double t = Math.max(0, Math.min(1, ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / (len * len)));
            if (Math.hypot(x - x1 - t * (x2 - x1), y - y1 - t * (y2 - y1)) < 46) {
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
    // SCORE SYSTEM
    // =====================================================================
    void addScore(String name, int value, int diff) {
        if (scoreCount < MAX_SCORES) {
            scoreNames[scoreCount] = name;
            scoreValues[scoreCount] = value;
            scoreDifficulty[scoreCount] = diff;
            scoreCount++;
        } else {
            int mi = 0;
            for (int i = 1; i < MAX_SCORES; i++) {
                if (scoreValues[i] < scoreValues[mi]) {
                    mi = i;
                }
            }
            if (value > scoreValues[mi]) {
                scoreNames[mi] = name;
                scoreValues[mi] = value;
                scoreDifficulty[mi] = diff;
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
        g.setColor(C_UI_BORDER);
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
        g.setColor(C_MONEY);
        for (int r = 0; r < c.length; r++) {
            for (int col = 0; col < c[r].length; col++) {
                if (c[r][col] == 1) {
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

    // Glowing text helper
    void drawGlowText(Graphics2D g, String txt, int x, int y, Color col, int glowLayers) {
        for (int gl = glowLayers; gl >= 1; gl--) {
            int a = Math.min(255, 10 + gl * 12);
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), a));
            g.drawString(txt, x - gl, y + gl);
            g.drawString(txt, x + gl, y - gl);
            g.drawString(txt, x - gl, y - gl);
            g.drawString(txt, x + gl, y + gl);
        }
        g.setColor(col);
        g.drawString(txt, x, y);
    }

    // =====================================================================
    // PAINT DISPATCHER
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
            case PAUSE_MENU:
                drawGame(g2);
                drawPauseMenu(g2);
                break;
            case BESTIARY:
                drawBestiary(g2);
                break;
        }
    }

    // =====================================================================
    // MENU
    // =====================================================================
    void drawMenu(Graphics2D g2) {
        // Background
        g2.setColor(C_BG);
        g2.fillRect(0, 0, SW, SH);
        long t = System.currentTimeMillis();
        // Animated hex grid
        int goff = (int) ((t / 100) % 40);
        for (int gx = -goff; gx < SW; gx += 40) {
            for (int gy = 0; gy < SH; gy += 34) {
                g2.setColor(C_GRID);
                g2.drawRect(gx, gy, 28, 18);
            }
        }
        // Scanlines
        for (int sy = 0; sy < SH; sy += 4) {
            g2.setColor(new Color(0, 15, 10, 14));
            g2.fillRect(0, sy, SW, 2);
        }

        // Top & bottom medical strips
        drawBioStrip(g2, 0, t);
        drawBioStrip(g2, SH - 24, t);

        // Central panel
        int pH = (int) (SH * 0.80), pY = (SH - pH) / 2;
        g2.setColor(new Color(0, 18, 12, 90));
        g2.fillRect(SW / 2 - 230, pY, 460, pH);
        g2.setColor(new Color(0, 175, 110, 40));
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(SW / 2 - 230, pY, 460, pH);
        // Corner brackets
        drawBracket(g2, SW / 2 - 230, pY, C_MED_GREEN, false, false);
        drawBracket(g2, SW / 2 + 218, pY, C_MED_GREEN, true, false);
        drawBracket(g2, SW / 2 - 230, pY + pH - 14, C_MED_GREEN, false, true);
        drawBracket(g2, SW / 2 + 218, pY + pH - 14, C_MED_GREEN, true, true);

        // Side viruses
        for (int vi = 0; vi < 4; vi++) {
            drawMenuVirus(g2, (int) (SW * (vi < 2 ? 0.06 : 0.84)), (int) (SH * (vi % 2 == 0 ? 0.18 : 0.66)), vi, t);
        }
        drawSmallVirus(g2, (int) (SW * 0.26), (int) (SH * 0.06), 2, t);
        drawSmallVirus(g2, (int) (SW * 0.66), (int) (SH * 0.88), 0, t);

        // TITLE
        g2.setFont(pixelFont(96));
        String title = "CELLS AT WORK";
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(title), tx2 = SW / 2 - tw / 2, ty = pY + (int) (pH * 0.22);
        for (int gl = 10; gl >= 1; gl--) {
            g2.setColor(new Color(0, 255, 150, 8 + gl * 10));
            g2.drawString(title, tx2 - gl, ty + gl);
            g2.drawString(title, tx2 + gl, ty - gl);
        }
        g2.setColor(new Color(0, 30, 20));
        g2.drawString(title, tx2 + 5, ty + 5);
        g2.setColor(C_PATH_EDGE);
        g2.drawString(title, tx2, ty);
        // Subtitle
        g2.setFont(pixelFont(13));
        String sub = "DEFENSA MEDICA  ·  TOWER DEFENSE";
        fm = g2.getFontMetrics();
        int subA = (int) (Math.abs(Math.sin(t / 700.0)) * 60) + 160;
        g2.setColor(new Color(100, 220, 160, subA));
        g2.drawString(sub, SW / 2 - fm.stringWidth(sub) / 2, ty + 32);
        // Version tag
        g2.setFont(pixelFont(8));
        g2.setColor(new Color(0, 120, 80, 140));
        g2.drawString("v2.0  |  MAX WAVE 35  |  8 DEFENSAS", SW / 2 - 100, ty + 48);

        // Divider
        g2.setColor(new Color(0, 150, 80, 100));
        g2.fillRect(SW / 2 - 190, (int) (ty * 1.04) + 10, 140, 2);
        g2.fillRect(SW / 2 + 50, (int) (ty * 1.04) + 10, 140, 2);
        drawMedCross(g2, SW / 2, (int) (ty * 1.04) + 8, 12, new Color(0, 200, 100, 160));

        // Buttons
        String[] lbls = {"JUGAR", "AJUSTES", "BESTIARY", "PUNTAJES", "SALIR"};
        String[] descs2 = {"Seleccionar mapa y combatir", "Dificultad · pantalla · sonido", "Catalogo de enemigos", "Ver tabla de records", "Cerrar el juego"};
        Color[] bCols = {new Color(0, 220, 120), new Color(80, 200, 255), new Color(220, 100, 255), new Color(255, 210, 60), new Color(220, 60, 60)};
        String[] icons2 = {">", "*", "?", "#", "X"};
        int bw = 360, bh = 56, bx0 = SW / 2 - bw / 2;
        int[] byArr = {pY + (int) (pH * 0.38), pY + (int) (pH * 0.50), pY + (int) (pH * 0.62), pY + (int) (pH * 0.74), pY + (int) (pH * 0.86)};
        for (int i = 0; i < lbls.length; i++) {
            boolean hov = mouseX >= bx0 && mouseX <= bx0 + bw && mouseY >= byArr[i] && mouseY <= byArr[i] + bh;
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRect(bx0 + 5, byArr[i] + 5, bw, bh);
            g2.setColor(hov ? new Color(bCols[i].getRed(), bCols[i].getGreen(), bCols[i].getBlue(), 28) : new Color(5, 14, 10));
            g2.fillRect(bx0, byArr[i], bw, bh);
            g2.setColor(hov ? bCols[i] : new Color(bCols[i].getRed() / 3, bCols[i].getGreen() / 3, bCols[i].getBlue() / 3));
            g2.fillRect(bx0, byArr[i], 5, bh);
            g2.setColor(hov ? bCols[i] : new Color(bCols[i].getRed() / 3, bCols[i].getGreen() / 3, bCols[i].getBlue() / 3));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(bx0, byArr[i], bw, bh);
            // Icon
            g2.setColor(hov ? new Color(bCols[i].getRed(), bCols[i].getGreen(), bCols[i].getBlue(), 55) : new Color(10, 22, 16));
            g2.fillRect(bx0 + 10, byArr[i] + 10, 36, 36);
            g2.setColor(hov ? bCols[i] : new Color(bCols[i].getRed() / 2, bCols[i].getGreen() / 2, bCols[i].getBlue() / 2));
            g2.drawRect(bx0 + 10, byArr[i] + 10, 36, 36);
            g2.setFont(pixelFont(16));
            FontMetrics fmI = g2.getFontMetrics();
            g2.setColor(hov ? bCols[i] : C_UI_TEXT);
            g2.drawString(icons2[i], bx0 + 10 + (36 - fmI.stringWidth(icons2[i])) / 2, byArr[i] + 34);
            g2.setColor(hov ? bCols[i] : C_UI_TEXT);
            g2.setFont(pixelFont(20));
            g2.drawString(lbls[i], bx0 + 58, byArr[i] + 28);
            g2.setColor(new Color(100, 165, 130));
            g2.setFont(pixelFont(9));
            g2.drawString(descs2[i], bx0 + 58, byArr[i] + 46);
            if (hov) {
                g2.setColor(bCols[i]);
                g2.setFont(pixelFont(12));
                g2.drawString(">>", bx0 + bw - 36, byArr[i] + bh / 2 + 4);
            }
        }
        // Bottom strip
        g2.setColor(new Color(0, 130, 65, 100));
        g2.fillRect(0, SH - 28, SW, 28);
        Color[] dClr = {new Color(60, 200, 80), new Color(80, 190, 255), new Color(220, 60, 60), new Color(185, 40, 225)};
        g2.setColor(dClr[difficulty.ordinal()]);
        g2.setFont(pixelFont(9));
        String ds = "DIFICULTAD: " + difficulty.name() + "   |   [F11] Pantalla completa   |   [ESC] Salir";
        g2.drawString(ds, SW / 2 - g2.getFontMetrics().stringWidth(ds) / 2, SH - 9);
    }

    void drawBioStrip(Graphics2D g2, int y, long t) {
        g2.setColor(new Color(0, 90, 50, 55));
        g2.fillRect(0, y, SW, 20);
        int off = (int) ((t / 65) % 40);
        for (int x = -off; x < SW; x += 40) {
            g2.setColor(new Color(0, 195, 110, 38));
            g2.fillRect(x, y + 2, 20, 4);
            drawMedCross(g2, x + 10, y + 10, 8, new Color(0, 175, 100, 55));
        }
    }

    void drawBracket(Graphics2D g2, int x, int y, Color c, boolean fx, boolean fy) {
        g2.setColor(c);
        int ox = fx ? -1 : 1, oy = fy ? -1 : 1;
        g2.fillRect(x, y, 2, 12 * oy);
        g2.fillRect(x, y, 12 * ox, 2);
        g2.fillRect(x + 2 * ox, y + 2 * oy, 2, 2);
    }

    void drawMenuVirus(Graphics2D g2, int x, int y, int var, long t) {
        int wb = (int) (t / 220 + var * 17) % 3;
        int[][] vs = {{0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 0}};
        Color[] vc = {new Color(200, 30, 30, 130), new Color(155, 20, 175, 130), new Color(25, 135, 60, 130), new Color(195, 120, 20, 130)};
        Color c = vc[var % vc.length];
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 28));
        g2.fillOval(x - 8, y - 8, 56, 44);
        g2.setColor(c);
        for (int r = 0; r < vs.length; r++) {
            for (int col = 0; col < vs[r].length; col++) {
                if (vs[r][col] == 1) {
                    g2.fillRect(x + col * PX, y + r * PX, PX, PX);
                }
            }
        }
        int cx = x + 16, cy2 = y + 12;
        int[][] sp = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        for (int[] s : sp) {
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 95));
            g2.fillRect(cx + s[0] * (13 + wb) - PX / 2, cy2 + s[1] * (13 + wb) - PX / 2, PX + 1, PX + 1);
        }
        g2.setColor(new Color(255, 255, 255, 175));
        g2.fillRect(x + PX, y + PX, PX, PX);
        g2.fillRect(x + PX * 5, y + PX, PX, PX);
    }

    void drawSmallVirus(Graphics2D g2, int x, int y, int var, long t) {
        int wb = (int) (t / 300 + var * 11) % 2;
        Color[] vc = {new Color(200, 30, 30, 72), new Color(155, 20, 175, 72), new Color(25, 135, 60, 72), new Color(195, 120, 20, 72)};
        Color c = vc[var % vc.length];
        g2.setColor(c);
        g2.fillOval(x, y, 18, 14);
        int[][] sp = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] s : sp) {
            g2.fillRect(x + 9 + s[0] * (10 + wb) - 1, y + 7 + s[1] * (10 + wb) - 1, 3, 3);
        }
    }

    // =====================================================================
    // MAP SELECT
    // =====================================================================
    void drawMapSelect(Graphics2D g2) {
        g2.setColor(C_BG);
        g2.fillRect(0, 0, SW, SH);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.setColor(C_GRID);
                g2.fillRect(gx, gy, 2, 2);
            }
        }
        g2.setColor(new Color(0, 25, 18, 120));
        g2.fillRect(0, 0, SW, (int) (SH * 0.14));
        g2.setFont(pixelFont(26));
        FontMetrics fm = g2.getFontMetrics();
        drawGlowText(g2, "SELECCIONAR MAPA", SW / 2 - fm.stringWidth("SELECCIONAR MAPA") / 2, (int) (SH * 0.10), C_PATH_EDGE, 6);
        g2.setColor(C_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(80, (int) (SH * 0.12), SW - 80, (int) (SH * 0.12));

        String[] mNames = {"PASILLO RECTO", "SERPENTINA BIOLAB"};
        String[] mDesc = {"Camino recto clásico.", "Zigzag de alta tensión."};
        String[] mFlavr = {"Coloca torres a ambos lados del corredor.", "Ruta compleja. Enemigos especiales de mapa 2."};
        Color[] mClr = {C_PATH_EDGE, C_PATH2_EDGE};
        int mw = (int) (SW * 0.36), mh = (int) (SH * 0.54), mx1 = (int) (SW * 0.07), mx2 = (int) (SW * 0.57), my = (int) (SH * 0.16);
        for (int i = 0; i < 2; i++) {
            int bx = i == 0 ? mx1 : mx2;
            boolean hov = mouseX >= bx && mouseX <= bx + mw && mouseY >= my && mouseY <= my + mh;
            boolean sel = selectedMap == i;
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillRect(bx + 8, my + 8, mw, mh);
            g2.setColor(sel ? new Color(mClr[i].getRed(), mClr[i].getGreen(), mClr[i].getBlue(), 18) : new Color(7, 15, 12));
            g2.fillRect(bx, my, mw, mh);
            g2.setColor(sel ? mClr[i] : (hov ? new Color(mClr[i].getRed(), mClr[i].getGreen(), mClr[i].getBlue(), 150) : new Color(0, 55, 38)));
            g2.setStroke(new BasicStroke(sel ? PX : 2));
            g2.drawRect(bx, my, mw, mh);
            g2.setColor(sel ? mClr[i] : new Color(mClr[i].getRed() / 3, mClr[i].getGreen() / 3, mClr[i].getBlue() / 3));
            g2.fillRect(bx, my, mw, 5);
            drawMiniMap(g2, bx + 16, my + 16, mw - 32, (int) (mh * 0.52), i);
            g2.setColor(sel ? mClr[i] : C_UI_TEXT);
            g2.setFont(pixelFont(15));
            fm = g2.getFontMetrics();
            g2.drawString(mNames[i], bx + (mw - fm.stringWidth(mNames[i])) / 2, my + (int) (mh * 0.63));
            g2.setColor(new Color(130, 190, 150));
            g2.setFont(pixelFont(10));
            fm = g2.getFontMetrics();
            g2.drawString(mDesc[i], bx + (mw - fm.stringWidth(mDesc[i])) / 2, my + (int) (mh * 0.73));
            g2.setColor(new Color(100, 150, 120));
            g2.setFont(pixelFont(9));
            fm = g2.getFontMetrics();
            g2.drawString(mFlavr[i], bx + (mw - fm.stringWidth(mFlavr[i])) / 2, my + (int) (mh * 0.82));
            if (sel) {
                g2.setColor(mClr[i]);
                g2.setFont(pixelFont(11));
                fm = g2.getFontMetrics();
                g2.drawString("[SELECCIONADO]", bx + (mw - fm.stringWidth("[SELECCIONADO]")) / 2, my + (int) (mh * 0.92));
            }
        }
        int sbW = 300, sbH = 56, sbX = SW / 2 - sbW / 2, sbY = (int) (SH * 0.78);
        boolean sH = mouseX >= sbX && mouseX <= sbX + sbW && mouseY >= sbY && mouseY <= sbY + sbH;
        g2.setColor(sH ? new Color(0, 220, 100, 38) : new Color(7, 17, 12));
        g2.fillRect(sbX, sbY, sbW, sbH);
        g2.setColor(sH ? C_PATH_EDGE : new Color(0, 145, 80));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(sbX, sbY, sbW, sbH);
        g2.setColor(sH ? C_BG : C_UI_TEXT);
        g2.setFont(pixelFont(18));
        fm = g2.getFontMetrics();
        g2.drawString(">> INICIAR JUEGO <<", sbX + (sbW - fm.stringWidth(">> INICIAR JUEGO <<") / 2), sbY + 36);
        // Back
        int bkX = 20, bkY = (int) (SH * 0.90), bkW = 180, bkH = 40;
        boolean bkH2 = mouseX >= bkX && mouseX <= bkX + bkW && mouseY >= bkY && mouseY <= bkY + bkH;
        g2.setColor(bkH2 ? new Color(0, 180, 100, 28) : new Color(7, 17, 12));
        g2.fillRect(bkX, bkY, bkW, bkH);
        g2.setColor(bkH2 ? C_PATH_EDGE : new Color(0, 95, 58));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(bkX, bkY, bkW, bkH);
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(12));
        g2.drawString("< VOLVER", bkX + 28, bkY + 25);
    }

    void drawMiniMap(Graphics2D g2, int x, int y, int w, int h, int idx) {
        g2.setColor(new Color(9, 20, 16));
        g2.fillRect(x, y, w, h);
        g2.setColor(new Color(0, 45, 30));
        g2.drawRect(x, y, w, h);
        if (idx == 0) {
            int py = y + h / 2 - 16;
            g2.setColor(new Color(18, 42, 32));
            g2.fillRect(x + 5, py, w - 10, 32);
            g2.setColor(C_PATH_EDGE);
            g2.fillRect(x + 5, py, w - 10, 3);
            g2.fillRect(x + 5, py + 29, w - 10, 3);
            g2.setColor(new Color(0, 75, 48));
            for (int dx = x + 10; dx < x + w - 15; dx += 20) {
                g2.fillRect(dx, py + 14, 10, 3);
            }
            Color tc = new Color(0, 200, 100, 135);
            for (int tx2 = x + 22; tx2 < x + w - 22; tx2 += 34) {
                g2.setColor(tc);
                g2.fillRect(tx2, py - 14, 12, 12);
                g2.setColor(new Color(0, 220, 120));
                g2.drawRect(tx2, py - 14, 12, 12);
                g2.setColor(tc);
                g2.fillRect(tx2, py + 34, 12, 12);
                g2.setColor(new Color(0, 220, 120));
                g2.drawRect(tx2, py + 34, 12, 12);
            }
        } else {
            int mid = y + h / 2, topY = y + (int) (h * 0.15), botY = y + (int) (h * 0.85);
            int[] xs2 = {x + 5, (int) (x + w * 0.12), (int) (x + w * 0.25), (int) (x + w * 0.38), (int) (x + w * 0.51), (int) (x + w * 0.64), (int) (x + w * 0.77), (int) (x + w * 0.88), x + w - 5};
            int[] ys2 = {mid, botY, topY, botY, topY, botY, topY, mid, mid};
            // Glow
            g2.setStroke(new BasicStroke(18, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0, 80, 160, 45));
            for (int i = 0; i < xs2.length - 1; i++) {
                g2.drawLine(xs2[i], ys2[i], xs2[i + 1], ys2[i + 1]);
            }
            // Path
            g2.setStroke(new BasicStroke(14, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(C_PATH2);
            for (int i = 0; i < xs2.length - 1; i++) {
                g2.drawLine(xs2[i], ys2[i], xs2[i + 1], ys2[i + 1]);
            }
            g2.setStroke(new BasicStroke(2));
            g2.setColor(C_PATH2_EDGE);
            for (int i = 0; i < xs2.length - 1; i++) {
                g2.drawLine(xs2[i], ys2[i], xs2[i + 1], ys2[i + 1]);
            }
            g2.setStroke(new BasicStroke(PX));
        }
        g2.setColor(C_BASE);
        g2.fillRect(x + w - 18, y + h / 2 - 14, 14, 28);
        drawMedCross(g2, x + w - 11, y + h / 2, 10, C_HEALTH);
    }

    // =====================================================================
    // SETTINGS SCREEN
    // =====================================================================
    void drawSettings(Graphics2D g2) {
        g2.setColor(C_BG);
        g2.fillRect(0, 0, SW, SH);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.setColor(C_GRID);
                g2.fillRect(gx, gy, 2, 2);
            }
        }
        g2.setColor(new Color(0, 25, 18, 115));
        g2.fillRect(0, 0, SW, (int) (SH * 0.14));
        g2.setFont(pixelFont(26));
        FontMetrics fm = g2.getFontMetrics();
        drawGlowText(g2, "AJUSTES", SW / 2 - fm.stringWidth("AJUSTES") / 2, (int) (SH * 0.09), C_PATH_EDGE, 6);
        g2.setColor(C_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(80, (int) (SH * 0.12), SW - 80, (int) (SH * 0.12));

        // Difficulty
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(13));
        g2.drawString("DIFICULTAD:", SW / 2 - 210, (int) (SH * 0.20));
        String[] dn = {"FACIL", "NORMAL", "DIFICIL", "PESADILLA"};
        Color[] dc = {new Color(60, 200, 80), new Color(80, 190, 255), new Color(220, 60, 60), new Color(185, 40, 225)};
        Difficulty[] diffs = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE};
        int bw = 160, bh = 65, tot = 4 * bw + 60, sx = SW / 2 - tot / 2, by = (int) (SH * 0.23);
        for (int i = 0; i < 4; i++) {
            int bx = sx + i * (bw + 20);
            boolean sel = difficulty == diffs[i];
            boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh;
            g2.setColor(sel ? new Color(dc[i].getRed(), dc[i].getGreen(), dc[i].getBlue(), 32) : new Color(7, 15, 11));
            g2.fillRect(bx, by, bw, bh);
            g2.setColor(sel ? dc[i] : (hov ? new Color(dc[i].getRed(), dc[i].getGreen(), dc[i].getBlue(), 155) : new Color(0, 65, 42)));
            g2.setStroke(new BasicStroke(sel ? PX : 2));
            g2.drawRect(bx, by, bw, bh);
            g2.setColor(sel ? dc[i] : new Color(dc[i].getRed() / 3, dc[i].getGreen() / 3, dc[i].getBlue() / 3));
            g2.fillRect(bx, by, bw, 4);
            g2.setColor(sel ? dc[i] : C_UI_TEXT);
            g2.setFont(pixelFont(13));
            fm = g2.getFontMetrics();
            g2.drawString(dn[i], bx + (bw - fm.stringWidth(dn[i])) / 2, by + 26);
            // Tower unlock info
            String ul = i == 2 ? "Desbloquea 4 torres extra" : (i == 3 ? "Desbloquea las 6 torres" : "Torres basicas (1-3)");
            g2.setColor(new Color(90, 140, 110));
            g2.setFont(pixelFont(7));
            fm = g2.getFontMetrics();
            g2.drawString(ul, bx + (bw - fm.stringWidth(ul)) / 2, by + 42);
            if (sel) {
                g2.setColor(dc[i]);
                g2.setFont(pixelFont(9));
                fm = g2.getFontMetrics();
                g2.drawString("< ACTIVO >", bx + (bw - fm.stringWidth("< ACTIVO >")) / 2, by + 57);
            }
        }

        // Volume
        int vSlX = SW / 2 - 200, vSlY = (int) (SH * 0.52), vSlW = 400, vSlH = 24;
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(12));
        g2.drawString("VOLUMEN: " + volume + "%", vSlX, vSlY - 8);
        g2.setColor(new Color(8, 20, 14));
        g2.fillRect(vSlX, vSlY, vSlW, vSlH);
        g2.setColor(new Color(0, 175, 120));
        g2.fillRect(vSlX, vSlY, (int) (vSlW * volume / 100.0), vSlH);
        g2.setColor(C_UI_BORDER);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(vSlX, vSlY, vSlW, vSlH);
        // Vol buttons
        drawSmallBtn(g2, vSlX - 52, vSlY - 2, 44, 28, "VOL-", mouseX >= vSlX - 52 && mouseX <= vSlX - 8 && mouseY >= vSlY - 2 && mouseY <= vSlY + 26, new Color(220, 80, 60));
        drawSmallBtn(g2, vSlX + vSlW + 8, vSlY - 2, 44, 28, "VOL+", mouseX >= vSlX + vSlW + 8 && mouseX <= vSlX + vSlW + 52 && mouseY >= vSlY - 2 && mouseY <= vSlY + 26, new Color(60, 200, 100));

        // Sound on/off
        int snX = SW / 2 - 90, snY = (int) (SH * 0.62), snW = 180, snH = 38;
        boolean snHov = mouseX >= snX && mouseX <= snX + snW && mouseY >= snY && mouseY <= snY + snH;
        g2.setColor(soundEnabled ? new Color(0, 200, 100, 30) : new Color(200, 50, 50, 30));
        g2.fillRect(snX, snY, snW, snH);
        g2.setColor(soundEnabled ? new Color(0, 200, 100) : new Color(200, 50, 50));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(snX, snY, snW, snH);
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(12));
        fm = g2.getFontMetrics();
        String sLabel = soundEnabled ? "[SFX: ON ]" : "[SFX: OFF]";
        g2.drawString(sLabel, snX + (snW - fm.stringWidth(sLabel)) / 2, snY + 25);

        // Window toggle
        int wX = SW / 2 - 120, wY = (int) (SH * 0.72), wW = 240, wH = 38;
        boolean wHov = mouseX >= wX && mouseX <= wX + wW && mouseY >= wY && mouseY <= wY + wH;
        g2.setColor(wHov ? new Color(0, 200, 100, 28) : new Color(7, 15, 11));
        g2.fillRect(wX, wY, wW, wH);
        g2.setColor(wHov ? C_PATH_EDGE : new Color(0, 115, 68));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(wX, wY, wW, wH);
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(11));
        fm = g2.getFontMetrics();
        String wLbl = isFullScreen ? ">> PANTALLA COMPLETA <<" : ">> MODO VENTANA <<";
        g2.drawString(wLbl, wX + (wW - fm.stringWidth(wLbl)) / 2, wY + 25);

        // Back
        drawNavBtn(g2, "< VOLVER", SW / 2 - 110, (int) (SH * 0.86), 220, 46);
    }

    void drawSmallBtn(Graphics2D g2, int x, int y, int w, int h, String lbl, boolean hov, Color c) {
        g2.setColor(hov ? new Color(c.getRed(), c.getGreen(), c.getBlue(), 38) : new Color(7, 15, 11));
        g2.fillRect(x, y, w, h);
        g2.setColor(hov ? c : new Color(c.getRed() / 2, c.getGreen() / 2, c.getBlue() / 2));
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(x, y, w, h);
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(8));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(lbl, x + (w - fm.stringWidth(lbl)) / 2, y + h / 2 + 4);
    }

    void drawNavBtn(Graphics2D g2, String lbl, int x, int y, int w, int h) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g2.setColor(hov ? new Color(0, 180, 100, 28) : new Color(7, 15, 11));
        g2.fillRect(x, y, w, h);
        g2.setColor(hov ? C_PATH_EDGE : new Color(0, 95, 55));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(x, y, w, h);
        g2.setColor(hov ? C_BG : C_UI_TEXT);
        g2.setFont(pixelFont(14));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(lbl, x + (w - fm.stringWidth(lbl)) / 2, y + h / 2 + 5);
    }

    // =====================================================================
    // BESTIARY
    // =====================================================================
    // Enemy data for bestiary: {name, disease, hp, speed, reward, desc}
    static final String[][] BESTIARY_DATA = {
        {"RHINOVIRUS", "Resfriado Común", "50-800", "2", "15", "El patógeno más común. Rápido en propagarse pero frágil si se trata a tiempo."},
        {"INFLUENZAE", "Gripe Estacional", "35-600", "5", "28", "Virus de alta velocidad. Se mueve en ráfagas y evade defensas lentas."},
        {"STREPTOCOCCUS", "Angina Bacteriana", "90-1400", "1", "38", "Lleva un escudo proteico que absorbe el primer impacto. Resistente al frío."},
        {"MYCOBACTERIUM", "Tuberculosis", "350-5000", "1", "75", "Coraza blindada de alto grosor. Requiere ataques concentrados para penetrar."},
        {"BORRELIA", "Enfermedad de Lyme", "90-1400", "2", "62", "Se camufla con el entorno, volviéndose casi invisible intermitentemente."},
        {"PLASMODIUM", "Malaria", "90-1900", "2", "62", "Genera aliados cercanos cuando está en peligro. Destruye tus defensas."},
        {"HIV-7", "Inmunodeficiencia", "450-7000", "2", "165", "Muta al llegar al 50% de vida: acelera y cambia de comportamiento."},
        {"COLERA MAX", "Cólera Avanzado", "2800+", "1", "335", "Coloso masivo. Absorbe enormes cantidades de daño antes de caer."},
        {"ULTRAPOX", "Viruela Ultra", "2200+", "1", "480", "El jefe final de la primera era. Lleva corona de espinas tóxicas."},
        {"OMEGA-PLAG", "Pandemia Omega", "5000+", "1", "900", "Amenaza máxima. Resistente a todo excepto a una estrategia perfecta."},
        // Map 2 exclusives
        {"NANOBOT-X", "Infección Nano", "180-2800", "2", "110", "Destructor de defensas. Desactiva torres cercanas sistemáticamente."},
        {"PHANTOM-V", "Virus Fantasma", "120-1900", "4", "75", "Se teletransporta entre dimensiones, haciéndolo invulnerable en fases."},
        {"SWARM-CELL", "Célula Enjambre", "20-300", "6", "11", "Pequeño y veloz. Viene en grandes cantidades. Difícil de eliminar."},
        {"TITAN-BACT", "Bacteria Titán", "250-3800", "2", "90", "Armadura masiva de peptidoglicano. Necesita daño ácido o plasma."},};

    void drawBestiary(Graphics2D g2) {
        g2.setColor(C_BG);
        g2.fillRect(0, 0, SW, SH);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.setColor(C_GRID);
                g2.fillRect(gx, gy, 2, 2);
            }
        }
        // Header
        g2.setColor(new Color(0, 25, 18, 115));
        g2.fillRect(0, 0, SW, (int) (SH * 0.12));
        g2.setFont(pixelFont(24));
        drawGlowText(g2, "BESTIARIO — CÉLULAS ENEMIGAS", SW / 2 - g2.getFontMetrics().stringWidth("BESTIARIO — CÉLULAS ENEMIGAS") / 2, (int) (SH * 0.085), new Color(220, 100, 255), 6);
        g2.setColor(C_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(80, (int) (SH * 0.10), SW - 80, (int) (SH * 0.10));

        // Left: enemy list
        int listX = 20, listY = (int) (SH * 0.12), listW = (int) (SW * 0.30), listH = (int) (SH * 0.82);
        g2.setColor(new Color(4, 12, 10));
        g2.fillRect(listX, listY, listW, listH);
        g2.setColor(new Color(0, 110, 70));
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(listX, listY, listW, listH);

        int rowH = 46;
        for (int i = 0; i < BESTIARY_DATA.length; i++) {
            int ry = listY + 8 + i * rowH;
            if (ry + rowH > listY + listH - 8) {
                break;
            }
            boolean sel = bestiarySelected == i;
            boolean hov = mouseX >= listX + 4 && mouseX <= listX + listW - 4 && mouseY >= ry && mouseY <= ry + rowH - 4;
            g2.setColor(sel ? new Color(220, 100, 255, 25) : (hov ? new Color(220, 100, 255, 12) : new Color(0, 0, 0, 0)));
            g2.fillRect(listX + 4, ry, listW - 8, rowH - 4);
            g2.setColor(sel ? new Color(220, 100, 255) : new Color(0, 80, 55));
            g2.setStroke(new BasicStroke(sel ? 2 : 1));
            g2.drawRect(listX + 4, ry, listW - 8, rowH - 4);
            // Mini virus preview
            drawEnemyMini(g2, listX + 12, ry + 8, i);
            g2.setColor(sel ? new Color(220, 100, 255) : C_UI_TEXT);
            g2.setFont(pixelFont(10));
            g2.drawString(BESTIARY_DATA[i][0], listX + 44, ry + 16);
            g2.setColor(new Color(120, 180, 140));
            g2.setFont(pixelFont(8));
            g2.drawString(BESTIARY_DATA[i][1], listX + 44, ry + 30);
        }
        // Right panel: details
        int detX = listX + listW + 20, detY = listY, detW = SW - detX - 20, detH = listH;
        g2.setColor(new Color(4, 12, 20));
        g2.fillRect(detX, detY, detW, detH);
        g2.setColor(new Color(0, 100, 160));
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(detX, detY, detW, detH);

        String[] d = BESTIARY_DATA[bestiarySelected];
        // Big enemy render
        drawEnemyBig(g2, detX + 50, detY + 40, bestiarySelected);
        // Name
        g2.setFont(pixelFont(22));
        drawGlowText(g2, d[0], detX + 160, detY + 65, new Color(220, 100, 255), 5);
        g2.setColor(new Color(150, 210, 180));
        g2.setFont(pixelFont(11));
        g2.drawString("ENFERMEDAD: " + d[1], detX + 160, detY + 90);
        // Stats
        int sy2 = detY + 120;
        String[] sLbls = {"VIDA:", "VELOCIDAD:", "RECOMPENSA:", "MAPA:"};
        String[] sVals = {d[2] + " HP", d[3] + " u/t", "$" + d[4], bestiarySelected >= 10 ? "MAPA 2" : "AMBOS"};
        Color[] sClrs = {C_HEALTH, new Color(100, 220, 255), C_MONEY, new Color(120, 220, 120)};
        for (int i = 0; i < sLbls.length; i++) {
            g2.setColor(new Color(100, 160, 130));
            g2.setFont(pixelFont(10));
            g2.drawString(sLbls[i], detX + 20, sy2 + i * 24);
            g2.setColor(sClrs[i]);
            g2.setFont(pixelFont(11));
            g2.drawString(sVals[i], detX + 130, sy2 + i * 24);
        }
        // Stat bars
        double[] bars = {Double.parseDouble(d[2].split("-")[0].replace("+", "")) / 500.0,
            Double.parseDouble(d[3]) / 6.0, Double.parseDouble(d[4]) / 900.0};
        String[] bLbls = {"DURABILIDAD", "VELOCIDAD", "VALOR"};
        Color[] bClrs = {C_HEALTH, new Color(100, 220, 255), C_MONEY};
        for (int i = 0; i < bars.length; i++) {
            g2.setColor(new Color(8, 20, 14));
            g2.fillRect(detX + 20, sy2 + 110 + i * 22, detW - 40, 14);
            g2.setColor(bClrs[i]);
            g2.fillRect(detX + 20, sy2 + 110 + i * 22, (int) ((detW - 40) * Math.min(1, bars[i])), 14);
            g2.setColor(C_UI_BORDER);
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(detX + 20, sy2 + 110 + i * 22, detW - 40, 14);
            g2.setColor(new Color(80, 130, 100));
            g2.setFont(pixelFont(8));
            g2.drawString(bLbls[i], detX + 20, sy2 + 107 + i * 22);
        }
        // Description
        int descY = sy2 + 190;
        g2.setColor(new Color(6, 16, 30));
        g2.fillRect(detX + 16, descY - 18, detW - 32, 80);
        g2.setColor(new Color(0, 100, 160));
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(detX + 16, descY - 18, detW - 32, 80);
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(9));
        wrapText(g2, d[5], detX + 24, descY, detW - 48, 15);

        // Abilities / notes
        String[] notes = getEnemyNotes(bestiarySelected);
        g2.setColor(new Color(220, 100, 255));
        g2.setFont(pixelFont(10));
        g2.drawString("HABILIDADES:", detX + 20, descY + 90);
        g2.setColor(new Color(200, 160, 255));
        for (int i = 0; i < notes.length && i < 3; i++) {
            g2.setFont(pixelFont(8));
            g2.drawString("• " + notes[i], detX + 20, descY + 108 + i * 16);
        }

        // Nav
        drawNavBtn(g2, "< MENU", SW / 2 - 110, (int) (SH * 0.93), 220, 40);
    }

    String[] getEnemyNotes(int idx) {
        String[][] notes = {
            {"Resistente al fuego", "Vulnerable al hielo", "Sin habilidades especiales"},
            {"Alta velocidad de movimiento", "Rastro de daño cinético", "Vulnerable a sonido"},
            {"Escudo de proteínas (+60 HP)", "Inmune al primer impacto", "Reducida velocidad"},
            {"Armadura de peptidoglicano", "Absorbe primero la armadura", "Requiere daño masivo"},
            {"Se vuelve invisible periódicamente", "Invulnerable cuando oculto", "Difícil de rastrear"},
            {"Cura aliados cercanos", "Aura de regeneración", "Prioritario a eliminar"},
            {"Muta al 50% de vida", "Aumenta velocidad al mutar", "Doble amenaza"},
            {"Coloso de extrema durabilidad", "Daño masivo a la base (35HP)", "Solo jefe de oleada 20/30"},
            {"Jefe con corona tóxica", "Daño brutal a la base (50HP)", "Resistente a todo"},
            {"Pandemia Omega — jefe final", "Aura de debilitación", "Debe ser destruido con precisión"},
            {"Destruye torres cercanas", "Intervalo de destrucción: 90t", "Prioritario eliminar"},
            {"Fases de invulnerabilidad", "Alterna entre visible/oculto", "Torre de plasma recomendada"},
            {"Velocidad extrema (6)", "Viene en grandes números", "Baja vida pero difícil de detener"},
            {"Armadura doble capa", "Requiere romper armadura primero", "ACIDO y PLASMA son efectivos"},};
        return idx < notes.length ? notes[idx] : new String[]{"Sin datos", "", ""};
    }

    void wrapText(Graphics2D g, String txt, int x, int y, int maxW, int lineH) {
        String[] words = txt.split(" ");
        StringBuilder line = new StringBuilder();
        int ly = y;
        FontMetrics fm = g.getFontMetrics();
        for (String w : words) {
            String test = line + (line.length() > 0 ? " " : "") + w;
            if (fm.stringWidth(test) > maxW) {
                g.drawString(line.toString(), x, ly);
                line = new StringBuilder(w);
                ly += lineH;
            } else {
                line.append(line.length() > 0 ? " " : "").append(w);
            }
        }
        if (line.length() > 0) {
            g.drawString(line.toString(), x, ly);
        }
    }

    void drawEnemyMini(Graphics2D g2, int x, int y, int idx) {
        Color[] clr = {new Color(210, 40, 40), new Color(255, 160, 0), new Color(30, 140, 70), new Color(70, 70, 95),
            new Color(60, 60, 200), new Color(60, 200, 90), new Color(100, 40, 180), new Color(50, 120, 180),
            new Color(170, 20, 20), new Color(130, 0, 200),
            new Color(200, 20, 20), new Color(120, 60, 220), new Color(220, 100, 20), new Color(140, 140, 180)};
        Color c = clr[idx % clr.length];
        g2.setColor(c);
        int[][] vs = {{0, 1, 1, 0}, {1, 1, 1, 1}, {1, 1, 1, 1}, {0, 1, 1, 0}};
        for (int r = 0; r < vs.length; r++) {
            for (int col = 0; col < vs[r].length; col++) {
                if (vs[r][col] == 1) {
                    g2.fillRect(x + col * 3, y + r * 3, 3, 3);
                }
            }
        }
    }

    void drawEnemyBig(Graphics2D g2, int x, int y, int idx) {
        Color[] clr = {new Color(210, 40, 40), new Color(255, 160, 0), new Color(30, 140, 70), new Color(70, 70, 95),
            new Color(60, 60, 200), new Color(60, 200, 90), new Color(100, 40, 180), new Color(50, 120, 180),
            new Color(170, 20, 20), new Color(130, 0, 200),
            new Color(200, 20, 20), new Color(120, 60, 220), new Color(220, 100, 20), new Color(140, 140, 180)};
        Color c = clr[idx % clr.length];
        int sc = idx >= 7 ? 5 : 3;
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 28));
        g2.fillOval(x - 10, y - 10, idx >= 7 ? 100 : 65, idx >= 7 ? 80 : 55);
        g2.setColor(c);
        int[][] vs = {{0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 0}};
        for (int r = 0; r < vs.length; r++) {
            for (int col = 0; col < vs[r].length; col++) {
                if (vs[r][col] == 1) {
                    g2.fillRect(x + col * sc, y + r * sc, sc, sc);
                }
            }
        }
        g2.setColor(new Color(255, 255, 255, 175));
        g2.fillRect(x + sc, y + sc, sc, sc);
        g2.fillRect(x + sc * 5, y + sc, sc, sc);
        // Spikes
        int cx = x + sc * 4, cy2 = y + sc * 3;
        g2.setColor(new Color(c.getRed() + 40, c.getGreen() + 20, c.getBlue() + 20, 120));
        for (int[] sp : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            g2.fillRect(cx + sp[0] * (sc * 4) - sc / 2, cy2 + sp[1] * (sc * 4) - sc / 2, sc, sc);
        }
    }

    // =====================================================================
    // SCORE ENTRY & SCOREBOARD
    // =====================================================================
    void drawScoreEntry(Graphics2D g2) {
        g2.setColor(C_BG);
        g2.fillRect(0, 0, SW, SH);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.setColor(C_GRID);
                g2.fillRect(gx, gy, 2, 2);
            }
        }
        g2.setColor(gameWon ? new Color(0, 220, 120) : new Color(220, 50, 60));
        g2.setFont(pixelFont(52));
        String hdr = gameWon ? "¡VICTORIA!" : "GAME OVER";
        FontMetrics fm = g2.getFontMetrics();
        drawGlowText(g2, hdr, SW / 2 - fm.stringWidth(hdr) / 2, (int) (SH * 0.18), gameWon ? new Color(0, 220, 120) : new Color(220, 50, 60), 8);
        g2.setColor(C_SCORE);
        g2.setFont(pixelFont(20));
        fm = g2.getFontMetrics();
        g2.drawString("PUNTAJE: " + score, SW / 2 - fm.stringWidth("PUNTAJE: " + score) / 2, (int) (SH * 0.30));
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(14));
        fm = g2.getFontMetrics();
        g2.drawString("INGRESA TU NOMBRE (5 LETRAS):", SW / 2 - fm.stringWidth("INGRESA TU NOMBRE (5 LETRAS):") / 2, (int) (SH * 0.42));
        int nW = 300, nH = 68, nX = SW / 2 - nW / 2, nY = (int) (SH * 0.46);
        g2.setColor(new Color(7, 15, 11));
        g2.fillRect(nX, nY, nW, nH);
        g2.setColor(C_PATH_EDGE);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(nX, nY, nW, nH);
        long t = System.currentTimeMillis();
        boolean cur = (t / 500) % 2 == 0;
        g2.setFont(pixelFont(34));
        fm = g2.getFontMetrics();
        int cW = fm.stringWidth("X"), totW = cW * 5 + 16 * 4, scX = SW / 2 - totW / 2;
        for (int i = 0; i < 5; i++) {
            char ch = i < currentName.length() ? currentName.charAt(i) : (i == currentName.length() && cur ? '_' : '-');
            g2.setColor(i == currentName.length() && cur ? C_PATH_EDGE : (i < currentName.length() ? new Color(0, 255, 150) : new Color(0, 65, 42)));
            g2.drawString(String.valueOf(ch), scX + i * (cW + 16), nY + 46);
            g2.setColor(new Color(0, 85, 52));
            g2.fillRect(scX + i * (cW + 16), nY + 52, cW, 3);
        }
        if (currentName.length() == 5) {
            int cbW = 250, cbH = 50, cbX = SW / 2 - cbW / 2, cbY = (int) (SH * 0.63);
            boolean ch = mouseX >= cbX && mouseX <= cbX + cbW && mouseY >= cbY && mouseY <= cbY + cbH;
            g2.setColor(ch ? new Color(0, 200, 100, 38) : new Color(7, 15, 11));
            g2.fillRect(cbX, cbY, cbW, cbH);
            g2.setColor(ch ? C_PATH_EDGE : new Color(0, 135, 78));
            g2.setStroke(new BasicStroke(PX));
            g2.drawRect(cbX, cbY, cbW, cbH);
            g2.setColor(ch ? C_BG : C_UI_TEXT);
            g2.setFont(pixelFont(16));
            fm = g2.getFontMetrics();
            g2.drawString("CONFIRMAR [ENTER]", cbX + (cbW - fm.stringWidth("CONFIRMAR [ENTER]")) / 2, cbY + 32);
        }
    }

    void drawScoreboard(Graphics2D g2) {
        g2.setColor(C_BG);
        g2.fillRect(0, 0, SW, SH);
        for (int gx = 0; gx < SW; gx += 16) {
            for (int gy = 0; gy < SH; gy += 16) {
                g2.setColor(C_GRID);
                g2.fillRect(gx, gy, 2, 2);
            }
        }
        drawStar(g2, SW / 2 - 175, (int) (SH * 0.04), C_SCORE);
        drawStar(g2, SW / 2 + 152, (int) (SH * 0.04), C_SCORE);
        g2.setFont(pixelFont(30));
        drawGlowText(g2, "TABLA DE PUNTAJES", SW / 2 - g2.getFontMetrics().stringWidth("TABLA DE PUNTAJES") / 2, (int) (SH * 0.11), C_SCORE, 7);
        g2.setColor(C_UI_BORDER);
        g2.fillRect(80, (int) (SH * 0.13), SW - 160, PX);
        g2.setColor(new Color(0, 175, 100));
        g2.setFont(pixelFont(11));
        int ry = (int) (SH * 0.19);
        g2.drawString("#", 120, ry);
        g2.drawString("NOMBRE", 175, ry);
        g2.drawString("PUNTAJE", SW / 2 - 35, ry);
        g2.drawString("DIFIC", SW / 2 + 130, ry);
        g2.fillRect(80, ry + 5, SW - 160, 2);
        String[] dn = {"FACIL", "NORMAL", "DIFICIL", "PESADILLA"};
        Color[] dc = {new Color(60, 200, 80), new Color(80, 190, 255), new Color(220, 60, 60), new Color(185, 40, 225)};
        for (int i = 0; i < scoreCount; i++) {
            int row = ry + 26 + i * 30;
            g2.setColor(i % 2 == 0 ? new Color(7, 18, 12) : new Color(5, 11, 7));
            g2.fillRect(80, row - 16, SW - 160, 26);
            Color rk = i == 0 ? new Color(255, 200, 50) : i == 1 ? new Color(200, 200, 200) : i == 2 ? new Color(200, 120, 60) : new Color(100, 145, 115);
            g2.setColor(rk);
            g2.setFont(pixelFont(12));
            g2.drawString((i + 1) + ".", 120, row);
            g2.setColor(C_UI_TEXT);
            g2.drawString(scoreNames[i], 175, row);
            g2.setColor(C_SCORE);
            g2.drawString(String.valueOf(scoreValues[i]), SW / 2 - 35, row);
            int dv = scoreDifficulty[i];
            if (dv >= 0 && dv < dn.length) {
                g2.setColor(dc[dv]);
                g2.setFont(pixelFont(9));
                g2.drawString(dn[dv], SW / 2 + 130, row);
            }
        }
        if (scoreCount == 0) {
            g2.setColor(new Color(55, 90, 72));
            g2.setFont(pixelFont(12));
            g2.drawString("Sin puntajes aún. ¡Juega y gana!", SW / 2 - 155, (int) (SH * 0.5));
        }
        drawNavBtn(g2, "< MENU", SW / 2 - 110, (int) (SH * 0.88), 220, 46);
    }

    // =====================================================================
    // PAUSE MENU (overlay on game)
    // =====================================================================
    void drawPauseMenu(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, SW, SH);
        int pw = 480, ph = (int) (SH * 0.72), px2 = SW / 2 - pw / 2, py2 = SH / 2 - ph / 2;
        g2.setColor(new Color(4, 12, 22));
        g2.fillRect(px2, py2, pw, ph);
        g2.setColor(new Color(0, 160, 240));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(px2, py2, pw, ph);
        drawBracket(g2, px2, py2, new Color(0, 200, 255), false, false);
        drawBracket(g2, px2 + pw - 14, py2, new Color(0, 200, 255), true, false);
        drawBracket(g2, px2, py2 + ph - 14, new Color(0, 200, 255), false, true);
        drawBracket(g2, px2 + pw - 14, py2 + ph - 14, new Color(0, 200, 255), true, true);

        g2.setFont(pixelFont(32));
        drawGlowText(g2, "— PAUSA —", px2 + (pw - g2.getFontMetrics().stringWidth("— PAUSA —")) / 2, py2 + 48, new Color(0, 220, 255), 6);

        // Continue
        drawPauseBtn(g2, px2 + 40, py2 + 78, pw - 80, 44, "▶  CONTINUAR", new Color(0, 220, 100), 0);
        // Auto-start toggle
        String autoLbl = (autoStartRounds ? "[AUTO-INICIO: ON ]" : "[AUTO-INICIO: OFF]");
        drawPauseBtn(g2, px2 + 40, py2 + 134, pw - 80, 44, autoLbl, new Color(255, 200, 60), 1);
        // Volume down
        drawPauseBtn(g2, px2 + 40, py2 + 190, (pw - 80) / 2 - 4, 40, "VOL -", new Color(220, 80, 60), 2);
        drawPauseBtn(g2, px2 + (pw - 80) / 2 + 44, py2 + 190, (pw - 80) / 2 - 4, 40, "VOL +", new Color(60, 200, 100), 3);
        // Sound toggle
        drawPauseBtn(g2, px2 + 40, py2 + 242, pw - 80, 44, soundEnabled ? "[SFX ON ] APAGAR" : "[SFX OFF] ACTIVAR", new Color(180, 100, 255), 4);
        // Speed cycle
        Color[] spClr = {C_UI_TEXT, new Color(100, 220, 100), new Color(255, 200, 50), new Color(255, 80, 80)};
        drawPauseBtn(g2, px2 + 40, py2 + 298, pw - 80, 44, "VELOCIDAD: x" + gameSpeed + " (TAB para cambiar)", spClr[gameSpeed - 1], 5);
        // Volume display
        g2.setColor(new Color(8, 20, 14));
        g2.fillRect(px2 + 40, py2 + 355, pw - 80, 18);
        g2.setColor(new Color(0, 175, 120));
        g2.fillRect(px2 + 40, py2 + 355, (int) ((pw - 80) * volume / 100.0), 18);
        g2.setColor(C_UI_BORDER);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(px2 + 40, py2 + 355, pw - 80, 18);
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(8));
        g2.drawString("VOLUMEN: " + volume + "%", px2 + 44, py2 + 367);
        // Back to menu
        drawPauseBtn(g2, px2 + 40, py2 + 390, pw - 80, 44, "< VOLVER AL MENU", new Color(220, 80, 50), 6);
        // Info
        g2.setColor(new Color(70, 110, 90));
        g2.setFont(pixelFont(9));
        g2.drawString("[ESPACIO] Continuar   [ESC] Menú", px2 + (pw - g2.getFontMetrics().stringWidth("[ESPACIO] Continuar   [ESC] Menú")) / 2, py2 + ph - 14);
    }

    void drawPauseBtn(Graphics2D g2, int x, int y, int w, int h, String lbl, Color c, int id) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g2.setColor(hov ? new Color(c.getRed(), c.getGreen(), c.getBlue(), 32) : new Color(5, 12, 22));
        g2.fillRect(x, y, w, h);
        g2.setColor(hov ? c : new Color(c.getRed() / 2, c.getGreen() / 2, c.getBlue() / 2));
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(x, y, w, h);
        g2.setColor(hov ? c : C_UI_TEXT);
        g2.setFont(pixelFont(12));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(lbl, x + (w - fm.stringWidth(lbl)) / 2, y + h / 2 + 4);
    }

    // =====================================================================
    // GAME SCREEN
    // =====================================================================
    void drawGame(Graphics2D g2) {
        g2.setColor(C_BG);
        g2.fillRect(0, 0, SW, SH);
        g2.setColor(selectedMap == 0 ? C_GRID : new Color(0, 18, 40));
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
        for (Tower tw : towers) {
            tw.draw(g2);
        }
        for (Projectile p : projectiles) {
            p.draw(g2);
        }
        // Tower placement preview
        if (!gameOver && !gameWon && gameState == GameState.PLAYING) {
            boolean valid = isValidTowerPosition(mouseX, mouseY) && !towerExistsAt(mouseX, mouseY);
            String sel = selectedTower;
            // Check if unlocked
            String[] types = {"NORMAL", "FIRE", "ICE", "ELEC", "SONIC", "NANO", "PLASMA", "ACID"};
            for (int i = 0; i < types.length; i++) {
                if (types[i].equals(sel) && !towerUnlocked[i]) {
                    valid = false;
                }
            }
            g2.setColor(valid ? new Color(0, 200, 100, 48) : new Color(200, 50, 50, 48));
            g2.fillRect(mouseX - TOWER_SIZE / 2, mouseY - TOWER_SIZE / 2, TOWER_SIZE, TOWER_SIZE);
            g2.setColor(valid ? new Color(0, 200, 100, 155) : new Color(200, 50, 50, 155));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(mouseX - TOWER_SIZE / 2, mouseY - TOWER_SIZE / 2, TOWER_SIZE, TOWER_SIZE);
            if (valid) {
                int pr = (int) (SW * 0.14);
                g2.setColor(new Color(0, 200, 100, 10));
                g2.fillOval(mouseX - pr, mouseY - pr, pr * 2, pr * 2);
                g2.setColor(new Color(0, 200, 100, 38));
                g2.drawOval(mouseX - pr, mouseY - pr, pr * 2, pr * 2);
            }
        }
        drawTopUI(g2);
        drawBottomUI(g2);
        if (waitingToStart && !gameOver && !gameWon) {
            drawWaitingToStart(g2);
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
        g2.setColor(new Color(0, 48, 26, 12));
        g2.fillRect(0, UI_TOP_H, BASE_X, PATH_TOP - UI_TOP_H);
        g2.fillRect(0, PATH_BOT, BASE_X, UI_BOT_Y - PATH_BOT);
        g2.setColor(new Color(0, 30, 18));
        g2.fillRect(0, PATH_TOP - 4, SW, PATH_BOT - PATH_TOP + 8);
        g2.setColor(C_PATH);
        g2.fillRect(0, PATH_TOP, SW, PATH_BOT - PATH_TOP);
        for (int sx = 0; sx < SW; sx += 28) {
            g2.setColor(new Color(0, 44, 28));
            g2.fillRect(sx, PATH_TOP, 14, PATH_BOT - PATH_TOP);
        }
        int mid = (PATH_TOP + PATH_BOT) / 2;
        g2.setColor(new Color(0, 70, 42));
        for (int px2 = 0; px2 < SW; px2 += PX * 6) {
            g2.fillRect(px2, mid - PX / 2, PX * 3, PX);
        }
        g2.setColor(C_PATH_EDGE);
        for (int px2 = 0; px2 < SW; px2 += PX * 4) {
            g2.fillRect(px2, PATH_TOP, PX * 2, PX);
            g2.fillRect(px2, PATH_BOT - PX, PX * 2, PX);
        }
        // Medical cross markers along path
        for (int px2 = 80; px2 < SW - 80; px2 += 200) {
            g2.setColor(new Color(0, 120, 80, 40));
            drawMedCross(g2, px2, mid, 20, new Color(0, 140, 90, 50));
        }
        g2.setColor(new Color(0, 220, 120, 16));
        g2.fillRect(0, PATH_TOP - 2, SW, 5);
        g2.fillRect(0, PATH_BOT - 3, SW, 5);
        drawBase(g2);
    }

    void drawMapSerpentine(Graphics2D g2) {
        if (mapWaypoints == null) {
            buildMapWaypoints();
        }
        g2.setColor(new Color(5, 11, 22));
        g2.fillRect(0, UI_TOP_H, SW, UI_BOT_Y - UI_TOP_H);
        // Hex grid
        for (int gx = 0; gx < SW; gx += 28) {
            for (int gy = UI_TOP_H; gy < UI_BOT_Y; gy += 24) {
                g2.setColor(new Color(0, 35, 75, 38));
                g2.drawRect(gx, gy, 20, 18);
            }
        }
        // DNA helix along sides
        long t = System.currentTimeMillis();
        for (int gy = UI_TOP_H; gy < UI_BOT_Y; gy += 24) {
            int off = (int) ((t / 100 + gy) % 24);
            g2.setColor(new Color(0, 120, 200, 55));
            g2.fillRect(off, gy + 8, 8, 4);
            g2.fillRect(SW - off - 10, gy + 8, 8, 4);
        }
        int pw = 62;
        // Outer glow
        g2.setStroke(new BasicStroke(pw + 20, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(0, 55, 115, 38));
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            g2.drawLine(mapWaypoints[i][0], mapWaypoints[i][1], mapWaypoints[i + 1][0], mapWaypoints[i + 1][1]);
        }
        // Main path
        g2.setStroke(new BasicStroke(pw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(C_PATH2);
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            g2.drawLine(mapWaypoints[i][0], mapWaypoints[i][1], mapWaypoints[i + 1][0], mapWaypoints[i + 1][1]);
        }
        // Texture
        g2.setStroke(new BasicStroke(pw - 18, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(9, 18, 40));
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            g2.drawLine(mapWaypoints[i][0], mapWaypoints[i][1], mapWaypoints[i + 1][0], mapWaypoints[i + 1][1]);
        }
        // Medical markers on path
        g2.setStroke(new BasicStroke(pw - 32, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(0, 40, 80));
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            int mx2 = (mapWaypoints[i][0] + mapWaypoints[i + 1][0]) / 2, my2 = (mapWaypoints[i][1] + mapWaypoints[i + 1][1]) / 2;
            drawMedCross(g2, mx2, my2, 14, new Color(0, 80, 140, 55));
        }
        // Edges
        g2.setStroke(new BasicStroke(2));
        g2.setColor(C_PATH2_EDGE);
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            int dx = mapWaypoints[i + 1][0] - mapWaypoints[i][0], dy = mapWaypoints[i + 1][1] - mapWaypoints[i][1];
            double len = Math.hypot(dx, dy);
            if (len == 0) {
                continue;
            }
            int nx = (int) (-dy / len * pw / 2), ny = (int) (dx / len * pw / 2);
            g2.drawLine(mapWaypoints[i][0] + nx, mapWaypoints[i][1] + ny, mapWaypoints[i + 1][0] + nx, mapWaypoints[i + 1][1] + ny);
            g2.drawLine(mapWaypoints[i][0] - nx, mapWaypoints[i][1] - ny, mapWaypoints[i + 1][0] - nx, mapWaypoints[i + 1][1] - ny);
        }
        // Waypoint nodes
        g2.setStroke(new BasicStroke(PX));
        for (int[] wp : mapWaypoints) {
            g2.setColor(new Color(0, 90, 190, 72));
            g2.fillOval(wp[0] - 10, wp[1] - 10, 20, 20);
            g2.setColor(C_PATH2_EDGE);
            g2.drawOval(wp[0] - 10, wp[1] - 10, 20, 20);
            g2.fillRect(wp[0] - 2, wp[1] - 2, 4, 4);
        }
        // Arrows
        for (int i = 0; i < mapWaypoints.length - 1; i++) {
            int mx2 = (mapWaypoints[i][0] + mapWaypoints[i + 1][0]) / 2, my2 = (mapWaypoints[i][1] + mapWaypoints[i + 1][1]) / 2;
            double ang = Math.atan2(mapWaypoints[i + 1][1] - mapWaypoints[i][1], mapWaypoints[i + 1][0] - mapWaypoints[i][0]);
            g2.setColor(new Color(0, 155, 255, 55));
            int ax = (int) (mx2 + Math.cos(ang) * 10), ay = (int) (my2 + Math.sin(ang) * 10);
            int bx2 = (int) (mx2 + Math.cos(ang + 2.5) * 6), by2 = (int) (my2 + Math.sin(ang + 2.5) * 6);
            int cx2 = (int) (mx2 + Math.cos(ang - 2.5) * 6), cy2 = (int) (my2 + Math.sin(ang - 2.5) * 6);
            g2.fillPolygon(new int[]{ax, bx2, cx2}, new int[]{ay, by2, cy2}, 3);
        }
        g2.setStroke(new BasicStroke(PX));
        drawBase(g2);
    }

    void drawBase(Graphics2D g2) {
        int mid = (PATH_TOP + PATH_BOT) / 2, bH = PATH_BOT - PATH_TOP, bW = 55;
        g2.setColor(new Color(9, 24, 20));
        g2.fillRect(BASE_X - 8, PATH_TOP + 2, bW + 16, bH - 4);
        g2.setColor(new Color(0, 175, 100, 22));
        g2.fillRect(BASE_X - 14, PATH_TOP - 10, bW + 28, bH + 20);
        // Animated pulse
        long t = System.currentTimeMillis();
        int gA = (int) (Math.abs(Math.sin(t / 800.0)) * 25) + 8;
        g2.setColor(new Color(0, 200, 130, gA));
        g2.fillRect(BASE_X - 14, PATH_TOP - 10, bW + 28, bH + 20);
        g2.setColor(C_BASE);
        g2.fillRect(BASE_X, PATH_TOP + 6, bW, bH - 12);
        g2.setColor(new Color(175, 255, 195));
        for (int wy = PATH_TOP + 18; wy < PATH_BOT - 18; wy += 20) {
            g2.fillRect(BASE_X + 8, wy, 10, 8);
            g2.fillRect(BASE_X + 26, wy, 10, 8);
        }
        drawMedCross(g2, BASE_X + 27, mid, 32, C_MED_RED);
        g2.setColor(C_PATH_EDGE);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(BASE_X, PATH_TOP + 6, bW, bH - 12);
        // HP label
        g2.setColor(C_MED_WHITE);
        g2.setFont(pixelFont(8));
        g2.drawString("BASE", BASE_X + 6, PATH_TOP - 2);
    }

    // =====================================================================
    // TOP UI
    // =====================================================================
    void drawTopUI(Graphics2D g2) {
        g2.setColor(C_UI_BG);
        g2.fillRect(0, 0, SW, UI_TOP_H);
        g2.setColor(new Color(0, 195, 120, 65));
        g2.fillRect(0, 0, SW, 4);
        g2.setColor(C_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(0, UI_TOP_H, SW, UI_TOP_H);
        // Medical cross left
        drawMedCross(g2, 16, UI_TOP_H / 2, 18, C_MED_RED);
        int topY = UI_TOP_H / 2 + 5;
        drawPixelHeart(g2, 30, UI_TOP_H / 2 - 9, C_HEALTH);
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(13));
        g2.drawString("" + baseHealth, 55, topY);
        g2.setColor(C_UI_BORDER);
        g2.fillRect(100, 5, PX, UI_TOP_H - 10);
        drawPixelCoin(g2, 110, UI_TOP_H / 2 - 10);
        g2.setColor(C_MONEY);
        g2.setFont(pixelFont(13));
        g2.drawString("$" + money, 136, topY);
        g2.setColor(C_UI_BORDER);
        g2.fillRect(225, 5, PX, UI_TOP_H - 10);
        g2.setColor(C_WAVE);
        g2.setFont(pixelFont(13));
        g2.drawString("OL " + wave + "/" + MAX_WAVE, 235, topY);
        g2.setColor(C_UI_BORDER);
        g2.fillRect(355, 5, PX, UI_TOP_H - 10);
        drawStar(g2, 364, UI_TOP_H / 2 - 10, C_SCORE);
        g2.setColor(C_SCORE);
        g2.setFont(pixelFont(12));
        g2.drawString("" + score, 388, topY);
        // Difficulty tag
        Color[] dC = {new Color(60, 200, 80), new Color(80, 190, 255), new Color(220, 60, 60), new Color(185, 40, 225)};
        String[] dN = {"FAC", "NOR", "DIF", "PES"};
        g2.setColor(dC[difficulty.ordinal()]);
        g2.setFont(pixelFont(8));
        g2.drawString("[" + dN[difficulty.ordinal()] + "]", 470, topY);
        // Auto-start indicator
        if (autoStartRounds) {
            g2.setColor(new Color(255, 200, 60));
            g2.setFont(pixelFont(7));
            g2.drawString("[AUTO]", 520, topY);
        }
        // Wave progress bar
        int pX = 560, pW = SW - 560 - 380, pH = 14;
        g2.setColor(new Color(5, 18, 11));
        g2.fillRect(pX, UI_TOP_H / 2 - pH / 2, pW, pH);
        for (int seg = 0; seg < MAX_WAVE; seg++) {
            g2.setColor(seg < wave - 1 ? new Color(0, 145, 82) : seg == wave - 1 ? new Color(0, 215, 110) : new Color(11, 28, 18));
            g2.fillRect(pX + 2 + seg * (pW - 4) / MAX_WAVE, UI_TOP_H / 2 - pH / 2 + 2, (pW - 4) / MAX_WAVE - 1, pH - 4);
        }
        g2.setColor(C_UI_BORDER);
        g2.drawRect(pX, UI_TOP_H / 2 - pH / 2, pW, pH);
        g2.setColor(new Color(0, 195, 100));
        g2.setFont(pixelFont(6));
        g2.drawString("OLEADAS", pX + pW / 2 - 20, UI_TOP_H / 2 + 3);
        // Speed/pause/menu buttons
        drawSpeedBtn(g2);
        drawPlayPauseBtn(g2);
        drawMenuBtn(g2);
    }

    void drawSpeedBtn(Graphics2D g2) {
        int bW = 88, bH = 24, bx = SW - bW - 238, by = UI_TOP_H / 2 - bH / 2;
        boolean hov = mouseX >= bx && mouseX <= bx + bW && mouseY >= by && mouseY <= by + bH;
        Color[] spC = {C_UI_TEXT, new Color(100, 220, 100), new Color(255, 200, 50), new Color(255, 80, 80)};
        Color sc = spC[gameSpeed - 1];
        g2.setColor(hov ? new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), 38) : new Color(7, 17, 11));
        g2.fillRect(bx, by, bW, bH);
        g2.setColor(sc);
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(bx, by, bW, bH);
        g2.setFont(pixelFont(9));
        FontMetrics fm = g2.getFontMetrics();
        String sp = "x" + gameSpeed + " VEL";
        g2.drawString(sp, bx + (bW - fm.stringWidth(sp)) / 2, by + bH - 5);
    }

    void drawPlayPauseBtn(Graphics2D g2) {
        int bW = 102, bH = 24, bx = SW - bW - 126, by = UI_TOP_H / 2 - bH / 2;
        boolean hov = mouseX >= bx && mouseX <= bx + bW && mouseY >= by && mouseY <= by + bH;
        boolean act = waitingToStart || paused;
        if (act) {
            long t = System.currentTimeMillis();
            int g = (int) (Math.abs(Math.sin(t / 400.0)) * 38) + 12;
            g2.setColor(new Color(0, 220, 100, g));
            g2.fillRect(bx - 4, by - 4, bW + 8, bH + 8);
        }
        g2.setColor(hov ? new Color(0, 200, 100, 42) : new Color(7, 17, 11));
        g2.fillRect(bx, by, bW, bH);
        g2.setColor(hov ? C_PATH_EDGE : (act ? new Color(0, 200, 80) : new Color(0, 115, 68)));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(bx, by, bW, bH);
        g2.setColor(act ? new Color(0, 255, 120) : C_UI_TEXT);
        if (waitingToStart || paused) {
            for (int row = 0; row < 10; row++) {
                int w = 10 - Math.abs(row - 5);
                g2.fillRect(bx + 5, by + 3 + row, w, 1);
            }
            g2.setFont(pixelFont(9));
            g2.drawString("JUGAR", bx + 20, by + bH - 5);
        } else {
            g2.setColor(C_UI_TEXT);
            g2.fillRect(bx + 5, by + 6, 5, 12);
            g2.fillRect(bx + 14, by + 6, 5, 12);
            g2.setFont(pixelFont(9));
            g2.drawString("PAUSA", bx + 24, by + bH - 5);
        }
    }

    void drawMenuBtn(Graphics2D g2) {
        int bW = 102, bH = 24, bx = SW - bW - 10, by = UI_TOP_H / 2 - bH / 2;
        boolean hov = mouseX >= bx && mouseX <= bx + bW && mouseY >= by && mouseY <= by + bH;
        g2.setColor(hov ? new Color(200, 100, 50, 42) : new Color(7, 17, 11));
        g2.fillRect(bx, by, bW, bH);
        g2.setColor(hov ? new Color(255, 145, 78) : new Color(155, 68, 22));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(bx, by, bW, bH);
        g2.setColor(hov ? C_BG : new Color(255, 158, 98));
        g2.setFont(pixelFont(9));
        g2.drawString("< MENU", bx + 12, by + bH - 5);
    }

    int speedBtnX() {
        return SW - 88 - 238;
    }

    int ppBtnX() {
        return SW - 102 - 126;
    }

    int ppBtnY() {
        return UI_TOP_H / 2 - 12;
    }

    int menuBtnX() {
        return SW - 102 - 10;
    }

    int menuBtnY() {
        return UI_TOP_H / 2 - 12;
    }

    // =====================================================================
    // BOTTOM UI - towers with stats and upgrade button
    // =====================================================================
    // Tower data: {name, cost, dmg, atkSpd, range, desc}
    static final String[] TOWER_NAMES = {"EPINEFRINA", "IBUPROFENO", "ANALGÉSICO", "AMOXICILNA", "ULTRASONIDO", "NANOTECNOL", "PLASMA RX", "ÁCIDO VX"};
    static final int[] TOWER_COSTS = {50, 70, 60, 80, 90, 110, 130, 100};
    static final int[] TOWER_DMG = {17, 30, 8, 24, 13, 38, 44, 20};
    static final int[] TOWER_SPD = {20, 18, 22, 16, 30, 12, 25, 20}; // cooldown (lower=faster)
    static final String[] TOWER_DESC = {
        "Balanceado. Daño moderado.",
        "Quema continua. AoE leve.",
        "Hielo: ralentiza enemi.",
        "Cadena eléctrica x2-3.",
        "Onda sónica de área.",
        "Disparo rápido nano.",
        "AoE explosión plasma.",
        "Reduce vida máxima."
    };
    static final String[] TOWER_TYPES = {"NORMAL", "FIRE", "ICE", "ELEC", "SONIC", "NANO", "PLASMA", "ACID"};
    static final Color[] TOWER_COLORS = {
        new Color(180, 255, 200), new Color(255, 120, 60), new Color(80, 200, 255),
        new Color(255, 230, 60), new Color(255, 80, 200), new Color(80, 255, 200),
        new Color(200, 80, 255), new Color(180, 255, 80)
    };

    void drawBottomUI(Graphics2D g2) {
        g2.setColor(C_UI_BG);
        g2.fillRect(0, UI_BOT_Y, SW, UI_BOT_H);
        g2.setColor(new Color(0, 195, 120, 65));
        g2.fillRect(0, UI_BOT_Y, SW, 4);
        g2.setColor(C_UI_BORDER);
        g2.setStroke(new BasicStroke(PX));
        g2.drawLine(0, UI_BOT_Y, SW, UI_BOT_Y);
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(8));
        g2.drawString("[ DEFENSAS MÉDICAS ]  Click en zona válida para colocar  |  [1-8] Seleccionar", 10, UI_BOT_Y + 14);

        int bW = (SW - 20) / 8 - 6, bH = UI_BOT_H - 22;
        for (int i = 0; i < TOWER_TYPES.length; i++) {
            boolean sel = selectedTower.equals(TOWER_TYPES[i]);
            boolean unlk = towerUnlocked[i];
            int bx = 10 + i * (bW + 6), by2 = UI_BOT_Y + 18;
            boolean hov = mouseX >= bx && mouseX <= bx + bW && mouseY >= by2 && mouseY <= by2 + bH;

            if (sel) {
                g2.setColor(new Color(TOWER_COLORS[i].getRed(), TOWER_COLORS[i].getGreen(), TOWER_COLORS[i].getBlue(), 22));
                g2.fillRect(bx, by2, bW, bH);
                g2.setColor(TOWER_COLORS[i]);
                g2.setStroke(new BasicStroke(PX));
                g2.drawRect(bx, by2, bW, bH);
                g2.fillRect(bx, by2, bW, 3);
            } else {
                g2.setColor(hov ? new Color(18, 38, 26) : new Color(12, 26, 18));
                g2.fillRect(bx, by2, bW, bH);
                g2.setColor(hov ? new Color(TOWER_COLORS[i].getRed() / 2, TOWER_COLORS[i].getGreen() / 2, TOWER_COLORS[i].getBlue() / 2) : new Color(0, 50, 34));
                g2.setStroke(new BasicStroke(hov ? PX : 1));
                g2.drawRect(bx, by2, bW, bH);
            }

            if (!unlk) {
                // Locked overlay
                g2.setColor(new Color(0, 0, 0, 165));
                g2.fillRect(bx, by2, bW, bH);
                g2.setColor(new Color(180, 40, 40));
                g2.setFont(pixelFont(9));
                g2.drawString("BLOQ", bx + bW / 2 - 14, by2 + bH / 2 - 6);
                String ureq = i <= 6 ? "DIFICIL" : "PESADILLA";
                g2.setColor(new Color(140, 60, 60));
                g2.setFont(pixelFont(7));
                g2.drawString(ureq, bx + bW / 2 - g2.getFontMetrics().stringWidth(ureq) / 2, by2 + bH / 2 + 8);
                continue;
            }

            // Icon
            drawTowerIcon(g2, bx + 4, by2 + 6, TOWER_TYPES[i], TOWER_COLORS[i], 32);
            // Name
            g2.setColor(sel ? TOWER_COLORS[i] : C_UI_TEXT);
            g2.setFont(pixelFont(7));
            g2.drawString(TOWER_NAMES[i], bx + 40, by2 + 14);
            // Key
            g2.setColor(new Color(65, 115, 85));
            g2.setFont(pixelFont(6));
            g2.drawString("[" + (i + 1) + "]", bx + 40, by2 + 24);
            // Cost
            g2.setColor(C_MONEY);
            g2.setFont(pixelFont(9));
            g2.drawString("$" + TOWER_COSTS[i], bx + 40, by2 + 37);
            // Stats
            g2.setColor(new Color(100, 180, 140));
            g2.setFont(pixelFont(7));
            g2.drawString("DMG:" + TOWER_DMG[i], bx + 4, by2 + 52);
            g2.drawString("SPD:" + (30 - TOWER_SPD[i] / 2), bx + 4, by2 + 62);
            // Upgrade button (only first 4, upgradeable)
            if (i < 4) {
                int upX = bx + 2, upY = by2 + bH - 22, upW = bW - 4, upH = 18;
                boolean upHov = mouseX >= upX && mouseX <= upX + upW && mouseY >= upY && mouseY <= upY + upH;
                int upgLvl = getTowerUpgradeAvgLevel(i);
                if (upgLvl < 2) {
                    g2.setColor(upHov ? new Color(255, 200, 60, 55) : new Color(8, 18, 12));
                    g2.fillRect(upX, upY, upW, upH);
                    g2.setColor(upHov ? new Color(255, 200, 60) : new Color(180, 140, 30));
                    g2.setStroke(new BasicStroke(1));
                    g2.drawRect(upX, upY, upW, upH);
                    g2.setColor(upHov ? new Color(255, 220, 100) : new Color(180, 150, 60));
                    g2.setFont(pixelFont(7));
                    String uLbl = "MEJORAR $" + (50 + (upgLvl * 75));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(uLbl, upX + (upW - fm.stringWidth(uLbl)) / 2, upY + 13);
                } else {
                    g2.setColor(new Color(60, 200, 80, 28));
                    g2.fillRect(upX, upY, upW, upH);
                    g2.setColor(new Color(60, 200, 80));
                    g2.setFont(pixelFont(7));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString("MAX", upX + (upW - fm.stringWidth("MAX")) / 2, upY + 13);
                }
            }
            // Desc
            g2.setColor(new Color(110, 175, 135, 210));
            g2.setFont(pixelFont(6));
            g2.drawString(TOWER_DESC[i], bx + 4, by2 + bH - (i < 4 ? 26 : 8));

            if (money < TOWER_COSTS[i]) {
                g2.setColor(new Color(135, 28, 28, 120));
                g2.fillRect(bx, by2, bW, bH);
                g2.setColor(new Color(195, 55, 55));
                g2.setFont(pixelFont(7));
                g2.drawString("SIN $", bx + bW / 2 - 14, by2 + bH - 10);
            }
        }
    }

    // Get average upgrade level for selected tower type (across placed towers)
    int getTowerUpgradeAvgLevel(int typeIdx) {
        String tp = TOWER_TYPES[typeIdx];
        int max = 0;
        for (Tower tw : towers) {
            if (tw.type.equals(tp)) {
                max = Math.max(max, tw.upgradeLevel);
            }
        }
        return max;
    }

    void drawWaitingToStart(Graphics2D g2) {
        int bY = (PATH_TOP + PATH_BOT) / 2 - 52;
        g2.setColor(new Color(0, 0, 0, 145));
        g2.fillRect(0, bY, SW, 104);
        long t = System.currentTimeMillis();
        int al = (int) (Math.abs(Math.sin(t / 600.0)) * 75) + 135;
        g2.setColor(new Color(0, 220, 100, al));
        g2.setFont(pixelFont(22));
        String msg = ">>> Presiona JUGAR para Oleada " + wave + (autoStartRounds ? " (AUTO en " + (AUTO_WAVE_DELAY - autoWaveTimer) / 35 + "s)" : "") + " <<<";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, SW / 2 - fm.stringWidth(msg) / 2, bY + 44);
        g2.setColor(new Color(180, 255, 200, 155));
        g2.setFont(pixelFont(9));
        g2.drawString("(Coloca torres — [P] pausa — click ▶ para empezar)", SW / 2 - 180, bY + 68);
    }

    void drawEndScreen(Graphics2D g2, boolean won) {
        g2.setColor(new Color(0, 0, 0, 185));
        g2.fillRect(0, 0, SW, SH);
        g2.setFont(pixelFont(48));
        String hdr = won ? "¡PANDEMIA ERRADICADA!" : "GAME OVER";
        drawGlowText(g2, hdr, SW / 2 - g2.getFontMetrics().stringWidth(hdr) / 2, SH / 2 - 78, won ? new Color(0, 220, 120) : new Color(220, 50, 60), 8);
        g2.setColor(C_SCORE);
        g2.setFont(pixelFont(20));
        String sc = "Puntaje: " + score;
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(sc, SW / 2 - fm.stringWidth(sc) / 2, SH / 2 - 26);
        g2.setColor(C_UI_TEXT);
        g2.setFont(pixelFont(13));
        String msg2 = won ? "¡Sobreviviste " + MAX_WAVE + " oleadas!  Vida restante: " + baseHealth : "Base destruida en oleada " + wave;
        fm = g2.getFontMetrics();
        g2.drawString(msg2, SW / 2 - fm.stringWidth(msg2) / 2, SH / 2 + 8);
        drawEndBtn(g2, SW / 2 - 155, SH / 2 + 36, 310, 52, "INGRESAR PUNTAJE", new Color(0, 200, 100));
        drawEndBtn(g2, SW / 2 - 155, SH / 2 + 100, 310, 48, "VOLVER AL MENÚ", new Color(220, 100, 50));
    }

    void drawEndBtn(Graphics2D g2, int x, int y, int w, int h, String lbl, Color c) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g2.setColor(hov ? new Color(c.getRed(), c.getGreen(), c.getBlue(), 38) : new Color(7, 17, 11));
        g2.fillRect(x, y, w, h);
        g2.setColor(hov ? c : new Color(c.getRed() / 2, c.getGreen() / 2, c.getBlue() / 2));
        g2.setStroke(new BasicStroke(PX));
        g2.drawRect(x, y, w, h);
        g2.setColor(hov ? C_BG : C_UI_TEXT);
        g2.setFont(pixelFont(15));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(lbl, x + (w - fm.stringWidth(lbl)) / 2, y + h / 2 + 5);
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
    // GAME LOOP
    // =====================================================================
    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState == GameState.PAUSE_MENU) {
            repaint();
            return;
        }
        if (gameState != GameState.PLAYING || gameOver || gameWon || paused) {
            // Auto-wave counter
            if (gameState == GameState.PLAYING && waitingToStart && autoStartRounds && !gameOver && !gameWon) {
                autoWaveTimer++;
                if (autoWaveTimer >= AUTO_WAVE_DELAY) {
                    launchWave();
                    autoWaveTimer = 0;
                }
            }
            repaint();
            return;
        }
        if (waitingToStart && autoStartRounds) {
            autoWaveTimer++;
            if (autoWaveTimer >= AUTO_WAVE_DELAY) {
                launchWave();
                autoWaveTimer = 0;
            }
        }
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
                int dmg = (en instanceof FinalBoss) ? 55 : (en instanceof UltraVirus) ? 45 : (en instanceof ColossusEnemy) ? 38 : (en instanceof TankEnemy || en instanceof ArmoredEnemy) ? 22 : (en instanceof BossEnemy) ? 32 : 12;
                baseHealth -= dmg;
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
                    for (Enemy a : enemies) {
                        if (a != h && Math.hypot(h.x - a.x, h.y - a.y) < 125 && a.health < a.maxHealth) {
                            a.health = Math.min(a.maxHealth, a.health + 15);
                        }
                    }
                }
            }
        }
        // Tower destroyer
        for (Enemy en : enemies) {
            if (en instanceof TowerDestroyerEnemy) {
                TowerDestroyerEnemy td = (TowerDestroyerEnemy) en;
                td.destroyTick++;
                if (td.destroyTick > 90) {
                    td.destroyTick = 0;
                    Tower cl = null;
                    double bd = Double.MAX_VALUE;
                    for (Tower tw : towers) {
                        double d = Math.hypot(td.x - tw.x, td.y - tw.y);
                        if (d < 125 && d < bd) {
                            bd = d;
                            cl = tw;
                        }
                    }
                    if (cl != null) {
                        towers.remove(cl);
                        sfxTowerDestroyed();
                    }
                }
            }
        }
        for (Tower tw : towers) {
            tw.update(enemies, projectiles);
        }
        List<Enemy> rem = new ArrayList<>();
        for (Enemy en : enemies) {
            if (en.health <= 0) {
                rem.add(en);
                money += en.reward;
                score += (int) (en.reward * scoreMult());
                if (en instanceof BossEnemy || en instanceof FinalBoss || en instanceof ColossusEnemy || en instanceof UltraVirus) {
                    sfxBossDeath();
                } else {
                    sfxEnemyDeath();
                }
            }
        }
        enemies.removeAll(rem);
        if (enemies.isEmpty() && !gameOver) {
            if (wave >= MAX_WAVE) {
                score += (int) (baseHealth * 55 * scoreMult());
                gameWon = true;
                sfxVictory();
            } else {
                wave++;
                money += waveBonus();
                prepareWave();
                waitingToStart = true;
                autoWaveTimer = 0;
            }
        }
    }

    // =====================================================================
    // MOUSE EVENTS
    // =====================================================================
    @Override
    public void mouseClicked(MouseEvent e) {
        int mx = e.getX(), my = e.getY();

        if (gameState == GameState.MENU) {
            int bW = 360, bH = 56, bx0 = SW / 2 - bW / 2;
            // Recalc button positions same as drawMenu
            int pH = (int) (SH * 0.80), pY = (SH - pH) / 2;
            int[] byA = {pY + (int) (pH * 0.38), pY + (int) (pH * 0.50), pY + (int) (pH * 0.62), pY + (int) (pH * 0.74), pY + (int) (pH * 0.86)};
            for (int i = 0; i < 5; i++) {
                if (mx >= bx0 && mx <= bx0 + bW && my >= byA[i] && my <= byA[i] + bH) {
                    sfxMenuClick();
                    switch (i) {
                        case 0:
                            gameState = GameState.MAP_SELECT;
                            break;
                        case 1:
                            gameState = GameState.SETTINGS;
                            startSettingsMusic();
                            break;
                        case 2:
                            gameState = GameState.BESTIARY;
                            break;
                        case 3:
                            gameState = GameState.SCOREBOARD;
                            startScoreboardMusic();
                            break;
                        case 4:
                            stopBGMusic();
                            System.exit(0);
                            break;
                    }
                    return;
                }
            }
        }

        if (gameState == GameState.MAP_SELECT) {
            int mW = (int) (SW * 0.36), mH = (int) (SH * 0.54), mx1 = (int) (SW * 0.07), mx2 = (int) (SW * 0.57), myB = (int) (SH * 0.16);
            if (mx >= mx1 && mx <= mx1 + mW && my >= myB && my <= myB + mH) {
                sfxMenuClick();
                selectedMap = 0;
            } else if (mx >= mx2 && mx <= mx2 + mW && my >= myB && my <= myB + mH) {
                sfxMenuClick();
                selectedMap = 1;
            }
            int sbX = SW / 2 - 150, sbY = (int) (SH * 0.78), sbW = 300, sbH = 56;
            if (mx >= sbX && mx <= sbX + sbW && my >= sbY && my <= sbY + sbH) {
                sfxMenuClick();
                startGame();
            }
            if (mx >= 20 && mx <= 200 && my >= (int) (SH * 0.90) && my <= (int) (SH * 0.90) + 40) {
                sfxMenuClick();
                gameState = GameState.MENU;
            }
        }

        if (gameState == GameState.SETTINGS) {
            Difficulty[] diffs = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE};
            int bW = 160, bH = 65, tot = 4 * bW + 60, sx = SW / 2 - tot / 2, by = (int) (SH * 0.23);
            for (int i = 0; i < 4; i++) {
                int bx = sx + i * (bW + 20);
                if (mx >= bx && mx <= bx + bW && my >= by && my <= by + bH) {
                    sfxMenuClick();
                    difficulty = diffs[i];
                    unlockTowers();
                }
            }
            // Vol- btn
            int vSlX = SW / 2 - 200, vSlY = (int) (SH * 0.52);
            if (mx >= vSlX - 52 && mx <= vSlX - 8 && my >= vSlY - 2 && my <= vSlY + 26) {
                volume = Math.max(0, volume - 10);
                setChannelVolumes();
            }
            if (mx >= vSlX + 400 + 8 && mx <= vSlX + 400 + 52 && my >= vSlY - 2 && my <= vSlY + 26) {
                volume = Math.min(100, volume + 10);
                setChannelVolumes();
            }
            // Vol slider drag
            if (mx >= vSlX && mx <= vSlX + 400 && my >= vSlY && my <= vSlY + 24) {
                volume = (int) ((mx - vSlX) * 100.0 / 400);
                setChannelVolumes();
            }
            // Sound toggle
            int snX = SW / 2 - 90, snY = (int) (SH * 0.62), snW = 180, snH = 38;
            if (mx >= snX && mx <= snX + snW && my >= snY && my <= snY + snH) {
                sfxMenuClick();
                soundEnabled = !soundEnabled;
                setChannelVolumes();
            }
            // Window toggle
            int wX = SW / 2 - 120, wY = (int) (SH * 0.72), wW = 240, wH = 38;
            if (mx >= wX && mx <= wX + wW && my >= wY && my <= wY + wH) {
                sfxMenuClick();
                toggleWindowMode();
            }
            // Back
            int bkX = SW / 2 - 110, bkY = (int) (SH * 0.86), bkW = 220, bkH = 46;
            if (mx >= bkX && mx <= bkX + bkW && my >= bkY && my <= bkY + bkH) {
                sfxMenuClick();
                gameState = GameState.MENU;
                startMenuMusic();
            }
        }

        if (gameState == GameState.BESTIARY) {
            int listX = 20, listY = (int) (SH * 0.12), listW = (int) (SW * 0.30);
            int rowH = 46;
            for (int i = 0; i < BESTIARY_DATA.length; i++) {
                int ry = listY + 8 + i * rowH;
                if (mx >= listX + 4 && mx <= listX + listW - 4 && my >= ry && my <= ry + rowH - 4) {
                    bestiarySelected = i;
                    sfxMenuClick();
                }
            }
            // Back
            if (mx >= SW / 2 - 110 && mx < SW / 2 + 110 && my >= (int) (SH * 0.93) && my <= (int) (SH * 0.93) + 40) {
                sfxMenuClick();
                gameState = GameState.MENU;
                startMenuMusic();
            }
        }

        if (gameState == GameState.SCOREBOARD) {
            if (mx >= SW / 2 - 110 && mx <= SW / 2 + 110 && my >= (int) (SH * 0.88) && my <= (int) (SH * 0.88) + 46) {
                sfxMenuClick();
                gameState = GameState.MENU;
                startMenuMusic();
            }
        }

        if (gameState == GameState.SCORE_ENTRY) {
            if (currentName.length() == 5) {
                int cbX = SW / 2 - 125, cbY = (int) (SH * 0.63), cbW = 250, cbH = 50;
                if (mx >= cbX && mx <= cbX + cbW && my >= cbY && my <= cbY + cbH) {
                    confirmScore();
                }
            }
        }

        if (gameState == GameState.PAUSE_MENU) {
            int pw = 480, ph = (int) (SH * 0.72), px2 = SW / 2 - pw / 2, py2 = SH / 2 - ph / 2;
            // Continue
            if (inBtn(mx, my, px2 + 40, py2 + 78, pw - 80, 44)) {
                sfxMenuClick();
                paused = false;
                gameState = GameState.PLAYING;
            }
            // Auto-start
            if (inBtn(mx, my, px2 + 40, py2 + 134, pw - 80, 44)) {
                autoStartRounds = !autoStartRounds;
                sfxMenuClick();
            }
            // Vol-
            if (inBtn(mx, my, px2 + 40, py2 + 190, (pw - 80) / 2 - 4, 40)) {
                volume = Math.max(0, volume - 10);
                setChannelVolumes();
            }
            // Vol+
            if (inBtn(mx, my, px2 + (pw - 80) / 2 + 44, py2 + 190, (pw - 80) / 2 - 4, 40)) {
                volume = Math.min(100, volume + 10);
                setChannelVolumes();
            }
            // Sound
            if (inBtn(mx, my, px2 + 40, py2 + 242, pw - 80, 44)) {
                soundEnabled = !soundEnabled;
                setChannelVolumes();
                sfxMenuClick();
            }
            // Back to menu
            if (inBtn(mx, my, px2 + 40, py2 + 390, pw - 80, 44)) {
                sfxMenuClick();
                stopBGMusic();
                gameState = GameState.MENU;
                startMenuMusic();
                paused = false;
            }
            return;
        }

        if (gameState == GameState.PLAYING) {
            if (gameOver || gameWon) {
                if (inBtn(mx, my, SW / 2 - 155, SH / 2 + 36, 310, 52)) {
                    currentName = "";
                    gameState = GameState.SCORE_ENTRY;
                    return;
                }
                if (inBtn(mx, my, SW / 2 - 155, SH / 2 + 100, 310, 48)) {
                    stopBGMusic();
                    gameState = GameState.MENU;
                    startMenuMusic();
                    return;
                }
                return;
            }
            // Menu btn
            if (inBtn(mx, my, menuBtnX(), menuBtnY(), 102, 24)) {
                sfxMenuClick();
                stopBGMusic();
                gameState = GameState.MENU;
                startMenuMusic();
                return;
            }
            // Play/Pause btn
            if (inBtn(mx, my, ppBtnX(), ppBtnY(), 102, 24)) {
                sfxMenuClick();
                if (waitingToStart) {
                    launchWave();
                } else {
                    paused = true;
                    gameState = GameState.PAUSE_MENU;
                }
                return;
            }
            // Speed btn
            if (inBtn(mx, my, speedBtnX(), ppBtnY(), 88, 24)) {
                gameSpeed = (gameSpeed % 4) + 1;
                return;
            }
            // Tower buttons
            int bW = (SW - 20) / 8 - 6, bH = UI_BOT_H - 22;
            for (int i = 0; i < TOWER_TYPES.length; i++) {
                int bx = 10 + i * (bW + 6), by2 = UI_BOT_Y + 18;
                if (inBtn(mx, my, bx, by2, bW, bH)) {
                    if (towerUnlocked[i]) {
                        selectedTower = TOWER_TYPES[i];
                    }
                    return;
                }
                // Upgrade button check (first 4 types)
                if (i < 4 && towerUnlocked[i]) {
                    int upX = bx + 2, upY = by2 + bH - 22, upW = bW - 4, upH = 18;
                    if (inBtn(mx, my, upX, upY, upW, upH)) {
                        int upLvl = getTowerUpgradeAvgLevel(i);
                        int upCost = 50 + upLvl * 75;
                        if (upLvl < 2 && money >= upCost) {
                            money -= upCost;
                            for (Tower tw : towers) {
                                if (tw.type.equals(TOWER_TYPES[i]) && tw.upgradeLevel == upLvl) {
                                    tw.upgradeLevel++;
                                }
                            }
                            sfxUpgrade();
                        }
                        return;
                    }
                }
            }
            // Place tower
            int cost = 50;
            for (int i = 0; i < TOWER_TYPES.length; i++) {
                if (TOWER_TYPES[i].equals(selectedTower)) {
                    cost = TOWER_COSTS[i];
                    break;
                }
            }
            boolean unlocked = false;
            for (int i = 0; i < TOWER_TYPES.length; i++) {
                if (TOWER_TYPES[i].equals(selectedTower) && towerUnlocked[i]) {
                    unlocked = true;
                    break;
                }
            }
            if (unlocked && money >= cost && isValidTowerPosition(mx, my) && !towerExistsAt(mx, my)) {
                towers.add(new Tower(mx, my, selectedTower));
                money -= cost;
                sfxTowerPlaced();
            }
        }
    }

    boolean inBtn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    void confirmScore() {
        addScore(currentName.toUpperCase(), score, difficulty.ordinal());
        gameState = GameState.SCOREBOARD;
        startScoreboardMusic();
    }

    void startSettingsMusic() {
        if (bgPlaying && currentMusicCtx.equals("settings")) {
            return;
        }
        stopBGMusic();
        currentMusicCtx = "settings";
        bgPlaying = true;
        final TDX self = this;
        bgThread = new Thread(() -> {
            int[] mel = {60, 62, 64, 60, 62, 65, 64, 62, 60, 64, 67, 65, 64, 62, 60, 62};
            if (channels != null) {
                channels[0].programChange(89);
                channels[2].programChange(44);
            }
            int tick = 0;
            while (bgPlaying) {
                try {
                    self.playNote(0, mel[tick % mel.length] + 12, 52, 340);
                    if (tick % 3 == 0) {
                        self.playNote(2, 48 + tick % 4, 38, 680);
                    }
                    Thread.sleep(345);
                    tick++;
                } catch (InterruptedException ex) {
                    break;
                } catch (Exception ignored) {
                }
            }
        });
        bgThread.setDaemon(true);
        bgThread.start();
    }

    // =====================================================================
    // WINDOW TOGGLE
    // =====================================================================
    static JFrame mainFrame;
    static TDX gameInstance;

    void toggleWindowMode() {
        if (isFullScreen) {
            gd.setFullScreenWindow(null);
            mainFrame.dispose();
            mainFrame.setUndecorated(false);
            mainFrame.setSize(1280, 720);
            mainFrame.setLocationRelativeTo(null);
            mainFrame.setVisible(true);
            isFullScreen = false;
        } else {
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
        for (int i = 0; i < 8; i++) {
            final int idx = i;
            final String tp = TOWER_TYPES[i];
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(String.valueOf(i + 1)), tp);
            getActionMap().put(tp, new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    if (towerUnlocked[idx]) {
                        selectedTower = tp;
                    }
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
                        paused = true;
                        gameState = GameState.PAUSE_MENU;
                    }
                } else if (gameState == GameState.PAUSE_MENU) {
                    paused = false;
                    gameState = GameState.PLAYING;
                }
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "toggle");
        getActionMap().put("toggle", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                toggleWindowMode();
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "speed");
        getActionMap().put("speed", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (gameState == GameState.PLAYING && !gameOver && !gameWon && !paused) {
                    gameSpeed = (gameSpeed % 4) + 1;
                }
            }
        });
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        getActionMap().put("esc", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (gameState == GameState.PAUSE_MENU) {
                    paused = false;
                    gameState = GameState.PLAYING;
                } else if (gameState == GameState.PLAYING) {
                    paused = true;
                    gameState = GameState.PAUSE_MENU;
                } else {
                    stopBGMusic();
                    gd.setFullScreenWindow(null);
                    System.exit(0);
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

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    // =====================================================================
    // MAIN
    // =====================================================================
    public static void main(String[] args) {
        mainFrame = new JFrame("CELLS AT WORK - Defensa Médica");
        mainFrame.getContentPane().setBackground(new Color(4, 10, 20));
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
            Dimension sc = Toolkit.getDefaultToolkit().getScreenSize();
            SW = sc.width;
            SH = sc.height;
            mainFrame.setPreferredSize(sc);
            mainFrame.pack();
            mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            mainFrame.setVisible(true);
        }
        gameInstance.startMenuMusic();
    }

    // =====================================================================
    // PROJECTILE
    // =====================================================================
    class Projectile {

        double x, y, tx, ty, speed = 12;
        Color col;
        boolean done = false;
        String type;
        Enemy target;

        Projectile(double x, double y, Enemy t, Color c, String tp) {
            this.x = x;
            this.y = y;
            target = t;
            col = c;
            type = tp;
        }

        void move() {
            if (target == null || target.health <= 0) {
                done = true;
                return;
            }
            tx = target.x + 16;
            ty = target.y + 12;
            double dx = tx - x, dy = ty - y, d = Math.hypot(dx, dy);
            if (d < speed + 2) {
                done = true;
                return;
            }
            x += dx / d * speed;
            y += dy / d * speed;
        }

        void draw(Graphics2D g) {
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 95));
            g.fillOval((int) x - 7, (int) y - 7, 14, 14);
            g.setColor(col);
            g.fillOval((int) x - 4, (int) y - 4, 8, 8);
            g.setColor(new Color(255, 255, 255, 175));
            g.fillRect((int) x - 1, (int) y - 1, 3, 3);
        }
    }

    // =====================================================================
    // ENEMY BASE - with animated virus body and health number display
    // =====================================================================
    static final int[][] VIRUS_SHAPE = {{0, 0, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 0, 0}};

    class Enemy {

        int x, y, maxHealth = 50, health = 50, speed = 2, animTick = 0, reward = 15;
        int waypointIndex = 0;

        Enemy(int x, int y) {
            this.x = x;
            this.y = y;
        }

        void move() {
            if (selectedMap == 1 && mapWaypoints != null) {
                moveWaypoints();
            } else {
                x += speed;
                animTick++;
            }
        }

        void moveWaypoints() {
            animTick++;
            if (waypointIndex >= mapWaypoints.length) {
                x += speed;
                return;
            }
            int tx2 = mapWaypoints[waypointIndex][0], ty2 = mapWaypoints[waypointIndex][1];
            double dx = tx2 - x, dy = ty2 - y, d = Math.hypot(dx, dy);
            if (d <= speed + 2) {
                x = tx2;
                y = ty2;
                waypointIndex++;
                return;
            }
            x += (int) Math.round(dx / d * speed);
            y += (int) Math.round(dy / d * speed);
        }

        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wb = (animTick / 6) % 2;
            // Shadow
            g2.setColor(new Color(0, 0, 0, 75));
            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < 8; c++) {
                    if (VIRUS_SHAPE[r][c] == 1) {
                        g2.fillRect(x + c * PX + 3, y + r * PX + 3, PX, PX);
                    }
                }
            }
            // Body
            g2.setColor(new Color(212, 42, 42));
            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < 8; c++) {
                    if (VIRUS_SHAPE[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Highlight
            g2.setColor(new Color(255, 125, 125));
            g2.fillRect(x + PX, y, PX * 2, PX);
            g2.fillRect(x, y + PX, PX, PX);
            // Spikes
            int cx2 = x + 16, cy2 = y + 12;
            int[][] sp = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
            g2.setColor(new Color(235, 62, 62));
            for (int[] s : sp) {
                g2.fillRect(cx2 + s[0] * (12 + wb) - PX / 2, cy2 + s[1] * (12 + wb) - PX / 2, PX, PX);
            }
            // Eyes (animated blink)
            g2.setColor((animTick / 30) % 8 == 0 ? new Color(80, 30, 30) : new Color(255, 225, 225));
            g2.fillRect(x + PX * 2, y + PX, PX, PX);
            g2.fillRect(x + PX * 5, y + PX, PX, PX);
            // Health display
            drawEnemyHealth(g2, x - 2, y - 10, 36, health, maxHealth, new Color(200, 55, 55));
        }
    }

    // Draws health bar + numeric value
    void drawEnemyHealth(Graphics2D g, int x, int y, int w, int hp, int maxHp, Color c) {
        if (hp >= maxHp) {
            return; // only when damaged
        }
        g.setColor(new Color(15, 5, 5));
        g.fillRect(x, y, w, 5);
        g.setColor(c);
        g.fillRect(x, y, (int) (w * Math.max(0, (double) hp / maxHp)), 5);
        g.setColor(new Color(0, 0, 0, 100));
        g.drawRect(x, y, w, 5);
        // HP number
        g.setColor(new Color(255, 220, 220));
        g.setFont(pixelFont(6));
        g.drawString(hp + "/" + maxHp, x, y - 1);
    }

    // =====================================================================
    // MAP 1 ENEMIES - named after diseases
    // =====================================================================
    class SpeedEnemy extends Enemy {

        SpeedEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((38 + wave * 7) * enemyHealthMult());
            health = maxHealth;
            speed = 5;
            reward = (int) (32 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wb = (animTick / 3) % 2;
            int[][] f = {{0, 1, 1, 1, 0}, {1, 1, 1, 1, 1}, {1, 1, 1, 1, 1}, {0, 1, 1, 1, 0}};
            g2.setColor(new Color(255, 168, 0));
            for (int r = 0; r < f.length; r++) {
                for (int c = 0; c < f[r].length; c++) {
                    if (f[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            for (int i = 1; i <= 5; i++) {
                g2.setColor(new Color(255, 200, 50, 80 - i * 14));
                g2.fillRect(x - i * 8 - wb, y + PX, PX * 3, PX * 2);
            }
            g2.setColor(Color.WHITE);
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 3, y + PX, PX, PX);
            g2.setColor(new Color(255, 148, 0));
            g2.setFont(pixelFont(7));
            g2.drawString(">>", x, y - 5);
            drawEnemyHealth(g2, x - 2, y - 13, 32, health, maxHealth, new Color(255, 168, 0));
        }
    }

    class ShieldEnemy extends Enemy {

        int shieldHP = 65;

        ShieldEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((92 + wave * 19) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = (int) (44 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int[][] b = {{0, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 0}};
            g2.setColor(new Color(30, 145, 72));
            for (int r = 0; r < b.length; r++) {
                for (int c = 0; c < b[r].length; c++) {
                    if (b[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            if (shieldHP > 0) {
                int sA = 120 + (int) (shieldHP / 65.0 * 100);
                g2.setColor(new Color(100, 200, 255, Math.min(255, sA)));
                g2.fillRect(x + PX * 5, y - PX, PX * 2, PX * 7);
                g2.setColor(new Color(200, 240, 255));
                g2.fillRect(x + PX * 5, y, PX, PX * 5);
                g2.setColor(new Color(100, 200, 255, 38));
                g2.fillOval(x - 4, y - 4, 32, 28);
            }
            g2.setColor(new Color(255, 245, 55));
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 4, y + PX, PX, PX);
            drawEnemyHealth(g2, x - 2, y - 10, 36, health, maxHealth, new Color(30, 200, 85));
        }
    }

    class TankEnemy extends Enemy {

        TankEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((360 + wave * 42) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = (int) (88 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wb = (animTick / 10) % 2;
            g2.setColor(new Color(72, 72, 98));
            g2.fillRect(x, y, PX * 12, PX * 10);
            g2.setColor(new Color(102, 102, 134));
            g2.fillRect(x + PX, y + PX, PX * 10, PX * 2);
            g2.fillRect(x + PX, y + PX * 7, PX * 10, PX * 2);
            g2.setColor(new Color(52, 52, 72));
            g2.fillRect(x, y + PX * 8, PX * 4, PX * 3);
            g2.fillRect(x + PX * 8, y + PX * 8, PX * 4, PX * 3);
            g2.setColor(new Color(30, 30, 48));
            for (int tx2 = 0; tx2 < 4; tx2++) {
                g2.fillRect(x + tx2 * PX * 3 + wb, y + PX * 9, PX * 2, PX * 2);
            }
            g2.setColor(new Color(56, 56, 82));
            g2.fillRect(x + PX * 3, y - PX * 2, PX * 6, PX * 4);
            g2.setColor(new Color(72, 72, 102));
            g2.fillRect(x + PX * 9, y + PX * 4, PX * 5, PX * 2);
            g2.setColor(new Color(255, 58, 58));
            g2.fillRect(x + PX * 3, y - PX, PX * 2, PX * 2);
            g2.fillRect(x + PX * 7, y - PX, PX * 2, PX * 2);
            g2.setColor(new Color(200, 85, 85));
            g2.setFont(pixelFont(7));
            g2.drawString("TANK", x + 8, y - 16);
            drawEnemyHealth(g2, x - 2, y - 12, 52, health, maxHealth, new Color(100, 100, 200));
        }
    }

    class BossEnemy extends Enemy {

        BossEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((225 + wave * 26) * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = (int) (155 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wb = (animTick / 8) % 2;
            int[][] b = {{0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            long t = System.currentTimeMillis();
            int gA = (int) (Math.abs(Math.sin(t / 500.0)) * 48) + 14;
            g2.setColor(new Color(145, 30, 168, gA));
            g2.fillOval(x - 8, y - 8, 64, 52);
            g2.setColor(new Color(145, 30, 168));
            for (int r = 0; r < b.length; r++) {
                for (int c = 0; c < b[r].length; c++) {
                    if (b[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            int[][] core = {{0, 1, 1, 0}, {1, 1, 1, 1}, {1, 1, 1, 1}, {0, 1, 1, 0}};
            g2.setColor(new Color(205, 62, 228));
            for (int r = 0; r < core.length; r++) {
                for (int c = 0; c < core[r].length; c++) {
                    if (core[r][c] == 1) {
                        g2.fillRect(x + 16 + c * PX, y + 16 + r * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(255, 62, 62));
            g2.fillRect(x + PX * 3, y + PX * 2, PX * 2, PX * 2);
            g2.fillRect(x + PX * 7, y + PX * 2, PX * 2, PX * 2);
            g2.setColor(new Color(222, 62, 222));
            g2.setFont(pixelFont(8));
            g2.drawString("JEFE", x + 14, y - 20);
            drawEnemyHealth(g2, x - 4, y - 14, 56, health, maxHealth, new Color(185, 42, 205));
        }
    }

    class StealthEnemy extends Enemy {

        int visTick = 0;
        boolean visible = false;

        StealthEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((68 + wave * 9) * enemyHealthMult());
            health = maxHealth;
            speed = 3;
            reward = (int) (58 * rewardMult());
        }

        @Override
        void move() {
            super.move();
            visTick++;
            if (visTick > 45) {
                visTick = 0;
                visible = !visible;
            }
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int al = visible ? 215 : 48;
            g2.setColor(new Color(62, 62, 205, al));
            for (int r = 0; r < VIRUS_SHAPE.length; r++) {
                for (int c = 0; c < VIRUS_SHAPE[r].length; c++) {
                    if (VIRUS_SHAPE[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            if (visible) {
                g2.setColor(new Color(185, 185, 255, al));
                g2.setFont(pixelFont(8));
                g2.drawString("???", x + 4, y - 6);
                drawEnemyHealth(g2, x - 2, y - 12, 36, health, maxHealth, new Color(82, 82, 225));
            }
        }
    }

    class HealerEnemy extends Enemy {

        int healTick = 0;

        HealerEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((92 + wave * 13) * enemyHealthMult());
            health = maxHealth;
            speed = 2;
            reward = (int) (75 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int pulse = (int) (Math.abs(Math.sin(animTick / 12.0)) * 22);
            int[][] h = {{0, 1, 1, 1, 0}, {1, 1, 1, 1, 1}, {1, 1, 1, 1, 1}, {1, 1, 1, 1, 1}, {0, 1, 1, 1, 0}};
            g2.setColor(new Color(62, 205, 92));
            for (int r = 0; r < h.length; r++) {
                for (int c = 0; c < h[r].length; c++) {
                    if (h[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            drawMedCross(g2, x + 10, y + 10, 14, new Color(255, 82, 82));
            g2.setColor(new Color(62, 225, 102, 32 + pulse));
            g2.drawOval(x - 16, y - 14, 64, 50);
            g2.setColor(new Color(122, 255, 155));
            g2.setFont(pixelFont(9));
            g2.drawString("+", x + 4, y - 5);
            drawEnemyHealth(g2, x - 2, y - 12, 32, health, maxHealth, new Color(62, 205, 92));
        }
    }

    class MutantEnemy extends Enemy {

        int phase = 0;

        MutantEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((462 + wave * 46) * enemyHealthMult());
            health = maxHealth;
            speed = 2;
            reward = (int) (198 * rewardMult());
        }

        @Override
        void move() {
            super.move();
            if (health < maxHealth / 2 && phase == 0) {
                phase = 1;
                speed += 2;
            }
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wb = (animTick / 4) % 3;
            Color bc = phase == 1 ? new Color(255, 105, 0) : new Color(105, 42, 185);
            int[][] m = {{0, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 0}};
            long t = System.currentTimeMillis();
            int gA = (int) (Math.abs(Math.sin(t / 300.0)) * 58) + 18;
            g2.setColor(new Color(bc.getRed(), bc.getGreen(), bc.getBlue(), gA));
            g2.fillOval(x - 6, y - 6, 44, 38);
            g2.setColor(bc);
            for (int r = 0; r < m.length; r++) {
                for (int c = 0; c < m[r].length; c++) {
                    if (m[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            int cx2 = x + 14, cy2 = y + 12;
            g2.setColor(phase == 1 ? new Color(255, 185, 0) : new Color(185, 82, 255));
            for (int deg = 0; deg < 360; deg += 45) {
                double rad = Math.toRadians(deg + animTick * 3);
                g2.fillRect(cx2 + (int) (Math.cos(rad) * (14 + wb)) - 1, cy2 + (int) (Math.sin(rad) * (14 + wb)) - 1, 3, 3);
            }
            g2.setColor(Color.WHITE);
            g2.fillRect(x + PX * 2, y + PX, PX, PX);
            g2.fillRect(x + PX * 5, y + PX, PX, PX);
            g2.setColor(phase == 1 ? new Color(255, 145, 0) : new Color(205, 105, 255));
            g2.setFont(pixelFont(7));
            g2.drawString(phase == 1 ? "BERSERKER!" : "MUTANTE", x - 4, y - 18);
            drawEnemyHealth(g2, x - 2, y - 14, 36, health, maxHealth, phase == 1 ? new Color(255, 145, 0) : new Color(185, 42, 225));
        }
    }

    class ColossusEnemy extends Enemy {

        ColossusEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) (2900 * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = (int) (415 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int[][] f = {{0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            long t = System.currentTimeMillis();
            int gA = (int) (Math.abs(Math.sin(t / 400.0)) * 48) + 14;
            g2.setColor(new Color(52, 122, 185, gA));
            g2.fillOval(x - 10, y - 10, 76, 58);
            g2.setColor(new Color(52, 122, 185));
            for (int r = 0; r < f.length; r++) {
                for (int c = 0; c < f[r].length; c++) {
                    if (f[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(82, 172, 235));
            g2.fillRect(x + PX * 2, y + PX * 2, PX * 4, PX * 4);
            g2.fillRect(x + PX * 8, y + PX * 2, PX * 4, PX * 4);
            g2.setColor(new Color(255, 205, 52));
            g2.fillRect(x + PX * 4, y + PX * 3, PX * 2, PX * 2);
            g2.fillRect(x + PX * 8, y + PX * 3, PX * 2, PX * 2);
            g2.setColor(new Color(105, 205, 255));
            g2.setFont(pixelFont(8));
            g2.drawString("!! COLOSO !!", x - 4, y - 20);
            drawEnemyHealth(g2, x - 4, y - 14, 64, health, maxHealth, new Color(62, 155, 255));
        }
    }

    class FinalBoss extends Enemy {

        FinalBoss(int x, int y) {
            super(x, y);
            maxHealth = 2400;
            health = 2400;
            speed = 1;
            reward = (int) (625 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wb = (animTick / 4) % 3;
            int[][] f = {{0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0}};
            long t = System.currentTimeMillis();
            int gA = (int) (Math.abs(Math.sin(t / 250.0)) * 68) + 24;
            g2.setColor(new Color(255, 62, 0, gA));
            g2.fillOval(x - 16, y - 16, 100, 75);
            g2.setColor(new Color(175, 22, 22));
            for (int r = 0; r < f.length; r++) {
                for (int c = 0; c < f[r].length; c++) {
                    if (f[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(255, 205, 0));
            g2.fillRect(x + PX * 3, y + PX * 3, PX * 3, PX * 3);
            g2.fillRect(x + PX * 10, y + PX * 3, PX * 3, PX * 3);
            // Crown
            g2.setColor(new Color(255, 185, 0));
            for (int i = 0; i < 5; i++) {
                g2.fillRect(x + PX * 2 + i * PX * 3, y - PX * (4 + i % 2), PX * 2, PX * (4 + i % 2));
            }
            g2.setColor(new Color(255, 82, 82, (int) (Math.abs(Math.sin(t / 200.0)) * 200) + 52));
            g2.fillOval(x + PX * 4, y - PX * 4 - 2, 6, 6);
            g2.setColor(new Color(255, 62, 0));
            g2.setFont(pixelFont(9));
            g2.drawString("!! JEFE FINAL !!", x - 10, y - 24);
            drawEnemyHealth(g2, x - 8, y - 16, 88, health, maxHealth, new Color(255, 82, 0));
        }
    }

    // NEW: Ultra Virus (wave 25 boss)
    class UltraVirus extends Enemy {

        UltraVirus(int x, int y) {
            super(x, y);
            maxHealth = (int) (5000 * enemyHealthMult());
            health = maxHealth;
            speed = 1;
            reward = (int) (850 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wb = (animTick / 3) % 4;
            long t = System.currentTimeMillis();
            int gA = (int) (Math.abs(Math.sin(t / 300.0)) * 80) + 30;
            g2.setColor(new Color(130, 0, 200, gA));
            g2.fillOval(x - 20, y - 18, 110, 85);
            // Multi-layer body
            int[][] b = {{0, 0, 1, 1, 1, 1, 1, 1, 0, 0}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 1, 1, 1, 0, 0}};
            g2.setColor(new Color(110, 0, 175));
            for (int r = 0; r < b.length; r++) {
                for (int c = 0; c < b[r].length; c++) {
                    if (b[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            // Rotating outer spikes
            int cx2 = x + 20, cy2 = y + 16;
            for (int deg = 0; deg < 360; deg += 30) {
                double rad = Math.toRadians(deg + animTick * 2);
                g2.setColor(new Color(200, 50, 255, 150));
                g2.fillRect(cx2 + (int) (Math.cos(rad) * (20 + wb)) - 2, cy2 + (int) (Math.sin(rad) * (20 + wb)) - 2, 4, 4);
            }
            g2.setColor(new Color(255, 255, 255, 200));
            g2.fillRect(x + PX * 3, y + PX * 2, PX, PX);
            g2.fillRect(x + PX * 7, y + PX * 2, PX, PX);
            // Inner core
            g2.setColor(new Color(220, 100, 255, 180));
            g2.fillOval(x + 12, y + 8, 18, 14);
            g2.setColor(new Color(130, 0, 200));
            g2.setFont(pixelFont(9));
            g2.drawString("!! ULTRA-VIRUS !!", x - 14, y - 26);
            drawEnemyHealth(g2, x - 6, y - 16, 78, health, maxHealth, new Color(200, 50, 255));
        }
    }

    // =====================================================================
    // MAP 2 EXCLUSIVE ENEMIES
    // =====================================================================
    class ArmoredEnemy extends Enemy {

        int armorHP = 130;

        ArmoredEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((258 + wave * 32) * enemyHealthMult());
            health = maxHealth;
            speed = 2;
            reward = (int) (108 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int[][] b = {{0, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 0}};
            g2.setColor(new Color(142, 142, 185));
            for (int r = 0; r < b.length; r++) {
                for (int c = 0; c < b[r].length; c++) {
                    if (b[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            if (armorHP > 0) {
                g2.setColor(new Color(185, 185, 225));
                g2.fillRect(x + PX, y + PX, PX * 4, PX * 3);
                g2.setColor(new Color(102, 102, 145));
                g2.drawRect(x + PX, y + PX, PX * 4, PX * 3);
            }
            g2.setColor(new Color(255, 102, 102));
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 4, y + PX, PX, PX);
            g2.setColor(new Color(185, 185, 255));
            g2.setFont(pixelFont(7));
            g2.drawString("ARM", x + 3, y - 5);
            drawEnemyHealth(g2, x - 2, y - 12, 32, health, maxHealth, new Color(185, 185, 255));
        }
    }

    class PhaserEnemy extends Enemy {

        int pTick = 0;
        boolean phased = false;

        PhaserEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((122 + wave * 16) * enemyHealthMult());
            health = maxHealth;
            speed = 4;
            reward = (int) (92 * rewardMult());
        }

        @Override
        void move() {
            super.move();
            pTick++;
            if (pTick > 60) {
                pTick = 0;
                phased = !phased;
            }
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int al = phased ? 28 : 195;
            long t = System.currentTimeMillis();
            int sh = (int) (Math.abs(Math.sin(t / 200.0)) * 38) + al;
            g2.setColor(new Color(122, 62, 225, Math.min(255, sh)));
            for (int r = 0; r < VIRUS_SHAPE.length; r++) {
                for (int c = 0; c < VIRUS_SHAPE[r].length; c++) {
                    if (VIRUS_SHAPE[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            if (!phased) {
                g2.setColor(new Color(205, 162, 255, 185));
                g2.setFont(pixelFont(7));
                g2.drawString("PHZ", x + 4, y - 5);
                drawEnemyHealth(g2, x - 2, y - 12, 36, health, maxHealth, new Color(185, 105, 255));
            }
        }
    }

    class SwarmEnemy extends Enemy {

        SwarmEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((22 + wave * 3) * enemyHealthMult());
            health = maxHealth;
            speed = 6;
            reward = (int) (12 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wb = (animTick / 2) % 3;
            int[][] t = {{0, 1, 1, 0}, {1, 1, 1, 1}, {1, 1, 1, 1}, {0, 1, 1, 0}};
            g2.setColor(new Color(225, 105, 22));
            for (int r = 0; r < t.length; r++) {
                for (int c = 0; c < t[r].length; c++) {
                    if (t[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            g2.setColor(Color.WHITE);
            g2.fillRect(x + PX, y, PX, PX);
            g2.fillRect(x + PX * 2, y, PX, PX);
        }
    }

    class TowerDestroyerEnemy extends Enemy {

        int destroyTick = 0;

        TowerDestroyerEnemy(int x, int y) {
            super(x, y);
            maxHealth = (int) ((185 + wave * 22) * enemyHealthMult());
            health = maxHealth;
            speed = 2;
            reward = (int) (138 * rewardMult());
        }

        @Override
        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int wb = (animTick / 5) % 2;
            int[][] b = {{0, 1, 1, 1, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1}, {0, 1, 1, 1, 1, 1, 0}};
            long t = System.currentTimeMillis();
            int fA = (int) (Math.abs(Math.sin(t / 300.0)) * 58) + 118;
            g2.setColor(new Color(205, 22, 22, fA));
            for (int r = 0; r < b.length; r++) {
                for (int c = 0; c < b[r].length; c++) {
                    if (b[r][c] == 1) {
                        g2.fillRect(x + c * PX, y + r * PX, PX, PX);
                    }
                }
            }
            g2.setColor(new Color(40, 40, 40));
            g2.fillRect(x + PX * 2, y + PX * 2, PX * 3, PX * 2);
            g2.setColor(new Color(255, 205, 52));
            g2.fillRect(x + PX * 2, y + PX, PX, PX * 4);
            g2.fillRect(x + PX * 4, y + PX, PX, PX * 4);
            g2.setColor(new Color(255, 255, 0));
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 5, y + PX, PX, PX);
            boolean near = towers.stream().anyMatch(tw -> Math.hypot(x - tw.x, y - tw.y) < 132);
            if (near) {
                g2.setColor(new Color(255, 52, 52, 58));
                g2.fillOval(x - 20, y - 18, 68, 52);
                g2.setColor(new Color(255, 52, 52, 118));
                g2.drawOval(x - 20, y - 18, 68, 52);
            }
            g2.setColor(new Color(255, 62, 62));
            g2.setFont(pixelFont(7));
            g2.drawString("DESTRUCT", x - 8, y - 18);
            drawEnemyHealth(g2, x - 2, y - 14, 40, health, maxHealth, new Color(255, 52, 52));
        }
    }

    // =====================================================================
    // TOWER - upgraded design with upgrade levels
    // =====================================================================
    class Tower {

        int x, y, range, cooldown = 0, animTick = 0, cooldownMax, upgradeLevel = 0;
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

        int getDamage() {
            int base = switch (type) {
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
            base += wave * 2;
            base = (int) (base * (1 + upgradeLevel * 0.45));
            return base;
        }

        int getCooldownMax() {
            int cm = cooldownMax - (upgradeLevel * 3);
            return Math.max(5, cm);
        }

        int getRange() {
            return range + (upgradeLevel * 20);
        }

        void update(List<Enemy> enemies, List<Projectile> projs) {
            if (cooldown > 0) {
                cooldown--;
            }
            animTick++;
            target = null;
            double bd = Double.MAX_VALUE;
            int rng = getRange();
            for (Enemy e : enemies) {
                if (e instanceof PhaserEnemy && ((PhaserEnemy) e).phased) {
                    continue;
                }
                double d = Math.hypot(x + 20 - (e.x + 16), y + 16 - (e.y + 12));
                if (d < rng && d < bd) {
                    bd = d;
                    target = e;
                }
            }
            if (target != null) {
                aimAngle = Math.atan2((target.y + 12) - (y + 16), (target.x + 16) - (x + 20));
                if (cooldown == 0) {
                    int dmg = getDamage();
                    applyDamage(target, dmg, enemies, projs);
                    Color pC = switch (type) {
                        case "FIRE" ->
                            new Color(255, 145, 62);
                        case "ICE" ->
                            new Color(82, 205, 255);
                        case "ELEC" ->
                            new Color(255, 235, 62);
                        case "SONIC" ->
                            new Color(255, 82, 205);
                        case "NANO" ->
                            new Color(82, 255, 205);
                        case "PLASMA" ->
                            new Color(205, 82, 255);
                        case "ACID" ->
                            new Color(185, 255, 82);
                        default ->
                            new Color(185, 255, 205);
                    };
                    projs.add(new Projectile(x + 20, y + 16, target, pC, type));
                    cooldown = getCooldownMax();
                }
            }
        }

        void applyDamage(Enemy t, int dmg, List<Enemy> enemies, List<Projectile> projs) {
            if (type.equals("ICE") && t.speed > 1) {
                t.speed = Math.max(1, t.speed - 1);
            }
            if (type.equals("SONIC")) {
                for (Enemy e : enemies) {
                    if (Math.hypot(x - e.x, y - e.y) < getRange() / 2 && e.speed > 1) {
                        e.speed = Math.max(1, e.speed - 1);
                    }
                }
            }
            if (type.equals("ACID")) {
                t.maxHealth = (int) (t.maxHealth * 0.97);
            }
            if (t instanceof ArmoredEnemy) {
                ArmoredEnemy ae = (ArmoredEnemy) t;
                if (ae.armorHP > 0) {
                    ae.armorHP -= dmg;
                    if (ae.armorHP < 0) {
                        t.health += ae.armorHP;
                        ae.armorHP = 0;
                    }
                } else {
                    t.health -= dmg;
                }
            } else if (t instanceof ShieldEnemy) {
                ShieldEnemy se = (ShieldEnemy) t;
                if (se.shieldHP > 0) {
                    se.shieldHP -= dmg;
                    if (se.shieldHP < 0) {
                        t.health += se.shieldHP;
                        se.shieldHP = 0;
                    }
                } else {
                    t.health -= dmg;
                }
            } else if (t instanceof StealthEnemy) {
                StealthEnemy st = (StealthEnemy) t;
                st.visible = true;
                st.visTick = 0;
                t.health -= dmg;
            } else if (t instanceof PhaserEnemy) {
                if (!((PhaserEnemy) t).phased) {
                    t.health -= dmg;
                }
            } else {
                t.health -= dmg;
            }
            if (type.equals("ELEC")) {
                for (Enemy e : enemies) {
                    if (e != t && Math.hypot(t.x - e.x, t.y - e.y) < 90 + (upgradeLevel * 20)) {
                        e.health -= dmg / 2;
                    }
                }
            }
            if (type.equals("PLASMA")) {
                for (Enemy e : enemies) {
                    if (e != t && Math.hypot(t.x - e.x, t.y - e.y) < 72 + (upgradeLevel * 18)) {
                        e.health -= dmg / 3;
                    }
                }
            }
        }

        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            Color mc, ac;
            switch (type) {
                case "FIRE":
                    mc = new Color(232, 82, 32);
                    ac = new Color(255, 172, 62);
                    break;
                case "ICE":
                    mc = new Color(42, 168, 228);
                    ac = new Color(182, 238, 255);
                    break;
                case "ELEC":
                    mc = new Color(212, 188, 0);
                    ac = new Color(255, 248, 82);
                    break;
                case "SONIC":
                    mc = new Color(222, 62, 182);
                    ac = new Color(255, 152, 222);
                    break;
                case "NANO":
                    mc = new Color(62, 222, 192);
                    ac = new Color(182, 255, 242);
                    break;
                case "PLASMA":
                    mc = new Color(182, 62, 255);
                    ac = new Color(222, 162, 255);
                    break;
                case "ACID":
                    mc = new Color(142, 232, 42);
                    ac = new Color(212, 255, 122);
                    break;
                default:
                    mc = new Color(42, 188, 108);
                    ac = new Color(182, 255, 202);
                    break;
            }
            // Upgrade glow
            if (upgradeLevel > 0) {
                Color gC = upgradeLevel == 2 ? new Color(255, 215, 50, 40) : new Color(180, 100, 255, 30);
                int rng = getRange();
                g2.setColor(gC);
                g2.fillOval(x - rng + 20, y - rng + 16, rng * 2, rng * 2);
            }
            // Range ring
            int rng = getRange();
            g2.setColor(new Color(mc.getRed(), mc.getGreen(), mc.getBlue(), 10));
            g2.fillOval(x - rng + 20, y - rng + 16, rng * 2, rng * 2);
            g2.setColor(new Color(mc.getRed(), mc.getGreen(), mc.getBlue(), 32));
            for (int deg = 0; deg < 360; deg += 10) {
                double rad = Math.toRadians(deg + (animTick * 0.5));
                g2.fillRect(x + 20 + (int) ((rng - 5) * Math.cos(rad)), y + 16 + (int) ((rng - 5) * Math.sin(rad)), PX, PX);
            }
            // Base platform
            g2.setColor(new Color(12, 32, 24));
            g2.fillRect(x - 5, y + 24, 48, 10);
            g2.setColor(new Color(0, 78, 48));
            g2.fillRect(x - 3, y + 26, 44, 6);
            // Upgrade indicator LEDs
            for (int ul = 0; ul < upgradeLevel; ul++) {
                g2.setColor(ul == 1 ? new Color(255, 215, 50) : new Color(100, 220, 255));
                g2.fillRect(x + 4 + ul * 10, y + 27, 7, 4);
            }
            // Animated base pulse
            long t = System.currentTimeMillis();
            int pA = (int) (Math.abs(Math.sin(t / 400.0 + x)) * 28) + 8;
            g2.setColor(new Color(mc.getRed(), mc.getGreen(), mc.getBlue(), pA));
            g2.fillRect(x - 5, y + 24, 48, 10);
            // Body
            drawTowerBody(g2, x, y, mc, ac);
            // Upgrade crown
            if (upgradeLevel == 2) {
                g2.setColor(new Color(255, 215, 50));
                g2.fillRect(x + 8, y - 8, 6, 6);
                g2.fillRect(x + 18, y - 10, 6, 8);
                g2.fillRect(x + 28, y - 8, 6, 6);
            } else if (upgradeLevel == 1) {
                g2.setColor(new Color(100, 220, 255));
                g2.fillRect(x + 16, y - 8, 6, 6);
            }
            // Barrel
            int bx2 = x + 20, by2 = y + 16;
            AffineTransform old = g2.getTransform();
            g2.rotate(aimAngle, bx2, by2);
            g2.setColor(new Color(0, 0, 0, 55));
            g2.fillRect(bx2 + 1, by2 - 2, 27, 7);
            g2.setColor(ac);
            g2.fillRect(bx2, by2 - 3, 24, 6);
            g2.setColor(mc);
            g2.fillRect(bx2 + 20, by2 - 2, 8, 4);
            g2.setColor(new Color(255, 255, 255, 148));
            g2.fillRect(bx2 + 26, by2 - 1, 4, 2);
            g2.setColor(new Color(mc.getRed() / 2, mc.getGreen() / 2, mc.getBlue() / 2));
            g2.fillRect(bx2 + 6, by2 - 3, 3, 6);
            g2.fillRect(bx2 + 13, by2 - 3, 3, 6);
            g2.setTransform(old);
            // Beam
            if (target != null && cooldown > getCooldownMax() - 7) {
                drawBeam(g2, bx2, by2, target.x + 16, target.y + 12, mc, ac);
            }
        }

        void drawTowerBody(Graphics2D g, int x, int y, Color mc, Color ac) {
            g.setColor(new Color(0, 0, 0, 55));
            g.fillRect(x + 3, y + 3, 38, 28);
            g.setColor(new Color(mc.getRed() / 5, mc.getGreen() / 5, mc.getBlue() / 5));
            g.fillRect(x, y, 38, 28);
            g.setColor(mc);
            g.fillRect(x, y, 38, 28);
            g.setColor(new Color(mc.getRed() * 2 / 3 + 55, mc.getGreen() * 2 / 3 + 55, mc.getBlue() * 2 / 3 + 55, 75));
            g.fillRect(x, y, 38, 10);
            g.setColor(new Color(mc.getRed() / 2, mc.getGreen() / 2, mc.getBlue() / 2));
            g.drawRect(x, y, 38, 28);
            switch (type) {
                case "NORMAL":
                    // Medical syringe body - upgraded shows IV drip
                    g.setColor(new Color(92, 215, 118));
                    g.fillRect(x + 5, y + 4, 18, 18);
                    g.setColor(new Color(52, 92, 72));
                    g.fillRect(x - 6, y + 2, 7, 24);
                    g.setColor(ac);
                    g.fillRect(x - 9, y, 7, 28);
                    drawMedCross(g, x + 18, y - 9, 13, new Color(225, 52, 62));
                    if (upgradeLevel > 0) {
                        g.setColor(new Color(0, 200, 255, 55));
                        g.fillOval(x + 10, y - 16, 16, 16);
                    }
                    break;
                case "FIRE":
                    g.setColor(new Color(255, 52, 22));
                    g.fillRect(x + 4, y + 4, 14, 20);
                    g.setColor(c);
                    g.fillRect(x + 7, y + 7, 8, 14);
                    long tf = System.currentTimeMillis();
                    int fl = (int) (Math.abs(Math.sin(tf / 100.0 + x)) * 6);
                    g.setColor(new Color(255, 225, 62, 175));
                    g.fillRect(x + 9, y - fl, 4, fl + 4);
                    if (upgradeLevel > 0) {
                        g.setColor(new Color(255, 150, 50, 80));
                        g.fillRect(x + 5, y - fl * 2, 8, fl + 6);
                    }
                    g.setColor(ac);
                    g.setFont(pixelFont(7));
                    g.drawString("UV", x + 24, y + 17);
                    break;
                case "ICE":
                    g.setColor(ac);
                    g.fillRect(x + 3, y + 8, 25, 5);
                    g.fillRect(x + 3, y + 18, 25, 5);
                    int[] cxs = {x + 16, x + 10, x + 12, x + 16, x + 20, x + 22, x + 16};
                    int[] cys = {y - 12, y - 4, y - 8, y - 14, y - 8, y - 4, y - 12};
                    g.setColor(new Color(185, 238, 255));
                    g.fillPolygon(cxs, cys, 7);
                    g.setColor(new Color(225, 252, 255));
                    g.drawPolygon(cxs, cys, 7);
                    break;
                case "ELEC":
                    g.setColor(new Color(42, 35, 5));
                    for (int fy = y + 3; fy < y + 25; fy += 5) {
                        g.fillRect(x + 3, fy, 28, 2);
                    }
                    g.setColor(ac);
                    g.fillRect(x + 12, y - 10, 8, 10);
                    g.fillRect(x + 15, y - 14, 3, 6);
                    if (animTick % 5 < 2) {
                        g.setColor(new Color(255, 248, 82, 198));
                        g.fillRect(x + 20, y - 12, PX, PX);
                        g.fillRect(x + 9, y - 10, PX, PX);
                    } else {
                        g.setColor(new Color(255, 205, 42, 198));
                        g.fillRect(x + 16, y - 15, PX, PX);
                        g.fillRect(x + 11, y - 8, PX, PX);
                    }
                    break;
                case "SONIC":
                    for (int i = 1; i <= 3; i++) {
                        g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 202 - i * 52));
                        g.drawOval(x + i * 4, y + i * 4, 30 - i * 8, 20 - i * 5);
                    }
                    int wO = (animTick * 2) % 22;
                    g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 78));
                    g.drawOval(x - wO, y - wO / 2, 38 + wO * 2, 28 + wO);
                    break;
                case "NANO":
                    g.setColor(ac);
                    for (int i = 0; i < 3; i++) {
                        for (int j = 0; j < 3; j++) {
                            g.fillRect(x + 4 + i * 11, y + 4 + j * 8, 8, 5);
                        }
                    }
                    g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 58));
                    for (int i = 0; i < 3; i++) {
                        g.drawLine(x, y + 4 + i * 8, x + 38, y + 4 + i * 8);
                    }
                    for (int i = 0; i < 3; i++) {
                        g.drawLine(x + 3 + i * 12, y, x + 3 + i * 12, y + 28);
                    }
                    break;
                case "PLASMA":
                    int pO = (animTick * 4) % 360;
                    g.setColor(ac);
                    g.fillOval(x + 5, y + 4, 26, 20);
                    g.setColor(new Color(255, 255, 255, 78));
                    g.fillOval(x + 12, y + 7, 10, 8);
                    double pR = Math.toRadians(pO);
                    g.setColor(new Color(255, 205, 255));
                    g.fillRect(x + 19 + (int) (Math.cos(pR) * 10), y + 14 + (int) (Math.sin(pR) * 7), 4, 4);
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
                    g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 58));
                    g.fillOval(x, y, 36, 26);
                    if (animTick % 20 < 5) {
                        g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 158));
                        g.fillRect(x + 16, y + 26, 4, 4 + animTick % 5);
                    }
                    break;
            }
        }
        // Dummy field to use 'c' inside switch
        Color c = new Color(100, 100, 100);

        void drawBeam(Graphics2D g, int x1, int y1, int x2, int y2, Color mc, Color ac) {
            int steps = 14;
            for (int s = 0; s < steps; s++) {
                float t = (float) s / steps;
                int bx = (int) (x1 + (x2 - x1) * t), by = (int) (y1 + (y2 - y1) * t);
                int j = s % 2 == 0 ? -PX : PX;
                g.setColor(s % 2 == 0 ? mc : ac);
                g.fillRect(bx + j, by, PX * 2, PX);
            }
            g.setColor(new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 155));
            g.fillOval(x2 - 6, y2 - 6, 12, 12);
            g.setColor(ac);
            g.fillRect(x2 - PX, y2 - PX, PX * 3, PX * 3);
        }
    }
}
