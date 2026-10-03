package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

import java.util.Random;

/** Beekeeper Boss Arena â€” a jungle clearing walled in by trees around a big open centre. */
public class BeekeeperBossStage extends BossStage {

    private static final String KAYKIT =
            "Models/Environment/KayKit_Forest_Nature_Pack_1.0_FREE/"
                    + "KayKit_Forest_Nature_Pack_1.0_FREE/Assets/gltf/";
    private static final String[] TREE_MODELS = {
            "Models/Environment/Forest/tree_palm.glb",
            "Models/Environment/Forest/tree_palmDetailedTall.glb",
            "Models/Environment/Forest/tree_detailed.glb",
            "Models/Environment/Forest/tree_fat.glb",
            "Models/Environment/Forest/tree_oak.glb"
    };

    @Override
    protected void buildArenaDecor(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(774);
        for (int i = 0; i < 28; i++) {
            float angle = (i / 28f) * FastMath.TWO_PI + rand.nextFloat() * 0.12f;
            float radius = getRingStart() + rand.nextFloat() * (getRingEnd() - getRingStart());
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            StageDecor.addTreeWithHitbox(assetManager, stageNode, bulletAppState,
                    physicsObjects, TREE_MODELS, rand, x, z);
        }

        String[] bushes = {
                KAYKIT + "Bush_1_A_Color1.gltf",
                KAYKIT + "Bush_2_C_Color1.gltf",
                KAYKIT + "Bush_3_B_Color1.gltf",
                KAYKIT + "Bush_4_D_Color1.gltf"
        };
        for (int i = 0; i < 18; i++) {
            float angle = (i / 18f) * FastMath.TWO_PI + rand.nextFloat() * 0.15f;
            float radius = getRingStart() + 1f + rand.nextFloat() * (getRingEnd() - getRingStart() - 1f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            StageDecor.placeFlat(stageNode, assetManager,
                    bushes[rand.nextInt(bushes.length)], x, z,
                    2f + rand.nextFloat() * 1.8f, rand);
        }

        String[] rocks = {
                "Models/Environment/Forest/stone_largeB.glb",
                "Models/Environment/Forest/stone_largeC.glb",
                "Models/Environment/Forest/stump_old.glb"
        };
        for (int i = 0; i < 6; i++) {
            float angle = (i / 6f) * FastMath.TWO_PI + rand.nextFloat() * 0.2f;
            float radius = getRingStart() + 1f + rand.nextFloat() * (getRingEnd() - getRingStart() - 1f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            // bushes above stay walk-through groundcover; the rocks do not
            StageDecor.placeSolid(stageNode, assetManager, bulletAppState, physicsObjects,
                    rocks[rand.nextInt(rocks.length)], x, z,
                    3f + rand.nextFloat() * 2.5f, rand);
        }

        // Grove carried on past the walls, unreachable and uncollided.
        scatterOuterScenery(assetManager, TREE_MODELS, 90, 3301L, 4.5f, 8f);
        scatterOuterScenery(assetManager, rocks, 30, 3302L, 3f, 5.5f);
        scatterOuterScenery(assetManager, bushes, 55, 3303L, 2f, 3.5f);
    }

    @Override
    protected float getBossScale() {
        return 1.15f;
    }

    @Override
    protected String getBossModelPath() {
        return "Models/Characters/boss/bee_keeper.gltf";
    }

    @Override
    protected BossSpec getBossSpec() {
        BossSpec s = new BossSpec();
        s.hoverHeight = 7f;
        s.moveSpeed = 4.6f;
        s.attackCooldown = 2.2f;
        s.meleeRange = 1.5f; // the keeper strikes from the air, not hover-melee
        s.charge = true;
        s.chargeCooldown = 8.5f;
        s.chargeSpeed = 19f;
        s.chargeRange = 7f;
        s.chargeDuration = 0.7f;
        s.chargeHitRadius = 3.2f;
        s.chargeDamage = 15f;
        s.chargeRecovery = 0.8f;
        s.aoe = true;
        s.aoeWindup = 1.0f;
        s.aoeCooldown = 11f;
        s.aoeRadius = 9f;
        s.aoeDamage = 13f;
        s.summon = true;
        s.summonInterval = 20f;
        s.summonCount = 3;
        s.summonCap = 5;
        s.summonTier = 3;
        s.summonModels = new String[]{
                "Models/Characters/enemy/jungle_enemy2.gltf",
                "Models/Characters/enemy/jungle_enemy3.gltf"};
        return s;
    }

    @Override
    protected ColorRGBA getGroundColor() {
        return new ColorRGBA(0.22f, 0.45f, 0.18f, 1f);
    }

    @Override
    protected ColorRGBA getArenaColor() {
        return new ColorRGBA(0.2f, 0.5f, 0.22f, 1f);
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
        return 55f;
    }

    @Override
    public String getName() {
        return "Beekeeper";
    }

    @Override
    public int getStageIndex() {
        return 23;
    }
}
