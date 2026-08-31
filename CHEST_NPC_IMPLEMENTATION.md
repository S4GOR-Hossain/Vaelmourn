# Chest and NPC Shop System Implementation

## Overview
Successfully implemented a chest and NPC shop system for the Vaelmourn 3D roguelike action RPG. The starting stage (Sanctuary/Forest biome) now has 3 interactive chests and 2 NPC shopkeepers that players can interact with using the F key.

## Features Implemented

### 1. **Interactable Interface** (`Interactable.java`)
- Base interface for all interactive objects (chests, NPCs, etc.)
- Methods: `getPosition()`, `isInRange()`, `interact()`, `getNode()`, `cleanup()`
- Allows flexible extension for future interactive elements

### 2. **Chest System** (`Chest.java`)
- Standalone interactable object that contains loot items
- Visually represented as a brown box (0.5x0.6x0.5 units)
- Physics-enabled (static collision box)
- Can store multiple item stacks with different counts
- Tracks opened/closed state

### 3. **Chest UI** (`ChestUI.java`)
- Displays chest contents in a scrollable list format
- Navigation with W/S or UP/DOWN arrow keys
- Loot items by pressing ENTER or clicking
- Items are added to player inventory when looted
- Can close with ESC or E key
- Shows item names and quantities (e.g., "health_potion x3")

### 4. **NPC System** (`NPC.java`)
- Non-player character represented as a green cylinder with a head
- Acts as a shop keeper/merchant
- Stores a list of items with soul dust prices
- Physically interactable (static collision capsule)
- Customizable names and inventory

### 5. **Shop UI** (`ShopUI.java`)
- Merchant shop interface showing available items for purchase
- Displays current soul dust balance
- Navigate with W/S or UP/DOWN keys
- Buy items with ENTER or mouse click
- Shows item names and prices in soul dust
- Validates purchases (checks if player has enough soul dust)
- Close with ESC or E key

### 6. **Integration with ForestBiome** (`ForestBiome.java`)
- Added F key input handler for interactions
- Added `interactables` list to track all interactive objects
- Created `spawnChestsAndNPCs()` method that:
  - Spawns 3 chests at strategic locations
  - Populates chests with varied loot
  - Spawns 2 NPCs (Merchant Elara and Blacksmith Kain)
  - Sets up their shop inventories with prices
- Integrated interaction checking in `handleInteract()` method
- Checks proximity (3.5 unit range) before allowing interaction

## Spawned Content

### Chests (3 total)
1. **Chest at (10, 1, -15)**
   - 3x health_potion
   - 2x mana_potion
   - 5x iron_ingot

2. **Chest at (-20, 1, 10)**
   - 8x leather
   - 1x dungeon_key
   - 2x health_potion

3. **Chest at (5, 1, 25)**
   - 1x iron_sword
   - 10x iron_ingot
   - 4x mana_potion

### NPCs (2 total)

1. **Merchant Elara at (-10, 0, -20)**
   - Sells consumables and crafting materials:
     - health_potion: 15 soul dust
     - mana_potion: 20 soul dust
     - iron_ingot: 25 soul dust
     - leather: 10 soul dust

2. **Blacksmith Kain at (15, 0, 5)**
   - Sells equipment and armor:
     - iron_sword: 100 soul dust
     - iron_helmet: 80 soul dust
     - iron_chestplate: 120 soul dust
     - iron_boots: 60 soul dust

## User Controls

| Action | Key |
|--------|-----|
| Navigate Menu | W/S or UP/DOWN |
| Select/Buy | ENTER or LEFT CLICK |
| Close Menu | ESC or E |
| Interact with Objects | F |

## Technical Details

- **Physics**: All interactables have proper collision shapes and are registered with BulletAppState
- **UI Rendering**: Uses jME3 BitmapText and Geometry for rendering (orthographic overlay)
- **Input Handling**: Integrated with ForestBiome's input system, respects game state (no interaction while inventory is open)
- **Item System**: Leverages existing ItemRegistry for item definitions and properties
- **Currency**: Uses existing PlayerStats soul dust tracking system

## Compilation Status
✅ **BUILD SUCCESS** - All 32 source files compile without errors

## Future Enhancement Opportunities
- Add animations when opening/closing chests
- Add particle effects or sounds on interaction
- Implement NPC dialogue before shopping
- Add special/rare items from chests
- Implement chest respawning with difficulty scaling
- Add merchant discount/reputation system
