package com.vaelmourn;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.SkinningControl;
import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
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
     * Manages a single enemy: AI, health, combat, death. Renders a real animated
     * model scaled to a per-model envelope when the stage supplies a path, else
     * the placeholder capsule.
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

    private final Vector3f knockback = new Vector3f();
    private static final float KNOCKBACK_DURATION = 0.35f;
    private static final float KNOCKBACK_DEFAULT_FORCE = 7f;
    private float knockbackTimer = 0f;

    private boolean dead = false;
    private float deathTimer = 0f;
    private boolean removable = false;   // effect finished, safe for the stage to remove us
    private static final float DEATH_EFFECT_DURATION = 0.6f;
    private static final float HIT_FLASH_DURATION = 0.15f;
    private static final float ATTACK_TELEGRAPH_DURATION = 0.4f;
    private boolean attacking = false;
    private float attackWindupTimer = 0f;

    private Material capsuleMat;
    private ColorRGBA defaultColor;
    private final ColorRGBA matScratch = new ColorRGBA();
    private final ColorRGBA lastMatColor = new ColorRGBA();
    private float damageFlashTimer = 0f;

    private int tier; // tiers: 1=easy (green), 2=medium (orange), 3=hard (red), 4=elite (dark red)

    // roguelike-stage knobs set by the spawning stage, never by the enemy itself
    private float variantScale = 1f;       // per-stage difficulty multiplier (1.0x -> 2x+)
    private float moveSpeedMultiplier = 1f; // e.g. Jungle 3 makes enemies 2x faster
    private float slowOnHit = 0f;          // seconds of player-slow per hit (Frozen 3)
    private boolean boss = false;          // boss arenas spawn exactly one of these

    private final AssetManager assetManager;
    private final Node parentNode;

    private final String modelPath; // null = capsule placeholder
    private final String bossId; // e.g. "tree_warden" from the boss model path, else null
    private Spatial bodyModel = null;

    private String idleClip;
    private String walkClip;
    private String runClip;
    private String attackClip;
    private String hitClip;
    private String deathClip;

    // the last animation category actually started. Guards clip switches so a
    // still-moving enemy doesn't re-trigger setCurrentAction every frame —
    // doing that restarts the stride and froze the model in place (the old
    // "ice-skating" slide while the enemy glided across the floor).
    private String currentAnimState = null;
    private float hitAnimTimer = 0f;
    private static final float HIT_ANIM_HOLD_DURATION = 0.28f;
    // locomotion clips speed-scaled in proportion to the enemy's real pace so
    // the stride matches the ground speed instead of moonwalking over it
    private static final float MOVE_ANIM_SPEED_PER_UNIT = 0.24f;
    private static final float MOVE_ANIM_MIN_SPEED = 0.8f;
    private static final float MOVE_ANIM_MAX_SPEED = 2.0f;

    private final List<Geometry> bodyGeoms = new ArrayList<>();
    private final List<Material> bodyMats = new ArrayList<>();
    private boolean flashActive = false;
    private Material flashMat;

    private Geometry healthBarFill;
    private float healthBarBaseWidth = 1.2f;

    private final ParticleEmitter hitEmitter;
    private float emitterTimer = 0f;
    private static final float EMITTER_LIFETIME = 0.35f;
    private final ParticleEmitter deathEmitter;

    public EnemyController(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState,
                          Vector3f spawnPos, int tier, int loopCount) {
        this(assetManager, parentNode, bulletAppState, spawnPos, tier, loopCount, 1f, false, null);
    }

    public EnemyController(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState,
                          Vector3f spawnPos, int tier, int loopCount, float variantScale) {
        this(assetManager, parentNode, bulletAppState, spawnPos, tier, loopCount, variantScale, false, null);
    }

    public EnemyController(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState,
                          Vector3f spawnPos, int tier, int loopCount, float variantScale, boolean boss) {
        this(assetManager, parentNode, bulletAppState, spawnPos, tier, loopCount, variantScale, boss, null);
    }

    public EnemyController(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState,
                          Vector3f spawnPos, int tier, int loopCount, float variantScale, boolean boss,
                          String modelPath) {
        this.tier = tier;
        this.assetManager = assetManager;
        this.parentNode = parentNode;
        this.variantScale = variantScale;
        this.boss = boss;
        this.modelPath = modelPath;
        this.bossId = bossIdFrom(modelPath);
        if (bossId != null) {
            SoundManager.registerBoss(bossId);
        }

        float baseHealth;
        float baseDamage;
        if (boss) {
            baseHealth = 900f;
            baseDamage = 14f;
        } else {
            baseHealth = switch(tier) {
                case 1 -> 55f;
                case 2 -> 115f;
                case 3 -> 180f;
                case 4 -> 260f;
                default -> 60f;
            };

            baseDamage = switch(tier) {
                case 1 -> 2f;
                case 2 -> 5f;
                case 3 -> 10f;
                case 4 -> 16f;
                default -> 2f;
            };
        }

        baseHealth *= variantScale;
        baseDamage *= variantScale;

        float loopScalar = (float) Math.pow(1.5, loopCount);
        this.maxHealth = baseHealth * loopScalar;
        this.health = maxHealth;
        this.damage = baseDamage * loopScalar;
        this.moveSpeed = boss ? 3.2f : 4f + (tier - 1) * 1.5f;

        node = new Node(boss ? "Boss" : "Enemy_Tier" + tier);

        if (modelPath != null) {
            try {
                Spatial body = assetManager.loadModel(modelPath).clone();
                // Hardware (GPU) skinning crashes the AMD OpenGL driver
                // (EXCEPTION_ACCESS_VIOLATION in glBufferData) — CPU skinning.
                disableHardwareSkinning(body);
                Profile profile = profileFor(modelPath, boss);
                if (profile != null) {
                    body.rotate(0f, profile.yawDeg * FastMath.DEG_TO_RAD, 0f);
                }
                node.attachChild(body);
                bodyModel = body;
                if (profile != null) {
                    scaleToEnvelope(body, profile);
                }
                collectBodyMaterials();
            } catch (RuntimeException ex) {
                System.err.println("EnemyController: could not load " + modelPath
                        + " (" + ex + "); using capsule placeholder.");
                bodyModel = null;
            }
        }

        if (bodyModel == null) {
            buildPlaceholderBody();
        }

        float visualHeight = measureVisualHeight();

        animComposer = findAnimComposer(node);
        if (animComposer != null) {
            Set<String> names = animComposer.getAnimClipsNames();
            idleClip = resolveClip(names, "Idle", "Flying_Idle");
            walkClip = resolveClip(names, "Walk");
            runClip = resolveClip(names, "Run", "Fast_Flying");
            attackClip = resolveClip(names, "Attack", "Sword", "Bite_Front", "Headbutt", "Punch", "Weapon");
            hitClip = resolveClip(names, "HitRecieve", "HitReact", "Hit");
            deathClip = resolveClip(names, "Death");
            if (idleClip != null) {
                animComposer.setCurrentAction(idleClip);
            }
        }

        this.healthBarBaseWidth = boss ? 2.6f : 1.2f;
        Node healthBarNode = new Node("HealthBar");
        healthBarNode.setLocalTranslation(0f, visualHeight + (boss ? 0.55f : 0.35f), 0f);
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

        float physRadius = boss ? 0.85f : 0.45f;
        float physHeight = Math.max(1.15f, Math.min(visualHeight, boss ? 4.5f : 2.2f));
        physics = new BetterCharacterControl(physRadius, physHeight, 0.8f);
        physics.setGravity(new Vector3f(0, -30f, 0));
        physics.warp(spawnPos);
        node.addControl(physics);
        bulletAppState.getPhysicsSpace().add(physics);
    }

    public void update(float tpf, Vector3f playerPos, PlayerStats playerStats) {
        if (dead) {
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

        if (hitAnimTimer > 0f) {
            hitAnimTimer -= tpf;
            if (hitAnimTimer <= 0f) {
                currentAnimState = null; // force the pose to re-resolve next frame
            }
        }

        if (damageFlashTimer > 0f) {
            damageFlashTimer -= tpf;
        }

        if (healthBarFill != null) {
            float ratio = maxHealth <= 0f ? 0f : FastMath.clamp(health / maxHealth, 0f, 1f);
            healthBarFill.setLocalScale(ratio, 1f, 1f);
        }

        if (emitterTimer > 0f) {
            emitterTimer -= tpf;
            if (emitterTimer <= 0f) {
                hitEmitter.killAllParticles();
                hitEmitter.setEnabled(false);
            }
        }

        Vector3f enemyPos = node.getWorldTranslation();
        // horizontal distance only — a 3D check made everyone drop aggro mid-jump
        Vector3f toPlayer = new Vector3f(playerPos.x - enemyPos.x, 0f, playerPos.z - enemyPos.z);
        float distToPlayer = toPlayer.length();

        if (distToPlayer < detectionRange) {
            Vector3f dir = toPlayer;
            if (dir.lengthSquared() > 1e-4f) {
                physics.setViewDirection(dir.normalizeLocal());
            }
        }

        // while a knockback is active, scale down the enemy's own movement so
        // it can't just walk straight through the push and cancel it
        float kbMag = knockback.length();
        float kbLinger = FastMath.clamp(knockbackTimer / KNOCKBACK_DURATION, 0f, 1f);

        Vector3f desiredMove = Vector3f.ZERO;
        if (attacking) {
            attackWindupTimer -= tpf;
            physics.setWalkDirection(knockback);
            if (attackWindupTimer <= 0f) {
                attacking = false;
                if (playerStats != null && distToPlayer <= attackRange * 1.1f) {
                    playerStats.damage(damage);
                    if (bossId != null) SoundManager.playBossDamageDeal(bossId);
                    // Frozen Depths 3 hits chill the player briefly (non-stacking)
                    if (slowOnHit > 0f) {
                        playerStats.applySlow(0.35f, slowOnHit);
                    }
                }
            }
        } else if (distToPlayer < detectionRange) {
            // chase the player, staying flat — a jumping player doesn't drag us skyward
            Vector3f direction = toPlayer;
            if (direction.lengthSquared() > 1e-4f) {
                direction.normalizeLocal();
            }

            if (distToPlayer < attackRange) {
                if (timeSinceLastAttack >= attackCooldown) {
                    timeSinceLastAttack = 0f;
                    attacking = true;
                    attackWindupTimer = ATTACK_TELEGRAPH_DURATION;
                    playAnim("Attack");
                    if (bossId != null) {
                        SoundManager.playBossAttack(bossId);
                    } else {
                        SoundManager.playEnemyAttack();
                    }
                }
            } else {
                // chase the player, but slower while being knocked back
                desiredMove = direction.mult(moveSpeed * moveSpeedMultiplier * (1f - kbLinger * 0.85f));
            }
        }

        if (!attacking && hitAnimTimer <= 0f) {
            boolean moving = desiredMove.lengthSquared() > 1e-3f;
            playAnim(moving ? "Move" : "Idle");
        }

        physics.setWalkDirection(desiredMove.add(knockback));

        if (kbMag > 0f) {
            knockbackTimer -= tpf;
            float scale = FastMath.clamp(knockbackTimer / KNOCKBACK_DURATION, 0f, 1f);
            knockback.multLocal(scale);
            if (knockback.lengthSquared() < 0.05f) knockback.set(0f, 0f, 0f);
        }

        refreshHitFlash();
    }

    private void refreshHitFlash() {
        if (!bodyGeoms.isEmpty()) {
            ColorRGBA c = bodyFlashColor();
            if (c == null) {
                if (flashActive) {
                    flashActive = false;
                    for (int i = 0; i < bodyGeoms.size(); i++) {
                        bodyGeoms.get(i).setMaterial(bodyMats.get(i));
                    }
                }
            } else {
                if (flashMat == null) {
                    flashMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                }
                if (!c.equals(lastMatColor)) {
                    lastMatColor.set(c);
                    flashMat.setColor("Color", c);
                }
                if (!flashActive) {
                    flashActive = true;
                    for (Geometry g : bodyGeoms) {
                        g.setMaterial(flashMat);
                    }
                }
            }
            return;
        }
        refreshCapsuleColor();
    }

    private ColorRGBA bodyFlashColor() {
        if (damageFlashTimer > 0f) return ColorRGBA.White;
        if (attacking) {
            // warning pulse — heartbeat toward hot red while the windup runs
            float p = (FastMath.sin(attackWindupTimer * FastMath.TWO_PI * 3f) + 1f) * 0.5f;
            matScratch.set(1f, 0.25f + 0.3f * p, 0.2f + 0.3f * p, 1f);
            return matScratch;
        }
        return null;
    }

    private void refreshCapsuleColor() {
        if (capsuleMat == null) return;
        ColorRGBA c;
        if (damageFlashTimer > 0f) {
            c = ColorRGBA.White;
        } else         if (attacking) {
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

    public void takeDamage(float amount) {
        if (dead) return;

        health -= amount;
        damageFlashTimer = HIT_FLASH_DURATION;
        hitAnimTimer = HIT_ANIM_HOLD_DURATION;
        playAnim("Hit");

        hitEmitter.setLocalTranslation(node.getWorldTranslation().add(0f, 0.7f, 0f));
        hitEmitter.setEnabled(true);
        hitEmitter.emitAllParticles();
        emitterTimer = EMITTER_LIFETIME;
        if (bossId != null) SoundManager.playBossHurt(bossId);

        if (health <= 0f) {
            die();
        }
    }

    /** Impulse away from the attacker plus a small vertical hop; fades over KNOCKBACK_DURATION. */
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
        if (bossId != null) {
            SoundManager.onBossDefeated(bossId);
        } else {
            SoundManager.playEnemyDeath();
        }

        deathEmitter.setLocalTranslation(node.getWorldTranslation().add(0f, 0.7f, 0f));
        deathEmitter.setEnabled(true);
        deathEmitter.emitAllParticles();
    }

    public void cleanup(BulletAppState bulletAppState) {
        if (bossId != null) SoundManager.unregisterBoss(bossId);
        bulletAppState.getPhysicsSpace().remove(physics);
        hitEmitter.removeFromParent();
        deathEmitter.removeFromParent();
        node.removeFromParent();
    }

    private void playAnim(String category) {
        if (animComposer == null) return;
        if (category == null || category.equals(currentAnimState)) return;
        String clip = switch (category) {
            case "Idle" -> idleClip;
            case "Move" -> runClip != null ? runClip : walkClip;
            case "Attack" -> attackClip;
            case "Hit" -> hitClip;
            case "Death" -> deathClip;
            default -> null;
        };
        if (clip == null) {
            // remember the state anyway so we don't re-attempt it every frame
            currentAnimState = category;
            return;
        }
        animComposer.setCurrentAction(clip);
        currentAnimState = category;
        if ("Move".equals(category)) {
            float animScale = FastMath.clamp(moveSpeed * MOVE_ANIM_SPEED_PER_UNIT,
                    MOVE_ANIM_MIN_SPEED, MOVE_ANIM_MAX_SPEED);
            animComposer.setGlobalSpeed(animScale);
        } else {
            animComposer.setGlobalSpeed(1f);
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

    /** Boss id from "Models/Characters/boss/<name>.gltf" (only bosses carry this path). */
    private static String bossIdFrom(String modelPath) {
        if (modelPath == null || !modelPath.contains("boss/")) return null;
        int start = modelPath.lastIndexOf('/') + 1;
        int end = modelPath.lastIndexOf('.');
        if (end <= start) return null;
        return modelPath.substring(start, end);
    }

    /** Exact clip name first, then keyword match so prefixed names still resolve. */
    private static String resolveClip(Set<String> names, String... keywords) {
        if (names == null || keywords == null) return null;
        for (String kw : keywords) {
            if (kw != null && names.contains(kw)) {
                return kw;
            }
        }
        for (String name : names) {
            if (name == null) continue;
            String lower = name.toLowerCase();
            for (String kw : keywords) {
                if (kw != null && kw.length() > 2 && lower.contains(kw.toLowerCase())) {
                    return name;
                }
            }
        }
        return null;
    }

    /** Scale to the profile envelope, then lower so the feet sit on the ground. */
    private void scaleToEnvelope(Spatial body, Profile profile) {
        node.updateGeometricState();
        body.updateModelBound();
        BoundingBox raw = toBoundingBox(body);
        if (raw == null) return;

        float rawY = Math.max(1e-4f, Math.abs(raw.getYExtent()));
        float rawX = Math.max(1e-4f, Math.abs(raw.getXExtent()));
        float rawZ = Math.max(1e-4f, Math.abs(raw.getZExtent()));

        float s = Math.min(profile.halfY / rawY,
                Math.min(profile.capX / rawX, profile.capZ / rawZ));
        body.setLocalScale(s);

        node.updateGeometricState();
        body.updateModelBound();
        BoundingBox scaled = toBoundingBox(body);
        if (scaled == null) return;

        float minY = scaled.getCenter().y - scaled.getYExtent();
        body.setLocalTranslation(0f, -minY, 0f);
    }

    private BoundingBox toBoundingBox(Spatial spatial) {
        if (spatial.getWorldBound() instanceof BoundingBox bbox) {
            return bbox;
        }
        return null;
    }

    private float measureVisualHeight() {
        if (bodyModel == null) return boss ? 2.2f : 1.4f;
        node.updateGeometricState();
        bodyModel.updateModelBound();
        BoundingBox bb = toBoundingBox(bodyModel);
        if (bb != null) {
            return Math.max(0.5f, 2f * bb.getYExtent());
        }
        return boss ? 2.6f : 1.6f;
    }

    private void collectBodyMaterials() {
        if (bodyModel == null) return;
        bodyGeoms.clear();
        bodyMats.clear();
        collectBodyMaterials(bodyModel);
    }

    private void collectBodyMaterials(Spatial spatial) {
        if (spatial instanceof Geometry geometry) {
            bodyGeoms.add(geometry);
            bodyMats.add(geometry.getMaterial());
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                collectBodyMaterials(child);
            }
        }
    }

    private void buildPlaceholderBody() {
        float capsuleRadius = boss ? 0.65f : 0.4f;
        float capsuleHeight = boss ? 2.2f : 1.4f;
        ColorRGBA color;
        if (boss) {
            color = new ColorRGBA(0.55f, 0.2f, 0.75f, 1f);
        } else {
            color = switch(tier) {
                case 1 -> ColorRGBA.Green;
                case 2 -> ColorRGBA.Orange;
                case 3 -> ColorRGBA.Red;
                case 4 -> new ColorRGBA(0.6f, 0.15f, 0.15f, 1f);
                default -> ColorRGBA.Gray;
            };
        }

        Cylinder capShape = new Cylinder(2, 16, capsuleRadius, capsuleHeight, true);
        Geometry capsule = new Geometry("EnemyCapsule", capShape);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        capsule.setMaterial(mat);
        // jME3's Cylinder runs along Z, so rotate it upright to match the physics capsule
        capsule.rotate(FastMath.HALF_PI, 0f, 0f);
        // BetterCharacterControl anchors the origin at the feet but our cylinder is
        // centered, so lift by half its height or the lower half sinks into the ground
        capsule.setLocalTranslation(0f, capsuleHeight / 2f, 0f);
        node.attachChild(capsule);
        // bosses wear a small crown so they read as a boss at a glance
        if (boss) {
            Cylinder crownShape = new Cylinder(2, 12, 0.35f, 0.5f, true);
            Geometry crown = new Geometry("BossCrown", crownShape);
            Material crownMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            crownMat.setColor("Color", new ColorRGBA(0.95f, 0.8f, 0.25f, 1f));
            crown.setMaterial(crownMat);
            crown.rotate(FastMath.HALF_PI, 0f, 0f);
            crown.setLocalTranslation(0f, capsuleHeight + 0.25f, 0f);
            node.attachChild(crown);
        }
        this.capsuleMat = mat;
        this.defaultColor = color;
    }

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
                alpha = alpha * alpha;
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

    /** Recursively forces CPU skinning (GPU skinning crashes the AMD GL driver). */
    private static void disableHardwareSkinning(Spatial spatial) {
        if (spatial == null) return;

        SkinningControl skinning = spatial.getControl(SkinningControl.class);
        if (skinning != null) {
            skinning.setHardwareSkinningPreferred(false);
        }

        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                disableHardwareSkinning(child);
            }
        }
    }

    /**
     * Visual envelope a model is scaled to fit. halfY is authoritative so differing
     * proportions still read as the same class of foe; capX/capZ bound half-width.
     * yawDeg corrects models whose front isn't +Z (all 0 — the pack faces +Z).
     */
    private static final class Profile {
        final float halfY;
        final float capX;
        final float capZ;
        final float yawDeg;

        Profile(float halfY, float capX, float capZ, float yawDeg) {
            this.halfY = halfY;
            this.capX = capX;
            this.capZ = capZ;
            this.yawDeg = yawDeg;
        }
    }

    private static Profile profileFor(String modelPath, boolean boss) {
        if (modelPath == null) return null;
        switch (modelPath) {
            case "Models/Characters/boss/tree_warden.gltf":
            case "Models/Characters/boss/frost_giant.gltf":
            case "Models/Characters/boss/fallen_king.gltf":
                // big upright bosses: ~3.4m, looming over a 1.8m player
                return new Profile(1.7f, 2.2f, 2.2f, 0f);
            case "Models/Characters/boss/hell_hound.gltf":
            case "Models/Characters/boss/bee_keeper.gltf":
                // beast bosses: lower-slung but broad and still huge
                return new Profile(1.3f, 3.2f, 3.2f, 0f);
            case "Models/Characters/enemy/kingdomcourt_enemy2.gltf":
                return new Profile(0.95f, 1.2f, 1.2f, 0f);  // armored knight
            default:
                return new Profile(0.825f, 1.1f, 1.1f, 0f);  // a step shorter than the player
        }
    }

    public boolean isDead() { return dead; }
    public boolean canRemove() { return removable; }
    public Vector3f getPosition() { return node.getWorldTranslation(); }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public float getAttackDamage() { return damage; }
    public Node getNode() { return node; }
    public BetterCharacterControl getPhysics() { return physics; }

    /** Jungle 3 makes its enemies 2x faster than the surrounding stages. */
    public void setMoveSpeedMultiplier(float multiplier) {
        this.moveSpeedMultiplier = Math.max(0f, multiplier);
    }

    /** Frozen Depths 3: each landed hit slows the player for this many seconds. */
    public void setSlowOnHit(float seconds) {
        this.slowOnHit = Math.max(0f, seconds);
    }

    /** Arena bosses are one-of-a-kind — the manager gives extra reward for them. */
    public boolean isBoss() { return boss; }
}