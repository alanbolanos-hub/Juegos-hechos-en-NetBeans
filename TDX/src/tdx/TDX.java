package tdx;

import javax.swing.*;
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

    String selectedTower = "NORMAL";

    public TDX() {
        setPreferredSize(new Dimension(900, 600));
        setBackground(Color.BLACK);
        addMouseListener(this);

        startWave();

        timer = new Timer(30, this);
        timer.start();
    }

    void startWave() {
        enemies.clear();

        for (int i = 0; i < wave * 5; i++) {
            enemies.add(new Enemy(0, 120 + (i % 5) * 60));
        }

        if (wave % 3 == 0) {
            enemies.add(new BossEnemy(0, 250));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;

        // fondo
        g2.setColor(new Color(30, 30, 30));
        g2.fillRect(0, 0, getWidth(), getHeight());

        // camino
        g2.setColor(new Color(80, 80, 80));
        g2.fillRoundRect(0, 100, 900, 180, 30, 30);

        g2.setColor(Color.GRAY);
        g2.drawRoundRect(0, 100, 900, 180, 30, 30);

        // base
        g2.setColor(new Color(0, 200, 100));
        g2.fillRoundRect(850, 120, 40, 140, 10, 10);

        // enemigos
        for (Enemy e : enemies) {
            e.draw(g2);
        }

        // torres
        for (Tower t : towers) {
            t.draw(g2);
        }

        // UI superior
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 14));
        g2.drawString("Vida: " + baseHealth, 10, 20);
        g2.drawString("Dinero: " + money, 120, 20);
        g2.drawString("Oleada: " + wave, 250, 20);

        // menú inferior
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(0, 500, 900, 100);

        g2.setColor(Color.WHITE);
        g2.drawString("[1] Normal", 20, 550);
        g2.drawString("[2] Fuego", 140, 550);
        g2.drawString("[3] Hielo", 260, 550);
        g2.drawString("[4] Eléctrica", 380, 550);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy en = it.next();
            en.move();

            if (en.x > 850) {
                baseHealth -= 10;
                it.remove();
            }
        }

        for (Tower t : towers) {
            t.update(enemies);
        }

        enemies.removeIf(en -> en.health <= 0);

        if (enemies.isEmpty()) {
            wave++;
            money += 100;
            startWave();
        }

        repaint();
    }

    @Override
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
            public void actionPerformed(ActionEvent e) { selectedTower = "NORMAL"; }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("2"), "f");
        getActionMap().put("f", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { selectedTower = "FIRE"; }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("3"), "i");
        getActionMap().put("i", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { selectedTower = "ICE"; }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("4"), "e");
        getActionMap().put("e", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { selectedTower = "ELEC"; }
        });
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("TDX");
        TDX game = new TDX();
        game.setKeyBindings();

        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    // ================= ENEMY =================
    class Enemy {
        int x, y;
        int health = 50;
        int speed = 2;

        Enemy(int x, int y) {
            this.x = x;
            this.y = y;
        }

        void move() {
            x += speed;
        }

        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;

            g2.setColor(Color.RED);
            g2.fillOval(x, y, 20, 20);

            g2.setColor(Color.BLACK);
            g2.fillRect(x, y - 6, 20, 4);

            g2.setColor(Color.GREEN);
            g2.fillRect(x, y - 6, (int)(20 * (health / 50.0)), 4);
        }
    }

    class BossEnemy extends Enemy {
        BossEnemy(int x, int y) {
            super(x, y);
            health = 200;
            speed = 1;
        }

        void draw(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;

            g2.setColor(Color.MAGENTA);
            g2.fillOval(x, y, 40, 40);

            g2.setColor(Color.BLACK);
            g2.fillRect(x, y - 8, 40, 6);

            g2.setColor(Color.GREEN);
            g2.fillRect(x, y - 8, (int)(40 * (health / 200.0)), 6);
        }
    }

    // ================= TOWER =================
    class Tower {
        int x, y;
        int range = 120;
        int cooldown = 0;
        String type;
        Enemy target = null;

        Tower(int x, int y, String type) {
            this.x = x;
            this.y = y;
            this.type = type;
        }

        void update(java.util.List<Enemy> enemies) {

            if (cooldown > 0) cooldown--;

            target = null;

            for (Enemy e : enemies) {
                if (inRange(e) && cooldown == 0) {

                    target = e;

                    int damage = 0;

                    switch (type) {
                        case "FIRE": damage = 25; break;
                        case "ICE": damage = 6; e.speed = 1; break;
                        case "ELEC": damage = 18; break;
                        default: damage = 15;
                    }

                    damage += wave * 2;

                    e.health -= damage;

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

            Color color;

            switch (type) {
                case "FIRE": color = Color.ORANGE; break;
                case "ICE": color = Color.CYAN; break;
                case "ELEC": color = Color.YELLOW; break;
                default: color = Color.WHITE;
            }

            g2.setColor(Color.BLACK);
            g2.fillOval(x+3, y+3, 25, 25);

            g2.setColor(color);
            g2.fillOval(x, y, 25, 25);

            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
            g2.drawOval(x - range/2, y - range/2, range, range);

            if (target != null) {
                g2.setStroke(new BasicStroke(2));
                g2.setColor(color);
                g2.drawLine(x + 12, y + 12, target.x + 10, target.y + 10);
            }
        }
    }

    // MouseListener
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
}