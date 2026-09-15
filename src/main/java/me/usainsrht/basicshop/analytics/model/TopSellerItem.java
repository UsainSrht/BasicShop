package me.usainsrht.basicshop.analytics.model;

import org.bukkit.Material;

/**
 * Immutable snapshot of a top selling item in the shop.
 */
public record TopSellerItem(
        int rank,
        String itemId,
        Material material,
        String categoryId,
        long unitsSold,
        double totalProfit,
        int transactionCount
) {
    public TopSellerItem withRank(int newRank) {
        return new TopSellerItem(newRank, itemId, material, categoryId, unitsSold, totalProfit, transactionCount);
    }
}
