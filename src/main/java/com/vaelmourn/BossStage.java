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
        EnemyController boss = new EnemyController(
                assetManager, stageNode, bulletAppState,
                BOSS_SPAWN_POS, 9, loopCount, getBossScale(), true, getBossModelPath());
        enemies.add(boss);
        return enemies;
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
}