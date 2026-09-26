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
    private static final float KNOCKBACK_DURATION = 0.35f;   // how long a single knockback push sticks around
    private static final float KNOCKBACK_DEFAULT_FORCE = 7f; // push used unless the caller passes a bigger one
    private float knockbackTimer = 0f;

    private boolean dead = false;
    private float deathTimer = 0f;
    private boolean removable = false;   // effect finished, safe for the stage to remove us
    private static final float DEATH_EFFECT_DURATION = 0.6f;  // how long the death pop plays before removal
    private static final float HIT_FLASH_DURATION = 0.15f;    // white blink after taking a hit
    // how long the "about to attack" telegraph lasts before the damage lands
    private static final float ATTACK_TELEGRAPH_DURATION = 0.4f;
    private boolean attacking = false;
    private float attackWindupTimer = 0f;

    private Material capsuleMat;
    private ColorRGBA defaultColor;
    private final ColorRGBA matScratch = new ColorRGBA();
    private ColorRGBA lastMatColor = new ColorRGBA();
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
    private final ParticleEmitter deathEmitter;

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
        this.capsuleMat = mat;
        this.defaultColor = color;

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

        // bigger, brighter burst reserved for when the enemy actually dies
        deathEmitter = new ParticleEmitter("DeathBurst", ParticleMesh.Type.Triangle, 40);
        Material dm = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        if (glowTex != null) {
            dm.setTexture("Texture", glowTex);
        }
        deathEmitter.setMaterial(dm);
        deathEmitter.setStartColor(ColorRGBA.Orange);
        deathEmitter.setEndColor(ColorRGBA.Red);
        deathEmitter.setStartSize(0.5f);
        deathEmitter.setEndSize(0.08f);
        deathEmitter.setGravity(0f, 4f, 0f);
        deathEmitter.setLowLife(0.3f);
        deathEmitter.setHighLife(0.7f);
        deathEmitter.setParticlesPerSec(0f);
        deathEmitter.setLocalTranslation(spawnPos.add(0f, 0.7f, 0f));
        deathEmitter.setEnabled(false);
        parentNode.attachChild(deathEmitter);

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
            // death pop plays out here instead of vanishing instantly
            deathTimer -= tpf;
            float k = FastMath.clamp((DEATH_EFFECT_DURATION - deathTimer) / DEATH_EFFECT_DURATION, 0f, 1f);
            node.setLocalScale(1f - 0.8f * k);
            if (capsuleMat != null) {
                matScratch.set(
                        FastMath.clamp(defaultColor.r + (1f - defaultColor.r) * k, 0f, 1f),
                        FastMath.clamp(defaultColor.g * (1f - k), 0f, 1f),
                        FastMath.clamp(defaultColor.b * (1f - k), 0f, 1f),
                        1f);
                if (!matScratch.equals(lastMatColor)) {
                    lastMatColor.set(matScratch);
                    capsuleMat.setColor("Color", matScratch);
                }
            }
            if (deathTimer <= 0f) {
                removable = true;
            }
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
        float kbLinger = FastMath.clamp(knockbackTimer / KNOCKBACK_DURATION, 0f, 1f);

        Vector3f desiredMove = Vector3f.ZERO;
        if (attacking) {
            // telegraphing: planted and pulsing red, hit lands when the timer hits zero
            attackWindupTimer -= tpf;
            physics.setWalkDirection(knockback);
            if (attackWindupTimer <= 0f) {
                attacking = false;
                if (playerStats != null && enemyPos.distance(playerPos) <= attackRange * 1.1f) {
                    playerStats.damage(damage);
                }
            }
        } else if (distToPlayer < detectionRange) {
            // chase the player
            Vector3f direction = playerPos.subtract(enemyPos).normalizeLocal();

            if (distToPlayer < attackRange) {
                // in range — start the swing telegraph instead of hitting instantly
                if (timeSinceLastAttack >= attackCooldown) {
                    timeSinceLastAttack = 0f;
                    attacking = true;
                    attackWindupTimer = ATTACK_TELEGRAPH_DURATION;
                    playAnim("Attack");
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
            float scale = FastMath.clamp(knockbackTimer / KNOCKBACK_DURATION, 0f, 1f);
            knockback.multLocal(scale);
            // flush any tiny leftover values so they don't linger
            if (knockback.lengthSquared() < 0.05f) knockback.set(0f, 0f, 0f);
        }

        refreshCapsuleColor();
    }

    /**
     * One material for the whole capsule — its color gets re-derived from the
     * current state every frame. That both gives the telegraph its pulse and
     * guarantees the hit flash always returns to the tier color (the old code
     * set it red once and never restored it).
     */
    private void refreshCapsuleColor() {
        if (capsuleMat == null) return;
        ColorRGBA c;
        if (damageFlashTimer > 0f) {
            c = ColorRGBA.White;
        } else if (attacking) {
            // warning pulse — heartbeat toward hot red while the windup runs
            float p = (FastMath.sin(attackWindupTimer * FastMath.TWO_PI * 3f) + 1f) * 0.5f;
            matScratch.set(1f, 0.25f + 0.3f * p, 0.2f + 0.3f * p, 1f);
            c = matScratch;
        } else {
            c = defaultColor;
        }
        // only poke the material when the color actually changed, so idle
        // enemies don't re-upload the same uniform every frame
        if (!c.equals(lastMatColor)) {
            lastMatColor.set(c);
            capsuleMat.setColor("Color", c);
        }
    }

    /**
     * Take damage. Flashes red briefly.
     */
    public void takeDamage(float amount) {
        if (dead) return;

        health -= amount;
        damageFlashTimer = HIT_FLASH_DURATION;

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
     * out over a short time (see {@link #KNOCKBACK_DURATION}).
     *
     * @param awayDir normalized horizontal direction pushing the enemy away
     * @param force   magnitude of the push
     */
    public void applyKnockback(Vector3f awayDir, float force) {
        if (dead) return;
        knockback.set(awayDir.x * force, force * 0.5f, awayDir.z * force);
        knockbackTimer = KNOCKBACK_DURATION;
    }

    public void applyKnockback(Vector3f awayDir) {
        applyKnockback(awayDir, KNOCKBACK_DEFAULT_FORCE);
    }

    private void die() {
        dead = true;
        deathTimer = DEATH_EFFECT_DURATION;
        physics.setWalkDirection(Vector3f.ZERO);
        playAnim("Death");

        // big burst for the kill — chunkier than the little hit spark. The
        // stage won't remove us until the timer above runs out (canRemove()).
        deathEmitter.setLocalTranslation(node.getWorldTranslation().add(0f, 0.7f, 0f));
        deathEmitter.setEnabled(true);
        deathEmitter.emitAllParticles();
    }

    /**
     * Called by StageManager to clean up physics and detach.
     */
    public void cleanup(BulletAppState bulletAppState) {
        bulletAppState.getPhysicsSpace().remove(physics);
        hitEmitter.removeFromParent();
        deathEmitter.removeFromParent();
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
    public boolean canRemove() { return removable; }
    public Vector3f getPosition() { return node.getWorldTranslation(); }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public float getAttackDamage() { return damage; }
    public Node getNode() { return node; }
    public BetterCharacterControl getPhysics() { return physics; }
}
