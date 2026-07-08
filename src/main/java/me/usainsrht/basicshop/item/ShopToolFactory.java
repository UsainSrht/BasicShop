package me.usainsrht.basicshop.item;

import me.usainsrht.basicshop.api.model.ShopToolType;
import me.usainsrht.basicshop.config.ToolsConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Builds and identifies tagged shop tool items.
 */
public final class ShopToolFactory {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ToolsConfig toolsConfig;
    private final NamespacedKey toolKey;
    /** When present, auto-sell is disabled (matches legacy {@code custom:autosell} semantics). */
    private final NamespacedKey autoSellDisabledKey;

    public ShopToolFactory(Plugin plugin, ToolsConfig toolsConfig) {
        this.toolsConfig = toolsConfig;
        this.toolKey = new NamespacedKey(plugin, "shop_tool");
        this.autoSellDisabledKey = new NamespacedKey(plugin, "autosell_disabled");
    }

    public ItemStack create(ShopToolType type, int amount) {
        ToolsConfig.ToolDefinition def = toolsConfig.get(type);
        ItemStack stack = new ItemStack(def.material(), amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(withoutDefaultItalic(MM.deserialize(def.name())));
            List<Component> lore = def.lore().stream()
                    .map(MM::deserialize)
                    .map(ShopToolFactory::withoutDefaultItalic)
                    .toList();
            meta.lore(lore);
            def.enchants().forEach((enchant, level) -> meta.addEnchant(enchant, level, true));
            if (def.enchantmentGlintOverride() != null) {
                meta.setEnchantmentGlintOverride(def.enchantmentGlintOverride());
            }
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, type.getId());
            stack.setItemMeta(meta);
        }
        return stack;
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

    private static Component withoutDefaultItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
