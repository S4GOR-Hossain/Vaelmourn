package com.vaelmourn;

/**
 * The player's inventory: a 5x5 grid of storage slots, five equipment slots
 * (helmet, chestplate, leggings, shield, boots) and five primary toolbar slots
 * (weapon, potion / key items).
 */
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

    // ---- Grid access ----

    public Slot getGridSlot(int index) {
        return grid[index];
    }

    public Slot getGridSlot(int col, int row) {
        return grid[row * GRID_COLS + col];
    }

    public Slot[] getGrid() {
        return grid;
    }

    // ---- Equipment access ----

    public Slot getEquipSlot(EquipSlot slot) {
        return equipment[slot.ordinal()];
    }

    public Slot[] getEquipment() {
        return equipment;
    }

    // ---- Toolbar access ----

    public Slot getToolbarSlot(int index) {
        return toolbar[index];
    }

    public Slot[] getToolbar() {
        return toolbar;
    }

    // ---- Adding items ----

    /**
     * Tries to add an item to the grid, stacking where possible.
     * @return true if the whole stack was placed, false if some (or all)
     *         could not fit.
     */
    public boolean addItem(String itemId, int count) {
        Item item = ItemRegistry.get(itemId);
        if (item == null) return false;

        int remaining = count;

        // 1. Stack onto existing matching stacks (if stackable)
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

        // 2. Fill empty slots
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

    /**
     * @return the slot at (col,row) of the grid for the UI to manipulate.
     */
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
     * Moves the contents of slot {@code from} into slot {@code to}. If the two
     * slots hold the same stackable item, it stacks the quantities rather than
     * swapping. Otherwise the two slots' contents are swapped.
     *
     * Callers are responsible for enforcing any slot-compatibility rules
     * (equipment type, toolbar allowed categories) before invoking this.
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

        // swap contents
        String tmpId = to.itemId;
        int tmpCount = to.count;
        to.itemId = from.itemId;
        to.count = from.count;
        from.itemId = tmpId;
        from.count = tmpCount;
    }
}
