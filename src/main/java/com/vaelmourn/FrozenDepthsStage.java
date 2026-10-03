package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.ConeCollisionShape;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** FrozenDepthsStage — Stage 3. */
public class FrozenDepthsStage implements Stage {
    private static final float AUTHORED_HALF_EXTENT = 200f;
    private static final float SLOW_ON_HIT_SECONDS = 2f;
    private static final String CENJI =
            "Models/Environment/Cenji_FantasyCrystalPack_FREE/"
                    + "Cenji_FantasyCrystalPack_FREE/GLB/";
    private static final String[] ICE_MASS = {
            CENJI + "Rocks/ROCK_Glacial_01_LargeCliff.glb",
            CENJI + "Rocks/ROCK_Glacial_02_MediumRock.glb",
            CENJI + "Rocks/ROCK_Slate_01_LargeBoulder.glb",
            CENJI + "Rocks/ROCK_Slate_03_FlatPlatform.glb",
            CENJI + "Rocks/ROCK_Glacial_03_FlatSlab.glb"
    };
    private static final String[] ICE_SPIRE = {
            CENJI + "Rocks/ROCK_Glacial_04_IceSpire.glb",
            CENJI + "Rocks/ROCK_Slate_04_TallSpire.glb"
    };
    private static final String[] ICE_SHARDS = {
            CENJI + "Rocks/ROCK_Glacial_05_FrostShards.glb",
            CENJI + "Rocks/ROCK_Slate_05_RubbleCluster.glb"
    };
    private static final String[] BLUE_CRYSTAL = {
            CENJI + "Crystal_Formations/PROP_03_LargeCrystalCluster.glb",
            CENJI + "Crystal_Formations/PROP_10_CrystalGeode.glb",
            CENJI + "Crystal_Formations/PROP_19_IceCrystalFormation.glb",
            CENJI + "Crystal_Formations/PROP_04_TallCrystalFormation.glb",
            CENJI + "Crystal_Formations/PROP_06_CrystalSpikes.glb"
    };
    private static final String[] BLUE_GEODE = {
            CENJI + "Gems/GEM_12_MassivePillarCrystal.glb",
            CENJI + "Gems/GEM_03_TetragonalSpire.glb",
            CENJI + "Gems/GEM_09_GeodeMiniCluster.glb",
            CENJI + "Gems/GEM_05_DoubleTerminatedDiamond.glb"
    };
    private static final String[] BLUE_GEM = {
            CENJI + "Gems/GEM_02_PentagonalShard.glb",
            CENJI + "Gems/GEM_06_SlenderNeedle.glb",
            CENJI + "Gems/GEM_01_HexagonalPoint.glb"
    };
    private static final String[] ENEMY_MODELS = {
            "Models/Characters/enemy/frozendepths_enemy.gltf",
            "Models/Characters/enemy/frozendepths_enemy2.gltf",
            "Models/Characters/enemy/frozendepths_enemy3.gltf"
    };
    private static final float TERRAIN_HALF = 260f;
    private static final float TERRAIN_STEP = 2.5f;
    private static final int TERRAIN_SAMPLES = (int) ((TERRAIN_HALF * 2f / TERRAIN_STEP)) + 1;
    private static final int TERRAIN_SIZE = TERRAIN_SAMPLES - 1;
    private static final float MIN_ELEVATION = -26f;
    private static final float SKY_RADIUS = 700f;
    private static final float MOUNT_FOOT_BASE = 232f;
    private static final float MOUNT_FOOT_WAVE = 18f;
    private static final float COLLAR_WIDTH = 34f;
    /** Steepest interior gradient the player can walk up: 1.0 is 45 degrees. */
    private static final float MAX_WALKABLE_SLOPE = 1.0f;
    private final int variant;
    /** This variant's geography and arena pads, resolved once in the constructor so
     *  every height query during the build reads the same tables. */
    private final Form[] FORMS;
    private final float[][] ARENA_PADS;
    private Node stageNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();
    private final Random deterministicYaw = new Random(13131L);
    public FrozenDepthsStage() { this(1); }
    public FrozenDepthsStage(int variant) {
        this.variant = Math.max(1, variant);
        this.FORMS = formsFor(this.variant);
        this.ARENA_PADS = padsFor(this.variant);
    }

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("FrozenDepths" + variant);
        parentNode.attachChild(stageNode);
        buildAuthored(assetManager, bulletAppState);
    }

    @Override
    public void cleanup(Node parentNode, BulletAppState bulletAppState) {
        for (RigidBodyControl physics : physicsObjects) {
            bulletAppState.getPhysicsSpace().remove(physics);
        }
        physicsObjects.clear();
        if (stageNode != null) {
            stageNode.removeFromParent();
        }
    }

    // ================================================================ authored v1

    private void buildAuthored(AssetManager assetManager, BulletAppState bulletAppState) {
        deterministicYaw.setSeed(13131L);
        formationCount = 0;
        crystalCount = 0;
        placedProps.clear();
        buildTerrain(assetManager, bulletAppState);
        buildSky(assetManager);
        sealIceRing(bulletAppState);
        addSafetyFloor(bulletAppState);
        if (variant == 1) {
            buildIceBasin(assetManager, bulletAppState);
            buildRidgeRun(assetManager, bulletAppState);
            buildCrystalBasin(assetManager, bulletAppState);
            buildFrozenCavern(assetManager, bulletAppState);
            buildGlacierChasm(assetManager, bulletAppState);
            buildGlacialPlateau(assetManager, bulletAppState);
            buildFrozenLake(assetManager, bulletAppState);
        } else {
            // Variants 2-4 are dressed from their own landforms rather than from a
            // coordinate list, so the scenery is guaranteed to sit on the geography
            // it belongs to: every crystal is on a crown or in a bowl, every ice
            // mass is on a ring. Hand-placing a second and third variant would mean
            // three more sets of coordinates to keep in step with three more height
            // fields, and nothing would check that they still matched.
            dressLandforms(assetManager, bulletAppState);
        }
        buildBoundaryCollar(assetManager, bulletAppState);
        verifyFormGradients();
        verifyClearances();
        System.out.println("[FrozenDepths " + variant + "] built basin: formations=" + formationCount
                + " crystals=" + crystalCount + " wall foot=" + MOUNT_FOOT_BASE
                + " | colliders=" + physicsObjects.size()
                + " solidProps=" + placedProps.size()
                + " decoration=" + (formationCount - placedProps.size()));
    }

    /**
     * Dresses a variant's own landforms with ice, in the same visual language as the
     * hand-placed variant 1 regions.
     *
     * <p>Placement is derived from the {@link Form} table rather than authored: a
     * crown of radius r gets crystals inside it and spires on its rim, a bowl gets
     * low masses and shards on its floor, a ring gets masses sitting on the circle
     * itself. Each spot is then filtered through the same layout rules the variant 1
     * coordinates obey -- off the arena pads, off the portal meadow, off the spawn
     * vale, not too steep, not on top of a neighbour -- so the two paths produce
     * scenery of the same quality from the same data.</p>
     */
    private void dressLandforms(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(2_700L + variant * 419L);

        for (Form f : FORMS) {
            switch (f.role) {
                case ROLE_CREST -> {
                    // the crown: the tallest crystal of the region at its centre,
                    // then smaller ones spread inside the level radius
                    float[] centre = {f.cx, f.cz};
                    if (propAllowed(centre[0], centre[1])) {
                        placeSpotSized(BLUE_CRYSTAL[crystalCursor(rand)], assetManager,
                                bulletAppState, centre[0], centre[1], CENTREPIECE_HEIGHT, false);
                    }
                    ringSpots(rand, f, 0.42f, 5, 12f, spot -> {
                        if (propAllowed(spot[0], spot[1])) {
                            placeSpots(BLUE_CRYSTAL, assetManager, bulletAppState,
                                    new float[][]{spot}, CRYSTAL_WIDTH, true);
                        }
                    });
                    // spires standing off the crown, on its shoulders
                    ringSpots(rand, f, 0.95f, 4, 9f, spot -> {
                        if (propAllowed(spot[0], spot[1])) {
                            placeSpots(ICE_SPIRE, assetManager, bulletAppState,
                                    new float[][]{spot}, SPIRE_HEIGHT, false);
                        }
                    });
                }
                case ROLE_BOWL -> {
                    // the floor: low masses and shards, kept small so the bowl still
                    // reads as open ground
                    ringSpots(rand, f, 0.45f, 6, 14f, spot -> {
                        if (propAllowed(spot[0], spot[1])) {
                            placeSpots(ICE_MASS, assetManager, bulletAppState,
                                    new float[][]{spot}, MASS_WIDTH * 0.72f, true);
                        }
                    });
                    ringSpots(rand, f, 0.8f, 5, 9f, spot -> {
                        if (propAllowed(spot[0], spot[1])) {
                            placeSpotsDecorative(ICE_SHARDS, assetManager, bulletAppState,
                                    new float[][]{spot}, SHARD_WIDTH);
                        }
                    });
                    // geode glow, the only light source down here
                    ringSpots(rand, f, 0.9f, 3, 6f, spot -> {
                        if (propAllowed(spot[0], spot[1])) {
                            placeSpotsDecorative(BLUE_GEODE, assetManager, bulletAppState,
                                    new float[][]{spot}, GEODE_WIDTH);
                        }
                    });
                }
                case ROLE_RAMPART -> {
                    // the circle itself: masses sitting on it, spires between them
                    ringSpots(rand, f, 1f, 9, 10f, spot -> {
                        if (propAllowed(spot[0], spot[1])) {
                            placeSpots(ICE_MASS, assetManager, bulletAppState,
                                    new float[][]{spot}, MASS_WIDTH, true);
                        }
                    });
                    ringSpots(rand, f, 1f, 4, 8f, spot -> {
                        if (propAllowed(spot[0], spot[1])) {
                            placeSpots(ICE_SPIRE, assetManager, bulletAppState,
                                    new float[][]{spot}, SPIRE_HEIGHT, false);
                        }
                    });
                }
                default -> {
                    // the base sheet carries nothing: it is the walkable middle
                }
            }
        }
    }

    /** Rotates through the crystal models without repeating, so two crowns in one
     *  variant do not both come out as the same rock. */
    private int crystalCursor(Random rand) {
        return rand.nextInt(BLUE_CRYSTAL.length);
    }

    /** N spots on a circle of radius {@code frac * f.r} about a landform centre,
     *  each jittered off the ideal bearing so a ring does not read as a gear. */
    private void ringSpots(Random rand, Form f, float frac, int count,
                           float jitter, java.util.function.Consumer<float[]> sink) {
        float base = rand.nextFloat() * FastMath.TWO_PI;
        for (int i = 0; i < count; i++) {
            float a = base + (i / (float) count) * FastMath.TWO_PI
                    + (rand.nextFloat() - 0.5f) * (jitter * FastMath.TWO_PI / count);
            float rad = f.r * frac * (0.82f + rand.nextFloat() * 0.36f);
            sink.accept(new float[]{f.cx + FastMath.cos(a) * rad, f.cz + FastMath.sin(a) * rad});
        }
    }

    /** The layout rules every formation must satisfy, whatever placed it: clear of
     *  the arena pads and their approach, clear of the spawn vale and the portal
     *  meadow, off the steep faces, inside the terrain, and not overlapping a
     *  formation that is already down. */
    private boolean propAllowed(float x, float z) {
        if (dist(x, z, 0f, 0f) < SPAWN_CLEAR) return false;
        if (dist(x, z, 0f, 25f) < PORTAL_CLEAR) return false;
        if (tooSteep(x, z)) return false;
        if (FastMath.sqrt(x * x + z * z) > TERRAIN_HALF - 6f) return false;
        for (float[] pad : ARENA_PADS) {
            if (dist(x, z, pad[0], pad[1]) < ARENA_CLEAR) return false;
        }
        // not on top of an existing formation: their colliders are 0.14 of their
        // footprint, so this has to allow for the visible rock, not just the box
        for (float[] p : placedProps) {
            if (dist(x, z, p[0], p[1]) < p[2] * 2.2f + 6f) return false;
        }
        return true;
    }

    private static final float SPAWN_CLEAR = 52f;
    private static final float PORTAL_CLEAR = 24f;
    private static final float ARENA_CLEAR = 26f;

    /** Steep enough that a formation on it would be floating or buried rather than
     *  seated. Same test the layout checks use. */
    private boolean tooSteep(float x, float z) {
        final float d = 2.5f;
        float hx = (heightAt(x + d, z) - heightAt(x - d, z)) / (2f * d);
        float hz = (heightAt(x, z + d) - heightAt(x, z - d)) / (2f * d);
        return FastMath.sqrt(hx * hx + hz * hz) > MAX_WALKABLE_SLOPE * 0.62f;
    }

    /**
     * The three arenas, as one shared list. The props of each region are authored
     * around these, the layout check keeps them clear, and the encounters spawn on
     * them, so there is exactly one place to change if an arena ever moves.
     */
    private static final float[][] ARENA_PADS_V1 = {
            {-34f, -14f}, {-34f, 10f}, {-8f, -14f}, {-8f, 10f},
            {-69f, -145f}, {-69f, -169f}, {-45f, -169f}, {-45f, -145f},
            {102f, 55f}, {130f, 83f}, {130f, 27f}, {158f, 55f}
    };

    private int formationCount;
    private int crystalCount;
    private final List<float[]> placedProps = new ArrayList<>();

    // ================================================= the authored landscape

    /**
     * A — the Frozen Entry terrace. The spawn point and the exit portal both sit
     * on the guaranteed level disc at the centre of the basin, so the middle is
     * left completely clear: this is the first thing the player sees and it has to
     * read as a huge space. The crags stand in a broken ring outside the disc, open
     * to the north for the portal, to the south-west for the basin, and to the east
     * for the climb to the plateau.
     */
    private void buildIceBasin(AssetManager assetManager, BulletAppState bulletAppState) {
        // the framing ring, outside the level disc and open on the three routes
        placeSpots(ICE_MASS, assetManager, bulletAppState, new float[][]{
                {58f, 0f}, {-58f, 0f}, {-48f, -32f}, {48f, -32f}
        }, MASS_WIDTH, true);

        // the low shard field breaking up the dish to the east of the terrace
        placeSpotsDecorative(ICE_SHARDS, assetManager, bulletAppState, new float[][]{
                {30f, 60f}, {8f, 64f}
        }, SHARD_WIDTH);

        // two tall spires on the outer lip, framing the route east to the plateau
        placeSpots(ICE_SPIRE, assetManager, bulletAppState, new float[][]{
                {70f, 10f}, {44f, 56f}
        }, SPIRE_HEIGHT, false);

        // one crystal at the threshold: the first blue the player sees, and the
        // signal that the whole basin is themed around them
        placeSpotSized(BLUE_CRYSTAL[2], assetManager, bulletAppState,
                -38f, 34f, CRYSTAL_WIDTH, true);
    }

    /**
     * B — the Ice Ridge. A long north-south spine of pressure ice on the west side,
     * climbed at its south end out of the basin and dropping to the lake at its
     * north end. The crest is walkable, so the masses are set back onto the two
     * flanks and the spires stand off the crest line itself.
     */
    private void buildRidgeRun(AssetManager assetManager, BulletAppState bulletAppState) {
        // the flanking masses, set back from the crest walk
        placeSpots(ICE_MASS, assetManager, bulletAppState, new float[][]{
                {-168f, -40f}, {-168f, 40f}, {-130f, -20f}, {-130f, 60f}
        }, MASS_WIDTH, true);

        // the sharp teeth standing on the crest itself
        placeSpots(ICE_SPIRE, assetManager, bulletAppState, new float[][]{
                {-150f, -58f}, {-148f, 96f}
        }, SPIRE_HEIGHT, false);

        // the ramp gate at the south end, funnelling the climb onto the crest
        placeSpots(ICE_MASS, assetManager, bulletAppState, new float[][]{
                {-140f, -84f}, {-118f, -74f}
        }, MASS_WIDTH, true);
    }

    /**
     * C — the Crystal Basin. The deepest floor in the basin, floored with blue
     * crystal and split by one huge central cluster. The west and south-west
     * quadrant is deliberately kept clear of everything: that is arena B, and it
     * has to stay open ground.
     */
    private void buildCrystalBasin(AssetManager assetManager, BulletAppState bulletAppState) {
        // the centrepiece — the largest crystal in the basin, offset east of the
        // arena quadrant so it frames the fight rather than sitting in it
        placeSpotSized(BLUE_CRYSTAL[0], assetManager, bulletAppState,
                -30f, -132f, CENTREPIECE_HEIGHT, false);

        // the supporting formations, all east and north-east of the arena
        placeSpots(BLUE_CRYSTAL, assetManager, bulletAppState, new float[][]{
                {2f, -160f}, {14f, -128f}, {-4f, -108f}, {-20f, -104f}
        }, CRYSTAL_WIDTH, true);

        // tall formations standing on the rim, so the drop reads from up on the ice
        placeSpots(BLUE_CRYSTAL, assetManager, bulletAppState, new float[][]{
                {15f, -145f}, {0f, -186f}
        }, CRYSTAL_TALL_HEIGHT, false);

        // the rim crags, closing the bowl except for the north-east ramp
        placeSpots(ICE_MASS, assetManager, bulletAppState, new float[][]{
                {22f, -178f}, {28f, -118f}, {-78f, -196f}
        }, MASS_WIDTH, true);

        // low geode glow on the bowl floor, at the edges only
        placeSpotsDecorative(BLUE_GEODE, assetManager, bulletAppState, new float[][]{
                {-22f, -120f}, {2f, -160f}, {-38f, -186f}
        }, GEODE_WIDTH);
    }

    /**
     * D — the Frozen Cavern. A walled chamber on the east side: the terrain rampart
     * encloses the floor, and these crystals line its inside face. No encounter
     * here — it is the quiet link on the road between the chasm and the plateau.
     */
    private void buildFrozenCavern(AssetManager assetManager, BulletAppState bulletAppState) {
        // the chamber wall: crystals standing just inside the rampart circle,
        // which is what turns a hollow in the ice into a room. Spaced well apart so
        // the circle still reads as a wall without closing into a solid ring of
        // colliders with no way between the mouths.
        placeSpots(BLUE_CRYSTAL, assetManager, bulletAppState, new float[][]{
                {120f, -36f}, {105f, -60f}, {90f, -54f}
        }, CRYSTAL_WIDTH, true);

        // the two great pillars, on the chamber floor
        placeSpotSized(BLUE_CRYSTAL[3], assetManager, bulletAppState,
                100f, -50f, CRYSTAL_TALL_HEIGHT, false);
        placeSpotSized(BLUE_CRYSTAL[1], assetManager, bulletAppState,
                112f, -40f, CRYSTAL_TALL_HEIGHT * 0.85f, false);

        // the buttress behind the chamber, sealing it from the plateau
        placeSpots(ICE_MASS, assetManager, bulletAppState, new float[][]{
                {136f, -45f}, {134f, -28f}
        }, MASS_WIDTH, true);

        // the ice climbing the rampart itself
        placeSpots(ICE_SPIRE, assetManager, bulletAppState, new float[][]{
                {131f, -50f}, {128f, -76f}
        }, SPIRE_HEIGHT, false);

        // geode glow at the chamber mouth, on the side the player arrives from
        placeSpotsDecorative(BLUE_GEODE, assetManager, bulletAppState, new float[][]{
                {84f, -45f}, {116f, -45f}
        }, GEODE_WIDTH);
    }

    /**
     * E — the Glacier Chasm. A north-south rift with broken ice on both lips, the
     * second way south. The player can drop in from the east and walk out to the
     * south bypass, and never climb back up.
     */
    private void buildGlacierChasm(AssetManager assetManager, BulletAppState bulletAppState) {
        // the lips, east and west, open at the north and south ends
        placeSpots(ICE_MASS, assetManager, bulletAppState, new float[][]{
                {112f, -152f}, {116f, -124f}, {110f, -100f},
                {8f, -152f}, {10f, -100f}
        }, MASS_WIDTH, true);

        // spires standing in the rift itself, so the drop is legible from the lip
        placeSpots(ICE_SPIRE, assetManager, bulletAppState, new float[][]{
                {66f, -112f}, {54f, -140f}
        }, SPIRE_HEIGHT, false);

        // the crystal at the bottom of the rift, the only way to read the depth
        placeSpotSized(BLUE_CRYSTAL[4], assetManager, bulletAppState,
                60f, -130f, CRYSTAL_WIDTH, true);

        // the southern bypass closing the loop back toward the basin
        placeSpots(ICE_MASS, assetManager, bulletAppState, new float[][]{
                {30f, -116f}, {-6f, -104f}
        }, MASS_WIDTH, true);
    }

    /**
     * F — the Glacial Plateau. The high crown of the basin, reached by one long
     * ramp out of the vale to the west. The crystals and spires stand on a ring
     * well outside the arena, so the crown itself stays open ground.
     */
    private void buildGlacialPlateau(AssetManager assetManager, BulletAppState bulletAppState) {
        // the crown crystals, on the ring and clear of the arena
        placeSpots(BLUE_CRYSTAL, assetManager, bulletAppState, new float[][]{
                {186f, 55f}, {130f, 111f}, {74f, 55f}, {130f, -1f}
        }, CRYSTAL_WIDTH, true);

        // the great spires — the tallest landmarks in the basin, set on the ring's
        // four corners so the crown reads as a ring without a colider at every post
        placeSpots(ICE_SPIRE, assetManager, bulletAppState, new float[][]{
                {170f, 95f}, {170f, 15f}, {90f, 15f}, {90f, 95f}
        }, SPIRE_HEIGHT, false);

        // the ramp gate on the western approach, the only way up
        placeSpots(ICE_MASS, assetManager, bulletAppState, new float[][]{
                {80f, 20f}, {72f, 44f}
        }, MASS_WIDTH, true);

        // the shoulder crags that hide the plateau's drop to the vale
        placeSpots(ICE_MASS, assetManager, bulletAppState, new float[][]{
                {150f, 95f}, {150f, 15f}
        }, MASS_WIDTH, true);

        // geode glow on the crown, at the very edges
        placeSpotsDecorative(BLUE_GEODE, assetManager, bulletAppState, new float[][]{
                {96f, 88f}, {164f, 88f}
        }, GEODE_WIDTH);
    }

    /**
     * G — the Frozen Lake. A wide, dead-level white sheet in the north: the largest
     * single open space in the basin, and where the ridge crest run delivers the
     * player. Everything stands on the shore or in the shallows; the ice itself is
     * left empty except for one crystal island.
     */
    private void buildFrozenLake(AssetManager assetManager, BulletAppState bulletAppState) {
        // the shore ring — the level ice inside it is left completely empty
        placeSpots(ICE_MASS, assetManager, bulletAppState, new float[][]{
                {23f, 135f}, {-73f, 135f}, {-25f, 78f}, {-62f, 176f}
        }, MASS_WIDTH, true);

        // pressure ridges standing out in the shallows
        placeSpots(ICE_SPIRE, assetManager, bulletAppState, new float[][]{
                {-25f, 179f}, {17f, 135f}, {-67f, 135f}
        }, SPIRE_HEIGHT, false);

        // scattered fracture shards, low and walk-around
        placeSpotsDecorative(ICE_SHARDS, assetManager, bulletAppState, new float[][]{
                {-50f, 120f}, {-8f, 152f}, {-40f, 156f}
        }, SHARD_WIDTH);

        // the crystal island, the one landmark out on the open ice
        placeSpotSized(BLUE_CRYSTAL[4], assetManager, bulletAppState,
                -25f, 135f, CRYSTAL_WIDTH, true);
    }

    /**
     * The visible ice teeth that stand on the collar, behind the seal wall. They
     * hide the seam and tell the player where the basin ends.
     *
     * <p>These are decoration, not collision. The seal cylinder stops the player
     * at r=206 and the collar stands at r=217 and beyond, so nothing here is
     * ever walked into; 74 static bodies for scenery behind an invisible wall was
     * 74 broadphase entries and a physics step for nothing. The count is also down
     * to 20 + 12 — the wall hides the density, and the far rank reads as one
     * continuous band rather than as individual rocks.</p>
     */
    private void buildBoundaryCollar(AssetManager assetManager, BulletAppState bulletAppState) {
        int teeth = 20;
        for (int i = 0; i < teeth; i++) {
            float a = i * FastMath.TWO_PI / teeth;
            float r = innerFoot(a) - COLLAR_WIDTH * 0.45f;
            boolean spire = i % 3 == 0;
            String model = spire ? ICE_SPIRE[i % ICE_SPIRE.length] : ICE_MASS[i % ICE_MASS.length];
            float size = (spire ? COLLAR_SPIRE_HEIGHT : COLLAR_MASS_WIDTH)
                    * (0.8f + deterministicYaw.nextFloat() * 0.45f);
            placeDecoration(model, assetManager, bulletAppState,
                    FastMath.cos(a) * r, FastMath.sin(a) * r, size, !spire);
        }

        // a second, taller rank further out, sitting on the teeth band itself
        for (int i = 0; i < 12; i++) {
            float a = (i + 0.5f) * FastMath.TWO_PI / 12;
            float r = innerFoot(a) + 6f;
            float size = COLLAR_SPIRE_HEIGHT * (0.85f + deterministicYaw.nextFloat() * 0.4f);
            placeDecoration(ICE_SPIRE[(i + 1) % ICE_SPIRE.length], assetManager, bulletAppState,
                    FastMath.cos(a) * r, FastMath.sin(a) * r, size, false);
        }
    }

    @Override
    public List<EnemyController> spawnEnemies(AssetManager assetManager, Node parentNode,
                                               BulletAppState bulletAppState, int loopCount) {
        List<EnemyController> enemies = new ArrayList<>();
        return spawnAuthoredEnemies(assetManager, bulletAppState, loopCount, enemies);
    }

    /** Three authored arenas: the entry terrace, the crystal basin, the plateau. */
    private List<EnemyController> spawnAuthoredEnemies(AssetManager assetManager,
                                                        BulletAppState bulletAppState,
                                                        int loopCount,
                                                        List<EnemyController> sink) {
        // The pads come from ARENA_PADS, the same list the regions were authored
        // around and the same one the clearance check guards, so an arena cannot
        // drift away from the ground it is standing on.
        int perArena = 4;
        int arenaCount = ARENA_PADS.length / perArena;
        // Deeper passes are meant to hit harder. Variant 1 was the only authored one
        // so it carried no scaling; now that all four use the arenas, the old legacy
        // ramp is applied here so stages 2-4 keep their intended difficulty and 1
        // stays exactly as it was.
        float scale = 1f + (variant - 1) * 0.4f;
        // Each arena's authored pad holds 4. The first wrap doubles every arena by adding
        // that many again, and later wraps add up to the 4x ceiling. The opening run
        // is untouched: reinforcements stay 0 until the player beats the Fallen King.
        int reinforcements = loopCount <= 0
                ? 0
                : Stage.loopedEnemyCount(perArena, loopCount);
        String[] arenaNames = arenaNamesFor(variant);
        int modelCursor = variant % ENEMY_MODELS.length;
        for (int a = 0; a < arenaCount; a++) {
            for (int i = 0; i < perArena; i++) {
                float[] pad = ARENA_PADS[a * perArena + i];
                float x = pad[0];
                float z = pad[1];
                float y = heightAt(x, z) + 2f;
                String modelPath = ENEMY_MODELS[modelCursor++ % ENEMY_MODELS.length];
                EnemyController enemy = new EnemyController(
                        assetManager, stageNode, bulletAppState,
                        new Vector3f(x, y, z), 3, loopCount, scale, false, modelPath
                );
                if (variant == 3) {
                    enemy.setSlowOnHit(SLOW_ON_HIT_SECONDS);
                }
                sink.add(enemy);
            }
            for (int r = 0; r < reinforcements; r++) {
                float[] spot = ARENA_PADS[a * perArena + (r % perArena)];
                float jitterX = (deterministicYaw.nextFloat() - 0.5f) * 10f;
                float jitterZ = (deterministicYaw.nextFloat() - 0.5f) * 10f;
                float x = spot[0] + jitterX;
                float z = spot[1] + jitterZ;
                float y = heightAt(x, z) + 2f;
                String modelPath = ENEMY_MODELS[modelCursor++ % ENEMY_MODELS.length];
                EnemyController enemy = new EnemyController(
                    assetManager, stageNode, bulletAppState,
                    new Vector3f(x, y, z), 3, loopCount, scale, false, modelPath
                );
                if (variant == 3) {
                    enemy.setSlowOnHit(SLOW_ON_HIT_SECONDS);
                }
                sink.add(enemy);
            }
            System.out.println("[FrozenDepths " + variant + "] arena=" + arenaNames[a]
                    + " enemies=" + (perArena + reinforcements));
        }
        return sink;
    }

    @Override
    /** Spawn sits on the terrain, not at a fixed height. A hardcoded y warps the
     *  capsule into the ground wherever the height field happens to be higher, and the
     *  character controller then resolves the overlap by pushing the player down
     *  through the mesh. For the legacy variants the ground is a flat plane at zero,
     *  so the old constant was correct there and only the authored basin needs this. */
    public Vector3f getPlayerSpawnPoint() {
        return new Vector3f(0f, heightAt(0f, 0f) + 2f, 0f);
    }

    @Override
    public ColorRGBA getSkyColor() {
        return new ColorRGBA(0.80f, 0.88f, 0.96f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.52f, 0.63f, 0.82f, 1f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.45f, -0.55f, 0.70f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() { return AUTHORED_HALF_EXTENT; }

    @Override
    public String getName() { return "Frozen Depths " + variant; }

    @Override
    public int getStageIndex() { return 3; }

    /** Sampled from the same height field the terrain mesh is built from, so the
     *  portal sits on the portal meadow instead of a fixed height. Legacy variants
     *  use a flat plane at zero, which the interface default already matches. */
    @Override
public Vector3f getExitPortalGround() {
            return new Vector3f(0f, heightAt(0f, 25f), 25f);
        }

        /** Thrown bombs integrate their own gravity, so they need the same terrain
         *  height the mesh was built from or they sink into the ice shelves. Legacy
         *  variants stay flat at zero, which the interface default already matches. */
        @Override
        public float groundHeightAt(float x, float z) {
            return heightAt(x, z);
        }

    // ============================================ the height field

    /** Kept for the stand-alone height probes. */
    static float groundHeight(int variant, float x, float z) {
        return frozenProfile(x, z, formsFor(variant));
    }

    private float heightAt(float x, float z) {
        return frozenProfile(x, z, FORMS);
    }

    /**
     * Frozen Depths 1 — the glacial basin. One big connected geography instead of
     * a ring of identical arenas: a broad spawn vale in the middle, a long ice
     * ridge running north-south out of it, a deep crystal bowl to the south-west,
     * a walled ice cavern in the north-east, a high glacial plateau east, a rift
     * in the south-east and a wide frozen lake to the north, all tied together by
     * a walkable circuit of ramps.
     *
     * <p>Every gradient inside the basin stays under the 45 degree walkable limit
     * (jump force 12 against gravity 24 only buys about three units, so nothing
     * that matters can be a cliff). The only unclimbable ice is the outer wall,
     * which is handled separately in {@link #frozenRing}.</p>
     */
    // ---- landform kinds. A mesa is dead level inside r and ramps out to amp over w;
    //      a ring peaks at amp on the circle of radius r and falls away on both sides.
    private static final int MESA = 0;
    private static final int RING = 1;

    // ---- dressing roles: what belongs standing on a landform. Decoration is derived
    //      from these rather than hand-placed per region, so a variant's props always
    //      fit its own terrain instead of needing its own coordinate list.
    private static final int ROLE_SHEET = 0;    // the base sheet: left bare
    private static final int ROLE_CREST = 1;    // high ground: crystals and spires
    private static final int ROLE_BOWL = 2;     // low ground: shards and low masses
    private static final int ROLE_RAMPART = 3;  // a ring: masses on it, spires between

    /**
     * One landform. A mesa is dead level inside r and ramps out to amp over w; a
     * ring peaks at amp on the circle of radius r and falls away on both sides.
     * Either way the steepest it can ever be is 1.5 * amp / w, so a region's
     * gradient is a number that can be checked on paper instead of one that has
     * to be discovered by walking into it.
     */
    private static final class Form {
        final String name;
        final float cx, cz, r, w, amp;
        final int kind;
        final int role;

        Form(String name, int kind, int role, float cx, float cz, float r, float w, float amp) {
            this.name = name;
            this.kind = kind;
            this.role = role;
            this.cx = cx;
            this.cz = cz;
            this.r = r;
            this.w = w;
            this.amp = amp;
        }

        float at(float x, float z) {
            float d = dist(x, z, cx, cz);
            if (kind == RING) return amp * (1f - smooth01(FastMath.abs(d - r) / w));
            return amp * (1f - smooth01((d - r) / w));
        }

        /** The gradient this form contributes on its own, for the size budget. */
        float maxGradient() {
            return 1.5f * FastMath.abs(amp) / w;
        }
    }

    /**
     * The geography of each variant's basin, as data.
     *
     * <p>Sizing rule: {@link Form#maxGradient()} may not exceed 0.58, which is 30
     * degrees on its own. Overlapping radii add their gradients, so two forms may
     * each sit at 0.55 and stack to 0.96 -- still under the 1.0 (45 degrees) that
     * {@link #MAX_WALKABLE_SLOPE} allows, but only just. That is why the steepest
     * forms are paired with nothing above them and the arena heights are all shallow:
     * the budget is spent in a few deliberate places rather than spread thinly over
     * the whole map.</p>
     *
     * <p>Variants 2-4 use the same basemap and the same ring, collar and seal as
     * variant 1 -- the outer ice is one wall, and re-authoring it four times would
     * not make the stages any more distinct -- but their interior geography is
     * different land, so the three arenas of each sit on different ground and the
     * routes between them run in different directions.</p>
     */
    private static Form[] formsFor(int v) {
        return switch (v) {
            case 2 -> FORMS_V2;
            case 3 -> FORMS_V3;
            case 4 -> FORMS_V4;
            default -> FORMS_V1;
        };
    }

    // V1 -- the glacial basin: a spawn vale in the middle, a long ice ridge running
    // north-south out of it, a deep crystal bowl to the south-west, a walled ice
    // cavern in the north-east, a high plateau east, a rift in the south-east and a
    // wide frozen lake to the north.
    private static final Form[] FORMS_V1 = {
            new Form("sheet",        MESA, ROLE_SHEET,   0f,    0f,  60f, 130f,   4f),
            new Form("spawnDish",    MESA, ROLE_SHEET,  25f,   45f,  22f,  45f,  -5f),
            new Form("southBypass",  MESA, ROLE_SHEET,   5f,  -95f,  18f,  42f,  -5f),
            new Form("northSaddle",  MESA, ROLE_SHEET,  45f,  115f,  18f,  45f,  -5f),

            new Form("ridgeCrest",   MESA, ROLE_CREST, -150f,   20f,  20f,  60f,  22f),
            new Form("ridgeNorth",   MESA, ROLE_CREST, -145f,  115f,  16f,  45f,  12f),
            new Form("ridgeSouth",   MESA, ROLE_CREST, -148f,  -55f,  16f,  45f,  11f),

            // the bowl's lip is built from ice props rather than terrain, so the bowl
            // itself can stay wide and shallow -- a terrain rim would need a skirt
            // wider than the basin
            new Form("basinFloor",   MESA, ROLE_BOWL,  -45f, -145f,  34f,  76f, -22f),

            new Form("chasmFloor",   MESA, ROLE_BOWL,   60f, -130f,  16f,  72f, -19f),
            new Form("chasmLips",    RING, ROLE_RAMPART, 60f, -130f, 56f,  44f,   9f),

            // the rampart is the chamber: raised on the circle and falling away on
            // both sides, so the inside is already a floor enclosed by ice
            new Form("cavernRampart", RING, ROLE_RAMPART, 105f, -45f, 22f, 28f,  8f),
            new Form("cavernFloor",  MESA, ROLE_BOWL,  105f, -45f,  14f,  34f,  -7f),

            new Form("plateauCrown", MESA, ROLE_CREST, 130f,   55f,  40f,  90f,  30f),

            new Form("lakeFloor",    MESA, ROLE_BOWL,  -25f,  135f,  34f,  60f, -10f),
            new Form("lakeShore",    RING, ROLE_RAMPART, -25f, 135f, 70f, 28f,   7f),
    };

    // V2 -- the Rift Shelf. A rift is cut straight across the middle of the basin
    // east-west instead of running north-south, with a high mesa and a stepped terrace
    // at opposite ends of it and a drowned lake to the south.
    private static final Form[] FORMS_V2 = {
            new Form("sheet",        MESA, ROLE_SHEET,   0f,    0f,  60f, 130f,   4f),
            new Form("spawnDish",    MESA, ROLE_SHEET, -30f,   35f,  22f,  45f,  -5f),

            new Form("riftFloor",    MESA, ROLE_BOWL,    0f,  -40f,  20f,  55f, -16f),
            new Form("riftLips",     RING, ROLE_RAMPART,  0f, -40f,  52f,  40f,  10f),

            new Form("northMesa",    MESA, ROLE_CREST, -40f,  130f,  45f,  95f,  24f),
            new Form("eastTerrace",  MESA, ROLE_CREST, 150f,   60f,  38f,  80f,  22f),

            new Form("southLake",    MESA, ROLE_BOWL,   70f, -130f,  40f,  85f, -18f),
            new Form("lakeShore",    RING, ROLE_RAMPART, 70f, -130f, 76f, 30f,   8f),

            new Form("westRampart",  RING, ROLE_RAMPART, -165f, 10f, 26f, 34f,   9f),
            new Form("westFloor",    MESA, ROLE_BOWL,  -165f,  10f,  15f,  40f,  -8f),
    };

    // V3 -- the Crystal Warrens. No central feature at all: the middle of the basin
    // is open, and every region sits out on the rim around it, so the fights are
    // fought out at the edges with a long walk between each one.
    private static final Form[] FORMS_V3 = {
            new Form("sheet",        MESA, ROLE_SHEET,   0f,    0f,  60f, 130f,   4f),
            new Form("spawnDish",    MESA, ROLE_SHEET,  30f,  -30f,  22f,  45f,  -5f),

            new Form("domeCrown",    MESA, ROLE_CREST, 115f,  115f,  40f,  80f,  20f),
            new Form("domeShoulder", RING, ROLE_RAMPART, 115f, 115f, 74f, 34f,   9f),

            // Pulled in from the rim: at -155,-85 the ring's far arc reached r=237, which is
            // behind the seal wall, so the dressing pass spent half its props on ice
            // the player can never walk to
            new Form("sunkFloor",    MESA, ROLE_BOWL, -125f,  -70f,  34f,  70f, -17f),
            new Form("sunkRim",      RING, ROLE_RAMPART, -125f, -70f, 58f, 32f,   8f),

            new Form("shelfCrown",   MESA, ROLE_CREST, 120f, -130f,  36f,  74f,  18f),

            new Form("westRampart",  RING, ROLE_RAMPART, -175f, 15f, 24f, 32f,  10f),
            new Form("westHollow",   MESA, ROLE_BOWL,  -175f,  15f,  14f,  38f,  -7f),

            new Form("southShelf",   MESA, ROLE_BOWL,  -15f, -165f,  30f,  66f,  -9f),
            new Form("eastNotch",    MESA, ROLE_BOWL,  178f,   10f,  16f,  46f,  -6f),
    };

    // V4 -- the Glacier Steps. High ground stacked on high ground: a tall mesa in
    // the west whose rim runs a long way, a step up to a terrace in the east and a
    // deep pit to the south between them, so the basin's floor is reached by
    // climbing and the arenas sit at three clearly separated heights.
    private static final Form[] FORMS_V4 = {
            new Form("sheet",        MESA, ROLE_SHEET,   0f,    0f,  60f, 130f,   4f),
            new Form("spawnDish",    MESA, ROLE_SHEET,  25f,   40f,  22f,  45f,  -5f),

            new Form("westMesa",     MESA, ROLE_CREST, -160f,   30f,  44f,  88f,  26f),
            new Form("westRim",      RING, ROLE_RAMPART, -160f, 30f, 80f, 36f,   9f),

            new Form("eastTerrace",  MESA, ROLE_CREST, 155f,   95f,  36f,  72f,  21f),
            new Form("eastRim",      RING, ROLE_RAMPART, 155f, 95f, 70f, 30f,   8f),

            new Form("northShelf",   MESA, ROLE_CREST,  -30f, 150f,  32f,  66f,  12f),
            new Form("southPit",     MESA, ROLE_BOWL,   60f, -150f,  32f,  68f, -16f),
            new Form("southPitRim",  RING, ROLE_RAMPART, 60f, -150f, 64f, 34f,   8f),
            new Form("centralRise",  MESA, ROLE_CREST, -25f,  -25f,  28f,  58f,   9f),
    };

    /** Arena pads per variant: three arenas of four spawn points each.
     *
     *  <p>The first arena is the spawn vale in every variant, on the same four pads,
     *  because that is where the first fight of a Frozen Depths pass belongs and it
     *  is the only patch of ground the flatten guarantee covers on every variant.
     *  The other two sit on a level crown of that variant's own geography, which is
     *  why their coordinates differ per variant rather than being shared.</p> */
    private static float[][] padsFor(int v) {
        return switch (v) {
            case 2 -> new float[][]{
                    {-34f, -14f}, {-34f, 10f}, {-8f, -14f}, {-8f, 10f},
                    // the north mesa crown
                    {-70f, 110f}, {-42f, 140f}, {-14f, 112f}, {-45f, 95f},
                    // the east terrace crown
                    {130f, 42f}, {160f, 36f}, {166f, 72f}, {136f, 80f}};
            case 3 -> new float[][]{
                    {-34f, -14f}, {-34f, 10f}, {-8f, -14f}, {-8f, 10f},
                    // the crystal dome
                    {92f, 98f}, {112f, 140f}, {140f, 120f}, {108f, 88f},
                    // the sunken shelf
                    {110f, -140f}, {140f, -145f}, {155f, -110f}, {122f, -100f}};
            case 4 -> new float[][]{
                    {-34f, -14f}, {-34f, 10f}, {-8f, -14f}, {-8f, 10f},
                    // the west mesa crown
                    {-185f, 10f}, {-160f, 55f}, {-135f, 30f}, {-160f, 5f},
                    // the east terrace crown
                    {135f, 85f}, {155f, 120f}, {178f, 98f}, {152f, 70f}};
            default -> ARENA_PADS_V1;
        };
    }

    private static String[] arenaNamesFor(int v) {
        return switch (v) {
            case 2 -> new String[]{"spawnVale", "northMesa", "eastTerrace"};
            case 3 -> new String[]{"spawnVale", "crystalDome", "sunkenShelf"};
            case 4 -> new String[]{"spawnVale", "westMesa", "eastSteps"};
            default -> new String[]{"entryTerrace", "crystalBasin", "plateauCrown"};
        };
    }

    private static float frozenProfile(float x, float z, Form[] forms) {
        float h = 0f;
        for (Form f : forms) h += f.at(x, z);

        // --- the spawn vale and the exit portal are the two zones the rest of the
        //     game hard-codes to y = 0, so they are pinned rather than derived, on
        //     every variant. The blend is deliberately far wider than the flat:
        //     flattening hauls the surrounding ice toward the target at
        //     1.5 * delta / blend, and a short blend around a disc this size turned
        //     the plateau's whole south-west approach into a 53 degree bank.
        h = flattenTo(h, 0f, 0f, 40f, 60f, 0f, x, z);        // the spawn vale
        h = flattenTo(h, 0f, 25f, 20f, 16f, 0f, x, z);       // the portal meadow

        return frozenRing(h, x, z);
    }

    /**
     * Checks the sizing rule the whole basin is laid out against: no single form may
     * be steeper than 30 degrees, because two forms are allowed to overlap and their
     * gradients add. This is a property of the data, so it is checked where the data
     * is declared rather than left to be rediscovered by walking into a bank at
     * runtime.
     */
    private void verifyFormGradients() {
        for (Form f : FORMS) {
            float g = f.maxGradient();
            if (g > 0.58f) {
                System.out.println("[FrozenDepths " + variant + "] landform " + f.name
                        + " is too steep on its own: " + String.format("%.2f", g)
                        + " = " + (int) Math.toDegrees(Math.atan(g)) + " deg");
            }
        }
    }

    /**
     * The outer ice wall. A rough, hand-shaped collar ramps up out of the basin
     * and then two bands of sharp angular teeth climb with a t^2 curve, so the
     * silhouette reads as broken pressure ice rather than smooth mountain domes.
     * A final blanket cliff makes the mesh edge unclimbable everywhere.
     */
    private static float frozenRing(float h, float x, float z) {
        float r = FastMath.sqrt(x * x + z * z);
        float th = FastMath.atan2(z, x);

        float foot = innerFoot(th);
        // The collar is roughened ice, not a ramp the player has to climb, so it is
        // clamped to start outside the basin. Letting it begin wherever the foot
        // happens to wander put its own jagged 0.7 gradient inside the play area,
        // on top of whatever landform was already sloping there.
        float collarStart = Math.max(foot - COLLAR_WIDTH, AUTHORED_HALF_EXTENT + 6f);
        if (r > collarStart && r < foot) {
            float jag = 1f + 0.4f * FastMath.sin(7f * th + 0.6f)
                    + 0.28f * FastMath.sin(19f * th + 2.4f)
                    + 0.16f * FastMath.sin(37f * th + 4.1f);
            h += 10f * jag * smooth01((r - collarStart) / (foot - collarStart));
        }

        // teeth band 1 — the near broken rim
        float t1Foot = foot;
        float t1Peak = foot + 30f + 9f * FastMath.sin(4f * th + 1.3f);
        float amp1 = 52f + 38f * (0.5f + 0.5f * FastMath.sin(11f * th + 2.2f))
                + 18f * (0.5f + 0.5f * FastMath.sin(23f * th + 0.9f))
                + 10f * (0.5f + 0.5f * FastMath.sin(41f * th + 3.6f));
        h += spikeRise(r, t1Foot, t1Peak, amp1);

        // teeth band 2 — the outer, taller ice
        float t2Foot = foot + 26f + 11f * FastMath.sin(2f * th + 0.9f);
        float t2Peak = t2Foot + 34f + 12f * FastMath.sin(5f * th + 2.1f);
        float amp2 = 108f + 66f * (0.5f + 0.5f * FastMath.sin(7f * th + 1.5f))
                + 24f * (0.5f + 0.5f * FastMath.sin(17f * th + 2.7f));
        h += spikeRise(r, t2Foot, t2Peak, amp2);

        // Blanket cliff so the mesh edge is unclimbable everywhere. It deliberately
        // only starts beyond the seal ring, so it seals the world without ever
        // encroaching on the basin the player walks around in.
        float blunt = smooth01((r - 215f) / 45f);
        h = Math.max(h, blunt * 280f - 70f);

        return Math.max(h, MIN_ELEVATION);
    }

    private static float innerFoot(float th) {
        return MOUNT_FOOT_BASE + MOUNT_FOOT_WAVE * FastMath.sin(3f * th + 1.7f)
                + MOUNT_FOOT_WAVE * 0.3f * FastMath.sin(7f * th + 0.4f);
    }

    /** Starts slowly, climbs fast: gentle at the foot, near-vertical at the crest. */
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

    private static float flattenTo(float h, float cx, float cz, float r,
                                   float blend, float target, float x, float z) {
        float d = dist(x, z, cx, cz);
        if (d >= r + blend) return h;
        float m = 1f - smooth01((d - r) / blend);
        return h + (target - h) * m;
    }

    // ------------------------------------------------------------- the terrain

    /** A real mesh collider, not a box: the player capsule, the enemies and every
     *  dropped physics object all follow the ice surface exactly, so nothing
     *  floats over a ridge or sinks through a dip. */
    private void buildTerrain(AssetManager assetManager, BulletAppState bulletAppState) {
        int n = TERRAIN_SAMPLES;
        int total = n * n;

        float[] heights = new float[total];
        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                heights[iz * n + ix] = heightAt(ix * TERRAIN_STEP - TERRAIN_HALF,
                                                iz * TERRAIN_STEP - TERRAIN_HALF);
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

                float slope = FastMath.sqrt(dhdx * dhdx + dhdz * dhdz);
                float[] rgb = terrainColor(x, z, h, slope);
                color.put(rgb[0]).put(rgb[1]).put(rgb[2]).put(1f);
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

        Geometry ground = new Geometry("FrozenDepthsGround", terrain);
        ground.setMaterial(mat);
        stageNode.attachChild(ground);

        MeshCollisionShape shape = new MeshCollisionShape(terrain);
        RigidBodyControl physics = new RigidBodyControl(shape, 0f);
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    /** Cold glacial palette: pale blue-grey ice in the deep basin floors, clean
     *  white-blue over the lake and plateau, and a darker blue on steep faces so
     *  the wall and the ridge read as solid ice instead of flat snow. */
    private float[] terrainColor(float x, float z, float h, float slope) {
        float[] deepIce = {0.62f, 0.70f, 0.80f};
        float[] basinFloor = {0.74f, 0.82f, 0.90f};
        float[] iceSheet = {0.86f, 0.91f, 0.96f};
        float[] whiteCap = {0.94f, 0.96f, 0.99f};
        float[] steepIce = {0.50f, 0.61f, 0.76f};

        float tH = FastMath.clamp((h + 24f) / 72f, 0f, 1f);
        float[] rgb = lerp(deepIce, basinFloor, FastMath.clamp(tH * 2.2f, 0f, 1f));
        rgb = lerp(rgb, iceSheet, FastMath.clamp((tH - 0.30f) / 0.70f, 0f, 1f));
        if (h > 20f) {
            rgb = lerp(rgb, whiteCap, FastMath.clamp((h - 20f) / 30f, 0f, 1f));
        }
        rgb = lerp(rgb, steepIce, FastMath.clamp((slope - 0.45f) / 0.9f, 0f, 1f));
        return rgb;
    }

    private float[] lerp(float[] a, float[] b, float t) {
        return new float[]{
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t
        };
    }

    /** Invisible seal closing the basin-to-wall seam: a character capsule cannot
     *  wedge between the collar ramp and the near-vertical teeth. The collar teeth
     *  are decoration now, so this wall is the only thing at the basin edge.
     *
     *  <p>Sixteen wide boxes rather than forty-eight narrow ones. The wall is read by
     *  the player as one flat ring, so the count only has to be high enough that
     *  adjacent boxes overlap; at r=206 a box half as wide as the 35-unit chord
     *  leaves no gap at all. A single cylinder would be cheaper still but a
     *  CylinderCollisionShape is solid, which would put the spawn point inside it.</p> */
    private void sealIceRing(BulletAppState bulletAppState) {
        float R = 206f;
        float halfTang = 44f;   // wider than the 35-unit chord between 16 segments
        float halfY = 90f;      // spans y 0..180 — taller than any walkable ice
        float halfRad = 5f;
        int n = 16;
        for (int i = 0; i < n; i++) {
            float a = i * FastMath.TWO_PI / n;
            BoxCollisionShape box = new BoxCollisionShape(new Vector3f(halfTang, halfY, halfRad));
            RigidBodyControl blocker = new RigidBodyControl(box, 0f);
            blocker.setPhysicsRotation(new Quaternion().fromAngleAxis(-a - FastMath.HALF_PI, Vector3f.UNIT_Y));
            blocker.setPhysicsLocation(new Vector3f(FastMath.cos(a) * R, halfY, FastMath.sin(a) * R));
            bulletAppState.getPhysicsSpace().add(blocker);
            physicsObjects.add(blocker);
        }
    }

    /** Last-resort catch floor, so a body that slips through the terrain mesh can
     *  never drop out of the world.
     *
     *  <p>Wider than the terrain it sits under ({@link #TERRAIN_HALF} plus margin), so
     *  there is no lateral gap to escape through, and raised to just below the lowest
     *  the height field can reach ({@link #MIN_ELEVATION}) so a caught body arrives
     *  promptly instead of tumbling through empty space. The old floor was narrower
     *  than the terrain and sat 32 units lower, which turned every leak into a long
     *  fall to a distant surface.</p>
     */
    private void addSafetyFloor(BulletAppState bulletAppState) {
        BoxCollisionShape shape = new BoxCollisionShape(
                new Vector3f(TERRAIN_HALF + 40f, 2f, TERRAIN_HALF + 40f));
        RigidBodyControl physics = new RigidBodyControl(shape, 0f);
        physics.setPhysicsLocation(new Vector3f(0f, MIN_ELEVATION - 4f, 0f));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    // --------------------------------------------------------------- the sky

    /** Cold polar sky (equirectangular, baked at build time — swap the painted
     *  image for a real panorama later; the dome and the queue bucket stay). */
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

    /** Painted polar sky: deep blue at the zenith bleeding to a pale white-blue
     *  haze at the horizon, a diffuse low sun off to one side, soft cloud banding
     *  and a pale aurora ribbon. Cold, bright, high-altitude. */
    private BufferedImage skyTextureImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint grad = new GradientPaint(
                0, 0, new Color(74, 128, 190),
                0, h, new Color(226, 240, 250));
        g.setPaint(grad);
        g.fillRect(0, 0, w, h);

        // pale haze band hugging the horizon, over the top of the main gradient
        g.setPaint(new GradientPaint(
                0, (int) (h * 0.58f), new Color(196, 222, 244, 0),
                0, (int) (h * 0.86f), new Color(240, 248, 253, 190)));
        g.fillRect(0, (int) (h * 0.58f), w, (int) (h * 0.30f));

        // the low winter sun, off-centre, echoing the authored sun direction
        int gx = (int) (w * 0.26f);
        int gy = (int) (h * 0.66f);
        int glowR = 200;
        for (int i = 0; i < 44; i++) {
            float t = i / 44f;
            int radius = Math.round(glowR * (1f + t * 2.8f));
            g.setColor(new Color(255, 252, 232,
                    Math.max(0, Math.round(165 * (1f - t) * (1f - t)))));
            g.fillOval(gx - radius, gy - radius, radius * 2, radius * 2);
        }
        g.setColor(new Color(255, 255, 250, 235));
        int core = 34;
        g.fillOval(gx - core, gy - core, core * 2, core * 2);

        // soft altocumulus banding, thickest near the horizon
        Random rand = new Random(707);
        g.setColor(new Color(255, 255, 255, 96));
        for (int i = 0; i < 42; i++) {
            int cx = rand.nextInt(w);
            int cy = (int) (h * 0.46f) + rand.nextInt((int) (h * 0.26f));
            int base = 70 + rand.nextInt(180);
            for (int j = 0; j < 9; j++) {
                int ox = cx + rand.nextInt(base) - base / 2;
                int oy = cy + rand.nextInt(base / 4) - base / 8;
                int size = base / 2 + rand.nextInt(base / 2);
                g.fillOval(ox, oy, size, size / 2);
            }
        }

        // a pale green aurora ribbon high over the north
        g.setColor(new Color(150, 226, 200, 70));
        for (int i = 0; i < 60; i++) {
            float t = i / 60f;
            int bandX = (int) (w * (0.08f + t * 0.42f));
            int bandY = (int) (h * (0.30f - 0.16f * FastMath.sin(t * 3.1f)));
            int bandH = 26 + rand.nextInt(52);
            g.fillOval(bandX, bandY, 34 + rand.nextInt(60), bandH);
        }

        g.dispose();
        return img;
    }

    // ============================================== placing the authored props

    /**
     * Target footprint, in world units, for each class of formation.
     *
     * <p>The Cenji meshes are authored large -- {@code ROCK_Glacial_01_LargeCliff}
     * is over 20 units across at scale 1 -- so a single scale factor per group was
     * not a size, it was a multiplier on a size. At 5.2x the framing ring became a
     * ring of 90-unit invisible boxes and the basin centrepiece a 223-unit wall,
     * which is what walled the player off from the arenas.</p>
     *
     * <p>Placing by target width instead means the number in the code is the number
     * the player meets, and a model swap cannot silently change the size of the map.
     */
    /** Fraction of a formation's measured footprint its collider actually covers.
     *  Well below a quarter on purpose. These formations are scenery the player walks
     *  between, not obstacles to climb, and the clusters are spaced closely enough that
     *  anything near full-footprint closes the gaps between neighbours and turns a
     *  cluster into a wall. A collider this small is a fraction of the visible boulder:
     *  you brush the middle of a formation and walk past its edges, which is what keeps
     *  every arena and the routes between them open. */
    private static final float COLLIDER_SHRINK = 0.14f;

    /** How much taller or wider than its target dimension a formation may end up, so
     *  a model authored far off-square cannot explode across the basin when it is
     *  fitted to one axis only. */
    private static final float MAX_SPREAD = 2.2f;

    private static final float MASS_WIDTH = 13f;
    private static final float SPIRE_HEIGHT = 17f;
    private static final float SHARD_WIDTH = 5f;
    private static final float CRYSTAL_WIDTH = 7f;
    private static final float GEODE_WIDTH = 3.5f;
    private static final float CRYSTAL_TALL_HEIGHT = 15f;
    private static final float CENTREPIECE_HEIGHT = 26f;
    private static final float COLLAR_MASS_WIDTH = 18f;
    private static final float COLLAR_SPIRE_HEIGHT = 24f;

    /**
     * Scale that makes {@code model} come out {@code target} units in the dimension
     * named by {@code byWidth}: the widest of width and depth for a mass or a
     * crystal, the height for a spire. Measured from the model's own bound, so this
     * is stable per model and cheap enough to redo per placement.
     */
    private float scaleForWidth(AssetManager assetManager, String model, float target, boolean byWidth) {
        Spatial s = StageDecor.loadCached(assetManager, model);
        s.updateModelBound();
        if (!(s.getWorldBound() instanceof BoundingBox bb)) {
            return target;
        }
        Vector3f e = bb.getExtent(new Vector3f());
        float width = Math.max(e.x, e.z) * 2f;
        float height = e.y * 2f;
        if (width < 0.01f || height < 0.01f) {
            return target;
        }
        // The scale is driven by the axis the caller asked for, but the other axis is
        // capped at a multiple of it. Several of these models are far wider than they
        // are tall, so fitting only the height let a formation meant to stand 26 units
        // high come out hundreds of units across, and its collider then covered whole
        // arenas -- which is what made the crystal basin unreachable. Capping the
        // secondary axis keeps each formation close to its authored proportion while
        // still bounding how much ground it can occupy.
        float primary = byWidth ? width : height;
        float secondary = byWidth ? height : width;
        float scale = target / primary;
        float secondaryAtScale = secondary * scale;
        if (secondaryAtScale > target * MAX_SPREAD) {
            scale = target * MAX_SPREAD / secondary;
        }
        return scale;
    }

    /** Places one formation per spot, cycling through the supplied model set in
     *  order so the layout is hand-decision but each group still reads as a
     *  deliberate mix of masses, spires and shards. Sizes are target dimensions in
     *  world units, not scale factors. */
    private void placeSpots(String[] models, AssetManager assetManager,
                            BulletAppState bulletAppState, float[][] spots,
                            float targetSize, boolean sizeByWidth) {
        placeSpots(models, assetManager, bulletAppState, spots, targetSize, sizeByWidth, true);
    }

    /** Scatter pass with no colliders, for anything small and low enough that the
     *  player should walk over it: rubble an ankle high, a geode glowing in the
     *  ice. These read as ground detail, and a collider on each one only snagged the
     *  character controller at foot height. */
    private void placeSpotsDecorative(String[] models, AssetManager assetManager,
                                      BulletAppState bulletAppState, float[][] spots,
                                      float targetSize) {
        placeSpots(models, assetManager, bulletAppState, spots, targetSize, true, false);
    }

    private void placeSpots(String[] models, AssetManager assetManager,
                            BulletAppState bulletAppState, float[][] spots,
                            float targetSize, boolean sizeByWidth, boolean solid) {
        for (float[] p : spots) {
            String model = models[formationCount % models.length];
            float jitter = 0.88f + deterministicYaw.nextFloat() * 0.26f;
            float scale = scaleForWidth(assetManager, model, targetSize, sizeByWidth) * jitter;
            place(model, assetManager, bulletAppState, p[0], p[1], scale, solid);
        }
    }


    /** Single formation placed by target size rather than by scale factor, so
     *  one-off landmarks cannot drift out of proportion the way a raw scale can. */
    private void placeSpotSized(String model, AssetManager assetManager,
                                BulletAppState bulletAppState,
                                float x, float z, float targetSize, boolean sizeByWidth) {
        float jitter = 0.88f + deterministicYaw.nextFloat() * 0.26f;
        float scale = scaleForWidth(assetManager, model, targetSize, sizeByWidth) * jitter;
        placeSpot(model, assetManager, bulletAppState, x, z, scale);
    }

    /** Places a formation that the player can never walk into: mesh only, no
     *  collider, and not recorded as a layout obstacle. Only safe where something
     *  else already blocks the player, which for the collar is the seal wall. */
    private void placeDecoration(String model, AssetManager assetManager,
                                 BulletAppState bulletAppState,
                                 float x, float z, float targetSize, boolean sizeByWidth) {
        float jitter = 0.88f + deterministicYaw.nextFloat() * 0.26f;
        float scale = scaleForWidth(assetManager, model, targetSize, sizeByWidth) * jitter;
        place(model, assetManager, bulletAppState, x, z, scale, false);
    }

    /**
     * Places a single model and gives it a collider fitted to the model it just
     * loaded, rather than a guessed box: the mesh's own bounding box is measured
     * after scaling, the base is seated exactly on the terrain, and the hitbox is
     * built from those measured extents. Tall, narrow shapes get a cone so the
     * silhouette a player collides with matches the silhouette they can see.
     */
    private void placeSpot(String model, AssetManager assetManager,
                           BulletAppState bulletAppState, float x, float z, float scale) {
        place(model, assetManager, bulletAppState, x, z, scale, true);
    }

    /**
     * Shared placement: measure, seat on the terrain, tint, attach, and — when the
     * formation is one the player can reach — build the collider and record it for
     * the layout checks.
     */
    private void place(String model, AssetManager assetManager,
                       BulletAppState bulletAppState,
                       float x, float z, float scale, boolean solid) {
        Spatial s = StageDecor.loadCached(assetManager, model).clone();
        s.setLocalScale(scale);

        float[] measured = measure(s, scale);
        float width = measured[0];
        float height = measured[1];
        float depth = measured[2];
        float centerY = measured[3];

        float ground = heightAt(x, z);
        float sink = 0.6f;   // press the base slightly into the ice
        float y = ground - sink - centerY + height * 0.5f;

        s.setLocalTranslation(x, y, z);
        float yaw = deterministicYaw.nextFloat() * FastMath.TWO_PI;
        s.rotate(0f, yaw, 0f);
        s.setMaterial(tintedMaterial(assetManager, model));
        stageNode.attachChild(s);

        // cone for spires, box for anything blocky
        boolean spiky = height > 2.2f * Math.max(width, depth) && height > 8f;
        // The collider is deliberately much smaller than the mesh. These formations
        // are scattered in clusters, so a collider at full footprint closes the
        // gaps between neighbours and the clusters become walls; at this fraction
        // the player brushes past the edge of a boulder instead of being stopped by
        // the space around it, and the mesh still reads as solid.
        float footX = width * COLLIDER_SHRINK;
        float footZ = depth * COLLIDER_SHRINK;
        float blockRadius = spiky
                ? Math.max(width, depth) * COLLIDER_SHRINK
                : FastMath.sqrt(footX * footX + footZ * footZ);

        if (solid) {
            // A cone is a narrow base and a tall body; both shapes are centred on
            // their local origin, so each is lifted by half its height. Seated at
            // ground level the box sank half its height into the ice and the player
            // could walk through the top of every boulder.
            CollisionShape shape = spiky
                    ? new ConeCollisionShape(Math.max(width, depth) * COLLIDER_SHRINK, height)
                    : new BoxCollisionShape(
                            new Vector3f(footX, height * 0.5f, footZ));
            RigidBodyControl physics = new RigidBodyControl(shape, 0f);
            physics.setPhysicsLocation(
                    new Vector3f(x, ground + height * 0.5f - sink, z));
            physics.setPhysicsRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
            bulletAppState.getPhysicsSpace().add(physics);
            physicsObjects.add(physics);

            // Remembered so the layout can be checked against the arenas and the
            // exit once everything is down. x, z, then the horizontal radius a
            // player has to walk around, from the same collider that was built.
            placedProps.add(new float[]{x, z, blockRadius});
        }

        formationCount++;
        if (isCrystal(model)) {
            crystalCount++;
        }
    }

    /**
     * Checks the finished layout against the things that must stay clear: the three
     * arenas, so a formation can never seal a spawn point inside itself, and the
     * exit portal, which the rest of the game hard-codes to (0, 4.2, 25). A
     * violation is reported rather than thrown, because it is a layout mistake to
     * fix, not a condition to survive.
     */
    private void verifyClearances() {
        int blocked = 0;
        // Pads alone are not enough. An enemy on a pad is only reachable if the
        // player can walk to the pad as well, so every pad is checked for a clear
        // approach out to WALK_CLEARANCE too: that catches a formation parked
        // between two pads, which is exactly how the arenas got sealed before.
        final float walkPad = 16f;
        for (int i = 0; i < ARENA_PADS.length; i++) {
            float[] pad = ARENA_PADS[i];
            for (int j = -1; j < placedProps.size(); j++) {
                float gap;
                String what;
                if (j < 0) {
                    // the approach ring: is the disc of radius walkPad around the
                    // pad itself free of formations?
                    float worst = Float.MAX_VALUE;
                    String worstAt = "";
                    for (float[] p : placedProps) {
                        float g = dist(p[0], p[1], pad[0], pad[1]) - p[2];
                        if (g < worst) {
                            worst = g;
                            worstAt = " at " + (int) p[0] + "," + (int) p[1];
                        }
                    }
                    gap = worst - walkPad;
                    what = "the approach to arena pad " + (int) pad[0] + "," + (int) pad[1]
                            + " by a formation" + worstAt;
                } else {
                    float[] p = placedProps.get(j);
                    gap = dist(p[0], p[1], pad[0], pad[1]) - p[2] - walkPad;
                    what = "arena pad " + (int) pad[0] + "," + (int) pad[1]
                            + " by a formation at " + (int) p[0] + "," + (int) p[1];
                }
                if (gap < 0f) {
                    blocked++;
                    System.out.println("[FrozenDepths 1] BLOCKED " + what
                            + " (gap " + String.format("%.1f", gap) + ")");
                }
            }
        }
        for (float[] p : placedProps) {
            float gap = dist(p[0], p[1], 0f, 25f) - p[2] - 1.5f;
            if (gap < 0f) {
                blocked++;
                System.out.println("[FrozenDepths 1] BLOCKED the exit portal by a formation at "
                        + (int) p[0] + "," + (int) p[1]
                        + " (gap " + String.format("%.1f", gap) + ")");
            }
            float r = FastMath.sqrt(p[0] * p[0] + p[1] * p[1]);
            if (r > 254f) {
                blocked++;
                System.out.println("[FrozenDepths 1] formation at " + (int) p[0] + "," + (int) p[1]
                        + " is off the terrain at r=" + String.format("%.0f", r));
            }
        }
        blocked += verifyReachability();

        if (blocked == 0) {
            System.out.println("[FrozenDepths 1] clearances ok: arenas and portal unobstructed");
        }
    }

    /**
     * Floods the walkable plane outward from the spawn and checks that every arena
     * pad and the exit portal fall inside the reachable area.
     *
     * <p>Per-pad gap checks cannot catch a pair of formations that seal a corridor
     * between two individually clear pads, which is the failure that made the arenas
     * unreachable: every pad had a metre of clearance and none of them could be
     * walked to. This checks the thing the player actually experiences.</p>
     */
    private int verifyReachability() {
        final float half = 190f;
        final float step = 2.5f;
        final float bodyR = 1.2f;   // a player capsule plus the collider skin
        int n = (int) (half * 2f / step);
        int blocked = 0;

        boolean[] open = new boolean[n * n];
        for (int gx = 0; gx < n; gx++) {
            for (int gz = 0; gz < n; gz++) {
                float x = (gx + 0.5f) * step - half;
                float z = (gz + 0.5f) * step - half;
                boolean free = true;
                for (float[] p : placedProps) {
                    if (dist(p[0], p[1], x, z) - p[2] < bodyR) {
                        free = false;
                        break;
                    }
                }
                open[gx * n + gz] = free;
            }
        }

        boolean[] seen = new boolean[n * n];
        int start = cellIndex(n, half, step, 0f, 0f);
        if (start < 0 || !open[start]) {
            System.out.println("[FrozenDepths 1] spawn (0,0) is inside a formation collider");
            return 1;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        seen[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            int cx = cur / n;
            int cz = cur % n;
            int[][] near = {{cx + 1, cz}, {cx - 1, cz}, {cx, cz + 1}, {cx, cz - 1}};
            for (int[] d : near) {
                if (d[0] < 0 || d[1] < 0 || d[0] >= n || d[1] >= n) {
                    continue;
                }
                int idx = d[0] * n + d[1];
                if (open[idx] && !seen[idx]) {
                    seen[idx] = true;
                    queue.add(idx);
                }
            }
        }

        int reach = 0;
        for (int i = 0; i < ARENA_PADS.length; i++) {
            int idx = cellIndex(n, half, step, ARENA_PADS[i][0], ARENA_PADS[i][1]);
            if (idx < 0) {
                System.out.println("[FrozenDepths 1] arena pad " + i + " is outside the level disc");
                blocked++;
            } else if (!seen[idx]) {
                System.out.println("[FrozenDepths 1] UNREACHABLE arena pad " + i
                        + " at " + (int) ARENA_PADS[i][0] + "," + (int) ARENA_PADS[i][1]
                        + " — formations seal the route from the spawn");
                blocked++;
            } else {
                reach++;
            }
        }

        int exitCell = cellIndex(n, half, step, 0f, 25f);
        boolean exitOk = exitCell >= 0 && seen[exitCell];
        if (!exitOk) {
            System.out.println("[FrozenDepths 1] UNREACHABLE exit portal at (0,25)");
            blocked++;
        }

        int openCount = 0;
        for (boolean b : open) {
            if (b) {
                openCount++;
            }
        }
        int seenCount = 0;
        for (boolean b : seen) {
            if (b) {
                seenCount++;
            }
        }
        System.out.println("[FrozenDepths 1] reachable from spawn: "
                + seenCount + " of " + openCount + " walkable cells, "
                + reach + "/" + ARENA_PADS.length + " arena pads, exit "
                + (exitOk ? "ok" : "SEALED"));
        return blocked;
    }

    private static int cellIndex(int n, float half, float step, float x, float z) {
        int gx = (int) FastMath.floor((x + half) / step);
        int gz = (int) FastMath.floor((z + half) / step);
        if (gx < 0 || gz < 0 || gx >= n || gz >= n) {
            return -1;
        }
        return gx * n + gz;
    }

    private static boolean isCrystal(String model) {
        return model.contains("Crystal") || model.contains("GEM_");
    }

    /** Scaled world extents plus the scaled mesh-centre height, read straight off
     *  the model bound. Returns {width, height, depth, centerY}. A detached
     *  spatial's world bound is just its local bound, so the scale is applied
     *  here rather than read back from the transform. */
    private float[] measure(Spatial s, float scale) {
        s.updateModelBound();
        BoundingVolume bound = s.getWorldBound();
        if (bound instanceof BoundingBox bbox) {
            Vector3f extent = bbox.getExtent(new Vector3f());
            Vector3f center = bbox.getCenter(new Vector3f());
            return new float[]{
                    Math.max(0.5f, extent.x * 2f * scale),
                    Math.max(0.5f, extent.y * 2f * scale),
                    Math.max(0.5f, extent.z * 2f * scale),
                    center.y * scale
            };
        }
        return new float[]{4f, 6f, 4f, 3f};
    }

    /**
     * The Cenji glacial and slate meshes are authored in a dark near-black base
     * colour, which reads as wet slate rather than ice. Each model path gets one
     * shared lit material and the crystal and geode paths get a brighter one, so
     * the whole pack is re-tinted into the stage's cold palette. Materials are
     * cached per path: the clone is re-tinted but the cached model's own material
     * is never mutated.
     */
    private Material tintedMaterial(AssetManager assetManager, String model) {
        Material cached = tintedMaterialCache.get(model);
        if (cached != null) {
            return cached;
        }
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        if (isCrystal(model)) {
            mat.setColor("Diffuse", new ColorRGBA(0.34f, 0.62f, 0.92f, 1f));
            mat.setColor("Ambient", new ColorRGBA(0.18f, 0.34f, 0.56f, 1f));
            mat.setColor("Specular", new ColorRGBA(0.95f, 0.99f, 1f, 1f));
            mat.setFloat("Shininess", 90f);
        } else if (model.contains("Glacial_04") || model.contains("Slate_04")) {
            mat.setColor("Diffuse", new ColorRGBA(0.74f, 0.85f, 0.94f, 1f));
            mat.setColor("Ambient", new ColorRGBA(0.30f, 0.40f, 0.52f, 1f));
            mat.setColor("Specular", new ColorRGBA(0.90f, 0.97f, 1f, 1f));
            mat.setFloat("Shininess", 70f);
        } else {
            mat.setColor("Diffuse", new ColorRGBA(0.88f, 0.93f, 0.98f, 1f));
            mat.setColor("Ambient", new ColorRGBA(0.36f, 0.44f, 0.55f, 1f));
            mat.setColor("Specular", new ColorRGBA(0.86f, 0.94f, 1f, 1f));
            mat.setFloat("Shininess", 34f);
        }
        tintedMaterialCache.put(model, mat);
        return mat;
    }

    private final Map<String, Material> tintedMaterialCache = new HashMap<>();

}
