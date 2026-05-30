package me.usainsrht.basicshop.gui;

import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.ShopAPIImpl;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.TransactionResult;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.QuickSellConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import space.arim.morepaperlib.MorePaperLib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lists each sellable inventory stack the player is carrying and lets them sell
 * individual stacks or sell everything at once.
 *
 * <p>Each player inventory slot is represented as its own GUI slot so clicking
 * one slot only sells that specific stack, not all stacks of that material.
 */
public final class QuickSellGui extends AbstractShopGui {

    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final MorePaperLib morePaperLib;
    private final Player viewer;

    /** Holds the ShopItem and the exact stack size shown in a GUI slot. */
    private record SlotEntry(ShopItem shopItem, int amount) {}

    /** GUI slot -> SlotEntry for each listed inventory stack. */
    private final Map<Integer, SlotEntry> slotItemMap = new LinkedHashMap<>();

    public QuickSellGui(ConfigManager configManager, ShopAPI shopAPI, MorePaperLib morePaperLib, Player viewer) {
        this.configManager = configManager;
        this.shopAPI       = shopAPI;
        this.morePaperLib  = morePaperLib;
        this.viewer        = viewer;
        build();
    }

    private void build() {
        QuickSellConfig cfg = configManager.getQuickSellConfig();
        Component title = MM.deserialize(cfg.getGuiTitle());
        int rows = Math.max(1, Math.min(6, cfg.getGuiRows()));
        inventory = Bukkit.createInventory(this, rows * 9, title);
        slotItemMap.clear();

        int navRowStart = (rows - 1) * 9;

        if (shopAPI instanceof ShopAPIImpl impl) {
            int guiSlot = 0;
            for (ItemStack stack : viewer.getInventory().getContents()) {
                if (stack == null || stack.getType() == Material.AIR) continue;

                Optional<ShopItem> shopItemOpt = impl.getItemByMaterial(stack.getType());
                if (shopItemOpt.isEmpty() || !shopItemOpt.get().canSell()) continue;

                if (guiSlot >= navRowStart) break;

                ShopItem shopItem = shopItemOpt.get();
                double unitPrice = shopItem.getSellPrice().getAsDouble();
                double total     = unitPrice * stack.getAmount();

                String displayName = cfg.getItemName()
                        .replace("<item>", "<lang:" + shopItem.getMaterial().translationKey() + ">");

                List<String> lore = new ArrayList<>();
                for (String line : cfg.getItemLore()) {
                    lore.add(line
                            .replace("<amount>", String.valueOf(stack.getAmount()))
                            .replace("<price>",  String.format("%.2f", unitPrice))
                            .replace("<total>",  String.format("%.2f", total)));
                }

                inventory.setItem(guiSlot, buildItem(shopItem.getMaterial(), displayName, lore));
                slotItemMap.put(guiSlot, new SlotEntry(shopItem, stack.getAmount()));
                guiSlot++;
            }
        }

        // Empty-state indicator
        if (slotItemMap.isEmpty()) {
            int emptySlot = cfg.getEmptySlot();
            if (emptySlot < navRowStart) {
                inventory.setItem(emptySlot, buildItem(cfg.getEmptyMaterial(), cfg.getEmptyName(), cfg.getEmptyLore()));
            }
        }

        // Close button
        inventory.setItem(cfg.getCloseSlot(), buildItem(cfg.getCloseMaterial(), cfg.getCloseName()));

        // Sell All button
        inventory.setItem(cfg.getSellAllSlot(), buildItem(cfg.getSellAllMaterial(), cfg.getSellAllName(), cfg.getSellAllLore()));

        // Filler for nav row gaps
        if (cfg.isFillerEnabled()) {
            ItemStack filler = buildItem(cfg.getFillerMaterial(), cfg.getFillerName());
            ItemMeta fillerMeta = filler.getItemMeta();
            if (fillerMeta != null) {
                fillerMeta.setHideTooltip(cfg.isFillerHideTooltip());
                filler.setItemMeta(fillerMeta);
            }
            for (int s = navRowStart; s < inventory.getSize(); s++) {
                if (inventory.getItem(s) == null) {
                    inventory.setItem(s, filler);
                }
            }
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        QuickSellConfig cfg = configManager.getQuickSellConfig();

        if (slot == cfg.getCloseSlot()) {
            if (cfg.isCloseReturnsToCategories()) {
                morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                    CategoriesGui categories = new CategoriesGui(configManager, shopAPI, morePaperLib, player);
                    player.openInventory(categories.getInventory());
                }, null);
            } else {
                morePaperLib.scheduling().entitySpecificScheduler(player).run((Runnable) player::closeInventory, null);
            }
            return;
        }

        if (slot == cfg.getSellAllSlot()) {
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
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                QuickSellGui refreshed = new QuickSellGui(configManager, shopAPI, morePaperLib, player);
                player.openInventory(refreshed.getInventory());
            }, null);
            return;
        }

        SlotEntry entry = slotItemMap.get(slot);
        if (entry == null) return;

        // Sell only the amount from this specific stack, not all stacks of this material
        TransactionResult result = shopAPI.sellItem(player, entry.shopItem(), entry.amount());
        sendResultMessage(player, result, entry.shopItem(), entry.amount());

        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            QuickSellGui refreshed = new QuickSellGui(configManager, shopAPI, morePaperLib, player);
            player.openInventory(refreshed.getInventory());
        }, null);
    }

    private void sendResultMessage(Player player, TransactionResult result, ShopItem shopItem, int amount) {
        String prefix = configManager.getMainConfig().getPrefix();
        if (result == TransactionResult.SUCCESS) {
            double price = shopItem.getSellPrice().orElse(0) * amount;
            String msg = configManager.getMainConfig().getMessage("sell-success")
                    .replace("<amount>", String.valueOf(amount))
                    .replace("<item>",   "<lang:" + shopItem.getMaterial().translationKey() + ">")
                    .replace("<price>",  String.format("%.2f", price));
            player.sendMessage(MM.deserialize(prefix + msg));
            return;
        }
        String key = switch (result) {
            case NOT_ENOUGH_ITEMS     -> "not-enough-items";
            case SELL_DISABLED        -> "item-sell-disabled";
            case GLOBAL_SELL_DISABLED -> "shop-sell-disabled";
            case ECONOMY_UNAVAILABLE  -> "vault-unavailable";
            default                   -> "vault-unavailable";
        };
        player.sendMessage(MM.deserialize(prefix + configManager.getMainConfig().getMessage(key)));
    }
}