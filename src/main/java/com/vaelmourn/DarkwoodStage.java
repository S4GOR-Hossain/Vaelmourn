package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * DarkwoodStage — Stage 1: Dark forest with tier-1 enemies.
 * Variants 1-4 scale enemy difficulty and re-seed the tree layout slightly,
 * so later stages feel like the same woods that have gotten meaner.
 */
public class DarkwoodStage implements Stage {

    private static final float HALF_EXTENT = 60f;

    private final int variant;
    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    public DarkwoodStage() {
        this(1);
    }

    public DarkwoodStage(int variant) {
        this.variant = Math.max(1, variant);
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("Darkwood" + variant);
        parentNode.attachChild(stageNode);

        buildGroundPlane(assetManager, bulletAppState);
        buildBoundaryWalls(bulletAppState);
        buildDecoration(assetManager, bulletAppState);
    }

    @Override
    public void cleanup(Node parentNode, BulletAppState bulletAppState) {
        for (RigidBodyControl physics : physicsObjects) {
            bulletAppState.getPhysicsSpace().remove(physics);
        }
        physicsObjects.clear();
        stageNode.removeFromParent();
    }

    @Override
    public List<EnemyController> spawnEnemies(AssetManager assetManager, Node parentNode,
                                               BulletAppState bulletAppState, int loopCount) {
        List<EnemyController> enemies = new ArrayList<>();

        // difficulty curve inside the biome: more of them, and they hit harder
        int enemyCount = 4 + variant + (loopCount / 2);
        int tier = Math.min(3, 1 + (variant - 1) / 2);
        float scale = 1f + (variant - 1) * 0.35f;
        Random rand = new Random(42 + variant * 7 + loopCount);

        for (int i = 0; i < enemyCount; i++) {
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = 15f + rand.nextFloat() * 10f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            EnemyController enemy = new EnemyController(
                assetManager, stageNode, bulletAppState,
                new Vector3f(x, 5f, z), tier, loopCount, scale
            );
            enemies.add(enemy);
        }

        return enemies;
    }

    @Override
    public Vector3f getPlayerSpawnPoint() {
        return new Vector3f(0, 2f, 0);
    }

    @Override
    public ColorRGBA getSkyColor() {
        return new ColorRGBA(0.15f, 0.18f, 0.25f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.3f, 0.35f, 0.4f, 1f).mult(0.5f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.3f, -0.9f, -0.4f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    @Override
    public String getName() {
        return "Darkwood " + variant;
    }

    @Override
    public int getStageIndex() {
        return 1;
    }

    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Box groundBox = new Box(HALF_EXTENT, 0.5f, HALF_EXTENT);
        Geometry ground = new Geometry("DarkwoodGround", groundBox);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.2f, 0.3f, 0.15f, 1f)); // dark mossy green
        ground.setMaterial(mat);
        ground.setLocalTranslation(0, -0.5f, 0);
        stageNode.attachChild(ground);
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(HALF_EXTENT, 0.5f, HALF_EXTENT));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildBoundaryWalls(BulletAppState bulletAppState) {
        float wallHeight = 10f;
        float wallThickness = 1f;

        createWall(new Vector3f(0, wallHeight / 2f, HALF_EXTENT),
                   new Vector3f(HALF_EXTENT, wallHeight / 2f, wallThickness), bulletAppState);
        createWall(new Vector3f(0, wallHeight / 2f, -HALF_EXTENT),
                   new Vector3f(HALF_EXTENT, wallHeight / 2f, wallThickness), bulletAppState);
        createWall(new Vector3f(HALF_EXTENT, wallHeight / 2f, 0),
                   new Vector3f(wallThickness, wallHeight / 2f, HALF_EXTENT), bulletAppState);
        createWall(new Vector3f(-HALF_EXTENT, wallHeight / 2f, 0),
                   new Vector3f(wallThickness, wallHeight / 2f, HALF_EXTENT), bulletAppState);
    }

    private void createWall(Vector3f position, Vector3f halfExtents, BulletAppState bulletAppState) {
        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildDecoration(AssetManager assetManager, BulletAppState bulletAppState) {
        // the variant re-seeds the layout so each stage is the same woods with a
        // slightly different tree arrangement (the "slight variation" between stages)
        Random rand = new Random(99 + variant);

        // Dark forest: only pull from a handful of tree-pack models (3-4 types,
        // repeated randomly) so no single tree stands out. They carry their own
        // colormaps, and the dark ambient light does the rest for the vibe.
        String[] treeModels = {
                "Models/Environment/Forest/tree_pack_02.glb",
                "Models/Environment/Forest/tree_pack_07.glb",
                "Models/Environment/Forest/tree_pack_13.glb",
                "Models/Environment/Forest/tree_pack_18.glb"
        };

        int placed = 0;
        for (int i = 0; i < 40 && placed < 32; i++) {
            float x = (rand.nextFloat() - 0.5f) * 84f;
            float z = (rand.nextFloat() - 0.5f) * 84f;
            if (new Vector3f(x, 0, z).length() < 9f) {
                continue;
            }

            placeTree(x, z, rand, treeModels, assetManager, bulletAppState);
            placed++;
        }
        System.out.println("[Darkwood] placed " + placed + " trees with bark hitboxes.");
    }

    private void placeTree(float x, float z, Random rand, String[] treeModels,
                           AssetManager assetManager, BulletAppState bulletAppState) {
        String chosenModel = treeModels[rand.nextInt(treeModels.length)];
        Spatial tree = assetManager.loadModel(chosenModel);

        tree.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);

        float scale = (3.5f + rand.nextFloat() * 1.2f) * 0.8f;
        tree.setLocalScale(scale);

        // Pack models pivot around their vertical center, so lift each tree until
        // its base touches the ground instead of half-burying it. Grab the scaled
        // bounds too — they drive the hitbox size below.
        tree.updateModelBound();
        Vector3f extent = new Vector3f();
        float lift = 0f;
        if (tree.getWorldBound() instanceof BoundingBox bbox) {
            bbox.getExtent(extent);
            lift = extent.y - bbox.getCenter().y;
        }

        tree.setLocalTranslation(x, lift, z);

        // Trunk hitbox: a narrow box as wide as the trunk, spanning the lower part of
        // the tree where the trunk actually sits. Sizes come from the scaled bounds
        // so it stays proportional to whichever model got picked.
        float trunkRadius = Math.max(0.5f, Math.min(1.3f, Math.max(extent.x, extent.z) * 0.2f));
        float treeHeight = 2f * extent.y;
        float collarHeight = Math.max(2.5f, treeHeight * 0.4f);
        BoxCollisionShape trunkShape = new BoxCollisionShape(new Vector3f(trunkRadius, collarHeight / 2f, trunkRadius));
        RigidBodyControl physics = new RigidBodyControl(trunkShape, 0);
        float centerY = collarHeight / 2f;
        physics.setPhysicsLocation(new Vector3f(x, centerY, z));

        stageNode.attachChild(tree);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }
}
