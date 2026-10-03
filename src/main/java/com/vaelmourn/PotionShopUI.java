package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;

import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC 1's menu: buy potions, and upgrade potion effects.
 *
 * <p>Self-contained: draws everything itself (rects + centred text) in a 1920x1080 design
 * space that is scaled to the real screen, so it no longer depends on UiKit's text
 * placement. Cream cards with a coloured title strip per potion, 3D-style buttons with
 * hover and disabled states, drop shadows, and a gold outline for keyboard focus.</p>
 *
 * <p>Controls: click a button, or W/S pick a card, Enter buy, U upgrade, ESC/E close.
 * Every number is read live from the game (prices, coins, levels).</p>
 */
public class PotionShopUI implements ActionListener, AnalogListener {

    // ---- layout (1920x1080 design space, origin top-left) -------------------------
    private static final float DESIGN_W = 1920f;
    private static final float DESIGN_H = 1080f;
    private static final float CARD_W = 812f;
    private static final float CARD_H = 233f;
    private static final float HEADER_H = 74f;

    private static final float[][] POS = {
            {77f, 196f}, {1028f, 196f}, {77f, 489f}, {1028f, 489f}, {77f, 801f}, {1028f, 801f},
    };
    private static final String[] CONCEPT_IDS = {
            "health_potion", "speed_potion", "critical_potion", "regen_potion",
            "strength_potion", Bombs.ITEM_ID,
    };
    private static final String[] CONCEPT_TITLES = {
            "HEALING POTION", "SPEED POTION", "CRITICAL POTION", "REGEN POTION",
            "ATTACK POTION", "BOMB",
    };

    // ---- palette --------------------------------------------------------------------
    private static final ColorRGBA BG = c(0.91f, 0.85f, 0.67f);
    private static final ColorRGBA BG_BAND = c(0.86f, 0.79f, 0.60f);
    private static final ColorRGBA INK = c(0.25f, 0.15f, 0.07f);
    private static final ColorRGBA CREAM = c(1.00f, 0.97f, 0.88f);
    private static final ColorRGBA CARD = c(0.99f, 0.93f, 0.78f);
    private static final ColorRGBA CARD_EDGE = c(0.47f, 0.31f, 0.14f);
    private static final ColorRGBA PLATE = c(0.31f, 0.19f, 0.09f);
    private static final ColorRGBA GOLD = c(1.00f, 0.82f, 0.28f);
    private static final ColorRGBA SHADOW = new ColorRGBA(0f, 0f, 0f, 0.22f);
    private static final ColorRGBA TEXT_SHADOW = new ColorRGBA(0f, 0f, 0f, 0.35f);
    private static final ColorRGBA RED_NUM = c(0.80f, 0.16f, 0.12f);
    private static final ColorRGBA ERROR = c(0.72f, 0.12f, 0.10f);

    private static final ColorRGBA UP_FACE = c(0.89f, 0.38f, 0.26f);
    private static final ColorRGBA UP_EDGE = c(0.60f, 0.21f, 0.14f);
    private static final ColorRGBA BUY_FACE = c(0.33f, 0.71f, 0.36f);
    private static final ColorRGBA BUY_EDGE = c(0.18f, 0.44f, 0.21f);
    private static final ColorRGBA OFF_FACE = c(0.72f, 0.68f, 0.60f);
    private static final ColorRGBA OFF_EDGE = c(0.54f, 0.50f, 0.42f);

    /** Title-strip accent per concept slot. */
    private static final ColorRGBA[] ACCENTS = {
            c(0.80f, 0.26f, 0.28f), // healing
            c(0.25f, 0.58f, 0.85f), // speed
            c(0.92f, 0.62f, 0.12f), // critical
            c(0.29f, 0.68f, 0.40f), // regen
            c(0.84f, 0.45f, 0.20f), // attack
            c(0.30f, 0.30f, 0.35f), // bomb
    };
    private static final ColorRGBA DEFAULT_ACCENT = c(0.55f, 0.40f, 0.22f);

    private static ColorRGBA c(float r, float g, float b) { return new ColorRGBA(r, g, b, 1f); }

    // ---- state ----------------------------------------------------------------------
    private final AssetManager assetManager;
    private final Node guiNode;
    private final Inventory inventory;
    private final PlayerStats playerStats;
    private final InputManager inputManager;
    private final float screenW, screenH, sx, sy;

    private final BitmapFont font;
    private final Node shopNode;
    private boolean shopOpen = false;

    private final List<Card> cards = new ArrayList<>();
    private final List<Btn> buttons = new ArrayList<>();
    private List<String> stockIds = new ArrayList<>();
    private List<Integer> stockPrices = new ArrayList<>();
    private int selectedIndex = 0;
    private boolean showFocus = false;
    private int hoverIdx = -1;
    private float z = 0f;
    /** Last refused action, shown under the cards until something succeeds. */
    private String message = "";

    private static final class Card {
        final String itemId, title;
        final float x, y;
        final int price;
        final boolean upgradable;
        final ColorRGBA accent;

        Card(String itemId, String title, float x, float y, int price, boolean upgradable,
             ColorRGBA accent) {
            this.itemId = itemId; this.title = title;
            this.x = x; this.y = y;
            this.price = price; this.upgradable = upgradable; this.accent = accent;
        }
    }

    /** A clickable button in design-space coordinates. */
    private static final class Btn {
        final float x, y, w, h;
        final Runnable action; // null = inert (e.g. MAX)
        Btn(float x, float y, float w, float h, Runnable action) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.action = action;
        }
        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    public PotionShopUI(AssetManager assetManager, InputManager inputManager, Camera camera,
                        Node guiNode, Inventory inventory, PlayerStats playerStats,
                        int screenWidth, int screenHeight) {
        this.assetManager = assetManager;
        this.guiNode = guiNode;
        this.inventory = inventory;
        this.playerStats = playerStats;
        this.inputManager = inputManager;
        this.screenW = screenWidth;
        this.screenH = screenHeight;
        this.sx = screenWidth / DESIGN_W;
        this.sy = screenHeight / DESIGN_H;

        font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        shopNode = new Node("PotionShopUI");

        // distinct action names from RunUpgradeUI so both listeners can stay registered
        inputManager.addMapping("PotionShopUp", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("PotionShopDown", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("PotionShopBuy", new KeyTrigger(KeyInput.KEY_RETURN));
        inputManager.addMapping("PotionShopUpgrade", new KeyTrigger(KeyInput.KEY_U));
        inputManager.addMapping("PotionShopClick", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("PotionShopClose", new KeyTrigger(KeyInput.KEY_ESCAPE), new KeyTrigger(KeyInput.KEY_E));
        inputManager.addMapping("PotionShopMouse",
                new MouseAxisTrigger(MouseInput.AXIS_X, false), new MouseAxisTrigger(MouseInput.AXIS_X, true),
                new MouseAxisTrigger(MouseInput.AXIS_Y, false), new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addListener(this, "PotionShopUp", "PotionShopDown", "PotionShopBuy",
                "PotionShopUpgrade", "PotionShopClick", "PotionShopClose", "PotionShopMouse");
    }

    public void show(String npcName, List<String> potionIds, List<Integer> prices) {
        if (shopOpen) return;
        SoundManager.playShopOpen();

        this.stockIds = new ArrayList<>(potionIds);
        this.stockPrices = new ArrayList<>(prices);
        shopOpen = true;
        selectedIndex = 0;
        showFocus = false;
        hoverIdx = -1;
        message = "";

        buildCards();
        buildUI();
        guiNode.attachChild(shopNode);
    }

    /** Concept ids first (in concept order), then any extra stocked ids into free slots. */
    private void buildCards() {
        cards.clear();
        boolean[] used = new boolean[POS.length];

        for (int slot = 0; slot < CONCEPT_IDS.length; slot++) {
            int idx = stockIds.indexOf(CONCEPT_IDS[slot]);
            if (idx < 0) continue;
            Item item = ItemRegistry.get(CONCEPT_IDS[slot]);
            if (item == null) continue;
            cards.add(new Card(item.id, CONCEPT_TITLES[slot], POS[slot][0], POS[slot][1],
                    priceAt(idx), item.getGroup() == Item.Group.CONSUMABLE, ACCENTS[slot]));
            used[slot] = true;
        }

        int slot = 0;
        for (int i = 0; i < stockIds.size(); i++) {
            String id = stockIds.get(i);
            if (indexOf(CONCEPT_IDS, id) >= 0) continue;
            Item item = ItemRegistry.get(id);
            if (item == null) continue;
            while (slot < used.length && used[slot]) slot++;
            if (slot >= used.length) break;
            cards.add(new Card(id, item.name.toUpperCase(), POS[slot][0], POS[slot][1],
                    priceAt(i), item.getGroup() == Item.Group.CONSUMABLE, DEFAULT_ACCENT));
            used[slot] = true;
            slot++;
        }

        if (selectedIndex >= cards.size()) selectedIndex = 0;
    }

    private int priceAt(int stockIndex) {
        int price = stockIndex < stockPrices.size() ? stockPrices.get(stockIndex) : -1;
        if (price >= 0) return price;
        Item item = ItemRegistry.get(stockIds.get(stockIndex));
        return item != null ? item.value : 10;
    }

    private static int indexOf(String[] arr, String value) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(value)) return i;
        return -1;
    }

    // ---- drawing --------------------------------------------------------------------

    private void buildUI() {
        shopNode.detachAllChildren();
        buttons.clear();
        z = 0f;

        // background: sand with a darker top band behind the plates
        rect(0, 0, DESIGN_W, DESIGN_H, BG);
        rect(0, 0, DESIGN_W, 168f, BG_BAND);
        rect(0, 168f, DESIGN_W, 4f, CARD_EDGE);

        // title + coins plates
        plate(137f, 40f, 450f, 96f);
        text("Potion shop", 137f + 225f, 88f, 46f, CREAM, true);

        plate(1337f, 40f, 450f, 96f);
        text("Coins", 1337f + 130f, 88f, 44f, CREAM, true);
        text(String.valueOf(Math.round(playerStats.getSoulDust())), 1337f + 330f, 88f, 48f, GOLD, true);

        for (int i = 0; i < cards.size(); i++) buildCard(cards.get(i), i);

        if (!message.isEmpty()) {
            text(message, DESIGN_W / 2f, 1022f, 32f, ERROR, false);
        }
        text("Click a button to buy or upgrade   |   W/S select, Enter buy, U upgrade   |   ESC or E to close",
                DESIGN_W / 2f, 1058f, 22f, INK, false);
    }

    private void buildCard(Card c, int index) {
        Item item = ItemRegistry.get(c.itemId);
        if (item == null) return;

        // drop shadow, outline (gold when keyboard-focused), cream body
        rect(c.x + 8f, c.y + 8f, CARD_W, CARD_H, SHADOW);
        boolean focused = showFocus && index == selectedIndex;
        float edge = focused ? 6f : 4f;
        rect(c.x - (edge - 4f), c.y - (edge - 4f), CARD_W + (edge - 4f) * 2f, CARD_H + (edge - 4f) * 2f,
                focused ? GOLD : CARD_EDGE);
        rect(c.x + 4f, c.y + 4f, CARD_W - 8f, CARD_H - 8f, CARD);

        // coloured title strip with a lighter top highlight
        rect(c.x + 4f, c.y + 4f, CARD_W - 8f, HEADER_H, c.accent);
        rect(c.x + 4f, c.y + 4f, CARD_W - 8f, 6f, lighten(c.accent, 0.18f));
        rect(c.x + 4f, c.y + 4f + HEADER_H, CARD_W - 8f, 4f, darken(c.accent, 0.25f));
        text(c.title, c.x + CARD_W / 2f, c.y + 4f + HEADER_H / 2f, 44f, CREAM, true);

        float btnY = c.y + 152f;
        float btnH = 62f;

        if (c.upgradable) {
            PotionUpgrades up = playerStats.getPotionUpgrades();
            boolean maxed = up.isMaxed(c.itemId);

            // "Lvl n", centred, number in red
            levelText(c.x + CARD_W / 2f, c.y + 4f + HEADER_H + 38f, up.getLevel(c.itemId));

            final int cost = maxed ? -1 : up.getNextUpgradeCost(c.itemId);
            boolean canUp = !maxed && playerStats.getSoulDust() >= cost;
            button(c.x + 70f, btnY, 300f, btnH, maxed ? "MAX" : "UPGRADE " + cost,
                    canUp ? UP_FACE : OFF_FACE, canUp ? UP_EDGE : OFF_EDGE,
                    maxed ? null : () -> buyUpgrade(c.itemId, cost));

            boolean canBuy = playerStats.getSoulDust() >= c.price;
            button(c.x + CARD_W - 70f - 300f, btnY, 300f, btnH, "BUY " + c.price,
                    canBuy ? BUY_FACE : OFF_FACE, canBuy ? BUY_EDGE : OFF_EDGE,
                    () -> buyItem(c.itemId, c.price));
        } else {
            // bomb: nothing to upgrade, one wide centred BUY
            boolean canBuy = playerStats.getSoulDust() >= c.price;
            button(c.x + (CARD_W - 360f) / 2f, c.y + 4f + HEADER_H + 40f, 360f, 70f,
                    "BUY " + c.price, canBuy ? BUY_FACE : OFF_FACE, canBuy ? BUY_EDGE : OFF_EDGE,
                    () -> buyItem(c.itemId, c.price));
        }
    }

    /** Header plate: dark wood with a light inner line. */
    private void plate(float x, float y, float w, float h) {
        rect(x + 6f, y + 6f, w, h, SHADOW);
        rect(x, y, w, h, CARD_EDGE);
        rect(x + 4f, y + 4f, w - 8f, h - 8f, PLATE);
        rect(x + 4f, y + 4f, w - 8f, 4f, lighten(PLATE, 0.12f));
    }

    /** 3D-style button: dark bottom edge, lighter on hover, registers its hit box. */
    private void button(float x, float y, float w, float h, String label,
                        ColorRGBA face, ColorRGBA edge, Runnable action) {
        int myIdx = buttons.size();
        buttons.add(new Btn(x, y, w, h, action));
        boolean hot = myIdx == hoverIdx && action != null;
        ColorRGBA f = hot ? lighten(face, 0.10f) : face;

        rect(x + 4f, y + 6f, w, h, SHADOW);
        rect(x, y, w, h, edge);                         // outline + bottom lip
        rect(x + 3f, y + 3f, w - 6f, h - 11f, f);       // face
        rect(x + 3f, y + 3f, w - 6f, 5f, lighten(f, 0.15f)); // top shine
        text(label, x + w / 2f, y + (h - 8f) / 2f + 3f, 34f, CREAM, true);
    }

    private void levelText(float cx, float cy, int level) {
        BitmapText a = makeText("Lvl ", 42f, INK);
        BitmapText b = makeText(String.valueOf(level), 42f, RED_NUM);
        float total = a.getLineWidth() + b.getLineWidth();
        float left = cx * sx - total / 2f;
        float top = screenH - cy * sy + a.getHeight() * 0.46f;
        place(a, left, top);
        place(b, left + a.getLineWidth(), top);
    }

    // ---- primitives -----------------------------------------------------------------

    /** Filled rectangle in design coords (top-left origin). */
    private void rect(float dx, float dy, float dw, float dh, ColorRGBA color) {
        Geometry g = new Geometry("r", new Quad(dw * sx, dh * sy));
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        if (color.a < 1f) m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        g.setMaterial(m);
        g.setLocalTranslation(dx * sx, screenH - (dy + dh) * sy, nextZ());
        shopNode.attachChild(g);
    }

    /** Text centred on (dcx, dcy) in design coords, optional soft drop shadow. */
    private void text(String s, float dcx, float dcy, float size, ColorRGBA color, boolean shadow) {
        BitmapText t = makeText(s, size, color);
        float left = dcx * sx - t.getLineWidth() / 2f;
        float top = screenH - dcy * sy + t.getHeight() * 0.46f;
        if (shadow) {
            BitmapText s2 = makeText(s, size, TEXT_SHADOW);
            place(s2, left + 2f * sx, top - 2f * sy);
        }
        place(t, left, top);
    }

    private BitmapText makeText(String s, float size, ColorRGBA color) {
        BitmapText t = new BitmapText(font, false);
        t.setSize(size * sy);
        t.setColor(color);
        t.setText(s);
        return t;
    }

    private void place(BitmapText t, float left, float top) {
        t.setLocalTranslation(left, top, nextZ());
        shopNode.attachChild(t);
    }

    private float nextZ() { z += 0.01f; return z; }

    private static ColorRGBA lighten(ColorRGBA col, float a) {
        return new ColorRGBA(Math.min(1f, col.r + a), Math.min(1f, col.g + a), Math.min(1f, col.b + a), col.a);
    }

    private static ColorRGBA darken(ColorRGBA col, float a) {
        return new ColorRGBA(Math.max(0f, col.r - a), Math.max(0f, col.g - a), Math.max(0f, col.b - a), col.a);
    }

    // ---- actions --------------------------------------------------------------------

    private void buyItem(String itemId, int price) {
        if (price < 0) return;
        if (playerStats.getSoulDust() < price) {
            refuse("Not enough coins - " + price + " needed");
            return;
        }
        if (!inventory.addItem(itemId, 1)) {
            refuse("Inventory is full");
            return;
        }
        playerStats.spendSoulDust(price);
        message = "";
        refresh();
    }

    private void buyUpgrade(String itemId, int cost) {
        if (cost < 0) return;
        if (playerStats.getSoulDust() < cost) {
            refuse("Not enough coins - " + cost + " needed");
            return;
        }
        if (!playerStats.getPotionUpgrades().upgrade(itemId)) return;
        playerStats.spendSoulDust(cost);
        message = "";
        refresh();
    }

    /** Explains a refused click so a dimmed button never just looks broken. */
    private void refuse(String msg) {
        message = msg;
        buildUI();
    }

    private void refresh() {
        buildCards();
        buildUI();
    }

    private void buySelected() {
        if (cards.isEmpty()) return;
        Card c = cards.get(selectedIndex);
        buyItem(c.itemId, c.price);
    }

    private void upgradeSelected() {
        if (cards.isEmpty()) return;
        Card c = cards.get(selectedIndex);
        if (!c.upgradable) return;
        PotionUpgrades up = playerStats.getPotionUpgrades();
        if (up.isMaxed(c.itemId)) return;
        buyUpgrade(c.itemId, up.getNextUpgradeCost(c.itemId));
    }

    // ---- input ----------------------------------------------------------------------

    /** Index of the button under the cursor, or -1. */
    private int buttonAtCursor() {
        Vector2f cur = inputManager.getCursorPosition();
        float dx = cur.x / sx;
        float dy = (screenH - cur.y) / sy;
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).contains(dx, dy)) return i;
        }
        return -1;
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        if (!shopOpen || !"PotionShopMouse".equals(name)) return;
        int idx = buttonAtCursor();
        if (idx != hoverIdx) {
            hoverIdx = idx;
            buildUI();
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!shopOpen || !isPressed) return;
        switch (name) {
            case "PotionShopUp":
                if (cards.isEmpty()) return;
                selectedIndex = (selectedIndex - 1 + cards.size()) % cards.size();
                showFocus = true;
                buildUI();
                break;
            case "PotionShopDown":
                if (cards.isEmpty()) return;
                selectedIndex = (selectedIndex + 1) % cards.size();
                showFocus = true;
                buildUI();
                break;
            case "PotionShopBuy":
                buySelected();
                break;
            case "PotionShopUpgrade":
                upgradeSelected();
                break;
            case "PotionShopClick": {
                int idx = buttonAtCursor();
                if (idx >= 0 && buttons.get(idx).action != null) buttons.get(idx).action.run();
                break;
            }
            case "PotionShopClose":
                closeShop();
                break;
        }
    }

    public void closeShop() {
        shopOpen = false;
        shopNode.removeFromParent();
    }

    public boolean isOpen() { return shopOpen; }
    public Node getNode() { return shopNode; }
}