package me.usainsrht.basicshop.api.model;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ShopToolTypeTest {

    @Test
    public void testCooldownKeys() {
        NamespacedKey hoeKey = ShopToolType.MONEY_HOE.getCooldownKey();
        assertNotNull(hoeKey);
        assertEquals("basicshop", hoeKey.getNamespace());
        assertEquals("money_hoe", hoeKey.getKey());

        NamespacedKey staffKey = ShopToolType.MONEY_STAFF.getCooldownKey();
        assertNotNull(staffKey);
        assertEquals("basicshop", staffKey.getNamespace());
        assertEquals("money_staff", staffKey.getKey());

        NamespacedKey sortingKey = ShopToolType.SORTING_STAFF.getCooldownKey();
        assertNotNull(sortingKey);
        assertEquals("basicshop", sortingKey.getNamespace());
        assertEquals("sorting_staff", sortingKey.getKey());
    }
}
