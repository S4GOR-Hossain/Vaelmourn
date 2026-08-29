package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * JungleStage — Stage 4: Dense tropical jungle teeming with palms, rock and vine.
 *
 * Built with the same model-loaded approach as the original forest biome: palm
 * trees and stone models are loaded from Models/Environment/Forest and scattered
 * with physics collision. Comes after the snow biome (Frozen Depths).
 */
public class JungleStage implements Stage {

    private static final float HALF_EXTENT = 50f;

    private Node stageNode;
    private AssetManager assetManager;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        this.assetManager = assetManager;
        stageNode = new Node("Jungle");
        parentNode.attachChild(stageNode);

        buildGroundPlane(assetManager, bulletAppState);
        buildBoundaryWalls(bulletAppState);
        buildJungle(assetManager, bulletAppState);
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
        int enemyCount = 9 + (loopCount / 2);
        Random rand = new Random(66 + loopCount);

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
        return new ColorRGBA(0.35f, 0.55f, 0.4f, 1f); // muggy jungle green haze
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.35f, 0.6f, 0.35f, 1f).mult(0.7f); // cool green shade
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(0.5f, -0.7f, -0.3f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    @Override
    public String getName() {
        return "Jungle";
    }

    @Override
    public int getStageIndex() {
        return 4; // comes after Frozen Depths (3)
    }

    // ---- Environment building (model-loaded, copied from original forest code) ----

    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Spatial groundModel = assetManager.loadModel("Models/Environment/Forest/ground_grass.glb");
        fixEnvironmentMaterials(groundModel, new ColorRGBA(0.22f, 0.45f, 0.18f, 1f));

        float tileScale = 20f;
        float tileSize = tileScale;
        int half = (int) Math.ceil(HALF_EXTENT / tileSize) + 1;

        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                Spatial ground = groundModel.clone();
                ground.setLocalTranslation(x * tileSize, 0, z * tileSize);
                ground.setLocalScale(tileScale);
                stageNode.attachChild(ground);
            }
        }

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

    private void buildJungle(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(42);

        // Densest mix: palms + broad forest trees for a lush canopy.
        String[] treeModels = {
                "Models/Environment/Forest/tree_palm.glb",
                "Models/Environment/Forest/tree_palmBend.glb",
                "Models/Environment/Forest/tree_palmDetailedShort.glb",
                "Models/Environment/Forest/tree_palmDetailedTall.glb",
                "Models/Environment/Forest/tree_detailed.glb",
                "Models/Environment/Forest/tree_fat.glb",
                "Models/Environment/Forest/tree_default.glb",
                "Models/Environment/Forest/tree_oak.glb",
                "Models/Environment/Forest/tree_blocks.glb"
        };

        String[] rockModels = {
                "Models/Environment/Forest/stone_largeA.glb",
                "Models/Environment/Forest/stone_largeB.glb",
                "Models/Environment/Forest/stone_largeC.glb",
                "Models/Environment/Forest/stone_smallA.glb",
                "Models/Environment/Forest/stone_smallB.glb",
                "Models/Environment/Forest/stone_smallC.glb",
                "Models/Environment/Forest/stone_smallFlatA.glb",
                "Models/Environment/Forest/stone_smallFlatB.glb",
                "Models/Environment/Forest/stone_smallFlatC.glb",
                "Models/Environment/Forest/stone_smallG.glb",
                "Models/Environment/Forest/stone_smallH.glb",
                "Models/Environment/Forest/stone_smallI.glb",
                "Models/Environment/Forest/stone_tallA.glb",
                "Models/Environment/Forest/stone_tallB.glb",
                "Models/Environment/Forest/stone_tallC.glb",
                "Models/Environment/Forest/stone_tallD.glb",
                "Models/Environment/Forest/stump_old.glb"
        };

        int treeCount = 90; // denser than the original forest

        for (int i = 0; i < treeCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * 95f;
            float z = (rand.nextFloat() - 0.5f) * 95f;

            if (new Vector3f(x, 0, z).length() < 15f) {
                i--;
                continue;
            }

            placeTree(x, z, rand, treeModels, assetManager, bulletAppState);
        }

        placeRock(20f, 15f, 4f, rand, rockModels, assetManager, bulletAppState);
        placeRock(-25f, -10f, 5f, rand, rockModels, assetManager, bulletAppState);
        placeRock(10f, -30f, 3.5f, rand, rockModels, assetManager, bulletAppState);
        placeRock(-18f, 28f, 4.5f, rand, rockModels, assetManager, bulletAppState);

        scatterPebbles(rockModels, assetManager);
    }

    private void scatterPebbles(String[] rockModels, AssetManager assetManager) {
        Random rand = new Random(99);
        int pebbleCount = 350;

        for (int i = 0; i < pebbleCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * 100f;
            float z = (rand.nextFloat() - 0.5f) * 100f;

            String chosenModel = rockModels[rand.nextInt(rockModels.length)];
            if (!chosenModel.contains("small")) continue;

            Spatial pebble = assetManager.loadModel(chosenModel);
            fixEnvironmentMaterials(pebble, new ColorRGBA(0.40f, 0.40f, 0.40f, 1f));

            pebble.setLocalTranslation(x, 0, z);
            pebble.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
            pebble.setLocalScale(0.3f + rand.nextFloat() * 0.4f);

            stageNode.attachChild(pebble);
        }
    }

    private void placeTree(float x, float z, Random rand, String[] treeModels,
                           AssetManager assetManager, BulletAppState bulletAppState) {
        String chosenModel = treeModels[rand.nextInt(treeModels.length)];
        Spatial tree = assetManager.loadModel(chosenModel);

        fixEnvironmentMaterials(tree, new ColorRGBA(0.16f, 0.5f, 0.16f, 1f));

        tree.setLocalTranslation(x, 0, z);
        tree.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);

        float baseScale = 4f;
        if (chosenModel.contains("palm")) baseScale = 5f;
        if (chosenModel.contains("detailed")) baseScale = 4.2f;
        if (chosenModel.contains("oak")) baseScale = 3.5f;
        if (chosenModel.contains("fat")) baseScale = 4f;
        if (chosenModel.contains("blocks")) baseScale = 4f;
        if (chosenModel.contains("default")) baseScale = 4.5f;

        float scale = baseScale + rand.nextFloat() * 0.8f;
        tree.setLocalScale(scale);

        CapsuleCollisionShape trunkShape = new CapsuleCollisionShape(0.7f, 3.5f);
        RigidBodyControl physics = new RigidBodyControl(trunkShape, 0);
        physics.setPhysicsLocation(new Vector3f(x, 1.8f, z));

        stageNode.attachChild(tree);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void placeRock(float x, float z, float size, Random rand, String[] rockModels,
                           AssetManager assetManager, BulletAppState bulletAppState) {
        String chosenModel = rockModels[rand.nextInt(rockModels.length)];
        Spatial rock = assetManager.loadModel(chosenModel);

        fixEnvironmentMaterials(rock, new ColorRGBA(0.40f, 0.40f, 0.40f, 1f));

        rock.setLocalTranslation(x, 0, z);
        rock.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
        rock.setLocalScale(size / 2f);

        stageNode.attachChild(rock);

        rock.updateModelBound();
        stageNode.updateGeometricState();

        BoundingBox bbox = getWorldBoundingBox(rock);

        Vector3f halfExtents =
                bbox != null ? bbox.getExtent(new Vector3f()) : new Vector3f(size, size * 0.6f, size);

        Vector3f center =
                bbox != null ? bbox.getCenter().clone() : new Vector3f(x, size * 0.6f, z);

        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(center);

        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private BoundingBox getWorldBoundingBox(Spatial spatial) {
        if (spatial.getWorldBound() instanceof BoundingBox) {
            return (BoundingBox) spatial.getWorldBound();
        }
        return null;
    }

    private void fixEnvironmentMaterials(Spatial spatial, ColorRGBA fallbackColor) {
        if (spatial instanceof Geometry) {
            Geometry geometry = (Geometry) spatial;
            Material existingMaterial = geometry.getMaterial();

            if (existingMaterial == null) {
                Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
                material.setBoolean("UseMaterialColors", true);
                material.setColor("Diffuse", fallbackColor);
                material.setColor("Specular", ColorRGBA.White);
                material.setFloat("Shininess", 8f);
                geometry.setMaterial(material);
            } else {
                boolean hasVertexColors =
                        geometry.getMesh().getBuffer(VertexBuffer.Type.Color) != null;

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

                if (hasVertexColors
                        && existingMaterial.getMaterialDef().getMaterialParam("UseVertexColor") != null) {
                    existingMaterial.setBoolean("UseVertexColor", true);
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
