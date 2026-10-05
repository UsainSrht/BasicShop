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

    @Test
    public void testCursorCooldownDefault() {
        YamlConfiguration config = new YamlConfiguration();
        ToolsConfig toolsConfig = new ToolsConfig(config);

        org.junit.jupiter.api.Assertions.assertEquals(0.25, toolsConfig.getCursorCooldownSeconds(ShopToolType.MONEY_STAFF));
    }

    @Test
    public void testCursorCooldownCustomConfiguration() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tools.money_staff.cursor-cooldown-seconds", 0.15);
        config.set("tools.bazaar_staff.cursor-cooldown-seconds", 0.10);

        ToolsConfig toolsConfig = new ToolsConfig(config);

        org.junit.jupiter.api.Assertions.assertEquals(0.15, toolsConfig.getCursorCooldownSeconds(ShopToolType.MONEY_STAFF));
        org.junit.jupiter.api.Assertions.assertEquals(0.10, toolsConfig.getCursorCooldownSeconds(ShopToolType.BAZAAR_STAFF));
    }

    @Test
    public void testUseNativeBazaarMessagesDefaultTrueAndConfigurable() {
        YamlConfiguration config = new YamlConfiguration();
        ToolsConfig toolsConfig = new ToolsConfig(config);
        assertTrue(toolsConfig.isUseNativeBazaarMessages(ShopToolType.BAZAAR_STAFF));

        config.set("tools.bazaar_staff.use-native-bazaar-messages", false);
        ToolsConfig customConfig = new ToolsConfig(config);
        assertFalse(customConfig.isUseNativeBazaarMessages(ShopToolType.BAZAAR_STAFF));
    }
}
