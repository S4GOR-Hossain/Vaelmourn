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
import com.jme3.scene.shape.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FrozenDepthsStage — Stage 3: Icy tundra with tier-3 enemies.
 */
public class FrozenDepthsStage implements Stage {

    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("FrozenDepths");
        parentNode.attachChild(stageNode);

        buildGroundPlane(assetManager, bulletAppState);
        buildBoundaryWalls(bulletAppState);
        buildDecoration(assetManager);
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
        int enemyCount = 8 + (loopCount / 2);
        Random rand = new Random(44 + loopCount);

        for (int i = 0; i < enemyCount; i++) {
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = 12f + rand.nextFloat() * 12f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            EnemyController enemy = new EnemyController(
                assetManager, stageNode, bulletAppState,
                new Vector3f(x, 5f, z), 3, loopCount
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
        return new ColorRGBA(0.7f, 0.8f, 0.92f, 1f); // pale icy blue
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.5f, 0.6f, 0.8f, 1f).mult(0.7f); // cold blue
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.4f, -0.6f, -0.5f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return 50f;
    }

    @Override
    public String getName() {
        return "Frozen Depths";
    }

    @Override
    public int getStageIndex() {
        return 3;
    }

    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Box groundBox = new Box(50, 0.5f, 50);
        Geometry ground = new Geometry("FrozenDepthsGround", groundBox);
        Material groundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        groundMat.setColor("Color", new ColorRGBA(0.75f, 0.85f, 0.95f, 1f)); // icy blue-white
        ground.setMaterial(groundMat);
        ground.setLocalTranslation(0, -0.5f, 0);
        stageNode.attachChild(ground);

        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(50, 0.5f, 50));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildBoundaryWalls(BulletAppState bulletAppState) {
        float wallHeight = 10f;
        float wallThickness = 1f;

        createWall(new Vector3f(0, wallHeight / 2f, 50),
                   new Vector3f(50, wallHeight / 2f, wallThickness), bulletAppState);
        createWall(new Vector3f(0, wallHeight / 2f, -50),
                   new Vector3f(50, wallHeight / 2f, wallThickness), bulletAppState);
        createWall(new Vector3f(50, wallHeight / 2f, 0),
                   new Vector3f(wallThickness, wallHeight / 2f, 50), bulletAppState);
        createWall(new Vector3f(-50, wallHeight / 2f, 0),
                   new Vector3f(wallThickness, wallHeight / 2f, 50), bulletAppState);
    }

    private void createWall(Vector3f position, Vector3f halfExtents, BulletAppState bulletAppState) {
        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildDecoration(AssetManager assetManager) {
        Random rand = new Random(101);

        // Ice pillars
        for (int i = 0; i < 10; i++) {
            float x = (rand.nextFloat() - 0.5f) * 80f;
            float z = (rand.nextFloat() - 0.5f) * 80f;

            float height = 2f + rand.nextFloat() * 3f;
            Box pillarBox = new Box(0.6f, height / 2f, 0.6f);
            Geometry pillar = new Geometry("IcePillar_" + i, pillarBox);
            Material pillarMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            pillarMat.setColor("Color", new ColorRGBA(0.6f, 0.75f, 0.9f, 0.8f)); // translucent ice blue
            pillar.setMaterial(pillarMat);
            pillar.setLocalTranslation(x, height / 2f, z);
            stageNode.attachChild(pillar);
        }

        // Snow mounds
        for (int i = 0; i < 15; i++) {
            float x = (rand.nextFloat() - 0.5f) * 85f;
            float z = (rand.nextFloat() - 0.5f) * 85f;

            Box moundBox = new Box(1f + rand.nextFloat() * 1.5f, 0.3f, 1f + rand.nextFloat() * 1.5f);
            Geometry mound = new Geometry("SnowMound_" + i, moundBox);
            Material moundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            moundMat.setColor("Color", new ColorRGBA(0.9f, 0.95f, 1f, 1f)); // white
            mound.setMaterial(moundMat);
            mound.setLocalTranslation(x, 0.3f, z);
            stageNode.attachChild(mound);
        }
    }
}
