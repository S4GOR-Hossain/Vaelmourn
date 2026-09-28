package com.vaelmourn;

public class Inventory {

    public static final int GRID_COLS = 5;
    public static final int GRID_ROWS = 5;
    public static final int GRID_SIZE = GRID_COLS * GRID_ROWS;

    public enum EquipSlot {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        SHIELD,
        BOOTS
    }

    public static final EquipSlot[] EQUIPMENT_SLOTS = {
            EquipSlot.HELMET,
            EquipSlot.CHESTPLATE,
            EquipSlot.LEGGINGS,
            EquipSlot.SHIELD,
            EquipSlot.BOOTS
    };

    public static final int TOOLBAR_SIZE = 5;

    private final Slot[] grid = new Slot[GRID_SIZE];
    private final Slot[] equipment = new Slot[EQUIPMENT_SLOTS.length];
    private final Slot[] toolbar = new Slot[TOOLBAR_SIZE];

    public Inventory() {
        for (int i = 0; i < GRID_SIZE; i++) grid[i] = new Slot(null, 0);
        for (int i = 0; i < equipment.length; i++) equipment[i] = new Slot(null, 0);
        for (int i = 0; i < TOOLBAR_SIZE; i++) toolbar[i] = new Slot(null, 0);
    }

    /**
     * Empties every slot; arrays stay alive so UI/handlers still referencing this Inventory stay valid.
     */
    public void clearAll() {
        for (Slot s : grid) s.clear();
        for (Slot s : equipment) s.clear();
        for (Slot s : toolbar) s.clear();
    }

    public Slot getGridSlot(int index) {
        return grid[index];
    }

    public Slot getGridSlot(int col, int row) {
        return grid[row * GRID_COLS + col];
    }

    public Slot[] getGrid() {
        return grid;
    }

    public Slot getEquipSlot(EquipSlot slot) {
        return equipment[slot.ordinal()];
    }

    public Slot[] getEquipment() {
        return equipment;
    }

    public Slot getToolbarSlot(int index) {
        return toolbar[index];
    }

    public void removeFromToolbar(int index, int count) {
        if (index < 0 || index >= TOOLBAR_SIZE) return;
        Slot slot = toolbar[index];
        if (slot.isEmpty()) return;
        slot.count -= count;
        if (slot.count <= 0) slot.clear();
    }

    public Slot[] getToolbar() {
        return toolbar;
    }

    /**
     * @return true if the whole stack was placed, false if some (or all) could not fit.
     */
    public boolean addItem(String itemId, int count) {
        Item item = ItemRegistry.get(itemId);
        if (item == null) return false;

        int remaining = count;

        if (item.maxStack > 1) {
            for (Slot s : grid) {
                if (s.isEmpty() || !s.itemId.equals(itemId)) continue;
                int space = item.maxStack - s.count;
                if (space <= 0) continue;
                int placed = Math.min(space, remaining);
                s.count += placed;
                remaining -= placed;
                if (remaining <= 0) return true;
            }
        }

        for (Slot s : grid) {
            if (!s.isEmpty()) continue;
            int placed = Math.min(item.maxStack, remaining);
            s.itemId = itemId;
            s.count = placed;
            remaining -= placed;
            if (remaining <= 0) return true;
        }

        return remaining <= 0;
    }

    public Slot slotAt(int col, int row) {
        return getGridSlot(col, row);
    }

    public boolean hasItem(String itemId, int count) {
        int total = 0;
        for (Slot s : grid) {
            if (s.itemId != null && s.itemId.equals(itemId)) total += s.count;
        }
        return total >= count;
    }

    /**
     * Moves slot {@code from} into {@code to}: matching stackable items merge
     * quantities, otherwise the two slots swap. Callers must enforce
     * slot-compatibility rules (equipment type, toolbar categories) first.
     */
    public void swapMove(Slot from, Slot to) {
        if (from == null || to == null) return;
        if (from.isEmpty()) return;

        Item fromItem = from.getItem();
        if (fromItem == null) return;

        if (!to.isEmpty()) {
            Item toItem = to.getItem();
            if (toItem != null && fromItem.id.equals(toItem.id) && fromItem.maxStack > 1) {
                int space = fromItem.maxStack - to.count;
                int moved = Math.min(space, from.count);
                if (moved > 0) {
                    to.count += moved;
                    from.count -= moved;
                    if (from.count <= 0) from.clear();
                    return;
                }
            }
        }

        String tmpId = to.itemId;
        int tmpCount = to.count;
        to.itemId = from.itemId;
        to.count = from.count;
        from.itemId = tmpId;
        from.count = tmpCount;
    }
}
