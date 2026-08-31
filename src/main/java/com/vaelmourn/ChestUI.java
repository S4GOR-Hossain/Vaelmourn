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
    private int selectedIndex = 0;

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

        // Register input handlers
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

        // Build the display list
        for (int i = 0; i < itemIds.size(); i++) {
            String itemId = itemIds.get(i);
            int count = counts.get(i);
            Item item = ItemRegistry.get(itemId);
            if (item != null) {
                displayedItems.add(new ChestItem(itemId, item.name, count, item));
            }
        }

        buildUI();
        guiNode.attachChild(chestNode);
    }

    private void buildUI() {
        chestNode.detachAllChildren();

        float padding = 40f * uiScale;
        float startX = padding;
        float startY = camera.getHeight() - padding;

        // Title
        BitmapText title = new BitmapText(font, false);
        title.setSize(24f * uiScale);
        title.setColor(ColorRGBA.Yellow);
        title.setText("Chest Contents");
        title.setLocalTranslation(startX, startY, 0);
        chestNode.attachChild(title);

        startY -= 50f * uiScale;

        // Item list
        for (int i = 0; i < displayedItems.size() && i < 10; i++) {
            ChestItem item = displayedItems.get(i);
            float itemY = startY - (i * 35f * uiScale);

            // Background highlight for selected item
            if (i == selectedIndex) {
                Geometry highlight = new Geometry("Highlight", new Quad(400f * uiScale, 30f * uiScale));
                Material highlightMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                highlightMat.setColor("Color", new ColorRGBA(0.5f, 0.5f, 0.5f, 0.3f));
                highlight.setMaterial(highlightMat);
                highlight.setLocalTranslation(startX - 10f * uiScale, itemY - 25f * uiScale, -1);
                chestNode.attachChild(highlight);
            }

            BitmapText itemText = new BitmapText(font, false);
            itemText.setSize(14f * uiScale);
            itemText.setColor(i == selectedIndex ? ColorRGBA.White : new ColorRGBA(0.8f, 0.8f, 0.8f, 1f));
            itemText.setText(item.name + "  x" + item.count);
            itemText.setLocalTranslation(startX, itemY, 0);
            chestNode.attachChild(itemText);
        }

        // Instructions
        startY -= (displayedItems.size() + 1) * 35f * uiScale + 20f * uiScale;
        BitmapText instructions = new BitmapText(font, false);
        instructions.setSize(12f * uiScale);
        instructions.setColor(new ColorRGBA(0.7f, 0.7f, 0.7f, 1f));
        instructions.setText("W/S or UP/DOWN to navigate  |  ENTER to loot  |  ESC or E to close");
        instructions.setLocalTranslation(startX, startY, 0);
        chestNode.attachChild(instructions);
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

        // Add to inventory
        inventory.addItem(item.itemId, item.count);
        System.out.println("Looted " + item.count + "x " + item.name);

        // Remove from chest display
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

    /**
     * Internal class to track chest item display info.
     */
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
