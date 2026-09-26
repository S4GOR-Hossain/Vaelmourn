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
    private CombatEffects effects;
    private PlayerStats playerStats;

    private Weapons.WeaponInstance equipped;

    private final List<EnemyController> enemies = new ArrayList<>();

    private boolean adsHeld = false;
    private boolean blockHeld = false;
    private boolean heavyCharging = false;
    private float heavyChargeTime = 0f;
    private float parryTimer = 0f;

    private float defaultFov = 45f;
    private float targetFov = 45f;

    // how much camera kick each attack carries when it connects
    private static final float MELEE_LIGHT_HIT_SHAKE = 0.05f;
    private static final float MELEE_HEAVY_HIT_SHAKE = 0.10f;
    private static final float RANGED_HIT_SHAKE = 0.08f;
    private static final float SHIELD_PUSH_HIT_SHAKE = 0.12f;

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

        this.defaultFov = cam.getFrustumTop() != 0 ? 45f : 45f; // same either way, just a safe default
        this.targetFov = defaultFov;
    }

    public void equip(String weaponId) {
        equipped = weapons.create(weaponId);
        adsHeld = false;
        blockHeld = false;
        heavyCharging = false;
        heavyChargeTime = 0f;
        // push the weapon's own stats through so the stat bar / buff math
        // works off the real, currently readied weapon
        if (playerStats != null && equipped != null) {
            playerStats.setBaseWeaponDamage(equipped.def.damage);
            playerStats.setBaseAttackSpeed(equipped.def.attackSpeed);
        }
    }

    public void setPlayerStats(PlayerStats playerStats) {
        this.playerStats = playerStats;
    }

    public String getEquippedWeaponId() {
        return equipped == null ? null : equipped.def.id;
    }

    public Weapons.WeaponInstance getEquipped() {
        return equipped;
    }

    /** Hook in the combat feel stuff (damage numbers, shake, sounds). */
    public void setEffects(CombatEffects effects) {
        this.effects = effects;
    }

    /**
     * What fraction of the cooldown is still pending (1.0 = just swung, 0.0 = ready).
     * The HUD mirrors this on its attack cooldown bar.
     */
    public float getCooldownFraction() {
        if (equipped == null) return 0f;
        float mult = playerStats != null ? playerStats.getAttackSpeedMultiplier() : 1f;
        float total = 1f / Math.max(0.01f, equipped.def.attackSpeed * Math.max(0.05f, mult));
        return FastMath.clamp(total <= 0f ? 0f : equipped.cooldown / total, 0f, 1f);
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
                // start the heavy charge and open a short parry window
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

        // Ease the FOV toward the ADS zoom value
        float currentFov = cam.getFov();
        float lerp = FastMath.clamp(tpf * 10f, 0f, 1f);
        cam.setFov(FastMath.interpolateLinear(lerp, currentFov, targetFov));
    }

    // ---------------- the actual attacks ----------------

    private void doMeleeLight() {
        // Sweep a cone in front of the player — arc across, weapon range deep.
        applyMeleeArc(finalizeDamage(equipped.def.damage), equipped.def.range, 90f, MELEE_LIGHT_HIT_SHAKE);
        playAnimSafe("Attack_Light");
        notifySwing(false);
        triggerCooldown();
    }

    private void doMeleeHeavy() {
        float chargeScale = FastMath.clamp(1f + heavyChargeTime, 1f, 2f);
        float raw = equipped.def.damage * equipped.def.heavyMultiplier * chargeScale;
        applyMeleeArc(finalizeDamage(raw), equipped.def.range * 1.2f, 160f, MELEE_HEAVY_HIT_SHAKE);
        playAnimSafe("Attack_Heavy");
        notifySwing(true);
        triggerCooldown();
    }

    private void doRangedFire() {
        // Pure hitscan: whichever enemy sits closest to the camera ray eats the damage.
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
            float dealt = finalizeDamage(equipped.def.damage);
            target.takeDamage(dealt);
            Vector3f away = target.getPosition().subtract(ray.origin);
            away.y = 0f;
            if (away.lengthSquared() > 1e-4f) {
                target.applyKnockback(away.normalizeLocal(), 12f);
            }
            if (effects != null) effects.onEnemyHit(target, dealt, target.isDead(), RANGED_HIT_SHAKE);
        }
        playAnimSafe("Shoot");
        notifySwing(false);
        triggerCooldown();
    }

    private void doShieldPush() {
        // Short-range push that knocks anything in front of the player back.
        Vector3f forward = cam.getDirection().normalizeLocal();
        for (EnemyController e : enemies) {
            if (e.isDead()) continue;
            Vector3f to = e.getPosition().subtract(playerNode.getWorldTranslation());
            to.y = 0f;
            if (to.length() > equipped.def.range + 0.5f) continue;
            if (forward.dot(to.normalizeLocal()) > 0.5f) {
                float dealt = finalizeDamage(equipped.def.damage);
                e.takeDamage(dealt);
                e.applyKnockback(to.normalizeLocal(), 22f);
                if (effects != null) effects.onEnemyHit(e, dealt, e.isDead(), SHIELD_PUSH_HIT_SHAKE);
            }
        }
        playAnimSafe("Shield_Push");
        notifySwing(true);
        triggerCooldown();
    }

    /** Damages every living enemy inside a horizontal cone (arcDeg wide, reach deep). */
    private void applyMeleeArc(float damage, float reach, float arcDeg, float shakeAmp) {
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
            if (dist > reach + 0.5f) continue; // + a touch of slack for the enemy radius
            Vector3f n = to.normalizeLocal();
            float dot = FastMath.clamp(dir.dot(n), -1f, 1f);
            if (FastMath.acos(dot) <= arcHalf) {
                e.takeDamage(damage);
                // heavy blades shove harder; light blades barely push
                e.applyKnockback(n, equipped.def.meleeKnockback);
                if (effects != null) effects.onEnemyHit(e, damage, e.isDead(), shakeAmp);
            }
        }
    }

    /** Applies strength potion + crit roll to a base weapon hit. */
    private float finalizeDamage(float baseDamage) {
        return playerStats != null ? playerStats.rollFinalDamage(baseDamage) : baseDamage;
    }

    /** Starts the swing cooldown, scaled by any active attack-speed buff. */
    private void triggerCooldown() {
        equipped.triggerCooldown(playerStats != null ? playerStats.getAttackSpeedMultiplier() : 1f);
    }

    private void notifySwing(boolean heavy) {
        if (effects != null) {
            effects.onPlayerAttack(playerNode.getWorldTranslation(), heavy);
        }
    }

    private void playAnimSafe(String clip) {
        if (animComposer == null || clip == null) return;
        if (animComposer.getAnimClipsNames().contains(clip)) {
            animComposer.setCurrentAction(clip);
        }
    }
}