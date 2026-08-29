package com.vaelmourn;

import com.jme3.anim.AnimComposer;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Cylinder;

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

    private boolean dead = false;
    private float deathTimer = 0f;
    private Material originalMaterial;
    private float damageFlashTimer = 0f;

    private int tier; // 1=easy (green), 2=medium (orange), 3=hard (red)

    public EnemyController(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState,
                          Vector3f spawnPos, int tier, int loopCount) {
        this.tier = tier;

        // Base stats per tier
        float baseHealth = switch(tier) {
            case 1 -> 8f;
            case 2 -> 20f;
            case 3 -> 40f;
            default -> 10f;
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
        node.attachChild(capsule);
        this.originalMaterial = mat;

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
    public void update(float tpf, Vector3f playerPos) {
        if (dead) {
            deathTimer -= tpf;
            return;
        }

        timeSinceLastAttack += tpf;

        if (damageFlashTimer > 0f) {
            damageFlashTimer -= tpf;
        }

        Vector3f enemyPos = node.getWorldTranslation();
        float distToPlayer = enemyPos.distance(playerPos);

        if (distToPlayer < detectionRange) {
            // Move toward player
            Vector3f direction = playerPos.subtract(enemyPos).normalizeLocal();

            if (distToPlayer < attackRange) {
                // In attack range — stop and attack
                physics.setWalkDirection(Vector3f.ZERO);
                if (timeSinceLastAttack >= attackCooldown) {
                    // Attack happens; damage applied by ForestBiome callback
                    timeSinceLastAttack = 0f;
                    playAnim("Attack");
                }
            } else {
                // Move toward player
                physics.setWalkDirection(direction.mult(moveSpeed));
                playAnim("Walk");
            }
        } else {
            // Idle
            physics.setWalkDirection(Vector3f.ZERO);
            playAnim("Idle");
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

        if (health <= 0f) {
            die();
        }
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

    // Getters
    public boolean isDead() { return dead; }
    public Vector3f getPosition() { return node.getWorldTranslation(); }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public float getAttackDamage() { return damage; }
    public Node getNode() { return node; }
    public BetterCharacterControl getPhysics() { return physics; }
}
