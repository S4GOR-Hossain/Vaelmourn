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

    public Chest(Vector3f position) {
        this.position = position;
        this.node = new Node("Chest");
        this.node.setLocalTranslation(position);
    }

    /**
     * Build the chest geometry and physics in the world.
     */
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        // Create a simple box to represent the chest
        Box chestBox = new Box(0.5f, 0.6f, 0.5f);
        Geometry chestGeo = new Geometry("ChestGeometry", chestBox);

        Material chestMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        chestMat.setBoolean("UseMaterialColors", true);
        chestMat.setColor("Diffuse", new ColorRGBA(0.6f, 0.4f, 0.1f, 1f)); // brownish
        chestMat.setColor("Specular", ColorRGBA.White);
        chestMat.setFloat("Shininess", 8f);
        chestGeo.setMaterial(chestMat);

        node.attachChild(chestGeo);
        parentNode.attachChild(node);

        // Add physics (static, non-moving)
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(0.5f, 0.6f, 0.5f));
        physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position);
        bulletAppState.getPhysicsSpace().add(physics);
    }

    /**
     * Add loot to this chest.
     */
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
        if (opened) return; // Already opened

        opened = true;
        System.out.println("Chest opened! Contains " + lootItems.size() + " item stacks.");

        // Show chest UI (will be created separately or passed in)
        if (chestUI != null) {
            chestUI.show(lootItems, lootCounts);
        }
    }

    /**
     * Set the UI handler for this chest.
     */
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

    /**
     * Interface for the chest UI callback.
     */
    public interface ChestUI {
        void show(List<String> itemIds, List<Integer> counts);
    }
}
