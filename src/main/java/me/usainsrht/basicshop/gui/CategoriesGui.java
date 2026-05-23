package me.usainsrht.basicshop.gui;

import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.ShopAPIImpl;
import me.usainsrht.basicshop.api.model.ShopCategory;
import me.usainsrht.basicshop.config.CategoriesConfig;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.api.model.TransactionResult;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import space.arim.morepaperlib.MorePaperLib;

/**
 * The main categories GUI — lists all shop categories and contains the QuickSell slot.
 */
public final class CategoriesGui extends AbstractShopGui {

    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final MorePaperLib morePaperLib;
    private final Player viewer;

    public CategoriesGui(ConfigManager configManager, ShopAPI shopAPI, MorePaperLib morePaperLib, Player viewer) {
        this.configManager = configManager;
        this.shopAPI       = shopAPI;
        this.morePaperLib  = morePaperLib;
        this.viewer        = viewer;
        build();
    }

    private void build() {
        CategoriesConfig cfg = configManager.getCategoriesConfig();

        Component title = MM.deserialize(cfg.getGuiTitle());
        int rows = Math.max(1, Math.min(6, cfg.getGuiRows()));
        inventory = Bukkit.createInventory(this, rows * 9, title);

        // Place category icons
        for (CategoriesConfig.CategoryEntry entry : cfg.getCategories()) {
            if (entry.slot() < 0 || entry.slot() >= inventory.getSize()) continue;
            ItemStack icon = buildItem(entry.material(), entry.displayName(), entry.lore());
            inventory.setItem(entry.slot(), icon);
        }

        // Place QuickSell button
        int qsSlot = cfg.getQuicksellSlot();
        if (qsSlot >= 0 && qsSlot < inventory.getSize()) {
            ItemStack qsIcon = buildItem(
                    cfg.getQuicksellMaterial(),
                    cfg.getQuicksellName(),
                    cfg.getQuicksellLore()
            );
            inventory.setItem(qsSlot, qsIcon);
        }

        // Fill empty slots with filler
        if (cfg.isFillerEnabled()) {
            fillEmpty(cfg.getFillerMaterial(), cfg.getFillerName(), cfg.isFillerHideTooltip());
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        CategoriesConfig cfg = configManager.getCategoriesConfig();

        // QuickSell slot
        if (slot == cfg.getQuicksellSlot()) {
            handleQuickSellSlot(player, event);
            return;
        }

        // Category slot
        for (CategoriesConfig.CategoryEntry entry : cfg.getCategories()) {
            if (entry.slot() == slot) {
                shopAPI.getCategory(entry.id()).ifPresent(category ->
                        morePaperLib.scheduling().entitySpecificScheduler(player).run(
                                () -> openCategory(player, category), null));
                return;
            }
        }
    }

    private void handleQuickSellSlot(Player player, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        boolean hasCursorItem = cursor != null
                && cursor.getType() != org.bukkit.Material.AIR
                && cursor.getAmount() > 0;

        if (hasCursorItem) {
            // Sell the cursor item immediately
            ShopAPI.QuickSellResult result = new ShopAPI.QuickSellResult(false, 0, 0);
            if (shopAPI instanceof ShopAPIImpl impl) {
                var shopItemOpt = impl.getItemByMaterial(cursor.getType());
                if (shopItemOpt.isPresent()) {
                    var txResult = shopAPI.sellItem(player, shopItemOpt.get(), cursor.getAmount());
                    if (txResult == TransactionResult.SUCCESS) {
                        event.setCursor(new ItemStack(org.bukkit.Material.AIR));
                        sendMessage(player, "sell-success",
                                cursor.getAmount(), "<lang:" + shopItemOpt.get().getMaterial().translationKey() + ">",
                                shopItemOpt.get().getSellPrice().orElse(0) * cursor.getAmount());
                    } else {
                        sendTransactionMessage(player, txResult);
                    }
                } else {
                    sendMessage(player, "item-sell-disabled");
                }
            }
        } else {
            // Open QuickSell inventory browser
            morePaperLib.scheduling().entitySpecificScheduler(player).run(
                    () -> {
                        QuickSellGui qs = new QuickSellGui(configManager, shopAPI, morePaperLib, player);
                        player.openInventory(qs.getInventory());
                    }, null);
        }
    }

    private void openCategory(Player player, ShopCategory category) {
        CategoryGui categoryGui = new CategoryGui(configManager, shopAPI, morePaperLib, player, category, 0);
        player.openInventory(categoryGui.getInventory());
    }

    private void sendMessage(Player player, String key, Object... args) {
        String msg = configManager.getMainConfig().getPrefix()
                + configManager.getMainConfig().getMessage(key);
        if (args.length >= 3) {
            msg = msg.replace("<amount>", String.valueOf(args[0]))
                    .replace("<item>", String.valueOf(args[1]))
                    .replace("<price>", String.format("%.2f", args[2]));
        }
        player.sendMessage(MM.deserialize(msg));
    }

    private void sendMessage(Player player, String key) {
        String msg = configManager.getMainConfig().getPrefix()
                + configManager.getMainConfig().getMessage(key);
        player.sendMessage(MM.deserialize(msg));
    }

    private void sendTransactionMessage(Player player, TransactionResult result) {
        String key = switch (result) {
            case INSUFFICIENT_FUNDS    -> "insufficient-funds";
            case NOT_ENOUGH_ITEMS      -> "not-enough-items";
            case BUY_DISABLED          -> "item-buy-disabled";
            case SELL_DISABLED         -> "item-sell-disabled";
            case GLOBAL_BUY_DISABLED   -> "shop-buy-disabled";
            case GLOBAL_SELL_DISABLED  -> "shop-sell-disabled";
            case ECONOMY_UNAVAILABLE   -> "vault-unavailable";
            default                    -> "vault-unavailable";
        };
        sendMessage(player, key);
    }
}
