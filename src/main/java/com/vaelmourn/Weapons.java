package com.vaelmourn;

import java.util.HashMap;
import java.util.Map;

/**
 * The player's single sword.
 *
 * <p>Reduced from a multi-weapon system to one blade: the RANGED and SPECIAL groups
 * were dropped along with the dagger, hunters' blade, heavy blade, longbow, pistol
 * and kite shield. The thrown bomb is not a weapon def - it is a stackable throwable
 * item fired by {@link CombatController} on its own input, so it never appears here.</p>
 *
 * <p>Only fields the sword actually consumes are kept. The former ranged
 * {@code projectileSpeed}/{@code adsFov}, shield {@code blockReduction}/{@code pushForce}
 * and {@code modelPath} were either dead data or tied to the removed groups.</p>
 */
public class Weapons {

    public enum WeaponGroup {
        MELEE
    }

    public static class WeaponDef {
        public final String id;
        public final WeaponGroup group;

        public final float damage;
        public final float attackSpeed;       // swings per second
        public final float heavyMultiplier;
        public final float range;
        public final float parryWindow;
        public final float meleeKnockback;

        public WeaponDef(
                String id,
                WeaponGroup group,
                float damage,
                float attackSpeed,
                float heavyMultiplier,
                float range,
                float parryWindow,
                float meleeKnockback
        ) {
            this.id = id;
            this.group = group;
            this.damage = damage;
            this.attackSpeed = attackSpeed;
            this.heavyMultiplier = heavyMultiplier;
            this.range = range;
            this.parryWindow = parryWindow;
            this.meleeKnockback = meleeKnockback;
        }
    }

    public static class WeaponInstance {
        public final WeaponDef def;
        public float cooldown = 0f;

        public WeaponInstance(WeaponDef def) {
            this.def = def;
        }

        public boolean ready() {
            return cooldown <= 0f;
        }

        public void triggerCooldown() {
            triggerCooldown(1f);
        }

        /** Starts the cooldown, scaled by the player's attack-speed multiplier. */
        public void triggerCooldown(float attackSpeedMultiplier) {
            float effective = def.attackSpeed * Math.max(0.05f, attackSpeedMultiplier);
            cooldown = 1f / Math.max(0.01f, effective);
        }

        public void update(float tpf) {
            if (cooldown > 0f) cooldown -= tpf;
        }
    }

    private final Map<String, WeaponDef> defs = new HashMap<>();

    public Weapons() {
        registerDefaults();
    }

    private void registerDefaults() {
        // balanced all-rounder; the default and only loadout
        add(new WeaponDef(
                "iron_sword",
                WeaponGroup.MELEE,
                28f, 1.4f, 1.9f, 2.4f,
                0.18f, 14f
        ));
    }

    public void add(WeaponDef def) {
        defs.put(def.id, def);
    }

    public WeaponInstance create(String id) {
        WeaponDef def = defs.get(id);
        if (def == null) throw new IllegalArgumentException("Unknown weapon id: " + id);
        return new WeaponInstance(def);
    }

    public WeaponDef get(String id) {
        return defs.get(id);
    }
}