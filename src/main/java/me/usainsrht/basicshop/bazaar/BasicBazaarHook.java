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
                RestockSession session = new RestockSession(player, candidateListings, bazaar);
                restockInventory(session, inv, recursive, 1);
                Map<Listing, Integer> restocked = session.finish();
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
                DeliverySession session = new DeliverySession(player, validOrders, bazaar, morePaperLib);
                deliverInventory(session, inv, recursive, 1);
                List<DeliverySummary> deliveries = session.finish();
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

                RestockSession session = new RestockSession(player, candidateListings, bazaar);

                if (clickType.isLeftClick()) {
                    // Left-click: single slot
                    if (isContainer) {
                        if (recursive) {
                            restockContainerItem(session, current, true, 1);
                            clickedInv.setItem(slot, current);
                        }
                    } else {
                        restockSingleSlot(session, clickedInv, slot, current);
                    }
                } else {
                    // Right-click: all matching items
                    if (isContainer) {
                        Set<Material> materials = getContainedMaterials(current);
                        if (!materials.isEmpty()) {
                            restockMatchingMaterials(session, clickedInv, materials, true);
                        }
                    } else {
                        restockMatchingSimilar(session, clickedInv, targetItem, recursive);
                    }
                }

                Map<Listing, Integer> restocked = session.finish();
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

                DeliverySession session = new DeliverySession(player, validOrders, bazaar, morePaperLib);

                if (clickType.isLeftClick()) {
                    // Left-click: single slot
                    if (isContainer) {
                        if (recursive) {
                            deliverFromContainerItem(session, current, true, 1);
                            clickedInv.setItem(slot, current);
                        }
                    } else {
                        deliverSingleSlot(session, clickedInv, slot, current);
                    }
                } else {
                    // Right-click: all matching items
                    if (isContainer) {
                        Set<Material> materials = getContainedMaterials(current);
                        if (!materials.isEmpty()) {
                            deliverMatchingMaterials(session, clickedInv, materials, true);
                        }
                    } else {
                        deliverMatchingSimilar(session, clickedInv, targetItem, recursive);
                    }
                }

                List<DeliverySummary> deliveries = session.finish();
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

                RestockSession session = new RestockSession(player, candidateListings, bazaar);

                if (clickType.isLeftClick()) {
                    // Left-click: cursor slot only
                    if (isContainer) {
                        if (recursive) {
                            restockContainerItem(session, currentCursor, true, 1);
                            player.setItemOnCursor(currentCursor);
                        }
                    } else {
                        restockCursorStack(session, player, currentCursor);
                    }
                } else {
                    // Right-click: matching in inventory AND cursor
                    if (isContainer) {
                        Set<Material> materials = getContainedMaterials(currentCursor);
                        if (!materials.isEmpty()) {
                            restockMatchingMaterials(session, clickedInv, materials, true);
                            if (clickedInv != player.getInventory()) {
                                restockMatchingMaterials(session, player.getInventory(), materials, true);
                            }
                        }
                        if (recursive) {
                            restockContainerItem(session, currentCursor, true, 1);
                            player.setItemOnCursor(currentCursor);
                        }
                    } else {
                        restockMatchingSimilar(session, clickedInv, targetCursor, recursive);
                        if (clickedInv != player.getInventory()) {
                            restockMatchingSimilar(session, player.getInventory(), targetCursor, recursive);
                        }
                        restockCursorStack(session, player, currentCursor);
                    }
                }

                Map<Listing, Integer> restocked = session.finish();
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

                DeliverySession session = new DeliverySession(player, validOrders, bazaar, morePaperLib);

                if (clickType.isLeftClick()) {
                    // Left-click: cursor slot only
                    if (isContainer) {
                        if (recursive) {
                            deliverFromContainerItem(session, currentCursor, true, 1);
                            player.setItemOnCursor(currentCursor);
                        }
                    } else {
                        deliverCursorStack(session, player, currentCursor);
                    }
                } else {
                    // Right-click: matching in inventory AND cursor
                    if (isContainer) {
                        Set<Material> materials = getContainedMaterials(currentCursor);
                        if (!materials.isEmpty()) {
                            deliverMatchingMaterials(session, clickedInv, materials, true);
                            if (clickedInv != player.getInventory()) {
                                deliverMatchingMaterials(session, player.getInventory(), materials, true);
                            }
                        }
                        if (recursive) {
                            deliverFromContainerItem(session, currentCursor, true, 1);
                            player.setItemOnCursor(currentCursor);
                        }
                    } else {
                        deliverMatchingSimilar(session, clickedInv, targetCursor, recursive);
                        if (clickedInv != player.getInventory()) {
                            deliverMatchingSimilar(session, player.getInventory(), targetCursor, recursive);
                        }
                        deliverCursorStack(session, player, currentCursor);
                    }
                }

                List<DeliverySummary> deliveries = session.finish();
                player.updateInventory();
                sendOrderFeedback(player, deliveries, useNativeMessages);
            }, null);
        });
    }

    // -------------------------------------------------------------------------
    // Core Restocking Algorithms & Session
    // -------------------------------------------------------------------------

    private final class RestockSession {
        private final Player player;
        private final List<Listing> candidateListings;
        private final BasicBazaarPlugin bazaar;
        private final Map<UUID, Listing> lockedListings = new LinkedHashMap<>();
        private final Set<UUID> failedListings = new java.util.HashSet<>();
        private final Map<Listing, Integer> restocked = new LinkedHashMap<>();

        RestockSession(Player player, List<Listing> candidateListings, BasicBazaarPlugin bazaar) {
            this.player = player;
            this.candidateListings = candidateListings;
            this.bazaar = bazaar;
        }

        public int restockStack(ItemStack stack) {
            if (isAirOrEmpty(stack) || isShopTool(stack)) return 0;

            Listing matched = null;
            for (Listing l : candidateListings) {
                if (l.getItem() != null && stack.isSimilar(l.getItem())) {
                    matched = l;
                    break;
                }
            }
            if (matched == null) return 0;

            UUID id = matched.getId();
            if (failedListings.contains(id)) return 0;

            if (!lockedListings.containsKey(id)) {
                if (!bazaar.acquireListingLock(id)) {
                    failedListings.add(id);
                    return 0;
                }
                lockedListings.put(id, matched);
            }

            int requested = stack.getAmount();
            BazaarListingStockAddEvent event = new BazaarListingStockAddEvent(player, matched, requested);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return 0;
            }

            int toAdd = Math.min(requested, event.getAmount());
            if (toAdd <= 0) {
                return 0;
            }

            restocked.merge(matched, toAdd, Integer::sum);
            return toAdd;
        }

        public Map<Listing, Integer> finish() {
            boolean activateOnStockAdd = bazaar.getConfig().getBoolean("settings.activate-on-stock-add", true);

            for (Map.Entry<UUID, Listing> entry : lockedListings.entrySet()) {
                UUID id = entry.getKey();
                Listing listing = entry.getValue();
                int added = restocked.getOrDefault(listing, 0);

                if (added > 0) {
                    listing.setStock(listing.getStock() + added);
                    if (activateOnStockAdd && (listing.isExpired() || listing.getExpiryTime() > 0)) {
                        listing.setExpiryTime(bazaar.calculateListingExpiryTime());
                    }

                    bazaar.getStorage().updateListing(listing).whenComplete((v, ex) -> {
                        bazaar.releaseListingLock(id);
                    });
                } else {
                    bazaar.releaseListingLock(id);
                }
            }
            return restocked;
        }
    }

    private void restockInventory(
            RestockSession session,
            Inventory inv,
            boolean recursive,
            int depth
    ) {
        if (inv == null) return;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isAirOrEmpty(stack) || isShopTool(stack)) continue;

            if (isContainerItem(stack)) {
                if (recursive && depth <= 5) {
                    boolean modified = restockContainerItem(session, stack, recursive, depth + 1);
                    if (modified) {
                        inv.setItem(i, stack);
                    }
                }
                continue;
            }

            restockSingleSlot(session, inv, i, stack);
        }
    }

    private void restockMatchingSimilar(
            RestockSession session,
            Inventory inv,
            ItemStack sample,
            boolean recursive
    ) {
        if (inv == null || sample == null || sample.isEmpty()) return;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isAirOrEmpty(stack) || isShopTool(stack)) continue;

            if (isContainerItem(stack)) {
                if (recursive) {
                    boolean modified = restockContainerItemSimilar(session, stack, sample, 1);
                    if (modified) {
                        inv.setItem(i, stack);
                    }
                }
                continue;
            }

            if (stack.isSimilar(sample)) {
                restockSingleSlot(session, inv, i, stack);
            }
        }
    }

    private void restockMatchingMaterials(
            RestockSession session,
            Inventory inv,
            Set<Material> materials,
            boolean recursive
    ) {
        if (inv == null || materials == null || materials.isEmpty()) return;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isAirOrEmpty(stack) || isShopTool(stack)) continue;

            if (isContainerItem(stack)) {
                if (recursive) {
                    boolean modified = restockContainerItem(session, stack, true, 1);
                    if (modified) {
                        inv.setItem(i, stack);
                    }
                }
                continue;
            }

            if (materials.contains(stack.getType())) {
                restockSingleSlot(session, inv, i, stack);
            }
        }
    }

    private boolean restockContainerItemSimilar(
            RestockSession session,
            ItemStack containerStack,
            ItemStack sample,
            int depth
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
                boolean subModified = restockContainerItemSimilar(session, innerStack, sample, depth + 1);
                if (subModified) {
                    inner.setItem(i, innerStack);
                    anyModified = true;
                }
                continue;
            }

            if (innerStack.isSimilar(sample)) {
                int added = session.restockStack(innerStack);
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
        }

        if (anyModified) {
            bsm.setBlockState(container);
            containerStack.setItemMeta(bsm);
        }
        return anyModified;
    }

    private boolean restockContainerItem(
            RestockSession session,
            ItemStack containerStack,
            boolean recursive,
            int depth
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
                    boolean subModified = restockContainerItem(session, innerStack, recursive, depth + 1);
                    if (subModified) {
                        inner.setItem(i, innerStack);
                        anyModified = true;
                    }
                }
                continue;
            }

            int added = session.restockStack(innerStack);
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
            RestockSession session,
            Inventory inv,
            int slot,
            ItemStack stack
    ) {
        int added = session.restockStack(stack);
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
            RestockSession session,
            Player player,
            ItemStack cursorStack
    ) {
        int added = session.restockStack(cursorStack);
        if (added > 0) {
            if (added >= cursorStack.getAmount()) {
                player.setItemOnCursor(null);
            } else {
                cursorStack.setAmount(cursorStack.getAmount() - added);
                player.setItemOnCursor(cursorStack);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Core Order Delivery Algorithms & Session
    // -------------------------------------------------------------------------

    public record DeliverySummary(Order order, int amount, double earnings) {}

    private final class DeliverySession {
        private final Player player;
        private final List<Order> orders;
        private final BasicBazaarPlugin bazaar;
        private final MorePaperLib morePaperLib;
        private final Map<UUID, Order> lockedOrders = new LinkedHashMap<>();
        private final Set<UUID> failedOrders = new java.util.HashSet<>();
        private final Map<Order, Integer> deliveredPerOrder = new LinkedHashMap<>();
        private final Map<Order, Double> earningsPerOrder = new LinkedHashMap<>();

        DeliverySession(Player player, List<Order> orders, BasicBazaarPlugin bazaar, MorePaperLib morePaperLib) {
            this.player = player;
            this.orders = orders;
            this.bazaar = bazaar;
            this.morePaperLib = morePaperLib;
        }

        public int deliverStack(ItemStack stack) {
            if (isAirOrEmpty(stack) || isShopTool(stack)) return 0;
            int totalDeliveredFromStack = 0;

            for (Order order : orders) {
                if (stack.getAmount() <= 0) break;
                if (!order.canFulfill()) continue;

                int alreadyDelivered = deliveredPerOrder.getOrDefault(order, 0);
                int remaining = order.getRemainingAmount() - alreadyDelivered;
                if (remaining <= 0) continue;

                if (!OrderNormalizationUtil.matchesOrderDelivery(stack, order.getItem())) continue;

                UUID id = order.getId();
                if (failedOrders.contains(id)) continue;

                if (!lockedOrders.containsKey(id)) {
                    if (!bazaar.acquireOrderLock(id)) {
                        failedOrders.add(id);
                        continue;
                    }
                    lockedOrders.put(id, order);
                }

                int toDeliver = Math.min(stack.getAmount(), remaining);
                if (toDeliver <= 0) continue;

                double initialEarnings = toDeliver * order.getPrice();
                BazaarOrderDeliverEvent deliverEvent = new BazaarOrderDeliverEvent(player, order, toDeliver, initialEarnings);
                Bukkit.getPluginManager().callEvent(deliverEvent);
                if (deliverEvent.isCancelled()) continue;

                double finalEarnings = deliverEvent.getTotalEarnings();

                deliveredPerOrder.merge(order, toDeliver, Integer::sum);
                earningsPerOrder.merge(order, finalEarnings, Double::sum);

                stack.setAmount(stack.getAmount() - toDeliver);
                totalDeliveredFromStack += toDeliver;
            }

            return totalDeliveredFromStack;
        }

        public List<DeliverySummary> finish() {
            List<DeliverySummary> summaries = new ArrayList<>();
            long now = System.currentTimeMillis();

            for (Map.Entry<UUID, Order> entry : lockedOrders.entrySet()) {
                UUID id = entry.getKey();
                Order order = entry.getValue();
                int totalDelivered = deliveredPerOrder.getOrDefault(order, 0);
                double totalEarnings = earningsPerOrder.getOrDefault(order, 0.0);

                if (totalDelivered > 0) {
                    summaries.add(new DeliverySummary(order, totalDelivered, totalEarnings));
                    order.setFulfilledAmount(order.getFulfilledAmount() + totalDelivered);

                    bazaar.getStorage().tryFulfillOrder(id, totalDelivered, now).whenComplete((updated, fulfillEx) -> {
                        bazaar.releaseOrderLock(id);
                        if (fulfillEx != null || updated == null) {
                            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                                ItemStack refund = OrderNormalizationUtil.cleanItem(order.getItem()).clone();
                                refund.setAmount(totalDelivered);
                                var leftover = player.getInventory().addItem(refund);
                                for (ItemStack drop : leftover.values()) {
                                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                }
                            }, null);
                            return;
                        }

                        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
                            bazaar.getEconomyManager().deposit(player.getUniqueId(), totalEarnings);

                            BazaarOrderDeliveredEvent deliveredEvent = new BazaarOrderDeliveredEvent(player, order, totalDelivered, totalEarnings);
                            Bukkit.getPluginManager().callEvent(deliveredEvent);

                            bazaar.getNotificationManager().notifyBuyerOrderDelivery(order.getBuyer(), order, totalDelivered, totalEarnings, player.getName());

                            ItemStack logItem = order.getItem().clone();
                            logItem.setAmount(totalDelivered);
                            bazaar.getTransactionLogger().logTransaction(
                                    player.getUniqueId(), player.getName(),
                                    "order_delivered", logItem,
                                    order.getBuyer(), order.getBuyerName(),
                                    totalEarnings
                            );
                        }, null);
                    });
                } else {
                    bazaar.releaseOrderLock(id);
                }
            }

            return summaries;
        }
    }

    private void deliverInventory(
            DeliverySession session,
            Inventory inv,
            boolean recursive,
            int depth
    ) {
        if (inv == null) return;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isAirOrEmpty(stack) || isShopTool(stack)) continue;

            if (isContainerItem(stack)) {
                if (recursive && depth <= 5) {
                    boolean modified = deliverFromContainerItem(session, stack, recursive, depth + 1);
                    if (modified) {
                        inv.setItem(i, stack);
                    }
                }
                continue;
            }

            deliverSingleSlot(session, inv, i, stack);
        }
    }

    private void deliverMatchingSimilar(
            DeliverySession session,
            Inventory inv,
            ItemStack sample,
            boolean recursive
    ) {
        if (inv == null || sample == null || sample.isEmpty()) return;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isAirOrEmpty(stack) || isShopTool(stack)) continue;

            if (isContainerItem(stack)) {
                if (recursive) {
                    boolean modified = deliverFromContainerItemSimilar(session, stack, sample, 1);
                    if (modified) {
                        inv.setItem(i, stack);
                    }
                }
                continue;
            }

            if (stack.isSimilar(sample)) {
                deliverSingleSlot(session, inv, i, stack);
            }
        }
    }

    private void deliverMatchingMaterials(
            DeliverySession session,
            Inventory inv,
            Set<Material> materials,
            boolean recursive
    ) {
        if (inv == null || materials == null || materials.isEmpty()) return;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isAirOrEmpty(stack) || isShopTool(stack)) continue;

            if (isContainerItem(stack)) {
                if (recursive) {
                    boolean modified = deliverFromContainerItem(session, stack, true, 1);
                    if (modified) {
                        inv.setItem(i, stack);
                    }
                }
                continue;
            }

            if (materials.contains(stack.getType())) {
                deliverSingleSlot(session, inv, i, stack);
            }
        }
    }

    private boolean deliverFromContainerItemSimilar(
            DeliverySession session,
            ItemStack containerStack,
            ItemStack sample,
            int depth
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
                boolean subModified = deliverFromContainerItemSimilar(session, innerStack, sample, depth + 1);
                if (subModified) {
                    inner.setItem(i, innerStack);
                    anyModified = true;
                }
                continue;
            }

            if (innerStack.isSimilar(sample)) {
                int delivered = session.deliverStack(innerStack);
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
        }

        if (anyModified) {
            bsm.setBlockState(container);
            containerStack.setItemMeta(bsm);
        }
        return anyModified;
    }

    private boolean deliverFromContainerItem(
            DeliverySession session,
            ItemStack containerStack,
            boolean recursive,
            int depth
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
                    boolean subModified = deliverFromContainerItem(session, innerStack, recursive, depth + 1);
                    if (subModified) {
                        inner.setItem(i, innerStack);
                        anyModified = true;
                    }
                }
                continue;
            }

            int delivered = session.deliverStack(innerStack);
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
            DeliverySession session,
            Inventory inv,
            int slot,
            ItemStack stack
    ) {
        int delivered = session.deliverStack(stack);
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
            DeliverySession session,
            Player player,
            ItemStack cursorStack
    ) {
        int delivered = session.deliverStack(cursorStack);
        if (delivered > 0) {
            if (delivered >= cursorStack.getAmount()) {
                player.setItemOnCursor(null);
            } else {
                cursorStack.setAmount(cursorStack.getAmount() - delivered);
                player.setItemOnCursor(cursorStack);
            }
        }
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
                        Placeholder.component("item", bazaar.getItemAsComponent(listing.getItem(), amount)),
                        Placeholder.parsed("amount", String.valueOf(amount)),
                        Placeholder.parsed("stock", String.valueOf(listing.getStock())),
                        Placeholder.parsed("price", bazaar.getMoneyFormatter().format(listing.getPrice())),
                        Placeholder.parsed("unit_price", bazaar.getMoneyFormatter().format(listing.getPrice())),
                        Placeholder.parsed("total", bazaar.getMoneyFormatter().format(listing.getPrice() * amount)),
                        bazaar.getMiniMessageService().playerComponent("seller", player.getUniqueId(), player.getName()));
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
                        Placeholder.parsed("price", bazaar.getMoneyFormatter().format(earnings)),
                        Placeholder.parsed("total", bazaar.getMoneyFormatter().format(earnings)),
                        Placeholder.parsed("unit_price", bazaar.getMoneyFormatter().format(order.getPrice())),
                        bazaar.getMiniMessageService().playerComponent("seller", player.getUniqueId(), player.getName()));
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
