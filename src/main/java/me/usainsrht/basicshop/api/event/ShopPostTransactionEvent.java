package me.usainsrht.basicshop.api.event;

import me.usainsrht.basicshop.api.model.ShopCategory;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.TransactionRecord;
import me.usainsrht.basicshop.api.model.TransactionType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Fired immediately after a shop transaction has successfully completed and been recorded.
 *
 * <p>Useful for external analytics, quest/battlepass progression, discord webhooks, and custom rewards.
 */
public class ShopPostTransactionEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ShopItem item;
    private final ShopCategory category;
    private final TransactionType type;
    private final int amount;
    private final double totalPrice;
    private final TransactionRecord record;

    public ShopPostTransactionEvent(
            @NotNull Player player,
            @NotNull ShopItem item,
            @Nullable ShopCategory category,
            @NotNull TransactionType type,
            int amount,
            double totalPrice,
            @NotNull TransactionRecord record
    ) {
        super(player);
        this.item = item;
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.totalPrice = totalPrice;
        this.record = record;
    }

    @NotNull
    public ShopItem getItem() {
        return item;
    }

    @NotNull
    public Optional<ShopCategory> getCategory() {
        return Optional.ofNullable(category);
    }

    @NotNull
    public TransactionType getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    @NotNull
    public TransactionRecord getRecord() {
        return record;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
