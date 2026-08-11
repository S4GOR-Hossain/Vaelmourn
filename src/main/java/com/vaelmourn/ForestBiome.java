package com.vaelmourn;

import com.jme3.anim.AnimComposer;
import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;

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

    private float camYaw = 0f;
    private float camPitch = 0.3f;
    private float camDistance = 8f;

    private final float HORIZONTAL_SENSITIVITY = 3f;
    private final float VERTICAL_SENSITIVITY = 1f;

    private Node forestZoneNode;

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

        playerControl.setJumpForce(
                new Vector3f(
                        0,
                        8f,
                        0
                )
        );

        playerControl.setGravity(
                new Vector3f(
                        0,
                        -30f,
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

        float tileSize = 80f;

        for (int x = -1; x <= 1; x++) {

            for (int z = -1; z <= 1; z++) {

                Spatial grass =
                        grassModel.clone();

                grass.setLocalTranslation(
                        x * tileSize,
                        0,
                        z * tileSize
                );

                grass.setLocalScale(10f);

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

        BoxCollisionShape shape =
                new BoxCollisionShape(
                        new Vector3f(
                                size,
                                size * 0.6f,
                                size
                        )
                );

        RigidBodyControl physics =
                new RigidBodyControl(
                        shape,
                        0
                );

        physics.setPhysicsLocation(
                new Vector3f(
                        x,
                        size * 0.6f,
                        z
                )
        );

        forestZoneNode.attachChild(rock);

        bulletAppState
                .getPhysicsSpace()
                .add(physics);
    }

    // Materials
    private void fixEnvironmentMaterials(
            Spatial spatial,
            ColorRGBA fallbackColor
    ) {

        if (spatial instanceof Geometry) {

            Geometry geometry =
                    (Geometry) spatial;

            Material oldMaterial =
                    geometry.getMaterial();

            if (oldMaterial == null) {

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

                Texture texture = null;

                if (
                        oldMaterial.getTextureParam(
                                "BaseColorMap"
                        ) != null
                ) {

                    texture =
                            oldMaterial
                                    .getTextureParam(
                                            "BaseColorMap"
                                    )
                                    .getTextureValue();
                }

                if (
                        texture == null
                                &&
                                oldMaterial.getTextureParam(
                                        "DiffuseMap"
                                ) != null
                ) {

                    texture =
                            oldMaterial
                                    .getTextureParam(
                                            "DiffuseMap"
                                    )
                                    .getTextureValue();
                }

                Material material =
                        new Material(
                                assetManager,
                                "Common/MatDefs/Light/Lighting.j3md"
                        );

                material.setBoolean(
                        "UseMaterialColors",
                        true
                );

                if (texture != null) {

                    material.setTexture(
                            "DiffuseMap",
                            texture
                    );

                } else {

                    material.setColor(
                            "Diffuse",
                            fallbackColor
                    );
                }

                material.setColor(
                        "Specular",
                        ColorRGBA.White
                );

                material.setFloat(
                        "Shininess",
                        8f
                );

                geometry.setMaterial(material);
            }
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

        inputManager.addListener(
                this,
                "Left",
                "Right",
                "Forward",
                "Backward",
                "Jump"
        );
    }

    @Override
    public void onAction(
            String name,
            boolean isPressed,
            float tpf
    ) {

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

                if (isPressed) {
                    playerControl.jump();
                }

                break;
        }
    }

    // Update
    @Override
    public void simpleUpdate(float tpf) {

        Camera camera = cam;

        Vector3f camDir =
                camera
                        .getDirection()
                        .clone()
                        .setY(0)
                        .normalizeLocal();

        Vector3f camLeft =
                camera
                        .getLeft()
                        .clone()
                        .setY(0)
                        .normalizeLocal();

        walkDirection.set(
                0,
                0,
                0
        );

        if (forward) {
            walkDirection.addLocal(camDir);
        }

        if (backward) {
            walkDirection.addLocal(
                    camDir.negate()
            );
        }

        if (left) {
            walkDirection.addLocal(camLeft);
        }

        if (right) {
            walkDirection.addLocal(
                    camLeft.negate()
            );
        }

        boolean isMoving =
                forward
                        || backward
                        || left
                        || right;

        if (
                walkDirection.lengthSquared()
                        > 0
        ) {

            walkDirection
                    .normalizeLocal()
                    .multLocal(
                            MOVE_SPEED
                    );
        }

        playerControl.setWalkDirection(
                walkDirection
        );

        if (isMoving) {

            playerControl.setViewDirection(
                    walkDirection
            );
        }

        playAnim(
                isMoving
                        ? "Walk"
                        : "Idle"
        );

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