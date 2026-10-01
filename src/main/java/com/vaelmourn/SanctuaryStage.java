package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SanctuaryStage — Stage 0: Safe hub.
 * Flat grass plaza ringed by a dense forest wall. The seven village props sit on an
 * evenly spaced ring inside the treeline; NPCs, chests and the player spawn are
 * authored elsewhere and are kept clear by {@link #HAZARD_POINTS}.
 */
public class SanctuaryStage implements Stage {

    private static final float HALF_EXTENT = 60f;
    private static final float WALL_HALF = 38f;

    /**
     * Evenly spaced prop ring. At 1.5x scale the props are wide enough that the ring had to
     * grow to 29u and the phase was solved so every slot clears the NPC/chest zone: the
     * narrowest gap from a prop edge to an NPC body centre is +0.98u, and the closest two
     * props are 8.6u apart. Props face inward, so the bounding box is measured in each
     * prop's own yaw frame rather than axis-aligned.
     */
    private static final float PROP_RING_RADIUS = 29f;
    private static final int PROP_SLOTS = 7;
    private static final float PROP_RING_PHASE_DEG = 264.5f;
    /** Walking room required between a prop edge and an NPC/chest body centre; ~1u is the body. */
    private static final float PROP_CLEARANCE = 2f;

    /**
     * Treeline sits just outside the boundary walls. It is scenery, not the barrier: the
     * ±38 wall ring plus its four corner seals already enclose the player, so the count can
     * stay low without opening a path out of the stage. Packs carry most of the visual mass.
     */
    private static final float TREELINE_RADIUS = 43f;
    private static final int TREELINE_COUNT = 44;
    private static final float TREELINE_JITTER = 1.2f;
    private static final float OUTER_SCATTER_RADIUS = 52f;
    private static final int OUTER_SCATTER_COUNT = 18;

    /** Multi-tree clumps make the treeline dense without paying one collider per tree. */
    private static final String[] TREELINE_PACKS = {
            "Models/Environment/Forest/tree_pack_01.glb",
            "Models/Environment/Forest/tree_pack_07.glb",
            "Models/Environment/Forest/tree_pack_17.glb"
    };

    private static final String[] TREELINE_SINGLES = {
            "Models/Environment/Forest/tree_default.glb",
            "Models/Environment/Forest/tree_cone.glb",
            "Models/Environment/Forest/tree_detailed.glb",
            "Models/Environment/Forest/tree_oak.glb",
            "Models/Environment/Forest/tree_fat.glb",
            "Models/Environment/Forest/tree_blocks.glb"
    };

    /** NPCs, chests and the player spawn — nothing decorative may overlap these. */
    private static final float[][] HAZARD_POINTS = {
            {0f, 0f},
            {-10f, -20f},
            {15f, 5f},
            {10f, -15f},
            {-20f, 10f},
            {5f, 25f}
    };

    /**
     * The seven requested village props. Scales are tuned against each model's authored
     * bounds (Town Center 1.26u, Hut 0.87u, Farm Dirt a flat 1.79u pad) so buildings
     * read as buildings next to a ~1.8u player.
     */
    private record Prop(String path, float scale, int slot) {
    }

    private static final Prop[] SANCTUARY_PROPS = {
            new Prop("Models/Environment/sanctuary/Town Center-76GTkSh4KM.glb", 9.0f, 0),
            new Prop("Models/Environment/sanctuary/Market Stalls.glb", 7.5f, 1),
            new Prop("Models/Environment/sanctuary/Archery Training Grounds.glb", 7.5f, 2),
            new Prop("Models/Environment/sanctuary/Houses.glb", 7.5f, 3),
            new Prop("Models/Environment/sanctuary/House-k6tP5nFUd2.glb", 7.5f, 4),
            new Prop("Models/Environment/sanctuary/Hut.glb", 8.25f, 5),
            new Prop("Models/Environment/sanctuary/Farm Dirt.glb", 7.5f, 6)
    };

    private Node stageNode;
    private Node decorNode;
    private Node forestNode;
    private final List<RigidBodyControl> physicsObjects = new ArrayList<>();

    @Override
    public void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState) {
        stageNode = new Node("Sanctuary");
        parentNode.attachChild(stageNode);

        decorNode = new Node("SanctuaryDecor");
        stageNode.attachChild(decorNode);

        forestNode = new Node("SanctuaryForest");
        stageNode.attachChild(forestNode);

        buildGroundPlane(assetManager, bulletAppState);
        buildBoundaryWalls(bulletAppState);
        buildVillageProps(assetManager, bulletAppState);
        buildForest(assetManager, bulletAppState);
    }

    @Override
    public void cleanup(Node parentNode, BulletAppState bulletAppState) {
        for (RigidBodyControl physics : physicsObjects) {
            bulletAppState.getPhysicsSpace().remove(physics);
        }
        physicsObjects.clear();
        if (stageNode != null) {
            stageNode.removeFromParent();
        }
    }

    private void buildGroundPlane(AssetManager assetManager, BulletAppState bulletAppState) {
        Box groundBox = new Box(HALF_EXTENT, 0.1f, HALF_EXTENT);
        Geometry ground = new Geometry("SanctuaryGround", groundBox);
        Material groundMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        groundMat.setBoolean("UseMaterialColors", true);
        groundMat.setColor("Diffuse", new ColorRGBA(0.1f, 0.2f, 0.1f, 1f));
        groundMat.setColor("Specular", ColorRGBA.White);
        groundMat.setFloat("Shininess", 16f);
        ground.setMaterial(groundMat);
        ground.setLocalTranslation(0, -0.1f, 0);
        stageNode.attachChild(ground);

        BoxCollisionShape groundShape = new BoxCollisionShape(new Vector3f(HALF_EXTENT, 0.1f, HALF_EXTENT));
        RigidBodyControl groundPhysics = new RigidBodyControl(groundShape, 0);
        groundPhysics.setPhysicsLocation(new Vector3f(0, -0.1f, 0));
        bulletAppState.getPhysicsSpace().add(groundPhysics);
        physicsObjects.add(groundPhysics);
    }

    private void buildBoundaryWalls(BulletAppState bulletAppState) {
        // North wall
        createWall(new Vector3f(0, 5f, -WALL_HALF), new Vector3f(40f, 5f, 2f), bulletAppState);
        // South wall
        createWall(new Vector3f(0, 5f, WALL_HALF), new Vector3f(40f, 5f, 2f), bulletAppState);
        // East wall
        createWall(new Vector3f(WALL_HALF, 5f, 0), new Vector3f(2f, 5f, 40f), bulletAppState);
        // West wall
        createWall(new Vector3f(-WALL_HALF, 5f, 0), new Vector3f(2f, 5f, 40f), bulletAppState);
        // Diagonal corner seals so the wall box corners cannot be slipped through
        createWall(new Vector3f(WALL_HALF - 3f, 5f, -WALL_HALF + 3f), new Vector3f(5f, 5f, 5f), bulletAppState);
        createWall(new Vector3f(WALL_HALF - 3f, 5f, WALL_HALF - 3f), new Vector3f(5f, 5f, 5f), bulletAppState);
        createWall(new Vector3f(-WALL_HALF + 3f, 5f, WALL_HALF - 3f), new Vector3f(5f, 5f, 5f), bulletAppState);
        createWall(new Vector3f(-WALL_HALF + 3f, 5f, -WALL_HALF + 3f), new Vector3f(5f, 5f, 5f), bulletAppState);
    }

    private void createWall(Vector3f position, Vector3f halfExtents, BulletAppState bulletAppState) {
        BoxCollisionShape wallShape = new BoxCollisionShape(halfExtents);
        RigidBodyControl wallPhysics = new RigidBodyControl(wallShape, 0);
        wallPhysics.setPhysicsLocation(position);
        bulletAppState.getPhysicsSpace().add(wallPhysics);
        physicsObjects.add(wallPhysics);
    }

    /** Drops the seven props on evenly spaced slots, each turned to face the plaza. */
    private void buildVillageProps(AssetManager assetManager, BulletAppState bulletAppState) {
        float stepDeg = 360f / PROP_SLOTS;

        for (Prop prop : SANCTUARY_PROPS) {
            float angleDeg = PROP_RING_PHASE_DEG + prop.slot() * stepDeg;
            float rad = FastMath.DEG_TO_RAD * angleDeg;
            float x = PROP_RING_RADIUS * FastMath.cos(rad);
            float z = PROP_RING_RADIUS * FastMath.sin(rad);

            Random rand = new Random(1000 + prop.slot());
            Spatial model = StageDecor.placeFlat(decorNode, assetManager, prop.path(),
                    x, z, prop.scale(), rand);

            // face inward toward the spawn plaza
            Quaternion facing = new Quaternion().fromAngleAxis(FastMath.atan2(-x, -z), Vector3f.UNIT_Y);
            model.setLocalRotation(model.getLocalRotation().mult(facing));

            if (blocksProp(prop.path())) {
                addPropBlocker(model, x, z, bulletAppState);
            }

            warnOnHazardOverlap(prop, model, x, z);
        }
    }

    /**
     * Distance from a hazard point to the nearest point on the prop's footprint box.
     * The prop is rotated to face the plaza, so the box is measured in its own yaw frame
     * rather than axis-aligned — at 1.5x scale a centre-to-centre check is far too lenient.
     */
    private static void warnOnHazardOverlap(Prop prop, Spatial model, float x, float z) {
        model.updateModelBound();
        if (!(model.getWorldBound() instanceof com.jme3.bounding.BoundingBox bbox)) {
            return;
        }
        Vector3f extent = bbox.getExtent(new Vector3f());
        float halfX = extent.x;
        float halfZ = extent.z;
        float yaw = FastMath.atan2(-x, -z);
        float cs = FastMath.cos(-yaw);
        float sn = FastMath.sin(-yaw);

        for (float[] hazard : HAZARD_POINTS) {
            float dx = hazard[0] - x;
            float dz = hazard[1] - z;
            float localX = dx * cs - dz * sn;
            float localZ = dx * sn + dz * cs;
            float overX = Math.max(Math.abs(localX) - halfX, 0f);
            float overZ = Math.max(Math.abs(localZ) - halfZ, 0f);
            float outside = FastMath.sqrt(overX * overX + overZ * overZ);

            if (outside < PROP_CLEARANCE) {
                System.out.println("[Sanctuary] " + prop.path().substring(prop.path().lastIndexOf('/') + 1)
                        + " at " + (int) x + "," + (int) z + " clears hazard "
                        + (int) hazard[0] + "," + (int) hazard[1] + " by only "
                        + String.format("%.2f", outside) + "u");
            }
        }
    }

    /** Flat decals (farm dirt) must not get a solid collider or the player walks on nothing. */
    private static boolean blocksProp(String path) {
        return !path.contains("Farm Dirt");
    }

    private void addPropBlocker(Spatial model, float x, float z, BulletAppState bulletAppState) {
        model.updateModelBound();
        if (model.getWorldBound() instanceof com.jme3.bounding.BoundingBox bbox) {
            Vector3f extent = bbox.getExtent(new Vector3f());
            float halfX = FastMath.clamp(extent.x * 0.9f, 1.5f, 8f);
            float halfZ = FastMath.clamp(extent.z * 0.9f, 1.5f, 8f);
            float halfY = FastMath.clamp(extent.y * 0.5f, 1.5f, 8f);
            float lift = StageDecor.baseLift(model);
            BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(halfX, halfY, halfZ));
            RigidBodyControl physics = new RigidBodyControl(shape, 0);
            physics.setPhysicsLocation(new Vector3f(x, lift + halfY, z));
            bulletAppState.getPhysicsSpace().add(physics);
            physicsObjects.add(physics);
        }
    }

    /**
     * Two concentric tree rings: a dense collidable treeline that closes the stage off at
     * {@link #TREELINE_RADIUS}, then a sparser uncollided outer ring for depth.
     */
    private void buildForest(AssetManager assetManager, BulletAppState bulletAppState) {
        Random rand = new Random(2024);

        float innerStep = 360f / TREELINE_COUNT;
        for (int i = 0; i < TREELINE_COUNT; i++) {
            float rad = FastMath.DEG_TO_RAD * (i * innerStep + rand.nextFloat() * innerStep * 0.4f);
            float jitter = TREELINE_RADIUS + (rand.nextFloat() - 0.5f) * TREELINE_JITTER;
            float x = jitter * FastMath.cos(rad);
            float z = jitter * FastMath.sin(rad);

            // packs on the even slots, singles on the odd ones: alternating clumps and
            // individual trees hides the repetition better than either alone
            boolean pack = (i % 2) == 0;
            String model = pack
                    ? TREELINE_PACKS[rand.nextInt(TREELINE_PACKS.length)]
                    : TREELINE_SINGLES[rand.nextInt(TREELINE_SINGLES.length)];
            float scale = pack
                    ? 1.9f + rand.nextFloat() * 0.7f
                    : 5.5f + rand.nextFloat() * 1.8f;

            Spatial tree = StageDecor.placeFlat(forestNode, assetManager, model, x, z, scale, rand);
            tree.updateModelBound();
            addTreeTrunk(tree, model, x, z, pack, bulletAppState);
        }

        float outerStep = 360f / OUTER_SCATTER_COUNT;
        for (int i = 0; i < OUTER_SCATTER_COUNT; i++) {
            float rad = FastMath.DEG_TO_RAD * (i * outerStep + rand.nextFloat() * outerStep * 0.6f);
            float jitter = OUTER_SCATTER_RADIUS + (rand.nextFloat() - 0.5f) * 6f;
            float x = jitter * FastMath.cos(rad);
            float z = jitter * FastMath.sin(rad);

            String model = TREELINE_SINGLES[rand.nextInt(TREELINE_SINGLES.length)];
            float scale = 6.0f + rand.nextFloat() * 2.2f;
            StageDecor.placeFlat(forestNode, assetManager, model, x, z, scale, rand);
        }

        System.out.println("[Sanctuary] " + SANCTUARY_PROPS.length + " village props on a radius-"
                + PROP_RING_RADIUS + " ring, " + TREELINE_COUNT + " treeline clumps at r="
                + TREELINE_RADIUS + " + " + OUTER_SCATTER_COUNT + " outer trees.");
    }

    /** Static trunk collider sized from the placed bounds so the player cannot walk out. */
    private void addTreeTrunk(Spatial tree, String model, float x, float z, boolean pack,
            BulletAppState bulletAppState) {
        if (!(tree.getWorldBound() instanceof com.jme3.bounding.BoundingBox bbox)) {
            return;
        }
        Vector3f extent = bbox.getExtent(new Vector3f());
        float radius = pack ? 2.2f : 1.1f;
        float collar = pack ? 4.5f : 6.5f;
        if (!model.contains("pack")) {
            radius = FastMath.clamp(Math.max(extent.x, extent.z) * 0.35f, 0.9f, 2f);
            collar = FastMath.clamp(extent.y, 4f, 9f);
        }

        BoxCollisionShape trunk = new BoxCollisionShape(new Vector3f(radius, collar * 0.5f, radius));
        RigidBodyControl physics = new RigidBodyControl(trunk, 0);
        physics.setPhysicsLocation(new Vector3f(x, collar * 0.5f, z));
        bulletAppState.getPhysicsSpace().add(physics);
        physicsObjects.add(physics);
    }

    @Override
    public ColorRGBA getSkyColor() {
        return new ColorRGBA(0.45f, 0.7f, 0.95f, 1f);
    }

    @Override
    public ColorRGBA getAmbientColor() {
        return new ColorRGBA(0.35f, 0.6f, 0.35f, 1f).mult(0.7f);
    }

    @Override
    public Vector3f getPlayerSpawnPoint() {
        return new Vector3f(0, 2f, 0);
    }

    @Override
    public Vector3f getSunDirection() {
        return new Vector3f(0.5f, -0.7f, -0.3f).normalizeLocal();
    }

    @Override
    public float getHalfExtent() {
        return HALF_EXTENT;
    }

    @Override
    public java.util.List<EnemyController> spawnEnemies(AssetManager assetManager, Node parentNode,
            BulletAppState bulletAppState, int variant) {
        return java.util.Collections.emptyList();
    }

    @Override
    public String getName() {
        return "Sanctuary";
    }

    @Override
    public int getStageIndex() {
        return 0;
    }
}