package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

import java.util.List;
import java.util.Random;

/**
 * Shared helpers for building scenery with collision — the tree-ring and ice-spike
 * recipes used by the boss arenas (and the biome stages needing the same tricks).
 */
public final class StageDecor {

    private StageDecor() {
    }

    public static void addTreeWithHitbox(AssetManager assetManager, Node parent,
                                         BulletAppState bulletAppState,
                                         List<RigidBodyControl> physicsOut,
                                         String[] models, Random rand, float x, float z) {
        String model = models[rand.nextInt(models.length)];
        Spatial tree = assetManager.loadModel(model);
        tree.rotate(0, rand.nextFloat() * FastMath.TWO_PI, 0);
        tree.setLocalScale(4.2f + rand.nextFloat() * 1.1f);

        // the pack pivots on the model's vertical center — lift it so the base
        // sits on the ground instead of half-burying it
        tree.updateModelBound();
        float lift = 2f;
        if (tree.getWorldBound() instanceof BoundingBox bbox) {
            Vector3f extent = bbox.getExtent(new Vector3f());
            lift = extent.y - bbox.getCenter().y;
        }
        tree.setLocalTranslation(x, lift, z);
        parent.attachChild(tree);

        BoxCollisionShape trunk = new BoxCollisionShape(new Vector3f(0.9f, 3f, 0.9f));
        RigidBodyControl physics = new RigidBodyControl(trunk, 0);
        physics.setPhysicsLocation(new Vector3f(x, 3f, z));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsOut.add(physics);
    }

    public static Mesh iceSpike(float baseRadius, float height) {
        int sides = 8;
        Mesh mesh = new Mesh();

        int vertCount = sides + 2;
        Vector3f[] positions = new Vector3f[vertCount];
        Vector3f[] normals = new Vector3f[vertCount];
        positions[0] = new Vector3f(0f, height, 0f);
        positions[1] = new Vector3f(0f, 0f, 0f);
        normals[0] = new Vector3f(0f, 1f, 0f);
        normals[1] = new Vector3f(0f, -1f, 0f);
        for (int i = 0; i < sides; i++) {
            float a = (i / (float) sides) * FastMath.TWO_PI;
            float nx = FastMath.cos(a);
            float nz = FastMath.sin(a);
            positions[i + 2] = new Vector3f(nx * baseRadius, 0f, nz * baseRadius);
            normals[i + 2] = new Vector3f(nx, 0f, nz);
        }

        int[] indices = new int[sides * 6];
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            indices[i * 6 + 0] = 0;
            indices[i * 6 + 1] = i + 2;
            indices[i * 6 + 2] = next + 2;
            indices[i * 6 + 3] = 1;
            indices[i * 6 + 4] = next + 2;
            indices[i * 6 + 5] = i + 2;
        }

        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        mesh.updateCounts();
        return mesh;
    }
}