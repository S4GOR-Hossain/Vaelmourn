package com.vaelmourn;

import com.jme3.app.SimpleApplication;
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
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop GUI for buying items from NPCs using soul dust currency.
 * Displays a list of items with prices and allows the player to purchase them.
 */
public class ShopUI implements ActionListener, NPC.ShopUI {

    private Node shopNode;
    private BitmapFont font;
    private AssetManager assetManager;
    private RenderManager renderManager;
    private InputManager inputManager;
    private Camera camera;
    private Inventory inventory;
    private PlayerStats playerStats;
    private NPC currentNPC;
    private boolean shopOpen = false;
    private Node guiNode;

    private List<ShopItem> displayedItems = new ArrayList<>();
    private int selectedIndex = 0;

    private float uiScale = 1f;

    public ShopUI(AssetManager assetManager, RenderManager renderManager,
                  InputManager inputManager, Camera camera, Node guiNode,
                  Inventory inventory, PlayerStats playerStats,
                  int screenWidth, int screenHeight) {
        this.assetManager = assetManager;
        this.renderManager = renderManager;
        this.inputManager = inputManager;
        this.camera = camera;
        this.guiNode = guiNode;
        this.inventory = inventory;
        this.playerStats = playerStats;

        uiScale = screenHeight / 1080f;

        font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        shopNode = new Node("ShopUI");

        // Register input handlers
        inputManager.addMapping("ShopUp", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("ShopDown", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("ShopBuy", new KeyTrigger(KeyInput.KEY_RETURN), new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("ShopClose", new KeyTrigger(KeyInput.KEY_ESCAPE), new KeyTrigger(KeyInput.KEY_E));

        inputManager.addListener(this, "ShopUp", "ShopDown", "ShopBuy", "ShopClose");
    }

    @Override
    public void show(NPC npc) {
        if (shopOpen) return;

        currentNPC = npc;
        shopOpen = true;
        displayedItems.clear();
        selectedIndex = 0;

        // Build the display list from the NPC's shop inventory
        for (int i = 0; i < npc.getShopItems().size(); i++) {
            String itemId = npc.getShopItems().get(i);
            int price = npc.getShopPrices().get(i);
            Item item = ItemRegistry.get(itemId);
            if (item != null) {
                displayedItems.add(new ShopItem(itemId, item.name, price, item));
            }
        }

        buildUI();
        guiNode.attachChild(shopNode);
    }

    private void buildUI() {
        shopNode.detachAllChildren();

        float padding = 40f * uiScale;
        float startX = padding;
        float startY = camera.getHeight() - padding;

        // Title
        BitmapText title = new BitmapText(font, false);
        title.setSize(24f * uiScale);
        title.setColor(ColorRGBA.Yellow);
        title.setText(currentNPC != null ? currentNPC.getName() + "'s Shop" : "Shop");
        title.setLocalTranslation(startX, startY, 0);
        shopNode.attachChild(title);

        startY -= 50f * uiScale;

        // Soul dust display
        BitmapText soulDustText = new BitmapText(font, false);
        soulDustText.setSize(16f * uiScale);
        soulDustText.setColor(new ColorRGBA(1f, 0.8f, 0f, 1f));
        soulDustText.setText("Soul Dust: " + playerStats.getSoulDust());
        soulDustText.setLocalTranslation(startX, startY, 0);
        shopNode.attachChild(soulDustText);

        startY -= 40f * uiScale;

        // Item list
        for (int i = 0; i < displayedItems.size() && i < 8; i++) {
            ShopItem item = displayedItems.get(i);
            float itemY = startY - (i * 35f * uiScale);

            // Background highlight for selected item
            if (i == selectedIndex) {
                Geometry highlight = new Geometry("Highlight", new Quad(400f * uiScale, 30f * uiScale));
                Material highlightMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                highlightMat.setColor("Color", new ColorRGBA(0.5f, 0.5f, 0.5f, 0.3f));
                highlight.setMaterial(highlightMat);
                highlight.setLocalTranslation(startX - 10f * uiScale, itemY - 25f * uiScale, -1);
                shopNode.attachChild(highlight);
            }

            BitmapText itemText = new BitmapText(font, false);
            itemText.setSize(14f * uiScale);
            itemText.setColor(i == selectedIndex ? ColorRGBA.White : new ColorRGBA(0.8f, 0.8f, 0.8f, 1f));
            itemText.setText(item.name + "  (" + item.price + " soul dust)");
            itemText.setLocalTranslation(startX, itemY, 0);
            shopNode.attachChild(itemText);
        }

        // Instructions
        startY -= (displayedItems.size() + 1) * 35f * uiScale + 20f * uiScale;
        BitmapText instructions = new BitmapText(font, false);
        instructions.setSize(12f * uiScale);
        instructions.setColor(new ColorRGBA(0.7f, 0.7f, 0.7f, 1f));
        instructions.setText("W/S or UP/DOWN to navigate  |  ENTER to buy  |  ESC or E to close");
        instructions.setLocalTranslation(startX, startY, 0);
        shopNode.attachChild(instructions);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!shopOpen) return;
        if (!isPressed) return;
        if (displayedItems.isEmpty()) return;

        switch (name) {
            case "ShopUp":
                selectedIndex = (selectedIndex - 1 + displayedItems.size()) % displayedItems.size();
                buildUI();
                break;
            case "ShopDown":
                selectedIndex = (selectedIndex + 1) % displayedItems.size();
                buildUI();
                break;
            case "ShopBuy":
                buySelectedItem();
                break;
            case "ShopClose":
                closeShop();
                break;
        }
    }

    private void buySelectedItem() {
        if (selectedIndex < 0 || selectedIndex >= displayedItems.size()) return;

        ShopItem item = displayedItems.get(selectedIndex);
        if (playerStats.getSoulDust() < item.price) {
            System.out.println("Not enough soul dust! Need " + item.price + ", have " + playerStats.getSoulDust());
            return;
        }

        // Deduct cost and add item to inventory
        playerStats.spendSoulDust(item.price);
        inventory.addItem(item.itemId, 1);
        System.out.println("Purchased " + item.name + " for " + item.price + " soul dust!");

        buildUI();
    }

    public void closeShop() {
        if (!shopOpen) return;

        shopOpen = false;
        shopNode.removeFromParent();
        currentNPC = null;
        displayedItems.clear();
    }

    public boolean isOpen() {
        return shopOpen;
    }

    public Node getNode() {
        return shopNode;
    }

    /**
     * Internal class to track shop item display info.
     */
    private static class ShopItem {
        String itemId;
        String name;
        int price;
        Item item;

        ShopItem(String itemId, String name, int price, Item item) {
            this.itemId = itemId;
            this.name = name;
            this.price = price;
            this.item = item;
        }
    }

    @Override
    public void purchaseItem(String itemId, int count) {
        // This is called by the NPC when a purchase is made
        // Already handled in buySelectedItem()
    }
}
