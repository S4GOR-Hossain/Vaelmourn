package com.vaelmourn;

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

import java.util.ArrayList;
import java.util.List;

/**
 * SanctuaryStage — Stage 0: Safe hub with healing fountain, shop, and chests.
 * No enemies. Player heals and rests here between combat runs.
 */
public class SanctuaryStage implements Stage {

    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("Sanctuary");
        parentNode.attachChild(stageNode);

        buildGroundPlane(assetManager, bulletAppState);
        buildBoundaryWalls(bulletAppState);
        buildHealingFountain(assetManager);
        buildShop(assetManager);
        buildChests(assetManager);
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
        return new ArrayList<>(); // No enemies in Sanctuary
    }

    @Override
    public Vector3f getPlayerSpawnPoint() {
        return new Vector3f(0, 2f, 5f);
    }

    @Override
    public ColorRGBA getSkyColor() {
        return new ColorRGBA(0.55f, 0.65f, 0.75f, 1f); // Warm golden hour
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(1.0f, 0.95f, 0.85f, 1f).mult(0.9f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.5f, -0.8f, -0.3f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return 40f;
    }

    @Override
    public String getName() {
        return "Sanctuary";
    }

    @Override
    public int getStageIndex() {
        return 0;
    }

    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Box groundBox = new Box(40, 0.5f, 40);
        Geometry ground = new Geometry("SanctuaryGround", groundBox);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.6f, 0.8f, 0.6f, 1f));
        ground.setMaterial(mat);
        // Align the visual ground top (y=0) with the physics collider top (y=0) so
        // the player and scenery sit ON the surface instead of clipping into it.
        ground.setLocalTranslation(0, -0.5f, 0);
        stageNode.attachChild(ground);

        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(40, 0.5f, 40));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildBoundaryWalls(BulletAppState bulletAppState) {
        float wallHeight = 10f;
        float wallThickness = 1f;

        createWall(new Vector3f(0, wallHeight / 2f, 40),
                   new Vector3f(40, wallHeight / 2f, wallThickness), bulletAppState);
        createWall(new Vector3f(0, wallHeight / 2f, -40),
                   new Vector3f(40, wallHeight / 2f, wallThickness), bulletAppState);
        createWall(new Vector3f(40, wallHeight / 2f, 0),
                   new Vector3f(wallThickness, wallHeight / 2f, 40), bulletAppState);
        createWall(new Vector3f(-40, wallHeight / 2f, 0),
                   new Vector3f(wallThickness, wallHeight / 2f, 40), bulletAppState);
    }

    private void createWall(Vector3f position, Vector3f halfExtents, BulletAppState bulletAppState) {
        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildHealingFountain(AssetManager assetManager) {
        Node fountainNode = new Node("HealingFountain");

        // Pedestal
        Box pedestalBox = new Box(1.5f, 2f, 1.5f);
        Geometry pedestal = new Geometry("FountainPedestal", pedestalBox);
        Material pedestalMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        pedestalMat.setBoolean("UseMaterialColors", true);
        pedestalMat.setColor("Diffuse", new ColorRGBA(0.6f, 0.6f, 0.5f, 1f));
        pedestal.setMaterial(pedestalMat);
        fountainNode.attachChild(pedestal);

        // Glowing water sphere on top
        com.jme3.scene.shape.Sphere waterSphere = new com.jme3.scene.shape.Sphere(32, 32, 1f);
        Geometry water = new Geometry("FountainWater", waterSphere);
        Material waterMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        waterMat.setBoolean("UseMaterialColors", true);
        waterMat.setColor("Diffuse", new ColorRGBA(0.2f, 0.6f, 1f, 1f));
        waterMat.setColor("GlowColor", new ColorRGBA(0f, 0.3f, 0.8f, 1f));
        water.setMaterial(waterMat);
        water.setLocalTranslation(0, 2.5f, 0);
        fountainNode.attachChild(water);

        fountainNode.setLocalTranslation(0, 0, 0);
        stageNode.attachChild(fountainNode);
    }

    private void buildShop(AssetManager assetManager) {
        Node shopNode = new Node("Shop");

        // Two stall boxes
        for (int i = 0; i < 2; i++) {
            Box stallBox = new Box(2f, 1.5f, 2f);
            Geometry stall = new Geometry("Stall_" + i, stallBox);
            Material stallMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            stallMat.setColor("Color", new ColorRGBA(0.6f, 0.5f, 0.3f, 1f)); // brown
            stall.setMaterial(stallMat);
            stall.setLocalTranslation(8f + i * 5f, 1.5f, 0);
            shopNode.attachChild(stall);
        }

        shopNode.setLocalTranslation(0, 0, 0);
        stageNode.attachChild(shopNode);
    }

    private void buildChests(AssetManager assetManager) {
        Node chestNode = new Node("ChestArea");

        // Three treasure chests
        for (int i = 0; i < 3; i++) {
            Box chestBox = new Box(1f, 1f, 1f);
            Geometry chest = new Geometry("Chest_" + i, chestBox);
            Material chestMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            chestMat.setColor("Color", new ColorRGBA(0.9f, 0.8f, 0.2f, 1f)); // golden
            chest.setMaterial(chestMat);
            chest.setLocalTranslation(-10f - i * 4f, 1f, 0);
            chestNode.attachChild(chest);
        }

        stageNode.attachChild(chestNode);
    }
}
