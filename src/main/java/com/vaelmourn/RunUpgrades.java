package com.vaelmourn;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central authority for the five Sanctuary run upgrades: Attack, Speed, Defense,
 * Jump Height and Luck.
 *
 * <p>Every stat spans level 1 to level 5. Level 1 is the player's unmodified base
 * value, and level 5 reaches at most 1.5x that base. The curve is deliberately
 * gradual: the first three steps are small (8%, 8%, 10%) and the last one is the
 * largest single gain (14%), so early purchases feel like a nudge rather than a
 * power spike.</p>
 *
 * <p>These are RUN upgrades. Nothing here writes to the permanent base stats in
 * {@link PlayerStats}; it only exposes multipliers that the existing stat
 * calculations read. {@link #reset()} is called on a new run.</p>
 */
public final class RunUpgrades {

    /** Hard cap per stat. */
    public static final int MAX_LEVEL = 5;

    /** Multiplier at MAX_LEVEL. Level 1 is always exactly 1.0x (unmodified base). */
    private static final float MAX_MULTIPLIER = 1.5f;

    /**
     * Cumulative multipliers indexed by (level - 1). Interpolated rather than listed as
     * five independent numbers so the 1.5x cap is structurally impossible to exceed.
     */
    private static final float[] LEVEL_MULTIPLIERS = {1.00f, 1.08f, 1.16f, 1.26f, 1.50f};

    /** Cost in soul dust for each level transition. Index 0 = L1 -> L2. */
    private static final int[] UPGRADE_COSTS = {80, 150, 250, 400};

    /** The five upgradable stats. Display order here is the order the UI lists them. */
    public enum Stat {
        ATTACK("Attack"),
        SPEED("Speed"),
        DEFENSE("Defense"),
        JUMP("Jump Height"),
        LUCK("Luck");

        private final String label;
        Stat(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private final Map<Stat, Integer> levels = new EnumMap<>(Stat.class);

    /** @return the stat's level, clamped to [1, MAX_LEVEL]. */
    public int getLevel(Stat stat) {
        Integer lv = levels.get(stat);
        return lv == null ? 1 : Math.min(Math.max(lv, 1), MAX_LEVEL);
    }

    public boolean isMaxed(Stat stat) {
        return getLevel(stat) >= MAX_LEVEL;
    }

    /**
     * The multiplier this stat's base value is scaled by, read live by PlayerStats and
     * the jump code. Level 1 returns exactly 1.0f so an unupgraded run behaves
     * identically to the pre-upgrade game.
     */
    public float getMultiplier(Stat stat) {
        return LEVEL_MULTIPLIERS[getLevel(stat) - 1];
    }

    /**
     * The multiplier the stat would have at its next level, so the UI can show the
     * player exactly what an upgrade buys before they spend. Returns the current
     * multiplier when already maxed.
     */
    public float getNextMultiplier(Stat stat) {
        int lv = getLevel(stat);
        if (lv >= MAX_LEVEL) return LEVEL_MULTIPLIERS[MAX_LEVEL - 1];
        return LEVEL_MULTIPLIERS[lv];
    }

    /**
     * Scales a base stat value to its current level. Kept separate from
     * {@link #getMultiplier} so callers read in terms of "my base value" rather than
     * having to remember to multiply manually.
     */
    public float apply(Stat stat, float baseValue) {
        return baseValue * getMultiplier(stat);
    }

    /** Cost of the next level, or -1 when maxed. */
    public int getNextUpgradeCost(Stat stat) {
        int lv = getLevel(stat);
        if (lv >= MAX_LEVEL) return -1;
        return UPGRADE_COSTS[lv - 1];
    }

    /**
     * Buys one level for the stat, refusing past {@link #MAX_LEVEL}.
     *
     * @return true if a level was actually gained
     */
    public boolean upgrade(Stat stat) {
        int lv = getLevel(stat);
        if (lv >= MAX_LEVEL) return false;
        levels.put(stat, lv + 1);
        return true;
    }

    /**
     * Run reset. Every stat returns to level 1, which makes every multiplier 1.0x and
     * therefore restores the player's original base behaviour with no separate
     * "un-modifier" step to forget.
     */
    public void reset() {
        levels.clear();
    }

    /** Exposed for the UI's MAX-level display; never mutated. */
    public static float maxMultiplier() {
        return MAX_MULTIPLIER;
    }
}
