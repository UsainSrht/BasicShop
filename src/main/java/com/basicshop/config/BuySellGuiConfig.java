package com.basicshop.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;

/**
 * Parsed wrapper around buysell.yml — slot layout and button display configs for the Item GUI.
 */
public final class BuySellGuiConfig {

    public record ButtonConfig(
            Material material,
            String name,
            List<String> lore
    ) {}

    private final String guiTitleTemplate;
    private final int guiRows;

    private final int slotItemDisplay;
    private final int slotBuyOne;
    private final int slotBuyStack;
    private final int slotSellOne;
    private final int slotSellAll;
    private final int slotBack;

    private final boolean fillerEnabled;
    private final Material fillerMaterial;
    private final String fillerName;

    private final ButtonConfig backButton;
    private final ButtonConfig buyOneButton;
    private final ButtonConfig buyStackButton;
    private final ButtonConfig sellOneButton;
    private final ButtonConfig sellAllButton;
    private final ButtonConfig sellStackButton;
    private final ButtonConfig disabledButton;

    public BuySellGuiConfig(FileConfiguration cfg) {
        this.guiTitleTemplate = cfg.getString("gui.title", "<gold>Buy / Sell — <item></gold>");
        this.guiRows          = cfg.getInt("gui.rows", 3);

        ConfigurationSection slots = cfg.getConfigurationSection("gui.slots");
        this.slotItemDisplay = slots != null ? slots.getInt("item-display", 13) : 13;
        this.slotBuyOne      = slots != null ? slots.getInt("buy-one",      10) : 10;
        this.slotBuyStack    = slots != null ? slots.getInt("buy-stack",    11) : 11;
        this.slotSellOne     = slots != null ? slots.getInt("sell-one",     15) : 15;
        this.slotSellAll     = slots != null ? slots.getInt("sell-all",     16) : 16;

        ConfigurationSection backSection = cfg.getConfigurationSection("gui.back-button");
        this.slotBack = backSection != null ? backSection.getInt("slot", 22) : 22;
        this.backButton = readButton(backSection, Material.ARROW, "<gray>← Back", Collections.emptyList());

        ConfigurationSection filler = cfg.getConfigurationSection("gui.filler");
        this.fillerEnabled  = filler != null && filler.getBoolean("enabled", true);
        this.fillerMaterial = parseMaterial(filler != null ? filler.getString("material") : null, Material.GRAY_STAINED_GLASS_PANE);
        this.fillerName     = filler != null ? filler.getString("name", " ") : " ";

        ConfigurationSection buttons = cfg.getConfigurationSection("buttons");
        this.buyOneButton    = readButton(buttons != null ? buttons.getConfigurationSection("buy-one")    : null, Material.LIME_STAINED_GLASS_PANE,  "<green>Buy 1",         Collections.emptyList());
        this.buyStackButton  = readButton(buttons != null ? buttons.getConfigurationSection("buy-stack")  : null, Material.GREEN_STAINED_GLASS_PANE,  "<green>Buy Stack (64)", Collections.emptyList());
        this.sellOneButton   = readButton(buttons != null ? buttons.getConfigurationSection("sell-one")   : null, Material.RED_STAINED_GLASS_PANE,    "<red>Sell 1",          Collections.emptyList());
        this.sellAllButton   = readButton(buttons != null ? buttons.getConfigurationSection("sell-all")   : null, Material.RED_STAINED_GLASS_PANE,    "<dark_red>Sell All",  Collections.emptyList());
        this.sellStackButton = readButton(buttons != null ? buttons.getConfigurationSection("sell-stack") : null, Material.ORANGE_STAINED_GLASS_PANE,  "<gold>Sell Stack",    Collections.emptyList());
        this.disabledButton  = readButton(buttons != null ? buttons.getConfigurationSection("disabled")   : null, Material.BARRIER,                    "<dark_gray>Disabled", Collections.emptyList());
    }

    private static ButtonConfig readButton(ConfigurationSection sec, Material defMat, String defName, List<String> defLore) {
        if (sec == null) return new ButtonConfig(defMat, defName, defLore);
        Material mat = parseMaterial(sec.getString("material"), defMat);
        String name  = sec.getString("name", defName);
        List<String> lore = sec.getStringList("lore");
        return new ButtonConfig(mat, name, lore);
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public String getGuiTitleTemplate() { return guiTitleTemplate; }
    public int getGuiRows()             { return guiRows; }
    public int getSlotItemDisplay()     { return slotItemDisplay; }
    public int getSlotBuyOne()          { return slotBuyOne; }
    public int getSlotBuyStack()        { return slotBuyStack; }
    public int getSlotSellOne()         { return slotSellOne; }
    public int getSlotSellAll()         { return slotSellAll; }
    public int getSlotBack()            { return slotBack; }
    public boolean isFillerEnabled()    { return fillerEnabled; }
    public Material getFillerMaterial() { return fillerMaterial; }
    public String getFillerName()       { return fillerName; }
    public ButtonConfig getBackButton()      { return backButton; }
    public ButtonConfig getBuyOneButton()    { return buyOneButton; }
    public ButtonConfig getBuyStackButton()  { return buyStackButton; }
    public ButtonConfig getSellOneButton()   { return sellOneButton; }
    public ButtonConfig getSellAllButton()   { return sellAllButton; }
    public ButtonConfig getSellStackButton() { return sellStackButton; }
    public ButtonConfig getDisabledButton()  { return disabledButton; }
}
