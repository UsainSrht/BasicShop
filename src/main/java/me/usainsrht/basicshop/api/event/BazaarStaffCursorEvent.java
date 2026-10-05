package me.usainsrht.basicshop.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player uses a Bazaar Staff inside an inventory GUI.
 */
public class BazaarStaffCursorEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ItemStack staff;
    private final Inventory clickedInventory;
    private final int slot;
    private final ClickType clickType;
    private final ItemStack targetItem;
    private final boolean staffOnCursor;
    private final boolean orderMode;
    private boolean recursive;
    private boolean cancelled;

    public BazaarStaffCursorEvent(
            @NotNull Player player,
            @NotNull ItemStack staff,
            @NotNull Inventory clickedInventory,
            int slot,
            @NotNull ClickType clickType,
            @NotNull ItemStack targetItem,
            boolean staffOnCursor,
            boolean orderMode,
            boolean recursive
    ) {
        super(player);
        this.staff = staff;
        this.clickedInventory = clickedInventory;
        this.slot = slot;
        this.clickType = clickType;
        this.targetItem = targetItem;
        this.staffOnCursor = staffOnCursor;
        this.orderMode = orderMode;
        this.recursive = recursive;
    }

    @NotNull
    public ItemStack getStaff() {
        return staff;
    }

    @NotNull
    public Inventory getClickedInventory() {
        return clickedInventory;
    }

    public int getSlot() {
        return slot;
    }

    @NotNull
    public ClickType getClickType() {
        return clickType;
    }

    @NotNull
    public ItemStack getTargetItem() {
        return targetItem;
    }

    public boolean isStaffOnCursor() {
        return staffOnCursor;
    }

    public boolean isOrderMode() {
        return orderMode;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
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
