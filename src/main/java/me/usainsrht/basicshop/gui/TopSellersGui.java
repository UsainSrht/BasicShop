package me.usainsrht.basicshop.gui;

import me.usainsrht.basicshop.analytics.TopSellersEngine;
import me.usainsrht.basicshop.analytics.model.TopSellerItem;
import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.event.ShopOpenEvent;
import me.usainsrht.basicshop.api.event.ShopViewType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.TopSellersConfig;
import me.usainsrht.basicshop.util.ShopSounds;
import me.usainsrht.itemapi.itemtext.ItemText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import space.arim.morepaperlib.MorePaperLib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI showing the top selling items in the shop.
 */
public final class TopSellersGui extends AbstractShopGui {

    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final MorePaperLib morePaperLib;
    private final TopSellersEngine topSellersEngine;
    private final Player viewer;

    private final Map<Integer, TopSellerItem> slotToItem = new HashMap<>();

    public TopSellersGui(
            ConfigManager configManager,
            ShopAPI shopAPI,
            MorePaperLib morePaperLib,
            TopSellersEngine topSellersEngine,
            Player viewer
    ) {
        this.configManager = configManager;
        this.shopAPI = shopAPI;
        this.morePaperLib = morePaperLib;
        this.topSellersEngine = topSellersEngine;
        this.viewer = viewer;
        build();
    }

    private void build() {
        TopSellersConfig cfg = configManager.getTopSellersConfig();
        int days = configManager.getMainConfig().getAnalyticsSettings().topSellers().days();

        Component title = MM.deserialize(cfg.getGuiTitle(), Placeholder.unparsed("days", String.valueOf(days)));
        inventory = Bukkit.createInventory(this, cfg.getGuiRows() * 9, title);

        // Place back button
        if (cfg.getBackButtonSlot() >= 0 && cfg.getBackButtonSlot() < inventory.getSize()) {
            ItemStack backItem = buildItem(cfg.getBackButtonMaterial(), cfg.getBackButtonName(), cfg.getBackButtonLore());
            inventory.setItem(cfg.getBackButtonSlot(), backItem);
        }

        List<TopSellerItem> topList = topSellersEngine.getTopSellers();

        if (topList.isEmpty()) {
            // Display empty notice
            if (cfg.getEmptySlot() >= 0 && cfg.getEmptySlot() < inventory.getSize()) {
                List<String> lore = cfg.getEmptyItemLore().stream()
                        .map(line -> line.replace("<days>", String.valueOf(days)))
                        .toList();
                ItemStack emptyItem = buildItem(cfg.getEmptyItemMaterial(), cfg.getEmptyItemName(), lore);
                inventory.setItem(cfg.getEmptySlot(), emptyItem);
            }
        } else {
            List<Integer> slots = cfg.getItemSlots();
            int max = Math.min(topList.size(), slots.size());

            for (int i = 0; i < max; i++) {
                int slot = slots.get(i);
                if (slot < 0 || slot >= inventory.getSize()) continue;

                TopSellerItem topItem = topList.get(i);
                slotToItem.put(slot, topItem);

                ItemStack icon = buildTopItemIcon(topItem, cfg);
                inventory.setItem(slot, icon);
            }
        }

        // Fill remaining slots
        if (cfg.isFillerEnabled()) {
            fillEmpty(cfg.getFillerMaterial(), cfg.getFillerName(), cfg.isFillerHideTooltip());
        }
    }

    private ItemStack buildTopItemIcon(TopSellerItem topItem, TopSellersConfig cfg) {
        ItemStack base = new ItemStack(topItem.material());
        ItemMeta meta = base.getItemMeta();
        if (meta == null) return base;

        Component itemComponent = ItemText.format(base, b -> b.amount(1));
        double sellPrice = shopAPI.getItem(topItem.itemId())
                .flatMap(si -> si.getSellPrice().isPresent() ? java.util.Optional.of(si.getSellPrice().getAsDouble()) : java.util.Optional.empty())
                .orElse(0.0);

        String formattedProfit = configManager.getMainConfig().formatPrice(topItem.totalProfit());
        String formattedPrice = configManager.getMainConfig().formatPrice(sellPrice);

        // Display Name
        String nameTemplate = cfg.getItemName();
        Component displayName = MM.deserialize(nameTemplate,
                Placeholder.unparsed("rank", String.valueOf(topItem.rank())),
                Placeholder.component("item", itemComponent)
        ).decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);

        // Lore
        List<Component> lore = new ArrayList<>();
        for (String raw : cfg.getItemLore()) {
            Component line = MM.deserialize(raw,
                    Placeholder.unparsed("rank", String.valueOf(topItem.rank())),
                    Placeholder.component("item", itemComponent),
                    Placeholder.unparsed("amount", String.valueOf(topItem.unitsSold())),
                    Placeholder.unparsed("total_profit", formattedProfit),
                    Placeholder.unparsed("sell_price", formattedPrice),
                    Placeholder.unparsed("category", topItem.categoryId())
            ).decoration(TextDecoration.ITALIC, false);
            lore.add(line);
        }
        meta.lore(lore);

        base.setItemMeta(meta);
        return base;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        TopSellersConfig cfg = configManager.getTopSellersConfig();

        // Back button
        if (slot == cfg.getBackButtonSlot()) {
            ShopSounds.play(player, configManager.getMessagesConfig(), "back-to-categories-sound");
            if (cfg.isBackButtonReturnToCategories()) {
                morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                    CategoriesGui categoriesGui = new CategoriesGui(configManager, shopAPI, morePaperLib, topSellersEngine, player);
                    player.openInventory(categoriesGui.getInventory());
                }, null);
            } else {
                player.closeInventory();
            }
            return;
        }

        // Top seller item clicked: navigate to that item's category if configured
        TopSellerItem clickedItem = slotToItem.get(slot);
        if (clickedItem != null) {
            shopAPI.getCategory(clickedItem.categoryId()).ifPresent(category -> {
                ShopOpenEvent openEvent = new ShopOpenEvent(player, ShopViewType.CATEGORY, category);
                Bukkit.getPluginManager().callEvent(openEvent);
                if (openEvent.isCancelled()) return;

                ShopSounds.play(player, configManager.getMessagesConfig(), "open-category-sound");
                morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                    CategoryGui categoryGui = new CategoryGui(configManager, shopAPI, morePaperLib, player, category, 0);
                    player.openInventory(categoryGui.getInventory());
                }, null);
            });
        }
    }
}
