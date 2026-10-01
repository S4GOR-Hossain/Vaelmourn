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

/** Fallen King Boss Arena — the final fight: a dark royal court of tall pillars and an overturned throne. */
public class FallenKingBossStage extends BossStage {

    private static final String DUNGEON =
            "Models/Environment/KayKit_Dungeon_Pack_1.1_FREE/Assets/gltf/";

    @Override
    protected void buildArenaDecor(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(775);
        String[] pillars = {
                DUNGEON + "pillar_decorated.gltf",
                DUNGEON + "pillar.gltf",
                DUNGEON + "column.gltf"
        };

        for (int i = 0; i < 16; i++) {
            float angle = (i / 16f) * FastMath.TWO_PI;
            float radius = getRingStart() + 2f + rand.nextFloat() * (getRingEnd() - getRingStart() - 4f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            Spatial pillar = StageDecor.placeFlat(stageNode, assetManager,
                    pillars[rand.nextInt(pillars.length)], x, z,
                    3.5f + rand.nextFloat() * 1.5f, rand);
            pillar.updateModelBound();
            float hw = 1.5f;
            float hh = 6f;
            if (pillar.getWorldBound() instanceof BoundingBox bbox) {
                Vector3f ext = bbox.getExtent(new Vector3f());
                hw = FastMath.clamp(Math.max(ext.x, ext.z) * 0.7f, 1.2f, 2.6f);
                hh = FastMath.clamp(ext.y, 4f, 9f);
            }
            StageDecor.addBlocker(bulletAppState, physicsObjects, x, z, hw, hh / 2f, hw);
        }

        // broken banner wall backdrop along the west balk, so the court reads as ruined
        String[] walls = {
                DUNGEON + "wall.gltf",
                DUNGEON + "wall_broken.gltf",
                DUNGEON + "wall_arched.gltf"
        };
        String[] banners = {
                DUNGEON + "banner_red.gltf",
                DUNGEON + "banner_shield_blue.gltf",
                DUNGEON + "banner_triple_red.gltf"
        };
        for (int i = 0; i < 5; i++) {
            float angle = FastMath.PI * (0.74f + 0.13f * i);
            float radius = 43f + rand.nextFloat() * 3f;
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;
            Spatial wall = StageDecor.placeFlat(stageNode, assetManager,
                    walls[i % walls.length], x, z, 2f + rand.nextFloat(), rand);
            wall.updateModelBound();
            float hw = 2f;
            float hh = 3f;
            if (wall.getWorldBound() instanceof BoundingBox bbox) {
                Vector3f ext = bbox.getExtent(new Vector3f());
                hw = FastMath.clamp(Math.max(ext.x, ext.z) * 0.7f, 1.5f, 4f);
                hh = FastMath.clamp(ext.y, 2f, 5f);
            }
            StageDecor.addBlocker(bulletAppState, physicsObjects, x, z, hw, hh / 2f, hw);
            StageDecor.placeFlat(stageNode, assetManager,
                    banners[rand.nextInt(banners.length)], x - 2.2f, z,
                    1.3f + rand.nextFloat() * 0.4f, rand);
        }

        // the fallen king's own palace looming behind the north rim
        Spatial castle = StageDecor.placeFlat(stageNode, assetManager,
                "Models/Environment/LowPolyCastle/LowPolyCastle/Models/LowPolyCastle.glb",
                0f, 51f, 1.2f, rand);
        castle.rotate(0, FastMath.PI, 0);
        castle.updateModelBound();
        StageDecor.addBlocker(bulletAppState, physicsObjects, 0f, 51f, 22f, 9f, 14f);

        float throneX = -26f;
        float throneZ = -26f;

        Box baseBox = new Box(3.5f, 0.8f, 2.5f);
        Geometry base = new Geometry("ThroneBase", baseBox);
        Material stoneMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        stoneMat.setColor("Color", new ColorRGBA(0.28f, 0.26f, 0.32f, 1f));
        base.setMaterial(stoneMat);
        base.setLocalTranslation(throneX, 0.8f, throneZ);
        stageNode.attachChild(base);

        Box backBox = new Box(3.5f, 3.2f, 0.9f);
        Geometry back = new Geometry("ThroneBack", backBox);
        Material backMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        backMat.setColor("Color", new ColorRGBA(0.24f, 0.22f, 0.3f, 1f));
        back.setMaterial(backMat);
        back.setLocalTranslation(throneX, 3.2f, throneZ + 1.6f);
        back.rotate(0, 0.12f, 0);
        stageNode.attachChild(back);

        com.jme3.scene.shape.Sphere moteSphere = new com.jme3.scene.shape.Sphere(16, 16, 0.5f);
        Geometry mote = new Geometry("ThroneMote", moteSphere);
        Material moteMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        moteMat.setBoolean("UseMaterialColors", true);
        moteMat.setColor("Diffuse", new ColorRGBA(0.55f, 0.35f, 0.75f, 1f));
        moteMat.setColor("GlowColor", new ColorRGBA(0.4f, 0.2f, 0.7f, 1f));
        mote.setMaterial(moteMat);
        mote.setLocalTranslation(throneX, 7f, throneZ);
        stageNode.attachChild(mote);

        // collider for the throne so the king's dais can't be walked through
        BoxCollisionShape throneShape = new BoxCollisionShape(new Vector3f(3.5f, 0.8f, 2.5f));
        RigidBodyControl thronePhysics = new RigidBodyControl(throneShape, 0);
        thronePhysics.setPhysicsLocation(new Vector3f(throneX, 0.8f, throneZ));
        bulletAppState.getPhysicsSpace().add(thronePhysics);
        physicsObjects.add(thronePhysics);
    }

    @Override
    protected float getBossScale() {
        return 1.5f;
    }

    @Override
    protected String getBossModelPath() {
        return "Models/Characters/boss/fallen_king.gltf";
    }

    @Override
    protected BossSpec getBossSpec() {
        BossSpec s = new BossSpec();
        s.moveSpeed = 3.0f;
        s.attackCooldown = 2.4f;
        s.meleeRange = 3.6f;
        s.charge = true;
        s.chargeCooldown = 8f;
        s.chargeSpeed = 20f;
        s.chargeRange = 6f;
        s.chargeDuration = 0.65f;
        s.chargeHitRadius = 3.2f;
        s.chargeDamage = 17f;
        s.aoe = true;
        s.aoeWindup = 1.1f;
        s.aoeCooldown = 11f;
        s.aoeRadius = 10f;
        s.aoeDamage = 15f;
        s.summon = true;
        s.summonInterval = 24f;
        s.summonCount = 2;
        s.summonCap = 4;
        s.summonTier = 3;
        s.summonModels = new String[]{
                "Models/Characters/enemy/kingdomcourt_enemy.gltf",
                "Models/Characters/enemy/kingdomcourt_enemy2.gltf"};
        return s;
    }

    @Override
    protected ColorRGBA getGroundColor() {
        return new ColorRGBA(0.18f, 0.18f, 0.22f, 1f);
    }

    @Override
    protected ColorRGBA getArenaColor() {
        return new ColorRGBA(0.26f, 0.24f, 0.34f, 1f);
    }

    @Override
    public ColorRGBA getSkyColor() {
        return new ColorRGBA(0.1f, 0.1f, 0.16f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.42f, 0.3f, 0.5f, 1f).mult(0.7f);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(-0.5f, -0.8f, -0.2f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return 55f;
    }

    @Override
    public String getName() {
        return "Fallen King";
    }

    @Override
    public int getStageIndex() {
        return 28; // the loop's last stage — beating him wraps back to the Sanctuary
    }
}