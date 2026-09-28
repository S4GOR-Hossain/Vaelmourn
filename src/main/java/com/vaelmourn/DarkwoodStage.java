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

/** Darkwood — stage 1, tier-1 forest. Variants 1-4 raise difficulty and re-seed the trees. */
public class DarkwoodStage implements Stage {

    private static final float HALF_EXTENT = 60f;
    // rotated per stage so each pass through the woods leads with a different grunt
    private static final String[] ENEMY_MODELS = {
            "Models/Characters/enemy/darkwood_enemy.glb",
            "Models/Characters/enemy/darkwood_enemy2.glb",
            "Models/Characters/enemy/darkwood_enemy3.gltf"
    };

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

        int enemyCount = 4 + variant + (loopCount / 2);
        int tier = Math.min(3, 1 + (variant - 1) / 2);
        float scale = 1f + (variant - 1) * 0.35f;
        Random rand = new Random(42 + variant * 7 + loopCount);

        for (int i = 0; i < enemyCount; i++) {
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = 15f + rand.nextFloat() * 10f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            String modelPath = ENEMY_MODELS[(variant + i) % ENEMY_MODELS.length];
            EnemyController enemy = new EnemyController(
                assetManager, stageNode, bulletAppState,
                new Vector3f(x, 5f, z), tier, loopCount, scale, false, modelPath
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
        mat.setColor("Color", new ColorRGBA(0.2f, 0.3f, 0.15f, 1f));
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
        // the variant re-seeds the layout so each stage is the same woods, rearranged
        Random rand = new Random(99 + variant);

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

        // Pack models pivot around their vertical center, so lift each until its
        // base touches the ground instead of half-burying it.
        tree.updateModelBound();
        float trunkRadius = 1.1f;
        float collarHeight = 4f;
        float lift = 0f;
        if (tree.getWorldBound() instanceof BoundingBox bbox) {
            Vector3f extent = new Vector3f();
            Vector3f center = new Vector3f();
            bbox.getExtent(extent);
            bbox.getCenter(center);
            lift = extent.y - center.y;
            // the old 0.2x hitbox was a tiny post you could walk around,
            // so size the collision from the tree silhouette instead
            trunkRadius = FastMath.clamp(Math.max(extent.x, extent.z) * 0.75f, 1.0f, 2.8f);
            collarHeight = FastMath.clamp(2f * extent.y, 4f, 9f);
        }

        tree.setLocalTranslation(x, lift, z);

        BoxCollisionShape trunkShape = new BoxCollisionShape(new Vector3f(trunkRadius, collarHeight / 2f, trunkRadius));
        RigidBodyControl physics = new RigidBodyControl(trunkShape, 0);
        physics.setPhysicsLocation(new Vector3f(x, collarHeight / 2f, z));

        stageNode.attachChild(tree);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }
}
