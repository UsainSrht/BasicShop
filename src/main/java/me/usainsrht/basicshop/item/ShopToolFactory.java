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

/**
 * Builds and identifies tagged shop tool items.
 */
public final class ShopToolFactory {

    private final ConfigManager configManager;
    private final NamespacedKey toolKey;
    /** When present, auto-sell is disabled (matches legacy {@code custom:autosell} semantics). */
    private final NamespacedKey autoSellDisabledKey;

    public ShopToolFactory(Plugin plugin, ConfigManager configManager) {
        this.configManager = configManager;
        this.toolKey = new NamespacedKey(plugin, "shop_tool");
        this.autoSellDisabledKey = new NamespacedKey(plugin, "autosell_disabled");
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
     * Toggles auto-sell on a money hoe and writes the updated item back to the stack.
     *
     * @return {@code true} if auto-sell is now enabled, {@code false} if disabled
     */
    public boolean toggleAutoSell(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return true;

        ItemMeta meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        boolean nowEnabled;

        if (pdc.has(autoSellDisabledKey, PersistentDataType.BYTE)) {
            pdc.remove(autoSellDisabledKey);
            nowEnabled = true;
        } else {
            pdc.set(autoSellDisabledKey, PersistentDataType.BYTE, (byte) 1);
            nowEnabled = false;
        }

        stack.setItemMeta(meta);
        return nowEnabled;
    }

    public NamespacedKey getToolKey() {
        return toolKey;
    }
}
