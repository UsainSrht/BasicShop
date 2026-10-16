package me.usainsrht.basicshop.config;

import me.usainsrht.basicshop.api.model.ShopToolType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    public void testEnabledDefaultsToTrueForAllTools() {
        YamlConfiguration config = new YamlConfiguration();
        ToolsConfig toolsConfig = new ToolsConfig(config);

        for (ShopToolType type : ShopToolType.values()) {
            assertTrue(toolsConfig.isEnabled(type),
                    "Tool " + type.getId() + " should default to enabled = true");
        }
    }

    @Test
    public void testEnabledConfigurablePerTool() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tools.money_staff.enabled", false);
        config.set("tools.money_hoe.enabled", true);
        config.set("tools.sorting_staff.enabled", false);
        config.set("tools.bazaar_staff.enabled", false);

        ToolsConfig toolsConfig = new ToolsConfig(config);

        assertFalse(toolsConfig.isEnabled(ShopToolType.MONEY_STAFF));
        assertTrue(toolsConfig.isEnabled(ShopToolType.MONEY_HOE));
        assertFalse(toolsConfig.isEnabled(ShopToolType.SORTING_STAFF));
        assertFalse(toolsConfig.isEnabled(ShopToolType.BAZAAR_STAFF));
    }

    @Test
    public void testCursorActionsEnabledDefaultsToTrue() {
        YamlConfiguration config = new YamlConfiguration();
        ToolsConfig toolsConfig = new ToolsConfig(config);

        assertTrue(toolsConfig.isCursorActionsEnabled(ShopToolType.MONEY_STAFF));
        assertTrue(toolsConfig.isCursorActionsEnabled(ShopToolType.BAZAAR_STAFF));
    }

    @Test
    public void testCursorActionsEnabledConfigurable() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tools.money_staff.cursor-actions-enabled", false);
        config.set("tools.bazaar_staff.cursor-actions-enabled", true);

        ToolsConfig toolsConfig = new ToolsConfig(config);

        assertFalse(toolsConfig.isCursorActionsEnabled(ShopToolType.MONEY_STAFF));
        assertTrue(toolsConfig.isCursorActionsEnabled(ShopToolType.BAZAAR_STAFF));
    }

    @Test
    public void testHotReloadUpdatesEnabledAndCursorActions() {
        YamlConfiguration config = new YamlConfiguration();
        ToolsConfig initial = new ToolsConfig(config);
        assertTrue(initial.isEnabled(ShopToolType.MONEY_STAFF));
        assertTrue(initial.isCursorActionsEnabled(ShopToolType.MONEY_STAFF));

        // Simulate config edit and reload
        config.set("tools.money_staff.enabled", false);
        config.set("tools.money_staff.cursor-actions-enabled", false);
        ToolsConfig reloaded = new ToolsConfig(config);

        assertFalse(reloaded.isEnabled(ShopToolType.MONEY_STAFF));
        assertFalse(reloaded.isCursorActionsEnabled(ShopToolType.MONEY_STAFF));
    }

    @Test
    public void testGlobalAndPerToolRestrictions() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tools.restrictions.world-mode", "BLACKLIST");
        config.set("tools.restrictions.worlds", java.util.List.of("world_nether"));
        config.set("tools.restrictions.region-mode", "BLACKLIST");
        config.set("tools.restrictions.regions", java.util.List.of("spawn"));

        // Override money_hoe to have its own region restrictions
        config.set("tools.money_hoe.restrictions.region-mode", "WHITELIST");
        config.set("tools.money_hoe.restrictions.regions", java.util.List.of("farm_area"));

        ToolsConfig toolsConfig = new ToolsConfig(config);

        // Global restrictions
        ToolRestrictions global = toolsConfig.getGlobalRestrictions();
        assertEquals(FilterMode.BLACKLIST, global.worldMode());
        assertTrue(global.worlds().contains("world_nether"));
        assertEquals(FilterMode.BLACKLIST, global.regionMode());
        assertTrue(global.regions().contains("spawn"));

        // Money Staff inherits global
        ToolRestrictions staffRes = toolsConfig.getRestrictions(ShopToolType.MONEY_STAFF);
        assertEquals(FilterMode.BLACKLIST, staffRes.worldMode());
        assertTrue(staffRes.worlds().contains("world_nether"));
        assertEquals(FilterMode.BLACKLIST, staffRes.regionMode());
        assertTrue(staffRes.regions().contains("spawn"));

        // Money Hoe overrides regions, inherits worlds
        ToolRestrictions hoeRes = toolsConfig.getRestrictions(ShopToolType.MONEY_HOE);
        assertEquals(FilterMode.BLACKLIST, hoeRes.worldMode());
        assertTrue(hoeRes.worlds().contains("world_nether"));
        assertEquals(FilterMode.WHITELIST, hoeRes.regionMode());
        assertTrue(hoeRes.regions().contains("farm_area"));
        assertFalse(hoeRes.regions().contains("spawn"));
    }
}
