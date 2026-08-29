package com.vaelmourn;

import com.jme3.app.SimpleApplication;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Checkbox;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.style.BaseStyles;

/**
 * Standalone Lemur demo.
 *
 * Lemur is the jME3 UI toolkit (Container / Button / Label / TextField /
 * Checkbox / Slider / Popup, plus drag-and-drop and flexible layouts).
 *
 * It is ALREADY initialised in ForestBiome.simpleInitApp() with:
 *
 *     GuiGlobals.initialize(this);   // call once, needs the Application
 *     BaseStyles.loadGlassStyle();   // dark translucent "Glass" theme
 *
 * That is the only setup needed — after that you just build Containers and
 * attach them to the guiNode. This class shows each common widget so you can
 * copy the patterns straight into your own UI.
 *
 * Run it standalone:
 *     mvn exec:java "-Dexec.mainClass=com.vaelmourn.LemurExample"
 */
public class LemurExample extends SimpleApplication {

    @Override
    public void simpleInitApp() {
        // Initialise Lemur for this standalone app. (Idempotent in a real game;
        // do this once in simpleInitApp, then skip it in later states.)
        GuiGlobals.initialize(this);
        BaseStyles.loadGlassStyle();

        // Containers lay out their children (currently a vertical list).
        Container panel = new Container();
        panel.setLocalTranslation(100f, 400f, 0f);

        panel.addChild(new Label("Inventory"));

        Button weapon = new Button("Weapon");
        weapon.addClickCommands(source -> System.out.println("clicked Weapon"));
        panel.addChild(weapon);

        Button potion = new Button("Potion");
        potion.addClickCommands(source -> System.out.println("clicked Potion"));
        panel.addChild(potion);

        panel.addChild(new Checkbox("Equip Shield"));
        panel.addChild(new TextField("type a name"));
        panel.addChild(new Label("Soul Dust: 0"));

        // Attach the whole panel to the GUI node so it renders on screen.
        guiNode.attachChild(panel);
    }

    public static void main(String[] args) {
        new LemurExample().start();
    }
}
