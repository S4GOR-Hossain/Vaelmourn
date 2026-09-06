package com.vaelmourn;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.jme3.anim.AnimComposer;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Quad;

/**
 * EnemyController manages a single enemy entity: AI, health, combat, death.
 */
public class EnemyController {

    private final Node node;
    private final BetterCharacterControl physics;
    private final AnimComposer animComposer;

    private float maxHealth;
    private float health;
    private float damage;
    private float moveSpeed;
    private float attackRange = 2.5f;
    private float detectionRange = 25f;
    private float attackCooldown = 1.5f;
    private float timeSinceLastAttack = 0f;

    // impulse applied by player hits; I let it decay out on its own
    private final Vector3f knockback = new Vector3f();
    private static final float KNOCKBACK_TIME = 0.35f;   // how long a single knockback push sticks around
    private float knockbackTimer = 0f;

    private boolean dead = false;
    private float deathTimer = 0f;
    private Material originalMaterial;
    private float damageFlashTimer = 0f;

    private int tier; // tiers: 1=easy (green), 2=medium (orange), 3=hard (red)

    // stashing these so I can spawn in-world effects later
    private final AssetManager assetManager;
    private final Node parentNode;

    // small health bar above the enemy, billboarding toward the camera
    private Geometry healthBarFill;
    private float healthBarBaseWidth = 1.2f;

    // quick burst of particles when the enemy takes a hit
    private final ParticleEmitter hitEmitter;
    private float emitterTimer = 0f;
    private static final float EMITTER_LIFETIME = 0.35f;

    public EnemyController(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState,
                          Vector3f spawnPos, int tier, int loopCount) {
        this.tier = tier;
        this.assetManager = assetManager;
        this.parentNode = parentNode;

        // baseline stats picked per tier
        float baseHealth = switch(tier) {
            case 1 -> 55f;
            case 2 -> 115f;
            case 3 -> 180f;
            default -> 60f;
        };

        float baseDamage = switch(tier) {
            case 1 -> 2f;
            case 2 -> 5f;
            case 3 -> 10f;
            default -> 2f;
        };

        // scale health and damage with loop count
        float loopScalar = (float) Math.pow(1.5, loopCount);
        this.maxHealth = baseHealth * loopScalar;
        this.health = maxHealth;
        this.damage = baseDamage * loopScalar;
        this.moveSpeed = 4f + (tier - 1) * 1.5f;

        // build the placeholder capsule visual
        node = new Node("Enemy_Tier" + tier);
        ColorRGBA color = switch(tier) {
            case 1 -> ColorRGBA.Green;
            case 2 -> ColorRGBA.Orange;
            case 3 -> ColorRGBA.Red;
            default -> ColorRGBA.Gray;
        };

        Cylinder capShape = new Cylinder(2, 16, 0.4f, 1.4f, true);
        Geometry capsule = new Geometry("EnemyCapsule", capShape);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        capsule.setMaterial(mat);
        // jME3's Cylinder runs along the Z axis, so it spawns lying flat. Rotate
        // it 90 degrees on X to stand the capsule up, matching the physics
        // capsule's vertical orientation.
        capsule.rotate(FastMath.HALF_PI, 0f, 0f);
        // Minie's BetterCharacterControl anchors the model's origin at the feet
        // (the physics capsule base), but our cylinder is centered on its origin —
        // lifted it by half its height so the lower half doesn't sink into the ground.
        capsule.setLocalTranslation(0f, 0.7f, 0f);
        node.attachChild(capsule);
        this.originalMaterial = mat;

        // health bar hovering above the capsule, always turned toward the camera
        Node healthBarNode = new Node("HealthBar");
        healthBarNode.setLocalTranslation(0f, 2.0f, 0f);
        healthBarNode.addControl(new BillboardControl());

        Geometry bg = new Geometry("HPBarBG", new Quad(healthBarBaseWidth, 0.16f));
        Material bgMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        bgMat.setColor("Color", new ColorRGBA(0.05f, 0.05f, 0.06f, 0.9f));
        bg.setMaterial(bgMat);
        bg.setLocalTranslation(-healthBarBaseWidth / 2f, -0.08f, 0f);
        healthBarNode.attachChild(bg);

        healthBarFill = new Geometry("HPBarFill", new Quad(healthBarBaseWidth, 0.12f));
        Material fillMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        fillMat.setColor("Color", ColorRGBA.Red);
        healthBarFill.setMaterial(fillMat);
        healthBarFill.setLocalTranslation(-healthBarBaseWidth / 2f, -0.06f, 0.01f);
        healthBarNode.attachChild(healthBarFill);

        node.attachChild(healthBarNode);

        // hits spawn this little burst right at the enemy's position
        // glow texture is made in code so no external asset is needed
        hitEmitter = new ParticleEmitter("HitSpark", ParticleMesh.Type.Triangle, 24);
        Material pm = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        Texture2D glowTex = null;
        try {
            glowTex = makeSoftGlowTexture();
        } catch (RuntimeException ex) {
            System.err.println("EnemyController: could not build glow texture, particles untextured: " + ex.getMessage());
        }
        if (glowTex != null) {
            pm.setTexture("Texture", glowTex);
        }
        hitEmitter.setMaterial(pm);
        hitEmitter.setStartColor(ColorRGBA.White);
        hitEmitter.setEndColor(new ColorRGBA(1f, 0.6f, 0.1f, 1f));
        hitEmitter.setStartSize(0.25f);
        hitEmitter.setEndSize(0.05f);
        hitEmitter.setGravity(0f, 8f, 0f);
        hitEmitter.setLowLife(0.15f);
        hitEmitter.setHighLife(0.35f);
        hitEmitter.setParticlesPerSec(0f);
        hitEmitter.setLocalTranslation(spawnPos.add(0f, 0.7f, 0f));
        hitEmitter.setEnabled(false);
        parentNode.attachChild(hitEmitter);

        node.setLocalTranslation(spawnPos);
        parentNode.attachChild(node);

        // physics rig
        physics = new BetterCharacterControl(0.4f, 1.4f, 0.8f);
        physics.setGravity(new Vector3f(0, -30f, 0));
        physics.warp(spawnPos);
        node.addControl(physics);
        bulletAppState.getPhysicsSpace().add(physics);

        // animation setup — still a placeholder
        animComposer = findAnimComposer(node);
    }

    /**
     * Update AI and movement each frame.
     */
    public void update(float tpf, Vector3f playerPos, PlayerStats playerStats) {
        if (dead) {
            deathTimer -= tpf;
            return;
        }

        timeSinceLastAttack += tpf;

        if (damageFlashTimer > 0f) {
            damageFlashTimer -= tpf;
        }

        // keep the health bar tracking current HP
        if (healthBarFill != null) {
            float ratio = maxHealth <= 0f ? 0f : FastMath.clamp(health / maxHealth, 0f, 1f);
            healthBarFill.setLocalScale(ratio, 1f, 1f);
        }

        // the hit burst is short-lived: shut it off once its timer runs out
        if (emitterTimer > 0f) {
            emitterTimer -= tpf;
            if (emitterTimer <= 0f) {
                hitEmitter.killAllParticles();
                hitEmitter.setEnabled(false);
            }
        }

        Vector3f enemyPos = node.getWorldTranslation();
        float distToPlayer = enemyPos.distance(playerPos);

        // while a knockback is active, scale down the enemy's own movement so
        // it can't just walk straight through the push and cancel it
        float kbMag = knockback.length();
        float kbLinger = FastMath.clamp(knockbackTimer / KNOCKBACK_TIME, 0f, 1f);

        Vector3f desiredMove = Vector3f.ZERO;
        if (distToPlayer < detectionRange) {
            // chase the player
            Vector3f direction = playerPos.subtract(enemyPos).normalizeLocal();

            if (distToPlayer < attackRange) {
                // in attack range — stop and swing
                if (timeSinceLastAttack >= attackCooldown) {
                    timeSinceLastAttack = 0f;
                    playAnim("Attack");
                    // land a hit on the player
                    if (playerStats != null) {
                        playerStats.damage(damage);
                    }
                }
            } else {
                // chase the player, but slower while being knocked back
                desiredMove = direction.mult(moveSpeed * (1f - kbLinger * 0.85f));
                playAnim("Walk");
            }
        } else {
            // out of range, so just idle
            playAnim("Idle");
        }

        physics.setWalkDirection(desiredMove.add(knockback));

        // knockback fades out over time, both in X/Z and the vertical hop
        if (kbMag > 0f) {
            knockbackTimer -= tpf;
            float scale = FastMath.clamp(knockbackTimer / KNOCKBACK_TIME, 0f, 1f);
            knockback.multLocal(scale);
            // flush any tiny leftover values so they don't linger
            if (knockback.lengthSquared() < 0.05f) knockback.set(0f, 0f, 0f);
        }
    }

    /**
     * Take damage. Flashes red briefly.
     */
    public void takeDamage(float amount) {
        if (dead) return;

        health -= amount;
        damageFlashTimer = 0.15f;

        if (originalMaterial != null) {
            originalMaterial.setColor("Color", ColorRGBA.Red);
        }

        // quick particle pop where the hit landed
        hitEmitter.setLocalTranslation(node.getWorldTranslation().add(0f, 0.7f, 0f));
        hitEmitter.setEnabled(true);
        hitEmitter.emitAllParticles();
        emitterTimer = EMITTER_LIFETIME;

        if (health <= 0f) {
            die();
        }
    }

    /**
     * Applies a knockback impulse directed away from the attacker (horizontal X/Z)
     * plus a small vertical hop so the hit is visually readable. The impulse fades
     * out over a short time (see {@link #KNOCKBACK_TIME}).
     *
     * @param awayDir normalized horizontal direction pushing the enemy away
     * @param force   magnitude of the push
     */
    public void applyKnockback(Vector3f awayDir, float force) {
        if (dead) return;
        knockback.set(awayDir.x * force, force * 0.5f, awayDir.z * force);
        knockbackTimer = KNOCKBACK_TIME;
    }

    public void applyKnockback(Vector3f awayDir) {
        applyKnockback(awayDir, 7f);
    }

    private void die() {
        dead = true;
        deathTimer = 0.5f;
        physics.setWalkDirection(Vector3f.ZERO);
        playAnim("Death");

        // shrink on death — cheap stand-in for a real death animation
        node.scale(0.5f);
    }

    /**
     * Called by StageManager to clean up physics and detach.
     */
    public void cleanup(BulletAppState bulletAppState) {
        bulletAppState.getPhysicsSpace().remove(physics);
        hitEmitter.removeFromParent();
        node.removeFromParent();
    }

    private void playAnim(String clip) {
        if (animComposer != null && clip != null
                && animComposer.getAnimClipsNames().contains(clip)) {
            animComposer.setCurrentAction(clip);
        }
    }

    private AnimComposer findAnimComposer(Spatial spatial) {
        AnimComposer composer = spatial.getControl(AnimComposer.class);
        if (composer != null) return composer;

        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                AnimComposer result = findAnimComposer(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * Builds a small soft circular white glow texture in code, so the hit
     * particle effect needs no external asset file (which would throw on load).
     */
    private static Texture2D makeSoftGlowTexture() {
        int size = 64;
        int center = size / 2;
        ByteBuffer data = ByteBuffer.allocateDirect(size * size * 4)
                .order(ByteOrder.nativeOrder());
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - center) / (float) center;
                float dy = (y - center) / (float) center;
                float dist = FastMath.sqrt(dx * dx + dy * dy);
                float alpha = FastMath.clamp(1f - dist, 0f, 1f);
                alpha = alpha * alpha; // squash the falloff into a soft glow
                data.put((byte) 255);
                data.put((byte) 255);
                data.put((byte) 255);
                data.put((byte) (int) (alpha * 255f));
            }
        }
        data.flip();
        Image image = new Image(Image.Format.RGBA8, size, size, data);
        Texture2D texture = new Texture2D(image);
        texture.setWrap(Texture.WrapMode.Clamp);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        return texture;
    }

    // the plain getters
    public boolean isDead() { return dead; }
    public Vector3f getPosition() { return node.getWorldTranslation(); }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public float getAttackDamage() { return damage; }
    public Node getNode() { return node; }
    public BetterCharacterControl getPhysics() { return physics; }
}
