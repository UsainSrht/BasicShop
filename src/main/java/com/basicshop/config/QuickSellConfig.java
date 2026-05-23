package com.basicshop.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * Parsed wrapper around quicksell.yml — QuickSell GUI layout.
 */
public final class QuickSellConfig {

    private final String guiTitle;
    private final int guiRows;

    private final int closeSlot;
    private final Material closeMaterial;
    private final String closeName;

    private final int sellAllSlot;
    private final Material sellAllMaterial;
    private final String sellAllName;
    private final List<String> sellAllLore;

    private final boolean fillerEnabled;
    private final Material fillerMaterial;
    private final String fillerName;
    private final boolean fillerHideTooltip;

    private final int emptySlot;
    private final Material emptyMaterial;
    private final String emptyName;
    private final List<String> emptyLore;

    private final String itemName;
    private final List<String> itemLore;

    public QuickSellConfig(FileConfiguration cfg) {
        this.guiTitle = cfg.getString("gui.title", "<gold>Quick Sell \u2014 Inventory");
        this.guiRows  = cfg.getInt("gui.rows", 6);

        ConfigurationSection close = cfg.getConfigurationSection("gui.close-button");
        this.closeSlot     = close != null ? close.getInt("slot", 45) : 45;
        this.closeMaterial = parseMaterial(close != null ? close.getString("material") : null, Material.BARRIER);
        this.closeName     = close != null ? close.getString("name", "<gray>\u2717 Close") : "<gray>\u2717 Close";

        ConfigurationSection sellAll = cfg.getConfigurationSection("gui.sell-all-button");
        this.sellAllSlot     = sellAll != null ? sellAll.getInt("slot", 49) : 49;
        this.sellAllMaterial = parseMaterial(sellAll != null ? sellAll.getString("material") : null, Material.EMERALD);
        this.sellAllName     = sellAll != null ? sellAll.getString("name", "<green><bold>Sell All</bold></green>") : "<green><bold>Sell All</bold></green>";
        this.sellAllLore     = sellAll != null ? sellAll.getStringList("lore") : Collections.emptyList();

        ConfigurationSection filler = cfg.getConfigurationSection("gui.filler");
        this.fillerEnabled     = filler == null || filler.getBoolean("enabled", true);
        this.fillerMaterial    = parseMaterial(filler != null ? filler.getString("material") : null, Material.GRAY_STAINED_GLASS_PANE);
        this.fillerName        = filler != null ? filler.getString("name", " ") : " ";
        this.fillerHideTooltip = filler == null || filler.getBoolean("hide-tooltip", true);

        ConfigurationSection empty = cfg.getConfigurationSection("gui.empty-item");
        this.emptySlot     = cfg.getInt("gui.empty-slot", 22);
        this.emptyMaterial = parseMaterial(empty != null ? empty.getString("material") : null, Material.BARRIER);
        this.emptyName     = empty != null ? empty.getString("name", "<red>No sellable items found.") : "<red>No sellable items found.";
        this.emptyLore     = empty != null ? empty.getStringList("lore") : Collections.emptyList();

        this.itemName = cfg.getString("gui.item-name", "<item>");
        this.itemLore = cfg.getStringList("gui.item-lore");
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public String getGuiTitle()          { return guiTitle; }
    public int getGuiRows()              { return guiRows; }
    public int getCloseSlot()            { return closeSlot; }
    public Material getCloseMaterial()   { return closeMaterial; }
    public String getCloseName()         { return closeName; }
    public int getSellAllSlot()          { return sellAllSlot; }
    public Material getSellAllMaterial() { return sellAllMaterial; }
    public String getSellAllName()       { return sellAllName; }
    public List<String> getSellAllLore() { return sellAllLore; }
    public boolean isFillerEnabled()     { return fillerEnabled; }
    public Material getFillerMaterial()  { return fillerMaterial; }
    public String getFillerName()        { return fillerName; }
    public boolean isFillerHideTooltip() { return fillerHideTooltip; }
    public int getEmptySlot()            { return emptySlot; }
    public Material getEmptyMaterial()   { return emptyMaterial; }
    public String getEmptyName()         { return emptyName; }
    public List<String> getEmptyLore()   { return emptyLore; }
    public String getItemName()          { return itemName; }
    public List<String> getItemLore()    { return itemLore; }
}
