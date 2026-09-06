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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
}
