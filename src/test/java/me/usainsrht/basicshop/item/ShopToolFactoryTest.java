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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
