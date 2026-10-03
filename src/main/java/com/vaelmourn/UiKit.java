package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw-jME port of the supplied {@code game.ui} design (Theme + UiKit).
 *
 * <p>Everything is expressed in the concept's 1920x1080 reference space with the origin
 * at the TOP-LEFT and y growing downward. {@link #X}/{@link #Y} convert to jME gui space
 * when placing nodes, and {@link Button#hit} converts cursor pixels back, so screens
 * never deal with the flip.</p>
 *
 * <p>{@link #box} returns the INNER rectangle, exactly like the concept's
 * {@code UiKit.box} returns the inner Lemur container. Children are then laid out in
 * even bands (vertical) and cells (horizontal) with {@link #bands} / {@link #cells},
 * which reproduces the concept's even {@code SpringGridLayout}.</p>
 */
public final class UiKit {

    private UiKit() {}

    public static final float REF_W = 1920f;
    public static final float REF_H = 1080f;

    private static float sx = 1f, sy = 1f;
    private static float screenW = REF_W, screenH = REF_H;

    /** Call once from the app with the real viewport size. */
    public static void init(int width, int height) {
        screenW = width;
        screenH = height;
        sx = width / REF_W;
        sy = height / REF_H;
    }

    public static float scaleX() { return sx; }
    public static float scaleY() { return sy; }
    public static float getScreenW() { return screenW; }
    public static float getScreenH() { return screenH; }

    /** reference x -> jME gui x (pixels). */
    public static float X(float refX) { return refX * sx; }
    /** reference y (from the TOP) -> jME gui y (from the BOTTOM, pixels). */
    public static float Y(float refY) { return screenH - refY * sy; }
    public static float w(float refW) { return refW * sx; }
    public static float h(float refH) { return refH * sy; }

    /**
     * Cursor pixels -> reference y from the top.
     *
     * <p>jME's {@code InputManager.getCursorPosition()} supplies y with the origin at the
     * BOTTOM of the window (verified against {@code GlfwMouseInput}). The mockup works
     * top-down, so the flip happens here once for every button.</p>
     */
    public static float toRefY(float pixelY) { return (screenH - pixelY) / sy; }
    public static float toRefX(float pixelX) { return pixelX / sx; }

    public static ColorRGBA hex(int rgb) {
        return new ColorRGBA(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f,
                (rgb & 255) / 255f, 1f);
    }

    // ---- palette, sampled from the concept images ------------------------------

    public static final ColorRGBA BORDER = hex(0x4A4A4A);
    public static final ColorRGBA WHITE = hex(0xFFFFFF);
    public static final ColorRGBA DARK_TEXT = hex(0x111111);
    public static final ColorRGBA VALUE_RED = hex(0xFF0000);

    public static final ColorRGBA POTION_BG = hex(0xC9B873);
    public static final ColorRGBA POTION_CARD = hex(0xE8B03F);
    public static final ColorRGBA POTION_HEADER = hex(0x968C70);
    public static final ColorRGBA BTN_UPGRADE = hex(0xE63A1E);
    public static final ColorRGBA BTN_BUY = hex(0x28A828);

    public static final ColorRGBA UPG_BG = hex(0x86BCD6);
    public static final ColorRGBA UPG_CARD = hex(0x6CC5E0);
    public static final ColorRGBA UPG_HEADER = hex(0x7A8A94);
    public static final ColorRGBA UPG_BTN = hex(0xC0343F);

    public static final ColorRGBA INV_BG = hex(0x7D7777);
    public static final ColorRGBA INV_PANEL = hex(0x5F5757);
    public static final ColorRGBA HP_GREEN = hex(0x44FF22);
    public static final ColorRGBA STAT_RED = hex(0x9B1010);

    public static ColorRGBA lighten(ColorRGBA c, float amount) {
        return new ColorRGBA(Math.min(1f, c.r + amount), Math.min(1f, c.g + amount),
                Math.min(1f, c.b + amount), c.a);
    }

    // ---- geometry helpers ------------------------------------------------------

    /** A rectangle in reference space, top-left origin. */
    public static final class Rect {
        public final float x, y, w, h;
        public Rect(float x, float y, float w, float h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public float cx() { return x + w / 2f; }
        public float cy() { return y + h / 2f; }
    }

    /** Splits a rect into {@code n} equal vertical bands, top to bottom. */
    public static Rect[] bands(Rect r, int n) {
        Rect[] out = new Rect[n];
        float bh = r.h / n;
        for (int i = 0; i < n; i++) out[i] = new Rect(r.x, r.y + i * bh, r.w, bh);
        return out;
    }

    /** Splits a rect into {@code n} equal horizontal cells, left to right. */
    public static Rect[] cells(Rect r, int n) {
        Rect[] out = new Rect[n];
        float cw = r.w / n;
        for (int i = 0; i < n; i++) out[i] = new Rect(r.x + i * cw, r.y, cw, r.h);
        return out;
    }

    // ---- drawing ---------------------------------------------------------------

    private static Material unshaded(AssetManager am, ColorRGBA color) {
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        // honour the alpha channel of the colour (semi-transparent tracks, overlays...)
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return m;
    }

    /** A gui quad sized in reference units; origin is the lower-left corner. */
    public static Geometry quad(AssetManager am, float refW, float refH, ColorRGBA color) {
        Quad q = new Quad(Math.max(1f, w(refW)), Math.max(1f, h(refH)));
        Geometry g = new Geometry("quad", q);
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        g.setMaterial(unshaded(am, color));
        return g;
    }

    public static Geometry background(AssetManager am, Node parent, ColorRGBA color) {
        Geometry g = new Geometry("bg", new Quad(screenW, screenH));
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        g.setMaterial(unshaded(am, color));
        g.setLocalTranslation(0f, 0f, 0f);
        g.setCullHint(Spatial.CullHint.Never);
        parent.attachChild(g);
        return g;
    }

    /** Draws a bordered panel and returns its INNER rect (fill area). */
    public static Rect box(AssetManager am, Node parent, float x, float y, float w, float h,
                           ColorRGBA fill, ColorRGBA border, float borderPx) {
        if (border != null) {
            Geometry bg = quad(am, w, h, border);
            bg.setLocalTranslation(X(x), Y(y + h), 0f);
            parent.attachChild(bg);
        }
        float b = border != null ? borderPx : 0f;
        if (fill != null) {
            Geometry inner = quad(am, w - b * 2f, h - b * 2f, fill);
            inner.setLocalTranslation(X(x + b), Y(y + h - b), 0f);
            parent.attachChild(inner);
        }
        return new Rect(x + b, y + b, w - b * 2f, h - b * 2f);
    }

    // NOTE on text placement: a BitmapText's local translation is the TOP-left of the
    // text and the text extends DOWNWARD from it. To centre it vertically in a rect the
    // top edge must therefore sit at (rectCentre + textHeight / 2).

    /** Centred label inside a rect (both axes). */
    public static BitmapText textIn(BitmapFont font, Node parent, String str,
                                    Rect r, float size, ColorRGBA color) {
        return textIn(font, parent, str, r.x, r.y, r.w, r.h, size, color);
    }

    public static BitmapText textIn(BitmapFont font, Node parent, String str,
                                    float x, float y, float w, float h,
                                    float size, ColorRGBA color) {
        BitmapText t = new BitmapText(font);
        t.setSize(Math.max(1f, h(size)));
        t.setColor(color);
        t.setText(str == null ? "" : str);
        t.setLocalTranslation(X(x) + w(w) / 2f - t.getLineWidth() / 2f,
                Y(y + h) + h(h) / 2f + t.getHeight() / 2f, 0f);
        parent.attachChild(t);
        return t;
    }

    /** Left-aligned label vertically centred inside a rect. */
    public static BitmapText textLeft(BitmapFont font, Node parent, String str,
                                      Rect r, float size, ColorRGBA color) {
        BitmapText t = new BitmapText(font);
        t.setSize(Math.max(1f, h(size)));
        t.setColor(color);
        t.setText(str == null ? "" : str);
        t.setLocalTranslation(X(r.x), Y(r.y + r.h) + h(r.h) / 2f + t.getHeight() / 2f, 0f);
        parent.attachChild(t);
        return t;
    }

    /** Right-aligned label vertically centred inside a rect. */
    public static BitmapText textRight(BitmapFont font, Node parent, String str,
                                       Rect r, float size, ColorRGBA color) {
        BitmapText t = new BitmapText(font);
        t.setSize(Math.max(1f, h(size)));
        t.setColor(color);
        t.setText(str == null ? "" : str);
        t.setLocalTranslation(X(r.x + r.w) - t.getLineWidth(),
                Y(r.y + r.h) + h(r.h) / 2f + t.getHeight() / 2f, 0f);
        parent.attachChild(t);
        return t;
    }

    // ---- buttons ---------------------------------------------------------------

    public static final class Button {
        final String label;
        final float x, y, w, h;
        final ColorRGBA color;
        private final Runnable action;
        private final Geometry bg;          // null for text-only buttons
        private final BitmapText caption;
        private boolean hovered;
        private boolean enabled = true;

        Button(String label, float x, float y, float w, float h, ColorRGBA color,
               Runnable action, Geometry bg, BitmapText caption) {
            this.label = label; this.x = x; this.y = y; this.w = w; this.h = h;
            this.color = color; this.action = action; this.bg = bg; this.caption = caption;
        }

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean on) {
            this.enabled = on;
            if (bg != null) bg.getMaterial().setColor("Color",
                    on ? (hovered ? lighten(color, 0.12f) : color) : dim(color));
            if (caption != null) {
                if (bg != null) {
                    caption.setColor(on ? WHITE : dim(WHITE));
                } else {
                    // text-only button: the caption itself carries the button colour
                    caption.setColor(on ? (hovered ? WHITE : color) : dim(color));
                }
            }
        }

        private static ColorRGBA dim(ColorRGBA c) {
            return new ColorRGBA(c.r * 0.5f, c.g * 0.5f, c.b * 0.5f, c.a);
        }

        boolean hit(float px, float py) {
            float rx = toRefX(px), ry = toRefY(py);
            return rx >= x && rx <= x + w && ry >= y && ry <= y + h;
        }

        void setHovered(boolean on) {
            if (this.hovered == on) return;
            this.hovered = on;
            if (bg != null) bg.getMaterial().setColor("Color",
                    enabled ? (on ? lighten(color, 0.12f) : color)
                            : (on ? lighten(dim(color), 0.12f) : dim(color)));
            // text-only button: brighten the caption on hover
            if (bg == null && caption != null && enabled) {
                caption.setColor(on ? WHITE : color);
            }
        }

        boolean click() {
            if (!enabled || action == null) return false;
            action.run();
            return true;
        }
    }

    public static Button button(AssetManager am, BitmapFont font, Node parent, List<Button> out,
                                String label, Rect r, float size, ColorRGBA color, Runnable action) {
        Geometry bg = quad(am, r.w, r.h, color);
        bg.setLocalTranslation(X(r.x), Y(r.y + r.h), 0f);
        parent.attachChild(bg);
        BitmapText caption = textIn(font, parent, label, r, size, WHITE);
        Button b = new Button(label, r.x, r.y, r.w, r.h, color, action, bg, caption);
        out.add(b);
        return b;
    }

    /**
     * Text-only button: no background rectangle, just a caption in {@code color}
     * that turns white on hover. The whole rect is the click area.
     */
    public static Button textButton(BitmapFont font, Node parent, List<Button> out,
                                    String label, Rect r, float size,
                                    ColorRGBA color, Runnable action) {
        BitmapText caption = textIn(font, parent, label, r, size, color);
        Button b = new Button(label, r.x, r.y, r.w, r.h, color, action, null, caption);
        out.add(b);
        return b;
    }

    /** Update hover highlight. Topmost (last added) match wins. */
    public static void hover(List<Button> buttons, float px, float py) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            boolean over = b.hit(px, py);
            b.setHovered(over);
            if (over) return;
        }
    }

    public static boolean click(List<Button> buttons, float px, float py) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            if (b.hit(px, py)) {
                b.click();
                // Consume the click even when the button is disabled, otherwise the
                // caller's "missed every button" fallback fires and buys a different card.
                return true;
            }
        }
        return false;
    }

    public static List<Button> newButtonList() { return new ArrayList<>(); }
}