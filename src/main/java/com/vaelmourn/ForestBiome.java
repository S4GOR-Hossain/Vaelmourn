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
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
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
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
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

    private final Vector3f movementVelocity = new Vector3f();

    private final Quaternion turnQuat = new Quaternion();


    private final float MOVE_ACCELERATION_RATE = 100f;
    private final float MOVE_BRAKE_RATE = 92f;
    private final float AIR_ACCELERATION_RATE = 85f;
    private final float AIR_BRAKE_RATE = 10f;
   
    private final float GROUND_TURN_RATE_MIN = 7f;  // rad/s — narrow corrections snap shut
    private final float GROUND_TURN_RATE_MAX = 13f; // rad/s — wide reversals still sweep
    private final float AIR_TURN_RATE_MIN = 8f;
    private final float AIR_TURN_RATE_MAX = 16f;
    // below this the current heading isn't meaningful yet (start from rest → face input directly)
    private final float MOMENTUM_EPS = 0.01f;
    // camera leans toward travel (~0.9 units at full run, zero when stationary)
    private final float CAMERA_LEAN_FACTOR = 0.028f;
    // run clip plays hot in proportion to actual speed — never forced to max
    private final float MOVE_ANIM_MIN_SPEED = 1.0f;
    private final float MOVE_ANIM_MAX_SPEED = 1.6f;

    private boolean dodging = false;
    private float dodgeTimer = 0f;
    private float dodgeCooldown = 0f;
    // dodge is a short burst of momentum, not a defensive roll
    private final float DODGE_DURATION = 0.28f;
    private final float DODGE_DURATION_FALLBACK = 0.28f;
    private float rollClipLength = DODGE_DURATION_FALLBACK;
    private final float DODGE_COOLDOWN_TIME = 0.6f;
    private final float DODGE_SPEED = 45f;
    private final Vector3f dodgeDirection = new Vector3f();
    private final Vector3f lastMoveDir = new Vector3f();

    private boolean crouching = false;
    private final float CROUCH_SPEED_MULTIPLIER = 0.5f;
    private String crouchIdleClipName = null;
    private String crouchWalkClipName = null;

    private boolean jumpHeld = false;
    private float jumpBufferTimer = 0f;
    private float coyoteTimer = 0f;
    private String runClipName = null;
    private String jumpClipName = null;
    private String fallClipName = null;
    private final float JUMP_FORCE = 12f;
    // jump forgiveness: coyote lets you jump just after leaving a ledge, and
    // buffered presses fire the instant you land — no more swallowed inputs
    private final float COYOTE_TIME = 0.12f;
    private final float JUMP_BUFFER_TIME = 0.10f;

    private final float BASE_GRAVITY = 24f;
    private final float RISE_GRAVITY_MULTIPLIER = 0.95f; // smooth rise to a real apex
    // letting go of jump mid-rise spikes gravity so taps give short hops
    private final float JUMP_CUT_GRAVITY_MULTIPLIER = 3.2f;
    private final float FALL_GRAVITY_MULTIPLIER = 2.0f;  // decisive fall, quick landing

    private final float BASE_FOV = 45f;
    private final float FOV_SPEED_MAX_INCREASE = 8f; // +8° at full run
    private final float FOV_RESPONSE = 6f;           // how eagerly FOV tracks velocity
    private float currentFov = BASE_FOV;

    private boolean wasAirborne = true;
    private float landDip = 0f;
    private float airborneFallPeak = 0f;
    private final float LANDING_FEEDBACK_THRESHOLD = 14f; // only real falls dip the cam
    private final float LANDING_FEEDBACK_SCALE = 0.03f;
    private final float LANDING_FEEDBACK_MAX_DIP = 0.35f;
    private final float LANDING_FEEDBACK_RECOVERY = 10f;   // springs back in ~0.1s
    private final float LANDING_FEEDBACK_SHAKE = 0.3f;

    private float camYaw = 0f;
    private float camPitch = 0.3f;
    private float camDistance = 8f;
    private final float HORIZONTAL_SENSITIVITY = 3f;
    private final float VERTICAL_SENSITIVITY = 1f;

    // Camera collision keeps the orbit camera out of terrain: a ray from the look
    // pivot eases it inward when blocked, back out when clear. MARGIN keeps it a
    // hair off the surface (no z-fight or clipping).
    private float camCollisionK = 1f;
    private final float CAMERA_COLLISION_MARGIN = 0.55f;
    private final float CAMERA_COLLISION_SMOOTHING = 14f;
    private final float CAMERA_COLLISION_MIN_DIST = 0.4f; // never cram the lens into the player

    private Weapons weapons;
    private CombatController combat;
    private CombatEffects effects;

    private Inventory inventory;
    private PlayerStats playerStats;
    private InventoryUI inventoryUI;
    private boolean inventoryOpen = false;

    private StageManager stageManager;

    private Node hudNode;
    private Geometry hudHpFill;
    private BitmapText hudHpText;
    private BitmapText hudEnemiesText;
    private Geometry hudCooldownFill;
    private float hudSx = 1f;
    private float hudSy = 1f;
    private static final float HUD_HP_FULL_WIDTH = 252f; // the fill bar's inner width at 1080p

    private final Geometry[] hudBuffTracks = new Geometry[PlayerStats.Buff.values().length];
    private final Geometry[] hudBuffFills = new Geometry[PlayerStats.Buff.values().length];
    private final BitmapText[] hudBuffSecs = new BitmapText[PlayerStats.Buff.values().length];
    private static final ColorRGBA[] HUD_BUFF_COLORS = {
            new ColorRGBA(0.35f, 0.85f, 1.00f, 1f),  // Speed
            new ColorRGBA(1.00f, 0.50f, 0.15f, 1f),  // Strength
            new ColorRGBA(1.00f, 0.85f, 0.20f, 1f),  // Critical
            new ColorRGBA(0.35f, 0.90f, 0.40f, 1f),  // Regen
    };

    private PlayerBuffEffects buffEffects;

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

    private boolean playerDead = false;
    private DeathScreen deathScreen;

    private Node forestZoneNode;

    private EnvironmentCamera envCam;
    private boolean lightProbeBaked = false;

    private List<Interactable> interactables = new ArrayList<>();
    private List<RigidBodyControl> interactablePhysics = new ArrayList<>();
    private boolean interactablesBuilt = false;
    private ShopUI shopUI;
    private ChestUI chestUI;
    private static final float INTERACT_RANGE = 3.5f;

    private final AnalogListener analogListener = (name, value, tpf) -> {
        if (inventoryOpen) return; // don't orbit the camera while browsing
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

        // Lemur must be up before any GUI widgets are made
        GuiGlobals.initialize(this);
        // The Glass theme comes from a Groovy stylesheet that throws on newer JDKs,
        // so guard it; our HUD is plain jME3, so a missing theme costs nothing.
        try {
            BaseStyles.loadGlassStyle();
        } catch (Throwable t) {
            System.err.println("Lemur Glass style unavailable: " + t);
        }

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

        // kill the default stats/FPS overlay and debug keys (they paint over the HUD)
        stateManager.detach(stateManager.getState(StatsAppState.class));
        stateManager.detach(stateManager.getState(DebugKeysAppState.class));

        Spatial playerModel = assetManager.loadModel("Models/Characters/Player/player.gltf");

        // Hardware (GPU) skinning of this model crashes the AMD OpenGL driver
        // (EXCEPTION_ACCESS_VIOLATION in glBufferData), so we stick to CPU skinning.
        disableHardwareSkinning(playerModel);

        playerNode = new Node("Player");
        playerNode.attachChild(playerModel);
        rootNode.attachChild(playerNode);

        playerControl = new BetterCharacterControl(0.5f, 1.8f, 1f);
        playerControl.setJumpForce(new Vector3f(0, JUMP_FORCE, 0));
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
            runClipName = findClipContaining("run");
            jumpClipName = findClipContaining("jump");
            fallClipName = findClipContaining("fall", "inair", "airborne", "midair");

            if (crouchIdleClipName == null) {
                System.out.println("WARNING: No crouch clip found.");
            }

            if (jumpClipName == null && fallClipName == null) {
                System.out.println("WARNING: No jump/fall clip found.");
            }
        }

        weapons = new Weapons();
        combat = new CombatController(cam, playerNode, animComposer, weapons);
        combat.equip("iron_sword"); // default loadout

        effects = new CombatEffects(assetManager, rootNode, playerNode);
        combat.setEffects(effects);
        SoundManager.init(assetManager, rootNode);
        buffEffects = new PlayerBuffEffects(assetManager, playerNode);

        ItemRegistry.registerDefaults();
        inventory = new Inventory();
        playerStats = new PlayerStats();
        playerStats.setDamageListener((amount, currentHealth) -> {
            if (effects != null) effects.onPlayerDamaged();
            SoundManager.playPlayerHurt();
        });
        playerStats.setInventory(inventory);
        combat.setPlayerStats(playerStats);
combat.equip("iron_sword"); // now that stats exist, push the default sword's values in
        grantStarterLoadout();
        // Fixed 29-stage rotation: each biome = 4 normal stages -> boss arena ->
        // safe hub (minus the Kingdom Court, which only has 3 regular stages),
        // looping back to a fresh Sanctuary after the Fallen King.
        stageManager = new StageManager(assetManager, rootNode, bulletAppState, this);
        stageManager.setPlayerStats(playerStats);
        stageManager.addStage(new SanctuaryStage());
        stageManager.addStage(new DarkwoodStage(1));
        stageManager.addStage(new DarkwoodStage(2));
        stageManager.addStage(new DarkwoodStage(3));
        stageManager.addStage(new DarkwoodStage(4));
        stageManager.addStage(new TreeWardenBossStage());
        stageManager.addStage(new SanctuaryStage());
        stageManager.addStage(new AshenWastesStage(1));
        stageManager.addStage(new AshenWastesStage(2));
        stageManager.addStage(new AshenWastesStage(3));
        stageManager.addStage(new AshenWastesStage(4));
        stageManager.addStage(new HellhoundBossStage());
        stageManager.addStage(new SanctuaryStage());
        stageManager.addStage(new FrozenDepthsStage(1));
        stageManager.addStage(new FrozenDepthsStage(2));
        stageManager.addStage(new FrozenDepthsStage(3));
        stageManager.addStage(new FrozenDepthsStage(4));
        stageManager.addStage(new FrostGiantBossStage());
        stageManager.addStage(new SanctuaryStage());
        stageManager.addStage(new JungleStage(1));
        stageManager.addStage(new JungleStage(2));
        stageManager.addStage(new JungleStage(3));
        stageManager.addStage(new JungleStage(4));
        stageManager.addStage(new BeekeeperBossStage());
        stageManager.addStage(new SanctuaryStage());
        stageManager.addStage(new DarkRoyaleKingdomCourtStage(1));
        stageManager.addStage(new DarkRoyaleKingdomCourtStage(2));
        stageManager.addStage(new DarkRoyaleKingdomCourtStage(3));
        stageManager.addStage(new FallenKingBossStage());            
        stageManager.loadInitialStage(playerControl);
        combat.setEnemies(stageManager.getActiveEnemies());

        // Shop and Chest UIs must exist before the interactables hold a reference
        // to them, or pressing F near a chest/NPC does nothing.
        shopUI = new ShopUI(assetManager, renderManager, inputManager, cam, guiNode,
                inventory, playerStats, settings.getWidth(), settings.getHeight());
        chestUI = new ChestUI(assetManager, renderManager, inputManager, cam, guiNode,
                inventory, settings.getWidth(), settings.getHeight());

        spawnChestsAndNPCs();

        inventoryUI = new InventoryUI(assetManager, renderManager, inputManager, cam,
                inventory, playerStats, playerModel,
                settings.getWidth(), settings.getHeight());
        guiNode.attachChild(inventoryUI.getNode());
        inventoryUI.setWeaponEquipHandler(weaponId -> {
            if (combat != null) combat.equip(weaponId);
        });

        buildHUD(settings.getWidth(), settings.getHeight());

        // built last so it always renders on top of every other GUI
        deathScreen = new DeathScreen(assetManager, inputManager, settings.getWidth(), settings.getHeight());
        deathScreen.setRespawnAction(this::startNewRun);
        deathScreen.setMainMenuAction(() -> { /* MAIN MENU is a safe no-op stub */ });
        guiNode.attachChild(deathScreen.getNode());
        deathScreen.setVisible(false);

        initKeys();

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(false);

        inputManager.addMapping("MouseX+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MouseX-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MouseY+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("MouseY-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));

        inputManager.addListener(analogListener, "MouseX+", "MouseX-", "MouseY+", "MouseY-");

        // Light-probe baking re-renders the scene into an env map every frame and
        // crashed the AMD OpenGL driver, so the probe is left unattached.
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

        // scale the hitbox with the model — a fixed capsule let you walk through trees
        tree.updateModelBound();
        float trunkRadius = 1.1f;
        float collarHeight = 5f;
        if (tree.getWorldBound() instanceof BoundingBox bbox) {
            Vector3f extent = new Vector3f();
            bbox.getExtent(extent);
            trunkRadius = FastMath.clamp(Math.max(extent.x, extent.z) * 0.7f, 1.0f, 3.2f);
            collarHeight = FastMath.clamp(2f * extent.y, 4f, 10f);
        }
        BoxCollisionShape trunkShape = new BoxCollisionShape(new Vector3f(trunkRadius, collarHeight / 2f, trunkRadius));
        RigidBodyControl physics = new RigidBodyControl(trunkShape, 0);
        physics.setPhysicsLocation(new Vector3f(x, collarHeight / 2f, z));

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

    /** Forces CPU skinning; hardware skinning crashes this AMD OpenGL driver. */
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

        inputManager.addMapping("Inventory", new KeyTrigger(KeyInput.KEY_E));
        inputManager.addMapping("Interact", new KeyTrigger(KeyInput.KEY_F));
        inputManager.addMapping("AttackPrimary", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("AttackSecondary", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
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

    /** Dev tool bound to L: teleports to the next stage in the rotation. */
    private void devSkipStage() {
        if (stageManager == null) return;
        System.out.println("[DEV] L pressed — skipping past " + stageManager.getCurrentStageName());
        stageManager.advanceStage();
        // warp to the spawn point; the skip bypasses the portal that normally
        // does this, so the player would otherwise drop in mid-air
        if (stageManager.getCurrentStage() != null && playerControl != null) {
            playerControl.warp(stageManager.getCurrentStage().getPlayerSpawnPoint());
        }
    }

    /** Locks all gameplay input and hands the cursor to the death screen. */
    private void triggerDeath() {
        if (playerDead) return;
        playerDead = true;
        SoundManager.playPlayerDeath();

        if (inventoryOpen && inventoryUI != null) inventoryUI.setVisible(false);
        inventoryOpen = false;
        if (chestUI != null && chestUI.isOpen()) chestUI.closeChest();
        if (shopUI != null && shopUI.isOpen()) shopUI.closeShop();

        // lock gameplay outright: no movement, no attacks
        forward = backward = left = right = false;
        jumpHeld = false;
        crouching = false;
        dodging = false;
        movementVelocity.set(0, 0, 0);
        if (playerControl != null) {
            playerControl.setWalkDirection(Vector3f.ZERO);
            playerControl.setDucked(false);
        }

        playAnim("Death");

        if (deathScreen != null) deathScreen.setVisible(true);
        inputManager.setCursorVisible(true);
        hudCursorShown = true;
        System.out.println("[Death] The Cursed One has fallen.");
    }

    /**
     * RESPAWN: ends the run and boots a brand-new one, identical to a fresh
     * launch. The dead flag drops first, so a second click can't reset twice.
     */
    private void startNewRun() {
        if (!playerDead) return;
        playerDead = false;

        // 1. wipe the run: inventory and stats back to fresh defaults
        if (inventory != null) inventory.clearAll();
        if (playerStats != null) playerStats.resetToDefaults();
        grantStarterLoadout();
        if (combat != null) combat.equip("iron_sword");

        // 2. back to a brand-new Sanctuary (stage 0, loop 0, difficulty 1.0)
        if (stageManager != null) {
            stageManager.resetToFirstStage(playerControl);
            if (combat != null) combat.setEnemies(stageManager.getActiveEnemies());
        }

        // 3. chests/NPCs get rebuilt fresh next frame in the Sanctuary
        cleanupInteractables();

        // 4. camera, FOV and every motion timer back to defaults
        camYaw = 0f;
        camPitch = 0.3f;
        camDistance = 8f;
        camCollisionK = 1f;
        currentFov = BASE_FOV;
        if (combat != null) combat.setSpeedFovBoost(0f);
        movementVelocity.set(0, 0, 0);
        forward = backward = left = right = false;
        jumpHeld = false;
        crouching = false;
        dodging = false;
        jumpBufferTimer = 0f;
        coyoteTimer = 0f;
        dodgeCooldown = 0f;
        dodgeTimer = 0f;
        landDip = 0f;
        airborneFallPeak = 0f;

        playAnim("Idle");
        if (deathScreen != null) deathScreen.setVisible(false);
        inputManager.setCursorVisible(false);
        hudCursorShown = false;

        System.out.println("[Respawn] A fresh run begins in the Sanctuary.");
    }

    /** Starting pack: potions, materials, the iron starter set, +250 dust, +40 XP. */
    private void grantStarterLoadout() {
        if (inventory == null || playerStats == null) return;

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
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        // a dead player's only interaction is the death screen
        if (playerDead) {
            if (isPressed && "AttackPrimary".equals(name) && deathScreen != null) {
                Vector2f cur = inputManager.getCursorPosition();
                deathScreen.handleClick(cur.x, cur.y);
            }
            return;
        }
        switch (name) {
            case "DevSkipStage":
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
                // when a UI is open, hand the click to it instead of combat
                if (isUiOpen()) {
                    if (isPressed && inventoryOpen && inventoryUI != null) inventoryUI.handlePrimaryClick();
                    return;
                }
                if (isPressed && inventoryUI != null && inventoryUI.isContextMenuOpen()) {
                    inventoryUI.handlePrimaryClick();
                    return;
                }
                break;

            case "AttackSecondary":
                // only on the press, so mouse-up doesn't close the menu it opened
                if (isUiOpen()) {
                    if (isPressed && inventoryOpen && inventoryUI != null) inventoryUI.handleSecondaryClick();
                    return;
                }
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
                // queued so it works with coyote time and buffers a mid-air press
                if (isPressed) queueJump();
                else jumpHeld = false;
                break;
            case "Dodge":
                if (isPressed && !dodging && dodgeCooldown <= 0f) startDodge();
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

            case "Hotbar0":
            case "Hotbar1":
            case "Hotbar2":
            case "Hotbar3":
            case "Hotbar4":
                if (isPressed) useToolbarSlot(name.charAt("Hotbar".length()) - '0');
                break;
        }
    }

    private void useToolbarSlot(int slotIndex) {
        if (inventory == null) return;
        Slot slot = inventory.getToolbarSlot(slotIndex);
        if (slot == null || slot.isEmpty()) return;
        Item item = ItemRegistry.get(slot.itemId);
        if (item == null) return;

        switch (item.getGroup()) {
            case CONSUMABLE:
                if (playerStats.consume(item)) inventory.removeFromToolbar(slotIndex, 1);
                break;
            case WEAPON:
                if (combat != null) combat.equip(item.id);
                break;
            default:
                break;
        }
    }

    /** True if any UI overlay is open (the world is then paused). */
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
            inputManager.setCursorVisible(true);
            if (playerControl != null) playerControl.setWalkDirection(Vector3f.ZERO);
            forward = backward = left = right = false;
        } else {
            inputManager.setCursorVisible(false);
        }
    }

    private void startDodge() {
        dodging = true;
        dodgeTimer = DODGE_DURATION;
        dodgeCooldown = DODGE_COOLDOWN_TIME;
        // dodge in the last held direction so it reads as a momentum burst;
        // with nothing held it still fires, straight ahead of the camera
        dodgeDirection.set(lastMoveDir);
        if (dodgeDirection.lengthSquared() < 0.01f) {
            dodgeDirection.set(cam.getDirection()).setY(0);
            if (dodgeDirection.lengthSquared() < 0.01f) dodgeDirection.set(0, 0, 1);
        }
        dodgeDirection.normalizeLocal();
        playAnim("Roll");
    }

    /** Jump with coyote time and buffering, per the platforming rules. */
    private void queueJump() {
        jumpHeld = true;
        if (playerControl.isOnGround() || coyoteTimer > 0f) {
            coyoteTimer = 0f;
            playerControl.jump();
        } else {
            jumpBufferTimer = JUMP_BUFFER_TIME;
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (stageManager != null) {
            SoundManager.update(tpf, stageManager.getCurrentStage());
        }

        // the world is frozen for a dead run; only the death screen reacts
        if (playerDead) {
            if (deathScreen != null) deathScreen.update(tpf);
            return;
        }

        if (inventoryUI != null) {
            inventoryUI.update(tpf, cam);
        }

        // the in-world toolbar menu needs the cursor to be aimable
        boolean menuUp = inventoryUI != null && inventoryUI.isContextMenuOpen();
        boolean wantCursor = inventoryOpen || menuUp;
        if (wantCursor != hudCursorShown) {
            inputManager.setCursorVisible(wantCursor);
            hudCursorShown = wantCursor;
        }

        // also ticks while the inventory is open, so the aura shows on drink
        if (buffEffects != null) {
            buffEffects.update(playerStats);
        }

        if (effects != null) {
            effects.update(tpf);
        }

        if (isUiOpen()) {
            return;
        }

        if (!lightProbeBaked && envCam != null && envCam.getApplication() != null) {
            bakeLightProbe();
            lightProbeBaked = true;
        }

        if (stageManager != null) {
            stageManager.update(tpf, playerNode.getWorldTranslation(), playerControl);
        }
        if (combat != null) {
            combat.setEnemies(stageManager != null ? stageManager.getActiveEnemies() : null);
            combat.update(tpf);
            if (playerStats != null) playerStats.update(tpf);

            // fires once: the world freezes this same frame, so no follow-up
            // damage, movement or portal checks can run on a dead run
            if (playerStats != null && playerStats.getHealth() <= 0f && !playerDead) {
                triggerDeath();
                return;
            }
        }

        updateSanctuaryInteractables();

        if (interactables != null && !interactables.isEmpty()
                && "Sanctuary".equals(stageManager.getCurrentStageName())) {
            Vector3f playerPos = playerNode.getWorldTranslation();
            Vector3f facing = cam.getDirection();
            for (Interactable it : interactables) {
                if (it instanceof NPC npc && npc.getVoicePool() > 0) {
                    SoundManager.maybeNpcVoice(npc.getVoicePool(), npc.getPosition(), playerPos, facing, tpf);
                }
            }
        }

        updateHUD();

        if (dodgeCooldown > 0f) {
            dodgeCooldown -= tpf;
        }

        float verticalVelocity = playerControl.getVelocity().y;
        boolean grounded = playerControl.isOnGround();

        if (grounded) {
            coyoteTimer = COYOTE_TIME;
            if (jumpBufferTimer > 0f) {
                jumpBufferTimer = 0f;
                playerControl.jump();
            }
        } else {
            if (coyoteTimer > 0f) coyoteTimer -= tpf;
            if (jumpBufferTimer > 0f) jumpBufferTimer -= tpf;
        }

        // release mid-rise and the gravity spike cuts the ascent, so taps hop
        float gravityScale;
        if (verticalVelocity < 0f) {
            gravityScale = FALL_GRAVITY_MULTIPLIER;
        } else if (!jumpHeld) {
            gravityScale = JUMP_CUT_GRAVITY_MULTIPLIER;
        } else {
            gravityScale = RISE_GRAVITY_MULTIPLIER;
        }
        playerControl.setGravity(new Vector3f(0, -BASE_GRAVITY * gravityScale, 0));

        boolean airborne = !grounded;

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

        float baseSpeed = playerStats != null ? playerStats.getMovementSpeed() : 29f;
        float targetSpeed = crouching ? baseSpeed * CROUCH_SPEED_MULTIPLIER : baseSpeed;

        // Speed is kept separate from heading on purpose. Lerping the whole velocity
        // vector toward the target dips speed to ~70% mid-turn and settles laggy —
        // instead we keep the carried magnitude and *steer* the direction on an
        // angle-scaled arc, so nothing ever snaps 90°.
        Vector3f curDir = new Vector3f(movementVelocity);
        float curSpeed = curDir.length();
        curDir.normalizeLocal();

        if (isMoving) {
            // raw summed input, square-root-2 long on diagonals
            Vector3f inputDir = walkDirection.normalizeLocal();

            if (curSpeed > MOMENTUM_EPS) {
                // atan2(crossY, dot) winds opposite to jME's right-handed +Y
                // rotation, so negate it; otherwise the heading U-turns 180°
                float dot = FastMath.clamp(curDir.dot(inputDir), -1f, 1f);
                float crossY = curDir.x * inputDir.z - curDir.z * inputDir.x;
                float signedAngle = -FastMath.atan2(crossY, dot);
                float angle = FastMath.abs(signedAngle);

                float turnRate = airborne
                        ? AIR_TURN_RATE_MIN + (AIR_TURN_RATE_MAX - AIR_TURN_RATE_MIN) * (angle / FastMath.PI)
                        : GROUND_TURN_RATE_MIN + (GROUND_TURN_RATE_MAX - GROUND_TURN_RATE_MIN) * (angle / FastMath.PI);
                float turnStep = Math.min(angle, turnRate * tpf);

                if (turnStep >= angle) {
                    curDir.set(inputDir);
                } else {
                    turnQuat.fromAngleAxis(turnStep * (float) Math.signum(signedAngle), Vector3f.UNIT_Y);
                    turnQuat.multLocal(curDir);
                }
            } else {
                curDir.set(inputDir);
            }

            float speedRate = targetSpeed > curSpeed
                    ? (airborne ? AIR_ACCELERATION_RATE : MOVE_ACCELERATION_RATE)
                    : (airborne ? AIR_BRAKE_RATE : MOVE_BRAKE_RATE);
            float speedBlend = 1f - FastMath.exp(-speedRate * tpf);
            curSpeed += (targetSpeed - curSpeed) * speedBlend;

            movementVelocity.set(curDir).multLocal(curSpeed);
        } else {
            float brakeRate = airborne ? AIR_BRAKE_RATE : MOVE_BRAKE_RATE;
            movementVelocity.multLocal((float) FastMath.exp(-brakeRate * tpf));
        }

        if (dodging) {
            dodgeTimer -= tpf;
            playerControl.setWalkDirection(dodgeDirection.mult(DODGE_SPEED));
            // stretch the roll clip so the pose matches the faster dodge
            if (rollClipLength > 0f && animComposer != null) {
                animComposer.setGlobalSpeed(Math.min(3f, rollClipLength / DODGE_DURATION));
            }

            if (dodgeTimer <= 0f) {
                dodging = false;
            }
        } else {
            // eased velocity, so strafes and backpedals keep a hint of momentum
            playerControl.setWalkDirection(movementVelocity);

            if (isMoving) {
                playerControl.setViewDirection(walkDirection);
            }
        }

        if (dodging) {
            playAnim("Roll");
        } else if (airborne && (jumpClipName != null || fallClipName != null)) {
            String airClip = verticalVelocity > 0f && jumpClipName != null ? jumpClipName : fallClipName;
            playAnim(airClip != null ? airClip : jumpClipName);
            if (animComposer != null) animComposer.setGlobalSpeed(1f);
        } else if (crouching && crouchIdleClipName != null) {
            playAnim(isMoving && crouchWalkClipName != null ? crouchWalkClipName : crouchIdleClipName);
            if (animComposer != null) animComposer.setGlobalSpeed(1f);
        } else {
            // run clip played hot in proportion to real velocity, not a moonwalk
            String moveClip = runClipName != null ? runClipName : "Walk";
            playAnim(isMoving ? moveClip : "Idle");
            float speedRatio = baseSpeed <= 0f ? 1f
                    : FastMath.clamp(movementVelocity.length() / baseSpeed, 0f, 1f);
            float animScale = isMoving
                    ? FastMath.interpolateLinear(speedRatio, MOVE_ANIM_MIN_SPEED, MOVE_ANIM_MAX_SPEED)
                    : 1f;
            if (animComposer != null) animComposer.setGlobalSpeed(animScale);
        }

Vector3f playerPos = playerNode.getWorldTranslation().clone();

        Vector3f pivot = playerPos.add(0f, 1.5f, 0f);

        Vector3f offset = new Vector3f(
                FastMath.sin(camYaw) * FastMath.cos(camPitch),
                FastMath.sin(camPitch),
                FastMath.cos(camYaw) * FastMath.cos(camPitch)
        ).multLocal(camDistance);

        Vector3f shakeOffset = effects != null ? effects.getShakeOffset(tpf) : Vector3f.ZERO;

        Vector3f leanOffset = movementVelocity.mult(CAMERA_LEAN_FACTOR);

        // Hard landing dips the camera briefly; movement is never locked. Fall
        // speed is the peak reached *while airborne*, since the capsule's own
        // velocity is ~0 by the frame it registers as grounded.
        if (airborne) {
            if (verticalVelocity < 0f) airborneFallPeak = Math.min(airborneFallPeak, verticalVelocity);
        } else {
            if (wasAirborne && airborneFallPeak < -LANDING_FEEDBACK_THRESHOLD) {
                landDip = Math.min(LANDING_FEEDBACK_MAX_DIP, -airborneFallPeak * LANDING_FEEDBACK_SCALE);
                if (effects != null) effects.startShake(LANDING_FEEDBACK_SHAKE, 0.12f);
            }
            airborneFallPeak = 0f;
        }
        wasAirborne = airborne;
        landDip = Math.max(0f, landDip - tpf * LANDING_FEEDBACK_RECOVERY);

        float fovTarget = BASE_FOV + FOV_SPEED_MAX_INCREASE
                * FastMath.clamp(movementVelocity.length() / (baseSpeed <= 0f ? 29f : baseSpeed), 0f, 1f);
        currentFov += (fovTarget - currentFov) * FastMath.clamp(tpf * FOV_RESPONSE, 0f, 1f);
        if (combat != null) combat.setSpeedFovBoost(currentFov - BASE_FOV);

        Vector3f desiredPos = pivot.add(offset).add(0f, -landDip, 0f)
                .add(shakeOffset).addLocal(leanOffset);
        camCollisionK = updateCameraCollision(pivot, desiredPos, tpf);

        Vector3f resolved = pivot.add(offset.mult(camCollisionK))
                .add(0f, -landDip, 0f).add(shakeOffset).addLocal(leanOffset);
        cam.setLocation(resolved);
        cam.lookAt(pivot, Vector3f.UNIT_Y);
    }

    private float updateCameraCollision(Vector3f pivot, Vector3f desiredPos, float tpf) {
        Vector3f rayDir = desiredPos.subtract(pivot);
        float rayLen = rayDir.length();
        float targetK = 1f;
        if (rayLen > 1e-4f) {
            rayDir.multLocal(1f / rayLen);
            CollisionResults hits = new CollisionResults();
            rootNode.collideWith(new Ray(pivot, rayDir), hits);
            for (CollisionResult hit : hits) {
                if (isCameraIgnored(hit.getGeometry())) continue;
                // first hit is the nearest; clamp the camera short of the surface
                targetK = Math.max(CAMERA_COLLISION_MIN_DIST / rayLen,
                        Math.min(1f, (hit.getDistance() - CAMERA_COLLISION_MARGIN) / rayLen));
                break;
            }
        } else {
            targetK = 0f;
        }
        return camCollisionK + (targetK - camCollisionK)
                * (1f - FastMath.exp(-CAMERA_COLLISION_SMOOTHING * tpf));
    }

    private boolean isCameraIgnored(Spatial s) {
        if (s instanceof BitmapText) return true;
        while (s != null) {
            String n = s.getName();
            if (n != null && (n.equals("Player") || n.equals("Boss") || n.equals("Portal")
                    || n.equals("Sky") || n.equals("HitSpark") || n.equals("DeathBurst")
                    || n.startsWith("Enemy"))) {
                return true;
            }
            s = s.getParent();
        }
        return false;
    }

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

        makeHudQuad(margin, 20f * hudSy, barW, barH, new ColorRGBA(0.08f, 0.08f, 0.10f, 0.85f));
        hudHpFill = makeHudQuad(fillX, fillY, HUD_HP_FULL_WIDTH * hudSx, 16f * hudSy,
                new ColorRGBA(0.2f, 0.85f, 0.25f, 1f));

        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        hudHpText = new BitmapText(font, false);
        hudHpText.setSize(14f * hudSy);
        hudHpText.setColor(ColorRGBA.White);
        hudHpText.setText("100 / 100");
        hudHpText.setLocalTranslation(fillX + 4f * hudSx, fillY + 1f * hudSy, 0);
        hudNode.attachChild(hudHpText);

        hudEnemiesText = new BitmapText(font, false);
        hudEnemiesText.setSize(16f * hudSy);
        hudEnemiesText.setColor(new ColorRGBA(0.9f, 0.9f, 0.95f, 1f));
        hudEnemiesText.setText("Enemies: 0");
        hudEnemiesText.setLocalTranslation(margin, (20f * hudSy + barH + 8f * hudSy), 0);
        hudNode.attachChild(hudEnemiesText);

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

    private void buildToolbarHUD(BitmapFont font, int screenW) {
        hudToolbarSlot = 46f * hudSx;
        hudToolbarGap = 8f * hudSx;
        float total = Inventory.TOOLBAR_SIZE * hudToolbarSlot
                + (Inventory.TOOLBAR_SIZE - 1) * hudToolbarGap;
        hudToolbarLeft = (screenW - total) / 2f;
        hudToolbarBottom = 46f * hudSy;

        for (int i = 0; i < Inventory.TOOLBAR_SIZE; i++) {
            float x = hudToolbarLeft + i * (hudToolbarSlot + hudToolbarGap);

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

    private void refreshToolbarHud() {
        if (!hudToolbarBuilt || inventory == null) return;

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

            hudToolbarBorders[i].getMaterial().setColor("Color",
                    i == armedWeapon
                            ? new ColorRGBA(0.35f, 0.85f, 0.45f, 0.9f)
                            : new ColorRGBA(0.55f, 0.57f, 0.60f, 0.28f));
        }
    }

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

        ColorRGBA fillColor;  // green -> yellow -> red as health falls
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

        if (hudCooldownFill != null) {
            float pending = combat != null ? combat.getCooldownFraction() : 0f;
            float fill = 1f - pending;
            // faint sliver so an "almost ready" bar doesn't vanish
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

    private void spawnChestsAndNPCs() {
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

        NPC merchant1 = new NPC("Merchant Elara", new Vector3f(-10f, 0f, -20f));
        merchant1.build(assetManager, rootNode, bulletAppState);
        merchant1.setVoicePool(1);
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
        merchant2.setVoicePool(2);
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
        Vector3f playerPos = playerNode.getWorldTranslation();
        for (Interactable interactable : interactables) {
            if (interactable.isInRange(playerPos, INTERACT_RANGE)) {
                interactable.interact();
                return;
            }
        }
    }
}