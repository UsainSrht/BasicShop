package me.usainsrht.basicshop.gui;

import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.model.ShopCategory;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.TransactionResult;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.MainConfig;
import me.usainsrht.basicshop.util.ShopSounds;
import me.usainsrht.itemapi.itemtext.ItemText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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

    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final MorePaperLib morePaperLib;
    private final Player viewer;
    private final ShopCategory category;
    private final int rows;
    private int page;

    private final int slotBack;
    private final int slotPrev;
    private final int slotNext;
    private final boolean modern;
    private final int[] itemSlots;
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

        int configuredRows = category.getRows();
        if (configuredRows <= 0) {
            configuredRows = configManager.getMainConfig().getCategoryGuiConfig().rows();
        }
        this.rows = Math.max(1, Math.min(6, configuredRows));

        var catGuiCfg = configManager.getMainConfig().getCategoryGuiConfig();
        this.slotBack = adjustSlot(catGuiCfg.backButton().slot(), this.rows);
        this.slotPrev = adjustSlot(catGuiCfg.prevButton().slot(), this.rows);
        this.slotNext = adjustSlot(catGuiCfg.nextButton().slot(), this.rows);
        this.modern   = configManager.getMainConfig().isModernItemListing();
        this.itemSlots = computeItemSlots(this.rows, this.modern);
        this.pageSize = this.itemSlots.length;

        build();
    }

    private static int adjustSlot(int slot, int rows) {
        int totalSlots = rows * 9;
        if (rows == 6) {
            return Math.min(slot, totalSlots - 1);
        }
        if (slot >= 45 && slot <= 53) {
            int col = slot - 45;
            return (rows - 1) * 9 + col;
        }
        if (slot >= totalSlots) {
            return (rows - 1) * 9 + (slot % 9);
        }
        return slot;
    }

    public static int[] computeItemSlots(int rows, boolean modern) {
        if (rows <= 1) {
            return new int[]{1, 2, 3, 5, 6, 7};
        }
        if (modern) {
            if (rows == 2) {
                return new int[]{1, 2, 3, 4, 5, 6, 7};
            }
            int numRows = rows - 2;
            int[] slots = new int[numRows * 7];
            int idx = 0;
            for (int r = 1; r <= rows - 2; r++) {
                for (int c = 1; c <= 7; c++) {
                    slots[idx++] = r * 9 + c;
                }
            }
            return slots;
        } else {
            int count = (rows - 1) * 9;
            int[] slots = new int[count];
            for (int i = 0; i < count; i++) {
                slots[i] = i;
            }
            return slots;
        }
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
        inventory = Bukkit.createInventory(this, rows * 9, title);

        int start = page * pageSize;
        int end   = Math.min(start + pageSize, items.size());

        for (int i = start; i < end; i++) {
            ShopItem item = items.get(i);
            String itemName = configManager.getMainConfig().getItemDisplayName()
                    .replace("<item>", "<lang:" + item.getMaterial().translationKey() + ">");
            ItemStack icon = buildItem(item.getMaterial(), itemName, buildItemLore(item));
            int guiSlot = itemSlots[i - start];
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
            int fillFrom = modern ? 0 : (rows - 1) * 9;
            for (int s = fillFrom; s < inventory.getSize(); s++) {
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
            ShopSounds.play(player, configManager.getMessagesConfig(), "back-to-categories-sound");
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                CategoriesGui cg = new CategoriesGui(configManager, shopAPI, morePaperLib, player);
                player.openInventory(cg.getInventory());
            }, null);
            return;
        }

        if (slot == slotPrev && page > 0) {
            ShopSounds.play(player, configManager.getMessagesConfig(), "gui-click-sound");
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                CategoryGui prev = new CategoryGui(configManager, shopAPI, morePaperLib, player, category, page - 1);
                player.openInventory(prev.getInventory());
            }, null);
            return;
        }

        if (slot == slotNext) {
            int totalPages = Math.max(1, (int) Math.ceil((double) category.getItems().size() / pageSize));
            if (page < totalPages - 1) {
                ShopSounds.play(player, configManager.getMessagesConfig(), "gui-click-sound");
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
        for (int i = 0; i < itemSlots.length; i++) {
            if (itemSlots[i] == slot) return i;
        }
        return -1;
    }

    private void handleTransaction(Player player, ShopItem item, boolean isBuy, int amount) {
        int finalAmount = amount;
        if (!isBuy) {
            int available = 0;
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && stack.getType() == item.getMaterial()) {
                    available += stack.getAmount();
                }
            }
            if (amount == -1) {
                finalAmount = available;
            } else {
                finalAmount = Math.min(amount, available);
            }

            if (finalAmount <= 0) {
                sendTransactionMessage(player, item, TransactionResult.NOT_ENOUGH_ITEMS, false, 0);
                morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                    CategoryGui refreshed = new CategoryGui(configManager, shopAPI, morePaperLib, player, category, page);
                    player.openInventory(refreshed.getInventory());
                }, null);
                return;
            }
        }

        TransactionResult result = isBuy
                ? shopAPI.buyItem(player, item, amount)
                : (amount == -1 ? shopAPI.sellAll(player, item) : shopAPI.sellItem(player, item, finalAmount));

        sendTransactionMessage(player, item, result, isBuy, finalAmount);

        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            CategoryGui refreshed = new CategoryGui(configManager, shopAPI, morePaperLib, player, category, page);
            player.openInventory(refreshed.getInventory());
        }, null);
    }

    private void sendTransactionMessage(Player player, ShopItem item, TransactionResult result, boolean isBuy, int amount) {
        if (result == TransactionResult.SUCCESS) {
            String key = isBuy ? "buy-success" : "sell-success";
            double price = (isBuy ? item.getBuyPrice().orElse(0) : item.getSellPrice().orElse(0)) * amount;

            ItemStack itemStack = new ItemStack(item.getMaterial(), 1);
            Component itemTextComp = ItemText.format(itemStack, b -> b.amount(amount));

            configManager.getMessagesConfig().send(player, key,
                    Placeholder.unparsed("amount", String.valueOf(amount)),
                    Placeholder.component("item", itemTextComp),
                    Placeholder.unparsed("price", configManager.getMainConfig().formatPrice(price)));
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
        configManager.getMessagesConfig().send(player, key);
    }
}

