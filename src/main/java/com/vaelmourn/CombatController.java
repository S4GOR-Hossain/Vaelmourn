package com.vaelmourn;

import com.jme3.anim.AnimComposer;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;

import java.util.ArrayList;
import java.util.List;

public class CombatController {

    private final Camera cam;
    private final Node playerNode;
    private final AnimComposer animComposer;
    private final Weapons weapons;

    private Weapons.WeaponInstance equipped;

    private final List<EnemyController> enemies = new ArrayList<>();

    private boolean adsHeld = false;
    private boolean blockHeld = false;
    private boolean heavyCharging = false;
    private float heavyChargeTime = 0f;
    private float parryTimer = 0f;

    private float defaultFov = 45f;
    private float targetFov = 45f;

    public CombatController(
            Camera cam,
            Node playerNode,
            AnimComposer animComposer,
            Weapons weapons
    ) {
        this.cam = cam;
        this.playerNode = playerNode;
        this.animComposer = animComposer;
        this.weapons = weapons;

        this.defaultFov = cam.getFrustumTop() != 0 ? 45f : 45f; // safe default
        this.targetFov = defaultFov;
    }

    public void equip(String weaponId) {
        equipped = weapons.create(weaponId);
        adsHeld = false;
        blockHeld = false;
        heavyCharging = false;
        heavyChargeTime = 0f;
    }

    public Weapons.WeaponInstance getEquipped() {
        return equipped;
    }

    /** Feed the currently active enemy list in so attacks can hit them. */
    public void setEnemies(List<EnemyController> enemies) {
        this.enemies.clear();
        if (enemies != null) this.enemies.addAll(enemies);
    }

    public boolean isBlocking() {
        return blockHeld;
    }

    public float getBlockReduction() {
        if (equipped == null) return 0f;
        if (equipped.def.group != Weapons.WeaponGroup.SPECIAL) return 0f;
        return blockHeld ? equipped.def.blockReduction : 0f;
    }

    public void onPrimaryPressed() {
        if (equipped == null || !equipped.ready()) return;

        switch (equipped.def.group) {
            case MELEE:
                doMeleeLight();
                break;
            case RANGED:
                doRangedFire();
                break;
            case SPECIAL:
                doShieldPush();
                break;
        }
    }

    public void onSecondaryPressed() {
        if (equipped == null) return;

        switch (equipped.def.group) {
            case MELEE:
                // start heavy charge + brief parry window
                heavyCharging = true;
                heavyChargeTime = 0f;
                parryTimer = equipped.def.parryWindow;
                playAnimSafe("Parry");
                break;

            case RANGED:
                adsHeld = true;
                targetFov = equipped.def.adsFov;
                break;

            case SPECIAL:
                blockHeld = true;
                playAnimSafe("Block");
                break;
        }
    }

    public void onSecondaryReleased() {
        if (equipped == null) return;

        switch (equipped.def.group) {
            case MELEE:
                if (heavyCharging && equipped.ready()) {
                    doMeleeHeavy();
                }
                heavyCharging = false;
                heavyChargeTime = 0f;
                break;

            case RANGED:
                adsHeld = false;
                targetFov = defaultFov;
                break;

            case SPECIAL:
                blockHeld = false;
                break;
        }
    }

    public void update(float tpf) {
        if (equipped != null) {
            equipped.update(tpf);
        }

        if (heavyCharging) {
            heavyChargeTime += tpf;
        }

        if (parryTimer > 0f) {
            parryTimer -= tpf;
        }

        // Smooth ADS FOV
        float currentFov = cam.getFov();
        float lerp = FastMath.clamp(tpf * 10f, 0f, 1f);
        cam.setFov(FastMath.interpolateLinear(lerp, currentFov, targetFov));
    }

    // ---------------- actions ----------------

    private void doMeleeLight() {
        // Cone sweep in front of the player (arc degrees wide, weapon range deep).
        applyMeleeArc(equipped.def.damage, equipped.def.range, 90f);
        playAnimSafe("Attack_Light");
        equipped.triggerCooldown();
    }

    private void doMeleeHeavy() {
        float chargeScale = FastMath.clamp(1f + heavyChargeTime, 1f, 2f);
        float finalDamage = equipped.def.damage * equipped.def.heavyMultiplier * chargeScale;
        applyMeleeArc(finalDamage, equipped.def.range * 1.2f, 160f);
        playAnimSafe("Attack_Heavy");
        equipped.triggerCooldown();
    }

    private void doRangedFire() {
        // Hitscan: the first enemy close to the camera ray takes damage.
        Ray ray = new Ray(cam.getLocation(), cam.getDirection());
        float best = Float.MAX_VALUE;
        EnemyController target = null;
        for (EnemyController e : enemies) {
            if (e.isDead()) continue;
            Vector3f rel = e.getPosition().subtract(ray.origin);
            float t = rel.dot(ray.direction);
            if (t < 0f || t > equipped.def.range) continue;
            Vector3f proj = ray.origin.add(ray.direction.mult(t));
            if (proj.distance(e.getPosition()) < 0.7f && t < best) {
                best = t;
                target = e;
            }
        }
        if (target != null) {
            target.takeDamage(equipped.def.damage);
            Vector3f away = target.getPosition().subtract(ray.origin);
            away.y = 0f;
            if (away.lengthSquared() > 1e-4f) {
                target.applyKnockback(away.normalizeLocal(), 12f);
            }
        }
        playAnimSafe("Shoot");
        equipped.triggerCooldown();
    }

    private void doShieldPush() {
        // Short-range knockback/push in front of the player.
        Vector3f forward = cam.getDirection().normalizeLocal();
        for (EnemyController e : enemies) {
            if (e.isDead()) continue;
            Vector3f to = e.getPosition().subtract(playerNode.getWorldTranslation());
            to.y = 0f;
            if (to.length() > equipped.def.range + 0.5f) continue;
            if (forward.dot(to.normalizeLocal()) > 0.5f) {
                e.takeDamage(equipped.def.damage);
                e.applyKnockback(to.normalizeLocal(), 22f);
            }
        }
        playAnimSafe("Shield_Push");
        equipped.triggerCooldown();
    }

    /** Damages every living enemy inside a horizontal cone (arcDeg wide, reach deep). */
    private void applyMeleeArc(float damage, float reach, float arcDeg) {
        if (enemies.isEmpty()) return;
        Vector3f origin = playerNode.getWorldTranslation();
        Vector3f forward = cam.getDirection();
        Vector3f dir = new Vector3f(forward.x, 0f, forward.z).normalizeLocal();
        float arcHalf = (arcDeg * FastMath.DEG_TO_RAD) / 2f;
        for (EnemyController e : enemies) {
            if (e.isDead()) continue;
            Vector3f to = e.getPosition().subtract(origin);
            to.y = 0f;
            float dist = to.length();
            if (dist > reach + 0.5f) continue; // + small enemy radius slack
            Vector3f n = to.normalizeLocal();
            float dot = FastMath.clamp(dir.dot(n), -1f, 1f);
            if (FastMath.acos(dot) <= arcHalf) {
                e.takeDamage(damage);
                e.applyKnockback(n, 14f);
            }
        }
    }

    private void playAnimSafe(String clip) {
        if (animComposer == null || clip == null) return;
        if (animComposer.getAnimClipsNames().contains(clip)) {
            animComposer.setCurrentAction(clip);
        }
    }
}