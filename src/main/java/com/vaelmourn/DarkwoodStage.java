package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.util.BufferUtils;

import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Darkwood — stage 1, tier-1 forest. Four variants, each its own handcrafted
 * level: one large forest basin (a 280-unit-wide valley ringed by unclimbable
 * mountain chains), a baked forest sky dome, deliberately composed tree stands,
 * and authored encounter arenas with long open travel between them.
 *
 * Everything is authored — no random position generation anywhere.
 */
public class DarkwoodStage implements Stage {

    private static final float HALF_EXTENT = 140f;
    private static final String KAYKIT_GLTF =
            "Models/Environment/KayKit_Forest_Nature_Pack_1.0_FREE/"
                    + "KayKit_Forest_Nature_Pack_1.0_FREE/Assets/gltf/";
    // rotated per stage so each pass through the woods leads with a different grunt
    private static final String[] ENEMY_MODELS = {
            "Models/Characters/enemy/darkwood_enemy.glb",
            "Models/Characters/enemy/darkwood_enemy2.glb",
            "Models/Characters/enemy/darkwood_enemy3.gltf"
    };

    // ------------------------------------------------------------------ terrain
    // Elevation is a pure, authored function of (x, z). The forest floor is a few
    // very broad rolling forms; the mountain ring is written as concentric,
    // irregular ridge bands plus a set of named giant summits. The whole basin —
    // forest floor AND mountains — is ONE mesh with ONE static collider.
    private static final float TERRAIN_HALF = 190f;      // mesh spans [-190, 190], play basin is ~±140
    private static final float TERRAIN_STEP = 2f;        // grid cell size (world units)
    private static final int TERRAIN_SIZE = (int) (TERRAIN_HALF * 2f / TERRAIN_STEP); // 190 cells
    private static final int TERRAIN_SAMPLES = TERRAIN_SIZE + 1;
    private static final float MIN_ELEVATION = -4f;      // no pits deeper than the valley floor
    private static final float SKY_RADIUS = 560f;        // dome surrounding the entire basin

    // mountain ring geometry (radians polar angle th)
    private static final float MOUNT_FOOT_BASE = 168f;   // foothill baseline from world center
    private static final float MOUNT_FOOT_WAVE = 14f;    // angular wobble -> irregular, not square
    private static final float COLLAR_WIDTH = 30f;       // gentle green collar before the climb

    private static final String[] TREE_MODELS = {
            KAYKIT_GLTF + "Tree_1_A_Color1.gltf",
            KAYKIT_GLTF + "Tree_1_B_Color1.gltf",
            KAYKIT_GLTF + "Tree_1_C_Color1.gltf",
            KAYKIT_GLTF + "Tree_2_A_Color1.gltf",
            KAYKIT_GLTF + "Tree_2_B_Color1.gltf",
            KAYKIT_GLTF + "Tree_2_C_Color1.gltf",
            KAYKIT_GLTF + "Tree_2_D_Color1.gltf",
            KAYKIT_GLTF + "Tree_2_E_Color1.gltf",
            KAYKIT_GLTF + "Tree_3_A_Color1.gltf",
            KAYKIT_GLTF + "Tree_3_B_Color1.gltf",
            KAYKIT_GLTF + "Tree_3_C_Color1.gltf",
            KAYKIT_GLTF + "Tree_4_A_Color1.gltf",
            KAYKIT_GLTF + "Tree_4_B_Color1.gltf",
            KAYKIT_GLTF + "Tree_4_C_Color1.gltf"
    };
    private static final String[] ROCK_MODELS = {
            KAYKIT_GLTF + "Rock_1_B_Color1.gltf",
            KAYKIT_GLTF + "Rock_1_F_Color1.gltf",
            KAYKIT_GLTF + "Rock_1_O_Color1.gltf",
            KAYKIT_GLTF + "Rock_2_C_Color1.gltf",
            KAYKIT_GLTF + "Rock_3_B_Color1.gltf",
            KAYKIT_GLTF + "Rock_3_H_Color1.gltf"
    };
    private static final String[] BUSH_MODELS = {
            KAYKIT_GLTF + "Bush_1_A_Color1.gltf",
            KAYKIT_GLTF + "Bush_1_D_Color1.gltf",
            KAYKIT_GLTF + "Bush_2_B_Color1.gltf",
            KAYKIT_GLTF + "Bush_2_F_Color1.gltf",
            KAYKIT_GLTF + "Bush_3_A_Color1.gltf",
            KAYKIT_GLTF + "Bush_4_C_Color1.gltf"
    };
    private static final String[] GRASS_MODELS = {
            KAYKIT_GLTF + "Grass_1_A_Color1.gltf",
            KAYKIT_GLTF + "Grass_1_C_Color1.gltf",
            KAYKIT_GLTF + "Grass_2_A_Color1.gltf",
            KAYKIT_GLTF + "Grass_2_D_Color1.gltf"
    };

    // the loose ring framing the big open spawn vale — every variant shares it.
    // Wide gaps keep the mountains visible; the north arc stays open to the vale.
    private static final float[][] SPAWN_RING = {
            {26f, 14f}, {29f, -2f}, {27f, -18f},   // E arc
            {15f, -30f}, {0f, -33f}, {-16f, -30f}, // S arc
            {-30f, -18f}, {-32f, -2f}, {-28f, 14f},// W arc
            {-22f, -22f}, {22f, -22f}              // vale mouth pair
    };

    // ------------------------------------------------- authored enemy encounters
    // Each variant gets 4 groups: a 2-grunt vale-mouth pair plus three full
    // arenas (4-6 each). The first arena opens the run, the others gate the loop.
    private static final float[][] ENCOUNTER_A = {{20f, 92f}, {38f, 96f}, {30f, 110f}, {28f, 102f}};
    private static final float[][] ENCOUNTER_B = {{-105f, -60f}, {-72f, -52f}, {-85f, -90f}};
    private static final float[][] ENCOUNTER_C = {{44f, 42f}, {52f, 48f}, {46f, 54f}};
    // a small pair at the vale mouth so the stage never reads empty; the first
    // arena fight still opens the long run north into the E1 vale
    private static final float[][] ENCOUNTER_GUARD = {{-18f, 50f}, {18f, 50f}};

    // Darkwood 2 — the gullies: two long N/S arenas split by the spawn vale,
    // plus a raised west shelf by the ring.
    private static final float[][] ENCOUNTER_A2 = {{18f, 92f}, {-16f, 98f}, {10f, 108f}, {-6f, 102f}};
    private static final float[][] ENCOUNTER_B2 = {{-12f, -88f}, {14f, -94f}, {2f, -104f}, {-8f, -98f}};
    private static final float[][] ENCOUNTER_C2 = {{-82f, -12f}, {-88f, 4f}, {-96f, -6f}, {-90f, 10f}};
    private static final float[][] ENCOUNTER_GUARD2 = {{-16f, 48f}, {16f, 48f}};

    // Darkwood 3 — the ridgeline: a wooded E-W ridge, a north arena beyond it
    // and a broad south basin arena, with the raised east shelf last.
    private static final float[][] ENCOUNTER_A3 = {{14f, 102f}, {-16f, 106f}, {6f, 114f}, {-4f, 96f}};
    private static final float[][] ENCOUNTER_B3 = {{-22f, -80f}, {-34f, -76f}, {-12f, -92f}, {-26f, -98f}};
    private static final float[][] ENCOUNTER_C3 = {{96f, 14f}, {104f, 24f}, {94f, 30f}, {110f, 12f}};
    private static final float[][] ENCOUNTER_GUARD3 = {{-15f, 45f}, {15f, 45f}};

    // Darkwood 4 — the highlands: the biggest basin, with a NE arena, a deep SW
    // basin and a SE shelf.
    private static final float[][] ENCOUNTER_A4 = {{66f, 82f}, {80f, 88f}, {70f, 96f}, {56f, 90f}};
    private static final float[][] ENCOUNTER_B4 = {{-78f, -92f}, {-90f, -88f}, {-64f, -102f}, {-82f, -108f}};
    private static final float[][] ENCOUNTER_C4 = {{96f, -22f}, {104f, -16f}, {110f, -26f}, {94f, -34f}};
    private static final float[][] ENCOUNTER_GUARD4 = {{-18f, 42f}, {18f, 42f}};

    private final int variant;
    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    public DarkwoodStage() {
        this(1);
    }

    public DarkwoodStage(int variant) {
        this.variant = Math.max(1, variant);
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("Darkwood" + variant);
        parentNode.attachChild(stageNode);

        // Every variant is its own authored basin: terrain + collider ARE the
        // mountains, the dome wraps inside, the forest is composed by hand, and
        // an invisible seal ring closes the basin-to-mountain seam below.
        buildAuthored(assetManager, bulletAppState);
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
        return spawnEncounters(assetManager, parentNode, bulletAppState, loopCount);
    }

    /** Places the authored encounter groups on this variant's terrain; extras from
     *  looping runs fill the remaining arena spots so fights stay in the rings. */
    private List<EnemyController> spawnEncounters(AssetManager assetManager, Node parentNode,
                                                  BulletAppState bulletAppState, int loopCount) {
        List<EnemyController> enemies = new ArrayList<>();
        int enemyCount = Stage.loopedEnemyCount(10, loopCount); // boot: 2 vale guards + 4 + 4 in two arenas
        int tier = Math.min(3, 1 + (variant - 1) / 2);
        float scale = 1f + (variant - 1) * 0.35f;
        Random rand = new Random(7 + loopCount * 13);

        float[][][] groups = variantGroups(variant);
        int placed = 0;
        while (placed < enemyCount) {
            for (float[][] group : groups) {
                if (placed >= enemyCount) break;
                for (float[] spot : group) {
                    if (placed >= enemyCount) break;
                    float x = spot[0];
                    float z = spot[1];
                    float y = heightAt(x, z) + 4f; // spawn above, gravity settles them
                    String modelPath = ENEMY_MODELS[(placed + rand.nextInt(3)) % ENEMY_MODELS.length];
                    EnemyController enemy = new EnemyController(
                        assetManager, stageNode, bulletAppState,
                        new Vector3f(x, y, z), tier, loopCount, scale, false, modelPath
                    );
                    enemies.add(enemy);
                    placed++;
                }
            }
        }
        return enemies;
    }

    private float[][][] variantGroups(int variant) {
        return switch (variant) {
            case 2 -> new float[][][]{ENCOUNTER_GUARD2, ENCOUNTER_A2, ENCOUNTER_B2, ENCOUNTER_C2};
            case 3 -> new float[][][]{ENCOUNTER_GUARD3, ENCOUNTER_A3, ENCOUNTER_B3, ENCOUNTER_C3};
            case 4 -> new float[][][]{ENCOUNTER_GUARD4, ENCOUNTER_A4, ENCOUNTER_B4, ENCOUNTER_C4};
            default -> new float[][][]{ENCOUNTER_GUARD, ENCOUNTER_A, ENCOUNTER_B, ENCOUNTER_C};
        };
    }

    @Override
    public Vector3f getPlayerSpawnPoint() {
        return new Vector3f(0, 2f, 0);
    }

    @Override
    public ColorRGBA getSkyColor() {
        // warm haze that matches the baked forest sky dome on every variant
        return new ColorRGBA(0.80f, 0.78f, 0.76f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        // bright, soft afternoon light so the huge basin stays readable
        return new ColorRGBA(0.48f, 0.46f, 0.42f, 1f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.3f, -0.9f, -0.4f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    @Override
    public String getName() {
        return "Darkwood " + variant;
    }

    @Override
    public int getStageIndex() {
        return 1;
    }

    // ====================================================== the authored basin

    private void buildAuthored(AssetManager assetManager, BulletAppState bulletAppState) {
        // The terrain mesh and its collider ARE the mountains — one static body for
        // the entire world. There are no boundary walls in this version.
        buildTerrain(assetManager, bulletAppState);
        buildSky(assetManager);
        buildAuthoredForest(assetManager, bulletAppState);
        // invisible seal ring where the basin floor gives way to the steep mountain
        // face: the town renderer's collider can let a capsule wedge through those
        // near-vertical triangles, so this ring guarantees nothing falls through.
        sealMountainRing(bulletAppState);
        // failsafe far below the basin in case a physics glitch ever pushes the
        // player through the floor (the ring is unclimbable, so it should never trip)
        addSafetyFloor(bulletAppState);
    }

    /** Closes the basin-to-mountain seam with an invisible ring of static blockers
     *  just inside the collar. A character capsule rides up the steep face a couple
     *  of steps, hits the ring, and slides back into the basin instead of clipping
     *  through the terrain triangles (which is what made the gap look/sound broken). */
    private void sealMountainRing(BulletAppState bulletAppState) {
        float R = 152f;        // just inside the inner foothill band
        float halfTang = 17f;  // segment length along the ring
        float halfY = 40f;     // the wall spans y 0..80
        float halfRad = 5f;    // radial thickness, overlapping segments seal it
        int n = 32;
        for (int i = 0; i < n; i++) {
            float a = i * FastMath.TWO_PI / n;
            float x = FastMath.cos(a) * R;
            float z = FastMath.sin(a) * R;
            BoxCollisionShape box = new BoxCollisionShape(new Vector3f(halfTang, halfY, halfRad));
            RigidBodyControl blocker = new RigidBodyControl(box, 0f);
            // spin the long axis tangent to the circle
            blocker.setPhysicsRotation(new Quaternion().fromAngleAxis(-a - FastMath.HALF_PI, Vector3f.UNIT_Y));
            blocker.setPhysicsLocation(new Vector3f(x, halfY, z));
            bulletAppState.getPhysicsSpace().add(blocker);
            physicsObjects.add(blocker);
        }
    }

    /** Builds the visual ground mesh and a matching static collider from the same
     *  height field, so what you see is exactly what you land on. */
    private void buildTerrain(AssetManager assetManager, BulletAppState bulletAppState) {
        int n = TERRAIN_SAMPLES;
        int total = n * n;

        float[] heights = new float[total];
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                float x = ix * TERRAIN_STEP - TERRAIN_HALF;
                float z = iz * TERRAIN_STEP - TERRAIN_HALF;
                heights[iz * n + ix] = profile(variant, x, z);
            }
        }

        FloatBuffer pos = BufferUtils.createFloatBuffer(total * 3);
        FloatBuffer norm = BufferUtils.createFloatBuffer(total * 3);
        FloatBuffer color = BufferUtils.createFloatBuffer(total * 4);

        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                float x = ix * TERRAIN_STEP - TERRAIN_HALF;
                float z = iz * TERRAIN_STEP - TERRAIN_HALF;
                float h = heights[iz * n + ix];

                pos.put(x).put(h).put(z);

                float dhdx = (heights[iz * n + Math.min(ix + 1, n - 1)]
                        - heights[iz * n + Math.max(ix - 1, 0)]) / (2f * TERRAIN_STEP);
                float dhdz = (heights[Math.min(iz + 1, n - 1) * n + ix]
                        - heights[Math.max(iz - 1, 0) * n + ix]) / (2f * TERRAIN_STEP);
                Vector3f normal = new Vector3f(-dhdx, 1f, -dhdz).normalizeLocal();
                norm.put(normal.x).put(normal.y).put(normal.z);

                float[] rgb = terrainColor(x, z, h, FastMath.sqrt(dhdx * dhdx + dhdz * dhdz));
                float shade = FastMath.clamp(1f - h * 0.008f, 0.72f, 1f); // gentle haze, no washout
                color.put(rgb[0] * shade).put(rgb[1] * shade).put(rgb[2] * shade).put(1f);
            }
        }
        pos.flip();
        norm.flip();
        color.flip();

        IntBuffer idx = BufferUtils.createIntBuffer(TERRAIN_SIZE * TERRAIN_SIZE * 6);
        for (int iz = 0; iz < TERRAIN_SIZE; iz++) {
            for (int ix = 0; ix < TERRAIN_SIZE; ix++) {
                int i00 = iz * n + ix;
                int i01 = (iz + 1) * n + ix;
                int i10 = iz * n + ix + 1;
                int i11 = (iz + 1) * n + ix + 1;
                // up-facing winding (right-hand rule, +Y normal)
                idx.put(i00).put(i01).put(i11);
                idx.put(i00).put(i11).put(i10);
            }
        }
        idx.flip();

        Mesh terrain = new Mesh();
        terrain.setBuffer(VertexBuffer.Type.Position, 3, pos);
        terrain.setBuffer(VertexBuffer.Type.Normal, 3, norm);
        terrain.setBuffer(VertexBuffer.Type.Color, 4, color);
        terrain.setBuffer(VertexBuffer.Type.Index, 3, idx);
        terrain.updateBound();
        terrain.updateCounts();

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setBoolean("VertexColor", true);
        mat.setColor("Color", ColorRGBA.White);

        Geometry ground = new Geometry("DarkwoodGround", terrain);
        ground.setMaterial(mat);
        stageNode.attachChild(ground);

        MeshCollisionShape shape = new MeshCollisionShape(terrain);
        RigidBodyControl physics = new RigidBodyControl(shape, 0f);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    /** Green meadow floors, forested mid slopes, and rock-grey mountains — tinted
     *  by elevation and by how much the ground is trying to climb. The flat arenas
     *  stay pure grass; only genuinely steep faces turn to rock, so the rolling
     *  elevation never reads pale or powdery. */
    private float[] terrainColor(float x, float z, float h, float slope) {
        float[] grass = {0.18f, 0.31f, 0.13f};
        float[] midGreen = {0.26f, 0.34f, 0.16f};
        float[] foot = {0.29f, 0.29f, 0.20f};
        float[] rock = {0.42f, 0.41f, 0.37f};
        float[] highRock = {0.37f, 0.39f, 0.41f};

        float tH = FastMath.clamp((h + 4f) / 40f, 0f, 1f);
        float[] rgb = lerp(grass, midGreen, Math.min(1f, tH * 1.6f));
        rgb = lerp(rgb, foot, FastMath.clamp((tH - 0.55f) / 0.45f, 0f, 1f));
        // only steep ground turns to rock: gentle hills (gradient < 0.3) keep their
        // green, the collar up to the mountain base picks up a rocky tint, and the
        // near-vertical faces go fully rock at gradient ~1.2
        rgb = lerp(rgb, rock, FastMath.clamp((slope - 0.30f) / 0.9f, 0f, 1f));
        if (h > 50f) {
            rgb = lerp(rgb, highRock, FastMath.clamp((h - 50f) / 70f, 0f, 1f));
        }
        return rgb;
    }

    private float[] lerp(float[] a, float[] b, float t) {
        return new float[]{
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t
        };
    }

    private void addSafetyFloor(BulletAppState bulletAppState) {
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(210f, 2f, 210f));
        RigidBodyControl physics = new RigidBodyControl(shape, 0f);
        physics.setPhysicsLocation(new Vector3f(0f, -46f, 0f));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    // --------------------------------------------------------- the height field

    /** Kept for the stand-alone height probes: the Darkwood 1 profile. */
    static float groundHeight(float x, float z) {
        return profile(1, x, z);
    }

    /** This variant's profile, matching the terrain the build loop samples. */
    private float heightAt(float x, float z) {
        return profile(variant, x, z);
    }

    /** Sampled so the exit portal rests on this stage's terrain rather than a
     *  fixed height, which left it buried in the higher profiles. */
    @Override
public Vector3f getExitPortalGround() {
            return new Vector3f(0f, heightAt(0f, 25f), 25f);
        }

        /** Thrown bombs integrate their own gravity, so they need the same terrain
         *  height the mesh was built from or they sink into the rolling ground. */
        @Override
        public float groundHeightAt(float x, float z) {
            return heightAt(x, z);
        }

    /** Dispatches to the authored elevation profile for any variant. Each
     *  profile composes broad rolling forms, planted summits, flat arenas and
     *  the shared mountain ring. */
    static float profile(int variant, float x, float z) {
        return switch (variant) {
            case 2 -> profileV2(x, z);
            case 3 -> profileV3(x, z);
            case 4 -> profileV4(x, z);
            default -> profileV1(x, z);
        };
    }

    /** Darkwood 1 — the basin: forest floor over a long climb to a raised ridge,
     *  then the first valley (E1), the west basin (E2) and the raised east
     *  shelf (E3). */
    private static float profileV1(float x, float z) {
        float h = 0f;

        // --- a few broad, rolling forest-floor forms -------------------------
        h += gaussian(-60f, -160f, 90f, 70f, 10f, x, z);   // NW broad rise
        h += gaussian(120f, -60f, 95f, 80f, 12f, x, z);    // SE rise (frames the east shelf)
        h += gaussian(-120f, 60f, 70f, 90f, 9f, x, z);     // W bulge behind the west basin
        h += gaussian(60f, 170f, 200f, 55f, 14f, x, z);    // long N slope up toward the ring
        h += gaussian(0f, 105f, 90f, 40f, 9f, x, z);       // raised forest ridge (the climb)
        h += gaussian(48f, 40f, 60f, 45f, 7f, x, z);       // east shelf rise (E3 arena)
        h -= gaussian(30f, 85f, 70f, 32f, 10f, x, z);      // carves the central valley (E1)
        h -= gaussian(-95f, -80f, 55f, 32f, 9f, x, z);     // carves the west basin (E2)

        // four authored giant summits — extra-tall, layered silhouettes. Added
        // before the flatten zones so their wide tails never spoil the arenas.
        h += gaussian(-150f, 105f, 30f, 30f, 55f, x, z);  // summit NW
        h += gaussian(140f, -110f, 30f, 30f, 50f, x, z);  // summit SE
        h += gaussian(10f, 180f, 28f, 28f, 60f, x, z);    // summit N
        h += gaussian(-180f, -20f, 32f, 32f, 48f, x, z);  // summit W

        // --- meadows and shelves: real flat ground where people fight ---------
        h = flattenTo(h, 0f, 0f, 34f, 14f, 0f, x, z);    // darkwoodSpawnArea  (huge open spawn vale)
        h = flattenTo(h, 0f, 25f, 24f, 10f, 0f, x, z);   // darkwoodExitArea  (portal meadow)
        h = flattenTo(h, 30f, 85f, 30f, 12f, -4f, x, z); // darkwoodValley    (E1 arena)
        h = flattenTo(h, -95f, -75f, 50f, 12f, -3f, x, z); // darkwoodWestBasin (E2 arena, covers all three spots)
        h = flattenTo(h, 48f, 46f, 24f, 16f, 12f, x, z); // darkwoodEastShelf (E3 arena, raised)
        h = flattenTo(h, -15f, 130f, 22f, 10f, 12f, x, z); // darkwoodForestRidge crest (clear of the E1 vale)

        return mountainRing(h, x, z);
    }

    /** Darkwood 2 — the gullies: two long N/S arenas split by the spawn vale,
     *  with a raised west shelf (E3) against the ring. */
    private static float profileV2(float x, float z) {
        float h = 0f;
        h += gaussian(0f, -56f, 70f, 95f, 8f, x, z);     // S broad basin
        h += gaussian(-70f, 24f, 55f, 50f, 7f, x, z);    // W hump
        h += gaussian(64f, 28f, 60f, 52f, 8f, x, z);     // E hump
        h += gaussian(-30f, -120f, 90f, 60f, 12f, x, z); // SW rise
        h += gaussian(30f, 136f, 95f, 60f, 9f, x, z);    // N rise
        // planted summits for layered silhouettes
        h += gaussian(-165f, 95f, 30f, 30f, 54f, x, z);  // summit NW
        h += gaussian(120f, 155f, 28f, 28f, 52f, x, z);  // summit NE
        h += gaussian(35f, -175f, 30f, 30f, 58f, x, z);  // summit S
        h += gaussian(-60f, -185f, 30f, 30f, 50f, x, z); // summit SW
        // flat arenas
        h = flattenTo(h, 0f, 0f, 34f, 14f, 0f, x, z);      // spawn vale
        h = flattenTo(h, 0f, 25f, 22f, 10f, 0f, x, z);     // portal meadow
        h = flattenTo(h, 0f, 95f, 34f, 12f, -4f, x, z);    // E1 north gully
        h = flattenTo(h, 0f, -90f, 34f, 12f, -3f, x, z);   // E2 south gully
        h = flattenTo(h, -85f, 0f, 24f, 16f, 10f, x, z);   // E3 west shelf
        return mountainRing(h, x, z);
    }

    /** Darkwood 3 — the ridgeline: a wooded E-W ridge, the north arena (E1)
     *  beyond it and the broad south basin (E2), with the east shelf (E3). */
    private static float profileV3(float x, float z) {
        float h = 0f;
        h += gaussian(0f, 30f, 62f, 120f, 10f, x, z);  // E-W ridge spine
        h += gaussian(0f, -70f, 110f, 60f, 8f, x, z);  // S basin
        h += gaussian(0f, 120f, 90f, 50f, 9f, x, z);   // N valley
        h += gaussian(-100f, -30f, 70f, 50f, 6f, x, z);// W hump
        h += gaussian(100f, 55f, 60f, 45f, 7f, x, z);  // E shoulder
        h += gaussian(-40f, 172f, 30f, 30f, 55f, x, z);  // summit N
        h += gaussian(150f, -30f, 30f, 30f, 52f, x, z);  // summit E
        h += gaussian(-160f, 10f, 30f, 30f, 48f, x, z);  // summit W
        h += gaussian(90f, -140f, 30f, 30f, 50f, x, z);  // summit SE
        h = flattenTo(h, 0f, 0f, 34f, 14f, 0f, x, z);      // spawn vale
        h = flattenTo(h, 0f, 25f, 22f, 10f, 0f, x, z);     // portal meadow
        h = flattenTo(h, 0f, 105f, 34f, 12f, -4f, x, z);   // E1 north arena
        h = flattenTo(h, -20f, -80f, 36f, 12f, -3f, x, z); // E2 south arena
        h = flattenTo(h, 100f, 20f, 26f, 16f, 10f, x, z);  // E3 east shelf
        return mountainRing(h, x, z);
    }

    /** Darkwood 4 — the highlands: the biggest, broadest basin with a NE arena
     *  (E1), a deep SW basin (E2) and a SE shelf (E3). */
    private static float profileV4(float x, float z) {
        float h = 0f;
        h += gaussian(0f, 0f, 150f, 130f, 6f, x, z);   // broad highland
        h += gaussian(-60f, 60f, 80f, 60f, 8f, x, z);  // NW swell
        h += gaussian(60f, -80f, 85f, 60f, 8f, x, z);  // SE swell
        h += gaussian(0f, -140f, 130f, 70f, 10f, x, z);// S rise
        h += gaussian(0f, 140f, 110f, 60f, 12f, x, z); // N rise
        h += gaussian(-150f, -150f, 30f, 30f, 58f, x, z); // summit SW
        h += gaussian(150f, 140f, 30f, 30f, 62f, x, z);   // summit NE
        h += gaussian(-170f, 150f, 30f, 30f, 50f, x, z);  // summit NW
        h += gaussian(160f, -120f, 30f, 30f, 54f, x, z);  // summit SE
        h = flattenTo(h, 0f, 0f, 34f, 14f, 0f, x, z);      // spawn vale
        h = flattenTo(h, 0f, 25f, 22f, 10f, 0f, x, z);     // portal meadow
        h = flattenTo(h, 70f, 85f, 30f, 12f, -4f, x, z);   // E1 NE arena
        h = flattenTo(h, -75f, -95f, 36f, 12f, -3f, x, z); // E2 SW basin
        h = flattenTo(h, 100f, -20f, 26f, 16f, 10f, x, z); // E3 SE shelf
        return mountainRing(h, x, z);
    }

    /** The shared ringed mountain wall every variant sits inside: a rolling
     *  green collar, a near forest-rim band, a far peak band, and a blanket
     *  step that makes the mesh edge an unclimbable ridge on all sides. */
    private static float mountainRing(float h, float x, float z) {
        // --- the mountain ring -------------------------------------------------
        float r = FastMath.sqrt(x * x + z * z);
        float th = FastMath.atan2(z, x);

        // rolling green foothill collar: walkable, but it steepens past here
        float foot = innerFoot(th);
        float collarStart = foot - COLLAR_WIDTH;
        if (r > collarStart && r < foot) {
            h += 12f * smooth01((r - collarStart) / COLLAR_WIDTH);
        }

        // inner ridge band: 40-66 tall, the near "forest rim" silhouette
        float innerPeak = foot + 16f + 20f * (0.5f + 0.5f * FastMath.sin(5f * th + 1.1f));
        h += ringRise(r, foot, innerPeak, innerHeight(th));

        // outer peak band: 100-155 tall, the layered far wall behind the ridge
        float outerFoot = foot + 22f + 10f * FastMath.sin(2f * th + 0.9f);
        float outerPeak = outerFoot + 26f + 24f * (0.5f + 0.5f * FastMath.sin(4f * th + 2.3f));
        h += ringRise(r, outerFoot, outerPeak, outerHeight(th));

        // a blanket so the mesh edge stays a towering unclimbable wall everywhere
        float blunt = smooth01((r - 150f) / 45f);
        h = Math.max(h, blunt * 180f - 60f);

        return Math.max(h, MIN_ELEVATION);
    }

    /** Angular wobble makes the mountain wall follow an organic, non-square line. */
    private static float innerFoot(float th) {
        return MOUNT_FOOT_BASE + MOUNT_FOOT_WAVE * FastMath.sin(3f * th + 1.7f);
    }

    private static float innerHeight(float th) {
        return 40f + 26f * (0.5f + 0.5f * FastMath.sin(3f * th + 2.6f));
    }

    private static float outerHeight(float th) {
        return 100f + 55f * (0.5f + 0.5f * FastMath.sin(2f * th + 1.9f));
    }

    /** Rises from foot to amp between the foot radius and the peak radius. */
    private static float ringRise(float r, float foot, float peakR, float amp) {
        if (r <= foot) return 0f;
        if (r >= peakR) return amp;
        return amp * smooth01((r - foot) / (peakR - foot));
    }

    private static float dist(float x, float z, float cx, float cz) {
        float dx = x - cx;
        float dz = z - cz;
        return FastMath.sqrt(dx * dx + dz * dz);
    }

    private static float smooth01(float t) {
        t = FastMath.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Wide soft swell for ridge lines and the broad rolling ground. */
    private static float gaussian(float cx, float cz, float sx, float sz,
                                  float amp, float x, float z) {
        float dx = x - cx;
        float dz = z - cz;
        return amp * FastMath.exp(-(dx * dx / (2f * sx * sx) + dz * dz / (2f * sz * sz)));
    }

    /** Pulls the accumulated height toward `target` inside a flat zone, blending
     *  over `blend` so meadow rims are gentle slopes, not steps. */
    private static float flattenTo(float h, float cx, float cz, float r,
                                   float blend, float target, float x, float z) {
        float d = dist(x, z, cx, cz);
        if (d >= r + blend) return h;
        float m = 1f - smooth01((d - r) / blend); // 1 inside r, 0 once outside
        return h + (target - h) * m;
    }

    // ------------------------------------------------------------ the sky dome

    /** Forest sky dome (equirectangular, baked at build time). To swap in a real
     *  panorama later, replace the painted BufferedImage with a texture loaded
     *  from a FileTextureKey/TextuKey — the dome, wrapping and queue all stay. */
    private void buildSky(AssetManager assetManager) {
        int w = 1024;
        int h = 512;

        BufferedImage img = skyTextureImage(w, h);

        // dome textures map top = zenith, so flip the paint before upload
        BufferedImage flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D fg = flipped.createGraphics();
        fg.drawImage(img, 0, h, w, -h, null);
        fg.dispose();

        AWTLoader loader = new AWTLoader();
        Texture2D tex = new Texture2D(loader.load(flipped, false));
        tex.setWrap(Texture.WrapMode.Repeat);

        Sphere domeMesh = new Sphere(24, 32, SKY_RADIUS);
        Geometry dome = new Geometry("SkyGeometry", domeMesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", tex);
        mat.setColor("Color", ColorRGBA.White);
        mat.getAdditionalRenderState().setFaceCullMode(
                com.jme3.material.RenderState.FaceCullMode.Front);
        mat.getAdditionalRenderState().setDepthWrite(false);
        dome.setMaterial(mat);
        dome.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Sky);

        Node sky = new Node("Sky");
        sky.attachChild(dome);
        stageNode.attachChild(sky);
    }

    /** Procedural golden-hour forest sky: clear azure going warm at the horizon,
     *  a soft sun glow, and a few high evening clouds. */
    private BufferedImage skyTextureImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint grad = new GradientPaint(
                0, 0, new java.awt.Color(52, 86, 138),
                0, h, new java.awt.Color(232, 206, 178));
        g.setPaint(grad);
        g.fillRect(0, 0, w, h);

        // sun glow, low and warm (echoing the sun direction -x, -y, -z)
        int gx = (int) (w * 0.36f);
        int gy = (int) (h * 0.30f);
        int glowR = 170;
        for (int i = 0; i < 42; i++) {
            float t = i / 42f;
            int radius = Math.round(glowR * (1f + t * 2.4f));
            g.setColor(new java.awt.Color(255, 232, 170,
                    Math.max(0, Math.round(130 * (1f - t) * (1f - t)))));
            g.fillOval(gx - radius, gy - radius, radius * 2, radius * 2);
        }

        // a few high evening clouds across the upper sky
        Random rand = new Random(11);
        g.setColor(new java.awt.Color(228, 214, 198, 130));
        for (int i = 0; i < 16; i++) {
            int cx = rand.nextInt(w);
            int cy = rand.nextInt((int) (h * 0.42f));
            int base = 45 + rand.nextInt(85);
            for (int j = 0; j < 6; j++) {
                int ox = cx + rand.nextInt(base) - base / 2;
                int oy = cy + rand.nextInt(base / 3) - base / 6;
                int size = base / 2 + rand.nextInt(base / 2);
                g.fillOval(ox, oy, size, size / 2);
            }
        }
        g.dispose();
        return img;
    }

    // ----------------------------------------------------- the authored forest

    /**
     * Deliberate tree stands. Open ground is the point — trees frame spaces and
     * break open sight-lines, never wall the basin in. Every coordinate is
     * authored; trees keep a trunk collider; grass and bushes are cosmetic.
     * Each variant composes its own stands around its own arenas.
     */
    private void buildAuthoredForest(AssetManager assetManager, BulletAppState bulletAppState) {
        int trees = switch (variant) {
            case 2 -> forestV2(assetManager, bulletAppState);
            case 3 -> forestV3(assetManager, bulletAppState);
            case 4 -> forestV4(assetManager, bulletAppState);
            default -> forestV1(assetManager, bulletAppState);
        };
        System.out.println("[Darkwood " + variant + "] authored " + trees + " trees.");
    }

    private int plantCluster(AssetManager assetManager, BulletAppState bulletAppState,
                             Random rand, float[][] spots) {
        int planted = 0;
        for (int i = 0; i < spots.length; i++) {
            // Drop every 5th authored spot, so ~20% of the trees (and their trunk
            // colliders) are never built. Deterministic on the spot index rather
            // than a random draw, so the forest rebuilds identically every time.
            if (i % 5 == 4) continue;
            plantTree(assetManager, bulletAppState, spots[i][0], spots[i][1], rand);
            planted++;
        }
        return planted;
    }

    private void plantBushes(AssetManager assetManager, Random rand, float[][] spots) {
        for (float[] p : spots) {
            ScalePlacement s = placeFlatTerrain(stageNode, assetManager,
                    BUSH_MODELS[rand.nextInt(BUSH_MODELS.length)], p[0], p[1],
                    2f + rand.nextFloat() * 1.2f, rand);
            s.spatial.setLocalTranslation(p[0], s.baseY + heightAt(p[0], p[1]), p[1]);
        }
    }

    private void plantGrass(AssetManager assetManager, Random rand, float[][] spots) {
        for (float[] p : spots) {
            ScalePlacement s = placeFlatTerrain(stageNode, assetManager,
                    GRASS_MODELS[rand.nextInt(GRASS_MODELS.length)], p[0], p[1],
                    1.0f + rand.nextFloat() * 1.0f, rand);
            s.spatial.setLocalTranslation(p[0], s.baseY + heightAt(p[0], p[1]), p[1]);
        }
    }

    /** Darkwood 1 — the basin: a broad valley framed by woodland, with a few open
     *  fields broken by new stands so the long travel stays readable. */
    private int forestV1(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(120 + variant);
        int trees = 0;

        // darkwoodValleyWoods — a broad ring of trees framing the E1 clearing
        float[][] valleyWoods = {
                {75f, 60f}, {92f, 80f}, {85f, 105f}, {68f, 125f},
                {95f, 50f}, {105f, 90f}, {60f, 140f}, {48f, 145f}
        };
        // darkwoodRidgeTimber — the long rise under the raised forest ridge
        float[][] ridgeTimber = {
                {-55f, 80f}, {-35f, 92f}, {-15f, 75f}, {5f, 72f}, {25f, 70f},
                {45f, 88f}, {65f, 80f}, {-45f, 105f}, {35f, 105f}, {-45f, 125f}
        };
        // darkwoodWestWoods — a corridor between the spawn vale and the west basin
        float[][] westWoods = {
                {-60f, -20f}, {-78f, -28f}, {-90f, -45f}, {-75f, -60f},
                {-55f, -38f}, {-40f, -55f}, {-65f, -95f}, {-52f, -80f}
        };
        // darkwoodEastGrove — trees flanking the E3 shelf
        float[][] eastGrove = {
                {88f, 30f}, {100f, 45f}, {92f, 60f}, {78f, 74f}, {70f, 82f}, {86f, 18f}
        };
        // darkwoodTrailPairs — paired trees marking the route to the valley
        float[][] trailPairs = {
                {-24f, 40f}, {24f, 40f}, {-28f, 58f}, {28f, 58f}, {-32f, 74f}, {32f, 74f}
        };
        // darkwoodBackdrop — thin framing on the near foothills
        float[][] backdrop = {
                {-118f, 58f}, {-96f, 86f}, {-20f, 132f}, {38f, 128f},
                {100f, 95f}, {128f, 55f}, {120f, -38f}, {96f, -90f},
                {60f, -120f}, {-30f, -122f}, {-95f, -120f}, {-125f, -40f}
        };
        // darkwoodHollow — fills the overly-open west rim and the arc between the
        // vale and the ridge, so the big spaces read as glades, not empty fields
        float[][] hollow = {
                {-52f, 20f}, {-44f, 6f}, {-38f, -14f}, {-28f, -44f}, {-12f, -56f},
                {4f, -46f}, {20f, -56f}, {34f, -44f}, {46f, -30f}, {42f, 6f},
                {32f, 22f}, {-22f, 22f}, {-34f, 30f}, {-46f, -30f}
        };
        // darkwoodSouthStand — frames the empty southern plain beyond the spawn
        float[][] southStand = {
                {-20f, -70f}, {-4f, -74f}, {10f, -68f}, {26f, -76f}, {38f, -66f}
        };
        // darkwoodNEstub — a small clump past the E1 clearing's east rim
        float[][] nEstub = {
                {72f, 96f}, {66f, 110f}, {80f, 108f}
        };

        trees += plantCluster(assetManager, bulletAppState, rand, SPAWN_RING);
        trees += plantCluster(assetManager, bulletAppState, rand, valleyWoods);
        trees += plantCluster(assetManager, bulletAppState, rand, ridgeTimber);
        trees += plantCluster(assetManager, bulletAppState, rand, westWoods);
        trees += plantCluster(assetManager, bulletAppState, rand, eastGrove);
        trees += plantCluster(assetManager, bulletAppState, rand, trailPairs);
        trees += plantCluster(assetManager, bulletAppState, rand, backdrop);
        trees += plantCluster(assetManager, bulletAppState, rand, hollow);
        trees += plantCluster(assetManager, bulletAppState, rand, southStand);
        trees += plantCluster(assetManager, bulletAppState, rand, nEstub);

        plantBushes(assetManager, rand, new float[][]{
                {-24f, 30f}, {24f, 30f}, {-14f, 42f}, {14f, 42f},
                {8f, -8f}, {-8f, -8f}, {30f, -2f}, {-2f, 30f},
                {70f, 58f}, {30f, 60f}, {34f, 120f}, {70f, 110f},
                {-78f, -50f}, {-60f, -70f}, {110f, 80f}, {-118f, 40f}
        });
        plantGrass(assetManager, rand, new float[][]{
                {0f, 18f}, {-8f, 22f}, {8f, 22f}, {-12f, 12f}, {12f, 12f},
                {-6f, -14f}, {6f, -14f}, {20f, 48f}, {-20f, 48f}, {26f, 64f},
                {-26f, 64f}, {-16f, 80f}, {16f, 80f}, {26f, 88f}, {8f, 96f},
                {-8f, 96f}, {26f, 104f}, {-66f, 36f}, {58f, 20f}, {30f, 66f},
                {66f, 36f}, {76f, 40f}, {70f, 68f}, {96f, 10f}, {70f, 30f},
                {118f, 0f}, {70f, -30f}, {90f, -76f}, {-50f, 118f}, {0f, 120f},
                {40f, 112f}, {-58f, 36f}, {-98f, -10f}, {-118f, -78f}, {-126f, -60f}, {-40f, -100f}
        });
        return trees;
    }

    /** Darkwood 2 — the gullies: dense N/S gully rims, a west shelf grove and
     *  corridors that keep the approaches open. */
    private int forestV2(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(120 + variant);
        int trees = 0;

        float[][] nGully = {
                {55f, 95f}, {-55f, 95f}, {62f, 120f}, {-62f, 120f},
                {58f, 65f}, {-58f, 65f}, {0f, 148f}
        };
        float[][] sGully = {
                {55f, -90f}, {-55f, -90f}, {0f, -140f}, {58f, -120f}, {-58f, -120f},
                {50f, -60f}, {-50f, -60f}, {30f, -130f}, {-30f, -130f}
        };
        float[][] wShelf = {
                {-62f, -14f}, {-64f, 10f}, {-70f, 22f}, {-62f, 34f}, {-90f, 44f},
                {-112f, 34f}, {-118f, 16f}, {-114f, -6f}, {-104f, -22f},
                {-92f, -30f}, {-76f, -34f}, {-56f, -30f}
        };
        float[][] westCorridor = {
                {-52f, 8f}, {-58f, 22f}, {-52f, -18f}, {-46f, -34f}, {-50f, 40f}, {-44f, 52f}
        };
        float[][] southFrame = {
                {-40f, -22f}, {40f, -22f}, {44f, -40f}, {-44f, -40f},
                {48f, -58f}, {-48f, -58f}, {30f, 18f}, {-30f, 18f}
        };
        float[][] backdrop = {
                {-90f, 100f}, {-125f, 55f}, {20f, 140f}, {75f, 130f}, {110f, 95f},
                {135f, 38f}, {120f, -45f}, {95f, -85f}, {58f, -125f}, {-28f, -140f},
                {-95f, -120f}, {-128f, -45f}
        };

        trees += plantCluster(assetManager, bulletAppState, rand, SPAWN_RING);
        trees += plantCluster(assetManager, bulletAppState, rand, nGully);
        trees += plantCluster(assetManager, bulletAppState, rand, sGully);
        trees += plantCluster(assetManager, bulletAppState, rand, wShelf);
        trees += plantCluster(assetManager, bulletAppState, rand, westCorridor);
        trees += plantCluster(assetManager, bulletAppState, rand, southFrame);
        trees += plantCluster(assetManager, bulletAppState, rand, backdrop);

        plantBushes(assetManager, rand, new float[][]{
                {-24f, 30f}, {24f, 30f}, {-14f, 44f}, {14f, 44f},
                {8f, -8f}, {-8f, -8f}, {30f, -2f}, {-2f, 30f},
                {0f, 55f}, {-30f, 70f}, {30f, 120f}, {55f, 95f}, {-55f, 95f},
                {-30f, -115f}, {30f, -115f}, {0f, -135f}, {-66f, -20f}, {-60f, 30f}
        });
        plantGrass(assetManager, rand, new float[][]{
                {0f, 18f}, {-8f, 22f}, {8f, 22f}, {-12f, 12f}, {12f, 12f},
                {-6f, -14f}, {6f, -14f}, {20f, 48f}, {-20f, 48f},
                {18f, 70f}, {-18f, 70f}, {28f, 80f}, {-28f, 80f},
                {8f, 108f}, {-8f, 108f}, {14f, -68f}, {-14f, -68f}, {24f, -78f},
                {-24f, -78f}, {0f, -98f}, {10f, -110f}, {-10f, -110f},
                {-60f, -40f}, {-40f, -60f}, {-85f, -15f}, {-95f, 0f}, {-70f, 10f},
                {45f, 55f}, {58f, 80f}, {-58f, 80f}, {70f, 110f}, {-70f, 110f}, {100f, 60f}
        });
        return trees;
    }

    /** Darkwood 3 — the ridgeline: trees march the E-W ridge, ring the north and
     *  south arenas, and frame the east shelf. */
    private int forestV3(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(120 + variant);
        int trees = 0;

        float[][] spine = {
                {-88f, 28f}, {-68f, 26f}, {-48f, 30f}, {-26f, 34f}, {-18f, 30f},
                {-8f, 38f}, {12f, 34f}, {32f, 32f}, {52f, 34f}, {74f, 28f},
                {96f, 30f}, {116f, 32f}
        };
        float[][] nArena = {
                {50f, 100f}, {-50f, 100f}, {58f, 128f}, {-58f, 128f}, {0f, 150f},
                {54f, 65f}, {-54f, 65f}, {32f, 140f}, {-32f, 140f}
        };
        float[][] sArena = {
                {38f, -84f}, {-78f, -84f}, {2f, -138f}, {40f, -124f}, {-60f, -124f},
                {40f, -36f}, {-74f, -40f}, {30f, -52f}, {-30f, -52f}
        };
        float[][] eGrove = {
                {142f, 18f}, {56f, 18f}, {110f, 62f}, {90f, 62f}, {140f, -10f},
                {60f, -6f}, {140f, 52f}, {58f, 46f}
        };
        float[][] backdrop = {
                {-45f, 150f}, {-120f, 120f}, {70f, 150f}, {120f, 110f}, {150f, 45f},
                {115f, -55f}, {35f, -150f}, {-60f, -150f}, {-130f, -95f},
                {-150f, -30f}, {-8f, -160f}
        };

        trees += plantCluster(assetManager, bulletAppState, rand, SPAWN_RING);
        trees += plantCluster(assetManager, bulletAppState, rand, spine);
        trees += plantCluster(assetManager, bulletAppState, rand, nArena);
        trees += plantCluster(assetManager, bulletAppState, rand, sArena);
        trees += plantCluster(assetManager, bulletAppState, rand, eGrove);
        trees += plantCluster(assetManager, bulletAppState, rand, backdrop);

        plantBushes(assetManager, rand, new float[][]{
                {-24f, 30f}, {24f, 30f}, {-14f, 44f}, {14f, 44f},
                {8f, -8f}, {-8f, -8f}, {30f, -2f}, {-2f, 30f},
                {0f, 50f}, {30f, 90f}, {-30f, 90f}, {12f, 120f}, {-12f, 120f},
                {30f, -50f}, {-30f, -50f}, {0f, -100f}, {28f, -95f}, {-28f, -95f},
                {95f, 35f}, {105f, 25f}
        });
        plantGrass(assetManager, rand, new float[][]{
                {0f, 18f}, {-8f, 22f}, {8f, 22f}, {-12f, 12f}, {12f, 12f},
                {-6f, -14f}, {6f, -14f}, {20f, 48f}, {-20f, 48f},
                {20f, 75f}, {-20f, 75f}, {0f, 90f}, {25f, 100f}, {-25f, 100f},
                {15f, 115f}, {-15f, 115f}, {-10f, -35f}, {10f, -35f}, {0f, -60f},
                {28f, -70f}, {-28f, -70f}, {0f, -90f}, {20f, -95f}, {-20f, -95f},
                {95f, 40f}, {110f, 20f}, {100f, 0f}, {-40f, 20f}, {-55f, 35f},
                {60f, 65f}, {-110f, -50f}, {-70f, -120f}, {40f, -130f}
        });
        return trees;
    }

    /** Darkwood 4 — the highlands: swaths of forest carving out the environs of
     *  the NE arena, SW basin and SE shelf. */
    private int forestV4(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(120 + variant);
        int trees = 0;

        float[][] neGrove = {
                {120f, 85f}, {70f, 136f}, {70f, 35f}, {118f, 105f}, {24f, 106f},
                {24f, 62f}, {112f, 58f}, {36f, 130f}, {108f, 132f}
        };
        float[][] swWoods = {
                {-124f, -95f}, {-75f, -45f}, {-75f, -146f}, {-30f, -142f},
                {-120f, -142f}, {-122f, -54f}, {-32f, -52f},
                {-45f, -30f}, {-40f, -50f}, {-50f, -20f}, {-58f, -45f}
        };
        float[][] seShelf = {
                {142f, -22f}, {58f, -18f}, {100f, -62f}, {100f, 22f},
                {140f, -52f}, {62f, -52f}, {140f, 12f}, {58f, -8f}
        };
        float[][] center = {
                {-34f, 40f}, {36f, 36f}, {-50f, 10f}, {48f, -30f}, {-20f, 56f},
                {24f, -50f}, {-44f, -30f}
        };
        float[][] backdrop = {
                {-15f, 150f}, {-110f, 115f}, {-145f, 55f}, {-55f, 135f}, {90f, 135f},
                {140f, 60f}, {135f, -75f}, {60f, -135f}, {-10f, -150f},
                {-125f, -85f}, {-155f, 0f}, {-140f, -45f}
        };

        trees += plantCluster(assetManager, bulletAppState, rand, SPAWN_RING);
        trees += plantCluster(assetManager, bulletAppState, rand, neGrove);
        trees += plantCluster(assetManager, bulletAppState, rand, swWoods);
        trees += plantCluster(assetManager, bulletAppState, rand, seShelf);
        trees += plantCluster(assetManager, bulletAppState, rand, center);
        trees += plantCluster(assetManager, bulletAppState, rand, backdrop);

        plantBushes(assetManager, rand, new float[][]{
                {-24f, 30f}, {24f, 30f}, {-14f, 44f}, {14f, 44f},
                {8f, -8f}, {-8f, -8f}, {30f, -2f}, {-2f, 30f},
                {35f, 45f}, {-35f, 45f}, {60f, 80f}, {80f, 90f}, {60f, 100f},
                {-60f, -80f}, {-80f, -90f}, {-70f, -100f}, {-90f, -100f},
                {95f, -10f}, {105f, -25f}, {70f, 20f}, {50f, -40f}
        });
        plantGrass(assetManager, rand, new float[][]{
                {0f, 18f}, {-8f, 22f}, {8f, 22f}, {-12f, 12f}, {12f, 12f},
                {-6f, -14f}, {6f, -14f}, {20f, 48f}, {-20f, 48f},
                {28f, 60f}, {-28f, 60f}, {50f, 72f}, {58f, 84f}, {75f, 78f},
                {90f, 70f}, {48f, 95f}, {64f, 100f}, {-18f, -58f}, {-45f, -75f},
                {-62f, -90f}, {-58f, -105f}, {-90f, -88f}, {-72f, -115f}, {-85f, -110f},
                {92f, -30f}, {105f, -15f}, {90f, -45f}, {110f, -40f}, {30f, 30f},
                {-30f, 30f}, {40f, -20f}, {60f, -60f}
        });
        return trees;
    }

    private void plantTree(AssetManager assetManager, BulletAppState bulletAppState,
                           float x, float z, Random rand) {
        String model = TREE_MODELS[rand.nextInt(TREE_MODELS.length)];
        float scale = 3.2f + rand.nextFloat() * 1.0f;
        ScalePlacement placed = placeFlatTerrain(stageNode, assetManager, model, x, z, scale, rand);
        float y = heightAt(x, z);
        placed.spatial.setLocalTranslation(x, placed.baseY + y, z);
        placed.spatial.updateModelBound();

        // Trunk-only collider. Sizing this off the model's full bounds made every tree a
        // 4x4 box up to 9u tall, so the invisible wall reached up through the canopy
        // and snagged the player in clear space. The helpers keep it to the stem.
        float trunkRadius = StageDecor.trunkHalfWidth(placed.spatial, 0.6f, 1.25f);
        float collarHeight = StageDecor.trunkHeight(placed.spatial, 2.5f, 4f);
        addBlockerAt(bulletAppState, x, y, z, trunkRadius, collarHeight / 2f, trunkRadius);
    }

    /** Box collider centered at (x, baseY + halfY, z) so it sits on the terrain. */
    private void addBlockerAt(BulletAppState bulletAppState, float x, float y, float z,
                              float halfX, float halfY, float halfZ) {
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(halfX, halfY, halfZ));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(x, y + halfY, z));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    /** Result of placeFlat with the model's base-lift exposed so callers can rest
     *  it on variable terrain instead of the y=0 assumption the helper makes. */
    private static final class ScalePlacement {
        final Spatial spatial;
        final float baseY;
        ScalePlacement(Spatial spatial, float baseY) {
            this.spatial = spatial;
            this.baseY = baseY;
        }
    }

    private static ScalePlacement placeFlatTerrain(Node parent, AssetManager assetManager,
                                                   String path, float x, float z,
                                                   float scale, Random rand) {
        Spatial s = StageDecor.loadCached(assetManager, path).clone();
        s.setLocalScale(scale);
        s.rotate(0f, rand.nextFloat() * FastMath.TWO_PI, 0f);
        s.updateModelBound();
        float lift = 0f;
        if (s.getWorldBound() instanceof BoundingBox bbox) {
            Vector3f extent = bbox.getExtent(new Vector3f());
            Vector3f center = bbox.getCenter(new Vector3f());
            lift = extent.y - center.y;
        }
        s.setLocalTranslation(x, lift, z);
        parent.attachChild(s);
        return new ScalePlacement(s, lift);
    }
}