package me.usainsrht.basicshop.config;

import me.usainsrht.basicshop.api.model.ShopToolType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToolsConfigTest {

    @Test
    public void testUsableWhenRidingDefaultFalse() {
        YamlConfiguration config = new YamlConfiguration();
        ToolsConfig toolsConfig = new ToolsConfig(config);

        for (ShopToolType type : ShopToolType.values()) {
            assertFalse(toolsConfig.isUsableWhenRiding(type),
                    "Tool " + type.getId() + " should default to usableWhenRiding = false");
        }
    }

    @Test
    public void testUsableWhenRidingIndividualConfiguration() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tools.money_staff.usable-when-riding", false);
        config.set("tools.money_hoe.usable-when-riding", true);
        config.set("tools.sorting_staff.usable-when-riding", false);

        ToolsConfig toolsConfig = new ToolsConfig(config);

        assertFalse(toolsConfig.isUsableWhenRiding(ShopToolType.MONEY_STAFF));
        assertTrue(toolsConfig.isUsableWhenRiding(ShopToolType.MONEY_HOE));
        assertFalse(toolsConfig.isUsableWhenRiding(ShopToolType.SORTING_STAFF));
    }
}
