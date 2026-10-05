package me.usainsrht.basicshop.item;

import me.usainsrht.basicshop.api.model.ShopToolType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.ToolsConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Builds and identifies tagged shop tool items.
 */
public final class ShopToolFactory {

    private final ConfigManager configManager;
    private final NamespacedKey toolKey;
    /** When present, auto-sell is disabled (matches legacy {@code custom:autosell} semantics). */
    private final NamespacedKey autoSellDisabledKey;
    /** When present, recursive selling is enabled on a money staff (default is disabled). */
    private final NamespacedKey recursiveSellKey;
    /** When present, order mode is enabled on a bazaar staff (default is disabled / bazaar listing restock mode). */
    private final NamespacedKey orderModeKey;
    /** Key used to store a unique random UUID on each shop tool instance. */
    private final NamespacedKey uuidKey;

    public ShopToolFactory(Plugin plugin, ConfigManager configManager) {
        this.configManager = configManager;
        this.toolKey = new NamespacedKey(plugin, "shop_tool");
        this.autoSellDisabledKey = new NamespacedKey(plugin, "autosell_disabled");
        this.recursiveSellKey = new NamespacedKey(plugin, "recursive_sell");
        this.orderModeKey = new NamespacedKey(plugin, "bazaar_order_mode");
        this.uuidKey = new NamespacedKey(plugin, "uuid");
    }

    public ItemStack create(ShopToolType type) {
        return create(type, 1);
    }

    public ItemStack create(ShopToolType type, int amount) {
        if (type == null) {
            return new ItemStack(org.bukkit.Material.BLAZE_ROD, Math.max(1, amount));
        }
        ToolsConfig toolsConfig = configManager != null ? configManager.getToolsConfig() : null;
        ToolsConfig.ToolDefinition def = toolsConfig != null ? toolsConfig.get(type) : null;
        org.bukkit.Material fallbackMat = ToolsConfig.getDefaultMaterial(type);
        ItemStack stack = (def != null && def.itemStack() != null && !def.itemStack().getType().isAir())
                ? def.itemStack().clone()
                : new ItemStack(fallbackMat);
        stack.setAmount(amount);

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, type.getId());
            meta.getPersistentDataContainer().set(uuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            UseCooldownComponent cooldownComponent = meta.getUseCooldown();
            if (cooldownComponent.getCooldownGroup() == null) {
                cooldownComponent.setCooldownGroup(type.getCooldownKey());
                meta.setUseCooldown(cooldownComponent);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void ensureUseCooldown(ItemStack item, ShopToolType type) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        UseCooldownComponent cooldownComponent = meta.getUseCooldown();
        if (cooldownComponent.getCooldownGroup() == null) {
            cooldownComponent.setCooldownGroup(type.getCooldownKey());
            meta.setUseCooldown(cooldownComponent);
            item.setItemMeta(meta);
        }
    }

    public void applyCooldown(Player player, ItemStack item, ShopToolType type) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasUseCooldown()) return;

        UseCooldownComponent cdComp = meta.getUseCooldown();
        float seconds = cdComp.getCooldownSeconds();
        if (seconds > 0) {
            int ticks = Math.round(seconds * 20.0f);
            player.setCooldown(item, ticks);
            NamespacedKey group = cdComp.getCooldownGroup() != null ? cdComp.getCooldownGroup() : type.getCooldownKey();
            player.setCooldown(group, ticks);
        }
    }

    public ShopToolType getToolType(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        return ShopToolType.fromId(id).orElse(null);
    }

    public boolean isShopTool(ItemStack stack) {
        return getToolType(stack) != null;
    }

    /**
     * Returns whether auto-sell is enabled on a money hoe.
     * Default is enabled; the disabled flag must be explicitly set on the item.
     */
    public boolean isAutoSellEnabled(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return true;
        return !stack.getItemMeta().getPersistentDataContainer().has(autoSellDisabledKey, PersistentDataType.BYTE);
    }

    /**
     * Sets whether auto-sell is enabled on a money hoe and writes the updated metadata back to the stack.
     */
    public void setAutoSellEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || !stack.hasItemMeta()) return;

        ItemMeta meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        if (enabled) {
            pdc.remove(autoSellDisabledKey);
        } else {
            pdc.set(autoSellDisabledKey, PersistentDataType.BYTE, (byte) 1);
        }
        stack.setItemMeta(meta);
    }

    /**
     * Toggles auto-sell on a money hoe and writes the updated item back to the stack.
     *
     * @return {@code true} if auto-sell is now enabled, {@code false} if disabled
     */
    public boolean toggleAutoSell(ItemStack stack) {
        boolean next = !isAutoSellEnabled(stack);
        setAutoSellEnabled(stack, next);
        return next;
    }

    /**
     * Returns whether recursive selling is enabled on a money staff.
     * Default is disabled; the recursive flag must be explicitly set on the item.
     */
    public boolean isRecursiveSellEnabled(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(recursiveSellKey, PersistentDataType.BYTE);
    }

    /**
     * Sets whether recursive selling is enabled on a money staff and writes the updated metadata back to the stack.
     */
    public void setRecursiveSellEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || !stack.hasItemMeta()) return;

        ItemMeta meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        if (enabled) {
            pdc.set(recursiveSellKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(recursiveSellKey);
        }
        stack.setItemMeta(meta);
    }

    /**
     * Toggles recursive selling on a money staff and writes the updated item back to the stack.
     *
     * @return {@code true} if recursive selling is now enabled, {@code false} if disabled
     */
    public boolean toggleRecursiveSell(ItemStack stack) {
        boolean next = !isRecursiveSellEnabled(stack);
        setRecursiveSellEnabled(stack, next);
        return next;
    }

    /**
     * Returns whether order mode is enabled on a bazaar staff.
     * Default is disabled (listing restock mode); order mode flag must be set.
     */
    public boolean isOrderModeEnabled(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(orderModeKey, PersistentDataType.BYTE);
    }

    /**
     * Sets whether order mode is enabled on a bazaar staff.
     */
    public void setOrderModeEnabled(ItemStack stack, boolean enabled) {
        if (stack == null || !stack.hasItemMeta()) return;

        ItemMeta meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        if (enabled) {
            pdc.set(orderModeKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(orderModeKey);
        }
        stack.setItemMeta(meta);
    }

    /**
     * Toggles order mode on a bazaar staff and writes the updated item back to the stack.
     *
     * @return {@code true} if order mode is now enabled, {@code false} if listing mode
     */
    public boolean toggleOrderMode(ItemStack stack) {
        boolean next = !isOrderModeEnabled(stack);
        setOrderModeEnabled(stack, next);
        return next;
    }

    public NamespacedKey getToolKey() {
        return toolKey;
    }

    public NamespacedKey getUuidKey() {
        return uuidKey;
    }

    /**
     * Retrieves the tool's unique identifier from its persistent data container.
     *
     * @param stack the tool item stack
     * @return the UUID, or null if absent or invalid
     */
    public UUID getToolUuid(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String raw = stack.getItemMeta().getPersistentDataContainer().get(uuidKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Checks whether the tool item stack has a UUID in its persistent data container.
     */
    public boolean hasToolUuid(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(uuidKey, PersistentDataType.STRING);
    }

    /**
     * Sets or removes the tool UUID in the item's persistent data container.
     */
    public void setToolUuid(ItemStack stack, UUID uuid) {
        if (stack == null || !stack.hasItemMeta()) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        if (uuid != null) {
            pdc.set(uuidKey, PersistentDataType.STRING, uuid.toString());
        } else {
            pdc.remove(uuidKey);
        }
        stack.setItemMeta(meta);
    }

    /**
     * Assigns a fresh random UUID to the tool's persistent data container.
     *
     * @return the assigned UUID, or null if the stack has no metadata
     */
    public UUID assignRandomUuid(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        UUID uuid = UUID.randomUUID();
        meta.getPersistentDataContainer().set(uuidKey, PersistentDataType.STRING, uuid.toString());
        stack.setItemMeta(meta);
        return uuid;
    }
}
