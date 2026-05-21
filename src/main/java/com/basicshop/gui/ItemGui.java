package com.basicshop.gui;

import com.basicshop.api.ShopAPI;
import com.basicshop.api.model.ShopItem;
import com.basicshop.api.model.TransactionResult;
import com.basicshop.config.BuySellGuiConfig;
import com.basicshop.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import space.arim.morepaperlib.MorePaperLib;

import java.util.ArrayList;
import java.util.List;

/**
 * The buy/sell GUI for a single {@link ShopItem}.
 *
 * <p>Controls:
 * <ul>
 *   <li>LEFT  on buy-one    → buy 1</li>
 *   <li>SHIFT_LEFT on buy-stack → buy 64</li>
 *   <li>RIGHT on sell-one   → sell 1</li>
 *   <li>SHIFT_RIGHT on sell-all → sell all</li>
 *   <li>DROP  on sell-stack → sell 1 stack (hand amount)</li>
 * </ul>
 *
 * <p>Disabled operations show a barrier button with no click effect.
 * All button lores use MiniMessage {@code <key:...>} tags so the client
 * dynamically renders the player's actual keybinds.
 */
public final class ItemGui extends AbstractShopGui {

    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final MorePaperLib morePaperLib;
    private final Player viewer;
    private final ShopItem item;
    private final AbstractShopGui parent; // CategoryGui to return to

    public ItemGui(
            ConfigManager configManager,
            ShopAPI shopAPI,
            MorePaperLib morePaperLib,
            Player viewer,
            ShopItem item,
            AbstractShopGui parent
    ) {
        this.configManager = configManager;
        this.shopAPI       = shopAPI;
        this.morePaperLib  = morePaperLib;
        this.viewer        = viewer;
        this.item          = item;
        this.parent        = parent;
        build();
    }

    private void build() {
        BuySellGuiConfig cfg = configManager.getBuySellGuiConfig();

        String titleStr = cfg.getGuiTitleTemplate().replace("<item>", item.getDisplayName());
        Component title = MM.deserialize(titleStr);
        int rows = Math.max(1, Math.min(6, cfg.getGuiRows()));
        inventory = Bukkit.createInventory(this, rows * 9, title);

        // Item display
        ItemStack display = buildItemDisplay();
        inventory.setItem(cfg.getSlotItemDisplay(), display);

        // Buy buttons
        if (item.canBuy() && shopAPI.isBuyingEnabled()) {
            double buyOnePrice   = item.getBuyPrice().getAsDouble();
            double buyStackPrice = buyOnePrice * 64;
            inventory.setItem(cfg.getSlotBuyOne(),   buildActionButton(cfg.getBuyOneButton(),   buyOnePrice,   1,  item.getDisplayName()));
            inventory.setItem(cfg.getSlotBuyStack(), buildActionButton(cfg.getBuyStackButton(), buyStackPrice, 64, item.getDisplayName()));
        } else {
            inventory.setItem(cfg.getSlotBuyOne(),   buildDisabled(cfg));
            inventory.setItem(cfg.getSlotBuyStack(), buildDisabled(cfg));
        }

        // Sell buttons
        if (item.canSell() && shopAPI.isSellingEnabled()) {
            double sellOnePrice = item.getSellPrice().getAsDouble();
            double sellAllPrice = sellOnePrice; // shown as per-unit in label
            inventory.setItem(cfg.getSlotSellOne(), buildActionButton(cfg.getSellOneButton(),   sellOnePrice, 1,  item.getDisplayName()));
            inventory.setItem(cfg.getSlotSellAll(), buildActionButton(cfg.getSellAllButton(),   sellAllPrice, -1, item.getDisplayName())); // -1 = "all"
            // Sell-stack button at a fixed slot (slotSellOne + 2 = sell-stack; use sell-stack slot = sellAll + 1)
            int sellStackSlot = cfg.getSlotSellAll() + 1;
            if (sellStackSlot < rows * 9 && sellStackSlot != cfg.getSlotBack()) {
                inventory.setItem(sellStackSlot, buildActionButton(cfg.getSellStackButton(), sellOnePrice, 0, item.getDisplayName()));
            }
        } else {
            inventory.setItem(cfg.getSlotSellOne(), buildDisabled(cfg));
            inventory.setItem(cfg.getSlotSellAll(), buildDisabled(cfg));
        }

        // Back button
        BuySellGuiConfig.ButtonConfig backCfg = cfg.getBackButton();
        inventory.setItem(cfg.getSlotBack(), buildItem(backCfg.material(), backCfg.name(), backCfg.lore()));

        // Filler
        if (cfg.isFillerEnabled()) {
            fillEmpty(cfg.getFillerMaterial(), cfg.getFillerName());
        }
    }

    private ItemStack buildItemDisplay() {
        List<String> lore = new ArrayList<>(item.getLore());
        if (!lore.isEmpty()) lore.add("");

        String buyStr  = item.canBuy()  ? "<gold>" + String.format("%.2f", item.getBuyPrice().getAsDouble())  : "<red>Disabled";
        String sellStr = item.canSell() ? "<gold>" + String.format("%.2f", item.getSellPrice().getAsDouble()) : "<red>Disabled";

        lore.add("<gray>Buy price:  " + buyStr);
        lore.add("<gray>Sell price: " + sellStr);
        return buildItem(item.getMaterial(), item.getDisplayName(), lore);
    }

    private ItemStack buildActionButton(BuySellGuiConfig.ButtonConfig btnCfg, double price, int amount, String itemName) {
        List<String> lore = new ArrayList<>(btnCfg.lore());
        // Replace placeholders
        lore.replaceAll(line -> line
                .replace("<price>",  String.format("%.2f", price))
                .replace("<amount>", amount == -1 ? "all" : String.valueOf(amount))
                .replace("<item>",   itemName));
        return buildItem(btnCfg.material(), btnCfg.name()
                .replace("<price>",  String.format("%.2f", price))
                .replace("<amount>", amount == -1 ? "all" : String.valueOf(amount))
                .replace("<item>",   itemName),
                lore);
    }

    private ItemStack buildDisabled(BuySellGuiConfig cfg) {
        BuySellGuiConfig.ButtonConfig dis = cfg.getDisabledButton();
        return buildItem(dis.material(), dis.name(), dis.lore());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        BuySellGuiConfig cfg = configManager.getBuySellGuiConfig();
        int slot      = event.getRawSlot();
        ClickType type = event.getClick();

        if (slot == cfg.getSlotBack()) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() ->
                    player.openInventory(parent.getInventory()), null);
            return;
        }

        if (slot == cfg.getSlotBuyOne() && type == ClickType.LEFT) {
            handleTransaction(() -> shopAPI.buyItem(player, item, 1), player, TransactionType.BUY, 1);
            return;
        }

        if (slot == cfg.getSlotBuyStack() && type == ClickType.SHIFT_LEFT) {
            handleTransaction(() -> shopAPI.buyItem(player, item, 64), player, TransactionType.BUY, 64);
            return;
        }

        if (slot == cfg.getSlotSellOne() && type == ClickType.RIGHT) {
            handleTransaction(() -> shopAPI.sellItem(player, item, 1), player, TransactionType.SELL, 1);
            return;
        }

        if (slot == cfg.getSlotSellAll() && type == ClickType.SHIFT_RIGHT) {
            handleTransaction(() -> shopAPI.sellAll(player, item), player, TransactionType.SELL, -1);
            return;
        }

        // DROP key (Q) = sell one stack (hand amount)
        if (type == ClickType.DROP || type == ClickType.CONTROL_DROP) {
            int handAmount = player.getInventory().getItemInMainHand().getAmount();
            if (handAmount <= 0) handAmount = 64;
            final int stackAmt = handAmount;
            handleTransaction(() -> shopAPI.sellItem(player, item, stackAmt), player, TransactionType.SELL, stackAmt);
        }
    }

    private enum TransactionType { BUY, SELL }

    private void handleTransaction(java.util.function.Supplier<TransactionResult> action,
                                   Player player, TransactionType type, int amount) {
        TransactionResult result = action.get();
        sendResultMessage(player, result, type, amount);
        // Refresh the GUI to reflect updated prices/state
        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            ItemGui refreshed = new ItemGui(configManager, shopAPI, morePaperLib, player, item, parent);
            player.openInventory(refreshed.getInventory());
        }, null);
    }

    private void sendResultMessage(Player player, TransactionResult result, TransactionType type, int amount) {
        String prefix = configManager.getMainConfig().getPrefix();
        String key;

        if (result == TransactionResult.SUCCESS) {
            key = type == TransactionType.BUY ? "buy-success" : "sell-success";
            String msg = configManager.getMainConfig().getMessage(key);
            String amountStr = amount == -1 ? "all" : String.valueOf(amount);
            double price;
            if (type == TransactionType.BUY) {
                price = item.getBuyPrice().orElse(0) * (amount == -1 ? 1 : amount);
            } else {
                price = item.getSellPrice().orElse(0) * (amount == -1 ? 1 : amount);
            }
            msg = msg.replace("<amount>", amountStr)
                    .replace("<item>", item.getDisplayName())
                    .replace("<price>", String.format("%.2f", price));
            player.sendMessage(MM.deserialize(prefix + msg));
            return;
        }

        key = switch (result) {
            case INSUFFICIENT_FUNDS    -> "insufficient-funds";
            case NOT_ENOUGH_ITEMS      -> "not-enough-items";
            case BUY_DISABLED          -> "item-buy-disabled";
            case SELL_DISABLED         -> "item-sell-disabled";
            case GLOBAL_BUY_DISABLED   -> "shop-buy-disabled";
            case GLOBAL_SELL_DISABLED  -> "shop-sell-disabled";
            case ECONOMY_UNAVAILABLE   -> "vault-unavailable";
            default                    -> "vault-unavailable";
        };
        String msg = configManager.getMainConfig().getMessage(key);
        if (result == TransactionResult.INSUFFICIENT_FUNDS) {
            msg = msg.replace("<price>", String.format("%.2f", item.getBuyPrice().orElse(0)));
        }
        player.sendMessage(MM.deserialize(prefix + msg));
    }
}
