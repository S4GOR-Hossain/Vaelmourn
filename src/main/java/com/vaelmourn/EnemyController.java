package com.vaelmourn;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

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

    private final BossStage.BossSpec bossSpec; // boss-fight kit; null for regular enemies

    private String idleClip;
    private String walkClip;
    private String runClip;
    private String attackClip;
    private String smashClip;
    private String chargeClip;
    private String aoeClip;
    private String roarClip;
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

    // -- boss-fight state machine ------------------------------------------------
    private static final int BOSS_ATTACK_NONE = 0;
    private static final int BOSS_ATTACK_MELEE = 1;
    private static final int BOSS_ATTACK_SMASH = 2;
    private static final int BOSS_ATTACK_CHARGE = 3;
    private static final int BOSS_ATTACK_AOE = 4;
    private int attackState = BOSS_ATTACK_NONE;
    private float attackTimer = 0f;
    /** false = attack is winding up (rooted/aiming), true = the damaging phase */
    private boolean attackActive = false;
    private boolean chargeHitDone = false;
    // eased movement so the boss accelerates/decelerates instead of snapping
    private static final float BOSS_MOVE_SMOOTH_RATE = 10f;
    private final Vector3f moveSmooth = new Vector3f();
    // charge ramps into full speed and eases off at the tail, no teleport-lunge
    private static final float CHARGE_RAMP_IN = 0.18f;
    private static final float CHARGE_RAMP_OUT = 0.16f;
    // hysteresis so chase anims don't flicker near the walk/stand boundary
    private static final float CHASE_MOVE_THRESHOLD = 0.5f;
    private static final float CHASE_IDLE_THRESHOLD = 0.1f;
    private boolean wasMoving = false;
    // eased vertical velocity for flyers: no snap, no sink, no endless bob
    private static final float ALTITUDE_GAIN = 2.2f;
    private static final float ALTITUDE_MAX_SPEED = 6f;
    private static final float ALTITUDE_EASE = 4f;
    private float altVy = 0f;
    /** altitude the flyer keeps; smash dips it near the ground for the slam */
    private float flightTargetY = 0f;
    private final Vector3f chargeDir = new Vector3f();
    private final List<EnemyController> summons = new ArrayList<>();
    private Function<Integer, List<EnemyController>> summoner;
    private Geometry telegraphGeo;
    private Material telegraphMat;
    /** loop scalar baked at spawn so boss-kit damages scale per run */
    private float bossLoopScalar = 1f;

    private int currentBossPhase = 1;
    private float recoveryTimer = 0f;
    private float phasePause = 0f;
    private float smashCd = 0f;
    private float chargeCd = 0f;
    private float aoeCd = 0f;
    private float summonTimer = 0f;

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
        this(assetManager, parentNode, bulletAppState, spawnPos, tier, loopCount,
                variantScale, boss, modelPath, null);
    }

    public EnemyController(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState,
                          Vector3f spawnPos, int tier, int loopCount, float variantScale, boolean boss,
                          String modelPath, BossStage.BossSpec bossSpec) {
        this.tier = tier;
        this.assetManager = assetManager;
        this.parentNode = parentNode;
        this.variantScale = variantScale;
        this.boss = boss;
        this.modelPath = modelPath;
        this.bossSpec = bossSpec;
        this.bossId = bossIdFrom(modelPath);
        if (bossId != null) {
            SoundManager.registerBoss(bossId);
        }

        float baseHealth;
        float baseDamage;
        if (boss) {
            baseHealth = 900f;
            baseDamage = 14f;
            // per-boss scaling lives on the spec so one biome can be rebalanced
            // without every other boss' numbers moving
            if (bossSpec != null) {
                baseHealth *= bossSpec.healthMul;
                baseDamage *= bossSpec.damageMul;
            }
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

        // Every completed cycle (the Fallen King falling and the run wrapping to a fresh
        // Sanctuary) doubles the whole rotation again: cycle 1 is 2x the opening run,
        // cycle 2 is 4x, and so on. Applied to normal enemies and to the boss alike -
        // bossLoopScalar reuses this exact value for the boss kit's charge, smash and
        // AOE damage, so no boss attack is left behind on the old 1.5x curve.
        float loopScalar = (float) Math.pow(2.0, loopCount);
        this.maxHealth = baseHealth * loopScalar;
        this.health = maxHealth;
        this.damage = baseDamage * loopScalar;
        this.moveSpeed = boss ? (bossSpec != null ? bossSpec.moveSpeed : 3.2f)
                : 4f + (tier - 1) * 1.5f;
        this.detectionRange = bossSpec != null && bossSpec.detectionRange > 0f
                ? bossSpec.detectionRange : 25f;
        this.attackRange = bossSpec != null && bossSpec.meleeRange > 0f
                ? bossSpec.meleeRange : 2.5f;
        this.slowOnHit = bossSpec != null ? bossSpec.slowOnHit : 0f;
        this.flightTargetY = bossSpec != null ? bossSpec.hoverHeight : 0f;
        this.bossLoopScalar = loopScalar;

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
            smashClip = resolveClip(names, "Smash", "Slam", "Stomp", "Attack");
            chargeClip = resolveClip(names, "Charge", "Run", "Fast_Flying", "Bite_Front", "Attack");
            aoeClip = resolveClip(names, "Telephone", "Magic", "Spell", "Special", "Attack");
            roarClip = resolveClip(names, "Roar", "Battle_Cry", "Shout", "Attack");
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
        // Minie overwrites a body's gravity with the PhysicsSpace's own (default
        // -9.81) at the moment the body is added, unless the body protects it. Both
        // branches below set gravity BEFORE the add on the next line, so without
        // this protection both were silently discarded: every walker fell at -9.81
        // instead of the intended -30 (a third of the rate, so they hung in the air
        // after every hop), and the flying bosses got -9.81 instead of zero and
        // steadily sank into the arena floor mid-fight - the exact opposite of what
        // the comment below claims. Minie logged a warning per body for this.
        if (physics.getRigidBody() != null) {
            physics.getRigidBody().setProtectGravity(true);
        }
        if (boss && bossSpec != null && bossSpec.hoverHeight > 0f) {
            // flying bosses ignore gravity: altitude is steered by the AI instead,
            // so they neither fall nor sink into the floor mid-fight
            physics.setGravity(Vector3f.ZERO);
        } else {
            physics.setGravity(new Vector3f(0, -30f, 0));
        }
        physics.warp(spawnPos);
        node.addControl(physics);
        bulletAppState.getPhysicsSpace().add(physics);

        if (boss) {
            buildTelegraph();
        }
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

        boolean bossMode = boss && bossSpec != null;

        if (!bossMode && distToPlayer < detectionRange) {
            Vector3f dir = toPlayer;
            if (dir.lengthSquared() > 1e-4f) {
                physics.setViewDirection(dir.normalizeLocal());
            }
        }

        // while a knockback is active, scale down the enemy's own movement so
        // it can't just walk straight through the push and cancel it
        float kbMag = knockback.length();
        float kbLinger = FastMath.clamp(knockbackTimer / KNOCKBACK_DURATION, 0f, 1f);

        Vector3f desiredMove = new Vector3f();
        if (bossMode) {
            desiredMove = updateBoss(tpf, playerPos, enemyPos, toPlayer, distToPlayer, playerStats);
        } else if (attacking) {
            attackWindupTimer -= tpf;
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

        if (!bossMode && !attacking && hitAnimTimer <= 0f) {
            boolean moving = desiredMove.lengthSquared() > 1e-3f;
            playAnim(moving ? "Move" : "Idle");
        }

        physics.setWalkDirection(desiredMove.add(knockback));

        // keep the boss on the combat disc so it never plows into the decor
        if (bossMode) {
            clampBossToArena();
        }

        if (kbMag > 0f) {
            knockbackTimer -= tpf;
            float scale = FastMath.clamp(knockbackTimer / KNOCKBACK_DURATION, 0f, 1f);
            knockback.multLocal(scale);
            if (knockback.lengthSquared() < 0.05f) knockback.set(0f, 0f, 0f);
        }

        refreshHitFlash();
    }

    // -- boss-fight AI ----------------------------------------------------------
    // The stage spec picks which attacks exist and how fast they come; this just
    // runs each attack's windup -> impact/blow -> recovery lifecycle.
    private Vector3f updateBoss(float tpf, Vector3f playerPos, Vector3f enemyPos,
                                Vector3f toPlayer, float dist, PlayerStats playerStats) {
        if (bossSpec == null) return Vector3f.ZERO;

        float[] thresholds = bossSpec.thresholds;
        float ratio = maxHealth <= 0f ? 0f : health / maxHealth;
        int nextPhase = 1;
        for (int i = 0; i < thresholds.length; i++) {
            if (ratio <= thresholds[i]) nextPhase = i + 2;
        }
        // transitions fire exactly once — later phases never re-trigger
        if (nextPhase > currentBossPhase) {
            for (int p = currentBossPhase + 1; p <= nextPhase; p++) {
                firePhaseChange(p);
            }
            currentBossPhase = nextPhase;
        }

        if (bossSpec.summon) {
            summonTimer -= tpf;
            summons.removeIf(EnemyController::canRemove);
        }

        float targetMoveX = 0f;
        float targetMoveZ = 0f;

        if (phasePause > 0f) {
            // interlude so the phase shift reads clearly, no pressure on the player
            phasePause -= tpf;
            if (phasePause <= 0f) currentAnimState = null;
            facePlayer(playerPos, enemyPos);
        } else if (hitAnimTimer > 0f) {
            facePlayer(playerPos, enemyPos);
        } else if (recoveryTimer > 0f) {
            recoveryTimer -= tpf;
            if (recoveryTimer <= 0f) currentAnimState = null;
            facePlayer(playerPos, enemyPos);
        } else if (attackState != BOSS_ATTACK_NONE) {
            advanceBossAttack(tpf, playerPos, enemyPos, playerStats);
            Vector3f step = bossChaseStep(toPlayer, 0.35f);
            if (attackState == BOSS_ATTACK_CHARGE) {
                if (attackActive) {
                    float elapsed = bossSpec.chargeDuration - attackTimer;
                    float rampIn = FastMath.clamp(elapsed / CHARGE_RAMP_IN, 0f, 1f);
                    float rampOut = FastMath.clamp(attackTimer / CHARGE_RAMP_OUT, 0f, 1f);
                    float speed = bossSpec.chargeSpeed * bossSpec.chargeMul[phaseIndex()]
                            * Math.min(rampIn, rampOut);
                    targetMoveX = chargeDir.x * speed;
                    targetMoveZ = chargeDir.z * speed;
                }
                if (chargeDir.lengthSquared() > 1e-4f) {
                    physics.setViewDirection(chargeDir);
                }
            } else if (attackState == BOSS_ATTACK_MELEE) {
                // step into the swing during the windup so the lunge keeps its pace
                targetMoveX = step.x;
                targetMoveZ = step.z;
            }
        } else {
            if (smashCd > 0f) smashCd -= tpf;
            if (chargeCd > 0f) chargeCd -= tpf;
            if (aoeCd > 0f) aoeCd -= tpf;

            facePlayer(playerPos, enemyPos);

            float atkCd = bossSpec.attackCooldown * bossSpec.attackMul[phaseIndex()];
            boolean near = dist <= bossSpec.meleeRange;
            boolean wantCharge = bossSpec.charge && !near
                    && dist >= bossSpec.chargeRange && chargeCd <= 0f;
            boolean wantSmash = bossSpec.smash && dist <= bossSpec.smashRadius
                    * bossSpec.radiusMul[phaseIndex()] * 1.2f && smashCd <= 0f;
            boolean wantAoe = bossSpec.aoe && dist <= bossSpec.aoeRadius
                    * bossSpec.radiusMul[phaseIndex()] * 1.2f && aoeCd <= 0f;

            if (timeSinceLastAttack >= atkCd) {
                if (wantCharge) {
                    startCharge(playerPos, enemyPos);
                } else if (wantSmash && wantAoe) {
                    if (smashCd <= aoeCd) startSmash(); else startAoe();
                } else if (wantSmash) {
                    startSmash();
                } else if (wantAoe) {
                    startAoe();
                } else if (near) {
                    startMelee();
                }
            }

            if (bossSpec.summon && summonTimer <= 0f
                    && summons.size() < bossSpec.summonCap) {
                summonMinions();
            }

            // chase the player — a step closer only when actually committing to
            // approach, so the boss slows smoothly instead of stopping cold
            if (attackState == BOSS_ATTACK_NONE
                    && toPlayer.lengthSquared() > 1e-4f) {
                Vector3f move = toPlayer.normalizeLocal();
                float pace = bossSpec.moveSpeed * bossSpec.moveMul[phaseIndex()];
                if (near) pace *= 0.35f;
                pace *= 1f - knockbackLinger() * 0.85f;
                targetMoveX = move.x * pace;
                targetMoveZ = move.z * pace;
            } else {
                // attack just fired out of a chase — let the eased motion wind down
                targetMoveX = 0f;
                targetMoveZ = 0f;
            }
        }

        // ease the horizontal move so starts, stops and turns never snap
        float k = 1f - FastMath.exp(-BOSS_MOVE_SMOOTH_RATE * tpf);
        moveSmooth.x += (targetMoveX - moveSmooth.x) * k;
        moveSmooth.z += (targetMoveZ - moveSmooth.z) * k;

        // flyers steer altitude with an eased velocity: no gravity, no snap, the
        // hover height is a target, not a teleport
        float ySpeed = 0f;
        if (bossSpec.hoverHeight > 0f) {
            float err = flightTargetY - enemyPos.y;
            float desiredVy = FastMath.clamp(err * ALTITUDE_GAIN,
                    -ALTITUDE_MAX_SPEED, ALTITUDE_MAX_SPEED);
            altVy += (desiredVy - altVy) * Math.min(1f, ALTITUDE_EASE * tpf);
            if (FastMath.abs(err) < 0.08f && FastMath.abs(altVy) < 0.2f) {
                altVy = 0f; // settle cleanly on the hover height, no residual bob
            }
            ySpeed = altVy;
        }

        resolveBossLocomotion(moveSmooth.length());

        return new Vector3f(moveSmooth.x, ySpeed, moveSmooth.z);
    }

    /** Small aimed step used during melee windups so the boss commits to the lunge. */
    private Vector3f bossChaseStep(Vector3f toPlayer, float factor) {
        if (toPlayer.lengthSquared() <= 1e-4f) return Vector3f.ZERO;
        return toPlayer.normalizeLocal().multLocal(
                bossSpec.moveSpeed * bossSpec.moveMul[phaseIndex()] * factor);
    }

    /** Move/Idle only outside attacks, pauses and flinches; hysteresis kills flicker. */
    private void resolveBossLocomotion(float speed) {
        if (phasePause > 0f || hitAnimTimer > 0f || recoveryTimer > 0f
                || attackState != BOSS_ATTACK_NONE) return;
        boolean moving = wasMoving
                ? speed > CHASE_IDLE_THRESHOLD
                : speed > CHASE_MOVE_THRESHOLD;
        if (moving != wasMoving) {
            playAnim(moving ? "Move" : "Idle");
            wasMoving = moving;
        }
    }

    private void firePhaseChange(int phase) {
        attackState = BOSS_ATTACK_NONE;
        hideTelegraph();
        recoveryTimer = 0f;
        phasePause = 1.1f;
        if (bossId != null) SoundManager.playBossRoar(bossId);
        playAnim("Roar");
        // summoning bosses bring backup with the shift
        if (phase >= 2 && bossSpec.summon && summoner != null) {
            summonMinions();
        }
    }

    private void summonMinions() {
        summonTimer = bossSpec.summonInterval;
        if (summoner == null) return;
        List<EnemyController> spawned = summoner.apply(bossSpec.summonCount);
        if (spawned != null) {
            summons.addAll(spawned);
        }
        SoundManager.playEnemyAttack();
    }

    private void startMelee() {
        attackState = BOSS_ATTACK_MELEE;
        attackActive = false;
        attackTimer = ATTACK_TELEGRAPH_DURATION;
        timeSinceLastAttack = 0f;
        playAnim("Attack");
        playBossAttackSound();
    }

    private void startSmash() {
        attackState = BOSS_ATTACK_SMASH;
        attackActive = false;
        attackTimer = bossSpec.smashWindup;
        timeSinceLastAttack = 0f;
        smashCd = bossSpec.smashCooldown * bossSpec.smashMul[phaseIndex()];
        playAnim("Smash");
        playBossAttackSound();
        // flying slammers dip for the impact, then rise again after the recovery
        if (bossSpec.hoverHeight > 0f) flightTargetY = 1.2f;
        showTelegraphAt(node.getWorldTranslation(),
                bossSpec.smashRadius * bossSpec.radiusMul[phaseIndex()]);
    }

    private void startCharge(Vector3f playerPos, Vector3f enemyPos) {
        attackState = BOSS_ATTACK_CHARGE;
        attackActive = false;
        attackTimer = bossSpec.chargeWindup;
        chargeHitDone = false;
        timeSinceLastAttack = 0f;
        chargeCd = bossSpec.chargeCooldown * bossSpec.chargeMul[phaseIndex()];
        Vector3f d = new Vector3f(playerPos.x - enemyPos.x, 0f, playerPos.z - enemyPos.z);
        if (d.lengthSquared() < 1e-4f) d.set(0f, 0f, -1f);
        chargeDir.set(d.normalizeLocal());
        physics.setViewDirection(chargeDir);
        playAnim("Charge");
        if (animComposer != null) {
            // rooted aim pose while the lunge is telegraphed, full sprint when it fires
            animComposer.setGlobalSpeed(0.45f);
        }
        playBossAttackSound();
    }

    private void startAoe() {
        attackState = BOSS_ATTACK_AOE;
        attackActive = false;
        attackTimer = bossSpec.aoeWindup;
        timeSinceLastAttack = 0f;
        aoeCd = bossSpec.aoeCooldown * bossSpec.aoeMul[phaseIndex()];
        playAnim("Aoe");
        playBossAttackSound();
        showTelegraphAt(node.getWorldTranslation(), 0.2f * bossSpec.aoeRadius);
    }

    private void advanceBossAttack(float tpf, Vector3f playerPos, Vector3f enemyPos,
                                   PlayerStats playerStats) {
        switch (attackState) {
            case BOSS_ATTACK_MELEE, BOSS_ATTACK_SMASH, BOSS_ATTACK_AOE -> {
                attackTimer -= tpf;
                if (attackTimer <= 0f) {
                    if (attackState == BOSS_ATTACK_AOE) {
                        float p = 0f;
                        updateTelegraph(bossSpec.aoeRadius
                                * bossSpec.radiusMul[phaseIndex()] * 0.2f);
                        doAoe(playerPos, enemyPos, playerStats);
                        hideTelegraph();
                        endBossAttack(bossSpec.aoeRecovery);
                    } else if (attackState == BOSS_ATTACK_SMASH) {
                        doSmashImpact(playerPos, enemyPos, playerStats);
                        hideTelegraph();
                        endBossAttack(bossSpec.smashRecovery);
                    } else {
                        if (playerWithinRadius(bossSpec.meleeRange * 1.1f, playerPos, enemyPos)) {
                            dealBossDamage(playerStats, damage);
                        }
                        endBossAttack(bossSpec.recovery);
                    }
                } else if (attackState == BOSS_ATTACK_AOE) {
                    float p = 1f - FastMath.clamp(attackTimer / Math.max(0.001f, bossSpec.aoeWindup),
                            0f, 1f);
                    updateTelegraph(bossSpec.aoeRadius
                            * bossSpec.radiusMul[phaseIndex()] * (0.2f + 0.8f * p));
                }
            }
            case BOSS_ATTACK_CHARGE -> {
                if (!attackActive) {
                    // windup: aim at the frozen target direction, hold still
                    attackTimer -= tpf;
                    physics.setViewDirection(chargeDir);
                    if (attackTimer <= 0f) {
                        attackActive = true;
                        attackTimer = bossSpec.chargeDuration;
                        if (animComposer != null) {
                            float animScale = FastMath.clamp(
                                    bossSpec.chargeSpeed * MOVE_ANIM_SPEED_PER_UNIT,
                                    MOVE_ANIM_MIN_SPEED, MOVE_ANIM_MAX_SPEED);
                            animComposer.setGlobalSpeed(animScale);
                        }
                    }
                } else {
                    attackTimer -= tpf;
                    if (!chargeHitDone
                            && playerWithinRadius(bossSpec.chargeHitRadius, playerPos, enemyPos)) {
                        chargeHitDone = true;
                        dealBossDamage(playerStats, bossSpec.chargeDamage * bossLoopScalar);
                    }
                    if (attackTimer <= 0f) {
                        attackActive = false;
                        endBossAttack(bossSpec.chargeRecovery);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void endBossAttack(float recovery) {
        attackState = BOSS_ATTACK_NONE;
        attackActive = false;
        recoveryTimer = recovery;
        if (bossSpec.hoverHeight > 0f) {
            flightTargetY = bossSpec.hoverHeight;
        }
        playAnim("Idle");
    }

    private void doSmashImpact(Vector3f playerPos, Vector3f enemyPos, PlayerStats playerStats) {
        float radius = bossSpec.smashRadius * bossSpec.radiusMul[phaseIndex()];
        if (playerWithinRadius(radius, playerPos, enemyPos)) {
            dealBossDamage(playerStats, bossSpec.smashDamage * bossLoopScalar);
        }
        impactBurst(radius);
    }

    private void doAoe(Vector3f playerPos, Vector3f enemyPos, PlayerStats playerStats) {
        float radius = bossSpec.aoeRadius * bossSpec.radiusMul[phaseIndex()];
        if (playerWithinRadius(radius, playerPos, enemyPos)) {
            dealBossDamage(playerStats, bossSpec.aoeDamage * bossLoopScalar);
        }
        impactBurst(radius);
    }

    private void dealBossDamage(PlayerStats playerStats, float amount) {
        if (playerStats == null || amount <= 0f) return;
        playerStats.damage(amount);
        if (bossId != null) SoundManager.playBossDamageDeal(bossId);
        if (slowOnHit > 0f) {
            playerStats.applySlow(0.35f, slowOnHit);
        }
    }

    private boolean playerWithinRadius(float radius, Vector3f playerPos, Vector3f enemyPos) {
        float dx = playerPos.x - enemyPos.x;
        float dz = playerPos.z - enemyPos.z;
        return dx * dx + dz * dz <= radius * radius;
    }

    private void facePlayer(Vector3f playerPos, Vector3f enemyPos) {
        Vector3f dir = new Vector3f(playerPos.x - enemyPos.x, 0f, playerPos.z - enemyPos.z);
        if (dir.lengthSquared() > 1e-4f) {
            physics.setViewDirection(dir.normalizeLocal());
        }
    }

    private void impactBurst(float radius) {
        Vector3f ground = node.getWorldTranslation();
        ground.y = 0.4f;
        deathEmitter.setLocalTranslation(ground);
        deathEmitter.setEnabled(true);
        deathEmitter.emitAllParticles();
    }

    private void buildTelegraph() {
        try {
            com.jme3.scene.shape.Cylinder mesh =
                    new com.jme3.scene.shape.Cylinder(2, 40, 1f, 0.02f, true);
            telegraphGeo = new Geometry("BossTelegraph", mesh);
            telegraphMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            telegraphMat.setColor("Color", new ColorRGBA(1f, 0.15f, 0.1f, 0.5f));
            telegraphMat.getAdditionalRenderState().setFaceCullMode(
                    com.jme3.material.RenderState.FaceCullMode.Off);
            telegraphMat.getAdditionalRenderState().setBlendMode(
                    com.jme3.material.RenderState.BlendMode.Alpha);
            telegraphGeo.setMaterial(telegraphMat);
            telegraphGeo.rotate(FastMath.HALF_PI, 0f, 0f);
            telegraphGeo.setLocalScale(0.01f, 0.01f, 1f);
            telegraphGeo.setCullHint(Spatial.CullHint.Always);
            parentNode.attachChild(telegraphGeo);
        } catch (RuntimeException ex) {
            System.err.println("EnemyController: telegraph unavailable: " + ex.getMessage());
            telegraphGeo = null;
        }
    }

    private void showTelegraphAt(Vector3f center, float radius) {
        if (telegraphGeo == null) return;
        // the unit circle lives in the mesh's X/Y plane; both get scaled so the
        // ground marker stays a true circle matching the smash/aoe hit radius
        telegraphGeo.setLocalScale(radius, radius, 1f);
        telegraphGeo.setLocalTranslation(center.x, 0.08f, center.z);
        telegraphGeo.setCullHint(Spatial.CullHint.Never);
    }

    private void updateTelegraph(float radius) {
        if (telegraphGeo == null) return;
        telegraphGeo.setLocalScale(radius, radius, 1f);
        telegraphGeo.setCullHint(Spatial.CullHint.Never);
    }

    private void hideTelegraph() {
        if (telegraphGeo != null) {
            telegraphGeo.setCullHint(Spatial.CullHint.Always);
        }
    }

    private void clampBossToArena() {
        Vector3f p = node.getWorldTranslation();
        float rad = bossSpec.arenaRadius;
        if (p.x * p.x + p.z * p.z > rad * rad) {
            Vector3f clamped = new Vector3f(p.x, 0f, p.z).normalizeLocal().multLocal(rad);
            clamped.y = p.y;
            node.setLocalTranslation(clamped);
            physics.warp(clamped);
        }
    }

    private int phaseIndex() {
        return Math.min(currentBossPhase - 1, 2);
    }

    private float knockbackLinger() {
        return FastMath.clamp(knockbackTimer / KNOCKBACK_DURATION, 0f, 1f);
    }

    private void playBossAttackSound() {
        if (bossId != null) SoundManager.playBossAttack(bossId);
        else SoundManager.playEnemyAttack();
    }

    /** Boss arenas call this so the boss can drag biome minions onto the fight. */
    public void setSummoner(Function<Integer, List<EnemyController>> summoner) {
        this.summoner = summoner;
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

    /** Impulse away from the attacker plus a small vertical hop; fades over KNOCKBACK_DURATION.
     *  Flyers stay airborne — horizontal shove only, or a hit would whip them up and down. */
    public void applyKnockback(Vector3f awayDir, float force) {
        if (dead) return;
        if (bossSpec != null && bossSpec.hoverHeight > 0f) {
            knockback.set(awayDir.x * force, 0f, awayDir.z * force);
        } else {
            knockback.set(awayDir.x * force, force * 0.5f, awayDir.z * force);
        }
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
            case "Smash" -> smashClip;
            case "Charge" -> chargeClip;
            case "Aoe" -> aoeClip;
            case "Roar" -> roarClip;
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
        if ("Move".equals(category) || "Charge".equals(category)) {
            float pace = "Charge".equals(category) && bossSpec != null
                    ? bossSpec.chargeSpeed : moveSpeed;
            float animScale = FastMath.clamp(pace * MOVE_ANIM_SPEED_PER_UNIT,
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
    public int getTier() { return tier; }
}