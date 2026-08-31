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

    // Knockback impulse applied by player attacks; fades out over time.
    private final Vector3f knockback = new Vector3f();
    private static final float KNOCKBACK_TIME = 0.35f;   // how long a push lasts
    private float knockbackTimer = 0f;

    private boolean dead = false;
    private float deathTimer = 0f;
    private Material originalMaterial;
    private float damageFlashTimer = 0f;

    private int tier; // 1=easy (green), 2=medium (orange), 3=hard (red)

    // Stored context for spawning in-world effects.
    private final AssetManager assetManager;
    private final Node parentNode;

    // Small health bar above the enemy (world-space, billboarded toward the camera).
    private Geometry healthBarFill;
    private float healthBarBaseWidth = 1.2f;

    // Short-lived hit particle burst.
    private final ParticleEmitter hitEmitter;
    private float emitterTimer = 0f;
    private static final float EMITTER_LIFETIME = 0.35f;

    public EnemyController(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState,
                          Vector3f spawnPos, int tier, int loopCount) {
        this.tier = tier;
        this.assetManager = assetManager;
        this.parentNode = parentNode;

        // Base stats per tier
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

        // Scale by loop count
        float loopScalar = (float) Math.pow(1.5, loopCount);
        this.maxHealth = baseHealth * loopScalar;
        this.health = maxHealth;
        this.damage = baseDamage * loopScalar;
        this.moveSpeed = 4f + (tier - 1) * 1.5f;

        // Create visual (capsule placeholder)
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
        // jME3's Cylinder runs its height along the Z axis, so it lies flat by
        // default. Rotate 90 degrees about X to stand the capsule upright, matching
        // the physics capsule's vertical orientation.
        capsule.rotate(FastMath.HALF_PI, 0f, 0f);
        node.attachChild(capsule);
        this.originalMaterial = mat;

        // Small health bar floating above the capsule, billboarded toward the camera.
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

        // Hit particle burst, spawned at the enemy's position when damaged.
        // Uses a procedurally generated soft glow texture so no external asset is required.
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

        // Physics
        physics = new BetterCharacterControl(0.4f, 1.4f, 0.8f);
        physics.setGravity(new Vector3f(0, -30f, 0));
        physics.warp(spawnPos);
        node.addControl(physics);
        bulletAppState.getPhysicsSpace().add(physics);

        // Animation (placeholder)
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

        // Keep the floating health bar in sync with current health.
        if (healthBarFill != null) {
            float ratio = maxHealth <= 0f ? 0f : FastMath.clamp(health / maxHealth, 0f, 1f);
            healthBarFill.setLocalScale(ratio, 1f, 1f);
        }

        // Short-lived hit particle burst: kill it once its lifetime elapses.
        if (emitterTimer > 0f) {
            emitterTimer -= tpf;
            if (emitterTimer <= 0f) {
                hitEmitter.killAllParticles();
                hitEmitter.setEnabled(false);
            }
        }

        Vector3f enemyPos = node.getWorldTranslation();
        float distToPlayer = enemyPos.distance(playerPos);

        // While a knockback is active, dampen the AI's own movement so the push
        // isn't instantly cancelled by the enemy walking back toward the player.
        float kbMag = knockback.length();
        float kbLinger = FastMath.clamp(knockbackTimer / KNOCKBACK_TIME, 0f, 1f);

        Vector3f desiredMove = Vector3f.ZERO;
        if (distToPlayer < detectionRange) {
            // Move toward player
            Vector3f direction = playerPos.subtract(enemyPos).normalizeLocal();

            if (distToPlayer < attackRange) {
                // In attack range — stop and attack
                if (timeSinceLastAttack >= attackCooldown) {
                    timeSinceLastAttack = 0f;
                    playAnim("Attack");
                    // Apply a small amount of damage to the player.
                    if (playerStats != null) {
                        playerStats.damage(damage);
                    }
                }
            } else {
                // Move toward player (scaled down while being knocked back).
                desiredMove = direction.mult(moveSpeed * (1f - kbLinger * 0.85f));
                playAnim("Walk");
            }
        } else {
            // Idle
            playAnim("Idle");
        }

        physics.setWalkDirection(desiredMove.add(knockback));

        // Decay the knockback over time (horizontal + vertical).
        if (kbMag > 0f) {
            knockbackTimer -= tpf;
            float scale = FastMath.clamp(knockbackTimer / KNOCKBACK_TIME, 0f, 1f);
            knockback.multLocal(scale);
            // Kill tiny leftover values.
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

        // Small particle burst at the hit point.
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

        // Shrink animation (optional)
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
                alpha = alpha * alpha; // sharpen the falloff into a soft glow
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

    // Getters
    public boolean isDead() { return dead; }
    public Vector3f getPosition() { return node.getWorldTranslation(); }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public float getAttackDamage() { return damage; }
    public Node getNode() { return node; }
    public BetterCharacterControl getPhysics() { return physics; }
}
