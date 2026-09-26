package com.vaelmourn;

import com.jme3.math.FastMath;

import java.util.Random;

/**
 * Tracks the player's core RPG statistics: health, mana, experience,
 * level, the soul dust currency, and the base/effective combat stats.
 *
 * Equipment and potions feed in through clean seams:
 *   - equipped armor/legs bonuses are summed from the Inventory every read
 *     (idempotent — nothing stacks across frames),
 *   - potion buffs are timed multipliers that expire back to base values.
 */
public class PlayerStats {

    /** lets the game react (flash, shake, sfx) the moment the player takes a hit */
    public interface DamageListener {
        void onDamaged(float amount, float currentHealth);
    }

    private DamageListener damageListener;

    private String playerName = "Vael";

    // health pool
    private float maxHealth = 100f;
    private float health = maxHealth;

    // mana pool (kept for future abilities; no spells consume it yet)
    private float maxMana = 100f;
    private float mana = maxMana;

    // xp and leveling
    private int level = 1;
    private float experience = 0f;
    private float experienceToNext = 100f;

    // currency
    private int soulDust = 0;

    // ---- base stats (the "unmodified" numbers gameplay starts from) ----
    private float baseArmor = 10f;             // suite of starting gear baseline
    private float baseMoveSpeed = 12f;         // matches the world movement speed
    private float baseWeaponDamage = 8f;       // replaced by the equipped weapon
    private float baseWeaponAttackSpeed = 1.2f;

    // crit curve lives here so it's one knob instead of many
    private static final float BASE_CRIT_CHANCE = 0.05f;
    private static final float CRIT_MULTIPLIER = 1.6f;

    // ---- inventory hook: equipment bonuses are read live, never stacked ----
    private Inventory inventory;

    // ---- active potion buffs (time is drained in update()) ----
    private float speedPower = 0f, speedTime = 0f, speedDuration = 0f;       // +move & +attack speed
    private float strengthPower = 0f, strengthTime = 0f, strengthDuration = 0f; // +damage
    private float critPower = 0f, critTime = 0f, critDuration = 0f;          // +crit chance
    private float regenRate = 0f, regenTime = 0f, regenDuration = 0f, regenAccum = 0f; // heal over time

    // ---- stage debuff: slow (Frozen Depths 3 slaps this on the player) ----
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
    public int getSoulDust() { return soulDust; }
    public String getPlayerName() { return playerName; }

    // ---- effective stats (equipment + potions folded in) ----

    /** Armor point total; raises the flat damage-reduction curve (100/(100+armor)). */
    public float getArmorPoints() {
        return baseArmor + defenseFromEquipment();
    }

    /** Movement speed; leggings/boots add flat, speed potion adds a %, slow debuff removes one. */
    public float getMovementSpeed() {
        return (baseMoveSpeed + moveSpeedFromEquipment()) * (1f + speedPower) * getSlowMultiplier();
    }

    /** 1.0 when normal, below 1.0 while a stage slow debuff is active. */
    public float getSlowMultiplier() {
        return slowTime > 0f ? Math.max(0.35f, 1f - slowPower) : 1f;
    }

    /**
     * Applies the Frozen Depths "enemy hit slows you" effect. Refreshes the
     * duration but never grows the magnitude, so it can't be stacked infinitely.
     */
    public void applySlow(float magnitude, float duration) {
        slowPower = Math.max(slowPower, magnitude);
        slowTime = Math.max(slowTime, duration);
    }

    /** What the currently readied weapon swings per second, buffs included. */
    public float getAttackSpeed() {
        return baseWeaponAttackSpeed * getAttackSpeedMultiplier();
    }

    public float getAttackSpeedMultiplier() {
        return 1f + speedPower;
    }

    public float getDamageMultiplier() {
        return 1f + strengthPower;
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

    // ================= setters / wiring =================

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

    public void setHealth(float health) {
        this.health = Math.max(0f, Math.min(maxHealth, health));
    }

    public void setMana(float mana) {
        this.mana = Math.max(0f, Math.min(maxMana, mana));
    }

    // ================= incoming damage =================

    public void damage(float amount) {
        if (amount <= 0f) return;
        // flat mitigation curve: 100/(100+armor) of the hit gets through
        float mitigated = amount * (100f / (100f + getArmorPoints()));
        float before = health;
        setHealth(health - mitigated);
        if (health < before && damageListener != null) {
            damageListener.onDamaged(mitigated, health);
        }
    }

    public void heal(float amount) {
        setHealth(health + amount);
    }

    // ================= consumables =================

    /**
     * Applies a consumable's effect. Returns true if the item was actually
     * consumed (effects any non-NONE consumable), false for keys/materials
     * and anything with no effect.
     */
    public boolean consume(Item item) {
        if (item == null) return false;
        switch (item.effect) {
            case HEAL_INSTANT:
                heal(item.power);
                return true;
            case REGEN:
                regenRate = item.power;
                regenTime = item.duration;
                regenDuration = item.duration;
                regenAccum = 0f;
                return true;
            case SPEED:
                speedPower = item.power;
                speedTime = item.duration;
                speedDuration = item.duration;
                return true;
            case STRENGTH:
                strengthPower = item.power;
                strengthTime = item.duration;
                strengthDuration = item.duration;
                return true;
            case CRIT:
                critPower = item.power;
                critTime = item.duration;
                critDuration = item.duration;
                return true;
            default:
                return false;
        }
    }

    /**
     * Rolls a hit for its final damage: strength potion multiplier first, then
     * a crit check (if it procs the multiplier replaces that hit's value).
     */
    public float rollFinalDamage(float baseDamage) {
        float dmg = baseDamage * getDamageMultiplier();
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

    // ---- potion buff state for the HUD timers & player effect particles ----

    /** The four timed potion buffs, in a fixed display order. */
    public enum Buff { SPEED, STRENGTH, CRIT, REGEN }

    /** How many seconds are left (0 when not active). */
    public float buffRemaining(Buff b) {
        return switch (b) {
            case SPEED -> speedTime;
            case STRENGTH -> strengthTime;
            case CRIT -> critTime;
            case REGEN -> regenTime;
        };
    }

    /** Seconds the buff started with (0 when no buff data). */
    public float buffDuration(Buff b) {
        return switch (b) {
            case SPEED -> speedDuration;
            case STRENGTH -> strengthDuration;
            case CRIT -> critDuration;
            case REGEN -> regenDuration;
        };
    }

    /** 0..1 of the timer still left (0 when inactive). */
    public float buffFraction(Buff b) {
        float dur = buffDuration(b);
        if (dur <= 0f) return 0f;
        return FastMath.clamp(buffRemaining(b) / dur, 0f, 1f);
    }

    /** Short label of every active buff, for the HUD ("SPD 18s  STR 12s"). */
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

    // ================= equipment =================

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

    /** Sums movement bonuses from leggings/boots (idempotent read). */
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

    // ================= misc =================

    public float getHealthFraction() {
        return maxHealth <= 0f ? 0f : health / maxHealth;
    }

    public float getManaFraction() {
        return maxMana <= 0f ? 0f : mana / maxMana;
    }

    public float getExperienceFraction() {
        return experienceToNext <= 0f ? 0f : experience / experienceToNext;
    }

    /** Adds experience; levels up (and resets the bar) as many times as needed. */
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