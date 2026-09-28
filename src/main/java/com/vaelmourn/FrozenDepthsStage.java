package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.util.BufferUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** FrozenDepthsStage — Stage 3: icy tundra, tier-3 enemies; variant 3 applies the slow-on-hit modifier. */
public class FrozenDepthsStage implements Stage {

    private static final float HALF_EXTENT = 50f;
    private static final float SLOW_ON_HIT_SECONDS = 2f; // Frozen 3 modifier duration
    private static final String[] ENEMY_MODELS = {
            "Models/Characters/enemy/frozendepths_enemy.gltf",
            "Models/Characters/enemy/frozendepths_enemy2.gltf",
            "Models/Characters/enemy/frozendepths_enemy3.gltf"
    };

    private final int variant;
    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    public FrozenDepthsStage() {
        this(1);
    }

    public FrozenDepthsStage(int variant) {
        this.variant = Math.max(1, variant);
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("FrozenDepths" + variant);
        parentNode.attachChild(stageNode);

        buildGroundPlane(assetManager, bulletAppState);
        buildBoundaryWalls(bulletAppState);
        buildDecoration(assetManager, bulletAppState);
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

        boolean slows = variant == 3;
        int enemyCount = 6 + variant + (loopCount / 2);
        float scale = 1f + (variant - 1) * 0.4f;
        Random rand = new Random(44 + variant * 7 + loopCount);

        for (int i = 0; i < enemyCount; i++) {
            float angle = (i / (float) enemyCount) * FastMath.TWO_PI;
            float radius = 12f + rand.nextFloat() * 12f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            String modelPath = ENEMY_MODELS[(variant + i) % ENEMY_MODELS.length];
            EnemyController enemy = new EnemyController(
                assetManager, stageNode, bulletAppState,
                new Vector3f(x, 5f, z), 3, loopCount, scale, false, modelPath
            );
            // Frozen 3: only this stage chills the player, so the modifier never leaks elsewhere.
            if (slows) {
                enemy.setSlowOnHit(SLOW_ON_HIT_SECONDS);
            }
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
        return new ColorRGBA(0.7f, 0.8f, 0.92f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.5f, 0.6f, 0.8f, 1f).mult(0.7f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.4f, -0.6f, -0.5f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    @Override
    public String getName() {
        return "Frozen Depths " + variant;
    }

    @Override
    public int getStageIndex() {
        return 3;
    }

    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Box groundBox = new Box(HALF_EXTENT, 0.5f, HALF_EXTENT);
        Geometry ground = new Geometry("FrozenDepthsGround", groundBox);
        Material groundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        groundMat.setColor("Color", new ColorRGBA(0.75f, 0.85f, 0.95f, 1f));
        ground.setMaterial(groundMat);
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

    private void buildDecoration(AssetManager assetManager, BulletAppState bulletAppState) {
        // variant re-seeds layout so each pass through the tundra shifts slightly
        Random rand = new Random(101 + variant);

        for (int i = 0; i < 10; i++) {
            float x = (rand.nextFloat() - 0.5f) * 80f;
            float z = (rand.nextFloat() - 0.5f) * 80f;

            float height = 2f + rand.nextFloat() * 6f;
            float base = 0.3f + rand.nextFloat() * 0.7f;

            Mesh spikeMesh = createIceSpikeMesh(base, height);
            Geometry spike = new Geometry("IceSpike_" + i, spikeMesh);
            Material spikeMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            spikeMat.setColor("Color", new ColorRGBA(0.6f, 0.75f, 0.9f, 0.85f));
            spike.setMaterial(spikeMat);
            // The cone's base ring sits at mesh-local y=0, so anchoring at y=0 sinks the spike.
            spike.setLocalTranslation(x, 0f, z);
            spike.rotate((rand.nextFloat() - 0.5f) * 0.6f,
                         rand.nextFloat() * FastMath.TWO_PI,
                         (rand.nextFloat() - 0.5f) * 0.6f);
            stageNode.attachChild(spike);

            float spikeRadius = base * 1.4f;
            CapsuleCollisionShape shape = new CapsuleCollisionShape(spikeRadius, height);
            RigidBodyControl physics = new RigidBodyControl(shape, 0);
            float centerY = (height + 2f * spikeRadius) / 2f;
            physics.setPhysicsLocation(new Vector3f(x, centerY, z));
            bulletAppState.getPhysicsSpace().add(physics);
            physicsObjects.add(physics);
        }

        for (int i = 0; i < 15; i++) {
            float x = (rand.nextFloat() - 0.5f) * 85f;
            float z = (rand.nextFloat() - 0.5f) * 85f;

            Box moundBox = new Box(1f + rand.nextFloat() * 1.5f, 0.3f, 1f + rand.nextFloat() * 1.5f);
            Geometry mound = new Geometry("SnowMound_" + i, moundBox);
            Material moundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            moundMat.setColor("Color", new ColorRGBA(0.9f, 0.95f, 1f, 1f));
            mound.setMaterial(moundMat);
            mound.setLocalTranslation(x, 0.3f, z);
            stageNode.attachChild(mound);
        }
    }

    /** Pointy cone mesh: a base ring plus a single apex vertex, flat-shaded. */
    private Mesh createIceSpikeMesh(float baseRadius, float height) {
        int sides = 8;
        Mesh mesh = new Mesh();

        // Vertex layout: 0 = apex, 1 = base center, 2..2+sides-1 = the base ring.
        int vertCount = sides + 2;
        Vector3f[] positions = new Vector3f[vertCount];
        Vector3f[] normals = new Vector3f[vertCount];
        positions[0] = new Vector3f(0f, height, 0f);
        positions[1] = new Vector3f(0f, 0f, 0f);
        normals[0] = new Vector3f(0f, 1f, 0f);
        normals[1] = new Vector3f(0f, -1f, 0f);
        for (int i = 0; i < sides; i++) {
            float a = (i / (float) sides) * FastMath.TWO_PI;
            float nx = FastMath.cos(a);
            float nz = FastMath.sin(a);
            positions[i + 2] = new Vector3f(nx * baseRadius, 0f, nz * baseRadius);
            normals[i + 2] = new Vector3f(nx, 0f, nz);
        }

        // Indexed triangles: sides (apex, ring[i], ring[i+1]) plus the base cap.
        int[] indices = new int[sides * 6];
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            indices[i * 6 + 0] = 0;
            indices[i * 6 + 1] = i + 2;
            indices[i * 6 + 2] = next + 2;
            indices[i * 6 + 3] = 1;
            indices[i * 6 + 4] = next + 2;
            indices[i * 6 + 5] = i + 2;
        }

        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        mesh.updateCounts();
        return mesh;
    }
}
