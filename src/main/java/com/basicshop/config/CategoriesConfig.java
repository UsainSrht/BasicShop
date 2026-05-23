package com.basicshop.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed wrapper around categories.yml — main GUI layout and quicksell button.
 */
public final class CategoriesConfig {

    public record CategoryEntry(
            String id,
            int slot,
            Material material,
            String displayName,
            List<String> lore
    ) {}

    private final String guiTitle;
    private final int guiRows;
    private final int quicksellSlot;
    private final boolean fillerEnabled;
    private final Material fillerMaterial;
    private final String fillerName;
    private final boolean fillerHideTooltip;
    private final Material quicksellMaterial;
    private final String quicksellName;
    private final List<String> quicksellLore;
    private final List<CategoryEntry> categories;

    public CategoriesConfig(FileConfiguration cfg) {
        this.guiTitle      = cfg.getString("gui.title", "<gold><bold>Shop</bold></gold>");
        this.guiRows       = cfg.getInt("gui.rows", 6);
        this.quicksellSlot = cfg.getInt("gui.quicksell-slot", 49);

        ConfigurationSection filler = cfg.getConfigurationSection("gui.filler");
        if (filler != null) {
            this.fillerEnabled     = filler.getBoolean("enabled", true);
            this.fillerMaterial    = parseMaterial(filler.getString("material"), Material.GRAY_STAINED_GLASS_PANE);
            this.fillerName        = filler.getString("name", " ");
            this.fillerHideTooltip = filler.getBoolean("hide-tooltip", true);
        } else {
            this.fillerEnabled     = false;
            this.fillerMaterial    = Material.GRAY_STAINED_GLASS_PANE;
            this.fillerName        = " ";
            this.fillerHideTooltip = true;
        }

        ConfigurationSection qs = cfg.getConfigurationSection("gui.quicksell-button");
        if (qs != null) {
            this.quicksellMaterial = parseMaterial(qs.getString("material"), Material.HOPPER);
            this.quicksellName     = qs.getString("name", "<yellow>Quick Sell");
            this.quicksellLore     = qs.getStringList("lore");
        } else {
            this.quicksellMaterial = Material.HOPPER;
            this.quicksellName     = "<yellow>Quick Sell";
            this.quicksellLore     = Collections.emptyList();
        }

        List<CategoryEntry> cats = new ArrayList<>();
        ConfigurationSection catsSec = cfg.getConfigurationSection("categories");
        if (catsSec != null) {
            for (String id : catsSec.getKeys(false)) {
                ConfigurationSection sub = catsSec.getConfigurationSection(id);
                if (sub == null) continue;
                int slot         = sub.getInt("slot", 0);
                Material mat     = parseMaterial(sub.getString("material"), Material.CHEST);
                String name      = sub.getString("name", id);
                List<String> lore = sub.getStringList("lore");
                cats.add(new CategoryEntry(id, slot, mat, name, lore));
            }
        }
        this.categories = Collections.unmodifiableList(cats);
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        Material mat = Material.matchMaterial(name);
        return mat != null ? mat : fallback;
    }

    public String getGuiTitle() { return guiTitle; }
    public int getGuiRows()     { return guiRows; }
    public int getQuicksellSlot() { return quicksellSlot; }
    public boolean isFillerEnabled()        { return fillerEnabled; }
    public Material getFillerMaterial()      { return fillerMaterial; }
    public String getFillerName()            { return fillerName; }
    public boolean isFillerHideTooltip()     { return fillerHideTooltip; }
    public Material getQuicksellMaterial()   { return quicksellMaterial; }
    public String getQuicksellName()       { return quicksellName; }
    public List<String> getQuicksellLore() { return quicksellLore; }
    public List<CategoryEntry> getCategories() { return categories; }
}
