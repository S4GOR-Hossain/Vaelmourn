package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

import java.util.Random;

/** Tree Warden Boss Arena — a clearing ringed by great trees, open disc in the middle. */
public class TreeWardenBossStage extends BossStage {

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