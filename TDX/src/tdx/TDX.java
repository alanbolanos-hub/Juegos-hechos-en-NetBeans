package tdx;

import javax.swing.*;
import javax.sound.midi.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;

public class TDX extends JPanel implements ActionListener, MouseListener {

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

    // === AUDIO (MIDI sintetizador integrado) ===
    static Synthesizer synth;
    static MidiChannel[] channels;

    static {
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            channels = synth.getChannels();
            // Canal 0: melodía principal, canal 1: efectos, canal 9: batería/percusión
            channels[0].programChange(80);  // Lead 1 (square wave)
            channels[1].programChange(98);  // FX Crystal
            channels[2].programChange(47);  // Timpani/Impact
        } catch (Exception ex) {
            System.out.println("MIDI no disponible: " + ex.getMessage());
        }
    }

    // Reproducir una nota en un canal dado
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

    // Melodía de fondo (se toca en bucle)
    static boolean bgPlaying = false;
    static Thread bgThread;

    static void startBGMusic() {
        if (bgPlaying) {
            return;
        }
        bgPlaying = true;
        bgThread = new Thread(() -> {
            // Melodía principal loop — escala pentatónica estilo 8-bit
            int[] melody = {60, 62, 64, 67, 69, 67, 64, 62,
                60, 64, 67, 72, 71, 69, 67, 64,
                60, 62, 64, 67, 64, 60, 62, 64};
            int[] bass = {36, 36, 43, 43, 41, 41, 38, 38};
            int noteDur = 180;
            int bassDur = 360;
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

    // Efecto: muerte de enemigo normal
    static void sfxEnemyDeath() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                channels[1].programChange(122); // Seashore / noise
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

    // Efecto: muerte de jefe
    static void sfxBossDeath() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                stopBGMusic();
                channels[1].programChange(100); // FX Atmosphere
                for (int note : new int[]{60, 55, 50, 45, 40, 35}) {
                    channels[1].noteOn(note, 110);
                    Thread.sleep(120);
                    channels[1].noteOff(note);
                }
                // Fanfarria de victoria breve
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

    // Efecto: construcción de torre
    static void sfxTowerPlaced() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                channels[1].programChange(11); // Vibraphone
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

    // Efecto: nueva oleada
    static void sfxNewWave(int wave) {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                stopBGMusic();
                channels[0].programChange(80);
                // Alarma ascendente
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

    // Efecto: victoria (última oleada superada)
    static void sfxVictory() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                stopBGMusic();
                channels[0].programChange(14); // Xylophone
                int[] fanfare = {60, 64, 67, 72, 71, 72, 76};
                for (int note : fanfare) {
                    channels[0].noteOn(note, 100);
                    Thread.sleep(130);
                    channels[0].noteOff(note);
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    // Efecto: game over
    static void sfxGameOver() {
        new Thread(() -> {
            try {
                if (channels == null) {
                    return;
                }
                stopBGMusic();
                channels[0].programChange(70); // Bassoon
                for (int note : new int[]{55, 50, 45, 40}) {
                    channels[0].noteOn(note, 100);
                    Thread.sleep(200);
                    channels[0].noteOff(note);
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    // Paleta médica pixel art
    static final Color COL_BG = new Color(10, 18, 25);
    static final Color COL_PATH = new Color(20, 40, 35);
    static final Color COL_PATH_EDGE = new Color(0, 180, 100);
    static final Color COL_GRID = new Color(0, 60, 45);
    static final Color COL_BASE = new Color(0, 220, 120);
    static final Color COL_BASE_CROSS = new Color(255, 255, 255);
    static final Color COL_UI_BG = new Color(5, 12, 18);
    static final Color COL_UI_BORDER = new Color(0, 180, 100);
    static final Color COL_UI_TEXT = new Color(180, 255, 200);
    static final Color COL_MONEY = new Color(255, 215, 60);
    static final Color COL_HEALTH = new Color(220, 50, 60);
    static final Color COL_WAVE = new Color(80, 180, 255);
    static final int PX = 4;

    public TDX() {
        setPreferredSize(new Dimension(900, 600));
        setBackground(COL_BG);
        addMouseListener(this);
        startWave();
        startBGMusic();
        timer = new Timer(30, this);
        timer.start();
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

            // Tipos de enemigo según oleada
            Enemy e;
            if (wave >= 10 && i % 7 == 0) {
                e = new TankEnemy(startX, startY);
            } else if (wave >= 7 && i % 5 == 0) {
                e = new SpeedEnemy(startX, startY);
            } else if (wave >= 4 && i % 4 == 0) {
                e = new ShieldEnemy(startX, startY);
            } else {
                e = new Enemy(startX, startY);
                // Escalar vida con oleada
                e.maxHealth = 50 + wave * 10;
                e.health = e.maxHealth;
            }
            enemies.add(e);
        }

        // Jefes en oleadas múltiplos de 3
        if (wave % 3 == 0) {
            if (wave == 15) {
                enemies.add(new FinalBoss(-200, 140));
            } else {
                enemies.add(new BossEnemy(-200, 160));
            }
        }

        sfxNewWave(wave);
    }

    // Verificar si ya existe torre en esa posición
    boolean towerExistsAt(int x, int y) {
        for (Tower t : towers) {
            if (Math.abs(t.x - x) < 32 && Math.abs(t.y - y) < 32) {
                return true;
            }
        }
        return false;
    }

    // Verificar que la posición no está dentro del camino
    boolean isOnPath(int x, int y) {
        return (y > 96 && y < 284);
    }

    void drawPixelRect(Graphics2D g, int x, int y, int w, int h, Color c) {
        g.setColor(c);
        int sx = (x / PX) * PX;
        int sy = (y / PX) * PX;
        int sw = ((w + PX - 1) / PX) * PX;
        int sh = ((h + PX - 1) / PX) * PX;
        g.fillRect(sx, sy, sw, sh);
    }

    void drawMedCross(Graphics2D g, int cx, int cy, int size, Color c) {
        int t = Math.max(PX, size / 3);
        g.setColor(c);
        g.fillRect(cx - t / 2, cy - size / 2, t, size);
        g.fillRect(cx - size / 2, cy - t / 2, size, t);
    }

    void drawHealthBar(Graphics2D g, int x, int y, int w, int h, double ratio, Color fg, Color bg) {
        g.setColor(bg);
        g.fillRect(x, y, w, h);
        g.setColor(fg);
        g.fillRect(x, y, (int) (w * Math.max(0, ratio)), h);
        g.setColor(COL_UI_BORDER);
        g.drawRect(x, y, w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // === FONDO ===
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(COL_GRID);
        for (int gx = 0; gx < getWidth(); gx += 16) {
            for (int gy = 0; gy < getHeight(); gy += 16) {
                g2.fillRect(gx, gy, 2, 2);
            }
        }

        // === CAMINO ===
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

        // === BASE ===
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

        // === ENEMIGOS ===
        for (Enemy e : enemies) {
            e.draw(g2);
        }

        // === TORRES ===
        for (Tower t : towers) {
            t.draw(g2);
        }

        // === UI SUPERIOR ===
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
        g2.drawString("OLA " + wave + "/" + MAX_WAVE, 216, 22);

        // Barra de progreso de oleadas
        int progW = 400;
        int progX = 480;
        g2.setColor(new Color(10, 30, 20));
        g2.fillRect(progX, 8, progW, 20);
        g2.setColor(new Color(0, 180, 100));
        g2.fillRect(progX, 8, (int) (progW * (double) wave / MAX_WAVE), 20);
        g2.setColor(COL_UI_BORDER);
        g2.drawRect(progX, 8, progW, 20);
        g2.setColor(COL_UI_TEXT);
        g2.setFont(pixelFont(9));
        g2.drawString("PROGRESO DEFENSA", progX + 130, 22);

        g2.setColor(new Color(0, 180, 80, 60));
        g2.fillRect(300, 0, 600, 36);

        // === MENÚ INFERIOR ===
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
        String[] labels = {"JERINGA", "LASER UV", "CRIOGENO", "ELECTROD."};
        String[] keys = {"[1]", "[2]", "[3]", "[4]"};
        Color[] colors = {
            new Color(180, 255, 200),
            new Color(255, 120, 60),
            new Color(80, 200, 255),
            new Color(255, 230, 60)
        };
        int[] xPos = {20, 240, 460, 680};

        for (int i = 0; i < types.length; i++) {
            boolean selected = selectedTower.equals(types[i]);
            int bx = xPos[i];
            int by = 506;
            int bw = 200;
            int bh = 84;
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
            drawTowerIcon(g2, bx + 12, by + 16, types[i], colors[i], selected);
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

        // === PANTALLAS DE FIN ===
        if (gameOver) {
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, 900, 600);
            g2.setColor(new Color(220, 50, 60));
            g2.setFont(pixelFont(36));
            g2.drawString("GAME OVER", 280, 250);
            g2.setColor(COL_UI_TEXT);
            g2.setFont(pixelFont(16));
            g2.drawString("La base fue destruida en la oleada " + wave, 220, 310);
            g2.setFont(pixelFont(12));
            g2.drawString("Reinicia la aplicacion para jugar de nuevo", 230, 360);
        }

        if (gameWon) {
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, 0, 900, 600);
            g2.setColor(new Color(0, 220, 120));
            g2.setFont(pixelFont(28));
            g2.drawString("¡DEFENSA COMPLETADA!", 195, 240);
            g2.setColor(COL_MONEY);
            g2.setFont(pixelFont(18));
            g2.drawString("Sobreviviste las 15 oleadas", 255, 300);
            g2.setColor(COL_UI_TEXT);
            g2.setFont(pixelFont(13));
            g2.drawString("Puntuacion final: $" + money + "  |  Vida: " + baseHealth, 250, 350);
        }
    }

    Font pixelFont(int size) {
        return new Font("Monospaced", Font.BOLD, size);
    }

    void drawPixelHeart(Graphics2D g, int x, int y, Color c) {
        int[][] heart = {
            {0, 1, 1, 0, 1, 1, 0},
            {1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1},
            {0, 1, 1, 1, 1, 1, 0},
            {0, 0, 1, 1, 1, 0, 0},
            {0, 0, 0, 1, 0, 0, 0},};
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
        int[][] coin = {
            {0, 1, 1, 1, 0},
            {1, 1, 0, 1, 1},
            {1, 0, 1, 0, 1},
            {1, 1, 0, 1, 1},
            {0, 1, 1, 1, 0},};
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
        g.setColor(new Color(80, 200, 120));
        int[][] bio = {
            {0, 0, 1, 1, 0, 0},
            {0, 1, 0, 0, 1, 0},
            {1, 0, 1, 1, 0, 1},
            {1, 0, 1, 1, 0, 1},
            {0, 1, 0, 0, 1, 0},
            {0, 0, 1, 1, 0, 0},};
        for (int row = 0; row < bio.length; row++) {
            for (int col = 0; col < bio[row].length; col++) {
                if (bio[row][col] == 1) {
                    g.fillRect(x + col * 4, y + row * 4, 4, 4);
                }
            }
        }
    }

    void drawTowerIcon(Graphics2D g, int x, int y, String type, Color c, boolean selected) {
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
        g.setColor(new Color(255, 100, 60, 180));
        g.fillRect(x + 36, y + 11, 4, 2);
    }

    void drawIconCryo(Graphics2D g, int x, int y, Color c) {
        g.setColor(c);
        drawMedCross(g, x + 20, y + 12, 20, c);
        g.setColor(new Color(180, 230, 255));
        g.fillRect(x + 18, y + 10, 4, 4);
    }

    void drawIconElec(Graphics2D g, int x, int y, Color c) {
        int[][] bolt = {
            {0, 0, 1, 1, 1},
            {0, 1, 1, 0, 0},
            {1, 1, 1, 0, 0},
            {0, 1, 0, 0, 0},
            {1, 0, 0, 0, 0},};
        g.setColor(c);
        for (int row = 0; row < bolt.length; row++) {
            for (int col = 0; col < bolt[row].length; col++) {
                if (bolt[row][col] == 1) {
                    g.fillRect(x + 8 + col * 5, y + 4 + row * 5, 5, 5);
                }
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameOver || gameWon) {
            return;
        }

        boolean bossWasAlive = enemies.stream().anyMatch(en -> en instanceof BossEnemy);

        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy en = it.next();
            en.move();
            if (en.x > 850) {
                baseHealth -= (en instanceof BossEnemy ? 25 : 10);
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

        // Detectar muertes
        java.util.List<Enemy> toRemove = new ArrayList<>();
        for (Enemy en : enemies) {
            if (en.health <= 0) {
                toRemove.add(en);
                money += en.reward;
                if (en instanceof BossEnemy) {
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

    public void mouseClicked(MouseEvent e) {
        int cost = 50;

        switch (selectedTower) {
            case "FIRE": cost = 70; break;
            case "ICE": cost = 60; break;
            case "ELEC": cost = 80; break;
        }

        if (money >= cost) {
            towers.add(new Tower(e.getX(), e.getY(), selectedTower));
            money -= cost;
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

    public static void main(String[] args) {
        JFrame frame = new JFrame("TDX - DEFENSA MEDICA");
        frame.getContentPane().setBackground(new Color(10, 18, 25));
        TDX game = new TDX();
        game.setKeyBindings();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
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
            int[][] virus = {
                {0, 0, 1, 1, 1, 1, 0, 0},
                {0, 1, 1, 1, 1, 1, 1, 0},
                {1, 1, 1, 1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1, 1, 1, 1},
                {0, 1, 1, 1, 1, 1, 1, 0},
                {0, 0, 1, 1, 1, 1, 0, 0},};
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
            g2.setColor(new Color(220, 60, 60));
            int cx = x + 16, cy = y + 12;
            int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
            for (int[] sp : spikes) {
                int sx = cx + sp[0] * (12 + wobble);
                int sy = cy + sp[1] * (12 + wobble);
                g2.fillRect(sx - PX / 2, sy - PX / 2, PX, PX);
            }
            g2.setColor(new Color(255, 220, 220));
            g2.fillRect(x + PX * 2, y + PX, PX, PX);
            g2.fillRect(x + PX * 5, y + PX, PX, PX);
            int bw = 32;
            drawHealthBar(g2, x, y - 8, bw, PX, (double) health / maxHealth,
                    health > maxHealth / 2 ? new Color(60, 200, 80) : new Color(220, 60, 60),
                    new Color(20, 40, 30));
        }
    }

    // =====================================================================
    // ENEMIGO RAPIDO — pequeño, muy veloz, poca vida
    // =====================================================================
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
            // Cuerpo más pequeño y alargado — color naranja ácido
            int[][] fast = {
                {0, 1, 1, 1, 0},
                {1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1},
                {0, 1, 1, 1, 0},};
            g2.setColor(new Color(255, 150, 0));
            for (int row = 0; row < fast.length; row++) {
                for (int col = 0; col < fast[row].length; col++) {
                    if (fast[row][col] == 1) {
                        g2.fillRect(x + col * PX, y + row * PX, PX, PX);
                    }
                }
            }
            // estela de velocidad
            g2.setColor(new Color(255, 200, 50, 100));
            for (int i = 1; i <= 3; i++) {
                g2.fillRect(x - i * 6 - wobble, y + PX, PX * 3, PX * 2);
            }
            // ojos
            g2.setColor(Color.WHITE);
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 3, y + PX, PX, PX);
            g2.setColor(new Color(255, 80, 0));
            g2.setFont(new Font("Monospaced", Font.BOLD, 8));
            g2.drawString(">>", x, y - 6);
            drawHealthBar(g2, x, y - 8, 20, PX, (double) health / maxHealth,
                    new Color(255, 160, 0), new Color(20, 20, 10));
        }
    }

    // =====================================================================
    // ENEMIGO BLINDADO — lento, armadura que absorbe primer impacto
    // =====================================================================
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
            int[][] body = {
                {0, 1, 1, 1, 1, 0},
                {1, 1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1, 1},
                {0, 1, 1, 1, 1, 0},};
            // cuerpo verde oscuro
            g2.setColor(new Color(30, 120, 60));
            for (int row = 0; row < body.length; row++) {
                for (int col = 0; col < body[row].length; col++) {
                    if (body[row][col] == 1) {
                        g2.fillRect(x + col * PX, y + row * PX, PX, PX);
                    }
                }
            }
            // escudo (frontal)
            if (shieldHP > 0) {
                g2.setColor(new Color(100, 200, 255, 180));
                g2.fillRect(x + PX * 5, y - PX, PX * 2, PX * 7);
                g2.setColor(new Color(200, 240, 255));
                g2.fillRect(x + PX * 5, y, PX, PX * 5);
            }
            // ojos amarillos
            g2.setColor(new Color(255, 240, 50));
            g2.fillRect(x + PX, y + PX, PX, PX);
            g2.fillRect(x + PX * 4, y + PX, PX, PX);
            drawHealthBar(g2, x, y - 8, 32, PX, (double) health / maxHealth,
                    new Color(30, 200, 80), new Color(10, 30, 15));
        }
    }

    // =====================================================================
    // ENEMIGO TANQUE — enorme, lentísimo, muchísima vida
    // =====================================================================
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
            // cuerpo grande gris acero
            g2.setColor(new Color(80, 80, 100));
            g2.fillRect(x, y, PX * 12, PX * 10);
            // detalles placa
            g2.setColor(new Color(120, 120, 150));
            g2.fillRect(x + PX, y + PX, PX * 10, PX * 2);
            g2.fillRect(x + PX, y + PX * 7, PX * 10, PX * 2);
            // ruedas
            g2.setColor(new Color(40, 40, 50));
            g2.fillRect(x, y + PX * 8, PX * 4, PX * 3);
            g2.fillRect(x + PX * 8, y + PX * 8, PX * 4, PX * 3);
            // cañón
            g2.setColor(new Color(60, 60, 80));
            g2.fillRect(x + PX * 10, y + PX * 4, PX * 5, PX * 2);
            // ojos rojos
            g2.setColor(new Color(255, 50, 50));
            g2.fillRect(x + PX * 2, y + PX * 3, PX * 2, PX * 2);
            g2.fillRect(x + PX * 7, y + PX * 3, PX * 2, PX * 2);
            g2.setColor(new Color(200, 80, 80));
            g2.setFont(new Font("Monospaced", Font.BOLD, 8));
            g2.drawString("TANK", x + 8, y - 6);
            drawHealthBar(g2, x, y - 8, PX * 12, PX, (double) health / maxHealth,
                    new Color(60, 120, 220), new Color(10, 10, 25));
        }
    }

    // =====================================================================
    // BOSS NORMAL — aparece cada 3 oleadas (excepto 15)
    // =====================================================================
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
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int wobble = (animTick / 8) % 2;
            int[][] boss = {
                {0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0},
                {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0},
                {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0},
                {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0},
                {0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0},};
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
            g2.setColor(new Color(220, 140, 240));
            g2.fillRect(x + PX * 2, y + PX, PX * 3, PX);
            g2.setColor(new Color(180, 50, 200));
            int cx2 = x + 24, cy2 = y + 18;
            int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
            for (int[] sp : spikes) {
                int sx = cx2 + sp[0] * (20 + wobble * 2);
                int sy = cy2 + sp[1] * (20 + wobble * 2);
                g2.fillRect(sx - PX, sy - PX, PX * 2, PX * 2);
                g2.fillRect(sx, sy, PX, PX);
            }
            g2.setColor(new Color(255, 60, 60));
            g2.fillRect(x + PX * 3, y + PX * 2, PX * 2, PX * 2);
            g2.fillRect(x + PX * 7, y + PX * 2, PX * 2, PX * 2);
            g2.setColor(Color.BLACK);
            g2.fillRect(x + PX * 3 + 2, y + PX * 2 + 2, PX, PX);
            g2.fillRect(x + PX * 7 + 2, y + PX * 2 + 2, PX, PX);
            g2.setColor(new Color(220, 60, 220));
            g2.setFont(new Font("Monospaced", Font.BOLD, 9));
            g2.drawString("JEFE", x + 12, y - 10);
            drawHealthBar(g2, x, y - 6, 48, PX, (double) health / maxHealth,
                    new Color(180, 50, 200), new Color(20, 10, 25));
        }
    }

    // =====================================================================
    // BOSS FINAL — oleada 15, monstruo definitivo
    // =====================================================================
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
            // Cuerpo enorme rojo-negro (16x14 bloques)
            int[][] final_ = {
                {0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0},
                {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0},
                {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0},
                {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0},
                {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0},
                {0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0},};
            // sombra
            g2.setColor(new Color(0, 0, 0, 120));
            for (int row = 0; row < final_.length; row++) {
                for (int col = 0; col < final_[row].length; col++) {
                    if (final_[row][col] == 1) {
                        g2.fillRect(x + col * PX + 4, y + row * PX + 4, PX, PX);
                    }
                }
            }
            // cuerpo
            g2.setColor(new Color(160, 20, 20));
            for (int row = 0; row < final_.length; row++) {
                for (int col = 0; col < final_[row].length; col++) {
                    if (final_[row][col] == 1) {
                        g2.fillRect(x + col * PX, y + row * PX, PX, PX);
                    }
                }
            }
            // patrón interior llameante
            g2.setColor(new Color(220, 80, 0));
            int[][] inner = {{0, 1, 1, 1, 0}, {1, 0, 1, 0, 1}, {0, 1, 1, 1, 0}};
            for (int row = 0; row < inner.length; row++) {
                for (int col = 0; col < inner[row].length; col++) {
                    if (inner[row][col] == 1) {
                        g2.fillRect(x + 20 + col * PX, y + 16 + row * PX, PX, PX);
                    }
                }
            }
            // brillo rojo superior
            g2.setColor(new Color(255, 100, 100));
            g2.fillRect(x + PX * 3, y + PX, PX * 5, PX);
            // espículas masivas
            g2.setColor(new Color(200, 30, 30));
            int[][] spikes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}, {2, -1}, {-2, -1}};
            int cx3 = x + 32, cy3 = y + 22;
            for (int[] sp : spikes) {
                int sx = cx3 + sp[0] * (28 + wobble * 3);
                int sy = cy3 + sp[1] * (24 + wobble * 2);
                g2.fillRect(sx - PX, sy - PX, PX * 3, PX * 3);
                g2.fillRect(sx, sy, PX, PX);
            }
            // ojos demoníacos (3 ojos)
            g2.setColor(new Color(255, 200, 0));
            g2.fillRect(x + PX * 3, y + PX * 3, PX * 3, PX * 3);
            g2.fillRect(x + PX * 10, y + PX * 3, PX * 3, PX * 3);
            g2.fillRect(x + PX * 7, y + PX * 2, PX * 2, PX * 2);
            g2.setColor(Color.BLACK);
            g2.fillRect(x + PX * 4, y + PX * 4, PX, PX);
            g2.fillRect(x + PX * 11, y + PX * 4, PX, PX);
            g2.fillRect(x + PX * 7 + 2, y + PX * 2 + 2, PX, PX);
            // etiqueta
            g2.setColor(new Color(255, 60, 0));
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.drawString("!! JEFE FINAL !!", x - 4, y - 12);
            // barra de vida grande
            drawHealthBar(g2, x - 4, y - 8, 80, PX + 2,
                    (double) health / maxHealth,
                    new Color(255, 80, 0), new Color(30, 5, 5));
        }
    }

    // =====================================================================
    // TOWER
    // =====================================================================
    class Tower {

        int x, y;
        int range = 120;
        int cooldown = 0;
        String type;
        Enemy target = null;
        int animTick = 0;

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
            for (Enemy e : enemies) {
                if (inRange(e) && cooldown == 0) {
                    target = e;
                    int damage = 0;
                    switch (type) {
                        case "FIRE":
                            damage = 25;
                            break;
                        case "ICE":
                            damage = 6;
                            if (e.speed > 1) {
                                e.speed--;
                            }
                            break;
                        case "ELEC":
                            damage = 18;
                            break;
                        default:
                            damage = 15;
                    }
                    damage += wave * 2;

                    // Escudos absorben daño primero
                    if (e instanceof ShieldEnemy) {
                        ShieldEnemy se = (ShieldEnemy) e;
                        if (se.shieldHP > 0) {
                            se.shieldHP -= damage;
                            if (se.shieldHP < 0) {
                                e.health += se.shieldHP; // remanente daña vida
                                se.shieldHP = 0;
                            }
                        } else {
                            e.health -= damage;
                        }
                    } else {
                        e.health -= damage;
                    }

                    cooldown = 20;
                    break;
                }
            }
        }

        boolean inRange(Enemy e) {
            return Math.hypot(x - e.x, y - e.y) < range;
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
            int rd = range * 2;
            g2.fillOval(x - range + 12, y - range + 12, rd, rd);
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
                    drawTowerNormal(g2, x, y, mainColor, accentColor);
                    break;
                case "FIRE":
                    drawTowerFire(g2, x, y, mainColor, accentColor);
                    break;
                case "ICE":
                    drawTowerIce(g2, x, y, mainColor, accentColor);
                    break;
                case "ELEC":
                    drawTowerElec(g2, x, y, mainColor, accentColor);
                    break;
            }
            if (target != null && cooldown > 15) {
                drawPixelBeam(g2, x + 14, y + 10, target.x + 10, target.y + 10, mainColor, accentColor);
            }
        }

        void drawTowerNormal(Graphics2D g, int x, int y, Color mc, Color ac) {
            g.setColor(new Color(30, 60, 50));
            g.fillRect(x + 2, y + 2, 26, 20);
            g.setColor(mc);
            g.fillRect(x, y, 26, 20);
            g.setColor(new Color(100, 220, 120));
            g.fillRect(x + 4, y + 4, 14, 12);
            g.setColor(ac);
            g.fillRect(x + 26, y + 8, 8, 4);
            g.setColor(new Color(200, 220, 200));
            g.fillRect(x + 34, y + 9, 4, 2);
            g.setColor(new Color(60, 100, 80));
            g.fillRect(x - 4, y + 2, 4, 16);
            g.setColor(ac);
            g.fillRect(x - 6, y, 4, 20);
            drawMedCross(g, x + 13, y - 6, 8, new Color(220, 50, 60));
        }

        void drawTowerFire(Graphics2D g, int x, int y, Color mc, Color ac) {
            g.setColor(new Color(40, 15, 10));
            g.fillRect(x + 2, y + 2, 26, 20);
            g.setColor(mc);
            g.fillRect(x, y, 26, 20);
            g.setColor(new Color(255, 60, 30));
            g.fillRect(x + 4, y + 4, 10, 12);
            g.setColor(new Color(255, 140, 80));
            g.fillRect(x + 6, y + 6, 6, 8);
            g.setColor(new Color(80, 30, 20));
            g.fillRect(x + 26, y + 6, 10, 8);
            g.setColor(ac);
            g.fillRect(x + 28, y + 8, 8, 4);
            g.setColor(ac);
            g.setFont(new Font("Monospaced", Font.BOLD, 8));
            g.drawString("UV", x + 18, y + 14);
        }

        void drawTowerIce(Graphics2D g, int x, int y, Color mc, Color ac) {
            g.setColor(new Color(10, 40, 60));
            g.fillRect(x + 2, y + 2, 20, 22);
            g.setColor(mc);
            g.fillRect(x, y, 20, 22);
            g.setColor(ac);
            g.fillRect(x + 2, y + 6, 16, 4);
            g.fillRect(x + 2, y + 14, 16, 4);
            g.setColor(new Color(20, 80, 120));
            g.fillRect(x + 20, y + 4, 12, 14);
            g.setColor(ac);
            g.fillRect(x + 22, y + 6, 8, 10);
            g.setColor(new Color(180, 230, 255));
            g.fillRect(x + 8, y - 8, PX, PX * 3);
            g.fillRect(x + 4, y - 4, PX * 3, PX);
            g.fillRect(x + 12, y - 4, PX * 3, PX);
        }

        void drawTowerElec(Graphics2D g, int x, int y, Color mc, Color ac) {
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

    // MouseListener stubs
    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }
}
