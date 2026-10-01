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
import com.jme3.scene.shape.Cylinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * BossStage — shared skeleton for the fixed biome boss arenas. Every arena is
 * the same shape on purpose: a large, open, obstacle-free circular combat disc,
 * a decorative boundary ring (each biome's look from the subclass), and a
 * boundary wall further out so the fight can't wander away.
 */
public abstract class BossStage implements Stage {

    private static final float HALF_EXTENT = 55f;
    private static final float ARENA_RADIUS = 36f;
    /** where the enclosing decor ring starts (just outside the combat disc) */
    private static final float RING_START = ARENA_RADIUS + 3f;
    /** the boss appears offset from dead-center so the player has a beat to orient */
    private static final Vector3f BOSS_SPAWN_POS = new Vector3f(0f, 3f, -14f);

    protected Node stageNode;
    protected final List<RigidBodyControl> physicsObjects = new ArrayList<>();
    /** combat list the fight feeds summons into (wired by the stage manager) */
    private Consumer<List<EnemyController>> enemySink;

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node(getName());
        parentNode.attachChild(stageNode);

        buildArenaFloor(assetManager, bulletAppState);
        buildBoundaryWalls(bulletAppState);
        buildArenaDecor(assetManager, bulletAppState);
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
        BossSpec spec = getBossSpec();
        // flying bosses spawn already airborne so nothing has to "rise" at the start
        float spawnY = spec.hoverHeight > 0f ? spec.hoverHeight + 2f : BOSS_SPAWN_POS.y;
        EnemyController boss = new EnemyController(
                assetManager, stageNode, bulletAppState,
                new Vector3f(BOSS_SPAWN_POS.x, spawnY, BOSS_SPAWN_POS.z),
                9, loopCount, getBossScale(), true, getBossModelPath(), spec);
        if (spec.summon) {
            boss.setSummoner(count -> spawnBossSummons(assetManager, stageNode,
                    bulletAppState, loopCount, count));
        }
        enemies.add(boss);
        return enemies;
    }

    /** Combat list hook the stage manager registers so summons join the active fight. */
    public void setEnemySpawnSink(Consumer<List<EnemyController>> sink) {
        this.enemySink = sink;
    }

    /**
     * Spawns biome minions around the arena, pushes them onto the fight (the sink
     * tracks combat) and returns them so the boss can count its live summons.
     */
    protected List<EnemyController> spawnBossSummons(AssetManager assetManager, Node parentNode,
                                                     BulletAppState bulletAppState,
                                                     int loopCount, int count) {
        List<EnemyController> minions = new ArrayList<>();
        if (count <= 0 || enemySink == null) return minions;
        BossSpec spec = getBossSpec();
        Random rand = new Random();
        for (int i = 0; i < count; i++) {
            float angle = rand.nextFloat() * FastMath.TWO_PI;
            float radius = spec.hoverHeight > 0f ? 8f : 10f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            String modelPath = spec.summonModels.length > 0
                    ? spec.summonModels[rand.nextInt(spec.summonModels.length)] : null;
            EnemyController minion = new EnemyController(
                    assetManager, parentNode, bulletAppState,
                    new Vector3f(x, 5f, z), spec.summonTier, loopCount, 1.1f, false, modelPath);
            minions.add(minion);
        }
        enemySink.accept(minions);
        return minions;
    }

    /** Every boss gets a default kit; individual stages override to tune theirs. */
    protected BossSpec getBossSpec() {
        return new BossSpec();
    }

    /**
     * Character model this biome's boss is rendered with. Returning null falls
     * back to the placeholder capsule (not abstract so the shared arena still
     * compiles if a subclass forgets to override).
     */
    protected String getBossModelPath() {
        return null;
    }

    @Override
    public Vector3f getPlayerSpawnPoint() {
        return new Vector3f(0f, 2f, 0f);
    }

    private void buildArenaFloor(AssetManager assetManager, BulletAppState bulletAppState) {
        Box groundBox = new Box(HALF_EXTENT, 0.5f, HALF_EXTENT);
        Geometry ground = new Geometry(getName() + "Ground", groundBox);
        Material groundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        groundMat.setColor("Color", getGroundColor());
        ground.setMaterial(groundMat);
        ground.setLocalTranslation(0, -0.5f, 0);
        stageNode.attachChild(ground);

        // thin disc laid on the ground so the open arena visibly reads as a circle
        // (jME requires >= 2 axis samples for cylinders, hence the 2)
        Cylinder disc = new Cylinder(2, 48, ARENA_RADIUS, 0.08f, true);
        Geometry arenaDisc = new Geometry(getName() + "ArenaDisc", disc);
        Material discMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        discMat.setColor("Color", getArenaColor());
        discMat.getAdditionalRenderState().setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);
        arenaDisc.setMaterial(discMat);
        arenaDisc.rotate(FastMath.HALF_PI, 0f, 0f);
        arenaDisc.setLocalTranslation(0f, 0.05f, 0f);
        stageNode.attachChild(arenaDisc);

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

    protected float getRingStart() {
        return RING_START;
    }

    protected float getRingEnd() {
        return HALF_EXTENT - 2f;
    }

    protected abstract void buildArenaDecor(AssetManager assetManager, BulletAppState bulletAppState);

    protected float getBossScale() {
        return 1f;
    }

    protected abstract ColorRGBA getGroundColor();

    protected abstract ColorRGBA getArenaColor();

    /**
     * The boss-fight kit an arena feeds its EnemyController. Fields are flat so a
     * stage can flip on the attacks it wants, tune timings, and leave the rest at
     * sensible defaults. Per-phase tuning arrays are indexed by phase - 1:
     * phaseMul -> base attack cadence (lower = swings faster), moveMul -> approach
     * speed, smashMul/chargeMul/aoeMul -> those attack cooldowns (lower = sooner),
     * radiusMul -> smash/AoE size (wider at higher phases).
     */
    public static class BossSpec {
        public float moveSpeed = 3.2f;
        public float attackCooldown = 2.2f;
        public float meleeRange = 3f;
        public float detectionRange = 42f;
        /** > 0 makes the boss airborne, hovering at this height. */
        public float hoverHeight = 0f;
        /** movement/charge relic edge clamp; keep inside the combat disc */
        public float arenaRadius = 34f;
        /** health fractions that flip the boss into its later phases */
        public float[] thresholds = {0.7f, 0.35f};
        /** each landed hit chills the player this many seconds (Frost Giant) */
        public float slowOnHit = 0f;

        public boolean smash = false;
        public float smashWindup = 0.8f;
        public float smashCooldown = 6f;
        public float smashRadius = 7f;
        public float smashDamage = 18f;
        public float smashRecovery = 0.7f;

        public boolean charge = false;
        public float chargeCooldown = 9f;
        public float chargeSpeed = 16f;
        /** minimum distance before the boss commits to a charge */
        public float chargeRange = 7f;
        /** brief rooted aim before the lunge fires, so the charge reads as telegraphed */
        public float chargeWindup = 0.45f;
        public float chargeDuration = 0.7f;
        public float chargeHitRadius = 3.2f;
        public float chargeDamage = 16f;
        public float chargeRecovery = 0.8f;

        public boolean aoe = false;
        public float aoeWindup = 1.0f;
        public float aoeCooldown = 12f;
        public float aoeRadius = 9f;
        public float aoeDamage = 14f;
        public float aoeRecovery = 1.1f;

        public boolean summon = false;
        public float summonInterval = 22f;
        public int summonCount = 2;
        public int summonCap = 3;
        public int summonTier = 2;
        public String[] summonModels = {};

        /** pause after any single-hit attack so the wind-up/impact reads clearly */
        public float recovery = 0.5f;

        public float[] attackMul = {1f, 0.72f, 0.5f};
        public float[] moveMul = {1f, 1.12f, 1.3f};
        public float[] smashMul = {1f, 0.75f, 0.55f};
        public float[] chargeMul = {1f, 0.75f, 0.5f};
        public float[] aoeMul = {1f, 0.75f, 0.6f};
        public float[] radiusMul = {1f, 1.15f, 1.3f};
    }
}