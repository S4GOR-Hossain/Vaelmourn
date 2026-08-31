package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Cylinder;

import java.util.ArrayList;
import java.util.List;

/**
 * An NPC (Non-Player Character) shopkeeper that sells items for soul dust.
 * When interacted with (via F key), displays a shop GUI.
 */
public class NPC implements Interactable {

    private Node node;
    private Vector3f position;
    private String name;
    private List<String> shopItems = new ArrayList<>();
    private List<Integer> shopPrices = new ArrayList<>(); // in soul dust
    private ShopUI shopUI;
    private RigidBodyControl physics;
    private static final float INTERACT_RANGE = 3.5f;

    public NPC(String name, Vector3f position) {
        this.name = name;
        this.position = position;
        this.node = new Node("NPC_" + name);
        this.node.setLocalTranslation(position);
    }

    /**
     * Build the NPC geometry and physics in the world.
     */
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        // Create a simple cylinder to represent the NPC
        Cylinder npcBody = new Cylinder(16, 32, 0.4f, 1.8f, true);
        Geometry npcGeo = new Geometry("NPCGeometry", npcBody);

        Material npcMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        npcMat.setBoolean("UseMaterialColors", true);
        npcMat.setColor("Diffuse", new ColorRGBA(0.3f, 0.7f, 0.4f, 1f)); // greenish
        npcMat.setColor("Specular", ColorRGBA.White);
        npcMat.setFloat("Shininess", 16f);
        npcGeo.setMaterial(npcMat);
        // jME3's Cylinder runs its height along the Z axis, so it lies flat by
        // default. Rotate 90 degrees about X to stand the body upright.
        npcGeo.rotate(FastMath.HALF_PI, 0f, 0f);

        npcGeo.setLocalTranslation(0, 0.9f, 0);
        node.attachChild(npcGeo);

        // Add a head (another cylinder on top)
        Cylinder head = new Cylinder(16, 32, 0.3f, 0.5f, true);
        Geometry headGeo = new Geometry("HeadGeometry", head);
        headGeo.setMaterial(npcMat);
        headGeo.rotate(FastMath.HALF_PI, 0f, 0f);
        headGeo.setLocalTranslation(0, 1.9f, 0);
        node.attachChild(headGeo);

        parentNode.attachChild(node);

        // Add physics (static, non-moving)
        CapsuleCollisionShape shape = new CapsuleCollisionShape(0.4f, 1.8f);
        physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position.add(0, 0.9f, 0));
        bulletAppState.getPhysicsSpace().add(physics);
    }

    /**
     * Add an item to this NPC's shop.
     */
    public void addShopItem(String itemId, int priceInSoulDust) {
        shopItems.add(itemId);
        shopPrices.add(priceInSoulDust);
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
        System.out.println("Interacted with NPC: " + name);

        // Show shop UI
        if (shopUI != null) {
            shopUI.show(this);
        }
    }

    /**
     * Set the UI handler for this NPC's shop.
     */
    public void setShopUI(ShopUI ui) {
        this.shopUI = ui;
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

    public String getName() {
        return name;
    }

    public List<String> getShopItems() {
        return shopItems;
    }

    public List<Integer> getShopPrices() {
        return shopPrices;
    }

    /**
     * Interface for the shop UI callback.
     */
    public interface ShopUI {
        void show(NPC npc);
        void purchaseItem(String itemId, int count);
    }
}
