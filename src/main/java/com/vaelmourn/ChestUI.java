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
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import java.util.ArrayList;
import java.util.List;

/**
 * UI for displaying and looting items from a chest.
 */
public class ChestUI implements ActionListener, Chest.ChestUI {

    // ---- Theme colours -------------------------------------------------
    private static final ColorRGBA COL_DIM         = new ColorRGBA(0f, 0f, 0f, 0.60f);
    private static final ColorRGBA COL_BORDER      = new ColorRGBA(0.62f, 0.48f, 0.20f, 1f);
    private static final ColorRGBA COL_BORDER_DARK = new ColorRGBA(0.30f, 0.22f, 0.09f, 1f);
    private static final ColorRGBA COL_PANEL       = new ColorRGBA(0.09f, 0.08f, 0.11f, 0.96f);
    private static final ColorRGBA COL_HEADER      = new ColorRGBA(0.16f, 0.13f, 0.09f, 1f);
    private static final ColorRGBA COL_ROW_A       = new ColorRGBA(1f, 1f, 1f, 0.04f);
    private static final ColorRGBA COL_ROW_B       = new ColorRGBA(1f, 1f, 1f, 0.00f);
    private static final ColorRGBA COL_SELECT      = new ColorRGBA(0.85f, 0.65f, 0.20f, 0.28f);
    private static final ColorRGBA COL_SELECT_EDGE = new ColorRGBA(0.95f, 0.75f, 0.30f, 1f);
    private static final ColorRGBA COL_SLOT        = new ColorRGBA(0.04f, 0.04f, 0.06f, 1f);
    private static final ColorRGBA COL_TEXT        = new ColorRGBA(0.80f, 0.80f, 0.82f, 1f);
    private static final ColorRGBA COL_TEXT_SEL    = ColorRGBA.White;
    private static final ColorRGBA COL_GOLD        = new ColorRGBA(1f, 0.82f, 0.35f, 1f);
    private static final ColorRGBA COL_HINT        = new ColorRGBA(0.60f, 0.60f, 0.65f, 1f);

    private static final int MAX_VISIBLE_ROWS = 10;

    private Node chestNode;
    private BitmapFont font;
    private AssetManager assetManager;
    private RenderManager renderManager;
    private InputManager inputManager;
    private Inventory inventory;
    private boolean chestOpen = false;
    private Node guiNode;
    private Camera camera;

    private List<ChestItem> displayedItems = new ArrayList<>();
    // The chest's own loot lists, so looted items are removed from the chest itself.
    private List<String> sourceIds;
    private List<Integer> sourceCounts;
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    private float uiScale = 1f;

    public ChestUI(AssetManager assetManager, RenderManager renderManager,
                   InputManager inputManager, Camera camera, Node guiNode,
                   Inventory inventory,
                   int screenWidth, int screenHeight) {
        this.assetManager = assetManager;
        this.renderManager = renderManager;
        this.inputManager = inputManager;
        this.camera = camera;
        this.guiNode = guiNode;
        this.inventory = inventory;

        uiScale = screenHeight / 1080f;

        font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        chestNode = new Node("ChestUI");

        inputManager.addMapping("ChestUp", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("ChestDown", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("ChestLoot", new KeyTrigger(KeyInput.KEY_RETURN), new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("ChestClose", new KeyTrigger(KeyInput.KEY_ESCAPE), new KeyTrigger(KeyInput.KEY_E));

        inputManager.addListener(this, "ChestUp", "ChestDown", "ChestLoot", "ChestClose");
    }

    @Override
    public void show(List<String> itemIds, List<Integer> counts) {
        if (chestOpen) return;

        chestOpen = true;
        displayedItems.clear();
        selectedIndex = 0;
        scrollOffset = 0;
        sourceIds = itemIds;
        sourceCounts = counts;

        for (int i = 0; i < itemIds.size(); i++) {
            String itemId = itemIds.get(i);
            int count = counts.get(i);
            Item item = ItemRegistry.get(itemId);
            if (item != null) {
                displayedItems.add(new ChestItem(itemId, item.name, count, item));
            }
        }

        // Nothing displayable: don't open (otherwise ESC/E would be ignored and the UI stuck).
        if (displayedItems.isEmpty()) {
            chestOpen = false;
            return;
        }

        buildUI();
        guiNode.attachChild(chestNode);
    }

    // ---- Small drawing helpers ----------------------------------------

    /** Flat coloured rectangle with alpha blending. z controls draw order (higher = in front). */
    private Geometry addRect(String name, float x, float y, float z, float w, float h, ColorRGBA color) {
        Geometry g = new Geometry(name, new Quad(w, h));
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        chestNode.attachChild(g);
        return g;
    }

    /** Text whose top-left corner is at (x, y). */
    private BitmapText addText(String text, float size, ColorRGBA color, float x, float y, float z) {
        BitmapText t = new BitmapText(font, false);
        t.setSize(size);
        t.setColor(color);
        t.setText(text);
        t.setLocalTranslation(x, y, z);
        chestNode.attachChild(t);
        return t;
    }

    /** Rectangle with a 2-tone border drawn around it. */
    private void addFramedRect(String name, float x, float y, float z, float w, float h,
                               float border, ColorRGBA edge, ColorRGBA fill) {
        addRect(name + "Edge", x, y, z, w, h, edge);
        addRect(name + "Fill", x + border, y + border, z + 0.1f, w - border * 2, h - border * 2, fill);
    }

    private void buildUI() {
        chestNode.detachAllChildren();

        float s = uiScale;
        float screenW = camera.getWidth();
        float screenH = camera.getHeight();

        // Keep the selection inside the visible window.
        int total = displayedItems.size();
        int visible = Math.min(total, MAX_VISIBLE_ROWS);
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex;
        if (selectedIndex >= scrollOffset + MAX_VISIBLE_ROWS) scrollOffset = selectedIndex - MAX_VISIBLE_ROWS + 1;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, total - MAX_VISIBLE_ROWS)));

        // ---- Layout ----
        float panelW   = 560f * s;
        float headerH  = 64f * s;
        float rowH     = 48f * s;
        float footerH  = 46f * s;
        float pad      = 18f * s;
        float listH    = Math.max(visible, 3) * rowH; // keep a sensible minimum size
        float panelH   = headerH + listH + footerH + pad * 2;
        float panelX   = (screenW - panelW) / 2f;
        float panelY   = (screenH - panelH) / 2f;
        float bw       = 3f * s;

        // ---- Dim background ----
        addRect("Dim", 0, 0, 0, screenW, screenH, COL_DIM);

        // ---- Panel frame ----
        addRect("Shadow", panelX + 6f * s, panelY - 6f * s, 0.5f, panelW, panelH, new ColorRGBA(0, 0, 0, 0.5f));
        addFramedRect("PanelOuter", panelX, panelY, 1f, panelW, panelH, bw, COL_BORDER_DARK, COL_BORDER);
        addFramedRect("PanelInner", panelX + bw, panelY + bw, 1.5f, panelW - bw * 2, panelH - bw * 2,
                bw * 0.5f, COL_BORDER_DARK, COL_PANEL);

        // ---- Header ----
        float headerY = panelY + panelH - bw * 1.5f - headerH;
        addRect("Header", panelX + bw * 1.5f, headerY, 2f, panelW - bw * 3f, headerH, COL_HEADER);
        addRect("HeaderLine", panelX + bw * 1.5f, headerY, 2.1f, panelW - bw * 3f, 2f * s, COL_BORDER);

        float titleSize = 26f * s;
        addText("Chest Contents", titleSize, COL_GOLD,
                panelX + pad + 6f * s, headerY + headerH / 2f + titleSize / 2f, 3f);

        String countStr = total + (total == 1 ? " item" : " items");
        BitmapText countText = addText(countStr, 14f * s, COL_HINT, 0, headerY + headerH / 2f + 7f * s, 3f);
        countText.setLocalTranslation(panelX + panelW - pad - 6f * s - countText.getLineWidth(),
                headerY + headerH / 2f + 7f * s, 3f);

        // ---- Item list ----
        float listTop = headerY - pad * 0.5f;

        for (int row = 0; row < visible; row++) {
            int i = scrollOffset + row;
            ChestItem item = displayedItems.get(i);
            boolean selected = (i == selectedIndex);

            float rowTop = listTop - row * rowH;
            float rowBottom = rowTop - rowH;
            float rowX = panelX + pad;
            float rowW = panelW - pad * 2 - 10f * s; // leave room for scrollbar

            // Row background (zebra stripe) / selection highlight
            addRect("Row" + i, rowX, rowBottom + 2f * s, 2f, rowW, rowH - 4f * s, (row % 2 == 0) ? COL_ROW_A : COL_ROW_B);
            if (selected) {
                addRect("Highlight", rowX, rowBottom + 2f * s, 2.2f, rowW, rowH - 4f * s, COL_SELECT);
                addRect("HighlightEdge", rowX, rowBottom + 2f * s, 2.3f, 4f * s, rowH - 4f * s, COL_SELECT_EDGE);
            }

            // Icon slot with the item's first letter
            float slot = rowH - 12f * s;
            float slotX = rowX + 14f * s;
            float slotY = rowBottom + 6f * s;
            addFramedRect("Slot" + i, slotX, slotY, 2.5f, slot, slot, 2f * s,
                    selected ? COL_SELECT_EDGE : COL_BORDER_DARK, COL_SLOT);
            String letter = (item.name == null || item.name.isEmpty()) ? "?" : item.name.substring(0, 1).toUpperCase();
            BitmapText letterText = addText(letter, 20f * s, selected ? COL_GOLD : COL_TEXT, 0, 0, 3f);
            letterText.setLocalTranslation(slotX + (slot - letterText.getLineWidth()) / 2f,
                    slotY + slot / 2f + 10f * s, 3f);

            // Name
            float nameSize = 17f * s;
            addText(item.name, nameSize, selected ? COL_TEXT_SEL : COL_TEXT,
                    slotX + slot + 14f * s, rowBottom + rowH / 2f + nameSize / 2f - 1f * s, 3f);

            // Count badge (right aligned)
            String cnt = "x" + item.count;
            BitmapText cntText = addText(cnt, 17f * s, selected ? COL_GOLD : COL_HINT, 0, 0, 3f);
            cntText.setLocalTranslation(rowX + rowW - 14f * s - cntText.getLineWidth(),
                    rowBottom + rowH / 2f + 17f * s / 2f - 1f * s, 3f);
        }

        // ---- Scrollbar (only when the list overflows) ----
        if (total > MAX_VISIBLE_ROWS) {
            float trackX = panelX + panelW - pad - 6f * s;
            float trackH = visible * rowH;
            float trackBottom = listTop - trackH;
            addRect("ScrollTrack", trackX, trackBottom, 2f, 6f * s, trackH, new ColorRGBA(1f, 1f, 1f, 0.08f));
            float thumbH = Math.max(24f * s, trackH * MAX_VISIBLE_ROWS / (float) total);
            float maxScroll = total - MAX_VISIBLE_ROWS;
            float t = maxScroll <= 0 ? 0 : scrollOffset / maxScroll;
            float thumbY = listTop - thumbH - t * (trackH - thumbH);
            addRect("ScrollThumb", trackX, thumbY, 2.5f, 6f * s, thumbH, COL_BORDER);
        }

        // ---- Footer ----
        float footerY = panelY + bw * 1.5f;
        addRect("FooterLine", panelX + bw * 1.5f, footerY + footerH, 2.1f, panelW - bw * 3f, 1f * s,
                new ColorRGBA(COL_BORDER.r, COL_BORDER.g, COL_BORDER.b, 0.5f));

        float hintSize = 13f * s;
        BitmapText instructions = addText(
                "W/S or UP/DOWN: navigate   |   ENTER: loot   |   ESC or E: close",
                hintSize, COL_HINT, 0, 0, 3f);
        instructions.setLocalTranslation(panelX + (panelW - instructions.getLineWidth()) / 2f,
                footerY + footerH / 2f + hintSize / 2f, 3f);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!chestOpen) return;
        if (!isPressed) return;
        if (displayedItems.isEmpty()) return;

        switch (name) {
            case "ChestUp":
                selectedIndex = (selectedIndex - 1 + displayedItems.size()) % displayedItems.size();
                buildUI();
                break;
            case "ChestDown":
                selectedIndex = (selectedIndex + 1) % displayedItems.size();
                buildUI();
                break;
            case "ChestLoot":
                lootSelectedItem();
                break;
            case "ChestClose":
                closeChest();
                break;
        }
    }

    private void lootSelectedItem() {
        if (selectedIndex < 0 || selectedIndex >= displayedItems.size()) return;

        ChestItem item = displayedItems.get(selectedIndex);

        inventory.addItem(item.itemId, item.count);
        System.out.println("Looted " + item.count + "x " + item.name);

        // Remove the stack from the chest itself so it isn't there (or duplicated) on reopen.
        if (sourceIds != null && sourceCounts != null) {
            for (int i = 0; i < sourceIds.size(); i++) {
                if (sourceIds.get(i).equals(item.itemId) && sourceCounts.get(i) == item.count) {
                    sourceIds.remove(i);
                    sourceCounts.remove(i);
                    break;
                }
            }
        }

        displayedItems.remove(selectedIndex);
        if (displayedItems.isEmpty()) {
            closeChest();
        } else {
            selectedIndex = Math.min(selectedIndex, displayedItems.size() - 1);
            buildUI();
        }
    }

    public void closeChest() {
        if (!chestOpen) return;

        chestOpen = false;
        chestNode.removeFromParent();
        displayedItems.clear();
    }

    public boolean isOpen() {
        return chestOpen;
    }

    public Node getNode() {
        return chestNode;
    }

    private static class ChestItem {
        String itemId;
        String name;
        int count;
        Item item;

        ChestItem(String itemId, String name, int count, Item item) {
            this.itemId = itemId;
            this.name = name;
            this.count = count;
            this.item = item;
        }
    }
}