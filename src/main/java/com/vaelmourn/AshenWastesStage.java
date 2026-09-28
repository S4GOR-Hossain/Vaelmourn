package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
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

/** AshenWastesStage — Stage 2: volcanic desert, tier-2 enemies; variant 3 is the horde stage. */
public class AshenWastesStage implements Stage {

    private static final float HALF_EXTENT = 55f;
    // horde keeps the enemy count bounded so the spawn pile doesn't tank the frame rate
    private static final int HORDE_ENEMY_COUNT = 22;
    // horde fodder hits far softer than the surrounding stages' regular troops
    private static final float HORDE_DIFFICULTY_SCALE = 0.55f;
    private static final String[] ENEMY_MODELS = {
            "Models/Characters/enemy/ashenwastes_enemy.gltf",
            "Models/Characters/enemy/ashenwastes_enemy2.gltf"
    };

    private final int variant;
    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    public AshenWastesStage() {
        this(1);
    }

    public AshenWastesStage(int variant) {
        this.variant = Math.max(1, variant);
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("AshenWastes" + variant);
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

        boolean horde = variant == 3;
        int enemyCount = horde ? HORDE_ENEMY_COUNT : 5 + variant + (loopCount / 2);
        float scale = horde ? HORDE_DIFFICULTY_SCALE : 1f + (variant - 1) * 0.4f;
        Random rand = new Random(43 + variant * 7 + loopCount);

        for (int i = 0; i < enemyCount; i++) {
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = 15f + rand.nextFloat() * 15f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            String modelPath = ENEMY_MODELS[(variant + i) % ENEMY_MODELS.length];
            EnemyController enemy = new EnemyController(
                assetManager, stageNode, bulletAppState,
                new Vector3f(x, 5f, z), 2, loopCount, scale, false, modelPath
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
        return new ColorRGBA(0.6f, 0.35f, 0.15f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.8f, 0.5f, 0.3f, 1f).mult(0.6f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.2f, -1f, -0.1f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    @Override
    public String getName() {
        return "Ashen Wastes " + variant;
    }

    @Override
    public int getStageIndex() {
        return 2;
    }

    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Box groundBox = new Box(HALF_EXTENT, 0.5f, HALF_EXTENT);
        Geometry ground = new Geometry("AshenWastesGround", groundBox);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.45f, 0.30f, 0.18f, 1f));
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
        Random rand = new Random(100 + variant);

        for (int i = 0; i < 12; i++) {
            float x = (rand.nextFloat() - 0.5f) * 90f;
            float z = (rand.nextFloat() - 0.5f) * 90f;

            Box lavaBox = new Box(2f + rand.nextFloat() * 1f, 0.1f, 2f + rand.nextFloat() * 1f);
            Geometry lava = new Geometry("Lava_" + i, lavaBox);
            Material lavaMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            lavaMat.setColor("Color", new ColorRGBA(0.9f, 0.3f, 0.05f, 1f));
            lava.setMaterial(lavaMat);
            lava.setLocalTranslation(x, 0.05f, z);
            stageNode.attachChild(lava);
        }

        // Forest pack stone models reused as rocky outcrops — no trees, this biome is barren.
        String[] stoneModels = {
                "Models/Environment/Forest/stone_tallA.glb",
                "Models/Environment/Forest/stone_tallB.glb",
                "Models/Environment/Forest/stone_tallC.glb",
                "Models/Environment/Forest/stone_tallD.glb",
                "Models/Environment/Forest/stone_largeA.glb",
                "Models/Environment/Forest/stone_largeB.glb",
                "Models/Environment/Forest/stone_largeC.glb"
        };

        for (int i = 0; i < 20; i++) {
            float x = (rand.nextFloat() - 0.5f) * 90f;
            float z = (rand.nextFloat() - 0.5f) * 90f;
            if (new Vector3f(x, 0, z).length() < 12f) continue;

            float size = 3.6f + rand.nextFloat() * 4.8f;
            placeStone(x, z, size, rand, stoneModels, assetManager, bulletAppState);
        }
    }

    private void placeStone(float x, float z, float size, Random rand, String[] stoneModels,
                            AssetManager assetManager, BulletAppState bulletAppState) {
        String chosenModel = stoneModels[rand.nextInt(stoneModels.length)];
        Spatial stone = assetManager.loadModel(chosenModel);

        stone.setLocalTranslation(x, 0, z);
        stone.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
        stone.setLocalScale(size);

        stageNode.attachChild(stone);

        // Rough box collider so the player can't walk straight through the outcrops.
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(size * 0.35f, size * 0.4f, size * 0.35f));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(x, size * 0.35f, z));

        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }
}
