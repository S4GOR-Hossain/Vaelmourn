package com.vaelmourn;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;

public class WorldTest extends SimpleApplication {

    private BulletAppState bulletAppState;

    public static void main(String[] args) {
        WorldTest app = new WorldTest();
        app.start();
    }

    @Override
    public void simpleInitApp() {

        // 1. Turn on the physics engine
        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

        // 2. Build the visible floor
        Box floorBox = new Box(50f, 0.5f, 50f); // half-extents: 100m x 1m x 100m total
        Geometry floor = new Geometry("Floor", floorBox);

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Gray);
        floor.setMaterial(mat);

        floor.setLocalTranslation(0, -0.5f, 0);

        // 3. Give the floor a physics body so things can stand on it
        RigidBodyControl floorPhysics = new RigidBodyControl(0); // mass 0 = static, never moves
        floor.addControl(floorPhysics);

        rootNode.attachChild(floor);
        bulletAppState.getPhysicsSpace().add(floorPhysics);

        // 4. Add basic lighting so you can actually see it
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal());
        sun.setColor(ColorRGBA.White);
        rootNode.addLight(sun);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.4f));
        rootNode.addLight(ambient);

        // 5. Pull the debug camera back so you can see the whole floor
        cam.setLocation(new Vector3f(0, 15, 30));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    }
}