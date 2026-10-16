package me.usainsrht.basicshop.region;

import org.bukkit.Location;

import java.util.Collections;
import java.util.Set;

/**
 * Fallback implementation used when WorldGuard is not installed.
 */
public final class NoOpRegionService implements RegionService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Set<String> getRegionNames(Location location) {
        return Collections.emptySet();
    }
}
