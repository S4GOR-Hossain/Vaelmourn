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
import com.jme3.scene.shape.Box;
import com.vaelmourn.EnemyController;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FrozenDepthsStage - Stage 3: An icy tundra and frozen cavern.
 *
 * A harsh, frozen landscape with towering ice pillars, snow-covered terrain,
 * and glacial cavern walls. Populated with tier-3 (hard) enemies.
 * Features cold blue lighting, translucent ice structures, and a tight combat arena.
 * Enemies spawn scattered throughout; player must clear them to spawn the exit portal.
 */
public class FrozenDepthsStage implements Stage {

    private static final int STAGE_INDEX = 3;
    private static final String STAGE_NAME = "Frozen Depths";
    private static final float HALF_EXTENT = 50f;
    private static final float SPAWN_RADIUS_MIN = 12f;
    private static final float SPAWN_RADIUS_MAX = 20f;
    private static final int BASE_ENEMY_COUNT = 8;
    private static final float PORTAL_SPAWN_DISTANCE = 30f;

    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();
    private AssetManager assetManager;

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
        this.assetManager = assetManager;
        stageNode = new Node("FrozenDepths");
        parentNode.attachChild(stageNode);

        buildGroundPlane(assetManager, bulletAppState);
        buildBoundaryWalls(bulletAppState);
        buildIcePillars(assetManager);
        buildSnowMounds(assetManager);
        buildFrozenStones(assetManager);
        buildLighting(parentNode);
    }

    @Override
    public void cleanup(Node parentNode, BulletAppState bulletAppState) {
        // Remove all tracked physics bodies
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

        // Scale enemy count with loop: base 8, +1 every 2 loops
        int enemyCount = BASE_ENEMY_COUNT + (loopCount / 2);

        Random rand = new Random(43 + loopCount); // Vary based on loop (different seed than other stages)

        for (int i = 0; i < enemyCount; i++) {
            // Spawn in a ring around the center arena, tighter than other stages
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = SPAWN_RADIUS_MIN + rand.nextFloat() * (SPAWN_RADIUS_MAX - SPAWN_RADIUS_MIN);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            Vector3f spawnPos = new Vector3f(x, 5f, z);

            // Tier 3 enemies for Frozen Depths (hardest)
            EnemyController enemy = new EnemyController(
                assetManager,
                stageNode,
                bulletAppState,
                spawnPos,
                3,  // tier 3 (hard)
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
        // Pale icy blue, cold and distant
        return new ColorRGBA(0.7f, 0.8f, 0.92f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        // Cold blue ambient lighting, reduced intensity
        return new ColorRGBA(0.5f, 0.6f, 0.8f, 1f).mult(0.7f);
    }

    @Override
    public Vector3f getSunDirection() {
        // Low angle sun, pale white, weak intensity
        return new Vector3f(-0.4f, -0.8f, -0.3f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    /**
     * Build the ground plane: flat icy surface with frozen appearance.
     */
    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        // Create a flat, icy ground using simple geometry
        // Use the base grass tile but recolor it with ice blue
        Spatial groundModel = assetManager.loadModel("Models/Environment/Forest/ground_grass.glb");

        // Apply icy blue-white coloring
        fixEnvironmentMaterials(groundModel, new ColorRGBA(0.75f, 0.85f, 0.95f, 1f));

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
     * Build translucent ice pillars: tall, narrow structures scattered throughout the arena.
     * 8-12 pillars with varying heights and tilts for visual interest.
     */
    private void buildIcePillars(AssetManager assetManager) {
        Random rand = new Random(101);
        int pillarCount = 10;

        for (int i = 0; i < pillarCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * (HALF_EXTENT * 1.8f);
            float z = (rand.nextFloat() - 0.5f) * (HALF_EXTENT * 1.8f);

            // Keep center arena clear
            if (new Vector3f(x, 0, z).length() < 10f) {
                i--;
                continue;
            }

            float height = 4f + rand.nextFloat() * 5f;  // Height between 4-9 units
            float width = 0.4f + rand.nextFloat() * 0.3f; // Width between 0.4-0.7 units

            // Create ice pillar geometry
            Box pillarShape = new Box(width, height / 2f, width);
            Geometry pillar = new Geometry("IcePillar_" + i, pillarShape);

            // Apply light blue semi-transparent material
            Material iceMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            iceMat.setColor("Color", new ColorRGBA(0.6f, 0.75f, 0.9f, 0.8f));
            pillar.setMaterial(iceMat);

            // Position and tilt
            pillar.setLocalTranslation(x, height / 2f, z);

            // Slight random rotation/tilt for visual interest
            if (rand.nextBoolean()) {
                float tiltAngle = rand.nextFloat() * 0.15f; // Up to ~8 degrees
                pillar.rotate(tiltAngle, 0, 0);
            }

            stageNode.attachChild(pillar);
        }
    }

    /**
     * Build snow mounds: low, flat white shapes scattered on the ground for detail.
     */
    private void buildSnowMounds(AssetManager assetManager) {
        Random rand = new Random(102);
        int moundCount = 15;

        for (int i = 0; i < moundCount; i++) {
            float x = (rand.nextFloat() - 0.5f) * (HALF_EXTENT * 1.9f);
            float z = (rand.nextFloat() - 0.5f) * (HALF_EXTENT * 1.9f);

            float width = 1f + rand.nextFloat() * 2f;   // Width 1-3 units
            float height = 0.3f + rand.nextFloat() * 0.5f; // Height 0.3-0.8 units

            // Create snow mound as a flat box
            Box moundShape = new Box(width, height / 2f, width);
            Geometry mound = new Geometry("SnowMound_" + i, moundShape);

            // White, unshaded material
            Material snowMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            snowMat.setColor("Color", new ColorRGBA(0.95f, 0.97f, 1.0f, 1f));
            mound.setMaterial(snowMat);

            mound.setLocalTranslation(x, height / 2f, z);

            stageNode.attachChild(mound);
        }
    }

    /**
     * Build frozen stones: use stone models from the forest set but with ice-blue coloring.
     * Scattered throughout the arena as environmental clutter.
     */
    private void buildFrozenStones(AssetManager assetManager) {
        Random rand = new Random(103);

        String[] stoneModels = {
                "Models/Environment/Forest/stone_largeA.glb",
                "Models/Environment/Forest/stone_largeB.glb",
                "Models/Environment/Forest/stone_largeC.glb",
                "Models/Environment/Forest/stone_smallA.glb",
                "Models/Environment/Forest/stone_smallB.glb",
                "Models/Environment/Forest/stone_smallC.glb",
                "Models/Environment/Forest/stone_smallFlatA.glb",
                "Models/Environment/Forest/stone_smallFlatB.glb",
                "Models/Environment/Forest/stone_smallFlatC.glb",
                "Models/Environment/Forest/stone_tallA.glb",
                "Models/Environment/Forest/stone_tallB.glb",
                "Models/Environment/Forest/stone_tallC.glb"
        };

        // Place 20-25 frozen stones
        for (int i = 0; i < 22; i++) {
            float x = (rand.nextFloat() - 0.5f) * (HALF_EXTENT * 1.8f);
            float z = (rand.nextFloat() - 0.5f) * (HALF_EXTENT * 1.8f);

            // Keep center arena clear
            if (new Vector3f(x, 0, z).length() < 8f) {
                i--;
                continue;
            }

            String chosenModel = stoneModels[rand.nextInt(stoneModels.length)];
            Spatial stone = assetManager.loadModel(chosenModel);

            // Apply icy blue coloring to frozen stones
            fixEnvironmentMaterials(stone, new ColorRGBA(0.65f, 0.75f, 0.85f, 1f));

            float scale = 1.5f + rand.nextFloat() * 1.5f; // Scale 1.5x to 3x

            stone.setLocalTranslation(x, 0, z);
            stone.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
            stone.setLocalScale(scale);

            stageNode.attachChild(stone);
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
     * Build the lighting for this stage: cold, weak sunlight with icy blue tones.
     */
    private void buildLighting(Node parentNode) {
        // Main sun (weak, low angle, pale white)
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(getSunDirection());
        sun.setColor(ColorRGBA.White.mult(0.35f));
        parentNode.addLight(sun);

        // Fill light (subtle, slightly blue-tinted)
        DirectionalLight fill = new DirectionalLight();
        fill.setDirection(new Vector3f(0.3f, -0.6f, 0.3f).normalizeLocal());
        fill.setColor(ColorRGBA.White.mult(0.15f).add(new ColorRGBA(0f, 0.05f, 0.1f, 0f)));
        parentNode.addLight(fill);

        // Ambient light (cold blue)
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(getAmbientColor());
        parentNode.addLight(ambient);
    }

    /**
     * Fix materials on environment geometry: ensure they render correctly with Frozen Depths colors.
     * Recolors stone and environmental models with icy blue tones.
     * Copied pattern from ForestBiome and DarkwoodStage for consistency.
     */
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
