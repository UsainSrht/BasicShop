package me.usainsrht.basicshop.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.usainsrht.basicshop.analytics.TopSellersEngine;
import me.usainsrht.basicshop.analytics.model.TopSellerItem;
import me.usainsrht.basicshop.config.ConfigManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * PlaceholderAPI expansion for BasicShop.
 * Exposes top sellers placeholders like %basicshop_top_1_name%, %basicshop_top_1_profit%, etc.
 */
public final class BasicShopPAPIExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final TopSellersEngine topSellersEngine;

    public BasicShopPAPIExpansion(Plugin plugin, ConfigManager configManager, TopSellersEngine topSellersEngine) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.topSellersEngine = topSellersEngine;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "basicshop";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        // Syntax: top_<rank>_<field>
        // Examples: top_1_name, top_1_amount, top_1_profit, top_1_material, top_1_category
        if (params.startsWith("top_")) {
            String[] parts = params.split("_");
            if (parts.length >= 3) {
                try {
                    int rank = Integer.parseInt(parts[1]);
                    String field = parts[2].toLowerCase();

                    Optional<TopSellerItem> itemOpt = topSellersEngine.getByRank(rank);
                    if (itemOpt.isEmpty()) {
                        return "---";
                    }

                    TopSellerItem item = itemOpt.get();
                    return switch (field) {
                        case "name" -> item.material().name();
                        case "id" -> item.itemId();
                        case "amount" -> String.valueOf(item.unitsSold());
                        case "profit", "price" -> configManager.getMainConfig().formatPrice(item.totalProfit());
                        case "material" -> item.material().name();
                        case "category" -> item.categoryId();
                        default -> null;
                    };
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
