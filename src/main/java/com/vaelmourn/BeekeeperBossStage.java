package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

import java.util.Random;

/**
 * Beekeeper Boss Arena — a jungle clearing walled in by trees. The center is a
 * big open circle for the Beekeeper's swarm fight.
 */
public class BeekeeperBossStage extends BossStage {

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
    }

    @Override
    protected float getBossScale() {
        return 1.15f;
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