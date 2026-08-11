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
        assertEquals(1, ItemCategorizer.getProgressionStage(Material.RAW_GOLD));
        assertEquals(4, ItemCategorizer.getProgressionStage(Material.GOLD_INGOT));
        assertEquals(5, ItemCategorizer.getProgressionStage(Material.GOLD_NUGGET));
        assertEquals(6, ItemCategorizer.getProgressionStage(Material.GOLD_BLOCK));
    }

    @Test
    public void testSubCategoryGrouping() {
        assertEquals("wood", ItemCategorizer.getSubCategory(Material.OAK_LOG));
        assertEquals("wood", ItemCategorizer.getSubCategory(Material.SPRUCE_PLANKS));
        assertEquals("wood", ItemCategorizer.getSubCategory(Material.OAK_FENCE));
        assertEquals("wood", ItemCategorizer.getSubCategory(Material.OAK_DOOR));
        assertEquals("wood", ItemCategorizer.getSubCategory(Material.OAK_STAIRS));
        assertEquals("wood", ItemCategorizer.getSubCategory(Material.OAK_SLAB));
        assertEquals("stone", ItemCategorizer.getSubCategory(Material.COBBLESTONE));
        assertEquals("dirt_organic", ItemCategorizer.getSubCategory(Material.DIRT));
        assertEquals("shulker_box", ItemCategorizer.getSubCategory(Material.SHULKER_BOX));
        assertEquals("shulker_box", ItemCategorizer.getSubCategory(Material.RED_SHULKER_BOX));
        assertEquals("colored_blocks", ItemCategorizer.getSubCategory(Material.RED_WOOL));
        assertEquals("colored_blocks", ItemCategorizer.getSubCategory(Material.WHITE_CONCRETE));
        assertEquals("colored_blocks", ItemCategorizer.getSubCategory(Material.BLUE_TERRACOTTA));
    }

    @Test
    public void testWoodVariantOrdering() {
        assertTrue(ItemCategorizer.isWood(Material.OAK_LOG));
        assertTrue(ItemCategorizer.isWood(Material.SPRUCE_PLANKS));
        assertTrue(ItemCategorizer.isWood(Material.OAK_FENCE));
        assertTrue(ItemCategorizer.isWood(Material.OAK_FENCE_GATE));
        assertTrue(ItemCategorizer.isWood(Material.OAK_DOOR));
        assertTrue(ItemCategorizer.isWood(Material.OAK_STAIRS));
        assertTrue(ItemCategorizer.isWood(Material.OAK_SLAB));

        int logRank = ItemCategorizer.getWoodVariantRank(Material.OAK_LOG);
        int plankRank = ItemCategorizer.getWoodVariantRank(Material.OAK_PLANKS);
        int fenceRank = ItemCategorizer.getWoodVariantRank(Material.OAK_FENCE);
        int gateRank = ItemCategorizer.getWoodVariantRank(Material.OAK_FENCE_GATE);
        int doorRank = ItemCategorizer.getWoodVariantRank(Material.OAK_DOOR);
        int stairRank = ItemCategorizer.getWoodVariantRank(Material.OAK_STAIRS);
        int slabRank = ItemCategorizer.getWoodVariantRank(Material.OAK_SLAB);

        assertTrue(logRank < plankRank, "Logs should come before Planks");
        assertTrue(plankRank < fenceRank, "Planks should come before Fences");
        assertTrue(fenceRank < gateRank, "Fences should come before Fence Gates");
        assertTrue(gateRank < doorRank, "Fence Gates should come before Doors");
        assertTrue(doorRank < stairRank, "Doors should come before Stairs");
        assertTrue(stairRank < slabRank, "Stairs should come before Slabs");
    }

    @Test
    public void testColoredBlocksAndShulkerBoxOrdering() {
        assertTrue(ItemCategorizer.isShulkerBox(Material.SHULKER_BOX));
        assertTrue(ItemCategorizer.isShulkerBox(Material.CYAN_SHULKER_BOX));
        assertTrue(ItemCategorizer.isColoredBlock(Material.WHITE_WOOL));
        assertTrue(ItemCategorizer.isColoredBlock(Material.BLACK_CONCRETE));
        assertTrue(ItemCategorizer.isColoredBlock(Material.RED_TERRACOTTA));

        int shulkerFam = ItemCategorizer.getColorFamilyRank(Material.SHULKER_BOX);
        int glassFam = ItemCategorizer.getColorFamilyRank(Material.GLASS);
        int woolFam = ItemCategorizer.getColorFamilyRank(Material.WHITE_WOOL);
        int concreteFam = ItemCategorizer.getColorFamilyRank(Material.WHITE_CONCRETE);
        int terracottaFam = ItemCategorizer.getColorFamilyRank(Material.TERRACOTTA);

        assertTrue(shulkerFam < glassFam);
        assertTrue(glassFam < woolFam);
        assertTrue(woolFam < concreteFam);
        assertTrue(concreteFam < terracottaFam);

        // Color index spectrum testing
        assertTrue(ItemCategorizer.getColorIndex(Material.WHITE_WOOL) < ItemCategorizer.getColorIndex(Material.RED_WOOL));
        assertTrue(ItemCategorizer.getColorIndex(Material.RED_WOOL) < ItemCategorizer.getColorIndex(Material.BLUE_WOOL));
    }

    private org.bukkit.inventory.ItemStack mockItem(Material mat, int amount) {
        org.bukkit.inventory.ItemStack stack = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(stack.getType()).thenReturn(mat);
        org.mockito.Mockito.when(stack.getAmount()).thenReturn(amount);
        return stack;
    }

    @Test
    public void testEquipmentTierOrdering() {
        org.bukkit.inventory.ItemStack netheriteChest = mockItem(Material.NETHERITE_CHESTPLATE, 1);
        org.bukkit.inventory.ItemStack diamondChest = mockItem(Material.DIAMOND_CHESTPLATE, 1);
        org.bukkit.inventory.ItemStack ironChest = mockItem(Material.IRON_CHESTPLATE, 1);

        assertTrue(ContainerSorter.compareItems(netheriteChest, diamondChest) < 0, "Netherite chestplate should sort before Diamond chestplate");
        assertTrue(ContainerSorter.compareItems(diamondChest, ironChest) < 0, "Diamond chestplate should sort before Iron chestplate");

        org.bukkit.inventory.ItemStack copperSword = mockItem(Material.matchMaterial("COPPER_SWORD") != null ? Material.matchMaterial("COPPER_SWORD") : Material.GOLDEN_SWORD, 1);
        org.bukkit.inventory.ItemStack stoneSword = mockItem(Material.STONE_SWORD, 1);
        org.bukkit.inventory.ItemStack woodSword = mockItem(Material.WOODEN_SWORD, 1);

        assertTrue(ItemCategorizer.getMaterialTier(Material.GOLDEN_SWORD).getWeight() > ItemCategorizer.getMaterialTier(Material.STONE_SWORD).getWeight());
        assertTrue(ItemCategorizer.getMaterialTier(Material.STONE_SWORD).getWeight() > ItemCategorizer.getMaterialTier(Material.WOODEN_SWORD).getWeight());
        assertTrue(ContainerSorter.compareItems(stoneSword, woodSword) < 0, "Stone sword should sort before Wooden sword");
    }

    @Test
    public void testMineralProgressionChainOrdering() {
        org.bukkit.inventory.ItemStack ancientDebris = mockItem(Material.ANCIENT_DEBRIS, 64);
        org.bukkit.inventory.ItemStack netheriteScrap = mockItem(Material.NETHERITE_SCRAP, 64);
        org.bukkit.inventory.ItemStack netheriteIngot = mockItem(Material.NETHERITE_INGOT, 64);
        org.bukkit.inventory.ItemStack netheriteBlock = mockItem(Material.NETHERITE_BLOCK, 64);

        assertTrue(ContainerSorter.compareItems(ancientDebris, netheriteScrap) < 0, "Ancient Debris should sort before Netherite Scrap");
        assertTrue(ContainerSorter.compareItems(netheriteScrap, netheriteIngot) < 0, "Netherite Scrap should sort before Netherite Ingot");
        assertTrue(ContainerSorter.compareItems(netheriteIngot, netheriteBlock) < 0, "Netherite Ingot should sort before Netherite Block");

        org.bukkit.inventory.ItemStack goldOre = mockItem(Material.GOLD_ORE, 64);
        org.bukkit.inventory.ItemStack rawGold = mockItem(Material.RAW_GOLD, 64);
        org.bukkit.inventory.ItemStack rawGoldBlock = mockItem(Material.RAW_GOLD_BLOCK, 64);
        org.bukkit.inventory.ItemStack goldIngot = mockItem(Material.GOLD_INGOT, 64);
        org.bukkit.inventory.ItemStack goldNugget = mockItem(Material.GOLD_NUGGET, 64);
        org.bukkit.inventory.ItemStack goldBlock = mockItem(Material.GOLD_BLOCK, 64);

        assertTrue(ContainerSorter.compareItems(goldOre, rawGold) < 0);
        assertTrue(ContainerSorter.compareItems(rawGold, rawGoldBlock) < 0);
        assertTrue(ContainerSorter.compareItems(rawGoldBlock, goldIngot) < 0);
        assertTrue(ContainerSorter.compareItems(goldIngot, goldNugget) < 0);
        assertTrue(ContainerSorter.compareItems(goldNugget, goldBlock) < 0);

        assertTrue(ContainerSorter.compareItems(netheriteIngot, goldIngot) < 0, "Netherite chain should sort before Gold chain");
    }

    @Test
    public void testCompareItemsOrder() {
        org.bukkit.inventory.ItemStack oakLog = mockItem(Material.OAK_LOG, 64);
        org.bukkit.inventory.ItemStack oakPlanks = mockItem(Material.OAK_PLANKS, 64);
        org.bukkit.inventory.ItemStack oakFence = mockItem(Material.OAK_FENCE, 64);
        org.bukkit.inventory.ItemStack oakDoor = mockItem(Material.OAK_DOOR, 64);
        org.bukkit.inventory.ItemStack oakStairs = mockItem(Material.OAK_STAIRS, 64);
        org.bukkit.inventory.ItemStack oakSlab = mockItem(Material.OAK_SLAB, 64);

        assertTrue(ContainerSorter.compareItems(oakLog, oakPlanks) < 0);
        assertTrue(ContainerSorter.compareItems(oakPlanks, oakFence) < 0);
        assertTrue(ContainerSorter.compareItems(oakFence, oakDoor) < 0);
        assertTrue(ContainerSorter.compareItems(oakDoor, oakStairs) < 0);
        assertTrue(ContainerSorter.compareItems(oakStairs, oakSlab) < 0);

        org.bukkit.inventory.ItemStack shulker = mockItem(Material.SHULKER_BOX, 1);
        org.bukkit.inventory.ItemStack glass = mockItem(Material.GLASS, 64);
        org.bukkit.inventory.ItemStack whiteWool = mockItem(Material.WHITE_WOOL, 64);
        org.bukkit.inventory.ItemStack redWool = mockItem(Material.RED_WOOL, 64);
        org.bukkit.inventory.ItemStack whiteConcrete = mockItem(Material.WHITE_CONCRETE, 64);

        assertTrue(ContainerSorter.compareItems(shulker, glass) < 0);
        assertTrue(ContainerSorter.compareItems(glass, whiteWool) < 0);
        assertTrue(ContainerSorter.compareItems(whiteWool, redWool) < 0);
        assertTrue(ContainerSorter.compareItems(whiteWool, whiteConcrete) < 0);
    }

    @Test
    public void testSortContainerWithNullSlots() {
        org.bukkit.block.Container container = org.mockito.Mockito.mock(org.bukkit.block.Container.class);
        org.bukkit.inventory.Inventory inv = org.mockito.Mockito.mock(org.bukkit.inventory.Inventory.class);
        org.bukkit.entity.Player player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);

        org.bukkit.inventory.ItemStack stack1 = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(stack1.getType()).thenReturn(Material.COBBLESTONE);
        org.mockito.Mockito.when(stack1.getAmount()).thenReturn(64);
        org.mockito.Mockito.when(stack1.clone()).thenReturn(stack1);

        org.bukkit.inventory.ItemStack stack2 = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(stack2.getType()).thenReturn(Material.OAK_LOG);
        org.mockito.Mockito.when(stack2.getAmount()).thenReturn(32);
        org.mockito.Mockito.when(stack2.clone()).thenReturn(stack2);

        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[27];
        contents[0] = stack1;
        contents[5] = stack2;

        org.mockito.Mockito.when(container.getInventory()).thenReturn(inv);
        org.mockito.Mockito.when(inv.getContents()).thenReturn(contents);

        boolean result = ContainerSorter.sortContainer(player, container);
        assertTrue(result);
        org.mockito.Mockito.verify(inv).setContents(org.mockito.Mockito.any(org.bukkit.inventory.ItemStack[].class));
    }
}
