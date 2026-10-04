package me.usainsrht.basicshop.listener;

import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.ShopAPIImpl;
import me.usainsrht.basicshop.api.event.MoneyHoeHarvestEvent;
import me.usainsrht.basicshop.api.event.MoneyHoeToggleEvent;
import me.usainsrht.basicshop.api.event.MoneyStaffCursorSellEvent;
import me.usainsrht.basicshop.api.event.MoneyStaffToggleEvent;
import me.usainsrht.basicshop.api.event.MoneyStaffUseEvent;
import me.usainsrht.basicshop.api.event.SortingStaffUseEvent;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.ShopToolType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.ToolsConfig;
import me.usainsrht.basicshop.item.ShopToolFactory;
import org.bukkit.Bukkit;
import me.usainsrht.itemapi.itemtext.ItemText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import space.arim.morepaperlib.MorePaperLib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Money Staff and Money Hoe interactions.
 *
 * <p>
 * Restrictions run at HIGH priority; sell/replant logic runs at MONITOR and
 * intentionally observes cancelled hoe breaks.
 */
public final class ToolListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /**
     * Drop types that cost one item to replant when auto-selling (replaces legacy
     * name substring checks).
     */
    private static final Set<Material> REPLANT_COST_DROPS = Set.of(
            Material.WHEAT_SEEDS,
            Material.BEETROOT_SEEDS,
            Material.MELON_SEEDS,
            Material.PUMPKIN_SEEDS,
            Material.NETHER_WART,
            Material.POTATO,
            Material.CARROT);

    /**
     * Replantable farm crops handled by the money hoe. Not every {@link Ageable}
     * block is a crop
     * (e.g. cactus, sugar cane, and bamboo also implement Ageable but should break
     * normally).
     */
    private static final Set<Material> MONEY_HOE_CROPS = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.MELON_STEM,
            Material.PUMPKIN_STEM,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH,
            Material.TORCHFLOWER_CROP,
            Material.PITCHER_CROP);

    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final ShopToolFactory toolFactory;
    private final MorePaperLib morePaperLib;
    private final Map<UUID, Long> cursorCooldowns = new ConcurrentHashMap<>();

    public ToolListener(
            ConfigManager configManager,
            ShopAPI shopAPI,
            ShopToolFactory toolFactory,
            MorePaperLib morePaperLib) {
        this.configManager = configManager;
        this.shopAPI = shopAPI;
        this.toolFactory = toolFactory;
        this.morePaperLib = morePaperLib;
    }

    private boolean isRidingRestricted(Player player, ShopToolType type) {
        if (!player.isInsideVehicle()) {
            return false;
        }
        ToolsConfig toolsConfig = configManager.getToolsConfig();
        return toolsConfig != null && !toolsConfig.isUsableWhenRiding(type);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHoeBreakRestriction(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (toolFactory.getToolType(tool) != ShopToolType.MONEY_HOE)
            return;
        if (isRidingRestricted(player, ShopToolType.MONEY_HOE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onStaffInteractRestriction(PlayerInteractEvent event) {
        if (!event.hasItem())
            return;
        ItemStack item = event.getItem();
        ShopToolType type = toolFactory.getToolType(item);
        if (type == ShopToolType.MONEY_STAFF || type == ShopToolType.SORTING_STAFF) {
            if (isRidingRestricted(event.getPlayer(), type)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ShopToolType type = toolFactory.getToolType(event.getItemInHand());
        // hoe tilling also fires blockplaceevent
        if (type == ShopToolType.MONEY_STAFF || type == ShopToolType.SORTING_STAFF) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStaffUse(PlayerInteractEvent event) {
        if (!event.hasBlock() || !event.hasItem())
            return;
        if (!event.getAction().isRightClick())
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        ItemStack item = event.getItem();
        if (toolFactory.getToolType(item) != ShopToolType.MONEY_STAFF)
            return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (isRidingRestricted(player, ShopToolType.MONEY_STAFF))
            return;
        if (!player.hasPermission("basicshop.tools.staff"))
            return;
        toolFactory.ensureUseCooldown(item, ShopToolType.MONEY_STAFF);
        if (player.getCooldown(ShopToolType.MONEY_STAFF.getCooldownKey()) > 0)
            return;

        Block block = event.getClickedBlock();
        if (block == null)
            return;

        BlockState state = block.getState();
        if (!(state instanceof Container container))
            return;

        MoneyStaffUseEvent staffEvent = new MoneyStaffUseEvent(player, item, block, container);
        Bukkit.getPluginManager().callEvent(staffEvent);
        if (staffEvent.isCancelled())
            return;

        toolFactory.applyCooldown(player, item, ShopToolType.MONEY_STAFF);

        boolean recursive = toolFactory.isRecursiveSellEnabled(item);
        Location location = block.getLocation();
        morePaperLib.scheduling().regionSpecificScheduler(location).runDelayed(
                () -> sellItemsInBlock(player, block, recursive),
                1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSortingStaffUse(PlayerInteractEvent event) {
        if (!event.hasBlock() || !event.hasItem())
            return;
        if (!event.getAction().isRightClick())
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        ItemStack item = event.getItem();
        if (toolFactory.getToolType(item) != ShopToolType.SORTING_STAFF)
            return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (isRidingRestricted(player, ShopToolType.SORTING_STAFF))
            return;
        if (!player.hasPermission("basicshop.tools.sorting_staff"))
            return;
        toolFactory.ensureUseCooldown(item, ShopToolType.SORTING_STAFF);
        if (player.getCooldown(ShopToolType.SORTING_STAFF.getCooldownKey()) > 0)
            return;

        Block block = event.getClickedBlock();
        if (block == null)
            return;

        BlockState state = block.getState();
        if (!(state instanceof Container container))
            return;

        SortingStaffUseEvent sortEvent = new SortingStaffUseEvent(player, item, block, container);
        Bukkit.getPluginManager().callEvent(sortEvent);
        if (sortEvent.isCancelled())
            return;

        toolFactory.applyCooldown(player, item, ShopToolType.SORTING_STAFF);

        Location location = block.getLocation();
        morePaperLib.scheduling().regionSpecificScheduler(location).run(() -> {
            if (!player.isOnline())
                return;
            BlockState currentState = block.getState();
            if (currentState instanceof Container currentContainer) {
                boolean sorted = me.usainsrht.basicshop.sorting.ContainerSorter.sortContainer(player, currentContainer);
                if (sorted) {
                    configManager.getMessagesConfig().send(player, "tool-sorting-staff-success");
                }
            }
        });
    }

    private void sellItemsInBlock(Player player, Block block, boolean recursive) {
        if (!player.isOnline())
            return;

        BlockState state = block.getState();
        if (!(state instanceof Container container))
            return;

        ShopAPI.QuickSellResult result = shopAPI.sellFromInventory(player, container.getInventory(), recursive);
        if (!result.anySuccess())
            return;

        for (ShopAPI.SoldMaterialLine line : result.lines()) {
            ItemStack lineStack = new ItemStack(line.material(), 1);
            Component itemTextComp = ItemText.format(lineStack, b -> b.amount(line.amount()));

            configManager.getMessagesConfig().send(player, "tool-staff-line",
                    Placeholder.unparsed("amount", String.valueOf(line.amount())),
                    Placeholder.component("item", itemTextComp),
                    Placeholder.unparsed("price", configManager.getMainConfig().formatPrice(line.earned())));
        }
    }

    /**
     * Paper fires {@link PlayerInteractEvent} as cancelled for air clicks where
     * vanilla does nothing (e.g. right-click air with a staff), so
     * {@code ignoreCancelled}
     * must be false.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onStaffToggle(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            item = event.getPlayer().getInventory().getItemInMainHand();
        }
        if (toolFactory.getToolType(item) != ShopToolType.MONEY_STAFF)
            return;

        Player player = event.getPlayer();
        if (isRidingRestricted(player, ShopToolType.MONEY_STAFF))
            return;
        if (!player.hasPermission("basicshop.tools.staff"))
            return;
        toolFactory.ensureUseCooldown(item, ShopToolType.MONEY_STAFF);
        if (player.getCooldown(ShopToolType.MONEY_STAFF.getCooldownKey()) > 0)
            return;

        boolean targetRecursive = !toolFactory.isRecursiveSellEnabled(item);
        MoneyStaffToggleEvent toggleEvent = new MoneyStaffToggleEvent(player, item, targetRecursive);
        Bukkit.getPluginManager().callEvent(toggleEvent);
        if (toggleEvent.isCancelled())
            return;

        boolean recursiveEnabled = toggleEvent.isNewRecursiveState();
        toolFactory.setRecursiveSellEnabled(item, recursiveEnabled);
        player.getInventory().setItemInMainHand(item);

        toolFactory.applyCooldown(player, item, ShopToolType.MONEY_STAFF);

        String key = recursiveEnabled ? "tool-staff-recursive-on" : "tool-staff-recursive-off";
        configManager.getMessagesConfig().send(player, key);
    }

    /**
     * Paper fires {@link PlayerInteractEvent} as cancelled for air clicks where
     * vanilla does
     * nothing (e.g. right-click air with a hoe), so {@code ignoreCancelled} must be
     * false.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onHoeToggle(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            item = event.getPlayer().getInventory().getItemInMainHand();
        }
        if (toolFactory.getToolType(item) != ShopToolType.MONEY_HOE)
            return;

        Player player = event.getPlayer();
        if (isRidingRestricted(player, ShopToolType.MONEY_HOE))
            return;
        if (!player.hasPermission("basicshop.tools.hoe"))
            return;
        toolFactory.ensureUseCooldown(item, ShopToolType.MONEY_HOE);
        if (player.getCooldown(ShopToolType.MONEY_HOE.getCooldownKey()) > 0)
            return;

        boolean targetAutoSell = !toolFactory.isAutoSellEnabled(item);
        MoneyHoeToggleEvent toggleEvent = new MoneyHoeToggleEvent(player, item, targetAutoSell);
        Bukkit.getPluginManager().callEvent(toggleEvent);
        if (toggleEvent.isCancelled())
            return;

        boolean autoSellEnabled = toggleEvent.isNewAutoSellState();
        toolFactory.setAutoSellEnabled(item, autoSellEnabled);
        player.getInventory().setItemInMainHand(item);

        toolFactory.applyCooldown(player, item, ShopToolType.MONEY_HOE);

        String key = autoSellEnabled ? "tool-hoe-autosell-on" : "tool-hoe-autosell-off";
        configManager.getMessagesConfig().send(player, key);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHoeBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (toolFactory.getToolType(tool) != ShopToolType.MONEY_HOE)
            return;
        if (!player.hasPermission("basicshop.tools.hoe"))
            return;

        Block block = event.getBlock();
        if (!MONEY_HOE_CROPS.contains(block.getType()))
            return;
        if (!isFullyGrown(block)) {
            event.setCancelled(true);
            return;
        }

        Collection<ItemStack> drops = new ArrayList<>(block.getDrops(tool));
        boolean autoSellEnabled = toolFactory.isAutoSellEnabled(tool);

        MoneyHoeHarvestEvent harvestEvent = new MoneyHoeHarvestEvent(player, tool, block, autoSellEnabled, drops);
        Bukkit.getPluginManager().callEvent(harvestEvent);
        if (harvestEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        event.setDropItems(false);

        if (harvestEvent.isAutoSell()) {
            handleAutoSell(player, block, harvestEvent.getDrops());
        } else {
            giveDrops(player, block, harvestEvent.getDrops());
        }

        scheduleReplant(block);
    }

    private void handleAutoSell(Player player, Block block, Collection<ItemStack> drops) {
        List<ItemStack> sellable = new ArrayList<>();
        List<ItemStack> unsellable = new ArrayList<>();
        double totalEarned = 0;
        int totalSold = 0;

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir())
                continue;

            ItemStack adjusted = drop.clone();
            applyReplantCost(adjusted);
            if (adjusted.getAmount() <= 0)
                continue;

            Optional<ShopItem> shopItemOpt = shopAPI.getItemByMaterial(adjusted.getType());
            if (shopItemOpt.isEmpty()) {
                unsellable.add(adjusted);
                continue;
            }

            OptionalDouble priceOpt = shopItemOpt.get().getSellPrice();
            if (priceOpt.isEmpty()) {
                unsellable.add(adjusted);
                continue;
            }

            totalEarned += priceOpt.getAsDouble() * adjusted.getAmount();
            totalSold += adjusted.getAmount();
            sellable.add(adjusted);
        }

        if (totalSold > 0) {
            shopAPI.sellItemStacks(player, sellable);
            configManager.getMessagesConfig().send(player, "tool-hoe-success",
                    Placeholder.unparsed("price", configManager.getMainConfig().formatPrice(totalEarned)));
        }

        for (ItemStack leftover : unsellable) {
            block.getWorld().dropItemNaturally(block.getLocation(), leftover);
        }
    }

    private static void giveDrops(Player player, Block block, Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir())
                continue;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop.clone());
            for (ItemStack overflow : leftover.values()) {
                block.getWorld().dropItemNaturally(block.getLocation(), overflow);
            }
        }
    }

    private static void applyReplantCost(ItemStack drop) {
        if (!REPLANT_COST_DROPS.contains(drop.getType()))
            return;
        drop.setAmount(drop.getAmount() - 1);
    }

    private static boolean isFullyGrown(Block block) {
        // allow if age is 1 below maximum age
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge() - 1;
        }
        return false;
    }

    private void scheduleReplant(Block block) {
        Location location = block.getLocation();
        Material cropType = block.getType();
        BlockData replantData = block.getBlockData();

        if (replantData instanceof Ageable ageable) {
            ageable.setAge(0);
            replantData = ageable;
        }

        BlockData finalData = replantData;
        morePaperLib.scheduling().regionSpecificScheduler(location).run(() -> {
            Block replantBlock = location.getBlock();
            if (replantBlock.getType() != Material.AIR && replantBlock.getType() != cropType)
                return;
            replantBlock.setType(cropType, false);
            replantBlock.setBlockData(finalData, false);
        });
    }

    private boolean isRealTopInventory(Inventory topInv) {
        if (topInv == null)
            return false;
        InventoryType type = topInv.getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.ENDER_CHEST) {
            return true;
        }
        InventoryHolder holder = topInv.getHolder();
        if (holder instanceof BlockInventoryHolder || holder instanceof DoubleChest) {
            return true;
        }
        if (holder instanceof org.bukkit.entity.Entity) {
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStaffCursorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        ItemStack cursor = event.getCursor();
        if (toolFactory.getToolType(cursor) != ShopToolType.MONEY_STAFF)
            return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null)
            return;

        Inventory topInv = event.getView().getTopInventory();
        if (!isRealTopInventory(topInv))
            return;

        // Disallow selling from the 2x2 crafting grid or crafting result slot
        if (clickedInv.getType() == InventoryType.CRAFTING)
            return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getAmount() <= 0 || current.isEmpty())
            return;

        ItemStack staff = cursor.clone();

        // Cancel the click event immediately so vanilla never swaps or moves items
        event.setCancelled(true);

        if (toolFactory.isShopTool(current)) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> restoreCursor(player, staff), null);
            return;
        }

        if (isRidingRestricted(player, ShopToolType.MONEY_STAFF)) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> restoreCursor(player, staff), null);
            return;
        }
        if (!player.hasPermission("basicshop.tools.staff")) {
            configManager.getMessagesConfig().send(player, "no-permission");
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> restoreCursor(player, staff), null);
            return;
        }

        long now = System.currentTimeMillis();
        double cdSeconds = configManager.getToolsConfig().getCursorCooldownSeconds(ShopToolType.MONEY_STAFF);
        long cdMillis = Math.round(cdSeconds * 1000.0);
        Long lastClick = cursorCooldowns.get(player.getUniqueId());
        if (lastClick != null && (now - lastClick) < cdMillis) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> restoreCursor(player, staff), null);
            return;
        }
        cursorCooldowns.put(player.getUniqueId(), now);

        ClickType clickType = event.getClick();
        if (!clickType.isLeftClick() && !clickType.isRightClick()) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> restoreCursor(player, staff), null);
            return;
        }

        MoneyStaffCursorSellEvent cursorEvent = new MoneyStaffCursorSellEvent(
                player, cursor, clickedInv, event.getSlot(), clickType, current, true);
        Bukkit.getPluginManager().callEvent(cursorEvent);
        if (cursorEvent.isCancelled()) {
            morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> restoreCursor(player, staff), null);
            return;
        }

        int slot = event.getSlot();
        ItemStack targetItem = current.clone();
        boolean isContainer = ShopAPIImpl.isContainerItem(current);

        // Defer inventory mutation to the next tick so the cancellation completes cleanly
        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            executeDeferredCursorSell(player, clickedInv, slot, clickType, targetItem, isContainer, staff);
        }, null);
    }

    private void executeDeferredCursorSell(
            Player player,
            Inventory clickedInv,
            int slot,
            ClickType clickType,
            ItemStack targetItem,
            boolean isContainer,
            ItemStack staff
    ) {
        if (!player.isOnline())
            return;

        // If it's a world block container, verify the block is still valid (not destroyed by TNT/mining)
        if (clickedInv.getHolder() instanceof BlockInventoryHolder bih) {
            if (!(bih.getBlock().getState() instanceof Container)) {
                restoreCursor(player, staff);
                return;
            }
        } else if (clickedInv.getHolder() instanceof DoubleChest dc) {
            if (!(dc.getLocation().getBlock().getState() instanceof Container)) {
                restoreCursor(player, staff);
                return;
            }
        }

        ItemStack current = clickedInv.getItem(slot);
        if (current == null || current.isEmpty() || !current.isSimilar(targetItem)) {
            restoreCursor(player, staff);
            return;
        }

        ShopAPI.QuickSellResult result;

        if (clickType.isLeftClick()) {
            result = shopAPI.sellSlot(player, clickedInv, slot, isContainer);
        } else {
            if (isContainer) {
                Set<Material> materials = ShopAPIImpl.getContainedMaterials(current);
                if (materials.isEmpty()) {
                    restoreCursor(player, staff);
                    configManager.getMessagesConfig().send(player, "tool-staff-no-items");
                    return;
                }
                result = shopAPI.sellMatchingItems(player, clickedInv, materials, true);
            } else {
                result = shopAPI.sellMatchingItems(player, clickedInv, Set.of(targetItem.getType()), false);
            }
        }

        // Always restore the Money Staff onto the player's cursor and force inventory resync
        restoreCursor(player, staff);

        if (!result.anySuccess()) {
            configManager.getMessagesConfig().send(player, "tool-staff-no-items");
            return;
        }

        for (ShopAPI.SoldMaterialLine line : result.lines()) {
            ItemStack lineStack = new ItemStack(line.material(), 1);
            Component itemTextComp = ItemText.format(lineStack, b -> b.amount(line.amount()));

            configManager.getMessagesConfig().send(player, "tool-staff-line",
                    Placeholder.unparsed("amount", String.valueOf(line.amount())),
                    Placeholder.component("item", itemTextComp),
                    Placeholder.unparsed("price", configManager.getMainConfig().formatPrice(line.earned())));
        }
    }

    private void restoreCursor(Player player, ItemStack staff) {
        if (!player.isOnline())
            return;
        player.setItemOnCursor(staff);
        try {
            if (player.getOpenInventory() != null) {
                player.getOpenInventory().setCursor(staff);
            }
        } catch (Throwable ignored) {
        }
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        if (player.getGameMode() != GameMode.CREATIVE)
            return;

        ItemStack cursor = event.getView().getCursor();
        ShopToolType toolType = toolFactory.getToolType(cursor);
        if (toolType == null)
            return;

        // In Creative mode, picking up an item clones it to the cursor while leaving the
        // original item in the inventory slot. When closing the inventory, vanilla would
        // then deposit the carried clone into the first empty slot, duplicating the tool.
        // If the player's inventory already contains this tool, clear the cursor to prevent duplication.
        for (ItemStack item : player.getInventory().getContents()) {
            if (toolFactory.getToolType(item) == toolType) {
                try {
                    event.getView().setCursor(null);
                } catch (Throwable ignored) {
                }
                player.setItemOnCursor(null);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        cursorCooldowns.remove(event.getPlayer().getUniqueId());
    }
}
