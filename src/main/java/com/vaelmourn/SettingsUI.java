package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;

import java.util.List;
import java.util.function.Consumer;

/**
 * Settings screen for display, audio and controls.
 *
 * Artwork: GUI/settings_bg.png (1920x1080).
 *
 * Everything here is in UiKit reference space: 1920x1080, origin TOP-LEFT,
 * y grows downward. The numbers are read straight off the mockup, and UiKit
 * does all scaling / flipping. UiKit.init(width, height) must have been
 * called before build().
 *
 * Requires the small UiKit patch (alpha blending, text vertical centering,
 * textButton) that came with this file.
 */
final class SettingsUI {

    // ---- colours ------------------------------------------------------------
    private static final ColorRGBA TEXT     = new ColorRGBA(0.96f, 0.96f, 0.96f, 1f);
    private static final ColorRGBA INACTIVE = new ColorRGBA(0.74f, 0.74f, 0.74f, 1f);
    private static final ColorRGBA SIDEBAR  = new ColorRGBA(0.62f, 0.62f, 0.62f, 1f);
    private static final ColorRGBA MUTED    = new ColorRGBA(0.80f, 0.80f, 0.80f, 1f);
    private static final ColorRGBA TRACK    = new ColorRGBA(0.45f, 0.45f, 0.45f, 0.55f);
    private static final ColorRGBA FILL     = new ColorRGBA(0.97f, 0.97f, 0.97f, 1f);
    private static final ColorRGBA ACCENT   = new ColorRGBA(0.85f, 0.85f, 0.85f, 0.9f);

    // ---- font sizes (reference px) ------------------------------------------
    private static final float SIZE_SIDEBAR = 44f;
    private static final float SIZE_OPTION  = 46f;
    private static final float SIZE_LABEL   = 46f;
    private static final float SIZE_ROW     = 34f;

    // ---- layout (reference px, top-left origin) -----------------------------
    // Left sidebar, centred on x = 305
    private static final float SIDE_X = 155f, SIDE_W = 300f, SIDE_H = 70f;
    private static final float DISPLAY_Y  = 177f;   // centre y
    private static final float AUDIO_Y    = 307f;
    private static final float CONTROLS_Y = 446f;
    private static final float EXIT_Y     = 586f;

    // Display options
    private static final float OPT_Y = 177f;        // centre y
    private static final float OPT_H = 70f;
    private static final float WINDOWED_X = 610f,    WINDOWED_W = 300f;
    private static final float FULLSCREEN_X = 1130f, FULLSCREEN_W = 340f;

    // Volume
    private static final float VOL_LABEL_X = 785f, VOL_LABEL_W = 190f;
    private static final float VOL_Y = 316f;        // centre y
    private static final float TRACK_X = 990f, TRACK_W = 200f, TRACK_H = 20f;
    private static final float KNOB = 26f;

    // Controls list
    private static final float KEY_X = 785f,     KEY_W = 300f;
    private static final float ACTION_X = 1100f, ACTION_W = 460f;
    private static final float ROW_START_Y = 436f;  // centre of first row
    private static final float ROW_H = 38f;

    private final AssetManager am;
    private final BitmapFont font;

    private final Runnable onBack;
    private final Consumer<Boolean> displayChange;
    private final Consumer<Float> volumeChange;

    private boolean fullscreen;
    private float volume;

    private UiKit.Rect sliderTrack;
    private Geometry fillGeo;
    private Geometry knobGeo;
    private boolean dragging;

    SettingsUI(
            AssetManager am,
            BitmapFont font,
            boolean fullscreen,
            float volume,
            Runnable onBack,
            Consumer<Boolean> displayChange,
            Consumer<Float> volumeChange
    ) {
        this.am = am;
        this.font = font;
        this.fullscreen = fullscreen;
        this.volume = clamp01(volume);
        this.onBack = onBack;
        this.displayChange = displayChange;
        this.volumeChange = volumeChange;
    }

    void build(Node root, List<UiKit.Button> buttons) {
        sliderTrack = null;
        fillGeo = null;
        knobGeo = null;
        dragging = false;

        addBackground(root);

        // ---- left sidebar ----
        sideLabel(root, "Display", DISPLAY_Y);
        sideLabel(root, "Audio", AUDIO_Y);
        sideLabel(root, "Controls", CONTROLS_Y);

        UiKit.textButton(font, root, buttons, "EXIT",
                new UiKit.Rect(SIDE_X + 50f, EXIT_Y - 35f, SIDE_W - 100f, 70f),
                SIZE_SIDEBAR, SIDEBAR, onBack);

        // ---- display ----
        optionButton(root, buttons, "Windowed",
                WINDOWED_X, WINDOWED_W, !fullscreen, () -> chooseFullscreen(false));
        optionButton(root, buttons, "Fullscreen",
                FULLSCREEN_X, FULLSCREEN_W, fullscreen, () -> chooseFullscreen(true));

        // ---- audio ----
        UiKit.textLeft(font, root, "Volume",
                new UiKit.Rect(VOL_LABEL_X, VOL_Y - 30f, VOL_LABEL_W, 60f),
                SIZE_LABEL, TEXT);
        buildVolumeSlider(root);

        // ---- controls ----
        buildControls(root);
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    /**
     * Mouse PRESSED at px/py (raw jME cursor pixels). Returns true if the press
     * landed on the slider; it then starts a drag and sets the volume.
     */
    boolean handleClick(float px, float py) {
        if (sliderTrack == null) {
            return false;
        }

        float rx = UiKit.toRefX(px);
        float ry = UiKit.toRefY(py);

        // generous hit area: the bar is thin
        float padX = 14f;
        float padY = 18f;

        if (rx < sliderTrack.x - padX
                || rx > sliderTrack.x + sliderTrack.w + padX
                || ry < sliderTrack.y - padY
                || ry > sliderTrack.y + sliderTrack.h + padY) {
            return false;
        }

        dragging = true;
        setVolumeFromCursor(px);
        return true;
    }

    /** Call every frame / on mouse move while the button is held. */
    void handleDrag(float px, float py) {
        if (dragging) {
            setVolumeFromCursor(px);
        }
    }

    /** Call when the mouse button is RELEASED. */
    void handleRelease() {
        dragging = false;
    }

    boolean isDragging() {
        return dragging;
    }

    private void setVolumeFromCursor(float px) {
        if (sliderTrack == null) {
            return;
        }
        float v = clamp01((UiKit.toRefX(px) - sliderTrack.x) / sliderTrack.w);
        if (v == volume) {
            return;
        }
        volume = v;
        refreshSlider();            // moves the existing geometry, no rebuild needed
        volumeChange.accept(volume);
    }

    /** Updates fill + knob in place so the slider reacts instantly. */
    private void refreshSlider() {
        if (fillGeo == null || knobGeo == null) {
            return;
        }
        fillGeo.setLocalScale(Math.max(volume, 0.0001f), 1f, 1f);
        knobGeo.setLocalTranslation(
                UiKit.X(TRACK_X + TRACK_W * volume - KNOB / 2f),
                UiKit.Y(VOL_Y + KNOB / 2f),
                0f);
    }

    boolean isFullscreen() {
        return fullscreen;
    }

    float getVolume() {
        return volume;
    }

    void setFullscreen(boolean now) {
        fullscreen = now;
    }

    private void chooseFullscreen(boolean want) {
        if (want == fullscreen) {
            return;
        }
        fullscreen = want;
        displayChange.accept(fullscreen);
    }

    // -------------------------------------------------------------------------
    // Background
    // -------------------------------------------------------------------------

    private void addBackground(Node root) {
        // UiKit stretches the 1920x1080 reference space over the whole
        // screen, so the artwork does the same and everything stays aligned.
        Picture background = new Picture("SettingsBackground");
        background.setImage(am, "GUI/settings_bg.png", false);
        background.setWidth(UiKit.getScreenW());
        background.setHeight(UiKit.getScreenH());
        background.setPosition(0f, 0f);
        root.attachChild(background);
    }

    // -------------------------------------------------------------------------
    // Sidebar + display
    // -------------------------------------------------------------------------

    private void sideLabel(Node root, String text, float centerY) {
        UiKit.textIn(font, root, text,
                new UiKit.Rect(SIDE_X, centerY - SIDE_H / 2f, SIDE_W, SIDE_H),
                SIZE_SIDEBAR, SIDEBAR);
    }

    /** Clickable text option; the active one is brighter and underlined. */
    private void optionButton(
            Node root,
            List<UiKit.Button> buttons,
            String label,
            float x,
            float w,
            boolean active,
            Runnable action
    ) {
        UiKit.textButton(font, root, buttons, label,
                new UiKit.Rect(x, OPT_Y - OPT_H / 2f, w, OPT_H),
                SIZE_OPTION, active ? TEXT : INACTIVE, action);

        if (active) {
            float lineW = Math.min(w - 60f, 220f);
            UiKit.box(am, root, x + (w - lineW) / 2f, OPT_Y + 36f,
                    lineW, 3f, ACCENT, null, 0f);
        }
    }

    // -------------------------------------------------------------------------
    // Volume
    // -------------------------------------------------------------------------

    private void buildVolumeSlider(Node root) {
        float trackY = VOL_Y - TRACK_H / 2f;

        sliderTrack = new UiKit.Rect(TRACK_X, trackY, TRACK_W, TRACK_H);

        // track
        UiKit.box(am, root, TRACK_X, trackY, TRACK_W, TRACK_H, TRACK, null, 0f);

        // filled part: a quad scaled horizontally by the volume
        fillGeo = UiKit.quad(am, TRACK_W, TRACK_H, FILL);
        fillGeo.setLocalTranslation(UiKit.X(TRACK_X), UiKit.Y(trackY + TRACK_H), 0f);
        root.attachChild(fillGeo);

        // knob on the end of the fill
        knobGeo = UiKit.quad(am, KNOB, KNOB, FILL);
        root.attachChild(knobGeo);

        refreshSlider();
    }

    // -------------------------------------------------------------------------
    // Controls
    // -------------------------------------------------------------------------

    private void buildControls(Node root) {
        String[][] bindings = {
                {"W A S D",     "MOVE"},
                {"SPACE",       "JUMP"},
                {"LEFT SHIFT",  "DODGE"},
                {"LEFT CTRL",   "CROUCH"},
                {"LEFT CLICK",  "ATTACK / UI"},
                {"RIGHT CLICK", "HEAVY ATTACK"},
                {"E",           "INVENTORY"},
                {"F",           "INTERACT"},
                {"Q",           "THROW BOMB"},
                {"1 - 6",       "HOTBAR"},
                {"ESC",         "PAUSE / BACK"}
        };

        for (int i = 0; i < bindings.length; i++) {
            float top = ROW_START_Y + i * ROW_H - ROW_H / 2f;

            UiKit.textLeft(font, root, bindings[i][0],
                    new UiKit.Rect(KEY_X, top, KEY_W, ROW_H), SIZE_ROW, TEXT);

            UiKit.textLeft(font, root, bindings[i][1],
                    new UiKit.Rect(ACTION_X, top, ACTION_W, ROW_H), SIZE_ROW, MUTED);
        }
    }

    private static float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }
}