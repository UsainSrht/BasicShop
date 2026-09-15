package me.usainsrht.basicshop.analytics;

import me.usainsrht.basicshop.analytics.model.TopSellerItem;
import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.TransactionRecord;
import me.usainsrht.basicshop.api.model.TransactionType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.MainConfig;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import space.arim.morepaperlib.MorePaperLib;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Calculates and caches the shop's most popular/profitable items over a rolling window of days.
 * Operates asynchronously and is thread-safe.
 */
public final class TopSellersEngine {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final MorePaperLib morePaperLib;

    private final AtomicReference<List<TopSellerItem>> cachedTopSellers = new AtomicReference<>(Collections.emptyList());
    private ScheduledTask task;

    public TopSellersEngine(Plugin plugin, ConfigManager configManager, ShopAPI shopAPI, MorePaperLib morePaperLib) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopAPI = shopAPI;
        this.morePaperLib = morePaperLib;
    }

    /**
     * Starts the periodic recalculation task.
     */
    public void start() {
        int intervalMinutes = configManager.getMainConfig().getAnalyticsSettings().topSellers().updateIntervalMinutes();
        intervalMinutes = Math.max(1, intervalMinutes);

        // Run initial calculation after 3 seconds, then periodically
        task = morePaperLib.scheduling().asyncScheduler().runAtFixedRate(
                this::recalculateSync,
                Duration.ofSeconds(3),
                Duration.ofMinutes(intervalMinutes)
        );
    }

    /**
     * Stops any scheduled tasks.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Returns the cached top seller items (thread-safe, O(1)).
     */
    public List<TopSellerItem> getTopSellers() {
        return cachedTopSellers.get();
    }

    /**
     * Returns the top item for the specified rank (1-indexed), if present.
     */
    public Optional<TopSellerItem> getByRank(int rank) {
        List<TopSellerItem> list = cachedTopSellers.get();
        if (rank < 1 || rank > list.size()) {
            return Optional.empty();
        }
        return Optional.of(list.get(rank - 1));
    }

    /**
     * Triggers an asynchronous recalculation.
     */
    public CompletableFuture<List<TopSellerItem>> recalculateAsync() {
        CompletableFuture<List<TopSellerItem>> future = new CompletableFuture<>();
        morePaperLib.scheduling().asyncScheduler().run(() -> {
            try {
                List<TopSellerItem> result = recalculateSync();
                future.complete(result);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Synchronously calculates top sellers from CSV files.
     */
    public List<TopSellerItem> recalculateSync() {
        MainConfig.TopSellersSettings settings = configManager.getMainConfig().getAnalyticsSettings().topSellers();
        int days = settings.days();
        int maxAmount = settings.amount();

        List<TransactionRecord> records = readRecordsForDays(days);

        // Aggregate by itemId: only SELL transactions are counted
        Map<String, ItemAggregation> map = new HashMap<>();

        for (TransactionRecord record : records) {
            if (record.getType() != TransactionType.SELL) {
                continue;
            }
            ItemAggregation agg = map.computeIfAbsent(record.getItemId(), k -> new ItemAggregation(record.getItemId(), record.getCategoryId()));
            agg.units += record.getAmount();
            agg.profit += record.getTotalPrice();
            agg.count++;
        }

        List<ItemAggregation> sorted = new ArrayList<>(map.values());
        // Sort descending by profit, then units
        sorted.sort((a, b) -> {
            int cmp = Double.compare(b.profit, a.profit);
            if (cmp != 0) return cmp;
            return Long.compare(b.units, a.units);
        });

        List<TopSellerItem> topList = new ArrayList<>();
        int rank = 1;
        for (ItemAggregation agg : sorted) {
            if (rank > maxAmount) break;

            Material mat = resolveMaterial(agg.itemId);
            topList.add(new TopSellerItem(
                    rank,
                    agg.itemId,
                    mat,
                    agg.categoryId,
                    agg.units,
                    agg.profit,
                    agg.count
            ));
            rank++;
        }

        List<TopSellerItem> unmodifiable = Collections.unmodifiableList(topList);
        cachedTopSellers.set(unmodifiable);
        return unmodifiable;
    }

    /**
     * Reads all transaction records across the last N days (including today).
     */
    public List<TransactionRecord> readRecordsForDays(int days) {
        List<TransactionRecord> all = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            all.addAll(readRecordsForDate(date));
        }
        return all;
    }

    /**
     * Reads transaction records from the CSV file of a specific date.
     */
    public List<TransactionRecord> readRecordsForDate(LocalDate date) {
        String name = "transactions-" + date.format(DATE_FORMAT) + ".csv";
        File file = new File(plugin.getDataFolder(), "analytics/" + name);
        if (!file.exists() || !file.isFile()) {
            return Collections.emptyList();
        }

        List<TransactionRecord> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                parseRecord(line).ifPresent(list::add);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read analytics log file: " + file.getName(), e);
        }
        return list;
    }

    private Optional<TransactionRecord> parseRecord(String csvLine) {
        // Format: timestamp,player_id,player_name,category_id,item_id,type,amount,total_price
        String[] parts = csvLine.split(",", -1);
        if (parts.length < 8) {
            return Optional.empty();
        }
        try {
            Instant timestamp = Instant.parse(parts[0]);
            UUID playerId = UUID.fromString(parts[1]);
            String playerName = parts[2];
            String categoryId = parts[3];
            String itemId = parts[4];
            TransactionType type = TransactionType.valueOf(parts[5].toUpperCase());
            int amount = Integer.parseInt(parts[6]);
            double totalPrice = Double.parseDouble(parts[7]);

            return Optional.of(new TransactionRecord(playerId, playerName, itemId, categoryId, type, amount, totalPrice, timestamp));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Material resolveMaterial(String itemId) {
        if (shopAPI != null) {
            Optional<ShopItem> item = shopAPI.getItem(itemId);
            if (item.isPresent()) {
                return item.get().getMaterial();
            }
        }
        // Fallback: itemId is category:material
        int colon = itemId.indexOf(':');
        String matName = colon != -1 ? itemId.substring(colon + 1) : itemId;
        Material mat = Material.matchMaterial(matName.toUpperCase());
        return mat != null ? mat : Material.CHEST;
    }

    private static class ItemAggregation {
        final String itemId;
        final String categoryId;
        long units;
        double profit;
        int count;

        ItemAggregation(String itemId, String categoryId) {
            this.itemId = itemId;
            this.categoryId = categoryId;
        }
    }
}
