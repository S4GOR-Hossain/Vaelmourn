package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.control.BillboardControl;
import com.jme3.util.BufferUtils;

/**
 * Small marker that hovers over the player's head and points at the closest living
 * enemy.
 *
 * <p>It answers exactly one question — "which way is the nearest enemy?" — so it never
 * encodes distance and never grows with range. It is drawn unshaded and depth-write
 * disabled so it stays legible against every biome's sky and terrain without becoming
 * an obstruction.</p>
 *
 * <p>The bearing is applied as a roll in screen space rather than a world-space
 * heading. That is what makes the marker player-relative for free: the enemy direction
 * is projected onto the camera's own right/forward axes, so ahead reads as up, to the
 * player's right reads as right, and behind reads as down — automatically consistent
 * with however the player has turned.</p>
 *
 * <p>All direction maths uses the ground plane (Y forced to zero) so enemies far above
 * or below still yield a clean horizontal bearing instead of a marker that pitches
 * around with elevation.</p>
 */
public class EnemyDirectionArrow {

    /** height above the player's feet; clears the head and the tallest jump arc */
    private static final float HOVER_HEIGHT = 2.55f;
    private static final float ARROW_SIZE = 0.62f;
    /** how far above the arrow a ceiling probe reaches before the marker pulls down */
    private static final float CEILING_PROBE = 2.6f;
    private static final float POSITION_SMOOTHING = 14f;
    private static final float ROTATION_SMOOTHING = 16f;

    private final AssetManager assetManager;
    private final Node rootNode;

    private Node arrowNode;
    private Geometry arrowGeo;

    /** cached world position, so the marker eases after the player instead of snapping */
    private final Vector3f currentPos = new Vector3f();
    private boolean positioned;
    /** screen-space roll, kept unwrapped so it eases the short way round */
    private float currentAngle;
    private boolean angleInitialized;

    private final Quaternion rollQuat = new Quaternion();
    private final Vector3f scratchPlayer = new Vector3f();
    private final Vector3f scratchToEnemy = new Vector3f();
    private final Vector3f scratchRight = new Vector3f();
    private final Vector3f scratchForward = new Vector3f();
    private final Vector3f scratchTarget = new Vector3f();
    private final Vector3f scratchRayDir = new Vector3f();
    private final CollisionResults ceilingHits = new CollisionResults();

    public EnemyDirectionArrow(AssetManager assetManager, Node rootNode) {
        this.assetManager = assetManager;
        this.rootNode = rootNode;
    }

    /**
     * One frame of upkeep. A null target — cleared level, Sanctuary, or a dead run —
     * hides the marker outright, so an arrow pointing at nothing can never exist.
     *
     * @param playerPos player's world position
     * @param target    closest living enemy, or null to hide
     * @param camRight  camera's world-space right axis
     * @param camFwd    camera's world-space forward axis
     */
    public void update(float tpf, Vector3f playerPos, EnemyController target,
                       Vector3f camRight, Vector3f camFwd) {
        if (target == null || playerPos == null) {
            hide();
            return;
        }
        if (arrowNode == null) build();

        setArrowCulled(false);
        updatePosition(tpf, playerPos);
        updateRotation(tpf, target.getPosition(), camRight, camFwd);
    }

    /** Hides the marker and forgets its pose so the next level starts clean. */
    public void hide() {
        if (arrowNode != null) setArrowCulled(true);
        positioned = false;
        angleInitialized = false;
    }

    /** Full teardown, for when the owning game state is discarded. */
    public void dispose() {
        if (arrowNode != null) arrowNode.removeFromParent();
        arrowNode = null;
        arrowGeo = null;
        positioned = false;
        angleInitialized = false;
    }

    // ---- internals -----------------------------------------------------------

    /**
     * Visibility is driven through the cull hint, matching how the rest of this
     * project hides HUD and effect nodes. Dynamic would also work but Always is
     * unambiguous and costs the renderer nothing while the marker is idle.
     */
    private void setArrowCulled(boolean hidden) {
        Spatial.CullHint hint = hidden ? Spatial.CullHint.Always : Spatial.CullHint.Never;
        if (arrowNode != null) arrowNode.setCullHint(hint);
        if (arrowGeo != null) arrowGeo.setCullHint(hint);
    }

    /**
     * Follows the player, pulling down when a ceiling is close so the marker is never
     * swallowed by geometry. Smoothing keeps it steady while jumping or dodging.
     */
    private void updatePosition(float tpf, Vector3f playerPos) {
        scratchPlayer.set(playerPos);
        scratchTarget.set(scratchPlayer.x, scratchPlayer.y + HOVER_HEIGHT, scratchPlayer.z);

        float headroom = clearanceAbove(scratchTarget);
        if (headroom >= 0f) {
            scratchTarget.y -= Math.max(0f, CEILING_PROBE - headroom);
        }

        if (!positioned) {
            currentPos.set(scratchTarget);
            positioned = true;
        } else {
            currentPos.interpolateLocal(scratchTarget,
                    1f - (float) Math.exp(-POSITION_SMOOTHING * Math.max(tpf, 1e-4f)));
        }
        arrowNode.setLocalTranslation(currentPos);
    }

    /**
     * Distance straight up from {@code from} to the first surface, or -1 for clear sky.
     * The probe is scoped away from characters so neither the player capsule nor an
     * enemy's body can be mistaken for a ceiling.
     */
    private float clearanceAbove(Vector3f from) {
        ceilingHits.clear();
        rootNode.collideWith(new Ray(from, Vector3f.UNIT_Y.mult(CEILING_PROBE)), ceilingHits);
        for (CollisionResult hit : ceilingHits) {
            if (isMarkerOrCharacter(hit.getGeometry())) continue;
            return hit.getDistance();
        }
        return -1f;
    }

    /** Skips the arrow itself and character nodes when probing for a ceiling. */
    private boolean isMarkerOrCharacter(Spatial spatial) {
        while (spatial != null) {
            String name = spatial.getName();
            if (name != null) {
                if (name.startsWith("EnemyArrow")
                        || name.equals("Player")
                        || name.equals("Boss")
                        || name.startsWith("Enemy")) {
                    return true;
                }
            }
            spatial = spatial.getParent();
        }
        return false;
    }

    /**
     * Rolls the marker in screen space to face the target.
     *
     * <p>Projecting the ground-plane bearing onto the camera's right/forward basis is
     * precisely what makes this player-relative: the projection already accounts for
     * where the player is facing, so no separate facing angle is threaded in and the
     * marker can never disagree with the camera.</p>
     */
    private void updateRotation(float tpf, Vector3f targetPos, Vector3f camRight, Vector3f camFwd) {
        scratchRight.set(camRight).setY(0f);
        scratchForward.set(camFwd).setY(0f);
        if (scratchRight.lengthSquared() < 1e-6f || scratchForward.lengthSquared() < 1e-6f) {
            return;
        }
        scratchRight.normalizeLocal();
        scratchForward.normalizeLocal();

        scratchToEnemy.set(targetPos).subtract(currentPos).setY(0f);
        if (scratchToEnemy.lengthSquared() < 1e-6f) return;

        float alongRight = scratchToEnemy.dot(scratchRight);
        float alongForward = scratchToEnemy.dot(scratchForward);

        // atan2(right, forward): 0 = straight ahead (marker up), +PI/2 = screen right.
        // Screen Y grows upward while jME rotates counter-clockwise about +Z, so the
        // roll is negated to keep the on-screen mapping intuitive.
        float angle = -FastMath.atan2(alongRight, alongForward);

        if (!angleInitialized) {
            currentAngle = angle;
            angleInitialized = true;
        } else {
            // ease the short way round rather than spinning the long way on wraparound
            float delta = FastMath.atan2(
                    FastMath.sin(angle - currentAngle), FastMath.cos(angle - currentAngle));
            currentAngle += delta * (1f - (float) Math.exp(-ROTATION_SMOOTHING * Math.max(tpf, 1e-4f)));
        }

        rollQuat.fromAngleAxis(currentAngle, Vector3f.UNIT_Z);
        arrowGeo.setLocalRotation(rollQuat);
    }

    /**
     * Builds a flat upward triangle and parks it in the scene graph once.
     *
     * <p>Model space puts the tip at +Y, so a zero roll already means "straight ahead"
     * and every other bearing is a pure Z rotation of this one mesh.</p>
     */
    private void build() {
        float s = ARROW_SIZE;
        float[] positions = {
                0f, s, 0f,               // tip
                -s * 0.62f, -s * 0.45f, 0f, // back left
                s * 0.62f, -s * 0.45f, 0f,  // back right
        };
        int[] indices = {0, 1, 2};

        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        mesh.updateCounts();

        arrowGeo = new Geometry("EnemyArrowGeometry", mesh);
        // bounds must be recomputed or the renderer frustum-culls a moving marker
        arrowGeo.updateModelBound();

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(1f, 0.35f, 0.35f, 0.95f));
        // a flat triangle is single-sided; without this it vanishes when behind
        mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        // translucent so it never fully hides the terrain detail underneath
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mat.getAdditionalRenderState().setDepthWrite(false);
        arrowGeo.setMaterial(mat);

        arrowNode = new Node("EnemyArrow");
        // Billboard first, roll second: the control pins local +X to screen right and
        // local +Y to screen up, so the Z rotation applied in updateRotation is read
        // directly as a screen-space roll with no world-orientation guesswork.
        arrowNode.addControl(new BillboardControl());
        arrowNode.attachChild(arrowGeo);
        setArrowCulled(true);
        rootNode.attachChild(arrowNode);
    }
}