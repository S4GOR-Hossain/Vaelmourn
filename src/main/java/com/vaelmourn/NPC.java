package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Cylinder;
import com.jme3.anim.SkinningControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A Sanctuary merchant. Interacting (F key) opens its menu — the potion shop for NPC 1,
 * the run stat upgrade list for NPC 2.
 *
 * <p>The body is driven by a {@link BetterCharacterControl} rather than a static capsule,
 * so the merchants can actually walk around the hub. The control anchors the node origin
 * at the character's FEET (same convention as EnemyController), which is why the visual is
 * grounded so its base sits on y=0 and the spawn Y stays 0 rather than 0.9.</p>
 */
public class NPC implements Interactable {

    private Node node;
    private final Vector3f position;
    private String name;
    private List<String> shopItems = new ArrayList<>();
    private List<Integer> shopPrices = new ArrayList<>(); // all priced in soul dust
    private PotionShopUI potionShopUI;
    private RunUpgradeUI runUpgradeUI;
    private BetterCharacterControl physics;
    private int voicePool = 0; // 1 or 2 maps to the NPC voiceline banks in SoundManager
    private static final float INTERACT_RANGE = 3.5f;

    /** Uniform scale applied to the loaded NPC character model. Tuned so the merchants
     *  read at roughly the same height as the player's ~1.8 unit capsule. */
    private static final float NPC_MODEL_SCALE = 1.0f;

    // ---- wandering -------------------------------------------------------
    /** How far from its post a merchant is willing to stroll. */
    private static final float WANDER_RADIUS = 7f;
    /** Walking speed. Deliberately slower than the player (6) and the enemies (4+). */
    private static final float WANDER_SPEED = 2.2f;
    /** Distance at which a destination counts as reached. */
    private static final float WANDER_REACH = 0.7f;
    private static final float WANDER_PAUSE_MIN = 1.5f;
    private static final float WANDER_PAUSE_MAX = 4.5f;
    /** Sanctuary walls are at +/-40 with 1 unit thickness; stay well inside them. */
    private static final float ARENA_LIMIT = 37f;

    private final Vector3f wanderTarget = new Vector3f();
    private float wanderWait = 0f;
    private final Random wanderRng = new Random();

    public NPC(String name, Vector3f position) {
        this.name = name;
        this.position = position;
        this.node = new Node("NPC_" + name);
        this.node.setLocalTranslation(position);
        this.wanderTarget.set(position);
    }

    /** Character model for this NPC, e.g. "Models/Characters/npc/npc1.gltf". Null keeps
     *  the legacy cylinder so nothing can fail to build if a model is missing. */
    private String modelPath = null;

    /** Y rotation in degrees so the merchant faces the player. */
    private float facingY = 0f;

    public void setModel(String path, float facingY) {
        this.modelPath = path;
        this.facingY = facingY;
    }

    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        if (modelPath != null && buildModel(assetManager, modelPath)) {
            // fall through to the controller setup below
        } else {
            buildCylinder(assetManager);
        }

        node.setLocalTranslation(position);
        parentNode.attachChild(node);

        // BetterCharacterControl, not a static capsule: a mass-0 body is kinematic, which
        // is why the merchants never budged. This one is walked with setWalkDirection
        // every frame, and its origin is the character's FEET, so the spawn Y stays 0 and
        // the grounded visual lands exactly on the floor.
        physics = new BetterCharacterControl(0.4f, 1.8f, 0.8f);
        physics.setGravity(new Vector3f(0f, -30f, 0f));
        // point the view along the configured facing so the character control owns the
        // body's rotation from the first frame (it overwrites any manual node rotation)
        float facingRad = facingY * FastMath.DEG_TO_RAD;
        physics.setViewDirection(new Vector3f(FastMath.sin(facingRad), 0f, FastMath.cos(facingRad)));
        node.addControl(physics);
        physics.warp(position);
        bulletAppState.getPhysicsSpace().add(physics);
    }

    /**
     * Loads the NPC character model and grounds it.
     *
     * <p>Grounding reuses {@link StageDecor#baseLift} so both base-pivot and centre-pivot
     * models end up standing on the node origin rather than floating or sunk. The lift is
     * measured while the model is still detached — see that method for why that matters.</p>
     *
     * @return false if the asset is missing, so the caller can fall back to a cylinder
     */
    private boolean buildModel(AssetManager assetManager, String path) {
        Spatial model;
        try {
            model = assetManager.loadModel(path);
        } catch (Exception e) {
            System.out.println("Failed to load NPC model " + path + ": " + e.getMessage());
            return false;
        }
        if (model == null) return false;

        // hardware skinning crashes this project's AMD OpenGL driver (the same
        // reason the player model is forced onto CPU skinning in ForestBiome)
        SkinningControl skinning = model.getControl(SkinningControl.class);
        if (skinning != null) {
            skinning.setHardwareSkinningPreferred(false);
        }

        model.setLocalScale(NPC_MODEL_SCALE);

        // Ground the visual BEFORE attaching, so the measurement is free of this node's
        // world transform. +baseLift puts the model's base on the node origin (the feet).
        // The old code used -lift, which pushed the model that same distance back down
        // the other way and buried the merchant's feet in the ground.
        model.setLocalTranslation(0f, StageDecor.baseLift(model), 0f);
        node.attachChild(model);
        return true;
    }

    /** Original placeholder body, kept only as a fallback when a model fails to load. */
    private void buildCylinder(AssetManager assetManager) {
        Cylinder npcBody = new Cylinder(16, 32, 0.4f, 1.8f, true);
        Geometry npcGeo = new Geometry("NPCGeometry", npcBody);

        Material npcMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        npcMat.setBoolean("UseMaterialColors", true);
        npcMat.setColor("Diffuse", new ColorRGBA(0.3f, 0.7f, 0.4f, 1f));
        npcMat.setColor("Specular", ColorRGBA.White);
        npcMat.setFloat("Shininess", 16f);
        npcGeo.setMaterial(npcMat);
        // jME3's Cylinder sizes its height along Z, so it spawns lying flat;
        // rotate 90 degrees about X to stand the body upright.
        npcGeo.rotate(FastMath.HALF_PI, 0f, 0f);
        npcGeo.setLocalTranslation(0, 0.9f, 0);
        node.attachChild(npcGeo);
    }

    public void addShopItem(String itemId, int priceInSoulDust) {
        shopItems.add(itemId);
        shopPrices.add(priceInSoulDust);
    }

    @Override
    public Vector3f getPosition() {
        // live position: the merchant walks, so the cached spawn point would go stale
        return node.getWorldTranslation();
    }

    @Override
    public boolean isInRange(Vector3f playerPos, float range) {
        return playerPos.distance(getPosition()) <= range;
    }

    /**
     * Per-frame wander. Mirrors EnemyController's conventions exactly: read the live
     * node position, flatten the target direction to y=0, and issue exactly one
     * setWalkDirection per frame (a zero vector simply means "stand still").
     */
    public void update(float tpf) {
        if (physics == null) return;

        if (wanderWait > 0f) {
            wanderWait -= tpf;
            physics.setWalkDirection(Vector3f.ZERO);
            return;
        }

        Vector3f pos = node.getWorldTranslation();
        Vector3f toTarget = new Vector3f(wanderTarget.x - pos.x, 0f, wanderTarget.z - pos.z);
        float dist = toTarget.length();

        if (dist <= WANDER_REACH) {
            physics.setWalkDirection(Vector3f.ZERO);
            pickWanderTarget();
            return;
        }

        toTarget.divideLocal(dist);
        physics.setViewDirection(toTarget);
        physics.setWalkDirection(toTarget.mult(WANDER_SPEED));
    }

    /** Chooses the next stroll destination: a random spot near the post. */
    private void pickWanderTarget() {
        float angle = wanderRng.nextFloat() * FastMath.TWO_PI;
        float r = 2f + wanderRng.nextFloat() * (WANDER_RADIUS - 2f);
        Vector3f p = new Vector3f(
                position.x + FastMath.sin(angle) * r,
                0f,
                position.z + FastMath.cos(angle) * r);

        p.x = FastMath.clamp(p.x, -ARENA_LIMIT, ARENA_LIMIT);
        p.z = FastMath.clamp(p.z, -ARENA_LIMIT, ARENA_LIMIT);
        wanderTarget.set(p);
        wanderWait = WANDER_PAUSE_MIN + wanderRng.nextFloat() * (WANDER_PAUSE_MAX - WANDER_PAUSE_MIN);
    }

    @Override
    public void interact() {
        System.out.println("Interacted with NPC: " + name);

        // one of these is set per NPC, so the two merchants open different menus
        if (runUpgradeUI != null) {
            runUpgradeUI.show(name);
        } else if (potionShopUI != null) {
            potionShopUI.show(name, shopItems, shopPrices);
        }
    }

    /** Routes this NPC to the potion merchant menu (buy + potion upgrades). */
    public void setPotionShopUI(PotionShopUI ui) {
        this.potionShopUI = ui;
    }

    /** Routes this NPC to the run stat upgrade menu. */
    public void setRunUpgradeUI(RunUpgradeUI ui) {
        this.runUpgradeUI = ui;
    }

    @Override
    public Node getNode() {
        return node;
    }

    /** The walking controller, as an opaque physics body. Typed as Object because
     *  PhysicsSpace.remove() accepts both RigidBodyControl and BetterCharacterControl,
     *  which have no useful common supertype. */
    public Object getPhysics() {
        return physics;
    }

    public int getVoicePool() {
        return voicePool;
    }

    public void setVoicePool(int pool) {
        this.voicePool = pool;
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
}
