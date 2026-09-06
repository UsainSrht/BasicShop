package me.usainsrht.basicshop.api.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Fired when a player breaks a fully grown crop using the Money Hoe.
 *
 * <p>Cancelling this event cancels the crop break and replant.
 * Listeners may also change {@link #setAutoSell(boolean)} to force drops to be given or auto-sold.
 */
public class MoneyHoeHarvestEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ItemStack tool;
    private final Block block;
    private boolean autoSell;
    private final Collection<ItemStack> drops;
    private boolean cancelled;

    public MoneyHoeHarvestEvent(
            @NotNull Player player,
            @NotNull ItemStack tool,
            @NotNull Block block,
            boolean autoSell,
            @NotNull Collection<ItemStack> drops
    ) {
        super(player);
        this.tool = tool;
        this.block = block;
        this.autoSell = autoSell;
        this.drops = drops;
    }

    @NotNull
    public ItemStack getTool() {
        return tool;
    }

    @NotNull
    public Block getBlock() {
        return block;
    }

    public boolean isAutoSell() {
        return autoSell;
    }

    public void setAutoSell(boolean autoSell) {
        this.autoSell = autoSell;
    }

    @NotNull
    public Collection<ItemStack> getDrops() {
        return drops;
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
