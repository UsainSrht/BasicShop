package me.usainsrht.basicshop.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player right-clicks air to toggle the auto-sell mode on their Money Hoe.
 */
public class MoneyHoeToggleEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ItemStack tool;
    private boolean newAutoSellState;
    private boolean cancelled;

    public MoneyHoeToggleEvent(
            @NotNull Player player,
            @NotNull ItemStack tool,
            boolean newAutoSellState
    ) {
        super(player);
        this.tool = tool;
        this.newAutoSellState = newAutoSellState;
    }

    @NotNull
    public ItemStack getTool() {
        return tool;
    }

    public boolean isNewAutoSellState() {
        return newAutoSellState;
    }

    public void setNewAutoSellState(boolean newAutoSellState) {
        this.newAutoSellState = newAutoSellState;
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
