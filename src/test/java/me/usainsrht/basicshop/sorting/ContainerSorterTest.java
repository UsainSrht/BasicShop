package me.usainsrht.basicshop.sorting;

import me.usainsrht.basicshop.sorting.ContainerSorter.Profile;
import me.usainsrht.basicshop.sorting.ItemCategorizer.Category;
import me.usainsrht.basicshop.sorting.ItemCategorizer.EquipmentType;
import me.usainsrht.basicshop.sorting.ItemCategorizer.MaterialTier;
import me.usainsrht.basicshop.sorting.ItemCategorizer.ProgressionChain;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContainerSorterTest {

    @Test
    public void testItemCategorization() {
        assertTrue(ItemCategorizer.isPriority(Material.NETHER_STAR));
        assertTrue(ItemCategorizer.isPriority(Material.BEACON));
        assertTrue(ItemCategorizer.isPriority(Material.ELYTRA));
        assertFalse(ItemCategorizer.isPriority(Material.COBBLESTONE));

        assertEquals(Category.EQUIPMENT, ItemCategorizer.getCategory(Material.DIAMOND_HELMET));
        assertEquals(EquipmentType.HELMET, ItemCategorizer.getEquipmentType(Material.DIAMOND_HELMET));
        assertEquals(EquipmentType.CHESTPLATE, ItemCategorizer.getEquipmentType(Material.NETHERITE_CHESTPLATE));
        assertEquals(EquipmentType.LEGGINGS, ItemCategorizer.getEquipmentType(Material.IRON_LEGGINGS));
        assertEquals(EquipmentType.BOOTS, ItemCategorizer.getEquipmentType(Material.GOLDEN_BOOTS));

        assertEquals(MaterialTier.NETHERITE, ItemCategorizer.getMaterialTier(Material.NETHERITE_CHESTPLATE));
        assertEquals(MaterialTier.DIAMOND, ItemCategorizer.getMaterialTier(Material.DIAMOND_SWORD));
        assertEquals(MaterialTier.GOLD, ItemCategorizer.getMaterialTier(Material.GOLDEN_HELMET));

        assertEquals(Category.MINERAL_PROGRESSION, ItemCategorizer.getCategory(Material.RAW_GOLD));
        assertEquals(ProgressionChain.GOLD, ItemCategorizer.getProgressionChain(Material.RAW_GOLD));
        assertEquals(0, ItemCategorizer.getProgressionStage(Material.RAW_GOLD));
        assertEquals(1, ItemCategorizer.getProgressionStage(Material.GOLD_INGOT));
        assertEquals(2, ItemCategorizer.getProgressionStage(Material.GOLD_NUGGET));
        assertEquals(3, ItemCategorizer.getProgressionStage(Material.GOLD_BLOCK));
    }

    @Test
    public void testSubCategoryGrouping() {
        assertEquals("wood", ItemCategorizer.getSubCategory(Material.OAK_LOG));
        assertEquals("wood", ItemCategorizer.getSubCategory(Material.SPRUCE_PLANKS));
        assertEquals("stone", ItemCategorizer.getSubCategory(Material.COBBLESTONE));
        assertEquals("dirt_organic", ItemCategorizer.getSubCategory(Material.DIRT));
    }
}
