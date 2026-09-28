package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Full-screen death/game-over overlay: blood-red haze, a "Cursed One Defeated"
 * title, and RESPAWN / MAIN MENU buttons in the game's flat dark-panel HUD style.
 * Built once and toggled via {@link #setVisible(boolean)}.
 */
public class DeathScreen {

    private final AssetManager assetManager;
    private final InputManager inputManager;
    private final int screenW;
    private final int screenH;
    private final float sx;
    private final float sy;

    private final Node node = new Node("DeathScreen");

    private Runnable respawnAction;
    private Runnable mainMenuAction;

    private static class DeathButton {
        final float x, y, w, h;
        final Material borderMat;
        final Runnable action;

        DeathButton(float x, float y, float w, float h, Material borderMat, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.borderMat = borderMat;
            this.action = action;
        }

        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    private final List<DeathButton> buttons = new ArrayList<>();

    private static final ColorRGBA BORDER_IDLE = new ColorRGBA(0.40f, 0.42f, 0.45f, 1f);
    private static final ColorRGBA BORDER_HOVER = new ColorRGBA(0.88f, 0.34f, 0.30f, 1f);

    public DeathScreen(AssetManager assetManager, InputManager inputManager, int screenW, int screenH) {
        this.assetManager = assetManager;
        this.inputManager = inputManager;
        this.screenW = screenW;
        this.screenH = screenH;
        this.sx = screenW / 1920f;
        this.sy = screenH / 1080f;

        buildOverlay(screenW, screenH);
        buildTitle();
        buildButtons();
    }

    public Node getNode() {
        return node;
    }

    public void setVisible(boolean visible) {
        node.setCullHint(visible ? Spatial.CullHint.Never : Spatial.CullHint.Always);
    }

    public void setRespawnAction(Runnable respawnAction) {
        this.respawnAction = respawnAction;
    }

    public void setMainMenuAction(Runnable mainMenuAction) {
        this.mainMenuAction = mainMenuAction;
    }

    public void update(float tpf) {
        Vector2f cur = inputManager.getCursorPosition();
        for (DeathButton b : buttons) {
            b.borderMat.setColor("Color", b.contains(cur.x, cur.y) ? BORDER_HOVER : BORDER_IDLE);
        }
    }

    /** Routes a left-click to whichever button it landed on. Returns true if consumed. */
    public boolean handleClick(float px, float py) {
        for (DeathButton b : buttons) {
            if (b.contains(px, py)) {
                if (b.action != null) b.action.run();
                return true;
            }
        }
        return false;
    }

    private void buildOverlay(int W, int H) {
        quad(0, 0, W, H, new ColorRGBA(0.30f, 0.03f, 0.05f, 0.30f));

        Geometry blood = new Geometry("DeathBlood", new Quad(W, H));
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        Texture2D tex = buildBloodTexture();
        mat.setTexture("ColorMap", tex);
        mat.setColor("Color", ColorRGBA.White);
        // explicit alpha blend so the texture's smeared edges stay see-through
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mat.getAdditionalRenderState().setDepthWrite(false);
        blood.setMaterial(mat);
        node.attachChild(blood);
    }

    private void buildTitle() {
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        BitmapText title = new BitmapText(font, false);
        title.setText("Cursed One Defeated");
        title.setSize(92f * sy);
        title.setColor(new ColorRGBA(0.94f, 0.89f, 0.82f, 1f));
        float tx = (screenW - title.getLineWidth()) / 2f;
        float ty = screenH * 0.56f;
        title.setLocalTranslation(tx, ty, 0f);
        node.attachChild(title);

        // gothic offset shadow gives the flat bitmap font its "bold" weight
        BitmapText shadow = new BitmapText(font, false);
        shadow.setText("Cursed One Defeated");
        shadow.setSize(92f * sy);
        shadow.setColor(new ColorRGBA(0.30f, 0.03f, 0.04f, 0.9f));
        shadow.setLocalTranslation(tx - 4f * sx, ty - 5f * sy, 0f);
        node.attachChild(shadow);

        float lineW = 460f * sx;
        quad((screenW - lineW) / 2f, ty - 130f * sy, lineW, Math.max(2f, 3f * sx),
                new ColorRGBA(0.45f, 0.07f, 0.09f, 0.9f));
    }

    private void buildButtons() {
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        float btnW = 300f * sx;
        float btnH = 58f * sy;
        float gap = 28f * sx;
        float pairW = 2f * btnW + gap;
        float bx = (screenW - pairW) / 2f;
        float by = screenH * 0.30f;

        addButton(font, "RESPAWN", bx, by, btnW, btnH,
                () -> { if (respawnAction != null) respawnAction.run(); });
        addButton(font, "MAIN MENU", bx + btnW + gap, by, btnW, btnH,
                () -> { if (mainMenuAction != null) mainMenuAction.run(); });
    }

    private void addButton(BitmapFont font, String label, float x, float y, float w, float h, Runnable action) {
        quad(x, y, w, h, new ColorRGBA(0.10f, 0.09f, 0.12f, 0.92f));

        float t = Math.max(2f, 2.5f * sx);
        Material borderMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        borderMat.setColor("Color", BORDER_IDLE);
        quad(x, y, w, t, borderMat);
        quad(x, y + h - t, w, t, borderMat);
        quad(x, y, t, h, borderMat);
        quad(x + w - t, y, t, h, borderMat);

        BitmapText labelText = new BitmapText(font, false);
        labelText.setText(label);
        labelText.setSize(26f * sy);
        labelText.setColor(new ColorRGBA(0.96f, 0.95f, 0.93f, 1f));
        labelText.setLocalTranslation(x + w / 2f - labelText.getLineWidth() / 2f,
                y + h / 2f + labelText.getLineHeight() / 2f, 0f);
        node.attachChild(labelText);

        buttons.add(new DeathButton(x, y, w, h, borderMat, action));
    }

    /**
     * Procedural blood smear: a dark-red radial vignette (see-through in the
     * middle), blotches biased toward the frame, and drips from the top.
     */
    private Texture2D buildBloodTexture() {
        int size = 1024;
        float c = size / 2f;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // center stays nearly clear so the frozen game world peeks through
        RadialGradientPaint vignette = new RadialGradientPaint(
                c, c, c * 0.9f + 1f,
                new float[]{0.0f, 0.55f, 1.0f},
                new Color[]{new Color(95, 10, 14, 0),
                        new Color(115, 12, 18, 70),
                        new Color(70, 5, 8, 180)});
        g.setPaint(vignette);
        g.fillRect(0, 0, size, size);

        Random rand = new Random(20260928L);

        // random blotches: bigger, denser ones hug the frame edges
        for (int i = 0; i < 320; i++) {
            float x = rand.nextFloat() * size;
            float y = rand.nextFloat() * size;
            float d = (float) StrictMath.hypot(x - c, y - c) / (c * 1.4f); // 0 center, 1 corner
            float r = 5f + 70f * d + rand.nextFloat() * 45f * d;
            int alpha = 24 + (int) (55f * d) + rand.nextInt(35);
            g.setColor(new Color(115 + rand.nextInt(55), 16, 20, Math.min(255, alpha)));
            g.fillOval((int) (x - r), (int) (y - r), (int) (2 * r), (int) (2 * r));
        }

        for (int i = 0; i < 42; i++) {
            float x = rand.nextFloat() * size;
            float top = rand.nextFloat() * size * 0.18f;
            float len = size * (0.12f + rand.nextFloat() * 0.38f);
            int w = 3 + rand.nextInt(8);
            g.setColor(new Color(125, 16, 20, 42 + rand.nextInt(60)));
            g.fillRoundRect((int) x, (int) top, w, (int) len, w, w);
        }

        for (int side = 0; side < 2; side++) {
            float cx = side == 0 ? c * 0.25f : size - c * 0.25f;
            float cy = size - c * 0.20f;
            g.setColor(new Color(90, 8, 12, 90));
            g.fillOval((int) (cx - c * 0.22f), (int) (cy - c * 0.10f),
                    (int) (c * 0.44f), (int) (c * 0.20f));
            g.fillOval((int) (cx + c * 0.05f), (int) (cy - c * 0.02f),
                    (int) (c * 0.24f), (int) (c * 0.12f));
        }

        g.dispose();

        AWTLoader loader = new AWTLoader();
        Texture2D tex = new Texture2D(loader.load(img, false));
        tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        tex.setMagFilter(Texture.MagFilter.Bilinear);
        tex.setWrap(Texture.WrapMode.Clamp);
        return tex;
    }

    private void quad(float x, float y, float w, float h, ColorRGBA color) {
        Geometry g = new Geometry("DeathQuad", new Quad(w, h));
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, 0);
        node.attachChild(g);
    }

    private void quad(float x, float y, float w, float h, Material mat) {
        Geometry g = new Geometry("DeathQuad", new Quad(w, h));
        g.setMaterial(mat);
        g.setLocalTranslation(x, y, 0);
        node.attachChild(g);
    }
}