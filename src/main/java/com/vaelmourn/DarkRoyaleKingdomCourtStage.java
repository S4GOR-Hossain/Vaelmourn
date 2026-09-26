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

/**
 * Dark Royale Kingdom Court — Stage 5 biome (last biome, guarded by the king).
 * Unlike the earlier biomes it only has 3 regular stages (variants 1-3) before
 * the Fallen King boss arena. Dark stone chambers ringed with tall pillars and
 * overturned court ornaments.
 */
public class DarkRoyaleKingdomCourtStage implements Stage {

    private static final float HALF_EXTENT = 60f;

    private static final String[] STONE_MODELS = {
            "Models/Environment/Forest/stone_tallA.glb",
            "Models/Environment/Forest/stone_tallB.glb",
            "Models/Environment/Forest/stone_tallC.glb",
            "Models/Environment/Forest/stone_tallD.glb",
            "Models/Environment/Forest/stone_largeA.glb",
            "Models/Environment/Forest/stone_largeB.glb",
            "Models/Environment/Forest/stone_largeC.glb"
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

        // the court gets mean fast — every stage hosts tier-3 elites at a heavy
        // scale multiplier, more of them per stage and per loop
        int enemyCount = 8 + variant + (loopCount / 2);
        float scale = 1.75f + variant * 0.35f;
        Random rand = new Random(88 + variant * 7 + loopCount);

        for (int i = 0; i < enemyCount; i++) {
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = 15f + rand.nextFloat() * 12f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            EnemyController enemy = new EnemyController(
                assetManager, stageNode, bulletAppState,
                new Vector3f(x, 5f, z), 3, loopCount, scale
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
        return new ColorRGBA(0.12f, 0.12f, 0.2f, 1f); // smothering dark royal night
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.4f, 0.38f, 0.55f, 1f).mult(0.6f); // hushed violet torchlight
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
        mat.setColor("Color", new ColorRGBA(0.2f, 0.2f, 0.26f, 1f)); // hewn midnight stone
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
        // variant re-seeds the ornament layout
        Random rand = new Random(102 + variant);

        // tall stone pillars mark the court—dense enough to feel royal, spaced
        // enough to keep the middle clear for fighting
        for (int i = 0; i < 24; i++) {
            float x = (rand.nextFloat() - 0.5f) * 100f;
            float z = (rand.nextFloat() - 0.5f) * 100f;
            if (new Vector3f(x, 0, z).length() < 14f) {
                continue;
            }

            String chosenModel = STONE_MODELS[rand.nextInt(STONE_MODELS.length)];
            Spatial stone = assetManager.loadModel(chosenModel);

            stone.setLocalTranslation(x, 0, z);
            stone.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
            float size = 5f + rand.nextFloat() * 4f;
            stone.setLocalScale(size);
            stageNode.attachChild(stone);

            BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(size * 0.35f, size * 0.4f, size * 0.35f));
            RigidBodyControl physics = new RigidBodyControl(shape, 0);
            physics.setPhysicsLocation(new Vector3f(x, size * 0.35f, z));
            bulletAppState.getPhysicsSpace().add(physics);
            physicsObjects.add(physics);
        }

        // a few wide, flat dais slabs to break up the floor (visual only)
        for (int i = 0; i < 8; i++) {
            float x = (rand.nextFloat() - 0.5f) * 90f;
            float z = (rand.nextFloat() - 0.5f) * 90f;
            if (new Vector3f(x, 0, z).length() < 16f) {
                continue;
            }

            Box slabBox = new Box(2.5f + rand.nextFloat() * 2.5f, 0.25f, 2.5f + rand.nextFloat() * 2.5f);
            Geometry slab = new Geometry("CourtSlab_" + i, slabBox);
            Material slabMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            slabMat.setColor("Color", new ColorRGBA(0.24f, 0.22f, 0.3f, 1f));
            slab.setMaterial(slabMat);
            slab.setLocalTranslation(x, 0.25f, z);
            stageNode.attachChild(slab);
        }
    }
}