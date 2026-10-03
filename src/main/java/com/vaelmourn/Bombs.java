package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;

import java.util.ArrayList;
import java.util.List;

/**
 * Thrown bombs: a lobbed projectile that fuses, shows a growing transparent sphere as
 * a countdown, then bursts for area damage.
 *
 * <p>Not a {@link Weapons.WeaponDef}. The sword is the only weapon; the bomb is a
 * stackable item fired from the hotbar on its own key, so it keeps its fuse and damage
 * constants here rather than sharing the weapon table.</p>
 *
 * <p>The fuse is real time ticked by {@link #update}, and the sphere grows to
 * {@link #BLAST_RADIUS} as it fills so the blast area is readable before it fires.</p>
 */
public final class Bombs {

    public static final String ITEM_ID = "bomb";

    /** Seconds from throw to detonation. */
    public static final float FUSE = 3f;
    /** Damage dealt to every enemy inside the sphere on detonation. */
    public static final float BLAST_DAMAGE = 50f;
    /** Detonation radius, and the size the fuse sphere grows to. */
    public static final float BLAST_RADIUS = 3.2f;

    /** Shockwave expands from this fraction of the blast radius to all of it, then fades. */
    private static final float SHOCKWAVE_START_FRACTION = 0.25f;
    private static final float SHOCKWAVE_FADE = 0.6f;
    private static final float FUSE_SPHERE_START = 0.5f;
    /** Gentle arc so the bomb lands a little ahead of the player's feet. */
    private static final float THROW_SPEED = 17f;
    private static final float GRAVITY = 22f;
    private static final float BOUNCE_DAMPING = 0.32f;
    private static final float REST_Y = 0.35f;
    private static final float AIR_DRAG = 0.82f;
    /** The .glb was authored oversized; halve it so the mesh sits inside the fuse sphere. */
    private static final float MODEL_SCALE = 0.5f;
    private static final int MAX_BOMBS = 8;

    private static final ColorRGBA FUSE_COLOR = new ColorRGBA(1f, 0.62f, 0.15f, 0.45f);
    private static final ColorRGBA BLAST_COLOR = new ColorRGBA(1f, 0.78f, 0.35f, 0.85f);

    private final AssetManager assetManager;
    private final Node worldRoot;
    private final List<Bomb> live = new ArrayList<>();

    public Bombs(AssetManager assetManager, Node worldRoot) {
        this.assetManager = assetManager;
        this.worldRoot = worldRoot;
    }

    /**
     * Throws one bomb from the player's camera along its view direction.
     *
     * @return false if the live-bomb cap is already reached, so the caller keeps the item
     *         in the inventory rather than silently eating it
     */
    public boolean throwFrom(Vector3f origin, Vector3f direction) {
        if (live.size() >= MAX_BOMBS) return false;

        Vector3f dir = direction.clone();
        // clamp the upward component so this reads as a lob, not a straight shot
        dir.y = Math.max(0.18f, dir.y);
        dir.normalizeLocal();

        Bomb bomb = new Bomb(assetManager, origin.clone(), dir.mult(THROW_SPEED));
        worldRoot.attachChild(bomb.node);
        live.add(bomb);
        return true;
    }

    public interface GroundSampler {
        float groundHeightAt(float x, float z);
    }

    /**
     * Ages every fuse, detonating any that run out, then lets the blast visuals play
     * out before the node is torn down.
     */
    public void update(float tpf, List<EnemyController> enemies, CombatEffects effects,
                       GroundSampler ground) {
        for (int i = live.size() - 1; i >= 0; i--) {
            Bomb b = live.get(i);
            b.age += tpf;

            if (b.blown) {
                // damage already dealt; just let the shockwave finish shrinking
                b.updateShockwave(b.age - b.blownAt);
                if (b.age >= b.blownAt + SHOCKWAVE_FADE) {
                    b.node.removeFromParent();
                    live.remove(i);
                }
                continue;
            }

            float gy = ground != null ? ground.groundHeightAt(b.position.x, b.position.z) : 0f;
            b.integrate(tpf, gy);

            float fuseLeft = Math.max(0f, FUSE - b.age);
            float k = 1f - FastMath.clamp(fuseLeft / FUSE, 0f, 1f);
            b.setFuseSphere(FastMath.interpolateLinear(k, FUSE_SPHERE_START, BLAST_RADIUS), k);

            if (b.age >= FUSE) {
                b.blown = true;
                b.blownAt = b.age;
                damage(b, enemies, effects);
                b.detonate();
            }
        }
    }

    /** Backwards-compatible overload for existing callers. */
    public void update(float tpf, List<EnemyController> enemies, CombatEffects effects) {
        update(tpf, enemies, effects, null);
    }

    private void damage(Bomb b, List<EnemyController> enemies, CombatEffects effects) {
        Vector3f at = b.position.clone();
        if (enemies != null) {
            float r2 = BLAST_RADIUS * BLAST_RADIUS;
            for (EnemyController e : enemies) {
                if (e == null || e.isDead()) continue;
                // horizontal distance only: a bomb resting on the ground should not
                // reach an enemy perched high above it
                // BOTH clones are required. getPosition() hands back the node's live world
                // translation (jME's Transform.getTranslation() returns the field itself,
                // not a copy) and subtract() mutates its receiver, so without the left-hand
                // clone the first enemy examined had its node teleported to the blast offset.
                // The blast then compared the *mutated* position against the radius, which
                // is what stopped bombs from ever damaging anything.
                Vector3f to = e.getPosition().clone().subtract(at);
                to.y = 0f;
                if (to.lengthSquared() > r2) continue;

                e.takeDamage(BLAST_DAMAGE);
                if (to.lengthSquared() > 1e-4f) {
                    e.applyKnockback(to.normalizeLocal(), 26f);
                }
                if (effects != null) {
                    effects.onEnemyHit(e, BLAST_DAMAGE, e.isDead(), 0.14f);
                }
            }
        }
        if (effects != null) {
            // one shake for the blast itself rather than one per enemy caught in it
            effects.startShake(0.22f, 0.3f);
        }
        SoundManager.playBombExplosion();
    }

    public void cleanup() {
        for (Bomb b : live) b.node.removeFromParent();
        live.clear();
    }

    public int getLiveCount() {
        return live.size();
    }

    /** One in-flight bomb: model, fuse sphere and the post-burst shockwave. */
    private static final class Bomb {
        final Node node = new Node("bomb");
        final Vector3f position;
        final Vector3f velocity;
        float age = 0f;
        /** true once the fuse expired and damage was dealt; only the visuals remain. */
        boolean blown = false;
        float blownAt = 0f;

        private final AssetManager assetManager;
        private final Spatial model;
        private final Geometry fuseSphere;
        private final Material fuseMat;
        private Geometry shockwave;
        private Material shockMat;

        Bomb(AssetManager assetManager, Vector3f position, Vector3f velocity) {
            this.assetManager = assetManager;
            this.position = position;
            this.velocity = velocity;

            model = loadBombModel(assetManager);
            if (model != null) {
                model.setLocalScale(MODEL_SCALE);
                node.attachChild(model);
            }

            // depth test OFF, deliberately: the bomb rests ~0.35 above the ground while the sphere
            // grows to 3.2, so nearly all of the shell is underground. With depth testing
            // on, terrain hid all but a thin sliver of cap - invisible in combat stages,
            // barely visible in the flat bright Sanctuary. As a countdown indicator it
            // reads better as a full translucent bubble drawn over the world anyway.
            fuseMat = transparentMat(assetManager, FUSE_COLOR, false);
            // unit sphere, scaled per frame as the fuse burns down
            fuseSphere = new Geometry("fuseSphere", new Sphere(18, 12, 1f));
            fuseSphere.setMaterial(fuseMat);
            fuseSphere.setCullHint(Spatial.CullHint.Never);
            node.attachChild(fuseSphere);
        }

        private static Spatial loadBombModel(AssetManager assetManager) {
            try {
                return StageDecor.loadCached(assetManager, "Models/Weapons/bomb.glb").clone();
            } catch (Exception e) {
                // the bomb is still fully functional without its mesh, but say why
                // instead of failing silently
                System.err.println("[Bomb] Failed to load Models/Weapons/bomb.glb: "
                        + e.getMessage());
                return null;
            }
        }

        private static Material transparentMat(AssetManager assetManager, ColorRGBA color) {
            return transparentMat(assetManager, color, true);
        }

        private static Material transparentMat(AssetManager assetManager, ColorRGBA color,
                                               boolean depthTest) {
            // Unshaded, not Transparent.j3md: that def does not exist in jME 3.6, and an
            // unlit surface is what we want anyway - the fuse sphere has to stay readable
            // in a dark forest and through its own geometry
            Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setColor("Color", color);
            // a sphere is single-sided; without this the far half of the blast shell
            // renders over the near half and the fuse sphere looks solid, not hollow
            m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            m.getAdditionalRenderState().setDepthWrite(false);
            m.getAdditionalRenderState().setDepthTest(depthTest);
            return m;
        }

        void integrate(float tpf, float groundY) {
            velocity.y -= GRAVITY * tpf;
            position.addLocal(velocity.clone().multLocal(tpf));

            float rest = groundY + REST_Y;
            if (position.y <= rest) {
                position.y = rest;
                if (velocity.y < 0f) {
                    velocity.y = -velocity.y * BOUNCE_DAMPING;
                    velocity.x *= AIR_DRAG;
                    velocity.z *= AIR_DRAG;
                    // stop micro-bouncing once it is essentially settled
                    if (Math.abs(velocity.y) < 1.2f) velocity.y = 0f;
                }
            }

            node.setLocalTranslation(position);
            if (model != null) {
                model.rotate(140f * tpf, 90f * tpf, 0f);
            }
        }

        /** Backwards-compatible no-arg version: remains for any callers we missed. */
        void integrate(float tpf) {
            integrate(tpf, 0f);
        }

        void setFuseSphere(float radius, float fuseK) {
            fuseSphere.setLocalScale(radius);
            // blink faster as it runs out
            float blinkHz = fuseK > 0.66f ? 14f : 6f;
            // floor raised to 0.38 so the early fuse is still readable against dark
            // forest ground, where 0.30 all but vanished
            float a = 0.38f + 0.28f * FastMath.abs(FastMath.sin(fuseK * blinkHz));
            fuseMat.setColor("Color",
                    new ColorRGBA(FUSE_COLOR.r, FUSE_COLOR.g, FUSE_COLOR.b, a));
        }

        void updateShockwave(float sinceBlast) {
            if (shockwave == null) return;
            float k = FastMath.clamp(sinceBlast / SHOCKWAVE_FADE, 0f, 1f);
            // grow from a small flash out to the real blast radius, so what the player
            // sees matches the radius the damage pass actually used
            float scale = BLAST_RADIUS
                    * FastMath.interpolateLinear(k, SHOCKWAVE_START_FRACTION, 1f);
            shockwave.setLocalScale(scale);
            shockMat.setColor("Color", new ColorRGBA(BLAST_COLOR.r, BLAST_COLOR.g,
                    BLAST_COLOR.b, BLAST_COLOR.a * (1f - k)));
            if (k >= 1f) shockwave.setCullHint(Spatial.CullHint.Always);
        }

        void detonate() {
            fuseSphere.setCullHint(Spatial.CullHint.Always);
            spawnShockwave();
            spawnBurst();
        }

        private void spawnShockwave() {
            if (shockwave != null) return;
            shockMat = transparentMat(assetManager, BLAST_COLOR);
            shockwave = new Geometry("blastWave", new Sphere(20, 14, 1f));
            shockwave.setMaterial(shockMat);
            shockwave.setCullHint(Spatial.CullHint.Never);
            // updateShockwave() takes over the scale on the very next frame
            shockwave.setLocalScale(BLAST_RADIUS * SHOCKWAVE_START_FRACTION);
            node.attachChild(shockwave);
        }

        private void spawnBurst() {
            try {
                ParticleEmitter burst = new ParticleEmitter("BombBurst", ParticleMesh.Type.Triangle, 60);
                Material pm = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
                burst.setMaterial(pm);
                burst.setStartColor(new ColorRGBA(1f, 0.85f, 0.45f, 1f));
                burst.setEndColor(new ColorRGBA(1f, 0.4f, 0.1f, 0f));
                burst.setStartSize(0.35f);
                burst.setEndSize(0.05f);
                burst.setGravity(0f, -9f, 0f);
                burst.setLowLife(0.3f);
                burst.setHighLife(0.55f);
                burst.setParticlesPerSec(0f);
                burst.setEnabled(false);
                node.attachChild(burst);
                // enable then emit, so the sparks burn out instead of vanishing
                burst.setEnabled(true);
                burst.emitAllParticles();
            } catch (Exception e) {
                // particles are cosmetic; the damage pass has already run
            }
        }
    }
}