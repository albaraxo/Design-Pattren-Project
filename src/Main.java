import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
// ✅ You don't need java.util.Timer; using Swing's timer below.
// import java.util.Timer;

/* ===================== Interfaces ===================== */

interface Character {
    void update();
    void draw(Graphics2D g);
    Rectangle getBounds();
    boolean isAlive();
}

interface Projectiles {
    void update();
    void draw(Graphics2D g);
    Rectangle getBounds();
    boolean isActive();
}

/* ===================== End Game State (Singletons) ===================== */

interface EndGameState {
    String message();
}

final class Won implements EndGameState {
    private static final Won INSTANCE = new Won();
    private Won() {}
    public static Won getInstance() { return INSTANCE; }
    @Override public String message() { return "YOU WON! 🎉"; }
}

final class GameOver implements EndGameState {
    private static final GameOver INSTANCE = new GameOver();
    private GameOver() {}
    public static GameOver getInstance() { return INSTANCE; }
    @Override public String message() { return "GAME OVER 💀"; }
}

/* ===================== Player (Singleton) ===================== */

final class Player implements Character {
    private static final Player INSTANCE = new Player();

    private int x, y, w, h;
    private int speed = 6;
    private boolean alive = true;
    private int cooldown = 0;

    private Player() {
        this.w = 40;
        this.h = 18;
    }

    public static Player getInstance() { return INSTANCE; }

    public void spawnAt(int x, int y) {
        this.x = x; this.y = y;
        this.alive = true;
        this.cooldown = 0;
    }

    public void moveLeft()  { this.x -= speed; }
    public void moveRight() { this.x += speed; }

    public Shot shoot() {
        if (cooldown == 0 && alive) {
            cooldown = 12; // small delay between shots
            // Spawn slightly above so it's visible immediately
            Rectangle r = getBounds();
            return new Shot(r.x + r.width/2 - 3, r.y - 18);
        }
        return null;
    }

    @Override public void update() {
        if (cooldown > 0) cooldown--;
        // clamp inside board
        Board b = Board.getInstance();
        x = Math.max(0, Math.min(b.getWidth() - w, x));
    }

    @Override public void draw(Graphics2D g) {
        g.fillRect(x, y, w, h);
        // little “cannon” tip
        g.fillRect(x + w/2 - 3, y - 6, 6, 6);
    }

    @Override public Rectangle getBounds() { return new Rectangle(x, y, w, h); }

    @Override public boolean isAlive() { return alive; }

    public void kill() { alive = false; }
}

/* ===================== Alien hierarchy + Factory ===================== */


abstract class Alien implements Character {
    private int x, y, w = 28, h = 18;
    private int dx;             // horizontal velocity
    private boolean alive = true;
    private int stepDown;       // pixels to step down when bouncing
    private int speed;          // magnitude
    private Color color;

    // NEW: RNG + bomb rate control
    private static final Random RNG = new Random();
    private static final double FPS = 60.0;
    // bombs per alien per second (tune this)
    private static final double BOMBS_PER_SECOND = 0.05; // ~= 1 bomb every 20s per alien
    private static final double P_PER_FRAME = BOMBS_PER_SECOND / FPS;
    private int bombCooldown = 0; // frames

    protected Alien(int x, int y, int speed, int stepDown, Color color) {
        this.x = x; this.y = y;
        this.speed = speed; this.stepDown = stepDown; this.color = color;
        this.dx = speed;
    }

    @Override public void update() {
        Board b = Board.getInstance();
        x += dx;

        // bounce at edges and step down
        if (x <= 0 || x + w >= b.getWidth()) {
            dx = -dx;
            y += stepDown;
            // lose if we reach the player row
            if (y + h >= Player.getInstance().getBounds().y) {
                b.setEndState(GameOver.getInstance());
            }
        }

        // NEW: try to drop a bomb
        if (alive) {
            if (bombCooldown > 0) bombCooldown--;
            if (bombCooldown == 0 && RNG.nextDouble() < P_PER_FRAME) {
                Rectangle r = getBounds();
                int bx = r.x + r.width / 2 - 3;
                int by = r.y + r.height;
                b.addBomb(new Bomb(bx, by));
                bombCooldown = 30; // ~0.5s at 60fps so one alien doesn't spam
            }
        }
    }

    @Override public void draw(Graphics2D g) {
        g.setColor(color);
        g.fillRect(x, y, w, h);
        // eyes
        g.setColor(Color.BLACK);
        g.fillRect(x + 6, y + 6, 3, 3);
        g.fillRect(x + w - 9, y + 6, 3, 3);
    }

    @Override public Rectangle getBounds() { return new Rectangle(x, y, w, h); }

    @Override public boolean isAlive() { return alive; }

    public void kill() { alive = false; }
}

final class GruntAlien extends Alien {
    public GruntAlien(int x, int y) {
        super(x, y, 2, 16, new Color(80, 200, 120));
    }
}

final class FastAlien extends Alien {
    public FastAlien(int x, int y) {
        super(x, y, 4, 16, new Color(90, 170, 255));
    }
}

enum AlienType { GRUNT, FAST }

class AlienFactory {
    private AlienFactory() {}
    public static Alien create(AlienType type, int x, int y) {
        switch (type) {
            case FAST:  return new FastAlien(x, y);
            case GRUNT:
            default:    return new GruntAlien(x, y);
        }
    }
}

/* ===================== Projectiles ===================== */

class Shot implements Projectiles {
    private int x, y, w = 6, h = 16;
    private int dy = -12;
    private boolean active = true;

    Image img;

    public Shot(int x, int y) {
        try {
            ImageIcon ii = new ImageIcon(getClass().getResource("/img/shot.png"));
            img = ii.getImage();
        } catch (Exception e) {
            img = null; // will use rectangle fallback
        }
        this.x = x; this.y = y;
    }

    @Override public void update() {
        y += dy;
        if (y + h < 0) active = false;
    }

    @Override
    public void draw(Graphics2D g) {
        if (img != null) g.drawImage(img, x, y, w, h, null);
        else g.fillRect(x, y, w, h); // fallback so you SEE it
    }

    @Override public Rectangle getBounds() { return new Rectangle(x, y, w, h); }

    @Override public boolean isActive() { return active; }

    public void deactivate() { active = false; }
}

/* NEW ===================== Bomb (Alien projectile) ===================== */

final class Bomb implements Projectiles {
    private int x, y, w = 6, h = 12;
    private int dy = 6;
    private boolean active = true;

    public Bomb(int x, int y) { this.x = x; this.y = y; }

    @Override public void update() {
        y += dy;
        if (y > Board.getInstance().getHeight()) active = false;
    }

    @Override public void draw(Graphics2D g) {
        g.fillRect(x, y, w, h);
    }

    @Override public Rectangle getBounds() { return new Rectangle(x, y, w, h); }

    @Override public boolean isActive() { return active; }

    public void deactivate() { active = false; }
}

/* ===================== Board (Singleton) ===================== */

final class Board {
    private static final Board INSTANCE = new Board();

    private final int width = 800;
    private final int height = 600;

    private final List<Alien> aliens = new ArrayList<>();
    private final List<Shot> shots = new ArrayList<>();
    // NEW: bombs storage
    private final List<Bomb> bombs = new ArrayList<>();

    private EndGameState endState = null;

    private Board() {}

    public static Board getInstance() { return INSTANCE; }

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }

    public void setEndState(EndGameState state) { this.endState = state; }
    public EndGameState getEndState() { return endState; }

    public void reset() {
        aliens.clear();
        shots.clear();
        bombs.clear();
        endState = null;

        // place player
        Player.getInstance().spawnAt(width/2 - 20, height - 60);

        // spawn a grid of aliens via factory
        int cols = 10, rows = 4, gapX = 14, gapY = 18;
        int startX = 60, startY = 60;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                AlienType t = (r % 2 == 0) ? AlienType.GRUNT : AlienType.FAST;
                int x = startX + c * (28 + gapX);
                int y = startY + r * (18 + gapY);
                aliens.add(AlienFactory.create(t, x, y));
            }
        }
    }

    public void addShot(Shot s) { if (s != null) shots.add(s); }
    public void addBomb(Bomb b) { if (b != null) bombs.add(b); } // NEW

    public void update() {
        if (endState != null) return;

        // update entities
        Player.getInstance().update();
        for (Alien a : aliens) a.update();
        for (Shot s : shots) s.update();
        for (Bomb b : bombs) b.update(); // NEW

        // collisions: shot vs alien
        for (Shot s : shots) {
            if (!s.isActive()) continue;
            for (Alien a : aliens) {
                if (a.isAlive() && s.getBounds().intersects(a.getBounds())) {
                    if (a instanceof Alien) ((Alien)a).kill();
                    s.deactivate();
                    break;
                }
            }
        }

        // NEW: collisions: bomb vs player
        Rectangle pr = Player.getInstance().getBounds();
        for (Bomb b : bombs) {
            if (b.isActive() && b.getBounds().intersects(pr)) {
                b.deactivate();
                Player.getInstance().kill();
                setEndState(GameOver.getInstance());
            }
        }

        // cleanup
        aliens.removeIf(a -> !a.isAlive());
        shots.removeIf(s -> !s.isActive());
        bombs.removeIf(b -> !b.isActive()); // NEW

        // win condition
        if (aliens.isEmpty() && endState == null) {
            endState = Won.getInstance();
        }
    }

    public void draw(Graphics2D g) {
        // background
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        // entities
        g.setColor(Color.WHITE);
        Player.getInstance().draw(g);
        for (Alien a : aliens) a.draw(g);
        for (Shot s : shots) s.draw(g);

        // NEW: bombs in red
        g.setColor(Color.RED);
        for (Bomb b : bombs) b.draw(g);

        // UI
        if (endState != null) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 36));
            String msg = endState.message() + "  (Press R to restart)";
            int tw = g.getFontMetrics().stringWidth(msg);
            g.drawString(msg, (width - tw)/2, height/2);
        }
    }
}

/* ===================== SpaceInvadersGame (JFrame) ===================== */

class SpaceInvadersGame extends JFrame {
    public SpaceInvadersGame() {
        setTitle("Space Invaders – Singleton + Factory");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel panel = new GamePanel();
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SpaceInvadersGame::new);
    }
}

/* ===================== GamePanel (loop + input) ===================== */

final class GamePanel extends JPanel implements ActionListener, KeyListener {

    public GamePanel() {
        setPreferredSize(new Dimension(Board.getInstance().getWidth(), Board.getInstance().getHeight()));
        setFocusable(true);
        setDoubleBuffered(true);
        setBackground(Color.BLACK);

        Board.getInstance().reset();
        addKeyListener(this);

        javax.swing.Timer timer = new javax.swing.Timer(16, this); // ~60 FPS
        timer.start();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Board.getInstance().draw((Graphics2D) g);
    }

    @Override public void actionPerformed(ActionEvent e) {
        Board.getInstance().update();
        repaint();
    }

    @Override public void keyPressed(KeyEvent e) {
        if (Board.getInstance().getEndState() != null) {
            if (e.getKeyCode() == KeyEvent.VK_R) {
                Board.getInstance().reset();
            }
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_LEFT)  Player.getInstance().moveLeft();
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) Player.getInstance().moveRight();
        if (e.getKeyCode() == KeyEvent.VK_SPACE) Board.getInstance().addShot(Player.getInstance().shoot());
    }

    @Override public void keyReleased(KeyEvent e) { /* no-op */ }
    @Override public void keyTyped(KeyEvent e) { /* no-op */ }
}
