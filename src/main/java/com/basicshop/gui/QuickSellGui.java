package com.basicshop.gui;

import com.basicshop.api.ShopAPI;
import com.basicshop.api.ShopAPIImpl;
import com.basicshop.api.model.ShopCategory;
import com.basicshop.api.model.ShopItem;
import com.basicshop.api.model.TransactionResult;
import com.basicshop.config.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import space.arim.morepaperlib.MorePaperLib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lists all sellable items currently in the player's inventory and lets them sell
 * individual item types or sell everything at once.
 */
public final class QuickSellGui extends AbstractShopGui {

    private static final int SLOT_SELL_ALL = 49;
    private static final int SLOT_BACK     = 45;

    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final MorePaperLib morePaperLib;
    private final Player viewer;

    /** Ordered map: GUI slot → ShopItem. Rebuilt on each open. */
    private final Map<Integer, ShopItem> slotItemMap = new LinkedHashMap<>();

    public QuickSellGui(ConfigManager configManager, ShopAPI shopAPI, MorePaperLib morePaperLib, Player viewer) {
        this.configManager = configManager;
        this.shopAPI       = shopAPI;
        this.morePaperLib  = morePaperLib;
        this.viewer        = viewer;
        build();
    }

    private void build() {
        Component title = MM.deserialize("<gold>Quick Sell — Inventory");
        inventory = Bukkit.createInventory(this, 54, title);
        slotItemMap.clear();

        if (!(shopAPI instanceof ShopAPIImpl impl)) return;

        int slot = 0;
        for (ItemStack stack : viewer.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;

            Optional<ShopItem> shopItemOpt = impl.getItemByMaterial(stack.getType());
            if (shopItemOpt.isEmpty() || !shopItemOpt.get().canSell()) continue;

            ShopItem shopItem = shopItemOpt.get();
            if (slot >= SLOT_BACK) break; // Don't overflow into nav row

            List<String> lore = new ArrayList<>();
            lore.add("<gray>In inventory: <white>" + stack.getAmount());
            lore.add("<gray>Sell price: <gold>" + String.format("%.2f", shopItem.getSellPrice().getAsDouble()) + " <gray>each");
            lore.add("<gray>Total: <gold>" + String.format("%.2f", shopItem.getSellPrice().getAsDouble() * stack.getAmount()));
            lore.add("");
            lore.add("<yellow>Click to sell all of this item.");

            inventory.setItem(slot, buildItem(shopItem.getMaterial(), shopItem.getDisplayName(), lore));
            slotItemMap.put(slot, shopItem);
            slot++;
        }

        if (slotItemMap.isEmpty()) {
            inventory.setItem(22, buildItem(Material.BARRIER, "<red>No sellable items found.",
                    List.of("<gray>You have no items in your inventory",
                            "<gray>that the shop will buy.")));
        }

        // Sell All button
        inventory.setItem(SLOT_SELL_ALL, buildItem(Material.EMERALD, "<green><bold>Sell All</bold></green>",
                List.of("<gray>Sells every sellable item", "<gray>in your inventory at once.",
                        "", "<yellow>Click to sell all.")));

        // Back button
        inventory.setItem(SLOT_BACK, buildItem(Material.BARRIER, "<gray>✗ Close"));

        // Filler nav row
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = SLOT_BACK; s < 54; s++) {
            if (inventory.getItem(s) == null) {
                inventory.setItem(s, filler);
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run((Runnable) player::closeInventory, null);
            return;
        }

        if (slot == SLOT_SELL_ALL) {
            ShopAPI.QuickSellResult result = shopAPI.quickSellInventory(player);
            if (result.anySuccess()) {
                String msg = configManager.getMainConfig().getPrefix()
                        + configManager.getMainConfig().getMessage("quicksell-inventory-success")
                        .replace("<price>", String.format("%.2f", result.totalEarned()));
                player.sendMessage(MM.deserialize(msg));
            } else {
                String msg = configManager.getMainConfig().getPrefix()
                        + configManager.getMainConfig().getMessage("no-sellable-items");
                player.sendMessage(MM.deserialize(msg));
            }
            // Refresh GUI
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                QuickSellGui refreshed = new QuickSellGui(configManager, shopAPI, morePaperLib, player);
                player.openInventory(refreshed.getInventory());
            }, null);
            return;
        }

        ShopItem shopItem = slotItemMap.get(slot);
        if (shopItem == null) return;

        TransactionResult result = shopAPI.sellAll(player, shopItem);
        sendResultMessage(player, result, shopItem);

        // Refresh
        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            QuickSellGui refreshed = new QuickSellGui(configManager, shopAPI, morePaperLib, player);
            player.openInventory(refreshed.getInventory());
        }, null);
    }

    private void sendResultMessage(Player player, TransactionResult result, ShopItem shopItem) {
        String prefix = configManager.getMainConfig().getPrefix();
        if (result == TransactionResult.SUCCESS) {
            String msg = configManager.getMainConfig().getMessage("sell-success")
                    .replace("<amount>", "all")
                    .replace("<item>", shopItem.getDisplayName())
                    .replace("<price>", "?");
            player.sendMessage(MM.deserialize(prefix + msg));
            return;
        }
        String key = switch (result) {
            case NOT_ENOUGH_ITEMS    -> "not-enough-items";
            case SELL_DISABLED       -> "item-sell-disabled";
            case GLOBAL_SELL_DISABLED -> "shop-sell-disabled";
            case ECONOMY_UNAVAILABLE -> "vault-unavailable";
            default                  -> "vault-unavailable";
        };
        player.sendMessage(MM.deserialize(prefix + configManager.getMainConfig().getMessage(key)));
    }
}
