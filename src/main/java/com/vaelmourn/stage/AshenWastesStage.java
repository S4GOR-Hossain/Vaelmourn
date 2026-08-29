package com.vaelmourn.stage;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.vaelmourn.EnemyController;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * AshenWastesStage — Stage 2, a volcanic desert wasteland.
 *
 * The Ashen Wastes is a hot, barren volcanic landscape with:
 * - Orange-brown cracked ground with glowing lava pools
 * - Dark rock pillars scattered throughout
 * - Dead trees (thin vertical sticks) for visual interest
 * - Harsh overhead sun with orange-tinted lighting
 * - Medium-tier enemies (tier 2) more spread out than in Darkwood
 * - Clear center arena for combat
 *
 * The environment conveys danger and desolation through color and scale.
 */
public class AshenWastesStage implements Stage {

    private Node root;
    private final Vector3f spawnPoint = new Vector3f(0, 2, 0);
    private final float halfExtent = 55f;

    // Track physics bodies so they can be cleaned up in cleanup()
    private final List<RigidBodyControl> physicsBodies = new ArrayList<>();

    @Override
    public String getName() {
        return "Ashen Wastes";
    }

    @Override
    public int getStageIndex() {
        return 2;
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        root = new Node("AshenWastes");
        parentNode.attachChild(root);

        buildGroundPlane(assetManager, bulletAppState);
        buildLavaPoolDecorations();
        buildRockPillars();
        buildDeadTrees();
        buildBoundaryWalls();
        buildLighting();
    }

    @Override
    public void cleanup(Node parentNode, BulletAppState bulletAppState) {
        // Remove all physics bodies from the physics space
        for (RigidBodyControl body : physicsBodies) {
            bulletAppState.getPhysicsSpace().remove(body);
        }
        physicsBodies.clear();

        root.removeFromParent();
    }

    @Override
    public List<EnemyController> spawnEnemies(AssetManager assetManager, Node parentNode,
                                              BulletAppState bulletAppState, int loopCount) {
        List<EnemyController> enemies = new ArrayList<>();

        // Spawn 6-10 tier 2 (medium) enemies, scaled by loop count
        int baseCount = 6 + (loopCount / 2);
        int maxCount = Math.min(10 + loopCount, 16);
        int spawnCount = Math.min(baseCount, maxCount);

        Random rand = new Random(456); // Deterministic but different from other stages

        for (int i = 0; i < spawnCount; i++) {
            // Scatter enemies in a ring around the player, keeping center clear (~12f radius)
            float angle = rand.nextFloat() * (float) Math.PI * 2f;
            float distance = 15f + rand.nextFloat() * 25f; // 15-40 units away
            float x = (float) Math.cos(angle) * distance;
            float z = (float) Math.sin(angle) * distance;

            Vector3f spawnPos = new Vector3f(x, 2f, z);

            EnemyController enemy = new EnemyController(
                assetManager,
                root,
                bulletAppState,
                spawnPos,
                2, // tier 2 (medium)
                loopCount
            );
            enemies.add(enemy);
        }

        return enemies;
    }

    @Override
    public Vector3f getPlayerSpawnPoint() {
        return spawnPoint;
    }

    @Override
    public ColorRGBA getSkyColor() {
        // Angry orange-red haze
        return new ColorRGBA(0.6f, 0.35f, 0.15f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        // Hot orange ambient light
        return new ColorRGBA(0.8f, 0.5f, 0.3f, 1f).mult(0.6f);
    }

    @Override
    public Vector3f getSunDirection() {
        // Harsh overhead sun, slightly angled
        return new Vector3f(-0.2f, -1f, -0.3f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return halfExtent;
    }

    /**
     * Build the ground plane: flat orange-brown cracked ground with physics.
     * Uses procedural geometry (Box) instead of external models.
     */
    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        // Create a flat ground geometry with orange-brown color
        Geometry groundGeo = new Geometry("AshenWastesGround",
            new Box(halfExtent, 0.5f, halfExtent));

        Material groundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        groundMat.setColor("Color", new ColorRGBA(0.45f, 0.30f, 0.18f, 1f));
        groundGeo.setMaterial(groundMat);

        groundGeo.setLocalTranslation(0, -0.5f, 0);
        root.attachChild(groundGeo);

        // Ground physics: large flat box
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(halfExtent, 0.5f, halfExtent));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        physicsBodies.add(physics);
        bulletAppState.getPhysicsSpace().add(physics);
    }

    /**
     * Create glowing lava pools: flat red-orange boxes scattered on the ground.
     * These are visual only (no physics).
     */
    private void buildLavaPoolDecorations() {
        Random rand = new Random(789);
        int lavaPoolCount = 12 + (int)(halfExtent / 10f);

        for (int i = 0; i < lavaPoolCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * halfExtent * 1.8f;
            float z = (rand.nextFloat() - 0.5f) * halfExtent * 1.8f;

            // Skip the center combat area
            if (new Vector3f(x, 0, z).length() < 12f) {
                continue;
            }

            float poolWidth = 2f + rand.nextFloat() * 4f;
            float poolDepth = 2f + rand.nextFloat() * 4f;

            Geometry lavaGeo = new Geometry("LavaPool_" + i,
                new Box(poolWidth / 2, 0.05f, poolDepth / 2));

            Material lavaMat = new Material(null, "Common/MatDefs/Misc/Unshaded.j3md");
            lavaMat.setColor("Color", new ColorRGBA(0.9f, 0.3f, 0.05f, 1f));
            lavaGeo.setMaterial(lavaMat);

            lavaGeo.setLocalTranslation(x, 0.05f, z);
            root.attachChild(lavaGeo);
        }
    }

    /**
     * Create rock pillars: tall narrow Box geometries of varying heights scattered around.
     * These are visual only (no physics).
     */
    private void buildRockPillars() {
        Random rand = new Random(321);
        int pillarCount = 20 + (int)(halfExtent / 8f);

        for (int i = 0; i < pillarCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * halfExtent * 1.9f;
            float z = (rand.nextFloat() - 0.5f) * halfExtent * 1.9f;

            // Skip the center combat area
            if (new Vector3f(x, 0, z).length() < 14f) {
                continue;
            }

            float width = 0.6f + rand.nextFloat() * 1.2f;
            float depth = 0.6f + rand.nextFloat() * 1.2f;
            float height = 4f + rand.nextFloat() * 8f;

            Geometry pillarGeo = new Geometry("RockPillar_" + i,
                new Box(width / 2, height / 2, depth / 2));

            Material pillarMat = new Material(null, "Common/MatDefs/Misc/Unshaded.j3md");
            // Dark grey/brown rock color
            pillarMat.setColor("Color", new ColorRGBA(0.35f, 0.30f, 0.25f, 1f));
            pillarGeo.setMaterial(pillarMat);

            pillarGeo.setLocalTranslation(x, height / 2, z);
            root.attachChild(pillarGeo);
        }
    }

    /**
     * Create dead trees: thin tall Cylinder geometries arranged vertically.
     * These are visual only (no physics).
     */
    private void buildDeadTrees() {
        Random rand = new Random(654);
        int treeCount = 5;

        for (int i = 0; i < treeCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * halfExtent * 1.7f;
            float z = (rand.nextFloat() - 0.5f) * halfExtent * 1.7f;

            // Skip the center combat area
            if (new Vector3f(x, 0, z).length() < 16f) {
                continue;
            }

            float radius = 0.15f + rand.nextFloat() * 0.25f;
            float height = 6f + rand.nextFloat() * 8f;

            Geometry treeGeo = new Geometry("DeadTree_" + i,
                new Cylinder(8, 8, radius, height, true));

            // Orient the cylinder vertically (it's created horizontally by default)
            treeGeo.rotate((float) Math.PI / 2f, 0, 0);

            Material treeMat = new Material(null, "Common/MatDefs/Misc/Unshaded.j3md");
            // Dark brown dead wood
            treeMat.setColor("Color", new ColorRGBA(0.25f, 0.15f, 0.08f, 1f));
            treeGeo.setMaterial(treeMat);

            treeGeo.setLocalTranslation(x, height / 2, z);
            root.attachChild(treeGeo);
        }
    }

    /**
     * Build boundary walls: physics-only walls to prevent player from leaving the arena.
     */
    private void buildBoundaryWalls() {
        float wallHeight = 15f;
        float wallThickness = 1f;

        // North wall
        createWall(new Vector3f(0, wallHeight / 2f, halfExtent),
                new Vector3f(halfExtent, wallHeight / 2f, wallThickness));

        // South wall
        createWall(new Vector3f(0, wallHeight / 2f, -halfExtent),
                new Vector3f(halfExtent, wallHeight / 2f, wallThickness));

        // East wall
        createWall(new Vector3f(halfExtent, wallHeight / 2f, 0),
                new Vector3f(wallThickness, wallHeight / 2f, halfExtent));

        // West wall
        createWall(new Vector3f(-halfExtent, wallHeight / 2f, 0),
                new Vector3f(wallThickness, wallHeight / 2f, halfExtent));
    }

    /**
     * Create a single boundary wall segment.
     *
     * @param position center position of the wall
     * @param halfExtents half-extents of the box (width, height, depth)
     */
    private void createWall(Vector3f position, Vector3f halfExtents) {
        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position);
        physicsBodies.add(physics);
    }

    /**
     * Set up lighting for the volcanic wasteland atmosphere.
     * Harsh overhead sun with orange tint and hot ambient light.
     */
    private void buildLighting() {
        // Main harsh sun: overhead and orange-tinted
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.2f, -1f, -0.3f).normalizeLocal());
        sun.setColor(new ColorRGBA(1.0f, 0.7f, 0.3f, 1f).mult(1.2f));
        root.addLight(sun);

        // Softer fill light from the side for depth
        DirectionalLight fill = new DirectionalLight();
        fill.setDirection(new Vector3f(0.6f, -0.5f, 0.4f).normalizeLocal());
        fill.setColor(new ColorRGBA(0.8f, 0.5f, 0.3f, 1f).mult(0.5f));
        root.addLight(fill);

        // Hot orange ambient light
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.8f, 0.5f, 0.3f, 1f).mult(0.6f));
        root.addLight(ambient);
    }
}
