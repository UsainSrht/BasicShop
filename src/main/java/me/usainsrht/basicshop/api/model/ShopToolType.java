package me.usainsrht.basicshop.api.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Identifies custom BasicShop tools stored via PDC on {@link org.bukkit.inventory.ItemStack}s.
 */
public enum ShopToolType {

    MONEY_STAFF("money_staff"),
    MONEY_HOE("money_hoe");

    private final String id;

    ShopToolType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static Optional<ShopToolType> fromId(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String normalized = id.toLowerCase(Locale.ROOT);
        for (ShopToolType type : values()) {
            if (type.id.equals(normalized)) return Optional.of(type);
        }
        return Optional.empty();
    }
}
