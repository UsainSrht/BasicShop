package com.basicshop.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;

/**
 * Base class for all BasicShop GUIs.
 *
 * <p>Implements {@link InventoryHolder} so {@code GuiListener} can identify
 * and route click events without casting to specific GUI types.
 */
public abstract class AbstractShopGui implements InventoryHolder {

    protected static final MiniMessage MM = MiniMessage.miniMessage();

    protected Inventory inventory;

    // -------------------------------------------------------------------------
    // InventoryHolder
    // -------------------------------------------------------------------------

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // -------------------------------------------------------------------------
    // Event routing
    // -------------------------------------------------------------------------

    /**
     * Called by {@link com.basicshop.listener.GuiListener} when a player clicks inside this GUI.
     * The event has already been cancelled; implementations should re-open, close,
     * or trigger API calls as needed.
     */
    public abstract void handleClick(InventoryClickEvent event);

    // -------------------------------------------------------------------------
    // Item building utilities
    // -------------------------------------------------------------------------

    /**
     * Builds a display {@link ItemStack} using MiniMessage formatting for both
     * the display name and each lore line.
     */
    protected ItemStack buildItem(Material material, String miniName, List<String> miniLore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(MM.deserialize(miniName));

        if (miniLore != null && !miniLore.isEmpty()) {
            List<Component> loreComponents = miniLore.stream()
                    .map(MM::deserialize)
                    .toList();
            meta.lore(loreComponents);
        } else {
            meta.lore(Collections.emptyList());
        }

        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Builds a single-line display item.
     */
    protected ItemStack buildItem(Material material, String miniName) {
        return buildItem(material, miniName, Collections.emptyList());
    }

    /**
     * Fills all null/AIR slots with the given filler item.
     */
    protected void fillEmpty(Material fillerMaterial, String fillerName) {
        ItemStack filler = buildItem(fillerMaterial, fillerName);
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack existing = inventory.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) {
                inventory.setItem(i, filler);
            }
        }
    }
}
