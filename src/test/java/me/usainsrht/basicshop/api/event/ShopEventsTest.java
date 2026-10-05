package me.usainsrht.basicshop.api.event;

import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.ShopAPIImpl;
import me.usainsrht.basicshop.api.economy.EconomyProvider;
import me.usainsrht.basicshop.api.model.ShopCategory;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.TransactionRecord;
import me.usainsrht.basicshop.api.model.TransactionResult;
import me.usainsrht.basicshop.api.model.TransactionType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.MainConfig;
import me.usainsrht.basicshop.config.ToolsConfig;
import me.usainsrht.basicshop.item.ShopToolFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ShopEventsTest {

    private Server server;
    private PluginManager pluginManager;

    @BeforeEach
    public void setUp() throws Exception {
        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
    }

    @AfterEach
    public void tearDown() throws Exception {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (NoSuchFieldException ignored) {
        }
    }

    @Test
    public void testAllEventsHaveValidHandlerLists() {
        Player player = mock(Player.class);
        ShopItem item = mock(ShopItem.class);
        ShopCategory category = mock(ShopCategory.class);
        Block block = mock(Block.class);
        Container container = mock(Container.class);
        ItemStack tool = mock(ItemStack.class);
        CommandSender sender = mock(CommandSender.class);

        ShopPreTransactionEvent preTx = new ShopPreTransactionEvent(player, item, category, TransactionType.BUY, 5, 50.0);
        assertNotNull(preTx.getHandlers());
        assertSame(preTx.getHandlers(), ShopPreTransactionEvent.getHandlerList());

        TransactionRecord record = new TransactionRecord(UUID.randomUUID(), "Test", "diamond", "ores", TransactionType.BUY, 5, 50.0, Instant.now());
        ShopPostTransactionEvent postTx = new ShopPostTransactionEvent(player, item, category, TransactionType.BUY, 5, 50.0, record);
        assertNotNull(postTx.getHandlers());
        assertSame(postTx.getHandlers(), ShopPostTransactionEvent.getHandlerList());

        ShopPreBulkSellEvent preBulk = new ShopPreBulkSellEvent(player, BulkSellSource.QUICK_SELL_INVENTORY, null);
        assertNotNull(preBulk.getHandlers());
        assertSame(preBulk.getHandlers(), ShopPreBulkSellEvent.getHandlerList());

        ShopBulkSellEvent bulk = new ShopBulkSellEvent(player, BulkSellSource.QUICK_SELL_INVENTORY, 10, 100.0, List.of());
        assertNotNull(bulk.getHandlers());
        assertSame(bulk.getHandlers(), ShopBulkSellEvent.getHandlerList());

        ShopOpenEvent open = new ShopOpenEvent(player, ShopViewType.CATEGORIES, null);
        assertNotNull(open.getHandlers());
        assertSame(open.getHandlers(), ShopOpenEvent.getHandlerList());

        ShopReloadEvent reload = new ShopReloadEvent(sender);
        assertNotNull(reload.getHandlers());
        assertSame(reload.getHandlers(), ShopReloadEvent.getHandlerList());

        MoneyStaffUseEvent staff = new MoneyStaffUseEvent(player, tool, block, container);
        assertNotNull(staff.getHandlers());
        assertSame(staff.getHandlers(), MoneyStaffUseEvent.getHandlerList());

        SortingStaffUseEvent sort = new SortingStaffUseEvent(player, tool, block, container);
        assertNotNull(sort.getHandlers());
        assertSame(sort.getHandlers(), SortingStaffUseEvent.getHandlerList());

        MoneyHoeHarvestEvent hoeHarvest = new MoneyHoeHarvestEvent(player, tool, block, true, new ArrayList<>());
        assertNotNull(hoeHarvest.getHandlers());
        assertSame(hoeHarvest.getHandlers(), MoneyHoeHarvestEvent.getHandlerList());

        MoneyHoeToggleEvent hoeToggle = new MoneyHoeToggleEvent(player, tool, true);
        assertNotNull(hoeToggle.getHandlers());
        assertSame(hoeToggle.getHandlers(), MoneyHoeToggleEvent.getHandlerList());

        MoneyStaffToggleEvent staffToggle = new MoneyStaffToggleEvent(player, tool, true);
        assertNotNull(staffToggle.getHandlers());
        assertSame(staffToggle.getHandlers(), MoneyStaffToggleEvent.getHandlerList());

        Inventory inv = mock(Inventory.class);
        MoneyStaffCursorSellEvent staffCursor = new MoneyStaffCursorSellEvent(
                player, tool, inv, 0, ClickType.LEFT, tool, true
        );
        assertNotNull(staffCursor.getHandlers());
        assertSame(staffCursor.getHandlers(), MoneyStaffCursorSellEvent.getHandlerList());
    }

    @Test
    public void testAllEventsAreSynchronous() {
        Player player = mock(Player.class);
        ShopItem item = mock(ShopItem.class);
        ShopCategory category = mock(ShopCategory.class);
        Block block = mock(Block.class);
        Container container = mock(Container.class);
        ItemStack tool = mock(ItemStack.class);
        CommandSender sender = mock(CommandSender.class);
        TransactionRecord record = mock(TransactionRecord.class);
        Inventory inv = mock(Inventory.class);

        List<org.bukkit.event.Event> events = List.of(
                new ShopPreTransactionEvent(player, item, category, TransactionType.BUY, 5, 50.0),
                new ShopPostTransactionEvent(player, item, category, TransactionType.BUY, 5, 50.0, record),
                new ShopPreBulkSellEvent(player, BulkSellSource.QUICK_SELL_INVENTORY, null),
                new ShopBulkSellEvent(player, BulkSellSource.QUICK_SELL_INVENTORY, 10, 100.0, List.of()),
                new ShopOpenEvent(player, ShopViewType.CATEGORIES, null),
                new ShopReloadEvent(sender),
                new MoneyStaffUseEvent(player, tool, block, container),
                new SortingStaffUseEvent(player, tool, block, container),
                new MoneyHoeHarvestEvent(player, tool, block, true, new ArrayList<>()),
                new MoneyHoeToggleEvent(player, tool, true),
                new MoneyStaffToggleEvent(player, tool, true),
                new MoneyStaffCursorSellEvent(player, tool, inv, 0, ClickType.LEFT, tool, true)
        );

        for (org.bukkit.event.Event event : events) {
            assertFalse(
                    event.isAsynchronous(),
                    event.getClass().getSimpleName() + " must be synchronous to allow safe Bukkit API access and prevent race conditions"
            );
        }
    }

    @Test
    public void testShopPreTransactionEventModificationAndCancellation() {
        Player player = mock(Player.class);
        ShopItem item = mock(ShopItem.class);
        ShopCategory category = mock(ShopCategory.class);

        ShopPreTransactionEvent event = new ShopPreTransactionEvent(player, item, category, TransactionType.BUY, 10, 100.0);
        assertFalse(event.isCancelled());
        assertEquals(10, event.getAmount());
        assertEquals(100.0, event.getPrice());
        assertEquals(TransactionType.BUY, event.getType());
        assertEquals(Optional.of(category), event.getCategory());

        event.setAmount(5);
        event.setPrice(40.0);
        event.setCancelled(true);

        assertTrue(event.isCancelled());
        assertEquals(5, event.getAmount());
        assertEquals(40.0, event.getPrice());
    }

    @Test
    public void testShopToolEvents() {
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        Container container = mock(Container.class);
        ItemStack tool = mock(ItemStack.class);

        MoneyStaffUseEvent staffEvent = new MoneyStaffUseEvent(player, tool, block, container);
        assertFalse(staffEvent.isCancelled());
        staffEvent.setCancelled(true);
        assertTrue(staffEvent.isCancelled());
        assertSame(tool, staffEvent.getTool());
        assertSame(block, staffEvent.getBlock());
        assertSame(container, staffEvent.getContainer());

        SortingStaffUseEvent sortEvent = new SortingStaffUseEvent(player, tool, block, container);
        assertFalse(sortEvent.isCancelled());
        sortEvent.setCancelled(true);
        assertTrue(sortEvent.isCancelled());

        List<ItemStack> drops = new ArrayList<>();
        MoneyHoeHarvestEvent harvestEvent = new MoneyHoeHarvestEvent(player, tool, block, true, drops);
        assertTrue(harvestEvent.isAutoSell());
        harvestEvent.setAutoSell(false);
        assertFalse(harvestEvent.isAutoSell());
        harvestEvent.setCancelled(true);
        assertTrue(harvestEvent.isCancelled());

        MoneyHoeToggleEvent toggleEvent = new MoneyHoeToggleEvent(player, tool, true);
        assertTrue(toggleEvent.isNewAutoSellState());
        toggleEvent.setNewAutoSellState(false);
        assertFalse(toggleEvent.isNewAutoSellState());
        toggleEvent.setCancelled(true);
        assertTrue(toggleEvent.isCancelled());

        MoneyStaffToggleEvent staffToggle = new MoneyStaffToggleEvent(player, tool, true);
        assertEquals(tool, staffToggle.getTool());
        assertTrue(staffToggle.isNewRecursiveState());
        staffToggle.setNewRecursiveState(false);
        assertFalse(staffToggle.isNewRecursiveState());
        staffToggle.setCancelled(true);
        assertTrue(staffToggle.isCancelled());

        Inventory inv = mock(Inventory.class);
        MoneyStaffCursorSellEvent cursorEvent = new MoneyStaffCursorSellEvent(
                player, tool, inv, 3, ClickType.RIGHT, tool, true
        );
        assertEquals(player, cursorEvent.getPlayer());
        assertEquals(tool, cursorEvent.getStaff());
        assertEquals(inv, cursorEvent.getClickedInventory());
        assertEquals(3, cursorEvent.getSlot());
        assertEquals(ClickType.RIGHT, cursorEvent.getClickType());
        assertEquals(tool, cursorEvent.getTargetItem());
        assertTrue(cursorEvent.isRecursive());
        cursorEvent.setRecursive(false);
        assertFalse(cursorEvent.isRecursive());
        cursorEvent.setCancelled(true);
        assertTrue(cursorEvent.isCancelled());
    }

    @Test
    public void testBuyItemEventCancellationReturnsCancelled() {
        ConfigManager configManager = mock(ConfigManager.class);
        MainConfig mainConfig = mock(MainConfig.class);
        when(configManager.getMainConfig()).thenReturn(mainConfig);
        when(mainConfig.isBuyingEnabled()).thenReturn(true);

        EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        ShopItem item = mock(ShopItem.class);
        when(item.getId()).thenReturn("diamond");
        when(item.getMaterial()).thenReturn(Material.DIAMOND);
        when(item.getBuyPrice()).thenReturn(OptionalDouble.of(10.0));

        // Simulate 3rd party plugin cancelling the pre-transaction event
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg instanceof ShopPreTransactionEvent pre) {
                pre.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(ShopPreTransactionEvent.class));

        ShopAPIImpl shopAPI = new ShopAPIImpl(configManager, economy, null, null);
        TransactionResult result = shopAPI.buyItem(player, item, 1);

        assertEquals(TransactionResult.CANCELLED, result);
        verify(economy, never()).withdraw(any(Player.class), anyDouble());
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test
    public void testSellItemPriceAndAmountModificationViaPreTransactionEvent() {
        ConfigManager configManager = mock(ConfigManager.class);
        MainConfig mainConfig = mock(MainConfig.class);
        when(configManager.getMainConfig()).thenReturn(mainConfig);
        when(mainConfig.isSellingEnabled()).thenReturn(true);
        when(configManager.getMessagesConfig()).thenReturn(mock(me.usainsrht.basicshop.config.MessagesConfig.class));

        EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");

        ItemStack heldDiamond = mock(ItemStack.class);
        when(heldDiamond.getType()).thenReturn(Material.DIAMOND);
        when(heldDiamond.getAmount()).thenReturn(10);

        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[]{heldDiamond});
        when(player.getInventory()).thenReturn(inventory);

        ShopItem item = mock(ShopItem.class);
        when(item.getId()).thenReturn("diamond");
        when(item.getMaterial()).thenReturn(Material.DIAMOND);
        when(item.getSellPrice()).thenReturn(OptionalDouble.of(5.0));

        // Simulate 3rd party booster plugin: sell booster changes price from 20.0 (for 4 items) to 40.0, and limits amount to 4
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg instanceof ShopPreTransactionEvent pre) {
                pre.setAmount(4);
                pre.setPrice(40.0);
            }
            return null;
        }).when(pluginManager).callEvent(any(ShopPreTransactionEvent.class));

        ShopAPIImpl shopAPI = new ShopAPIImpl(configManager, economy, null, null);
        TransactionResult result = shopAPI.sellItem(player, item, 10);

        assertEquals(TransactionResult.SUCCESS, result);
        verify(economy).deposit(player, 40.0);
        verify(heldDiamond).setAmount(6); // 10 - 4 = 6

        ArgumentCaptor<ShopPostTransactionEvent> postCaptor = ArgumentCaptor.forClass(ShopPostTransactionEvent.class);
        verify(pluginManager).callEvent(postCaptor.capture());
        assertEquals(40.0, postCaptor.getValue().getTotalPrice());
        assertEquals(4, postCaptor.getValue().getAmount());
    }

    @Test
    public void testPreBulkSellEventCancellation() {
        ConfigManager configManager = mock(ConfigManager.class);
        MainConfig mainConfig = mock(MainConfig.class);
        when(configManager.getMainConfig()).thenReturn(mainConfig);
        when(mainConfig.isSellingEnabled()).thenReturn(true);

        EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);

        Player player = mock(Player.class);
        ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.getType()).thenReturn(Material.DIAMOND);
        when(itemStack.getAmount()).thenReturn(5);

        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg instanceof ShopPreBulkSellEvent pre) {
                pre.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any(ShopPreBulkSellEvent.class));

        ShopAPIImpl shopAPI = new ShopAPIImpl(configManager, economy, null, null);
        ShopAPI.QuickSellResult result = shopAPI.sellItemStacks(player, List.of(itemStack));

        assertEquals(ShopAPI.QuickSellResult.NOTHING, result);
        verify(economy, never()).deposit(any(Player.class), anyDouble());
    }

    @Test
    public void testQuickSellCursorSuccess() {
        ConfigManager configManager = mock(ConfigManager.class);
        MainConfig mainConfig = mock(MainConfig.class);
        when(configManager.getMainConfig()).thenReturn(mainConfig);
        when(mainConfig.isSellingEnabled()).thenReturn(true);
        when(configManager.getMessagesConfig()).thenReturn(mock(me.usainsrht.basicshop.config.MessagesConfig.class));

        EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");

        ItemStack cursorStack = mock(ItemStack.class);
        when(cursorStack.getType()).thenReturn(Material.DIAMOND);
        when(cursorStack.getAmount()).thenReturn(5);
        when(player.getItemOnCursor()).thenReturn(cursorStack);

        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[0]); // Inventory is empty!
        when(player.getInventory()).thenReturn(inventory);

        ShopItem item = mock(ShopItem.class);
        when(item.getId()).thenReturn("diamond");
        when(item.getMaterial()).thenReturn(Material.DIAMOND);
        when(item.getSellPrice()).thenReturn(OptionalDouble.of(10.0));

        ShopCategory category = mock(ShopCategory.class);
        when(category.getItems()).thenReturn(List.of(item));
        when(configManager.getCategories()).thenReturn(List.of(category));

        ShopAPIImpl shopAPI = new ShopAPIImpl(configManager, economy, null, null);
        TransactionResult result = shopAPI.quickSellCursor(player);

        assertEquals(TransactionResult.SUCCESS, result);
        verify(economy).deposit(player, 50.0);
        verify(player).setItemOnCursor(null);

        // Player's inventory contents should not be checked or modified
        verify(inventory, never()).setItem(anyInt(), any());
        verify(inventory, never()).removeItem(any(ItemStack[].class));

        ArgumentCaptor<ShopPostTransactionEvent> postCaptor = ArgumentCaptor.forClass(ShopPostTransactionEvent.class);
        verify(pluginManager).callEvent(postCaptor.capture());
        assertEquals(50.0, postCaptor.getValue().getTotalPrice());
        assertEquals(5, postCaptor.getValue().getAmount());
    }

    @Test
    public void testQuickSellCursorEmpty() {
        ConfigManager configManager = mock(ConfigManager.class);
        MainConfig mainConfig = mock(MainConfig.class);
        when(configManager.getMainConfig()).thenReturn(mainConfig);
        when(mainConfig.isSellingEnabled()).thenReturn(true);

        EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getItemOnCursor()).thenReturn(null);

        ShopAPIImpl shopAPI = new ShopAPIImpl(configManager, economy, null, null);
        TransactionResult result = shopAPI.quickSellCursor(player);

        assertEquals(TransactionResult.NOT_ENOUGH_ITEMS, result);
        verify(economy, never()).deposit(any(Player.class), anyDouble());
    }

    @Test
    public void testQuickSellCursorUnsellable() {
        ConfigManager configManager = mock(ConfigManager.class);
        MainConfig mainConfig = mock(MainConfig.class);
        when(configManager.getMainConfig()).thenReturn(mainConfig);
        when(mainConfig.isSellingEnabled()).thenReturn(true);

        EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);

        Player player = mock(Player.class);
        ItemStack cursorStack = mock(ItemStack.class);
        when(cursorStack.getType()).thenReturn(Material.BEDROCK);
        when(cursorStack.getAmount()).thenReturn(1);
        when(player.getItemOnCursor()).thenReturn(cursorStack);

        when(configManager.getCategories()).thenReturn(List.of());

        ShopAPIImpl shopAPI = new ShopAPIImpl(configManager, economy, null, null);
        TransactionResult result = shopAPI.quickSellCursor(player);

        assertEquals(TransactionResult.SELL_DISABLED, result);
        verify(economy, never()).deposit(any(Player.class), anyDouble());
        verify(player, never()).setItemOnCursor(any());
    }

    @Test
    public void testQuickSellCursorPartialSellViaPreEvent() {
        ConfigManager configManager = mock(ConfigManager.class);
        MainConfig mainConfig = mock(MainConfig.class);
        when(configManager.getMainConfig()).thenReturn(mainConfig);
        when(mainConfig.isSellingEnabled()).thenReturn(true);
        when(configManager.getMessagesConfig()).thenReturn(mock(me.usainsrht.basicshop.config.MessagesConfig.class));

        EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");

        ItemStack cursorStack = mock(ItemStack.class);
        when(cursorStack.getType()).thenReturn(Material.DIAMOND);
        when(cursorStack.getAmount()).thenReturn(5);
        when(player.getItemOnCursor()).thenReturn(cursorStack);

        ShopItem item = mock(ShopItem.class);
        when(item.getId()).thenReturn("diamond");
        when(item.getMaterial()).thenReturn(Material.DIAMOND);
        when(item.getSellPrice()).thenReturn(OptionalDouble.of(10.0));

        ShopCategory category = mock(ShopCategory.class);
        when(category.getItems()).thenReturn(List.of(item));
        when(configManager.getCategories()).thenReturn(List.of(category));

        // PreEvent modifies amount from 5 to 2 and price to 25.0
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg instanceof ShopPreTransactionEvent pre) {
                pre.setAmount(2);
                pre.setPrice(25.0);
            }
            return null;
        }).when(pluginManager).callEvent(any(ShopPreTransactionEvent.class));

        ShopAPIImpl shopAPI = new ShopAPIImpl(configManager, economy, null, null);
        TransactionResult result = shopAPI.quickSellCursor(player);

        assertEquals(TransactionResult.SUCCESS, result);
        verify(economy).deposit(player, 25.0);
        verify(cursorStack).setAmount(3); // 5 - 2 = 3
        verify(player).setItemOnCursor(cursorStack);
    }

    @Test
    public void testSellSlotRegularItem() {
        ConfigManager configManager = mock(ConfigManager.class);
        MainConfig mainConfig = mock(MainConfig.class);
        when(configManager.getMainConfig()).thenReturn(mainConfig);
        when(mainConfig.isSellingEnabled()).thenReturn(true);
        when(configManager.getMessagesConfig()).thenReturn(mock(me.usainsrht.basicshop.config.MessagesConfig.class));

        EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");

        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(9);

        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND);
        when(stack.getAmount()).thenReturn(5);
        when(inventory.getItem(0)).thenReturn(stack);

        ShopItem item = mock(ShopItem.class);
        when(item.getId()).thenReturn("diamond");
        when(item.getMaterial()).thenReturn(Material.DIAMOND);
        when(item.getSellPrice()).thenReturn(OptionalDouble.of(10.0));

        ShopCategory category = mock(ShopCategory.class);
        when(category.getItems()).thenReturn(List.of(item));
        when(configManager.getCategories()).thenReturn(List.of(category));

        ShopAPIImpl shopAPI = new ShopAPIImpl(configManager, economy, null, null);
        ShopAPI.QuickSellResult result = shopAPI.sellSlot(player, inventory, 0, false);

        assertTrue(result.anySuccess());
        assertEquals(5, result.totalAmount());
        assertEquals(50.0, result.totalEarned());
        verify(economy).deposit(player, 50.0);
        verify(inventory).setItem(0, null);

        ArgumentCaptor<ShopBulkSellEvent> captor = ArgumentCaptor.forClass(ShopBulkSellEvent.class);
        verify(pluginManager).callEvent(captor.capture());
        assertEquals(BulkSellSource.STAFF_CURSOR, captor.getValue().getSource());
    }

    @Test
    public void testSellMatchingItemsRegularItems() {
        ConfigManager configManager = mock(ConfigManager.class);
        MainConfig mainConfig = mock(MainConfig.class);
        when(configManager.getMainConfig()).thenReturn(mainConfig);
        when(mainConfig.isSellingEnabled()).thenReturn(true);
        when(configManager.getMessagesConfig()).thenReturn(mock(me.usainsrht.basicshop.config.MessagesConfig.class));

        EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");

        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(3);

        ItemStack stack0 = mock(ItemStack.class);
        when(stack0.getType()).thenReturn(Material.DIAMOND);
        when(stack0.getAmount()).thenReturn(5);
        when(inventory.getItem(0)).thenReturn(stack0);

        ItemStack stack1 = mock(ItemStack.class);
        when(stack1.getType()).thenReturn(Material.EMERALD);
        when(stack1.getAmount()).thenReturn(10);
        when(inventory.getItem(1)).thenReturn(stack1);

        ItemStack stack2 = mock(ItemStack.class);
        when(stack2.getType()).thenReturn(Material.DIAMOND);
        when(stack2.getAmount()).thenReturn(3);
        when(inventory.getItem(2)).thenReturn(stack2);

        ShopItem diamond = mock(ShopItem.class);
        when(diamond.getId()).thenReturn("diamond");
        when(diamond.getMaterial()).thenReturn(Material.DIAMOND);
        when(diamond.getSellPrice()).thenReturn(OptionalDouble.of(10.0));

        ShopCategory category = mock(ShopCategory.class);
        when(category.getItems()).thenReturn(List.of(diamond));
        when(configManager.getCategories()).thenReturn(List.of(category));

        ShopAPIImpl shopAPI = new ShopAPIImpl(configManager, economy, null, null);
        ShopAPI.QuickSellResult result = shopAPI.sellMatchingItems(player, inventory, java.util.Set.of(Material.DIAMOND), false);

        assertTrue(result.anySuccess());
        assertEquals(8, result.totalAmount());
        assertEquals(80.0, result.totalEarned());
        verify(inventory).setItem(0, null);
        verify(inventory, never()).setItem(eq(1), any());
        verify(inventory).setItem(2, null);
    }

    @Test
    public void testStaffCursorClickCreativeModeExitsEarlyWithoutCancellingEvent() {
        ConfigManager configManager = mock(ConfigManager.class);
        ShopToolFactory toolFactory = mock(ShopToolFactory.class);
        me.usainsrht.basicshop.listener.ToolListener listener =
                new me.usainsrht.basicshop.listener.ToolListener(configManager, null, toolFactory, null);

        Player player = mock(Player.class);
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.CREATIVE);

        ItemStack cursorStaff = mock(ItemStack.class);
        when(toolFactory.getToolType(cursorStaff)).thenReturn(me.usainsrht.basicshop.api.model.ShopToolType.MONEY_STAFF);

        org.bukkit.event.inventory.InventoryClickEvent event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCursor()).thenReturn(cursorStaff);
        when(event.isCancelled()).thenReturn(false);

        listener.onStaffCursorClick(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertFalse(event.isCancelled());
    }

    @Test
    public void testItemOnStaffClickCreativeModeExitsEarlyWithoutCancellingEvent() {
        ConfigManager configManager = mock(ConfigManager.class);
        ShopToolFactory toolFactory = mock(ShopToolFactory.class);
        me.usainsrht.basicshop.listener.ToolListener listener =
                new me.usainsrht.basicshop.listener.ToolListener(configManager, null, toolFactory, null);

        Player player = mock(Player.class);
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.CREATIVE);

        ItemStack cursorItem = mock(ItemStack.class);
        when(toolFactory.getToolType(cursorItem)).thenReturn(null);

        ItemStack staffSlot = mock(ItemStack.class);
        when(toolFactory.getToolType(staffSlot)).thenReturn(me.usainsrht.basicshop.api.model.ShopToolType.MONEY_STAFF);

        org.bukkit.event.inventory.InventoryClickEvent event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCursor()).thenReturn(cursorItem);
        when(event.getCurrentItem()).thenReturn(staffSlot);
        when(event.isCancelled()).thenReturn(false);

        listener.onStaffCursorClick(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertFalse(event.isCancelled());
    }

    @Test
    public void testItemOnStaffClickSurvivalModeCancelsEvent() {
        ConfigManager configManager = mock(ConfigManager.class);
        ShopToolFactory toolFactory = mock(ShopToolFactory.class);
        ToolsConfig toolsConfig = mock(ToolsConfig.class);
        when(configManager.getToolsConfig()).thenReturn(toolsConfig);
        when(toolsConfig.getCursorCooldownSeconds(any())).thenReturn(0.0);

        me.usainsrht.basicshop.listener.ToolListener listener =
                new me.usainsrht.basicshop.listener.ToolListener(configManager, null, toolFactory, null);

        Player player = mock(Player.class);
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("basicshop.tools.staff")).thenReturn(true);

        ItemStack cursorItem = mock(ItemStack.class);
        when(cursorItem.getAmount()).thenReturn(5);
        when(cursorItem.isEmpty()).thenReturn(false);
        when(toolFactory.getToolType(cursorItem)).thenReturn(null);
        when(toolFactory.isShopTool(cursorItem)).thenReturn(false);

        ItemStack staffSlot = mock(ItemStack.class);
        when(toolFactory.getToolType(staffSlot)).thenReturn(me.usainsrht.basicshop.api.model.ShopToolType.MONEY_STAFF);

        Inventory inventory = mock(Inventory.class);
        org.bukkit.inventory.BlockInventoryHolder holder = mock(org.bukkit.inventory.BlockInventoryHolder.class);
        when(inventory.getHolder()).thenReturn(holder);

        org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);

        org.bukkit.event.inventory.InventoryClickEvent event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCursor()).thenReturn(cursorItem);
        when(event.getCurrentItem()).thenReturn(staffSlot);
        when(event.getClickedInventory()).thenReturn(inventory);
        when(event.getView()).thenReturn(view);
        when(event.getClick()).thenReturn(ClickType.LEFT);

        space.arim.morepaperlib.MorePaperLib morePaperLib = mock(space.arim.morepaperlib.MorePaperLib.class, RETURNS_DEEP_STUBS);
        when(morePaperLib.scheduling().entitySpecificScheduler(player).run(any(Runnable.class), any())).thenReturn(null);

        me.usainsrht.basicshop.listener.ToolListener listenerWithPaper =
                new me.usainsrht.basicshop.listener.ToolListener(configManager, null, toolFactory, morePaperLib);

        listenerWithPaper.onStaffCursorClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    public void testSellCursorRegularItem() {
        ConfigManager configManager = mock(ConfigManager.class);
        MainConfig mainConfig = mock(MainConfig.class);
        when(configManager.getMainConfig()).thenReturn(mainConfig);
        when(mainConfig.isSellingEnabled()).thenReturn(true);
        when(configManager.getMessagesConfig()).thenReturn(mock(me.usainsrht.basicshop.config.MessagesConfig.class));

        EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");

        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND);
        when(stack.getAmount()).thenReturn(5);
        when(player.getItemOnCursor()).thenReturn(stack);

        ShopItem item = mock(ShopItem.class);
        when(item.getId()).thenReturn("diamond");
        when(item.getMaterial()).thenReturn(Material.DIAMOND);
        when(item.getSellPrice()).thenReturn(OptionalDouble.of(10.0));

        ShopCategory category = mock(ShopCategory.class);
        when(category.getItems()).thenReturn(List.of(item));
        when(configManager.getCategories()).thenReturn(List.of(category));

        ShopAPIImpl shopAPI = new ShopAPIImpl(configManager, economy, null, null);
        ShopAPI.QuickSellResult result = shopAPI.sellCursor(player, false);

        assertTrue(result.anySuccess());
        assertEquals(5, result.totalAmount());
        assertEquals(50.0, result.totalEarned());
        verify(economy).deposit(player, 50.0);
        verify(player).setItemOnCursor(null);
    }
}
