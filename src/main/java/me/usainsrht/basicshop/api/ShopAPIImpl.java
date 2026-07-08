package me.usainsrht.basicshop.api;

import me.usainsrht.basicshop.analytics.AnalyticsManager;
import me.usainsrht.basicshop.analytics.TransactionLogger;
import me.usainsrht.basicshop.api.economy.EconomyProvider;
import me.usainsrht.basicshop.api.model.ShopCategory;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.TransactionRecord;
import me.usainsrht.basicshop.api.model.TransactionResult;
import me.usainsrht.basicshop.api.model.TransactionType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.util.ShopSounds;
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
        if (economy.getBalance(player) < totalCost) return TransactionResult.INSUFFICIENT_FUNDS;

        economy.withdraw(player, totalCost);
        player.getInventory().addItem(new ItemStack(item.getMaterial(), amount));

        record(player, item, TransactionType.BUY, amount, totalCost);
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

        removeFromInventory(player, item.getMaterial(), actualAmount);
        double totalEarned = priceOpt.getAsDouble() * actualAmount;
        economy.deposit(player, totalEarned);

        record(player, item, TransactionType.SELL, actualAmount, totalEarned);
        playSellSound(player);
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

        removeFromInventory(player, item.getMaterial(), available);
        double totalEarned = priceOpt.getAsDouble() * available;
        economy.deposit(player, totalEarned);

        record(player, item, TransactionType.SELL, available, totalEarned);
        playSellSound(player);
        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult quickSellHand(Player player) {
        if (!economy.isAvailable())                return TransactionResult.ECONOMY_UNAVAILABLE;
        if (!configManager.getMainConfig().isSellingEnabled()) return TransactionResult.GLOBAL_SELL_DISABLED;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() == 0) {
            return TransactionResult.NOT_ENOUGH_ITEMS;
        }

        Optional<ShopItem> shopItemOpt = getItemByMaterial(hand.getType());
        if (shopItemOpt.isEmpty())                 return TransactionResult.SELL_DISABLED;

        ShopItem shopItem = shopItemOpt.get();
        OptionalDouble priceOpt = shopItem.getSellPrice();
        if (priceOpt.isEmpty())                    return TransactionResult.SELL_DISABLED;

        int amount = hand.getAmount();
        double totalEarned = priceOpt.getAsDouble() * amount;

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        economy.deposit(player, totalEarned);

        record(player, shopItem, TransactionType.SELL, amount, totalEarned);
        playSellSound(player);
        return TransactionResult.SUCCESS;
    }

    @Override
    public QuickSellResult quickSellInventory(Player player) {
        if (!economy.isAvailable())                return QuickSellResult.NOTHING;
        if (!configManager.getMainConfig().isSellingEnabled()) return QuickSellResult.NOTHING;

        int totalAmount = 0;
        double totalEarned = 0;

        for (ShopCategory category : getCategories()) {
            for (ShopItem shopItem : category.getItems()) {
                OptionalDouble priceOpt = shopItem.getSellPrice();
                if (priceOpt.isEmpty()) continue;

                int available = countInInventory(player, shopItem.getMaterial());
                if (available <= 0) continue;

                removeFromInventory(player, shopItem.getMaterial(), available);
                double earned = priceOpt.getAsDouble() * available;
                economy.deposit(player, earned);

                record(player, shopItem, TransactionType.SELL, available, earned);
                totalAmount += available;
                totalEarned += earned;
            }
        }

        if (totalAmount > 0) {
            playSellSound(player);
            return new QuickSellResult(true, totalAmount, totalEarned, List.of());
        }
        return QuickSellResult.NOTHING;
    }

    @Override
    public QuickSellResult sellFromInventory(Player player, Inventory inventory) {
        if (!economy.isAvailable())                return QuickSellResult.NOTHING;
        if (!configManager.getMainConfig().isSellingEnabled()) return QuickSellResult.NOTHING;

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
            inventory.setItem(i, null);

            double earned = priceOpt.getAsDouble() * amount;
            economy.deposit(player, earned);
            record(player, shopItem, TransactionType.SELL, amount, earned);

            Material material = stack.getType();
            long[] line = totals.computeIfAbsent(material, m -> new long[2]);
            line[0] += amount;
            line[1] += Math.round(earned * 100);

            totalAmount += amount;
            totalEarned += earned;
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

        return new QuickSellResult(true, totalAmount, totalEarned, List.copyOf(lines));
    }

    @Override
    public QuickSellResult sellItemStacks(Player player, Collection<ItemStack> stacks) {
        if (!economy.isAvailable())                return QuickSellResult.NOTHING;
        if (!configManager.getMainConfig().isSellingEnabled()) return QuickSellResult.NOTHING;
        if (stacks == null || stacks.isEmpty())    return QuickSellResult.NOTHING;

        int totalAmount = 0;
        double totalEarned = 0;

        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir()) continue;

            Optional<ShopItem> shopItemOpt = getItemByMaterial(stack.getType());
            if (shopItemOpt.isEmpty()) continue;

            ShopItem shopItem = shopItemOpt.get();
            OptionalDouble priceOpt = shopItem.getSellPrice();
            if (priceOpt.isEmpty()) continue;

            int amount = stack.getAmount();
            double earned = priceOpt.getAsDouble() * amount;
            economy.deposit(player, earned);
            record(player, shopItem, TransactionType.SELL, amount, earned);

            totalAmount += amount;
            totalEarned += earned;
        }

        if (totalAmount > 0) {
            playSellSound(player);
            return new QuickSellResult(true, totalAmount, totalEarned, List.of());
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

    private void record(Player player, ShopItem item, TransactionType type, int amount, double totalPrice) {
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

        analyticsManager.record(record);
        transactionLogger.log(record);
    }

    private void playSellSound(Player player) {
        ShopSounds.play(player, configManager.getMainConfig().getSellSound());
    }
}
