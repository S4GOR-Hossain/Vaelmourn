package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shared helpers for building scenery with collision — the tree-ring and ice-spike
 * recipes used by the boss arenas (and the biome stages needing the same tricks).
 * Assets are loaded once into a template cache and clone()d per placement so the
 * packs never re-parse the same model dozens of times a second.
 */
public final class StageDecor {

    private static final Map<String, Spatial> MODEL_CACHE = new HashMap<>();

    private StageDecor() {
    }

    /** Load a model once; later placements clone() the cached template (shared mesh/material). */
    public static Spatial loadCached(AssetManager assetManager, String path) {
        return MODEL_CACHE.computeIfAbsent(path, assetManager::loadModel);
    }

    /**
     * How far a spatial's lowest point sits above its own origin, in the spatial's own
     * scaled/rotated space. Translating the spatial by +this value puts its base exactly
     * on its parent's origin.
     *
     * <p>Must be measured while the spatial is still DETACHED. {@code getWorldBound()}
     * folds in the whole parent chain's transform, so measuring after attaching to an
     * already-translated parent (the chest node sits at y=0.6) shifts the answer by that
     * parent's Y and the model ends up buried or floating.</p>
     */
    public static float baseLift(Spatial s) {
        s.updateModelBound();
        if (s.getWorldBound() instanceof BoundingBox bbox) {
            return bbox.getExtent(new Vector3f()).y - bbox.getCenter(new Vector3f()).y;
        }
        return 0f;
    }

    /**
     * Clone a cached pack asset, ground it, scatter-rotate it and attach it to the
     * parent. The lift logic measures how far above the origin the model's base is,
     * so both base-pivot (KayKit) and center-pivot (legacy Forest glb) packs sit flat.
     */
    public static Spatial placeFlat(Node parent, AssetManager assetManager, String path,
                                    float x, float z, float scale, Random rand) {
        Spatial s = loadCached(assetManager, path).clone();
        s.setLocalScale(scale);
        s.rotate(0f, rand.nextFloat() * FastMath.TWO_PI, 0f);
        s.setLocalTranslation(x, baseLift(s), z);
        parent.attachChild(s);
        return s;
    }

    /** Static box collider so the player (and foes) can't walk through a big prop. */
    public static void addBlocker(BulletAppState bulletAppState,
                                  List<RigidBodyControl> physicsOut,
                                  float x, float z, float halfX, float halfY, float halfZ) {
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(halfX, halfY, halfZ));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(x, halfY, z));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsOut.add(physics);
    }

    /** Height of a placed clone in world units (translation-independent), for collider sizing. */
    public static float boundHeight(Spatial s, float fallback) {
        s.updateModelBound();
        if (s.getWorldBound() instanceof BoundingBox bbox) {
            return bbox.getExtent(new Vector3f()).y;
        }
        return fallback;
    }

    public static void addTreeWithHitbox(AssetManager assetManager, Node parent,
                                         BulletAppState bulletAppState,
                                         List<RigidBodyControl> physicsOut,
                                         String[] models, Random rand, float x, float z) {
        String model = models[rand.nextInt(models.length)];
        Spatial tree = placeFlat(parent, assetManager, model, x, z,
                4.2f + rand.nextFloat() * 1.1f, rand);
        tree.updateModelBound();

        float trunkRadius = 0.9f;
        float collar = 3f;
        if (tree.getWorldBound() instanceof BoundingBox bbox) {
            Vector3f extent = bbox.getExtent(new Vector3f());
            trunkRadius = FastMath.clamp(Math.max(extent.x, extent.z) * 0.35f, 0.8f, 1.8f);
            collar = FastMath.clamp(Math.min(extent.y, 9f), 3f, 9f);
        }
        BoxCollisionShape trunk = new BoxCollisionShape(
                new Vector3f(trunkRadius, collar * 0.6f, trunkRadius));
        RigidBodyControl physics = new RigidBodyControl(trunk, 0);
        physics.setPhysicsLocation(new Vector3f(x, collar * 0.6f, z));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsOut.add(physics);
    }

    public static Mesh iceSpike(float baseRadius, float height) {
        int sides = 8;
        Mesh mesh = new Mesh();

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