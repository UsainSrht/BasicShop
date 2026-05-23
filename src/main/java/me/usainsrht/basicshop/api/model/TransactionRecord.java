package me.usainsrht.basicshop.api.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a single shop transaction for analytics purposes.
 */
public final class TransactionRecord {

    private final UUID playerId;
    private final String playerName;
    private final String itemId;
    private final String categoryId;
    private final TransactionType type;
    private final int amount;
    private final double totalPrice;
    private final Instant timestamp;

    public TransactionRecord(
            UUID playerId,
            String playerName,
            String itemId,
            String categoryId,
            TransactionType type,
            int amount,
            double totalPrice,
            Instant timestamp
    ) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.itemId = itemId;
        this.categoryId = categoryId;
        this.type = type;
        this.amount = amount;
        this.totalPrice = totalPrice;
        this.timestamp = timestamp;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getItemId() {
        return itemId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public TransactionType getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /** Formats this record as a CSV row. */
    public String toCsvRow() {
        return String.join(",",
                timestamp.toString(),
                playerId.toString(),
                playerName,
                categoryId,
                itemId,
                type.name(),
                String.valueOf(amount),
                String.format("%.2f", totalPrice)
        );
    }

    public static String csvHeader() {
        return "timestamp,player_id,player_name,category_id,item_id,type,amount,total_price";
    }
}
