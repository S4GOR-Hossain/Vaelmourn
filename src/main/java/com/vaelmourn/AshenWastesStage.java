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
 * AshenWastesStage — Stage 2: Volcanic desert with tier-2 enemies.
 */
public class AshenWastesStage implements Stage {

    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("AshenWastes");
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
        int enemyCount = 6 + (loopCount / 2);
        Random rand = new Random(43 + loopCount);

        for (int i = 0; i < enemyCount; i++) {
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = 15f + rand.nextFloat() * 15f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            EnemyController enemy = new EnemyController(
                assetManager, stageNode, bulletAppState,
                new Vector3f(x, 5f, z), 2, loopCount
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
        return new ColorRGBA(0.6f, 0.35f, 0.15f, 1f); // orange-red haze
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.8f, 0.5f, 0.3f, 1f).mult(0.6f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.2f, -1f, -0.1f).normalizeLocal(); // harsh overhead
    }

    @Override
    public float getHalfExtent() {
        return 55f;
    }

    @Override
    public String getName() {
        return "Ashen Wastes";
    }

    @Override
    public int getStageIndex() {
        return 2;
    }

    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Box groundBox = new Box(55, 0.5f, 55);
        Geometry ground = new Geometry("AshenWastesGround", groundBox);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.45f, 0.30f, 0.18f, 1f)); // orange-brown
        ground.setMaterial(mat);
        ground.setLocalTranslation(0, -0.5f, 0);
        stageNode.attachChild(ground);

        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(55, 0.5f, 55));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildBoundaryWalls(BulletAppState bulletAppState) {
        float wallHeight = 10f;
        float wallThickness = 1f;

        createWall(new Vector3f(0, wallHeight / 2f, 55),
                   new Vector3f(55, wallHeight / 2f, wallThickness), bulletAppState);
        createWall(new Vector3f(0, wallHeight / 2f, -55),
                   new Vector3f(55, wallHeight / 2f, wallThickness), bulletAppState);
        createWall(new Vector3f(55, wallHeight / 2f, 0),
                   new Vector3f(wallThickness, wallHeight / 2f, 55), bulletAppState);
        createWall(new Vector3f(-55, wallHeight / 2f, 0),
                   new Vector3f(wallThickness, wallHeight / 2f, 55), bulletAppState);
    }

    private void createWall(Vector3f position, Vector3f halfExtents, BulletAppState bulletAppState) {
        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildDecoration(AssetManager assetManager) {
        Random rand = new Random(100);

        // Lava pools
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

        // Rock pillars
        for (int i = 0; i < 10; i++) {
            float x = (rand.nextFloat() - 0.5f) * 90f;
            float z = (rand.nextFloat() - 0.5f) * 90f;
            if (new Vector3f(x, 0, z).length() < 12f) continue;

            float height = 2f + rand.nextFloat() * 4f;
            Box pillarBox = new Box(0.4f, height / 2f, 0.4f);
            Geometry pillar = new Geometry("Pillar_" + i, pillarBox);
            Material pillarMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            pillarMat.setColor("Color", new ColorRGBA(0.3f, 0.2f, 0.15f, 1f)); // dark grey
            pillar.setMaterial(pillarMat);
            pillar.setLocalTranslation(x, height / 2f, z);
            stageNode.attachChild(pillar);
        }
    }
}
