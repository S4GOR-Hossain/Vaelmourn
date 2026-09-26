package com.vaelmourn;

import com.jme3.math.ColorRGBA;

import java.util.HashMap;
import java.util.Map;

/**
 * Static catalog of all item definitions in the game.
 */
public final class ItemRegistry {

    private static final Map<String, Item> REGISTRY = new HashMap<>();

    private ItemRegistry() {
    }

    public static void registerDefaults() {
        // ---------- weapons ----------
        add(new Item("iron_sword", "Iron Sword", Item.Category.WEAPON, new ColorRGBA(0.7f, 0.7f, 0.75f, 1f),
                1, 40, "Models/Weapons/Melee/Sword.glb", "Textures/Items/iron_sword.png",
                "A balanced one-handed blade."));
        add(new Item("dagger", "Dagger", Item.Category.WEAPON, new ColorRGBA(0.6f, 0.6f, 0.65f, 1f),
                1, 20, null, null, "Fast and nimble, but it barely bites."));
        add(new Item("longbow", "Longbow", Item.Category.WEAPON, new ColorRGBA(0.55f, 0.4f, 0.25f, 1f),
                1, 35, null, "Textures/Items/wooden_bow.png", "A hunter's bow with a long reach."));
        add(new Item("pistol", "Pistol", Item.Category.WEAPON, new ColorRGBA(0.2f, 0.2f, 0.25f, 1f),
                1, 30, null, null, "A reliable sidearm."));

        // fast, light — the mobility blade
        add(new Item("hunters_blade", "Hunter's Blade", Item.Category.WEAPON, new ColorRGBA(0.45f, 0.75f, 0.5f, 1f),
                1, 55, null, "Textures/Items/iron_sword.png",
                "Swift and light. Weak hits, very fast swings."));
        // slow, brutal — big damage and hard knockback
        add(new Item("heavy_blade", "Heavy Blade", Item.Category.WEAPON, new ColorRGBA(0.72f, 0.35f, 0.2f, 1f),
                1, 75, null, "Textures/Items/iron_sword.png",
                "A slab of metal on a handle. Huge damage, slow swing."));

        // shields & specials
        add(new Item("kite_shield", "Kite Shield", Item.Category.SHIELD, new ColorRGBA(0.55f, 0.5f, 0.45f, 1f),
                1, 25, "Models/Weapons/Special/kite_shield.glb", "Textures/Items/iron_shield.png",
                "Blocks a large share of incoming damage while held."));

        // ---------- consumables ----------
        add(new Item("health_potion", "Health Potion", Item.Category.CONSUMABLE, new ColorRGBA(0.9f, 0.2f, 0.2f, 1f),
                10, 8, null, "Textures/Items/health_potion.png",
                "Restores 40 HP instantly.",
                Item.Effect.HEAL_INSTANT, 40f, 0f));
        // replaced the old mana potion — a burst of fleetness instead
        add(new Item("speed_potion", "Speed Potion", Item.Category.CONSUMABLE, new ColorRGBA(0.3f, 0.85f, 0.95f, 1f),
                10, 10, null, "Textures/Items/mana_potion.png",
                "Boosts movement and attack speed 40% for 20s.",
                Item.Effect.SPEED, 0.4f, 20f));
        add(new Item("strength_potion", "Strength Potion", Item.Category.CONSUMABLE, new ColorRGBA(0.95f, 0.45f, 0.2f, 1f),
                10, 12, null, null,
                "Boosts damage 25% for 20s.",
                Item.Effect.STRENGTH, 0.25f, 20f));
        add(new Item("critical_potion", "Critical Potion", Item.Category.CONSUMABLE, new ColorRGBA(1f, 0.85f, 0.25f, 1f),
                10, 14, null, null,
                "Boosts critical chance for 20s.",
                Item.Effect.CRIT, 0.15f, 20f));
        add(new Item("regen_potion", "Regeneration Potion", Item.Category.CONSUMABLE, new ColorRGBA(0.3f, 0.75f, 0.35f, 1f),
                10, 10, null, "Textures/Items/health_potion.png",
                "Regenerates 5 HP per second for 20s.",
                Item.Effect.REGEN, 5f, 20f));

        // ---------- keys (progression, never consumed like potions) ----------
        add(new Item("dungeon_key", "Dungeon Key", Item.Category.KEY, new ColorRGBA(0.9f, 0.8f, 0.2f, 1f),
                5, 15, null, "Textures/Items/dungeon_key.png",
                "Opens sealed doors in the dungeon biomes."));
        add(new Item("boss_key", "Boss Key", Item.Category.KEY, new ColorRGBA(0.6f, 0.2f, 0.8f, 1f),
                5, 25, null, null, "Opens the boss chamber door."));

        // ---------- crafting materials ----------
        add(material("soul_dust", "Soul Dust", new ColorRGBA(0.6f, 0.9f, 1.0f, 1f), 1, null,
                "Currency left behind by fallen enemies.", 1));
        add(material("iron_ingot", "Iron Ingot", new ColorRGBA(0.6f, 0.65f, 0.7f, 1f), 3,
                "Textures/Items/iron_ingot.png", "Smelted iron, ready to forge.", 1));
        add(material("iron_ore", "Iron Ore", new ColorRGBA(0.55f, 0.5f, 0.45f, 1f), 2,
                "Textures/Items/iron_ingot.png", "Raw ore from the mines. Basic crafting material.", 1));
        add(material("leather", "Leather", new ColorRGBA(0.55f, 0.4f, 0.3f, 1f), 2,
                "Textures/Items/leather.png", "Tanned hide, used in light gear.", 1));
        add(material("blood_shard", "Blood Shard", new ColorRGBA(0.85f, 0.15f, 0.15f, 1f), 12,
                null, "A piece of something old and thirsty. Rare upgrade material.", 2));
        add(material("void_crystal", "Void Crystal", new ColorRGBA(0.35f, 0.2f, 0.7f, 1f), 25,
                null, "Pure condensed darkness. Very rare upgrade material.", 3));
        add(material("wolf_fang", "Wolf Fang", new ColorRGBA(0.85f, 0.85f, 0.9f, 1f), 10,
                null, "A sharp fang tied to speed and attack upgrades.", 2));
        add(material("ember_core", "Ember Core", new ColorRGBA(1f, 0.45f, 0.1f, 1f), 15,
                null, "A core that still glows with heat. Tied to fire and damage upgrades.", 2));

        // ---------- armor / equipment ----------
        add(equipment("iron_helmet", "Iron Helmet", Item.Category.HELMET,
                new ColorRGBA(0.62f, 0.66f, 0.72f, 1f), 45,
                "Textures/Items/iron_helmet.png", "Steel headguard. Boosts defense.", 8f, 0f));
        add(equipment("iron_chestplate", "Iron Chestplate", Item.Category.CHESTPLATE,
                new ColorRGBA(0.62f, 0.66f, 0.72f, 1f), 65,
                "Textures/Items/iron_chestplate.png", "Heavy plate. Stronger defense boost.", 14f, 0f));
        add(equipment("iron_leggings", "Iron Leggings", Item.Category.LEGGINGS,
                new ColorRGBA(0.62f, 0.66f, 0.72f, 1f), 55,
                null, "Articulated greaves. Small movement-speed bonus.", 0f, 0.6f));
        add(equipment("iron_boots", "Iron Boots", Item.Category.BOOTS,
                new ColorRGBA(0.62f, 0.66f, 0.72f, 1f), 45,
                "Textures/Items/iron_boot.png", "Sturdy sabatons. Larger movement-speed bonus.", 0f, 1.2f));
    }

    /** Convenience constructor for materials with a rarity tier. */
    private static Item material(String id, String name, ColorRGBA color, int value,
                                 String icon, String desc, int tier) {
        return new Item(id, name, Item.Category.MATERIAL, color, 99, value,
                null, icon, desc, Item.Effect.NONE, 0f, 0f, 0f, 0f, tier);
    }

    /** Convenience constructor for equipment (unique, grants stat bonuses). */
    private static Item equipment(String id, String name, Item.Category cat, ColorRGBA color,
                                  int value, String icon, String desc, float defense, float moveSpeed) {
        return new Item(id, name, cat, color, 1, value,
                null, icon, desc, Item.Effect.NONE, 0f, 0f, defense, moveSpeed, 0);
    }

    public static void add(Item item) {
        REGISTRY.put(item.id, item);
    }

    public static Item get(String id) {
        return REGISTRY.get(id);
    }

    public static boolean exists(String id) {
        return REGISTRY.containsKey(id);
    }
}