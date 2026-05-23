package me.usainsrht.basicshop.gui;

import me.usainsrht.basicshop.listener.GuiListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
     * Called by {@link GuiListener} when a player clicks inside this GUI.
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

        if (miniLore != null && !miniName.isEmpty()) meta.displayName(MM.deserialize(miniName).decoration(TextDecoration.ITALIC, false));

        if (miniLore != null && !miniLore.isEmpty()) {
            List<Component> loreComponents = miniLore.stream()
                    .map(line -> MM.deserialize(line).decoration(TextDecoration.ITALIC, false))
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
     * Fills all null/AIR slots with the given filler item,
     * optionally hiding its tooltip.
     */
    protected void fillEmpty(Material fillerMaterial, String fillerName, boolean hideTooltip) {
        ItemStack filler = buildItem(fillerMaterial, fillerName);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(hideTooltip);
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack existing = inventory.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Converts a {@link Material} enum name to a human-readable title-cased string.
     * e.g. {@code OAK_LOG} → {@code "Oak Log"}, {@code COBBLESTONE} → {@code "Cobblestone"}.
     */
    protected static String formatMaterialName(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1));
        }
        return sb.toString();
    }
}
