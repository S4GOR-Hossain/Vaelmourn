package com.vaelmourn;

import com.jme3.asset.AssetManager;
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

    private static final String[] STONE_MODELS = {
            "Models/Environment/Forest/stone_tallA.glb",
            "Models/Environment/Forest/stone_tallB.glb",
            "Models/Environment/Forest/stone_tallC.glb",
            "Models/Environment/Forest/stone_tallD.glb",
            "Models/Environment/Forest/stone_largeA.glb",
            "Models/Environment/Forest/stone_largeB.glb",
            "Models/Environment/Forest/stone_largeC.glb"
    };

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

        for (int i = 0; i < 18; i++) {
            float angle = (i / 18f) * FastMath.TWO_PI + rand.nextFloat() * 0.15f;
            float radius = getRingStart() + 2f + rand.nextFloat() * (getRingEnd() - getRingStart() - 2f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            String model = STONE_MODELS[rand.nextInt(STONE_MODELS.length)];
            Spatial stone = assetManager.loadModel(model);
            stone.setLocalTranslation(x, 0, z);
            stone.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
            float size = 4f + rand.nextFloat() * 3f;
            stone.setLocalScale(size);
            stageNode.attachChild(stone);

            BoxCollisionShape rockShape = new BoxCollisionShape(new Vector3f(size * 0.35f, size * 0.4f, size * 0.35f));
            RigidBodyControl physics = new RigidBodyControl(rockShape, 0);
            physics.setPhysicsLocation(new Vector3f(x, size * 0.35f, z));
            bulletAppState.getPhysicsSpace().add(physics);
            physicsObjects.add(physics);
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