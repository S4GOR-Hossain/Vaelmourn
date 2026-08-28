package com.vaelmourn;

/**
 * Container that holds a stack of items in a single slot.
 */
public class Slot {

    public String itemId;
    public int count;

    public Slot(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    public boolean isEmpty() {
        return itemId == null || count <= 0;
    }

    public Item getItem() {
        if (itemId == null) return null;
        return ItemRegistry.get(itemId);
    }

    public void clear() {
        itemId = null;
        count = 0;
    }
}
