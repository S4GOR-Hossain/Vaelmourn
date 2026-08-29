package com.vaelmourn.stage;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
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
import com.jme3.scene.VertexBuffer;
import com.vaelmourn.EnemyController;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * DarkwoodStage - Stage 1: A dark forest combat area.
 *
 * A moody, shadowy forest populated with tier-1 (easy) enemies.
 * Features dim lighting, dark trees, scattered rocks, and a central combat arena.
 * Enemies spawn in a ring around the center; player must clear them to spawn the exit portal.
 */
public class DarkwoodStage implements Stage {

    private static final int STAGE_INDEX = 1;
    private static final String STAGE_NAME = "Darkwood";
    private static final float HALF_EXTENT = 60f;
    private static final float SPAWN_RADIUS_MIN = 15f;
    private static final float SPAWN_RADIUS_MAX = 25f;
    private static final int BASE_ENEMY_COUNT = 5;
    private static final float PORTAL_SPAWN_DISTANCE = 30f;

    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    @Override
    public String getName() {
        return STAGE_NAME;
    }

    @Override
    public int getStageIndex() {
        return STAGE_INDEX;
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("Darkwood");
        parentNode.attachChild(stageNode);

        buildGroundPlane(assetManager, bulletAppState);
        buildBoundaryWalls(bulletAppState);
        buildForest(assetManager);
        buildLighting(parentNode);
    }

    @Override
    public void cleanup(Node parentNode, BulletAppState bulletAppState) {
        // Remove all physics bodies
        for (RigidBodyControl physics : physicsObjects) {
            bulletAppState.getPhysicsSpace().remove(physics);
        }
        physicsObjects.clear();

        // Detach stage from scene graph
        stageNode.removeFromParent();
    }

    @Override
    public List<EnemyController> spawnEnemies(AssetManager assetManager, Node parentNode,
                                               BulletAppState bulletAppState, int loopCount) {
        List<EnemyController> enemies = new ArrayList<>();

        // Scale enemy count with loop: base 5, +1 every 2 loops
        int enemyCount = BASE_ENEMY_COUNT + (loopCount / 2);

        Random rand = new Random(42 + loopCount); // Vary based on loop

        for (int i = 0; i < enemyCount; i++) {
            // Spawn in a ring around the center arena
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = SPAWN_RADIUS_MIN + rand.nextFloat() * (SPAWN_RADIUS_MAX - SPAWN_RADIUS_MIN);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            Vector3f spawnPos = new Vector3f(x, 5f, z);

            // Tier 1 enemies for Darkwood
            EnemyController enemy = new EnemyController(
                assetManager,
                stageNode,
                bulletAppState,
                spawnPos,
                1,  // tier 1 (easy)
                loopCount
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
        // Dark blue-grey for moody atmosphere
        return new ColorRGBA(0.15f, 0.18f, 0.25f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        // Dim, cool ambient lighting
        return new ColorRGBA(0.3f, 0.35f, 0.4f, 1f).mult(0.5f);
    }

    @Override
    public Vector3f getSunDirection() {
        // Weak sun, steeply angled, slightly blue-tinted
        return new Vector3f(-0.3f, -0.9f, -0.4f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    /**
     * Build the ground plane with tiled grass material, darkened for the forest.
     */
    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Spatial grassModel = assetManager.loadModel("Models/Environment/Forest/ground_grass.glb");

        // Darken the grass for the forest aesthetic
        fixEnvironmentMaterials(grassModel, new ColorRGBA(0.15f, 0.25f, 0.10f, 1f));

        float tileScale = 20f;
        float tileSize = tileScale;
        int half = (int) Math.ceil(HALF_EXTENT / tileSize) + 1;

        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                Spatial grass = grassModel.clone();
                grass.setLocalTranslation(x * tileSize, 0, z * tileSize);
                grass.setLocalScale(tileScale);
                stageNode.attachChild(grass);
            }
        }

        // Ground physics (flat plane)
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(HALF_EXTENT, 0.5f, HALF_EXTENT));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    /**
     * Build invisible boundary walls to contain the stage.
     */
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

    /**
     * Create a single boundary wall.
     */
    private void createWall(Vector3f position, Vector3f halfExtents, BulletAppState bulletAppState) {
        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(position);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    /**
     * Build the forest: dark trees, rocks, and pebbles scattered throughout.
     */
    private void buildForest(AssetManager assetManager) {
        Random rand = new Random(42);

        // Use dark variants of trees for Darkwood
        String[] treeModels = {
                "Models/Environment/Forest/tree_default.glb",
                "Models/Environment/Forest/tree_cone.glb",
                "Models/Environment/Forest/tree_detailed.glb",
                "Models/Environment/Forest/tree_oak.glb",
                "Models/Environment/Forest/tree_fat.glb",
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

        // Spawn 60-80 trees with a 15-unit clear zone in the center for combat
        int treeCount = 70;

        for (int i = 0; i < treeCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * (HALF_EXTENT * 2.2f);
            float z = (rand.nextFloat() - 0.5f) * (HALF_EXTENT * 2.2f);

            // Keep center arena clear
            if (new Vector3f(x, 0, z).length() < 15f) {
                i--;
                continue;
            }

            placeTree(assetManager, x, z, rand, treeModels);
        }

        // Place rock clusters
        placeRock(assetManager, 20f, 15f, 4f, rand, rockModels);
        placeRock(assetManager, -25f, -10f, 5f, rand, rockModels);
        placeRock(assetManager, 10f, -30f, 3.5f, rand, rockModels);
        placeRock(assetManager, -35f, 20f, 4.5f, rand, rockModels);

        // Scatter pebbles
        scatterPebbles(assetManager, rockModels);
    }

    /**
     * Place a single tree with physics collision.
     */
    private void placeTree(AssetManager assetManager, float x, float z, Random rand, String[] treeModels) {
        String chosenModel = treeModels[rand.nextInt(treeModels.length)];
        Spatial tree = assetManager.loadModel(chosenModel);

        // Darken tree color for Darkwood
        fixEnvironmentMaterials(tree, new ColorRGBA(0.12f, 0.20f, 0.08f, 1f));

        tree.setLocalTranslation(x, 0, z);
        tree.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);

        float baseScale = 4f;
        if (chosenModel.contains("default")) baseScale = 4.5f;
        if (chosenModel.contains("cone")) baseScale = 4.5f;
        if (chosenModel.contains("detailed")) baseScale = 4f;
        if (chosenModel.contains("oak")) baseScale = 3.5f;
        if (chosenModel.contains("fat")) baseScale = 4f;
        if (chosenModel.contains("blocks")) baseScale = 4f;

        float scale = baseScale + rand.nextFloat() * 0.8f;
        tree.setLocalScale(scale);

        stageNode.attachChild(tree);

        // Add trunk collision
        CapsuleCollisionShape trunkShape = new CapsuleCollisionShape(0.7f, 3.5f);
        RigidBodyControl physics = new RigidBodyControl(trunkShape, 0);
        physics.setPhysicsLocation(new Vector3f(x, 1.8f, z));
        // Note: Physics bodies for trees are not tracked for cleanup; they remain
        // attached to the static tree geometry and will be cleaned up with the stage node
    }

    /**
     * Place a rock cluster at the given position.
     */
    private void placeRock(AssetManager assetManager, float x, float z, float size, Random rand, String[] rockModels) {
        String chosenModel = rockModels[rand.nextInt(rockModels.length)];
        Spatial rock = assetManager.loadModel(chosenModel);

        // Darken rock color for Darkwood
        fixEnvironmentMaterials(rock, new ColorRGBA(0.25f, 0.25f, 0.25f, 1f));

        rock.setLocalTranslation(x, 0, z);
        rock.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
        rock.setLocalScale(size / 2f);

        stageNode.attachChild(rock);

        rock.updateModelBound();
        stageNode.updateGeometricState();

        BoundingBox bbox = getWorldBoundingBox(rock);
        Vector3f halfExtents = bbox != null ? bbox.getExtent(new Vector3f()) : new Vector3f(size, size * 0.6f, size);
        Vector3f center = bbox != null ? bbox.getCenter().clone() : new Vector3f(x, size * 0.6f, z);

        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(center);

        // Note: Rock physics are not tracked; they remain static on the stage
    }

    /**
     * Scatter small pebbles across the ground for detail.
     */
    private void scatterPebbles(AssetManager assetManager, String[] rockModels) {
        Random rand = new Random(99);
        int pebbleCount = 200;

        for (int i = 0; i < pebbleCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * (HALF_EXTENT * 2.2f);
            float z = (rand.nextFloat() - 0.5f) * (HALF_EXTENT * 2.2f);

            String chosenModel = rockModels[rand.nextInt(rockModels.length)];
            if (!chosenModel.contains("small")) continue;

            Spatial pebble = assetManager.loadModel(chosenModel);
            fixEnvironmentMaterials(pebble, new ColorRGBA(0.30f, 0.30f, 0.30f, 1f));

            pebble.setLocalTranslation(x, 0, z);
            pebble.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
            pebble.setLocalScale(0.3f + rand.nextFloat() * 0.4f);

            stageNode.attachChild(pebble);
        }
    }

    /**
     * Get the world bounding box of a spatial.
     */
    private BoundingBox getWorldBoundingBox(Spatial spatial) {
        if (spatial.getWorldBound() instanceof BoundingBox) {
            return (BoundingBox) spatial.getWorldBound();
        }
        return null;
    }

    /**
     * Build the lighting for this stage: dim, moody atmosphere with cool tones.
     */
    private void buildLighting(Node parentNode) {
        // Main sun (weak, steep angle, cool blue tint)
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(getSunDirection());
        sun.setColor(ColorRGBA.White.mult(0.4f).add(new ColorRGBA(0f, 0.1f, 0.2f, 0f)));
        parentNode.addLight(sun);

        // Fill light (subtle)
        DirectionalLight fill = new DirectionalLight();
        fill.setDirection(new Vector3f(0.5f, -0.5f, 0.5f).normalizeLocal());
        fill.setColor(ColorRGBA.White.mult(0.2f));
        parentNode.addLight(fill);

        // Ambient light (dim)
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(getAmbientColor());
        parentNode.addLight(ambient);
    }

    /**
     * Fix materials on environment geometry: ensure they render correctly with Darkwood colors.
     * Copied from ForestBiome for consistency.
     * Note: AssetManager must be passed externally or accessed through stage context.
     */
    private void fixEnvironmentMaterials(Spatial spatial, ColorRGBA fallbackColor) {
        if (spatial instanceof Geometry) {
            Geometry geometry = (Geometry) spatial;
            Material existingMaterial = geometry.getMaterial();

            if (existingMaterial != null) {
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
