package me.usainsrht.basicshop.bazaar;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Service interface decoupling BasicShop from BasicBazaar.
 */
public interface BazaarService {

    /**
     * Returns true if BasicBazaar is active and ready to handle requests.
     */
    boolean isAvailable();

    /**
     * Processes container block restocking or order delivery.
     */
    void handleContainerUse(
            Player player,
            Block block,
            boolean orderMode,
            boolean recursive,
            boolean useNativeMessages
    );

    /**
     * Processes in-GUI click when the Bazaar Staff is held on the player's cursor and clicks an inventory slot.
     */
    void handleStaffOnCursorClick(
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
    );

    /**
     * Processes in-GUI click when an item is held on the cursor and clicks a Bazaar Staff in an inventory slot.
     */
    void handleItemOnStaffClick(
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
    );
}
