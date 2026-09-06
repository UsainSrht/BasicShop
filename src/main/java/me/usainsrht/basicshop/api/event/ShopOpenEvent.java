package me.usainsrht.basicshop.api.event;

import me.usainsrht.basicshop.api.model.ShopCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Fired when a player attempts to open any shop GUI.
 *
 * <p>Cancelling this event prevents the GUI from opening (e.g. for combat logging,
 * world/region restrictions, or custom permissions).
 */
public class ShopOpenEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ShopViewType viewType;
    private final ShopCategory category;
    private boolean cancelled;

    public ShopOpenEvent(
            @NotNull Player player,
            @NotNull ShopViewType viewType,
            @Nullable ShopCategory category
    ) {
        super(player);
        this.viewType = viewType;
        this.category = category;
    }

    @NotNull
    public ShopViewType getViewType() {
        return viewType;
    }

    @NotNull
    public Optional<ShopCategory> getCategory() {
        return Optional.ofNullable(category);
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
