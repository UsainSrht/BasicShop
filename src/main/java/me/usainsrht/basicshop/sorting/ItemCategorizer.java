package me.usainsrht.basicshop.sorting;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Categorizes Minecraft items into priority items, equipment slots, material tiers,
 * mineral progression lifecycles, base materials, wood species/variants, and colored block families.
 * Uses Bukkit Tags (org.bukkit.Tag) and Paper MaterialTags (com.destroystokyo.paper.MaterialTags).
 */
public final class ItemCategorizer {

    public enum Category {
        PRIORITY,
        EQUIPMENT,
        MINERAL_PROGRESSION,
        BUILDING_BLOCKS,
        FARMING,
        REDSTONE,
        MOB_DROPS,
        MISC
    }

    public enum EquipmentType {
        HELMET(0),
        CHESTPLATE(1),
        LEGGINGS(2),
        BOOTS(3),
        SWORD(4),
        PICKAXE(5),
        AXE(6),
        OTHER_TOOL(7),
        NOT_EQUIPMENT(-1);

        private final int columnIndex;

        EquipmentType(int columnIndex) {
            this.columnIndex = columnIndex;
        }

        public int getColumnIndex() {
            return columnIndex;
        }
    }

    public enum MaterialTier {
        NETHERITE(8),
        DIAMOND(7),
        IRON(6),
        GOLD(5),
        COPPER(4),
        CHAINMAIL_STONE(3),
        TURTLE(2),
        WOOD_LEATHER_OTHER(1);

        private final int weight;

        MaterialTier(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }
    }

    public enum ProgressionChain {
        NETHERITE(0),
        DIAMOND(1),
        EMERALD(2),
        GOLD(3),
        IRON(4),
        COPPER(5),
        COAL(6),
        LAPIS(7),
        REDSTONE(8),
        QUARTZ(9),
        AMETHYST(10),
        NONE(-1);

        private final int order;

        ProgressionChain(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }
    }

    /** High priority / unique / valuable items centered in chest layouts. */
    private static final Set<Material> PRIORITY_MATERIALS = EnumSet.noneOf(Material.class);

    static {
        addIfPresent(PRIORITY_MATERIALS,
                "NETHER_STAR", "BEACON", "ELYTRA", "DRAGON_EGG", "ENCHANTED_GOLDEN_APPLE",
                "CONDUIT", "TOTEM_OF_UNDYING", "HEART_OF_THE_SEA", "DRAGON_HEAD", "HEAVY_CORE",
                "RECOVERY_COMPASS", "RESPAWN_ANCHOR"
        );
    }

    private static void addIfPresent(Set<Material> set, String... names) {
        for (String name : names) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) {
                set.add(mat);
            }
        }
    }

    public static boolean isAir(Material mat) {
        if (mat == null) return true;
        return mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR;
    }

    public static boolean isPriority(Material mat) {
        if (isAir(mat)) return false;
        return PRIORITY_MATERIALS.contains(mat);
    }

    public static boolean isPriority(ItemStack stack) {
        if (stack == null || isAir(stack.getType())) return false;
        return isPriority(stack.getType());
    }

    public static Category getCategory(Material mat) {
        if (isAir(mat)) return Category.MISC;
        if (isPriority(mat)) return Category.PRIORITY;

        if (getEquipmentType(mat) != EquipmentType.NOT_EQUIPMENT) {
            return Category.EQUIPMENT;
        }
        if (getProgressionChain(mat) != ProgressionChain.NONE) {
            return Category.MINERAL_PROGRESSION;
        }
        if (isRedstone(mat)) {
            return Category.REDSTONE;
        }
        if (isFarming(mat)) {
            return Category.FARMING;
        }
        if (isMobDrop(mat)) {
            return Category.MOB_DROPS;
        }
        if (isBuildingBlock(mat)) {
            return Category.BUILDING_BLOCKS;
        }
        return Category.MISC;
    }

    public static Category getCategory(ItemStack stack) {
        if (stack == null || isAir(stack.getType())) return Category.MISC;
        return getCategory(stack.getType());
    }

    public static EquipmentType getEquipmentType(Material mat) {
        if (mat == null) return EquipmentType.NOT_EQUIPMENT;
        String name = mat.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) return EquipmentType.HELMET;
        if (name.endsWith("_CHESTPLATE")) return EquipmentType.CHESTPLATE;
        if (name.endsWith("_LEGGINGS")) return EquipmentType.LEGGINGS;
        if (name.endsWith("_BOOTS")) return EquipmentType.BOOTS;
        if (name.endsWith("_SWORD")) return EquipmentType.SWORD;
        if (name.endsWith("_PICKAXE")) return EquipmentType.PICKAXE;
        if (name.endsWith("_AXE") && !name.contains("WAXED")) return EquipmentType.AXE;
        if (name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.equals("BOW") ||
                name.equals("CROSSBOW") || name.equals("TRIDENT") || name.equals("SHIELD") ||
                name.equals("SHEARS") || name.equals("FISHING_ROD") || name.equals("FLINT_AND_STEEL")) {
            return EquipmentType.OTHER_TOOL;
        }
        return EquipmentType.NOT_EQUIPMENT;
    }

    public static MaterialTier getMaterialTier(Material mat) {
        if (mat == null) return MaterialTier.WOOD_LEATHER_OTHER;
        String name = mat.name();
        if (name.startsWith("NETHERITE_")) return MaterialTier.NETHERITE;
        if (name.startsWith("DIAMOND_")) return MaterialTier.DIAMOND;
        if (name.startsWith("IRON_")) return MaterialTier.IRON;
        if (name.startsWith("GOLDEN_") || name.startsWith("GOLD_")) return MaterialTier.GOLD;
        if (name.startsWith("COPPER_")) return MaterialTier.COPPER;
        if (name.startsWith("CHAINMAIL_") || name.startsWith("STONE_")) return MaterialTier.CHAINMAIL_STONE;
        if (name.equals("TURTLE_HELMET") || name.startsWith("TURTLE_")) return MaterialTier.TURTLE;
        return MaterialTier.WOOD_LEATHER_OTHER;
    }

    public static ProgressionChain getProgressionChain(Material mat) {
        if (mat == null || isAir(mat)) return ProgressionChain.NONE;
        if (getEquipmentType(mat) != EquipmentType.NOT_EQUIPMENT) return ProgressionChain.NONE;
        String name = mat.name();

        if (name.contains("ANCIENT_DEBRIS") || name.equals("NETHERITE_SCRAP") || name.equals("NETHERITE_INGOT") || name.equals("NETHERITE_BLOCK")) {
            return ProgressionChain.NETHERITE;
        }
        if (name.contains("DIAMOND") && !name.contains("HORSE")) {
            return ProgressionChain.DIAMOND;
        }
        if (name.contains("EMERALD")) {
            return ProgressionChain.EMERALD;
        }
        if (name.contains("GOLD") && !name.contains("APPLE") && !name.contains("CARROT") && !name.contains("HORSE")) {
            return ProgressionChain.GOLD;
        }
        if (name.contains("IRON") && !name.contains("BARS") && !name.contains("DOOR") && !name.contains("TRAPDOOR") && !name.contains("GOLEM") && !name.contains("HORSE")) {
            return ProgressionChain.IRON;
        }
        if (name.contains("COPPER") && !name.contains("WAXED") && !name.contains("GOLEM") && !name.contains("GRATE") && !name.contains("BULB") && !name.contains("DOOR") && !name.contains("TRAPDOOR")) {
            return ProgressionChain.COPPER;
        }
        if (name.contains("COAL")) {
            return ProgressionChain.COAL;
        }
        if (name.contains("LAPIS")) {
            return ProgressionChain.LAPIS;
        }
        if (name.contains("REDSTONE")) {
            return ProgressionChain.REDSTONE;
        }
        if (name.contains("QUARTZ") && !name.contains("SMOOTH") && !name.contains("PILLAR") && !name.contains("STAIRS") && !name.contains("SLAB")) {
            return ProgressionChain.QUARTZ;
        }
        if (name.contains("AMETHYST")) {
            return ProgressionChain.AMETHYST;
        }

        return ProgressionChain.NONE;
    }

    /**
     * Gets the crafting lifecycle stage index for progression sequencing.
     * Stage 0: Ores (Ancient Debris, *_ORE, DEEPSLATE_*_ORE, NETHER_*_ORE)
     * Stage 1: Raw Ore (RAW_*)
     * Stage 2: Raw Ore Block (RAW_*_BLOCK)
     * Stage 3: Intermediate / Scrap (NETHERITE_SCRAP, AMETHYST_SHARD, CHARCOAL)
     * Stage 4: Ingot / Gem (NETHERITE_INGOT, *_INGOT, DIAMOND, EMERALD, COAL, LAPIS_LAZULI, REDSTONE, QUARTZ)
     * Stage 5: Nugget (*_NUGGET)
     * Stage 6: Mineral Block (*_BLOCK, NETHERITE_BLOCK, AMETHYST_BLOCK)
     */
    public static int getProgressionStage(Material mat) {
        if (mat == null) return -1;
        String name = mat.name();

        if (name.equals("ANCIENT_DEBRIS") || (name.contains("_ORE") && !name.contains("BLOCK"))) {
            return 0;
        }
        if (name.startsWith("RAW_") && !name.endsWith("_BLOCK")) {
            return 1;
        }
        if (name.startsWith("RAW_") && name.endsWith("_BLOCK")) {
            return 2;
        }
        if (name.equals("NETHERITE_SCRAP") || name.equals("AMETHYST_SHARD") || name.equals("CHARCOAL")) {
            return 3;
        }
        if (name.endsWith("_INGOT") || name.equals("DIAMOND") || name.equals("EMERALD") ||
                name.equals("COAL") || name.equals("LAPIS_LAZULI") || name.equals("REDSTONE") ||
                name.equals("QUARTZ")) {
            return 4;
        }
        if (name.endsWith("_NUGGET")) {
            return 5;
        }
        if (name.endsWith("_BLOCK")) {
            return 6;
        }
        return 4;
    }

    public static String getSubCategory(Material mat) {
        if (isAir(mat)) return "air";
        Category cat = getCategory(mat);

        if (cat == Category.PRIORITY) return "priority";
        if (cat == Category.EQUIPMENT) return getEquipmentType(mat).name().toLowerCase(Locale.ROOT);
        if (cat == Category.MINERAL_PROGRESSION) return getProgressionChain(mat).name().toLowerCase(Locale.ROOT);
        if (cat == Category.REDSTONE) return "redstone";
        if (cat == Category.FARMING) return "farming";
        if (cat == Category.MOB_DROPS) return "mob_drops";

        if (isWood(mat)) {
            return "wood";
        }
        if (isShulkerBox(mat)) {
            return "shulker_box";
        }
        if (isColoredBlock(mat)) {
            return "colored_blocks";
        }
        if (isStone(mat)) {
            return "stone";
        }

        String name = mat.name();
        if (name.contains("DIRT") || name.contains("GRASS") || name.contains("SAND") || name.contains("GRAVEL") || name.contains("MUD") || name.contains("CLAY") || name.contains("PODZOL")) {
            return "dirt_organic";
        }
        if (name.contains("BRICK")) return "bricks";

        return "building_misc";
    }

    public static String getSubCategory(ItemStack stack) {
        if (stack == null || isAir(stack.getType())) return "air";
        return getSubCategory(stack.getType());
    }

    private static boolean isBukkitTaggedField(String fieldName, Material mat) {
        if (mat == null) return false;
        try {
            if (org.bukkit.Bukkit.getServer() != null) {
                Class<?> clazz = Class.forName("org.bukkit.Tag");
                java.lang.reflect.Field field = clazz.getField(fieldName);
                Object tagObj = field.get(null);
                if (tagObj != null) {
                    java.lang.reflect.Method method = tagObj.getClass().getMethod("isTagged", Object.class);
                    return (Boolean) method.invoke(tagObj, mat);
                }
            }
        } catch (Throwable ignored) {
            // Safe fallback when Bukkit server is not initialized (e.g. standalone unit tests)
        }
        return false;
    }

    private static boolean isPaperMaterialTagged(String fieldName, Material mat) {
        if (mat == null) return false;
        try {
            if (org.bukkit.Bukkit.getServer() != null) {
                Class<?> clazz = Class.forName("com.destroystokyo.paper.MaterialTags");
                java.lang.reflect.Field field = clazz.getField(fieldName);
                Object tagObj = field.get(null);
                if (tagObj != null) {
                    java.lang.reflect.Method method = tagObj.getClass().getMethod("isTagged", Material.class);
                    return (Boolean) method.invoke(tagObj, mat);
                }
            }
        } catch (Throwable ignored) {
            // Safe fallback when server is uninitialized (e.g. standalone unit tests)
        }
        return false;
    }

    public static boolean isWood(Material mat) {
        if (mat == null || isAir(mat)) return false;
        if (isBukkitTaggedField("LOGS", mat) || isBukkitTaggedField("PLANKS", mat) || isBukkitTaggedField("WOODEN_FENCES", mat) ||
                isBukkitTaggedField("FENCE_GATES", mat) || isBukkitTaggedField("WOODEN_DOORS", mat) || isBukkitTaggedField("WOODEN_TRAPDOORS", mat) ||
                isBukkitTaggedField("WOODEN_STAIRS", mat) || isBukkitTaggedField("WOODEN_SLABS", mat) || isBukkitTaggedField("WOODEN_PRESSURE_PLATES", mat) ||
                isBukkitTaggedField("WOODEN_BUTTONS", mat) || isBukkitTaggedField("SIGNS", mat) || isBukkitTaggedField("ITEMS_HANGING_SIGNS", mat) ||
                isBukkitTaggedField("ITEMS_BOATS", mat)) {
            return true;
        }
        if (isPaperMaterialTagged("WOODEN_FENCES", mat) || isPaperMaterialTagged("WOODEN_GATES", mat) ||
                isPaperMaterialTagged("WOODEN_DOORS", mat) || isPaperMaterialTagged("WOODEN_TRAPDOORS", mat)) {
            return true;
        }
        String name = mat.name();
        if (name.contains("WOOD") || name.contains("LOG") || name.contains("PLANKS") || name.contains("STRIPPED_") ||
                name.contains("HYPHAE") || name.contains("STEM") || (name.contains("FENCE") && !name.contains("IRON")) ||
                name.contains("DOOR") || name.contains("SIGN") || name.contains("BOAT")) {
            return true;
        }
        // Fallback for wood species variants like OAK_STAIRS, SPRUCE_SLAB, BIRCH_BUTTON etc.
        if (getWoodSpeciesRank(mat) != 12) {
            return name.contains("STAIRS") || name.contains("SLAB") || name.contains("BUTTON") || name.contains("PRESSURE_PLATE") || name.contains("TRAPDOOR");
        }
        return false;
    }

    /**
     * Gets wood structural variant rank for grouping:
     * 0: Wood / Logs / Hyphae (unstripped)
     * 1: Stripped Wood / Logs
     * 2: Planks
     * 3: Fences
     * 4: Fence Gates
     * 5: Doors
     * 6: Trapdoors
     * 7: Stairs
     * 8: Slabs
     * 9: Pressure Plates
     * 10: Buttons
     * 11: Signs / Hanging Signs
     * 12: Boats / Chest Boats
     * 13: Other Wood
     */
    public static int getWoodVariantRank(Material mat) {
        if (mat == null) return 99;
        String name = mat.name();

        boolean isStripped = name.startsWith("STRIPPED_");
        boolean isLogOrWood = isBukkitTaggedField("LOGS", mat) || name.endsWith("_LOG") || name.endsWith("_WOOD") ||
                name.endsWith("_STEM") || name.endsWith("_HYPHAE");

        if (isLogOrWood) {
            return isStripped ? 1 : 0;
        }
        if (isBukkitTaggedField("PLANKS", mat) || name.endsWith("_PLANKS")) {
            return 2;
        }
        if (isBukkitTaggedField("WOODEN_FENCES", mat) || isPaperMaterialTagged("WOODEN_FENCES", mat) || name.endsWith("_FENCE")) {
            return 3;
        }
        if (isBukkitTaggedField("FENCE_GATES", mat) || isPaperMaterialTagged("WOODEN_GATES", mat) || name.endsWith("_FENCE_GATE")) {
            return 4;
        }
        if (isBukkitTaggedField("WOODEN_DOORS", mat) || isPaperMaterialTagged("WOODEN_DOORS", mat) || name.endsWith("_DOOR")) {
            return 5;
        }
        if (isBukkitTaggedField("WOODEN_TRAPDOORS", mat) || isPaperMaterialTagged("WOODEN_TRAPDOORS", mat) || name.endsWith("_TRAPDOOR")) {
            return 6;
        }
        if (isBukkitTaggedField("WOODEN_STAIRS", mat) || name.endsWith("_STAIRS")) {
            return 7;
        }
        if (isBukkitTaggedField("WOODEN_SLABS", mat) || name.endsWith("_SLAB")) {
            return 8;
        }
        if (isBukkitTaggedField("WOODEN_PRESSURE_PLATES", mat) || name.endsWith("_PRESSURE_PLATE")) {
            return 9;
        }
        if (isBukkitTaggedField("WOODEN_BUTTONS", mat) || name.endsWith("_BUTTON")) {
            return 10;
        }
        if (isBukkitTaggedField("SIGNS", mat) || isBukkitTaggedField("ITEMS_HANGING_SIGNS", mat) || isPaperMaterialTagged("SIGNS", mat) || name.endsWith("_SIGN")) {
            return 11;
        }
        if (isBukkitTaggedField("ITEMS_BOATS", mat) || name.endsWith("_BOAT")) {
            return 12;
        }

        return 13;
    }

    /**
     * Gets wood species index for secondary sorting.
     */
    public static int getWoodSpeciesRank(Material mat) {
        if (mat == null) return 99;
        String name = mat.name();
        if (name.contains("OAK") && !name.contains("DARK_OAK") && !name.contains("PALE_OAK")) return 0;
        if (name.contains("SPRUCE")) return 1;
        if (name.contains("BIRCH")) return 2;
        if (name.contains("JUNGLE")) return 3;
        if (name.contains("ACACIA")) return 4;
        if (name.contains("DARK_OAK")) return 5;
        if (name.contains("MANGROVE")) return 6;
        if (name.contains("CHERRY")) return 7;
        if (name.contains("PALE_OAK")) return 8;
        if (name.contains("BAMBOO")) return 9;
        if (name.contains("CRIMSON")) return 10;
        if (name.contains("WARPED")) return 11;
        return 12;
    }

    public static boolean isShulkerBox(Material mat) {
        if (mat == null || isAir(mat)) return false;
        return isBukkitTaggedField("SHULKER_BOXES", mat) || isPaperMaterialTagged("SHULKER_BOXES", mat) || mat.name().endsWith("SHULKER_BOX");
    }

    public static boolean isColoredBlock(Material mat) {
        if (mat == null || isAir(mat)) return false;
        if (isShulkerBox(mat)) return true;
        if (isBukkitTaggedField("WOOL", mat) || isBukkitTaggedField("WOOL_CARPETS", mat) || isBukkitTaggedField("BEDS", mat) || isBukkitTaggedField("CANDLES", mat) || isBukkitTaggedField("ITEMS_BANNERS", mat)) return true;
        if (isPaperMaterialTagged("STAINED_GLASS", mat) || isPaperMaterialTagged("STAINED_GLASS_PANES", mat) ||
                isPaperMaterialTagged("GLASS", mat) || isPaperMaterialTagged("GLASS_PANES", mat) ||
                isPaperMaterialTagged("CONCRETES", mat) || isPaperMaterialTagged("CONCRETE_POWDER", mat) ||
                isPaperMaterialTagged("TERRACOTTA", mat) || isPaperMaterialTagged("STAINED_TERRACOTTA", mat) ||
                isPaperMaterialTagged("GLAZED_TERRACOTTA", mat) || isPaperMaterialTagged("BEDS", mat) ||
                isPaperMaterialTagged("DYES", mat) || isPaperMaterialTagged("COLORABLE", mat)) return true;

        String name = mat.name();
        return name.contains("GLASS") || name.contains("WOOL") || name.contains("CARPET") ||
                name.contains("CONCRETE") || name.contains("TERRACOTTA") || name.contains("CANDLE") ||
                name.contains("BANNER") || name.contains("BED");
    }

    /**
     * Gets family rank for colored blocks:
     * 0: Shulker Boxes
     * 1: Glass
     * 2: Glass Panes
     * 3: Wool
     * 4: Carpets
     * 5: Concrete
     * 6: Concrete Powder
     * 7: Terracotta
     * 8: Glazed Terracotta
     * 9: Beds
     * 10: Candles
     * 11: Banners
     * 12: Dyes
     * 99: Other
     */
    public static int getColorFamilyRank(Material mat) {
        if (mat == null) return 99;
        if (isShulkerBox(mat)) return 0;

        String name = mat.name();
        if (isPaperMaterialTagged("GLASS", mat) || isPaperMaterialTagged("STAINED_GLASS", mat) || (name.contains("GLASS") && !name.contains("PANE"))) return 1;
        if (isPaperMaterialTagged("GLASS_PANES", mat) || isPaperMaterialTagged("STAINED_GLASS_PANES", mat) || name.contains("GLASS_PANE")) return 2;
        if (isBukkitTaggedField("WOOL", mat) || name.endsWith("_WOOL")) return 3;
        if (isBukkitTaggedField("WOOL_CARPETS", mat) || name.endsWith("_CARPET")) return 4;
        if (isPaperMaterialTagged("CONCRETES", mat) || (name.endsWith("_CONCRETE") && !name.contains("POWDER"))) return 5;
        if (isPaperMaterialTagged("CONCRETE_POWDER", mat) || name.endsWith("_CONCRETE_POWDER")) return 6;
        if (isPaperMaterialTagged("TERRACOTTA", mat) || isPaperMaterialTagged("STAINED_TERRACOTTA", mat) || (name.endsWith("TERRACOTTA") && !name.contains("GLAZED"))) return 7;
        if (isPaperMaterialTagged("GLAZED_TERRACOTTA", mat) || name.contains("GLAZED_TERRACOTTA")) return 8;
        if (isBukkitTaggedField("BEDS", mat) || isPaperMaterialTagged("BEDS", mat) || name.endsWith("_BED")) return 9;
        if (isBukkitTaggedField("CANDLES", mat) || name.endsWith("_CANDLE")) return 10;
        if (isBukkitTaggedField("ITEMS_BANNERS", mat) || name.endsWith("_BANNER")) return 11;
        if (isPaperMaterialTagged("DYES", mat) || name.endsWith("_DYE")) return 12;

        return 99;
    }

    /**
     * Gets color spectrum index (0..16) for colorable blocks:
     * 0: Undyed / Clear / Plain
     * 1: White, 2: Light Gray, 3: Gray, 4: Black, 5: Brown, 6: Red, 7: Orange, 8: Yellow,
     * 9: Lime, 10: Green, 11: Cyan, 12: Light Blue, 13: Blue, 14: Purple, 15: Magenta, 16: Pink
     */
    public static int getColorIndex(Material mat) {
        if (mat == null) return 0;
        String name = mat.name();
        if (name.startsWith("WHITE_")) return 1;
        if (name.startsWith("LIGHT_GRAY_")) return 2;
        if (name.startsWith("GRAY_")) return 3;
        if (name.startsWith("BLACK_")) return 4;
        if (name.startsWith("BROWN_")) return 5;
        if (name.startsWith("RED_")) return 6;
        if (name.startsWith("ORANGE_")) return 7;
        if (name.startsWith("YELLOW_")) return 8;
        if (name.startsWith("LIME_")) return 9;
        if (name.startsWith("GREEN_")) return 10;
        if (name.startsWith("CYAN_")) return 11;
        if (name.startsWith("LIGHT_BLUE_")) return 12;
        if (name.startsWith("BLUE_")) return 13;
        if (name.startsWith("PURPLE_")) return 14;
        if (name.startsWith("MAGENTA_")) return 15;
        if (name.startsWith("PINK_")) return 16;
        return 0;
    }

    public static boolean isStone(Material mat) {
        if (mat == null || isAir(mat)) return false;
        if (isBukkitTaggedField("STAIRS", mat) || isBukkitTaggedField("SLABS", mat) || isBukkitTaggedField("WALLS", mat)) {
            if (isWood(mat)) return false;
            return true;
        }
        String name = mat.name();
        return name.contains("STONE") || name.contains("GRANITE") || name.contains("DIORITE") ||
                name.contains("ANDESITE") || name.contains("DEEPSLATE") || name.contains("TUFF") ||
                name.contains("COBBLESTONE") || name.contains("BASALT") || name.contains("BLACKSTONE") ||
                name.contains("PRISMARINE") || name.contains("SANDSTONE");
    }

    /**
     * Gets stone structural variant rank:
     * 0: Base / Smooth / Raw
     * 1: Polished / Chiseled / Cut
     * 2: Bricks / Tiles
     * 3: Stairs
     * 4: Slabs
     * 5: Walls
     * 6: Other
     */
    public static int getStoneVariantRank(Material mat) {
        if (mat == null) return 99;
        String name = mat.name();
        if (isBukkitTaggedField("WALLS", mat) || name.endsWith("_WALL")) return 5;
        if (isBukkitTaggedField("SLABS", mat) || name.endsWith("_SLAB")) return 4;
        if (isBukkitTaggedField("STAIRS", mat) || name.endsWith("_STAIRS")) return 3;
        if (name.contains("BRICK") || name.contains("TILE")) return 2;
        if (name.contains("POLISHED") || name.contains("CHISELED") || name.contains("CUT")) return 1;
        return 0;
    }

    private static boolean isRedstone(Material mat) {
        String name = mat.name();
        return name.contains("REDSTONE") || name.contains("REPEATER") || name.contains("COMPARATOR") ||
                name.contains("PISTON") || name.contains("OBSERVER") || name.contains("DISPENSER") ||
                name.contains("DROPPER") || name.contains("HOPPER") || name.contains("DAYLIGHT") ||
                name.contains("TARGET") || name.contains("LEVER") || name.contains("BUTTON") ||
                name.contains("PRESSURE_PLATE") || name.contains("TRIPWIRE");
    }

    private static boolean isFarming(Material mat) {
        String name = mat.name();
        return name.contains("WHEAT") || name.contains("CARROT") || name.contains("POTATO") ||
                name.contains("BEETROOT") || name.contains("MELON") || name.contains("PUMPKIN") ||
                name.contains("SEED") || name.contains("SUGAR_CANE") || name.contains("BAMBOO") ||
                name.contains("CACTUS") || name.contains("APPLE") || name.contains("BREAD") ||
                name.contains("PORKCHOP") || name.contains("BEEF") || name.contains("CHICKEN") ||
                name.contains("MUTTON") || name.contains("FISH") || name.contains("SALMON") ||
                name.contains("BERRY") || name.contains("CHORUS") || name.contains("COCOA");
    }

    private static boolean isMobDrop(Material mat) {
        String name = mat.name();
        return name.contains("FLESH") || name.contains("BONE") || name.contains("GUNPOWDER") ||
                name.contains("STRING") || name.contains("SPIDER_EYE") || name.contains("ENDER_PEARL") ||
                name.contains("BLAZE_ROD") || name.contains("GHAST_TEAR") || name.contains("SLIME") ||
                name.contains("MAGMA") || name.contains("MEMBRANE") || name.contains("LEATHER") ||
                name.contains("FEATHER") || name.contains("INK_SAC") || name.contains("PRISMARINE");
    }

    private static boolean isBuildingBlock(Material mat) {
        if (mat == null || isAir(mat)) return false;
        if (isWood(mat) || isColoredBlock(mat) || isStone(mat)) return true;
        String name = mat.name();
        return name.contains("DIRT") || name.contains("GRASS") || name.contains("SAND") ||
                name.contains("GRAVEL") || name.contains("MUD") || name.contains("CLAY") ||
                name.contains("PODZOL") || name.contains("BRICK") || name.contains("BLOCK");
    }
}

