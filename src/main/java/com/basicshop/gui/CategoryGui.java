package com.basicshop.gui;

import com.basicshop.api.ShopAPI;
import com.basicshop.api.model.ShopCategory;
import com.basicshop.api.model.ShopItem;
import com.basicshop.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import space.arim.morepaperlib.MorePaperLib;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays all items in a single {@link ShopCategory} with pagination support.
 *
 * <p>Layout (54-slot chest):
 * <ul>
 *   <li>Slots 0–44: item icons (up to 45 per page)</li>
 *   <li>Slot 45: previous page button</li>
 *   <li>Slot 49: page info</li>
 *   <li>Slot 53: next page button</li>
 *   <li>Slot 48: back button (return to categories)</li>
 * </ul>
 */
public final class CategoryGui extends AbstractShopGui {

    private static final int PAGE_SIZE    = 45;
    private static final int SLOT_PREV    = 45;
    private static final int SLOT_BACK    = 48;
    private static final int SLOT_PAGE    = 49;
    private static final int SLOT_NEXT    = 53;

    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final MorePaperLib morePaperLib;
    private final Player viewer;
    private final ShopCategory category;
    private int page;

    public CategoryGui(
            ConfigManager configManager,
            ShopAPI shopAPI,
            MorePaperLib morePaperLib,
            Player viewer,
            ShopCategory category,
            int page
    ) {
        this.configManager = configManager;
        this.shopAPI       = shopAPI;
        this.morePaperLib  = morePaperLib;
        this.viewer        = viewer;
        this.category      = category;
        this.page          = page;
        build();
    }

    private void build() {
        Component title = MM.deserialize(category.getDisplayName());
        inventory = Bukkit.createInventory(this, 54, title);

        List<ShopItem> items = category.getItems();
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, items.size());

        for (int i = start; i < end; i++) {
            ShopItem item = items.get(i);
            List<String> lore = buildItemLore(item);
            ItemStack icon = buildItem(item.getMaterial(), item.getDisplayName(), lore);
            inventory.setItem(i - start, icon);
        }

        // Navigation bar (bottom row)
        if (page > 0) {
            inventory.setItem(SLOT_PREV, buildItem(Material.ARROW, "<gray>← Previous Page"));
        }
        inventory.setItem(SLOT_BACK, buildItem(Material.BARRIER, "<gray>✗ Back to Categories"));
        inventory.setItem(SLOT_PAGE, buildItem(Material.PAPER,
                "<gray>Page <white>" + (page + 1) + "</white> / <white>" + totalPages + "</white>"));
        if (page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, buildItem(Material.ARROW, "<gray>Next Page →"));
        }

        // Filler for the bottom row gaps
        ItemStack navFiller = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s < 54; s++) {
            if (inventory.getItem(s) == null) {
                inventory.setItem(s, navFiller);
            }
        }
    }

    private List<String> buildItemLore(ShopItem item) {
        List<String> lore = new ArrayList<>(item.getLore());
        if (!lore.isEmpty()) lore.add("");

        if (item.canBuy()) {
            lore.add("<gray>Buy: <gold>" + String.format("%.2f", item.getBuyPrice().getAsDouble()) + "</gold>");
        } else {
            lore.add("<dark_gray>Buy: <red>Disabled</red>");
        }
        if (item.canSell()) {
            lore.add("<gray>Sell: <gold>" + String.format("%.2f", item.getSellPrice().getAsDouble()) + "</gold>");
        } else {
            lore.add("<dark_gray>Sell: <red>Disabled</red>");
        }
        lore.add("");
        lore.add("<dark_gray>Click to open buy/sell menu.");
        return lore;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                CategoriesGui cg = new CategoriesGui(configManager, shopAPI, morePaperLib, player);
                player.openInventory(cg.getInventory());
            }, null);
            return;
        }

        if (slot == SLOT_PREV && page > 0) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                CategoryGui prev = new CategoryGui(configManager, shopAPI, morePaperLib, player, category, page - 1);
                player.openInventory(prev.getInventory());
            }, null);
            return;
        }

        if (slot == SLOT_NEXT) {
            int totalPages = Math.max(1, (int) Math.ceil((double) category.getItems().size() / PAGE_SIZE));
            if (page < totalPages - 1) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                    CategoryGui next = new CategoryGui(configManager, shopAPI, morePaperLib, player, category, page + 1);
                    player.openInventory(next.getInventory());
                }, null);
            }
            return;
        }

        // Item slot (0–44)
        if (slot >= 0 && slot < PAGE_SIZE) {
            int index = page * PAGE_SIZE + slot;
            List<ShopItem> items = category.getItems();
            if (index < items.size()) {
                ShopItem item = items.get(index);
                morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                    ItemGui itemGui = new ItemGui(configManager, shopAPI, morePaperLib, player, item, this);
                    player.openInventory(itemGui.getInventory());
                }, null);
            }
        }
    }
}
