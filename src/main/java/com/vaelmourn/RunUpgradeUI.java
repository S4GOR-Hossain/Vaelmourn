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
 * NPC 2's menu: the five run upgrades.
 *
 * <p>Each row shows the stat, its current level out of {@link RunUpgrades#MAX_LEVEL},
 * the multiplier it currently contributes, the multiplier the next level would add, and
 * the cost. Maxed rows read MAX and cannot be bought.</p>
 *
 * <p>All levels, costs and multipliers are read from {@link RunUpgrades}. This class
 * holds no balance data of its own.</p>
 */
public class RunUpgradeUI implements ActionListener {

    private final PlayerStats playerStats;
    private final Camera camera;
    private final Node guiNode;
    private final AssetManager assetManager;

    private final BitmapFont font;
    private final Node panelNode;
    private boolean open = false;
    private String npcName = "Run Upgrader";

    private final List<RunUpgrades.Stat> stats = new ArrayList<>();
    private int selectedIndex = 0;
    private final float uiScale;

    public RunUpgradeUI(AssetManager assetManager, InputManager inputManager, Camera camera,
                        Node guiNode, PlayerStats playerStats,
                        int screenWidth, int screenHeight) {
        this.assetManager = assetManager;
        this.camera = camera;
        this.guiNode = guiNode;
        this.playerStats = playerStats;

        uiScale = screenHeight / 1080f;
        font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        panelNode = new Node("RunUpgradeUI");

        for (RunUpgrades.Stat s : RunUpgrades.Stat.values()) {
            stats.add(s);
        }

        // own action names; W/S/ENTER/ESC overlap with the other menus by design since
        // only one menu can be open at a time (see isUiOpen in ForestBiome)
        inputManager.addMapping("RunUpgUp", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("RunUpgDown", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("RunUpgBuy", new KeyTrigger(KeyInput.KEY_RETURN), new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("RunUpgClose", new KeyTrigger(KeyInput.KEY_ESCAPE), new KeyTrigger(KeyInput.KEY_E));
        inputManager.addListener(this, "RunUpgUp", "RunUpgDown", "RunUpgBuy", "RunUpgClose");
    }

    public void show(String npcName) {
        if (open) return;
        SoundManager.playShopOpen();

        this.npcName = npcName;
        open = true;
        selectedIndex = 0;
        buildUI();
        guiNode.attachChild(panelNode);
    }

    private void buildUI() {
        panelNode.detachAllChildren();

        float padding = 40f * uiScale;
        float startX = padding;
        float startY = camera.getHeight() - padding;

        addText(npcName.toUpperCase() + " - RUN UPGRADES", startX, startY,
                24f * uiScale, ColorRGBA.Yellow);
        addText("Soul Dust: " + playerStats.getSoulDust(), startX,
                startY - 34f * uiScale, 16f * uiScale, new ColorRGBA(1f, 0.8f, 0f, 1f));
        addText("These last for this run only.", startX, startY - 56f * uiScale,
                13f * uiScale, new ColorRGBA(0.6f, 0.6f, 0.6f, 1f));

        RunUpgrades up = playerStats.getRunUpgrades();
        float rowY = startY - 92f * uiScale;

        for (int i = 0; i < stats.size(); i++) {
            RunUpgrades.Stat s = stats.get(i);
            int lv = up.getLevel(s);
            boolean maxed = up.isMaxed(s);
            boolean selected = (i == selectedIndex);

            if (selected) {
                Geometry hl = new Geometry("Highlight",
                        new com.jme3.scene.shape.Quad(660f * uiScale, 32f * uiScale));
                Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                m.setColor("Color", new ColorRGBA(0.5f, 0.5f, 0.5f, 0.3f));
                hl.setMaterial(m);
                hl.setLocalTranslation(startX + 330f * uiScale, rowY, -1f);
                panelNode.attachChild(hl);
            }

            ColorRGBA c = selected ? ColorRGBA.White : new ColorRGBA(0.8f, 0.8f, 0.8f, 1f);
            StringBuilder line = new StringBuilder();
            line.append(pad(s.getLabel(), 14))
                .append("Lv. ").append(lv).append('/').append(RunUpgrades.MAX_LEVEL)
                .append("   x").append(String.format("%.2f", up.getMultiplier(s)));

            if (maxed) {
                line.append("   MAX");
            } else {
                // preview comes from the same table the game applies, so the number
                // shown here is exactly the number the player will get
                line.append("   -> x").append(String.format("%.2f", up.getNextMultiplier(s)))
                    .append("   (").append(up.getNextUpgradeCost(s)).append(" dust)");
            }

            addText(line.toString(), startX, rowY, 15f * uiScale, c);
            rowY -= 32f * uiScale;
        }

        addText("W/S or UP/DOWN to navigate  |  ENTER to upgrade  |  ESC or E to close",
                startX, rowY - 10f * uiScale, 13f * uiScale, new ColorRGBA(0.6f, 0.6f, 0.6f, 1f));
    }

    private static String pad(String s, int width) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < width) b.append(' ');
        return b.toString();
    }

    private void addText(String text, float x, float y, float size, ColorRGBA color) {
        BitmapText t = new BitmapText(font);
        t.setText(text);
        t.setSize(size);
        t.setColor(color);
        t.setLocalTranslation(x, y, 0f);
        panelNode.attachChild(t);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!open || !isPressed) return;
        switch (name) {
            case "RunUpgUp":
                selectedIndex = (selectedIndex - 1 + stats.size()) % stats.size();
                buildUI();
                break;
            case "RunUpgDown":
                selectedIndex = (selectedIndex + 1) % stats.size();
                buildUI();
                break;
            case "RunUpgBuy":
                buySelected();
                break;
            case "RunUpgClose":
                close();
                break;
        }
    }

    private void buySelected() {
        RunUpgrades.Stat s = stats.get(selectedIndex);
        RunUpgrades up = playerStats.getRunUpgrades();

        if (up.isMaxed(s)) {
            System.out.println(s.getLabel() + " is already at max level.");
            return;
        }
        int cost = up.getNextUpgradeCost(s);
        if (playerStats.getSoulDust() < cost) {
            System.out.println("Not enough soul dust! Need " + cost
                    + ", have " + playerStats.getSoulDust());
            return;
        }

        // upgrade() refuses past MAX_LEVEL, so the cap holds even on a stale row
        if (!up.upgrade(s)) return;
        playerStats.spendSoulDust(cost);
        System.out.println("Upgraded " + s.getLabel() + " to Lv." + up.getLevel(s) + "!");
        buildUI();
    }

    public void close() {
        open = false;
        panelNode.removeFromParent();
    }

    public boolean isOpen() { return open; }
    public Node getNode() { return panelNode; }
}
