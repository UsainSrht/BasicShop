package me.usainsrht.basicshop.region;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * WorldGuard integration for querying region membership at locations.
 */
public final class WorldGuardHook implements RegionService {

    private final RegionContainer container;

    public WorldGuardHook() {
        this.container = WorldGuard.getInstance().getPlatform().getRegionContainer();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Set<String> getRegionNames(Location location) {
        if (location == null || location.getWorld() == null) {
            return Collections.emptySet();
        }
        try {
            RegionQuery query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));
            Set<String> names = new HashSet<>();
            for (ProtectedRegion region : set) {
                if (region != null && region.getId() != null) {
                    names.add(region.getId().toLowerCase(Locale.ROOT));
                }
            }
            return Collections.unmodifiableSet(names);
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }
}
