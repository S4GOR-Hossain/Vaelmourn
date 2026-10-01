package com.vaelmourn;

import java.util.HashMap;
import java.util.Map;

/**
 * Central authority for potion upgrade levels.
 *
 * <p>Potions have exactly three levels. Level 1 is the item's authored power; each
 * level above that scales the <em>effect</em> the potion applies when consumed. The
 * scaling is a flat percentage of the base power rather than a new number per potion,
 * so a single rule covers every potion type (healing, regen, speed, strength, crit)
 * without duplicating balance knowledge in the UI.</p>
 *
 * <p>Deliberately NOT modelled here: quantity, price or inventory size. A potion
 * upgrade makes the potion hit harder, never cheaper or more numerous.</p>
 *
 * <p>Levels are run-scoped. {@link #reset()} is called from the new-run path so
 * upgrades never leak into the next run.</p>
 */
public final class PotionUpgrades {

    /** Hard cap. Level 3 is the strongest a potion can ever be. */
    public static final int MAX_LEVEL = 3;

    /** Effect multiplier per upgrade level beyond the first: L1 = x1.00, L2 = x1.50, L3 = x2.00. */
    private static final float EFFECT_PER_LEVEL = 0.5f;

    /** Cost in soul dust to reach level 2, then level 3. Index 0 = L1 -> L2. */
    private static final int[] UPGRADE_COSTS = {60, 140};

    /** itemId -> current level. Only entries the player has actually upgraded appear. */
    private final Map<String, Integer> levels = new HashMap<>();

    /** @return the potion's level, clamped to [1, MAX_LEVEL]. Unknown potions are level 1. */
    public int getLevel(String itemId) {
        if (itemId == null) return 1;
        Integer lv = levels.get(itemId);
        return lv == null ? 1 : Math.min(Math.max(lv, 1), MAX_LEVEL);
    }

    public boolean isMaxed(String itemId) {
        return getLevel(itemId) >= MAX_LEVEL;
    }

    /**
     * The multiplier a potion's authored power is scaled by at its current level.
     * Consumed potions use this so an upgraded bottle is immediately stronger.
     */
    public float getEffectMultiplier(String itemId) {
        return 1f + EFFECT_PER_LEVEL * (getLevel(itemId) - 1);
    }

    /**
     * Applies the current level's multiplier to a potion's authored power.
     * Healing amounts, regen rate and buff magnitudes all funnel through here, which
     * is what makes an upgrade visible in actual gameplay rather than just the menu.
     *
     * @param basePower the value from {@link Item#power}
     * @return the effective value for the potion at its current upgrade level
     */
    public float effectivePower(String itemId, float basePower) {
        return basePower * getEffectMultiplier(itemId);
    }

    /**
     * The multiplier a potion <em>would</em> have at an arbitrary level, so a shop can
     * show the player exactly what the next upgrade buys before they pay for it.
     * Clamped to the same [1, MAX_LEVEL] range as {@link #getLevel}.
     */
    public float getEffectMultiplierAtLevel(int level) {
        int lv = Math.min(Math.max(level, 1), MAX_LEVEL);
        return 1f + EFFECT_PER_LEVEL * (lv - 1);
    }

    /** Effect multiplier the potion would have at the given level, applied to a base power. */
    public float effectivePowerAtLevel(String itemId, float basePower, int level) {
        return basePower * getEffectMultiplierAtLevel(level);
    }

    /**
     * Cost of the next level, or -1 when already maxed so callers can render MAX
     * without a second lookup.
     */
    public int getNextUpgradeCost(String itemId) {
        int lv = getLevel(itemId);
        if (lv >= MAX_LEVEL) return -1;
        return UPGRADE_COSTS[lv - 1];
    }

    /**
     * Buys one level. Refuses past {@link #MAX_LEVEL} and returns false, so a stale
     * menu row or a repeated keypress can never push a potion beyond the cap.
     *
     * @return true if a level was actually gained
     */
    public boolean upgrade(String itemId) {
        if (itemId == null) return false;
        int lv = getLevel(itemId);
        if (lv >= MAX_LEVEL) return false;
        levels.put(itemId, lv + 1);
        return true;
    }

    /** Run reset: every potion drops back to level 1. */
    public void reset() {
        levels.clear();
    }
}
