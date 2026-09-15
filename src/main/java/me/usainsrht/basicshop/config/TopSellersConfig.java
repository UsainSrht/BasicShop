package me.usainsrht.basicshop.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * Parsed wrapper around topsellers.yml.
 */
public final class TopSellersConfig {

    private final String guiTitle;
    private final int guiRows;
    private final int backButtonSlot;
    private final Material backButtonMaterial;
    private final String backButtonName;
    private final List<String> backButtonLore;
    private final boolean backButtonReturnToCategories;

    private final boolean fillerEnabled;
    private final Material fillerMaterial;
    private final String fillerName;
    private final boolean fillerHideTooltip;

    private final List<Integer> itemSlots;
    private final int emptySlot;
    private final Material emptyItemMaterial;
    private final String emptyItemName;
    private final List<String> emptyItemLore;

    private final String itemName;
    private final List<String> itemLore;

    public TopSellersConfig(FileConfiguration cfg) {
        this.guiTitle = cfg.getString("gui.title", "<gold><bold>Top Sellers</bold></gold> <gray>(Last <days> Days)</gray>");
        this.guiRows = Math.max(1, Math.min(6, cfg.getInt("gui.rows", 4)));

        ConfigurationSection back = cfg.getConfigurationSection("gui.back-button");
        if (back != null) {
            this.backButtonSlot = back.getInt("slot", 31);
            this.backButtonMaterial = parseMaterial(back.getString("material"), Material.BARRIER);
            this.backButtonName = back.getString("name", "<gray>← Back to Categories");
            this.backButtonLore = back.getStringList("lore");
            this.backButtonReturnToCategories = back.getBoolean("return-to-categories", true);
        } else {
            this.backButtonSlot = 31;
            this.backButtonMaterial = Material.BARRIER;
            this.backButtonName = "<gray>← Back to Categories";
            this.backButtonLore = Collections.emptyList();
            this.backButtonReturnToCategories = true;
        }

        ConfigurationSection filler = cfg.getConfigurationSection("gui.filler");
        if (filler != null) {
            this.fillerEnabled = filler.getBoolean("enabled", true);
            this.fillerMaterial = parseMaterial(filler.getString("material"), Material.GRAY_STAINED_GLASS_PANE);
            this.fillerName = filler.getString("name", " ");
            this.fillerHideTooltip = filler.getBoolean("hide-tooltip", true);
        } else {
            this.fillerEnabled = false;
            this.fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
            this.fillerName = " ";
            this.fillerHideTooltip = true;
        }

        List<Integer> slots = cfg.getIntegerList("gui.item-slots");
        this.itemSlots = slots.isEmpty() ? List.of(10, 11, 12, 13, 14, 19, 20, 21, 22, 23) : Collections.unmodifiableList(slots);

        this.emptySlot = cfg.getInt("gui.empty-slot", 13);
        ConfigurationSection empty = cfg.getConfigurationSection("gui.empty-item");
        if (empty != null) {
            this.emptyItemMaterial = parseMaterial(empty.getString("material"), Material.BARRIER);
            this.emptyItemName = empty.getString("name", "<red>No sales recorded.");
            this.emptyItemLore = empty.getStringList("lore");
        } else {
            this.emptyItemMaterial = Material.BARRIER;
            this.emptyItemName = "<red>No sales recorded.";
            this.emptyItemLore = Collections.emptyList();
        }

        this.itemName = cfg.getString("item-name", "<gold>#<rank></gold> <yellow><item></yellow>");
        this.itemLore = Collections.unmodifiableList(cfg.getStringList("item-lore"));
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material mat = Material.matchMaterial(name.toUpperCase());
        return mat != null ? mat : fallback;
    }

    public String getGuiTitle() { return guiTitle; }
    public int getGuiRows() { return guiRows; }
    public int getBackButtonSlot() { return backButtonSlot; }
    public Material getBackButtonMaterial() { return backButtonMaterial; }
    public String getBackButtonName() { return backButtonName; }
    public List<String> getBackButtonLore() { return backButtonLore; }
    public boolean isBackButtonReturnToCategories() { return backButtonReturnToCategories; }
    public boolean isFillerEnabled() { return fillerEnabled; }
    public Material getFillerMaterial() { return fillerMaterial; }
    public String getFillerName() { return fillerName; }
    public boolean isFillerHideTooltip() { return fillerHideTooltip; }
    public List<Integer> getItemSlots() { return itemSlots; }
    public int getEmptySlot() { return emptySlot; }
    public Material getEmptyItemMaterial() { return emptyItemMaterial; }
    public String getEmptyItemName() { return emptyItemName; }
    public List<String> getEmptyItemLore() { return emptyItemLore; }
    public String getItemName() { return itemName; }
    public List<String> getItemLore() { return itemLore; }
}
