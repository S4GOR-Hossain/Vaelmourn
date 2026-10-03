package com.vaelmourn;

import com.jme3.anim.AnimComposer;
import com.jme3.math.FastMath;
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

    private boolean heavyCharging = false;
    private float heavyChargeTime = 0f;
    private float parryTimer = 0f;

    private float defaultFov = 45f;
    private float targetFov = 45f;
    // velocity FOV swell from the movement system; composes on top of targetFov
    private float speedFovBoost = 0f;

    private static final float MELEE_LIGHT_HIT_SHAKE = 0.05f;
    private static final float MELEE_HEAVY_HIT_SHAKE = 0.10f;

    /** Where thrown bombs go. Null until the game hands one over. */
    private Bombs bombs;

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
        heavyCharging = false;
        heavyChargeTime = 0f;
        // push the weapon's own stats through so stat/buff math uses the real weapon
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

    public void setEffects(CombatEffects effects) {
        this.effects = effects;
    }

    /** Fraction of the cooldown still pending (1.0 = just swung, 0.0 = ready); the HUD mirrors it. */
    public float getCooldownFraction() {
        if (equipped == null) return 0f;
        float mult = playerStats != null ? playerStats.getAttackSpeedMultiplier() : 1f;
        float total = 1f / Math.max(0.01f, equipped.def.attackSpeed * Math.max(0.05f, mult));
        return FastMath.clamp(total <= 0f ? 0f : equipped.cooldown / total, 0f, 1f);
    }

    public void setEnemies(List<EnemyController> enemies) {
        this.enemies.clear();
        if (enemies != null) this.enemies.addAll(enemies);
    }

    /**
     * The sword is the only weapon, so the old per-group primary branch collapsed into a
     * straight light swing. The ranged and shield-push paths went away with their weapons.
     */
    public void onPrimaryPressed() {
        if (equipped == null || !equipped.ready()) return;
        doMeleeLight();
    }

    /**
     * Throws a bomb on the same cooldown as a sword swing, so the two share one rhythm.
     *
     * @return true if a bomb was thrown, false if on cooldown, out of bombs, or the
     *         live-bomb cap is reached - the caller only decrements the stack on true
     */
    public boolean throwBomb() {
        if (bombs == null || equipped == null || !equipped.ready()) return false;
        if (!bombs.throwFrom(cam.getLocation(), cam.getDirection())) return false;

        // heavy swing clip as the throwing gesture, but deliberately no
        // playSwordSwing() here: a blade swoosh on a bomb throw reads wrong
        playAnimSafe("Attack_Heavy");
        SoundManager.playBombThrow();
        // same cooldown as a sword attack, so spamming one weapon blocks the other
        triggerCooldown();
        return true;
    }

    public void setBombs(Bombs bombs) {
        this.bombs = bombs;
    }

    /** Hold to charge a heavy blow; a short parry window opens while held. */
    public void onSecondaryPressed() {
        if (equipped == null) return;
        heavyCharging = true;
        heavyChargeTime = 0f;
        parryTimer = equipped.def.parryWindow;
        playAnimSafe("Parry");
    }

    public void onSecondaryReleased() {
        if (equipped == null) return;
        if (heavyCharging && equipped.ready()) {
            doMeleeHeavy();
        }
        heavyCharging = false;
        heavyChargeTime = 0f;
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

        float currentFov = cam.getFov();
        float lerp = FastMath.clamp(tpf * 10f, 0f, 1f);
        cam.setFov(FastMath.interpolateLinear(lerp, currentFov, targetFov + speedFovBoost));
    }

    public void setSpeedFovBoost(float boost) {
        this.speedFovBoost = boost;
    }

    private void doMeleeLight() {
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

    private void applyMeleeArc(float damage, float reach, float arcDeg, float shakeAmp) {
        if (enemies.isEmpty()) return;
        Vector3f origin = playerNode.getWorldTranslation();
        Vector3f forward = cam.getDirection();
        Vector3f dir = new Vector3f(forward.x, 0f, forward.z).normalizeLocal();
        float arcHalf = (arcDeg * FastMath.DEG_TO_RAD) / 2f;
        for (EnemyController e : enemies) {
            if (e.isDead()) continue;
            Vector3f to = e.getPosition().subtract(origin.clone());
            to.y = 0f;
            float dist = to.length();
            if (dist > reach + 0.5f) continue; // + a touch of slack for the enemy radius
            Vector3f n = to.normalizeLocal();
            float dot = FastMath.clamp(dir.dot(n), -1f, 1f);
            if (FastMath.acos(dot) <= arcHalf) {
                e.takeDamage(damage);
                e.applyKnockback(n, equipped.def.meleeKnockback);
                if (effects != null) effects.onEnemyHit(e, damage, e.isDead(), shakeAmp);
                SoundManager.playPlayerHit();
            }
        }
    }

    /** Strength potion + crit roll applied to a base weapon hit. */
    private float finalizeDamage(float baseDamage) {
        return playerStats != null ? playerStats.rollFinalDamage(baseDamage) : baseDamage;
    }

    private void triggerCooldown() {
        equipped.triggerCooldown(playerStats != null ? playerStats.getAttackSpeedMultiplier() : 1f);
    }

    private void notifySwing(boolean heavy) {
        if (equipped != null && equipped.def.group == Weapons.WeaponGroup.MELEE) {
            SoundManager.playSwordSwing(heavy);
        }
        if (effects != null) {
            effects.onPlayerAttack(playerNode.getWorldTranslation(), heavy);
        }
    }

    private void playAnimSafe(String clip) {
        if (animComposer == null || clip == null) return;
        String mapped = clip;
        switch (clip) {
            case "Attack_Light":
                if (hasClip("Sword_Slash")) mapped = "Sword_Slash";
                else if (hasClip("Punch_Left")) mapped = "Punch_Left";
                break;
            case "Attack_Heavy":
                if (hasClip("Sword_Slash")) mapped = "Sword_Slash";
                else if (hasClip("Punch_Right")) mapped = "Punch_Right";
                break;
            case "Parry":
                if (hasClip("Idle_Sword")) mapped = "Idle_Sword";
                else if (hasClip("Idle")) mapped = "Idle";
                break;
            default:
                mapped = clip;
                break;
        }
        if (mapped != null && hasClip(mapped)) {
            animComposer.setCurrentAction(mapped);
        }
    }

    private boolean hasClip(String clip) {
        if (animComposer == null || clip == null) return false;
        for (String c : animComposer.getAnimClipsNames()) {
            if (c.equals(clip)) return true;
        }
        return false;
    }
}