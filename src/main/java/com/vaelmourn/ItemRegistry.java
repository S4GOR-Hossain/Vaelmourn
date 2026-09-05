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
        // Weapons
        add(new Item("iron_sword", "Iron Sword", Item.Category.WEAPON, new ColorRGBA(0.7f, 0.7f, 0.75f, 1f), 1, 40, "Models/Weapons/Melee/Sword.glb", "Textures/Items/iron_sword.png"));
        add(new Item("dagger", "Dagger", Item.Category.WEAPON, new ColorRGBA(0.6f, 0.6f, 0.65f, 1f), 1, 20, null));
        add(new Item("longbow", "Longbow", Item.Category.WEAPON, new ColorRGBA(0.55f, 0.4f, 0.25f, 1f), 1, 35, null, "Textures/Items/wooden_bow.png"));
        add(new Item("pistol", "Pistol", Item.Category.WEAPON, new ColorRGBA(0.2f, 0.2f, 0.25f, 1f), 1, 30, null));

        // Potions
        add(new Item("health_potion", "Health Potion", Item.Category.POTION, new ColorRGBA(0.9f, 0.2f, 0.2f, 1f), 10, 8, null, "Textures/Items/health_potion.png"));
        add(new Item("mana_potion", "Mana Potion", Item.Category.POTION, new ColorRGBA(0.2f, 0.4f, 0.9f, 1f), 10, 8, null, "Textures/Items/mana_potion.png"));

        // Keys
        add(new Item("dungeon_key", "Dungeon Key", Item.Category.KEY, new ColorRGBA(0.9f, 0.8f, 0.2f, 1f), 5, 15, null, "Textures/Items/dungeon_key.png"));
        add(new Item("boss_key", "Boss Key", Item.Category.KEY, new ColorRGBA(0.6f, 0.2f, 0.8f, 1f), 5, 25, null));

        // Materials
        add(new Item("soul_dust", "Soul Dust", Item.Category.MATERIAL, new ColorRGBA(0.6f, 0.9f, 1.0f, 1f), 99, 1, null));
        add(new Item("iron_ingot", "Iron Ingot", Item.Category.MATERIAL, new ColorRGBA(0.6f, 0.65f, 0.7f, 1f), 99, 3, null, "Textures/Items/iron_ingot.png"));
        add(new Item("leather", "Leather", Item.Category.MATERIAL, new ColorRGBA(0.55f, 0.4f, 0.3f, 1f), 99, 2, null, "Textures/Items/leather.png"));

        // Armor
        add(new Item("iron_helmet", "Iron Helmet", Item.Category.HELMET, new ColorRGBA(0.62f, 0.66f, 0.72f, 1f), 1, 25, null));
        add(new Item("iron_chestplate", "Iron Chestplate", Item.Category.CHESTPLATE, new ColorRGBA(0.62f, 0.66f, 0.72f, 1f), 1, 35, null));
        add(new Item("iron_leggings", "Iron Leggings", Item.Category.LEGGINGS, new ColorRGBA(0.62f, 0.66f, 0.72f, 1f), 1, 30, null));
        add(new Item("iron_boots", "Iron Boots", Item.Category.BOOTS, new ColorRGBA(0.62f, 0.66f, 0.72f, 1f), 1, 20, null));
        add(new Item("kite_shield", "Kite Shield", Item.Category.SHIELD, new ColorRGBA(0.55f, 0.5f, 0.45f, 1f), 1, 25, "Models/Weapons/Special/kite_shield.glb"));
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
