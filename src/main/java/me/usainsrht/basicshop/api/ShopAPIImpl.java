package me.usainsrht.basicshop.api;

import me.usainsrht.basicshop.analytics.AnalyticsManager;
import me.usainsrht.basicshop.analytics.TransactionLogger;
import me.usainsrht.basicshop.api.economy.EconomyProvider;
import me.usainsrht.basicshop.api.event.BulkSellSource;
import me.usainsrht.basicshop.api.event.ShopBulkSellEvent;
import me.usainsrht.basicshop.api.event.ShopPostTransactionEvent;
import me.usainsrht.basicshop.api.event.ShopPreBulkSellEvent;
import me.usainsrht.basicshop.api.event.ShopPreTransactionEvent;
import me.usainsrht.basicshop.api.model.ShopCategory;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.TransactionRecord;
import me.usainsrht.basicshop.api.model.TransactionResult;
import me.usainsrht.basicshop.api.model.TransactionType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.util.ShopSounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Default implementation of {@link ShopAPI}.
 * All public methods are thread-safe with respect to the analytics layer,
 * but inventory mutations must occur on the server/region thread.
 */
public final class ShopAPIImpl implements ShopAPI {

    private final ConfigManager configManager;
    private final EconomyProvider economy;
    private final AnalyticsManager analyticsManager;
    private final TransactionLogger transactionLogger;

    public ShopAPIImpl(
            ConfigManager configManager,
            EconomyProvider economy,
            AnalyticsManager analyticsManager,
            TransactionLogger transactionLogger
    ) {
        this.configManager    = configManager;
        this.economy          = economy;
        this.analyticsManager = analyticsManager;
        this.transactionLogger = transactionLogger;
    }

    // -------------------------------------------------------------------------
    // Transactions
    // -------------------------------------------------------------------------

    @Override
    public TransactionResult buyItem(Player player, ShopItem item, int amount) {
        if (!economy.isAvailable())               return TransactionResult.ECONOMY_UNAVAILABLE;
        if (!configManager.getMainConfig().isBuyingEnabled()) return TransactionResult.GLOBAL_BUY_DISABLED;

        OptionalDouble priceOpt = item.getBuyPrice();
        if (priceOpt.isEmpty())                   return TransactionResult.BUY_DISABLED;

        double totalCost = priceOpt.getAsDouble() * amount;
        ShopCategory category = getCategoryForItem(item).orElse(null);

        ShopPreTransactionEvent preEvent = new ShopPreTransactionEvent(
                player, item, category, TransactionType.BUY, amount, totalCost
        );
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            return TransactionResult.CANCELLED;
        }

        int finalAmount = preEvent.getAmount();
        double finalCost = preEvent.getPrice();
        if (finalAmount <= 0) {
            return TransactionResult.CANCELLED;
        }

        if (economy.getBalance(player) < finalCost) return TransactionResult.INSUFFICIENT_FUNDS;

        economy.withdraw(player, finalCost);
        player.getInventory().addItem(new ItemStack(item.getMaterial(), finalAmount));

        TransactionRecord record = record(player, item, TransactionType.BUY, finalAmount, finalCost);
        ShopPostTransactionEvent postEvent = new ShopPostTransactionEvent(
                player, item, category, TransactionType.BUY, finalAmount, finalCost, record
        );
        Bukkit.getPluginManager().callEvent(postEvent);

        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult sellItem(Player player, ShopItem item, int amount) {
        if (!economy.isAvailable())                return TransactionResult.ECONOMY_UNAVAILABLE;
        if (!configManager.getMainConfig().isSellingEnabled()) return TransactionResult.GLOBAL_SELL_DISABLED;

        OptionalDouble priceOpt = item.getSellPrice();
        if (priceOpt.isEmpty())                    return TransactionResult.SELL_DISABLED;

        int available = countInInventory(player, item.getMaterial());
        if (available <= 0)                        return TransactionResult.NOT_ENOUGH_ITEMS;

        int actualAmount = Math.min(amount, available);
        ShopCategory category = getCategoryForItem(item).orElse(null);
        double totalEarned = priceOpt.getAsDouble() * actualAmount;

        ShopPreTransactionEvent preEvent = new ShopPreTransactionEvent(
                player, item, category, TransactionType.SELL, actualAmount, totalEarned
        );
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            return TransactionResult.CANCELLED;
        }

        int finalAmount = Math.min(preEvent.getAmount(), available);
        double finalEarned = preEvent.getPrice();
        if (finalAmount <= 0) {
            return TransactionResult.CANCELLED;
        }

        removeFromInventory(player, item.getMaterial(), finalAmount);
        economy.deposit(player, finalEarned);

        TransactionRecord record = record(player, item, TransactionType.SELL, finalAmount, finalEarned);
        playSellSound(player);

        ShopPostTransactionEvent postEvent = new ShopPostTransactionEvent(
                player, item, category, TransactionType.SELL, finalAmount, finalEarned, record
        );
        Bukkit.getPluginManager().callEvent(postEvent);

        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult sellAll(Player player, ShopItem item) {
        if (!economy.isAvailable())                return TransactionResult.ECONOMY_UNAVAILABLE;
        if (!configManager.getMainConfig().isSellingEnabled()) return TransactionResult.GLOBAL_SELL_DISABLED;

        OptionalDouble priceOpt = item.getSellPrice();
        if (priceOpt.isEmpty())                    return TransactionResult.SELL_DISABLED;

        int available = countInInventory(player, item.getMaterial());
        if (available <= 0)                        return TransactionResult.NOT_ENOUGH_ITEMS;

        return sellItem(player, item, available);
    }

    @Override
    public TransactionResult quickSellHand(Player player) {
        return sellHeldItemStack(player,
                player.getInventory().getItemInMainHand(),
                newStack -> player.getInventory().setItemInMainHand(newStack));
    }

    @Override
    public TransactionResult quickSellCursor(Player player) {
        return sellHeldItemStack(player,
                player.getItemOnCursor(),
                player::setItemOnCursor);
    }

    private TransactionResult sellHeldItemStack(Player player, ItemStack stack, Consumer<ItemStack> stackUpdater) {
        if (!economy.isAvailable())                return TransactionResult.ECONOMY_UNAVAILABLE;
        if (!configManager.getMainConfig().isSellingEnabled()) return TransactionResult.GLOBAL_SELL_DISABLED;

        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return TransactionResult.NOT_ENOUGH_ITEMS;
        }

        Optional<ShopItem> shopItemOpt = getItemByMaterial(stack.getType());
        if (shopItemOpt.isEmpty())                 return TransactionResult.SELL_DISABLED;

        ShopItem shopItem = shopItemOpt.get();
        OptionalDouble priceOpt = shopItem.getSellPrice();
        if (priceOpt.isEmpty())                    return TransactionResult.SELL_DISABLED;

        int amount = stack.getAmount();
        double totalEarned = priceOpt.getAsDouble() * amount;
        ShopCategory category = getCategoryForItem(shopItem).orElse(null);

        ShopPreTransactionEvent preEvent = new ShopPreTransactionEvent(
                player, shopItem, category, TransactionType.SELL, amount, totalEarned
        );
        Bukkit.getPluginManager().callEvent(preEvent);
        if (preEvent.isCancelled()) {
            return TransactionResult.CANCELLED;
        }

        int finalAmount = Math.min(preEvent.getAmount(), amount);
        double finalEarned = preEvent.getPrice();
        if (finalAmount <= 0) {
            return TransactionResult.CANCELLED;
        }

        if (finalAmount >= stack.getAmount()) {
            stackUpdater.accept(null);
        } else {
            stack.setAmount(stack.getAmount() - finalAmount);
            stackUpdater.accept(stack);
        }

        economy.deposit(player, finalEarned);

        TransactionRecord record = record(player, shopItem, TransactionType.SELL, finalAmount, finalEarned);
        playSellSound(player);

        ShopPostTransactionEvent postEvent = new ShopPostTransactionEvent(
                player, shopItem, category, TransactionType.SELL, finalAmount, finalEarned, record
        );
        Bukkit.getPluginManager().callEvent(postEvent);

        return TransactionResult.SUCCESS;
    }

    @Override
    public QuickSellResult quickSellInventory(Player player) {
        if (!economy.isAvailable())                return QuickSellResult.NOTHING;
        if (!configManager.getMainConfig().isSellingEnabled()) return QuickSellResult.NOTHING;

        ShopPreBulkSellEvent preBulk = new ShopPreBulkSellEvent(player, BulkSellSource.QUICK_SELL_INVENTORY, null);
        Bukkit.getPluginManager().callEvent(preBulk);
        if (preBulk.isCancelled()) return QuickSellResult.NOTHING;

        int totalAmount = 0;
        double totalEarned = 0;
        Map<Material, long[]> totals = new LinkedHashMap<>();

        for (ShopCategory category : getCategories()) {
            for (ShopItem shopItem : category.getItems()) {
                OptionalDouble priceOpt = shopItem.getSellPrice();
                if (priceOpt.isEmpty()) continue;

                int available = countInInventory(player, shopItem.getMaterial());
                if (available <= 0) continue;

                double plannedEarned = priceOpt.getAsDouble() * available;
                ShopPreTransactionEvent preEvent = new ShopPreTransactionEvent(
                        player, shopItem, category, TransactionType.SELL, available, plannedEarned
                );
                Bukkit.getPluginManager().callEvent(preEvent);
                if (preEvent.isCancelled()) continue;

                int finalAmount = Math.min(preEvent.getAmount(), available);
                double finalEarned = preEvent.getPrice();
                if (finalAmount <= 0) continue;

                removeFromInventory(player, shopItem.getMaterial(), finalAmount);
                economy.deposit(player, finalEarned);

                TransactionRecord record = record(player, shopItem, TransactionType.SELL, finalAmount, finalEarned);
                ShopPostTransactionEvent postEvent = new ShopPostTransactionEvent(
                        player, shopItem, category, TransactionType.SELL, finalAmount, finalEarned, record
                );
                Bukkit.getPluginManager().callEvent(postEvent);

                totalAmount += finalAmount;
                totalEarned += finalEarned;

                long[] line = totals.computeIfAbsent(shopItem.getMaterial(), m -> new long[2]);
                line[0] += finalAmount;
                line[1] += Math.round(finalEarned * 100);
            }
        }

        if (totalAmount > 0) {
            playSellSound(player);
            List<SoldMaterialLine> lines = new ArrayList<>();
            for (Map.Entry<Material, long[]> entry : totals.entrySet()) {
                lines.add(new SoldMaterialLine(
                        entry.getKey(),
                        (int) entry.getValue()[0],
                        entry.getValue()[1] / 100.0
                ));
            }
            ShopBulkSellEvent bulkEvent = new ShopBulkSellEvent(
                    player, BulkSellSource.QUICK_SELL_INVENTORY, totalAmount, totalEarned, lines
            );
            Bukkit.getPluginManager().callEvent(bulkEvent);
            return new QuickSellResult(true, totalAmount, totalEarned, List.copyOf(lines));
        }
        return QuickSellResult.NOTHING;
    }

    @Override
    public QuickSellResult sellFromInventory(Player player, Inventory inventory) {
        if (!economy.isAvailable())                return QuickSellResult.NOTHING;
        if (!configManager.getMainConfig().isSellingEnabled()) return QuickSellResult.NOTHING;

        ShopPreBulkSellEvent preBulk = new ShopPreBulkSellEvent(player, BulkSellSource.CONTAINER_STAFF, inventory);
        Bukkit.getPluginManager().callEvent(preBulk);
        if (preBulk.isCancelled()) return QuickSellResult.NOTHING;

        Map<Material, long[]> totals = new LinkedHashMap<>();
        int totalAmount = 0;
        double totalEarned = 0;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;

            Optional<ShopItem> shopItemOpt = getItemByMaterial(stack.getType());
            if (shopItemOpt.isEmpty()) continue;

            ShopItem shopItem = shopItemOpt.get();
            OptionalDouble priceOpt = shopItem.getSellPrice();
            if (priceOpt.isEmpty()) continue;

            int amount = stack.getAmount();
            double plannedEarned = priceOpt.getAsDouble() * amount;
            ShopCategory category = getCategoryForItem(shopItem).orElse(null);

            ShopPreTransactionEvent preEvent = new ShopPreTransactionEvent(
                    player, shopItem, category, TransactionType.SELL, amount, plannedEarned
            );
            Bukkit.getPluginManager().callEvent(preEvent);
            if (preEvent.isCancelled()) continue;

            int finalAmount = Math.min(preEvent.getAmount(), amount);
            double finalEarned = preEvent.getPrice();
            if (finalAmount <= 0) continue;

            if (finalAmount >= stack.getAmount()) {
                inventory.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - finalAmount);
                inventory.setItem(i, stack);
            }

            economy.deposit(player, finalEarned);
            TransactionRecord record = record(player, shopItem, TransactionType.SELL, finalAmount, finalEarned);
            ShopPostTransactionEvent postEvent = new ShopPostTransactionEvent(
                    player, shopItem, category, TransactionType.SELL, finalAmount, finalEarned, record
            );
            Bukkit.getPluginManager().callEvent(postEvent);

            Material material = shopItem.getMaterial();
            long[] line = totals.computeIfAbsent(material, m -> new long[2]);
            line[0] += finalAmount;
            line[1] += Math.round(finalEarned * 100);

            totalAmount += finalAmount;
            totalEarned += finalEarned;
        }

        if (totalAmount <= 0) return QuickSellResult.NOTHING;

        playSellSound(player);

        List<SoldMaterialLine> lines = new ArrayList<>();
        for (Map.Entry<Material, long[]> entry : totals.entrySet()) {
            lines.add(new SoldMaterialLine(
                    entry.getKey(),
                    (int) entry.getValue()[0],
                    entry.getValue()[1] / 100.0
            ));
        }

        ShopBulkSellEvent bulkEvent = new ShopBulkSellEvent(
                player, BulkSellSource.CONTAINER_STAFF, totalAmount, totalEarned, lines
        );
        Bukkit.getPluginManager().callEvent(bulkEvent);

        return new QuickSellResult(true, totalAmount, totalEarned, List.copyOf(lines));
    }

    @Override
    public QuickSellResult sellItemStacks(Player player, Collection<ItemStack> stacks) {
        if (!economy.isAvailable())                return QuickSellResult.NOTHING;
        if (!configManager.getMainConfig().isSellingEnabled()) return QuickSellResult.NOTHING;
        if (stacks == null || stacks.isEmpty())    return QuickSellResult.NOTHING;

        ShopPreBulkSellEvent preBulk = new ShopPreBulkSellEvent(player, BulkSellSource.ITEM_STACKS, null);
        Bukkit.getPluginManager().callEvent(preBulk);
        if (preBulk.isCancelled()) return QuickSellResult.NOTHING;

        int totalAmount = 0;
        double totalEarned = 0;
        Map<Material, long[]> totals = new LinkedHashMap<>();

        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir()) continue;

            Optional<ShopItem> shopItemOpt = getItemByMaterial(stack.getType());
            if (shopItemOpt.isEmpty()) continue;

            ShopItem shopItem = shopItemOpt.get();
            OptionalDouble priceOpt = shopItem.getSellPrice();
            if (priceOpt.isEmpty()) continue;

            int amount = stack.getAmount();
            double plannedEarned = priceOpt.getAsDouble() * amount;
            ShopCategory category = getCategoryForItem(shopItem).orElse(null);

            ShopPreTransactionEvent preEvent = new ShopPreTransactionEvent(
                    player, shopItem, category, TransactionType.SELL, amount, plannedEarned
            );
            Bukkit.getPluginManager().callEvent(preEvent);
            if (preEvent.isCancelled()) continue;

            int finalAmount = Math.min(preEvent.getAmount(), amount);
            double finalEarned = preEvent.getPrice();
            if (finalAmount <= 0) continue;

            economy.deposit(player, finalEarned);
            TransactionRecord record = record(player, shopItem, TransactionType.SELL, finalAmount, finalEarned);
            ShopPostTransactionEvent postEvent = new ShopPostTransactionEvent(
                    player, shopItem, category, TransactionType.SELL, finalAmount, finalEarned, record
            );
            Bukkit.getPluginManager().callEvent(postEvent);

            totalAmount += finalAmount;
            totalEarned += finalEarned;

            Material material = shopItem.getMaterial();
            long[] line = totals.computeIfAbsent(material, m -> new long[2]);
            line[0] += finalAmount;
            line[1] += Math.round(finalEarned * 100);
        }

        if (totalAmount > 0) {
            playSellSound(player);
            List<SoldMaterialLine> lines = new ArrayList<>();
            for (Map.Entry<Material, long[]> entry : totals.entrySet()) {
                lines.add(new SoldMaterialLine(
                        entry.getKey(),
                        (int) entry.getValue()[0],
                        entry.getValue()[1] / 100.0
                ));
            }
            ShopBulkSellEvent bulkEvent = new ShopBulkSellEvent(
                    player, BulkSellSource.ITEM_STACKS, totalAmount, totalEarned, lines
            );
            Bukkit.getPluginManager().callEvent(bulkEvent);
            return new QuickSellResult(true, totalAmount, totalEarned, List.copyOf(lines));
        }
        return QuickSellResult.NOTHING;
    }

    // -------------------------------------------------------------------------
    // Catalogue
    // -------------------------------------------------------------------------

    @Override
    public List<ShopCategory> getCategories() {
        return configManager.getCategories();
    }

    @Override
    public Optional<ShopCategory> getCategory(String id) {
        return getCategories().stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<ShopItem> getItem(String id) {
        return getCategories().stream()
                .flatMap(c -> c.getItems().stream())
                .filter(i -> i.getId().equals(id))
                .findFirst();
    }

    @Override
    public Optional<ShopCategory> getCategoryForItem(ShopItem item) {
        return getCategories().stream()
                .filter(c -> c.getItems().contains(item))
                .findFirst();
    }

    /** Finds a ShopItem by its Bukkit Material across all categories. */
    @Override
    public Optional<ShopItem> getItemByMaterial(Material material) {
        return getCategories().stream()
                .flatMap(c -> c.getItems().stream())
                .filter(i -> i.getMaterial() == material)
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // Global toggles
    // -------------------------------------------------------------------------

    @Override
    public boolean isBuyingEnabled() {
        return configManager.getMainConfig().isBuyingEnabled();
    }

    @Override
    public boolean isSellingEnabled() {
        return configManager.getMainConfig().isSellingEnabled();
    }

    // -------------------------------------------------------------------------
    // Analytics
    // -------------------------------------------------------------------------

    @Override
    public List<TransactionRecord> getPlayerHistory(UUID playerId) {
        return analyticsManager.getPlayerHistory(playerId);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static int countInInventory(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private static void removeFromInventory(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;

            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
    }

    private TransactionRecord record(Player player, ShopItem item, TransactionType type, int amount, double totalPrice) {
        Optional<ShopCategory> catOpt = getCategoryForItem(item);
        String categoryId = catOpt.map(ShopCategory::getId).orElse("unknown");

        TransactionRecord record = new TransactionRecord(
                player.getUniqueId(),
                player.getName(),
                item.getId(),
                categoryId,
                type,
                amount,
                totalPrice,
                Instant.now()
        );

        if (analyticsManager != null) {
            analyticsManager.record(record);
        }
        if (transactionLogger != null) {
            transactionLogger.log(record);
        }
        return record;
    }

    private void playSellSound(Player player) {
        ShopSounds.play(player, configManager.getMessagesConfig(), "sell-sound");
    }
}
