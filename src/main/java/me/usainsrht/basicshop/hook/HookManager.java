package me.usainsrht.basicshop.hook;

import me.usainsrht.basicshop.analytics.TopSellersEngine;
import me.usainsrht.basicshop.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Manages soft-dependency integrations such as PlaceholderAPI and MiniPlaceholders.
 */
public final class HookManager {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final TopSellersEngine topSellersEngine;

    private BasicShopPAPIExpansion papiExpansion;
    private BasicShopMiniPlaceholdersExpansion miniPlaceholdersExpansion;

    public HookManager(Plugin plugin, ConfigManager configManager, TopSellersEngine topSellersEngine) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.topSellersEngine = topSellersEngine;
    }

    public void registerHooks() {
        // PlaceholderAPI
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                papiExpansion = new BasicShopPAPIExpansion(plugin, configManager, topSellersEngine);
                if (papiExpansion.register()) {
                    plugin.getLogger().info("Successfully hooked into PlaceholderAPI.");
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to register PlaceholderAPI expansion", t);
            }
        }

        // MiniPlaceholders v3
        if (Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            try {
                miniPlaceholdersExpansion = new BasicShopMiniPlaceholdersExpansion(plugin, configManager, topSellersEngine);
                miniPlaceholdersExpansion.register();
                plugin.getLogger().info("Successfully hooked into MiniPlaceholders v3.");
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to register MiniPlaceholders expansion", t);
            }
        }
    }

    public void unregisterHooks() {
        if (papiExpansion != null) {
            try {
                papiExpansion.unregister();
            } catch (Throwable ignored) {}
            papiExpansion = null;
        }
        if (miniPlaceholdersExpansion != null) {
            try {
                miniPlaceholdersExpansion.unregister();
            } catch (Throwable ignored) {}
            miniPlaceholdersExpansion = null;
        }
    }
}
