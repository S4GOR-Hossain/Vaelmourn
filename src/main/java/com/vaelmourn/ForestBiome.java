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
import java.util.Random;

public class ForestBiome extends SimpleApplication implements ActionListener {

    private BulletAppState bulletAppState;
    private BetterCharacterControl playerControl;
    private Node playerNode;
    private AnimComposer animComposer;
    private String currentAnim = "";

    private boolean left, right, forward, backward;
    private final Vector3f walkDirection = new Vector3f();
    private final float MOVE_SPEED = 10f;

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
    private final float BASE_GRAVITY = 30f;
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
    private float hudSx = 1f;
    private float hudSy = 1f;
    private static final float HUD_HP_FULL_WIDTH = 252f; // inner fill width at 1080p

    private Node forestZoneNode;

    private EnvironmentCamera envCam;
    private boolean lightProbeBaked = false;

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

        // Initialize Lemur (jME3 UI toolkit) so GUI widgets can be created anywhere.
        // GuiGlobals.initialize(this) must be called once, before building any Lemur UI.
        GuiGlobals.initialize(this);
        // The Glass theme is loaded from a Groovy stylesheet. On JDKs newer than what the
        // bundled Groovy supports this throws, so guard it to never break startup. The game's
        // HUD is jME3-native (not Lemur), so a missing Glass theme has no functional impact.
        try {
            BaseStyles.loadGlassStyle(); // dark translucent "Glass" look (sci-fi default)
        } catch (Throwable t) {
            System.err.println("Lemur Glass style unavailable: " + t);
        }

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

        // Disable the default debug overlays (FPS/stats HUD and debug keys) so
        // they don't render on-screen text (bottom-left numbers) in the game UI.
        stateManager.detach(stateManager.getState(StatsAppState.class));
        stateManager.detach(stateManager.getState(DebugKeysAppState.class));

        // The world geometry, lighting and atmosphere now come from the Stage system,
        // which is initialized after the player and PlayerStats are created (below)
        // because loadInitialStage() warps the player and needs those to exist.

        // Player
        Spatial playerModel = assetManager.loadModel("Models/Characters/Player/player.gltf");

        // GPU (hardware) skinning of the skinned model crashes this AMD OpenGL driver
        // (EXCEPTION_ACCESS_VIOLATION in glBufferData). Force CPU skinning instead.
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
        combat.equip("iron_sword"); // default weapon

        // Inventory + HUD
        ItemRegistry.registerDefaults();
        inventory = new Inventory();
        playerStats = new PlayerStats();

        // Give the player a few starter items so the UI is populated.
        inventory.addItem("health_potion", 6);
        inventory.addItem("mana_potion", 3);
        inventory.addItem("dungeon_key", 2);
        inventory.addItem("iron_ingot", 12);
        inventory.addItem("leather", 8);
        inventory.addItem("iron_sword", 1);
        inventory.getToolbarSlot(0).itemId = "iron_sword";
        inventory.getToolbarSlot(0).count = 1;
        inventory.getToolbarSlot(1).itemId = "health_potion";
        inventory.getToolbarSlot(1).count = 3;
        inventory.getToolbarSlot(2).itemId = "dungeon_key";
        inventory.getToolbarSlot(2).count = 2;
        inventory.getEquipSlot(Inventory.EquipSlot.HELMET).itemId = "iron_helmet";
        inventory.getEquipSlot(Inventory.EquipSlot.HELMET).count = 1;
        inventory.getEquipSlot(Inventory.EquipSlot.CHESTPLATE).itemId = "iron_chestplate";
        inventory.getEquipSlot(Inventory.EquipSlot.CHESTPLATE).count = 1;
        inventory.getEquipSlot(Inventory.EquipSlot.BOOTS).itemId = "iron_boots";
        inventory.getEquipSlot(Inventory.EquipSlot.BOOTS).count = 1;
        playerStats.addSoulDust(250);
        playerStats.addExperience(40f);

        // ---- Stage / roguelike system ----
        stageManager = new StageManager(assetManager, rootNode, bulletAppState, this);
        stageManager.setPlayerStats(playerStats);
        stageManager.addStage(new SanctuaryStage());
        stageManager.addStage(new DarkwoodStage());
        stageManager.addStage(new AshenWastesStage());
        stageManager.addStage(new FrozenDepthsStage());
        stageManager.loadInitialStage(playerControl);
        combat.setEnemies(stageManager.getActiveEnemies());

        inventoryUI = new InventoryUI(assetManager, renderManager, inputManager, cam,
                inventory, playerStats, playerModel,
                settings.getWidth(), settings.getHeight());
        guiNode.attachChild(inventoryUI.getNode());

        buildHUD(settings.getWidth(), settings.getHeight());

        initKeys();

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(false);

        inputManager.addMapping("MouseX+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MouseX-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MouseY+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("MouseY-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));

        inputManager.addListener(analogListener, "MouseX+", "MouseX-", "MouseY+", "MouseY-");

        // The EnvironmentCamera / light-probe baking re-renders the whole scene into an
        // environment map every frame, which crashes the native AMD OpenGL driver
        // (EXCEPTION_ACCESS_VIOLATION in glBufferData). Lighting now comes from the Stage
        // system, so the probe is unnecessary -- leave it unattached to stay safe.
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

        // Inventory toggle
        inputManager.addMapping("Inventory", new KeyTrigger(KeyInput.KEY_E));

        // Combat mouse buttons
        inputManager.addMapping("AttackPrimary", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("AttackSecondary", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));

        inputManager.addListener(
                this,
                "Left", "Right", "Forward", "Backward",
                "Jump", "Dodge", "Crouch",
                "AttackPrimary", "AttackSecondary",
                "Inventory"
        );
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "Inventory":
                if (isPressed) toggleInventory();
                return;

            case "Left":
            case "Right":
            case "Forward":
            case "Backward":
            case "Jump":
            case "Dodge":
            case "Crouch":
            case "AttackPrimary":
                // When the inventory is open, route the click to the UI
                // (select / drag-drop) instead of the gameplay combat.
                if (inventoryOpen) {
                    if (isPressed && inventoryUI != null) inventoryUI.handlePrimaryClick();
                    return;
                }
                break;

            case "AttackSecondary":
                // Ignore gameplay input while the inventory is open.
                if (inventoryOpen) return;
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
        }
    }

    private void toggleInventory() {
        inventoryOpen = !inventoryOpen;
        inventoryUI.setVisible(inventoryOpen);

        if (inventoryOpen) {
            // Freeze gameplay state while browsing.
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

        // While the inventory is open the game world is paused.
        if (inventoryOpen) {
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
        }

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
            float speed = crouching ? MOVE_SPEED * CROUCH_SPEED_MULTIPLIER : MOVE_SPEED;

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

        cam.setLocation(playerPos.add(offset).add(0, 1.5f, 0));
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

        // dark frame/background bar
        makeHudQuad(margin, 20f * hudSy, barW, barH, new ColorRGBA(0.08f, 0.08f, 0.10f, 0.85f));
        // health fill (green), width scaled per-frame in updateHUD
        hudHpFill = makeHudQuad(fillX, fillY, HUD_HP_FULL_WIDTH * hudSx, 16f * hudSy,
                new ColorRGBA(0.2f, 0.85f, 0.25f, 1f));

        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");

        hudHpText = new BitmapText(font, false);
        hudHpText.setSize(14f * hudSy);
        hudHpText.setColor(ColorRGBA.White);
        hudHpText.setText("100 / 100");
        hudHpText.setLocalTranslation(fillX + 4f * hudSx, fillY + 1f * hudSy, 0);
        hudNode.attachChild(hudHpText);

        // enemies remaining indicator below the bar
        hudEnemiesText = new BitmapText(font, false);
        hudEnemiesText.setSize(16f * hudSy);
        hudEnemiesText.setColor(new ColorRGBA(0.9f, 0.9f, 0.95f, 1f));
        hudEnemiesText.setText("Enemies: 0");
        hudEnemiesText.setLocalTranslation(margin, (20f * hudSy + barH + 8f * hudSy), 0);
        hudNode.attachChild(hudEnemiesText);

        guiNode.attachChild(hudNode);
        updateHUD();
    }

    private void updateHUD() {
        if (hudHpFill == null || playerStats == null) return;

        float maxHp = Math.max(1f, playerStats.getMaxHealth());
        float ratio = Math.max(0f, Math.min(1f, playerStats.getHealth() / maxHp));

        // tint fill green -> yellow -> red as health drops
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

        int remaining = (stageManager != null && stageManager.getActiveEnemies() != null)
                ? stageManager.getActiveEnemies().size() : 0;
        if (remaining > 0) {
            String stageName = stageManager != null ? stageManager.getCurrentStageName() : "?";
            hudEnemiesText.setText(stageName + "  |  Enemies: " + remaining);
        } else {
            hudEnemiesText.setText("Enemies: 0");
        }
    }
}