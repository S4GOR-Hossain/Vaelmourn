package com.vaelmourn.stage;

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
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.vaelmourn.EnemyController;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SanctuaryStage — Stage 0, the safe hub/rest area.
 *
 * The Sanctuary is a peaceful area with no enemies. It contains:
 * - A healing fountain at the center
 * - Shop area (placeholder stalls)
 * - Chest area (placeholder chests)
 * - Boundary walls to contain the player
 *
 * The player spawns here at the start of each roguelike loop,
 * with full health and mana restored.
 */
public class SanctuaryStage implements Stage {

    private Node root;
    private final Vector3f spawnPoint = new Vector3f(0, 2, 5);
    private final float halfExtent = 40f;

    // Track physics bodies so they can be cleaned up in cleanup()
    private final List<RigidBodyControl> physicsBodies = new ArrayList<>();

    @Override
    public String getName() {
        return "Sanctuary";
    }

    @Override
    public int getStageIndex() {
        return 0;
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        root = new Node("Sanctuary");
        parentNode.attachChild(root);

        buildGroundPlane(assetManager);
        buildHealingFountain(assetManager);
        buildShopArea(assetManager);
        buildChestArea(assetManager);
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
        // Sanctuary has no enemies
        return new ArrayList<>();
    }

    @Override
    public Vector3f getPlayerSpawnPoint() {
        return spawnPoint;
    }

    @Override
    public ColorRGBA getSkyColor() {
        // Warm golden hour sky
        return new ColorRGBA(0.55f, 0.65f, 0.75f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        // Warm, inviting ambient light
        return new ColorRGBA(1f, 0.95f, 0.85f, 1f).mult(0.9f);
    }

    @Override
    public Vector3f getSunDirection() {
        // Warm light angled from above-left
        return new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return halfExtent;
    }

    private void buildGroundPlane(AssetManager assetManager) {
        Spatial grassModel = assetManager.loadModel("Models/Environment/Forest/ground_grass.glb");

        fixEnvironmentMaterials(grassModel, new ColorRGBA(0.35f, 0.55f, 0.30f, 1f));

        float tileScale = 20f;
        float tileSize = tileScale;
        int half = (int) Math.ceil(halfExtent / tileSize) + 1;

        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                Spatial grass = grassModel.clone();
                grass.setLocalTranslation(x * tileSize, 0, z * tileSize);
                grass.setLocalScale(tileScale);
                root.attachChild(grass);
            }
        }

        // Ground physics: large flat box
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(halfExtent, 0.5f, halfExtent));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        physicsBodies.add(physics);
    }

    private void buildHealingFountain(AssetManager assetManager) {
        Node fountainNode = new Node("HealingFountain");
        fountainNode.setLocalTranslation(0, 0, 0);

        // Pedestal: a short cylinder
        Geometry pedestalGeo = new Geometry("Pedestal",
                new Cylinder(16, 16, 1.5f, 0.5f));
        Material pedestalMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        pedestalMat.setColor("Diffuse", new ColorRGBA(0.6f, 0.5f, 0.3f, 1f));
        pedestalMat.setColor("Specular", ColorRGBA.White);
        pedestalMat.setFloat("Shininess", 4f);
        pedestalGeo.setMaterial(pedestalMat);
        pedestalGeo.setLocalTranslation(0, 0.25f, 0);
        fountainNode.attachChild(pedestalGeo);

        // Water globe: a sphere with blue emissive material
        Geometry waterGeo = new Geometry("Water",
                new com.jme3.scene.shape.Sphere(16, 16, 0.8f));
        Material waterMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        waterMat.setColor("Diffuse", new ColorRGBA(0.1f, 0.3f, 0.8f, 0.8f));
        waterMat.setColor("Emissive", new ColorRGBA(0f, 0.4f, 1f, 1f));
        waterMat.setColor("Specular", ColorRGBA.White);
        waterMat.setFloat("Shininess", 16f);
        waterGeo.setMaterial(waterMat);
        waterGeo.setLocalTranslation(0, 1.2f, 0);
        fountainNode.attachChild(waterGeo);

        root.attachChild(fountainNode);
    }

    private void buildShopArea(AssetManager assetManager) {
        Node shopNode = new Node("ShopArea");
        shopNode.setLocalTranslation(15f, 0, 0);

        // Three market stall boxes arranged in a row
        float stallWidth = 2.5f;
        float stallHeight = 2f;
        float stallDepth = 2f;

        ColorRGBA stallColor = new ColorRGBA(0.7f, 0.6f, 0.4f, 1f);

        for (int i = 0; i < 3; i++) {
            Geometry stallGeo = new Geometry("Stall_" + i,
                    new Box(stallWidth / 2, stallHeight / 2, stallDepth / 2));
            Material stallMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            stallMat.setColor("Diffuse", stallColor);
            stallMat.setColor("Specular", ColorRGBA.White);
            stallMat.setFloat("Shininess", 2f);
            stallGeo.setMaterial(stallMat);
            stallGeo.setLocalTranslation((i - 1) * (stallWidth + 1f), stallHeight / 2, 0);
            shopNode.attachChild(stallGeo);
        }

        root.attachChild(shopNode);
    }

    private void buildChestArea(AssetManager assetManager) {
        Node chestAreaNode = new Node("ChestArea");
        chestAreaNode.setLocalTranslation(-15f, 0, 0);

        // Three small treasure chest boxes
        float chestWidth = 1.5f;
        float chestHeight = 1.2f;
        float chestDepth = 1.5f;

        ColorRGBA chestColor = new ColorRGBA(0.8f, 0.7f, 0.2f, 1f);

        Random rand = new Random(123);

        for (int i = 0; i < 3; i++) {
            Geometry chestGeo = new Geometry("Chest_" + i,
                    new Box(chestWidth / 2, chestHeight / 2, chestDepth / 2));
            Material chestMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            chestMat.setColor("Diffuse", chestColor);
            chestMat.setColor("Specular", ColorRGBA.White);
            chestMat.setFloat("Shininess", 8f);
            chestGeo.setMaterial(chestMat);

            float offsetX = (rand.nextFloat() - 0.5f) * 4f;
            float offsetZ = (rand.nextFloat() - 0.5f) * 3f;
            chestGeo.setLocalTranslation(offsetX, chestHeight / 2, offsetZ);
            chestAreaNode.attachChild(chestGeo);
        }

        root.attachChild(chestAreaNode);
    }

    private void buildBoundaryWalls() {
        float wallHeight = 10f;
        float wallThickness = 1f;

        createWall(new Vector3f(0, wallHeight / 2f, halfExtent),
                new Vector3f(halfExtent, wallHeight / 2f, wallThickness));
        createWall(new Vector3f(0, wallHeight / 2f, -halfExtent),
                new Vector3f(halfExtent, wallHeight / 2f, wallThickness));
        createWall(new Vector3f(halfExtent, wallHeight / 2f, 0),
                new Vector3f(wallThickness, wallHeight / 2f, halfExtent));
        createWall(new Vector3f(-halfExtent, wallHeight / 2f, 0),
                new Vector3f(wallThickness, wallHeight / 2f, halfExtent));
    }

    private void createWall(Vector3f position, Vector3f halfExtents) {
        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position);
        physicsBodies.add(physics);
    }

    private void buildLighting() {
        // Warm directional sun
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal());
        sun.setColor(ColorRGBA.White.mult(1.1f));
        root.addLight(sun);

        // Soft fill light
        DirectionalLight fill = new DirectionalLight();
        fill.setDirection(new Vector3f(0.5f, -0.5f, 0.5f).normalizeLocal());
        fill.setColor(ColorRGBA.White.mult(0.4f));
        root.addLight(fill);

        // Warm ambient
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(1f, 0.95f, 0.85f, 1f).mult(0.9f));
        root.addLight(ambient);
    }

    private void fixEnvironmentMaterials(Spatial spatial, ColorRGBA fallbackColor) {
        if (spatial instanceof Geometry) {
            Geometry geometry = (Geometry) spatial;
            Material existingMaterial = geometry.getMaterial();

            if (existingMaterial == null) {
                Material material = new Material(null, "Common/MatDefs/Light/Lighting.j3md");
                material.setBoolean("UseMaterialColors", true);
                material.setColor("Diffuse", fallbackColor);
                material.setColor("Specular", ColorRGBA.White);
                material.setFloat("Shininess", 8f);
                geometry.setMaterial(material);
            } else {
                boolean isPbr =
                        existingMaterial.getMaterialDef()
                                .getAssetName()
                                .contains("PBRLighting");

                if (isPbr) {
                    if (existingMaterial.getMaterialDef().getMaterialParam("Metallic") != null) {
                        existingMaterial.setFloat("Metallic", 0f);
                    }
                    if (existingMaterial.getMaterialDef().getMaterialParam("Roughness") != null) {
                        existingMaterial.setFloat("Roughness", 1f);
                    }
                }
            }
        }

        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                fixEnvironmentMaterials(child, fallbackColor);
            }
        }
    }
}
