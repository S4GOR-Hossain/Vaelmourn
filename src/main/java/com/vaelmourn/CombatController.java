package com.vaelmourn;

import com.jme3.anim.AnimComposer;
import com.jme3.math.FastMath;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;

public class CombatController {

    private final Camera cam;
    private final Node playerNode;
    private final AnimComposer animComposer;
    private final Weapons weapons;

    private Weapons.WeaponInstance equipped;

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
        // TODO: add hit detection cone/sphere in front of player
        // damage = equipped.def.damage
        playAnimSafe("Attack_Light");
        equipped.triggerCooldown();
    }

    private void doMeleeHeavy() {
        float chargeScale = FastMath.clamp(1f + heavyChargeTime, 1f, 2f);
        float finalDamage = equipped.def.damage * equipped.def.heavyMultiplier * chargeScale;

        // TODO: add heavier hit detection; apply finalDamage
        playAnimSafe("Attack_Heavy");
        equipped.triggerCooldown();
    }

    private void doRangedFire() {
        // TODO: spawn projectile or do hitscan ray from camera
        // damage = equipped.def.damage
        // speed = equipped.def.projectileSpeed
        playAnimSafe("Shoot");
        equipped.triggerCooldown();
    }

    private void doShieldPush() {
        // TODO: short-range knockback in front of player using equipped.def.pushForce
        playAnimSafe("Shield_Push");
        equipped.triggerCooldown();
    }

    private void playAnimSafe(String clip) {
        if (animComposer == null || clip == null) return;
        if (animComposer.getAnimClipsNames().contains(clip)) {
            animComposer.setCurrentAction(clip);
        }
    }
}