package me.usainsrht.basicshop.gui;

import me.usainsrht.basicshop.api.model.ShopCategory;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CategoryGuiTest {

    @Test
    public void testComputeItemSlotsModern() {
        // 6 rows modern: 4 item rows (rows 1-4) * 7 = 28 slots
        int[] slots6 = CategoryGui.computeItemSlots(6, true);
        assertEquals(28, slots6.length);
        assertEquals(10, slots6[0]);
        assertEquals(43, slots6[27]);

        // 4 rows modern: 2 item rows (rows 1-2) * 7 = 14 slots
        int[] slots4 = CategoryGui.computeItemSlots(4, true);
        assertEquals(14, slots4.length);
        assertArrayEquals(new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25
        }, slots4);

        // 3 rows modern: 1 item row (row 1) * 7 = 7 slots
        int[] slots3 = CategoryGui.computeItemSlots(3, true);
        assertEquals(7, slots3.length);
        assertArrayEquals(new int[]{10, 11, 12, 13, 14, 15, 16}, slots3);

        // 2 rows modern: row 0 cols 1-7 = 7 slots
        int[] slots2 = CategoryGui.computeItemSlots(2, true);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7}, slots2);

        // 1 row modern: 6 slots (excluding 0, 4, 8)
        int[] slots1 = CategoryGui.computeItemSlots(1, true);
        assertArrayEquals(new int[]{1, 2, 3, 5, 6, 7}, slots1);
    }

    @Test
    public void testComputeItemSlotsClassic() {
        // 6 rows classic: 5 rows * 9 = 45 slots
        int[] slots6 = CategoryGui.computeItemSlots(6, false);
        assertEquals(45, slots6.length);
        assertEquals(0, slots6[0]);
        assertEquals(44, slots6[44]);

        // 4 rows classic: 3 rows * 9 = 27 slots
        int[] slots4 = CategoryGui.computeItemSlots(4, false);
        assertEquals(27, slots4.length);
        assertEquals(0, slots4[0]);
        assertEquals(26, slots4[26]);

        // 3 rows classic: 2 rows * 9 = 18 slots
        int[] slots3 = CategoryGui.computeItemSlots(3, false);
        assertEquals(18, slots3.length);
        assertEquals(0, slots3[0]);
        assertEquals(17, slots3[17]);
    }

    @Test
    public void testShopCategoryRows() {
        ShopCategory defaultCat = new ShopCategory(
                "test", "Test", "Test", Material.CHEST, 0, Collections.emptyList(), Collections.emptyList()
        );
        assertEquals(6, defaultCat.getRows());

        ShopCategory customCat = new ShopCategory(
                "test", "Test", "Test", Material.CHEST, 0, Collections.emptyList(), Collections.emptyList(), 4
        );
        assertEquals(4, customCat.getRows());
    }
}
