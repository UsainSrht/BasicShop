package me.usainsrht.basicshop.api.event;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when the BasicShop configuration is reloaded via {@code /shop reload}.
 */
public class ShopReloadEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender sender;

    public ShopReloadEvent(@NotNull CommandSender sender) {
        this.sender = sender;
    }

    @NotNull
    public CommandSender getSender() {
        return sender;
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
