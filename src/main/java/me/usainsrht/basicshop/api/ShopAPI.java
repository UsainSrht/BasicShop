package me.usainsrht.basicshop.api;

import me.usainsrht.basicshop.api.model.ShopCategory;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.TransactionRecord;
import me.usainsrht.basicshop.api.model.TransactionResult;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core API for BasicShop operations.
 *
 * <p>All buy/sell methods should be executed on the server or player thread, as they fire
 * synchronous Bukkit events and interact directly with the player's inventory and economy.
 * Callers should ensure the player is online.
 */
public interface ShopAPI {

    // -------------------------------------------------------------------------
    // Transactions
    // -------------------------------------------------------------------------

    /**
     * Attempts to buy {@code amount} of {@code item} for the {@code player}.
     */
    TransactionResult buyItem(Player player, ShopItem item, int amount);

    /**
     * Attempts to sell {@code amount} of {@code item} from the {@code player}'s inventory.
     */
    TransactionResult sellItem(Player player, ShopItem item, int amount);

    /**
     * Sells all copies of {@code item} currently in the {@code player}'s inventory.
     */
    TransactionResult sellAll(Player player, ShopItem item);

    /**
     * Sells the item the player is currently holding in their main hand.
     *
     * @return the transaction result; {@code NOT_ENOUGH_ITEMS} if hand is empty
     */
    TransactionResult quickSellHand(Player player);

    /**
     * Sells the item stack currently on the player's cursor.
     *
     * @return the transaction result; {@code NOT_ENOUGH_ITEMS} if cursor is empty
     */
    TransactionResult quickSellCursor(Player player);

    /**
     * Sells every sellable item currently in the player's inventory.
     * Processes each item individually; the returned result reflects the
     * outcome of the overall operation.
     *
     * @return {@code SUCCESS} if at least one item was sold, otherwise {@code NOT_ENOUGH_ITEMS}
     */
    QuickSellResult quickSellInventory(Player player);

    /**
     * Sells every sellable item in the given inventory and pays the player.
     */
    QuickSellResult sellFromInventory(Player player, Inventory inventory);

    /**
     * Sells every sellable item in the given inventory and pays the player.
     * When {@code recursive} is true, sellable items inside container items (e.g. Shulker Boxes)
     * are also sold without consuming the container items themselves.
     */
    QuickSellResult sellFromInventory(Player player, Inventory inventory, boolean recursive);

    /**
     * Sells the item in the specified inventory slot.
     * If the item is a container item (e.g. Shulker Box) and {@code recursive} is true,
     * sellable items inside it are sold and the container item is updated in place.
     */
    QuickSellResult sellSlot(Player player, Inventory inventory, int slot, boolean recursive);

    /**
     * Sells the item stack currently on the player's cursor.
     * If the item is a container item (e.g. Shulker Box) and {@code recursive} is true,
     * sellable items inside it are sold and the container item is updated in place.
     */
    QuickSellResult sellCursor(Player player, boolean recursive);

    /**
     * Sells all items in the given inventory matching the specified materials.
     * If {@code includeContainers} is true, matching items inside container items (e.g. Shulker Boxes)
     * are also sold and the container items are updated in place.
     */
    QuickSellResult sellMatchingItems(Player player, Inventory inventory, java.util.Set<Material> materials, boolean includeContainers);

    /**
     * Sells the given item stacks virtually (no inventory removal) and pays the player.
     */
    QuickSellResult sellItemStacks(Player player, Collection<ItemStack> stacks);

    // -------------------------------------------------------------------------
    // Catalogue
    // -------------------------------------------------------------------------

    List<ShopCategory> getCategories();

    Optional<ShopCategory> getCategory(String id);

    /**
     * Returns the {@link ShopItem} whose {@code id} matches the given string,
     * searching across all categories.
     */
    Optional<ShopItem> getItem(String id);

    /**
     * Returns the {@link ShopCategory} that contains the given {@link ShopItem},
     * or empty if the item is not in any category.
     */
    Optional<ShopCategory> getCategoryForItem(ShopItem item);

    /** Finds a {@link ShopItem} by its Bukkit material across all categories. */
    Optional<ShopItem> getItemByMaterial(Material material);

    // -------------------------------------------------------------------------
    // Global toggles
    // -------------------------------------------------------------------------

    boolean isBuyingEnabled();

    boolean isSellingEnabled();

    // -------------------------------------------------------------------------
    // Analytics
    // -------------------------------------------------------------------------

    /**
     * Returns the transaction history for the given player (most-recent first),
     * capped at 500 entries.
     */
    List<TransactionRecord> getPlayerHistory(UUID playerId);

    // -------------------------------------------------------------------------
    // Inner result type for quickSellInventory
    // -------------------------------------------------------------------------

    record SoldMaterialLine(Material material, int amount, double earned) {}

    record QuickSellResult(
            boolean anySuccess,
            int totalAmount,
            double totalEarned,
            List<SoldMaterialLine> lines
    ) {
        public static final QuickSellResult NOTHING = new QuickSellResult(false, 0, 0, List.of());
    }
}
