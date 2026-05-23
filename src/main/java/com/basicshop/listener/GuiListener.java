package com.basicshop.listener;

import com.basicshop.gui.AbstractShopGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Routes inventory click events to the appropriate {@link AbstractShopGui}.
 *
 * <p>All GUI state changes (opening another inventory) are scheduled onto
 * the correct thread inside the GUI's {@code handleClick} method via MorePaperLib.
 */
public final class GuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AbstractShopGui gui)) return;

        // Allow clicks in the player's own inventory area (bottom half)
        if (event.getRawSlot() >= event.getInventory().getSize()) return;

        // Cancel item movement only within the shop GUI area
        event.setCancelled(true);
        gui.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof AbstractShopGui)) return;

        // Cancel drag only if it touches the shop inventory area
        int shopSize = event.getInventory().getSize();
        boolean touchesShop = event.getRawSlots().stream().anyMatch(s -> s < shopSize);
        if (touchesShop) {
            event.setCancelled(true);
        }
    }
}
