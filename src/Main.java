import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

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

    private int x, y, w = 40, h = 18;
    private int speed = 6;
    private boolean alive = true;
    private int cooldown = 0;

    private Player() {}

    public static Player getInstance() { return INSTANCE; }

    public void spawnAt(int x, int y) {
        this.x = x;
        this.y = y;
        alive = true;
        cooldown = 0;
    }

    public void moveLeft()  { x -= speed; }
    public void moveRight() { x += speed; }

    public Shot shoot() {
        if (cooldown == 0 && alive) {
            cooldown = 12;
            Rectangle r = getBounds();
            return new Shot(r.x + r.width / 2 - 3, r.y - 18);
        }
        return null;
    }

    @Override public void update() {
        if (cooldown > 0) cooldown--;
        Board b = Board.getInstance();
        x = Math.max(0, Math.min(b.getWidth() - w, x));
    }

    @Override public void draw(Graphics2D g) {
        g.fillRect(x, y, w, h);
        g.fillRect(x + w/2 - 3, y - 6, 6, 6);
    }

    @Override public Rectangle getBounds() { return new Rectangle(x, y, w, h); }
    @Override public boolean isAlive() { return alive; }
    public void kill() { alive = false; }
}

/* ===================== Alien Types & Flyweight ===================== */

enum AlienType { GRUNT, FAST }

final class AlienStyle {
    final int w, h, speed, stepDown;
    final Color color;

    AlienStyle(int w, int h, int speed, int stepDown, Color color) {
        this.w = w;
        this.h = h;
        this.speed = speed;
        this.stepDown = stepDown;
        this.color = color;
    }
}

final class AlienStyleFactory {
    private static final Map<AlienType, AlienStyle> CACHE = new EnumMap<>(AlienType.class);

    static AlienStyle getStyle(AlienType type) {
        AlienStyle style = CACHE.get(type);
        if (style == null) {
            switch (type) {
                case FAST:
                    style = new AlienStyle(28, 18, 8, 16, new Color(90,170,255));
                    break;
                case GRUNT:
                default:
                    style = new AlienStyle(28, 18, 2, 16, new Color(80,200,120));
                    break;
            }
            CACHE.put(type, style);
        }
        return style;
    }
}

/* ===================== Alien hierarchy ===================== */

abstract class BaseAlien implements Character {

    private int x, y;
    private int dx;
    private boolean alive = true;
    private int bombCooldown = 0;

    private final AlienStyle style;

    private static final Random RNG = new Random();
    private static final double FPS = 60.0;
    private static final double BOMBS_PER_SECOND = 0.05;
    private static final double P_PER_FRAME = BOMBS_PER_SECOND / FPS;

    protected BaseAlien(int x, int y, AlienStyle style) {
        this.x = x;
        this.y = y;
        this.style = style;
        this.dx = style.speed;
    }

    @Override public void update() {
        Board b = Board.getInstance();
        x += dx;

        if (x <= 0 || x + style.w >= b.getWidth()) {
            dx = -dx;
            y += style.stepDown;
            if (y + style.h >= Player.getInstance().getBounds().y) {
                b.setEndState(GameOver.getInstance());
            }
        }

        if (alive) {
            if (bombCooldown > 0) bombCooldown--;
            if (bombCooldown == 0 && RNG.nextDouble() < P_PER_FRAME) {
                Rectangle r = getBounds();
                b.addBomb(new Bomb(
                        r.x + r.width/2 - 3,
                        r.y + r.height
                ));
                bombCooldown = 10;
            }
        }
    }

    @Override public void draw(Graphics2D g) {
        g.setColor(style.color);
        g.fillRect(x, y, style.w, style.h);
        g.setColor(Color.BLACK);
        g.fillRect(x+6, y+6, 3,3);
        g.fillRect(x+style.w-9, y+6, 3,3);
    }

    @Override public Rectangle getBounds() { return new Rectangle(x, y, style.w, style.h); }
    @Override public boolean isAlive() { return alive; }
    public void kill() { alive = false; }
}

final class GruntAlien extends BaseAlien {
    public GruntAlien(int x, int y) {
        super(x, y, AlienStyleFactory.getStyle(AlienType.GRUNT));
    }
}

final class FastAlien extends BaseAlien {
    public FastAlien(int x, int y) {
        super(x, y, AlienStyleFactory.getStyle(AlienType.FAST));
    }
}

/* ===================== Alien Factory ===================== */

class AlienFactory {
    public BaseAlien create(AlienType type, int x, int y) {
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

    public Shot(int x, int y) { this.x = x; this.y = y; }

    @Override public void update() {
        y += dy;
        if (y + h < 0) active = false;
    }

    @Override public void draw(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(x, y, w, h);
    }

    @Override public Rectangle getBounds() { return new Rectangle(x, y, w, h); }
    @Override public boolean isActive() { return active; }
    public void deactivate() { active = false; }
}

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
        g.setColor(Color.RED);
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

    private final List<BaseAlien> aliens = new ArrayList<>();
    private final List<Shot> shots = new ArrayList<>();
    private final List<Bomb> bombs = new ArrayList<>();

    private EndGameState endState = null;

    private Board() {}

    public static Board getInstance() { return INSTANCE; }

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }

    public void setEndState(EndGameState state) { endState = state; }
    public EndGameState getEndState() { return endState; }

    public void reset() {
        aliens.clear();
        shots.clear();
        bombs.clear();
        endState = null;

        Player.getInstance().spawnAt(width/2 - 20, height - 60);

        AlienFactory factory = new AlienFactory();

        int cols = 10, rows = 4, gapX = 14, gapY = 18;
        int startX = 60, startY = 60;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                AlienType t = (r % 2 == 0) ? AlienType.GRUNT : AlienType.FAST;
                aliens.add(factory.create(
                        t,
                        startX + c * (28 + gapX),
                        startY + r * (18 + gapY)
                ));
            }
        }
    }

    public void addShot(Shot s) { if (s != null) shots.add(s); }
    public void addBomb(Bomb b) { if (b != null) bombs.add(b); }

    public void update() {
        if (endState != null) return;

        Player.getInstance().update();
        aliens.forEach(BaseAlien::update);
        shots.forEach(Shot::update);
        bombs.forEach(Bomb::update);

        for (Shot s : shots) {
            if (!s.isActive()) continue;
            for (BaseAlien a : aliens) {
                if (a.isAlive() && s.getBounds().intersects(a.getBounds())) {
                    a.kill();
                    s.deactivate();
                    break;
                }
            }
        }

        Rectangle pr = Player.getInstance().getBounds();
        for (Bomb b : bombs) {
            if (b.isActive() && b.getBounds().intersects(pr)) {
                b.deactivate();
                Player.getInstance().kill();
                setEndState(GameOver.getInstance());
            }
        }

        aliens.removeIf(a -> !a.isAlive());
        shots.removeIf(s -> !s.isActive());
        bombs.removeIf(b -> !b.isActive());

        if (aliens.isEmpty() && endState == null) {
            endState = Won.getInstance();
        }
    }

    public void draw(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.WHITE);
        Player.getInstance().draw(g);
        aliens.forEach(a -> a.draw(g));
        shots.forEach(s -> s.draw(g));
        bombs.forEach(b -> b.draw(g));

        if (endState != null) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 36));
            String msg = endState.message() + "  (Press R to restart)";
            int tw = g.getFontMetrics().stringWidth(msg);
            g.drawString(msg, (width - tw)/2, height/2);
        }
    }
}

/* ===================== Facade (ShapeMaker-style) ===================== */

final class SpaceInvadersFacade {

    private final Board board;
    private final Player player;
    private final AlienFactory factory; // here just to show cooperation

    public SpaceInvadersFacade() {
        board = Board.getInstance();
        player = Player.getInstance();
        factory = new AlienFactory();
    }

    public void startGame() {
        board.reset();
    }

    public void moveLeft()  { player.moveLeft(); }
    public void moveRight() { player.moveRight(); }

    public void playerShoot() {
        board.addShot(player.shoot());
    }

    public void updateGame() {
        board.update();
    }

    public void drawGame(Graphics2D g) {
        board.draw(g);
    }

    public boolean isGameOver() {
        return board.getEndState() != null;
    }

    public void restartGame() {
        board.reset();
    }
}

/* ===================== GamePanel ===================== */

final class GamePanel extends JPanel implements ActionListener, KeyListener {

    private final SpaceInvadersFacade game = new SpaceInvadersFacade();

    public GamePanel() {
        game.startGame();

        setPreferredSize(new Dimension(
                Board.getInstance().getWidth(),
                Board.getInstance().getHeight()
        ));
        setFocusable(true);
        setDoubleBuffered(true);
        setBackground(Color.BLACK);

        addKeyListener(this);

        new javax.swing.Timer(16, this).start();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        game.drawGame((Graphics2D) g);
    }

    @Override public void actionPerformed(ActionEvent e) {
        game.updateGame();
        repaint();
    }

    @Override public void keyPressed(KeyEvent e) {

        if (game.isGameOver()) {
            if (e.getKeyCode() == KeyEvent.VK_R)
                game.restartGame();
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_LEFT)  game.moveLeft();
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) game.moveRight();
        if (e.getKeyCode() == KeyEvent.VK_SPACE) game.playerShoot();
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}

/* ===================== Main JFrame ===================== */
 class SpaceInvadersGame extends JFrame {

    public SpaceInvadersGame() {
        setTitle("Space Invaders – Singleton + Factory + Flyweight + Facade");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        setContentPane(new GamePanel());

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SpaceInvadersGame::new);
    }
}
