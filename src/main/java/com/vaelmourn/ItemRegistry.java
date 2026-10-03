package com.vaelmourn;

import com.jme3.math.ColorRGBA;

import java.util.HashMap;
import java.util.Map;

public final class ItemRegistry {

    private static final Map<String, Item> REGISTRY = new HashMap<>();

    private ItemRegistry() {
    }

    public static void registerDefaults() {
        // The one and only weapon. The dagger, hunters' blade, heavy blade, longbow,
        // pistol and kite shield were all removed when the multi-weapon set collapsed
        // to a single blade; their stats went with them.
        add(new Item("iron_sword", "Iron Sword", Item.Category.WEAPON, new ColorRGBA(0.7f, 0.7f, 0.75f, 1f),
                1, 40, "Models/Weapons/Melee/Sword.glb", "Textures/Items/iron_sword.png",
                "A balanced one-handed blade."));

        // Bought from the potion merchant and thrown with Q. Not equippable: it stacks,
        // fuses for Bombs.FUSE seconds and bursts for Bombs.BLAST_DAMAGE in a radius.
        // icon path is set even though the PNG is not in the repo yet: loadItemTexture()
        // swallows the miss and falls back to the colour swatch, so it lights up as soon
        // as Textures/Items/bomb.png lands
        add(new Item(Bombs.ITEM_ID, "Bomb", Item.Category.THROWABLE, new ColorRGBA(0.95f, 0.55f, 0.15f, 1f),
                10, 50, null, "Textures/Items/bomb.png",
                "Thrown. Fuses for 3s then bursts for 50 damage in a wide radius.",
                Item.Effect.THROW_EXPLOSIVE, Bombs.BLAST_DAMAGE, Bombs.FUSE));

        add(new Item("health_potion", "Health Potion", Item.Category.CONSUMABLE, new ColorRGBA(0.9f, 0.2f, 0.2f, 1f),
                10, 8, null, "Textures/Items/health_potion.png",
                "Restores 40 HP instantly.",
                Item.Effect.HEAL_INSTANT, 40f, 0f));
        // replaced the old mana potion — a burst of fleetness instead
        add(new Item("speed_potion", "Speed Potion", Item.Category.CONSUMABLE, new ColorRGBA(0.3f, 0.85f, 0.95f, 1f),
                10, 10, null, "Textures/Items/speed_potion.png",
                "Boosts movement and attack speed 40% for 20s.",
                Item.Effect.SPEED, 0.4f, 20f));
        add(new Item("strength_potion", "Strength Potion", Item.Category.CONSUMABLE, new ColorRGBA(0.95f, 0.45f, 0.2f, 1f),
                10, 12, null, "Textures/Items/strength_potion.png",
                "Boosts damage 25% for 20s.",
                Item.Effect.STRENGTH, 0.25f, 20f));
        add(new Item("critical_potion", "Critical Potion", Item.Category.CONSUMABLE, new ColorRGBA(1f, 0.85f, 0.25f, 1f),
                10, 14, null, "Textures/Items/critical_potion.png",
                "Boosts critical chance for 20s.",
                Item.Effect.CRIT, 0.15f, 20f));
        add(new Item("regen_potion", "Regeneration Potion", Item.Category.CONSUMABLE, new ColorRGBA(0.3f, 0.75f, 0.35f, 1f),
                10, 10, null, "Textures/Items/regeneration_potion.png",
                "Regenerates 5 HP per second for 20s.",
                Item.Effect.REGEN, 5f, 20f));

        // keys: progression items, never consumed like potions
        add(new Item("dungeon_key", "Dungeon Key", Item.Category.KEY, new ColorRGBA(0.9f, 0.8f, 0.2f, 1f),
                5, 15, null, "Textures/Items/dungeon_key.png",
                "Opens sealed doors in the dungeon biomes."));
        add(new Item("boss_key", "Boss Key", Item.Category.KEY, new ColorRGBA(0.6f, 0.2f, 0.8f, 1f),
                5, 25, null, null, "Opens the boss chamber door."));

        add(material("soul_dust", "Soul Dust", new ColorRGBA(0.6f, 0.9f, 1.0f, 1f), 1, null,
                "Currency left behind by fallen enemies."));

        // ---- biome gems -------------------------------------------------------
        // One unique, non-stackable gem per biome arc. Registered straight from the
        // progression table so the item id, the model and the biome never drift apart.
        // They live in the KEY group (never consumed as potions) and keep maxStack 1 so
        // a second copy can never stack in the grid. The icon is derived from the model
        // filename (Models/props/Gem3.fbx -> Textures/Items/gem3.png) so the picture can
        // never drift onto the wrong gem.
        for (GemProgression.Gem gem : GemProgression.GEMS) {
            add(new Item(gem.id, gem.name, Item.Category.KEY, gem.color,
                    1, 0, gem.modelPath, gemIconPath(gem.modelPath),
                    "Required to challenge the " + gem.bossName + " in the "
                            + gem.biomeName + ". Lost if your run ends."));
        }

        // No defensive gear registers at all: the four iron armour pieces went with
        // the armour tier/material system (Defense is a Sanctuary run upgrade now),
        // and the kite shield went with the single-sword rework. That also retired
        // the equipment() helper below - nothing can be equipped any more.
    }

    /**
     * Gem icons are numbered to match their models, so derive the path from the model
     * instead of keeping a second hand-written list that could drift out of sync.
     *
     * @return e.g. {@code Textures/Items/gem3.png} for {@code Models/props/Gem3.fbx},
     *         or null if the model name does not follow that convention
     */
    private static String gemIconPath(String modelPath) {
        if (modelPath == null) return null;
        String file = modelPath.substring(modelPath.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        if (dot <= 0) return null;
        String stem = file.substring(0, dot);
        if (!stem.toLowerCase().startsWith("gem")) return null;
        return "Textures/Items/" + stem.toLowerCase() + ".png";
    }

    private static Item material(String id, String name, ColorRGBA color, int value,
                                 String icon, String desc) {
        return new Item(id, name, Item.Category.MATERIAL, color, 99, value,
                null, icon, desc, Item.Effect.NONE, 0f, 0f, 0f, 0f, 0);
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