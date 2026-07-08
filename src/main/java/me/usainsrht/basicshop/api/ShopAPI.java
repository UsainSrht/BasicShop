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
 * <p>All buy/sell methods are safe to call from any thread; however, they interact
 * with the player's inventory and economy, so callers should ensure the player is online.
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
