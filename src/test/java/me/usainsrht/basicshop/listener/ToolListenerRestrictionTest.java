package me.usainsrht.basicshop.listener;

import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.model.ShopToolType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.FilterMode;
import me.usainsrht.basicshop.config.MessagesConfig;
import me.usainsrht.basicshop.config.ToolRestrictions;
import me.usainsrht.basicshop.config.ToolsConfig;
import me.usainsrht.basicshop.item.ShopToolFactory;
import me.usainsrht.basicshop.region.RegionService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ToolListenerRestrictionTest {

    private ConfigManager configManager;
    private ToolsConfig toolsConfig;
    private MessagesConfig messagesConfig;
    private ShopAPI shopAPI;
    private ShopToolFactory toolFactory;
    private RegionService regionService;
    private ToolListener listener;

    private Player player;
    private World world;
    private Location playerLoc;
    private Block block;
    private Location blockLoc;
    private PlayerInventory inventory;

    @BeforeEach
    public void setUp() {
        configManager = mock(ConfigManager.class);
        toolsConfig = mock(ToolsConfig.class);
        messagesConfig = mock(MessagesConfig.class);
        shopAPI = mock(ShopAPI.class);
        toolFactory = mock(ShopToolFactory.class);
        regionService = mock(RegionService.class);

        when(configManager.getToolsConfig()).thenReturn(toolsConfig);
        when(configManager.getMessagesConfig()).thenReturn(messagesConfig);
        when(toolsConfig.isEnabled(any())).thenReturn(true);

        listener = new ToolListener(
                configManager,
                shopAPI,
                toolFactory,
                null,
                null,
                regionService
        );

        player = mock(Player.class);
        world = mock(World.class);
        when(world.getName()).thenReturn("world_nether");

        playerLoc = new Location(world, 10, 64, 10);
        when(player.getLocation()).thenReturn(playerLoc);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isInsideVehicle()).thenReturn(false);

        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        block = mock(Block.class);
        blockLoc = new Location(world, 12, 64, 12);
        when(block.getLocation()).thenReturn(blockLoc);
        when(block.getWorld()).thenReturn(world);
    }

    @Test
    public void testHoeBreakCancelledInBlacklistedWorld() {
        ItemStack hoe = mock(ItemStack.class);
        when(inventory.getItemInMainHand()).thenReturn(hoe);
        when(toolFactory.getToolType(hoe)).thenReturn(ShopToolType.MONEY_HOE);

        ToolRestrictions restrictions = new ToolRestrictions(
                FilterMode.BLACKLIST,
                Set.of("world_nether"),
                FilterMode.BLACKLIST,
                Collections.emptySet()
        );
        when(toolsConfig.getRestrictions(ShopToolType.MONEY_HOE)).thenReturn(restrictions);

        BlockBreakEvent event = new BlockBreakEvent(block, player);
        assertFalse(event.isCancelled());

        listener.onHoeBreakRestriction(event);

        assertTrue(event.isCancelled(), "BlockBreakEvent should be cancelled in a blacklisted world");
        verify(messagesConfig).send(eq(player), eq("tool-disabled-world"), any(), any(), any());
    }

    @Test
    public void testHoeBreakAllowedInAllowedWorld() {
        ItemStack hoe = mock(ItemStack.class);
        when(inventory.getItemInMainHand()).thenReturn(hoe);
        when(toolFactory.getToolType(hoe)).thenReturn(ShopToolType.MONEY_HOE);

        ToolRestrictions restrictions = new ToolRestrictions(
                FilterMode.BLACKLIST,
                Set.of("world_the_end"),
                FilterMode.BLACKLIST,
                Collections.emptySet()
        );
        when(toolsConfig.getRestrictions(ShopToolType.MONEY_HOE)).thenReturn(restrictions);

        BlockBreakEvent event = new BlockBreakEvent(block, player);
        assertFalse(event.isCancelled());

        listener.onHoeBreakRestriction(event);

        assertFalse(event.isCancelled(), "BlockBreakEvent should NOT be cancelled in an allowed world");
        verify(messagesConfig, never()).send(any(), any(), any());
    }

    @Test
    public void testStaffInteractCancelledInBlacklistedRegion() {
        ItemStack staff = mock(ItemStack.class);
        when(toolFactory.getToolType(staff)).thenReturn(ShopToolType.MONEY_STAFF);

        ToolRestrictions restrictions = new ToolRestrictions(
                FilterMode.BLACKLIST,
                Collections.emptySet(),
                FilterMode.BLACKLIST,
                Set.of("spawn")
        );
        when(toolsConfig.getRestrictions(ShopToolType.MONEY_STAFF)).thenReturn(restrictions);

        when(regionService.isAvailable()).thenReturn(true);
        when(regionService.getRegionNames(blockLoc)).thenReturn(Set.of("spawn"));
        when(regionService.getRegionNames(playerLoc)).thenReturn(Collections.emptySet());

        PlayerInteractEvent event = new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                staff,
                block,
                null,
                EquipmentSlot.HAND
        );

        listener.onStaffInteractRestriction(event);

        assertTrue(event.isCancelled(), "Staff interact should be cancelled when clicking in a blacklisted region");
        verify(messagesConfig).send(eq(player), eq("tool-disabled-region"), any(), any(), any());
    }

    @Test
    public void testStaffInteractAllowedInWhitelistedRegion() {
        ItemStack staff = mock(ItemStack.class);
        when(toolFactory.getToolType(staff)).thenReturn(ShopToolType.MONEY_STAFF);

        ToolRestrictions restrictions = new ToolRestrictions(
                FilterMode.BLACKLIST,
                Collections.emptySet(),
                FilterMode.WHITELIST,
                Set.of("market")
        );
        when(toolsConfig.getRestrictions(ShopToolType.MONEY_STAFF)).thenReturn(restrictions);

        when(regionService.isAvailable()).thenReturn(true);
        when(regionService.getRegionNames(blockLoc)).thenReturn(Set.of("market"));
        when(regionService.getRegionNames(playerLoc)).thenReturn(Set.of("market"));

        PlayerInteractEvent event = new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                staff,
                block,
                null,
                EquipmentSlot.HAND
        );

        listener.onStaffInteractRestriction(event);

        assertFalse(event.isCancelled(), "Staff interact should NOT be cancelled in a whitelisted region");
        verify(messagesConfig, never()).send(any(), any(), any());
    }

    @Test
    public void testStaffInteractDeniedWhenOutsideWhitelistedRegion() {
        ItemStack staff = mock(ItemStack.class);
        when(toolFactory.getToolType(staff)).thenReturn(ShopToolType.MONEY_STAFF);

        ToolRestrictions restrictions = new ToolRestrictions(
                FilterMode.BLACKLIST,
                Collections.emptySet(),
                FilterMode.WHITELIST,
                Set.of("market")
        );
        when(toolsConfig.getRestrictions(ShopToolType.MONEY_STAFF)).thenReturn(restrictions);

        when(regionService.isAvailable()).thenReturn(true);
        when(regionService.getRegionNames(blockLoc)).thenReturn(Collections.emptySet());
        when(regionService.getRegionNames(playerLoc)).thenReturn(Collections.emptySet());

        PlayerInteractEvent event = new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                staff,
                block,
                null,
                EquipmentSlot.HAND
        );

        listener.onStaffInteractRestriction(event);

        assertTrue(event.isCancelled(), "Staff interact should be denied when outside a whitelisted region");
        verify(messagesConfig).send(eq(player), eq("tool-disabled-region"), any(), any(), any());
    }
}
