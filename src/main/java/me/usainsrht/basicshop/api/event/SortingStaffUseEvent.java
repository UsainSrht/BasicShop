package me.usainsrht.basicshop.api.event;

import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player uses the Sorting Staff on a container block.
 *
 * <p>Cancelling this event aborts container sorting and avoids applying the tool cooldown.
 */
public class SortingStaffUseEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ItemStack tool;
    private final Block block;
    private final Container container;
    private boolean cancelled;

    public SortingStaffUseEvent(
            @NotNull Player player,
            @NotNull ItemStack tool,
            @NotNull Block block,
            @NotNull Container container
    ) {
        super(player);
        this.tool = tool;
        this.block = block;
        this.container = container;
    }

    @NotNull
    public ItemStack getTool() {
        return tool;
    }

    @NotNull
    public Block getBlock() {
        return block;
    }

    @NotNull
    public Container getContainer() {
        return container;
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
