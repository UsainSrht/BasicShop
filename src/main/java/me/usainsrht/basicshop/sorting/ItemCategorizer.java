package me.usainsrht.basicshop.sorting;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Categorizes Minecraft items into priority items, equipment slots, material tiers,
 * mineral progression lifecycles, and base materials for boundary insertion.
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
        NETHERITE(6),
        DIAMOND(5),
        GOLD(4),
        IRON(3),
        CHAINMAIL(2),
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
                "RECOVERY_COMPASS", "NETHERITE_BLOCK", "RESPAWN_ANCHOR"
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
        if (stack == null || stack.getType().isAir()) return Category.MISC;
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
        if (name.startsWith("GOLDEN_") || name.startsWith("GOLD_")) return MaterialTier.GOLD;
        if (name.startsWith("IRON_")) return MaterialTier.IRON;
        if (name.startsWith("CHAINMAIL_")) return MaterialTier.CHAINMAIL;
        return MaterialTier.WOOD_LEATHER_OTHER;
    }

    public static ProgressionChain getProgressionChain(Material mat) {
        if (mat == null) return ProgressionChain.NONE;
        String name = mat.name();

        if (name.contains("GOLD")) return ProgressionChain.GOLD;
        if (name.contains("IRON")) return ProgressionChain.IRON;
        if (name.contains("COPPER")) return ProgressionChain.COPPER;
        if (name.contains("DIAMOND")) return ProgressionChain.DIAMOND;
        if (name.contains("EMERALD")) return ProgressionChain.EMERALD;
        if (name.contains("COAL")) return ProgressionChain.COAL;
        if (name.contains("LAPIS")) return ProgressionChain.LAPIS;
        if (name.contains("REDSTONE") && (name.contains("ORE") || name.equals("REDSTONE") || name.contains("BLOCK"))) return ProgressionChain.REDSTONE;
        if (name.contains("ANCIENT_DEBRIS") || name.contains("NETHERITE_SCRAP") || name.contains("NETHERITE_INGOT") || (name.equals("NETHERITE_BLOCK"))) return ProgressionChain.NETHERITE;
        if (name.contains("QUARTZ") && !name.contains("SMOOTH") && !name.contains("PILLAR") && !name.contains("STAIRS") && !name.contains("SLAB")) return ProgressionChain.QUARTZ;
        if (name.contains("AMETHYST")) return ProgressionChain.AMETHYST;

        return ProgressionChain.NONE;
    }

    /**
     * Gets the crafting lifecycle stage index for progression sequencing.
     * Stage 0: Raw / Ore
     * Stage 1: Ingot / Gem
     * Stage 2: Nugget / Intermediate
     * Stage 3: Block
     */
    public static int getProgressionStage(Material mat) {
        if (mat == null) return -1;
        String name = mat.name();

        if (name.startsWith("RAW_") || name.contains("_ORE") || name.equals("ANCIENT_DEBRIS") || name.equals("AMETHYST_SHARD")) {
            return 0;
        }
        if (name.endsWith("_INGOT") || name.equals("DIAMOND") || name.equals("EMERALD") || name.equals("COAL") ||
                name.equals("LAPIS_LAZULI") || name.equals("REDSTONE") || name.equals("QUARTZ") || name.equals("NETHERITE_SCRAP")) {
            return 1;
        }
        if (name.endsWith("_NUGGET")) {
            return 2;
        }
        if (name.endsWith("_BLOCK")) {
            return 3;
        }
        return 1;
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

        String name = mat.name();
        if (name.contains("WOOD") || name.contains("LOG") || name.contains("PLANKS") || name.contains("STRIPPED_") || name.contains("HYPHAE") || name.contains("STEM")) {
            return "wood";
        }
        if (name.contains("STONE") || name.contains("GRANITE") || name.contains("DIORITE") || name.contains("ANDESITE") || name.contains("DEEPSLATE") || name.contains("TUFF") || name.contains("COBBLESTONE") || name.contains("BASALT") || name.contains("BLACKSTONE")) {
            return "stone";
        }
        if (name.contains("DIRT") || name.contains("GRASS") || name.contains("SAND") || name.contains("GRAVEL") || name.contains("MUD") || name.contains("CLAY") || name.contains("PODZOL")) {
            return "dirt_organic";
        }
        if (name.contains("GLASS")) return "glass";
        if (name.contains("TERRACOTTA") || name.contains("CONCRETE")) return "terracotta_concrete";
        if (name.contains("BRICK")) return "bricks";

        return "building_misc";
    }

    public static String getSubCategory(ItemStack stack) {
        if (stack == null || isAir(stack.getType())) return "air";
        return getSubCategory(stack.getType());
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
        String name = mat.name();
        return name.contains("STONE") || name.contains("DIRT") || name.contains("LOG") ||
                name.contains("WOOD") || name.contains("PLANKS") || name.contains("BRICK") ||
                name.contains("TERRACOTTA") || name.contains("CONCRETE") || name.contains("GLASS") ||
                name.contains("SAND") || name.contains("GRAVEL") || name.contains("SLAB") ||
                name.contains("STAIRS") || name.contains("WALL") || name.contains("FENCE") ||
                name.contains("BLOCK");
    }
}
