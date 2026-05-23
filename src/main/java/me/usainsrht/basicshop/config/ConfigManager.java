package me.usainsrht.basicshop.config;

import me.usainsrht.basicshop.api.model.ShopCategory;
import me.usainsrht.basicshop.api.model.ShopItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.logging.Level;

/**
 * Central configuration manager.
 * Handles loading, saving defaults, and reloading all configuration files.
 */
public final class ConfigManager {

    private final Plugin plugin;

    private MainConfig       mainConfig;
    private CategoriesConfig categoriesConfig;
    private QuickSellConfig  quickSellConfig;
    private List<ShopCategory> categories;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all configuration files. Saves defaults from the jar if not present.
     * Call this from onEnable and on /shop reload.
     */
    public void load() {
        // Save default resource files from jar
        saveDefault("config.yml");
        saveDefault("categories.yml");
        saveDefault("quicksell.yml");
        saveDefaultCategories();

        plugin.reloadConfig();

        FileConfiguration mainCfg = plugin.getConfig();
        this.mainConfig = new MainConfig(mainCfg);

        FileConfiguration catsCfg = loadYml("categories.yml");
        this.categoriesConfig = new CategoriesConfig(catsCfg);

        FileConfiguration quickSellCfg = loadYml("quicksell.yml");
        this.quickSellConfig = new QuickSellConfig(quickSellCfg);

        this.categories = loadCategories();

        plugin.getLogger().info("Loaded " + categories.size() + " categories.");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void saveDefault(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private void saveDefaultCategories() {
        // Save built-in category files if the categories/ directory is empty or missing
        File categoriesDir = new File(plugin.getDataFolder(), "categories");
        if (!categoriesDir.exists() || categoriesDir.list() == null || categoriesDir.list().length == 0) {
            for (String name : new String[]{"blocks.yml", "tools.yml", "food.yml", "ores.yml", "misc.yml"}) {
                String resource = "categories/" + name;
                File dest = new File(categoriesDir, name);
                if (!dest.exists()) {
                    plugin.saveResource(resource, false);
                }
            }
        }
    }

    private FileConfiguration loadYml(String path) {
        File file = new File(plugin.getDataFolder(), path);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // Merge defaults from jar
        InputStream jarStream = plugin.getResource(path);
        if (jarStream != null) {
            YamlConfiguration jarDefaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(jarStream, StandardCharsets.UTF_8));
            cfg.setDefaults(jarDefaults);
        }
        return cfg;
    }

    private List<ShopCategory> loadCategories() {
        List<ShopCategory> result = new ArrayList<>();
        File categoriesDir = new File(plugin.getDataFolder(), "categories");

        if (!categoriesDir.isDirectory()) {
            return result;
        }

        File[] files = categoriesDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return result;
        }

        // Build a quick lookup from categoriesConfig to get slot / icon info
        Map<String, CategoriesConfig.CategoryEntry> entryMap = new java.util.HashMap<>();
        for (CategoriesConfig.CategoryEntry entry : categoriesConfig.getCategories()) {
            entryMap.put(entry.id(), entry);
        }

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                List<ShopItem> items = parseItems(id, cfg);

                CategoriesConfig.CategoryEntry entry = entryMap.get(id);
                String displayName = entry != null ? entry.displayName() : cfg.getString("title", id);
                String guiTitle    = cfg.getString("title", displayName);
                Material iconMat   = entry != null ? entry.material() : Material.CHEST;
                int slot           = entry != null ? entry.slot() : 0;
                List<String> lore  = entry != null ? entry.lore() : Collections.emptyList();

                result.add(new ShopCategory(id, displayName, guiTitle, iconMat, slot, lore, items));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load category file: " + file.getName(), e);
            }
        }

        return Collections.unmodifiableList(result);
    }

    private List<ShopItem> parseItems(String categoryId, YamlConfiguration cfg) {
        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection section = cfg.getConfigurationSection("items");
        if (section == null) return items;

        for (String key : section.getKeys(false)) {
            ConfigurationSection sub = section.getConfigurationSection(key);
            if (sub == null) continue;

            Material mat = Material.matchMaterial(key);
            if (mat == null) {
                plugin.getLogger().warning("Unknown material '" + key + "' in category '" + categoryId + "', skipping.");
                continue;
            }

            String id            = categoryId + ":" + key;
            OptionalDouble buyPrice  = parsePrice(sub.get("buy"));
            OptionalDouble sellPrice = parsePrice(sub.get("sell"));

            items.add(new ShopItem(id, mat, buyPrice, sellPrice));
        }

        return items;
    }

    private static OptionalDouble parsePrice(Object value) {
        if (value == null) return OptionalDouble.empty();
        double d;
        if (value instanceof Number n) {
            d = n.doubleValue();
        } else {
            try {
                d = Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return OptionalDouble.empty();
            }
        }
        return d < 0 ? OptionalDouble.empty() : OptionalDouble.of(d);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public MainConfig getMainConfig()             { return mainConfig; }
    public CategoriesConfig getCategoriesConfig() { return categoriesConfig; }
    public QuickSellConfig getQuickSellConfig()   { return quickSellConfig; }
    public List<ShopCategory> getCategories()     { return categories; }
}
