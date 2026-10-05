package me.usainsrht.basicshop.bazaar;

import me.usainsrht.basicshop.api.event.BazaarStaffCursorEvent;
import me.usainsrht.basicshop.api.event.BazaarStaffModeToggleEvent;
import me.usainsrht.basicshop.api.event.BazaarStaffRecursiveToggleEvent;
import me.usainsrht.basicshop.api.event.BazaarStaffUseEvent;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class BazaarStaffTest {

    @Test
    public void testNoOpBazaarService() {
        BazaarService noOp = new NoOpBazaarService();
        assertFalse(noOp.isAvailable());

        Player player = mock(Player.class);
        Block block = mock(Block.class);
        assertDoesNotThrow(() -> noOp.handleContainerUse(player, block, false, false, true));
        assertDoesNotThrow(() -> noOp.handleStaffOnCursorClick(player, null, 0, ClickType.LEFT, null, false, null, false, false, true));
        assertDoesNotThrow(() -> noOp.handleItemOnStaffClick(player, null, 0, ClickType.LEFT, null, false, null, false, false, true));
    }

    @Test
    public void testBazaarStaffUseEvent() {
        Player player = mock(Player.class);
        ItemStack tool = mock(ItemStack.class);
        Block block = mock(Block.class);
        Container container = mock(Container.class);

        BazaarStaffUseEvent event = new BazaarStaffUseEvent(player, tool, block, container, true, true);
        assertEquals(player, event.getPlayer());
        assertEquals(tool, event.getTool());
        assertEquals(block, event.getBlock());
        assertEquals(container, event.getContainer());
        assertTrue(event.isOrderMode());
        assertTrue(event.isRecursive());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    public void testBazaarStaffModeToggleEvent() {
        Player player = mock(Player.class);
        ItemStack tool = mock(ItemStack.class);

        BazaarStaffModeToggleEvent event = new BazaarStaffModeToggleEvent(player, tool, true);
        assertEquals(player, event.getPlayer());
        assertEquals(tool, event.getTool());
        assertTrue(event.isNewOrderMode());

        event.setNewOrderMode(false);
        assertFalse(event.isNewOrderMode());

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    public void testBazaarStaffRecursiveToggleEvent() {
        Player player = mock(Player.class);
        ItemStack tool = mock(ItemStack.class);

        BazaarStaffRecursiveToggleEvent event = new BazaarStaffRecursiveToggleEvent(player, tool, true);
        assertEquals(player, event.getPlayer());
        assertEquals(tool, event.getTool());
        assertTrue(event.isNewRecursiveState());

        event.setNewRecursiveState(false);
        assertFalse(event.isNewRecursiveState());

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    public void testBazaarStaffCursorEvent() {
        Player player = mock(Player.class);
        ItemStack staff = mock(ItemStack.class);
        Inventory inv = mock(Inventory.class);
        ItemStack target = mock(ItemStack.class);

        BazaarStaffCursorEvent event = new BazaarStaffCursorEvent(
                player, staff, inv, 5, ClickType.LEFT, target, true, true, false
        );

        assertEquals(player, event.getPlayer());
        assertEquals(staff, event.getStaff());
        assertEquals(inv, event.getClickedInventory());
        assertEquals(5, event.getSlot());
        assertEquals(ClickType.LEFT, event.getClickType());
        assertEquals(target, event.getTargetItem());
        assertTrue(event.isStaffOnCursor());
        assertTrue(event.isOrderMode());
        assertFalse(event.isRecursive());

        event.setRecursive(true);
        assertTrue(event.isRecursive());

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }
}
