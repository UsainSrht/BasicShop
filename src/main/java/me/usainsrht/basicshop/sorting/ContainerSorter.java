package me.usainsrht.basicshop.sorting;

import me.usainsrht.basicshop.sorting.ItemCategorizer.Category;
import me.usainsrht.basicshop.sorting.ItemCategorizer.EquipmentType;
import me.usainsrht.basicshop.sorting.ItemCategorizer.MaterialTier;
import me.usainsrht.basicshop.sorting.ItemCategorizer.ProgressionChain;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Multi-pass algorithmic container sorter.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Stack consolidation</li>
 *   <li>Pass 1: Profile scanning (Armory, Ore, Dump)</li>
 *   <li>Pass 2: Priority placement & structural formations (Armor/Tool grids, Progression sequencing, Centering)</li>
 *   <li>Pass 3: Bulk item distribution with category gapping (2 empty slots) & row snapping (snap to next row if past col 4)</li>
 *   <li>Pass 4: Flatten 2D grid, validate checksum safety, apply via {@link Inventory#setContents(ItemStack[])}</li>
 * </ol>
 */
public final class ContainerSorter {

    public enum Profile {
        ARMORY,
        ORE,
        DUMP
    }

    private ContainerSorter() {}

    /**
     * Executes single-tick container sorting for a player.
     *
     * @param player    The player using the sorting staff
     * @param container The targeted container block state
     * @return {@code true} if sorted successfully, {@code false} if inventory was empty or invalid
     */
    public static boolean sortContainer(Player player, Container container) {
        if (container == null) return false;
        Inventory inv = container.getInventory();
        ItemStack[] originalContents = inv.getContents();
        if (originalContents == null || originalContents.length == 0) return false;

        // 1. Stack Consolidation
        List<ItemStack> consolidated = consolidateItems(originalContents);
        if (consolidated.isEmpty()) return false;

        // Build item checksum for fail-safe safety validation
        Map<ItemKey, Integer> originalChecksum = buildChecksum(consolidated);

        int totalSize = originalContents.length;
        int cols = (totalSize % 9 == 0) ? 9 : Math.min(totalSize, 9);
        int rows = (totalSize + cols - 1) / cols;

        // Pass 1: Profile scanning
        Profile profile = scanProfile(consolidated);

        // Pass 2 & 3: Multi-pass grid layout calculation
        ItemStack[] sortedContents = calculateSortedContents(consolidated, profile, rows, cols, totalSize);

        // Pass 4: Checksum safety validation
        Map<ItemKey, Integer> sortedChecksum = buildChecksum(Arrays.asList(sortedContents));
        if (!checksumMatches(originalChecksum, sortedChecksum)) {
            // Fallback: tight packed sort without gaps to guarantee no item loss
            sortedContents = createFallbackTightSort(consolidated, totalSize);
        }

        // Apply single-tick
        inv.setContents(sortedContents);
        return true;
    }

    /**
     * Consolidates duplicate stackable items up to their max stack size.
     */
    public static List<ItemStack> consolidateItems(ItemStack[] contents) {
        List<ItemStack> list = new ArrayList<>();
        if (contents == null) return list;

        for (ItemStack item : contents) {
            if (item == null || ItemCategorizer.isAir(item.getType()) || item.getAmount() <= 0) continue;

            ItemStack clone = item.clone();
            boolean merged = false;

            for (ItemStack existing : list) {
                if (existing.isSimilar(clone)) {
                    int maxStack = existing.getMaxStackSize();
                    int space = maxStack - existing.getAmount();
                    if (space > 0) {
                        int toAdd = Math.min(space, clone.getAmount());
                        existing.setAmount(existing.getAmount() + toAdd);
                        clone.setAmount(clone.getAmount() - toAdd);
                        if (clone.getAmount() <= 0) {
                            merged = true;
                            break;
                        }
                    }
                }
            }

            if (!merged && clone.getAmount() > 0) {
                list.add(clone);
            }
        }
        return list;
    }

    /**
     * Pass 1: Profiles inventory contents to select the dominant template.
     */
    public static Profile scanProfile(List<ItemStack> items) {
        if (items.isEmpty()) return Profile.DUMP;

        int equipmentCount = 0;
        int oreCount = 0;
        int totalStacks = items.size();

        for (ItemStack stack : items) {
            Category cat = ItemCategorizer.getCategory(stack);
            if (cat == Category.EQUIPMENT) equipmentCount++;
            else if (cat == Category.MINERAL_PROGRESSION) oreCount++;
        }

        double equipRatio = (double) equipmentCount / totalStacks;
        double oreRatio = (double) oreCount / totalStacks;

        if (equipRatio >= 0.35 && equipRatio >= oreRatio) {
            return Profile.ARMORY;
        } else if (oreRatio >= 0.35) {
            return Profile.ORE;
        } else {
            return Profile.DUMP;
        }
    }

    /**
     * Multi-pass grid layout calculation.
     */
    private static ItemStack[] calculateSortedContents(List<ItemStack> items, Profile profile, int rows, int cols, int totalSize) {
        ItemStack[][] grid = new ItemStack[rows][cols];
        boolean[][] locked = new boolean[rows][cols];

        List<ItemStack> remaining = new ArrayList<>(items);

        // Pass 2: Priority Items (Symmetry and Centering)
        List<ItemStack> priorityItems = extractPriorityItems(remaining);
        placePriorityItems(grid, locked, priorityItems, rows, cols);

        // Pass 2: Structural Formations based on Profile
        if (profile == Profile.ARMORY) {
            placeArmoryGrid(grid, locked, remaining, rows, cols);
        } else if (profile == Profile.ORE) {
            placeProgressionGrid(grid, locked, remaining, rows, cols);
        }

        // Pass 3: Category Gapping & Row Snapping for remaining bulk items
        placeBulkItemsWithGapping(grid, locked, remaining, rows, cols);

        // Flatten 2D grid to 1D array
        ItemStack[] flat = new ItemStack[totalSize];
        int index = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (index < totalSize) {
                    flat[index++] = grid[r][c];
                }
            }
        }

        return flat;
    }

    private static List<ItemStack> extractPriorityItems(List<ItemStack> remaining) {
        List<ItemStack> priority = new ArrayList<>();
        var iterator = remaining.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (ItemCategorizer.isPriority(stack)) {
                priority.add(stack);
                iterator.remove();
            }
        }
        return priority;
    }

    /**
     * Places high-value / priority items in central slots of the grid.
     */
    private static void placePriorityItems(ItemStack[][] grid, boolean[][] locked, List<ItemStack> priorityItems, int rows, int cols) {
        if (priorityItems.isEmpty()) return;

        int centerRow = rows / 2;
        int centerCol = cols / 2;

        // Preferred order of central slots around grid middle
        List<int[]> centerSlots = new ArrayList<>();
        centerSlots.add(new int[]{centerRow, centerCol});
        for (int offset = 1; offset < cols; offset++) {
            if (centerCol - offset >= 0) centerSlots.add(new int[]{centerRow, centerCol - offset});
            if (centerCol + offset < cols) centerSlots.add(new int[]{centerRow, centerCol + offset});
        }

        int itemIdx = 0;
        for (int[] pos : centerSlots) {
            if (itemIdx >= priorityItems.size()) break;
            int r = pos[0];
            int c = pos[1];
            if (!locked[r][c] && grid[r][c] == null) {
                grid[r][c] = priorityItems.get(itemIdx++);
                locked[r][c] = true;
            }
        }
    }

    /**
     * Armory Profile Structural Grid:
     * Col 0: Helmets | Col 1: Chestplates | Col 2: Leggings | Col 3: Boots
     * Col 4: Swords | Col 5: Pickaxes | Col 6: Axes | Col 7: Other Tools
     * Sorted descending by Material Tier (Netherite -> Leather).
     */
    private static void placeArmoryGrid(ItemStack[][] grid, boolean[][] locked, List<ItemStack> remaining, int rows, int cols) {
        List<ItemStack> equipment = new ArrayList<>();
        var iter = remaining.iterator();
        while (iter.hasNext()) {
            ItemStack stack = iter.next();
            if (ItemCategorizer.getCategory(stack) == Category.EQUIPMENT) {
                equipment.add(stack);
                iter.remove();
            }
        }

        // Sort by equipment column, then descending by MaterialTier weight
        equipment.sort((a, b) -> {
            EquipmentType typeA = ItemCategorizer.getEquipmentType(a.getType());
            EquipmentType typeB = ItemCategorizer.getEquipmentType(b.getType());
            if (typeA.getColumnIndex() != typeB.getColumnIndex()) {
                return Integer.compare(typeA.getColumnIndex(), typeB.getColumnIndex());
            }
            MaterialTier tierA = ItemCategorizer.getMaterialTier(a.getType());
            MaterialTier tierB = ItemCategorizer.getMaterialTier(b.getType());
            return Integer.compare(tierB.getWeight(), tierA.getWeight());
        });

        // Track next available row for each equipment column
        int[] nextRow = new int[cols];

        for (ItemStack equip : equipment) {
            EquipmentType type = ItemCategorizer.getEquipmentType(equip.getType());
            int targetCol = Math.min(type.getColumnIndex(), cols - 1);
            if (targetCol < 0) targetCol = 0;

            int targetRow = nextRow[targetCol];
            while (targetRow < rows && (locked[targetRow][targetCol] || grid[targetRow][targetCol] != null)) {
                targetRow++;
            }

            if (targetRow < rows) {
                grid[targetRow][targetCol] = equip;
                locked[targetRow][targetCol] = true;
                nextRow[targetCol] = targetRow + 1;
            } else {
                // If column is full, return item back to remaining pool
                remaining.add(equip);
            }
        }
    }

    /**
     * Ore Profile Progression Grid:
     * Groups crafting lifecycles (e.g., Raw -> Ingot -> Nugget -> Block).
     * Reserves empty space (null) for missing stages in a sequence.
     */
    private static void placeProgressionGrid(ItemStack[][] grid, boolean[][] locked, List<ItemStack> remaining, int rows, int cols) {
        Map<ProgressionChain, List<ItemStack>> chainMap = new EnumMap<>(ProgressionChain.class);
        var iter = remaining.iterator();
        while (iter.hasNext()) {
            ItemStack stack = iter.next();
            ProgressionChain chain = ItemCategorizer.getProgressionChain(stack.getType());
            if (chain != ProgressionChain.NONE) {
                chainMap.computeIfAbsent(chain, k -> new ArrayList<>()).add(stack);
                iter.remove();
            }
        }

        int currentRow = 0;
        for (ProgressionChain chain : ProgressionChain.values()) {
            if (chain == ProgressionChain.NONE) continue;
            List<ItemStack> chainItems = chainMap.get(chain);
            if (chainItems == null || chainItems.isEmpty()) continue;

            while (currentRow < rows && rowIsOccupied(locked, currentRow, cols)) {
                currentRow++;
            }
            if (currentRow >= rows) {
                // Grid full, put leftovers back
                remaining.addAll(chainItems);
                continue;
            }

            // Progression stages: 0=Raw, 1=Ingot/Gem, 2=Nugget, 3=Block
            ItemStack[] stageArray = new ItemStack[4];
            for (ItemStack item : chainItems) {
                int stage = ItemCategorizer.getProgressionStage(item.getType());
                if (stage >= 0 && stage < 4 && stageArray[stage] == null) {
                    stageArray[stage] = item;
                } else {
                    remaining.add(item); // Extra stacks placed in bulk
                }
            }

            // Reserve space for progression lifecycle:
            // Place present items at their stage column, leaving null for missing stages
            for (int stage = 0; stage < 4; stage++) {
                int col = stage;
                if (col < cols && !locked[currentRow][col]) {
                    if (stageArray[stage] != null) {
                        grid[currentRow][col] = stageArray[stage];
                        locked[currentRow][col] = true;
                    } else {
                        // Reserved empty slot
                        locked[currentRow][col] = true;
                    }
                }
            }
            currentRow++;
        }
    }

    private static boolean rowIsOccupied(boolean[][] locked, int row, int cols) {
        for (int c = 0; c < cols; c++) {
            if (locked[row][c]) return true;
        }
        return false;
    }

    /**
     * Pass 3: Category Gapping & Row Snapping.
     * Groups remaining bulk items by category & base material.
     * Inserts 2 empty whitespace slots between distinct categories/subcategories.
     * Snaps to next row if previous category ended past 4th column (col >= 4).
     */
    private static void placeBulkItemsWithGapping(ItemStack[][] grid, boolean[][] locked, List<ItemStack> remaining, int rows, int cols) {
        if (remaining.isEmpty()) return;

        // Group items by category & sub-category (base material)
        Map<String, List<ItemStack>> groups = new LinkedHashMap<>();
        for (ItemStack item : remaining) {
            String key = ItemCategorizer.getCategory(item).name() + ":" + ItemCategorizer.getSubCategory(item);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        // Sort items within each sub-category
        for (List<ItemStack> groupList : groups.values()) {
            groupList.sort(ContainerSorter::compareItems);
        }

        int currRow = 0;
        int currCol = 0;
        boolean firstGroup = true;

        for (Map.Entry<String, List<ItemStack>> entry : groups.entrySet()) {
            List<ItemStack> groupItems = entry.getValue();
            if (groupItems.isEmpty()) continue;

            if (!firstGroup) {
                // Apply Category Gapping & Row Snapping
                if (currCol >= 4) {
                    // Row Snapping: snap to beginning of next row
                    currRow++;
                    currCol = 0;
                } else {
                    // Insert 2 empty slots (whitespace padding)
                    currCol += 2;
                    if (currCol >= cols) {
                        currRow += currCol / cols;
                        currCol = currCol % cols;
                    }
                }
            }
            firstGroup = false;

            for (ItemStack item : groupItems) {
                // Advance cursor to next unlocked slot
                while (currRow < rows) {
                    while (currCol < cols && (locked[currRow][currCol] || grid[currRow][currCol] != null)) {
                        currCol++;
                    }
                    if (currCol < cols) break;
                    currRow++;
                    currCol = 0;
                }

                if (currRow >= rows) {
                    // Inventory layout full — fill any remaining unlocked null slots tightly
                    fillRemainingTightly(grid, locked, item, rows, cols);
                    continue;
                }

                grid[currRow][currCol] = item;
                locked[currRow][currCol] = true;
                currCol++;
                if (currCol >= cols) {
                    currRow++;
                    currCol = 0;
                }
            }
        }
    }

    private static void fillRemainingTightly(ItemStack[][] grid, boolean[][] locked, ItemStack item, int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == null) {
                    grid[r][c] = item;
                    locked[r][c] = true;
                    return;
                }
            }
        }
    }

    /**
     * Fallback tight sort without whitespace gaps to guarantee 100% item safety.
     */
    private static ItemStack[] createFallbackTightSort(List<ItemStack> items, int totalSize) {
        List<ItemStack> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> {
            Category catA = ItemCategorizer.getCategory(a);
            Category catB = ItemCategorizer.getCategory(b);
            if (catA != catB) return Integer.compare(catA.ordinal(), catB.ordinal());
            return compareItems(a, b);
        });

        ItemStack[] array = new ItemStack[totalSize];
        for (int i = 0; i < sorted.size() && i < totalSize; i++) {
            array[i] = sorted.get(i);
        }
        return array;
    }

    /**
     * Compares two item stacks to group similar items together.
     */
    public static int compareItems(ItemStack a, ItemStack b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;

        Material matA = a.getType();
        Material matB = b.getType();

        // 1. Wood Items comparison (variant rank -> species -> name -> amount)
        boolean woodA = ItemCategorizer.isWood(matA);
        boolean woodB = ItemCategorizer.isWood(matB);
        if (woodA && woodB) {
            int varRankA = ItemCategorizer.getWoodVariantRank(matA);
            int varRankB = ItemCategorizer.getWoodVariantRank(matB);
            if (varRankA != varRankB) {
                return Integer.compare(varRankA, varRankB);
            }
            int speciesA = ItemCategorizer.getWoodSpeciesRank(matA);
            int speciesB = ItemCategorizer.getWoodSpeciesRank(matB);
            if (speciesA != speciesB) {
                return Integer.compare(speciesA, speciesB);
            }
        }

        // 2. Colored Blocks comparison (family rank -> color index -> name -> amount)
        boolean colA = ItemCategorizer.isColoredBlock(matA);
        boolean colB = ItemCategorizer.isColoredBlock(matB);
        if (colA && colB) {
            int famA = ItemCategorizer.getColorFamilyRank(matA);
            int famB = ItemCategorizer.getColorFamilyRank(matB);
            if (famA != famB) {
                return Integer.compare(famA, famB);
            }
            int colorA = ItemCategorizer.getColorIndex(matA);
            int colorB = ItemCategorizer.getColorIndex(matB);
            if (colorA != colorB) {
                return Integer.compare(colorA, colorB);
            }
        }

        // 3. Stone Variants comparison (variant rank -> name -> amount)
        boolean stoneA = ItemCategorizer.isStone(matA);
        boolean stoneB = ItemCategorizer.isStone(matB);
        if (stoneA && stoneB) {
            int rankA = ItemCategorizer.getStoneVariantRank(matA);
            int rankB = ItemCategorizer.getStoneVariantRank(matB);
            if (rankA != rankB) {
                return Integer.compare(rankA, rankB);
            }
        }

        // 4. Default material name comparison
        int matCmp = matA.name().compareTo(matB.name());
        if (matCmp != 0) return matCmp;

        // 5. Stack size descending
        return Integer.compare(b.getAmount(), a.getAmount());
    }

    /**
     * Record representing a unique item type & metadata for checksum verification.
     */
    private record ItemKey(Material material, ItemStack sample) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemKey itemKey = (ItemKey) o;
            if (material != itemKey.material) return false;
            return sample.isSimilar(itemKey.sample);
        }

        @Override
        public int hashCode() {
            return Objects.hash(material);
        }
    }

    private static Map<ItemKey, Integer> buildChecksum(List<ItemStack> items) {
        Map<ItemKey, Integer> map = new HashMap<>();
        for (ItemStack item : items) {
            if (item == null || ItemCategorizer.isAir(item.getType()) || item.getAmount() <= 0) continue;
            ItemKey key = new ItemKey(item.getType(), item);
            map.put(key, map.getOrDefault(key, 0) + item.getAmount());
        }
        return map;
    }

    private static boolean checksumMatches(Map<ItemKey, Integer> original, Map<ItemKey, Integer> sorted) {
        if (original.size() != sorted.size()) return false;
        for (Map.Entry<ItemKey, Integer> entry : original.entrySet()) {
            Integer sortedCount = sorted.get(entry.getKey());
            if (!Objects.equals(entry.getValue(), sortedCount)) {
                return false;
            }
        }
        return true;
    }
}
