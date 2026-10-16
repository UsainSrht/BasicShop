package me.usainsrht.basicshop.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ToolRestrictions(
        FilterMode worldMode,
        Set<String> worlds,
        FilterMode regionMode,
        Set<String> regions
) {
    public ToolRestrictions {
        worldMode = worldMode != null ? worldMode : FilterMode.BLACKLIST;
        worlds = worlds != null ? Collections.unmodifiableSet(worlds) : Collections.emptySet();
        regionMode = regionMode != null ? regionMode : FilterMode.BLACKLIST;
        regions = regions != null ? Collections.unmodifiableSet(regions) : Collections.emptySet();
    }

    public static ToolRestrictions defaultRestrictions() {
        return new ToolRestrictions(FilterMode.BLACKLIST, Collections.emptySet(), FilterMode.BLACKLIST, Collections.emptySet());
    }

    public static ToolRestrictions parse(ConfigurationSection section, ToolRestrictions fallback) {
        if (section == null) {
            return fallback != null ? fallback : defaultRestrictions();
        }

        FilterMode defaultWorldMode = fallback != null ? fallback.worldMode() : FilterMode.BLACKLIST;
        Set<String> defaultWorlds = fallback != null ? fallback.worlds() : Collections.emptySet();
        FilterMode defaultRegionMode = fallback != null ? fallback.regionMode() : FilterMode.BLACKLIST;
        Set<String> defaultRegions = fallback != null ? fallback.regions() : Collections.emptySet();

        FilterMode worldMode = section.contains("world-mode")
                ? FilterMode.fromString(section.getString("world-mode"))
                : defaultWorldMode;

        Set<String> worlds = section.contains("worlds")
                ? normalizeSet(section.getStringList("worlds"))
                : defaultWorlds;

        FilterMode regionMode = section.contains("region-mode")
                ? FilterMode.fromString(section.getString("region-mode"))
                : defaultRegionMode;

        Set<String> regions = section.contains("regions")
                ? normalizeSet(section.getStringList("regions"))
                : defaultRegions;

        return new ToolRestrictions(worldMode, worlds, regionMode, regions);
    }

    public static Set<String> normalizeSet(List<String> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String item : list) {
            if (item != null && !item.isBlank()) {
                normalized.add(item.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    public boolean isWorldAllowed(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return true;
        }
        String normalized = worldName.trim().toLowerCase(Locale.ROOT);
        if (worldMode == FilterMode.BLACKLIST) {
            return !worlds.contains(normalized);
        } else {
            return worlds.isEmpty() || worlds.contains(normalized);
        }
    }

    public boolean isRegionAllowed(Collection<String> activeRegions) {
        if (regionMode == FilterMode.BLACKLIST) {
            if (activeRegions == null || activeRegions.isEmpty() || regions.isEmpty()) {
                return true;
            }
            for (String active : activeRegions) {
                if (active != null && regions.contains(active.trim().toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
            return true;
        } else {
            if (regions.isEmpty()) {
                return true;
            }
            if (activeRegions == null || activeRegions.isEmpty()) {
                return false;
            }
            for (String active : activeRegions) {
                if (active != null && regions.contains(active.trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }
}
