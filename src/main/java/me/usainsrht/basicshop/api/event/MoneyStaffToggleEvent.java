package me.usainsrht.basicshop.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player right-clicks air to toggle the recursive selling mode on their Money Staff.
 */
public class MoneyStaffToggleEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ItemStack tool;
    private boolean newRecursiveState;
    private boolean cancelled;

    public MoneyStaffToggleEvent(
            @NotNull Player player,
            @NotNull ItemStack tool,
            boolean newRecursiveState
    ) {
        super(player);
        this.tool = tool;
        this.newRecursiveState = newRecursiveState;
    }

    @NotNull
    public ItemStack getTool() {
        return tool;
    }

    public boolean isNewRecursiveState() {
        return newRecursiveState;
    }

    public void setNewRecursiveState(boolean newRecursiveState) {
        this.newRecursiveState = newRecursiveState;
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
