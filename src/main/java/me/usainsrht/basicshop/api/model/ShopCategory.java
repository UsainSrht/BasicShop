package me.usainsrht.basicshop.api.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

/**
 * Represents a category of items in the shop, loaded from a file in the categories/ folder.
 */
public final class ShopCategory {

    private final String id;
    private final String displayName;   // MiniMessage string
    private final String guiTitle;      // MiniMessage string for the GUI header
    private final Material iconMaterial;
    private final int slot;
    private final List<String> iconLore; // MiniMessage strings
    private final List<ShopItem> items;

    public ShopCategory(
            String id,
            String displayName,
            String guiTitle,
            Material iconMaterial,
            int slot,
            List<String> iconLore,
            List<ShopItem> items
    ) {
        this.id = id;
        this.displayName = displayName;
        this.guiTitle = guiTitle;
        this.iconMaterial = iconMaterial;
        this.slot = slot;
        this.iconLore = Collections.unmodifiableList(iconLore);
        this.items = Collections.unmodifiableList(items);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    /** Slot in the main categories GUI. */
    public int getSlot() {
        return slot;
    }

    public List<String> getIconLore() {
        return iconLore;
    }

    public List<ShopItem> getItems() {
        return items;
    }
}
