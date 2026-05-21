package com.basicshop.api.model;

/**
 * Result code returned by every shop transaction.
 */
public enum TransactionResult {
    /** The transaction completed successfully. */
    SUCCESS,

    /** The player does not have enough money to buy the item. */
    INSUFFICIENT_FUNDS,

    /** The player does not have enough of the item to sell. */
    NOT_ENOUGH_ITEMS,

    /** Buying is disabled for this specific item (price missing or -1). */
    BUY_DISABLED,

    /** Selling is disabled for this specific item (price missing or -1). */
    SELL_DISABLED,

    /** Buying has been disabled globally in config.yml. */
    GLOBAL_BUY_DISABLED,

    /** Selling has been disabled globally in config.yml. */
    GLOBAL_SELL_DISABLED,

    /** The economy provider is not available. */
    ECONOMY_UNAVAILABLE
}
