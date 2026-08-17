package com.vaelmourn;

import com.jme3.anim.AnimComposer;
import com.jme3.app.SimpleApplication;
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
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
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
    private final float MOVE_SPEED = 6f;

    // --- Dodge state ---
    private boolean dodging = false;
    private float dodgeTimer = 0f;
    private float dodgeCooldown = 0f;
    // Fallback only — used if the "Roll" clip can't be found/measured.
    private final float DODGE_DURATION_FALLBACK = 0.4f;
    // Actual length of the "Roll" animation clip, read from the AnimComposer
    // at startup. The dodge state now lasts exactly as long as the clip
    // plays, instead of a hardcoded guess that was cutting the animation
    // off early (which is why it looked like a bow instead of a roll).
    private float rollClipLength = DODGE_DURATION_FALLBACK;
    private final float DODGE_COOLDOWN_TIME = 0.8f;
    private final float DODGE_SPEED = 14f;
    private final Vector3f dodgeDirection = new Vector3f();
    private final Vector3f lastMoveDir = new Vector3f();

    // --- Crouch state ---
    private boolean crouching = false;
    private final float CROUCH_SPEED_MULTIPLIER = 0.5f;
    // Names of the crouch clips actually available on the rig, resolved once
    // at startup so we don't repeatedly query / risk a missing-clip crash.
    // Resolved with a "contains" match (see findClipContaining) instead of
    // exact-name match, since rigs rarely use the exact literal "Crouch".
    private String crouchIdleClipName = null;
    private String crouchWalkClipName = null;

    // --- Jump / fall state ---
    // Same story as crouch: resolved loosely against whatever clip names
    // actually exist on the rig, logged at startup for verification.
    private String jumpClipName = null;
    private String fallClipName = null;

    // --- Gravity shaping ---
    // A single constant gravity value applied equally on the way up and
    // down reads as "floaty" — real platformers pull harder on the way
    // down than on the way up. These multipliers are applied every frame
    // in simpleUpdate() based on current vertical velocity.
    private final float BASE_GRAVITY = 30f;
    private final float FALL_GRAVITY_MULTIPLIER = 2.5f;
    private final float RISE_GRAVITY_MULTIPLIER = 1.1f;

    private float camYaw = 0f;
    private float camPitch = 0.3f;
    private float camDistance = 8f;

    private final float HORIZONTAL_SENSITIVITY = 3f;
    private final float VERTICAL_SENSITIVITY = 1f;

    private Node forestZoneNode;

    private EnvironmentCamera envCam;
    private boolean lightProbeBaked = false;

    private final AnalogListener analogListener = (name, value, tpf) -> {

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

        camPitch = FastMath.clamp(
                camPitch,
                -1.2f,
                1.2f
        );
    };

    public static void main(String[] args) {

        ForestBiome app = new ForestBiome();

        AppSettings settings = new AppSettings(true);

        GraphicsDevice device =
                GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice();

        DisplayMode dm = device.getDisplayMode();

        settings.setResolution(
                dm.getWidth(),
                dm.getHeight()
        );

        settings.setFrequency(
                dm.getRefreshRate()
        );

        settings.setBitsPerPixel(
                dm.getBitDepth() > 0 ? dm.getBitDepth() : 24
        );

        boolean fullscreenSupported =
                device.isFullScreenSupported();

        settings.setFullscreen(fullscreenSupported);

        if (!fullscreenSupported) {
            // Fall back to a borderless-looking maximized window at native
            // resolution if exclusive fullscreen isn't supported on this
            // display/driver combo.
            System.out.println(
                    "Exclusive fullscreen not supported on this device — "
                            + "falling back to windowed at native resolution."
            );
        }

        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {

        viewPort.setBackgroundColor(
                new ColorRGBA(
                        0.45f,
                        0.65f,
                        0.82f,
                        1f
                )
        );

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

        forestZoneNode = new Node("ForestZone");
        rootNode.attachChild(forestZoneNode);

        buildSky();
        buildGroundPlane(80f);
        buildBoundaryWalls(80f);
        buildForest();

        // Lighting
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(
                new Vector3f(
                        -0.5f,
                        -1f,
                        -0.5f
                ).normalizeLocal()
        );
        sun.setColor(
                ColorRGBA.White.mult(1.2f)
        );
        rootNode.addLight(sun);

        DirectionalLight fill = new DirectionalLight();
        fill.setDirection(
                new Vector3f(
                        0.5f,
                        -0.5f,
                        0.5f
                ).normalizeLocal()
        );
        fill.setColor(
                ColorRGBA.White.mult(0.5f)
        );
        rootNode.addLight(fill);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(
                ColorRGBA.White.mult(0.8f)
        );
        rootNode.addLight(ambient);

        // Player
        Spatial playerModel =
                assetManager.loadModel(
                        "Models/Characters/Player/player.gltf"
                );

        playerNode = new Node("Player");
        playerNode.attachChild(playerModel);
        rootNode.attachChild(playerNode);

        playerControl =
                new BetterCharacterControl(
                        0.5f,
                        1.8f,
                        1f
                );

        // Bumped up from 8f: with the new heavier fall gravity, the old
        // jump force produced a noticeably shorter arc than before. This
        // restores a similar apex height while keeping the harder landing.
        playerControl.setJumpForce(
                new Vector3f(
                        0,
                        15f,
                        0
                )
        );

        playerControl.setGravity(
                new Vector3f(
                        0,
                        -BASE_GRAVITY,
                        0
                )
        );

        playerNode.addControl(playerControl);
        bulletAppState
                .getPhysicsSpace()
                .add(playerControl);

        playerControl
                .getRigidBody()
                .setCcdMotionThreshold(0.1f);

        playerControl
                .getRigidBody()
                .setCcdSweptSphereRadius(0.5f);

        playerControl.warp(
                new Vector3f(
                        0,
                        5f,
                        0
                )
        );

        animComposer =
                findAnimComposer(playerModel);

        if (animComposer != null) {

            playAnim("Idle");

            // Print every clip name actually present on the rig once, so
            // mismatched clip names are obvious in the log instead of
            // silently failing to resolve below.
            System.out.println(
                    "Available animation clips: "
                            + animComposer.getAnimClipsNames()
            );

            if (animComposer.getAnimClipsNames().contains("Roll")) {
                rollClipLength =
                        (float) animComposer.getAnimClip("Roll").getLength();
            } else {
                System.out.println(
                        "WARNING: No 'Roll' clip found — falling back to "
                                + DODGE_DURATION_FALLBACK
                                + "s dodge duration. Check the clip name above."
                );
            }

            // Loose "contains" matching instead of exact-name matching.
            // The previous exact-match version silently found nothing if
            // the rig's clip was named e.g. "Crouching" or "Duck" instead
            // of the literal string "Crouch" — which is why crouch never
            // visibly did anything even though setDucked() was firing.
            crouchIdleClipName = findClipContaining("crouch", "duck", "sneak", "crawl");
            crouchWalkClipName = findClipContaining("crouchwalk", "crouch_walk", "sneakwalk", "duckwalk");

            jumpClipName = findClipContaining("jump");
            fallClipName = findClipContaining("fall", "inair", "airborne", "midair");

            if (crouchIdleClipName == null) {
                System.out.println(
                        "WARNING: No crouch clip found — crouching will only "
                                + "affect collision/speed, not the pose. Check the "
                                + "clip list above and adjust findClipContaining() "
                                + "keywords if your clip uses a different name."
                );
            }

            if (jumpClipName == null && fallClipName == null) {
                System.out.println(
                        "WARNING: No jump/fall clip found — airborne state will "
                                + "have no animation. Check the clip list above."
                );
            }
        }

        initKeys();

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(false);

        inputManager.addMapping(
                "MouseX+",
                new MouseAxisTrigger(
                        MouseInput.AXIS_X,
                        false
                )
        );

        inputManager.addMapping(
                "MouseX-",
                new MouseAxisTrigger(
                        MouseInput.AXIS_X,
                        true
                )
        );

        inputManager.addMapping(
                "MouseY+",
                new MouseAxisTrigger(
                        MouseInput.AXIS_Y,
                        false
                )
        );

        inputManager.addMapping(
                "MouseY-",
                new MouseAxisTrigger(
                        MouseInput.AXIS_Y,
                        true
                )
        );

        inputManager.addListener(
                analogListener,
                "MouseX+",
                "MouseX-",
                "MouseY+",
                "MouseY-"
        );

        // Attach here, but DON'T bake yet — EnvironmentCamera.getApplication()
        // is still null until the AppStateManager processes this attach on
        // the next update cycle. Baking is deferred to the first simpleUpdate().
        envCam = new EnvironmentCamera();
        stateManager.attach(envCam);
    }

    // Loose keyword search over the rig's actual clip names, case-insensitive.
    // Returns the first clip whose name contains any of the given keywords,
    // or null if none match. Used for crouch/jump/fall clip resolution so we
    // don't depend on one exact literal name that the rig may not use.
    private String findClipContaining(String... keywords) {

        if (animComposer == null) {
            return null;
        }

        for (String clip : animComposer.getAnimClipsNames()) {

            String lower = clip.toLowerCase();

            for (String keyword : keywords) {
                if (lower.contains(keyword)) {
                    return clip;
                }
            }
        }

        return null;
    }

    // Lighting bake — PBR materials render flat grey without a LightProbe
    // to provide indirect/environment lighting. Directional + Ambient
    // lights alone are not enough for PBR shading.
    private void bakeLightProbe() {

        LightProbeFactory.makeProbe(envCam, rootNode, new JobProgressAdapter<LightProbe>() {
            @Override
            public void done(LightProbe result) {
                rootNode.addLight(result);
            }
        });
    }

    // Sky
    private void buildSky() {

        int width = 1024;
        int height = 512;

        BufferedImage img =
                new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

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

        // Flip vertically: AWT's Y grows down, texture Y grows up
        BufferedImage flipped =
                new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = flipped.createGraphics();
        g2.drawImage(img, 0, height, width, -height, null);
        g2.dispose();

        AWTLoader awtLoader = new AWTLoader();
        com.jme3.texture.Image jmeImage = awtLoader.load(flipped, false);

        Texture2D skyTexture = new Texture2D(jmeImage);
        skyTexture.setWrap(Texture.WrapMode.Repeat);

        Spatial sky = SkyFactory.createSky(
                assetManager,
                skyTexture,
                SkyFactory.EnvMapType.EquirectMap
        );

        rootNode.attachChild(sky);
    }

    // Ground
    private void buildGroundPlane(float halfExtent) {

        Spatial grassModel =
                assetManager.loadModel(
                        "Models/Environment/Forest/ground_grass.glb"
                );

        fixEnvironmentMaterials(
                grassModel,
                new ColorRGBA(
                        0.25f,
                        0.45f,
                        0.20f,
                        1f
                )
        );

        // Grass mesh is 1x1 unit at scale 1 (confirmed via bounds check).
        // tileScale IS the actual world-space size of each tile once scaled,
        // so tileSize == tileScale (no separate assumed constant here).
        float tileScale = 20f;
        float tileSize = tileScale;

        // Enough tiles to fully cover -halfExtent..halfExtent with no gaps
        int half = (int) Math.ceil(halfExtent / tileSize) + 1;

        for (int x = -half; x <= half; x++) {

            for (int z = -half; z <= half; z++) {

                Spatial grass =
                        grassModel.clone();

                grass.setLocalTranslation(
                        x * tileSize,
                        0,
                        z * tileSize
                );

                grass.setLocalScale(tileScale);

                forestZoneNode.attachChild(grass);
            }
        }

        BoxCollisionShape shape =
                new BoxCollisionShape(
                        new Vector3f(
                                halfExtent,
                                0.5f,
                                halfExtent
                        )
                );

        RigidBodyControl physics =
                new RigidBodyControl(
                        shape,
                        0
                );

        physics.setPhysicsLocation(
                new Vector3f(
                        0,
                        -0.5f,
                        0
                )
        );

        bulletAppState
                .getPhysicsSpace()
                .add(physics);
    }

    // Boundary
    private void buildBoundaryWalls(float halfExtent) {

        float wallHeight = 10f;
        float wallThickness = 1f;

        createWall(
                new Vector3f(
                        0,
                        wallHeight / 2f,
                        halfExtent
                ),
                new Vector3f(
                        halfExtent,
                        wallHeight / 2f,
                        wallThickness
                )
        );

        createWall(
                new Vector3f(
                        0,
                        wallHeight / 2f,
                        -halfExtent
                ),
                new Vector3f(
                        halfExtent,
                        wallHeight / 2f,
                        wallThickness
                )
        );

        createWall(
                new Vector3f(
                        halfExtent,
                        wallHeight / 2f,
                        0
                ),
                new Vector3f(
                        wallThickness,
                        wallHeight / 2f,
                        halfExtent
                )
        );

        createWall(
                new Vector3f(
                        -halfExtent,
                        wallHeight / 2f,
                        0
                ),
                new Vector3f(
                        wallThickness,
                        wallHeight / 2f,
                        halfExtent
                )
        );
    }

    private void createWall(
            Vector3f position,
            Vector3f halfExtents
    ) {

        BoxCollisionShape shape =
                new BoxCollisionShape(
                        halfExtents
                );

        RigidBodyControl physics =
                new RigidBodyControl(
                        shape,
                        0
                );

        physics.setPhysicsLocation(position);

        bulletAppState
                .getPhysicsSpace()
                .add(physics);
    }

    // Forest
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

            float x =
                    (rand.nextFloat() - 0.5f) * 135f;

            float z =
                    (rand.nextFloat() - 0.5f) * 135f;

            if (
                    new Vector3f(
                            x,
                            0,
                            z
                    ).length() < 15f
            ) {
                i--;
                continue;
            }

            placeTree(
                    x,
                    z,
                    rand,
                    treeModels
            );
        }

        placeRock(
                20f,
                15f,
                4f,
                rand,
                rockModels
        );

        placeRock(
                -25f,
                -10f,
                5f,
                rand,
                rockModels
        );

        placeRock(
                10f,
                -30f,
                3.5f,
                rand,
                rockModels
        );

        // Decorative small-stone scatter across the ground — purely visual, no physics
        scatterPebbles(rockModels);
    }

    // Decorative pebble scatter (no collision bodies — keeps this cheap)
    private void scatterPebbles(String[] rockModels) {

        Random rand = new Random(99);
        int pebbleCount = 300;

        for (int i = 0; i < pebbleCount; i++) {

            float x = (rand.nextFloat() - 0.5f) * 150f;
            float z = (rand.nextFloat() - 0.5f) * 150f;

            String chosenModel =
                    rockModels[rand.nextInt(rockModels.length)];

            if (!chosenModel.contains("small")) {
                continue; // only tiny stones for scatter
            }

            Spatial pebble =
                    assetManager.loadModel(chosenModel);

            fixEnvironmentMaterials(
                    pebble,
                    new ColorRGBA(0.40f, 0.40f, 0.40f, 1f)
            );

            pebble.setLocalTranslation(x, 0, z);

            pebble.rotate(
                    0,
                    rand.nextFloat() * FastMath.TWO_PI,
                    0
            );

            pebble.setLocalScale(
                    0.3f + rand.nextFloat() * 0.4f
            );

            forestZoneNode.attachChild(pebble);
        }
    }

    private void placeTree(
            float x,
            float z,
            Random rand,
            String[] treeModels
    ) {

        String chosenModel =
                treeModels[
                        rand.nextInt(
                                treeModels.length
                        )
                        ];

        Spatial tree =
                assetManager.loadModel(
                        chosenModel
                );

        fixEnvironmentMaterials(
                tree,
                new ColorRGBA(
                        0.20f,
                        0.38f,
                        0.16f,
                        1f
                )
        );

        tree.setLocalTranslation(
                x,
                0,
                z
        );

        tree.rotate(
                0,
                rand.nextFloat()
                        * FastMath.TWO_PI,
                0
        );

        float baseScale = 4f;

        if (chosenModel.contains("default")) {
            baseScale = 4.5f;
        }

        if (chosenModel.contains("cone")) {
            baseScale = 4.5f;
        }

        if (chosenModel.contains("detailed")) {
            baseScale = 4f;
        }

        if (chosenModel.contains("oak")) {
            baseScale = 3.5f;
        }

        if (chosenModel.contains("fat")) {
            baseScale = 4f;
        }

        if (chosenModel.contains("blocks")) {
            baseScale = 4f;
        }

        float scale =
                baseScale
                        + rand.nextFloat() * 0.8f;

        tree.setLocalScale(scale);

        CapsuleCollisionShape trunkShape =
                new CapsuleCollisionShape(
                        0.7f,
                        3.5f
                );

        RigidBodyControl physics =
                new RigidBodyControl(
                        trunkShape,
                        0
                );

        physics.setPhysicsLocation(
                new Vector3f(
                        x,
                        1.8f,
                        z
                )
        );

        forestZoneNode.attachChild(tree);

        bulletAppState
                .getPhysicsSpace()
                .add(physics);
    }

    private void placeRock(
            float x,
            float z,
            float size,
            Random rand,
            String[] rockModels
    ) {

        String chosenModel =
                rockModels[
                        rand.nextInt(
                                rockModels.length
                        )
                        ];

        Spatial rock =
                assetManager.loadModel(
                        chosenModel
                );

        fixEnvironmentMaterials(
                rock,
                new ColorRGBA(
                        0.40f,
                        0.40f,
                        0.40f,
                        1f
                )
        );

        rock.setLocalTranslation(
                x,
                0,
                z
        );

        rock.rotate(
                0,
                rand.nextFloat()
                        * FastMath.TWO_PI,
                0
        );

        rock.setLocalScale(
                size / 2f
        );

        forestZoneNode.attachChild(rock);

        // The collision shape used to be built from a hardcoded guess
        // (size, size * 0.6f, size) that had nothing to do with the actual
        // mesh — different rock models (stone_smallA vs stone_tallD, etc.)
        // have very different proportions, so that one formula could never
        // fit all of them. Instead, compute the real world-space bounding
        // box after the model is attached and scaled, and build the
        // collision box from that.
        rock.updateModelBound();
        forestZoneNode.updateGeometricState();

        BoundingBox bbox = getWorldBoundingBox(rock);

        Vector3f halfExtents =
                bbox != null
                        ? bbox.getExtent(new Vector3f())
                        : new Vector3f(size, size * 0.6f, size); // fallback if bounds unavailable

        Vector3f center =
                bbox != null
                        ? bbox.getCenter().clone()
                        : new Vector3f(x, size * 0.6f, z);

        BoxCollisionShape shape =
                new BoxCollisionShape(
                        halfExtents
                );

        RigidBodyControl physics =
                new RigidBodyControl(
                        shape,
                        0
                );

        physics.setPhysicsLocation(center);

        bulletAppState
                .getPhysicsSpace()
                .add(physics);
    }

    // Returns the world-space bounding box of a spatial, or null if none is
    // available. Node bounds (ModelBound) computed after attachment/scale
    // give the true extent of the loaded mesh, unlike a hand-picked guess.
    private BoundingBox getWorldBoundingBox(Spatial spatial) {

        if (spatial.getWorldBound() instanceof BoundingBox) {
            return (BoundingBox) spatial.getWorldBound();
        }

        return null;
    }

    // Materials
    //
    // IMPORTANT: glTF/GLB models loaded through jME's importer already carry
    // correct PBR materials (with their real textures). We must NOT rebuild
    // a material for geometry that already has one — doing so was the cause
    // of every model rendering as a flat fallback color instead of its real
    // texture. We only fabricate a material when a geometry truly has none.
    private void fixEnvironmentMaterials(
            Spatial spatial,
            ColorRGBA fallbackColor
    ) {

        if (spatial instanceof Geometry) {

            Geometry geometry =
                    (Geometry) spatial;

            Material existingMaterial =
                    geometry.getMaterial();

            if (existingMaterial == null) {

                Material material =
                        new Material(
                                assetManager,
                                "Common/MatDefs/Light/Lighting.j3md"
                        );

                material.setBoolean(
                        "UseMaterialColors",
                        true
                );

                material.setColor(
                        "Diffuse",
                        fallbackColor
                );

                material.setColor(
                        "Specular",
                        ColorRGBA.White
                );

                material.setFloat(
                        "Shininess",
                        8f
                );

                geometry.setMaterial(material);

            } else {

                boolean hasVertexColors =
                        geometry.getMesh()
                                .getBuffer(VertexBuffer.Type.Color) != null;

                boolean isPbr =
                        existingMaterial.getMaterialDef()
                                .getAssetName()
                                .contains("PBRLighting");

                if (isPbr) {

                    // glTF defaults metallicFactor/roughnessFactor to 1.0
                    // when no metallic-roughness texture is present. Fully
                    // metallic + fully rough with no environment texture
                    // renders almost black — it only lights from probe
                    // reflections, which is why untextured low-poly packs
                    // like this one come out flat black/grey. Force a matte,
                    // non-metallic look so it lights normally instead.
                    if (existingMaterial.getMaterialDef().getMaterialParam("Metallic") != null) {
                        existingMaterial.setFloat("Metallic", 0f);
                    }

                    if (existingMaterial.getMaterialDef().getMaterialParam("Roughness") != null) {
                        existingMaterial.setFloat("Roughness", 1f);
                    }

                    // NOTE: deliberately NOT touching "BaseColor" here.
                    // These models carry real per-submesh colors (brown
                    // trunks, green canopies, grey stone) via their own
                    // BaseColorFactor from the glTF file — forcing it to
                    // white wipes that out, which is why everything turned
                    // flat white. Only Metallic/Roughness needed correcting.
                }

                // Untextured low-poly packs often store color per-vertex
                // rather than via a texture map. If the mesh has a color
                // buffer, make sure the material actually samples it.
                if (hasVertexColors
                        && existingMaterial.getMaterialDef().getMaterialParam("UseVertexColor") != null) {

                    existingMaterial.setBoolean("UseVertexColor", true);
                }
            }

            // else branch above: leave the loaded material's textures untouched
        }

        if (spatial instanceof Node) {

            for (
                    Spatial child
                    :
                    ((Node) spatial).getChildren()
            ) {

                fixEnvironmentMaterials(
                        child,
                        fallbackColor
                );
            }
        }
    }

    // Animation
    private AnimComposer findAnimComposer(
            Spatial spatial
    ) {

        AnimComposer composer =
                spatial.getControl(
                        AnimComposer.class
                );

        if (composer != null) {
            return composer;
        }

        if (spatial instanceof Node) {

            for (
                    Spatial child
                    :
                    ((Node) spatial).getChildren()
            ) {

                AnimComposer result =
                        findAnimComposer(child);

                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private void playAnim(String name) {

        if (animComposer == null) {
            return;
        }

        if (name == null) {
            return;
        }

        if (!currentAnim.equals(name)) {

            animComposer.setCurrentAction(
                    name
            );

            currentAnim = name;
        }
    }

    // Input
    private void initKeys() {

        inputManager.addMapping(
                "Left",
                new KeyTrigger(
                        KeyInput.KEY_A
                )
        );

        inputManager.addMapping(
                "Right",
                new KeyTrigger(
                        KeyInput.KEY_D
                )
        );

        inputManager.addMapping(
                "Forward",
                new KeyTrigger(
                        KeyInput.KEY_W
                )
        );

        inputManager.addMapping(
                "Backward",
                new KeyTrigger(
                        KeyInput.KEY_S
                )
        );

        inputManager.addMapping(
                "Jump",
                new KeyTrigger(
                        KeyInput.KEY_SPACE
                )
        );

        inputManager.addMapping(
                "Dodge",
                new KeyTrigger(
                        KeyInput.KEY_LSHIFT
                )
        );
        inputManager.addMapping(
                "Crouch",
                new KeyTrigger(
                        KeyInput.KEY_LCONTROL
                )
        );

        inputManager.addListener(this, "Left", "Right", "Forward", "Backward", "Jump", "Dodge", "Crouch");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "Left": left = isPressed; break;
            case "Right": right = isPressed; break;
            case "Forward": forward = isPressed; break;
            case "Backward": backward = isPressed; break;
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
                // Left in place for quick verification in the console that
                // the key event is actually being received (e.g. rules out
                // an OS/focus/key-binding issue if crouch still seems dead).
                System.out.println("Crouch pressed: " + isPressed);
                playerControl.setDucked(crouching);
                break;
        }
    }

    private void startDodge() {
        dodging = true;
        dodgeTimer = rollClipLength;
        dodgeCooldown = DODGE_COOLDOWN_TIME;
        dodgeDirection.set(lastMoveDir);
        playAnim("Roll");
    }

    // Update
    @Override
    public void simpleUpdate(float tpf) {

        // Fire once: by the first simpleUpdate() call, the AppStateManager
        // has already run envCam's initialize() during this frame's
        // stateManager.update(), so it's now safe to bake.
        if (!lightProbeBaked && envCam.getApplication() != null) {
            bakeLightProbe();
            lightProbeBaked = true;
        }

        // Cooldown ticking down regardless of state
        if (dodgeCooldown > 0f) {
            dodgeCooldown -= tpf;
        }

        // Heavier fall than rise: apply less gravity on the way up (a
        // snappier, more controllable ascent) and noticeably more on the
        // way down (a punchier, less floaty landing). Recomputed every
        // frame from current vertical velocity.
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

        // Store the raw (unscaled) direction for dodge to use, whether or not we're currently moving
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

        // Animation priority: dodge > airborne (jump/fall) > crouch > walk/idle.
        // Crouch plays a pose (if the rig has one) instead of only shrinking
        // the physics capsule via setDucked(). Airborne now also gets its
        // own pose instead of silently falling through to Idle/Walk, which
        // is why jumping used to look like the model just floating upward
        // with no animation at all.
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

        Vector3f playerPos =
                playerNode
                        .getWorldTranslation()
                        .clone();

        Vector3f offset =
                new Vector3f(
                        FastMath.sin(camYaw)
                                * FastMath.cos(camPitch),

                        FastMath.sin(camPitch),

                        FastMath.cos(camYaw)
                                * FastMath.cos(camPitch)
                ).multLocal(
                        camDistance
                );

        cam.setLocation(
                playerPos
                        .add(offset)
                        .add(0, 1.5f, 0)
        );

        cam.lookAt(
                playerPos.add(0, 1.5f, 0),
                Vector3f.UNIT_Y
        );
    }
}