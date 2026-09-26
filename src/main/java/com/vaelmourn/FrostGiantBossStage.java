package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;

import java.util.Random;

/**
 * Frost Giant Boss Arena — a frozen caldera ringed by towering ice peaks and
 * icebergs. The center is a smooth open disc for the Giant to stomp around in.
 */
public class FrostGiantBossStage extends BossStage {

    @Override
    protected void buildArenaDecor(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(773);

        // jagged ice-peak ring around the open combat area
        for (int i = 0; i < 22; i++) {
            float angle = (i / 22f) * FastMath.TWO_PI + rand.nextFloat() * 0.2f;
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

        // snow mounds add floor texture without crowding the arena
        for (int i = 0; i < 18; i++) {
            float angle = (i / 18f) * FastMath.TWO_PI + rand.nextFloat() * 0.2f;
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