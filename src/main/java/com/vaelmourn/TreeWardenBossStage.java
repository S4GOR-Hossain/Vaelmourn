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

    @Override
    protected void buildArenaDecor(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(771);
        for (int i = 0; i < 26; i++) {
            float angle = (i / 26f) * FastMath.TWO_PI + rand.nextFloat() * 0.12f;
            float radius = getRingStart() + rand.nextFloat() * (getRingEnd() - getRingStart());
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            StageDecor.addTreeWithHitbox(assetManager, stageNode, bulletAppState,
                    physicsObjects, TREE_MODELS, rand, x, z);
        }

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
            StageDecor.placeFlat(stageNode, assetManager,
                    bushes[rand.nextInt(bushes.length)], x, z,
                    1.8f + rand.nextFloat() * 1.6f, rand);
        }
        for (int i = 0; i < 6; i++) {
            float angle = (i / 6f) * FastMath.TWO_PI + rand.nextFloat() * 0.2f;
            float radius = getRingStart() + 1f + rand.nextFloat() * (getRingEnd() - getRingStart() - 1f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            Spatial rock = StageDecor.placeFlat(stageNode, assetManager,
                    rocks[rand.nextInt(rocks.length)], x, z,
                    3f + rand.nextFloat() * 2.5f, rand);
            rock.updateModelBound();
            float half = 1.6f;
            if (rock.getWorldBound() instanceof BoundingBox bbox) {
                Vector3f ext = bbox.getExtent(new Vector3f());
                half = FastMath.clamp(Math.max(ext.x, ext.z) * 0.5f, 1.4f, 3.5f);
            }
            StageDecor.addBlocker(bulletAppState, physicsObjects, x, z, half, half, half);
        }
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