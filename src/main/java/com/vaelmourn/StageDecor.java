package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
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
 * Shared helpers for building scenery with collision â€” the tree-ring and ice-spike
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
        // assumes the prop rests on the ground, so its centre is half a height up
        addBlocker(bulletAppState, physicsOut, new Vector3f(x, halfY, z),
                halfX, halfY, halfZ);
    }

    /** Static box collider centred at an explicit world point. */
    public static void addBlocker(BulletAppState bulletAppState,
                                  List<RigidBodyControl> physicsOut,
                                  Vector3f centre, float halfX, float halfY, float halfZ) {
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(halfX, halfY, halfZ));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(centre.clone());
        bulletAppState.getPhysicsSpace().add(physics);
        physicsOut.add(physics);
    }

    // ---- reusable auto-sized collision ---------------------------------------
    // Every stage used to hand-roll its own "measure the bbox, clamp it, add a
    // blocker" loop, and roughly half the prop sites skipped it entirely â€” which is
    // why rocks, crystals and barrels could be walked straight through. These two
    // methods are the single place that decision now lives: measure the placed
    // model's real bounding box and wrap a box around its footprint, shrunk so the
    // collider never becomes a wall wider than the thing you can see, and seated on
    // the model's own base so nothing floats or gets buried.

    /** How much of a prop's horizontal footprint the collider covers. Below 1 so a
     *  canopy or overhang stays walkable instead of becoming an invisible wall. */
    private static final float XZ_SHRINK = 0.72f;
    /** Vertical coverage. The full height is used so a player cannot walk through the
     *  top half of a boulder, but it is capped so tall scenery is not a giant ramp. */
    private static final float Y_SHRINK = 0.9f;
    /** Smallest collider half-extent: keeps pebbles and gems from having a degenerate
     *  box the player can still slip inside. */
    private static final float MIN_HALF = 0.3f;
    /** Largest collider half-extent: a 40-unit wall prop must not become a 20-unit
     *  blocker that walls off half the level. */
    private static final float MAX_HALF = 6f;

    /**
     * Stage boundary wall: static collision only, with no visual geometry.
     *
     * <p>Deliberately invisible. A visible border reads as a hard box around the
     * playable area and breaks the scene, and the levels are already framed by
     * authored scenery (treelines, cliff walls, palace facades) that closes them off
     * visually. Containment still needs the collider or the player walks off the map.
     * Sizes are the only thing that matters here -- keep the walls at the outer edge
     * and out of the play space so they never read as a stray hitbox.</p>
     *
     * @param physicsOut tracked for teardown, or null when the caller does not
     *                   maintain a list (as in the home forest, which is torn down
     *                   with its whole node)
     */
    public static void addBoundaryWall(BulletAppState bulletAppState,
                                      List<RigidBodyControl> physicsOut,
                                      Vector3f center,
                                      Vector3f halfExtents) {
        BoxCollisionShape shape = new BoxCollisionShape(halfExtents);
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(center);
        bulletAppState.getPhysicsSpace().add(physics);
        if (physicsOut != null) {
            physicsOut.add(physics);
        }
    }

    /** Fraction of a tree's canopy width that stays solid. Keeps the stem, drops the
     *  branches, so walking under a tree does not collide with its silhouette. */
    private static final float TRUNK_WIDTH_OF_CANOPY = 0.18f;

    /** Fraction of a tree's height that stays solid: the lower stem only. */
    private static final float TRUNK_HEIGHT_OF_MODEL = 0.40f;

    /**
     * Half-width of the solid part of a placed tree's trunk.
     *
     * <p>Deliberately a small fraction of the canopy: {@code getExtent} reports the
     * widest axis of the whole model, so sizing a collider on it produces a box as
     * broad as the canopy. The player then collides with invisible geometry while
     * walking through what looks like open space under the branches, which reads as
     * random slowing and snagging around trees.</p>
     */
    public static float trunkHalfWidth(Spatial tree, float minHalf, float maxHalf) {
        if (tree == null) return minHalf;
        tree.updateModelBound();
        if (!(tree.getWorldBound() instanceof BoundingBox bbox)) return minHalf;
        Vector3f extent = bbox.getExtent(new Vector3f());
        float canopyHalf = Math.max(extent.x, extent.z);
        if (canopyHalf <= 0f) return minHalf;
        return FastMath.clamp(canopyHalf * TRUNK_WIDTH_OF_CANOPY, minHalf, maxHalf);
    }

    /**
     * Full height of a tree's solid trunk, measured up from the ground. Only the
     * lowest stem is solid so the canopy stays walkable underneath.
     */
    public static float trunkHeight(Spatial tree, float minHeight, float maxHeight) {
        if (tree == null) return minHeight;
        tree.updateModelBound();
        if (!(tree.getWorldBound() instanceof BoundingBox bbox)) return minHeight;
        float halfHeight = bbox.getExtent(new Vector3f()).y;
        if (halfHeight <= 0f) return minHeight;
        return FastMath.clamp(halfHeight * TRUNK_HEIGHT_OF_MODEL, minHeight, maxHeight);
    }

    /**
     * Gives an already-placed model a box collider sized from its own bounding box.
     *
     * <p>Sizes on the world bound, so it accounts for the scale and random rotation
     * {@link #placeFlat} just applied. A box is deliberately used rather than a mesh:
     * these are low-poly props and the brief does not need exact mesh collision.</p>
     */
    public static void addBlockerFor(BulletAppState bulletAppState,
                                     List<RigidBodyControl> physicsOut,
                                     Spatial model) {
        // updateModelBound() is mandatory before reading the world bound: a spatial
        // that has never been bound-updated has no RF_BOUND refresh flag, so
        // getWorldBound() returns stale/null and the measurement silently fails.
        model.updateModelBound();
        if (!(model.getWorldBound() instanceof BoundingBox bbox)) return;

        Vector3f extent = bbox.getExtent(new Vector3f());
        if (extent.x <= 0f || extent.y <= 0f || extent.z <= 0f) return;

        float halfX = FastMath.clamp(extent.x * XZ_SHRINK, MIN_HALF, MAX_HALF);
        float halfY = FastMath.clamp(extent.y * Y_SHRINK * 0.5f, MIN_HALF, MAX_HALF);
        float halfZ = FastMath.clamp(extent.z * XZ_SHRINK, MIN_HALF, MAX_HALF);

        // seat the box on the model's own lowest point so it is grounded exactly
        // where the art is, and keep it centred horizontally on the model
        Vector3f centre = new Vector3f(
                bbox.getCenter(new Vector3f()).x,
                bbox.getMin(new Vector3f()).y + halfY,
                bbox.getCenter(new Vector3f()).z);
        addBlocker(bulletAppState, physicsOut, centre, halfX, halfY, halfZ);
    }

    /**
     * {@link #placeFlat} plus {@link #addBlockerFor} in one call. This is what every
     * solid scenery placement should use, so a new prop cannot be added without
     * collision just by forgetting a second line.
     */
    public static Spatial placeSolid(Node parent, AssetManager assetManager,
                                     BulletAppState bulletAppState,
                                     List<RigidBodyControl> physicsOut,
                                     String path, float x, float z, float scale,
                                     Random rand) {
        Spatial placed = placeFlat(parent, assetManager, path, x, z, scale, rand);
        addBlockerFor(bulletAppState, physicsOut, placed);
        return placed;
    }

    /** Height of a placed clone in world units (translation-independent), for collider sizing. */
    public static float boundHeight(Spatial s, float fallback) {
        s.updateModelBound();
        if (s.getWorldBound() instanceof BoundingBox bbox) {
            return bbox.getExtent(new Vector3f()).y;
        }
        return fallback;
    }

    /**
     * Trunk-sized collider for a tree that has already been placed, seated on
     * {@code groundY}. Used instead of {@link #addBlockerFor} for anything with a
     * canopy, because that helper matches the whole silhouette and so boxes in the
     * branches the player can plainly see through.
     */
    public static void addTrunkBlocker(BulletAppState bulletAppState,
                                       List<RigidBodyControl> physicsOut,
                                       Spatial tree, float x, float groundY, float z,
                                       float minHalf, float maxHalf,
                                       float minHeight, float maxHeight) {
        float halfWidth = trunkHalfWidth(tree, minHalf, maxHalf);
        float height = trunkHeight(tree, minHeight, maxHeight);
        BoxCollisionShape trunk = new BoxCollisionShape(
                new Vector3f(halfWidth, height * 0.5f, halfWidth));
        RigidBodyControl physics = new RigidBodyControl(trunk, 0);
        physics.setPhysicsLocation(new Vector3f(x, groundY + height * 0.5f, z));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsOut.add(physics);
    }

    public static void addTreeWithHitbox(AssetManager assetManager, Node parent,
                                         BulletAppState bulletAppState,
                                         List<RigidBodyControl> physicsOut,
                                         String[] models, Random rand, float x, float z) {
        String model = models[rand.nextInt(models.length)];
        Spatial tree = placeFlat(parent, assetManager, model, x, z,
                4.2f + rand.nextFloat() * 1.1f, rand);
        // Trunk-only: this used to size off the canopy at 35% width and up to 9u tall.
        addTrunkBlocker(bulletAppState, physicsOut, tree, x, 0f, z,
                0.8f, 1.5f, 2.5f, 4f);
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
