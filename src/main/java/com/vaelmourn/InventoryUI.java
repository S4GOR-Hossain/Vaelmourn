package com.vaelmourn;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.SkinningControl;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.input.InputManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
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
import java.util.Locale;
import java.util.Map;

/**
 * Player inventory screen. Toggle with E.
 *
 * <p>Layout is a faithful port of the inventory mockup, taken from the concept's own
 * reference space (1920x1080, origin top-left) — so every number below is a literal
 * constant from the design rather than a proportion:</p>
 * <ul>
 *   <li>HP label, a 594x10 bar and the "cur/max" readout in the upper left</li>
 *   <li>20 item slots, 5 columns x 4 rows of 130x128 on a 149x148 pitch</li>
 *   <li>6 hotbar slots, 180x178 on a 211 pitch</li>
 *   <li>one large middle panel for the selected item</li>
 *   <li>a tall right panel listing ATK / SPD / DEF / ATK SPD / LUCK, each a white
 *       label above a dark-red value</li>
 * </ul>
 *
 * <p>Coordinates here are authored top-down (like the design) and converted once in
 * {@link #quadAt}, because jME's gui space has its origin at the BOTTOM left.</p>
 *
 * Interactions: left-click to select, drag (ghost follows cursor) and drop with
 * strict hotbar compatibility, hover highlighting, drop-target feedback, tooltip and a
 * two-step trash confirmation.
 */
public class InventoryUI {

    // ---- mockup geometry (reference space, top-left origin) --------------------

    private static final float GRID_X = 172f, GRID_Y = 254f;
    private static final float GRID_W = 130f, GRID_H = 128f;
    private static final float GRID_PITCH_X = 149f, GRID_PITCH_Y = 148f;

    private static final float HOT_X = 172f, HOT_Y = 846f;
    private static final float HOT_W = 180f, HOT_H = 178f, HOT_PITCH = 211f;

    private static final float DETAIL_X = 934f, DETAIL_Y = 106f;
    private static final float DETAIL_W = 472f, DETAIL_H = 720f;

    private static final float STATS_X = 1506f, STATS_Y = 101f;
    private static final float STATS_W = 353f, STATS_H = 923f;

    private static final float HP_LABEL_X = 178f, HP_LABEL_Y = 130f;
    private static final float HP_LABEL_W = 100f, HP_LABEL_H = 50f;
    private static final float BAR_X = 278f, BAR_Y = 147f, BAR_W = 594f, BAR_H = 10f;
    private static final float HP_TEXT_Y = 172f, HP_TEXT_H = 50f;
    private static final float XP_Y = 232f, XP_H = 8f;

    /** compact extras the mockup has no room for */
    private static final float IDENT_X = 172f, IDENT_Y = 26f;
    private static final float SOUL_ICON_X = 640f, SOUL_ICON_Y = 30f, SOUL_ICON_S = 22f;
    private static final float PREVIEW_X = 946f, PREVIEW_Y = 196f, PREVIEW_W = 448f, PREVIEW_H = 456f;
    private static final float DETAIL_NAME_Y = 148f;      // centre of the item name
    private static final float DETAIL_DESC_Y = 676f;      // TOP of the description block
    private static final float DESC_SIZE = 28f;
    private static final float TRASH_X = 1416f, TRASH_Y = 900f, TRASH_SIZE = 80f;

    private static final float PANEL_BORDER = 4f;   // concept's panel border thickness

    /** vertical field of view of the character preview camera, in degrees */
    private static final float PREVIEW_FOV = 40f;
    /** +1 = camera in front of a +Z facing model. Set to -1f if you see the character's back. */
    private static final float PREVIEW_CAM_SIDE = 1f;

    // ---- look & feel ------------------------------------------------------------

    private static final ColorRGBA COL_MUTED   = new ColorRGBA(0.62f, 0.64f, 0.69f, 1f);
    private static final ColorRGBA COL_SHADOW  = new ColorRGBA(0f, 0f, 0f, 0.40f);
    private static final ColorRGBA COL_SHINE   = new ColorRGBA(1f, 1f, 1f, 0.07f);
    private static final ColorRGBA COL_SELECT  = new ColorRGBA(0.30f, 0.62f, 0.80f, 1f);
    private static final ColorRGBA COL_OK      = new ColorRGBA(0.30f, 0.75f, 0.45f, 1f);
    private static final ColorRGBA COL_BAD     = new ColorRGBA(0.85f, 0.25f, 0.25f, 1f);
    private static final ColorRGBA COL_GOLD    = new ColorRGBA(0.92f, 0.76f, 0.38f, 1f);

    // ---- runtime --------------------------------------------------------------

    private final AssetManager assetManager;
    private final RenderManager renderManager;
    private final InputManager inputManager;
    private final Inventory inventory;
    private final PlayerStats stats;

    private final Node hudNode = new Node("InventoryHUD");
    private BitmapFont font;

    private final Map<String, Texture> iconCache = new HashMap<>();

    private final float sx, sy, screenW, screenH;

    private BitmapText nameText, levelText, soulText;
    private Geometry soulIcon;
    private Geometry hpTrack, hpFill, hpShine, xpTrack, xpFill;
    private float barThick;

    private static final String[] STAT_LABELS = {"ATK", "SPD", "DEF", "ATK SPD", "LUCK"};
    private static final int[] STAT_DECIMALS = {0, 1, 0, 2, 2};
    private final BitmapText[] statValues = new BitmapText[STAT_LABELS.length];
    private final float[] statCenterXRef = new float[STAT_LABELS.length];
    private final float[] statValueCenterYRef = new float[STAT_LABELS.length];

    private BitmapText hpText;
    private BitmapText detailName, detailDesc, detailHint;
    private BitmapText trashCaption;

    private enum SlotKind {GRID, TOOLBAR, TRASH}

    private static class SlotView {
        final Node root;
        final Geometry border;
        final Geometry fill;
        final Geometry icon;
        final Geometry iconTex;
        final BitmapText label;
        final BitmapText count;
        final Node silhouette;
        final Slot data;
        final SlotKind kind;
        final ColorRGBA baseBorder;
        final ColorRGBA fillBase;
        float x, y, w, h;

        SlotView(Node root, Geometry border, Geometry fill, Geometry icon, Geometry iconTex,
                 BitmapText label, BitmapText count, Node silhouette,
                 Slot data, SlotKind kind, ColorRGBA baseBorder, ColorRGBA fillBase,
                 float x, float y, float w, float h) {
            this.root = root;
            this.border = border;
            this.fill = fill;
            this.icon = icon;
            this.iconTex = iconTex;
            this.label = label;
            this.count = count;
            this.silhouette = silhouette;
            this.data = data;
            this.kind = kind;
            this.baseBorder = baseBorder;
            this.fillBase = fillBase;
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    private final SlotView[] gridViews = new SlotView[Inventory.GRID_SIZE];
    private final SlotView[] toolbarViews = new SlotView[Inventory.TOOLBAR_SIZE];
    private SlotView trashView;

    private SlotView selected;
    private SlotView hovered;
    private int denyTimer = 0;
    private SlotView denySlot;
    private int trashArmTimer = 0;
    private static final int TRASH_ARM = 90;

    private final Node tooltipNode = new Node("Tooltip");
    private Geometry tooltipFrame;
    private Geometry tooltipBg;
    private BitmapText tooltipTitle;
    private BitmapText tooltipBody;

    private final Node ghostNode = new Node("DragGhost");
    private Geometry ghostIcon;
    private Geometry ghostIconTex;
    private BitmapText ghostCount;
    private float ghostW, ghostH;

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
    private Geometry menuFrame;
    private Geometry menuBg;
    private final Geometry[] menuRows = new Geometry[MAX_MENU_OPTIONS];
    private final BitmapText[] menuLabels = new BitmapText[MAX_MENU_OPTIONS];
    private boolean menuOpen = false;
    private Slot menuSlotData;
    private int menuOptionCount = 0;
    private float menuRowH, menuRowW;
    private float menuX, menuY;
    private java.util.function.Consumer<String> weaponEquipHandler;

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
        this.playerModelRef = playerModel;

        this.sx = screenW / 1920f;
        this.sy = screenH / 1080f;
        this.screenW = screenW;
        this.screenH = screenH;

        font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        barThick = Math.max(1f, 2f * sy);
        ghostW = GRID_W * sx;
        ghostH = GRID_H * sy;

        buildBackground();
        buildIdentity();
        buildVitals();
        buildGrid();
        buildHotbar();
        buildTrash();
        buildDetailPanel();
        buildStatsPanel();
        buildPreview(cam, playerModel);
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

    /** Lets the world re-equip the combat weapon whenever "Use" hits a sword. */
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
        openMenu(hit.data, hit.x, hit.y, hit.w);
    }

    /** Called when the inventory is closed: right-click on the world HUD hotbar slot. */
    public void openMenuForSlot(Slot data, float x, float y) {
        if (selected != null) return;
        if (menuOpen && data == menuSlotData) {
            closeMenu();
            return;
        }
        closeMenu();
        openMenu(data, x, y, HOT_W * sx);
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
            refreshIdentity();
            refreshVitals();
            refreshStats();
            refreshDetail();
            refreshSlots();
        } else if (menuOpen) {
            updateMenuHover(inputManager.getCursorPosition());
        }
    }

    // ---- coordinate helpers ----------------------------------------------------

    /** reference x -> pixels. */
    private float rx(float refX) { return refX * sx; }

    /** reference y measured from the TOP -> jME y of that edge. */
    private float ry(float refTopY) { return screenH - refTopY * sy; }

    /** jME lower-left y for an element whose top edge is {@code refTopY}. */
    private float bottomOf(float refTopY, float refH) { return screenH - (refTopY + refH) * sy; }

    /** Unshaded colour material with alpha blending, so translucent colours/icons really blend. */
    private Material mat(ColorRGBA color) {
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return m;
    }

    private static ColorRGBA mix(ColorRGBA a, ColorRGBA b, float t) {
        return new ColorRGBA(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t,
                a.b + (b.b - a.b) * t, a.a + (b.a - a.a) * t);
    }

    /** A gui quad authored top-down; returns it unattached. */
    private Geometry quadAt(float refX, float refTopY, float refW, float refH, ColorRGBA color) {
        Quad q = new Quad(Math.max(1f, rx(refW)), Math.max(1f, refH * sy));
        Geometry g = new Geometry("quad", q);
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        g.setMaterial(mat(color));
        g.setLocalTranslation(rx(refX), bottomOf(refTopY, refH), 0f);
        return g;
    }

    private Geometry attachAt(float refX, float refTopY, float refW, float refH, ColorRGBA color) {
        Geometry g = quadAt(refX, refTopY, refW, refH, color);
        hudNode.attachChild(g);
        return g;
    }

    /** Bordered square panel, mirroring the concept's boxes. */
    private void panelAt(float refX, float refTopY, float refW, float refH,
                         ColorRGBA fill, ColorRGBA border, float t) {
        attachAt(refX, refTopY, refW, refH, border);
        attachAt(refX + t, refTopY + t, refW - t * 2f, refH - t * 2f, fill);
    }

    /** panelAt plus a drop shadow, inner top highlight and corner accents. */
    private void fancyPanel(float refX, float refTopY, float refW, float refH,
                            ColorRGBA fill, ColorRGBA border, float t) {
        attachAt(refX + 7f, refTopY + 9f, refW, refH, COL_SHADOW);
        panelAt(refX, refTopY, refW, refH, fill, border, t);
        // inner highlight + inner shade
        attachAt(refX + t, refTopY + t, refW - 2f * t, 2f, COL_SHINE);
        attachAt(refX + t, refTopY + refH - t - 3f, refW - 2f * t, 3f, new ColorRGBA(0f, 0f, 0f, 0.22f));
        // corner accents
        float a = 26f, th = 3f;
        ColorRGBA acc = UiKit.lighten(border, 0.35f);
        attachAt(refX, refTopY, a, th, acc);
        attachAt(refX, refTopY, th, a, acc);
        attachAt(refX + refW - a, refTopY, a, th, acc);
        attachAt(refX + refW - th, refTopY, th, a, acc);
        attachAt(refX, refTopY + refH - th, a, th, acc);
        attachAt(refX, refTopY + refH - a, th, a, acc);
        attachAt(refX + refW - a, refTopY + refH - th, a, th, acc);
        attachAt(refX + refW - th, refTopY + refH - a, th, a, acc);
    }

    private BitmapText textRef(Node parent, String str, float refLeftX, float refTopY,
                               float refSize, ColorRGBA color) {
        BitmapText bt = new BitmapText(font);
        bt.setText(str == null ? "" : str);
        bt.setSize(Math.max(1f, refSize * sy));
        bt.setColor(color);
        bt.setLocalTranslation(rx(refLeftX), ry(refTopY), 0f);
        parent.attachChild(bt);
        return bt;
    }

    /** Text centred on a reference point, both axes. */
    private BitmapText textCentered(Node parent, String str, float refCenterX, float refCenterY,
                                    float refSize, ColorRGBA color) {
        BitmapText bt = new BitmapText(font);
        bt.setText(str == null ? "" : str);
        bt.setSize(Math.max(1f, refSize * sy));
        bt.setColor(color);
        float h = bt.getHeight();
        bt.setLocalTranslation(rx(refCenterX) - bt.getLineWidth() / 2f,
                ry(refCenterY) + h / 2f, 0f);
        parent.attachChild(bt);
        return bt;
    }

    private BitmapText textRight(Node parent, String str, float refRightX, float refCenterY,
                                 float refSize, ColorRGBA color) {
        BitmapText bt = new BitmapText(font);
        bt.setText(str == null ? "" : str);
        bt.setSize(Math.max(1f, refSize * sy));
        bt.setColor(color);
        bt.setLocalTranslation(rx(refRightX) - bt.getLineWidth(),
                ry(refCenterY) + bt.getHeight() / 2f, 0f);
        parent.attachChild(bt);
        return bt;
    }

    // ---- static construction ----------------------------------------------------

    private void buildBackground() {
        Geometry overlay = new Geometry("bg", new Quad(screenW, screenH));
        overlay.setQueueBucket(RenderQueue.Bucket.Gui);
        overlay.setMaterial(mat(UiKit.INV_BG));
        overlay.setLocalTranslation(0f, 0f, 0f);
        overlay.setCullHint(Spatial.CullHint.Never);
        hudNode.attachChild(overlay);

        // soft top / bottom bands frame the screen
        attachAt(0f, 0f, 1920f, 90f, new ColorRGBA(0f, 0f, 0f, 0.28f));
        attachAt(0f, 1040f, 1920f, 40f, new ColorRGBA(0f, 0f, 0f, 0.28f));

        // header rule under the identity row
        attachAt(IDENT_X, 96f, BAR_X + BAR_W - IDENT_X, 2f, UiKit.BORDER);
        attachAt(IDENT_X, 96f, 90f, 2f, COL_GOLD);

        // divider between the grid and the hotbar
        attachAt(HOT_X, 835f, HOT_PITCH * (Inventory.TOOLBAR_SIZE - 1) + HOT_W, 2f, UiKit.BORDER);
    }

    /** Compact extra: identity sits in the empty strip above the mockup's HP row. */
    private void buildIdentity() {
        nameText = textRef(hudNode, stats.getPlayerName(), IDENT_X, IDENT_Y, 26f, UiKit.WHITE);
        levelText = textRef(hudNode, "LV " + stats.getLevel(), IDENT_X, IDENT_Y, 20f, UiKit.VALUE_RED);

        // soul-dust gem with a dark frame
        attachAt(SOUL_ICON_X - 2f, SOUL_ICON_Y - 2f, SOUL_ICON_S + 4f, SOUL_ICON_S + 4f,
                new ColorRGBA(0.05f, 0.08f, 0.10f, 1f));
        soulIcon = attachAt(SOUL_ICON_X, SOUL_ICON_Y, SOUL_ICON_S, SOUL_ICON_S,
                new ColorRGBA(0.6f, 0.9f, 1.0f, 1f));
        attachAt(SOUL_ICON_X, SOUL_ICON_Y, SOUL_ICON_S, 4f, new ColorRGBA(1f, 1f, 1f, 0.45f));
        soulText = textRef(hudNode, "0", SOUL_ICON_X + SOUL_ICON_S + 10f, IDENT_Y + 2f,
                22f, UiKit.VALUE_RED);

        // screen title above the middle panel
        textCentered(hudNode, "INVENTORY", DETAIL_X + DETAIL_W / 2f, 56f, 40f, UiKit.WHITE);
        attachAt(DETAIL_X + DETAIL_W / 2f - 60f, 80f, 120f, 3f, COL_GOLD);
        textRight(hudNode, "E  CLOSE", STATS_X + STATS_W - 4f, 56f, 22f, COL_MUTED);
    }

    private void buildVitals() {
        // mockup HP row: label, bar, and the "cur/max" readout
        textRef(hudNode, "HP", HP_LABEL_X, HP_LABEL_Y, 42f, UiKit.WHITE);

        ColorRGBA trackCol = new ColorRGBA(0.20f, 0.19f, 0.20f, 1f);
        ColorRGBA frameCol = new ColorRGBA(0.04f, 0.04f, 0.05f, 1f);

        attachAt(BAR_X - 2f, BAR_Y - 2f, BAR_W + 4f, BAR_H + 4f, frameCol);
        hpTrack = attachAt(BAR_X, BAR_Y, BAR_W, BAR_H, trackCol);
        hpFill = attachAt(BAR_X, BAR_Y, BAR_W, BAR_H, UiKit.HP_GREEN);
        hpShine = attachAt(BAR_X, BAR_Y, BAR_W, 3f, new ColorRGBA(1f, 1f, 1f, 0.28f));

        hpText = textRight(hudNode, "0/0", BAR_X + BAR_W, HP_TEXT_Y + HP_TEXT_H / 2f,
                40f, UiKit.HP_GREEN);

        // compact extra: XP directly under the HP readout
        textRef(hudNode, "XP", HP_LABEL_X, XP_Y - 8f, 22f, COL_MUTED);
        attachAt(BAR_X - 2f, XP_Y - 2f, BAR_W + 4f, XP_H + 4f, frameCol);
        xpTrack = attachAt(BAR_X, XP_Y, BAR_W, XP_H, trackCol);
        xpFill = attachAt(BAR_X, XP_Y, BAR_W, XP_H, new ColorRGBA(0.30f, 0.52f, 0.95f, 1f));
        attachAt(BAR_X, XP_Y, BAR_W, 2f, new ColorRGBA(1f, 1f, 1f, 0.12f));
    }

    private void buildGrid() {
        for (int r = 0; r < Inventory.GRID_ROWS; r++) {
            for (int c = 0; c < Inventory.GRID_COLS; c++) {
                int idx = r * Inventory.GRID_COLS + c;
                float refX = GRID_X + GRID_PITCH_X * c;
                float refY = GRID_Y + GRID_PITCH_Y * r;
                gridViews[idx] = createSlot(refX, refY, GRID_W, GRID_H,
                        inventory.getGridSlot(idx), SlotKind.GRID, UiKit.BORDER);
            }
        }
    }

    private void buildHotbar() {
        for (int i = 0; i < Inventory.TOOLBAR_SIZE; i++) {
            float refX = HOT_X + HOT_PITCH * i;
            SlotView v = createSlot(refX, HOT_Y, HOT_W, HOT_H,
                    inventory.getToolbarSlot(i), SlotKind.TOOLBAR, UiKit.BORDER);
            toolbarViews[i] = v;
            // hotkey number on a small dark plate
            float plate = 30f * sx;
            Geometry bg = rawQuad(v.x + 6f * sx, v.y + v.h - 6f * sy - plate, plate, plate,
                    new ColorRGBA(0f, 0f, 0f, 0.55f));
            hudNode.attachChild(bg);
            BitmapText num = new BitmapText(font);
            num.setText("" + (i + 1));
            num.setSize(Math.max(8f, 20f * sy));
            num.setColor(COL_GOLD);
            num.setLocalTranslation(v.x + 6f * sx + plate / 2f - num.getLineWidth() / 2f,
                    v.y + v.h - 6f * sy - plate / 2f + num.getHeight() / 2f, 0f);
            hudNode.attachChild(num);
        }
    }

    private void buildTrash() {
        trashView = createSlot(TRASH_X, TRASH_Y, TRASH_SIZE, TRASH_SIZE,
                new Slot(null, 0), SlotKind.TRASH, new ColorRGBA(0.40f, 0.20f, 0.20f, 1f));
        trashCaption = textCentered(hudNode, "TRASH", TRASH_X + TRASH_SIZE / 2f,
                TRASH_Y + TRASH_SIZE + 18f, 18f, COL_MUTED);
    }

    private void buildDetailPanel() {
        fancyPanel(DETAIL_X, DETAIL_Y, DETAIL_W, DETAIL_H, UiKit.INV_PANEL, UiKit.BORDER, PANEL_BORDER);

        // compact extra: the character preview lives inside the mockup's middle panel
        attachAt(PREVIEW_X - 6f, PREVIEW_Y - 6f, PREVIEW_W + 12f, PREVIEW_H + 12f, UiKit.BORDER);
        attachAt(PREVIEW_X - 4f, PREVIEW_Y - 4f, PREVIEW_W + 8f, PREVIEW_H + 8f,
                new ColorRGBA(0.10f, 0.10f, 0.11f, 1f));

        // divider between preview and description
        attachAt(DETAIL_X + 24f, 664f, DETAIL_W - 48f, 2f, UiKit.BORDER);

        detailName = textCentered(hudNode, "", DETAIL_X + DETAIL_W / 2f, DETAIL_NAME_Y,
                46f, UiKit.WHITE);

        // description: centred inside a fixed-width box (BitmapText aligns each line for us)
        float boxW = (DETAIL_W - 40f) * sx;
        detailDesc = new BitmapText(font);
        detailDesc.setSize(Math.max(1f, DESC_SIZE * sy));
        detailDesc.setColor(UiKit.WHITE);
        detailDesc.setBox(new Rectangle(0f, 0f, boxW, 150f * sy));
        detailDesc.setAlignment(BitmapFont.Align.Center);
        detailDesc.setText("");
        detailDesc.setLocalTranslation(rx(DETAIL_X) + (DETAIL_W * sx - boxW) / 2f,
                ry(DETAIL_DESC_Y), 0f);
        hudNode.attachChild(detailDesc);

        detailHint = textCentered(hudNode, "Select an item to inspect",
                DETAIL_X + DETAIL_W / 2f, DETAIL_DESC_Y + 50f, 24f, COL_MUTED);
    }

    private void buildStatsPanel() {
        fancyPanel(STATS_X, STATS_Y, STATS_W, STATS_H, UiKit.INV_PANEL, UiKit.BORDER, PANEL_BORDER);

        // ten even bands: white label above a dark-red value, per stat
        float bandH = STATS_H / (STAT_LABELS.length * 2);
        float centerX = STATS_X + STATS_W / 2f;
        for (int i = 0; i < STAT_LABELS.length; i++) {
            float labelCenterY = STATS_Y + bandH * (i * 2 + 0.5f);
            float valueCenterY = STATS_Y + bandH * (i * 2 + 1.5f);
            if (i > 0) {
                attachAt(STATS_X + 28f, STATS_Y + bandH * (i * 2), STATS_W - 56f, 2f,
                        new ColorRGBA(1f, 1f, 1f, 0.08f));
            }
            textCentered(hudNode, STAT_LABELS[i], centerX, labelCenterY, 36f, UiKit.WHITE);
            statValues[i] = textCentered(hudNode, "0", centerX, valueCenterY, 54f, UiKit.STAT_RED);
            statCenterXRef[i] = centerX;
            statValueCenterYRef[i] = valueCenterY;
        }
    }

    private SlotView createSlot(float refX, float refTopY, float refW, float refH,
                                Slot data, SlotKind kind, ColorRGBA borderColor) {
        Node root = new Node("slot");
        hudNode.attachChild(root);

        float jx = rx(refX);
        float jy = bottomOf(refTopY, refH);
        float jw = Math.max(1f, rx(refW));
        float jh = Math.max(1f, refH * sy);
        float t = Math.min(barThick, Math.min(jw, jh) * 0.12f);

        // soft drop shadow
        Geometry shadow = rawQuad(jx + 4f * sx, jy - 5f * sy, jw, jh, COL_SHADOW);
        root.attachChild(shadow);

        Geometry border = new Geometry("slotBorder", new Quad(jw, jh));
        border.setQueueBucket(RenderQueue.Bucket.Gui);
        border.setMaterial(mat(borderColor));
        border.setLocalTranslation(jx, jy, 0f);
        root.attachChild(border);

        ColorRGBA fillBase = mix(UiKit.INV_PANEL, ColorRGBA.Black, 0.28f);
        fillBase.a = UiKit.INV_PANEL.a;
        Geometry fill = new Geometry("slotFill", new Quad(jw - 2f * t, jh - 2f * t));
        fill.setQueueBucket(RenderQueue.Bucket.Gui);
        fill.setMaterial(mat(fillBase));
        fill.setLocalTranslation(jx + t, jy + t, 0f);
        root.attachChild(fill);

        // bevel: light top edge, dark bottom edge
        float bt = Math.max(1f, sy * 1.5f);
        root.attachChild(rawQuad(jx + t, jy + jh - t - bt, jw - 2f * t, bt, COL_SHINE));
        root.attachChild(rawQuad(jx + t, jy + t, jw - 2f * t, bt, new ColorRGBA(0f, 0f, 0f, 0.35f)));

        float inset = Math.min(jw, jh) * 0.12f;
        float iw = Math.max(1f, jw - 2f * inset);
        float ih = Math.max(1f, jh - 2f * inset);

        Geometry icon = new Geometry("slotIcon", new Quad(iw, ih));
        icon.setQueueBucket(RenderQueue.Bucket.Gui);
        icon.setMaterial(mat(new ColorRGBA(0.2f, 0.2f, 0.2f, 1f)));
        icon.setLocalTranslation(jx + inset, jy + inset, 0f);
        icon.setCullHint(Spatial.CullHint.Always);
        root.attachChild(icon);

        Geometry iconTex = new Geometry("slotIconTex", new Quad(iw, ih));
        iconTex.setQueueBucket(RenderQueue.Bucket.Gui);
        iconTex.setMaterial(mat(ColorRGBA.White));
        iconTex.setLocalTranslation(jx + inset, jy + inset, 0f);
        iconTex.setCullHint(Spatial.CullHint.Always);
        root.attachChild(iconTex);

        Node silhouette = buildSilhouette(jx + inset, jy + inset, iw, ih, kind);
        if (silhouette != null) root.attachChild(silhouette);

        float side = Math.min(iw, ih);
        BitmapText label = new BitmapText(font);
        label.setSize(Math.max(8f, side * 0.44f));
        label.setColor(new ColorRGBA(0.92f, 0.93f, 0.95f, 1f));
        label.setLocalTranslation(jx + jw / 2f, jy + jh / 2f + label.getHeight() / 2f, 0f);
        root.attachChild(label);

        BitmapText count = new BitmapText(font);
        count.setSize(Math.max(8f, Math.min(20f * sy, side * 0.30f)));
        count.setColor(new ColorRGBA(0.98f, 0.88f, 0.60f, 1f));
        count.setLocalTranslation(jx + jw - 8f * sx, jy + 6f * sy + count.getHeight(), 0f);
        root.attachChild(count);

        return new SlotView(root, border, fill, icon, iconTex, label, count, silhouette,
                data, kind, borderColor, fillBase, jx, jy, jw, jh);
    }

    private Node buildSilhouette(float x, float y, float w, float h, SlotKind kind) {
        Node n = null;
        if (kind == SlotKind.TRASH) {
            n = new Node("sil-trash");
            ColorRGBA col = new ColorRGBA(0.72f, 0.28f, 0.24f, 0.75f);
            float u = Math.min(w, h) / 10f;
            float c = x + w / 2f, m = y + h / 2f;
            sil(n, c - 2.2f * u, m + 1.5f * u, 4.4f * u, 0.8f * u, col);   // lid
            sil(n, c + 0.4f * u, m + 2.3f * u, 1.6f * u, 0.8f * u, col);   // handle
            sil(n, c - 1.8f * u, m - 2.4f * u, 3.6f * u, 3.4f * u, col);   // bin
            sil(n, c - 0.5f * u, m - 0.6f * u, 1.0f * u, 0.7f * u,
                    new ColorRGBA(0.10f, 0.11f, 0.13f, 0.9f));               // opening
        }
        if (n != null) n.setCullHint(Spatial.CullHint.Always);
        return n;
    }

    private void sil(Node parent, float x, float y, float w, float h, ColorRGBA col) {
        Quad q = new Quad(Math.max(1f, w), Math.max(1f, h));
        Geometry g = new Geometry("sil", q);
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        g.setMaterial(mat(col));
        g.setLocalTranslation(x, y, 0f);
        parent.attachChild(g);
    }

    // ---- character preview -----------------------------------------------------

    private void buildPreview(Camera cam, Spatial playerModel) {
        float pw = rx(PREVIEW_W);
        float ph = PREVIEW_H * sy;

        int tpw = (int) Math.max(32, Math.min(512, pw));
        int tph = (int) Math.max(32, Math.min(512, ph));

        previewTex = new Texture2D(tpw, tph, Image.Format.RGBA8);
        previewTex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        previewTex.setMagFilter(Texture.MagFilter.Bilinear);

        FrameBuffer fb = new FrameBuffer(tpw, tph, 1);
        fb.setDepthBuffer(Image.Format.Depth);
        fb.setColorTexture(previewTex);

        previewRoot = new Node("InventoryPreviewScene");

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.4f, -0.6f, -0.9f * PREVIEW_CAM_SIDE).normalizeLocal());
        sun.setColor(new ColorRGBA(0.95f, 0.95f, 0.98f, 1f));
        previewRoot.addLight(sun);
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.55f, 0.57f, 0.62f, 1f));
        previewRoot.addLight(ambient);

        previewCamera = new Camera(tpw, tph);
        previewCamera.setFrustumPerspective(PREVIEW_FOV, (float) tpw / tph, 0.05f, 500f);

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
        Geometry q = new Geometry("previewQuad", new Quad(pw, ph));
        previewQuad = q;
        previewQuad.setMaterial(mat);
        previewQuad.setQueueBucket(RenderQueue.Bucket.Gui);
        previewQuad.setLocalTranslation(rx(PREVIEW_X), bottomOf(PREVIEW_Y, PREVIEW_H), 0f);
        hudNode.attachChild(previewQuad);
    }

    /**
     * Clones the real player model so the preview always matches what the player wears,
     * forces CPU skinning (GPU skinning of a clone crashes some AMD drivers), and
     * reframes the dedicated preview camera around the character.
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
            // started ONCE here; re-setting it every frame would pin the pose to frame 0
            if (composer.getAnimClip("Idle") != null) {
                composer.setCurrentAction("Idle");
            }
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
        // jME cylinders stand along Z; lay it flat so it is a disc under the feet
        plinth.rotate(-FastMath.HALF_PI, 0f, 0f);
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

        // frustumTop + frustumBottom is 0 for a symmetric frustum, so use the real FOV
        float fovV = PREVIEW_FOV * FastMath.DEG_TO_RAD;
        float aspect = previewCamera.getWidth() / (float) previewCamera.getHeight();
        float fovH = 2f * FastMath.atan(FastMath.tan(fovV / 2f) * aspect);
        float dist = radius / FastMath.tan(Math.min(fovV, fovH) / 2f) * 1.18f;

        previewCamera.setLocation(new Vector3f(center.x, center.y, center.z + PREVIEW_CAM_SIDE * dist));
        previewCamera.lookAt(new Vector3f(center.x, center.y, center.z), Vector3f.UNIT_Y);
        previewCamera.update();
    }

    private void updatePreview(float tpf) {
        if (!previewReady || previewView == null || previewRoot == null) return;
        previewRoot.updateLogicalState(tpf);
        previewRoot.updateGeometricState();
        renderManager.renderViewPort(previewView, tpf);
    }

    // ---- tooltip / ghost / context menu ---------------------------------------

    private void buildTooltip() {
        hudNode.attachChild(tooltipNode);
        tooltipFrame = rawQuad(-1f, -1f, 1f, 1f, UiKit.BORDER);
        tooltipNode.attachChild(tooltipFrame);
        tooltipBg = rawQuad(0, 0, 1, 1, new ColorRGBA(0.07f, 0.08f, 0.10f, 0.97f));
        tooltipNode.attachChild(tooltipBg);
        tooltipTitle = new BitmapText(font);
        tooltipTitle.setSize(Math.max(8f, 18f * sy));
        tooltipTitle.setColor(COL_GOLD);
        tooltipNode.attachChild(tooltipTitle);
        tooltipBody = new BitmapText(font);
        tooltipBody.setSize(Math.max(7f, 13f * sy));
        tooltipBody.setColor(new ColorRGBA(0.78f, 0.83f, 0.87f, 1f));
        tooltipNode.attachChild(tooltipBody);
        tooltipNode.setCullHint(Spatial.CullHint.Always);
    }

    private void buildGhost() {
        hudNode.attachChild(ghostNode);
        ghostIcon = rawQuad(0, 0, ghostW, ghostH, new ColorRGBA(0.7f, 0.7f, 0.7f, 0.9f));
        ghostNode.attachChild(ghostIcon);
        ghostIconTex = rawQuad(0, 0, ghostW, ghostH, ColorRGBA.White);
        ghostIconTex.setCullHint(Spatial.CullHint.Always);
        ghostNode.attachChild(ghostIconTex);
        ghostCount = new BitmapText(font);
        ghostCount.setSize(Math.max(8f, 15f * sy));
        ghostCount.setColor(new ColorRGBA(0.98f, 0.88f, 0.6f, 1f));
        ghostNode.attachChild(ghostCount);
        ghostNode.setCullHint(Spatial.CullHint.Always);
    }

    private void buildContextMenu() {
        menuRowH = 30f * sy;
        menuRowW = 130f * sx;
        menuNode.setCullHint(Spatial.CullHint.Always);
        menuFrame = rawQuad(-2f, -2f, 1f, 1f, UiKit.BORDER);
        menuNode.attachChild(menuFrame);
        menuBg = rawQuad(0f, 0f, 1f, 1f, new ColorRGBA(0.12f, 0.13f, 0.16f, 0.98f));
        menuNode.attachChild(menuBg);
        for (int i = 0; i < MAX_MENU_OPTIONS; i++) {
            menuRows[i] = rawQuad(0f, 0f, menuRowW, menuRowH, new ColorRGBA(0.42f, 0.16f, 0.16f, 0.95f));
            menuRows[i].setCullHint(Spatial.CullHint.Always);
            menuNode.attachChild(menuRows[i]);
            menuLabels[i] = new BitmapText(font);
            menuLabels[i].setSize(Math.max(8f, 18f * sy));
            menuLabels[i].setColor(new ColorRGBA(0.96f, 0.97f, 0.98f, 1f));
            menuLabels[i].setCullHint(Spatial.CullHint.Always);
            menuNode.attachChild(menuLabels[i]);
        }
        hudNode.attachChild(menuNode);
    }

    private boolean canUse(Item item) {
        return switch (item.getGroup()) {
            case CONSUMABLE, WEAPON -> true;
            default -> false;
        };
    }

    /** bottom edge (menu-local) of option {@code i}; option 0 sits at the TOP of the menu. */
    private float rowBottom(int i) {
        return (menuOptionCount - 1 - i) * menuRowH;
    }

    private void openMenu(Slot data, float slotX, float slotY, float slotW) {
        if (data == null || data.isEmpty()) return;
        Item item = data.getItem();
        if (item == null) return;

        menuSlotData = data;
        menuOptionCount = 0;

        if (canUse(item)) setMenuOption(menuOptionCount++, "Use");
        setMenuOption(menuOptionCount++, "Drop");
        if (data.count > 1) setMenuOption(menuOptionCount++, "Drop All");

        float mh = menuOptionCount * menuRowH;
        menuBg.setLocalScale(menuRowW, mh, 1f);
        menuFrame.setLocalScale(menuRowW + 4f, mh + 4f, 1f);
        layoutMenuRows();
        positionMenu(slotX, slotY, slotW);
        menuOpen = true;
        // draw the menu above the overlay by re-attaching it to the gui node
        menuNode.setCullHint(Spatial.CullHint.Never);
        Node parent = hudNode.getParent();
        if (parent != null) parent.attachChild(menuNode);
        else hudNode.attachChild(menuNode);
    }

    private void setMenuOption(int i, String label) {
        menuLabels[i].setText(label);
        menuLabels[i].setCullHint(Spatial.CullHint.Never);
    }

    private void layoutMenuRows() {
        for (int i = 0; i < menuOptionCount; i++) {
            float yb = rowBottom(i);
            BitmapText t = menuLabels[i];
            t.setLocalTranslation(12f * sx, yb + menuRowH / 2f + t.getLineHeight() / 2f, 0f);
            menuRows[i].setLocalTranslation(0f, yb, 0f);
        }
    }

    /** Anchors the menu beside the slot, flipping left when it would clip off-screen. */
    private void positionMenu(float slotX, float slotY, float slotW) {
        float mw = menuRowW;
        float mh = menuOptionCount * menuRowH;
        float mx = slotX + slotW + 6f * sx;
        float my = slotY + 0f - mh;
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
                    && cur.y >= menuY + rowBottom(i) && cur.y <= menuY + rowBottom(i) + menuRowH;
            menuRows[i].setCullHint(inside ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        }
    }

    private boolean handleMenuClick(float px_, float py_) {
        if (!menuOpen) return false;
        for (int i = 0; i < menuOptionCount; i++) {
            if (px_ >= menuX && px_ <= menuX + menuRowW
                    && py_ >= menuY + rowBottom(i) && py_ <= menuY + rowBottom(i) + menuRowH) {
                triggerMenuOption(i);
                return true;
            }
        }
        return false;
    }

    private void triggerMenuOption(int i) {
        Item item = menuSlotData.getItem();
        if (item == null) return;
        if (canUse(item)) {
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
            // bombs are thrown with Q, not from this menu
            default -> { }
        }
    }

    /** Equip a weapon: put it in hotbar slot 0 and hand it to the combat controller. */
    private void equipWeapon(Slot v, Item item) {
        Slot weaponSlot = inventory.getToolbarSlot(0);
        if (v != weaponSlot && !item.id.equals(weaponSlot.itemId)) {
            inventory.swapMove(v, weaponSlot);
        }
        if (weaponEquipHandler != null) weaponEquipHandler.accept(item.id);
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

    // ---- pointer, hover, tooltip, ghost ----------------------------------------

    private void updatePointer() {
        Vector2f cur = inputManager.getCursorPosition();
        hovered = slotAt(cur.x, cur.y);

        if (menuOpen) {
            updateMenuHover(cur);
            tooltipNode.setCullHint(Spatial.CullHint.Always);
            return;
        }

        if (selected != null && !selected.data.isEmpty()) {
            Item item = selected.data.getItem();
            ghostNode.setCullHint(Spatial.CullHint.Never);
            ghostNode.setLocalTranslation(cur.x - ghostW / 2f, cur.y - ghostH / 2f, 0f);
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
            // bottom-right, same spot as the count in a real slot
            ghostCount.setLocalTranslation(ghostW - 8f * sx - ghostCount.getLineWidth(),
                    6f * sy + ghostCount.getHeight(), 0f);
        } else {
            ghostNode.setCullHint(Spatial.CullHint.Always);
        }

        if (hovered != null && !hovered.data.isEmpty() && selected == null) {
            Item item = hovered.data.getItem();
            tooltipTitle.setText(item.name);
            tooltipBody.setText(tooltipBodyText(item, hovered));

            float padX = 10f * sx, padY = 8f * sy, gap = 4f * sy;
            float titleH = tooltipTitle.getHeight();
            float bodyH = tooltipBody.getHeight();
            float tw = Math.max(tooltipTitle.getLineWidth(), tooltipBody.getLineWidth()) + padX * 2f;
            float th = padY * 2f + titleH + gap + bodyH;

            float tx = cur.x + 14f;
            float ty = cur.y + 6f;
            if (tx + tw > screenW - 4f) tx = cur.x - tw - 14f;
            if (ty + th > screenH - 4f) ty = screenH - 4f - th;
            if (tx < 4f) tx = 4f;
            if (ty < 4f) ty = 4f;

            tooltipNode.setLocalTranslation(tx, ty, 0f);
            tooltipBg.setLocalScale(tw, th, 1f);
            tooltipFrame.setLocalScale(tw + 2f, th + 2f, 1f);
            tooltipTitle.setLocalTranslation(padX, th - padY, 0f);
            tooltipBody.setLocalTranslation(padX, th - padY - titleH - gap, 0f);
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
        return body.toString();
    }

    private String effectLabel(Item item) {
        return switch (item.effect) {
            case HEAL_INSTANT -> "Heal " + (int) item.power + " instantly";
            case REGEN -> "Regen " + (int) item.power + " HP/s for " + (int) item.duration + "s";
            case SPEED -> "Speed +" + (int) (item.power * 100f) + "% for " + (int) item.duration + "s";
            case STRENGTH -> "Damage +" + (int) (item.power * 100f) + "% for " + (int) item.duration + "s";
            case CRIT -> "Crit chance for " + (int) item.duration + "s";
            case THROW_EXPLOSIVE -> "Thrown (Q). Fuses " + (int) item.duration
                    + "s, then " + (int) item.power + " damage in a radius";
            default -> "";
        };
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
        if (contains(trashView, px_, py_)) return trashView;
        return null;
    }

    private SlotView find(SlotView[] arr, float px_, float py_) {
        for (SlotView v : arr) {
            if (v == null) continue;
            if (contains(v, px_, py_)) return v;
        }
        return null;
    }

    private static boolean contains(SlotView v, float px_, float py_) {
        if (v == null) return false;
        return px_ >= v.x && px_ <= v.x + v.w && py_ >= v.y && py_ <= v.y + v.h;
    }

    private boolean canPlaceIn(SlotView target, Item item) {
        return switch (target.kind) {
            case GRID, TRASH -> true;
            // bombs belong on the hotbar so Q can throw them
            case TOOLBAR ->
                    item.getGroup() == Item.Group.WEAPON
                            || item.getGroup() == Item.Group.CONSUMABLE
                            || item.getGroup() == Item.Group.KEY
                            || item.getGroup() == Item.Group.THROWABLE;
        };
    }

    private void tryMove(SlotView from, SlotView to) {
        if (from == null || from.data.isEmpty() || from == to) {
            clearSelection();
            return;
        }
        Item item = from.data.getItem();
        if (item == null || to == null) {
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

    // ---- per-frame refresh -----------------------------------------------------

    private void refreshIdentity() {
        String name = stats.getPlayerName();
        if (!name.equals(nameText.getText())) nameText.setText(name);
        String lvl = "LV " + stats.getLevel();
        if (!lvl.equals(levelText.getText())) levelText.setText(lvl);
        String dust = "" + stats.getSoulDust();
        if (!dust.equals(soulText.getText())) soulText.setText(dust);

        float nameX = rx(IDENT_X);
        nameText.setLocalTranslation(nameX, ry(IDENT_Y), 0f);
        levelText.setLocalTranslation(nameX + nameText.getLineWidth() + 14f * sx,
                ry(IDENT_Y + 3f), 0f);
        soulText.setLocalTranslation(rx(SOUL_ICON_X) + rx(SOUL_ICON_S) + 10f * sx,
                ry(IDENT_Y + 2f), 0f);
    }

    /** green when healthy, amber when hurt, red when low */
    private ColorRGBA hpColor(float frac) {
        ColorRGBA low = new ColorRGBA(0.85f, 0.22f, 0.22f, 1f);
        ColorRGBA mid = new ColorRGBA(0.95f, 0.75f, 0.25f, 1f);
        if (frac >= 0.6f) return UiKit.HP_GREEN;
        if (frac >= 0.3f) return mix(mid, UiKit.HP_GREEN, (frac - 0.3f) / 0.3f);
        return mix(low, mid, frac / 0.3f);
    }

    private void refreshVitals() {
        float max = Math.max(0.0001f, stats.getMaxHealth());
        float frac = FastMath.clamp(stats.getHealth() / max, 0f, 1f);
        hpFill.setLocalScale(frac, 1f, 1f);
        hpShine.setLocalScale(frac, 1f, 1f);
        hpTrack.setLocalScale(1f, 1f, 1f);

        ColorRGBA hc = hpColor(frac);
        hpFill.getMaterial().setColor("Color", hc);
        hpText.setColor(hc);

        String hp = (int) Math.ceil(stats.getHealth()) + "/" + (int) max;
        if (!hp.equals(hpText.getText())) {
            hpText.setText(hp);
            hpText.setLocalTranslation(rx(BAR_X + BAR_W) - hpText.getLineWidth(),
                    ry(HP_TEXT_Y + HP_TEXT_H / 2f) + hpText.getHeight() / 2f, 0f);
        }

        xpFill.setLocalScale(FastMath.clamp(stats.getExperienceFraction(), 0f, 1f), 1f, 1f);
        xpTrack.setLocalScale(1f, 1f, 1f);
    }

    private void refreshDetail() {
        Item item = selected != null ? selected.data.getItem() : null;
        String name = item != null ? item.name : "";
        if (!name.equals(detailName.getText())) {
            detailName.setText(name);
            // BitmapText is left-anchored, so re-centre (and shrink if too wide) on every change
            float maxW = rx(DETAIL_W - 30f);
            detailName.setSize(Math.max(1f, 46f * sy));
            float w = detailName.getLineWidth();
            if (w > maxW && w > 0f) detailName.setSize(Math.max(1f, 46f * sy * maxW / w));
            detailName.setLocalTranslation(
                    rx(DETAIL_X + DETAIL_W / 2f) - detailName.getLineWidth() / 2f,
                    ry(DETAIL_NAME_Y) + detailName.getHeight() / 2f, 0f);
        }

        String desc = item == null ? "" : wrapText(item.description, 420f * sx, DESC_SIZE * sy);
        if (!desc.equals(detailDesc.getText())) detailDesc.setText(desc);

        detailHint.setCullHint(item == null ? Spatial.CullHint.Never : Spatial.CullHint.Always);
    }

    /** BitmapText has no word wrap, so break on measured width. */
    private String wrapText(String text, float maxWidthPx, float sizePx) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && textWidth(candidate, sizePx) > maxWidthPx) {
                out.append(line).append('\n');
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) out.append(line);
        return out.toString();
    }

    private static String fmtStat(float v, int decimals) {
        String s = String.format(Locale.US, "%." + decimals + "f", v);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s;
    }

    private void refreshStats() {
        float[] values = {
                stats.getAverageDamage(),
                stats.getMovementSpeed(),
                stats.getArmorPoints(),
                stats.getAttackSpeed(),
                stats.getLuckMultiplier(),
        };
        for (int i = 0; i < statValues.length; i++) {
            String str = fmtStat(values[i], STAT_DECIMALS[i]);
            if (!str.equals(statValues[i].getText())) statValues[i].setText(str);
            // re-centre: the value's width changes whenever the number's digits change
            statValues[i].setLocalTranslation(
                    rx(statCenterXRef[i]) - statValues[i].getLineWidth() / 2f,
                    ry(statValueCenterYRef[i]) + statValues[i].getHeight() / 2f, 0f);
        }
    }

    private void refreshSlots() {
        if (denyTimer > 0) denyTimer--;
        if (denyTimer == 0) denySlot = null;
        if (trashArmTimer > 0) trashArmTimer--;

        refreshArray(gridViews, inventory.getGrid());
        refreshArray(toolbarViews, inventory.getToolbar());
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

        // drop-target feedback while dragging
        Item dragItem = selected != null ? selected.data.getItem() : null;
        boolean isTarget = dragItem != null && v == hovered && v != selected;
        boolean targetOk = isTarget && canPlaceIn(v, dragItem);
        boolean targetBad = isTarget && !targetOk;

        if (s.isEmpty()) {
            v.icon.setCullHint(Spatial.CullHint.Always);
            v.iconTex.setCullHint(Spatial.CullHint.Always);
            if (v.silhouette != null) v.silhouette.setCullHint(Spatial.CullHint.Never);
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
                if (v.silhouette != null) v.silhouette.setCullHint(Spatial.CullHint.Always);
            } else {
                v.iconTex.setCullHint(Spatial.CullHint.Always);
                v.icon.setCullHint(Spatial.CullHint.Never);
                if (v.silhouette != null) v.silhouette.setCullHint(Spatial.CullHint.Always);
                if (item != null) {
                    v.icon.getMaterial().setColor("Color",
                            new ColorRGBA(item.iconColor.r, item.iconColor.g, item.iconColor.b, 1f));
                    v.label.setText(item.name.substring(0, 1));
                    v.label.setLocalTranslation(
                            v.x + v.w / 2f - v.label.getLineWidth() / 2f,
                            v.y + v.h / 2f + v.label.getHeight() / 2f,
                            0f);
                }
            }

            v.count.setText(s.count > 1 ? "" + s.count : "");
            v.count.setLocalTranslation(v.x + v.w - 8f * sx - v.count.getLineWidth(),
                    v.y + 6f * sy + v.count.getHeight(), 0f);
        }

        applyBorderState(v, isSel, isHov, isDeny, targetOk, targetBad);
    }

    private void applyBorderState(SlotView v, boolean isSel, boolean isHov, boolean isDeny,
                                  boolean targetOk, boolean targetBad) {
        ColorRGBA border = v.baseBorder;
        ColorRGBA fill = v.fillBase;
        if (isDeny) {
            border = COL_BAD;
            fill = mix(v.fillBase, COL_BAD, 0.20f);
        } else if (isSel) {
            border = COL_SELECT;
            fill = mix(v.fillBase, COL_SELECT, 0.18f);
        } else if (targetOk) {
            border = COL_OK;
            fill = mix(v.fillBase, COL_OK, 0.14f);
        } else if (targetBad) {
            border = COL_BAD;
            fill = mix(v.fillBase, COL_BAD, 0.14f);
        } else if (isHov) {
            border = UiKit.lighten(v.baseBorder, 0.14f);
            fill = mix(v.fillBase, ColorRGBA.White, 0.07f);
        }
        v.border.getMaterial().setColor("Color", border);
        v.fill.getMaterial().setColor("Color", fill);
    }

    private void applyTrashVisual() {
        if (trashView == null) return;
        if (trashView.silhouette != null) {
            trashView.silhouette.setCullHint(Spatial.CullHint.Never);
        }
        boolean armed = trashArmTimer > 0;
        boolean hov = trashView == hovered;
        ColorRGBA border = trashView.baseBorder;
        ColorRGBA fill = trashView.fillBase;
        if (armed) {
            border = new ColorRGBA(0.95f, 0.30f, 0.25f, 1f);
            fill = mix(trashView.fillBase, border, 0.22f);
        } else if (hov) {
            border = new ColorRGBA(0.62f, 0.30f, 0.28f, 1f);
            fill = mix(trashView.fillBase, border, 0.12f);
        }
        trashView.border.getMaterial().setColor("Color", border);
        trashView.fill.getMaterial().setColor("Color", fill);

        String cap = armed ? "CONFIRM" : "TRASH";
        if (!cap.equals(trashCaption.getText())) {
            trashCaption.setText(cap);
            trashCaption.setColor(armed ? new ColorRGBA(0.95f, 0.38f, 0.32f, 1f) : COL_MUTED);
            trashCaption.setLocalTranslation(
                    rx(TRASH_X + TRASH_SIZE / 2f) - trashCaption.getLineWidth() / 2f,
                    ry(TRASH_Y + TRASH_SIZE + 18f) + trashCaption.getHeight() / 2f, 0f);
        }
    }

    // ---- small helpers ----------------------------------------------------------

    /** A gui quad in raw jME space (lower-left origin); returns it unattached. */
    private Geometry rawQuad(float x, float y, float w, float h, ColorRGBA color) {
        Quad q = new Quad(Math.max(1f, w), Math.max(1f, h));
        Geometry g = new Geometry("quad", q);
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        g.setMaterial(mat(color));
        g.setLocalTranslation(x, y, 0f);
        return g;
    }

    private float textWidth(String text, float sizePx) {
        BitmapText tmp = new BitmapText(font);
        tmp.setSize(Math.max(1f, sizePx));
        tmp.setText(text);
        return tmp.getLineWidth();
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