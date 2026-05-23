package com.basicshop.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed wrapper around config.yml — global toggles, player messages, and click actions.
 */
public final class MainConfig {

    public enum ActionType { BUY, SELL }

    public record ClickAction(ActionType type, int amount) {}

    public record NavButtonConfig(Material material, String name, List<String> lore) {}
    public record FillerConfig(boolean enabled, Material material, String name, boolean hideTooltip) {}
    public record CategoryGuiConfig(NavButtonConfig backButton, NavButtonConfig prevButton, NavButtonConfig nextButton, FillerConfig filler) {}

    private final boolean buyingEnabled;
    private final boolean sellingEnabled;
    private final String prefix;
    private final Map<String, String> messages;

    // Item display template
    private final String itemDisplayName;
    private final List<String> itemDisplayLore;

    // Per-click actions
    private final Map<ClickType, ClickAction> clickActions;

    // Category GUI nav buttons + filler
    private final CategoryGuiConfig categoryGuiConfig;

    public MainConfig(FileConfiguration cfg) {
        this.buyingEnabled  = cfg.getBoolean("global.buying-enabled",  true);
        this.sellingEnabled = cfg.getBoolean("global.selling-enabled", true);
        this.prefix         = cfg.getString("messages.prefix", "<dark_gray>[<gold>BasicShop</gold>]</dark_gray> ");

        Map<String, String> msgs = new HashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) msgs.put(key, value);
            }
        }
        this.messages = Collections.unmodifiableMap(msgs);

        ConfigurationSection itemDisplay = cfg.getConfigurationSection("item-display");
        this.itemDisplayName = itemDisplay != null
                ? itemDisplay.getString("name", "<green>\u25A0 <item>")
                : "<green>\u25A0 <item>";
        List<String> loreCfg = itemDisplay != null ? itemDisplay.getStringList("lore") : new ArrayList<>();
        this.itemDisplayLore = Collections.unmodifiableList(loreCfg);

        // Default actions
        Map<ClickType, ClickAction> actions = new EnumMap<>(ClickType.class);
        actions.put(ClickType.LEFT,        new ClickAction(ActionType.SELL, 1));
        actions.put(ClickType.SHIFT_LEFT,  new ClickAction(ActionType.SELL, 64));
        actions.put(ClickType.RIGHT,       new ClickAction(ActionType.BUY,  1));
        actions.put(ClickType.SHIFT_RIGHT, new ClickAction(ActionType.BUY,  64));
        actions.put(ClickType.DROP,        new ClickAction(ActionType.SELL, -1));

        ConfigurationSection actionsCfg = cfg.getConfigurationSection("actions");
        if (actionsCfg != null) {
            Map<String, ClickType> keyMap = Map.of(
                    "left-click",        ClickType.LEFT,
                    "shift-left-click",  ClickType.SHIFT_LEFT,
                    "right-click",       ClickType.RIGHT,
                    "shift-right-click", ClickType.SHIFT_RIGHT,
                    "drop-key",          ClickType.DROP
            );
            for (Map.Entry<String, ClickType> entry : keyMap.entrySet()) {
                ConfigurationSection sub = actionsCfg.getConfigurationSection(entry.getKey());
                if (sub == null) continue;
                String actionStr = sub.getString("action", "sell");
                ActionType type = "buy".equalsIgnoreCase(actionStr) ? ActionType.BUY : ActionType.SELL;
                String amountStr = sub.getString("amount", "1");
                int amount = "all".equalsIgnoreCase(amountStr) ? -1 : Integer.parseInt(amountStr);
                actions.put(entry.getValue(), new ClickAction(type, amount));
            }
        }
        this.clickActions = Collections.unmodifiableMap(actions);

        ConfigurationSection catGuiSec = cfg.getConfigurationSection("category-gui");
        this.categoryGuiConfig = parseCategoryGuiConfig(catGuiSec);
    }

    private static CategoryGuiConfig parseCategoryGuiConfig(ConfigurationSection sec) {
        NavButtonConfig back = parseNavButton(
                sec != null ? sec.getConfigurationSection("back-button") : null,
                Material.BARRIER, "<gray>\u2717 Back to Categories");
        NavButtonConfig prev = parseNavButton(
                sec != null ? sec.getConfigurationSection("prev-button") : null,
                Material.ARROW, "<gray>\u2190 Previous Page");
        NavButtonConfig next = parseNavButton(
                sec != null ? sec.getConfigurationSection("next-button") : null,
                Material.ARROW, "<gray>Next Page \u2192");
        ConfigurationSection fs = sec != null ? sec.getConfigurationSection("filler") : null;
        boolean fillerEnabled     = fs == null || fs.getBoolean("enabled", true);
        Material fillerMat        = parseMat(fs != null ? fs.getString("material") : null, Material.GRAY_STAINED_GLASS_PANE);
        String fillerName         = fs != null ? fs.getString("name", " ") : " ";
        boolean fillerHideTooltip = fs == null || fs.getBoolean("hide-tooltip", true);
        return new CategoryGuiConfig(back, prev, next, new FillerConfig(fillerEnabled, fillerMat, fillerName, fillerHideTooltip));
    }

    private static NavButtonConfig parseNavButton(ConfigurationSection sec, Material defaultMat, String defaultName) {
        Material mat    = parseMat(sec != null ? sec.getString("material") : null, defaultMat);
        String name     = sec != null ? sec.getString("name", defaultName) : defaultName;
        List<String> lore = sec != null ? sec.getStringList("lore") : Collections.emptyList();
        return new NavButtonConfig(mat, name, lore);
    }

    private static Material parseMat(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public boolean isBuyingEnabled()  { return buyingEnabled; }
    public boolean isSellingEnabled() { return sellingEnabled; }
    public String getPrefix()         { return prefix; }

    /**
     * Returns the MiniMessage string for a given message key,
     * falling back to the key itself if not found.
     */
    public String getMessage(String key) {
        return messages.getOrDefault(key, "<red>Missing message: " + key);
    }

    public String getItemDisplayName()                    { return itemDisplayName; }
    public List<String> getItemDisplayLore()              { return itemDisplayLore; }
    public Map<ClickType, ClickAction> getClickActions()         { return clickActions; }
    public CategoryGuiConfig getCategoryGuiConfig()               { return categoryGuiConfig; }
}
