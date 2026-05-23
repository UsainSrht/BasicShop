package me.usainsrht.basicshop.gui;

import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.model.ShopCategory;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.TransactionResult;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.MainConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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

    private static final int CLASSIC_PAGE_SIZE = 45;
    private static final int[] MODERN_ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final MorePaperLib morePaperLib;
    private final Player viewer;
    private final ShopCategory category;
    private int page;

    private final int slotBack;
    private final int slotPrev;
    private final int slotNext;
    private final boolean modern;
    private final int pageSize;

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

        var catGuiCfg = configManager.getMainConfig().getCategoryGuiConfig();
        this.slotBack = catGuiCfg.backButton().slot();
        this.slotPrev = catGuiCfg.prevButton().slot();
        this.slotNext = catGuiCfg.nextButton().slot();
        this.modern   = configManager.getMainConfig().isModernItemListing();
        this.pageSize = modern ? MODERN_ITEM_SLOTS.length : CLASSIC_PAGE_SIZE;

        build();
    }

    private void build() {
        List<ShopItem> items = category.getItems();
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / pageSize));
        page = Math.max(0, Math.min(page, totalPages - 1));

        // Include page info in title only when there is more than one page
        String titleStr = category.getGuiTitle();
        if (totalPages > 1) {
            titleStr += configManager.getMainConfig().getCategoryGuiConfig().pageFormat()
                    .replace("<page>", String.valueOf(page + 1))
                    .replace("<total>", String.valueOf(totalPages));
        }
        Component title = MM.deserialize(titleStr);
        inventory = Bukkit.createInventory(this, 54, title);

        int start = page * pageSize;
        int end   = Math.min(start + pageSize, items.size());

        for (int i = start; i < end; i++) {
            ShopItem item = items.get(i);
            String itemName = configManager.getMainConfig().getItemDisplayName()
                    .replace("<item>", "<lang:" + item.getMaterial().translationKey() + ">");
            ItemStack icon = buildItem(item.getMaterial(), itemName, buildItemLore(item));
            int guiSlot = modern ? MODERN_ITEM_SLOTS[i - start] : (i - start);
            inventory.setItem(guiSlot, icon);
        }

        // Navigation bar (bottom row)
        MainConfig.CategoryGuiConfig catGuiCfg = configManager.getMainConfig().getCategoryGuiConfig();
        MainConfig.NavButtonConfig backCfg   = catGuiCfg.backButton();
        MainConfig.NavButtonConfig prevCfg   = catGuiCfg.prevButton();
        MainConfig.NavButtonConfig nextCfg   = catGuiCfg.nextButton();
        MainConfig.FillerConfig    fillerCfg = catGuiCfg.filler();

        if (page > 0) {
            inventory.setItem(slotPrev, buildItem(prevCfg.material(), prevCfg.name(), prevCfg.lore()));
        }
        inventory.setItem(slotBack, buildItem(backCfg.material(), backCfg.name(), backCfg.lore()));
        if (page < totalPages - 1) {
            inventory.setItem(slotNext, buildItem(nextCfg.material(), nextCfg.name(), nextCfg.lore()));
        }

        // Filler — bottom row only in classic mode, all empty slots in modern mode
        if (fillerCfg.enabled()) {
            ItemStack navFiller = buildItem(fillerCfg.material(), fillerCfg.name());
            ItemMeta navFillerMeta = navFiller.getItemMeta();
            if (navFillerMeta != null) {
                navFillerMeta.setHideTooltip(fillerCfg.hideTooltip());
                navFiller.setItemMeta(navFillerMeta);
            }
            int fillFrom = modern ? 0 : 45;
            for (int s = fillFrom; s < 54; s++) {
                if (inventory.getItem(s) == null) {
                    inventory.setItem(s, navFiller);
                }
            }
        }
    }

    private List<String> buildItemLore(ShopItem item) {
        boolean buyEnabled  = shopAPI.isBuyingEnabled() && item.canBuy();
        boolean sellEnabled = shopAPI.isSellingEnabled() && item.canSell();

        String disabledText = configManager.getMainConfig().getDisabledText();
        String priceBuy  = buyEnabled  ? configManager.getMainConfig().formatPrice(item.getBuyPrice().getAsDouble())  : disabledText;
        String priceSell = sellEnabled ? configManager.getMainConfig().formatPrice(item.getSellPrice().getAsDouble()) : disabledText;

        List<String> result = new ArrayList<>();
        for (String line : configManager.getMainConfig().getItemDisplayLore()) {
            boolean isBuyCond  = line.contains("<if_buy>");
            boolean isSellCond = line.contains("<if_sell>");

            if (isBuyCond  && !buyEnabled)  continue;
            if (isSellCond && !sellEnabled) continue;

            String resolved = line
                    .replace("<price_buy>",  priceBuy)
                    .replace("<price_sell>", priceSell)
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

        if (slot == slotBack) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                CategoriesGui cg = new CategoriesGui(configManager, shopAPI, morePaperLib, player);
                player.openInventory(cg.getInventory());
            }, null);
            return;
        }

        if (slot == slotPrev && page > 0) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                CategoryGui prev = new CategoryGui(configManager, shopAPI, morePaperLib, player, category, page - 1);
                player.openInventory(prev.getInventory());
            }, null);
            return;
        }

        if (slot == slotNext) {
            int totalPages = Math.max(1, (int) Math.ceil((double) category.getItems().size() / pageSize));
            if (page < totalPages - 1) {
                morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                    CategoryGui next = new CategoryGui(configManager, shopAPI, morePaperLib, player, category, page + 1);
                    player.openInventory(next.getInventory());
                }, null);
            }
            return;
        }

        // Item slots
        int itemIndex = findItemIndex(slot);
        if (itemIndex >= 0) {
            int index = page * pageSize + itemIndex;
            List<ShopItem> items = category.getItems();
            if (index >= items.size()) return;
            ShopItem item = items.get(index);

            var actions = configManager.getMainConfig().getClickActions();
            MainConfig.ClickAction action = actions.get(type);
            if (action != null) {
                boolean isBuy = action.type() == MainConfig.ActionType.BUY;
                handleTransaction(player, item, isBuy, action.amount());
            }
        }
    }

    private int findItemIndex(int slot) {
        if (modern) {
            for (int i = 0; i < MODERN_ITEM_SLOTS.length; i++) {
                if (MODERN_ITEM_SLOTS[i] == slot) return i;
            }
            return -1;
        }
        return (slot >= 0 && slot < CLASSIC_PAGE_SIZE) ? slot : -1;
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
                    .replace("<price>",  configManager.getMainConfig().formatPrice(price));
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

