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
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Jungle — stage 4, a large jungle basin. Four variants, each its own handcrafted
 * valley: one broad playable floor (~280 units across) of rolling forest, shallow
 * valleys and a raised canopy ridge, ringed by an unclimbable chain of rounded,
 * irregular mountains.
 *
 * <p>Every variant is authored. Elevation is a pure function of (x, z), the encounter
 * groups are fixed coordinates, and the tree stands are hand-placed clusters. There is
 * no random position generation anywhere: {@link Random} only picks which of the
 * already-used models a stand is built from and how it is turned and scaled.</p>
 *
 * <p>The terrain mesh and its collider are the same geometry, so what you see is
 * exactly what you land on — and the mountains <em>are</em> the level boundary. An
 * invisible ring of static blockers seals the basin-to-mountain seam, because a
 * character capsule can wedge a couple of steps into those near-vertical triangles.</p>
 */
public class JungleStage implements Stage {

    private static final float HALF_EXTENT = 165f;
    private static final float FAST_STAGE_SPEED_MULTIPLIER = 2f; // Jungle 3 modifier
    private static final String KAYKIT_GLTF =
            "Models/Environment/KayKit_Forest_Nature_Pack_1.0_FREE/"
                    + "KayKit_Forest_Nature_Pack_1.0_FREE/Assets/gltf/";
    private static final String[] ENEMY_MODELS = {
            "Models/Characters/enemy/jungle_enemy.gltf",
            "Models/Characters/enemy/jungle_enemy2.gltf",
            "Models/Characters/enemy/jungle_enemy3.gltf"
    };

    // ------------------------------------------------------------------ terrain
    // Elevation is an authored function of (x, z). The forest floor is a few very
    // broad rolling forms; the mountain ring is concentric, irregular ridge bands
    // plus named giant summits. The whole basin - forest floor AND mountains - is
    // ONE mesh with ONE static collider.
    private static final float TERRAIN_HALF = 190f;   // mesh spans [-190, 190]; play basin is ~±140
    private static final float TERRAIN_STEP = 2f;     // grid cell size (world units)
    private static final int TERRAIN_SIZE = (int) (TERRAIN_HALF * 2f / TERRAIN_STEP);
    private static final int TERRAIN_SAMPLES = TERRAIN_SIZE + 1;
    private static final float MIN_ELEVATION = -9f;   // deeper than the deepest authored valley

    /** Steepest gradient the walkable basin is allowed to keep, as rise over run.
     *  The player's BetterCharacterControl is built with a 1-radian (57 deg, 1.56
     *  slope) limit in ForestBiome, and FrozenDepthsStage already designs to 1.0 as
     *  its ceiling, so 0.85 (40 deg) leaves real headroom for the capsule while still
     *  reading as rolling jungle floor rather than a scree field. */
    private static final float MAX_BASIN_SLOPE = 0.85f;

    // mountain ring geometry (radians polar angle th)
    // The foothill baseline sits far enough out that the gentle collar starts beyond
    // r=137: an earlier value (168) put the collar at r~127, which bled a radial ramp
    // straight through the northern arena discs.
    private static final float MOUNT_FOOT_BASE = 176f; // foothill baseline from world centre
    private static final float MOUNT_FOOT_WAVE = 13f;  // angular wobble -> irregular, not square
    private static final float COLLAR_WIDTH = 26f;     // gentle green collar before the climb

    /** How far out the basin is slope-relaxed, as a radius from world centre.
     *
     *  <p>Deliberately larger than the innermost collar start (137). A relaxation disc
     *  that stops exactly at the collar leaves a partly-relaxed seam: the four grid
     *  corners around a sample near the boundary are interleaved soft and hard, so
     *  bilinear reads see a fraction of a stiff flank. Relaxing a full grid cell past
     *  the collar removes that seam, and costs nothing because the ring itself - collar
     *  and peaks alike - is added only afterwards.</p>
     *
     *  <p>Also stops short of the innermost summit (r~163), so the giants keep their
     *  height instead of being spread into the basin.</p> */
    private static final float RELAX_LIMIT = MOUNT_FOOT_BASE - MOUNT_FOOT_WAVE - COLLAR_WIDTH;

    /** Offset from innerFoot() where the invisible seal is planted - right where the
     *  steep face begins, so it is never felt as a wall in open ground. */
    private static final float SEAL_OFFSET = 1.5f;

    // --------------------------------------------------------------- the palette
    // Warm, high-key foliage against the cool cyan ground. Every entry is a LIGHT,
    // desaturated tint: these models carry baked vertex colour and texture detail, and
    // a saturated base tint would multiply that into mud. Trunks stay brown so the
    // silhouettes still read as trees rather than as floating candy.
    private static final ColorRGBA[] TREE_TINTS = {
            new ColorRGBA(1.00f, 0.66f, 0.28f, 1f), // tangerine
            new ColorRGBA(1.00f, 0.55f, 0.62f, 1f), // watermelon pink
            new ColorRGBA(1.00f, 0.85f, 0.35f, 1f), // lemon
            new ColorRGBA(0.78f, 0.95f, 0.35f, 1f), // lime
            new ColorRGBA(0.60f, 0.92f, 0.62f, 1f), // spring green
            new ColorRGBA(1.00f, 0.75f, 0.50f, 1f), // apricot
            new ColorRGBA(0.98f, 0.45f, 0.55f, 1f), // fuchsia
            new ColorRGBA(0.55f, 0.90f, 0.80f, 1f)  // mint - ties back to the ground
    };

    /** Bushes and grass take the same family a shade off the trees, so undergrowth
     *  supports the canopy instead of competing with it. */
    private static final ColorRGBA[] UNDERSTORY_TINTS = {
            new ColorRGBA(1.00f, 0.74f, 0.42f, 1f),
            new ColorRGBA(1.00f, 0.68f, 0.58f, 1f),
            new ColorRGBA(0.88f, 0.96f, 0.45f, 1f),
            new ColorRGBA(0.70f, 0.95f, 0.68f, 1f)
    };

    /** Rock and boulder tint, cool enough to sit with the mountains rather than the
     *  trees. */
    private static final ColorRGBA ROCK_TINT = new ColorRGBA(0.74f, 0.83f, 0.90f, 1f);

    // ------------------------------------------------------------- models in use
    // Unchanged from the previous Jungle build: same palms, same KayKit trees,
    // same rocks, bushes and grass. The rework moves terrain and layout, not assets.
    private static final String[] TREE_MODELS = {
            "Models/Environment/Forest/tree_palm.glb",
            "Models/Environment/Forest/tree_palmBend.glb",
            "Models/Environment/Forest/tree_palmDetailedShort.glb",
            "Models/Environment/Forest/tree_palmDetailedTall.glb",
            "Models/Environment/Forest/tree_detailed.glb",
            "Models/Environment/Forest/tree_fat.glb",
            "Models/Environment/Forest/tree_default.glb",
            "Models/Environment/Forest/tree_oak.glb",
            KAYKIT_GLTF + "Tree_2_A_Color1.gltf",
            KAYKIT_GLTF + "Tree_2_C_Color1.gltf",
            KAYKIT_GLTF + "Tree_3_B_Color1.gltf",
            KAYKIT_GLTF + "Tree_4_A_Color1.gltf",
            KAYKIT_GLTF + "Tree_4_B_Color1.gltf",
            KAYKIT_GLTF + "Tree_4_C_Color1.gltf"
    };

    private static final String[] ROCK_MODELS = {
            "Models/Environment/Forest/stone_largeA.glb",
            "Models/Environment/Forest/stone_largeB.glb",
            "Models/Environment/Forest/stone_largeC.glb",
            "Models/Environment/Forest/stone_smallFlatA.glb",
            "Models/Environment/Forest/stone_smallG.glb",
            "Models/Environment/Forest/stone_smallH.glb",
            "Models/Environment/Forest/stone_tallB.glb",
            "Models/Environment/Forest/stump_old.glb"
    };

    private static final String[] BUSH_MODELS = {
            KAYKIT_GLTF + "Bush_1_C_Color1.gltf",
            KAYKIT_GLTF + "Bush_2_D_Color1.gltf",
            KAYKIT_GLTF + "Bush_3_B_Color1.gltf",
            KAYKIT_GLTF + "Bush_4_A_Color1.gltf",
            KAYKIT_GLTF + "Bush_4_E_Color1.gltf"
    };

    private static final String[] GRASS_MODELS = {
            KAYKIT_GLTF + "Grass_1_A_Color1.gltf",
            KAYKIT_GLTF + "Grass_1_D_Color1.gltf",
            KAYKIT_GLTF + "Grass_2_B_Color1.gltf"
    };

    /** Ground cover from the jungle prop folder, scattered across the basin rather than
     *  hand-placed. The duplicate-looking names are real exports from the source scene
     *  (the tool appended a random suffix); they are kept as-is rather than trimmed,
     *  because the library is user-supplied content. */
    /** Ground cover from the jungle prop folder, scattered across the basin rather than
     *  hand-placed. The duplicate-looking names are real exports from the source scene
     *  (the tool appended a random suffix); they are kept as-is rather than trimmed,
     *  because the library is user-supplied content.
     *
     *  <p>Split by cost, not by taxonomy. Flowers carry visibly more geometry than a
     *  grass tuft - petal clusters are separate meshes - so mixing them evenly meant
     *  most of the draw calls in the basin were petals. Ferns and grass carry the layer
     *  on their own, and flowers only need to punctuate it. They also break up the
     *  green, which is the whole reason they are here at all.</p> */
    private static final String[] UNDERSTORY_FERN_GRASS = {
            "Models/Environment/jungle/Fern.glb",
            "Models/Environment/jungle/Grass.glb",
            "Models/Environment/jungle/Grass Wispy.glb",
            "Models/Environment/jungle/Grass Wispy-Msr9zx66VU.glb",
            "Models/Environment/jungle/Plant.glb",
            "Models/Environment/jungle/Plant Big.glb",
            "Models/Environment/jungle/Plant Big-MbhbP7JrTI.glb"
    };

    private static final String[] UNDERSTORY_FLOWERS = {
            "Models/Environment/jungle/Flower Petal.glb",
            "Models/Environment/jungle/Flower Petal-eVE0j49ux9.glb",
            "Models/Environment/jungle/Flower Petal-LqvxG9OBOU.glb",
            "Models/Environment/jungle/Flower Petal-niuBUEJdvM.glb",
            "Models/Environment/jungle/Flower Petal-tzG4JcqYWs.glb",
            "Models/Environment/jungle/Flower Single.glb",
            "Models/Environment/jungle/Flower Single-GvfHo0roi3.glb",
            "Models/Environment/jungle/Flower Group.glb",
            "Models/Environment/jungle/Flower Group-LqTljN6Wg2.glb",
            "Models/Environment/jungle/Mushroom.glb"
    };

    // ------------------------------------------------------- authored section data
    /** A flat, walkable authored space: spawn vale, clearing, valley or exit meadow. */
    private static final class Arena {
        final float x, z, r;
        Arena(float x, float z, float r) {
            this.x = x;
            this.z = z;
            this.r = r;
        }
    }

    /** A hand-placed boulder used as a landmark. */
    private static final class Boulder {
        final float x, z, size;
        Boulder(float x, float z, float size) {
            this.x = x;
            this.z = z;
            this.size = size;
        }
    }

    // Layout rules, enforced by JungleAudit: (a) no two arenas whose targets differ by
    // more than a few units may have overlapping blend discs, or the later flattenTo
    // drags a ramp through the earlier arena's floor; (b) every arena's flat disc must
    // stay inside r=137, or the mountain collar tilts it; (c) the exit meadow is always
    // given the SAME target as the final arena so the two merge into one level plateau.

    // Variant 1 - "the canopy basin". Route runs north out of a low spawn vale, through
    // an open clearing, west into a hollow valley, then up onto the raised canopy ridge
    // and across a level plateau to the exit.
    private static final Arena[] V1_SPACES = {
            new Arena(0f, 0f, 30f),     // jungleSpawnArea
            new Arena(0f, 44f, 26f),    // jungleClearing1      (encounter 1)
            new Arena(-56f, 74f, 28f),  // jungleValley         (encounter 2)
            new Arena(48f, 100f, 22f),  // jungleRidgeClearing  (encounter 3, raised)
            new Arena(0f, 132f, 16f)    // jungleExitArea  (shares E3's target -> level plateau)
    };

    // Variant 2 - "the twin gullies". Two parallel clearings split by the spawn vale,
    // converging on a raised north shelf and a level exit plateau.
    private static final Arena[] V2_SPACES = {
            new Arena(0f, 0f, 30f),
            new Arena(-62f, 56f, 26f),  // jungleWestGully      (encounter 1)
            new Arena(62f, 56f, 26f),   // jungleEastGully      (encounter 2)
            new Arena(0f, 108f, 24f),   // jungleNorthShelf     (encounter 3, raised)
            new Arena(0f, 128f, 16f)    // jungleExitArea  (same target as the shelf)
    };

    // Variant 3 - "the ridge line". Spawn sits in the south basin; the player crosses
    // west into a gully, then runs the length of the valley to the east shelf.
    private static final Arena[] V3_SPACES = {
            new Arena(0f, -56f, 28f),   // jungleSpawnArea
            new Arena(-58f, -6f, 26f),  // jungleWestGully     (encounter 1)
            new Arena(0f, 50f, 24f),    // jungleNorthClearing (encounter 2)
            new Arena(60f, 92f, 22f),   // jungleEastShelf     (encounter 3, raised)
            new Arena(0f, 130f, 16f)    // jungleExitArea  (same target as the shelf)
    };

    // Variant 4 - "the deep hollow". The broadest basin: the lowest authored ground in
    // a south-west hollow, a north-east grove, and a raised northern shelf to the exit.
    private static final Arena[] V4_SPACES = {
            new Arena(0f, -20f, 30f),
            new Arena(-64f, 46f, 30f),  // jungleDeepHollow     (encounter 1, lowest)
            new Arena(64f, 36f, 26f),   // jungleNortheastGrove (encounter 2)
            new Arena(10f, 100f, 24f),  // jungleNorthShelf     (encounter 3, raised)
            new Arena(0f, 130f, 16f)    // jungleExitArea  (same target as the shelf)
    };

    // --------------------------------------------------- authored encounter groups
    // Two guards near spawn, then one complete group per clearing. Every coordinate
    // is fixed and every enemy is dropped onto the terrain that spot actually sits on.
    private static final float[][] V1_GUARD = {{-15f, 20f}, {16f, 18f}};
    private static final float[][] V1_E1 = {{-14f, 38f}, {13f, 36f}, {0f, 56f}, {4f, 30f}};
    private static final float[][] V1_E2 = {{-72f, 68f}, {-44f, 66f}, {-52f, 88f}};
    private static final float[][] V1_E3 = {{38f, 94f}, {60f, 96f}, {50f, 110f}};

    private static final float[][] V2_GUARD = {{-16f, 20f}, {15f, 18f}};
    private static final float[][] V2_E1 = {{-76f, 48f}, {-52f, 44f}, {-58f, 70f}, {-48f, 58f}};
    private static final float[][] V2_E2 = {{76f, 50f}, {52f, 44f}, {58f, 70f}, {68f, 60f}};
    private static final float[][] V2_E3 = {{-14f, 102f}, {12f, 100f}, {2f, 118f}};

    private static final float[][] V3_GUARD = {{-14f, -70f}, {14f, -72f}};
    private static final float[][] V3_E1 = {{-72f, -12f}, {-48f, -20f}, {-58f, 0f}, {-46f, -6f}};
    private static final float[][] V3_E2 = {{-10f, 44f}, {10f, 42f}, {0f, 62f}, {6f, 34f}};
    private static final float[][] V3_E3 = {{52f, 86f}, {70f, 88f}, {60f, 102f}};

    private static final float[][] V4_GUARD = {{-16f, -36f}, {15f, -38f}};
    private static final float[][] V4_E1 = {{-82f, 40f}, {-56f, 34f}, {-62f, 62f}, {-46f, 50f}};
    private static final float[][] V4_E2 = {{48f, 30f}, {78f, 28f}, {66f, 50f}, {56f, 40f}};
    private static final float[][] V4_E3 = {{-2f, 94f}, {22f, 92f}, {10f, 110f}};

    // --------------------------------------------------------------- state
    private final int variant;
    private Node stageNode;
    private AssetManager assetManager;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    public JungleStage() {
        this(1);
    }

    public JungleStage(int variant) {
        this.variant = Math.max(1, variant);
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        this.assetManager = assetManager;
        stageNode = new Node("Jungle" + variant);
        parentNode.attachChild(stageNode);

        // terrain + collider ARE the mountains - there are no boundary walls here
        buildTerrain(assetManager, bulletAppState);
        buildAuthoredJungle(assetManager, bulletAppState);
        sealMountainRing(bulletAppState);
        // failsafe far below the basin, in case a physics glitch ever pushes the
        // player through the floor (the seal ring is unclimbable, so it should never trip)
        addSafetyFloor(bulletAppState);
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

        boolean fast = variant == 3;
        // was 7 + variant + loopCount/2 (8..11 on a first pass), which left the arenas
        // thin and the stages short. The authored groups hold 12-14 spots, so a higher
        // base fills a whole group per clearing while loopCount still compounds on top.
        int enemyCount = 12 + variant + loopCount;
        float scale = 1f + (variant - 1) * 0.35f;
        Random rand = new Random(66 + variant * 7 + loopCount);

        // authored groups only - the loop below just cycles them so a looping run
        // tops the arenas back up rather than inventing new positions
        float[][][] groups = variantGroups();
        int placed = 0;
        while (placed < enemyCount) {
            for (float[][] group : groups) {
                if (placed >= enemyCount) break;
                for (float[] spot : group) {
                    if (placed >= enemyCount) break;
                    float x = spot[0];
                    float z = spot[1];
                    // spawn above the spot; gravity settles them onto the real terrain
                    float y = profileOf(variant, x, z) + 4f;
                    String modelPath = ENEMY_MODELS[(variant + placed + rand.nextInt(3)) % ENEMY_MODELS.length];
                    EnemyController enemy = new EnemyController(
                            assetManager, stageNode, bulletAppState,
                            new Vector3f(x, y, z), 3, loopCount, scale, false, modelPath
                    );
                    // Jungle 3: speed-doubled hunters. Just movement - no stat inflation.
                    if (fast) {
                        enemy.setMoveSpeedMultiplier(FAST_STAGE_SPEED_MULTIPLIER);
                    }
                    enemies.add(enemy);
                    placed++;
                }
            }
        }
        return enemies;
    }

    private float[][][] variantGroups() {
        return switch (variant) {
            case 2 -> new float[][][]{V2_GUARD, V2_E1, V2_E2, V2_E3};
            case 3 -> new float[][][]{V3_GUARD, V3_E1, V3_E2, V3_E3};
            case 4 -> new float[][][]{V4_GUARD, V4_E1, V4_E2, V4_E3};
            default -> new float[][][]{V1_GUARD, V1_E1, V1_E2, V1_E3};
        };
    }

    @Override
    public Vector3f getPlayerSpawnPoint() {
        Arena spawn = spaces()[0];
        // sampled off the height field so the player never lands inside or above it
        return new Vector3f(spawn.x, profileOf(variant, spawn.x, spawn.z) + 2f, spawn.z);
    }

    /** Sampled so the exit portal rests on this stage's terrain instead of a fixed
     *  height, which would leave it buried in the raised northern shelves. */
    @Override
    public Vector3f getExitPortalGround() {
        Arena exit = spaces()[spaces().length - 1];
        return new Vector3f(exit.x, profileOf(variant, exit.x, exit.z), exit.z);
    }

    @Override
    public ColorRGBA getSkyColor() {
        return new ColorRGBA(0.35f, 0.55f, 0.4f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.35f, 0.6f, 0.35f, 1f).mult(0.7f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(0.5f, -0.7f, -0.3f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    @Override
    public String getName() {
        return "Jungle " + variant;
    }

    @Override
    public int getStageIndex() {
        return 4;
    }

    private Arena[] spaces() {
        return switch (variant) {
            case 2 -> V2_SPACES;
            case 3 -> V3_SPACES;
            case 4 -> V4_SPACES;
            default -> V1_SPACES;
        };
    }

    // ====================================================== the authored basin

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
                heights[iz * n + ix] = profileOf(variant, x, z);
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
                float shade = FastMath.clamp(1f - h * 0.007f, 0.74f, 1f); // gentle haze, no washout

                // Two low-frequency tints, both driven by noise already in the profile
                // rather than by a new noise field: the elevation gradient makes the
                // slopes cooler and the flats warmer, and a cheap sine cross-hatch puts
                // broad patches of mint into the cyan so the floor is not one flat wash.
                // Amplitudes are small on purpose - enough to read as variation at a
                // distance, not enough to look like a texture error up close.
                float slope01 = FastMath.clamp(FastMath.sqrt(dhdx * dhdx + dhdz * dhdz) / 1.2f, 0f, 1f);
                float patch = FastMath.sin(x * 0.031f + z * 0.017f)
                        * FastMath.sin(z * 0.024f - x * 0.011f);

                rgb[0] *= 1f - slope01 * 0.10f + patch * 0.07f;
                rgb[1] *= 1f + patch * 0.04f;
                rgb[2] *= 1f + slope01 * 0.06f - patch * 0.05f;

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

        Geometry ground = new Geometry("JungleGround", terrain);
        ground.setMaterial(mat);
        stageNode.attachChild(ground);

        MeshCollisionShape shape = new MeshCollisionShape(terrain);
        RigidBodyControl physics = new RigidBodyControl(shape, 0f);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    /** Pastel cyan jungle: a pale mint floor, cooler cyan mid slopes, a deeper lagoon
     *  tone in the hollows, blue-grey rock where it is genuinely steep, and a near-white
     *  frost at the peaks. The palette is deliberately high-key and low-contrast, so the
     *  warm trees read as the only saturated thing on screen.
     *
     *  <p>Kept well clear of full white: the terrain mesh is unlit vertex colour, so any
     *  value near 1.0 clips to white under the sun and loses the cyan.</p> */
    private float[] terrainColor(float x, float z, float h, float slope) {
        float[] floorMint = {0.56f, 0.95f, 0.84f};
        float[] midCyan = {0.40f, 0.88f, 0.92f};
        float[] lagoon = {0.30f, 0.80f, 0.90f};
        float[] rock = {0.66f, 0.78f, 0.88f};
        float[] frost = {0.82f, 0.94f, 0.97f};

        float tH = FastMath.clamp((h + 9f) / 46f, 0f, 1f);
        float[] rgb = lerp(floorMint, midCyan, Math.min(1f, tH * 1.7f));
        rgb = lerp(rgb, lagoon, FastMath.clamp((tH - 0.55f) / 0.45f, 0f, 1f));
        rgb = lerp(rgb, rock, FastMath.clamp((slope - 0.30f) / 0.9f, 0f, 1f));
        if (h > 50f) {
            rgb = lerp(rgb, frost, FastMath.clamp((h - 50f) / 70f, 0f, 1f));
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

    /** Closes the basin-to-mountain seam with an invisible ring of static blockers planted
     *  exactly on the innerFoot() contour - the base of the steep face. The player can
     *  stroll up the gentle collar and is stopped precisely where the rock starts, so
     *  the wall is never felt in open ground.
     *
     *  <p>The contour wobbles with angle rather than being a fixed circle: a circular
     *  ring would sit deep in the mountain at some headings and out in flat meadow at
     *  others, which is exactly how the old fixed-radius ring exposed itself.</p>
     *
     *  <p>A character capsule also rides up the steep triangles a couple of steps before
     *  hitting this, so it is what actually guarantees containment against a 29 u/s run,
     *  a 3.2-unit jump and a 12.6-unit dodge.</p> */
    private void sealMountainRing(BulletAppState bulletAppState) {
        float halfY = 40f;      // the wall spans y 0..80
        float halfRad = 6f;     // radial thickness; overlapping segments seal it
        int n = 48;
        for (int i = 0; i < n; i++) {
            float a = i * FastMath.TWO_PI / n;
            // plant this segment on the wobbling contour, and make it long enough to
            // overlap its neighbours at this radius
            float R = innerFoot(a) + SEAL_OFFSET;
            float halfTang = FastMath.PI * R / n + 3f;
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

    private void addSafetyFloor(BulletAppState bulletAppState) {
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(210f, 2f, 210f));
        RigidBodyControl physics = new RigidBodyControl(shape, 0f);
        physics.setPhysicsLocation(new Vector3f(0f, -46f, 0f));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    // --------------------------------------------------------- the height field

    /** Elevation for a point on this variant's terrain - the ONE source of truth for the
     *  mesh, the collider, prop placement, the spawn and the exit portal, all sampled
     *  from the same slope-relaxed grid. Static so the spawn and portal probes work
     *  before build() has run. */
    static float profileOf(int variant, float x, float z) {
        return sampleField(variant, x, z);
    }

    /** The authored, unconstrained basin floor for a variant: broad rolling forms,
     *  summit lumps and the flat arena discs. No mountain ring - that is added after
     *  slope relaxation so the ring can never be flattened into the basin. */
    private static float baseProfile(int variant, float x, float z) {
        return switch (variant) {
            case 2 -> profileV2(x, z);
            case 3 -> profileV3(x, z);
            case 4 -> profileV4(x, z);
            default -> profileV1(x, z);
        };
    }

    // one cached grid per variant; ~190 KB each, built on first touch
    private static final float[][] FIELDS = new float[4][];

    private static float[] field(int variant) {
        int v = (int) FastMath.clamp(variant, 1f, 4f);
        float[] h = FIELDS[v - 1];
        if (h != null) return h;

        int n = TERRAIN_SAMPLES;
        h = new float[n * n];
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                h[iz * n + ix] = baseProfile(v, ix * TERRAIN_STEP - TERRAIN_HALF,
                        iz * TERRAIN_STEP - TERRAIN_HALF);
            }
        }
        relaxBasin(h, n);
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                float x = ix * TERRAIN_STEP - TERRAIN_HALF;
                float z = iz * TERRAIN_STEP - TERRAIN_HALF;
                h[iz * n + ix] = mountainRing(h[iz * n + ix], x, z);
            }
        }
        FIELDS[v - 1] = h;
        return h;
    }

    /** Caps the basin's gradient at {@link #MAX_BASIN_SLOPE} by repeatedly easing any
     *  neighbouring pair that is too far apart toward each other.
     *
     *  <p>This exists because the authored gaussians and flat discs are composed by
     *  hand: a 20-unit hill next to a flat arena produces a cliff in the blend band
     *  however carefully the numbers are tuned, and the failure is invisible until a
     *  height is sampled. Relaxing the grid once, up front, makes "the whole basin is
     *  walkable" a property of the terrain rather than a hope. Arena interiors are
     *  already flat, so their exact heights survive untouched.</p> */
    private static void relaxBasin(float[] h, int n) {
        float maxDiff = MAX_BASIN_SLOPE * TERRAIN_STEP;
        // a diagonal neighbour is STEP*sqrt2 away, so it gets that much more headroom;
        // clamping only the four axis neighbours would still let the measured gradient
        // reach MAX_BASIN_SLOPE*sqrt2 on a corner.
        float diagDiff = maxDiff * 1.4142136f;
        int[] off = {1, -1, n, -n, n + 1, n - 1, 1 - n, -1 - n};
        float[] lim = {maxDiff, maxDiff, maxDiff, maxDiff, diagDiff, diagDiff, diagDiff, diagDiff};

        boolean[] soft = new boolean[n * n];
        for (int iz = 1; iz < n - 1; iz++) {
            for (int ix = 1; ix < n - 1; ix++) {
                float x = ix * TERRAIN_STEP - TERRAIN_HALF;
                float z = iz * TERRAIN_STEP - TERRAIN_HALF;
                soft[iz * n + ix] = x * x + z * z < RELAX_LIMIT * RELAX_LIMIT;
            }
        }

        // One half-space projection per vertex per pass: only the single most-violated
        // neighbour constraint is corrected, never all of them at once.
        //
        //  <p>Correcting every violated pair in a sweep looks stronger but it
        //  over-corrects: a vertex pinned by three neighbours at once gets yanked by
        //  the sum of all three overshoots and oscillates instead of settling, so
        //  whatever pass count is chosen the field keeps a residue of ~MAX_BASIN_SLOPE.
        //  Projecting onto the worst constraint alone is monotone and converges. The
        //  scan direction alternates so a violation propagates inward from both the
        //  collar and the map edge rather than only in scan order.</p>
        for (int pass = 0; pass < 400; pass++) {
            boolean moved = false;
            boolean forward = (pass & 1) == 0;
            for (int k = 1; k < n - 1; k++) {
                int iz = forward ? k : n - 2 - k;
                for (int m = 1; m < n - 1; m++) {
                    int ix = forward ? m : n - 2 - m;
                    int i = iz * n + ix;
                    if (!soft[i]) continue;

                    float worst = 0f;
                    int worstJ = -1;
                    for (int q = 0; q < off.length; q++) {
                        int j = i + off[q];
                        if (!soft[j]) continue;
                        float over = FastMath.abs(h[i] - h[j]) - lim[q];
                        if (over > worst) {
                            worst = over;
                            worstJ = j;
                        }
                    }
                    if (worstJ < 0) continue;

                    // ease the one worst pair together, each giving ground equally so
                    // the total elevation of the basin is preserved
                    float fix = worst * 0.5f * (h[i] > h[worstJ] ? 1f : -1f);
                    h[i] -= fix;
                    h[worstJ] += fix;
                    moved = true;
                }
            }
            if (!moved) break;
        }
    }

    /** Bilinear read of the grid, so props, spawn and portal sit on exactly the surface
     *  the mesh renders. */
    private static float sampleField(int variant, float x, float z) {
        int n = TERRAIN_SAMPLES;
        float[] h = field(variant);
        float fx = FastMath.clamp((x + TERRAIN_HALF) / TERRAIN_STEP, 0f, n - 1.0001f);
        float fz = FastMath.clamp((z + TERRAIN_HALF) / TERRAIN_STEP, 0f, n - 1.0001f);
        int x0 = (int) fx;
        int z0 = (int) fz;
        float tx = fx - x0;
        float tz = fz - z0;
        float a = h[z0 * n + x0];
        float b = h[z0 * n + x0 + 1];
        float c = h[(z0 + 1) * n + x0];
        float d = h[(z0 + 1) * n + x0 + 1];
        return (a + (b - a) * tx) * (1f - tz) + (c + (d - c) * tx) * tz;
    }

    private float heightAt(float x, float z) {
        return profileOf(variant, x, z);
    }

    /** Jungle 1 - "the canopy basin": a low spawn vale, a rise into an open clearing,
     *  a shallow hollow valley to the west, then the raised canopy ridge and a level
     *  exit plateau. */
    private static float profileV1(float x, float z) {
        float h = 0f;

        // --- a few broad, rolling forms ------------------------------------
        h += gaussian(-75f, -140f, 90f, 68f, 11f, x, z);  // NW broad rise
        h += gaussian(120f, -55f, 90f, 80f, 12f, x, z);   // SE rise (frames the ridge)
        h += gaussian(-130f, 50f, 70f, 90f, 9f, x, z);     // W bulge behind the valley
        h += gaussian(30f, 165f, 180f, 55f, 13f, x, z);    // long N slope up to the ring
        h += gaussian(10f, 72f, 85f, 42f, 10f, x, z);      // canopy ridge (the climb)
        h += gaussian(48f, 96f, 58f, 44f, 8f, x, z);      // shelf rise (E3)
        h += gaussian(0f, -58f, 78f, 55f, 6f, x, z);       // S basin
        h -= gaussian(-56f, 74f, 62f, 30f, 10f, x, z);     // carves the hollow valley (E2)
        h -= gaussian(0f, 20f, 55f, 22f, 4f, x, z);        // shallow dip before clearing 1

        // --- named giant summits, for layered silhouettes --------------------
        h += gaussian(-150f, 105f, 30f, 30f, 54f, x, z);
        h += gaussian(140f, -115f, 30f, 30f, 50f, x, z);
        h += gaussian(6f, 175f, 28f, 28f, 58f, x, z);
        h += gaussian(-178f, -26f, 32f, 32f, 47f, x, z);

        // --- flat spaces: real ground where people walk and fight ------------
        h = flattenTo(h, 0f, 0f, 30f, 12f, 0f, x, z);      // jungleSpawnArea
        h = flattenTo(h, 0f, 44f, 26f, 12f, -3f, x, z);    // jungleClearing1
        h = flattenTo(h, -56f, 74f, 28f, 12f, -6f, x, z);  // jungleValley
        h = flattenTo(h, 48f, 100f, 22f, 12f, 12f, x, z);  // jungleRidgeClearing (raised)
        h = flattenTo(h, 0f, 132f, 16f, 10f, 12f, x, z);   // jungleExitArea - same target,
        // so it merges with the shelf into one level plateau instead of ramping down

        return h;
    }

    /** Jungle 2 - "the twin gullies": two long clearings split by the spawn vale,
     *  converging on a raised north shelf and a level exit plateau. */
    private static float profileV2(float x, float z) {
        float h = 0f;
        h += gaussian(0f, -58f, 76f, 92f, 7f, x, z);      // S basin
        h += gaussian(-68f, 22f, 58f, 48f, 8f, x, z);     // W hump dividing the gullies
        h += gaussian(68f, 22f, 58f, 48f, 8f, x, z);      // E hump
        h += gaussian(-40f, -120f, 92f, 58f, 12f, x, z);  // SW rise
        h += gaussian(30f, 135f, 95f, 58f, 9f, x, z);     // N rise into the shelf
        h -= gaussian(-62f, 56f, 58f, 28f, 10f, x, z);    // west gully
        h -= gaussian(62f, 56f, 58f, 28f, 10f, x, z);     // east gully
        h += gaussian(-165f, 95f, 30f, 30f, 54f, x, z);   // summit NW
        h += gaussian(120f, 150f, 28f, 28f, 52f, x, z);   // summit NE
        h += gaussian(36f, -172f, 30f, 30f, 58f, x, z);   // summit S
        h += gaussian(-62f, -180f, 30f, 30f, 50f, x, z);  // summit SW
        h = flattenTo(h, 0f, 0f, 30f, 12f, 0f, x, z);      // jungleSpawnArea
        h = flattenTo(h, -62f, 56f, 26f, 12f, -5f, x, z);  // jungleWestGully
        h = flattenTo(h, 62f, 56f, 26f, 12f, -5f, x, z);   // jungleEastGully
        h = flattenTo(h, 0f, 108f, 24f, 12f, 10f, x, z);   // jungleNorthShelf (raised)
        h = flattenTo(h, 0f, 128f, 16f, 10f, 10f, x, z);   // jungleExitArea - same target
        return h;
    }

    /** Jungle 3 - "the ridge line": spawn south of an east-west ridge, a west gully,
     *  a long northward valley clearing and a raised east shelf. */
    private static float profileV3(float x, float z) {
        float h = 0f;
        h += gaussian(0f, 16f, 125f, 40f, 11f, x, z);     // E-W ridge spine
        h += gaussian(0f, -60f, 88f, 58f, 7f, x, z);      // S basin (spawn)
        h += gaussian(0f, 50f, 90f, 46f, 9f, x, z);       // N valley clearing
        h += gaussian(-100f, 22f, 64f, 48f, 7f, x, z);    // W hump
        h += gaussian(60f, 92f, 56f, 44f, 9f, x, z);      // E shelf
        h -= gaussian(-58f, -6f, 58f, 28f, 10f, x, z);    // west gully
        h -= gaussian(0f, 50f, 62f, 28f, 9f, x, z);       // clears the N clearing
        // Summits sit at or beyond the mountain foot on purpose: a named summit planted
        // at r~155 sits inside the relaxation disc, and its unrelaxed inner flank reads
        // as a cliff in the playable basin rather than as part of the rim.
        h += gaussian(-40f, 182f, 30f, 30f, 55f, x, z);   // summit N
        h += gaussian(174f, -36f, 30f, 30f, 52f, x, z);   // summit E
        h += gaussian(-182f, 12f, 30f, 30f, 48f, x, z);   // summit W
        h += gaussian(102f, -158f, 30f, 30f, 50f, x, z);  // summit SE
        h = flattenTo(h, 0f, -56f, 28f, 12f, 0f, x, z);    // jungleSpawnArea
        h = flattenTo(h, -58f, -6f, 26f, 12f, -5f, x, z);  // jungleWestGully
        h = flattenTo(h, 0f, 50f, 24f, 12f, -3f, x, z);    // jungleNorthClearing
        h = flattenTo(h, 60f, 92f, 22f, 12f, 12f, x, z);   // jungleEastShelf (raised)
        h = flattenTo(h, 0f, 130f, 16f, 10f, 12f, x, z);   // jungleExitArea - same target
        return h;
    }

    /** Jungle 4 - "the deep hollow": the broadest basin, with the lowest authored
     *  ground, a north-east grove and a raised northern shelf. */
    private static float profileV4(float x, float z) {
        float h = 0f;
        h += gaussian(0f, 0f, 145f, 115f, 6f, x, z);      // broad basin floor
        h += gaussian(-70f, 40f, 80f, 55f, 8f, x, z);     // hollow shoulder
        h += gaussian(68f, 34f, 74f, 54f, 8f, x, z);      // NE grove shoulder
        h += gaussian(-30f, -112f, 105f, 58f, 10f, x, z); // S rise
        h += gaussian(16f, 145f, 100f, 54f, 13f, x, z);    // N rise into the shelf
        h -= gaussian(-64f, 46f, 62f, 30f, 12f, x, z);    // carves the deep hollow
        h -= gaussian(0f, -56f, 76f, 38f, 5f, x, z);      // shallow dip south of spawn
        h += gaussian(-150f, 145f, 30f, 30f, 58f, x, z);  // summit NW
        h += gaussian(150f, 132f, 30f, 30f, 62f, x, z);   // summit NE
        h += gaussian(-170f, 135f, 30f, 30f, 50f, x, z);  // summit far W
        h += gaussian(158f, -118f, 30f, 30f, 54f, x, z);  // summit SE
        h = flattenTo(h, 0f, -20f, 30f, 12f, 0f, x, z);    // jungleSpawnArea
        h = flattenTo(h, -64f, 46f, 30f, 12f, -8f, x, z); // jungleDeepHollow (lowest)
        h = flattenTo(h, 64f, 36f, 26f, 12f, -2f, x, z);  // jungleNortheastGrove
        h = flattenTo(h, 10f, 100f, 24f, 12f, 13f, x, z); // jungleNorthShelf (raised)
        h = flattenTo(h, 0f, 130f, 16f, 10f, 13f, x, z);  // jungleExitArea - same target
        return h;
    }

    /** The shared ringed mountain wall every variant sits inside: a rolling green
     *  collar, a near forest-rim band, a far peak band, and a blanket step that makes
     *  the mesh edge a towering unclimbable ridge on all sides. */
    private static float mountainRing(float h, float x, float z) {
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

    /** Steepest grade an arena's meadow rim is allowed to use while easing down to its
     *  floor. Deliberately gentler than {@link #MAX_BASIN_SLOPE}: a combat clearing
     *  should be entered by a stroll, not a scramble. */
    private static final float MAX_MEADOW_GRADE = 0.55f;

    /** Pulls the accumulated height toward `target` inside a flat zone, so a meadow rim
     *  is a gentle slope rather than a step.
     *
     *  <p>The blend width is computed from the height it actually has to absorb: a flat
     *  disc whose floor is 21 units below the surrounding ground needs a far wider
     *  apron than one cut into a hillside at the same level. A fixed blend made every
     *  such rim a cliff, and post-hoc slope relaxation could only smear those cliffs
     *  into long uniform ramps. Deriving the width here keeps the shape the layout
     *  intended - a bowl with a walkable lip.</p> */
    private static float flattenTo(float h, float cx, float cz, float r,
                                   float blend, float target, float x, float z) {
        float d = dist(x, z, cx, cz);
        float drop = FastMath.abs(target - h);
        // smooth01 peaks at a gradient of 1.5 (it is a smoothstep), so the rim's steepest
        // grade is 1.5*drop/w, not drop/w. Sizing the apron from the peak keeps the rim
        // inside MAX_MEADOW_GRADE instead of overshooting it by half again.
        float need = 1.5f * drop / MAX_MEADOW_GRADE;
        float w = Math.max(blend, need);
        if (d >= r + w) return h;
        float m = 1f - smooth01((d - r) / w); // 1 inside r, 0 once outside
        return h + (target - h) * m;
    }

    // ----------------------------------------------------- the authored jungle

    /** Hand-placed tree stands, rocks and undergrowth. Open ground is the point:
     *  stands frame the routes and ring the clearings, they never fill a fight space.
     *  Every coordinate is authored; {@link Random} only chooses which of the existing
     *  models is used and how it is turned and scaled. */
    private void buildAuthoredJungle(AssetManager assetManager, BulletAppState bulletAppState) {
        int trees = switch (variant) {
            case 2 -> jungleV2(assetManager, bulletAppState);
            case 3 -> jungleV3(assetManager, bulletAppState);
            case 4 -> jungleV4(assetManager, bulletAppState);
            default -> jungleV1(assetManager, bulletAppState);
        };
        int extra = thickenAuthoredStands(assetManager, bulletAppState);
        int scatter = scatterUnderstory(assetManager);
        System.out.println("[Jungle " + variant + "] " + (trees + extra) + " trees ("
                + trees + " authored + " + extra + " stand companions), "
                + scatter + " plants.");
    }

    /** Every tree the authored stands actually placed, in order. Recorded during
     *  {@link #plantStand} so the thickening pass can build on the composition instead
     *  of re-deriving it. */
    private final List<float[]> authoredTreeSpots = new ArrayList<>();

    /** Adds a partner tree to each authored stand, roughly doubling the canopy.
     *
     *  <p>The earlier attempt at this scattered trees on a jittered grid, which put the
     *  extras in even rows across open ground and read as an orchard rather than as
     *  forest. Growing each extra from an existing stand instead keeps every addition
     *  attached to something intentional: a stand becomes a cluster of two or three
     *  trees of mixed scale and tint, which is what the authored coordinates were
     *  already describing, just fuller.</p>
     *
     *  <p>Partners are offset on a per-spot bearing so a stand thickens outward rather
     *  than every tree doubling straight up on top of its neighbour. Rejected by the
     *  same {@link #blocksSpace} rule as everything else, so no cluster grows into an
     *  arena, and a minimum gap stops trunks interpenetrating.</p> */
    private int thickenAuthoredStands(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(4_100L + variant * 977L);
        int planted = 0;

        // How many extra trees each authored stand tries to grow. Four attempts against
        // the arena, slope and gap tests settles at roughly three accepted, which is
        // what turns ~50 surviving authored stands into ~200 - a canopy that actually
        // closes over a 330-unit basin. One attempt per stand doubled the count on paper
        // and still looked like a handful, because the basin is far larger than the
        // authored composition was scaled for.
        final int triesPerStand = 4;

        List<float[]> taken = new ArrayList<>(authoredTreeSpots);
        for (float[] spot : authoredTreeSpots) {
            for (int i = 0; i < triesPerStand; i++) {
                // Fan the companions out around the original rather than stacking them
                // on one bearing, so a stand thickens into a clump instead of a line
                float bearing = rand.nextFloat() * FastMath.TWO_PI;
                float off = 8f + rand.nextFloat() * 17f;
                float x = spot[0] + FastMath.cos(bearing) * off;
                float z = spot[1] + FastMath.sin(bearing) * off;

                if (!blocksSpace(x, z)) continue;

                boolean clash = false;
                for (float[] other : taken) {
                    if (dist(x, z, other[0], other[1]) < 9f) {
                        clash = true;
                        break;
                    }
                }
                if (clash) continue;

                if (!plantTree(assetManager, bulletAppState, x, z, rand)) continue;
                taken.add(new float[]{x, z});
                planted++;
            }
        }
        return planted;
    }

    // ----------------------------------------------------- the scattered understory


    /** Ferns, flowers, grass tufts and a few mushrooms, spread over the whole basin.
     *
     *  <p>Scattered rather than authored because ground cover does not need to be
     *  legible as level design - it needs to look natural everywhere. Positions come
     *  from a jittered grid instead of uniform randomness: pure random clumps visibly,
     *  because plants reject their own neighbours and the survivors end up in bunches.
     *  Jittering one candidate per cell gives even coverage with organic irregularity,
     *  and seeding per variant makes it stable across stage rebuilds.</p>
     *
     *  <p>No colliders. These are ankle-high; a hitbox per plant would be hundreds of
     *  static bodies the player bumps into and cannot walk through, which is worse than
     *  walking through visible foliage. The player passes over them and they never
     *  obstruct a route.</p> */
    private int scatterUnderstory(AssetManager assetManager) {
        Random rand = new Random(9_000L + variant * 131L);
        int planted = 0;

        // One jittered candidate per cell across the walkable basin. Cell 9 is roughly 2.25x
        // sparser than the 6 it replaced (cell area goes as the square), and only one
        // candidate in five is a flower. Both cuts target the same cost: petal clusters
        // are the heaviest ground-cover meshes, and the frame cost of the understory is
        // almost entirely the number of spatials, not their size.
        float cell = 9f;
        float reach = innerFoot(FastMath.HALF_PI) - COLLAR_WIDTH - 8f;
        for (float gx = -reach; gx <= reach; gx += cell) {
            for (float gz = -reach; gz <= reach; gz += cell) {
                // Bias each plant toward a random corner of its cell rather than the
                // centre, and let plants be dropped entirely on a coin flip weighted by
                // distance from the middle. That breaks the visible grid: at this cell
                // size a centred jitter still reads as rows, and an evenly-filled grid
                // reads as wallpaper however random the offsets are.
                float x = gx + (rand.nextFloat() - 0.5f) * cell * 1.7f;
                float z = gz + (rand.nextFloat() - 0.5f) * cell * 1.7f;
                float r = FastMath.sqrt(x * x + z * z);
                if (r > reach) continue;

                // thin toward the foothills so the scatter fades out instead of ending
                // at an obvious circle, but never below a third of the density
                float edge = FastMath.clamp((reach - r) / (reach * 0.35f), 0.32f, 1f);
                if (rand.nextFloat() > edge) continue;

                if (!understoryAllowed(x, z)) continue;

                // one flower in five: enough to punctuate the fern-and-grass layer with colour,
                // not enough for the petals to dominate the frame rate
                boolean flower = rand.nextFloat() < 0.2f;
                String model = flower
                        ? UNDERSTORY_FLOWERS[rand.nextInt(UNDERSTORY_FLOWERS.length)]
                        : UNDERSTORY_FERN_GRASS[rand.nextInt(UNDERSTORY_FERN_GRASS.length)];
                float scale = 0.55f + rand.nextFloat() * 0.75f;
                // the folder mixes a knee-high fern with a waist-high flower, so per-model scale
                // bands keep them all reading as the same layer of ground cover
                if (model.contains("Mushroom")) scale = 0.4f + rand.nextFloat() * 0.3f;
                if (model.contains("Flower")) scale = 0.45f + rand.nextFloat() * 0.35f;
                if (model.contains("Plant Big")) scale = 0.5f + rand.nextFloat() * 0.4f;
                if (model.contains("Plant")) scale = 0.55f + rand.nextFloat() * 0.4f;
                if (model.contains("Fern")) scale = 0.6f + rand.nextFloat() * 0.45f;
                if (model.contains("Grass")) scale = 0.7f + rand.nextFloat() * 0.5f;

                ScalePlacement placed = placeFlatTerrain(stageNode, assetManager, model,
                        x, z, scale, rand);
                placed.spatial.setLocalTranslation(x, placed.baseY + heightAt(x, z), z);

                ColorRGBA tint = UNDERSTORY_TINTS[rand.nextInt(UNDERSTORY_TINTS.length)];
                fixEnvironmentMaterials(placed.spatial, tint);
                tintFoliage(placed.spatial, tint);
                planted++;
            }
        }
        return planted;
    }

    /** Where undergrowth may grow: off the steep faces, off the mountain collar, and
     *  clear of the exact spot the player and the exit portal occupy. Unlike a tree it
     *  may sit inside an arena - low cover in a clearing is the point of it. */
    private boolean understoryAllowed(float x, float z) {
        float th = FastMath.atan2(z, x);
        float r = FastMath.sqrt(x * x + z * z);
        if (r > innerFoot(th) - COLLAR_WIDTH) return false;
        if (tooSteep(x, z)) return false;

        for (Arena a : spaces()) {
            float d = dist(x, z, a.x, a.z);
            // only the spawn pad and the portal pad need to stay completely bare
            if ((a == spaces()[0] || a == spaces()[spaces().length - 1]) && d < 6f) {
                return false;
            }
        }
        return true;
    }

    /** True where a tree would block a route, an arena or the portal. Used to keep the
     *  authored clusters honest without hand-verifying every coordinate. */
    private boolean blocksSpace(float x, float z) {
        for (Arena a : spaces()) {
            if (dist(x, z, a.x, a.z) < a.r + 8f) return true;
        }
        // keep off the mountain faces: the collar begins at the innermost innerFoot(),
        // and the seal stands a little beyond it
        float th = FastMath.atan2(z, x);
        if (FastMath.sqrt(x * x + z * z) > innerFoot(th) - COLLAR_WIDTH - 6f) return true;
        return tooSteep(x, z);
    }

    /** Terrain gradient magnitude at a point, sampled from the same profile the mesh uses. */
    private boolean tooSteep(float x, float z) {
        float e = TERRAIN_STEP;
        float dx = (profileOf(variant, x + e, z) - profileOf(variant, x - e, z)) / (2f * e);
        float dz = (profileOf(variant, x, z + e) - profileOf(variant, x, z - e)) / (2f * e);
        return FastMath.sqrt(dx * dx + dz * dz) > 0.55f;
    }

    private int plantStand(AssetManager assetManager, BulletAppState bulletAppState,
                           Random rand, float[][] spots) {
        int planted = 0;
        for (float[] p : spots) {
            if (plantTree(assetManager, bulletAppState, p[0], p[1], rand)) {
                planted++;
                // remembered so thickenAuthoredStands() can grow a partner from it
                authoredTreeSpots.add(new float[]{p[0], p[1]});
            }
        }
        return planted;
    }

    /** Jungle 1 - "the canopy basin". A vale framed by palms, a route north marked by
     *  paired trees, a valley wood, a ridge stand and a foothill backdrop. */
    private int jungleV1(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(420 + variant);
        int trees = 0;

        // frames the spawn vale without entering it
        float[][] spawnFrame = {
                {36f, -20f}, {32f, 20f}, {-34f, 20f}, {-38f, -16f}, {-20f, 34f},
                {22f, 34f}, {-46f, 4f}, {46f, 2f}, {-14f, -38f}, {16f, -36f}
        };
        // paired trees marking the route north out of the vale toward clearing 1
        float[][] trailPairs = {
                {-28f, 30f}, {30f, 30f}, {-30f, 44f}, {32f, 44f}, {-30f, 58f}, {34f, 58f}
        };
        // woodland ringing the hollow valley (E2) - keeps the floor open
        float[][] valleyWood = {
                {-92f, 62f}, {-84f, 52f}, {-96f, 84f}, {-88f, 100f}, {-78f, 104f},
                {-40f, 68f}, {-36f, 88f}, {-100f, 56f}, {-34f, 84f}, {-58f, 112f}
        };
        // the canopy ridge stand (E3), sitting high above the valley
        float[][] ridgeStand = {
                {30f, 108f}, {68f, 108f}, {28f, 126f}, {70f, 124f}, {40f, 96f},
                {80f, 108f}, {22f, 118f}, {82f, 92f}
        };
        // the open ground between clearing 1 and the valley - sparse by design
        float[][] hollow = {
                {-18f, 60f}, {12f, 66f}, {-14f, 78f}, {10f, 80f}, {-24f, 70f}, {24f, 62f}
        };
        // foothill backdrop, thin, to close the sight-line at the rim
        float[][] backdrop = {
                {-116f, 20f}, {-108f, -46f}, {-88f, -84f}, {-44f, -74f}, {10f, -76f},
                {58f, -68f}, {98f, -40f}, {116f, 6f}, {104f, 60f}, {92f, 92f},
                {56f, 132f}, {-30f, 138f}, {-86f, 124f}, {-112f, 88f}, {-120f, 40f},
                {-70f, -112f}, {30f, -110f}, {96f, -96f}, {104f, 128f}, {-108f, 128f}
        };

        trees += plantStand(assetManager, bulletAppState, rand, spawnFrame);
        trees += plantStand(assetManager, bulletAppState, rand, trailPairs);
        trees += plantStand(assetManager, bulletAppState, rand, valleyWood);
        trees += plantStand(assetManager, bulletAppState, rand, ridgeStand);
        trees += plantStand(assetManager, bulletAppState, rand, hollow);
        trees += plantStand(assetManager, bulletAppState, rand, backdrop);

        plantBoulders(assetManager, bulletAppState, rand, new Boulder[]{
                new Boulder(32f, 24f, 5.5f),      // marks the foot of the climb
                new Boulder(-34f, 52f, 4.5f),
                new Boulder(-98f, 76f, 6.0f),
                new Boulder(76f, 116f, 5.0f),
                new Boulder(-46f, -46f, 4.0f),
                new Boulder(64f, -30f, 5.0f),
                new Boulder(28f, 118f, 5.0f)
        });
        plantBushes(assetManager, rand, new float[][]{
                {-18f, 26f}, {20f, 28f}, {-30f, 42f}, {32f, 44f}, {0f, 66f}, {-6f, 74f},
                {-44f, 74f}, {-74f, 82f}, {-92f, 66f}, {40f, 94f}, {60f, 106f}, {44f, 122f},
                {-34f, 110f}, {-62f, 46f}, {70f, 48f}, {-100f, 18f}, {86f, -6f}, {20f, -60f}
        });
        plantGrass(assetManager, rand, new float[][]{
                {0f, 20f}, {-10f, 24f}, {12f, 22f}, {0f, 40f}, {-14f, 44f}, {16f, 44f},
                {0f, 58f}, {-20f, 64f}, {24f, 66f}, {-6f, 84f}, {12f, 86f}, {-40f, 82f},
                {-62f, 90f}, {-80f, 78f}, {44f, 88f}, {62f, 96f}, {74f, 108f}, {50f, 116f},
                {-26f, 128f}, {16f, 140f}, {-50f, 106f}, {-92f, 40f}, {-108f, 66f},
                {92f, 30f}, {70f, -20f}, {34f, -54f}, {-40f, -34f}, {30f, 112f}
        });
        return trees;
    }

    /** Jungle 2 - "the twin gullies". Two parallel woods flanking the gullies, a stand
     *  on the shelf between them, and a wide open central corridor. */
    private int jungleV2(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(421 + variant);
        int trees = 0;

        float[][] spawnFrame = {
                {36f, -18f}, {-36f, -18f}, {34f, 20f}, {-34f, 20f}, {-18f, 34f},
                {20f, 34f}, {-46f, 0f}, {46f, 0f}, {-12f, -36f}, {14f, -34f}
        };
        // the woods between the gullies and the spawn vale - the dividing ridge
        float[][] dividingStand = {
                {-34f, 40f}, {36f, 40f}, {-30f, 12f}, {32f, 12f}, {-16f, 66f}, {18f, 66f},
                {-40f, 88f}, {42f, 88f}, {0f, 84f}, {-8f, 44f}
        };
        float[][] westWood = {
                {-94f, 40f}, {-88f, 78f}, {-80f, 96f}, {-100f, 62f}, {-72f, 46f},
                {-104f, 86f}, {-66f, 30f}, {-110f, 52f}
        };
        float[][] eastWood = {
                {94f, 40f}, {88f, 78f}, {80f, 96f}, {100f, 62f}, {72f, 46f},
                {104f, 86f}, {66f, 30f}, {110f, 52f}
        };
        // the shelf that guards the exit, framing the new E3 at z=108 and exit at z=128
        float[][] shelfStand = {
                {-32f, 104f}, {34f, 104f}, {-26f, 124f}, {28f, 124f}, {-40f, 116f},
                {42f, 116f}, {-30f, 136f}, {32f, 136f}
        };
        float[][] backdrop = {
                {-118f, -20f}, {-104f, -70f}, {-60f, -100f}, {0f, -112f}, {60f, -100f},
                {104f, -70f}, {118f, -20f}, {116f, 40f}, {96f, 96f}, {58f, 124f},
                {0f, 134f}, {-58f, 124f}, {-96f, 96f}, {-116f, 40f}, {-84f, -126f},
                {84f, -126f}, {92f, 122f}, {-92f, 122f}
        };

        trees += plantStand(assetManager, bulletAppState, rand, spawnFrame);
        trees += plantStand(assetManager, bulletAppState, rand, dividingStand);
        trees += plantStand(assetManager, bulletAppState, rand, westWood);
        trees += plantStand(assetManager, bulletAppState, rand, eastWood);
        trees += plantStand(assetManager, bulletAppState, rand, shelfStand);
        trees += plantStand(assetManager, bulletAppState, rand, backdrop);

        plantBoulders(assetManager, bulletAppState, rand, new Boulder[]{
                new Boulder(-22f, 26f, 5f), new Boulder(24f, 26f, 4.5f),
                new Boulder(0f, 74f, 6f), new Boulder(-96f, 62f, 5.5f),
                new Boulder(96f, 62f, 5.5f), new Boulder(-34f, 126f, 5f),
                new Boulder(36f, 126f, 5f), new Boulder(0f, -84f, 6f)
        });
        plantBushes(assetManager, rand, new float[][]{
                {-20f, 22f}, {22f, 22f}, {-16f, 44f}, {18f, 44f}, {0f, 62f}, {-4f, 78f},
                {-46f, 52f}, {-82f, 56f}, {-88f, 84f}, {48f, 52f}, {84f, 58f}, {90f, 86f},
                {-26f, 100f}, {28f, 100f}, {0f, 116f}, {-40f, 116f}, {42f, 116f},
                {-70f, -40f}, {70f, -40f}, {0f, -60f}
        });
        plantGrass(assetManager, rand, new float[][]{
                {0f, 18f}, {-12f, 22f}, {14f, 22f}, {-18f, 32f}, {20f, 32f},
                {-24f, 46f}, {26f, 46f}, {-20f, 60f}, {22f, 60f}, {0f, 68f}, {-8f, 90f},
                {10f, 90f}, {-40f, 66f}, {-60f, 44f}, {62f, 44f}, {-44f, 78f}, {44f, 78f},
                {-30f, 96f}, {32f, 96f}, {-16f, 114f}, {18f, 114f}, {0f, 130f}, {-28f, 140f},
                {-76f, 70f}, {78f, 70f}, {-100f, 20f}, {100f, 20f}
        });
        return trees;
    }

    /** Jungle 3 - "the ridge line". Spawn sits in the south basin; the route runs west
     *  into the gully, then north the length of the valley to the east shelf. */
    private int jungleV3(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(422 + variant);
        int trees = 0;

        float[][] spawnFrame = {
                {34f, -78f}, {-34f, -78f}, {30f, -40f}, {-30f, -40f}, {-16f, -22f},
                {18f, -24f}, {-46f, -60f}, {46f, -58f}, {-10f, -98f}, {12f, -96f}
        };
        // the ridge spine that separates spawn from the valley
        float[][] spineStand = {
                {-30f, -34f}, {32f, -34f}, {-24f, -18f}, {26f, -16f}, {-36f, 4f},
                {38f, 6f}, {-16f, 2f}, {18f, 4f}
        };
        float[][] gullyWood = {
                {-84f, -30f}, {-76f, 6f}, {-84f, 22f}, {-96f, -6f}, {-66f, -14f},
                {-100f, 18f}, {-70f, 26f}
        };
        // woodland along the north clearing's edges, leaving its floor open
        float[][] valleyWood = {
                {-28f, 34f}, {-20f, 74f}, {38f, 44f}, {40f, 78f}, {26f, 86f},
                {-30f, 64f}, {52f, 62f}, {-24f, 90f}, {0f, 26f}
        };
        float[][] shelfStand = {
                {44f, 92f}, {86f, 92f}, {52f, 118f}, {86f, 116f}, {68f, 128f},
                {42f, 108f}, {92f, 106f}
        };
        float[][] backdrop = {
                {-118f, -70f}, {-112f, -10f}, {-96f, 60f}, {-52f, 112f}, {0f, 140f},
                {56f, 138f}, {108f, 96f}, {120f, 20f}, {104f, -60f}, {58f, -104f},
                {0f, -116f}, {-58f, -104f}, {104f, -110f}, {-40f, 136f}, {36f, 138f}
        };

        trees += plantStand(assetManager, bulletAppState, rand, spawnFrame);
        trees += plantStand(assetManager, bulletAppState, rand, spineStand);
        trees += plantStand(assetManager, bulletAppState, rand, gullyWood);
        trees += plantStand(assetManager, bulletAppState, rand, valleyWood);
        trees += plantStand(assetManager, bulletAppState, rand, shelfStand);
        trees += plantStand(assetManager, bulletAppState, rand, backdrop);

        plantBoulders(assetManager, bulletAppState, rand, new Boulder[]{
                new Boulder(-20f, -48f, 5.5f), new Boulder(22f, -46f, 5f),
                new Boulder(0f, -8f, 6f), new Boulder(-44f, 12f, 5f),
                new Boulder(44f, 30f, 5.5f), new Boulder(84f, 84f, 5f),
                new Boulder(-70f, 66f, 4.5f), new Boulder(14f, 120f, 6f)
        });
        plantBushes(assetManager, rand, new float[][]{
                {-18f, -44f}, {20f, -46f}, {-14f, -76f}, {16f, -74f}, {-24f, -18f},
                {26f, -16f}, {-44f, -30f}, {-78f, -12f}, {-66f, 14f},
                {-8f, 40f}, {30f, 46f}, {-20f, 72f}, {34f, 74f}, {16f, 88f},
                {-36f, 78f}, {56f, 88f}, {76f, 110f}, {-60f, 46f}, {40f, -84f}, {0f, -66f}
        });
        plantGrass(assetManager, rand, new float[][]{
                {0f, -66f}, {-14f, -62f}, {16f, -62f}, {-22f, -40f}, {24f, -40f},
                {-18f, -24f}, {20f, -22f}, {-30f, -4f}, {32f, -2f}, {-46f, 20f},
                {-80f, -20f}, {-70f, 20f}, {-10f, 30f}, {24f, 28f},
                {40f, 54f}, {-16f, 66f}, {18f, 80f}, {-26f, 86f}, {44f, 70f},
                {56f, 100f}, {80f, 118f}, {-34f, 96f},
                {0f, 130f}, {-84f, 34f}, {88f, 40f}, {46f, -96f}
        });
        return trees;
    }

    /** Jungle 4 - "the deep hollow". The widest basin: a deep south-west hollow, a
     *  north-east grove, and a long approach north to the shelf and exit. */
    private int jungleV4(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(423 + variant);
        int trees = 0;

        float[][] spawnFrame = {
                {36f, -28f}, {-34f, -28f}, {32f, 8f}, {-32f, 8f}, {-18f, 22f},
                {20f, 24f}, {-46f, -12f}, {46f, -10f}, {-12f, -46f}, {14f, -44f}
        };
        float[][] hollowWood = {
                {-100f, 34f}, {-92f, 70f}, {-104f, 62f}, {-88f, 22f}, {-84f, 84f},
                {-50f, 18f}, {-58f, 88f}, {-110f, 46f}, {-70f, 66f}, {-44f, 76f}
        };
        // the open shelf of ground between the hollow and the grove - deliberately empty
        float[][] sparseMid = {
                {-24f, 44f}, {6f, 40f}, {-14f, 12f}, {24f, 20f}, {2f, 66f}
        };
        float[][] groveWood = {
                {92f, 26f}, {88f, 66f}, {78f, 84f}, {102f, 48f}, {70f, 34f},
                {96f, 88f}, {42f, 14f}, {58f, 84f}
        };
        float[][] shelfStand = {
                {-26f, 100f}, {52f, 100f}, {-20f, 126f}, {46f, 124f}, {-6f, 74f},
                {-34f, 116f}, {36f, 116f}
        };
        float[][] backdrop = {
                {-116f, -40f}, {-104f, -86f}, {-56f, -112f}, {4f, -116f}, {62f, -104f},
                {110f, -66f}, {122f, 6f}, {116f, 74f}, {88f, 122f},
                {36f, 140f}, {-22f, 142f}, {-76f, 126f},
                {-122f, 92f}, {-126f, 18f}, {-70f, 148f},
                {72f, -120f}
        };

        trees += plantStand(assetManager, bulletAppState, rand, spawnFrame);
        trees += plantStand(assetManager, bulletAppState, rand, hollowWood);
        trees += plantStand(assetManager, bulletAppState, rand, sparseMid);
        trees += plantStand(assetManager, bulletAppState, rand, groveWood);
        trees += plantStand(assetManager, bulletAppState, rand, shelfStand);
        trees += plantStand(assetManager, bulletAppState, rand, backdrop);

        plantBoulders(assetManager, bulletAppState, rand, new Boulder[]{
                new Boulder(-30f, 24f, 5.5f), new Boulder(28f, 6f, 5f),
                new Boulder(-108f, 52f, 6f), new Boulder(-76f, 78f, 5f),
                new Boulder(88f, 44f, 5.5f), new Boulder(66f, 78f, 5f),
                new Boulder(-24f, 112f, 5.5f), new Boulder(34f, 112f, 5f),
                new Boulder(0f, 78f, 6f), new Boulder(-52f, -70f, 5f)
        });
        plantBushes(assetManager, rand, new float[][]{
                {-18f, -18f}, {20f, -16f}, {-26f, 4f}, {28f, 8f}, {-38f, 34f}, {42f, 30f},
                {-64f, 42f}, {-88f, 56f}, {-80f, 88f}, {-48f, 76f}, {56f, 44f}, {80f, 52f},
                {90f, 80f}, {64f, 74f}, {-20f, 96f}, {26f, 96f}, {0f, 110f},
                {-36f, 118f}, {40f, 116f}, {-100f, 4f}, {100f, -20f}, {0f, -70f}
        });
        plantGrass(assetManager, rand, new float[][]{
                {0f, -18f}, {-14f, -14f}, {16f, -12f}, {-20f, 0f}, {22f, 2f}, {-30f, 26f},
                {32f, 24f}, {-46f, 40f}, {-70f, 46f}, {-90f, 66f}, {-64f, 80f},
                {-40f, 60f}, {10f, 30f}, {34f, 40f}, {56f, 56f}, {76f, 66f}, {88f, 84f},
                {-18f, 82f}, {20f, 84f}, {-26f, 104f}, {30f, 104f}, {0f, 122f}, {-40f, 128f},
                {-104f, 84f}, {104f, 34f}, {-70f, -50f}, {66f, -56f}
        });
        return trees;
    }

    // ------------------------------------------------------------- placement

    /** Plants one tree unless the spot is reserved for a route, an arena or the portal.
     *  Returns false when the coordinate was skipped, so the console count stays honest. */
    private boolean plantTree(AssetManager assetManager, BulletAppState bulletAppState,
                              float x, float z, Random rand) {
        if (blocksSpace(x, z)) return false;

        String model = TREE_MODELS[rand.nextInt(TREE_MODELS.length)];
        boolean legacy = model.startsWith("Models/Environment/Forest/");
        float baseScale = 4f;
        if (model.contains("palm")) baseScale = 5f;
        if (model.contains("detailed")) baseScale = 4.2f;
        if (model.contains("oak")) baseScale = 3.5f;
        if (model.contains("fat")) baseScale = 4f;
        if (model.contains("default")) baseScale = 4.5f;
        if (!legacy) baseScale = 2.6f;
        float scale = baseScale + rand.nextFloat() * 0.8f;

        float y = heightAt(x, z);
        ScalePlacement placed = placeFlatTerrain(stageNode, assetManager, model, x, z, scale, rand);
        placed.spatial.setLocalTranslation(x, placed.baseY + y, z);

        // One tint per tree, chosen here so a stand reads as a deliberate colour block
        // rather than as noise. Tinting the whole spatial also recolours the trunk, so
        // the bark is restored afterwards to keep the silhouettes legible.
        ColorRGBA tint = TREE_TINTS[rand.nextInt(TREE_TINTS.length)];
        fixEnvironmentMaterials(placed.spatial, tint);
        tintFoliage(placed.spatial, tint);
        restoreTrunks(placed.spatial);
        placed.spatial.updateModelBound();

        // trunk collider from the model bounds, so what you bump into matches the trunk
        float trunkRadius = 0.9f;
        float collarHeight = 4f;
        if (placed.spatial.getWorldBound() instanceof BoundingBox bbox) {
            Vector3f extent = new Vector3f();
            bbox.getExtent(extent);
            trunkRadius = FastMath.clamp(Math.max(extent.x, extent.z) * 0.35f, 0.9f, 2f);
            collarHeight = FastMath.clamp(extent.y, 3f, 9f);
        }
        addBlockerAt(bulletAppState, x, y, z, trunkRadius, collarHeight / 2f, trunkRadius);
        return true;
    }

    private void plantBoulders(AssetManager assetManager, BulletAppState bulletAppState,
                               Random rand, Boulder[] boulders) {
        for (Boulder b : boulders) {
            if (blocksSpace(b.x, b.z)) continue;
            String model = ROCK_MODELS[rand.nextInt(ROCK_MODELS.length)];
            float y = heightAt(b.x, b.z);
            ScalePlacement placed = placeFlatTerrain(stageNode, assetManager, model,
                    b.x, b.z, b.size / 2f, rand);
placed.spatial.setLocalTranslation(b.x, placed.baseY + y, b.z);
        fixEnvironmentMaterials(placed.spatial, ROCK_TINT);
        tintFoliage(placed.spatial, ROCK_TINT);

            // collider matches the visible boulder, sitting on the terrain
            float half = FastMath.clamp(b.size * 0.5f, 1.4f, 4f);
            addBlockerAt(bulletAppState, b.x, y, b.z, half, half * 0.8f, half);
        }
    }

    private void plantBushes(AssetManager assetManager, Random rand, float[][] spots) {
        for (float[] p : spots) {
            if (blocksSpace(p[0], p[1])) continue;
            ScalePlacement placed = placeFlatTerrain(stageNode, assetManager,
                    BUSH_MODELS[rand.nextInt(BUSH_MODELS.length)], p[0], p[1],
                    2.2f + rand.nextFloat() * 2.2f, rand);
            placed.spatial.setLocalTranslation(p[0], placed.baseY + heightAt(p[0], p[1]), p[1]);
            ColorRGBA tint = UNDERSTORY_TINTS[rand.nextInt(UNDERSTORY_TINTS.length)];
            fixEnvironmentMaterials(placed.spatial, tint);
            tintFoliage(placed.spatial, tint);
        }
    }

    private void plantGrass(AssetManager assetManager, Random rand, float[][] spots) {
        for (float[] p : spots) {
            if (blocksSpace(p[0], p[1])) continue;
            ScalePlacement placed = placeFlatTerrain(stageNode, assetManager,
                    GRASS_MODELS[rand.nextInt(GRASS_MODELS.length)], p[0], p[1],
                    1.2f + rand.nextFloat() * 1.6f, rand);
            placed.spatial.setLocalTranslation(p[0], placed.baseY + heightAt(p[0], p[1]), p[1]);
            ColorRGBA tint = UNDERSTORY_TINTS[rand.nextInt(UNDERSTORY_TINTS.length)];
            fixEnvironmentMaterials(placed.spatial, tint);
            tintFoliage(placed.spatial, tint);
        }
    }

    /** Multiplies the chosen tint into a geometry's diffuse material.
     *
     *  <p>These models are shared cached instances, so the tint has to be applied to a
     *  material that belongs to the CLONE. Writing straight into the shared
     *  {@link Material} would repaint every other plant using the same model - and, on a
     *  later loop, plants already placed.</p>
     *
     *  <p>Vertex colour is switched off first. The source meshes carry baked vertex
     *  colour, and with it enabled the tint multiplies against that, which crushes the
     *  result toward black instead of toward the pastel we want.</p> */
    private void tintFoliage(Spatial spatial, ColorRGBA tint) {
        if (spatial instanceof Geometry geometry) {
            Material existing = geometry.getMaterial();
            if (existing == null) return;

            Material tinted = existing.clone();
            if (tinted.getMaterialDef().getMaterialParam("UseVertexColor") != null) {
                tinted.setBoolean("UseVertexColor", false);
            }
            if (tinted.getMaterialDef().getMaterialParam("Diffuse") != null) {
                tinted.setColor("Diffuse", tint);
            }
            if (tinted.getMaterialDef().getMaterialParam("Metallic") != null) {
                tinted.setFloat("Metallic", 0f);
            }
            if (tinted.getMaterialDef().getMaterialParam("Roughness") != null) {
                tinted.setFloat("Roughness", 1f);
            }
            geometry.setMaterial(tinted);
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                tintFoliage(child, tint);
            }
        }
    }

    /** Puts the bark back after the foliage tint.
     *
     *  <p>A tree is mostly trunk by surface area on some of these models, so tinting
     *  everything orange leaves a column of solid colour with no read of "tree". This
     *  walks the geometry buffers and restores any vertex whose baked colour is
     *  brown-ish - the trunk and branch polygons - leaving the rest tinted.</p>
     *
     *  <p>Requires vertex colour to stay enabled on those geometries, which is why
     *  {@link #tintFoliage} runs first and this second.</p> */
    private void restoreTrunks(Spatial spatial) {
        for (Geometry geometry : geometriesOf(spatial)) {
            VertexBuffer vc = geometry.getMesh().getBuffer(VertexBuffer.Type.Color);
            if (vc == null || !vc.getData().isDirect()) continue;
            FloatBuffer vb = (FloatBuffer) vc.getData();

            Material mat = geometry.getMaterial();
            if (mat != null && mat.getMaterialDef().getMaterialParam("UseVertexColor") != null) {
                mat.setBoolean("UseVertexColor", true);
            }

            // rewind first: the buffer's position is wherever the renderer left it, and
            // absolute get/put would then read from the wrong offset
            vb.rewind();
            for (int i = 0; i < vb.limit(); i += 4) {
                float r = vb.get(i);
                float g = vb.get(i + 1);
                float b = vb.get(i + 2);
                if (isBark(r, g, b)) {
                    vb.put(i, 0.42f).put(i + 1, 0.30f).put(i + 2, 0.22f);
                }
            }
        }
    }

    /** Brown bark: red clearly dominant, and warm (red above blue) by enough that a
     *  green or grey leaf never qualifies. */
    private static boolean isBark(float r, float g, float b) {
        return r > g && r > b && (r - b) > 0.10f && r > 0.25f;
    }

    private static List<Geometry> geometriesOf(Spatial spatial) {
        List<Geometry> out = new ArrayList<>();
        collectGeometries(spatial, out);
        return out;
    }

    private static void collectGeometries(Spatial spatial, List<Geometry> out) {
        if (spatial instanceof Geometry geometry) {
            out.add(geometry);
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                collectGeometries(child, out);
            }
        }
    }

    /** Box collider centred at (x, baseY + halfY, z) so it sits on the terrain. */
    private void addBlockerAt(BulletAppState bulletAppState, float x, float y, float z,
                              float halfX, float halfY, float halfZ) {
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(halfX, halfY, halfZ));
        RigidBodyControl physics = new RigidBodyControl(shape, 0);
        physics.setPhysicsLocation(new Vector3f(x, y + halfY, z));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    /** placeFlat with the model's base-lift exposed, so callers can rest it on variable
     *  terrain instead of the flat y=0 assumption the shared helper makes. */
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
