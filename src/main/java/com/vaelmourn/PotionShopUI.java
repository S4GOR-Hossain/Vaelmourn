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
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC 1's menu: buy potions, and upgrade potion effects.
 *
 * <p>Deliberately the only thing this NPC offers. Each potion appears twice — once as a
 * BUY row and once as an UPGRADE row — so the player sees the current level, the cost
 * and the resulting effect without opening a second screen.</p>
 *
 * <p>Levels, caps and effect scaling all come from {@link PotionUpgrades}; this class
 * only renders and spends currency. It never computes an effect itself.</p>
 */
public class PotionShopUI implements ActionListener {

    private final AssetManager assetManager;
    private final Camera camera;
    private final Node guiNode;
    private final Inventory inventory;
    private final PlayerStats playerStats;

    private final BitmapFont font;
    private final Node shopNode;
    private boolean shopOpen = false;
    private String npcName = "Potion Merchant";

    private final List<Row> rows = new ArrayList<>();
    /** Kept so rows can be rebuilt after a purchase without re-deriving stock from text. */
    private List<String> stockIds = new ArrayList<>();
    private List<Integer> stockPrices = new ArrayList<>();
    private int selectedIndex = 0;

    private final float uiScale;

    /** A purchase or an upgrade attempt. Rows execute themselves via {@link #buySelected}. */
    private static final class Row {
        final String label;
        final int price;      // -1 means unavailable/maxed
        final boolean upgrade;
        final String itemId;
        final String subLabel;

        Row(String label, int price, boolean upgrade, String itemId, String subLabel) {
            this.label = label;
            this.price = price;
            this.upgrade = upgrade;
            this.itemId = itemId;
            this.subLabel = subLabel;
        }
    }

    public PotionShopUI(AssetManager assetManager, InputManager inputManager, Camera camera,
                        Node guiNode, Inventory inventory, PlayerStats playerStats,
                        int screenWidth, int screenHeight) {
        this.assetManager = assetManager;
        this.camera = camera;
        this.guiNode = guiNode;
        this.inventory = inventory;
        this.playerStats = playerStats;

        uiScale = screenHeight / 1080f;
        font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        shopNode = new Node("PotionShopUI");

        // distinct action names from RunUpgradeUI so both listeners can stay
        // registered without one intercepting the other's keys
        inputManager.addMapping("PotionShopUp", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("PotionShopDown", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("PotionShopBuy", new KeyTrigger(KeyInput.KEY_RETURN), new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("PotionShopClose", new KeyTrigger(KeyInput.KEY_ESCAPE), new KeyTrigger(KeyInput.KEY_E));
        inputManager.addListener(this, "PotionShopUp", "PotionShopDown", "PotionShopBuy", "PotionShopClose");
    }

    public void show(String npcName, List<String> potionIds, List<Integer> prices) {
        if (shopOpen) return;
        SoundManager.playShopOpen();

        this.npcName = npcName;
        this.stockIds = new ArrayList<>(potionIds);
        this.stockPrices = new ArrayList<>(prices);
        shopOpen = true;
        selectedIndex = 0;

        buildRows();
        buildUI();
        guiNode.attachChild(shopNode);
    }

    private void buildRows() {
        rows.clear();
        PotionUpgrades up = playerStats.getPotionUpgrades();

        for (int i = 0; i < stockIds.size(); i++) {
            String id = stockIds.get(i);
            Item item = ItemRegistry.get(id);
            if (item == null) continue;

            int price = i < stockPrices.size() ? stockPrices.get(i) : 10;
            int lv = up.getLevel(id);

            // purchase row — always available, unaffected by upgrade level
            rows.add(new Row(item.name, price, false, id, "BUY"));

            // upgrade row — level, next level's effect, and cost (or MAX)
            if (lv >= PotionUpgrades.MAX_LEVEL) {
                rows.add(new Row(item.name, -1, true, id,
                        "Lv." + lv + "  MAX  (" + fmt(item.power) + " effect)"));
            } else {
                float now = up.effectivePower(id, item.power);
                float next = up.effectivePowerAtLevel(id, item.power, lv + 1);
                rows.add(new Row(item.name, up.getNextUpgradeCost(id), true, id,
                        "Lv." + lv + " -> Lv." + (lv + 1)
                                + "  UPGRADE  (" + fmt(now) + " -> " + fmt(next) + ")"));
            }
        }
        if (selectedIndex >= rows.size()) selectedIndex = 0;
    }

    /** Heal amounts are large; buff magnitudes are fractions. Format each sensibly. */
    private static String fmt(float v) {
        return v >= 1f ? String.format("%.0f", v) : String.format("%.2f", v);
    }

    private void buildUI() {
        shopNode.detachAllChildren();

        float padding = 40f * uiScale;
        float startX = padding;
        float startY = camera.getHeight() - padding;

        addText(npcName.toUpperCase() + " - POTIONS", startX, startY,
                24f * uiScale, ColorRGBA.Yellow);
        addText("Soul Dust: " + playerStats.getSoulDust(), startX,
                startY - 34f * uiScale, 16f * uiScale, new ColorRGBA(1f, 0.8f, 0f, 1f));

        float rowY = startY - 72f * uiScale;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean selected = (i == selectedIndex);

            if (selected) {
                Geometry hl = new Geometry("Highlight",
                        new com.jme3.scene.shape.Quad(640f * uiScale, 30f * uiScale));
                Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                m.setColor("Color", new ColorRGBA(0.5f, 0.5f, 0.5f, 0.3f));
                hl.setMaterial(m);
                hl.setLocalTranslation(startX + 320f * uiScale, rowY, -1f);
                shopNode.attachChild(hl);
            }

            ColorRGBA c = selected ? ColorRGBA.White : new ColorRGBA(0.8f, 0.8f, 0.8f, 1f);
            StringBuilder line = new StringBuilder(r.label).append("   ").append(r.subLabel);
            if (r.price >= 0) line.append("   (").append(r.price).append(" dust)");
            addText(line.toString(), startX, rowY, 15f * uiScale, c);
            rowY -= 30f * uiScale;
        }

        addText("W/S or UP/DOWN to navigate  |  ENTER to buy or upgrade  |  ESC or E to close",
                startX, rowY - 10f * uiScale, 13f * uiScale, new ColorRGBA(0.6f, 0.6f, 0.6f, 1f));
    }

    private void addText(String text, float x, float y, float size, ColorRGBA color) {
        BitmapText t = new BitmapText(font);
        t.setText(text);
        t.setSize(size);
        t.setColor(color);
        t.setLocalTranslation(x, y, 0f);
        shopNode.attachChild(t);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!shopOpen || !isPressed) return;
        // deliberately no rows.isEmpty() early-return before the close case:
        // that mistake leaves ESC/E dead when a shop is empty.
        switch (name) {
            case "PotionShopUp":
                if (rows.isEmpty()) return;
                selectedIndex = (selectedIndex - 1 + rows.size()) % rows.size();
                buildUI();
                break;
            case "PotionShopDown":
                if (rows.isEmpty()) return;
                selectedIndex = (selectedIndex + 1) % rows.size();
                buildUI();
                break;
            case "PotionShopBuy":
                buySelected();
                break;
            case "PotionShopClose":
                closeShop();
                break;
        }
    }

    private void buySelected() {
        if (rows.isEmpty()) return;
        Row r = rows.get(selectedIndex);

        if (r.price < 0) {
            System.out.println(r.label + " is already at max level.");
            return;
        }
        if (playerStats.getSoulDust() < r.price) {
            System.out.println("Not enough soul dust! Need " + r.price
                    + ", have " + playerStats.getSoulDust());
            return;
        }

        if (r.upgrade) {
            // PotionUpgrades refuses past MAX_LEVEL, so a stale row cannot overshoot.
            if (!playerStats.getPotionUpgrades().upgrade(r.itemId)) return;
            System.out.println("Upgraded " + r.label + " to Lv."
                    + playerStats.getPotionUpgrades().getLevel(r.itemId) + "!");
        } else {
            inventory.addItem(r.itemId, 1);
            System.out.println("Purchased " + r.label + " for " + r.price + " soul dust!");
        }

        playerStats.spendSoulDust(r.price);
        buildRows();   // refresh levels, costs and MAX flags
        buildUI();
    }

    public void closeShop() {
        shopOpen = false;
        shopNode.removeFromParent();
    }

    public boolean isOpen() { return shopOpen; }
    public Node getNode() { return shopNode; }
}
