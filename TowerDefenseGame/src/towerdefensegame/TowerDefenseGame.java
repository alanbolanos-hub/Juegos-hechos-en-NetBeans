package towerdefensegame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;

public class TowerDefenseGame extends JPanel implements ActionListener, MouseListener {

    javax.swing.Timer timer;
    ArrayList<Enemy> enemies = new ArrayList<>();
    ArrayList<Tower> towers = new ArrayList<>();
    ArrayList<Bullet> bullets = new ArrayList<>();

    int money = 100;
    int wave = 1;
    int enemiesToSpawn = 0;
    int spawnCooldown = 0;
    boolean gameWon = false;

    public TowerDefenseGame() {
        setPreferredSize(new Dimension(800, 600));
        addMouseListener(this);

        startWave();

        timer = new Timer(30, this);
        timer.start();
    }

    void startWave() {
        enemiesToSpawn = wave * 5;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (gameWon) {
            return;
        }

        // Spawn enemigos
        if (enemiesToSpawn > 0 && spawnCooldown == 0) {
            enemies.add(new Enemy(0, 120));
            enemiesToSpawn--;
            spawnCooldown = 40;
        }

        if (spawnCooldown > 0) {
            spawnCooldown--;
        }

        // Mover enemigos
        for (Enemy enemy : enemies) {
            enemy.move();
        }

        // Torres disparan
        for (Tower tower : towers) {
            tower.update(enemies, bullets);
        }

        // Mover balas
        for (Bullet b : bullets) {
            b.move();
        }

        // Colisiones
        Iterator<Bullet> bIter = bullets.iterator();
        while (bIter.hasNext()) {
            Bullet b = bIter.next();
            Iterator<Enemy> eIter = enemies.iterator();

            while (eIter.hasNext()) {
                Enemy enemy = eIter.next();

                if (b.hit(enemy)) {
                    enemy.health -= b.damage;
                    bIter.remove();

                    if (enemy.health <= 0) {
                        eIter.remove();
                        money += 20; // 💰 ganas dinero
                    }
                    break;
                }
            }
        }

        // Siguiente oleada
        if (enemies.isEmpty() && enemiesToSpawn == 0) {
            wave++;
            if (wave > 5) {
                gameWon = true;
            } else {
                startWave();
            }
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 🌄 Fondo cielo
        g.setColor(new Color(120, 150, 180));
        g.fillRect(0, 0, 800, 600);

        // 🌍 Tierra
        g.setColor(new Color(90, 70, 50));
        g.fillRect(0, 100, 800, 500);

        // 🪖 Trinchera
        g.setColor(new Color(60, 40, 20));
        g.fillRect(0, 140, 800, 80);

        // Sacos
        g.setColor(new Color(194, 178, 128));
        for (int i = 0; i < 800; i += 40) {
            g.fillOval(i, 140, 40, 20);
        }

        // Dibujar entidades
        for (Enemy enemy : enemies) {
            enemy.draw(g);
        }
        for (Tower tower : towers) {
            tower.draw(g);
        }
        for (Bullet b : bullets) {
            b.draw(g);
        }

        // 🧾 HUD
        g.setColor(Color.WHITE);
        g.drawString("Dinero: " + money, 10, 20);
        g.drawString("Oleada: " + wave + "/5", 10, 40);
        g.drawString("Costo torre: 50 (click)", 10, 60);

        if (gameWon) {
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.drawString("¡VICTORIA!", 250, 300);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (money >= 50 && !gameWon) {
            towers.add(new Tower(e.getX(), e.getY()));
            money -= 50;
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("WW1 Tower Defense");
        TowerDefenseGame game = new TowerDefenseGame();

        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }
}

// ---------------- ENEMY ----------------
class Enemy {

    int x, y;
    int health = 100;

    public Enemy(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void move() {
        x += 1;
    }

    public void draw(Graphics g) {
        g.setColor(new Color(34, 85, 34));
        g.fillRect(x, y, 20, 20);

        g.setColor(Color.DARK_GRAY);
        g.fillOval(x, y - 5, 20, 10);

        g.setColor(Color.RED);
        g.fillRect(x, y - 10, 20, 3);
        g.setColor(Color.GREEN);
        g.fillRect(x, y - 10, health / 5, 3);
    }
}

// ---------------- TOWER ----------------
class Tower {

    int x, y;
    int range = 130;
    int cooldown = 0;

    public Tower(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update(ArrayList<Enemy> enemies, ArrayList<Bullet> bullets) {
        if (cooldown > 0) {
            cooldown--;
        }

        for (Enemy e : enemies) {
            double dist = Math.hypot(x - e.x, y - e.y);

            if (dist < range && cooldown == 0) {
                bullets.add(new Bullet(x, y, e));
                cooldown = 25;
                break;
            }
        }
    }

    public void draw(Graphics g) {
        g.setColor(new Color(70, 70, 70));
        g.fillRect(x - 10, y - 10, 20, 20);

        g.setColor(Color.BLACK);
        g.drawLine(x, y, x + 15, y);
    }
}

// ---------------- BULLET ----------------
class Bullet {

    double x, y;
    Enemy target;
    int speed = 6;
    int damage = 25;

    public Bullet(int x, int y, Enemy target) {
        this.x = x;
        this.y = y;
        this.target = target;
    }

    public void move() {
        double dx = target.x - x;
        double dy = target.y - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist != 0) {
            x += (dx / dist) * speed;
            y += (dy / dist) * speed;
        }
    }

    public boolean hit(Enemy e) {
        return Math.hypot(x - e.x, y - e.y) < 10;
    }

    public void draw(Graphics g) {
        g.setColor(Color.YELLOW);
        g.fillOval((int) x, (int) y, 5, 5);
    }
}
