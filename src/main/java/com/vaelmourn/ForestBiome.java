package com.vaelmourn;

import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.style.BaseStyles;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.SkinningControl;
import com.jme3.app.SimpleApplication;
import com.jme3.app.DebugKeysAppState;
import com.jme3.app.StatsAppState;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.BetterCharacterControl;
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
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.texture.Texture;

import java.awt.Color;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.Map;
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
   
    private final float GROUND_TURN_RATE_MIN = 7f;  // rad/s Ã¢â‚¬â€ narrow corrections snap shut
    private final float GROUND_TURN_RATE_MAX = 13f; // rad/s Ã¢â‚¬â€ wide reversals still sweep
    private final float AIR_TURN_RATE_MIN = 8f;
    private final float AIR_TURN_RATE_MAX = 16f;
    // below this the current heading isn't meaningful yet (start from rest Ã¢â€ â€™ face input directly)
    private final float MOMENTUM_EPS = 0.01f;

    // Minie's BetterCharacterControl reports onGround from a downward sphere sweep
    // that is only ~0.4 units long, so it drops out for a frame or two over every
    // terrain seam, rock lip and prop edge. Without a grace window each dropout
    // swapped the player onto the air rates (brake 10 vs 92), which read as the
    // character randomly slowing down mid-stride.
    private final float GROUND_GRACE = 0.12f;

    // BetterCharacterControl has NO step offset: any lip the capsule cannot climb
    // stops it dead, and because Minie re-applies only the velocity component along
    // the walk direction, a blocked character has nothing left to push against. These
    // drive a short upward assist so the capsule hops a low obstacle instead of
    // grinding to a halt on it.
    private final float STEP_UP_DELAY = 0.10f;
    // Launch speed for the assist, derived rather than guessed. Gravity here is not
    // BASE_GRAVITY: the block above picks between a 0.95x rise, a 3.2x jump-cut and a
    // 2.0x fall, so a hand-picked 4.2 bought a hop of only 4.2^2/(2*76.8) = 0.11
    // units - invisible, which is why the character still caught on every rock. This
    // is sqrt(2 * g * clearance), which actually clears the requested lip. It has to
    // be a method rather than a field initialiser because the gravity constants it
    // reads are declared further down the class.
    private final float STEP_UP_CLEARANCE = 0.8f;
    // While the assist is lifting, gravity is pinned to the mild step-up multiplier so
    // the 3.2x jump-cut cannot eat the hop half a frame after it starts.
    private final float STEP_UP_GRAVITY_MULTIPLIER = 1.0f;
    private final float STEP_UP_ASSIST_DURATION = 0.22f;
    // consecutive assists allowed before the character is treated as genuinely
    // walled in Ã¢â‚¬â€ stops the assist from turning any wall into a climb
    private final int STEP_UP_MAX_ATTEMPTS = 3;
    // Actual speed below this fraction of the requested speed counts as blocked. Set to
    // 0.6 rather than something low on purpose: a capsule scraping a box edge or a
    // terrain triangle sheds only part of its speed, and that reads as the character
    // "slowing down for no reason" rather than as a hard stop, so the assist has to
    // catch the partial case too and not just a dead stop.
    private final float STEP_UP_SPEED_RATIO = 0.6f;



    private float groundGraceTimer = 0f;
    private float stepUpTimer = 0f;
    private float stepUpAssistTimer = 0f;
    private int stepUpAttempts = 0;

    /**
     * Upward speed the step-up assist needs in order to clear {@link #STEP_UP_CLEARANCE}
     * against the strongest gravity the jump can apply, from
     * {@code v^2 = 2 * g * h}.
     */
    private float stepUpSpeed() {
        return FastMath.sqrt(2f * BASE_GRAVITY * JUMP_CUT_GRAVITY_MULTIPLIER * STEP_UP_CLEARANCE);
    }
    // camera leans toward travel (~0.9 units at full run, zero when stationary)
    private final float CAMERA_LEAN_FACTOR = 0.028f;
    // run clip plays hot in proportion to actual speed Ã¢â‚¬â€ never forced to max
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
    // scaled 1.5x alongside the 29->43.5 base run so the dodge keeps reading as a burst
    private final float DODGE_SPEED = 67.5f;
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
    // buffered presses fire the instant you land Ã¢â‚¬â€ no more swallowed inputs
    private final float COYOTE_TIME = 0.12f;
    private final float JUMP_BUFFER_TIME = 0.10f;

    private final float BASE_GRAVITY = 24f;
    private final float RISE_GRAVITY_MULTIPLIER = 0.95f; // smooth rise to a real apex
    // letting go of jump mid-rise spikes gravity so taps give short hops
    private final float JUMP_CUT_GRAVITY_MULTIPLIER = 3.2f;
    private final float FALL_GRAVITY_MULTIPLIER = 2.0f;  // decisive fall, quick landing

    private final float BASE_FOV = 45f;
    private final float FOV_SPEED_MAX_INCREASE = 8f; // +8Ã‚Â° at full run
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
    private Bombs bombs;
    /** Stage we last ticked bombs for, so an in-flight fuse cannot carry across a portal. */
    private String bombStageName;

    private Inventory inventory;
    private PlayerStats playerStats;
    private InventoryUI inventoryUI;
    private boolean inventoryOpen = false;

    /** Points at the closest living enemy, so the player never hunts blind. */
    private EnemyDirectionArrow enemyArrow;

    private StageManager stageManager;

    private Node hudNode;
    private Geometry hudHpFill;
    private BitmapText hudHpText;
    private BitmapText hudEnemiesText;
    private BitmapText hudSoulText;
    private BitmapText interactPrompt;
    /** transient one-liner for gem pickups, drops and boss-gate refusals */
    private BitmapText hudMessage;
    private float hudMessageTimer;
    private static final float HUD_MESSAGE_SECONDS = 4.5f;
    /** current frame's delta, captured in simpleUpdate so HUD timers can age */
    private float frameDelta = 1f / 60f;
    private float hudSoulRightAnchor;
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

    /**
     * Main menu / settings / about overlay. {@link #gameStarted} stays false until
     * START GAME is pressed, which keeps the world (and its input) frozen behind the
     * menu instead of letting the player walk off during the intro.
     */
    private MainMenuUI menuUI;
    private boolean gameStarted = false;
    /** True while an overlay has the world stopped. Kept in step with
     *  {@link #isUiOpen()} by applyPauseState() so it can never drift. */
    private boolean gamePaused = false;
    private boolean displayModePending = false;
    private int lastOverlayWidth, lastOverlayHeight;
    /** true when the open Settings screen was reached from the main menu, not gameplay */
    private boolean settingsFromMainMenu = false;
    /** true when the main menu was opened from the death screen, so START resumes it */
    private boolean menuFromDeathScreen = false;


    private EnvironmentCamera envCam;
    private boolean lightProbeBaked = false;

    private List<Interactable> interactables = new ArrayList<>();
    private List<Object> interactablePhysics = new ArrayList<>();
    private boolean interactablesBuilt = false;
    private PotionShopUI potionShopUI;
    private RunUpgradeUI runUpgradeUI;
    private ChestUI chestUI;
    private static final float INTERACT_RANGE = 3.5f;

    private final AnalogListener analogListener = (name, value, tpf) -> {
        // any overlay owns the mouse, so the camera must not orbit underneath it
        if (menuUI != null && menuUI.isOpen()) return;
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
                    "Exclusive fullscreen not supported on this device Ã¢â‚¬â€ " +
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

        // The biome gems ship as .fbx and nothing else in the game uses that format,
        // so the plugin's loader has to be registered before anything asks for one.
        // jME3's AssetManager only knows about loaders it is explicitly told about.
        assetManager.registerLoader(com.jme3.scene.plugins.fbx.FbxLoader.class, "fbx");

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
                        "WARNING: No 'Roll' clip found Ã¢â‚¬â€ falling back to " +
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
        bombs = new Bombs(assetManager, rootNode);
        combat.setBombs(bombs);
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
        // Fixed 30-stage rotation: each biome = 4 normal stages -> boss arena ->
        // safe hub (the Kingdom Court only shipped 3 regular stages, so KC4 was
        // added to give the Fallen King the same four-level gate as every other
        // biome's gem), looping back to a fresh Sanctuary after the Fallen King.
        stageManager = new StageManager(assetManager, rootNode, bulletAppState, this);
        enemyArrow = new EnemyDirectionArrow(assetManager, rootNode);
        stageManager.setPlayerStats(playerStats);
        stageManager.setInventory(inventory);
        stageManager.setMessageSink(this::showHudMessage);
        stageManager.setPostStageChange(this::settleAfterStageChange);
        if (enemyArrow != null) enemyArrow.hide();
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
        stageManager.addStage(new DarkRoyaleKingdomCourtStage(4));
        stageManager.addStage(new FallenKingBossStage());            
        stageManager.loadInitialStage(playerControl);
        combat.setEnemies(stageManager.getActiveEnemies());

        // Chest UI must exist before the interactables hold a reference
        // to it, or pressing F near a chest does nothing.
        chestUI = new ChestUI(assetManager, renderManager, inputManager, cam, guiNode,
                inventory, settings.getWidth(), settings.getHeight());
        // the two Sanctuary merchant menus. Also must exist before the NPCs that
        // reference them, and must be registered in isUiOpen() so the world pauses.
        potionShopUI = new PotionShopUI(assetManager, inputManager, cam, guiNode,
                inventory, playerStats, settings.getWidth(), settings.getHeight());
        runUpgradeUI = new RunUpgradeUI(assetManager, inputManager, cam, guiNode,
                playerStats, settings.getWidth(), settings.getHeight());

        spawnChestsAndNPCs();

        inventoryUI = new InventoryUI(assetManager, renderManager, inputManager, cam,
                inventory, playerStats, playerModel,
                settings.getWidth(), settings.getHeight());
        guiNode.attachChild(inventoryUI.getNode());
        inventoryUI.setWeaponEquipHandler(weaponId -> {
            if (combat != null) combat.equip(weaponId);
        });

        buildHUD(settings.getWidth(), settings.getHeight());

        // Main menu last of the permanent overlays so it paints over the HUD, but
        // before the death screen so a death can never be hidden by the menu.
        menuUI = new MainMenuUI(assetManager, guiFont, guiNode,
                settings.getWidth(), settings.getHeight(),
                settings.isFullscreen(), SoundManager.getMasterVolume(),
                this::applyDisplayMode, SoundManager::setMasterVolume);
        menuUI.onStartGame = this::startGame;
        menuUI.onExit = this::stop;
        guiNode.attachChild(menuUI.getNode());

        // built last so it always renders on top of every other GUI
        deathScreen = new DeathScreen(assetManager, inputManager, settings.getWidth(), settings.getHeight());
        deathScreen.setRespawnAction(this::startNewRun);
        deathScreen.setMainMenuAction(this::openMainMenu);
        guiNode.attachChild(deathScreen.getNode());
        deathScreen.setVisible(false);

        initKeys();
        disableEngineEscapeQuit();

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(false);

        inputManager.addMapping("MouseX+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MouseX-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MouseY+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("MouseY-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));

        inputManager.addListener(analogListener, "MouseX+", "MouseX-", "MouseY+", "MouseY-");

        // The main menu is what the player sees on frame one; START GAME is the only
        // way into the world.
        lastOverlayWidth = settings.getWidth();
        lastOverlayHeight = settings.getHeight();
        openMainMenu();

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
        inputManager.addMapping("Hotbar5", new KeyTrigger(KeyInput.KEY_6));
        inputManager.addMapping("ThrowBomb", new KeyTrigger(KeyInput.KEY_Q));

        // ESC is the one global menu key. It never quits the game: the priority order
        // lives in handleEscape() so every overlay keeps its own "close me" behaviour.
        inputManager.addMapping("MenuToggle", new KeyTrigger(KeyInput.KEY_ESCAPE));

        // dev shortcut: L warps straight to the next stage without fighting
        inputManager.addMapping("DevSkipStage", new KeyTrigger(KeyInput.KEY_L));
        // dev shortcuts: grant gems in order for quick demonstration
        inputManager.addMapping("DevGem0", new KeyTrigger(KeyInput.KEY_B));
        inputManager.addMapping("DevGem1", new KeyTrigger(KeyInput.KEY_N));
        inputManager.addMapping("DevGem2", new KeyTrigger(KeyInput.KEY_M));
        inputManager.addMapping("DevGem3", new KeyTrigger(KeyInput.KEY_K));
        inputManager.addMapping("DevGem4", new KeyTrigger(KeyInput.KEY_J));

        inputManager.addListener(
                this,
                "Left", "Right", "Forward", "Backward",
                "Jump", "Dodge", "Crouch",
                "AttackPrimary", "AttackSecondary",
                "Inventory", "Interact",
                "Hotbar0", "Hotbar1", "Hotbar2", "Hotbar3", "Hotbar4", "Hotbar5",
                "ThrowBomb",
                "MenuToggle",
                "DevSkipStage",
                "DevGem0", "DevGem1", "DevGem2", "DevGem3", "DevGem4"
        );
    }

    /**
 * Removes jME's built-in ESC-to-quit binding.
 *
 * <p>{@code SimpleApplication.initialize()} registers {@code SIMPLEAPP_Exit} on
 * {@code KEY_ESCAPE} and its listener calls {@code stop()}. That runs alongside our
 * own {@code MenuToggle} mapping, so every ESC press both opened the menu and shut
 * the game down. Deleting the mapping is enough: our own ESC handling already lives
 * in {@link #handleEscape()}, and EXIT on the main menu remains the only way out.</p>
 */
private void disableEngineEscapeQuit() {
        inputManager.deleteMapping(SimpleApplication.INPUT_MAPPING_EXIT);
    }

    /** Dev tool bound to L: teleports to the next stage in the rotation. */
    private void devSkipStage() {
        if (stageManager == null) return;
        System.out.println("[DEV] L pressed Ã¢â‚¬â€ skipping past " + stageManager.getCurrentStageName());
        // advanceStage() already warps to the new spawn and runs settleAfterStageChange()
        stageManager.advanceStage();
    }

    /**
     * Runs after every stage transition: drops leftover motion so a fall or a dodge
     * does not carry through the doorway, closes any overlay the previous stage left
     * behind, and snaps the third-person camera onto the warped player.
     */
    private void settleAfterStageChange() {
        movementVelocity.set(0, 0, 0);
        forward = backward = left = right = false;
        jumpHeld = false;
        crouching = false;
        dodging = false;
        jumpBufferTimer = 0f;
        coyoteTimer = 0f;
        landDip = 0f;
        airborneFallPeak = 0f;
        groundGraceTimer = 0f;
        stepUpTimer = 0f;
        stepUpAssistTimer = 0f;
        stepUpAttempts = 0;
        if (playerControl != null) {
            playerControl.setWalkDirection(Vector3f.ZERO);
            if (playerControl.getRigidBody() != null) {
                playerControl.getRigidBody().setLinearVelocity(Vector3f.ZERO);
            }
        }
        closeAllOverlays();
        snapCameraToPlayer();
    }

    /** Puts the third-person rig directly behind the player with no smoothing,
     *  matching the offset maths the per-frame camera update uses. */
    private void snapCameraToPlayer() {
        if (cam == null || playerNode == null) return;
        Vector3f pivot = playerNode.getWorldTranslation().clone().add(0f, 1.5f, 0f);
        Vector3f offset = new Vector3f(
                FastMath.sin(camYaw) * FastMath.cos(camPitch),
                FastMath.sin(camPitch),
                FastMath.cos(camYaw) * FastMath.cos(camPitch)
        ).multLocal(camDistance);
        cam.setLocation(pivot.add(offset));
        cam.lookAt(pivot, Vector3f.UNIT_Y);
    }

    /** Dev tool bound to B/N/M/K/J: grants the Nth gem and marks it collected. */
    private void devAddGem(int index) {
        if (stageManager == null) return;
        GemProgression gems = stageManager.getGemProgression();
        if (gems == null || inventory == null) return;
        if (index < 0 || index >= GemProgression.GEM_COUNT) return;

        String id = GemProgression.GEMS[index].id;
        if (!inventory.hasItem(id, 1)) {
            inventory.addItem(id, 1);
        }
        gems.devGrantCollected(index);
        System.out.println("[DEV] Granted gem " + index + " (" + id + ")");
    }

    /**
     * Shuts every gameplay overlay in one place, so no flag can be left set across a
     * death, a stage change or a run reset. Anything left "open" here makes
     * {@link #isUiOpen()} true, which freezes the world and makes ESC look broken.
     */
    private void closeAllOverlays() {
        if (inventoryOpen && inventoryUI != null) {
            inventoryUI.closeContextMenu();
            inventoryUI.setVisible(false);
        }
        inventoryOpen = false;
        if (chestUI != null && chestUI.isOpen()) chestUI.closeChest();
        if (potionShopUI != null && potionShopUI.isOpen()) potionShopUI.closeShop();
        if (runUpgradeUI != null && runUpgradeUI.isOpen()) runUpgradeUI.close();
    }

    /** Locks all gameplay input and hands the cursor to the death screen. */
    private void triggerDeath() {
        if (playerDead) return;
        playerDead = true;
        SoundManager.playPlayerDeath();
        if (bombs != null) bombs.cleanup();

        closeAllOverlays();

        // lock gameplay outright: no movement, no attacks
        forward = backward = left = right = false;
        jumpHeld = false;
        crouching = false;
        dodging = false;
        if (playerStats != null) playerStats.setInvulnerable(false);
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
        if (playerStats != null) {
            playerStats.resetToDefaults();
            // Run stat upgrades (Attack/Speed/Defense/Jump/Luck) and potion upgrade
            // levels are temporary. Clearing them here is what makes every stat
            // multiplier fall back to 1.0x and every potion to level 1, so the next
            // run starts from the player's original base values.
            playerStats.resetUpgrades();
        }
        grantStarterLoadout();
        if (combat != null) combat.equip("iron_sword");

        // 2. back to a brand-new Sanctuary (stage 0, loop 0, difficulty 1.0)
        if (stageManager != null) {
            stageManager.resetToFirstStage(playerControl);
            if (combat != null) combat.setEnemies(stageManager.getActiveEnemies());
        }
        if (bombs != null) {
            bombs.cleanup();
            bombStageName = null;
        }
        if (enemyArrow != null) enemyArrow.hide();

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
        if (playerStats != null) playerStats.setInvulnerable(false);
        landDip = 0f;
        airborneFallPeak = 0f;

        playAnim("Idle");
        if (deathScreen != null) deathScreen.setVisible(false);
        inputManager.setCursorVisible(false);
        hudCursorShown = false;

        System.out.println("[Respawn] A fresh run begins in the Sanctuary.");
    }

    /** Starting pack: one iron sword, two bombs, two of every potion, +250 soul dust, +40 XP.
     *
     *  <p>Sword and bombs go in the hotbar; the potions go in the inventory grid via
     *  {@link Inventory#addItem}. Previously every starter item was added to the grid <em>and</em>
     *  to a hotbar slot, so a run began with two swords, four keys and double potions.</p>
     *
     *  <p>Only those two items are hotbar items because the hotbar is the only place the
     *  game can spend them from: {@code useToolbarSlot} and {@code throwBomb} both scan the
     *  toolbar exclusively, and a bomb is {@code THROWABLE} so a number key does nothing to
     *  it. Grid potions are still consumed, just through right-click Ã¢â€ â€™ Use on the inventory
     *  screen ({@code InventoryUI.useItem}) rather than a number key.</p>
     */
    private void grantStarterLoadout() {
        if (inventory == null || playerStats == null) return;

        // Reset the hotbar first so this stays idempotent and can never inherit a stale slot.
        for (int i = 0; i < Inventory.TOOLBAR_SIZE; i++) {
            inventory.getToolbarSlot(i).clear();
        }

        // Slot 0 is the equipped weapon that InventoryUI reads back, so the sword goes there.
        inventory.getToolbarSlot(0).itemId = "iron_sword";
        inventory.getToolbarSlot(0).count = 1;
        // Q throws a bomb from whichever hotbar slot holds them, so they need a slot too.
        inventory.getToolbarSlot(1).itemId = Bombs.ITEM_ID;
        inventory.getToolbarSlot(1).count = 2;

        inventory.addItem("health_potion", 2);
        inventory.addItem("speed_potion", 2);
        inventory.addItem("strength_potion", 2);
        inventory.addItem("critical_potion", 2);
        inventory.addItem("regen_potion", 2);

        // no armour is equipped at run start any more Ã¢â‚¬â€ Defense comes from the
        // Sanctuary run upgrade instead, so the player begins on base defense 10.
        playerStats.addSoulDust(250);
        playerStats.addExperience(40f);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        // ESC is intercepted centrally so its priority is defined in exactly one place.
        // It is handled before everything else because an open UI must be able to claim
        // it without also triggering a gameplay action on the same keypress.
        if (isPressed && "MenuToggle".equals(name)) {
            handleEscape();
            return;
        }

        // The menu outranks even the death screen, because MAIN MENU on the death screen
        // is one of the ways it can be opened.
        if (menuUI != null && menuUI.isOpen()) {
            if (isPressed && "AttackPrimary".equals(name)) {
                Vector2f cur = inputManager.getCursorPosition();
                menuUI.handleClick(cur.x, cur.y);
            }
            return;
        }

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
        case "DevGem0":
            if (isPressed) devAddGem(0);
            return;
        case "DevGem1":
            if (isPressed) devAddGem(1);
            return;
        case "DevGem2":
            if (isPressed) devAddGem(2);
            return;
        case "DevGem3":
            if (isPressed) devAddGem(3);
            return;
        case "DevGem4":
            if (isPressed) devAddGem(4);
            return;

            case "Inventory":
                if (isPressed) toggleInventory();
                return;

        case "Interact":
            // gated like the combat actions: without this, pressing F while a shop or
            // upgrade menu is open would open a second menu underneath the first.
            if (isUiOpen()) return;
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
            case "Hotbar5":
                if (isPressed) useToolbarSlot(name.charAt("Hotbar".length()) - '0');
                break;

            case "ThrowBomb":
                if (isPressed) throwBomb();
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

    /**
     * Throws one bomb from the first toolbar slot holding them, on Q.
     *
     * <p>Drains whichever hotbar slot has the stack (rather than a fixed slot) so the
     * player can move bombs around freely. The stack is only decremented once the
     * combat controller confirms the throw landed, so a bomb is never lost to a
     * cooldown or to hitting the live-bomb cap.</p>
     */
    private void throwBomb() {
        if (inventory == null || combat == null) return;

        int slotIndex = -1;
        for (int i = 0; i < Inventory.TOOLBAR_SIZE; i++) {
            Slot s = inventory.getToolbarSlot(i);
            if (s.isEmpty() || !Bombs.ITEM_ID.equals(s.itemId)) continue;
            slotIndex = i;
            break;
        }
        if (slotIndex < 0) return;

        if (!combat.throwBomb()) return;
        inventory.removeFromToolbar(slotIndex, 1);
    }

    /** True if any UI overlay is open (the world is then paused). */
    private boolean isUiOpen() {
        if (menuUI != null && menuUI.isOpen()) return true;
        if (inventoryOpen) return true;
        if (chestUI != null && chestUI.isOpen()) return true;
        if (potionShopUI != null && potionShopUI.isOpen()) return true;
        if (runUpgradeUI != null && runUpgradeUI.isOpen()) return true;
        return false;
    }

    /**
     * Really stops the world while an overlay is up.
     *
     * <p>Returning early out of {@code simpleUpdate} was never enough to pause: {@code
     * bulletAppState} is attached to the state manager and kept stepping on its own, so
     * the capsule still fell under gravity, enemies still walked and the animation
     * composer kept playing Ã¢â‚¬â€ the game looked live behind the settings menu. Zeroing
     * the physics speed halts the simulation without tearing the physics space down,
     * and the animation composer is held at 0 for the same reason.</p>
     *
     * <p>Called every frame from one place with the current {@link #isUiOpen()} result,
     * so the paused flag can never disagree with what is actually on screen.</p>
     */
    private void applyPauseState(boolean paused) {
        if (paused == gamePaused) return;
        gamePaused = paused;
        if (bulletAppState != null) bulletAppState.setSpeed(paused ? 0f : 1f);
        if (animComposer != null) animComposer.setGlobalSpeed(paused ? 0f : 1f);
        System.out.println("[Pause] " + (paused ? "paused" : "resumed"));
        if (paused) freezeInput();
    }

    // ---- main menu / settings ------------------------------------------------

    /**
     * Shows the main menu and parks the game behind it. Nothing is reset and no stage
     * is rebuilt: the world keeps whatever state it had, it just stops ticking.
     */
    private void openMainMenu() {
        if (menuUI == null) return;
        // the death screen owns the screen while the player is down
        menuFromDeathScreen = playerDead;
        if (playerDead && deathScreen != null) deathScreen.setVisible(false);
        menuUI.show(MainMenuUI.Screen.MAIN);
        freezeInput();
    }

    /** Closes the menu and hands control back to the running game. */
    private void startGame() {
        // START GAME coming from the death screen goes back to the death screen, not
        // straight into a run that is still in progress.
        if (menuFromDeathScreen) {
            menuFromDeathScreen = false;
            menuUI.close();
            if (deathScreen != null) deathScreen.setVisible(true);
            return;
        }
        gameStarted = true;
        if (menuUI != null) menuUI.close();
    }

    /**
     * ESC routing, in strict priority order. Every branch either consumes the press or
     * deliberately does nothing, so ESC can never fall through to an exit path.
     *
     * <p>The chest and both shops register their own ESC mappings to close themselves,
     * so those branches deliberately return instead of closing anything: the overlay
     * gets the press on its own listener. Inventory has no ESC mapping of its own, so
     * it is closed here.</p>
     */
    private void handleEscape() {
        if (playerDead) {
            logEscape("ignored: player dead");
            return;
        }

        // Snapshot the overlay state BEFORE anything can mutate it. Chest, shop and
        // run-upgrade each register their own ESC mapping, so jME fires this listener
        // and theirs off the same key press; reading the flags live would see them
        // already closed and wrongly open the pause menu on top of them.
        boolean menuWasOpen = menuUI != null && menuUI.isOpen();
        boolean chestWasOpen = chestUI != null && chestUI.isOpen();
        boolean shopWasOpen = potionShopUI != null && potionShopUI.isOpen();
        boolean runUpgWasOpen = runUpgradeUI != null && runUpgradeUI.isOpen();
        boolean invWasOpen = inventoryOpen;

        if (menuWasOpen) {
            MainMenuUI.Screen s = menuUI.getScreen();
            if (s == MainMenuUI.Screen.SETTINGS || s == MainMenuUI.Screen.ABOUT) {
                // settings opened from the main menu go back one level; settings opened
                // from gameplay close outright so the frozen frame is resumed exactly.
                if (settingsFromMainMenu) menuUI.show(MainMenuUI.Screen.MAIN);
                else menuUI.close();
                logEscape("menu: closed " + s);
                return;
            }
            // bare main menu: ESC does nothing. START GAME / EXIT are the only ways out.
            if (!gameStarted) {
                logEscape("menu: blocked at main menu");
                return;
            }
            if (menuFromDeathScreen) {
                logEscape("menu: blocked behind death screen");
                return;
            }
            menuUI.close();
            logEscape("menu: closed bare main menu");
            return;
        }

        // These close themselves via their own ESC mapping, but we close them here as
        // well and return, so one press is always exactly one action. ChestUI in
        // particular skips its own "ChestClose" case when the chest is empty, which
        // left chestOpen stuck true forever: isUiOpen() then froze the world and made
        // ESC look dead. Closing from here cannot get stuck.
        if (chestWasOpen) {
            chestUI.closeChest();
            logEscape("chest: closed");
            return;
        }
        if (shopWasOpen) {
            potionShopUI.closeShop();
            logEscape("shop: closed");
            return;
        }
        if (runUpgWasOpen) {
            runUpgradeUI.close();
            logEscape("run upgrade: closed");
            return;
        }

        // InventoryUI has no ESC mapping of its own, so ESC closes it here and only it.
        if (invWasOpen) {
            toggleInventory();
            logEscape("inventory: closed");
            return;
        }

        // gameplay -> paused settings overlay
        if (gameStarted) {
            openSettings();
            logEscape("gameplay: opened pause/settings");
        } else {
            logEscape("ignored: game not started");
        }
    }

    /** Prints which ESC branch ran plus every overlay flag, to trace routing bugs. */
    private void logEscape(String branch) {
        System.out.println("[ESC] " + branch
                + " | chest=" + (chestUI != null && chestUI.isOpen())
                + " shop=" + (potionShopUI != null && potionShopUI.isOpen())
                + " runUpg=" + (runUpgradeUI != null && runUpgradeUI.isOpen())
                + " inventory=" + inventoryOpen
                + " menu=" + (menuUI != null && menuUI.isOpen())
                + " menuScreen=" + (menuUI != null ? menuUI.getScreen() : "-")
                + " paused=" + (menuUI != null && gameStarted
                        && menuUI.getScreen() == MainMenuUI.Screen.SETTINGS)
                + " dead=" + playerDead);
    }

    /**
     * Shown above the gameplay settings overlay so a paused game reads as paused
     * rather than looking like the main menu again.
     */
    private void buildPauseBanner() {
        if (!gameStarted) return;
        UiKit.textIn(guiFont, menuUI.getNode(), "GAME PAUSED",
                new UiKit.Rect(0f, 830f, 1920f, 60f), 44f, UiKit.hex(0xC0C0C0));
    }

    private void openSettings() {
        if (menuUI == null) return;
        settingsFromMainMenu = !gameStarted;
        menuUI.show(MainMenuUI.Screen.SETTINGS);
        buildPauseBanner();
        freezeInput();
    }

    /** Halts movement keys so the player cannot walk off while a menu is up. */
    private void freezeInput() {
        if (playerControl != null) playerControl.setWalkDirection(Vector3f.ZERO);
        forward = backward = left = right = false;
        // the aim arrow lives in the world, so it must be hidden explicitly or it
        // keeps tracking enemies behind the overlay
        if (enemyArrow != null) enemyArrow.hide();
    }

    /**
     * Applies a windowed/fullscreen change.
     *
     * <p>jME keeps rendering against the window it was handed at startup, so the new
     * settings are copied in and the context is told to restart. This runs from the
     * click callback, which jME dispatches on the render thread, so the teardown and
     * recreate happen in the right place.</p>
     */
    private void applyDisplayMode(boolean fullscreen) {
        if (settings.isFullscreen() == fullscreen) return;
        try {
            AppSettings next = new AppSettings(true);
            next.copyFrom(settings);
            next.setFullscreen(fullscreen);
            getContext().setSettings(next);
            settings = next;
            getContext().restart();
            displayModePending = true;
        } catch (Throwable t) {
            System.err.println("Display mode switch failed: " + t);
        }
    }

    /**
     * Re-anchors every overlay that caches screen pixels after the window changed size.
     * The UI classes own no resize hook, so their geometry is only rebuilt on their
     * next buildUI(); the menu is rebuilt here because it can be open at that moment.
     */
    private void refreshViewportOverlay() {
        int w = settings.getWidth();
        int h = settings.getHeight();
        lastOverlayWidth = w;
        lastOverlayHeight = h;
        UiKit.init(w, h);
        if (menuUI != null) {
            menuUI.onViewportResized(w, h, settings.isFullscreen());
            // the banner is part of the menu's rebuilt screen, so it is re-added here
            if (menuUI.isOpen() && menuUI.getScreen() == MainMenuUI.Screen.SETTINGS && gameStarted) {
                buildPauseBanner();
            }
        }
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
        // Full immunity for exactly as long as the roll plays. The Roll clip is
        // time-scaled to finish with dodgeTimer (see simpleUpdate), so the invulnerable
        // window and the animation are the same window.
        if (playerStats != null) playerStats.setInvulnerable(true);
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
        frameDelta = tpf;

        // The context restart lands a frame or two after the click, so the overlay is
        // re-anchored once the new size is actually in effect.
        if (displayModePending) {
            int w = settings.getWidth();
            int h = settings.getHeight();
            if (w != lastOverlayWidth || h != lastOverlayHeight) {
                displayModePending = false;
                refreshViewportOverlay();
            }
        }

        if (stageManager != null) {
            SoundManager.update(tpf, stageManager.getCurrentStage());
        }

        // the menu owns the cursor and its hover state before anything else runs
        if (menuUI != null && menuUI.isOpen()) {
            Vector2f cur = inputManager.getCursorPosition();
            menuUI.update(cur.x, cur.y);
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

        // The cursor belongs to whichever overlay is up. Only toggleInventory() used to
        // reveal it, so a shop opened with F at an NPC left the pointer invisible and its
        // buttons impossible to aim at. Derived every frame so every open/close path
        // (inventory, chest, both shops, death) stays in sync from one place.
        boolean uiOpen = isUiOpen();
        inputManager.setCursorVisible(uiOpen || playerDead);

        applyPauseState(uiOpen);

        if (uiOpen) {
            return;
        }

        if (!lightProbeBaked && envCam != null && envCam.getApplication() != null) {
            bakeLightProbe();
            lightProbeBaked = true;
        }

        if (stageManager != null) {
            stageManager.update(tpf, playerNode.getWorldTranslation(), playerControl);
        }

        // The arrow must feel like part of the HUD, but it lives in the world so
        // its pitch never reads "screen-locked" Ã¢â‚¬â€ it always aims at the enemy's
        // position relative to the player, in the camera's own space.
        if (enemyArrow != null && stageManager != null && playerNode != null && cam != null) {
            Vector3f playerPos = playerNode.getWorldTranslation();
            EnemyController nearest = stageManager.getNearestLivingEnemy(playerPos, tpf);
            // jME's Camera exposes getLeft(), so the right axis is its negation
            enemyArrow.update(tpf, playerPos, nearest, cam.getLeft().negate(), cam.getDirection());
        }
        if (combat != null) {
            combat.setEnemies(stageManager != null ? stageManager.getActiveEnemies() : null);
            combat.update(tpf);
            // ticked after stageManager.update so the enemy list is settled, and it runs
            // on its own so fuses keep burning even if combat is mid-death-check
            if (bombs != null) {
                String stage = stageManager != null ? stageManager.getCurrentStageName() : null;
                if (stage != null && !stage.equals(bombStageName)) {
                    // a portal swap must not detonate a leftover bomb into the new
                    // stage's enemies
                    if (bombStageName != null) bombs.cleanup();
                    bombStageName = stage;
                }
                bombs.update(tpf,
                        stageManager != null ? stageManager.getActiveEnemies() : null,
                        effects,
                        // the live stage, so each bomb lands on the terrain under it
                        // instead of a flat y=0 plane that every height field sinks into
                        stageManager != null ? stageManager.getCurrentStage() : null);
            }
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
                if (it instanceof NPC npc) {
                    // drive the wander before the voice line, so the distance check uses
                    // this frame's position rather than last frame's
                    npc.update(tpf);
                    if (npc.getVoicePool() > 0) {
                        SoundManager.maybeNpcVoice(npc.getVoicePool(), npc.getPosition(), playerPos, facing, tpf);
                    }
                }
            }
        }

        updateHUD();

        if (dodgeCooldown > 0f) {
            dodgeCooldown -= tpf;
        }

        float verticalVelocity = playerControl.getVelocity().y;
        // a capsule clipped through a height-field collider lands on the failsafe floor
        // under the map and is stranded there; put them back on the stage spawn
        stageManager.recoverPlayerIfFallen(playerControl);
        boolean groundContact = playerControl.isOnGround();
        // hold the last good ground state briefly so a single dropped sweep does not
        // demote a running character to the much slower airborne rates
        if (groundContact) {
            groundGraceTimer = GROUND_GRACE;
        } else {
            groundGraceTimer = Math.max(0f, groundGraceTimer - tpf);
        }
        boolean grounded = groundContact || groundGraceTimer > 0f;

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
        if (stepUpAssistTimer > 0f) {
            // mid step-up: hold a mild gravity so the climb is a controlled hop
            // rather than being cancelled by the jump-cut on the very next frame
            gravityScale = STEP_UP_GRAVITY_MULTIPLIER;
        } else if (verticalVelocity < 0f) {
            gravityScale = FALL_GRAVITY_MULTIPLIER;
        } else if (!jumpHeld) {
            gravityScale = JUMP_CUT_GRAVITY_MULTIPLIER;
        } else {
            gravityScale = RISE_GRAVITY_MULTIPLIER;
        }
        playerControl.setGravity(new Vector3f(0, -BASE_GRAVITY * gravityScale, 0));
        // Jump Height run-upgrade, applied to the jump force the character controller
        // reads. Peak height goes as v^2/(2g), so scaling the force by the stat
        // multiplier's square root makes the HEIGHT scale linearly Ã¢â‚¬â€ a 1.5x Jump stat
        // jumps 1.5x as high rather than 2.25x. Re-applied every frame so a purchase
        // takes effect immediately and a new run's reset is picked up automatically.
        if (playerStats != null) {
            float jumpMult = playerStats.getRunUpgrades().getMultiplier(
                    RunUpgrades.Stat.JUMP);
            playerControl.setJumpForce(new Vector3f(0, JUMP_FORCE * FastMath.sqrt(jumpMult), 0));
        }

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

        float baseSpeed = playerStats != null ? playerStats.getMovementSpeed() : 43.5f;
        float targetSpeed = crouching ? baseSpeed * CROUCH_SPEED_MULTIPLIER : baseSpeed;

        // Speed is kept separate from heading on purpose. Lerping the whole velocity
        // vector toward the target dips speed to ~70% mid-turn and settles laggy Ã¢â‚¬â€
        // instead we keep the carried magnitude and *steer* the direction on an
        // angle-scaled arc, so nothing ever snaps 90Ã‚Â°.
        Vector3f curDir = new Vector3f(movementVelocity);
        float curSpeed = curDir.length();
        curDir.normalizeLocal();

        if (isMoving) {
            // raw summed input, square-root-2 long on diagonals
            Vector3f inputDir = walkDirection.normalizeLocal();

            if (curSpeed > MOMENTUM_EPS) {
                // atan2(crossY, dot) winds opposite to jME's right-handed +Y
                // rotation, so negate it; otherwise the heading U-turns 180Ã‚Â°
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
                // roll is over Ã¢â‚¬â€ the player takes damage normally again from here on
                if (playerStats != null) playerStats.setInvulnerable(false);
            }
        } else {
            // eased velocity, so strafes and backpedals keep a hint of momentum
            playerControl.setWalkDirection(movementVelocity);

            if (isMoving) {
                playerControl.setViewDirection(walkDirection);
            }

            // Step-up assist. Minie's control has no step offset, so the capsule
            // catches on any box edge it meets Ã¢â‚¬â€ and every scenery collider in the
            // game is a box. When the player is grounded, asking to move, and the
            // body is travelling far slower than requested, it is being blocked by
            // something steppable rather than genuinely walled in, so lift it just
            // enough to clear the lip.
            //
            // Gated on the RAW onGround flag, not the grace-extended `grounded`. The
            // grace window deliberately stays true for 0.12s after the character walks
            // off a ledge, and firing an upward impulse inside that window cancelled
            // the start of every fall Ã¢â‚¬â€ the player visibly stalled and then dropped,
            // which read as "slows down while falling". `risingSpeed` additionally
            // rules out firing while genuinely moving upward.
            Vector3f bodyVel = playerControl.getVelocity();
            float requested = movementVelocity.length();
            float actualSpeed = FastMath.sqrt(bodyVel.x * bodyVel.x + bodyVel.z * bodyVel.z);
            boolean blocked = groundContact && isMoving && !dodging
                    && bodyVel.y <= 0.5f
                    && requested > 1f && actualSpeed < requested * STEP_UP_SPEED_RATIO;

            if (stepUpAssistTimer > 0f) {
                stepUpAssistTimer = Math.max(0f, stepUpAssistTimer - tpf);
            }

            if (blocked) {
                stepUpTimer += tpf;
            } else {
                stepUpTimer = 0f;
                stepUpAttempts = 0;
            }

            if (stepUpTimer > STEP_UP_DELAY) {
                stepUpTimer = 0f;
                if (stepUpAttempts < STEP_UP_MAX_ATTEMPTS
                        && playerControl.getRigidBody() != null) {
                    stepUpAttempts++;
                    stepUpAssistTimer = STEP_UP_ASSIST_DURATION;
                    // BetterCharacterControl exposes no setLinearVelocity of its own;
                    // the impulse has to go through the rigid body
                    playerControl.getRigidBody().setLinearVelocity(
                            new Vector3f(bodyVel.x, stepUpSpeed(), bodyVel.z));
                }
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
                * FastMath.clamp(movementVelocity.length() / (baseSpeed <= 0f ? 43.5f : baseSpeed), 0f, 1f);
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

        // Remaining-enemy counter. Top-right, above Soul Dust, so it reads as a
        // "how much fight is left" readout rather than part of the vitals cluster.
        // Right-aligned like the Soul Dust line below it.
        hudEnemiesText = new BitmapText(font, false);
        hudEnemiesText.setSize(18f * hudSy);
        hudEnemiesText.setColor(new ColorRGBA(0.95f, 0.65f, 0.65f, 1f));
        hudEnemiesText.setText("ENEMIES: 00");
        hudEnemiesText.setCullHint(Spatial.CullHint.Always);
        hudEnemiesText.setLocalTranslation(screenW - margin, 58f * hudSy, 0);
        hudNode.attachChild(hudEnemiesText);

        hudSoulText = new BitmapText(font, false);
        hudSoulText.setSize(18f * hudSy);
        hudSoulText.setColor(new ColorRGBA(0.85f, 0.92f, 1f, 1f));
        hudSoulText.setText("Soul Dust: 0");
        // anchored to the top-right corner, right-aligned in updateHUD
        hudSoulRightAnchor = screenW - margin;
        hudSoulText.setLocalTranslation(hudSoulRightAnchor - hudSoulText.getLineWidth(),
                30f * hudSy, 0);
        hudNode.attachChild(hudSoulText);

        // context prompt shown only while standing in range of a chest or NPC.
        // Hidden (empty text) the rest of the time so it never occupies the screen.
        interactPrompt = new BitmapText(font, false);
        interactPrompt.setSize(18f * hudSy);
        interactPrompt.setColor(new ColorRGBA(1f, 0.95f, 0.7f, 1f));
        interactPrompt.setText("");
        interactPrompt.setLocalTranslation(margin, 70f * hudSy, 0);
        hudNode.attachChild(interactPrompt);

        // transient gem/progression feedback; sits above the interact prompt and
        // blanks itself once its timer runs out, so it never clutters the HUD
        hudMessage = new BitmapText(font, false);
        hudMessage.setSize(20f * hudSy);
        hudMessage.setColor(new ColorRGBA(1f, 0.95f, 0.55f, 1f));
        hudMessage.setText("");
        hudMessage.setLocalTranslation(margin, 96f * hudSy, 0);
        hudNode.attachChild(hudMessage);

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

        int remaining = stageManager != null ? stageManager.getRemainingEnemyCount() : 0;
        if (hudEnemiesText != null) {
            // format with leading zero so a single enemy reads ENEMIES: 01
            String formatted = String.format("ENEMIES: %02d", Math.max(0, remaining));
            hudEnemiesText.setText(formatted);
            hudEnemiesText.setLocalTranslation(
                    hudSoulRightAnchor - hudEnemiesText.getLineWidth(), 58f * hudSy, 0);
            Spatial.CullHint hint = (remaining > 0 || (stageManager != null
                    && stageManager.getCurrentStage() != null
                    && !stageManager.getCurrentStage().isSafe()))
                    ? Spatial.CullHint.Never : Spatial.CullHint.Always;
            hudEnemiesText.setCullHint(hint);
        }

        if (hudSoulText != null) {
            hudSoulText.setText("Soul Dust: " + playerStats.getSoulDust());
            hudSoulText.setLocalTranslation(
                    hudSoulRightAnchor - hudSoulText.getLineWidth(), 30f * hudSy, 0);
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
        updateInteractPrompt();

        if (hudMessage != null && hudMessageTimer > 0f) {
            hudMessageTimer -= frameDelta;
            if (hudMessageTimer <= 0f) {
                hudMessageTimer = 0f;
                hudMessage.setText("");
            }
        }
    }

    /**
     * Transient HUD line used by the gem progression ("Gem dropped", "Gem acquired",
     * boss-gate refusals). Mirrors to the console so a message is never lost behind a
     * UI overlay, and simply drops the text if the HUD is not built yet.
     */
    private void showHudMessage(String message) {
        if (message == null || message.isEmpty()) return;
        System.out.println("[Gem] " + message);
        if (hudMessage == null) return;
        hudMessage.setText(message);
        hudMessageTimer = HUD_MESSAGE_SECONDS;
    }

    /**
     * Shows "[F] Name" while the player stands in range of the closest chest or NPC,
     * mirroring the closest-wins rule {@link #handleInteract()} uses so the prompt
     * always names the object F would actually trigger.
     */
    private void updateInteractPrompt() {
        if (interactPrompt == null) return;
        if (isUiOpen()) {
            interactPrompt.setText("");
            return;
        }
        Vector3f playerPos = playerNode.getWorldTranslation();
        Interactable nearest = null;
        float best = Float.MAX_VALUE;
        for (Interactable it : interactables) {
            if (!it.isInRange(playerPos, INTERACT_RANGE)) continue;
            float d = it.getPosition().distance(playerPos);
            if (d < best) {
                best = d;
                nearest = it;
            }
        }
        if (nearest == null) {
            interactPrompt.setText("");
        } else {
            String label = (nearest instanceof NPC) ? ((NPC) nearest).getName() : "Chest";
            interactPrompt.setText("[F] " + label);
        }
    }

    private void spawnChestsAndNPCs() {
        Chest chest1 = new Chest(new Vector3f(10f, 0.6f, -15f));
        chest1.build(assetManager, rootNode, bulletAppState);
        chest1.addLoot("health_potion", 3);
        chest1.addLoot("regen_potion", 2);
        chest1.addLoot("speed_potion", 2);
        chest1.addLoot("strength_potion", 1);
        chest1.setChestUI(chestUI);
        interactables.add(chest1);
        interactablePhysics.add(chest1.getPhysics());

        Chest chest2 = new Chest(new Vector3f(-20f, 0.6f, 10f));
        chest2.build(assetManager, rootNode, bulletAppState);
        chest2.addLoot("health_potion", 2);
        chest2.addLoot("speed_potion", 2);
        chest2.addLoot("critical_potion", 1);
        chest2.setChestUI(chestUI);
        interactables.add(chest2);
        interactablePhysics.add(chest2.getPhysics());

        Chest chest3 = new Chest(new Vector3f(5f, 0.6f, 25f));
        chest3.build(assetManager, rootNode, bulletAppState);
        chest3.addLoot("iron_sword", 1);
        chest3.addLoot("speed_potion", 2);
        chest3.addLoot("regen_potion", 1);
        chest3.setChestUI(chestUI);
        interactables.add(chest3);
        interactablePhysics.add(chest3.getPhysics());

        System.out.println("Spawned 3 chests in Sanctuary");

        // ---- NPC 1: THE POTION MERCHANT -------------------------------------
        // Potions only, plus the potion upgrade rows rendered by PotionShopUI.
        // npc1.gltf stands at (-10,0,-20); facing ~135deg turns them toward the
        // spawn point at the origin so they look at an approaching player.
        NPC merchant1 = new NPC("Potion Merchant", new Vector3f(-10f, 0f, -20f));
        merchant1.setModel("Models/Characters/npc/npc1.gltf", 135f);
        merchant1.build(assetManager, rootNode, bulletAppState);
        merchant1.setVoicePool(1);
        merchant1.addShopItem("health_potion", 15);
        merchant1.addShopItem("speed_potion", 18);
        merchant1.addShopItem("strength_potion", 22);
        merchant1.addShopItem("critical_potion", 25);
        merchant1.addShopItem("regen_potion", 16);
                // bombs are stock here too, but they are not a potion: the shop skips
                // their upgrade row, so buying one just adds it to the inventory
                merchant1.addShopItem(Bombs.ITEM_ID, 50);
        merchant1.setPotionShopUI(potionShopUI);
        interactables.add(merchant1);
        interactablePhysics.add(merchant1.getPhysics());

        // ---- NPC 2: THE RUN UPGRADER ---------------------------------------
        // No stock at all Ã¢â‚¬â€ this NPC only sells the five run stat upgrades.
        // npc2.gltf stands at (15,0,5), facing ~-110deg to look back at the spawn.
        NPC merchant2 = new NPC("Run Upgrader", new Vector3f(15f, 0f, 5f));
        merchant2.setModel("Models/Characters/npc/npc2.gltf", -110f);
        merchant2.build(assetManager, rootNode, bulletAppState);
        merchant2.setVoicePool(2);
        merchant2.setRunUpgradeUI(runUpgradeUI);
        interactables.add(merchant2);
        interactablePhysics.add(merchant2.getPhysics());

        interactablesBuilt = true;
        System.out.println("Spawned 2 NPCs in Sanctuary (Potion Merchant, Run Upgrader)");
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
        for (Object body : interactablePhysics) {
            bulletAppState.getPhysicsSpace().remove(body);
        }
        interactablePhysics.clear();
        interactablesBuilt = false;
    }

    private void handleInteract() {
        Vector3f playerPos = playerNode.getWorldTranslation();
        // closest-wins rather than first-in-list-wins: a chest sitting closer than an
        // NPC (or vice versa) would otherwise swallow the interaction and make the
        // other one feel unreachably dead.
        Interactable nearest = null;
        float nearestDist = Float.MAX_VALUE;
        for (Interactable interactable : interactables) {
            if (!interactable.isInRange(playerPos, INTERACT_RANGE)) continue;
            float d = interactable.getPosition().distance(playerPos);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = interactable;
            }
        }
        if (nearest != null) {
            nearest.interact();
        }
    }
}
