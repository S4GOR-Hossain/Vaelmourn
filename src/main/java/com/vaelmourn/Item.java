package com.vaelmourn;

import com.jme3.math.ColorRGBA;

/**
 * A single stackable item that lives in the player's inventory.
 * The visual icon is rendered as a colored cell on the HUD; the colour
 * helps identify the item category at a glance (mirroring the packed
 * screenshot style the UI was designed around).
 */
public class Item {

    public enum Category {
        WEAPON,
        POTION,
        KEY,
        MATERIAL,
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        SHIELD,
        BOOTS
    }

    public final String id;
    public final String name;
    public final Category category;
    public final ColorRGBA iconColor;
    public final int maxStack;
    public final int value;          // in soul dust, if it can be sold
    public final String modelPath;   // optional 3D model for previews
    public final String iconPath;    // optional 2D icon texture for the HUD

    public Item(String id, String name, Category category, ColorRGBA iconColor,
                int maxStack, int value, String modelPath, String iconPath) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.iconColor = iconColor;
        this.maxStack = maxStack;
        this.value = value;
        this.modelPath = modelPath;
        this.iconPath = iconPath;
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
