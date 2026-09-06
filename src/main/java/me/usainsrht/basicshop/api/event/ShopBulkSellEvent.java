package me.usainsrht.basicshop.api.event;

import me.usainsrht.basicshop.api.ShopAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Fired after a bulk sell operation completes successfully and at least one item was sold.
 */
public class ShopBulkSellEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BulkSellSource source;
    private final int totalAmount;
    private final double totalEarned;
    private final List<ShopAPI.SoldMaterialLine> lines;

    public ShopBulkSellEvent(
            @NotNull Player player,
            @NotNull BulkSellSource source,
            int totalAmount,
            double totalEarned,
            @NotNull List<ShopAPI.SoldMaterialLine> lines
    ) {
        super(player);
        this.source = source;
        this.totalAmount = totalAmount;
        this.totalEarned = totalEarned;
        this.lines = List.copyOf(lines);
    }

    @NotNull
    public BulkSellSource getSource() {
        return source;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public double getTotalEarned() {
        return totalEarned;
    }

    @NotNull
    public List<ShopAPI.SoldMaterialLine> getLines() {
        return lines;
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
