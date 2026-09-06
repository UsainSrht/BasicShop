package me.usainsrht.basicshop.api.event;

/**
 * Indicates the origin of a bulk sell operation.
 */
public enum BulkSellSource {
    /** Sold via /quicksell inventory or QuickSell GUI "Sell All". */
    QUICK_SELL_INVENTORY,

    /** Sold from a container via the Money Staff tool. */
    CONTAINER_STAFF,

    /** Sold programmatically via {@code sellItemStacks}. */
    ITEM_STACKS,

    /** Sold automatically via the Money Hoe tool when harvesting crops. */
    HOE_AUTOSELL
}
