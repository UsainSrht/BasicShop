package com.basicshop.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsed wrapper around config.yml — global toggles and player messages.
 */
public final class MainConfig {

    private final boolean buyingEnabled;
    private final boolean sellingEnabled;
    private final String prefix;
    private final Map<String, String> messages;

    public MainConfig(FileConfiguration cfg) {
        this.buyingEnabled  = cfg.getBoolean("global.buying-enabled",  true);
        this.sellingEnabled = cfg.getBoolean("global.selling-enabled", true);
        this.prefix         = cfg.getString("messages.prefix", "<dark_gray>[<gold>BasicShop</gold>]</dark_gray> ");

        Map<String, String> msgs = new HashMap<>();
        org.bukkit.configuration.ConfigurationSection section = cfg.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) {
                    msgs.put(key, value);
                }
            }
        }
        this.messages = Collections.unmodifiableMap(msgs);
    }

    public boolean isBuyingEnabled() {
        return buyingEnabled;
    }

    public boolean isSellingEnabled() {
        return sellingEnabled;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Returns the MiniMessage string for a given message key,
     * falling back to the key itself if not found.
     */
    public String getMessage(String key) {
        return messages.getOrDefault(key, "<red>Missing message: " + key);
    }
}
