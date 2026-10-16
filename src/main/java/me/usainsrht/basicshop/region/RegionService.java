package me.usainsrht.basicshop.region;

import org.bukkit.Location;

import java.util.Set;

/**
 * Service interface decoupling BasicShop from WorldGuard.
 */
public interface RegionService {

    /**
     * Returns true if WorldGuard is active and ready to query regions.
     */
    boolean isAvailable();

    /**
     * Gets the set of region IDs covering the given location (lowercased).
     *
     * @param location the location to query
     * @return set of region IDs, or an empty set if none or unavailable
     */
    Set<String> getRegionNames(Location location);
}
