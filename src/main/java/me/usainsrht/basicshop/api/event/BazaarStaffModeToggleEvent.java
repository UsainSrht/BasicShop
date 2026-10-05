package me.usainsrht.basicshop.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player right-clicks air with a Bazaar Staff to toggle between listing restock mode and order delivery mode.
 */
public class BazaarStaffModeToggleEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ItemStack tool;
    private boolean newOrderMode;
    private boolean cancelled;

    public BazaarStaffModeToggleEvent(
            @NotNull Player player,
            @NotNull ItemStack tool,
            boolean newOrderMode
    ) {
        super(player);
        this.tool = tool;
        this.newOrderMode = newOrderMode;
    }

    @NotNull
    public ItemStack getTool() {
        return tool;
    }

    public boolean isNewOrderMode() {
        return newOrderMode;
    }

    public void setNewOrderMode(boolean newOrderMode) {
        this.newOrderMode = newOrderMode;
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
