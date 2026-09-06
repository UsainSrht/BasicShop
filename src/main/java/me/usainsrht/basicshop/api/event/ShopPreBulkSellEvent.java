package me.usainsrht.basicshop.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Fired before a bulk sell operation begins (e.g. quicksell inventory, container staff sell, or item stacks sell).
 *
 * <p>Cancelling this event cancels the entire bulk sell operation immediately.
 */
public class ShopPreBulkSellEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BulkSellSource source;
    private final Inventory targetInventory;
    private boolean cancelled;

    public ShopPreBulkSellEvent(
            @NotNull Player player,
            @NotNull BulkSellSource source,
            @Nullable Inventory targetInventory
    ) {
        super(player);
        this.source = source;
        this.targetInventory = targetInventory;
    }

    @NotNull
    public BulkSellSource getSource() {
        return source;
    }

    @NotNull
    public Optional<Inventory> getTargetInventory() {
        return Optional.ofNullable(targetInventory);
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
