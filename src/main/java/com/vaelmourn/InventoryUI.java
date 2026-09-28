package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.SkinningControl;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

import java.util.HashMap;
import java.util.Map;

/**
 * Player inventory / equipment screen. Toggle with E.
 *
 * Layout (proportions derive from a 1920x1080 reference, scaled otherwise):
 *   - a centered rounded panel (~90% width x ~85% height)
 *   - left column: "Inventory" label + 5x5 grid, then "Toolbar" label + 5
 *     restricted slots (weapons / potions / keys) with a distinct accent border
 *   - a cohesive right-side block containing (tightly grouped):
 *       * "[Level] | [PlayerName]" with an HP bar (green) and XP bar (blue)
 *         beneath it, Soul Dust grouped beside the name
 *       * a bordered, no-fill character preview window (~25% panel W x ~60%
 *         panel H) rendering the player model via render-to-texture
 *       * a vertical column of 5 equipment slots (helmet, chestplate, leggings,
 *         boots, shield) with distinct faint silhouette icons, plus a trash slot
 *       * a gold-accented stats bar below the preview (damage / armor / movement
 *         speed / attack speed), data-driven from PlayerStats
 *
 * Interactions: left-click to select, drag (ghost follows cursor) and drop with
 * strict equipment/toolbar compatibility, hover highlighting, item tooltip and a
 * two-step trash confirmation.
 */
public class InventoryUI {

    private final AssetManager assetManager;
    private final RenderManager renderManager;
    private final InputManager inputManager;
    private final Inventory inventory;
    private final PlayerStats stats;

    private final Node hudNode = new Node("InventoryHUD");
    private BitmapFont font;

    private final Map<String, Texture> iconCache = new HashMap<>();

    private final float sx;
    private final float sy;

    private float pw, ph, px, py;
    private float panelTop, panelRight;
    private float slot, gap, eqSlot, eqGap;
    private float gridLeft, gridBottomY, toolbarY, trashX, trashY;

    private float charPanelLeft, charPanelRight, charPanelW, charPanelTop, charPanelBottom;
    private float previewLeft, previewRight, previewBottom, previewTop, previewW, previewH;
    private float eqLeftX, eqRightX;
    private float helmY, chestY, legsY, shieldY, bootsY;

    private static final ColorRGBA DEFAULT_EQUIP_SIL_COLOR = new ColorRGBA(0.55f, 0.58f, 0.62f, 0.30f);

    private static final Inventory.EquipSlot[] EQUIP_VISUAL = {
            Inventory.EquipSlot.HELMET,
            Inventory.EquipSlot.CHESTPLATE,
            Inventory.EquipSlot.LEGGINGS,
            Inventory.EquipSlot.BOOTS,
            Inventory.EquipSlot.SHIELD
    };

    private BitmapText nameText, levelText;
    private Geometry hpTrack, hpFill, xpTrack, xpFill;
    private float barThick;

    private BitmapText soulText;
    private float soulIconX, soulIconY;

    private static final String[] STAT_LABELS = { "DMG", "ARM", "MSPD", "ATK SPD" };
    private static final int[] STAT_DECIMALS = { 0, 0, 1, 2 };
    private final BitmapText[] statValues = new BitmapText[4];
    private final float[] statCenterX = new float[4];

    private enum SlotKind { GRID, TOOLBAR, EQUIPMENT, TRASH }

    private static class SlotView {
        final Node root;
        final Geometry border;
        final Geometry icon;
        final Geometry iconTex;
        final BitmapText label;
        final BitmapText count;
        final Node silhouette;
        final Slot data;
        final SlotKind kind;
        final Inventory.EquipSlot equipSlot;
        Geometry equipMark;
        float x, y;

        SlotView(Node root, Geometry border, Geometry icon, Geometry iconTex,
                 BitmapText label, BitmapText count, Node silhouette,
                 Slot data, SlotKind kind, Inventory.EquipSlot equipSlot,
                 float x, float y) {
            this.root = root;
            this.border = border;
            this.icon = icon;
            this.iconTex = iconTex;
            this.label = label;
            this.count = count;
            this.silhouette = silhouette;
            this.data = data;
            this.kind = kind;
            this.equipSlot = equipSlot;
            this.x = x;
            this.y = y;
        }
    }

    private final SlotView[] gridViews = new SlotView[Inventory.GRID_SIZE];
    private final SlotView[] equipViews = new SlotView[EQUIP_VISUAL.length];
    private final SlotView[] toolbarViews = new SlotView[Inventory.TOOLBAR_SIZE];
    private SlotView trashView;

    private SlotView selected;
    private SlotView hovered;
    private int denyTimer = 0;
    private SlotView denySlot;
    private int trashArmTimer = 0;
    private static final int TRASH_ARM = 90;

    private final Node tooltipNode = new Node("Tooltip");
    private Geometry tooltipBg;
    private BitmapText tooltipTitle;
    private BitmapText tooltipBody;
    private final Node ghostNode = new Node("DragGhost");
    private Geometry ghostIcon;
    private Geometry ghostIconTex;
    private BitmapText ghostCount;

    private boolean previewReady = false;
    private Texture2D previewTex;
    private Geometry previewQuad;
    private Node previewRoot;
    private Spatial previewModel;
    private AnimComposer previewComposer;
    private Geometry previewPlinth;
    private ViewPort previewView;
    private Camera previewCamera = null;
    private Spatial playerModelRef;

    private static final int MAX_MENU_OPTIONS = 3;
    private final Node menuNode = new Node("ContextMenu");
    private Geometry menuBg;
    private final Geometry[] menuRows = new Geometry[MAX_MENU_OPTIONS];
    private final BitmapText[] menuLabels = new BitmapText[MAX_MENU_OPTIONS];
    private boolean menuOpen = false;
    private Slot menuSlotData;
    private float menuSlotX, menuSlotY;
    private int menuOptionCount = 0;
    private float menuRowH, menuRowW;
    private float menuX, menuY;
    private java.util.function.Consumer<String> weaponEquipHandler;
    private final float screenW, screenH;

    private boolean visible = false;
    private boolean xpFractionLogged = false;

    public InventoryUI(AssetManager assetManager, RenderManager renderManager,
                       InputManager inputManager, Camera cam,
                       Inventory inventory, PlayerStats stats,
                       Spatial playerModel, int screenW, int screenH) {
        this.assetManager = assetManager;
        this.renderManager = renderManager;
        this.inputManager = inputManager;
        this.inventory = inventory;
        this.stats = stats;
        this.playerModelRef = playerModel;

        this.sx = screenW / 1920f;
        this.sy = screenH / 1080f;
        this.screenW = screenW;
        this.screenH = screenH;

        font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        layout();
        buildOverlay(screenW, screenH);
        buildPanel();
        buildGridAndToolbar();
        buildCenterBlock();
        buildPreview(cam, playerModel);
        buildPreviewFrame();
        buildTooltip();
        buildGhost();
        buildContextMenu();

        setVisible(false);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        hudNode.setCullHint(visible ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        if (visible) {
            rebuildPreviewModel();
        } else {
            clearSelection();
            closeMenu();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    /** Lets the world re-equip the combat weapon whenever "Use" hits a sword/bow. */
    public void setWeaponEquipHandler(java.util.function.Consumer<String> handler) {
        this.weaponEquipHandler = handler;
    }

    public Node getNode() {
        return hudNode;
    }

    public void handlePrimaryClick() {
        Vector2f cur = inputManager.getCursorPosition();
        // a right-click menu can be up even while the inventory overlay is hidden
        if (menuOpen) {
            if (handleMenuClick(cur.x, cur.y)) return;
            closeMenu();
            if (!visible) return; // outside the menu while hidden = just dismiss
        }
        if (!visible) return;

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

    public void handleSecondaryClick() {
        if (!visible) return;
        if (selected != null) return; // never open a menu mid-drag

        Vector2f cur = inputManager.getCursorPosition();
        SlotView hit = slotAt(cur.x, cur.y);

        // right-click on the already-open menu's slot just toggles it shut
        if (menuOpen && hit != null && hit.data == menuSlotData) {
            closeMenu();
            return;
        }
        closeMenu();
        if (hit == null || hit.data.isEmpty()) return;
        openMenu(hit.data, hit.x, hit.y);
    }

    /** Called when the inventory is closed: right-click on the world toolbar slot. */
    public void openMenuForSlot(Slot data, float x, float y) {
        if (selected != null) return;
        if (menuOpen && data == menuSlotData) {
            closeMenu();
            return;
        }
        closeMenu();
        openMenu(data, x, y);
    }

    public boolean isContextMenuOpen() {
        return menuOpen;
    }

    public void closeContextMenu() {
        closeMenu();
    }

    public void update(float tpf, Camera cam) {
        if (!visible && !menuOpen) return;
        if (visible) {
            updatePreview(tpf);
            updatePointer();
            refreshIdentityBars();
            refreshSoulDust();
            refreshStats();
            refreshSlots();
        } else if (menuOpen) {
            updateMenuHover(inputManager.getCursorPosition());
        }
    }

    private void layout() {
        pw = 1728f * sx;
        ph = 918f * sy;
        px = (1920f * sx - pw) / 2f;
        py = (1080f * sy - ph) / 2f;
        panelTop = py + ph;
        panelRight = px + pw;

        slot = 54f * sx;
        gap = 14f * sx;
        eqSlot = slot;
        eqGap = 10f * sx;

        gridLeft = px + 0.028f * pw;
        float gridLabelY = panelTop - 92f * sy;
        float gridTopY = gridLabelY - 30f * sy;
        gridBottomY = gridTopY - (5 * slot + 4 * gap);
        float toolbarLabelY = gridBottomY - 20f * sy;
        toolbarY = toolbarLabelY - 28f * sy;
        trashX = gridLeft + (5 * slot + 4 * gap) - slot;
        trashY = toolbarY - 44f * sy - slot;

        charPanelTop = panelTop - 66f * sy;
        charPanelBottom = py + 30f * sy;
        helmY = charPanelTop - 218f * sy - slot;
        previewTop = helmY - 10f * sy;
        previewBottom = py + 300f * sy;
        previewH = previewTop - previewBottom;
        previewW = 0.92f * previewH;

        charPanelW = 22f * sx + slot + 20f * sx + previewW + 20f * sx + slot + 22f * sx;
        float availLeft = gridLeft + (5 * slot + 4 * gap) + 42f * sx;
        float availRight = panelRight - 42f * sx;
        charPanelLeft = availLeft + (availRight - availLeft - charPanelW) / 2f;
        charPanelRight = charPanelLeft + charPanelW;

        previewLeft = charPanelLeft + 22f * sx + slot + 20f * sx;
        previewRight = previewLeft + previewW;
        eqLeftX = charPanelLeft + 22f * sx;
        eqRightX = charPanelRight - 22f * sx - slot;

        chestY = previewTop - 36f * sy - slot;
        shieldY = chestY;
        legsY = previewBottom + 22f * sy;
        bootsY = legsY;

        barThick = Math.max(1.5f, 4f * sy);
    }

    private void buildOverlay(int W, int H) {
        Geometry overlay = quad(0, 0, W, H, new ColorRGBA(0.02f, 0.02f, 0.03f, 0.75f));
        hudNode.attachChild(overlay);
    }

    private void buildPanel() {
        float r = Math.min(16f * sx, slot * 0.5f);
        float t = Math.max(1f, 2f * sx);
        quadAttach(px + 4, py - 4, pw, ph, new ColorRGBA(0f, 0f, 0f, 0.35f));
        roundedRect(px, py, pw, ph, r, new ColorRGBA(0.08f, 0.09f, 0.11f, 0.86f));
        borderStroke(px, py, pw, ph, t, new ColorRGBA(0.30f, 0.34f, 0.36f, 1f));

        BitmapText title = addText(hudNode, "INVENTORY", 0, panelTop - 44f * sy, 24f * sy,
                new ColorRGBA(0.86f, 0.89f, 0.92f, 1f));
        title.setLocalTranslation(px + pw / 2f - title.getLineWidth() / 2f,
                panelTop - 44f * sy, 0f);
        float hlW = 220f * sx;
        quadAttach(px + pw / 2f - hlW / 2f, panelTop - 62f * sy, hlW, Math.max(1f, sy),
                new ColorRGBA(0.30f, 0.34f, 0.36f, 1f));
    }

    private void buildGridAndToolbar() {
        addText(hudNode, "Inventory", gridLeft, panelTop - 92f * sy, 18f * sy,
                new ColorRGBA(0.72f, 0.78f, 0.82f, 1f));
        buildGrid(gridLeft, gridBottomY);

        addText(hudNode, "Toolbar", gridLeft, gridBottomY - 20f * sy, 18f * sy,
                new ColorRGBA(0.72f, 0.78f, 0.82f, 1f));
        buildToolbar(gridLeft, toolbarY);

        buildTrash(trashX, trashY);

        String hint = "Drag items between slots  |  Right-click for actions  |  Drop here to destroy";
        BitmapText hintText = new BitmapText(font);
        hintText.setSize(11f * sy);
        hintText.setColor(new ColorRGBA(0.42f, 0.47f, 0.50f, 1f));
        hintText.setText(hint);
        hintText.setLocalTranslation(gridLeft + (5 * slot + 4 * gap) / 2f - hintText.getLineWidth() / 2f,
                trashY - 26f * sy, 0f);
        hudNode.attachChild(hintText);
    }

    private void buildGrid(float left, float bottom) {
        for (int r = 0; r < Inventory.GRID_ROWS; r++) {
            for (int c = 0; c < Inventory.GRID_COLS; c++) {
                int idx = r * Inventory.GRID_COLS + c;
                float x = left + c * (slot + gap);
                float y = bottom + r * (slot + gap);
                gridViews[idx] = createSlot(x, y, inventory.getGridSlot(idx),
                        SlotKind.GRID, null, 0.20f, 0.22f, 0.24f);
            }
        }
    }

    private void buildToolbar(float left, float bottom) {
        ColorRGBA accent = new ColorRGBA(0.28f, 0.60f, 0.66f, 1f);
        for (int i = 0; i < Inventory.TOOLBAR_SIZE; i++) {
            float x = left + i * (slot + gap);
            SlotView v = createSlot(x, bottom, inventory.getToolbarSlot(i),
                    SlotKind.TOOLBAR, null, accent.r, accent.g, accent.b);
            toolbarViews[i] = v;
            BitmapText num = addText(hudNode, "" + (i + 1), x + 3f * sx, bottom + slot - 14f * sy,
                    12f * sy, new ColorRGBA(0.50f, 0.56f, 0.60f, 1f));
            v.root.getParent().attachChild(num);
        }
    }

    private void buildCenterBlock() {
        float t = Math.max(1f, 2f * sx);
        float charPanelH = charPanelTop - charPanelBottom;

        quadAttach(charPanelLeft + 4, charPanelBottom - 4, charPanelW, charPanelH,
                new ColorRGBA(0f, 0f, 0f, 0.35f));
        roundedRect(charPanelLeft, charPanelBottom, charPanelW, charPanelH,
                Math.min(16f * sx, slot * 0.5f), new ColorRGBA(0.055f, 0.058f, 0.070f, 0.92f));
        borderStroke(charPanelLeft, charPanelBottom, charPanelW, charPanelH, t,
                new ColorRGBA(0.22f, 0.25f, 0.27f, 1f));

        addText(hudNode, "CHARACTER",
                charPanelLeft + (charPanelW - textWidth("CHARACTER", 15f * sy)) / 2f,
                charPanelTop - 24f * sy, 15f * sy, new ColorRGBA(0.74f, 0.66f, 0.50f, 1f));

        nameText = addText(hudNode, stats.getPlayerName(), 0, charPanelTop - 62f * sy, 22f * sy,
                new ColorRGBA(0.93f, 0.95f, 0.97f, 1f));
        levelText = addText(hudNode, "LV " + stats.getLevel(), 0, charPanelTop - 63f * sy, 13f * sy,
                new ColorRGBA(0.80f, 0.68f, 0.40f, 1f));
        soulIconX = charPanelRight - 78f * sx;
        soulIconY = charPanelTop - 74f * sy;
        quadAttach(soulIconX, soulIconY, 20f * sx, 20f * sy, new ColorRGBA(0.55f, 0.80f, 0.95f, 1f));
        soulText = addText(hudNode, "0", soulIconX - 10f * sx, soulIconY + 4f * sy, 19f * sy,
                new ColorRGBA(0.86f, 0.95f, 1f, 1f));
        refreshIdentityPosition();

        float bandW = Math.min(400f * sx, charPanelW - 100f * sx);
        ColorRGBA trackCol = new ColorRGBA(0.16f, 0.17f, 0.19f, 0.95f);
        float hpY = charPanelTop - 128f * sy;
        hpTrack = quad((charPanelLeft + charPanelW) / 2f - bandW / 2f, hpY, 1f, barThick, trackCol);
        hudNode.attachChild(hpTrack);
        hpFill = quad((charPanelLeft + charPanelW) / 2f - bandW / 2f, hpY, 1f, barThick,
                new ColorRGBA(0.30f, 0.70f, 0.28f, 1f));
        hudNode.attachChild(hpFill);

        float xpY = hpY - (barThick + 14f * sy);
        xpTrack = quad((charPanelLeft + charPanelW) / 2f - bandW / 2f, xpY, 1f, barThick, trackCol);
        hudNode.attachChild(xpTrack);
        xpFill = quad((charPanelLeft + charPanelW) / 2f - bandW / 2f, xpY, 1f, barThick,
                new ColorRGBA(0.28f, 0.48f, 0.92f, 1f));
        hudNode.attachChild(xpFill);

        buildEquipSlot(0, charPanelLeft + (charPanelW - slot) / 2f, helmY, EQUIP_VISUAL[0]);
        buildEquipSlot(1, eqLeftX, chestY, EQUIP_VISUAL[1]);
        buildEquipSlot(2, eqLeftX, legsY, EQUIP_VISUAL[2]);
        buildEquipSlot(3, eqRightX, shieldY, EQUIP_VISUAL[3]);
        buildEquipSlot(4, eqRightX, bootsY, EQUIP_VISUAL[4]);

        buildStatsPanel();
    }

    private void refreshIdentityPosition() {
        if (nameText == null || levelText == null) return;
        float nameW = nameText.getLineWidth();
        float cx = charPanelLeft + charPanelW / 2f - nameW / 2f;
        nameText.setLocalTranslation(cx, charPanelTop - 62f * sy, 0f);
        levelText.setLocalTranslation(cx + nameW + 12f * sx, charPanelTop - 63f * sy, 0f);
        String dust = "" + stats.getSoulDust();
        soulText.setLocalTranslation(soulIconX - 12f * sx - textWidth(dust, 19f * sy), soulIconY + 4f * sy, 0f);
    }

    private void buildEquipSlot(int visualIndex, float x, float y, Inventory.EquipSlot slotType) {
        SlotView v = createSlot(x, y, inventory.getEquipSlot(slotType),
                SlotKind.EQUIPMENT, slotType, 0.22f, 0.24f, 0.26f);
        equipViews[visualIndex] = v;

        String cap = switch (slotType) {
            case HELMET -> "HELMET";
            case CHESTPLATE -> "CHEST";
            case LEGGINGS -> "LEGS";
            case BOOTS -> "BOOTS";
            case SHIELD -> "SHIELD";
        };
        BitmapText caption = addText(hudNode, cap, x + (slot - textWidth(cap, 10f * sy)) / 2f,
                y - 15f * sy, 10f * sy, new ColorRGBA(0.42f, 0.47f, 0.50f, 1f));
        v.root.getParent().attachChild(caption);
    }

    private void buildTrash(float x, float y) {
        trashView = createSlot(x, y, new Slot(null, 0), SlotKind.TRASH, null,
                0.34f, 0.16f, 0.16f);
    }

    private void buildStatsPanel() {
        float titleY = previewBottom - 26f * sy;
        addText(hudNode, "ATTRIBUTES",
                previewLeft + (previewW - textWidth("ATTRIBUTES", 15f * sy)) / 2f,
                titleY, 15f * sy, new ColorRGBA(0.74f, 0.66f, 0.50f, 1f));
        quadAttach(previewLeft, titleY - 12f * sy, previewW, Math.max(1f, sy),
                new ColorRGBA(0.22f, 0.24f, 0.26f, 1f));

        statCenterX[0] = previewLeft + previewW * 0.29f;
        statCenterX[1] = previewLeft + previewW * 0.71f;
        statCenterX[2] = statCenterX[0];
        statCenterX[3] = statCenterX[1];

        float r1l = titleY - 38f * sy;
        float r1v = titleY - 76f * sy;
        float r2l = titleY - 148f * sy;
        float r2v = titleY - 186f * sy;

        float[] labelY = { r1l, r1l, r2l, r2l };
        float[] valueY = { r1v, r1v, r2v, r2v };

        for (int i = 0; i < 4; i++) {
            addText(hudNode, STAT_LABELS[i], 0, labelY[i], 12f * sy,
                    new ColorRGBA(0.55f, 0.60f, 0.63f, 1f))
                    .setLocalTranslation(statCenterX[i] - textWidth(STAT_LABELS[i], 12f * sy) / 2f,
                            labelY[i], 0f);
            statValues[i] = addText(hudNode, "0", 0, valueY[i], 34f * sy, ColorRGBA.White);
        }
        refreshStatPositions();
    }

    private void refreshStatPositions() {
        for (int i = 0; i < 4; i++) {
            float vy = statValues[i].getLocalTranslation().y;
            statValues[i].setLocalTranslation(statCenterX[i] - statValues[i].getLineWidth() / 2f,
                    vy, 0f);
        }
    }

    private static String fmtStat(float v, int decimals) {
        String s = String.format("%." + decimals + "f", v);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s;
    }

    private SlotView createSlot(float x, float y, Slot data, SlotKind kind,
                                Inventory.EquipSlot equipSlot,
                                float br, float bg, float bb) {
        Node root = new Node("slot");
        hudNode.attachChild(root);

        Geometry shadow = quad(x + 2f, y - 2f, slot, slot, new ColorRGBA(0f, 0f, 0f, 0.5f));
        root.attachChild(shadow);

        Geometry border = quad(x - 1f, y - 1f, slot + 2f, slot + 2f,
                new ColorRGBA(br, bg, bb, 1f));
        root.attachChild(border);

        Geometry bgq = quad(x, y, slot, slot, new ColorRGBA(0.10f, 0.11f, 0.13f, 0.92f));
        root.attachChild(bgq);

        Geometry icon = quad(x + 3f, y + 3f, slot - 6f, slot - 6f,
                new ColorRGBA(0.2f, 0.2f, 0.2f, 1f));
        icon.setCullHint(Spatial.CullHint.Always);
        root.attachChild(icon);

        Geometry iconTex = quad(x + 3f, y + 3f, slot - 6f, slot - 6f, ColorRGBA.White);
        iconTex.setCullHint(Spatial.CullHint.Always);
        root.attachChild(iconTex);

        Node silhouette = buildSilhouette(x, y, kind, equipSlot);
        if (silhouette != null) root.attachChild(silhouette);

        BitmapText label = new BitmapText(font);
        label.setSize(19f * sy);
        label.setColor(new ColorRGBA(0.9f, 0.92f, 0.95f, 1f));
        label.setLocalTranslation(x + slot / 2f - 7f, y + slot / 2f + label.getLineHeight() / 2f, 0f);
        root.attachChild(label);

        BitmapText count = new BitmapText(font);
        count.setSize(13f * sy);
        count.setColor(new ColorRGBA(0.95f, 0.85f, 0.55f, 1f));
        count.setLocalTranslation(x + slot - 20f * sx, y + 2f * sy + count.getLineHeight() / 2f, 0f);
        root.attachChild(count);

        Geometry equipMark = quad(x + slot * 0.32f, y + 3f * sy, slot * 0.36f, Math.max(2f, 3f * sy),
                new ColorRGBA(0.85f, 0.70f, 0.38f, 0.9f));
        equipMark.setCullHint(Spatial.CullHint.Always);
        root.attachChild(equipMark);

        return new SlotView(root, border, icon, iconTex, label, count, silhouette,
                data, kind, equipSlot, x, y);
    }

    private Node buildSilhouette(float x, float y, SlotKind kind, Inventory.EquipSlot es) {
        Node n = null;
        if (kind == SlotKind.TRASH) {
            n = new Node("sil-trash");
            ColorRGBA col = new ColorRGBA(0.72f, 0.28f, 0.24f, 0.55f);
            float s = eqSlot;
            float c = x + s / 2f, m = y + s / 2f, u = s / 8f;
            sil(n, c - 2.2f * u, m + 1.5f * u, 4.4f * u, 0.8f * u, col);  // lid
            sil(n, c + 0.4f * u, m + 2.3f * u, 1.6f * u, 0.8f * u, col);  // handle
            sil(n, c - 1.8f * u, m - 2.4f * u, 3.6f * u, 3.4f * u, col);  // bin body
            sil(n, c - 0.5f * u, m - 0.6f * u, 1.0f * u, 0.7f * u,
                    new ColorRGBA(0.10f, 0.11f, 0.13f, 0.9f));            // opening
        } else if (kind == SlotKind.EQUIPMENT) {
            n = new Node("sil-equip");
            ColorRGBA col = new ColorRGBA(0.55f, 0.58f, 0.62f, 0.30f);
            float s = eqSlot;
            float c = x + s / 2f, m = y + s / 2f, u = s / 9f;
            switch (es) {
                case HELMET -> {
                    sil(n, c - 1.9f * u, m + 0.8f * u, 3.8f * u, 1.8f * u, col);
                    sil(n, c - 2.3f * u, m - 0.5f * u, 4.6f * u, 0.6f * u, col);
                }
                case CHESTPLATE -> {
                    sil(n, c - 1.5f * u, m - 2.0f * u, 3.0f * u, 3.4f * u, col);
                    sil(n, c - 2.3f * u, m + 0.4f * u, 1.5f * u, 1.3f * u, col);
                    sil(n, c + 0.8f * u, m + 0.4f * u, 1.5f * u, 1.3f * u, col);
                }
                case LEGGINGS -> {
                    sil(n, c - 1.9f * u, m + 0.5f * u, 3.8f * u, 0.8f * u, col);
                    sil(n, c - 1.4f * u, m - 2.4f * u, 1.2f * u, 2.5f * u, col);
                    sil(n, c + 0.2f * u, m - 2.4f * u, 1.2f * u, 2.5f * u, col);
                }
                case BOOTS -> {
                    sil(n, c - 1.6f * u, m - 1.8f * u, 1.6f * u, 1.3f * u, col);
                    sil(n, c - 1.6f * u, m - 0.5f * u, 2.0f * u, 0.7f * u, col);
                    sil(n, c + 0.0f * u, m - 1.8f * u, 1.6f * u, 1.3f * u, col);
                    sil(n, c + 0.0f * u, m - 0.5f * u, 2.0f * u, 0.7f * u, col);
                }
                case SHIELD -> {
                    sil(n, c - 1.0f * u, m - 2.6f * u, 2.0f * u, 4.6f * u, col);
                    sil(n, c - 1.0f * u, m + 2.0f * u, 2.0f * u, 0.8f * u, col);
                }
                default -> { }
            }
        }
        if (n != null) n.setCullHint(Spatial.CullHint.Always);
        return n;
    }

    private void sil(Node parent, float x, float y, float w, float h, ColorRGBA col) {
        parent.attachChild(quad(x, y, w, h, col));
    }

    private void buildPreview(Camera cam, Spatial playerModel) {
        int tpw = (int) Math.max(32, Math.min(512, previewW));
        int tph = (int) Math.max(32, Math.min(512, previewH));

        previewTex = new Texture2D(tpw, tph, Image.Format.RGBA8);
        previewTex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        previewTex.setMagFilter(Texture.MagFilter.Bilinear);

        FrameBuffer fb = new FrameBuffer(tpw, tph, 1);
        fb.setDepthBuffer(Image.Format.Depth);
        fb.setColorTexture(previewTex);

        previewRoot = new Node("InventoryPreviewScene");

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.4f, -0.6f, -0.9f).normalizeLocal());
        sun.setColor(new ColorRGBA(0.95f, 0.95f, 0.98f, 1f));
        previewRoot.addLight(sun);
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.55f, 0.57f, 0.62f, 1f));
        previewRoot.addLight(ambient);

        previewCamera = new Camera(tpw, tph);
        previewCamera.setFrustumPerspective(40f, (float) tpw / tph, 0.05f, 500f);

        ViewPort off = renderManager.createMainView("inventoryPreview", previewCamera);
        off.setClearFlags(true, true, true);
        off.setBackgroundColor(new ColorRGBA(0.08f, 0.09f, 0.11f, 1f));
        off.attachScene(previewRoot);
        off.setOutputFrameBuffer(fb);
        renderManager.removeMainView(off);
        previewView = off;
        previewReady = true;

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", previewTex);
        Quad q = new Quad(previewW, previewH);
        previewQuad = new Geometry("previewQuad", q);
        previewQuad.setMaterial(mat);
        previewQuad.setQueueBucket(RenderQueue.Bucket.Gui);
        previewQuad.setLocalTranslation(previewLeft, previewBottom, 0f);
        hudNode.attachChild(previewQuad);
    }

    /**
     * Refreshes the character shown in the preview. Clones the real player model
     * (so it always matches what the player wears and the current idle pose),
     * forces CPU skinning (GPU skinning of a clone crashes some AMD drivers),
     * and reframes the dedicated preview camera around the character.
     */
    private void rebuildPreviewModel() {
        if (!previewReady || previewRoot == null) return;
        if (previewModel != null) {
            previewModel.removeFromParent();
            previewModel = null;
        }
        if (previewPlinth != null) {
            previewPlinth.removeFromParent();
            previewPlinth = null;
        }
        previewComposer = null;

        if (playerModelRef == null) return;

        Spatial clone = playerModelRef.clone();
        setCpuSkinning(clone);
        previewRoot.attachChild(clone);
        previewModel = clone;

        AnimComposer composer = findAnimComposer(clone);
        if (composer != null) {
            previewComposer = composer;
            composer.setCurrentAction("Idle");
        }
        buildPlinth();
        fitPreviewCamera();
    }

    private void setCpuSkinning(Spatial s) {
        SkinningControl sc = s.getControl(SkinningControl.class);
        if (sc != null) sc.setHardwareSkinningPreferred(false);
        if (s instanceof Node n) {
            for (Spatial child : n.getChildren()) setCpuSkinning(child);
        }
    }

    private AnimComposer findAnimComposer(Spatial s) {
        AnimComposer composer = s.getControl(AnimComposer.class);
        if (composer != null) return composer;
        if (s instanceof Node n) {
            for (Spatial child : n.getChildren()) {
                AnimComposer found = findAnimComposer(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void buildPlinth() {
        if (previewModel == null) return;
        previewModel.updateGeometricState();
        com.jme3.bounding.BoundingBox bb = (com.jme3.bounding.BoundingBox) previewModel.getWorldBound();
        if (bb == null) return;
        Vector3f center = bb.getCenter(new Vector3f());
        Vector3f ext = bb.getExtent(new Vector3f());
        float radius = Math.max(ext.x, ext.z) * 1.45f + 0.08f;
        Cylinder cyl = new Cylinder(2, 24, radius, 0.06f, true);
        Geometry plinth = new Geometry("previewPlinth", cyl);
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", new ColorRGBA(0.16f, 0.17f, 0.19f, 1f));
        mat.setColor("Ambient", new ColorRGBA(0.16f, 0.17f, 0.19f, 1f));
        mat.setColor("Specular", ColorRGBA.Black);
        plinth.setMaterial(mat);
        plinth.setLocalTranslation(center.x, (center.y - ext.y) - 0.04f, center.z);
        previewRoot.attachChild(plinth);
        previewPlinth = plinth;
    }

    private void fitPreviewCamera() {
        if (previewCamera == null || previewModel == null) return;
        previewModel.updateGeometricState();
        com.jme3.bounding.BoundingBox bb = (com.jme3.bounding.BoundingBox) previewModel.getWorldBound();
        if (bb == null) return;
        Vector3f center = bb.getCenter(new Vector3f());
        Vector3f ext = bb.getExtent(new Vector3f());
        float radius = Math.max(Math.max(ext.x, ext.z), ext.y) * 1.15f + 0.15f;

        float fovV = previewCamera.getFrustumTop() + previewCamera.getFrustumBottom();
        float aspect = previewCamera.getWidth() / (float) previewCamera.getHeight();
        float fovH = 2f * FastMath.atan(FastMath.tan(fovV / 2f) * aspect);
        float dist = radius / FastMath.tan(Math.min(fovV, fovH) / 2f) * 1.18f;

        previewCamera.setLocation(new Vector3f(center.x, center.y, center.z - dist));
        previewCamera.lookAt(new Vector3f(center.x, center.y, center.z), Vector3f.UNIT_Y);
        previewCamera.update();
    }

    private void buildPreviewFrame() {
        float t = Math.max(1f, 2f * sx);
        borderStroke(previewLeft - 2f * sx, previewBottom - 2f * sy,
                previewW + 4f * sx, previewH + 4f * sy, t,
                new ColorRGBA(0.36f, 0.40f, 0.42f, 1f));
    }

    private void updatePreview(float tpf) {
        if (!previewReady || previewView == null || previewRoot == null) return;
        previewRoot.updateLogicalState(tpf);
        if (previewComposer != null && !"Idle".equals(previewComposer.getCurrentAction())) {
            previewComposer.setCurrentAction("Idle");
        }
        previewRoot.updateGeometricState();
        renderManager.renderViewPort(previewView, tpf);
    }

    private void buildTooltip() {
        hudNode.attachChild(tooltipNode);
        tooltipBg = quad(0, 0, 1, 1, new ColorRGBA(0.07f, 0.08f, 0.10f, 0.96f));
        tooltipNode.attachChild(tooltipBg);
        tooltipTitle = new BitmapText(font);
        tooltipTitle.setSize(16f * sy);
        tooltipTitle.setColor(new ColorRGBA(0.92f, 0.95f, 0.98f, 1f));
        tooltipNode.attachChild(tooltipTitle);
        tooltipBody = new BitmapText(font);
        tooltipBody.setSize(12f * sy);
        tooltipBody.setColor(new ColorRGBA(0.6f, 0.68f, 0.72f, 1f));
        tooltipNode.attachChild(tooltipBody);
        tooltipNode.setCullHint(Spatial.CullHint.Always);
    }

    private void buildGhost() {
        hudNode.attachChild(ghostNode);
        ghostIcon = quad(0, 0, slot - 4f, slot - 4f, new ColorRGBA(0.7f, 0.7f, 0.7f, 0.9f));
        ghostNode.attachChild(ghostIcon);
        ghostIconTex = quad(0, 0, slot - 4f, slot - 4f, ColorRGBA.White);
        ghostIconTex.setCullHint(Spatial.CullHint.Always);
        ghostNode.attachChild(ghostIconTex);
        ghostCount = new BitmapText(font);
        ghostCount.setSize(15f * sy);
        ghostCount.setColor(new ColorRGBA(0.98f, 0.88f, 0.6f, 1f));
        ghostNode.attachChild(ghostCount);
        ghostNode.setCullHint(Spatial.CullHint.Always);
    }

    private void buildContextMenu() {
        menuRowH = 26f * sy;
        menuRowW = 120f * sx;
        menuNode.setCullHint(Spatial.CullHint.Always);
        menuBg = quad(0f, 0f, 1f, 1f, new ColorRGBA(0.32f, 0.33f, 0.36f, 0.96f));
        menuNode.attachChild(menuBg);
        for (int i = 0; i < MAX_MENU_OPTIONS; i++) {
            menuRows[i] = quad(0f, 0f, menuRowW, menuRowH, new ColorRGBA(0.55f, 0.57f, 0.63f, 0.95f));
            menuRows[i].setCullHint(Spatial.CullHint.Always);
            menuNode.attachChild(menuRows[i]);
            menuLabels[i] = addText(menuNode, "", 0f, 0f, 16f * sy, new ColorRGBA(0.96f, 0.97f, 0.98f, 1f));
            menuLabels[i].setCullHint(Spatial.CullHint.Always);
        }
        hudNode.attachChild(menuNode);
    }

    private boolean canUse(Item item) {
        return switch (item.getGroup()) {
            case CONSUMABLE, WEAPON, EQUIPMENT -> true;
            default -> false;
        };
    }

    private void openMenu(Slot data, float slotX, float slotY) {
        if (data == null || data.isEmpty()) return;
        Item item = data.getItem();
        if (item == null) return;

        menuSlotData = data;
        menuSlotX = slotX;
        menuSlotY = slotY;
        menuOptionCount = 0;

        boolean hasUse = canUse(item);
        if (hasUse) setMenuOption(menuOptionCount++, "Use");
        setMenuOption(menuOptionCount++, "Drop");
        if (data.count > 1) setMenuOption(menuOptionCount++, "Drop All");

        menuBg.setLocalScale(menuRowW, menuOptionCount * menuRowH, 1f);
        positionMenu(slotX, slotY);
        menuOpen = true;
        // make everything visible again (openMenu != closeMenu!), then draw the
        // menu above the overlay by attaching to its parent (the gui node)
        menuNode.setCullHint(Spatial.CullHint.Never);
        Node parent = hudNode.getParent();
        if (parent != null) {
            parent.attachChild(menuNode);
        } else {
            hudNode.attachChild(menuNode);
        }
    }

    private void setMenuOption(int i, String label) {
        BitmapText t = menuLabels[i];
        t.setText(label);
        float lh = t.getLineHeight();
        t.setLocalTranslation(8f * sx, i * menuRowH + menuRowH / 2f + lh / 2f, 0f);
        t.setCullHint(Spatial.CullHint.Never);
        menuRows[i].setLocalTranslation(0f, i * menuRowH, 0f);
    }

    /** Anchors the menu beside the slot, flipping left when it'd clip off-screen. */
    private void positionMenu(float slotX, float slotY) {
        float mw = menuRowW;
        float mh = menuOptionCount * menuRowH;
        float mx = slotX + slot + 6f * sx;
        float my = slotY + slot - mh;
        if (mx + mw > screenW - 4f) mx = slotX - mw - 6f * sx;
        if (my < 4f) my = 4f;
        if (my + mh > screenH - 4f) my = screenH - 4f - mh;
        menuX = mx;
        menuY = my;
        menuNode.setLocalTranslation(mx, my, 0f);
    }

    private void closeMenu() {
        menuOpen = false;
        menuSlotData = null;
        menuNode.setCullHint(Spatial.CullHint.Always);
        for (int i = 0; i < MAX_MENU_OPTIONS; i++) {
            menuLabels[i].setCullHint(Spatial.CullHint.Always);
            menuRows[i].setCullHint(Spatial.CullHint.Always);
        }
    }

    private void updateMenuHover(Vector2f cur) {
        for (int i = 0; i < menuOptionCount; i++) {
            boolean inside = cur.x >= menuX && cur.x <= menuX + menuRowW
                    && cur.y >= menuY + i * menuRowH && cur.y <= menuY + (i + 1) * menuRowH;
            menuRows[i].setCullHint(inside ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        }
    }

    private boolean handleMenuClick(float px_, float py_) {
        if (!menuOpen) return false;
        for (int i = 0; i < menuOptionCount; i++) {
            if (px_ >= menuX && px_ <= menuX + menuRowW
                    && py_ >= menuY + i * menuRowH && py_ <= menuY + (i + 1) * menuRowH) {
                triggerMenuOption(i);
                return true;
            }
        }
        return false;
    }

    private void triggerMenuOption(int i) {
        Item item = menuSlotData.getItem();
        if (item == null) return;
        boolean hasUse = canUse(item);
        if (hasUse) {
            switch (i) {
                case 0 -> useItem(menuSlotData);
                case 1 -> dropItem(menuSlotData);
                default -> dropAll(menuSlotData);
            }
        } else {
            if (i == 0) dropItem(menuSlotData);
            else dropAll(menuSlotData);
        }
    }

    private void useItem(Slot v) {
        Item item = v.getItem();
        if (item == null) return;
        switch (item.getGroup()) {
            case CONSUMABLE -> {
                if (stats != null && stats.consume(item)) consumeCount(v, 1);
            }
            case WEAPON -> equipWeapon(v, item);
            case EQUIPMENT -> equipArmor(v, item);
            default -> { }
        }
    }

    /** Equip a weapon: put it in toolbar slot 0 and hand it to the combat controller. */
    private void equipWeapon(Slot v, Item item) {
        Slot weaponSlot = inventory.getToolbarSlot(0);
        if (v != weaponSlot && !item.id.equals(weaponSlot.itemId)) {
            inventory.swapMove(v, weaponSlot);
        }
        if (weaponEquipHandler != null) weaponEquipHandler.accept(item.id);
    }

    private void equipArmor(Slot v, Item item) {
        Inventory.EquipSlot es = equipSlotFor(item.category);
        if (es == null) return;
        Slot target = inventory.getEquipSlot(es);
        if (target == v) return;
        inventory.swapMove(v, target);
    }

    private void dropItem(Slot v) {
        v.count--;
        if (v.count <= 0) v.clear();
    }

    private void dropAll(Slot v) {
        v.clear();
    }

    private void consumeCount(Slot v, int amount) {
        v.count -= amount;
        if (v.count <= 0) v.clear();
    }

    private Inventory.EquipSlot equipSlotFor(Item.Category cat) {
        try {
            return Inventory.EquipSlot.valueOf(cat.name());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void updatePointer() {
        Vector2f cur = inputManager.getCursorPosition();
        hovered = slotAt(cur.x, cur.y);

        if (menuOpen) {
            updateMenuHover(cur);
            return;
        }

        float gs = slot - 4f;
        if (selected != null && !selected.data.isEmpty()) {
            Item item = selected.data.getItem();
            ghostNode.setCullHint(Spatial.CullHint.Never);
            ghostNode.setLocalTranslation(cur.x - gs / 2f, cur.y - gs / 2f + 12f, 0f);
            if (item != null) {
                Texture tex = loadItemTexture(item.iconPath);
                if (tex != null) {
                    ghostIconTex.getMaterial().setTexture("ColorMap", tex);
                    ghostIconTex.getMaterial().setColor("Color", ColorRGBA.White);
                    ghostIconTex.setCullHint(Spatial.CullHint.Never);
                    ghostIcon.setCullHint(Spatial.CullHint.Always);
                } else {
                    ghostIcon.getMaterial().setColor("Color",
                            new ColorRGBA(item.iconColor.r, item.iconColor.g, item.iconColor.b, 0.85f));
                    ghostIcon.setCullHint(Spatial.CullHint.Never);
                    ghostIconTex.setCullHint(Spatial.CullHint.Always);
                }
            }
            ghostCount.setText(selected.data.count > 1 ? "" + selected.data.count : "");
            ghostCount.setLocalTranslation(gs - 22f, gs - 20f, 0f);
            ghostNode.getParent().attachChild(ghostNode);
        } else {
            ghostNode.setCullHint(Spatial.CullHint.Always);
        }

        if (hovered != null && !hovered.data.isEmpty()) {
            Item item = hovered.data.getItem();
            tooltipTitle.setText(item.name);
            tooltipBody.setText(tooltipBodyText(item, hovered));
            float tw = Math.max(tooltipTitle.getLineWidth(), tooltipBody.getLineWidth()) + 20f;
            int lines = tooltipBody.getLineCount();
            float th = Math.max(44f * sy, 20f * sy + lines * 14f * sy);
            tooltipNode.setLocalTranslation(cur.x + 14f, cur.y + 6f, 0f);
            tooltipBg.setLocalScale(tw, th, 1f);
            tooltipTitle.setLocalTranslation(5f, th - 18f * sy, 0f);
            tooltipBody.setLocalTranslation(5f, th - 18f * sy - 4f, 0f);
            tooltipNode.setCullHint(Spatial.CullHint.Never);
        } else {
            tooltipNode.setCullHint(Spatial.CullHint.Always);
        }
    }

    private String tooltipBodyText(Item item, SlotView view) {
        StringBuilder body = new StringBuilder();
        if (item.description != null && !item.description.isEmpty()) {
            body.append(item.description);
        }
        if (item.defenseBonus > 0f) {
            body.append("\nDefense +").append((int) item.defenseBonus);
        }
        if (item.moveSpeedBonus > 0f) {
            body.append("\nMove speed +").append(item.moveSpeedBonus);
        }
        if (item.effect != Item.Effect.NONE) {
            body.append("\nEffect: ").append(effectLabel(item));
        }
        body.append("\n").append(categoryLabel(item.category));
        if (item.maxStack > 1) {
            body.append("  |  x").append(view.data.count);
        }
        if (isEquipped(item)) {
            body.append("  |  (EQUIPPED)");
        }
        return body.toString();
    }

    private String effectLabel(Item item) {
        return switch (item.effect) {
            case HEAL_INSTANT -> "Heal " + (int) item.power + " instantly";
            case REGEN -> "Regen " + (int) item.power + " HP/s for " + (int) item.duration + "s";
            case SPEED -> "Speed +" + (int) (item.power * 100f) + "% for " + (int) item.duration + "s";
            case STRENGTH -> "Damage +" + (int) (item.power * 100f) + "% for " + (int) item.duration + "s";
            case CRIT -> "Crit chance for " + (int) item.duration + "s";
            default -> "";
        };
    }

    private boolean isEquipped(Item item) {
        if (inventory == null) return false;
        for (Slot s : inventory.getEquipment()) {
            if (!s.isEmpty() && s.itemId.equals(item.id)) return true;
        }
        return false;
    }

    private String categoryLabel(Item.Category c) {
        String s = c.toString();
        return s.substring(0, 1) + s.substring(1).toLowerCase();
    }

    private SlotView slotAt(float px_, float py_) {
        SlotView r = find(toolbarViews, px_, py_);
        if (r != null) return r;
        r = find(gridViews, px_, py_);
        if (r != null) return r;
        r = find(equipViews, px_, py_);
        if (r != null) return r;
        if (trashView != null && px_ >= trashView.x && px_ <= trashView.x + slot
                && py_ >= trashView.y && py_ <= trashView.y + slot) {
            return trashView;
        }
        return null;
    }

    private SlotView find(SlotView[] arr, float px_, float py_) {
        for (SlotView v : arr) {
            if (v == null) continue;
            if (px_ >= v.x && px_ <= v.x + slot && py_ >= v.y && py_ <= v.y + slot) return v;
        }
        return null;
    }

    private boolean canPlaceIn(SlotView target, Item item) {
        return switch (target.kind) {
            case GRID, TRASH -> true;
            case TOOLBAR ->
                    item.getGroup() == Item.Group.WEAPON
                            || item.getGroup() == Item.Group.CONSUMABLE
                            || item.getGroup() == Item.Group.KEY;
            case EQUIPMENT -> matchEquip(item.category, target.equipSlot);
        };
    }

    private boolean matchEquip(Item.Category cat, Inventory.EquipSlot slotType) {
        if (slotType == null) return false;
        return cat == Item.Category.valueOf(slotType.toString());
    }

    private void tryMove(SlotView from, SlotView to) {
        if (from == null || from.data.isEmpty() || from == to) {
            clearSelection();
            return;
        }
        Item item = from.data.getItem();
        if (item == null) {
            clearSelection();
            return;
        }
        if (to == null) {
            clearSelection();
            return;
        }
        if (to.kind == SlotKind.TRASH) {
            if (trashArmTimer > 0) {
                from.data.clear();
                trashArmTimer = 0;
                clearSelection();
            } else {
                trashArmTimer = TRASH_ARM;
            }
            return;
        }
        if (!canPlaceIn(to, item)) {
            deny(to);
            return;
        }
        inventory.swapMove(from.data, to.data);
        clearSelection();
    }

    private void deny(SlotView slotView) {
        denySlot = slotView;
        denyTimer = 18;
    }

    private void clearSelection() {
        selected = null;
        trashArmTimer = 0;
    }

    private void refreshIdentityBars() {
        String name = stats.getPlayerName();
        if (!name.equals(nameText.getText())) nameText.setText(name);
        String lvl = "LV " + stats.getLevel();
        if (!lvl.equals(levelText.getText())) levelText.setText(lvl);
        refreshIdentityPosition();
        float w = Math.min(400f * sx, charPanelW - 100f * sx);
        setBar(hpTrack, hpFill, stats.getHealthFraction(), w);
        setBar(xpTrack, xpFill, stats.getExperienceFraction(), w);
        if (!xpFractionLogged) {
            xpFractionLogged = true;
            System.out.println("[InventoryUI] XP fraction at open: "
                    + stats.getExperienceFraction() + " (xp=" + stats.getExperience()
                    + " / toNext=" + stats.getExperienceToNext() + ")");
        }
    }

    private void setBar(Geometry track, Geometry fill, float frac, float w) {
        track.setLocalScale(w, 1f, 1f);
        fill.setLocalScale(w * Math.max(0f, Math.min(1f, frac)), 1f, 1f);
    }

    private void refreshSoulDust() {
        String dust = "" + stats.getSoulDust();
        if (!dust.equals(soulText.getText())) soulText.setText(dust);
    }

    private void refreshStats() {
        statValues[0].setText(fmtStat(stats.getAverageDamage(), STAT_DECIMALS[0]));
        statValues[1].setText(fmtStat(stats.getArmorPoints(), STAT_DECIMALS[1]));
        statValues[2].setText(fmtStat(stats.getMovementSpeed(), STAT_DECIMALS[2]));
        statValues[3].setText(fmtStat(stats.getAttackSpeed(), STAT_DECIMALS[3]));
        refreshStatPositions();
    }

    private void refreshSlots() {
        if (denyTimer > 0) denyTimer--;
        if (denyTimer == 0) denySlot = null;
        if (trashArmTimer > 0) trashArmTimer--;

        refreshArray(gridViews, inventory.getGrid());
        refreshArray(toolbarViews, inventory.getToolbar());
        refreshArray(equipViews, inventory.getEquipment());
        applyTrashVisual();
    }

    private void refreshArray(SlotView[] views, Slot[] data) {
        for (int i = 0; i < views.length; i++) {
            SlotView v = views[i];
            if (v == null) continue;
            applySlotVisual(v, data[i]);
        }
    }

    private void applySlotVisual(SlotView v, Slot s) {
        boolean isSel = v == selected;
        boolean isHov = v == hovered && selected == null;
        boolean isDeny = v == denySlot && denyTimer > 0;

        if (v.kind == SlotKind.EQUIPMENT) {
            v.label.setText("");
            v.count.setText("");
            Item equipItem = s.isEmpty() ? null : s.getItem();
            Texture equipTex = equipItem != null ? loadItemTexture(equipItem.iconPath) : null;
            if (equipTex != null) {
                v.iconTex.getMaterial().setTexture("ColorMap", equipTex);
                v.iconTex.getMaterial().setColor("Color", ColorRGBA.White);
                v.iconTex.setCullHint(Spatial.CullHint.Never);
                v.icon.setCullHint(Spatial.CullHint.Always);
                if (v.silhouette != null) {
                    v.silhouette.setCullHint(Spatial.CullHint.Always);
                }
            } else {
                v.iconTex.setCullHint(Spatial.CullHint.Always);
                v.icon.setCullHint(Spatial.CullHint.Always);
                if (v.silhouette != null) {
                    v.silhouette.setCullHint(Spatial.CullHint.Never);
                    if (equipItem != null) {
                        tintSilhouette(v.silhouette, equipItem.iconColor);
                    } else {
                        tintSilhouette(v.silhouette, DEFAULT_EQUIP_SIL_COLOR);
                    }
                }
            }
            applyBorderState(v, isSel, isHov, isDeny);
            if (v.equipMark != null) {
                v.equipMark.setCullHint(s.isEmpty() ? Spatial.CullHint.Always : Spatial.CullHint.Never);
            }
            return;
        }

        if (s.isEmpty()) {
            v.icon.setCullHint(Spatial.CullHint.Always);
            v.iconTex.setCullHint(Spatial.CullHint.Always);
            if (v.silhouette != null) {
                v.silhouette.setCullHint(Spatial.CullHint.Never);
            }
            v.label.setText("");
            v.count.setText("");
        } else {
            Item item = s.getItem();
            Texture tex = item != null ? loadItemTexture(item.iconPath) : null;
            if (tex != null) {
                v.iconTex.getMaterial().setTexture("ColorMap", tex);
                v.iconTex.getMaterial().setColor("Color", ColorRGBA.White);
                v.iconTex.setCullHint(Spatial.CullHint.Never);
                v.icon.setCullHint(Spatial.CullHint.Always);
                v.label.setText("");
                if (v.silhouette != null) {
                    v.silhouette.setCullHint(Spatial.CullHint.Always);
                }
            } else {
                v.iconTex.setCullHint(Spatial.CullHint.Always);
                v.icon.setCullHint(Spatial.CullHint.Never);
                if (v.silhouette != null) {
                    v.silhouette.setCullHint(Spatial.CullHint.Always);
                }
                if (item != null) {
                    v.icon.getMaterial().setColor("Color",
                            new ColorRGBA(item.iconColor.r, item.iconColor.g, item.iconColor.b, 1f));
                    v.label.setText(item.name.substring(0, 1));
                    // BitmapText anchors top-left and draws downward, so the letter centers
                    // by offsetting up half its line height (Bug 3).
                    float lh = v.label.getLineHeight();
                    v.label.setLocalTranslation(
                            v.x + slot / 2f - v.label.getLineWidth() / 2f,
                            v.y + slot / 2f + lh / 2f,
                            0f);
                }
            }
            if (v.equipMark != null) {
                boolean equipped = !s.isEmpty() && isEquipped(s.getItem());
                v.equipMark.setCullHint(equipped ? Spatial.CullHint.Never : Spatial.CullHint.Always);
            }

            v.count.setText(s.count > 1 ? "" + s.count : "");
            float ch = v.count.getLineHeight();
            v.count.setLocalTranslation(v.x + slot - 3f * sx - v.count.getLineWidth(),
                    v.y + ch / 2f + 2f * sy, 0f);
        }

        applyBorderState(v, isSel, isHov, isDeny);
    }

    private void applyBorderState(SlotView v, boolean isSel, boolean isHov, boolean isDeny) {
        if (isDeny) {
            v.border.getMaterial().setColor("Color", new ColorRGBA(0.85f, 0.25f, 0.25f, 1f));
        } else if (isSel) {
            v.border.getMaterial().setColor("Color", new ColorRGBA(0.30f, 0.62f, 0.80f, 1f));
        } else if (isHov) {
            v.border.getMaterial().setColor("Color", new ColorRGBA(0.45f, 0.48f, 0.52f, 1f));
        } else {
            v.border.getMaterial().setColor("Color", toolbarBorder(v.kind));
        }
    }

    private void tintSilhouette(Node n, ColorRGBA col) {
        for (Spatial child : n.getChildren()) {
            if (child instanceof Geometry g) {
                g.getMaterial().setColor("Color", col.clone());
            } else if (child instanceof Node cn) {
                tintSilhouette(cn, col);
            }
        }
    }

    private ColorRGBA toolbarBorder(SlotKind kind) {
        if (kind == SlotKind.TOOLBAR) {
            return new ColorRGBA(0.28f, 0.60f, 0.66f, 1f);
        }
        return new ColorRGBA(0.22f, 0.24f, 0.26f, 1f);
    }

    private void applyTrashVisual() {
        if (trashView == null) return;
        if (trashView.silhouette != null) {
            trashView.silhouette.setCullHint(Spatial.CullHint.Never);
        }
        boolean armed = trashArmTimer > 0;
        boolean hov = trashView == hovered && selected == null;
        if (armed) {
            trashView.border.getMaterial().setColor("Color", new ColorRGBA(0.95f, 0.30f, 0.25f, 1f));
        } else if (hov) {
            trashView.border.getMaterial().setColor("Color", new ColorRGBA(0.62f, 0.30f, 0.28f, 1f));
        } else {
            trashView.border.getMaterial().setColor("Color", new ColorRGBA(0.34f, 0.16f, 0.16f, 1f));
        }
    }

    private void roundedRect(float x, float y, float w, float h, float r, ColorRGBA color) {
        float r2 = Math.max(0f, r);
        quadAttach(x, y + r2, w, h - 2 * r2, color);
        quadAttach(x + r2, y, w - 2 * r2, h, color);
        for (int i = 0; i < 4; i++) {
            float cx = (i == 0 || i == 3) ? x + r2 : x + w - r2;
            float cy = (i < 2) ? y + h - r2 : y + r2;
            quadAttach(cx, cy, r2, r2, color);
        }
    }

    private void borderStroke(float x, float y, float w, float h, float t, ColorRGBA color) {
        quadAttach(x, y, w, t, color);
        quadAttach(x, y + h - t, w, t, color);
        quadAttach(x, y, t, h, color);
        quadAttach(x + w - t, y, t, h, color);
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

    private float textWidth(String text, float size) {
        BitmapText tmp = new BitmapText(font);
        tmp.setSize(size);
        tmp.setText(text);
        return tmp.getLineWidth();
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

    private Texture loadItemTexture(String path) {
        if (path == null) return null;
        Texture tex = iconCache.get(path);
        if (tex == null) {
            try {
                tex = assetManager.loadTexture(path);
            } catch (Exception e) {
                tex = null;
            }
            iconCache.put(path, tex);
        }
        return tex;
    }
}
