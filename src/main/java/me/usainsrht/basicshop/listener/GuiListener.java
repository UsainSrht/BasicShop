package me.usainsrht.basicshop.listener;

import me.usainsrht.basicshop.gui.AbstractShopGui;
import me.usainsrht.basicshop.gui.QuickSellGui;
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

        // Route clicks to GUI handler
        int rawSlot = event.getRawSlot();
        int invSize = event.getInventory().getSize();

        if (rawSlot >= invSize) {
            // Clicked in player inventory area
            if (gui instanceof QuickSellGui quickSellGui) {
                if (event.isShiftClick()) {
                    quickSellGui.handlePlayerInventoryShiftClick(event);
                }
                event.setCancelled(true);
            }
            return;
        }

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
