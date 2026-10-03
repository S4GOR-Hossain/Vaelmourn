package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;

import java.util.List;

/**
 * NPC 2's menu: the run upgrades.
 *
 * <p>Dark-fantasy look: midnight backdrop, gold-framed header plates, colour-coded stat cards
 * with accent strips and drop shadows. Text is drawn by this class so it is centred exactly.
 * Logic and input handling are unchanged.</p>
 *
 * <p>Every level and cost comes from {@link RunUpgrades}; this class stores no balance
 * data of its own.</p>
 */
public class RunUpgradeUI implements ActionListener {

    private static final float CARD_W = 812f;
    private static final float CARD_H = 233f;
    private static final float BORDER = 4f;
    private static final float TEXT_Z = 20f;   // raise if text ever hides behind a box

    /** sRGB hex -> linear colour, so colours look the way the hex says under jME's gamma. */
    private static ColorRGBA rgb(int hex) {
        float r = (float) Math.pow(((hex >> 16) & 0xFF) / 255f, 2.2);
        float g = (float) Math.pow(((hex >> 8) & 0xFF) / 255f, 2.2);
        float b = (float) Math.pow((hex & 0xFF) / 255f, 2.2);
        return new ColorRGBA(r, g, b, 1f);
    }

    // ---- palette (visual only) ----
    private static final ColorRGBA COL_BG       = rgb(0x0B0D1F);
    private static final ColorRGBA COL_BAR      = rgb(0x12142B);
    private static final ColorRGBA COL_PLATE    = rgb(0x241E45);
    private static final ColorRGBA COL_SHADOW   = rgb(0x04050C);
    private static final ColorRGBA COL_CARD     = rgb(0x1C2347);
    private static final ColorRGBA COL_CARD_MAX = rgb(0x3A2E12);
    private static final ColorRGBA COL_BTN      = rgb(0x1FA876);
    private static final ColorRGBA COL_GOLD     = rgb(0xF2C14E);
    private static final ColorRGBA COL_GOLD_DIM = rgb(0x7A6428);
    private static final ColorRGBA COL_TEXT     = rgb(0xF0F2FA);
    private static final ColorRGBA COL_TEXT_DIM = rgb(0x9AA3C7);
    private static final ColorRGBA COL_NEXT     = rgb(0x6EF2A0);
    private static final ColorRGBA COL_ERROR    = rgb(0xFF6B6B);
    private static final ColorRGBA COL_TEXT_OFF = rgb(0x6C7396);
    private static final ColorRGBA COL_TXT_SHD  = new ColorRGBA(0f, 0f, 0f, 0.7f);

    /** Concept order and positions: ATK/SPD on top, AtkSpd/DEF below, LUCK centred last. */
    private static final RunUpgrades.Stat[] ORDER = {
            RunUpgrades.Stat.ATTACK,
            RunUpgrades.Stat.SPEED,
            RunUpgrades.Stat.ATTACK_SPEED,
            RunUpgrades.Stat.DEFENSE,
            RunUpgrades.Stat.LUCK,
    };
    private static final String[] LABELS = {"ATTACK", "SPEED", "ATK SPEED", "DEFENSE", "LUCK"};
    private static final String[] TAGS   = {"DAMAGE", "MOVEMENT", "RATE OF FIRE", "RESILIENCE", "FORTUNE"};
    private static final ColorRGBA[] ACCENT = {
            rgb(0xB5283A),   // attack   - crimson
            rgb(0x1E8FC4),   // speed    - cyan
            rgb(0xD98A1C),   // atk spd  - amber
            rgb(0x3F55C9),   // defense  - royal blue
            rgb(0x8A3FD1),   // luck     - violet
    };
    private static final float[][] POS = {
            {101f, 190f}, {998f, 190f}, {101f, 467f}, {998f, 467f}, {534f, 745f},
    };

    private final PlayerStats playerStats;
    private final Node guiNode;
    private final AssetManager assetManager;
    private final InputManager inputManager;
    private final float scaleX;
    private final float scaleY;
    private final int screenH;

    private final BitmapFont font;
    private final Node panelNode;
    private boolean open = false;

    private final List<UiKit.Button> buttons = UiKit.newButtonList();
    private int selectedIndex = 0;
    private boolean showFocus = false;
    /** Last refused action, shown under the cards until something succeeds. */
    private String message = "";

    public RunUpgradeUI(AssetManager assetManager, InputManager inputManager, Camera camera,
                        Node guiNode, PlayerStats playerStats,
                        int screenWidth, int screenHeight) {
        this.assetManager = assetManager;
        this.guiNode = guiNode;
        this.playerStats = playerStats;
        this.inputManager = inputManager;
        this.scaleX = screenWidth / 1920f;
        this.scaleY = screenHeight / 1080f;
        this.screenH = screenHeight;

        UiKit.init(screenWidth, screenHeight);
        font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        panelNode = new Node("RunUpgradeUI");

        inputManager.addMapping("RunUpgUp", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("RunUpgDown", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("RunUpgBuy", new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping("RunUpgClick", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("RunUpgClose", new KeyTrigger(KeyInput.KEY_ESCAPE), new KeyTrigger(KeyInput.KEY_E));
        inputManager.addListener(this, "RunUpgUp", "RunUpgDown", "RunUpgBuy", "RunUpgClick", "RunUpgClose");
    }

    public void show(String npcName) {
        if (open) return;
        SoundManager.playShopOpen();

        open = true;
        selectedIndex = 0;
        showFocus = false;
        message = "";
        buildUI();
        guiNode.attachChild(panelNode);
    }

    /**
     * Draws text centred (align 0), left (-1) or right (1) inside a box given in the same
     * 1920x1080 top-left coordinates the UiKit boxes use.
     */
    private void drawText(String s, float x, float y, float w, float h, float size,
                          ColorRGBA color, int align, boolean shadow) {
        if (shadow) {
            drawText(s, x + 2f, y + 2f, w, h, size, COL_TXT_SHD, align, false);
        }
        BitmapText t = new BitmapText(font);
        t.setSize(size * scaleY);
        t.setColor(color);
        t.setText(s);

        float bx = x * scaleX, bw = w * scaleX;
        float by = y * scaleY, bh = h * scaleY;
        float tw = t.getLineWidth();
        float th = t.getHeight();

        float px = bx + (bw - tw) / 2f;
        if (align < 0) px = bx;
        if (align > 0) px = bx + bw - tw;
        float topFromTop = by + (bh - th) / 2f;

        t.setLocalTranslation(px, screenH - topFromTop, shadow ? TEXT_Z : TEXT_Z - 1f);
        panelNode.attachChild(t);
    }

    private void buildUI() {
        panelNode.detachAllChildren();
        buttons.clear();

        UiKit.background(assetManager, panelNode, COL_BG);

        // ---- top bar ----
        UiKit.box(assetManager, panelNode, 0f, 0f, 1920f, 160f, COL_BAR, COL_GOLD_DIM, BORDER);

        // title plate
        UiKit.box(assetManager, panelNode, 109f, 44f, 560f, 96f, COL_SHADOW, COL_SHADOW, 1f);
        UiKit.box(assetManager, panelNode, 101f, 36f, 560f, 96f, COL_PLATE, COL_GOLD, BORDER);
        drawText("UPGRADE SHOP", 101f, 36f, 560f, 96f, 50f, COL_GOLD, 0, true);

        buildCoinsPlate();

        for (int i = 0; i < ORDER.length; i++) {
            buildCard(ORDER[i], LABELS[i], i);
        }

        // ---- bottom hint bar ----
        UiKit.box(assetManager, panelNode, 0f, 1030f, 1920f, 50f, COL_BAR, COL_GOLD_DIM, BORDER);
        if (!message.isEmpty()) {
            drawText(message, 0f, 982f, 1920f, 40f, 32f, COL_ERROR, 0, true);
        }
        drawText("Click UPGRADE to buy a level   |   W/S + Enter also work   |   ESC or E to close",
                0f, 1030f, 1920f, 50f, 22f, COL_TEXT_DIM, 0, false);
    }

    private void buildCoinsPlate() {
        UiKit.box(assetManager, panelNode, 1368f, 44f, 450f, 96f, COL_SHADOW, COL_SHADOW, 1f);
        UiKit.box(assetManager, panelNode, 1360f, 36f, 450f, 96f, COL_PLATE, COL_GOLD, BORDER);
        drawText("COINS", 1380f, 36f, 180f, 96f, 36f, COL_TEXT_DIM, -1, false);
        drawText(String.valueOf(playerStats.getSoulDust()), 1540f, 36f, 250f, 96f, 52f,
                COL_GOLD, 1, true);
    }

    private void buildCard(RunUpgrades.Stat stat, String label, int index) {
        RunUpgrades up = playerStats.getRunUpgrades();
        int lv = up.getLevel(stat);
        boolean maxed = up.isMaxed(stat);

        float x = POS[index][0], y = POS[index][1];
        boolean focused = showFocus && index == selectedIndex;
        ColorRGBA borderColor = (focused || maxed) ? COL_GOLD : COL_GOLD_DIM;

        // drop shadow
        UiKit.box(assetManager, panelNode, x + 8f, y + 8f, CARD_W, CARD_H, COL_SHADOW, COL_SHADOW, 1f);

        UiKit.Rect in = UiKit.box(assetManager, panelNode, x, y, CARD_W, CARD_H,
                maxed ? COL_CARD_MAX : COL_CARD, borderColor, focused ? BORDER + 3f : BORDER);

        // header strip with stat name (left) and tag (right)
        final float stripH = 68f;
        ColorRGBA strip = maxed ? COL_GOLD_DIM : ACCENT[index];
        UiKit.box(assetManager, panelNode, in.x, in.y, in.w, stripH, strip, strip, 1f);
        drawText(label, in.x + 26f, in.y, in.w * 0.6f, stripH, 44f, COL_TEXT, -1, true);
        drawText(maxed ? "MAXED" : TAGS[index], in.x, in.y, in.w - 26f, stripH, 24f,
                maxed ? COL_GOLD : COL_TEXT, 1, false);

        // level row: "Lvl a  >>  Lvl b"
        int to = maxed ? lv : lv + 1;
        float rowY = in.y + stripH + 6f, rowH = 70f;
        drawText("Lvl " + lv, in.x, rowY, in.w * 0.5f - 60f, rowH, 46f, COL_TEXT, 0, false);
        drawText(">>", in.x + in.w * 0.5f - 60f, rowY, 120f, rowH, 46f,
                maxed ? COL_TEXT_DIM : COL_GOLD, 0, false);
        drawText("Lvl " + to, in.x + in.w * 0.5f + 60f, rowY, in.w * 0.5f - 60f, rowH, 46f,
                maxed ? COL_GOLD : COL_NEXT, 0, false);

        // UPGRADE button (label is drawn by drawText so it is centred)
        float btnH = 54f;
        UiKit.Rect btn = new UiKit.Rect(in.x + 200f, in.y + in.h - btnH - 14f,
                in.w - 400f, btnH);
        final int cost = maxed ? -1 : up.getNextUpgradeCost(stat);
        UiKit.Button b = UiKit.button(assetManager, font, panelNode, buttons,
                "", btn, 32f, COL_BTN, () -> buy(stat, cost));
        boolean canBuy = !maxed && playerStats.getSoulDust() >= cost;
        b.setEnabled(canBuy);
        drawText(maxed ? "MAX LEVEL" : "UPGRADE  -  " + cost, btn.x, btn.y, btn.w, btn.h, 32f,
                canBuy ? COL_TEXT : COL_TEXT_OFF, 0, canBuy);
    }

    private void buy(RunUpgrades.Stat stat, int cost) {
        RunUpgrades up = playerStats.getRunUpgrades();
        if (cost < 0 || up.isMaxed(stat)) return;
        if (playerStats.getSoulDust() < cost) {
            notify("Not enough coins - " + cost + " needed");
            return;
        }
        if (!up.upgrade(stat)) return;          // refuses past MAX_LEVEL on a stale card
        playerStats.spendSoulDust(cost);
        message = "";
        buildUI();
    }

    /** Explains a refused click, so a dimmed button never just looks broken. */
    private void notify(String msg) {
        message = msg;
        buildUI();
    }

    private void buySelected() {
        RunUpgrades up = playerStats.getRunUpgrades();
        RunUpgrades.Stat stat = ORDER[selectedIndex];
        buy(stat, up.isMaxed(stat) ? -1 : up.getNextUpgradeCost(stat));
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!open || !isPressed) return;
        switch (name) {
            case "RunUpgUp":
                selectedIndex = (selectedIndex - 1 + ORDER.length) % ORDER.length;
                showFocus = true;
                buildUI();
                break;
            case "RunUpgDown":
                selectedIndex = (selectedIndex + 1) % ORDER.length;
                showFocus = true;
                buildUI();
                break;
            case "RunUpgBuy":
                buySelected();
                break;
            case "RunUpgClick": {
                Vector2f cur = inputManager.getCursorPosition();
                UiKit.hover(buttons, cur.x, cur.y);
                if (UiKit.click(buttons, cur.x, cur.y)) return;
                buySelected();
                break;
            }
            case "RunUpgClose":
                close();
                break;
        }
    }

    public void close() {
        open = false;
        panelNode.removeFromParent();
    }

    public boolean isOpen() { return open; }
    public Node getNode() { return panelNode; }
}