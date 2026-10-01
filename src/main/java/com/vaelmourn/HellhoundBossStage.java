package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

import java.util.Random;

/** Hellhound Boss Arena — a volcanic caldera of lava pools and rocks around an open circle. */
public class HellhoundBossStage extends BossStage {

    private static final String CENJI =
            "Models/Environment/Cenji_FantasyCrystalPack_FREE/Cenji_FantasyCrystalPack_FREE/GLB/";

    @Override
    protected void buildArenaDecor(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(772);

        for (int i = 0; i < 22; i++) {
            float angle = (i / 22f) * FastMath.TWO_PI + rand.nextFloat() * 0.2f;
            float radius = getRingStart() + 1f + rand.nextFloat() * (getRingEnd() - getRingStart() - 1f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            float size = 2.2f + rand.nextFloat() * 1.6f;
            Box lavaBox = new Box(size, 0.08f, size);
            Geometry lava = new Geometry("LavaRock_" + i, lavaBox);
            Material lavaMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            lavaMat.setColor("Color", new ColorRGBA(0.9f, 0.3f, 0.05f, 1f));
            lava.setMaterial(lavaMat);
            lava.setLocalTranslation(x, 0.04f, z);
            stageNode.attachChild(lava);
        }

        String[] rocks = {
                CENJI + "Rocks/ROCK_Volcanic_01_LargeCrag.glb",
                CENJI + "Rocks/ROCK_Volcanic_02_MediumBlock.glb",
                CENJI + "Rocks/ROCK_Volcanic_04_BasaltPillar.glb",
                CENJI + "Rocks/ROCK_Corrupted_01_LargeMass.glb",
                CENJI + "Rocks/ROCK_Corrupted_04_TwistedPinnacle.glb"
        };
        String[] crystals = {
                CENJI + "Crystal_Formations/PROP_06_CrystalSpikes.glb",
                CENJI + "Crystal_Formations/PROP_18_DarkCursedCrystal.glb",
                CENJI + "Crystal_Formations/PROP_20_EmberCrystalFormation.glb",
                CENJI + "Crystal_Formations/PROP_17_RuneCrystal.glb"
        };

        for (int i = 0; i < 18; i++) {
            float angle = (i / 18f) * FastMath.TWO_PI + rand.nextFloat() * 0.15f;
            float radius = getRingStart() + 2f + rand.nextFloat() * (getRingEnd() - getRingStart() - 2f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            Spatial stone = StageDecor.placeFlat(stageNode, assetManager,
                    rocks[rand.nextInt(rocks.length)], x, z,
                    2.6f + rand.nextFloat() * 2.2f, rand);
            stone.updateModelBound();
            float half = 1.4f;
            if (stone.getWorldBound() instanceof BoundingBox bbox) {
                Vector3f ext = bbox.getExtent(new Vector3f());
                half = FastMath.clamp((ext.x + ext.z) * 0.4f, 1.2f, 3f);
            }
            StageDecor.addBlocker(bulletAppState, physicsObjects, x, z, half, half, half);
        }

        for (int i = 0; i < 8; i++) {
            float angle = rand.nextFloat() * FastMath.TWO_PI;
            float radius = getRingStart() + 3f + rand.nextFloat() * (getRingEnd() - getRingStart() - 3f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            StageDecor.placeFlat(stageNode, assetManager, crystals[rand.nextInt(crystals.length)],
                    x, z, 2.8f + rand.nextFloat() * 1.6f, rand);
        }
    }

    @Override
    protected float getBossScale() {
        return 1.15f;
    }

    @Override
    protected String getBossModelPath() {
        return "Models/Characters/boss/hell_hound.gltf";
    }

    @Override
    protected BossSpec getBossSpec() {
        BossSpec s = new BossSpec();
        s.hoverHeight = 6f;
        s.moveSpeed = 5.2f;
        s.attackCooldown = 2.4f;
        s.meleeRange = 1.5f; // aerial beast bites from swoops, not hover-melee
        s.charge = true;
        s.chargeCooldown = 8f;
        s.chargeSpeed = 24f;
        s.chargeRange = 6f;
        s.chargeDuration = 0.8f;
        s.chargeHitRadius = 3.4f;
        s.chargeDamage = 16f;
        s.chargeRecovery = 0.9f;
        s.smash = true; // diving slam: rises at the windup, slams into the disc
        s.smashWindup = 0.7f;
        s.smashCooldown = 7.5f;
        s.smashRadius = 7f;
        s.smashDamage = 14f;
        s.smashRecovery = 1.0f;
        s.summon = true;
        s.summonInterval = 21f;
        s.summonCount = 2;
        s.summonCap = 4;
        s.summonTier = 2;
        s.summonModels = new String[]{
                "Models/Characters/enemy/ashenwastes_enemy.gltf",
                "Models/Characters/enemy/ashenwastes_enemy2.gltf"};
        return s;
    }

    @Override
    protected ColorRGBA getGroundColor() {
        return new ColorRGBA(0.45f, 0.3f, 0.18f, 1f);
    }

    @Override
    protected ColorRGBA getArenaColor() {
        return new ColorRGBA(0.4f, 0.24f, 0.12f, 1f);
    }

    @Override
    public ColorRGBA getSkyColor() {
        return new ColorRGBA(0.6f, 0.35f, 0.15f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.8f, 0.5f, 0.3f, 1f).mult(0.6f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.2f, -1f, -0.1f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return 55f;
    }

    @Override
    public String getName() {
        return "Hellhound";
    }

    @Override
    public int getStageIndex() {
        return 11;
    }
}