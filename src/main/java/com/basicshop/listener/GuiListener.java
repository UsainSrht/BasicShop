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

        // Cancel the event first to prevent item movement in all shop GUIs
        event.setCancelled(true);

        // Guard: only handle clicks that land inside the top inventory
        // (rawSlot < inventory size means it's the shop GUI, not the player inv)
        if (event.getRawSlot() >= event.getInventory().getSize()) return;

        gui.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof AbstractShopGui) {
            event.setCancelled(true);
        }
    }
}
