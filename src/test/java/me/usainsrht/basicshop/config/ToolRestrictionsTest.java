package me.usainsrht.basicshop.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ToolRestrictionsTest {

    @Test
    public void testDefaultRestrictions() {
        ToolRestrictions restrictions = ToolRestrictions.defaultRestrictions();
        assertEquals(FilterMode.BLACKLIST, restrictions.worldMode());
        assertTrue(restrictions.worlds().isEmpty());
        assertEquals(FilterMode.BLACKLIST, restrictions.regionMode());
        assertTrue(restrictions.regions().isEmpty());

        assertTrue(restrictions.isWorldAllowed("world"));
        assertTrue(restrictions.isWorldAllowed("world_nether"));
        assertTrue(restrictions.isRegionAllowed(Collections.emptySet()));
        assertTrue(restrictions.isRegionAllowed(Set.of("spawn", "pvp")));
    }

    @Test
    public void testWorldBlacklistMode() {
        ToolRestrictions restrictions = new ToolRestrictions(
                FilterMode.BLACKLIST,
                Set.of("world_nether", "world_the_end"),
                FilterMode.BLACKLIST,
                Collections.emptySet()
        );

        assertTrue(restrictions.isWorldAllowed("world"));
        assertFalse(restrictions.isWorldAllowed("world_nether"));
        assertFalse(restrictions.isWorldAllowed("WORLD_NETHER"));
        assertFalse(restrictions.isWorldAllowed("world_the_end"));
        assertTrue(restrictions.isWorldAllowed("custom_world"));
        assertTrue(restrictions.isWorldAllowed(null));
    }

    @Test
    public void testWorldWhitelistMode() {
        ToolRestrictions restrictions = new ToolRestrictions(
                FilterMode.WHITELIST,
                Set.of("world", "mining_world"),
                FilterMode.BLACKLIST,
                Collections.emptySet()
        );

        assertTrue(restrictions.isWorldAllowed("world"));
        assertTrue(restrictions.isWorldAllowed("WORLD"));
        assertTrue(restrictions.isWorldAllowed("mining_world"));
        assertFalse(restrictions.isWorldAllowed("world_nether"));
        assertFalse(restrictions.isWorldAllowed("world_the_end"));

        // Empty whitelist should allow all
        ToolRestrictions emptyWhitelist = new ToolRestrictions(
                FilterMode.WHITELIST,
                Collections.emptySet(),
                FilterMode.BLACKLIST,
                Collections.emptySet()
        );
        assertTrue(emptyWhitelist.isWorldAllowed("world"));
        assertTrue(emptyWhitelist.isWorldAllowed("world_nether"));
    }

    @Test
    public void testRegionBlacklistMode() {
        ToolRestrictions restrictions = new ToolRestrictions(
                FilterMode.BLACKLIST,
                Collections.emptySet(),
                FilterMode.BLACKLIST,
                Set.of("spawn", "pvp_arena")
        );

        // Location in wild (no regions)
        assertTrue(restrictions.isRegionAllowed(Collections.emptySet()));
        assertTrue(restrictions.isRegionAllowed(null));

        // Location in allowed region
        assertTrue(restrictions.isRegionAllowed(Set.of("market")));

        // Location in blacklisted region
        assertFalse(restrictions.isRegionAllowed(Set.of("spawn")));
        assertFalse(restrictions.isRegionAllowed(Set.of("SPAWN")));
        assertFalse(restrictions.isRegionAllowed(Set.of("pvp_arena")));

        // Location overlapping allowed and blacklisted regions
        assertFalse(restrictions.isRegionAllowed(Set.of("market", "spawn")));
    }

    @Test
    public void testRegionWhitelistMode() {
        ToolRestrictions restrictions = new ToolRestrictions(
                FilterMode.BLACKLIST,
                Collections.emptySet(),
                FilterMode.WHITELIST,
                Set.of("shop_region", "spawn")
        );

        // Location in whitelisted region
        assertTrue(restrictions.isRegionAllowed(Set.of("shop_region")));
        assertTrue(restrictions.isRegionAllowed(Set.of("SPAWN")));
        assertTrue(restrictions.isRegionAllowed(Set.of("wilderness_claim", "shop_region")));

        // Location in non-whitelisted region
        assertFalse(restrictions.isRegionAllowed(Set.of("pvp_arena")));

        // Location with no regions (wild)
        assertFalse(restrictions.isRegionAllowed(Collections.emptySet()));
        assertFalse(restrictions.isRegionAllowed(null));

        // Empty whitelist allows all
        ToolRestrictions emptyWhitelist = new ToolRestrictions(
                FilterMode.BLACKLIST,
                Collections.emptySet(),
                FilterMode.WHITELIST,
                Collections.emptySet()
        );
        assertTrue(emptyWhitelist.isRegionAllowed(Collections.emptySet()));
        assertTrue(emptyWhitelist.isRegionAllowed(Set.of("any_region")));
    }

    @Test
    public void testParseConfiguration() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("restrictions.world-mode", "WHITELIST");
        config.set("restrictions.worlds", List.of("World", "World_Nether"));
        config.set("restrictions.region-mode", "BLACKLIST");
        config.set("restrictions.regions", List.of("Spawn", "PVP"));

        ToolRestrictions restrictions = ToolRestrictions.parse(config.getConfigurationSection("restrictions"), null);

        assertEquals(FilterMode.WHITELIST, restrictions.worldMode());
        assertTrue(restrictions.worlds().contains("world"));
        assertTrue(restrictions.worlds().contains("world_nether"));
        assertEquals(FilterMode.BLACKLIST, restrictions.regionMode());
        assertTrue(restrictions.regions().contains("spawn"));
        assertTrue(restrictions.regions().contains("pvp"));
    }

    @Test
    public void testParseFallbackInheritance() {
        ToolRestrictions global = new ToolRestrictions(
                FilterMode.WHITELIST,
                Set.of("world_main"),
                FilterMode.BLACKLIST,
                Set.of("global_blocked_region")
        );

        YamlConfiguration config = new YamlConfiguration();
        // Override only regions
        config.set("tool.restrictions.region-mode", "WHITELIST");
        config.set("tool.restrictions.regions", List.of("farm_region"));

        ToolRestrictions toolRes = ToolRestrictions.parse(config.getConfigurationSection("tool.restrictions"), global);

        // Inherited from global
        assertEquals(FilterMode.WHITELIST, toolRes.worldMode());
        assertEquals(Set.of("world_main"), toolRes.worlds());

        // Overridden
        assertEquals(FilterMode.WHITELIST, toolRes.regionMode());
        assertEquals(Set.of("farm_region"), toolRes.regions());
    }
}
