package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
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

    private final float sx;
    private final float sy;

    private float pw, ph, px, py;         // panel
    private float panelTop, panelRight;
    private float slot, gap, eqSlot, eqGap;
    private float playerBlockLeft;        // left edge of the cohesive right block
    private float previewLeft, previewBottom, previewW, previewH;

    private static final ColorRGBA DEFAULT_EQUIP_SIL_COLOR = new ColorRGBA(0.55f, 0.58f, 0.62f, 0.30f);

    private static final Inventory.EquipSlot[] EQUIP_VISUAL = {
            Inventory.EquipSlot.HELMET,
            Inventory.EquipSlot.CHESTPLATE,
            Inventory.EquipSlot.LEGGINGS,
            Inventory.EquipSlot.BOOTS,
            Inventory.EquipSlot.SHIELD
    };

    // ---- identity (top of right block) ----
    private BitmapText identityText;
    private float identityX, barX;
    private Geometry hpTrack, hpFill, xpTrack, xpFill;
    private float hpBottomY, xpBottomY, barThick;

    // ---- soul dust ----
    private BitmapText soulText;
    private float soulIconX, soulIconY;

    // ---- stats bar ----
    private final BitmapText[] statValues = new BitmapText[4];

    // ---- slots ----
    private enum SlotKind { GRID, TOOLBAR, EQUIPMENT, TRASH }

    private static class SlotView {
        final Node root;
        final Geometry border;
        final Geometry icon;
        final BitmapText label;
        final BitmapText count;
        final Node silhouette;
        final Slot data;
        final SlotKind kind;
        final Inventory.EquipSlot equipSlot;
        float x, y;

        SlotView(Node root, Geometry border, Geometry icon,
                 BitmapText label, BitmapText count, Node silhouette,
                 Slot data, SlotKind kind, Inventory.EquipSlot equipSlot,
                 float x, float y) {
            this.root = root;
            this.border = border;
            this.icon = icon;
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

    // ---- interaction ----
    private SlotView selected;
    private SlotView hovered;
    private int denyTimer = 0;
    private SlotView denySlot;
    private int trashArmTimer = 0;
    private static final int TRASH_ARM = 90;

    // ---- tooltip / drag ghost ----
    private final Node tooltipNode = new Node("Tooltip");
    private Geometry tooltipBg;
    private BitmapText tooltipTitle;
    private BitmapText tooltipBody;
    private final Node ghostNode = new Node("DragGhost");
    private Geometry ghostIcon;
    private BitmapText ghostCount;

    // ---- character preview (RTT) ----
    private boolean previewReady = false;
    private Texture2D previewTex;
    private Geometry previewQuad;
    private Node previewRoot;
    private Spatial previewModel;
    private ViewPort previewView;

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

        this.sx = screenW / 1920f;
        this.sy = screenH / 1080f;

        font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        layout();
        buildOverlay(screenW, screenH);
        buildPanel();
        buildGridAndToolbar();
        buildRightBlock();
        buildPreview(cam, playerModel);
        buildPreviewFrame();       // border outline drawn ON TOP of the render
        buildTooltip();
        buildGhost();

        setVisible(false);
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
        refreshIdentityBars();
        refreshSoulDust();
        refreshStats();
        refreshSlots();
    }

    // ================= layout =================

    private void layout() {
        pw = 1728f * sx;
        ph = 918f * sy;
        px = (1920f * sx - pw) / 2f;
        py = (1080f * sy - ph) / 2f;
        panelTop = py + ph;
        panelRight = px + pw;

        slot = 54f * sx;
        gap = 14f * sx;
        eqSlot = 48f * sx;
        eqGap = 10f * sx;
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
    }

    // ================= left column =================

    private void buildGridAndToolbar() {
        float gridLabelY = panelTop - 0.05f * ph;
        addText(hudNode, "Inventory", px + 0.03f * pw, gridLabelY, 18f * sy,
                new ColorRGBA(0.72f, 0.78f, 0.82f, 1f));
        float gridLeft = px + 0.03f * pw;
        float gridTopY = gridLabelY - 30f * sy;
        float gridBottomY = gridTopY - (5 * slot + 4 * gap);
        buildGrid(gridLeft, gridBottomY);

        float toolbarLabelY = gridBottomY - 32f * sy;
        addText(hudNode, "Toolbar", gridLeft, toolbarLabelY, 18f * sy,
                new ColorRGBA(0.72f, 0.78f, 0.82f, 1f));
        float toolbarY = toolbarLabelY - 30f * sy;
        buildToolbar(gridLeft, toolbarY);
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

    // ================= cohesive right block =================

    private void buildRightBlock() {
        // Preview window: 25% of panel width x 60% of panel height.
        previewW = 0.25f * pw;
        previewH = 0.60f * ph;

        // Stats bar sits below the preview.
        float statsY = py + 34f * sy;
        float statsH = 96f * sy;

        // Preview is placed just above the stats bar.
        previewBottom = statsY + statsH + 18f * sy;
        float previewTop = previewBottom + previewH;

        // Anchor the right block to the grid's right edge (Bug 4), not the panel's,
        // so the two halves sit close together with no dead space in between.
        float gridLeft = px + 0.03f * pw;
        float gridRight = gridLeft + (Inventory.GRID_COLS * slot + (Inventory.GRID_COLS - 1) * gap);
        float fixedSpacing = 48f * sx;
        playerBlockLeft = gridRight + fixedSpacing;

        float colX = playerBlockLeft + previewW + 14f * sx; // equipment column right of preview
        previewLeft = playerBlockLeft;

        // Identity block above the preview.
        identityX = previewLeft;
        barX = identityX;

        barThick = Math.max(1.5f, 4f * sy);
        // Name text draws downward from its top-left anchor, so the bar baseline uses
        // the measured line height instead of a flat guess.
        float idLineH = 26f * sy;
        hpBottomY = previewTop + 34f * sy - idLineH - 6f * sy;
        xpBottomY = hpBottomY - (barThick + 5f * sy);

        // Build both track and fill meshes at width 1f so local scale == real pixel width.
        // Track color must be clearly distinct from the panel background (0.08,0.09,0.11)
        // so the empty-state bar is visible even at fraction 0.
        ColorRGBA trackCol = new ColorRGBA(0.16f, 0.17f, 0.19f, 0.95f);
        hpTrack = quad(barX, hpBottomY, 1f, barThick, trackCol);
        hudNode.attachChild(hpTrack);
        hpFill = quad(barX, hpBottomY, 1f, barThick, new ColorRGBA(0.30f, 0.70f, 0.28f, 1f));
        hudNode.attachChild(hpFill);

        xpTrack = quad(barX, xpBottomY, 1f, barThick, trackCol);
        hudNode.attachChild(xpTrack);
        xpFill = quad(barX, xpBottomY, 1f, barThick, new ColorRGBA(0.28f, 0.48f, 0.92f, 1f));
        hudNode.attachChild(xpFill);

        // Identity text attached AFTER the bars so it draws on top (Bug 2).
        identityText = addText(hudNode, "00 | Player", identityX, previewTop + 34f * sy, idLineH,
                new ColorRGBA(0.94f, 0.96f, 0.98f, 1f));

        // Soul Dust grouped beside the name (to the right of the identity block).
        float idBaseline = previewTop + 34f * sy;
        soulIconX = identityX + identityText.getLineWidth() + 24f * sx;
        soulIconY = idBaseline - idLineH - 30f * sy;
        quadAttach(soulIconX, soulIconY, 22f * sx, 22f * sy, new ColorRGBA(0.55f, 0.80f, 0.95f, 1f));
        addText(hudNode, "SOUL DUST", soulIconX + 30f * sx, soulIconY + 12f * sy, 12f * sy,
                new ColorRGBA(0.62f, 0.70f, 0.74f, 1f));
        soulText = addText(hudNode, "0", soulIconX + 30f * sx, soulIconY - 22f * sy, 22f * sy,
                new ColorRGBA(0.86f, 0.95f, 1f, 1f));

        // Equipment column + trash (right of the preview).
        for (int i = 0; i < EQUIP_VISUAL.length; i++) {
            float top = previewTop - i * (eqSlot + eqGap);
            buildEquipSlot(i, colX, top - eqSlot, EQUIP_VISUAL[i]);
        }
        float trashTop = (previewTop - (EQUIP_VISUAL.length - 1) * (eqSlot + eqGap) - eqSlot) - 20f * sx;
        buildTrash(colX, trashTop - eqSlot);

        buildStatsBar(statsY, statsH);
    }

    private void buildEquipSlot(int visualIndex, float x, float y, Inventory.EquipSlot slotType) {
        SlotView v = createSlot(x, y, inventory.getEquipSlot(slotType),
                SlotKind.EQUIPMENT, slotType, 0.22f, 0.24f, 0.26f);
        equipViews[visualIndex] = v;
    }

    private void buildTrash(float x, float y) {
        trashView = createSlot(x, y, new Slot(null, 0), SlotKind.TRASH, null,
                0.34f, 0.16f, 0.16f);
    }

    // ================= stats bar =================

    private void buildStatsBar(float y, float h) {
        float statsW = previewW;
        float statsX = previewLeft;

        quad(statsX, y, statsW, h, new ColorRGBA(0.07f, 0.08f, 0.10f, 0.9f));
        float t = Math.max(1f, 2f * sx);
        borderStroke(statsX, y, statsW, h, t, new ColorRGBA(0.85f, 0.70f, 0.32f, 1f));

        String[] icons = { "DMG", "ARM", "MSPD", "ATK SPD" };
        float segW = statsW / 4f;
        for (int i = 0; i < 4; i++) {
            float segX = statsX + i * segW;
            // centered icon label near the top of the segment
            BitmapText icon = addText(hudNode, icons[i], 0, 0, 15f * sy,
                    new ColorRGBA(0.88f, 0.74f, 0.38f, 1f));
            icon.setLocalTranslation(segX + segW / 2f - icon.getLineWidth() / 2f, y + h - 20f * sy, 0f);
            // centered numeric value
            statValues[i] = addText(hudNode, "0", 0, 0, 28f * sy, ColorRGBA.White);
            statValues[i].setLocalTranslation(segX + segW / 2f - statValues[i].getLineWidth() / 2f,
                    y + 14f * sy, 0f);
            if (i < 3) {
                quad(segX + segW, y + 6f * sy, Math.max(1f, sx), h - 12f * sy,
                        new ColorRGBA(0.35f, 0.30f, 0.20f, 0.8f));
            }
        }
    }

    // ================= slot creation =================

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

        return new SlotView(root, border, icon, label, count, silhouette, data, kind, equipSlot, x, y);
    }

    // ================= equipment / trash silhouette icons =================

    /** Builds a faint per-type silhouette. Returns null for plain grid/toolbar slots. */
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

    // ================= character preview (RTT) =================

    private void buildPreview(Camera cam, Spatial playerModel) {
        int tpw = (int) Math.max(32, previewW);
        int tph = (int) Math.max(32, previewH);

        previewTex = new Texture2D(tpw, tph, Image.Format.RGBA8);
        previewTex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        previewTex.setMagFilter(Texture.MagFilter.Bilinear);

        FrameBuffer fb = new FrameBuffer(tpw, tph, 1);
        fb.setDepthBuffer(Image.Format.Depth);
        fb.setColorTexture(previewTex);

        previewRoot = new Node("InventoryPreviewScene");
        if (playerModel != null) {
            previewModel = playerModel.clone();
            previewModel.rotate(0f, (float) StrictMath.PI, 0f);
            previewRoot.attachChild(previewModel);
        }

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.4f, -1f, -0.4f).normalizeLocal());
        sun.setColor(ColorRGBA.White);
        previewRoot.addLight(sun);
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(1.1f));
        previewRoot.addLight(ambient);

        Camera offCam = cam.clone();
        offCam.setFrustumPerspective(45f, (float) tpw / tph, 0.1f, 100f);
        offCam.setLocation(new Vector3f(0, 1.15f, 2.8f));
        offCam.lookAt(new Vector3f(0, 1f, 0), Vector3f.UNIT_Y);

        ViewPort off = renderManager.createMainView("inventoryPreview", offCam);
        off.setClearFlags(true, true, true);
        // Clear to the same color as the panel interior so we don't get a black box.
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

    /** Draws the 2px border-only outline of the preview window, on top of the render. */
    private void buildPreviewFrame() {
        float t = Math.max(1f, 2f * sx);
        borderStroke(previewLeft - 2f * sx, previewBottom - 2f * sy,
                previewW + 4f * sx, previewH + 4f * sy, t,
                new ColorRGBA(0.36f, 0.40f, 0.42f, 1f));
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
        ghostCount = new BitmapText(font);
        ghostCount.setSize(15f * sy);
        ghostCount.setColor(new ColorRGBA(0.98f, 0.88f, 0.6f, 1f));
        ghostNode.attachChild(ghostCount);
        ghostNode.setCullHint(Spatial.CullHint.Always);
    }

    private void updatePointer() {
        Vector2f cur = inputManager.getCursorPosition();
        hovered = slotAt(cur.x, cur.y);

        float gs = slot - 4f;
        if (selected != null && !selected.data.isEmpty()) {
            Item item = selected.data.getItem();
            ghostNode.setCullHint(Spatial.CullHint.Never);
            ghostNode.setLocalTranslation(cur.x - gs / 2f, cur.y - gs / 2f + 12f, 0f);
            if (item != null) {
                ghostIcon.getMaterial().setColor("Color",
                        new ColorRGBA(item.iconColor.r, item.iconColor.g, item.iconColor.b, 0.85f));
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
            tooltipBody.setText(categoryLabel(item.category)
                    + (item.maxStack > 1 ? "  |  x" + hovered.data.count : ""));
            float tw = Math.max(tooltipTitle.getLineWidth(), tooltipBody.getLineWidth()) + 18f;
            float th = 44f * sy;
            tooltipNode.setLocalTranslation(cur.x + 14f, cur.y + 6f, 0f);
            tooltipBg.setLocalScale(tw, th, 1f);
            tooltipTitle.setLocalTranslation(5f, th - 18f * sy, 0f);
            tooltipBody.setLocalTranslation(5f, th - 36f * sy, 0f);
            tooltipNode.setCullHint(Spatial.CullHint.Never);
        } else {
            tooltipNode.setCullHint(Spatial.CullHint.Always);
        }
    }

    private String categoryLabel(Item.Category c) {
        String s = c.toString();
        return s.substring(0, 1) + s.substring(1).toLowerCase();
    }

    // ================= interaction =================

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
                    item.category == Item.Category.WEAPON
                            || item.category == Item.Category.POTION
                            || item.category == Item.Category.KEY;
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

    // ================= refresh =================

    private void refreshIdentityBars() {
        identityText.setText(String.format("%02d | %s", stats.getLevel(), stats.getPlayerName()));
        float w = Math.max(24f, identityText.getLineWidth());
        if (!xpFractionLogged) {
            xpFractionLogged = true;
            System.out.println("[InventoryUI] XP fraction at open: "
                    + stats.getExperienceFraction() + " (xp=" + stats.getExperience()
                    + " / toNext=" + stats.getExperienceToNext() + ")");
        }
        setBar(hpTrack, hpFill, stats.getHealthFraction(), w);
        setBar(xpTrack, xpFill, stats.getExperienceFraction(), w);
    }

    private void setBar(Geometry track, Geometry fill, float frac, float w) {
        track.setLocalScale(w, 1f, 1f);
        fill.setLocalScale(w * Math.max(0f, Math.min(1f, frac)), 1f, 1f);
    }

    private void refreshSoulDust() {
        soulText.setText("" + stats.getSoulDust());
    }

    private void refreshStats() {
        statValues[0].setText(String.format("%.0f", stats.getAverageDamage()));
        statValues[1].setText(String.format("%.0f", stats.getArmorPoints()));
        statValues[2].setText(String.format("%.1f", stats.getMovementSpeed()));
        statValues[3].setText(String.format("%.1f", stats.getAttackSpeed()));
        float segW = previewW / 4f;
        for (int i = 0; i < 4; i++) {
            statValues[i].setLocalTranslation(previewLeft + i * segW + segW / 2f
                    - statValues[i].getLineWidth() / 2f, statValues[i].getLocalTranslation().y, 0f);
        }
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
            // Equipment slots always show their fixed type silhouette. Equipping an item
            // tints the silhouette with that item's color instead of swapping to a letter.
            v.icon.setCullHint(Spatial.CullHint.Always);
            v.label.setText("");
            v.count.setText("");
            if (v.silhouette != null) {
                v.silhouette.setCullHint(Spatial.CullHint.Never);
                if (s.isEmpty()) {
                    tintSilhouette(v.silhouette, DEFAULT_EQUIP_SIL_COLOR);
                } else if (s.getItem() != null) {
                    tintSilhouette(v.silhouette, s.getItem().iconColor);
                }
            }
            applyBorderState(v, isSel, isHov, isDeny);
            return;
        }

        if (s.isEmpty()) {
            v.icon.setCullHint(Spatial.CullHint.Always);
            if (v.silhouette != null) {
                v.silhouette.setCullHint(Spatial.CullHint.Never);
            }
            v.label.setText("");
            v.count.setText("");
        } else {
            Item item = s.getItem();
            v.icon.setCullHint(Spatial.CullHint.Never);
            if (v.silhouette != null) {
                v.silhouette.setCullHint(Spatial.CullHint.Always);
            }
            if (item != null) {
                v.icon.getMaterial().setColor("Color",
                        new ColorRGBA(item.iconColor.r, item.iconColor.g, item.iconColor.b, 1f));
                v.label.setText(item.name.substring(0, 1));
                // BitmapText anchors top-left and draws downward; center the letter by
                // offsetting up by half its line height (Bug 3).
                float lh = v.label.getLineHeight();
                v.label.setLocalTranslation(
                        v.x + slot / 2f - v.label.getLineWidth() / 2f,
                        v.y + slot / 2f + lh / 2f,
                        0f);
                // Count pinned to the bottom-right corner, using its own line height.
                v.count.setText(s.count > 1 ? "" + s.count : "");
                float ch = v.count.getLineHeight();
                v.count.setLocalTranslation(v.x + slot - 3f * sx - v.count.getLineWidth(),
                        v.y + ch / 2f + 2f * sy, 0f);
            }
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

    /** Tints every Geometry under the silhouette node (recursively). */
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
        // trash-can silhouette always visible
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

    // ================= generic builders =================

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
