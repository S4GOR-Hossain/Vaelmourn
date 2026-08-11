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

public class PlayerTest extends SimpleApplication implements ActionListener {

    private BulletAppState bulletAppState;
    private BetterCharacterControl playerControl;
    private Node playerNode;
    private AnimComposer animComposer;
    private String currentAnim = "";

    private boolean left, right, forward, backward;
    private final Vector3f walkDirection = new Vector3f();
    private final float MOVE_SPEED = 6f;

    // --- Custom orbit camera state ---
    private float camYaw = 0f;
    private float camPitch = 0.3f;
    private float camDistance = 8f;
    private final float HORIZONTAL_SENSITIVITY = 3f;
    private final float VERTICAL_SENSITIVITY = 1f;

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
        PlayerTest app = new PlayerTest();
        app.start();
    }

    @Override
    public void simpleInitApp() {

        // Physics setup
        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

        // --- FLOOR ---
        Box floorBox = new Box(50f, 0.5f, 50f);
        Geometry floor = new Geometry("Floor", floorBox);
        Material floorMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        floorMat.setColor("Color", ColorRGBA.Gray);
        floor.setMaterial(floorMat);
        floor.setLocalTranslation(0, -0.5f, 0);

        BoxCollisionShape floorShape = new BoxCollisionShape(new Vector3f(50f, 0.5f, 50f));
        RigidBodyControl floorPhysics = new RigidBodyControl(floorShape, 0);
        floor.addControl(floorPhysics);

        rootNode.attachChild(floor);
        bulletAppState.getPhysicsSpace().add(floorPhysics);

        // --- LIGHTING ---
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal());
        rootNode.addLight(sun);
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.4f));
        rootNode.addLight(ambient);

        // --- PLAYER VISUAL (real model) ---
        Spatial playerModel = assetManager.loadModel("Models/Characters/Player/player.gltf");

        playerNode = new Node("Player");
        playerNode.attachChild(playerModel);
        rootNode.attachChild(playerNode);

        // --- PLAYER PHYSICS ---
        playerControl = new BetterCharacterControl(0.5f, 1.8f, 1f);
        playerControl.setJumpForce(new Vector3f(0, 8f, 0));
        playerControl.setGravity(new Vector3f(0, -30f, 0));
        playerNode.addControl(playerControl);
        bulletAppState.getPhysicsSpace().add(playerControl);

        playerControl.getRigidBody().setCcdMotionThreshold(0.1f);
        playerControl.getRigidBody().setCcdSweptSphereRadius(0.5f);

        playerControl.warp(new Vector3f(0, 5f, 0));

        // --- ANIMATION SETUP ---
        animComposer = findAnimComposer(playerModel);
        if (animComposer != null) {
            System.out.println("Available animations: " + animComposer.getAnimClipsNames());
            playAnim("Idle");
        } else {
            System.out.println("No AnimComposer found on this model.");
        }

        // --- INPUT ---
        initKeys();

        // --- CAMERA ---
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(false);

        inputManager.addMapping("MouseX+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MouseX-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MouseY+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("MouseY-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));

        inputManager.addListener(analogListener, "MouseX+", "MouseX-", "MouseY+", "MouseY-");
    }

    private AnimComposer findAnimComposer(Spatial spatial) {
        AnimComposer composer = spatial.getControl(AnimComposer.class);
        if (composer != null) {
            return composer;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                AnimComposer result = findAnimComposer(child);
                if (result != null) {
                    return result;
                }
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
            case "Jump":
                if (isPressed) playerControl.jump();
                break;
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

        if (isMoving) {
            playerControl.setViewDirection(walkDirection);
        }

        if (isMoving) {
            playAnim("Walk");
        } else {
            playAnim("Idle");
        }

        // --- Custom orbit camera positioning ---
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