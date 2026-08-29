
package com.vaelmourn;

import com.jme3.app.SimpleApplication;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Checkbox;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.component.SpringGridLayout;
import com.simsilica.lemur.style.BaseStyles;

/**
 * How to build GUI with Lemur (jME3 UI toolkit), version 1.16.0.
 *
 * Lemur is ALREADY initialised in ForestBiome.simpleInitApp():
 *     GuiGlobals.initialize(this);   // only once, needs the Application
 *     BaseStyles.loadGlassStyle();   // dark translucent theme
 *
 * Three common screens, showing the patterns you will reuse:
 *   1. Main menu -> a vertical stack of Buttons (Container default layout)
 *   2. Basic UI  -> Labels, TextField, Checkbox, button callbacks
 *   3. Inventory -> a rows x cols grid of slots (SpringGridLayout)
 *
 * Run standalone: mvn exec:java "-Dexec.mainClass=com.vaelmourn.LemurUIGuide"
 */
public class LemurUIGuide extends SimpleApplication {

    @Override
    public void simpleInitApp() {
        GuiGlobals.initialize(this);   // only call once per app
        BaseStyles.loadGlassStyle();

        buildMainMenu();
        buildBasicUI();
        buildInventory();
    }

    // 1. MAIN MENU -------------------------------------------------------
    // new Container() stacks its children vertically by default.
    private void buildMainMenu() {
        Container menu = new Container();
        menu.setLocalTranslation(300f, 500f, 0f);   // x, y (screen px, origin bottom-left), z

        menu.addChild(new Label("Vaelmourn"));

        Button play = new Button("Play");
        play.addClickCommands(src -> System.out.println("Start game"));
        menu.addChild(play);

        Button settings = new Button("Settings");
        settings.addClickCommands(src -> System.out.println("Open settings"));
        menu.addChild(settings);

        Button quit = new Button("Quit");
        quit.addClickCommands(src -> stop());       // stop() closes the app
        menu.addChild(quit);

        // Attach the whole panel to the on-screen GUI node.
        guiNode.attachChild(menu);
    }

    // 2. BASIC UI --------------------------------------------------------
    // A form built from smaller containers, with a click callback that
    // reads a checkbox's live state.
    private void buildBasicUI() {
        Container form = new Container();
        form.setLocalTranslation(700f, 500f, 0f);

        form.addChild(new Label("Character Name:"));
        form.addChild(new TextField("Rook"));

        Checkbox pvp = new Checkbox("Enable PvP");
        form.addChild(pvp);

        Button submit = new Button("Submit");
        submit.addClickCommands(src ->
                System.out.println("PvP=" + pvp.isChecked()));   // lambda captures 'pvp'
        form.addChild(submit);

        guiNode.attachChild(form);
    }

    // 3. INVENTORY -------------------------------------------------------
    // SpringGridLayout places children at (row, col). Use Container + a
    // small Panel per slot for a clean 5-col x 2-row grid.
    private void buildInventory() {
        SpringGridLayout gridLayout = new SpringGridLayout();
        Container grid = new Container(gridLayout);
        grid.setLocalTranslation(1100f, 500f, 0f);

        int cols = 5, rows = 2;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                // A sized blank box = one inventory slot.
                Panel slot = new Panel(44f, 44f);
                gridLayout.addChild(r, c, slot);   // place at row r, column c
            }
        }

        guiNode.attachChild(grid);
    }

    public static void main(String[] args) {
        new LemurUIGuide().start();
    }
}
