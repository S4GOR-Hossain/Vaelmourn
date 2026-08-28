package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

/**
 * Survival-game style player inventory / equipment screen.
 *
 * The whole inventory is one cohesive equipment screen:
 *   - a translucent full-screen overlay over the world
 *   - a compact top bar with player level/name, HP / MP / XP bars and Soul Dust
 *   - a 5x5 item grid (left / center-left)
 *   - a paper-doll character preview with 5 equipment slots around it
 *   - a 5-slot quick-access toolbar across the bottom
 *
 * This implementation reuses the project's data model (Inventory, PlayerStats,
 * ItemRegistry) and supports real interactions: left-click to select, drag
 * (a ghost follows the cursor) and drop into compatible slots, hover
 * highlighting, a built-in item tooltip, and strict equipment/toolbar type
 * compatibility.
 *
 * No values are hardcoded in here for currency / stats / level / name — they
 * all come from the supplied PlayerStats and Inventory.
 */
public class InventoryUI {

    private final AssetManager assetManager;
    private final RenderManager renderManager;
    private final InputManager inputManager;
    private final Inventory inventory;
    private final PlayerStats stats;

    private final Node hudNode = new Node("InventoryHUD");
    private BitmapFont font;

    private final int S = 54;   // slot size
    private final int G = 7;    // gap between slots

    // ---- top info strip ----
    private BitmapText levelText;
    private BitmapText levelLabel;
    private BitmapText nameText;
    private Geometry hpBar, mpBar, xpBar;
    private BitmapText hpText, mpText, xpText;
    private BitmapText soulText;
    private float barW;

    // ---- slot views ----
    private enum SlotKind { GRID, TOOLBAR, EQUIPMENT }

    private static class SlotView {
        final Node root;
        final Geometry border;
        final Geometry icon;
        final BitmapText label;
        final BitmapText count;
        final Slot data;
        final SlotKind kind;
        final int index;
        float x, y;

        SlotView(Node root, Geometry border, Geometry icon,
                 BitmapText label, BitmapText count,
                 Slot data, SlotKind kind, int index, float x, float y) {
            this.root = root;
            this.border = border;
            this.icon = icon;
            this.label = label;
            this.count = count;
            this.data = data;
            this.kind = kind;
            this.index = index;
            this.x = x;
            this.y = y;
        }
    }

    private final SlotView[] gridViews = new SlotView[Inventory.GRID_SIZE];
    private final SlotView[] equipViews = new SlotView[Inventory.EQUIPMENT_SLOTS.length];
    private final SlotView[] toolbarViews = new SlotView[Inventory.TOOLBAR_SIZE];

    // ---- interaction state ----
    private SlotView selected;
    private SlotView hovered;
    private int denyTimer = 0;
    private SlotView denySlot;

    // ---- tooltip ----
    private final Node tooltipNode = new Node("Tooltip");
    private Geometry tooltipBg;
    private BitmapText tooltipTitle;
    private BitmapText tooltipBody;

    // ---- drag ghost ----
    private final Node ghostNode = new Node("DragGhost");
    private Geometry ghostIcon;
    private BitmapText ghostCount;

    // ---- character preview (render-to-texture) ----
    private boolean previewReady = false;
    private Texture2D previewTex;
    private Geometry previewQuad;
    private Node previewRoot;
    private Spatial previewModel;
    private ViewPort previewView;

    private boolean visible = false;

    public InventoryUI(AssetManager assetManager, RenderManager renderManager,
                       InputManager inputManager, Camera cam,
                       Inventory inventory, PlayerStats stats,
                       Spatial playerModel, int screenW, int screenH) {
        this.assetManager = assetManager;
        this.renderManager = renderManager;
        this.inputManager = inputManager;
        this.inventory = inventory;
        this.stats = stats;

        font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        buildOverlay(screenW, screenH);
        buildTopInfo(screenW, screenH);
        buildSlots(screenW, screenH);
        buildTooltip();
        buildGhost();
        buildPreview(cam, playerModel, screenW, screenH);

        hudNode.setCullHint(Spatial.CullHint.Always);
    }

    // ================= public API =================

    public void setVisible(boolean visible) {
        this.visible = visible;
        hudNode.setCullHint(visible ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        if (!visible) {
            clearSelection();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public Node getNode() {
        return hudNode;
    }

    /** Called when the primary mouse button is pressed while the inventory is open. */
    public void handlePrimaryClick() {
        if (!visible) return;
        Vector2f cur = inputManager.getCursorPosition();
        SlotView hit = slotAt(cur.x, cur.y);

        if (hit == null) {
            clearSelection();
            return;
        }
        if (selected == null) {
            if (!hit.data.isEmpty()) {
                selected = hit;
            }
            return;
        }
        if (selected == hit) {
            clearSelection();
            return;
        }
        tryMove(selected, hit);
    }

    public void update(float tpf, Camera cam) {
        if (!visible) return;

        updatePreview(cam);
        updatePointer();
        refreshBars();
        refreshSoulDust();
        refreshSlots();
    }

    // ================= background overlay =================

    private void buildOverlay(int W, int H) {
        // darkened translucent overlay over the world — not an opaque panel
        Geometry overlay = quad(0, 0, W, H, new ColorRGBA(0.02f, 0.02f, 0.03f, 0.78f));
        hudNode.attachChild(overlay);

        // subtle vignette band at top and bottom to frame the layout
        quadAttach(0, H - 6, W, 2, new ColorRGBA(0.45f, 0.5f, 0.5f, 0.35f));
        quadAttach(0, 4, W, 2, new ColorRGBA(0.45f, 0.5f, 0.5f, 0.35f));
    }

    private void buildTopInfo(int W, int H) {
        float infoTop = H - 14f;
        float infoBottom = H - 104f;

        // semi-transparent status band (not opaque)
        quadAttach(24, infoBottom - 4, W - 48, infoTop - infoBottom + 8,
                new ColorRGBA(0.08f, 0.09f, 0.10f, 0.55f));

        // --- level + name (left) ---
        levelText = addText(hudNode, "01", 58f, infoTop - 62f, 46f, new ColorRGBA(0.92f, 0.95f, 0.98f, 1f));
        levelLabel = addText(hudNode, "LEVEL", 60f, infoTop - 96f, 13f, new ColorRGBA(0.55f, 0.62f, 0.66f, 1f));
        nameText = addText(hudNode, "PLAYER", 172f, infoTop - 40f, 25f, new ColorRGBA(0.93f, 0.95f, 0.97f, 1f));

        // --- XP bar (under name) ---
        addText(hudNode, "XP", 176f, infoTop - 88f, 14f, new ColorRGBA(0.62f, 0.68f, 0.72f, 1f));
        barW = 360f;
        quadAttach(210f, infoTop - 92f, barW, 14f, new ColorRGBA(0.05f, 0.06f, 0.08f, 0.85f));
        xpBar = quad(210f, infoTop - 92f, 1f, 14f, new ColorRGBA(0.45f, 0.62f, 0.30f, 1f));
        hudNode.attachChild(xpBar);
        xpText = addText(hudNode, "0 / 100", 212f, infoTop - 90f, 11f, new ColorRGBA(0.95f, 0.97f, 0.9f, 1f));

        // --- HP / MP bars (middle) ---
        float hx = 620f;
        float bx = hx + 40f;
        float bw = 300f;

        addText(hudNode, "HP", hx, infoTop - 52f, 15f, new ColorRGBA(0.95f, 0.55f, 0.5f, 1f));
        quadAttach(bx, infoTop - 56f, bw, 16f, new ColorRGBA(0.05f, 0.06f, 0.08f, 0.85f));
        hpBar = quad(bx, infoTop - 56f, 1f, 16f, new ColorRGBA(0.78f, 0.26f, 0.24f, 1f));
        hudNode.attachChild(hpBar);
        hpText = addText(hudNode, "100 / 100", bx + 4f, infoTop - 53f, 11f, ColorRGBA.White);
        addText(hudNode, "MP", hx, infoTop - 90f, 15f, new ColorRGBA(0.5f, 0.62f, 0.95f, 1f));
        quadAttach(bx, infoTop - 94f, bw, 16f, new ColorRGBA(0.05f, 0.06f, 0.08f, 0.85f));
        mpBar = quad(bx, infoTop - 94f, 1f, 16f, new ColorRGBA(0.28f, 0.46f, 0.92f, 1f));
        hudNode.attachChild(mpBar);
        mpText = addText(hudNode, "100 / 100", bx + 4f, infoTop - 91f, 11f, ColorRGBA.White);

        // --- Soul Dust (top-right) ---
        float sx = W - 320f;
        quadAttach(sx, infoTop - 36f, 20f, 20f, new ColorRGBA(0.55f, 0.8f, 0.95f, 1f));
        addText(hudNode, "SOUL DUST", sx + 28f, infoTop - 34f, 13f, new ColorRGBA(0.6f, 0.68f, 0.72f, 1f));
        soulText = addText(hudNode, "0", sx + 28f, infoTop - 62f, 30f, new ColorRGBA(0.85f, 0.95f, 1f, 1f));
    }

    // ================= slots =================

    private void buildSlots(int W, int H) {
        int gridW = 5 * S + 4 * G;
        int gridH = gridW;
        int toolbarW = 5 * S + 4 * G;

        // toolbar near the bottom, centered
        float toolbarY = 30f;
        float toolbarX = W / 2f - toolbarW / 2f;

        // vertical band available for the main composition
        float bandTop = H - 104f - 16f;
        float bandBottom = toolbarY + S + 24f;
        float bandMid = (bandTop + bandBottom) / 2f;

        // grid panel (left / center-left)
        float gridPad = 22f;
        float gridPanelW = gridW + gridPad * 2f;
        float gridPanelH = gridH + 58f;
        float gridPanelX = 60f;
        float gridPanelY = bandMid - gridPanelH / 2f;
        panel(gridPanelX, gridPanelY, gridPanelW, gridPanelH, "INVENTORY");
        float gridX = gridPanelX + (gridPanelW - gridW) / 2f;
        float gridY = gridPanelY + (gridPanelH - 28f - gridH) / 2f;
        buildGrid(gridX, gridY);

        // character / equipment panel (center)
        float panelW = 560f;
        float panelH = 560f;
        float panelX = gridPanelX + gridPanelW + 30f;
        float panelY = bandMid - panelH / 2f;
        panel(panelX, panelY, panelW, panelH, "EQUIPMENT");

        // paper-doll character render
        float renderW = 250f;
        float renderH = 400f;
        float renderX = panelX + (panelW - renderW) / 2f;
        float helmetY = panelY + panelH - 28f - S;   // slot bottom y
        float bootsY = panelY + 16f;
        float renderY = bootsY + S + 14f;
        previewLTRB(renderX, renderY, renderW, renderH);

        // 5 equipment slots arranged around the paper-doll
        // order: 0 HELMET, 1 CHESTPLATE, 2 LEGGINGS, 3 SHIELD, 4 BOOTS
        float midY = panelY + panelH / 2f - S / 2f;
        buildEquipSlot(3, panelX + 16f, midY, "SHIELD");   // shield (left)
        buildEquipSlot(1, panelX + panelW - 16f - S, midY, "CHESTPLATE"); // right
        buildEquipSlot(0, panelX + (panelW - S) / 2f, helmetY, "HELMET"); // top
        buildEquipSlot(2, panelX + 16f, panelY + 90f, "LEGGINGS");        // lower-left
        buildEquipSlot(4, panelX + (panelW - S) / 2f, bootsY, "BOOTS");   // bottom

        // toolbar panel (bottom)
        float toolPad = 20f;
        float toolPanelW = toolbarW + toolPad * 2f;
        quadAttach(toolbarX - toolPad, toolbarY - 14f, toolPanelW, S + 24f,
                new ColorRGBA(0.07f, 0.08f, 0.09f, 0.6f));
        addText(hudNode, "QUICK ACCESS", toolbarX - toolPad, toolbarY + S + 8f, 13f,
                new ColorRGBA(0.55f, 0.62f, 0.66f, 1f));
        buildToolbar(toolbarX, toolbarY);
    }

    private void buildGrid(float left, float bottom) {
        for (int r = 0; r < Inventory.GRID_ROWS; r++) {
            for (int c = 0; c < Inventory.GRID_COLS; c++) {
                int idx = r * Inventory.GRID_COLS + c;
                float x = left + c * (S + G);
                float y = bottom + r * (S + G);
                gridViews[idx] = createSlot(x, y, inventory.getGridSlot(idx),
                        SlotKind.GRID, idx, false);
            }
        }
    }

    private void buildToolbar(float left, float bottom) {
        for (int i = 0; i < Inventory.TOOLBAR_SIZE; i++) {
            float x = left + i * (S + G);
            SlotView v = createSlot(x, bottom, inventory.getToolbarSlot(i),
                    SlotKind.TOOLBAR, i, false);
            toolbarViews[i] = v;
            // show the hotbar number in the top-left corner
            BitmapText num = addText(hudNode, "" + (i + 1), x + 3f, bottom + S - 14f, 12f,
                    new ColorRGBA(0.5f, 0.56f, 0.6f, 1f));
            v.root.getParent().attachChild(num);
        }
    }

    private void buildEquipSlot(int equipIndex, float x, float y, String label) {
        SlotView v = createSlot(x, y, inventory.getEquipSlot(Inventory.EquipSlot.values()[equipIndex]),
                SlotKind.EQUIPMENT, equipIndex, true);
        equipViews[equipIndex] = v;
        addText(hudNode, label, x + 6f, y - 12f, 11f, new ColorRGBA(0.45f, 0.5f, 0.55f, 1f));
    }

    /** Creates a single slot: border + dark background + icon + labels. */
    private SlotView createSlot(float x, float y, Slot data, SlotKind kind, int index, boolean equipLabel) {
        Node root = new Node("slot");
        hudNode.attachChild(root);

        // drop shadow
        Geometry shadow = quad(x + 2, y - 2, S, S, new ColorRGBA(0f, 0f, 0f, 0.5f));
        root.attachChild(shadow);

        // border (recolored for hover/selection)
        Geometry border = quad(x - 1, y - 1, S + 2, S + 2, new ColorRGBA(0.22f, 0.24f, 0.26f, 1f));
        root.attachChild(border);

        // dark translucent background
        Geometry bg = quad(x, y, S, S, new ColorRGBA(0.10f, 0.11f, 0.13f, 0.92f));
        root.attachChild(bg);

        // item icon tile (hidden when empty)
        Geometry icon = quad(x + 3, y + 3, S - 6, S - 6, new ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
        icon.setCullHint(Spatial.CullHint.Always);
        root.attachChild(icon);

        // item initial + count
        BitmapText label = new BitmapText(font);
        label.setSize(20f);
        label.setColor(new ColorRGBA(0.9f, 0.92f, 0.95f, 1f));
        label.setLocalTranslation(x + S / 2f - 8f, y + S / 2f - 14f, 0f);
        root.attachChild(label);

        BitmapText count = new BitmapText(font);
        count.setSize(14f);
        count.setColor(new ColorRGBA(0.95f, 0.85f, 0.55f, 1f));
        count.setLocalTranslation(x + S - 24f, y + 2f, 0f);
        root.attachChild(count);

        return new SlotView(root, border, icon, label, count, data, kind, index, x, y);
    }

    // ================= character preview =================

    private void previewLTRB(float left, float bottom, float w, float h) {
        // just record the quad area on the GUI (the actual RTT quad is built later)
        previewRectLeft = left;
        previewRectBottom = bottom;
        previewRectW = w;
        previewRectH = h;
    }

    private float previewRectLeft, previewRectBottom, previewRectW, previewRectH;

    private void buildPreview(Camera cam, Spatial playerModel, int W, int H) {
        int pw = (int) Math.max(32, previewRectW);
        int ph = (int) Math.max(32, previewRectH);

        previewTex = new Texture2D(pw, ph, Image.Format.RGBA8);
        previewTex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        previewTex.setMagFilter(Texture.MagFilter.Bilinear);

        FrameBuffer fb = new FrameBuffer(pw, ph, 1);
        fb.setDepthBuffer(Image.Format.Depth);
        fb.setColorTexture(previewTex);

        previewRoot = new Node("InventoryPreviewScene");
        if (playerModel != null) {
            previewModel = playerModel.clone();
            previewModel.rotate(0f, (float) StrictMath.PI, 0f);
            previewRoot.attachChild(previewModel);
        }

        com.jme3.light.DirectionalLight sun = new com.jme3.light.DirectionalLight();
        sun.setDirection(new Vector3f(-0.4f, -1f, -0.4f).normalizeLocal());
        sun.setColor(ColorRGBA.White);
        previewRoot.addLight(sun);
        com.jme3.light.AmbientLight ambient = new com.jme3.light.AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(1.1f));
        previewRoot.addLight(ambient);

        Camera offCam = cam.clone();
        offCam.setFrustumPerspective(45f, (float) pw / ph, 0.1f, 100f);
        offCam.setLocation(new Vector3f(0, 1.1f, 2.6f));
        offCam.lookAt(new Vector3f(0, 1f, 0), Vector3f.UNIT_Y);

        ViewPort off = renderManager.createMainView("inventoryPreview", offCam);
        off.setClearFlags(true, true, true);
        off.setBackgroundColor(new ColorRGBA(0.08f, 0.09f, 0.11f, 0f)); // transparent-ish
        off.attachScene(previewRoot);
        off.setOutputFrameBuffer(fb);
        renderManager.removeMainView(off);
        previewView = off;
        previewReady = true;

        // textured quad on the GUI at the recorded position
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", previewTex);
        Quad q = new Quad(previewRectW, previewRectH);
        previewQuad = new Geometry("previewQuad", q);
        previewQuad.setMaterial(mat);
        previewQuad.setQueueBucket(RenderQueue.Bucket.Gui);
        previewQuad.setLocalTranslation(previewRectLeft, previewRectBottom, 0f);
        hudNode.attachChild(previewQuad);
    }

    private void updatePreview(Camera cam) {
        if (!previewReady || previewView == null || previewRoot == null) return;
        previewRoot.updateGeometricState();
        renderManager.renderViewPort(previewView, 1f / 60f);
    }

    // ================= tooltip + drag ghost =================

    private void buildTooltip() {
        hudNode.attachChild(tooltipNode);
        tooltipBg = quad(0, 0, 1, 1, new ColorRGBA(0.07f, 0.08f, 0.10f, 0.96f));
        tooltipNode.attachChild(tooltipBg);
        tooltipTitle = new BitmapText(font);
        tooltipTitle.setSize(16f);
        tooltipTitle.setColor(new ColorRGBA(0.92f, 0.95f, 0.98f, 1f));
        tooltipNode.attachChild(tooltipTitle);
        tooltipBody = new BitmapText(font);
        tooltipBody.setSize(12f);
        tooltipBody.setColor(new ColorRGBA(0.6f, 0.68f, 0.72f, 1f));
        tooltipNode.attachChild(tooltipBody);
        tooltipNode.setCullHint(Spatial.CullHint.Always);
    }

    private void buildGhost() {
        hudNode.attachChild(ghostNode);
        ghostIcon = quad(0, 0, S - 4, S - 4, new ColorRGBA(0.7f, 0.7f, 0.7f, 0.9f));
        ghostNode.attachChild(ghostIcon);
        ghostCount = new BitmapText(font);
        ghostCount.setSize(15f);
        ghostCount.setColor(new ColorRGBA(0.98f, 0.88f, 0.6f, 1f));
        ghostNode.attachChild(ghostCount);
        ghostNode.setCullHint(Spatial.CullHint.Always);
    }

    private void updatePointer() {
        Vector2f cur = inputManager.getCursorPosition();
        hovered = slotAt(cur.x, cur.y);

        // --- drag ghost ---
        if (selected != null && !selected.data.isEmpty()) {
            Item item = selected.data.getItem();
            ghostNode.setCullHint(Spatial.CullHint.Never);
            ghostNode.setLocalTranslation(cur.x - (S - 4) / 2f, cur.y - (S - 4) / 2f + 12f, 0f);
            ghostIcon.getMaterial().setColor("Color",
                    new ColorRGBA(item.iconColor.r, item.iconColor.g, item.iconColor.b, 0.85f));
            ghostCount.setText(selected.data.count > 1 ? "" + selected.data.count : "");
            ghostCount.setLocalTranslation((S - 4) - 22f, (S - 4) - 20f, 0f);
            ghostNode.getParent().attachChild(ghostNode); // keep on top
        } else {
            ghostNode.setCullHint(Spatial.CullHint.Always);
        }

        // --- tooltip ---
        if (hovered != null && !hovered.data.isEmpty()) {
            Item item = hovered.data.getItem();
            tooltipTitle.setText(item.name);
            tooltipBody.setText(categoryLabel(item.category)
                    + (item.maxStack > 1 ? "  |  x" + hovered.data.count : ""));
            float tw = Math.max(tooltipTitle.getLineWidth(), tooltipBody.getLineWidth()) + 18f;
            float th = 44f;
            float tx = cur.x + 14f;
            float ty = cur.y + 6f;
            tooltipNode.setLocalTranslation(tx, ty, 0f);
            tooltipBg.setLocalScale(tw, th, 1f);
            tooltipTitle.setLocalTranslation(5f, th - 18f, 0f);
            tooltipBody.setLocalTranslation(5f, th - 36f, 0f);
            tooltipNode.setCullHint(Spatial.CullHint.Never);
        } else {
            tooltipNode.setCullHint(Spatial.CullHint.Always);
        }
    }

    private String categoryLabel(Item.Category c) {
        String s = c.toString();
        return s.substring(0, 1) + s.substring(1).toLowerCase();
    }

    // ================= interaction helpers =================

    private SlotView slotAt(float px, float py) {
        SlotView r = find(equipViews, px, py);
        if (r != null) return r;
        r = find(gridViews, px, py);
        if (r != null) return r;
        return find(toolbarViews, px, py);
    }

    private SlotView find(SlotView[] arr, float px, float py) {
        for (SlotView v : arr) {
            if (v == null) continue;
            if (px >= v.x && px <= v.x + S && py >= v.y && py <= v.y + S) return v;
        }
        return null;
    }

    private boolean canPlaceIn(SlotView target, Item item) {
        return switch (target.kind) {
            case GRID -> true;
            case TOOLBAR ->
                    item.category == Item.Category.WEAPON
                            || item.category == Item.Category.POTION
                            || item.category == Item.Category.KEY;
            case EQUIPMENT -> item.category == equipCategoryFor(target.index);
        };
    }

    private Item.Category equipCategoryFor(int equipIndex) {
        return switch (equipIndex) {
            case 0 -> Item.Category.HELMET;
            case 1 -> Item.Category.CHESTPLATE;
            case 2 -> Item.Category.LEGGINGS;
            case 3 -> Item.Category.SHIELD;
            case 4 -> Item.Category.BOOTS;
            default -> null;
        };
    }

    private void tryMove(SlotView from, SlotView to) {
        if (from.data.isEmpty() || from == to) {
            clearSelection();
            return;
        }
        Item item = from.data.getItem();
        if (item == null) {
            clearSelection();
            return;
        }
        if (!canPlaceIn(to, item)) {
            deny(to); // keep selection so the player can try elsewhere
            return;
        }
        inventory.swapMove(from.data, to.data);
        clearSelection();
    }

    private void deny(SlotView slot) {
        denySlot = slot;
        denyTimer = 18;
    }

    private void clearSelection() {
        selected = null;
    }

    // ================= data refresh =================

    private void refreshBars() {
        setBar(hpBar, stats.getHealthFraction(), barColor(0.78f, 0.26f, 0.24f, stats.getHealthFraction()));
        setBar(mpBar, stats.getManaFraction(), barColor(0.28f, 0.46f, 0.92f, stats.getManaFraction()));
        setBar(xpBar, stats.getExperienceFraction(), barColor(0.45f, 0.62f, 0.30f, stats.getExperienceFraction()));

        hpText.setText((int) stats.getHealth() + " / " + (int) stats.getMaxHealth());
        mpText.setText((int) stats.getMana() + " / " + (int) stats.getMaxMana());
        xpText.setText((int) stats.getExperience() + " / " + (int) stats.getExperienceToNext());

        levelText.setText(String.format("%02d", stats.getLevel()));
        nameText.setText(stats.getPlayerName().toUpperCase());
    }

    private void setBar(Geometry bar, float frac, ColorRGBA color) {
        if (bar == null) return;
        float w = Math.max(0f, barW * Math.max(0f, Math.min(1f, frac)));
        bar.setLocalScale(w, 1f, 1f);
        bar.getMaterial().setColor("Color", color);
    }

    private ColorRGBA barColor(float r, float g, float b, float frac) {
        // dim the fill slightly when low
        float low = frac < 0.25f ? 0.6f : 1f;
        return new ColorRGBA(r * low, g * low, b * low, 1f);
    }

    private void refreshSoulDust() {
        soulText.setText("" + stats.getSoulDust());
    }

    private void refreshSlots() {
        if (denyTimer > 0) denyTimer--;
        if (denyTimer == 0) denySlot = null;

        refreshArray(gridViews, inventory.getGrid(), false);
        refreshArray(toolbarViews, inventory.getToolbar(), true);
        refreshArray(equipViews, inventory.getEquipment(), true);
    }

    private void refreshArray(SlotView[] views, Slot[] data, boolean showEmptyHints) {
        for (int i = 0; i < views.length; i++) {
            SlotView v = views[i];
            if (v == null) continue;
            Slot s = data[i];
            applySlotVisual(v, s);
        }
    }

    private void applySlotVisual(SlotView v, Slot s) {
        boolean isSel = v == selected;
        boolean isHov = v == hovered && selected == null;
        boolean isDeny = v == denySlot && denyTimer > 0;

        if (s.isEmpty()) {
            v.icon.setCullHint(Spatial.CullHint.Always);
            v.label.setText("");
            v.count.setText("");
        } else {
            Item item = s.getItem();
            v.icon.setCullHint(Spatial.CullHint.Never);
            v.icon.getMaterial().setColor("Color", new ColorRGBA(item.iconColor.r, item.iconColor.g, item.iconColor.b, 1f));
            v.label.setText(item.name.substring(0, 1));
            v.count.setText(s.count > 1 ? "" + s.count : "");
        }

        // border highlight state
        if (isDeny) {
            v.border.getMaterial().setColor("Color", new ColorRGBA(0.85f, 0.25f, 0.25f, 1f));
        } else if (isSel) {
            v.border.getMaterial().setColor("Color", new ColorRGBA(0.30f, 0.62f, 0.80f, 1f));
        } else if (isHov) {
            v.border.getMaterial().setColor("Color", new ColorRGBA(0.45f, 0.48f, 0.52f, 1f));
        } else {
            v.border.getMaterial().setColor("Color", new ColorRGBA(0.22f, 0.24f, 0.26f, 1f));
        }
    }

    // ================= generic builders =================

    private void panel(float x, float y, float w, float h, String title) {
        // soft shadow
        quadAttach(x + 3, y - 3, w, h, new ColorRGBA(0f, 0f, 0f, 0.4f));
        // main translucent surface
        quadAttach(x, y, w, h, new ColorRGBA(0.07f, 0.08f, 0.10f, 0.82f));
        // thin border
        float t = 1.5f;
        quadAttach(x, y, w, t, new ColorRGBA(0.28f, 0.32f, 0.34f, 1f));
        quadAttach(x, y + h - t, w, t, new ColorRGBA(0.28f, 0.32f, 0.34f, 1f));
        quadAttach(x, y, t, h, new ColorRGBA(0.28f, 0.32f, 0.34f, 1f));
        quadAttach(x + w - t, y, t, h, new ColorRGBA(0.28f, 0.32f, 0.34f, 1f));
        // muted accent line under the title
        quadAttach(x + 12f, y + h - 26f, w - 24f, 1.5f, new ColorRGBA(0.32f, 0.50f, 0.60f, 0.6f));
        addText(hudNode, title, x + 14f, y + h - 24f, 15f, new ColorRGBA(0.6f, 0.68f, 0.72f, 1f));
    }

    private Geometry quad(float x, float y, float w, float h, ColorRGBA color) {
        Quad q = new Quad(Math.max(1f, w), Math.max(1f, h));
        Geometry g = new Geometry("quad", q);
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, 0f);
        return g;
    }

    private void quadAttach(float x, float y, float w, float h, ColorRGBA color) {
        hudNode.attachChild(quad(x, y, w, h, color));
    }

    private BitmapText addText(Node parent, String text, float x, float y,
                               float size, ColorRGBA color) {
        BitmapText bt = new BitmapText(font);
        bt.setText(text);
        bt.setSize(size);
        bt.setColor(color);
        bt.setLocalTranslation(x, y, 0f);
        parent.attachChild(bt);
        return bt;
    }
}
