package com.vaelmourn;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * A chest that contains loot items. When interacted with (via F key),
 * it displays a GUI showing the items inside.
 */
public class Chest implements Interactable {

    private Node node;
    private Vector3f position;
    private List<String> lootItems = new ArrayList<>();
    private List<Integer> lootCounts = new ArrayList<>();
    private boolean opened = false;
    private ChestUI chestUI;
    private RigidBodyControl physics;
    private static final float INTERACT_RANGE = 3.5f;

    /** Half-extents of the closed chest asset, measured from the model's own bounding box. */
    private static final float CHEST_HALF_X = 0.70f;
    private static final float CHEST_HALF_Y = 0.44f;
    private static final float CHEST_HALF_Z = 0.45f;

    public Chest(Vector3f position) {
        this.position = position;
        this.node = new Node("Chest");
        this.node.setLocalTranslation(position);
    }

    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        Spatial model = loadChestModel(assetManager);

        if (model != null) {
            node.attachChild(model);
        } else {
            // Asset missing — fall back to the old box so the hub still has a chest.
            Box chestBox = new Box(CHEST_HALF_X, CHEST_HALF_Y, CHEST_HALF_Z);
            Geometry chestGeo = new Geometry("ChestGeometry", chestBox);
            Material chestMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            chestMat.setBoolean("UseMaterialColors", true);
            chestMat.setColor("Diffuse", new ColorRGBA(0.6f, 0.4f, 0.1f, 1f));
            chestMat.setColor("Specular", ColorRGBA.White);
            chestMat.setFloat("Shininess", 8f);
            chestGeo.setMaterial(chestMat);
            node.attachChild(chestGeo);
        }

        parentNode.attachChild(node);

        // Collider matches the model's footprint and sits on the floor, so the player
        // can't walk through the chest's corners.
        BoxCollisionShape shape = new BoxCollisionShape(
                new Vector3f(CHEST_HALF_X, CHEST_HALF_Y, CHEST_HALF_Z));
        physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(position.x, CHEST_HALF_Y, position.z));
        bulletAppState.getPhysicsSpace().add(physics);
    }

    private static final String CHEST_MODEL = "Models/props/chest.gltf";

    /**
     * Loads the props/chest model and drops it onto the floor.
     *
     * <p>The chest node sits at y=0.6 (its spawn height), so the model is translated by
     * (lift - 0.6): +lift puts the model's base on the node origin, then -0.6 walks it
     * down onto the ground. Measured while detached — see {@link StageDecor#baseLift}.</p>
     */
    private Spatial loadChestModel(AssetManager assetManager) {
        Spatial model;
        try {
            model = assetManager.loadModel(CHEST_MODEL);
        } catch (Exception e) {
            System.out.println("Failed to load chest model: " + e.getMessage());
            return null;
        }
        if (model == null) return null;

        model.setLocalTranslation(0f, StageDecor.baseLift(model) - position.y, 0f);
        return model;
    }

    public void addLoot(String itemId, int count) {
        lootItems.add(itemId);
        lootCounts.add(count);
    }

    @Override
    public Vector3f getPosition() {
        return position;
    }

    @Override
    public boolean isInRange(Vector3f playerPos, float range) {
        return playerPos.distance(position) <= range;
    }

    @Override
    public void interact() {
        if (opened) return; // one-time loot, already opened

        opened = true;
        System.out.println("Chest opened! Contains " + lootItems.size() + " item stacks.");

        if (chestUI != null) {
            chestUI.show(lootItems, lootCounts);
        }
    }

    public void setChestUI(ChestUI ui) {
        this.chestUI = ui;
    }

    @Override
    public Node getNode() {
        return node;
    }

    public RigidBodyControl getPhysics() {
        return physics;
    }

    @Override
    public void cleanup() {
        node.removeFromParent();
    }

    public boolean isOpened() {
        return opened;
    }

    public interface ChestUI {
        void show(List<String> itemIds, List<Integer> counts);
    }
}
