package com.vaelmourn;

import java.util.HashMap;
import java.util.Map;

public class Weapons {

    public enum WeaponGroup {
        MELEE,
        RANGED,
        SPECIAL
    }

    public static class WeaponDef {
        public final String id;
        public final WeaponGroup group;
        public final String modelPath;

        public final float damage;
        public final float attackSpeed;       // attacks per second
        public final float heavyMultiplier;   // melee heavy scaling
        public final float range;
        public final float projectileSpeed;   // for ranged
        public final float adsFov;            // zoom fov while RMB held
        public final float blockReduction;    // 0..1 incoming reduction
        public final float pushForce;         // for shield push
        public final float parryWindow;       // seconds

        public WeaponDef(
                String id,
                WeaponGroup group,
                String modelPath,
                float damage,
                float attackSpeed,
                float heavyMultiplier,
                float range,
                float projectileSpeed,
                float adsFov,
                float blockReduction,
                float pushForce,
                float parryWindow
        ) {
            this.id = id;
            this.group = group;
            this.modelPath = modelPath;
            this.damage = damage;
            this.attackSpeed = attackSpeed;
            this.heavyMultiplier = heavyMultiplier;
            this.range = range;
            this.projectileSpeed = projectileSpeed;
            this.adsFov = adsFov;
            this.blockReduction = blockReduction;
            this.pushForce = pushForce;
            this.parryWindow = parryWindow;
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
            cooldown = 1f / Math.max(0.01f, def.attackSpeed);
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
        // --- MELEE ---
        add(new WeaponDef(
                "iron_sword",
                WeaponGroup.MELEE,
                "Models/Weapons/Melee/iron_sword.glb",
                28f, 1.4f, 1.9f, 2.4f,
                0f, 50f, 0f, 0f, 0.18f
        ));

        add(new WeaponDef(
                "dagger",
                WeaponGroup.MELEE,
                "Models/Weapons/Melee/dagger.glb",
                16f, 2.4f, 1.5f, 1.8f,
                0f, 55f, 0f, 0f, 0.14f
        ));

        // --- RANGED ---
        add(new WeaponDef(
                "longbow",
                WeaponGroup.RANGED,
                "Models/Weapons/Ranged/longbow.glb",
                24f, 1.0f, 1.0f, 60f,
                55f, 35f, 0f, 0f, 0f
        ));

        add(new WeaponDef(
                "pistol",
                WeaponGroup.RANGED,
                "Models/Weapons/Ranged/pistol.glb",
                20f, 3.2f, 1.0f, 80f,
                120f, 42f, 0f, 0f, 0f
        ));

        // --- SPECIAL (shield) ---
        add(new WeaponDef(
                "kite_shield",
                WeaponGroup.SPECIAL,
                "Models/Weapons/Special/kite_shield.glb",
                10f, 1.0f, 1.0f, 2.0f,
                0f, 55f, 0.65f, 14f, 0f
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
