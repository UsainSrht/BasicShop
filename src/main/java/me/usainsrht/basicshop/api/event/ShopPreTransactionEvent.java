package me.usainsrht.basicshop.api.event;

import me.usainsrht.basicshop.api.model.ShopCategory;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.TransactionType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Fired before a shop transaction (buy or sell) is processed.
 *
 * <p>Cancelling this event prevents the transaction from occurring. Listeners may
 * also adjust {@link #getAmount()} or {@link #getPrice()} to apply dynamic pricing,
 * taxes, discounts, or quantity limits.
 */
public class ShopPreTransactionEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ShopItem item;
    private final ShopCategory category;
    private final TransactionType type;
    private int amount;
    private double price;
    private boolean cancelled;

    public ShopPreTransactionEvent(
            @NotNull Player player,
            @NotNull ShopItem item,
            @Nullable ShopCategory category,
            @NotNull TransactionType type,
            int amount,
            double price
    ) {
        super(player);
        this.item = item;
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.price = price;
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

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
