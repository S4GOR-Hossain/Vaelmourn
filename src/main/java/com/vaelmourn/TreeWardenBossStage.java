package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

import java.util.Random;

/** Tree Warden Boss Arena — a clearing ringed by great trees, open disc in the middle. */
public class TreeWardenBossStage extends BossStage {

    private static final String KAYKIT =
            "Models/Environment/KayKit_Forest_Nature_Pack_1.0_FREE/"
                    + "KayKit_Forest_Nature_Pack_1.0_FREE/Assets/gltf/";
    private static final String[] TREE_MODELS = {
            "Models/Environment/Forest/tree_pack_02.glb",
            "Models/Environment/Forest/tree_pack_07.glb",
            "Models/Environment/Forest/tree_pack_13.glb",
            "Models/Environment/Forest/tree_pack_18.glb"
    };

    /** Tree scale range; kept in one place since the arena-overhang test depends on it. */
    private static final float TREE_SCALE_MIN = 4.2f;
    private static final float TREE_SCALE_MAX = 5.3f;
    /** How far outside the combat disc a tree's foliage must stay, in world units. */
    private static final float ARENA_CLEARANCE = 0f;

    @Override
    protected void buildArenaDecor(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(771);
        int kept = 0;
        int culled = 0;
        for (int i = 0; i < 26; i++) {
            float angle = (i / 26f) * FastMath.TWO_PI + rand.nextFloat() * 0.12f;
            float radius = getRingStart() + rand.nextFloat() * (getRingEnd() - getRingStart());
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            Spatial tree = StageDecor.placeFlat(stageNode, assetManager,
                    TREE_MODELS[rand.nextInt(TREE_MODELS.length)], x, z,
                    TREE_SCALE_MIN + rand.nextFloat() * (TREE_SCALE_MAX - TREE_SCALE_MIN), rand);
            // These are full canopy trees at scale ~4-5, so a trunk legally placed in
            // the decor ring still throws foliage inside the combat disc and buries the
            // arena the player is supposed to fight in. Cull only the trees whose canopy
            // actually reaches the disc and leave every other one in place.
            if (overhangsArena(tree)) {
                tree.removeFromParent();
                culled++;
            } else {
                // Trunk-only, not addBlockerFor: that matches the whole silhouette,
                // which boxed these trees in at canopy width and buried the arena in
                // invisible walls the player could see straight through.
                StageDecor.addTrunkBlocker(bulletAppState, physicsObjects, tree, x, 0f, z,
                        0.6f, 1.25f, 2.5f, 4f);
                kept++;
            }
        }
        System.out.println("[TreeWardenArena] trees kept=" + kept + " culled=" + culled
                + " arenaRadius=" + getArenaRadius());

        String[] bushes = {
                KAYKIT + "Bush_1_C_Color1.gltf",
                KAYKIT + "Bush_2_D_Color1.gltf",
                KAYKIT + "Bush_3_A_Color1.gltf"
        };
        String[] rocks = {
                "Models/Environment/Forest/stone_largeA.glb",
                "Models/Environment/Forest/stone_largeB.glb",
                "Models/Environment/Forest/stone_tallA.glb"
        };

        for (int i = 0; i < 16; i++) {
            float angle = (i / 16f) * FastMath.TWO_PI + rand.nextFloat() * 0.15f;
            float radius = getRingStart() + 1f + rand.nextFloat() * (getRingEnd() - getRingStart() - 1f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            // bushes are ankle-high ground cover, walked through by design
            StageDecor.placeFlat(stageNode, assetManager,
                    bushes[rand.nextInt(bushes.length)], x, z,
                    1.8f + rand.nextFloat() * 1.6f, rand);
        }
        for (int i = 0; i < 6; i++) {
            float angle = (i / 6f) * FastMath.TWO_PI + rand.nextFloat() * 0.2f;
            float radius = getRingStart() + 1f + rand.nextFloat() * (getRingEnd() - getRingStart() - 1f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            // solid: these boulders used to be walk-through
            StageDecor.placeSolid(stageNode, assetManager, bulletAppState, physicsObjects,
                    rocks[rand.nextInt(rocks.length)], x, z,
                    3f + rand.nextFloat() * 2.5f, rand);
        }

        // Same forest, continued past the walls: the arena now sits in a clearing
        // rather than at the edge of a 550-unit floor, and none of this is reachable.
        scatterOuterScenery(assetManager, TREE_MODELS, 90, 4101L, 4.5f, 8f);
        scatterOuterScenery(assetManager, rocks, 30, 4102L, 3f, 5.5f);
        scatterOuterScenery(assetManager, bushes, 55, 4103L, 2f, 3.5f);
    }

    /**
     * True when any part of the tree's canopy reaches inside the open combat disc.
     *
     * <p>{@code ARENA_CLEARANCE} is deliberately 0. The decor ring already starts outside
     * the arena radius, so adding a buffer here widened the cull well past real
     * obstruction and started deleting the outer treeline for no gameplay reason. A tree
     * whose bounds genuinely intersect the disc is obstructed; one that merely leans over
     * from beyond it stays.</p>
     */
    private boolean overhangsArena(Spatial tree) {
        tree.updateModelBound();
        if (!(tree.getWorldBound() instanceof BoundingBox bbox)) return false;

        // closest point of the tree's footprint to the arena centre
        Vector3f min = bbox.getMin(new Vector3f());
        Vector3f max = bbox.getMax(new Vector3f());
        float nearestX = FastMath.clamp(0f, min.x, max.x);
        float nearestZ = FastMath.clamp(0f, min.z, max.z);
        float closest = FastMath.sqrt(nearestX * nearestX + nearestZ * nearestZ);
        return closest < getArenaRadius() + ARENA_CLEARANCE;
    }

    @Override
    protected float getBossScale() {
        return 1.05f;
    }

    @Override
    protected String getBossModelPath() {
        return "Models/Characters/boss/tree_warden.gltf";
    }

    @Override
    protected BossSpec getBossSpec() {
        BossSpec s = new BossSpec();
        s.moveSpeed = 2.4f;
        s.attackCooldown = 2.8f;
        s.meleeRange = 4.2f;
        s.recovery = 0.6f;
        s.smash = true;
        s.smashWindup = 1.0f;
        s.smashCooldown = 7f;
        s.smashRadius = 9f;
        s.smashDamage = 18f;
        s.smashRecovery = 0.9f;
        s.aoe = true;
        s.aoeWindup = 1.2f;
        s.aoeCooldown = 13f;
        s.aoeRadius = 11f;
        s.aoeDamage = 14f;
        s.summon = true;
        s.summonInterval = 26f;
        s.summonCount = 2;
        s.summonCap = 3;
        s.summonTier = 2;
        s.summonModels = new String[]{
                "Models/Characters/enemy/darkwood_enemy2.glb",
                "Models/Characters/enemy/darkwood_enemy3.gltf"};
        return s;
    }

    @Override
    protected ColorRGBA getGroundColor() {
        return new ColorRGBA(0.2f, 0.3f, 0.15f, 1f);
    }

    @Override
    protected ColorRGBA getArenaColor() {
        return new ColorRGBA(0.18f, 0.4f, 0.2f, 1f);
    }

    @Override
    public ColorRGBA getSkyColor() {
        return new ColorRGBA(0.15f, 0.18f, 0.25f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.3f, 0.35f, 0.4f, 1f).mult(0.55f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.3f, -0.9f, -0.4f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return 55f;
    }

    @Override
    public String getName() {
        return "Tree Warden";
    }

    @Override
    public int getStageIndex() {
        return 5;
    }
}