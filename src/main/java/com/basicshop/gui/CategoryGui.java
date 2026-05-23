package com.basicshop.gui;

import com.basicshop.api.ShopAPI;
import com.basicshop.api.model.ShopCategory;
import com.basicshop.api.model.ShopItem;
import com.basicshop.api.model.TransactionResult;
import com.basicshop.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
 *   <li>Slot 53: next page button</li>
 *   <li>Slot 48: back button (return to categories)</li>
 * </ul>
 *
 * <p>Click actions on item slots (when quick-actions enabled):
 * <ul>
 *   <li>LEFT         → sell one</li>
 *   <li>SHIFT_LEFT   → sell stack</li>
 *   <li>DROP (Q)     → sell all</li>
 *   <li>RIGHT        → buy one</li>
 *   <li>SHIFT_RIGHT  → buy stack</li>
 * </ul>
 */
public final class CategoryGui extends AbstractShopGui {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_NEXT = 53;

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
        List<ShopItem> items = category.getItems();
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        // Include page info in title only when there is more than one page
        String titleStr = category.getDisplayName();
        if (totalPages > 1) {
            titleStr += " <gray>(" + (page + 1) + "/" + totalPages + ")";
        }
        Component title = MM.deserialize(titleStr);
        inventory = Bukkit.createInventory(this, 54, title);

        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, items.size());

        for (int i = start; i < end; i++) {
            ShopItem item = items.get(i);
            String itemName = configManager.getMainConfig().getItemDisplayName()
                    .replace("<item>", "<lang:" + item.getMaterial().translationKey() + ">");
            ItemStack icon = buildItem(item.getMaterial(), itemName, buildItemLore(item));
            inventory.setItem(i - start, icon);
        }

        // Navigation bar (bottom row)
        com.basicshop.config.MainConfig.CategoryGuiConfig catGuiCfg = configManager.getMainConfig().getCategoryGuiConfig();
        com.basicshop.config.MainConfig.NavButtonConfig backCfg   = catGuiCfg.backButton();
        com.basicshop.config.MainConfig.NavButtonConfig prevCfg   = catGuiCfg.prevButton();
        com.basicshop.config.MainConfig.NavButtonConfig nextCfg   = catGuiCfg.nextButton();
        com.basicshop.config.MainConfig.FillerConfig    fillerCfg = catGuiCfg.filler();

        if (page > 0) {
            inventory.setItem(SLOT_PREV, buildItem(prevCfg.material(), prevCfg.name(), prevCfg.lore()));
        }
        inventory.setItem(SLOT_BACK, buildItem(backCfg.material(), backCfg.name(), backCfg.lore()));
        if (page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, buildItem(nextCfg.material(), nextCfg.name(), nextCfg.lore()));
        }

        // Filler for the bottom row gaps
        if (fillerCfg.enabled()) {
            ItemStack navFiller = buildItem(fillerCfg.material(), fillerCfg.name());
            ItemMeta navFillerMeta = navFiller.getItemMeta();
            if (navFillerMeta != null) {
                navFillerMeta.setHideTooltip(fillerCfg.hideTooltip());
                navFiller.setItemMeta(navFillerMeta);
            }
            for (int s = 45; s < 54; s++) {
                if (inventory.getItem(s) == null) {
                    inventory.setItem(s, navFiller);
                }
            }
        }
    }

    private List<String> buildItemLore(ShopItem item) {
        boolean buyEnabled  = shopAPI.isBuyingEnabled() && item.canBuy();
        boolean sellEnabled = shopAPI.isSellingEnabled() && item.canSell();

        String priceBuy  = buyEnabled  ? String.format("%.2f", item.getBuyPrice().getAsDouble())  : null;
        String priceSell = sellEnabled ? String.format("%.2f", item.getSellPrice().getAsDouble()) : null;

        List<String> result = new ArrayList<>();
        for (String line : configManager.getMainConfig().getItemDisplayLore()) {
            boolean isBuyCond  = line.contains("<price_buy>")  || line.contains("<if_buy>");
            boolean isSellCond = line.contains("<price_sell>") || line.contains("<if_sell>");

            if (isBuyCond  && !buyEnabled)  continue;
            if (isSellCond && !sellEnabled) continue;

            String resolved = line
                    .replace("<price_buy>",  priceBuy  != null ? priceBuy  : "")
                    .replace("<price_sell>", priceSell != null ? priceSell : "")
                    .replace("<if_buy>",  "")
                    .replace("<if_sell>", "");
            result.add(resolved);
        }

        // Trim trailing blank lines
        while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot      = event.getRawSlot();
        ClickType type = event.getClick();

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

        // Item slots (0–44)
        if (slot >= 0 && slot < PAGE_SIZE) {
            int index = page * PAGE_SIZE + slot;
            List<ShopItem> items = category.getItems();
            if (index >= items.size()) return;
            ShopItem item = items.get(index);

            var actions = configManager.getMainConfig().getClickActions();
            com.basicshop.config.MainConfig.ClickAction action = actions.get(type);
            if (action != null) {
                boolean isBuy = action.type() == com.basicshop.config.MainConfig.ActionType.BUY;
                handleTransaction(player, item, isBuy, action.amount());
            }
        }
    }

    private void handleTransaction(Player player, ShopItem item, boolean isBuy, int amount) {
        TransactionResult result = isBuy
                ? shopAPI.buyItem(player, item, amount)
                : (amount == -1 ? shopAPI.sellAll(player, item) : shopAPI.sellItem(player, item, amount));

        sendTransactionMessage(player, item, result, isBuy, amount);

        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            CategoryGui refreshed = new CategoryGui(configManager, shopAPI, morePaperLib, player, category, page);
            player.openInventory(refreshed.getInventory());
        }, null);
    }

    private void sendTransactionMessage(Player player, ShopItem item, TransactionResult result, boolean isBuy, int amount) {
        String prefix = configManager.getMainConfig().getPrefix();
        if (result == TransactionResult.SUCCESS) {
            String key = isBuy ? "buy-success" : "sell-success";
            String msg = configManager.getMainConfig().getMessage(key);
            String amountStr = amount == -1 ? "all" : String.valueOf(amount);
            double price = isBuy
                    ? item.getBuyPrice().orElse(0)  * (amount == -1 ? 1 : amount)
                    : item.getSellPrice().orElse(0) * (amount == -1 ? 1 : amount);
            msg = msg.replace("<amount>", amountStr)
                    .replace("<item>",   "<lang:" + item.getMaterial().translationKey() + ">")
                    .replace("<price>",  String.format("%.2f", price));
            player.sendMessage(MM.deserialize(prefix + msg));
            return;
        }
        String key = switch (result) {
            case INSUFFICIENT_FUNDS   -> "insufficient-funds";
            case NOT_ENOUGH_ITEMS     -> "not-enough-items";
            case BUY_DISABLED         -> "item-buy-disabled";
            case SELL_DISABLED        -> "item-sell-disabled";
            case GLOBAL_BUY_DISABLED  -> "shop-buy-disabled";
            case GLOBAL_SELL_DISABLED -> "shop-sell-disabled";
            default                   -> "vault-unavailable";
        };
        player.sendMessage(MM.deserialize(prefix + configManager.getMainConfig().getMessage(key)));
    }
}

