package com.vaelmourn;

import com.jme3.anim.AnimComposer;
import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
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
import com.jme3.scene.shape.Box;

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
            case "MouseX+": camYaw -= value * HORIZONTAL_SENSITIVITY; break;
            case "MouseX-": camYaw += value * HORIZONTAL_SENSITIVITY; break;
            case "MouseY+": camPitch += value * VERTICAL_SENSITIVITY; break;
            case "MouseY-": camPitch -= value * VERTICAL_SENSITIVITY; break;
        }
        camPitch = FastMath.clamp(camPitch, -1.2f, 1.2f);
    };

    public static void main(String[] args) {
        ForestBiome app = new ForestBiome();
        app.start();
    }

    @Override
    public void simpleInitApp() {

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

        buildGroundPlane(80f); // 160m x 160m total, generous room around your 80x80 zone
        buildBoundaryWalls(80f);

        forestZoneNode = new Node("ForestZone");
        rootNode.attachChild(forestZoneNode);
        greyBoxForest();

        // --- LIGHTING ---
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal());
        rootNode.addLight(sun);
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.4f));
        rootNode.addLight(ambient);

        // --- PLAYER ---
        Spatial playerModel = assetManager.loadModel("Models/Characters/Player/player.gltf");
        playerNode = new Node("Player");
        playerNode.attachChild(playerModel);
        rootNode.attachChild(playerNode);

        playerControl = new BetterCharacterControl(0.5f, 1.8f, 1f);
        playerControl.setJumpForce(new Vector3f(0, 8f, 0));
        playerControl.setGravity(new Vector3f(0, -30f, 0));
        playerNode.addControl(playerControl);
        bulletAppState.getPhysicsSpace().add(playerControl);
        playerControl.getRigidBody().setCcdMotionThreshold(0.1f);
        playerControl.getRigidBody().setCcdSweptSphereRadius(0.5f);
        playerControl.warp(new Vector3f(0, 5f, 0)); // spawn at center of the cleared area

        animComposer = findAnimComposer(playerModel);
        if (animComposer != null) {
            playAnim("Idle");
        }

        initKeys();

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(false);
        inputManager.addMapping("MouseX+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MouseX-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MouseY+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("MouseY-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addListener(analogListener, "MouseX+", "MouseX-", "MouseY+", "MouseY-");
    }

    /** Flat ground plane, sized generously beyond the zone footprint. */
    private void buildGroundPlane(float halfExtent) {
        Box groundBox = new Box(halfExtent, 0.5f, halfExtent);
        Geometry ground = new Geometry("Ground", groundBox);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.35f, 0.45f, 0.3f, 1f)); // dull green stand-in for forest floor
        ground.setMaterial(mat);
        ground.setLocalTranslation(0, -0.5f, 0);

        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(halfExtent, 0.5f, halfExtent));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        ground.addControl(physics);

        rootNode.attachChild(ground);
        bulletAppState.getPhysicsSpace().add(physics);
    }

    /** Invisible walls around the zone edge so the player can't wander into the void. */
    private void buildBoundaryWalls(float halfExtent) {
        float wallHeight = 10f;
        float wallThickness = 1f;

        // North, South, East, West walls
        createWall(new Vector3f(0, wallHeight / 2, halfExtent), new Vector3f(halfExtent, wallHeight / 2, wallThickness));
        createWall(new Vector3f(0, wallHeight / 2, -halfExtent), new Vector3f(halfExtent, wallHeight / 2, wallThickness));
        createWall(new Vector3f(halfExtent, wallHeight / 2, 0), new Vector3f(wallThickness, wallHeight / 2, halfExtent));
        createWall(new Vector3f(-halfExtent, wallHeight / 2, 0), new Vector3f(wallThickness, wallHeight / 2, halfExtent));
    }

    private void createWall(Vector3f position, Vector3f halfExtents) {
        Box wallBox = new Box(halfExtents.x, halfExtents.y, halfExtents.z);
        Geometry wall = new Geometry("BoundaryWall", wallBox);
        wall.setLocalTranslation(position);

        // Invisible: attach physics but don't attach to rootNode for rendering...
        // actually we DO attach it, just make it fully transparent-looking via unshaded dark material for now (debug-visible)
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(1f, 0f, 0f, 0.15f));
        mat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        wall.setMaterial(mat);
        wall.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);

        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        wall.addControl(physics);

        rootNode.attachChild(wall);
        bulletAppState.getPhysicsSpace().add(physics);
    }

    /** Grey-box stand-ins for trees and rocks, laid out to create paths and blocked sightlines. */
    private void greyBoxForest() {
        Random rand = new Random(42); // fixed seed so layout is reproducible between runs

        // Cleared spawn area: leave roughly a 12m radius around origin empty

        // Scatter "trees" (thin tall boxes) in a rough ring/cluster pattern, avoiding the spawn clearing
        int treeCount = 60;
        for (int i = 0; i < treeCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * 70f;
            float z = (rand.nextFloat() - 0.5f) * 70f;

            if (new Vector3f(x, 0, z).length() < 12f) continue; // skip spawn clearing

            placeTreeStandIn(x, z, rand);
        }

        // A few large "rock formations" to block sightlines
        placeRockStandIn(20f, 15f, 4f);
        placeRockStandIn(-25f, -10f, 5f);
        placeRockStandIn(10f, -30f, 3.5f);
    }

    private void placeTreeStandIn(float x, float z, Random rand) {
        float height = 4f + rand.nextFloat() * 3f; // 4-7m tall
        Box trunk = new Box(0.4f, height / 2, 0.4f);
        Geometry tree = new Geometry("TreeStandIn", trunk);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.25f, 0.18f, 0.1f, 1f)); // brown
        tree.setMaterial(mat);
        tree.setLocalTranslation(x, height / 2, z);

        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(0.4f, height / 2, 0.4f));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        tree.addControl(physics);

        forestZoneNode.attachChild(tree);
        bulletAppState.getPhysicsSpace().add(physics);
    }

    private void placeRockStandIn(float x, float z, float size) {
        Box rock = new Box(size, size * 0.6f, size);
        Geometry rockGeom = new Geometry("RockStandIn", rock);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.4f, 0.4f, 0.4f, 1f)); // grey
        rockGeom.setMaterial(mat);
        rockGeom.setLocalTranslation(x, size * 0.6f, z);

        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(size, size * 0.6f, size));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        rockGeom.addControl(physics);

        forestZoneNode.attachChild(rockGeom);
        bulletAppState.getPhysicsSpace().add(physics);
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
        inputManager.addListener(this, "Left", "Right", "Forward", "Backward", "Jump");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "Left": left = isPressed; break;
            case "Right": right = isPressed; break;
            case "Forward": forward = isPressed; break;
            case "Backward": backward = isPressed; break;
            case "Jump": if (isPressed) playerControl.jump(); break;
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        Camera camera = cam;
        Vector3f camDir = camera.getDirection().clone().setY(0).normalizeLocal();
        Vector3f camLeft = camera.getLeft().clone().setY(0).normalizeLocal();

        walkDirection.set(0, 0, 0);
        if (forward) walkDirection.addLocal(camDir);
        if (backward) walkDirection.addLocal(camDir.negate());
        if (left) walkDirection.addLocal(camLeft);
        if (right) walkDirection.addLocal(camLeft.negate());

        boolean isMoving = forward || backward || left || right;
        walkDirection.normalizeLocal().multLocal(MOVE_SPEED);
        playerControl.setWalkDirection(walkDirection);

        if (isMoving) playerControl.setViewDirection(walkDirection);
        playAnim(isMoving ? "Walk" : "Idle");

        Vector3f playerPos = playerNode.getLocalTranslation();
        Vector3f offset = new Vector3f(
                FastMath.sin(camYaw) * FastMath.cos(camPitch),
                FastMath.sin(camPitch),
                FastMath.cos(camYaw) * FastMath.cos(camPitch)
        ).multLocal(camDistance);

        cam.setLocation(playerPos.add(offset).add(0, 1.5f, 0));
        cam.lookAt(playerPos.add(0, 1.5f, 0), Vector3f.UNIT_Y);
    }
}