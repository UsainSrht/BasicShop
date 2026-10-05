package me.usainsrht.basicshop.bazaar;

import me.usainsrht.basicbazaar.BasicBazaarPlugin;
import me.usainsrht.basicbazaar.api.Listing;
import me.usainsrht.basicbazaar.api.Order;
import me.usainsrht.basicbazaar.api.event.BazaarListingStockAddEvent;
import me.usainsrht.basicbazaar.api.event.BazaarOrderDeliverEvent;
import me.usainsrht.basicbazaar.api.event.BazaarOrderDeliveredEvent;
import me.usainsrht.basicbazaar.core.OrderNormalizationUtil;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.item.ShopToolFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import space.arim.morepaperlib.MorePaperLib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Concrete integration hook with BasicBazaar.
 */
public final class BasicBazaarHook implements BazaarService {

    private final ConfigManager configManager;
    private final ShopToolFactory toolFactory;
    private final MorePaperLib morePaperLib;

    public BasicBazaarHook(
            ConfigManager configManager,
            ShopToolFactory toolFactory,
            MorePaperLib morePaperLib
    ) {
        this.configManager = configManager;
        this.toolFactory = toolFactory;
        this.morePaperLib = morePaperLib;
    }

    private BasicBazaarPlugin getBazaar() {
        return BasicBazaarPlugin.getInstance();
    }

    @Override
    public boolean isAvailable() {
        BasicBazaarPlugin plugin = getBazaar();
        return plugin != null && plugin.isEnabled();
    }

    // -------------------------------------------------------------------------
    // World Container Interaction
    // -------------------------------------------------------------------------

    @Override
    public void handleContainerUse(
            Player player,
            Block block,
            boolean orderMode,
            boolean recursive,
            boolean useNativeMessages
    ) {
        if (!isAvailable() || !player.isOnline()) return;

        BlockState state = block.getState();
        if (!(state instanceof Container container)) return;

        if (orderMode) {
            handleContainerDeliver(player, block, recursive, useNativeMessages);
        } else {
            handleContainerRestock(player, block, recursive, useNativeMessages);
        }
    }

    private void handleContainerRestock(
            Player player,
            Block block,
            boolean recursive,
            boolean useNativeMessages
    ) {
        BasicBazaarPlugin bazaar = getBazaar();
        UUID playerUuid = player.getUniqueId();

        bazaar.getStorage().getListingsByPlayer(playerUuid).whenComplete((listings, ex) -> {
            if (ex != null || listings == null || listings.isEmpty()) {
                sendListingFeedback(player, Map.of(), useNativeMessages);
                return;
            }

            boolean activateOnStockAdd = bazaar.getConfig().getBoolean("settings.activate-on-stock-add", true);
            List<Listing> candidateListings = new ArrayList<>();
            for (Listing l : listings) {
                if (l != null && l.getItem() != null && (!l.isExpired() || activateOnStockAdd)) {
                    candidateListings.add(l);
                }
            }

            if (candidateListings.isEmpty()) {
                sendListingFeedback(player, Map.of(), useNativeMessages);
                return;
            }

            morePaperLib.scheduling().regionSpecificScheduler(block.getLocation()).run(() -> {
                if (!player.isOnline()) return;
                BlockState currentState = block.getState();
                if (!(currentState instanceof Container currentContainer)) return;

                Inventory inv = currentContainer.getInventory();
                Map<Listing, Integer> restocked = restockInventory(player, inv, candidateListings, recursive, 1);
                sendListingFeedback(player, restocked, useNativeMessages);
            });
        });
    }

    private void handleContainerDeliver(
            Player player,
            Block block,
            boolean recursive,
            boolean useNativeMessages
    ) {
        BasicBazaarPlugin bazaar = getBazaar();
        UUID playerUuid = player.getUniqueId();

        bazaar.getStorage().getAllOrders().whenComplete((allOrders, ex) -> {
            if (ex != null || allOrders == null || allOrders.isEmpty()) {
                sendOrderFeedback(player, List.of(), useNativeMessages);
                return;
            }

            List<Order> validOrders = new ArrayList<>();
            for (Order o : allOrders) {
                if (o != null && o.canFulfill() && !o.getBuyer().equals(playerUuid)) {
                    validOrders.add(o);
                }
            }

            if (validOrders.isEmpty()) {
                sendOrderFeedback(player, List.of(), useNativeMessages);
                return;
            }

            // Highest paying orders first, oldest first on tie
            validOrders.sort((o1, o2) -> {
                int cmp = Double.compare(o2.getPrice(), o1.getPrice());
                if (cmp != 0) return cmp;
                return Long.compare(o1.getCreationTime(), o2.getCreationTime());
            });

            morePaperLib.scheduling().regionSpecificScheduler(block.getLocation()).run(() -> {
                if (!player.isOnline()) return;
                BlockState currentState = block.getState();
                if (!(currentState instanceof Container currentContainer)) return;

                Inventory inv = currentContainer.getInventory();
                List<DeliverySummary> deliveries = deliverFromInventory(player, inv, validOrders, recursive, 1);
                sendOrderFeedback(player, deliveries, useNativeMessages);
            });
        });
    }

    // -------------------------------------------------------------------------
    // GUI Cursor Interactions
    // -------------------------------------------------------------------------

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
        if (!isAvailable()) return;

        if (orderMode) {
            handleStaffOnCursorDeliver(player, clickedInv, slot, clickType, targetItem, isContainer, staff, recursive, useNativeMessages);
        } else {
            handleStaffOnCursorRestock(player, clickedInv, slot, clickType, targetItem, isContainer, staff, recursive, useNativeMessages);
        }
    }

    private void handleStaffOnCursorRestock(
            Player player,
            Inventory clickedInv,
            int slot,
            ClickType clickType,
            ItemStack targetItem,
            boolean isContainer,
            ItemStack staff,
            boolean recursive,
            boolean useNativeMessages
    ) {
        BasicBazaarPlugin bazaar = getBazaar();
        bazaar.getStorage().getListingsByPlayer(player.getUniqueId()).whenComplete((listings, ex) -> {
            if (ex != null || listings == null || listings.isEmpty()) {
                restoreCursor(player, staff);
                sendListingFeedback(player, Map.of(), useNativeMessages);
                return;
            }

            boolean activateOnStockAdd = bazaar.getConfig().getBoolean("settings.activate-on-stock-add", true);
            List<Listing> candidateListings = new ArrayList<>();
            for (Listing l : listings) {
                if (l != null && l.getItem() != null && (!l.isExpired() || activateOnStockAdd)) {
                    candidateListings.add(l);
                }
            }

            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                if (!player.isOnline()) return;
                if (!isValidInventory(clickedInv)) {
                    restoreCursor(player, staff);
                    return;
                }

                ItemStack current = clickedInv.getItem(slot);
                if (current == null || current.isEmpty() || !current.isSimilar(targetItem)) {
                    restoreCursor(player, staff);
                    return;
                }

                Map<Listing, Integer> restocked = new LinkedHashMap<>();

                if (clickType.isLeftClick()) {
                    // Left-click: single slot
                    if (isContainer) {
                        if (recursive) {
                            restockContainerItem(player, current, candidateListings, true, 1, restocked);
                            clickedInv.setItem(slot, current);
                        }
                    } else {
                        restockSingleSlot(player, clickedInv, slot, current, candidateListings, restocked);
                    }
                } else {
                    // Right-click: all matching items
                    if (isContainer) {
                        Set<Material> materials = getContainedMaterials(current);
                        if (!materials.isEmpty()) {
                            restockMatching(player, clickedInv, materials, candidateListings, true, restocked);
                        }
                    } else {
                        restockMatching(player, clickedInv, Set.of(targetItem.getType()), candidateListings, false, restocked);
                    }
                }

                restoreCursor(player, staff);
                sendListingFeedback(player, restocked, useNativeMessages);
            }, null);
        });
    }

    private void handleStaffOnCursorDeliver(
            Player player,
            Inventory clickedInv,
            int slot,
            ClickType clickType,
            ItemStack targetItem,
            boolean isContainer,
            ItemStack staff,
            boolean recursive,
            boolean useNativeMessages
    ) {
        BasicBazaarPlugin bazaar = getBazaar();
        bazaar.getStorage().getAllOrders().whenComplete((allOrders, ex) -> {
            if (ex != null || allOrders == null || allOrders.isEmpty()) {
                restoreCursor(player, staff);
                sendOrderFeedback(player, List.of(), useNativeMessages);
                return;
            }

            List<Order> validOrders = new ArrayList<>();
            for (Order o : allOrders) {
                if (o != null && o.canFulfill() && !o.getBuyer().equals(player.getUniqueId())) {
                    validOrders.add(o);
                }
            }

            validOrders.sort((o1, o2) -> {
                int cmp = Double.compare(o2.getPrice(), o1.getPrice());
                if (cmp != 0) return cmp;
                return Long.compare(o1.getCreationTime(), o2.getCreationTime());
            });

            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                if (!player.isOnline()) return;
                if (!isValidInventory(clickedInv)) {
                    restoreCursor(player, staff);
                    return;
                }

                ItemStack current = clickedInv.getItem(slot);
                if (current == null || current.isEmpty() || !current.isSimilar(targetItem)) {
                    restoreCursor(player, staff);
                    return;
                }

                List<DeliverySummary> deliveries = new ArrayList<>();

                if (clickType.isLeftClick()) {
                    // Left-click: single slot
                    if (isContainer) {
                        if (recursive) {
                            deliverFromContainerItem(player, current, validOrders, true, 1, deliveries);
                            clickedInv.setItem(slot, current);
                        }
                    } else {
                        deliverSingleSlot(player, clickedInv, slot, current, validOrders, deliveries);
                    }
                } else {
                    // Right-click: all matching items
                    if (isContainer) {
                        Set<Material> materials = getContainedMaterials(current);
                        if (!materials.isEmpty()) {
                            deliverMatching(player, clickedInv, materials, validOrders, true, deliveries);
                        }
                    } else {
                        deliverMatching(player, clickedInv, Set.of(targetItem.getType()), validOrders, false, deliveries);
                    }
                }

                restoreCursor(player, staff);
                sendOrderFeedback(player, deliveries, useNativeMessages);
            }, null);
        });
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
        if (!isAvailable()) return;

        if (orderMode) {
            handleItemOnStaffDeliver(player, clickedInv, slot, clickType, targetCursor, isContainer, staffItem, recursive, useNativeMessages);
        } else {
            handleItemOnStaffRestock(player, clickedInv, slot, clickType, targetCursor, isContainer, staffItem, recursive, useNativeMessages);
        }
    }

    private void handleItemOnStaffRestock(
            Player player,
            Inventory clickedInv,
            int slot,
            ClickType clickType,
            ItemStack targetCursor,
            boolean isContainer,
            ItemStack staffItem,
            boolean recursive,
            boolean useNativeMessages
    ) {
        BasicBazaarPlugin bazaar = getBazaar();
        bazaar.getStorage().getListingsByPlayer(player.getUniqueId()).whenComplete((listings, ex) -> {
            if (ex != null || listings == null || listings.isEmpty()) {
                sendListingFeedback(player, Map.of(), useNativeMessages);
                return;
            }

            boolean activateOnStockAdd = bazaar.getConfig().getBoolean("settings.activate-on-stock-add", true);
            List<Listing> candidateListings = new ArrayList<>();
            for (Listing l : listings) {
                if (l != null && l.getItem() != null && (!l.isExpired() || activateOnStockAdd)) {
                    candidateListings.add(l);
                }
            }

            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                if (!player.isOnline()) return;
                if (!isValidInventory(clickedInv)) return;

                ItemStack currentSlot = clickedInv.getItem(slot);
                if (currentSlot == null || currentSlot.isEmpty() || !currentSlot.isSimilar(staffItem)) return;

                ItemStack currentCursor = player.getItemOnCursor();
                if (currentCursor == null || currentCursor.isEmpty() || !currentCursor.isSimilar(targetCursor)) return;

                Map<Listing, Integer> restocked = new LinkedHashMap<>();

                if (clickType.isLeftClick()) {
                    // Left-click: cursor slot only
                    if (isContainer) {
                        if (recursive) {
                            restockContainerItem(player, currentCursor, candidateListings, true, 1, restocked);
                            player.setItemOnCursor(currentCursor);
                        }
                    } else {
                        restockCursorStack(player, currentCursor, candidateListings, restocked);
                    }
                } else {
                    // Right-click: matching in inventory AND cursor
                    if (isContainer) {
                        Set<Material> materials = getContainedMaterials(currentCursor);
                        if (!materials.isEmpty()) {
                            restockMatching(player, clickedInv, materials, candidateListings, true, restocked);
                        }
                        if (recursive) {
                            restockContainerItem(player, currentCursor, candidateListings, true, 1, restocked);
                            player.setItemOnCursor(currentCursor);
                        }
                    } else {
                        restockMatching(player, clickedInv, Set.of(targetCursor.getType()), candidateListings, false, restocked);
                        restockCursorStack(player, currentCursor, candidateListings, restocked);
                    }
                }

                player.updateInventory();
                sendListingFeedback(player, restocked, useNativeMessages);
            }, null);
        });
    }

    private void handleItemOnStaffDeliver(
            Player player,
            Inventory clickedInv,
            int slot,
            ClickType clickType,
            ItemStack targetCursor,
            boolean isContainer,
            ItemStack staffItem,
            boolean recursive,
            boolean useNativeMessages
    ) {
        BasicBazaarPlugin bazaar = getBazaar();
        bazaar.getStorage().getAllOrders().whenComplete((allOrders, ex) -> {
            if (ex != null || allOrders == null || allOrders.isEmpty()) {
                sendOrderFeedback(player, List.of(), useNativeMessages);
                return;
            }

            List<Order> validOrders = new ArrayList<>();
            for (Order o : allOrders) {
                if (o != null && o.canFulfill() && !o.getBuyer().equals(player.getUniqueId())) {
                    validOrders.add(o);
                }
            }

            validOrders.sort((o1, o2) -> {
                int cmp = Double.compare(o2.getPrice(), o1.getPrice());
                if (cmp != 0) return cmp;
                return Long.compare(o1.getCreationTime(), o2.getCreationTime());
            });

            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                if (!player.isOnline()) return;
                if (!isValidInventory(clickedInv)) return;

                ItemStack currentSlot = clickedInv.getItem(slot);
                if (currentSlot == null || currentSlot.isEmpty() || !currentSlot.isSimilar(staffItem)) return;

                ItemStack currentCursor = player.getItemOnCursor();
                if (currentCursor == null || currentCursor.isEmpty() || !currentCursor.isSimilar(targetCursor)) return;

                List<DeliverySummary> deliveries = new ArrayList<>();

                if (clickType.isLeftClick()) {
                    // Left-click: cursor slot only
                    if (isContainer) {
                        if (recursive) {
                            deliverFromContainerItem(player, currentCursor, validOrders, true, 1, deliveries);
                            player.setItemOnCursor(currentCursor);
                        }
                    } else {
                        deliverCursorStack(player, currentCursor, validOrders, deliveries);
                    }
                } else {
                    // Right-click: matching in inventory AND cursor
                    if (isContainer) {
                        Set<Material> materials = getContainedMaterials(currentCursor);
                        if (!materials.isEmpty()) {
                            deliverMatching(player, clickedInv, materials, validOrders, true, deliveries);
                        }
                        if (recursive) {
                            deliverFromContainerItem(player, currentCursor, validOrders, true, 1, deliveries);
                            player.setItemOnCursor(currentCursor);
                        }
                    } else {
                        deliverMatching(player, clickedInv, Set.of(targetCursor.getType()), validOrders, false, deliveries);
                        deliverCursorStack(player, currentCursor, validOrders, deliveries);
                    }
                }

                player.updateInventory();
                sendOrderFeedback(player, deliveries, useNativeMessages);
            }, null);
        });
    }

    // -------------------------------------------------------------------------
    // Core Restocking Algorithms
    // -------------------------------------------------------------------------

    private Map<Listing, Integer> restockInventory(
            Player player,
            Inventory inv,
            List<Listing> listings,
            boolean recursive,
            int depth
    ) {
        Map<Listing, Integer> restocked = new LinkedHashMap<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isAirOrEmpty(stack) || isShopTool(stack)) continue;

            if (isContainerItem(stack)) {
                if (recursive && depth <= 5) {
                    boolean modified = restockContainerItem(player, stack, listings, recursive, depth + 1, restocked);
                    if (modified) {
                        inv.setItem(i, stack);
                    }
                }
                continue;
            }

            restockSingleSlot(player, inv, i, stack, listings, restocked);
        }
        return restocked;
    }

    private void restockMatching(
            Player player,
            Inventory inv,
            Set<Material> materials,
            List<Listing> listings,
            boolean recursive,
            Map<Listing, Integer> restocked
    ) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isAirOrEmpty(stack) || isShopTool(stack)) continue;

            if (isContainerItem(stack)) {
                if (recursive) {
                    boolean modified = restockContainerItem(player, stack, listings, true, 1, restocked);
                    if (modified) {
                        inv.setItem(i, stack);
                    }
                }
                continue;
            }

            if (materials.contains(stack.getType())) {
                restockSingleSlot(player, inv, i, stack, listings, restocked);
            }
        }
    }

    private boolean restockContainerItem(
            Player player,
            ItemStack containerStack,
            List<Listing> listings,
            boolean recursive,
            int depth,
            Map<Listing, Integer> restocked
    ) {
        if (depth > 5 || !isContainerItem(containerStack)) return false;
        ItemMeta meta = containerStack.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) return false;
        if (!(bsm.getBlockState() instanceof Container container)) return false;

        Inventory inner = container.getInventory();
        boolean anyModified = false;

        for (int i = 0; i < inner.getSize(); i++) {
            ItemStack innerStack = inner.getItem(i);
            if (isAirOrEmpty(innerStack) || isShopTool(innerStack)) continue;

            if (isContainerItem(innerStack)) {
                if (recursive) {
                    boolean subModified = restockContainerItem(player, innerStack, listings, recursive, depth + 1, restocked);
                    if (subModified) {
                        inner.setItem(i, innerStack);
                        anyModified = true;
                    }
                }
                continue;
            }

            int added = executeRestockOnStack(player, innerStack, listings, restocked);
            if (added > 0) {
                anyModified = true;
                if (added >= innerStack.getAmount()) {
                    inner.setItem(i, null);
                } else {
                    innerStack.setAmount(innerStack.getAmount() - added);
                    inner.setItem(i, innerStack);
                }
            }
        }

        if (anyModified) {
            bsm.setBlockState(container);
            containerStack.setItemMeta(bsm);
        }
        return anyModified;
    }

    private void restockSingleSlot(
            Player player,
            Inventory inv,
            int slot,
            ItemStack stack,
            List<Listing> listings,
            Map<Listing, Integer> restocked
    ) {
        int added = executeRestockOnStack(player, stack, listings, restocked);
        if (added > 0) {
            if (added >= stack.getAmount()) {
                inv.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - added);
                inv.setItem(slot, stack);
            }
        }
    }

    private void restockCursorStack(
            Player player,
            ItemStack cursorStack,
            List<Listing> listings,
            Map<Listing, Integer> restocked
    ) {
        int added = executeRestockOnStack(player, cursorStack, listings, restocked);
        if (added > 0) {
            if (added >= cursorStack.getAmount()) {
                player.setItemOnCursor(null);
            } else {
                cursorStack.setAmount(cursorStack.getAmount() - added);
                player.setItemOnCursor(cursorStack);
            }
        }
    }

    private int executeRestockOnStack(
            Player player,
            ItemStack stack,
            List<Listing> listings,
            Map<Listing, Integer> restocked
    ) {
        if (isAirOrEmpty(stack) || isShopTool(stack)) return 0;
        Listing matched = null;
        for (Listing l : listings) {
            if (l.getItem() != null && stack.isSimilar(l.getItem())) {
                matched = l;
                break;
            }
        }
        if (matched == null) return 0;

        BasicBazaarPlugin bazaar = getBazaar();
        if (!bazaar.acquireListingLock(matched.getId())) {
            return 0;
        }

        int requested = stack.getAmount();
        BazaarListingStockAddEvent event = new BazaarListingStockAddEvent(player, matched, requested);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            bazaar.releaseListingLock(matched.getId());
            return 0;
        }

        int toAdd = Math.min(requested, event.getAmount());
        if (toAdd <= 0) {
            bazaar.releaseListingLock(matched.getId());
            return 0;
        }

        matched.setStock(matched.getStock() + toAdd);
        boolean activateOnStockAdd = bazaar.getConfig().getBoolean("settings.activate-on-stock-add", true);
        if (activateOnStockAdd && (matched.isExpired() || matched.getExpiryTime() > 0)) {
            matched.setExpiryTime(bazaar.calculateListingExpiryTime());
        }

        final Listing finalMatched = matched;
        bazaar.getStorage().updateListing(finalMatched).whenComplete((v, ex) -> {
            bazaar.releaseListingLock(finalMatched.getId());
        });

        restocked.merge(matched, toAdd, Integer::sum);
        return toAdd;
    }

    // -------------------------------------------------------------------------
    // Core Order Delivery Algorithms
    // -------------------------------------------------------------------------

    public record DeliverySummary(Order order, int amount, double earnings) {}

    private List<DeliverySummary> deliverFromInventory(
            Player player,
            Inventory inv,
            List<Order> orders,
            boolean recursive,
            int depth
    ) {
        List<DeliverySummary> deliveries = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isAirOrEmpty(stack) || isShopTool(stack)) continue;

            if (isContainerItem(stack)) {
                if (recursive && depth <= 5) {
                    boolean modified = deliverFromContainerItem(player, stack, orders, recursive, depth + 1, deliveries);
                    if (modified) {
                        inv.setItem(i, stack);
                    }
                }
                continue;
            }

            deliverSingleSlot(player, inv, i, stack, orders, deliveries);
        }
        return deliveries;
    }

    private void deliverMatching(
            Player player,
            Inventory inv,
            Set<Material> materials,
            List<Order> orders,
            boolean recursive,
            List<DeliverySummary> deliveries
    ) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isAirOrEmpty(stack) || isShopTool(stack)) continue;

            if (isContainerItem(stack)) {
                if (recursive) {
                    boolean modified = deliverFromContainerItem(player, stack, orders, true, 1, deliveries);
                    if (modified) {
                        inv.setItem(i, stack);
                    }
                }
                continue;
            }

            if (materials.contains(stack.getType())) {
                deliverSingleSlot(player, inv, i, stack, orders, deliveries);
            }
        }
    }

    private boolean deliverFromContainerItem(
            Player player,
            ItemStack containerStack,
            List<Order> orders,
            boolean recursive,
            int depth,
            List<DeliverySummary> deliveries
    ) {
        if (depth > 5 || !isContainerItem(containerStack)) return false;
        ItemMeta meta = containerStack.getItemMeta();
        if (!(meta instanceof BlockStateMeta bsm)) return false;
        if (!(bsm.getBlockState() instanceof Container container)) return false;

        Inventory inner = container.getInventory();
        boolean anyModified = false;

        for (int i = 0; i < inner.getSize(); i++) {
            ItemStack innerStack = inner.getItem(i);
            if (isAirOrEmpty(innerStack) || isShopTool(innerStack)) continue;

            if (isContainerItem(innerStack)) {
                if (recursive) {
                    boolean subModified = deliverFromContainerItem(player, innerStack, orders, recursive, depth + 1, deliveries);
                    if (subModified) {
                        inner.setItem(i, innerStack);
                        anyModified = true;
                    }
                }
                continue;
            }

            int delivered = executeDeliveryOnStack(player, innerStack, orders, deliveries);
            if (delivered > 0) {
                anyModified = true;
                if (delivered >= innerStack.getAmount()) {
                    inner.setItem(i, null);
                } else {
                    innerStack.setAmount(innerStack.getAmount() - delivered);
                    inner.setItem(i, innerStack);
                }
            }
        }

        if (anyModified) {
            bsm.setBlockState(container);
            containerStack.setItemMeta(bsm);
        }
        return anyModified;
    }

    private void deliverSingleSlot(
            Player player,
            Inventory inv,
            int slot,
            ItemStack stack,
            List<Order> orders,
            List<DeliverySummary> deliveries
    ) {
        int delivered = executeDeliveryOnStack(player, stack, orders, deliveries);
        if (delivered > 0) {
            if (delivered >= stack.getAmount()) {
                inv.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - delivered);
                inv.setItem(slot, stack);
            }
        }
    }

    private void deliverCursorStack(
            Player player,
            ItemStack cursorStack,
            List<Order> orders,
            List<DeliverySummary> deliveries
    ) {
        int delivered = executeDeliveryOnStack(player, cursorStack, orders, deliveries);
        if (delivered > 0) {
            if (delivered >= cursorStack.getAmount()) {
                player.setItemOnCursor(null);
            } else {
                cursorStack.setAmount(cursorStack.getAmount() - delivered);
                player.setItemOnCursor(cursorStack);
            }
        }
    }

    private int executeDeliveryOnStack(
            Player player,
            ItemStack stack,
            List<Order> orders,
            List<DeliverySummary> deliveries
    ) {
        if (isAirOrEmpty(stack) || isShopTool(stack)) return 0;
        int totalDeliveredFromStack = 0;

        for (Order order : orders) {
            if (stack.getAmount() <= 0) break;
            if (!order.canFulfill() || order.getRemainingAmount() <= 0) continue;
            if (!OrderNormalizationUtil.matchesOrderDelivery(stack, order.getItem())) continue;

            BasicBazaarPlugin bazaar = getBazaar();
            if (!bazaar.acquireOrderLock(order.getId())) continue;

            int deliverAmount = Math.min(stack.getAmount(), order.getRemainingAmount());
            if (deliverAmount <= 0) {
                bazaar.releaseOrderLock(order.getId());
                continue;
            }

            double initialEarnings = deliverAmount * order.getPrice();
            BazaarOrderDeliverEvent deliverEvent = new BazaarOrderDeliverEvent(player, order, deliverAmount, initialEarnings);
            Bukkit.getPluginManager().callEvent(deliverEvent);
            if (deliverEvent.isCancelled()) {
                bazaar.releaseOrderLock(order.getId());
                continue;
            }

            double finalEarnings = deliverEvent.getTotalEarnings();
            long now = System.currentTimeMillis();

            // Perform atomic fulfillment in storage
            bazaar.getStorage().tryFulfillOrder(order.getId(), deliverAmount, now).whenComplete((updated, fulfillEx) -> {
                bazaar.releaseOrderLock(order.getId());
                if (fulfillEx != null || updated == null) {
                    // Refund items to player if DB update fails unexpectedly
                    morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                        ItemStack refund = OrderNormalizationUtil.cleanItem(order.getItem()).clone();
                        refund.setAmount(deliverAmount);
                        var leftover = player.getInventory().addItem(refund);
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }, null);
                    return;
                }

                morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                    bazaar.getEconomyManager().deposit(player.getUniqueId(), finalEarnings);

                    BazaarOrderDeliveredEvent deliveredEvent = new BazaarOrderDeliveredEvent(player, order, deliverAmount, finalEarnings);
                    Bukkit.getPluginManager().callEvent(deliveredEvent);

                    bazaar.getNotificationManager().notifyBuyerOrderDelivery(order.getBuyer(), order, deliverAmount, finalEarnings, player.getName());

                    ItemStack logItem = order.getItem().clone();
                    logItem.setAmount(deliverAmount);
                    bazaar.getTransactionLogger().logTransaction(
                            player.getUniqueId(), player.getName(),
                            "order_delivered", logItem,
                            order.getBuyer(), order.getBuyerName(),
                            finalEarnings
                    );
                }, null);
            });

            // Update in-memory order remaining amount so next items in this pass know the updated state
            order.setFulfilledAmount(order.getFulfilledAmount() + deliverAmount);

            totalDeliveredFromStack += deliverAmount;
            stack.setAmount(stack.getAmount() - deliverAmount);
            deliveries.add(new DeliverySummary(order, deliverAmount, finalEarnings));
        }

        return totalDeliveredFromStack;
    }

    // -------------------------------------------------------------------------
    // Feedback & Utility Helpers
    // -------------------------------------------------------------------------

    private void sendListingFeedback(
            Player player,
            Map<Listing, Integer> restocked,
            boolean useNativeMessages
    ) {
        if (!player.isOnline()) return;
        if (restocked.isEmpty()) {
            configManager.getMessagesConfig().send(player, "tool-bazaar-staff-no-items");
            return;
        }

        BasicBazaarPlugin bazaar = getBazaar();
        for (Map.Entry<Listing, Integer> entry : restocked.entrySet()) {
            Listing listing = entry.getKey();
            int amount = entry.getValue();

            if (useNativeMessages) {
                bazaar.getMessageManager().getMessage("listing-stock-added").send(player,
                        Placeholder.parsed("amount", String.valueOf(amount)),
                        Placeholder.parsed("stock", String.valueOf(listing.getStock())));
            } else {
                Component itemComp = bazaar.getItemAsComponent(listing.getItem(), amount);
                configManager.getMessagesConfig().send(player, "tool-bazaar-staff-restock-line",
                        Placeholder.unparsed("amount", String.valueOf(amount)),
                        Placeholder.component("item", itemComp),
                        Placeholder.unparsed("stock", String.valueOf(listing.getStock())));
            }
        }
    }

    private void sendOrderFeedback(
            Player player,
            List<DeliverySummary> deliveries,
            boolean useNativeMessages
    ) {
        if (!player.isOnline()) return;
        if (deliveries.isEmpty()) {
            configManager.getMessagesConfig().send(player, "tool-bazaar-staff-no-orders");
            return;
        }

        BasicBazaarPlugin bazaar = getBazaar();
        for (DeliverySummary d : deliveries) {
            Order order = d.order();
            int amount = d.amount();
            double earnings = d.earnings();

            if (useNativeMessages) {
                bazaar.getMessageManager().getMessage("order-delivered").send(player,
                        Placeholder.component("item", bazaar.getItemAsComponent(order.getItem(), amount)),
                        Placeholder.parsed("amount", String.valueOf(amount)),
                        bazaar.getMiniMessageService().playerComponent("buyer", order.getBuyer(), order.getBuyerName()),
                        Placeholder.parsed("price", bazaar.getMoneyFormatter().format(earnings)));
            } else {
                Component itemComp = bazaar.getItemAsComponent(order.getItem(), amount);
                configManager.getMessagesConfig().send(player, "tool-bazaar-staff-deliver-line",
                        Placeholder.unparsed("amount", String.valueOf(amount)),
                        Placeholder.component("item", itemComp),
                        Placeholder.unparsed("buyer", order.getBuyerName()),
                        Placeholder.unparsed("price", bazaar.getMoneyFormatter().format(earnings)));
            }
        }
    }

    private void restoreCursor(Player player, ItemStack staff) {
        if (!player.isOnline()) return;
        player.setItemOnCursor(staff);
        player.updateInventory();
    }

    private boolean isValidInventory(Inventory inv) {
        if (inv == null) return false;
        if (inv.getHolder() instanceof BlockInventoryHolder bih) {
            return bih.getBlock().getState() instanceof Container;
        } else if (inv.getHolder() instanceof DoubleChest dc) {
            return dc.getLocation().getBlock().getState() instanceof Container;
        }
        return true;
    }

    private boolean isShopTool(ItemStack stack) {
        return toolFactory.isShopTool(stack);
    }

    private static boolean isAirOrEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0 || stack.isEmpty();
    }

    public static boolean isContainerItem(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof Container;
    }

    public static Set<Material> getContainedMaterials(ItemStack containerStack) {
        if (!isContainerItem(containerStack)) return Set.of();
        BlockStateMeta bsm = (BlockStateMeta) containerStack.getItemMeta();
        if (!(bsm.getBlockState() instanceof Container container)) return Set.of();

        java.util.HashSet<Material> mats = new java.util.HashSet<>();
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.isEmpty() && !item.getType().isAir()) {
                mats.add(item.getType());
            }
        }
        return Set.copyOf(mats);
    }
}
