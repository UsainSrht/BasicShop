package me.usainsrht.basicshop.hook;

import io.github.miniplaceholders.api.Expansion;
import me.usainsrht.basicshop.analytics.TopSellersEngine;
import me.usainsrht.basicshop.analytics.model.TopSellerItem;
import me.usainsrht.basicshop.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * MiniPlaceholders v3 expansion for BasicShop.
 * Exposes top sellers tags such as:
 * - <basicshop_top:1:name>
 * - <basicshop_top:1:profit>
 * - <basicshop_top:1:amount>
 * And flat tags like <basicshop_top_1_name>, <basicshop_top_1_profit>, etc.
 */
public final class BasicShopMiniPlaceholdersExpansion {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final TopSellersEngine topSellersEngine;
    private Expansion expansion;

    public BasicShopMiniPlaceholdersExpansion(Plugin plugin, ConfigManager configManager, TopSellersEngine topSellersEngine) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.topSellersEngine = topSellersEngine;
    }

    public void register() {
        Expansion.Builder builder = Expansion.builder("basicshop")
                .author(String.join(", ", plugin.getPluginMeta().getAuthors()))
                .version(plugin.getPluginMeta().getVersion());

        // Parameterized placeholder: <basicshop_top:<rank>:<field>>
        builder.globalPlaceholder("top", (queue, ctx) -> {
            if (!queue.hasNext()) {
                return null;
            }
            int rank;
            try {
                rank = Integer.parseInt(queue.pop().value());
            } catch (NumberFormatException e) {
                return null;
            }
            String field = queue.hasNext() ? queue.pop().value().toLowerCase() : "name";
            return resolveTag(rank, field);
        });

        // Register flat tags for ranks 1 to 20 for convenience: <basicshop_top_1_name>, etc.
        for (int r = 1; r <= 20; r++) {
            final int rank = r;
            builder.globalPlaceholder("top_" + rank + "_name", (queue, ctx) -> resolveTag(rank, "name"));
            builder.globalPlaceholder("top_" + rank + "_id", (queue, ctx) -> resolveTag(rank, "id"));
            builder.globalPlaceholder("top_" + rank + "_amount", (queue, ctx) -> resolveTag(rank, "amount"));
            builder.globalPlaceholder("top_" + rank + "_profit", (queue, ctx) -> resolveTag(rank, "profit"));
            builder.globalPlaceholder("top_" + rank + "_price", (queue, ctx) -> resolveTag(rank, "price"));
            builder.globalPlaceholder("top_" + rank + "_material", (queue, ctx) -> resolveTag(rank, "material"));
            builder.globalPlaceholder("top_" + rank + "_category", (queue, ctx) -> resolveTag(rank, "category"));
        }

        expansion = builder.build();
        expansion.register();
    }

    public void unregister() {
        if (expansion != null && expansion.registered()) {
            expansion.unregister();
        }
    }

    private Tag resolveTag(int rank, String field) {
        Optional<TopSellerItem> itemOpt = topSellersEngine.getByRank(rank);
        if (itemOpt.isEmpty()) {
            return Tag.selfClosingInserting(Component.text("---"));
        }

        TopSellerItem item = itemOpt.get();
        String val = switch (field) {
            case "name" -> item.material().name();
            case "id" -> item.itemId();
            case "amount" -> String.valueOf(item.unitsSold());
            case "profit", "price" -> configManager.getMainConfig().formatPrice(item.totalProfit());
            case "material" -> item.material().name();
            case "category" -> item.categoryId();
            default -> "---";
        };
        return Tag.selfClosingInserting(Component.text(val));
    }
}
