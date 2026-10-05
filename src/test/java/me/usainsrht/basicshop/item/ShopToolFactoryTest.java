package me.usainsrht.basicshop.item;

import me.usainsrht.basicshop.config.ConfigManager;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ShopToolFactoryTest {

    private Plugin plugin;
    private ConfigManager configManager;
    private ShopToolFactory factory;

    @BeforeEach
    public void setUp() {
        plugin = mock(Plugin.class);
        when(plugin.namespace()).thenReturn("basicshop");
        when(plugin.getName()).thenReturn("basicshop");
        configManager = mock(ConfigManager.class);
        factory = new ShopToolFactory(plugin, configManager);
    }

    @Test
    public void testRecursiveSellDefaultDisabled() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(false);

        assertFalse(factory.isRecursiveSellEnabled(stack));
    }

    @Test
    public void testRecursiveSellEnabledWhenPresent() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(true);

        assertTrue(factory.isRecursiveSellEnabled(stack));
    }

    @Test
    public void testSetRecursiveSellEnabled() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);

        factory.setRecursiveSellEnabled(stack, true);
        verify(pdc).set(any(NamespacedKey.class), eq(PersistentDataType.BYTE), eq((byte) 1));
        verify(stack).setItemMeta(meta);

        factory.setRecursiveSellEnabled(stack, false);
        verify(pdc).remove(any(NamespacedKey.class));
    }

    @Test
    public void testToggleRecursiveSell() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(false);

        boolean next = factory.toggleRecursiveSell(stack);
        assertTrue(next);
        verify(pdc).set(any(NamespacedKey.class), eq(PersistentDataType.BYTE), eq((byte) 1));
    }

    @Test
    public void testOrderModeDefaultDisabled() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(false);

        assertFalse(factory.isOrderModeEnabled(stack));
    }

    @Test
    public void testSetOrderModeEnabled() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);

        factory.setOrderModeEnabled(stack, true);
        verify(pdc).set(any(NamespacedKey.class), eq(PersistentDataType.BYTE), eq((byte) 1));
        verify(stack).setItemMeta(meta);

        factory.setOrderModeEnabled(stack, false);
        verify(pdc).remove(any(NamespacedKey.class));
    }

    @Test
    public void testToggleOrderMode() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(false);

        boolean next = factory.toggleOrderMode(stack);
        assertTrue(next);
        verify(pdc).set(any(NamespacedKey.class), eq(PersistentDataType.BYTE), eq((byte) 1));
    }

    @Test
    public void testUuidKey() {
        NamespacedKey key = factory.getUuidKey();
        assertNotNull(key);
        assertEquals("basicshop", key.getNamespace());
        assertEquals("uuid", key.getKey());
    }

    @Test
    public void testHasToolUuid() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(factory.getUuidKey(), PersistentDataType.STRING)).thenReturn(false);

        assertFalse(factory.hasToolUuid(stack));

        when(pdc.has(factory.getUuidKey(), PersistentDataType.STRING)).thenReturn(true);
        assertTrue(factory.hasToolUuid(stack));
    }

    @Test
    public void testGetToolUuid() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        UUID uuid = UUID.randomUUID();

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(factory.getUuidKey(), PersistentDataType.STRING)).thenReturn(uuid.toString());

        assertEquals(uuid, factory.getToolUuid(stack));
    }

    @Test
    public void testSetToolUuid() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        UUID uuid = UUID.randomUUID();

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);

        factory.setToolUuid(stack, uuid);
        verify(pdc).set(factory.getUuidKey(), PersistentDataType.STRING, uuid.toString());
        verify(stack).setItemMeta(meta);

        factory.setToolUuid(stack, null);
        verify(pdc).remove(factory.getUuidKey());
    }

    @Test
    public void testAssignRandomUuid() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);

        UUID assigned = factory.assignRandomUuid(stack);
        assertNotNull(assigned);
        verify(pdc).set(eq(factory.getUuidKey()), eq(PersistentDataType.STRING), eq(assigned.toString()));
        verify(stack).setItemMeta(meta);
    }
}
