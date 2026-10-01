package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

import java.util.Random;

/** Frost Giant Boss Arena — a frozen caldera ringed by ice peaks; open disc at the centre. */
public class FrostGiantBossStage extends BossStage {

    private static final String CENJI =
            "Models/Environment/Cenji_FantasyCrystalPack_FREE/Cenji_FantasyCrystalPack_FREE/GLB/";

    @Override
    protected void buildArenaDecor(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(773);

        for (int i = 0; i < 18; i++) {
            float angle = (i / 18f) * FastMath.TWO_PI + rand.nextFloat() * 0.2f;
            float radius = getRingStart() + rand.nextFloat() * (getRingEnd() - getRingStart());
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            float height = 5f + rand.nextFloat() * 7f;
            float base = 1.4f + rand.nextFloat() * 1.2f;

            Geometry spike = new Geometry("FrostPeak_" + i, StageDecor.iceSpike(base, height));
            Material spikeMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            spikeMat.setColor("Color", new ColorRGBA(0.65f, 0.8f, 0.95f, 0.9f));
            spike.setMaterial(spikeMat);
            spike.setLocalTranslation(x, 0f, z);
            spike.rotate((rand.nextFloat() - 0.5f) * 0.5f, rand.nextFloat() * FastMath.TWO_PI,
                    (rand.nextFloat() - 0.5f) * 0.5f);
            stageNode.attachChild(spike);

            CapsuleCollisionShape shape = new CapsuleCollisionShape(base * 1.3f, height);
            RigidBodyControl physics = new RigidBodyControl(shape, 0);
            float centerY = (height + 2f * base * 1.3f) / 2f;
            physics.setPhysicsLocation(new Vector3f(x, centerY, z));
            bulletAppState.getPhysicsSpace().add(physics);
            physicsObjects.add(physics);
        }

        String[] rocks = {
                CENJI + "Rocks/ROCK_Glacial_01_LargeCliff.glb",
                CENJI + "Rocks/ROCK_Glacial_02_MediumRock.glb",
                CENJI + "Rocks/ROCK_Glacial_04_IceSpire.glb"
        };
        String[] formations = {
                CENJI + "Crystal_Formations/PROP_19_IceCrystalFormation.glb",
                CENJI + "Crystal_Formations/PROP_04_TallCrystalFormation.glb",
                CENJI + "Crystal_Formations/PROP_07_CrystalPillar.glb",
                CENJI + "Crystal_Formations/PROP_10_CrystalGeode.glb",
                CENJI + "Crystal_Formations/PROP_15_CrystalShardPile.glb"
        };

        for (int i = 0; i < 10; i++) {
            float angle = (i / 10f) * FastMath.TWO_PI + rand.nextFloat() * 0.15f;
            float radius = getRingStart() + 2f + rand.nextFloat() * (getRingEnd() - getRingStart() - 2f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            Spatial rock = StageDecor.placeFlat(stageNode, assetManager,
                    rocks[rand.nextInt(rocks.length)], x, z,
                    2.4f + rand.nextFloat() * 2f, rand);
            rock.updateModelBound();
            float half = 1.4f;
            if (rock.getWorldBound() instanceof BoundingBox bbox) {
                Vector3f ext = bbox.getExtent(new Vector3f());
                half = FastMath.clamp(Math.max(ext.x, ext.z) * 0.5f, 1.2f, 3f);
            }
            StageDecor.addBlocker(bulletAppState, physicsObjects, x, z, half, half, half);
        }

        for (int i = 0; i < 10; i++) {
            float angle = rand.nextFloat() * FastMath.TWO_PI;
            float radius = getRingStart() + 2f + rand.nextFloat() * (getRingEnd() - getRingStart() - 2f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            StageDecor.placeFlat(stageNode, assetManager,
                    formations[rand.nextInt(formations.length)], x, z,
                    2.6f + rand.nextFloat() * 1.8f, rand);
        }

        for (int i = 0; i < 10; i++) {
            float angle = (i / 10f) * FastMath.TWO_PI + rand.nextFloat() * 0.2f;
            float radius = getRingStart() + 1f + rand.nextFloat() * (getRingEnd() - getRingStart() - 1f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            Box mound = new Box(1.5f + rand.nextFloat() * 1.4f, 0.8f, 1.5f + rand.nextFloat() * 1.4f);
            Geometry moundGeo = new Geometry("SnowMound_" + i, mound);
            Material moundMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            moundMat.setColor("Color", new ColorRGBA(0.9f, 0.95f, 1f, 1f));
            moundGeo.setMaterial(moundMat);
            moundGeo.setLocalTranslation(x, 0.6f, z);
            stageNode.attachChild(moundGeo);
        }
    }

    @Override
    protected float getBossScale() {
        return 1.3f;
    }

    @Override
    protected String getBossModelPath() {
        return "Models/Characters/boss/frost_giant.gltf";
    }

    @Override
    protected BossSpec getBossSpec() {
        BossSpec s = new BossSpec();
        s.moveSpeed = 2.1f;
        s.attackCooldown = 3.0f;
        s.meleeRange = 4.8f;
        s.slowOnHit = 1.2f; // everything the giant lands chills the player
        s.smash = true;
        s.smashWindup = 1.1f;
        s.smashCooldown = 7.5f;
        s.smashRadius = 10.5f;
        s.smashDamage = 22f;
        s.smashRecovery = 1.1f;
        s.aoe = true;
        s.aoeWindup = 1.3f;
        s.aoeCooldown = 12f;
        s.aoeRadius = 13f;
        s.aoeDamage = 16f;
        s.aoeRecovery = 1.3f;
        return s;
    }

    @Override
    protected ColorRGBA getGroundColor() {
        return new ColorRGBA(0.75f, 0.85f, 0.95f, 1f);
    }

    @Override
    protected ColorRGBA getArenaColor() {
        return new ColorRGBA(0.6f, 0.72f, 0.88f, 1f);
    }

    @Override
    public ColorRGBA getSkyColor() {
        return new ColorRGBA(0.7f, 0.8f, 0.92f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.5f, 0.6f, 0.8f, 1f).mult(0.7f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.4f, -0.6f, -0.5f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return 55f;
    }

    @Override
    public String getName() {
        return "Frost Giant";
    }

    @Override
    public int getStageIndex() {
        return 17;
    }
}