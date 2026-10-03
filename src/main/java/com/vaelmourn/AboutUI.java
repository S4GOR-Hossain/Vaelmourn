package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;

import java.util.List;

/**
 * ABOUT screen of the main menu: the team headline and the two credited members.
 *
 * Artwork: GUI/about_bg.png (1920x1080). This class only places text on it.
 *
 * Layout values are UiKit reference px (1920x1080, origin TOP-LEFT), read
 * straight off the mockup. UiKit.init(width, height) must have run before build().
 */
final class AboutUI {

    // ---- colours ------------------------------------------------------------
    private static final ColorRGBA TEXT   = new ColorRGBA(0.96f, 0.96f, 0.96f, 1f);
    private static final ColorRGBA DETAIL = new ColorRGBA(0.86f, 0.86f, 0.86f, 1f);
    private static final ColorRGBA BACK   = new ColorRGBA(0.62f, 0.62f, 0.62f, 1f);

    // ---- content ------------------------------------------------------------
    private static final String BY       = "By";
    private static final String HEADLINE = "BONE  APPéTIT";

    /** name, student id, programme. */
    private static final String[][] CREDITS = {
            {"Sagor Hossain",   "0112330696", "BsCSE, VIU"},
            {"Ahmed Naz Saif",  "0112330825", "BsCSE, VIU"},
    };

    // ---- layout (reference px, top-left origin) -----------------------------
    private static final float CENTER_X = 975f;

    private static final float BY_Y = 185f;        // centre y
    private static final float BY_SIZE = 32f;

    private static final float HEAD_Y = 277f;      // centre y
    private static final float HEAD_SIZE = 56f;

    private static final float[] COLUMN_X = {664f, 1229f};   // centre x of each member
    private static final float COLUMN_W = 520f;
    private static final float NAME_Y = 533f;      // centre y
    private static final float NAME_SIZE = 48f;
    private static final float ID_Y = 577f;
    private static final float PROG_Y = 613f;
    private static final float DETAIL_SIZE = 32f;
    private static final float LINE_H = 44f;

    private final AssetManager am;
    private final BitmapFont font;
    private final Runnable onBack;

    AboutUI(AssetManager am, BitmapFont font, Runnable onBack) {
        this.am = am;
        this.font = font;
        this.onBack = onBack;
    }

    void build(Node root, List<UiKit.Button> buttons) {
        addBackground(root);

        centered(root, BY, CENTER_X, BY_Y, 600f, BY_SIZE, TEXT);
        centered(root, HEADLINE, CENTER_X, HEAD_Y, 1000f, HEAD_SIZE, TEXT);

        for (int i = 0; i < CREDITS.length; i++) {
            float cx = COLUMN_X[i];
            centered(root, CREDITS[i][0], cx, NAME_Y, COLUMN_W, NAME_SIZE, TEXT);
            centered(root, CREDITS[i][1], cx, ID_Y, COLUMN_W, DETAIL_SIZE, DETAIL);
            centered(root, CREDITS[i][2], cx, PROG_Y, COLUMN_W, DETAIL_SIZE, DETAIL);
        }

        // The mockup has no back control; this is a quiet text button in the
        // top-left corner so the screen can still be left with the mouse.
        // Delete this call if you only want to leave with ESC.
        UiKit.textButton(font, root, buttons, "BACK",
                new UiKit.Rect(60f, 50f, 240f, 80f), 40f, BACK, onBack);
    }

    private void addBackground(Node root) {
        // UiKit stretches the 1920x1080 reference space over the whole screen,
        // so the artwork does the same and stays aligned with the text.
        Picture bg = new Picture("AboutBackground");
        bg.setImage(am, "GUI/about_bg.png", false);
        bg.setWidth(UiKit.getScreenW());
        bg.setHeight(UiKit.getScreenH());
        bg.setPosition(0f, 0f);
        root.attachChild(bg);
    }

    /** Text centred on (cx, cy) in reference px. */
    private void centered(Node root, String text, float cx, float cy, float w,
                          float size, ColorRGBA color) {
        UiKit.textIn(font, root, text,
                new UiKit.Rect(cx - w / 2f, cy - LINE_H, w, LINE_H * 2f), size, color);
    }
}