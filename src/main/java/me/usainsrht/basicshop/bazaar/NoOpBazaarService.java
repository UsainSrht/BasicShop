package me.usainsrht.basicshop.bazaar;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * No-op implementation used when BasicBazaar is not enabled or available on the server.
 */
public final class NoOpBazaarService implements BazaarService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void handleContainerUse(
            Player player,
            Block block,
            boolean orderMode,
            boolean recursive,
            boolean useNativeMessages
    ) {
        // No-op: BasicBazaar is not installed or enabled.
    }

    @Override
    public void handleStaffOnCursorClick(
            Player player,
            Inventory clickedInv,
            int slot,
            ClickType clickType,
            ItemStack targetItem,
            boolean isContainer,
            ItemStack staff,
            boolean orderMode,
            boolean recursive,
            boolean useNativeMessages
    ) {
        // No-op
    }

    @Override
    public void handleItemOnStaffClick(
            Player player,
            Inventory clickedInv,
            int slot,
            ClickType clickType,
            ItemStack targetCursor,
            boolean isContainer,
            ItemStack staffItem,
            boolean orderMode,
            boolean recursive,
            boolean useNativeMessages
    ) {
        // No-op
    }
}
