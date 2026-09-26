package com.vaelmourn;

import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.style.BaseStyles;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.SkinningControl;
import com.jme3.app.SimpleApplication;
import com.jme3.app.DebugKeysAppState;
import com.jme3.app.StatsAppState;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.environment.EnvironmentCamera;
import com.jme3.environment.LightProbeFactory;
import com.jme3.environment.generation.JobProgressAdapter;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.util.SkyFactory;

import java.awt.Color;
import java.awt.DisplayMode;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class ForestBiome extends SimpleApplication implements ActionListener {

    private BulletAppState bulletAppState;
    private BetterCharacterControl playerControl;
    private Node playerNode;
    private AnimComposer animComposer;
    private String currentAnim = "";

    private boolean left, right, forward, backward;
    private final Vector3f walkDirection = new Vector3f();

    // --- Dodge state ---
    private boolean dodging = false;
    private float dodgeTimer = 0f;
    private float dodgeCooldown = 0f;
    private final float DODGE_DURATION_FALLBACK = 0.4f;
    private float rollClipLength = DODGE_DURATION_FALLBACK;
    private final float DODGE_COOLDOWN_TIME = 0.8f;
    private final float DODGE_SPEED = 14f;
    private final Vector3f dodgeDirection = new Vector3f();
    private final Vector3f lastMoveDir = new Vector3f();

    // --- Crouch state ---
    private boolean crouching = false;
    private final float CROUCH_SPEED_MULTIPLIER = 0.5f;
    private String crouchIdleClipName = null;
    private String crouchWalkClipName = null;

    // --- Jump / fall state ---
    private String jumpClipName = null;
    private String fallClipName = null;

    // --- Gravity shaping ---
    private final float BASE_GRAVITY = 25f;
    private final float FALL_GRAVITY_MULTIPLIER = 2.5f;
    private final float RISE_GRAVITY_MULTIPLIER = 1.1f;

    // --- Orbit camera ---
    private float camYaw = 0f;
    private float camPitch = 0.3f;
    private float camDistance = 8f;
    private final float HORIZONTAL_SENSITIVITY = 3f;
    private final float VERTICAL_SENSITIVITY = 1f;

    // --- Combat integration ---
    private Weapons weapons;
    private CombatController combat;
    private CombatEffects effects;

    // --- Inventory / HUD integration ---
    private Inventory inventory;
    private PlayerStats playerStats;
    private InventoryUI inventoryUI;
    private boolean inventoryOpen = false;

    // --- Stage / roguelike system ---
    private StageManager stageManager;

    // --- Persistent gameplay HUD (health bar + enemies remaining, bottom-left) ---
    private Node hudNode;
    private Geometry hudHpFill;
    private BitmapText hudHpText;
    private BitmapText hudEnemiesText;
    private Geometry hudCooldownFill;
    private float hudSx = 1f;
    private float hudSy = 1f;
    private static final float HUD_HP_FULL_WIDTH = 252f; // the fill bar's inner width at 1080p

    // potion buff timers, drawn as draining colored pills above the enemy counter
    private final Geometry[] hudBuffTracks = new Geometry[PlayerStats.Buff.values().length];
    private final Geometry[] hudBuffFills = new Geometry[PlayerStats.Buff.values().length];
    private final BitmapText[] hudBuffSecs = new BitmapText[PlayerStats.Buff.values().length];
    private static final ColorRGBA[] HUD_BUFF_COLORS = {
            new ColorRGBA(0.35f, 0.85f, 1.00f, 1f),  // Speed
            new ColorRGBA(1.00f, 0.50f, 0.15f, 1f),  // Strength
            new ColorRGBA(1.00f, 0.85f, 0.20f, 1f),  // Critical
            new ColorRGBA(0.35f, 0.90f, 0.40f, 1f),  // Regen
    };

    // --- colored particle aura hugging the player while a buff is running ---
    private PlayerBuffEffects buffEffects;

    // --- transparent quick-use toolbar (1-5), bottom-center of the screen ---
    private final Geometry[] hudToolbarSlots = new Geometry[Inventory.TOOLBAR_SIZE];
    private final Geometry[] hudToolbarBorders = new Geometry[Inventory.TOOLBAR_SIZE];
    private final Geometry[] hudToolbarIcons = new Geometry[Inventory.TOOLBAR_SIZE];
    private final Geometry[] hudToolbarIconsTex = new Geometry[Inventory.TOOLBAR_SIZE];
    private final BitmapText[] hudToolbarCounts = new BitmapText[Inventory.TOOLBAR_SIZE];
    private final BitmapText[] hudToolbarNumbers = new BitmapText[Inventory.TOOLBAR_SIZE];
    private final Map<String, Texture> hudIconCache = new HashMap<>();
    private float hudToolbarLeft, hudToolbarBottom, hudToolbarSlot, hudToolbarGap;
    private boolean hudCursorShown = false;
    private boolean hudToolbarBuilt = false;

    private Node forestZoneNode;

    private EnvironmentCamera envCam;
    private boolean lightProbeBaked = false;

    // --- Interactables (chests, NPCs) ---
    private List<Interactable> interactables = new ArrayList<>();
    private List<RigidBodyControl> interactablePhysics = new ArrayList<>();
    private boolean interactablesBuilt = false;
    private ShopUI shopUI;
    private ChestUI chestUI;
    private static final float INTERACT_RANGE = 3.5f;

    private final AnalogListener analogListener = (name, value, tpf) -> {
        if (inventoryOpen) return; // so it doesn't orbit the camera while browsing
        switch (name) {
            case "MouseX+":
                camYaw -= value * HORIZONTAL_SENSITIVITY;
                break;
            case "MouseX-":
                camYaw += value * HORIZONTAL_SENSITIVITY;
                break;
            case "MouseY+":
                camPitch += value * VERTICAL_SENSITIVITY;
                break;
            case "MouseY-":
                camPitch -= value * VERTICAL_SENSITIVITY;
                break;
        }

        camPitch = FastMath.clamp(camPitch, -1.2f, 1.2f);
    };

    public static void main(String[] args) {
        ForestBiome app = new ForestBiome();

        AppSettings settings = new AppSettings(true);

        GraphicsDevice device =
                GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice();

        DisplayMode dm = device.getDisplayMode();

        settings.setResolution(dm.getWidth(), dm.getHeight());
        settings.setFrequency(dm.getRefreshRate());
        settings.setBitsPerPixel(dm.getBitDepth() > 0 ? dm.getBitDepth() : 24);

        boolean fullscreenSupported = device.isFullScreenSupported();
        settings.setFullscreen(fullscreenSupported);

        if (!fullscreenSupported) {
            System.out.println(
                    "Exclusive fullscreen not supported on this device — " +
                            "falling back to windowed at native resolution."
            );
        }

        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {

        viewPort.setBackgroundColor(new ColorRGBA(0.45f, 0.65f, 0.82f, 1f));

        // Lemur (jME3's UI toolkit) needs to be up before we can make any GUI widgets.
        // GuiGlobals.initialize(this) only needs the one call, before building Lemur UI.
        GuiGlobals.initialize(this);
        // The Glass theme comes from a Groovy stylesheet, which throws on JDKs newer than
        // the bundled Groovy supports — so guard it so startup never breaks. Our HUD is
        // plain jME3 (not Lemur), so a missing Glass theme has zero impact anyway.
        try {
            BaseStyles.loadGlassStyle(); // gives Lemur its dark translucent sci-fi look
        } catch (Throwable t) {
            System.err.println("Lemur Glass style unavailable: " + t);
        }

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

        // Kill the default stats/FPS overlay and debug keys so they don't paint
        // numbers over the bottom-left corner of the game UI.
        stateManager.detach(stateManager.getState(StatsAppState.class));
        stateManager.detach(stateManager.getState(DebugKeysAppState.class));

        // World geometry, lighting and atmosphere all live in the Stage system now,
        // which is why it's set up after the player and PlayerStats below —
        // loadInitialStage() warps the player and needs those to already exist.

        // Player
        Spatial playerModel = assetManager.loadModel("Models/Characters/Player/player.gltf");

        // Hardware (GPU) skinning of this model crashes the AMD OpenGL driver
        // (EXCEPTION_ACCESS_VIOLATION in glBufferData), so we stick to CPU skinning.
        disableHardwareSkinning(playerModel);

        playerNode = new Node("Player");
        playerNode.attachChild(playerModel);
        rootNode.attachChild(playerNode);

        playerControl = new BetterCharacterControl(0.5f, 1.8f, 1f);
        playerControl.setJumpForce(new Vector3f(0, 15f, 0));
        playerControl.setGravity(new Vector3f(0, -BASE_GRAVITY, 0));

        playerNode.addControl(playerControl);
        bulletAppState.getPhysicsSpace().add(playerControl);

        playerControl.getRigidBody().setCcdMotionThreshold(0.1f);
        playerControl.getRigidBody().setCcdSweptSphereRadius(0.5f);

        playerControl.warp(new Vector3f(0, 5f, 0));

        animComposer = findAnimComposer(playerModel);

        if (animComposer != null) {

            playAnim("Idle");

            System.out.println("Available animation clips: " + animComposer.getAnimClipsNames());

            if (animComposer.getAnimClipsNames().contains("Roll")) {
                rollClipLength = (float) animComposer.getAnimClip("Roll").getLength();
            } else {
                System.out.println(
                        "WARNING: No 'Roll' clip found — falling back to " +
                                DODGE_DURATION_FALLBACK + "s dodge duration. Check clip list."
                );
            }

            crouchIdleClipName = findClipContaining("crouch", "duck", "sneak", "crawl");
            crouchWalkClipName = findClipContaining("crouchwalk", "crouch_walk", "sneakwalk", "duckwalk");
            jumpClipName = findClipContaining("jump");
            fallClipName = findClipContaining("fall", "inair", "airborne", "midair");

            if (crouchIdleClipName == null) {
                System.out.println("WARNING: No crouch clip found.");
            }

            if (jumpClipName == null && fallClipName == null) {
                System.out.println("WARNING: No jump/fall clip found.");
            }
        }

        // Combat system
        weapons = new Weapons();
        combat = new CombatController(cam, playerNode, animComposer, weapons);
        combat.equip("iron_sword"); // iron sword is the default loadout

        // combat feel: damage numbers, shake, sounds, player flash
        effects = new CombatEffects(assetManager, rootNode, playerNode);
        combat.setEffects(effects);
        // potion buff cosmetics: color-tinted particle aura on the player model
        buffEffects = new PlayerBuffEffects(assetManager, playerNode);

        // Inventory + HUD
        ItemRegistry.registerDefaults();
        inventory = new Inventory();
        playerStats = new PlayerStats();
        // whenever the player actually loses health, trigger the hurt flash + shake + sound
        playerStats.setDamageListener((amount, currentHealth) -> {
            if (effects != null) effects.onPlayerDamaged();
        });
        // the stat system reads equipped gear live and the combat controller
        // converts potion buffs into effective damage/attack speed
        playerStats.setInventory(inventory);
        combat.setPlayerStats(playerStats);
        combat.equip("iron_sword"); // now that stats exist, push the default sword's values in

        // Throw in some starter items so the inventory UI actually has stuff in it.
        inventory.addItem("health_potion", 6);
        inventory.addItem("speed_potion", 3);
        inventory.addItem("strength_potion", 2);
        inventory.addItem("critical_potion", 2);
        inventory.addItem("regen_potion", 3);
        inventory.addItem("dungeon_key", 2);
        inventory.addItem("iron_ingot", 12);
        inventory.addItem("iron_ore", 8);
        inventory.addItem("leather", 8);
        inventory.addItem("blood_shard", 1);
        inventory.addItem("wolf_fang", 1);
        inventory.addItem("ember_core", 1);
        inventory.addItem("void_crystal", 1);
        inventory.addItem("hunters_blade", 1);
        inventory.addItem("heavy_blade", 1);
        inventory.addItem("iron_sword", 1);
        inventory.getToolbarSlot(0).itemId = "iron_sword";
        inventory.getToolbarSlot(0).count = 1;
        inventory.getToolbarSlot(1).itemId = "health_potion";
        inventory.getToolbarSlot(1).count = 3;
        inventory.getToolbarSlot(2).itemId = "dungeon_key";
        inventory.getToolbarSlot(2).count = 2;
        inventory.getToolbarSlot(3).itemId = "speed_potion";
        inventory.getToolbarSlot(3).count = 2;
        inventory.getToolbarSlot(4).itemId = "regen_potion";
        inventory.getToolbarSlot(4).count = 2;
        inventory.getEquipSlot(Inventory.EquipSlot.HELMET).itemId = "iron_helmet";
        inventory.getEquipSlot(Inventory.EquipSlot.HELMET).count = 1;
        inventory.getEquipSlot(Inventory.EquipSlot.CHESTPLATE).itemId = "iron_chestplate";
        inventory.getEquipSlot(Inventory.EquipSlot.CHESTPLATE).count = 1;
        inventory.getEquipSlot(Inventory.EquipSlot.BOOTS).itemId = "iron_boots";
        inventory.getEquipSlot(Inventory.EquipSlot.BOOTS).count = 1;
        playerStats.addSoulDust(250);
        playerStats.addExperience(40f);

        // ---- Stage / roguelike system ----
        // Fixed 29-stage rotation: each biome = 4 normal stages -> boss arena ->
        // safe hub (minus the Kingdom Court, which only has 3 regular stages),
        // looping back to a fresh Sanctuary after the Fallen King.
        stageManager = new StageManager(assetManager, rootNode, bulletAppState, this);
        stageManager.setPlayerStats(playerStats);
        stageManager.addStage(new SanctuaryStage());                 // 0: hub
        stageManager.addStage(new DarkwoodStage(1));                 // 1
        stageManager.addStage(new DarkwoodStage(2));                 // 2
        stageManager.addStage(new DarkwoodStage(3));                 // 3
        stageManager.addStage(new DarkwoodStage(4));                 // 4
        stageManager.addStage(new TreeWardenBossStage());            // 5: boss
        stageManager.addStage(new SanctuaryStage());                 // 6: hub
        stageManager.addStage(new AshenWastesStage(1));              // 7
        stageManager.addStage(new AshenWastesStage(2));              // 8
        stageManager.addStage(new AshenWastesStage(3));              // 9: horde
        stageManager.addStage(new AshenWastesStage(4));              // 10
        stageManager.addStage(new HellhoundBossStage());             // 11: boss
        stageManager.addStage(new SanctuaryStage());                 // 12: hub
        stageManager.addStage(new FrozenDepthsStage(1));             // 13
        stageManager.addStage(new FrozenDepthsStage(2));             // 14
        stageManager.addStage(new FrozenDepthsStage(3));             // 15: slowing
        stageManager.addStage(new FrozenDepthsStage(4));             // 16
        stageManager.addStage(new FrostGiantBossStage());            // 17: boss
        stageManager.addStage(new SanctuaryStage());                 // 18: hub
        stageManager.addStage(new JungleStage(1));                   // 19
        stageManager.addStage(new JungleStage(2));                   // 20
        stageManager.addStage(new JungleStage(3));                   // 21: double speed
        stageManager.addStage(new JungleStage(4));                   // 22
        stageManager.addStage(new BeekeeperBossStage());             // 23: boss
        stageManager.addStage(new SanctuaryStage());                 // 24: hub
        stageManager.addStage(new DarkRoyaleKingdomCourtStage(1));   // 25
        stageManager.addStage(new DarkRoyaleKingdomCourtStage(2));   // 26
        stageManager.addStage(new DarkRoyaleKingdomCourtStage(3));   // 27
        stageManager.addStage(new FallenKingBossStage());            // 28: final boss
        stageManager.loadInitialStage(playerControl);
        combat.setEnemies(stageManager.getActiveEnemies());

        // Build the Shop and Chest UIs before the interactables hold a reference to them,
        // otherwise pressing F near a chest/NPC does nothing at all (handlers stay null).
        shopUI = new ShopUI(assetManager, renderManager, inputManager, cam, guiNode,
                inventory, playerStats, settings.getWidth(), settings.getHeight());
        chestUI = new ChestUI(assetManager, renderManager, inputManager, cam, guiNode,
                inventory, settings.getWidth(), settings.getHeight());

        // Drop chests and NPCs into the Sanctuary (the starting stage)
        spawnChestsAndNPCs();

        inventoryUI = new InventoryUI(assetManager, renderManager, inputManager, cam,
                inventory, playerStats, playerModel,
                settings.getWidth(), settings.getHeight());
        guiNode.attachChild(inventoryUI.getNode());
        // "Use" on a weapon in the inventory menu hands it to the combat controller
        inventoryUI.setWeaponEquipHandler(weaponId -> {
            if (combat != null) combat.equip(weaponId);
        });

        buildHUD(settings.getWidth(), settings.getHeight());

        initKeys();

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(false);

        inputManager.addMapping("MouseX+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MouseX-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MouseY+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("MouseY-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));

        inputManager.addListener(analogListener, "MouseX+", "MouseX-", "MouseY+", "MouseY-");

        // Light-probe baking re-renders the whole scene into an environment map every
        // frame and that crashed the native AMD OpenGL driver (EXCEPTION_ACCESS_VIOLATION
        // in glBufferData). The Stage system handles lighting now, so the probe is just
        // left unattached to keep things safe.
        // envCam = new EnvironmentCamera();
        // stateManager.attach(envCam);
    }

    private String findClipContaining(String... keywords) {
        if (animComposer == null) return null;

        for (String clip : animComposer.getAnimClipsNames()) {
            String lower = clip.toLowerCase();
            for (String keyword : keywords) {
                if (lower.contains(keyword)) return clip;
            }
        }
        return null;
    }

    private void bakeLightProbe() {
        LightProbeFactory.makeProbe(envCam, rootNode, new JobProgressAdapter<LightProbe>() {
            @Override
            public void done(LightProbe result) {
                rootNode.addLight(result);
            }
        });
    }

    private void buildSky() {
        int width = 1024;
        int height = 512;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(90, 150, 220),
                0, height, new Color(210, 230, 245)
        );
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, width, height);

        Random rand = new Random(7);
        g2d.setColor(new Color(255, 255, 255, 210));

        for (int i = 0; i < 35; i++) {
            int cx = rand.nextInt(width);
            int cy = rand.nextInt(height / 2);
            int baseSize = 50 + rand.nextInt(90);

            for (int j = 0; j < 6; j++) {
                int ox = cx + rand.nextInt(baseSize) - baseSize / 2;
                int oy = cy + rand.nextInt(baseSize / 3) - baseSize / 6;
                int size = baseSize / 2 + rand.nextInt(baseSize / 2);
                g2d.fillOval(ox, oy, size, size / 2);
            }
        }

        g2d.dispose();

        BufferedImage flipped = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = flipped.createGraphics();
        g2.drawImage(img, 0, height, width, -height, null);
        g2.dispose();

        AWTLoader awtLoader = new AWTLoader();
        com.jme3.texture.Image jmeImage = awtLoader.load(flipped, false);

        Texture2D skyTexture = new Texture2D(jmeImage);
        skyTexture.setWrap(Texture.WrapMode.Repeat);

        Spatial sky = SkyFactory.createSky(assetManager, skyTexture, SkyFactory.EnvMapType.EquirectMap);
        rootNode.attachChild(sky);
    }

    private void buildGroundPlane(float halfExtent) {
        Spatial grassModel = assetManager.loadModel("Models/Environment/Forest/ground_grass.glb");

        fixEnvironmentMaterials(grassModel, new ColorRGBA(0.25f, 0.45f, 0.20f, 1f));

        float tileScale = 20f;
        float tileSize = tileScale;
        int half = (int) Math.ceil(halfExtent / tileSize) + 1;

        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                Spatial grass = grassModel.clone();
                grass.setLocalTranslation(x * tileSize, 0, z * tileSize);
                grass.setLocalScale(tileScale);
                forestZoneNode.attachChild(grass);
            }
        }

        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(halfExtent, 0.5f, halfExtent));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        bulletAppState.getPhysicsSpace().add(physics);
    }

    private void buildBoundaryWalls(float halfExtent) {
        float wallHeight = 10f;
        float wallThickness = 1f;

        createWall(new Vector3f(0, wallHeight / 2f, halfExtent), new Vector3f(halfExtent, wallHeight / 2f, wallThickness));
        createWall(new Vector3f(0, wallHeight / 2f, -halfExtent), new Vector3f(halfExtent, wallHeight / 2f, wallThickness));
        createWall(new Vector3f(halfExtent, wallHeight / 2f, 0), new Vector3f(wallThickness, wallHeight / 2f, halfExtent));
        createWall(new Vector3f(-halfExtent, wallHeight / 2f, 0), new Vector3f(wallThickness, wallHeight / 2f, halfExtent));
    }

    private void createWall(Vector3f position, Vector3f halfExtents) {
        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position);
        bulletAppState.getPhysicsSpace().add(physics);
    }

    private void buildForest() {
        Random rand = new Random(42);

        String[] treeModels = {
                "Models/Environment/Forest/tree_default.glb",
                "Models/Environment/Forest/tree_cone.glb",
                "Models/Environment/Forest/tree_detailed.glb",
                "Models/Environment/Forest/tree_oak.glb",
                "Models/Environment/Forest/tree_fat.glb",
                "Models/Environment/Forest/tree_blocks.glb"
        };

        String[] rockModels = {
                "Models/Environment/Forest/stone_largeA.glb",
                "Models/Environment/Forest/stone_largeB.glb",
                "Models/Environment/Forest/stone_largeC.glb",
                "Models/Environment/Forest/stone_smallA.glb",
                "Models/Environment/Forest/stone_smallB.glb",
                "Models/Environment/Forest/stone_smallC.glb",
                "Models/Environment/Forest/stone_smallFlatA.glb",
                "Models/Environment/Forest/stone_smallFlatB.glb",
                "Models/Environment/Forest/stone_smallFlatC.glb",
                "Models/Environment/Forest/stone_smallG.glb",
                "Models/Environment/Forest/stone_smallH.glb",
                "Models/Environment/Forest/stone_smallI.glb",
                "Models/Environment/Forest/stone_tallA.glb",
                "Models/Environment/Forest/stone_tallB.glb",
                "Models/Environment/Forest/stone_tallC.glb",
                "Models/Environment/Forest/stone_tallD.glb",
                "Models/Environment/Forest/stump_old.glb"
        };

        int treeCount = 75;

        for (int i = 0; i < treeCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * 135f;
            float z = (rand.nextFloat() - 0.5f) * 135f;

            if (new Vector3f(x, 0, z).length() < 15f) {
                i--;
                continue;
            }

            placeTree(x, z, rand, treeModels);
        }

        placeRock(20f, 15f, 4f, rand, rockModels);
        placeRock(-25f, -10f, 5f, rand, rockModels);
        placeRock(10f, -30f, 3.5f, rand, rockModels);

        scatterPebbles(rockModels);
    }

    private void scatterPebbles(String[] rockModels) {
        Random rand = new Random(99);
        int pebbleCount = 300;

        for (int i = 0; i < pebbleCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * 150f;
            float z = (rand.nextFloat() - 0.5f) * 150f;

            String chosenModel = rockModels[rand.nextInt(rockModels.length)];
            if (!chosenModel.contains("small")) continue;

            Spatial pebble = assetManager.loadModel(chosenModel);
            fixEnvironmentMaterials(pebble, new ColorRGBA(0.40f, 0.40f, 0.40f, 1f));

            pebble.setLocalTranslation(x, 0, z);
            pebble.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
            pebble.setLocalScale(0.3f + rand.nextFloat() * 0.4f);

            forestZoneNode.attachChild(pebble);
        }
    }

    private void placeTree(float x, float z, Random rand, String[] treeModels) {
        String chosenModel = treeModels[rand.nextInt(treeModels.length)];
        Spatial tree = assetManager.loadModel(chosenModel);

        fixEnvironmentMaterials(tree, new ColorRGBA(0.20f, 0.38f, 0.16f, 1f));

        tree.setLocalTranslation(x, 0, z);
        tree.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);

        float baseScale = 4f;
        if (chosenModel.contains("default")) baseScale = 4.5f;
        if (chosenModel.contains("cone")) baseScale = 4.5f;
        if (chosenModel.contains("detailed")) baseScale = 4f;
        if (chosenModel.contains("oak")) baseScale = 3.5f;
        if (chosenModel.contains("fat")) baseScale = 4f;
        if (chosenModel.contains("blocks")) baseScale = 4f;

        float scale = baseScale + rand.nextFloat() * 0.8f;
        tree.setLocalScale(scale);

        CapsuleCollisionShape trunkShape = new CapsuleCollisionShape(0.7f, 3.5f);
        RigidBodyControl physics = new RigidBodyControl(trunkShape, 0);
        physics.setPhysicsLocation(new Vector3f(x, 1.8f, z));

        forestZoneNode.attachChild(tree);
        bulletAppState.getPhysicsSpace().add(physics);
    }

    private void placeRock(float x, float z, float size, Random rand, String[] rockModels) {
        String chosenModel = rockModels[rand.nextInt(rockModels.length)];
        Spatial rock = assetManager.loadModel(chosenModel);

        fixEnvironmentMaterials(rock, new ColorRGBA(0.40f, 0.40f, 0.40f, 1f));

        rock.setLocalTranslation(x, 0, z);
        rock.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
        rock.setLocalScale(size / 2f);

        forestZoneNode.attachChild(rock);

        rock.updateModelBound();
        forestZoneNode.updateGeometricState();

        BoundingBox bbox = getWorldBoundingBox(rock);

        Vector3f halfExtents =
                bbox != null ? bbox.getExtent(new Vector3f()) : new Vector3f(size, size * 0.6f, size);

        Vector3f center =
                bbox != null ? bbox.getCenter().clone() : new Vector3f(x, size * 0.6f, z);

        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(center);

        bulletAppState.getPhysicsSpace().add(physics);
    }

    private BoundingBox getWorldBoundingBox(Spatial spatial) {
        if (spatial.getWorldBound() instanceof BoundingBox) {
            return (BoundingBox) spatial.getWorldBound();
        }
        return null;
    }

    private void fixEnvironmentMaterials(Spatial spatial, ColorRGBA fallbackColor) {
        if (spatial instanceof Geometry) {
            Geometry geometry = (Geometry) spatial;
            Material existingMaterial = geometry.getMaterial();

            if (existingMaterial == null) {
                Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
                material.setBoolean("UseMaterialColors", true);
                material.setColor("Diffuse", fallbackColor);
                material.setColor("Specular", ColorRGBA.White);
                material.setFloat("Shininess", 8f);
                geometry.setMaterial(material);

            } else {
                boolean hasVertexColors =
                        geometry.getMesh().getBuffer(VertexBuffer.Type.Color) != null;

                boolean isPbr =
                        existingMaterial.getMaterialDef()
                                .getAssetName()
                                .contains("PBRLighting");

                if (isPbr) {
                    if (existingMaterial.getMaterialDef().getMaterialParam("Metallic") != null) {
                        existingMaterial.setFloat("Metallic", 0f);
                    }
                    if (existingMaterial.getMaterialDef().getMaterialParam("Roughness") != null) {
                        existingMaterial.setFloat("Roughness", 1f);
                    }
                }

                if (hasVertexColors
                        && existingMaterial.getMaterialDef().getMaterialParam("UseVertexColor") != null) {
                    existingMaterial.setBoolean("UseVertexColor", true);
                }
            }
        }

        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                fixEnvironmentMaterials(child, fallbackColor);
            }
        }
    }

    /**
     * Disables GPU (hardware) skinning on every skinned mesh in the given spatial tree.
     * Hardware skinning crashes this AMD OpenGL driver (EXCEPTION_ACCESS_VIOLATION in
     * glBufferData when uploading the animated vertex data), so we force CPU skinning.
     */
    private void disableHardwareSkinning(Spatial spatial) {
        if (spatial == null) return;

        SkinningControl skinning = spatial.getControl(SkinningControl.class);
        if (skinning != null) {
            skinning.setHardwareSkinningPreferred(false);
        }

        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                disableHardwareSkinning(child);
            }
        }
    }

    private AnimComposer findAnimComposer(Spatial spatial) {
        AnimComposer composer = spatial.getControl(AnimComposer.class);
        if (composer != null) return composer;

        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                AnimComposer result = findAnimComposer(child);
                if (result != null) return result;
            }
        }

        return null;
    }

    private void playAnim(String name) {
        if (animComposer == null) return;
        if (name == null) return;

        if (!currentAnim.equals(name)) {
            animComposer.setCurrentAction(name);
            currentAnim = name;
        }
    }

    private void initKeys() {
        inputManager.addMapping("Left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("Right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Forward", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("Backward", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("Jump", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("Dodge", new KeyTrigger(KeyInput.KEY_LSHIFT));
        inputManager.addMapping("Crouch", new KeyTrigger(KeyInput.KEY_LCONTROL));

        // opens/closes the inventory
        inputManager.addMapping("Inventory", new KeyTrigger(KeyInput.KEY_E));

        // talk to NPCs / open chests
        inputManager.addMapping("Interact", new KeyTrigger(KeyInput.KEY_F));

        // mouse buttons drive combat
        inputManager.addMapping("AttackPrimary", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("AttackSecondary", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));

        // quick-use the five toolbar slots (1-5 are the supply bar, 0 is the weapon)
        inputManager.addMapping("Hotbar0", new KeyTrigger(KeyInput.KEY_1));
        inputManager.addMapping("Hotbar1", new KeyTrigger(KeyInput.KEY_2));
        inputManager.addMapping("Hotbar2", new KeyTrigger(KeyInput.KEY_3));
        inputManager.addMapping("Hotbar3", new KeyTrigger(KeyInput.KEY_4));
        inputManager.addMapping("Hotbar4", new KeyTrigger(KeyInput.KEY_5));

        // dev shortcut: L warps straight to the next stage without fighting
        inputManager.addMapping("DevSkipStage", new KeyTrigger(KeyInput.KEY_L));

        inputManager.addListener(
                this,
                "Left", "Right", "Forward", "Backward",
                "Jump", "Dodge", "Crouch",
                "AttackPrimary", "AttackSecondary",
                "Inventory", "Interact",
                "Hotbar0", "Hotbar1", "Hotbar2", "Hotbar3", "Hotbar4",
                "DevSkipStage"
        );
    }

    /**
     * Dev tool bound to L: teleports to the next stage in the rotation. Cleanup
     * of the stage we're leaving (enemies, portal, physics) all happens inside
     * advanceStage(), so it's as safe as walking through a portal — just faster.
     */
    private void devSkipStage() {
        if (stageManager == null) return;
        System.out.println("[DEV] L pressed — skipping past " + stageManager.getCurrentStageName());
        stageManager.advanceStage();
        // teleport to the new stage's spawn point instead of dropping in mid-air
        // (normally you'd enter via the portal, which the skip bypasses)
        if (stageManager.getCurrentStage() != null && playerControl != null) {
            playerControl.warp(stageManager.getCurrentStage().getPlayerSpawnPoint());
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "DevSkipStage":
                // dev tool: hop to the next stage regardless of enemies left
                if (isPressed) devSkipStage();
                return;

            case "Inventory":
                if (isPressed) toggleInventory();
                return;

            case "Interact":
                if (isPressed) handleInteract();
                return;

            case "Left":
            case "Right":
            case "Forward":
            case "Backward":
            case "Jump":
            case "Dodge":
            case "Crouch":
            case "AttackPrimary":
                // When some UI is open, hand the click to it (inventory select /
                // drag-drop) instead of triggering combat.
                if (isUiOpen()) {
                    if (isPressed && inventoryOpen && inventoryUI != null) inventoryUI.handlePrimaryClick();
                    return;
                }
                // an in-world right-click menu owns mouse clicks while it's up
                if (isPressed && inventoryUI != null && inventoryUI.isContextMenuOpen()) {
                    inventoryUI.handlePrimaryClick();
                    return;
                }
                break;

            case "AttackSecondary":
                // drop gameplay input whenever a UI is up, but let the inventory
                // see it — right-click opens the item's action menu. Only on the
                // press so mouse-up doesn't instantly close it again.
                if (isUiOpen()) {
                    if (isPressed && inventoryOpen && inventoryUI != null) inventoryUI.handleSecondaryClick();
                    return;
                }
                // right-clicking an in-world toolbar slot opens its action menu
                if (isPressed && inventoryUI != null) {
                    if (inventoryUI.isContextMenuOpen()) {
                        inventoryUI.closeContextMenu();
                        return;
                    }
                    Vector2f cur = inputManager.getCursorPosition();
                    int idx = hudToolbarSlotAt(cur.x, cur.y);
                    if (idx >= 0) {
                        float x = hudToolbarLeft + idx * (hudToolbarSlot + hudToolbarGap);
                        inventoryUI.openMenuForSlot(inventory.getToolbarSlot(idx), x, hudToolbarBottom);
                        return;
                    }
                }
                break;
        }

        switch (name) {
            case "Left":
                left = isPressed;
                break;
            case "Right":
                right = isPressed;
                break;
            case "Forward":
                forward = isPressed;
                break;
            case "Backward":
                backward = isPressed;
                break;
            case "Jump":
                if (isPressed) playerControl.jump();
                break;
            case "Dodge":
                if (isPressed && !dodging && dodgeCooldown <= 0f && lastMoveDir.lengthSquared() > 0.01f) {
                    startDodge();
                }
                break;
            case "Crouch":
                crouching = isPressed;
                playerControl.setDucked(crouching);
                break;

            case "AttackPrimary":
                if (isPressed && combat != null) combat.onPrimaryPressed();
                break;

            case "AttackSecondary":
                if (combat != null) {
                    if (isPressed) combat.onSecondaryPressed();
                    else combat.onSecondaryReleased();
                }
                break;

            // toolbar quick-use: 1-5 map to the supply bar slots
            case "Hotbar0":
            case "Hotbar1":
            case "Hotbar2":
            case "Hotbar3":
            case "Hotbar4":
                if (isPressed) useToolbarSlot(name.charAt("Hotbar".length()) - '0');
                break;
        }
    }

    /**
     * Uses whatever sits in a toolbar slot. Potions get drunk, the weapon slot
     * re-equips the readied weapon; keys and materials sit there but never get used.
     */
    private void useToolbarSlot(int slotIndex) {
        if (inventory == null) return;
        Slot slot = inventory.getToolbarSlot(slotIndex);
        if (slot == null || slot.isEmpty()) return;
        Item item = ItemRegistry.get(slot.itemId);
        if (item == null) return;

        switch (item.getGroup()) {
            case CONSUMABLE:
                // drink it if the effect is non-empty; keys/materials never fire
                if (playerStats.consume(item)) inventory.removeFromToolbar(slotIndex, 1);
                break;
            case WEAPON:
                if (combat != null) combat.equip(item.id);
                break;
            default:
                // keys and materials are guarded by consume() returning false
                break;
        }
    }

    /**
     * True if any UI overlay is open (inventory, NPC shop, or chest),
     * in which case the game world should be paused and gameplay input ignored.
     */
    private boolean isUiOpen() {
        if (inventoryOpen) return true;
        if (chestUI != null && chestUI.isOpen()) return true;
        if (shopUI != null && shopUI.isOpen()) return true;
        return false;
    }

    private void toggleInventory() {
        inventoryOpen = !inventoryOpen;
        inventoryUI.setVisible(inventoryOpen);

        if (inventoryOpen) {
            // freeze the player in place while they browse.
            inputManager.setCursorVisible(true);
            if (playerControl != null) playerControl.setWalkDirection(Vector3f.ZERO);
            forward = backward = left = right = false;
        } else {
            inputManager.setCursorVisible(false);
        }
    }

    private void startDodge() {
        dodging = true;
        dodgeTimer = rollClipLength;
        dodgeCooldown = DODGE_COOLDOWN_TIME;
        dodgeDirection.set(lastMoveDir);
        playAnim("Roll");
    }

    @Override
    public void simpleUpdate(float tpf) {

        if (inventoryUI != null) {
            inventoryUI.update(tpf, cam);
        }

        // while the in-world toolbar menu is up, reveal the cursor so the
        // player can aim at its options (it's hidden during normal gameplay)
        boolean menuUp = inventoryUI != null && inventoryUI.isContextMenuOpen();
        boolean wantCursor = inventoryOpen || menuUp;
        if (wantCursor != hudCursorShown) {
            inputManager.setCursorVisible(wantCursor);
            hudCursorShown = wantCursor;
        }

        // keep the buff particle auras in sync with the potion timers (also while
        // browsing the paused inventory, so the aura is visible right on drink)
        if (buffEffects != null) {
            buffEffects.update(playerStats);
        }

        if (effects != null) {
            effects.update(tpf);
        }

        // the world is paused while inventory/shop/chest is open.
        if (isUiOpen()) {
            return;
        }

        if (!lightProbeBaked && envCam != null && envCam.getApplication() != null) {
            bakeLightProbe();
            lightProbeBaked = true;
        }

        // ---- Stage / roguelike system: update AI, portals, transitions ----
        if (stageManager != null) {
            stageManager.update(tpf, playerNode.getWorldTranslation(), playerControl);
        }
        if (combat != null) {
            combat.setEnemies(stageManager != null ? stageManager.getActiveEnemies() : null);
            combat.update(tpf);
        // potion buffs tick down and regen does its thing every frame
        if (playerStats != null) playerStats.update(tpf);
        }

        // Chests/NPCs only live in the Sanctuary — rebuild them when we come back,
        // and tear them down (physics included) when we move to another biome.
        updateSanctuaryInteractables();

        updateHUD();

        if (dodgeCooldown > 0f) {
            dodgeCooldown -= tpf;
        }

        float verticalVelocity = playerControl.getVelocity().y;
        float gravityScale = verticalVelocity < 0f ? FALL_GRAVITY_MULTIPLIER : RISE_GRAVITY_MULTIPLIER;
        playerControl.setGravity(new Vector3f(0, -BASE_GRAVITY * gravityScale, 0));

        boolean airborne = !playerControl.isOnGround();

        Camera camera = cam;
        Vector3f camDir = camera.getDirection().clone().setY(0).normalizeLocal();
        Vector3f camLeft = camera.getLeft().clone().setY(0).normalizeLocal();

        walkDirection.set(0, 0, 0);
        if (forward) walkDirection.addLocal(camDir);
        if (backward) walkDirection.addLocal(camDir.negate());
        if (left) walkDirection.addLocal(camLeft);
        if (right) walkDirection.addLocal(camLeft.negate());

        boolean isMoving = forward || backward || left || right;

        if (isMoving) {
            lastMoveDir.set(walkDirection).normalizeLocal();
        }

        if (dodging) {
            dodgeTimer -= tpf;
            playerControl.setWalkDirection(dodgeDirection.mult(DODGE_SPEED));

            if (dodgeTimer <= 0f) {
                dodging = false;
            }
        } else {
            // speed potions and leggings/boots now feed straight into walking
            float baseSpeed = playerStats != null ? playerStats.getMovementSpeed() : 12f;
            float speed = crouching ? baseSpeed * CROUCH_SPEED_MULTIPLIER : baseSpeed;

            if (walkDirection.lengthSquared() > 0) {
                walkDirection.normalizeLocal().multLocal(speed);
            }

            playerControl.setWalkDirection(walkDirection);

            if (isMoving) {
                playerControl.setViewDirection(walkDirection);
            }
        }

        if (dodging) {
            playAnim("Roll");
        } else if (airborne && (jumpClipName != null || fallClipName != null)) {
            String airClip = verticalVelocity > 0f && jumpClipName != null ? jumpClipName : fallClipName;
            playAnim(airClip != null ? airClip : jumpClipName);
        } else if (crouching && crouchIdleClipName != null) {
            playAnim(isMoving && crouchWalkClipName != null ? crouchWalkClipName : crouchIdleClipName);
        } else {
            playAnim(isMoving ? "Walk" : "Idle");
        }

        Vector3f playerPos = playerNode.getWorldTranslation().clone();

        Vector3f offset = new Vector3f(
                FastMath.sin(camYaw) * FastMath.cos(camPitch),
                FastMath.sin(camPitch),
                FastMath.cos(camYaw) * FastMath.cos(camPitch)
        ).multLocal(camDistance);

        // the combat shake rides on top of the orbit position; getShakeOffset
        // returns zero when nothing's kicking, so the camera stays untouched
        Vector3f shakeOffset = effects != null ? effects.getShakeOffset(tpf) : Vector3f.ZERO;
        cam.setLocation(playerPos.add(offset).add(0, 1.5f, 0).add(shakeOffset));
        cam.lookAt(playerPos.add(0, 1.5f, 0), Vector3f.UNIT_Y);
    }

    // =================== persistent gameplay HUD ===================

    private Geometry makeHudQuad(float x, float y, float w, float h, ColorRGBA color) {
        Geometry g = new Geometry("HudQuad", new Quad(w, h));
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, 0);
        hudNode.attachChild(g);
        return g;
    }

    private void buildHUD(int screenW, int screenH) {
        hudNode = new Node("HUD");
        hudSx = screenW / 1920f;
        hudSy = screenH / 1080f;

        float margin = 20f * hudSx;
        float barW = 260f * hudSx;
        float barH = 24f * hudSy;
        float inset = 4f * hudSx;
        float fillX = margin + inset;
        float fillY = 20f * hudSy + inset;

        // the dark backing bar behind the hp fill
        makeHudQuad(margin, 20f * hudSy, barW, barH, new ColorRGBA(0.08f, 0.08f, 0.10f, 0.85f));
        // green hp fill — updateHUD scales its width every frame
        hudHpFill = makeHudQuad(fillX, fillY, HUD_HP_FULL_WIDTH * hudSx, 16f * hudSy,
                new ColorRGBA(0.2f, 0.85f, 0.25f, 1f));

        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        hudHpText = new BitmapText(font, false);
        hudHpText.setSize(14f * hudSy);
        hudHpText.setColor(ColorRGBA.White);
        hudHpText.setText("100 / 100");
        hudHpText.setLocalTranslation(fillX + 4f * hudSx, fillY + 1f * hudSy, 0);
        hudNode.attachChild(hudHpText);

        // enemy counter sitting just below the hp bar
        hudEnemiesText = new BitmapText(font, false);
        hudEnemiesText.setSize(16f * hudSy);
        hudEnemiesText.setColor(new ColorRGBA(0.9f, 0.9f, 0.95f, 1f));
        hudEnemiesText.setText("Enemies: 0");
        hudEnemiesText.setLocalTranslation(margin, (20f * hudSy + barH + 8f * hudSy), 0);
        hudNode.attachChild(hudEnemiesText);

        // potion buff timers: a row of tiny draining pills above the enemy counter,
        // each tinted with its buff's color and showing the seconds left
        float buffX = margin;
        float buffY = (20f * hudSy + barH + 26f * hudSy) + 3f * hudSy;
        float buffW = 34f * hudSx;
        float buffH = 10f * hudSy;
        float buffGap = 30f * hudSx; // room for the seconds label next to each bar
        for (int i = 0; i < PlayerStats.Buff.values().length; i++) {
            hudBuffTracks[i] = makeHudQuad(buffX, buffY, buffW, buffH, new ColorRGBA(0.08f, 0.08f, 0.1f, 0.85f));
            hudBuffTracks[i].setCullHint(Spatial.CullHint.Always);
            hudNode.attachChild(hudBuffTracks[i]);
            hudBuffFills[i] = makeHudQuad(buffX + 2f * hudSx, buffY + 2f * hudSy,
                    Math.max(1f, buffW - 4f * hudSx), Math.max(1f, buffH - 4f * hudSy),
                    HUD_BUFF_COLORS[i]);
            hudBuffFills[i].setCullHint(Spatial.CullHint.Always);
            hudNode.attachChild(hudBuffFills[i]);
            hudBuffSecs[i] = new BitmapText(font, false);
            hudBuffSecs[i].setSize(13f * hudSy);
            hudBuffSecs[i].setColor(ColorRGBA.White);
            hudBuffSecs[i].setText("");
            hudBuffSecs[i].setLocalTranslation(buffX + buffW + 4f * hudSx, buffY - 1f * hudSy, 0);
            hudBuffSecs[i].setCullHint(Spatial.CullHint.Always);
            hudNode.attachChild(hudBuffSecs[i]);
            buffX += buffW + buffGap;
        }

        // attack cooldown indicator, centered along the bottom edge. The fill
        // drains right-to-left as the current weapon comes off cooldown.
        float cdW = 200f * hudSx;
        float cdH = 12f * hudSy;
        float cdX = (screenW - cdW) / 2f;
        float cdY = 26f * hudSy;
        makeHudQuad(cdX, cdY, cdW, cdH, new ColorRGBA(0.08f, 0.08f, 0.10f, 0.85f));
        hudCooldownFill = makeHudQuad(cdX + inset, cdY + inset,
                (cdW - inset * 2f), cdH - inset * 2f,
                new ColorRGBA(0.95f, 0.75f, 0.2f, 1f));

        buildToolbarHUD(font, screenW);
        guiNode.attachChild(hudNode);
        updateHUD();
    }

    /** Five semi-transparent quick-use slots (keys 1-5) centered above the bar. */
    private void buildToolbarHUD(BitmapFont font, int screenW) {
        hudToolbarSlot = 46f * hudSx;
        hudToolbarGap = 8f * hudSx;
        float total = Inventory.TOOLBAR_SIZE * hudToolbarSlot
                + (Inventory.TOOLBAR_SIZE - 1) * hudToolbarGap;
        hudToolbarLeft = (screenW - total) / 2f;
        hudToolbarBottom = 46f * hudSy;

        for (int i = 0; i < Inventory.TOOLBAR_SIZE; i++) {
            float x = hudToolbarLeft + i * (hudToolbarSlot + hudToolbarGap);

            // transparent background so the world still shows through
            hudToolbarSlots[i] = makeHudQuad(x, hudToolbarBottom, hudToolbarSlot, hudToolbarSlot,
                    new ColorRGBA(0.04f, 0.05f, 0.06f, 0.28f));
            hudToolbarBorders[i] = makeHudQuad(x - 1f, hudToolbarBottom - 1f,
                    hudToolbarSlot + 2f, hudToolbarSlot + 2f,
                    new ColorRGBA(0.55f, 0.57f, 0.60f, 0.28f));

            hudToolbarIcons[i] = makeHudQuad(x + 3f, hudToolbarBottom + 3f,
                    hudToolbarSlot - 6f, hudToolbarSlot - 6f,
                    new ColorRGBA(0.25f, 0.25f, 0.28f, 1f));
            hudToolbarIcons[i].setCullHint(Spatial.CullHint.Always);

            hudToolbarIconsTex[i] = makeHudQuad(x + 3f, hudToolbarBottom + 3f,
                    hudToolbarSlot - 6f, hudToolbarSlot - 6f, ColorRGBA.White);
            hudToolbarIconsTex[i].setCullHint(Spatial.CullHint.Always);

            hudToolbarCounts[i] = new BitmapText(font, false);
            hudToolbarCounts[i].setSize(13f * hudSy);
            hudToolbarCounts[i].setColor(new ColorRGBA(0.98f, 0.88f, 0.6f, 1f));
            hudNode.attachChild(hudToolbarCounts[i]);

            hudToolbarNumbers[i] = new BitmapText(font, false);
            hudToolbarNumbers[i].setSize(12f * hudSy);
            hudToolbarNumbers[i].setColor(new ColorRGBA(0.85f, 0.88f, 0.92f, 0.9f));
            hudToolbarNumbers[i].setText("" + (i + 1));
            hudToolbarNumbers[i].setLocalTranslation(x + 3f * hudSx,
                    hudToolbarBottom + hudToolbarSlot - 12f * hudSy, 0f);
            hudNode.attachChild(hudToolbarNumbers[i]);
        }
        hudToolbarBuilt = true;
    }

    /** Paints the current inventory toolbar onto the HUD slots each frame. */
    private void refreshToolbarHud() {
        if (!hudToolbarBuilt || inventory == null) return;

        // which toolbar slot holds the weapon the combat controller is using?
        int armedWeapon = -1;
        if (combat != null && combat.getEquippedWeaponId() != null) {
            String armed = combat.getEquippedWeaponId();
            for (int i = 0; i < Inventory.TOOLBAR_SIZE; i++) {
                Slot s = inventory.getToolbarSlot(i);
                if (!s.isEmpty() && armed.equals(s.itemId)) {
                    armedWeapon = i;
                    break;
                }
            }
        }

        for (int i = 0; i < Inventory.TOOLBAR_SIZE; i++) {
            float x = hudToolbarLeft + i * (hudToolbarSlot + hudToolbarGap);
            Slot s = inventory.getToolbarSlot(i);
            Geometry icon = hudToolbarIcons[i];
            Geometry tex = hudToolbarIconsTex[i];

            if (s.isEmpty()) {
                icon.setCullHint(Spatial.CullHint.Always);
                tex.setCullHint(Spatial.CullHint.Always);
                hudToolbarCounts[i].setText("");
            } else {
                Item item = s.getItem();
                Texture t = loadHudIcon(item.iconPath);
                if (t != null) {
                    tex.getMaterial().setTexture("ColorMap", t);
                    tex.getMaterial().setColor("Color", ColorRGBA.White);
                    tex.setCullHint(Spatial.CullHint.Never);
                    icon.setCullHint(Spatial.CullHint.Always);
                } else {
                    tex.setCullHint(Spatial.CullHint.Always);
                    icon.setCullHint(Spatial.CullHint.Never);
                    icon.getMaterial().setColor("Color",
                            new ColorRGBA(item.iconColor.r, item.iconColor.g, item.iconColor.b, 0.9f));
                }
                hudToolbarCounts[i].setText(s.count > 1 ? "" + s.count : "");
                float ch = hudToolbarCounts[i].getLineHeight();
                hudToolbarCounts[i].setLocalTranslation(
                        x + hudToolbarSlot - 3f * hudSx - hudToolbarCounts[i].getLineWidth(),
                        hudToolbarBottom + ch / 2f + 2f * hudSy, 0f);
            }

            // armed weapon glows green so it's obvious what you'll swing
            hudToolbarBorders[i].getMaterial().setColor("Color",
                    i == armedWeapon
                            ? new ColorRGBA(0.35f, 0.85f, 0.45f, 0.9f)
                            : new ColorRGBA(0.55f, 0.57f, 0.60f, 0.28f));
        }
    }

    /** Returns the toolbar index under the cursor, or -1. */
    private int hudToolbarSlotAt(float px_, float py_) {
        if (!hudToolbarBuilt) return -1;
        for (int i = 0; i < Inventory.TOOLBAR_SIZE; i++) {
            float x = hudToolbarLeft + i * (hudToolbarSlot + hudToolbarGap);
            if (px_ >= x && px_ <= x + hudToolbarSlot
                    && py_ >= hudToolbarBottom && py_ <= hudToolbarBottom + hudToolbarSlot) {
                return i;
            }
        }
        return -1;
    }

    /** Loads a HUD item icon by path, cached and missing-file-safe. */
    private Texture loadHudIcon(String path) {
        if (path == null) return null;
        Texture tex = hudIconCache.get(path);
        if (tex == null) {
            try {
                tex = assetManager.loadTexture(path);
            } catch (Exception e) {
                tex = null;
            }
            hudIconCache.put(path, tex);
        }
        return tex;
    }

    private void updateHUD() {
        if (hudHpFill == null || playerStats == null) return;

        float maxHp = Math.max(1f, playerStats.getMaxHealth());
        float ratio = Math.max(0f, Math.min(1f, playerStats.getHealth() / maxHp));

        // hp fill goes green -> yellow -> red as health falls
        ColorRGBA fillColor;
        if (ratio > 0.5f) {
            fillColor = new ColorRGBA(0.2f, 0.85f, 0.25f, 1f);
        } else if (ratio > 0.25f) {
            fillColor = new ColorRGBA(0.9f, 0.8f, 0.15f, 1f);
        } else {
            fillColor = new ColorRGBA(0.9f, 0.2f, 0.15f, 1f);
        }
        hudHpFill.getMaterial().setColor("Color", fillColor);
        hudHpFill.setLocalScale(ratio, 1f, 1f);
        hudHpText.setText((int) playerStats.getHealth() + " / " + (int) maxHp);

        // cooldown bar mirrors the weapon's remaining cooldown, draining when ready
        if (hudCooldownFill != null) {
            float pending = combat != null ? combat.getCooldownFraction() : 0f;
            float fill = 1f - pending;
            // leave a faint sliver so an "almost ready" bar doesn't vanish
            hudCooldownFill.setLocalScale(Math.max(0.02f, fill), 1f, 1f);
        }

        int remaining = (stageManager != null && stageManager.getActiveEnemies() != null)
                ? stageManager.getActiveEnemies().size() : 0;
        if (remaining > 0) {
            String stageName = stageManager != null ? stageManager.getCurrentStageName() : "?";
            hudEnemiesText.setText(stageName + "  |  Enemies: " + remaining);
        } else {
            hudEnemiesText.setText("Enemies: 0");
        }

        // potion buff timers: show each active buff as a draining colored pill. When
        // the timer hits zero the pill vanishes and the stats have already reset.
        PlayerStats.Buff[] buffs = PlayerStats.Buff.values();
        for (int i = 0; i < buffs.length; i++) {
            float frac = playerStats.buffFraction(buffs[i]);
            boolean active = frac > 0f;
            Spatial.CullHint hint = active ? Spatial.CullHint.Never : Spatial.CullHint.Always;
            hudBuffTracks[i].setCullHint(hint);
            hudBuffFills[i].setCullHint(hint);
            hudBuffSecs[i].setCullHint(hint);
            if (active) {
                hudBuffFills[i].setLocalScale(frac, 1f, 1f);
                hudBuffSecs[i].setText("" + (int) Math.ceil(playerStats.buffRemaining(buffs[i])));
            }
        }

        refreshToolbarHud();
    }

    /**
     * Spawn chests and NPCs in the Sanctuary/Forest biome.
     */
    private void spawnChestsAndNPCs() {
        // scatter three chests around the area
        Chest chest1 = new Chest(new Vector3f(10f, 0.6f, -15f));
        chest1.build(assetManager, rootNode, bulletAppState);
        chest1.addLoot("health_potion", 3);
        chest1.addLoot("regen_potion", 2);
        chest1.addLoot("iron_ingot", 5);
        chest1.addLoot("iron_ore", 6);
        chest1.setChestUI(chestUI);
        interactables.add(chest1);
        interactablePhysics.add(chest1.getPhysics());

        Chest chest2 = new Chest(new Vector3f(-20f, 0.6f, 10f));
        chest2.build(assetManager, rootNode, bulletAppState);
        chest2.addLoot("leather", 8);
        chest2.addLoot("dungeon_key", 1);
        chest2.addLoot("health_potion", 2);
        chest2.addLoot("wolf_fang", 1);
        chest2.setChestUI(chestUI);
        interactables.add(chest2);
        interactablePhysics.add(chest2.getPhysics());

        Chest chest3 = new Chest(new Vector3f(5f, 0.6f, 25f));
        chest3.build(assetManager, rootNode, bulletAppState);
        chest3.addLoot("iron_sword", 1);
        chest3.addLoot("iron_ingot", 10);
        chest3.addLoot("speed_potion", 2);
        chest3.addLoot("strength_potion", 1);
        chest3.addLoot("hunters_blade", 1);
        chest3.setChestUI(chestUI);
        interactables.add(chest3);
        interactablePhysics.add(chest3.getPhysics());

        System.out.println("Spawned 3 chests in Sanctuary");

        // two shopkeeper NPCs
        NPC merchant1 = new NPC("Merchant Elara", new Vector3f(-10f, 0f, -20f));
        merchant1.build(assetManager, rootNode, bulletAppState);
        // stock the shop with items and prices
        merchant1.addShopItem("health_potion", 15);
        merchant1.addShopItem("speed_potion", 18);
        merchant1.addShopItem("strength_potion", 22);
        merchant1.addShopItem("critical_potion", 25);
        merchant1.addShopItem("regen_potion", 16);
        merchant1.addShopItem("iron_ingot", 25);
        merchant1.addShopItem("iron_ore", 8);
        merchant1.addShopItem("leather", 10);
        merchant1.addShopItem("wolf_fang", 35);
        merchant1.addShopItem("blood_shard", 45);
        merchant1.addShopItem("ember_core", 55);
        merchant1.addShopItem("void_crystal", 95);
        merchant1.setShopUI(shopUI);
        interactables.add(merchant1);
        interactablePhysics.add(merchant1.getPhysics());

        NPC merchant2 = new NPC("Blacksmith Kain", new Vector3f(15f, 0f, 5f));
        merchant2.build(assetManager, rootNode, bulletAppState);
        // stock the shop with items and prices
        merchant2.addShopItem("iron_sword", 100);
        merchant2.addShopItem("hunters_blade", 120);
        merchant2.addShopItem("heavy_blade", 140);
        merchant2.addShopItem("iron_helmet", 80);
        merchant2.addShopItem("iron_chestplate", 120);
        merchant2.addShopItem("iron_leggings", 90);
        merchant2.addShopItem("iron_boots", 60);
        merchant2.addShopItem("dungeon_key", 50);
        merchant2.setShopUI(shopUI);
        interactables.add(merchant2);
        interactablePhysics.add(merchant2.getPhysics());

        interactablesBuilt = true;
        System.out.println("Spawned 2 NPCs in Sanctuary");
    }

    /**
     * Keep Sanctuary's chests and NPCs in sync with the current stage:
     * build them when entering the Sanctuary, remove them (and their physics)
     * when leaving to any other biome.
     */
    private void updateSanctuaryInteractables() {
        if (stageManager == null) return;

        boolean inSanctuary = "Sanctuary".equals(stageManager.getCurrentStageName());

        if (inSanctuary && !interactablesBuilt) {
            spawnChestsAndNPCs();
        } else if (!inSanctuary && interactablesBuilt) {
            cleanupInteractables();
        }
    }

    private void cleanupInteractables() {
        for (Interactable interactable : interactables) {
            interactable.cleanup();
        }
        interactables.clear();
        for (RigidBodyControl body : interactablePhysics) {
            bulletAppState.getPhysicsSpace().remove(body);
        }
        interactablePhysics.clear();
        interactablesBuilt = false;
    }

    private void handleInteract() {
        // see if anything interactable is in range
        Vector3f playerPos = playerNode.getWorldTranslation();
        for (Interactable interactable : interactables) {
            if (interactable.isInRange(playerPos, INTERACT_RANGE)) {
                interactable.interact();
                return; // only take the closest one
            }
        }
    }
}