package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;

import java.util.List;
import java.util.function.Consumer;

public final class MainMenuUI {

    public enum Screen { MAIN, SETTINGS, ABOUT }

    private static final ColorRGBA TEXT = new ColorRGBA(0.96f, 0.96f, 0.96f, 1f);
    private static final ColorRGBA TEXT_HOVER = new ColorRGBA(1f, 1f, 1f, 1f);

    private final AssetManager am;
    private final BitmapFont font;
    private final Node root;

    private final Consumer<Boolean> displayChange;
    private final Consumer<Float> volumeChange;

    private final SettingsUI settings;
    private final AboutUI about;

    private List<UiKit.Button> buttons = UiKit.newButtonList();

    private Screen screen = Screen.MAIN;
    private boolean open = false;

    private int width;
    private int height;

    // Main menu buttons
    private final MainButton[] mainButtons = new MainButton[4];

    private static final String[] MAIN_LABELS = {
            "PLAY",
            "Settings",
            "About",
            "EXIT"
    };

    public MainMenuUI(AssetManager am, BitmapFont font, Node guiNode, int width, int height,
                      boolean fullscreen, float volume,
                      Consumer<Boolean> displayChange, Consumer<Float> volumeChange) {

        this.am = am;
        this.font = font;
        this.root = new Node("MainMenuUI");

        this.width = width;
        this.height = height;

        this.displayChange = displayChange;
        this.volumeChange = volumeChange;

        guiNode.attachChild(root);

        this.settings = new SettingsUI(
                am,
                font,
                fullscreen,
                volume,
                () -> show(Screen.MAIN),
                displayChange,
                volumeChange
        );

        this.about = new AboutUI(
                am,
                font,
                () -> show(Screen.MAIN)
        );

        build();
    }

    public Node getNode() {
        return root;
    }

    public boolean isOpen() {
        return open;
    }

    public Screen getScreen() {
        return screen;
    }

    public boolean isFullscreen() {
        return settings.isFullscreen();
    }

    public float getVolume() {
        return settings.getVolume();
    }

    public void onViewportResized(int newWidth, int newHeight, boolean stillFullscreen) {
        settings.setFullscreen(stillFullscreen);

        if (newWidth == width && newHeight == height) {
            return;
        }

        width = newWidth;
        height = newHeight;

        build();
    }

    public void show(Screen target) {
        screen = target;
        open = true;
        build();
    }

    public void close() {
        if (!open) {
            return;
        }

        open = false;
        build();
    }

    /**
     * Call every frame with the cursor position (jME pixels, origin bottom-left).
     * Drives hover effects and the volume slider drag.
     */
    public void update(float px, float py) {
        if (!open) {
            return;
        }

        if (screen == Screen.MAIN) {
            updateMainButtons(px, py);
        } else {
            UiKit.hover(buttons, px, py);

            if (screen == Screen.SETTINGS) {
                settings.handleDrag(px, py);   // no-op unless a drag is active
            }
        }
    }

    /** Call when the mouse button is PRESSED. */
    public boolean handleClick(float px, float py) {
        if (!open) {
            return false;
        }

        if (screen == Screen.MAIN) {
            return handleMainClick(px, py);
        }

        if (screen == Screen.SETTINGS && settings.handleClick(px, py)) {
            return true;
        }

        return UiKit.click(buttons, px, py);
    }

    /** Call when the mouse button is RELEASED (ends a slider drag). */
    public void handleRelease() {
        settings.handleRelease();
    }

    private void build() {
        root.detachAllChildren();
        buttons = UiKit.newButtonList();

        if (!open) {
            return;
        }

        UiKit.init(width, height);

        switch (screen) {
            case MAIN -> buildMain();
            case SETTINGS -> settings.build(root, buttons);
            case ABOUT -> about.build(root, buttons);
        }
    }

    private void buildMain() {
        addBackground();

        /*
         * The background artwork is already responsible for the dark panel
         * on the left, so there is no extra panel or overlay here.
         */

        float scaleX = width / 1920f;
        float scaleY = height / 1080f;

        float scale = Math.max(scaleX, scaleY);

        float imageWidth = 1920f * scale;
        float imageHeight = 1080f * scale;

        float imageX = (width - imageWidth) * 0.5f;
        float imageY = (height - imageHeight) * 0.5f;

        // Positions are based on the original 1920x1080 artwork.
        float menuCenterX = 350f;
        float[] menuY = {
                632f,
                552f,
                472f,
                392f
        };

        float buttonWidth = 260f;
        float buttonHeight = 58f;

        for (int i = 0; i < MAIN_LABELS.length; i++) {
            float centerX = imageX + menuCenterX * scale;
            float centerY = imageY + menuY[i] * scale;

            float w = buttonWidth * scale;
            float h = buttonHeight * scale;

            MainButton button = new MainButton(
                    MAIN_LABELS[i],
                    centerX,
                    centerY,
                    w,
                    h
            );

            mainButtons[i] = button;

            addMainButtonText(button, scale);
        }
    }

    private void addBackground() {
        Picture background = new Picture("MainMenuBackground");

        background.setImage(
                am,
                "GUI/menu_bg.png",
                false
        );

        float scaleX = width / 1920f;
        float scaleY = height / 1080f;

        // Cover the viewport while keeping the artwork's aspect ratio.
        float scale = Math.max(scaleX, scaleY);

        float imageWidth = 1920f * scale;
        float imageHeight = 1080f * scale;

        float imageX = (width - imageWidth) * 0.5f;
        float imageY = (height - imageHeight) * 0.5f;

        background.setWidth(imageWidth);
        background.setHeight(imageHeight);
        background.setPosition(imageX, imageY);

        root.attachChild(background);
    }

    private void addMainButtonText(MainButton button, float scale) {
        BitmapText text = new BitmapText(font);

        text.setText(button.label);
        text.setSize(38f * scale);
        text.setColor(TEXT);
        text.setLocalTranslation(
                button.centerX - text.getLineWidth() / 2f,
                button.centerY + text.getLineHeight() / 2f,
                10f
        );

        button.text = text;

        root.attachChild(text);
    }

    private void updateMainButtons(float px, float py) {
        for (MainButton button : mainButtons) {
            if (button == null) {
                continue;
            }

            boolean hovered = button.contains(px, py);

            if (hovered != button.hovered) {
                button.hovered = hovered;

                if (button.text != null) {
                    button.text.setColor(
                            hovered ? TEXT_HOVER : TEXT
                    );

                    float scale = hovered ? 1.05f : 1f;

                    button.text.setSize(38f * getScale() * scale);

                    button.text.setLocalTranslation(
                            button.centerX - button.text.getLineWidth() / 2f,
                            button.centerY + button.text.getLineHeight() / 2f,
                            10f
                    );
                }
            }
        }
    }

    private boolean handleMainClick(float px, float py) {
        for (int i = 0; i < mainButtons.length; i++) {
            MainButton button = mainButtons[i];

            if (button == null || !button.contains(px, py)) {
                continue;
            }

            switch (i) {
                case 0 -> {
                    close();
                    onStartGame.run();
                    return true;
                }

                case 1 -> {
                    show(Screen.SETTINGS);
                    return true;
                }

                case 2 -> {
                    show(Screen.ABOUT);
                    return true;
                }

                case 3 -> {
                    onExit.run();
                    return true;
                }

                default -> {
                    return true;
                }
            }
        }

        return false;
    }

    private float getScale() {
        return Math.max(
                width / 1920f,
                height / 1080f
        );
    }

    private static final class MainButton {

        private final String label;

        private final float centerX;
        private final float centerY;

        private final float width;
        private final float height;

        private boolean hovered;
        private BitmapText text;

        private MainButton(
                String label,
                float centerX,
                float centerY,
                float width,
                float height
        ) {
            this.label = label;
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = width;
            this.height = height;
        }

        private boolean contains(float x, float y) {
            return x >= centerX - width / 2f
                    && x <= centerX + width / 2f
                    && y >= centerY - height / 2f
                    && y <= centerY + height / 2f;
        }
    }

    public Runnable onStartGame = () -> { };

    public Runnable onExit = () -> { };
}