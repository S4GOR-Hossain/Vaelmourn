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
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;

import java.util.Random;

/**
 * Fallen King Boss Arena — the final fight. A dark royal court ringed by very
 * tall pillars, with the fallen king's overturned throne on one side. The
 * middle stays an open disc so the king has the whole chamber to fight in.
 */
public class FallenKingBossStage extends BossStage {

    @Override
    protected void buildArenaDecor(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(775);

        // the great columns ringing the chamber
        for (int i = 0; i < 16; i++) {
            float angle = (i / 16f) * FastMath.TWO_PI;
            float radius = getRingStart() + 2f + rand.nextFloat() * (getRingEnd() - getRingStart() - 4f);
            float x = FastMath.cos(angle) * radius;
            float z = FastMath.sin(angle) * radius;

            Cylinder pillar = new Cylinder(2, 14, 1.6f, 11f, true);
            Geometry pillarGeo = new Geometry("CourtPillar_" + i, pillar);
            Material pillarMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            pillarMat.setColor("Color", new ColorRGBA(0.32f, 0.3f, 0.34f, 1f)); // cold stone
            pillarGeo.setMaterial(pillarMat);
            pillarGeo.rotate(FastMath.HALF_PI, 0f, 0f); // stand the cylinder up
            pillarGeo.setLocalTranslation(x, 5.5f, z);
            stageNode.attachChild(pillarGeo);

            // volumetric collider so the columns actually block movement
            BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(1.6f, 5.5f, 1.6f));
            RigidBodyControl physics = new RigidBodyControl(shape, 0);
            physics.setPhysicsLocation(new Vector3f(x, 5.5f, z));
            bulletAppState.getPhysicsSpace().add(physics);
            physicsObjects.add(physics);
        }

        // the overturned throne: slab base, backrest, and a slumped crown block
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
        back.rotate(0, 0.12f, 0); // knocked slightly off-square
        stageNode.attachChild(back);

        // a dim purple mote hovering over the throne as a "his seat is cursed" accent
        com.jme3.scene.shape.Sphere moteSphere = new com.jme3.scene.shape.Sphere(16, 16, 0.5f);
        Geometry mote = new Geometry("ThroneMote", moteSphere);
        Material moteMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        moteMat.setBoolean("UseMaterialColors", true);
        moteMat.setColor("Diffuse", new ColorRGBA(0.55f, 0.35f, 0.75f, 1f));
        moteMat.setColor("GlowColor", new ColorRGBA(0.4f, 0.2f, 0.7f, 1f));
        mote.setMaterial(moteMat);
        mote.setLocalTranslation(throneX, 7f, throneZ);
        stageNode.attachChild(mote);

        // pillar collider catches the throne too so the king's dais can't be walked through
        BoxCollisionShape throneShape = new BoxCollisionShape(new Vector3f(3.5f, 0.8f, 2.5f));
        RigidBodyControl thronePhysics = new RigidBodyControl(throneShape, 0);
        thronePhysics.setPhysicsLocation(new Vector3f(throneX, 0.8f, throneZ));
        bulletAppState.getPhysicsSpace().add(thronePhysics);
        physicsObjects.add(thronePhysics);
    }

    @Override
    protected float getBossScale() {
        return 1.5f; // the king hits hardest — it's the last gate before the next loop
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
        return new ColorRGBA(0.1f, 0.1f, 0.16f, 1f); // near-black court, lit only by embers
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.42f, 0.3f, 0.5f, 1f).mult(0.7f); // cold royal purple fill
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