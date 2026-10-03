package com.vaelmourn;

import com.jme3.math.ColorRGBA;

/**
 * A single stackable inventory item; the HUD icon colour encodes its category.
 */
public class Item {

    /**
     * Item categories.
     *
     * <p>HELMET/CHESTPLATE/LEGGINGS/BOOTS were removed with the armour tier and
     * material system, and SHIELD was removed when the kite shield went with the
     * multi-weapon rework. THROWABLE covers the bomb: bought from the merchant,
     * stacked, and thrown on a keypress rather than equipped or drunk.</p>
     */
    public enum Category {
        WEAPON,
        CONSUMABLE,
        KEY,
        MATERIAL,
        THROWABLE
    }

    /** Coarse bucket every category maps into, so generic systems can ask "is this a consumable?" without knowing every specific sub-category. */
    public enum Group {
        CONSUMABLE,
        EQUIPMENT,
        WEAPON,
        MATERIAL,
        KEY,
        THROWABLE
    }

    public enum Effect {
        NONE,
        HEAL_INSTANT,
        REGEN,
        SPEED,
        STRENGTH,
        CRIT,
        /** Thrown on use; fuses for {@link #duration} seconds then bursts for {@link #power} damage. */
        THROW_EXPLOSIVE
    }

    public final String id;
    public final String name;
    public final Category category;
    public final Group group;
    public final ColorRGBA iconColor;
    public final int maxStack;
    public final int value;          // in soul dust, if it can be sold
    public final String modelPath;
    public final String iconPath;
    public final String description;

    public final Effect effect;
    public final float power;        // heal amount, regen hp/sec, or buff magnitude (0..1)
    public final float duration;

    public final float defenseBonus;
    public final float moveSpeedBonus;

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
            case THROWABLE -> Group.THROWABLE;
        };
    }

    public Group getGroup() {
        return group;
    }

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