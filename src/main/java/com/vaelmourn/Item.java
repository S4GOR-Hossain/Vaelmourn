package com.vaelmourn;

import com.jme3.math.ColorRGBA;

/**
 * A single stackable item that lives in the player's inventory.
 * The visual icon is rendered as a colored cell on the HUD; the colour
 * helps identify the item category at a glance (mirroring the packed
 * screenshot style the UI was designed around).
 *
 * Each item carries the data gameplay needs: which broad group it belongs to
 * (consumable / equipment / weapon / material / key), its consumable effect
 * if it has one (potion type, power, duration), the stat bonuses it grants
 * while equipped, and a material tier for the future Forge/crafting step.
 */
public class Item {

    public enum Category {
        WEAPON,
        CONSUMABLE,
        KEY,
        MATERIAL,
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        SHIELD,
        BOOTS
    }

    /** Coarse bucket every category maps into, so generic systems can ask
     *  "is this a consumable?" without knowing every specific sub-category. */
    public enum Group {
        CONSUMABLE,
        EQUIPMENT,
        WEAPON,
        MATERIAL,
        KEY
    }

    /** What a consumable does when used. NONE means "not consumable". */
    public enum Effect {
        NONE,
        HEAL_INSTANT,
        REGEN,
        SPEED,
        STRENGTH,
        CRIT
    }

    public final String id;
    public final String name;
    public final Category category;
    public final Group group;
    public final ColorRGBA iconColor;
    public final int maxStack;
    public final int value;          // in soul dust, if it can be sold
    public final String modelPath;   // optional 3D model for previews
    public final String iconPath;    // optional 2D icon texture for the HUD
    public final String description; // one-liner shown in the inventory tooltip

    // consumable behaviour
    public final Effect effect;
    public final float power;        // heal amount, regen hp/sec, or buff magnitude (0..1)
    public final float duration;     // seconds for duration-based effects

    // equipment bonuses (only meaningful for the four armor categories)
    public final float defenseBonus;
    public final float moveSpeedBonus;

    // materials rank so a future Forge can gate recipes by rarity
    public final int materialTier;   // 0 = not a material, 1 basic ... 3/4 rare/epic

    public Item(String id, String name, Category category, ColorRGBA iconColor,
                int maxStack, int value, String modelPath, String iconPath,
                String description, Effect effect, float power, float duration,
                float defenseBonus, float moveSpeedBonus, int materialTier) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.group = groupFor(category);
        this.iconColor = iconColor;
        this.maxStack = maxStack;
        this.value = value;
        this.modelPath = modelPath;
        this.iconPath = iconPath;
        this.description = description;
        this.effect = effect;
        this.power = power;
        this.duration = duration;
        this.defenseBonus = defenseBonus;
        this.moveSpeedBonus = moveSpeedBonus;
        this.materialTier = materialTier;
    }

    public static Group groupFor(Category category) {
        return switch (category) {
            case WEAPON -> Group.WEAPON;
            case CONSUMABLE -> Group.CONSUMABLE;
            case KEY -> Group.KEY;
            case MATERIAL -> Group.MATERIAL;
            case HELMET, CHESTPLATE, LEGGINGS, SHIELD, BOOTS -> Group.EQUIPMENT;
        };
    }

    public Group getGroup() {
        return group;
    }

    // ---- convenient shorthands callers use to build items ----

    public Item(String id, String name, Category category, ColorRGBA iconColor,
                int maxStack, int value, String modelPath, String iconPath,
                String description) {
        this(id, name, category, iconColor, maxStack, value, modelPath, iconPath,
                description, Effect.NONE, 0f, 0f, 0f, 0f, 0);
    }

    public Item(String id, String name, Category category, ColorRGBA iconColor,
                int maxStack, int value, String modelPath, String iconPath,
                String description, Effect effect, float power, float duration) {
        this(id, name, category, iconColor, maxStack, value, modelPath, iconPath,
                description, effect, power, duration, 0f, 0f, 0);
    }

    public Item(String id, String name, Category category, ColorRGBA iconColor,
                int maxStack, int value, String modelPath, String iconPath) {
        this(id, name, category, iconColor, maxStack, value, modelPath, iconPath,
                null, Effect.NONE, 0f, 0f, 0f, 0f, 0);
    }

    public Item(String id, String name, Category category, ColorRGBA iconColor,
                int maxStack, int value, String modelPath) {
        this(id, name, category, iconColor, maxStack, value, modelPath, null);
    }

    public Item(String id, String name, Category category, ColorRGBA iconColor,
                int maxStack, int value) {
        this(id, name, category, iconColor, maxStack, value, null, null);
    }
}