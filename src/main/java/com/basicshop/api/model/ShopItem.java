package com.basicshop.api.model;

import org.bukkit.Material;

import java.util.OptionalDouble;

/**
 * Represents a single purchasable/sellable item configured in a category file.
 *
 * <p>If {@code buyPrice} is empty or was configured as -1, buying is disabled.
 * If {@code sellPrice} is empty or was configured as -1, selling is disabled.
 */
public final class ShopItem {

    private final String id;
    private final Material material;
    private final OptionalDouble buyPrice;
    private final OptionalDouble sellPrice;

    public ShopItem(
            String id,
            Material material,
            OptionalDouble buyPrice,
            OptionalDouble sellPrice
    ) {
        this.id = id;
        this.material = material;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    /** Unique identifier for this item (material name, lower-cased). */
    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    /**
     * Returns the buy price, or empty if buying this item is disabled.
     */
    public OptionalDouble getBuyPrice() {
        return buyPrice;
    }

    /**
     * Returns the sell price, or empty if selling this item is disabled.
     */
    public OptionalDouble getSellPrice() {
        return sellPrice;
    }

    public boolean canBuy() {
        return buyPrice.isPresent();
    }

    public boolean canSell() {
        return sellPrice.isPresent();
    }
}
