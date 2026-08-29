package com.vaelmourn;

import com.jme3.asset.AssetManager;
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
import com.jme3.scene.shape.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * DarkwoodStage — Stage 1: Dark forest with tier-1 enemies.
 */
public class DarkwoodStage implements Stage {

    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("Darkwood");
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
        int enemyCount = 5 + (loopCount / 2);
        Random rand = new Random(42 + loopCount);

        for (int i = 0; i < enemyCount; i++) {
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = 15f + rand.nextFloat() * 10f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            EnemyController enemy = new EnemyController(
                assetManager, stageNode, bulletAppState,
                new Vector3f(x, 5f, z), 1, loopCount
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
        return 60f;
    }

    @Override
    public String getName() {
        return "Darkwood";
    }

    @Override
    public int getStageIndex() {
        return 1;
    }

    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Box groundBox = new Box(60, 0.5f, 60);
        Geometry ground = new Geometry("DarkwoodGround", groundBox);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.2f, 0.3f, 0.15f, 1f)); // dark green
        ground.setMaterial(mat);
        ground.setLocalTranslation(0, -0.5f, 0);
        stageNode.attachChild(ground);
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(60, 0.5f, 60));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildBoundaryWalls(BulletAppState bulletAppState) {
        float wallHeight = 10f;
        float wallThickness = 1f;

        createWall(new Vector3f(0, wallHeight / 2f, 60),
                   new Vector3f(60, wallHeight / 2f, wallThickness), bulletAppState);
        createWall(new Vector3f(0, wallHeight / 2f, -60),
                   new Vector3f(60, wallHeight / 2f, wallThickness), bulletAppState);
        createWall(new Vector3f(60, wallHeight / 2f, 0),
                   new Vector3f(wallThickness, wallHeight / 2f, 60), bulletAppState);
        createWall(new Vector3f(-60, wallHeight / 2f, 0),
                   new Vector3f(wallThickness, wallHeight / 2f, 60), bulletAppState);
    }

    private void createWall(Vector3f position, Vector3f halfExtents, BulletAppState bulletAppState) {
        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildDecoration(AssetManager assetManager) {
        Random rand = new Random(99);

        // Scatter some dark trees (just box geometries as placeholders)
        for (int i = 0; i < 8; i++) {
            float x = (rand.nextFloat() - 0.5f) * 100f;
            float z = (rand.nextFloat() - 0.5f) * 100f;
            if (new Vector3f(x, 0, z).length() < 15f) continue;

            Box treeBox = new Box(0.5f, 3f, 0.5f);
            Geometry tree = new Geometry("Tree_" + i, treeBox);
            Material treeMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            treeMat.setColor("Color", new ColorRGBA(0.1f, 0.15f, 0.08f, 1f));
            tree.setMaterial(treeMat);
            tree.setLocalTranslation(x, 3f, z);
            stageNode.attachChild(tree);
        }
    }
}
