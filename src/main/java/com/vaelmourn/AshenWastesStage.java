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

import java.awt.Color;
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
 * Ashen Wastes — stage 2, tier-2 volcanic wastes. All four variants are fully
 * rebuilt by hand, each one a large open volcanic landscape ringed by jagged,
 * unclimbable mountain teeth:
 * 1 — the caldera (west basin, north basin, high east ridge)
 *   2 — the ashfall terraces (stepped rings, a sunken west bowl, an east ledge)
 *   3 — the cinder flats (a wide open horde battlefield, low relief)
 *   4 — the obsidian spires (a spire field around a central pit, elite troops)
 * Every elevation, rock, landmark and encounter is authored — no random
 * placement anywhere. Variant 3 remains the horde stage (many weak foes).
 */
public class AshenWastesStage implements Stage {

    private static final float HALF_EXTENT = 140f;       // playable basin (±140)
    private static final String CENJI =
            "Models/Environment/Cenji_FantasyCrystalPack_FREE/"
                    + "Cenji_FantasyCrystalPack_FREE/GLB/";
    // the horde keeps the enemy count bounded so the spawn pile doesn't tank the frame rate
    private static final int HORDE_ENEMY_COUNT = 30;
    // horde fodder hits far softer than the surrounding stages' regular troops
    private static final float HORDE_DIFFICULTY_SCALE = 0.55f;
    private static final String[] ENEMY_MODELS = {
            "Models/Characters/enemy/ashenwastes_enemy.gltf",
            "Models/Characters/enemy/ashenwastes_enemy2.gltf"
    };

    // --------------------------------------------------------- volcanic assets
    // Only genuinely volcanic/corrupted/grey-ash rocks plus the red ember
    // crystals. No trees, no grass, no blue/purple/green crystals, no ice.
    private static final String[] BIG_JAGGED_ROCKS = {
            CENJI + "Rocks/ROCK_Volcanic_01_LargeCrag.glb",
            CENJI + "Rocks/ROCK_Corrupted_01_LargeMass.glb",
            CENJI + "Rocks/ROCK_Volcanic_04_BasaltPillar.glb",
            CENJI + "Rocks/ROCK_Corrupted_04_TwistedPinnacle.glb"
    };
    private static final String[] BOULDER_ROCKS = {
            CENJI + "Rocks/ROCK_Slate_01_LargeBoulder.glb",
            CENJI + "Rocks/ROCK_Volcanic_02_MediumBlock.glb",
            CENJI + "Rocks/ROCK_Corrupted_02_MediumHorn.glb"
    };
    private static final String[] SMALL_DEBRIS = {
            CENJI + "Rocks/ROCK_Volcanic_05_DebrisCluster.glb",
            CENJI + "Rocks/ROCK_Corrupted_05_VoidFragments.glb",
            CENJI + "Rocks/ROCK_Volcanic_03_FlatLedge.glb",
            CENJI + "Rocks/ROCK_Slate_05_RubbleCluster.glb",
            CENJI + "Rocks/ROCK_Corrupted_03_FlatShelf.glb"
    };
    // red ember geodes + ember accents — landmarks, deliberately few
    private static final String[] EMBER_GEODE = {
            CENJI + "Crystal_Formations/PROP_20_EmberCrystalFormation.glb"
    };
    private static final String[] EMBER_ACCENT = {
            CENJI + "Gems/GEM_11_RoughTwinCrystal.glb",
            CENJI + "Gems/GEM_07_CurvedHornSpike.glb",
            CENJI + "Gems/GEM_04_ChiselQuartz.glb"
    };
    private static final String[] OBSIDIAN_SHARD = {
            CENJI + "Gems/GEM_08_CursedObsidianShard.glb"
    };

    // ------------------------------------------------------------------ terrain
    private static final float TERRAIN_HALF = 190f;      // mesh spans [-190, 190]
    private static final float TERRAIN_STEP = 2f;        // grid cell size (world units)
    private static final int TERRAIN_SIZE = (int) (TERRAIN_HALF * 2f / TERRAIN_STEP); // 190
    private static final int TERRAIN_SAMPLES = TERRAIN_SIZE + 1;
private static final float MIN_ELEVATION = -14f;     // the spire-pit floor goes this deep
    private static final float SKY_RADIUS = 540f;

    // jagged mountain ring
    private static final float MOUNT_FOOT_BASE = 168f;
    private static final float MOUNT_FOOT_WAVE = 14f;
    private static final float COLLAR_WIDTH = 30f;

    // ------------------------------------------- authored enemy encounters
    // every variant opens with the same guard pair at the vale mouth (the "final
    // approach" back to the portal), then five arena packs laid out on that
    // variant's authored flats.
    private static final float[][] ENCOUNTER_GUARD = {{-16f, 40f}, {16f, 40f}};

    // variant 1 — the caldera
    private static final float[][] ENCOUNTER_A1 = {{-105f, -60f}, {-72f, -52f}, {-85f, -90f}, {-100f, -95f}};
    private static final float[][] ENCOUNTER_B1 = {{20f, 118f}, {-20f, 112f}, {14f, 88f}, {-24f, 98f}};
    private static final float[][] ENCOUNTER_C1 = {{95f, 8f}, {112f, 16f}, {104f, 32f}, {118f, 26f}};
    private static final float[][] ENCOUNTER_D1 = {{4f, -60f}, {-12f, -92f}, {24f, -78f}, {30f, -104f}};
    private static final float[][] ENCOUNTER_E1 = {{-48f, -16f}, {-66f, -36f}, {-52f, -38f}, {-40f, -44f}};

    // variant 2 — the ashfall terraces
    private static final float[][] ENCOUNTER_A2 = {{24f, 110f}, {-24f, 104f}, {12f, 84f}, {-14f, 88f}};
    private static final float[][] ENCOUNTER_B2 = {{-60f, -20f}, {-90f, -40f}, {-70f, -50f}, {-95f, -15f}};
    private static final float[][] ENCOUNTER_C2 = {{82f, 40f}, {108f, 58f}, {96f, 66f}, {112f, 38f}};
    private static final float[][] ENCOUNTER_D2 = {{-6f, -86f}, {26f, -96f}, {6f, -110f}, {30f, -80f}};
    private static final float[][] ENCOUNTER_E2 = {{-58f, 44f}, {-84f, 62f}, {-66f, 72f}, {-90f, 44f}};

    // variant 3 — the cinder flats (the horde battlefield)
    private static final float[][] ENCOUNTER_A3 = {{-70f, -30f}, {-100f, -60f}, {-85f, -70f}, {-110f, -25f}};
    private static final float[][] ENCOUNTER_B3 = {{75f, 35f}, {105f, 65f}, {90f, 80f}, {115f, 40f}};
    private static final float[][] ENCOUNTER_C3 = {{25f, 115f}, {-25f, 110f}, {10f, 85f}, {-15f, 90f}};
    private static final float[][] ENCOUNTER_D3 = {{25f, -115f}, {-20f, -110f}, {5f, -85f}, {-30f, -90f}};
    private static final float[][] ENCOUNTER_E3 = {{-55f, 62f}, {-85f, 88f}, {-68f, 98f}, {-92f, 62f}};

    // variant 4 — the obsidian spires
    private static final float[][] ENCOUNTER_A4 = {{15f, 78f}, {-15f, 84f}, {9f, 70f}, {-9f, 92f}};
    private static final float[][] ENCOUNTER_B4 = {{75f, 18f}, {100f, 36f}, {85f, 45f}, {105f, 20f}};
    private static final float[][] ENCOUNTER_C4 = {{-82f, 0f}, {-108f, 24f}, {-92f, 30f}, {-112f, 2f}};
    private static final float[][] ENCOUNTER_D4 = {{-12f, -90f}, {16f, -104f}, {2f, -118f}, {22f, -86f}};
    private static final float[][] ENCOUNTER_E4 = {{46f, 90f}, {70f, 96f}, {56f, 102f}, {76f, 88f}};

    private final int variant;
    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    public AshenWastesStage() {
        this(1);
    }

    public AshenWastesStage(int variant) {
        this.variant = Math.max(1, variant);
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("AshenWastes" + variant);
        parentNode.attachChild(stageNode);

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
        return spawnAuthored(assetManager, parentNode, bulletAppState, loopCount);
    }

    /** Authored encounter groups. Boot = all six groups (guard pair + the five
     *  arena packs of this variant); later loops add spare reinforcements on top. */
    private List<EnemyController> spawnAuthored(AssetManager assetManager, Node parentNode,
                                                BulletAppState bulletAppState, int loopCount) {
        List<EnemyController> enemies = new ArrayList<>();
        int enemyCount = authoredEnemyCount() + (loopCount / 4);
        int tier = authoredTier();
        float scale = authoredScale();
        Random rand = new Random(7 + loopCount * 13);

        float[][][] groups = encounterGroups();
        int placed = 0;
        while (placed < enemyCount) {
            for (float[][] group : groups) {
                if (placed >= enemyCount) break;
                for (float[] spot : group) {
                    if (placed >= enemyCount) break;
                    float x = spot[0];
                    float z = spot[1];
                    float y = heightAt(x, z) + 4f;
                    String modelPath = ENEMY_MODELS[(placed + rand.nextInt(2)) % ENEMY_MODELS.length];
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

    /** Boot population per variant: 1 and 2 are a normal sweep, 3 is the horde,
     *  4 is a lean elite gauntlet. Later loops top the count up. */
    private int authoredEnemyCount() {
        switch (variant) {
            case 3: return HORDE_ENEMY_COUNT;
            case 4: return 18;
            default: return 22;
        }
    }

    private int authoredTier() {
        return variant >= 4 ? 3 : 2;
    }

    private float authoredScale() {
        return variant == 3 ? HORDE_DIFFICULTY_SCALE : 1f + (variant - 1) * 0.4f;
    }

    private float[][][] encounterGroups() {
        switch (variant) {
            case 2: return new float[][][]{ENCOUNTER_GUARD, ENCOUNTER_A2, ENCOUNTER_B2,
                    ENCOUNTER_C2, ENCOUNTER_D2, ENCOUNTER_E2};
            case 3: return new float[][][]{ENCOUNTER_GUARD, ENCOUNTER_A3, ENCOUNTER_B3,
                    ENCOUNTER_C3, ENCOUNTER_D3, ENCOUNTER_E3};
            case 4: return new float[][][]{ENCOUNTER_GUARD, ENCOUNTER_A4, ENCOUNTER_B4,
                    ENCOUNTER_C4, ENCOUNTER_D4, ENCOUNTER_E4};
            default: return new float[][][]{ENCOUNTER_GUARD, ENCOUNTER_A1, ENCOUNTER_B1,
                    ENCOUNTER_C1, ENCOUNTER_D1, ENCOUNTER_E1};
        }
    }

    @Override
    public Vector3f getPlayerSpawnPoint() {
        return new Vector3f(0, 2f, 0);
    }

    @Override
    public ColorRGBA getSkyColor() {
        // hellish ember haze matching the baked volcanic sky dome bottom; the
        // wastes darken and redden as the variants progress
        switch (variant) {
            case 2: return new ColorRGBA(0.24f, 0.10f, 0.05f, 1f);
            case 3: return new ColorRGBA(0.27f, 0.14f, 0.09f, 1f);
            case 4: return new ColorRGBA(0.18f, 0.07f, 0.04f, 1f);
            default: return new ColorRGBA(0.22f, 0.09f, 0.05f, 1f);
        }
    }

    @Override
    public ColorRGBA getAmbientColor() {
        // warm, dim volcanic light — dark rock, glowing embers, readable combat.
        // The cinder flats stay brighter so 30 weak horde enemies stay readable.
        switch (variant) {
            case 2: return new ColorRGBA(0.32f, 0.24f, 0.17f, 1f);
            case 3: return new ColorRGBA(0.40f, 0.31f, 0.22f, 1f);
            case 4: return new ColorRGBA(0.26f, 0.19f, 0.15f, 1f);
            default: return new ColorRGBA(0.34f, 0.26f, 0.18f, 1f);
        }
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.35f, -0.65f, 0.45f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    @Override
    public String getName() {
        return "Ashen Wastes " + variant;
    }

    @Override
    public int getStageIndex() {
        return 2;
    }

    // ============================================ the handcrafted Ashen variants

    private void buildAuthored(AssetManager assetManager, BulletAppState bulletAppState) {
        buildTerrain(assetManager, bulletAppState);
        buildSky(assetManager);
        sealMountainRing(bulletAppState);
        addSafetyFloor(bulletAppState);
        int[] counts = buildLandscape(assetManager, bulletAppState);
        System.out.println("[Ashen Wastes " + variant + "] authored " + counts[0]
                + " formations, " + counts[1] + " geodes.");
    }

    /** Invisible seal ring closing the basin-to-mountain seam, same trick that made
     *  Darkwood's steep faces safe: the ring stops a character capsule from
     *  wedging into the near-vertical jagged teeth above the collar. */
    private void sealMountainRing(BulletAppState bulletAppState) {
        float R = 152f;
        float halfTang = 17f;
        float halfY = 60f;   // spans y 0..120 — the teeth are taller than Darkwood's
        float halfRad = 5f;
        int n = 32;
        for (int i = 0; i < n; i++) {
            float a = i * FastMath.TWO_PI / n;
            float x = FastMath.cos(a) * R;
            float z = FastMath.sin(a) * R;
            BoxCollisionShape box = new BoxCollisionShape(new Vector3f(halfTang, halfY, halfRad));
            RigidBodyControl blocker = new RigidBodyControl(box, 0f);
            blocker.setPhysicsRotation(new Quaternion().fromAngleAxis(-a - FastMath.HALF_PI, Vector3f.UNIT_Y));
            blocker.setPhysicsLocation(new Vector3f(x, halfY, z));
            bulletAppState.getPhysicsSpace().add(blocker);
            physicsObjects.add(blocker);
        }
    }

    private void addSafetyFloor(BulletAppState bulletAppState) {
        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(210f, 2f, 210f));
        RigidBodyControl physics = new RigidBodyControl(shape, 0f);
        physics.setPhysicsLocation(new Vector3f(0f, -50f, 0f));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    private void buildTerrain(AssetManager assetManager, BulletAppState bulletAppState) {
        int n = TERRAIN_SAMPLES;
        int total = n * n;

        float[] heights = new float[total];
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                float x = ix * TERRAIN_STEP - TERRAIN_HALF;
                float z = iz * TERRAIN_STEP - TERRAIN_HALF;
                heights[iz * n + ix] = heightAt(x, z);
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
                float shade = FastMath.clamp(1f - h * 0.004f, 0.7f, 1f);
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

        Geometry ground = new Geometry("AshenWastesGround", terrain);
        ground.setMaterial(mat);
        stageNode.attachChild(ground);

        MeshCollisionShape shape = new MeshCollisionShape(terrain);
        RigidBodyControl physics = new RigidBodyControl(shape, 0f);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    /** Dark burnt flats, red-brown basin floors, rock that reads volcanic, and
     *  pale ash only on the far peaks — so the flat arenas stay dark and the red
     *  ember landmarks pop, never powdery. */
    private float[] terrainColor(float x, float z, float h, float slope) {
        float[] ashGround = {0.16f, 0.10f, 0.08f};
        float[] basinFloor = {0.21f, 0.13f, 0.09f};
        float[] scree = {0.28f, 0.19f, 0.14f};
        float[] rock = {0.38f, 0.27f, 0.21f};
        float[] ashGrey = {0.42f, 0.39f, 0.35f};

        float tH = FastMath.clamp((h + 8f) / 60f, 0f, 1f);
        float[] rgb = lerp(ashGround, basinFloor, Math.min(1f, tH * 1.6f));
        rgb = lerp(rgb, scree, FastMath.clamp((tH - 0.45f) / 0.55f, 0f, 1f));
        rgb = lerp(rgb, rock, FastMath.clamp((slope - 0.35f) / 1.0f, 0f, 1f));
        if (h > 40f) {
            rgb = lerp(rgb, ashGrey, FastMath.clamp((h - 40f) / 90f, 0f, 1f));
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

    // --------------------------------------------------------- the height field

    /** Kept for the stand-alone height probes. */
    static float groundHeight(int variant, float x, float z) {
        return profileFor(variant, x, z);
    }

    private float heightAt(float x, float z) {
        return profileFor(variant, x, z);
    }

    /** Sampled so the exit portal rests on this stage's terrain rather than a
     *  fixed height, which left it buried in the higher profiles. */
    @Override
    public Vector3f getExitPortalGround() {
        return new Vector3f(0f, heightAt(0f, 25f), 25f);
    }

    private static float profileFor(int variant, float x, float z) {
        switch (variant) {
            case 2: return profileV2(x, z);
            case 3: return profileV3(x, z);
            case 4: return profileV4(x, z);
            default: return profileV1(x, z);
        }
    }

    /**
     * Ashen Wastes 1 — the volcanic caldera. A west basin corridor (arena A),
     * a deep north basin (arena B), and a high east ridge with an overlook crest
     * (arena C). Every flat zone is where a fight is meant to happen.
     */
    private static float profileV1(float x, float z) {
        float h = 0f;

        // --- broad volcanic basin forms ---------------------------------------
        h += gaussian(0f, 0f, 150f, 150f, 3f, x, z);     // broad caldera floor swell
        h += gaussian(80f, -20f, 95f, 70f, 6f, x, z);    // SE rise (frames east ridge)
        h += gaussian(-30f, 40f, 70f, 80f, 4f, x, z);    // NW swell
        h += gaussian(0f, -120f, 120f, 80f, 8f, x, z);   // S rise
        h += gaussian(10f, 120f, 110f, 70f, 9f, x, z);   // N rise toward the north basin
        h += gaussian(-130f, -20f, 60f, 55f, 7f, x, z);  // W rise
        h += gaussian(105f, 20f, 58f, 44f, 12f, x, z);   // east high ridge (arena C)
        h -= gaussian(-95f, -75f, 70f, 50f, 12f, x, z);  // carves the west basin (arena A)
        h -= gaussian(0f, 105f, 62f, 42f, 10f, x, z);    // carves the north basin (arena B)
        h -= gaussian(30f, 60f, 60f, 45f, 5f, x, z);     // saddle cut toward the east ridge

        // five authored volcanic summits for the jagged far silhouette
        h += gaussian(-150f, 105f, 30f, 30f, 58f, x, z); // summit NW
        h += gaussian(140f, -125f, 30f, 30f, 54f, x, z); // summit SE
        h += gaussian(5f, 182f, 30f, 30f, 62f, x, z);    // summit N
        h += gaussian(-175f, -35f, 30f, 30f, 52f, x, z); // summit W
        h += gaussian(155f, 130f, 30f, 30f, 56f, x, z);  // summit NE

        // --- flat zones: spawn, portal, arenas ---------------------------------
        h = flattenTo(h, 0f, 0f, 34f, 12f, 0f, x, z);       // ashenSpawnArea
        h = flattenTo(h, 0f, 25f, 22f, 8f, 0f, x, z);       // ashenExitArea (portal meadow)
        h = flattenTo(h, 0f, 42f, 20f, 8f, 0f, x, z);       // ashenGuardShelf (vale mouth)
        h = flattenTo(h, -95f, -75f, 50f, 12f, -5f, x, z);  // ashenWestBasin (arena A)
        h = flattenTo(h, 0f, 105f, 38f, 10f, -5f, x, z);    // ashenNorthBasin (arena B)
        h = flattenTo(h, 105f, 20f, 26f, 12f, 8f, x, z);    // ashenEastRidge (arena C crest)

        return ashenRing(h, x, z);
    }

    /**
     * Ashen Wastes 2 — the ashfall terraces. The whole basin is stepped: a raised
     * spawn dais in the middle, two broken concentric terrace walls, a sunken bowl
     * in the west (arena B) and a high shelf on the east (arena C), so every
     * approach is either up a step or down into a bowl.
     */
    private static float profileV2(float x, float z) {
        float h = 0f;

        // --- terraced basin forms ---------------------------------------------
        h += gaussian(0f, 0f, 150f, 150f, 3f, x, z);     // basin floor
        h += gaussian(-70f, 60f, 80f, 70f, 5f, x, z);    // NW terrace shelf
        h += gaussian(80f, 70f, 75f, 65f, 6f, x, z);     // NE rise
        h += gaussian(-90f, -70f, 70f, 60f, 6f, x, z);   // SW rise
        h += gaussian(85f, -75f, 70f, 60f, 5f, x, z);    // SE rise
        h += gaussian(0f, 0f, 45f, 45f, 7f, x, z);       // raised spawn dais

        // inner terrace wall: six broken step segments ringing the dais
        for (int i = 0; i < 6; i++) {
            float a = i * FastMath.TWO_PI / 6f + 0.35f;
            h += gaussian(FastMath.cos(a) * 62f, FastMath.sin(a) * 62f,
                    27f, 27f, 9f, x, z);
        }
        // outer terrace wall: a wider, taller step further out
        for (int i = 0; i < 5; i++) {
            float a = i * FastMath.TWO_PI / 5f - 0.5f;
            h += gaussian(FastMath.cos(a) * 112f, FastMath.sin(a) * 112f,
                    32f, 32f, 12f, x, z);
        }

        // sunken west bowl (arena B) and the east shelf (arena C)
        h -= gaussian(-75f, -30f, 58f, 52f, 10f, x, z);
        h += gaussian(95f, 50f, 52f, 46f, 10f, x, z);

        // twin summits + N/S/W/E caps for a distinct silhouette
        h += gaussian(-158f, 72f, 26f, 26f, 56f, x, z);
        h += gaussian(150f, -88f, 26f, 26f, 60f, x, z);
        h += gaussian(10f, -176f, 26f, 26f, 52f, x, z);
        h += gaussian(172f, 96f, 26f, 26f, 50f, x, z);
        h += gaussian(-178f, -20f, 26f, 26f, 48f, x, z);

        // --- flat zones: spawn, portal, arenas ---------------------------------
        h = flattenTo(h, 0f, 0f, 34f, 12f, 0f, x, z);      // ashenSpawnArea
        h = flattenTo(h, 0f, 25f, 22f, 8f, 0f, x, z);      // ashenExitArea (portal meadow)
        h = flattenTo(h, 0f, 42f, 20f, 8f, 0f, x, z);      // ashenGuardShelf (vale mouth)
        h = flattenTo(h, 0f, 95f, 40f, 12f, -3f, x, z);    // north terrace shelf (arena A)
        h = flattenTo(h, -75f, -30f, 32f, 12f, -6f, x, z); // sunken west bowl (arena B)
        h = flattenTo(h, 95f, 50f, 30f, 14f, 12f, x, z);   // east shelf (arena C)
        h = flattenTo(h, 10f, -95f, 34f, 12f, 4f, x, z);   // south ramp (arena D)
        h = flattenTo(h, -70f, 55f, 26f, 12f, 3f, x, z);   // NW mid terrace (arena E)

        return ashenRing(h, x, z);
    }

    /**
     * Ashen Wastes 3 — the cinder flats. A wide, low-relief ash plain (the horde
     * battlefield): almost the whole basin stays walkable, with only a handful of
     * low crag clusters and shoulder ridges for cover. Deliberately the most open
     * of the four so 30 weak enemies can be fought in one big engagement.
     */
    private static float profileV3(float x, float z) {
        float h = 0f;

        // --- the open plain ----------------------------------------------------
        h += gaussian(0f, 0f, 165f, 165f, 2f, x, z);     // flat ash plain
        h += gaussian(-60f, 70f, 60f, 55f, 4f, x, z);   // gentle NW swells
        h += gaussian(70f, -60f, 60f, 55f, 4f, x, z);    // gentle SE swells
        h -= gaussian(0f, 0f, 90f, 90f, 1.5f, x, z);     // barely-there central dip

        // four low crag clusters, all outside the middle so lanes stay open
        h += gaussian(-115f, 10f, 26f, 26f, 14f, x, z);
        h += gaussian(120f, 20f, 26f, 26f, 13f, x, z);
        h += gaussian(-40f, -115f, 26f, 26f, 12f, x, z);
        h += gaussian(55f, 110f, 26f, 26f, 13f, x, z);

        // outer shoulders that start the climb to the mountain ring
        h += gaussian(-150f, 60f, 30f, 30f, 30f, x, z);
        h += gaussian(150f, -70f, 30f, 30f, 32f, x, z);
        h += gaussian(0f, 175f, 30f, 30f, 34f, x, z);
        h += gaussian(-170f, -50f, 30f, 30f, 28f, x, z);
        h += gaussian(165f, 120f, 30f, 30f, 30f, x, z);

        // --- flat zones: spawn, portal, arenas ---------------------------------
        h = flattenTo(h, 0f, 0f, 40f, 14f, 0f, x, z);      // ashenSpawnArea
        h = flattenTo(h, 0f, 25f, 24f, 8f, 0f, x, z);      // ashenExitArea (portal meadow)
        h = flattenTo(h, 0f, 44f, 22f, 8f, 0f, x, z);      // ashenGuardShelf (vale mouth)
        h = flattenTo(h, -85f, -45f, 44f, 14f, -2f, x, z); // west plain (arena A)
        h = flattenTo(h, 90f, 50f, 42f, 14f, 3f, x, z);     // east plain (arena B)
        h = flattenTo(h, 0f, 100f, 40f, 14f, 0f, x, z);    // north plain (arena C)
        h = flattenTo(h, 0f, -100f, 40f, 14f, -1f, x, z);  // south plain (arena D)
        h = flattenTo(h, -70f, 75f, 30f, 12f, 1f, x, z);   // NW shelf (arena E)

        return ashenRing(h, x, z);
    }

    /**
     * Ashen Wastes 4 — the obsidian spires. The final wastes: a forest of tall,
     * narrow obsidian spires stands between the arenas, focused around a deep
     * central pit (arena A) with a high ridge walk east and a raised plateau west.
     * The spires break sightlines and split the fight into pockets.
     */
    private static float profileV4(float x, float z) {
        float h = 0f;

        // --- basin forms -------------------------------------------------------
        h += gaussian(0f, 0f, 150f, 150f, 3f, x, z);     // basin floor
        h += gaussian(0f, -110f, 70f, 50f, 8f, x, z);    // S approach swell (arena D)
        h += gaussian(85f, 25f, 45f, 40f, 16f, x, z);     // high ridge walk (arena B)
        h += gaussian(-95f, 10f, 40f, 38f, 9f, x, z);    // west plateau (arena C)
        h += gaussian(60f, 100f, 40f, 35f, 7f, x, z);    // NE shelf (arena E)
        h -= gaussian(0f, 80f, 46f, 34f, 15f, x, z);     // carves the central spire pit (arena A)
        h += gaussian(-34f, 52f, 16f, 12f, 6f, x, z);    // the one walkable ramp down into it

        // --- the obsidian spire field: narrow, tall, placed between the arenas
        h += gaussian(46f, 10f, 14f, 14f, 34f, x, z);
        h += gaussian(-48f, 15f, 13f, 13f, 30f, x, z);
        h += gaussian(30f, 105f, 12f, 12f, 32f, x, z);
        h += gaussian(-35f, 100f, 12f, 12f, 28f, x, z);
        h += gaussian(95f, -15f, 14f, 14f, 36f, x, z);
        h += gaussian(-95f, -20f, 14f, 14f, 32f, x, z);
        h += gaussian(60f, -85f, 13f, 13f, 30f, x, z);
        h += gaussian(-60f, -80f, 13f, 13f, 28f, x, z);
        h += gaussian(120f, 60f, 12f, 12f, 30f, x, z);
        h += gaussian(-120f, 65f, 12f, 12f, 28f, x, z);
        h += gaussian(20f, -120f, 12f, 12f, 26f, x, z);
        h += gaussian(-25f, 135f, 12f, 12f, 28f, x, z);

        // far summits
        h += gaussian(-155f, -95f, 26f, 26f, 54f, x, z);
        h += gaussian(150f, 105f, 26f, 26f, 58f, x, z);
        h += gaussian(0f, 178f, 28f, 28f, 60f, x, z);
        h += gaussian(-172f, 25f, 28f, 28f, 50f, x, z);
        h += gaussian(175f, -35f, 26f, 26f, 52f, x, z);

        // --- flat zones: spawn, portal, arenas ---------------------------------
        h = flattenTo(h, 0f, 0f, 34f, 12f, 0f, x, z);      // ashenSpawnArea
        h = flattenTo(h, 0f, 25f, 22f, 8f, 0f, x, z);      // ashenExitArea (portal meadow)
        h = flattenTo(h, 0f, 42f, 16f, 14f, 0f, x, z);     // ashenGuardShelf (vale mouth)
        h = flattenTo(h, 88f, 28f, 28f, 12f, 14f, x, z);   // east ridge walk (arena B)
        h = flattenTo(h, -95f, 12f, 30f, 12f, 8f, x, z);   // west plateau (arena C)
        h = flattenTo(h, 0f, -100f, 34f, 12f, 6f, x, z);   // south approach (arena D)
        h = flattenTo(h, 60f, 100f, 26f, 12f, 6f, x, z);   // NE shelf (arena E)
        // the pit floor is flattened last so the vale-mouth blend above can't
        // tilt the bowl; the bowl wall itself is the walkable way in
        h = flattenTo(h, 0f, 80f, 16f, 14f, -13f, x, z);  // central spire pit (arena A)

        return ashenRing(h, x, z);
    }

    /** The jagged volcanic mountain wall. Instead of Darkwood's smooth concentric
     *  ridges, this is written as overlapping bands of sharp angular teeth plus a
     *  blanket cliff, so the basin reads as violent, broken volcanic slopes. The
     *  teeth get a fast t^2 climb — gentle at first, near-vertical at the crest. */
    private static float ashenRing(float h, float x, float z) {
        float r = FastMath.sqrt(x * x + z * z);
        float th = FastMath.atan2(z, x);

        // jagged ash collar: a rough ramp up to the foot of the teeth
        float foot = innerFoot(th);
        float collarStart = foot - COLLAR_WIDTH;
        if (r > collarStart && r < foot) {
            float jag = 1f + 0.5f * FastMath.sin(7f * th + 0.3f)
                    + 0.33f * FastMath.sin(17f * th + 2.1f)
                    + 0.2f * FastMath.sin(31f * th + 4.4f);
            h += 11f * jag * smooth01((r - collarStart) / COLLAR_WIDTH);
        }

        // teeth band 1 — the near, broken rim ridge
        float t1Foot = foot;
        float t1Peak = foot + 26f + 8f * FastMath.sin(5f * th + 1.1f);
        float amp1 = 42f + 32f * (0.5f + 0.5f * FastMath.sin(9f * th + 2.6f))
                + 16f * (0.5f + 0.5f * FastMath.sin(17f * th + 0.7f))
                + 9f * (0.5f + 0.5f * FastMath.sin(29f * th + 3.9f));
        h += spikeRise(r, t1Foot, t1Peak, amp1);

        // teeth band 2 — the outer, taller broken ridge
        float t2Foot = foot + 24f + 10f * FastMath.sin(2f * th + 0.9f);
        float t2Peak = t2Foot + 30f + 10f * FastMath.sin(4f * th + 2.3f);
        float amp2 = 100f + 60f * (0.5f + 0.5f * FastMath.sin(7f * th + 1.3f))
                + 22f * (0.5f + 0.5f * FastMath.sin(15f * th + 2.8f));
        h += spikeRise(r, t2Foot, t2Peak, amp2);

        // blanket cliff so the mesh edge is an unclimbable wall everywhere
        float blunt = smooth01((r - 150f) / 50f);
        h = Math.max(h, blunt * 260f - 80f);

        return Math.max(h, MIN_ELEVATION);
    }

    private static float innerFoot(float th) {
        return MOUNT_FOOT_BASE + MOUNT_FOOT_WAVE * FastMath.sin(3f * th + 1.7f);
    }

    /** Shaped like a jagged spike: starts slowly, climbs fast — near-vertical at
     *  the crest, so the silhouette reads as teeth instead of mountain domes. */
    private static float spikeRise(float r, float foot, float peakR, float amp) {
        if (r <= foot) return 0f;
        if (r >= peakR) return amp;
        float t = (r - foot) / (peakR - foot);
        return amp * t * t;
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

    private static float gaussian(float cx, float cz, float sx, float sz,
                                  float amp, float x, float z) {
        float dx = x - cx;
        float dz = z - cz;
        return amp * FastMath.exp(-(dx * dx / (2f * sx * sx) + dz * dz / (2f * sz * sz)));
    }

    private static float flattenTo(float h, float cx, float cz, float r,
                                   float blend, float target, float x, float z) {
        float d = dist(x, z, cx, cz);
        if (d >= r + blend) return h;
        float m = 1f - smooth01((d - r) / blend);
        return h + (target - h) * m;
    }

    // ------------------------------------------------------------ the sky dome

    /** Hellish volcanic sky (equirectangular, baked at build time — swap the painted
     *  BufferedImage for a real panorama later; the dome, wrapping and queue stay). */
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

    /** Procedural volcanic sky: dark charcoal overhead bleeding into deep ember at
     *  the horizon, a low red sun glow off-centre, ash smog bands and a few
     *  twisted cloud streaks. Hot, dangerous, volcanic. */
    private BufferedImage skyTextureImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint grad = new GradientPaint(
                0, 0, new Color(30, 9, 7),
                0, h, new Color(236, 82, 22));
        g.setPaint(grad);
        g.fillRect(0, 0, w, h);

        // low red sun glow (off-centre, echoing the sun direction -x, +z)
        int gx = (int) (w * 0.30f);
        int gy = (int) (h * 0.74f);
        int glowR = 210;
        for (int i = 0; i < 46; i++) {
            float t = i / 46f;
            int radius = Math.round(glowR * (1f + t * 2.6f));
            g.setColor(new Color(255, 132, 34,
                    Math.max(0, Math.round(150 * (1f - t) * (1f - t)))));
            g.fillOval(gx - radius, gy - radius, radius * 2, radius * 2);
        }

        // thick ash smog band sitting along the horizon
        Random rand = new Random(31);
        g.setColor(new Color(52, 18, 12, 110));
        for (int i = 0; i < 34; i++) {
            int cx = rand.nextInt(w);
            int cy = (int) (h * 0.82f) + rand.nextInt((int) (h * 0.12f)) - (int) (h * 0.06f);
            int base = 60 + rand.nextInt(140);
            for (int j = 0; j < 7; j++) {
                int ox = cx + rand.nextInt(base) - base / 2;
                int oy = cy + rand.nextInt(base / 3) - base / 6;
                int size = base / 2 + rand.nextInt(base / 2);
                g.fillOval(ox, oy, size, size / 3);
            }
        }

        // a few twisted black cloud streaks high up
        g.setColor(new Color(16, 7, 5, 150));
        for (int i = 0; i < 10; i++) {
            int cx = rand.nextInt(w);
            int cy = rand.nextInt((int) (h * 0.4f));
            int base = 40 + rand.nextInt(90);
            for (int j = 0; j < 8; j++) {
                int ox = cx + rand.nextInt(base) - base / 2;
                int oy = cy + rand.nextInt(base / 3) - base / 6;
                int size = base / 2 + rand.nextInt(base / 2);
                g.fillOval(ox, oy, size, size / 4);
            }
        }
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------ the landscape

    /**
     * Authored volcanic landscape: big deliberate rock formations, a handful of
     * red geode landmarks, and everything placed carries a solid hitbox. Open
     * ground is the point — objects frame spaces and funnel routes, never
     * clutter the basins. Each variant gets its own hand-placed layout.
     */
    private int[] buildLandscape(AssetManager assetManager, BulletAppState bulletAppState) {
        switch (variant) {
            case 2: return buildLandscapeV2(assetManager, bulletAppState);
            case 3: return buildLandscapeV3(assetManager, bulletAppState);
            case 4: return buildLandscapeV4(assetManager, bulletAppState);
            default: return buildLandscapeV1(assetManager, bulletAppState);
        }
    }

    /** Variant 1 — the caldera: a spawn ring, the Horn on the east approach, a
     *  funneled west corridor, and framed frame around the three big arenas. */
    private int[] buildLandscapeV1(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(210 + variant);
        int formations = 0;
        int geodes = 0;

        // ---- spawn ring: 9 big volcanic crags framing the open vale, north open
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {28f, 18f}, {34f, -4f}, {30f, -22f}, {16f, -33f}, {0f, -36f},
                {-16f, -33f}, {-30f, -22f}, {-34f, -4f}, {-28f, 18f}
        }, 4.6f, true);
        // ---- landmark: the Horn — a giant basalt spire on the east approach
        formations += placeRock(assetManager, bulletAppState,
                BIG_JAGGED_ROCKS[2], 60f, 26f, 7.2f, true, rand);
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {52f, 30f}, {68f, 34f}, {56f, 16f}, {72f, 44f}
        }, 2.6f, false);
        // ---- west corridor funnels: rocks flanking the route to the west basin
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-46f, -10f}, {-34f, -32f}, {-56f, -6f}, {-60f, -28f}
        }, 3.4f, true);
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-72f, -22f}, {-86f, -38f}, {-62f, -66f}, {-92f, -54f},
                {-66f, -84f}, {-76f, -104f}, {-100f, -40f}, {-120f, -80f}
        }, 3.8f, true);
        // ---- west field frame rocks around the west basin arena
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-92f, -20f}, {-58f, -40f}, {-70f, -106f}, {-110f, -66f}, {-116f, -84f}
        }, 3.2f, false);
        // ---- north basin frame rocks around the north basin arena
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-34f, 130f}, {-42f, 118f}, {-40f, 90f}, {-36f, 72f}, {-8f, 140f},
                {18f, 140f}, {34f, 124f}, {40f, 104f}, {46f, 84f}, {34f, 66f}, {8f, 64f}
        }, 3.6f, true);
        // ---- east ridge crags framing the high crest arena
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-70f, 8f}, {-58f, 20f}, {-46f, 10f}, {80f, -14f}, {88f, 0f},
                {76f, 28f}, {92f, 36f}, {78f, 48f}, {120f, -4f}, {134f, 10f},
                {128f, 42f}, {60f, 12f}, {54f, 30f}, {48f, -10f}
        }, 3.4f, true);
        // ---- backdrop boulders on the low ring collar (mostly scenery)
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {128f, -58f}, {118f, -100f}, {60f, -128f}, {-26f, -134f}, {-96f, -120f},
                {-128f, -64f}, {-130f, 10f}, {-96f, 100f}, {10f, 134f}, {92f, 122f}
        }, 4.2f, false);

        // ---- red geode landmarks (deliberate clusters only) -------------------
        // 1. the west crossing — "the red geode field"
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {-46f, -36f}, {-82f, -44f}, {-78f, -22f}, {-58f, -14f}
        }, 3.6f);
        // 2. the north basin rim — ember clusters marking the basin approach
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {22f, 92f}, {-32f, 94f}, {28f, 126f}, {-30f, 126f}, {6f, 62f}
        }, 3.2f);
        // 3. the east overlook crest — the landmark you can see from the spawn
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {118f, 8f}, {96f, 40f}, {128f, 32f}, {132f, 20f}
        }, 3.8f);

        // ---- obsidian shard accents near the landmarks ------------------------
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-42f, -28f}, {-64f, -48f}, {14f, 66f}, {36f, -26f},
                {102f, 40f}, {124f, 14f}, {30f, 60f}
        }, 1.4f, false);

        // ---- scattered volcanic debris along the routes ------------------------
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-18f, 60f}, {20f, 64f}, {-30f, 80f}, {14f, -52f}, {-8f, -72f},
                {40f, 30f}, {-44f, 60f}, {74f, -30f}, {94f, 58f}, {-28f, 100f},
                {30f, 96f}, {-14f, -40f}, {8f, 78f}, {46f, 74f}
        }, 2.2f, false);

        return new int[]{formations, geodes};
    }

    /** Variant 2 — the ashfall terraces: step rocks sit on the two concentric
     *  terrace walls so the terracing reads as built geology, and each arena gets
     *  a frame that funnels you in from one side only. */
    private int[] buildLandscapeV2(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(220 + variant);
        int formations = 0;
        int geodes = 0;

        // ---- spawn dais ring: 9 crags framing the open vale, north open
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {30f, 20f}, {36f, -2f}, {32f, -24f}, {17f, -36f}, {0f, -39f},
                {-17f, -36f}, {-32f, -24f}, {-36f, -2f}, {-30f, 20f}
        }, 4.6f, true);

        // ---- landmark: the Broken Anvil on the south-east approach
        formations += placeRock(assetManager, bulletAppState,
                BIG_JAGGED_ROCKS[2], 46f, -42f, 7.2f, true, rand);
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {38f, -30f}, {58f, -52f}, {62f, -34f}, {34f, -58f}
        }, 2.8f, false);

        // ---- inner terrace wall: step crags ringing the spawn dais
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {56f, 24f}, {34f, 56f}, {-34f, 58f}, {-60f, 22f}, {-22f, -60f}, {44f, -46f}
        }, 3.6f, true);
        // ---- outer terrace wall: taller broken steps further out
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {150f, 130f}, {150f, -60f}, {30f, -150f}, {-140f, 70f}, {-150f, -60f}
        }, 4.4f, true);

        // ---- approach funnels: one gate per arena, so every terrace is entered
        //      from a single readable gap
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {20f, 70f}, {-24f, 72f}, {-44f, 4f}, {-38f, -20f}, {46f, 8f},
                {58f, 34f}, {26f, -60f}, {12f, -66f}
        }, 3.4f, true);

        // ---- arena frames -----------------------------------------------------
        // north terrace shelf (arena A)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-30f, 128f}, {30f, 128f}, {-38f, 70f}, {36f, 68f}, {2f, 140f}
        }, 3.4f, true);
        // sunken west bowl (arena B)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-52f, -2f}, {-112f, -40f}, {-92f, -62f}, {-46f, -58f}, {-40f, -30f}
        }, 3.4f, true);
        // east shelf (arena C)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {68f, 26f}, {120f, 20f}, {124f, 70f}, {72f, 72f}
        }, 3.4f, true);
        // south ramp (arena D)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-30f, -78f}, {52f, -72f}, {-14f, -126f}, {36f, -126f}
        }, 3.2f, true);
        // NW mid terrace (arena E)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-104f, 24f}, {-92f, 82f}, {-40f, 68f}, {-44f, 92f}
        }, 3.2f, true);

        // ---- red geode landmarks marking the three terrace steps --------------
        // 1. the sunken bowl rim
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {-50f, -34f}, {-112f, -16f}, {-96f, -56f}, {-34f, -40f}
        }, 3.6f);
        // 2. the north terrace step
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {0f, 120f}, {-46f, 116f}, {44f, 122f}, {-8f, 72f}
        }, 3.2f);
        // 3. the east shelf
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {78f, 22f}, {134f, 50f}, {70f, 68f}, {140f, 60f}
        }, 3.8f);

        // ---- obsidian shard accents -------------------------------------------
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-30f, 56f}, {32f, 18f}, {-70f, -70f}, {66f, -60f},
                {-6f, 66f}, {-40f, 20f}, {60f, 4f}
        }, 1.4f, false);

        // ---- scattered volcanic debris along the terrace routes ---------------
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-20f, 120f}, {34f, 122f}, {-34f, 40f}, {36f, 40f}, {-64f, 0f},
                {-104f, -2f}, {70f, 6f}, {86f, -16f}, {4f, -40f}, {-18f, -52f},
                {44f, -108f}, {-8f, -118f}, {56f, 88f}, {-88f, 96f}
        }, 2.2f, false);

        return new int[]{formations, geodes};
    }

    /** Variant 3 — the cinder flats: the open horde battlefield. Almost all the big
     *  rock sits on the four crag clusters and the outer shoulders, leaving the
     *  plains themselves wide, flat and readable so 30 enemies can be fought. */
    private int[] buildLandscapeV3(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(230 + variant);
        int formations = 0;
        int geodes = 0;

        // ---- spawn ring: wider, lower ring so the plain stays open
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {32f, 22f}, {38f, -2f}, {34f, -26f}, {18f, -38f}, {0f, -42f},
                {-18f, -38f}, {-34f, -26f}, {-38f, -2f}, {-32f, 22f}
        }, 4.4f, true);

        // ---- landmarks: the west and east cinder crags the plain is named for
        formations += placeRock(assetManager, bulletAppState,
                BIG_JAGGED_ROCKS[1], -118f, 8f, 7.4f, true, rand);
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-126f, -14f}, {-108f, 26f}, {-132f, 10f}
        }, 3.0f, false);
        formations += placeRock(assetManager, bulletAppState,
                BIG_JAGGED_ROCKS[2], 124f, 22f, 6.6f, true, rand);
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {134f, 6f}, {112f, 36f}, {140f, 26f}
        }, 2.8f, false);

        // ---- the south and north crag clusters, kept off the plains
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-44f, -118f}, {-30f, -128f}, {-58f, -108f},
                {58f, 112f}, {44f, 122f}, {72f, 104f}
        }, 3.6f, true);

        // ---- arena frames: sparse, set well back so sightlines stay long -------
        // west plain (arena A)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-118f, -60f}, {-58f, -12f}, {-128f, -14f}, {-70f, -96f}, {-104f, -88f}
        }, 3.2f, true);
        // east plain (arena B)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {128f, 62f}, {58f, 30f}, {64f, 78f}, {120f, 88f}
        }, 3.2f, true);
        // north plain (arena C)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {40f, 112f}, {-40f, 88f}, {6f, 130f}, {-6f, 78f}
        }, 3.2f, true);
        // south plain (arena D)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {44f, -92f}, {-46f, -80f}, {10f, -132f}, {-6f, -70f}
        }, 3.2f, true);
        // NW shelf (arena E)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-100f, 92f}, {-46f, 88f}, {-108f, 46f}, {-40f, 108f}
        }, 3.0f, true);

        // ---- backdrop boulders on the outer shoulders
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {150f, -40f}, {140f, 90f}, {60f, 150f}, {-30f, 152f}, {-120f, 130f},
                {-150f, 40f}, {-150f, -60f}, {-60f, -150f}, {40f, -150f}, {155f, 20f}
        }, 4.2f, false);

        // ---- red geode landmarks: three lane markers across the flat ---------
        // 1. the west lane
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {-96f, -8f}, {-134f, -46f}, {-52f, -70f}, {-112f, -80f}
        }, 3.6f);
        // 2. the east lane
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {96f, 8f}, {136f, 30f}, {56f, 66f}, {104f, 96f}
        }, 3.4f);
        // 3. the north lane
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {-14f, 128f}, {18f, 134f}, {-4f, 64f}, {44f, 96f}
        }, 3.2f);

        // ---- obsidian shard accents -------------------------------------------
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-64f, 26f}, {30f, 20f}, {-30f, -30f}, {26f, 60f},
                {-20f, -20f}, {70f, -40f}, {-96f, 110f}
        }, 1.4f, false);

        // ---- scattered volcanic debris along the lanes -------------------------
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-44f, 46f}, {40f, 52f}, {-86f, 8f}, {82f, 4f}, {-12f, 108f},
                {40f, 130f}, {-44f, -104f}, {44f, -132f}, {-100f, 100f}, {70f, 100f},
                {-24f, 56f}, {24f, 74f}, {-70f, -96f}, {96f, -20f}
        }, 2.2f, false);

        return new int[]{formations, geodes};
    }

    /** Variant 4 — the obsidian spires: a big jagged rock stands on every one of
     *  the twelve terrain spires, so the spire field is physically real, plus the
     *  Great Fang over the pit and a frame around each arena. */
    private int[] buildLandscapeV4(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(240 + variant);
        int formations = 0;
        int geodes = 0;

        // ---- spawn ring: tighter and taller, the pit is close behind you
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {28f, 18f}, {34f, -4f}, {30f, -22f}, {16f, -33f}, {0f, -36f},
                {-16f, -33f}, {-30f, -22f}, {-34f, -4f}, {-28f, 18f}
        }, 4.4f, true);

        // ---- the spire field: one crag per terrain spire
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {52f, 4f}, {-48f, 15f}, {30f, 105f}, {-40f, 106f}, {95f, -15f},
                {-95f, -20f}, {60f, -85f}, {-60f, -80f}, {120f, 60f}, {-120f, 65f},
                {40f, -124f}, {-25f, 135f}
        }, 6.0f, true);
        // smaller shards crowding the spire bases
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {44f, 18f}, {-40f, 4f}, {42f, 92f}, {22f, 118f}, {82f, -26f},
                {-84f, -32f}, {72f, -70f}, {-72f, -66f}, {108f, 48f}, {-108f, 52f},
                {30f, -108f}, {-36f, 122f}
        }, 2.8f, false);

        // ---- landmark: the Great Fang, watching the pit from the east
        formations += placeRock(assetManager, bulletAppState,
                BIG_JAGGED_ROCKS[2], 66f, 40f, 7.0f, true, rand);
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {58f, 52f}, {76f, 30f}, {56f, 28f}
        }, 2.6f, false);

        // ---- arena frames -----------------------------------------------------
        // central spire pit (arena A) — rim rocks only, the pit floor stays open
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-42f, 62f}, {42f, 58f}, {-36f, 102f}, {38f, 100f}, {0f, 114f}
        }, 3.4f, true);
        // east ridge walk (arena B)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {118f, 6f}, {120f, 50f}, {60f, 10f}, {74f, 58f}
        }, 3.4f, true);
        // west plateau (arena C)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-132f, -12f}, {-118f, 44f}, {-62f, -12f}, {-70f, 34f}
        }, 3.4f, true);
        // south approach (arena D)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {-40f, -76f}, {44f, -74f}, {-18f, -132f}, {44f, -132f}
        }, 3.2f, true);
        // NE shelf (arena E)
        formations += placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {30f, 122f}, {92f, 118f}, {86f, 74f}, {26f, 78f}
        }, 3.2f, true);

        // ---- backdrop boulders on the low ring collar
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {140f, 100f}, {150f, -30f}, {30f, 158f}, {-40f, 150f}, {-140f, 100f},
                {-155f, -20f}, {-60f, -140f}, {60f, -145f}, {160f, 30f}
        }, 4.2f, false);

        // ---- red geode landmarks: pit rim, ridge walk, plateau ----------------
        // 1. the spire pit: one tall ember pillar in the bowl, the rest on the rim
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {0f, 80f}, {-30f, 58f}, {30f, 56f}, {0f, 108f}, {-30f, 104f}
        }, 3.6f);
        // 2. the east ridge walk
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {70f, 2f}, {128f, 22f}, {86f, 62f}, {140f, 40f}
        }, 3.4f);
        // 3. the west plateau
        geodes += placeGeodes(assetManager, bulletAppState, rand, new float[][]{
                {-96f, -22f}, {-130f, 20f}, {-70f, 24f}, {-104f, 56f}
        }, 3.2f);

        // ---- obsidian shard accents -------------------------------------------
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {52f, 68f}, {-46f, -46f}, {28f, -70f}, {-24f, -68f},
                {66f, 90f}, {-100f, 70f}, {66f, 132f}
        }, 1.4f, false);

        // ---- scattered volcanic debris along the spire lanes ------------------
        placeRocks(assetManager, bulletAppState, rand, new float[][]{
                {30f, 28f}, {-46f, 40f}, {-16f, 118f}, {100f, 78f}, {-86f, 76f},
                {18f, -70f}, {-40f, -60f}, {78f, -60f}, {60f, -120f}, {-6f, -76f},
                {140f, -60f}, {96f, 140f}, {-150f, 60f}, {-20f, 96f}
        }, 2.2f, false);

        return new int[]{formations, geodes};
    }
    /** Places large authored rocks; every one gets a box collider so the player
     *  and enemies can't walk through the formations. */
    private int placeRocks(AssetManager assetManager, BulletAppState bulletAppState, Random rand,
                           float[][] spots, float scale, boolean solid) {
        int count = 0;
        for (float[] p : spots) {
            String model = solid
                    ? (rand.nextBoolean() ? BIG_JAGGED_ROCKS[rand.nextInt(BIG_JAGGED_ROCKS.length)]
                            : BOULDER_ROCKS[rand.nextInt(BOULDER_ROCKS.length)])
                    : (rand.nextBoolean() ? BOULDER_ROCKS[rand.nextInt(BOULDER_ROCKS.length)]
                            : SMALL_DEBRIS[rand.nextInt(SMALL_DEBRIS.length)]);
            placeAt(assetManager, bulletAppState, model, p[0], p[1],
                    scale * (0.85f + rand.nextFloat() * 0.35f));
            count++;
        }
        return count;
    }

    private int placeRock(AssetManager assetManager, BulletAppState bulletAppState,
                          String model, float x, float z, float scale, boolean solid, Random rand) {
        placeAt(assetManager, bulletAppState, model, x, z, scale);
        return 1;
    }

    /** Red geode landmarks — clustered ember formations, never sprinkled. */
    private int placeGeodes(AssetManager assetManager, BulletAppState bulletAppState, Random rand,
                            float[][] spots, float scale) {
        int count = 0;
        for (float[] p : spots) {
            String model = rand.nextInt(4) == 0
                    ? EMBER_ACCENT[rand.nextInt(EMBER_ACCENT.length)]
                    : EMBER_GEODE[0];
            placeAt(assetManager, bulletAppState, model, p[0], p[1],
                    scale * (0.8f + rand.nextFloat() * 0.4f));
            count++;
        }
        return count;
    }

    private void placeAt(AssetManager assetManager, BulletAppState bulletAppState,
                         String model, float x, float z, float scale) {
        Spatial s = StageDecor.loadCached(assetManager, model).clone();
        s.setLocalScale(scale);
        s.updateModelBound();
        float lift = 0f;
        if (s.getWorldBound() instanceof BoundingBox bbox) {
            Vector3f extent = bbox.getExtent(new Vector3f());
            Vector3f center = bbox.getCenter(new Vector3f());
            lift = extent.y - center.y;
        }
        float y = heightAt(x, z);
        s.setLocalTranslation(x, y + lift, z);
        stageNode.attachChild(s);

        // every authored rock, boulder, geode and debris gets a solid hitbox so
        // neither the player nor enemies can brush straight through terrain props
        BoxCollisionShape shape = new BoxCollisionShape(
                new Vector3f(scale * 0.42f, scale * 0.5f, scale * 0.42f));
        RigidBodyControl physics = new RigidBodyControl(shape, 0f);
        physics.setPhysicsLocation(new Vector3f(x, y + scale * 0.5f, z));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }
}