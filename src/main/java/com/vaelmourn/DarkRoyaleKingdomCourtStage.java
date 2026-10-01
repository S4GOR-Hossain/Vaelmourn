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

/** Dark Royale Kingdom Court — Stage 5 biome (last biome), 3 variants then the Fallen King arena. */
public class DarkRoyaleKingdomCourtStage implements Stage {

    private static final float HALF_EXTENT = 60f;
    private static final String DUNGEON = "Models/Environment/KayKit_Dungeon_Pack_1.1_FREE/Assets/gltf/";

    private static final String[] ENEMY_MODELS = {
            "Models/Characters/enemy/kingdomcourt_enemy.gltf",
            "Models/Characters/enemy/kingdomcourt_enemy2.gltf"
    };

    private final int variant;
    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    public DarkRoyaleKingdomCourtStage() {
        this(1);
    }

    public DarkRoyaleKingdomCourtStage(int variant) {
        this.variant = Math.max(1, variant);
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("DarkRoyaleKingdomCourt" + variant);
        parentNode.attachChild(stageNode);

        buildGroundPlane(assetManager, bulletAppState);
        buildBoundaryWalls(bulletAppState);
        buildCourt(assetManager, bulletAppState);
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

        // the court gets mean fast: tier-3 elites at a heavy scale, more of them per stage/loop
        int enemyCount = 8 + variant + (loopCount / 2);
        float scale = 1.75f + variant * 0.35f;
        Random rand = new Random(88 + variant * 7 + loopCount);

        for (int i = 0; i < enemyCount; i++) {
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = 15f + rand.nextFloat() * 12f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            String modelPath = ENEMY_MODELS[(variant + i) % ENEMY_MODELS.length];
            EnemyController enemy = new EnemyController(
                assetManager, stageNode, bulletAppState,
                new Vector3f(x, 5f, z), 3, loopCount, scale, false, modelPath
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
        return new ColorRGBA(0.12f, 0.12f, 0.2f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.4f, 0.38f, 0.55f, 1f).mult(0.6f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.4f, -0.75f, -0.25f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    @Override
    public String getName() {
        // display kept short for the HUD: "Kingdom Court 1/2/3"
        return "Kingdom Court " + variant;
    }

    @Override
    public int getStageIndex() {
        return 8;
    }

    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Box groundBox = new Box(HALF_EXTENT, 0.5f, HALF_EXTENT);
        Geometry ground = new Geometry("KingdomCourtGround", groundBox);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.2f, 0.2f, 0.26f, 1f));
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

    private void buildCourt(AssetManager assetManager, BulletAppState bulletAppState) {
        // variant re-seeds the ornament layout; each stage walks one step deeper
        // into the fallen castle — gate approach, courtyard, then the inner hall.
        Random rand = new Random(102 + variant);

        if (variant == 1) {
            buildExteriorGate(assetManager, bulletAppState, rand);
            scatterRuins(assetManager, bulletAppState, rand, 12);
        } else if (variant == 2) {
            buildCourtyardRing(assetManager, bulletAppState, rand);
            scatterRuins(assetManager, bulletAppState, rand, 8);
        } else {
            buildInnerHall(assetManager, bulletAppState, rand);
            scatterRuins(assetManager, bulletAppState, rand, 6);
        }
    }

    /** Stage 1 of 3: a monumental ruined gate flanking the portal, towers and banners. */
    private void buildExteriorGate(AssetManager am, BulletAppState bulletAppState, Random rand) {
        placePiece(DUNGEON + "wall_archedwindow_gated.gltf", am, bulletAppState, rand, -7f, 31f, 1.7f, true);
        placePiece(DUNGEON + "wall_archedwindow_gated.gltf", am, bulletAppState, rand, 7f, 31f, 1.7f, true);
        placePiece(DUNGEON + "pillar_decorated.gltf", am, bulletAppState, rand, -16f, 33f, 1.6f, true);
        placePiece(DUNGEON + "pillar_decorated.gltf", am, bulletAppState, rand, 16f, 33f, 1.6f, true);
        placePiece(DUNGEON + "wall_doorway.gltf", am, bulletAppState, rand, 0f, -32f, 1.6f, true);
        placePiece(DUNGEON + "torch_mounted.gltf", am, bulletAppState, rand, -9.5f, 31.5f, 1.2f, false);
        placePiece(DUNGEON + "torch_mounted.gltf", am, bulletAppState, rand, 9.5f, 31.5f, 1.2f, false);
        placePiece(DUNGEON + "banner_triple_red.gltf", am, bulletAppState, rand, -3.5f, 31.5f, 1.5f, false);
        placePiece(DUNGEON + "banner_shield_blue.gltf", am, bulletAppState, rand, 3.5f, 31.5f, 1.5f, false);
    }

    /** Stage 2 of 3: a ceremonial colonnade ring with banners and war stores. */
    private void buildCourtyardRing(AssetManager am, BulletAppState bulletAppState, Random rand) {
        int pillars = 16;
        for (int i = 0; i < pillars; i++) {
            float angle = (i / (float) pillars) * FastMath.TWO_PI;
            float x = FastMath.cos(angle) * 34f;
            float z = FastMath.sin(angle) * 34f;
            placePiece(DUNGEON + "pillar_decorated.gltf", am, bulletAppState, rand, x, z, 1.7f, true);
            if (i % 2 == 0) {
                float bx = FastMath.cos(angle + 0.15f) * 31f;
                float bz = FastMath.sin(angle + 0.15f) * 31f;
                String banner = rand.nextBoolean() ? DUNGEON + "banner_red.gltf" : DUNGEON + "banner_white.gltf";
                placePiece(banner, am, bulletAppState, rand, bx, bz, 1.4f, false);
            }
        }
        for (int i = 0; i < 4; i++) {
            float angle = i * FastMath.HALF_PI + FastMath.QUARTER_PI;
            float x = FastMath.cos(angle) * 26f;
            float z = FastMath.sin(angle) * 26f;
            placePiece(DUNGEON + "crates_stacked.gltf", am, bulletAppState, rand, x, z, 1.5f, false);
            placePiece(DUNGEON + "barrel_large.gltf", am, bulletAppState, rand, x + 3f, z, 1.3f, false);
        }
    }

    /** Stage 3 of 3: the inner hall — torch-lit columns and garrison stores. */
    private void buildInnerHall(AssetManager am, BulletAppState bulletAppState, Random rand) {
        int columns = 22;
        for (int i = 0; i < columns; i++) {
            float angle = (i / (float) columns) * FastMath.TWO_PI + 0.1f;
            float x = FastMath.cos(angle) * 30f;
            float z = FastMath.sin(angle) * 30f;
            placePiece(DUNGEON + "column.gltf", am, bulletAppState, rand, x, z, 1.8f, true);
            if (i % 3 == 0) {
                placePiece(DUNGEON + "torch_mounted.gltf", am, bulletAppState, rand, x - 1.5f, z, 1.2f, false);
            }
        }
        for (int i = 0; i < 4; i++) {
            float p = (float) (34 + i * 2);
            placePiece(DUNGEON + "table_small.gltf", am, bulletAppState, rand, p, -p, 1.3f, false);
            placePiece(DUNGEON + "box_stacked.gltf", am, bulletAppState, rand, -p, p, 1.5f, false);
            placePiece(DUNGEON + "shelf_small_candles.gltf", am, bulletAppState, rand, -p, -p, 1.3f, false);
            placePiece(DUNGEON + "barrel_small_stack.gltf", am, bulletAppState, rand, p, p, 1.3f, false);
        }
    }

    /** Broken/doorway wall segments scattered as half-standing ruins. */
    private void scatterRuins(AssetManager am, BulletAppState bulletAppState, Random rand, int count) {
        String[] walls = {
                DUNGEON + "wall_broken.gltf",
                DUNGEON + "wall_cracked.gltf",
                DUNGEON + "wall_arched.gltf",
                DUNGEON + "wall.gltf"
        };
        for (int i = 0; i < count; i++) {
            float angle = rand.nextFloat() * FastMath.TWO_PI;
            float radius = 34f + rand.nextFloat() * 14f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            if (blocksPortalLane(x, z)) continue;
            placePiece(walls[rand.nextInt(walls.length)], am, bulletAppState, rand, x, z,
                    1.5f + rand.nextFloat() * 0.8f, true);
        }
    }

    private static boolean blocksPortalLane(float x, float z) {
        return Math.abs(x) < 7f && z > -4f && z < 34f;
    }

    private void placePiece(String path, AssetManager am, BulletAppState bulletAppState,
                            Random rand, float x, float z, float scale, boolean block) {
        Spatial s = StageDecor.placeFlat(stageNode, am, path, x, z, scale, rand);
        if (block) {
            s.updateModelBound();
            float hw = 1.4f;
            float hh = 2.5f;
            if (s.getWorldBound() instanceof com.jme3.bounding.BoundingBox bbox) {
                Vector3f ext = new Vector3f();
                bbox.getExtent(ext);
                hw = FastMath.clamp(Math.max(ext.x, ext.z) * 0.7f, 1f, 3f);
                hh = FastMath.clamp(ext.y, 1.5f, 7f);
            }
            StageDecor.addBlocker(bulletAppState, physicsObjects, x, z, hw, hh / 2f, hw);
        }
    }
}