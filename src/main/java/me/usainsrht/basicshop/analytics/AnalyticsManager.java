package me.usainsrht.basicshop.analytics;

import me.usainsrht.basicshop.api.model.TransactionRecord;
import me.usainsrht.basicshop.api.model.TransactionType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe in-memory analytics store.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Per-item buy and sell volumes (unit counts)</li>
 *   <li>Per-category buy and sell volumes</li>
 *   <li>Per-player transaction history (capped at {@value #MAX_HISTORY_PER_PLAYER})</li>
 * </ul>
 */
public final class AnalyticsManager {

    private static final int MAX_HISTORY_PER_PLAYER = 500;

    // itemId -> [bought, sold]
    private final Map<String, LongAdder[]> itemVolumes = new ConcurrentHashMap<>();

    // categoryId -> [bought, sold]
    private final Map<String, LongAdder[]> categoryVolumes = new ConcurrentHashMap<>();

    // playerId -> recent history (most-recent first, bounded)
    private final Map<UUID, Deque<TransactionRecord>> playerHistories = new ConcurrentHashMap<>();

    /**
     * Records a completed transaction.
     * This method is thread-safe and non-blocking.
     */
    public void record(TransactionRecord record) {
        int idx = record.getType() == TransactionType.BUY ? 0 : 1;

        // Item volumes
        LongAdder[] iVol = itemVolumes.computeIfAbsent(record.getItemId(), k -> new LongAdder[]{new LongAdder(), new LongAdder()});
        iVol[idx].add(record.getAmount());

        // Category volumes
        LongAdder[] cVol = categoryVolumes.computeIfAbsent(record.getCategoryId(), k -> new LongAdder[]{new LongAdder(), new LongAdder()});
        cVol[idx].add(record.getAmount());

        // Player history
        Deque<TransactionRecord> history = playerHistories.computeIfAbsent(record.getPlayerId(), k -> new ArrayDeque<>());
        synchronized (history) {
            history.addFirst(record);
            while (history.size() > MAX_HISTORY_PER_PLAYER) {
                history.removeLast();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Read methods
    // -------------------------------------------------------------------------

    /** Total units bought for the given item ID. */
    public long getItemBought(String itemId) {
        LongAdder[] v = itemVolumes.get(itemId);
        return v == null ? 0 : v[0].sum();
    }

    /** Total units sold for the given item ID. */
    public long getItemSold(String itemId) {
        LongAdder[] v = itemVolumes.get(itemId);
        return v == null ? 0 : v[1].sum();
    }

    /** Total units bought in the given category. */
    public long getCategoryBought(String categoryId) {
        LongAdder[] v = categoryVolumes.get(categoryId);
        return v == null ? 0 : v[0].sum();
    }

    /** Total units sold in the given category. */
    public long getCategorySold(String categoryId) {
        LongAdder[] v = categoryVolumes.get(categoryId);
        return v == null ? 0 : v[1].sum();
    }

    /** Returns all tracked item IDs. */
    public java.util.Set<String> getTrackedItemIds() {
        return Collections.unmodifiableSet(itemVolumes.keySet());
    }

    /** Returns all tracked category IDs. */
    public java.util.Set<String> getTrackedCategoryIds() {
        return Collections.unmodifiableSet(categoryVolumes.keySet());
    }

    /**
     * Returns the transaction history for the given player, most-recent first.
     * Returns an empty list if no history exists.
     */
    public List<TransactionRecord> getPlayerHistory(UUID playerId) {
        Deque<TransactionRecord> history = playerHistories.get(playerId);
        if (history == null) return Collections.emptyList();
        synchronized (history) {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
    }

    /**
     * Exports a snapshot of all item and category volumes as a YAML-compatible map.
     * Used for persistence on plugin shutdown.
     */
    public Map<String, Object> exportSummary() {
        Map<String, Object> summary = new java.util.LinkedHashMap<>();

        Map<String, Object> items = new java.util.LinkedHashMap<>();
        itemVolumes.forEach((id, vol) -> {
            Map<String, Long> entry = new java.util.LinkedHashMap<>();
            entry.put("bought", vol[0].sum());
            entry.put("sold", vol[1].sum());
            items.put(id, entry);
        });
        summary.put("items", items);

        Map<String, Object> cats = new java.util.LinkedHashMap<>();
        categoryVolumes.forEach((id, vol) -> {
            Map<String, Long> entry = new java.util.LinkedHashMap<>();
            entry.put("bought", vol[0].sum());
            entry.put("sold", vol[1].sum());
            cats.put(id, entry);
        });
        summary.put("categories", cats);

        return summary;
    }
}
