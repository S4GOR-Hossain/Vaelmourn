package com.vaelmourn;

import com.jme3.math.FastMath;

import java.util.Random;

/**
 * Equipment bonuses are re-summed from the Inventory on every read (idempotent,
 * nothing stacks across frames); potion buffs are timed multipliers that expire.
 */
public class PlayerStats {

    /** lets the game react (flash, shake, sfx) the moment the player takes a hit */
    public interface DamageListener {
        void onDamaged(float amount, float currentHealth);
    }

    private DamageListener damageListener;

    private String playerName = "Vael";

    private float maxHealth = 100f;
    private float health = maxHealth;

    /**
     * While true, {@link #damage(float)} is a no-op. Owned here rather than on the
     * player controller because this class is the single choke point every attack in the
     * game funnels through, so one guard covers enemy melee and all four boss attacks
     * without any of them needing to know a dodge happened.
     */
    private boolean invulnerable = false;

    // kept for future abilities; no spells consume it yet
    private float maxMana = 100f;
    private float mana = maxMana;

    private int level = 1;
    private float experience = 0f;
    private float experienceToNext = 100f;

    private int soulDust = 0;

    private float baseArmor = 10f;
    private float baseMoveSpeed = 29f;
    private float baseWeaponDamage = 8f;
    private float baseWeaponAttackSpeed = 1.2f;

    // crit curve lives here so it's one knob instead of many
    private static final float BASE_CRIT_CHANCE = 0.05f;
    private static final float CRIT_MULTIPLIER = 1.6f;

    // equipment bonuses are read live, never stacked
    private Inventory inventory;

    // ---- Sanctuary upgrade state (run-scoped) --------------------------------
    // Authoritative sources live in these two objects; PlayerStats only reads their
    // multipliers so there is exactly one place that decides a level's effect.
    // Both are reset by the new-run path, never persisted.
    private final RunUpgrades runUpgrades = new RunUpgrades();
    private final PotionUpgrades potionUpgrades = new PotionUpgrades();

    private float speedPower = 0f, speedTime = 0f, speedDuration = 0f;
    private float strengthPower = 0f, strengthTime = 0f, strengthDuration = 0f;
    private float critPower = 0f, critTime = 0f, critDuration = 0f;
    private float regenRate = 0f, regenTime = 0f, regenDuration = 0f, regenAccum = 0f;

    // stage debuff: slow (Frozen Depths 3 slaps this on the player)
    private float slowPower = 0f, slowTime = 0f;

    private final Random random = new Random();

    // ================= accessors =================

    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public float getMana() { return mana; }
    public float getMaxMana() { return maxMana; }
    public int getLevel() { return level; }
    public float getExperience() { return experience; }
    public float getExperienceToNext() { return experienceToNext; }
    public float getSoulDust() { return soulDust; }
    public String getPlayerName() { return playerName; }

    /** Run-upgrade state (Attack/Speed/Defense/Jump/Luck). Owned here so the UI and the
     *  stat maths can never disagree about what the player has bought. */
    public RunUpgrades getRunUpgrades() { return runUpgrades; }

    /** Potion upgrade levels. Owned here for the same reason. */
    public PotionUpgrades getPotionUpgrades() { return potionUpgrades; }

    /**
     * Resets both upgrade tracks. Called from the new-run path alongside
     * {@link #resetToDefaults()} so a fresh run starts every stat at level 1 and every
     * potion at level 1. Kept separate from resetToDefaults so that method stays the
     * single place that restores raw base values.
     */
    public void resetUpgrades() {
        runUpgrades.reset();
        potionUpgrades.reset();
    }

    /** Armor point total; raises the flat damage-reduction curve (100/(100+armor)). */
    public float getArmorPoints() {
        // Defense run-upgrade scales the armor total itself, so it flows through the
        // project's existing 100/(100+armor) mitigation curve rather than introducing
        // a second damage-reduction formula.
        return runUpgrades.apply(RunUpgrades.Stat.DEFENSE, baseArmor + defenseFromEquipment());
    }

    /** Movement speed; equipment adds flat, speed potion adds a %, slow debuff removes
     *  one, and the Speed run-upgrade scales the whole result. */
    public float getMovementSpeed() {
        float withEquipment = baseMoveSpeed + moveSpeedFromEquipment();
        return runUpgrades.apply(RunUpgrades.Stat.SPEED, withEquipment)
                * (1f + speedPower) * getSlowMultiplier();
    }

    public float getSlowMultiplier() {
        return slowTime > 0f ? Math.max(0.35f, 1f - slowPower) : 1f;
    }

    /** Frozen Depths "enemy hit slows you": refreshes duration, never grows magnitude, so it can't stack. */
    public void applySlow(float magnitude, float duration) {
        slowPower = Math.max(slowPower, magnitude);
        slowTime = Math.max(slowTime, duration);
    }

    public float getAttackSpeed() {
        return baseWeaponAttackSpeed * getAttackSpeedMultiplier();
    }

    public float getAttackSpeedMultiplier() {
        return 1f + speedPower;
    }

    public float getDamageMultiplier() {
        return 1f + strengthPower;
    }

    /**
     * Luck run-upgrade multiplier. Exposed so the one centralized reward path
     * ({@code StageManager}'s soul-dust payout) can read it — there is no other luck
     * calculation in the project, so this stays the single source.
     */
    public float getLuckMultiplier() {
        return runUpgrades.getMultiplier(RunUpgrades.Stat.LUCK);
    }

    public float getCritChance() {
        return FastMath.clamp(BASE_CRIT_CHANCE + critPower, 0.05f, 0.9f);
    }

    public float getCritMultiplier() {
        return CRIT_MULTIPLIER;
    }

    public float getAverageDamage() {
        return baseWeaponDamage * getDamageMultiplier();
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName == null || playerName.isEmpty() ? "Player" : playerName;
    }

    public void setDamageListener(DamageListener listener) {
        this.damageListener = listener;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Called when a weapon is readied so the stats bar shows the true weapon values. */
    public void setBaseWeaponDamage(float damage) {
        baseWeaponDamage = Math.max(0f, damage);
    }

    public void setBaseAttackSpeed(float attackSpeed) {
        baseWeaponAttackSpeed = Math.max(0.01f, attackSpeed);
    }

    public void setArmorPoints(float armorPoints) {
        this.baseArmor = Math.max(0f, armorPoints);
    }

    public void setMovementSpeed(float movementSpeed) {
        this.baseMoveSpeed = Math.max(0f, movementSpeed);
    }

    /** Back to a freshly-started run's values; callers re-grant the starter loadout.
     *  Wiring (inventory ref, damage listener) is left untouched so in-place reset
     *  is safe for every holder of this instance. */
    public void resetToDefaults() {
        maxHealth = 100f;
        health = maxHealth;
        invulnerable = false;
        maxMana = 100f;
        mana = maxMana;

        level = 1;
        experience = 0f;
        experienceToNext = 100f;

        soulDust = 0;

        baseArmor = 10f;
        baseMoveSpeed = 29f;
        baseWeaponDamage = 8f;
        baseWeaponAttackSpeed = 1.2f;

        speedPower = 0f; speedTime = 0f; speedDuration = 0f;
        strengthPower = 0f; strengthTime = 0f; strengthDuration = 0f;
        critPower = 0f; critTime = 0f; critDuration = 0f;
        regenRate = 0f; regenTime = 0f; regenDuration = 0f; regenAccum = 0f;
        slowPower = 0f; slowTime = 0f;
    }

    public void setHealth(float health) {
        this.health = Math.max(0f, Math.min(maxHealth, health));
    }

    public void setMana(float mana) {
        this.mana = Math.max(0f, Math.min(maxMana, mana));
    }

    public void damage(float amount) {
        if (amount <= 0f) return;
        // Full immunity during a dodge roll. Returning before the mitigation math and
        // before the damageListener call means a dodged hit also skips the hurt flash,
        // the hurt sound and the death check — the hit is treated as never having landed.
        if (invulnerable) return;
        // flat mitigation curve: 100/(100+armor) of the hit gets through
        float mitigated = amount * (100f / (100f + getArmorPoints()));
        float before = health;
        setHealth(health - mitigated);
        if (health < before && damageListener != null) {
            damageListener.onDamaged(mitigated, health);
        }
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public void heal(float amount) {
        setHealth(health + amount);
    }

    /** True if the item was actually consumed (any non-NONE effect), false for keys/materials. */
    public boolean consume(Item item) {
        if (item == null) return false;
        // every effect below is scaled by the potion's upgrade level, so a bottle bought
        // at level 3 is immediately stronger than the same item at level 1. The rule
        // lives in PotionUpgrades; this is the single place it is applied.
        float power = potionUpgrades.effectivePower(item.id, item.power);
        switch (item.effect) {
            case HEAL_INSTANT:
                heal(power);
                return true;
            case REGEN:
                regenRate = power;
                regenTime = item.duration;
                regenDuration = item.duration;
                regenAccum = 0f;
                return true;
            case SPEED:
                speedPower = power;
                speedTime = item.duration;
                speedDuration = item.duration;
                return true;
            case STRENGTH:
                strengthPower = power;
                strengthTime = item.duration;
                strengthDuration = item.duration;
                return true;
            case CRIT:
                critPower = power;
                critTime = item.duration;
                critDuration = item.duration;
                return true;
            default:
                return false;
        }
    }

    /** Strength multiplier first, then the Attack run-upgrade, then a crit check
     *  (if it procs it replaces that hit's value). */
    public float rollFinalDamage(float baseDamage) {
        float dmg = baseDamage * getDamageMultiplier();
        // Attack run-upgrade scales the weapon's damage through the existing
        // finalizeDamage path, so every weapon/attack benefits without a second
        // damage formula existing anywhere.
        dmg *= runUpgrades.getMultiplier(RunUpgrades.Stat.ATTACK);
        if (random.nextFloat() < getCritChance()) {
            dmg *= CRIT_MULTIPLIER;
        }
        return dmg;
    }

    // ================= per-frame upkeep =================

    /** Drains potion timers and ticks regen. Call once per game frame. */
    public void update(float tpf) {
        if (speedTime > 0f) {
            speedTime -= tpf;
            if (speedTime <= 0f) { speedPower = 0f; speedDuration = 0f; }
        }
        if (strengthTime > 0f) {
            strengthTime -= tpf;
            if (strengthTime <= 0f) { strengthPower = 0f; strengthDuration = 0f; }
        }
        if (critTime > 0f) {
            critTime -= tpf;
            if (critTime <= 0f) { critPower = 0f; critDuration = 0f; }
        }
        if (regenTime > 0f) {
            regenTime -= tpf;
            regenAccum += tpf * regenRate;
            int healed = (int) regenAccum;
            if (healed > 0) {
                setHealth(health + healed);
                regenAccum -= healed;
            }
            if (regenTime <= 0f) { regenRate = 0f; regenDuration = 0f; }
        }
        if (slowTime > 0f) {
            slowTime -= tpf;
            if (slowTime <= 0f) slowPower = 0f;
        }
    }

    /** The four timed potion buffs, in a fixed display order. */
    public enum Buff { SPEED, STRENGTH, CRIT, REGEN }

    public float buffRemaining(Buff b) {
        return switch (b) {
            case SPEED -> speedTime;
            case STRENGTH -> strengthTime;
            case CRIT -> critTime;
            case REGEN -> regenTime;
        };
    }

    public float buffDuration(Buff b) {
        return switch (b) {
            case SPEED -> speedDuration;
            case STRENGTH -> strengthDuration;
            case CRIT -> critDuration;
            case REGEN -> regenDuration;
        };
    }

    public float buffFraction(Buff b) {
        float dur = buffDuration(b);
        if (dur <= 0f) return 0f;
        return FastMath.clamp(buffRemaining(b) / dur, 0f, 1f);
    }

    public String getActiveBuffSummary() {
        StringBuilder sb = new StringBuilder();
        if (speedTime > 0f) {
            sb.append("SPD ").append((int) Math.ceil(speedTime)).append("s  ");
        }
        if (strengthTime > 0f) {
            sb.append("STR ").append((int) Math.ceil(strengthTime)).append("s  ");
        }
        if (critTime > 0f) {
            sb.append("CRIT ").append((int) Math.ceil(critTime)).append("s  ");
        }
        if (regenTime > 0f) {
            sb.append("REGEN ").append((int) Math.ceil(regenTime)).append("s");
        }
        return sb.toString().trim();
    }

    /** Sums defense bonuses from the four armor slots (idempotent read). */
    private float defenseFromEquipment() {
        if (inventory == null) return 0f;
        float total = 0f;
        for (Slot s : inventory.getEquipment()) {
            if (s.isEmpty()) continue;
            Item it = s.getItem();
            if (it != null) total += it.defenseBonus;
        }
        return total;
    }

    private float moveSpeedFromEquipment() {
        if (inventory == null) return 0f;
        float total = 0f;
        for (Slot s : inventory.getEquipment()) {
            if (s.isEmpty()) continue;
            Item it = s.getItem();
            if (it != null) total += it.moveSpeedBonus;
        }
        return total;
    }

    public float getHealthFraction() {
        return maxHealth <= 0f ? 0f : health / maxHealth;
    }

    public float getManaFraction() {
        return maxMana <= 0f ? 0f : mana / maxMana;
    }

    public float getExperienceFraction() {
        return experienceToNext <= 0f ? 0f : experience / experienceToNext;
    }

    public void addExperience(float amount) {
        experience += amount;
        while (experience >= experienceToNext) {
            experience -= experienceToNext;
            level++;
            experienceToNext = experienceToNext * 1.3f;
        }
    }

    public void addSoulDust(int amount) {
        soulDust = Math.max(0, soulDust + amount);
    }

    public void spendSoulDust(int amount) {
        soulDust = Math.max(0, soulDust - amount);
    }
}