package me.usainsrht.basicshop.config;

import me.usainsrht.basicshop.api.model.ShopToolType;
import me.usainsrht.itemapi.yamlitem.YamlItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parsed tool definitions from config.yml ({@code tools} section).
 * All item attributes including use-cooldown are parsed using {@link YamlItem#parse(ConfigurationSection)}.
 */
public final class ToolsConfig {

    private static final Logger LOGGER = Logger.getLogger(ToolsConfig.class.getName());

    public record ToolDefinition(
            ItemStack itemStack
    ) {}

    private final Map<ShopToolType, ToolDefinition> tools;

    public ToolsConfig(FileConfiguration cfg) {
        Map<ShopToolType, ToolDefinition> parsed = new EnumMap<>(ShopToolType.class);
        ConfigurationSection section = cfg != null ? cfg.getConfigurationSection("tools") : null;

        for (ShopToolType type : ShopToolType.values()) {
            ConfigurationSection sub = section != null ? section.getConfigurationSection(type.getId()) : null;
            ItemStack stack = null;
            if (sub != null) {
                try {
                    stack = YamlItem.parse(sub);
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Failed to parse tool configuration section '" + type.getId() + "': " + t.getMessage(), t);
                }
            }
            if (stack == null || stack.getType().isAir()) {
                try {
                    stack = createDefaultItemStack(type);
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Failed to create default item for tool '" + type.getId() + "': " + t.getMessage(), t);
                }
            }
            if (stack == null || stack.getType().isAir()) {
                stack = new ItemStack(getDefaultMaterial(type));
            }

            parsed.put(type, new ToolDefinition(stack));
        }

        this.tools = Collections.unmodifiableMap(parsed);
    }

    public ToolDefinition get(ShopToolType type) {
        if (type == null) return new ToolDefinition(new ItemStack(Material.BLAZE_ROD));
        ToolDefinition def = tools.get(type);
        if (def == null || def.itemStack() == null) {
            def = new ToolDefinition(new ItemStack(getDefaultMaterial(type)));
        }
        return def;
    }

    public Map<ShopToolType, ToolDefinition> getAll() {
        return tools;
    }

    public static Material getDefaultMaterial(ShopToolType type) {
        if (type == null) return Material.BLAZE_ROD;
        return switch (type) {
            case MONEY_STAFF -> Material.BLAZE_ROD;
            case MONEY_HOE -> Material.GOLDEN_HOE;
            case SORTING_STAFF -> Material.AMETHYST_SHARD;
        };
    }

    private static ItemStack createDefaultItemStack(ShopToolType type) {
        YamlConfiguration config = new YamlConfiguration();
        switch (type) {
            case MONEY_STAFF -> {
                config.set("material", "BLAZE_ROD");
                config.set("name", "<gold>Money Staff");
                config.set("lore", List.of("<gray>Click a container to sell its contents."));
                config.set("enchantment-glint-override", true);
                config.set("use-cooldown.cooldown_group", "basicshop:money_staff");
                config.set("use-cooldown.seconds", 1.0);
            }
            case MONEY_HOE -> {
                config.set("material", "GOLDEN_HOE");
                config.set("name", "<gold>Money Hoe");
                config.set("lore", List.of(
                        "<gray>Break mature crops to sell drops and replant.",
                        "<gray>Click air to toggle auto-sell."
                ));
                config.set("enchants.efficiency", 5);
                config.set("enchants.unbreaking", 3);
                config.set("enchants.fortune", 3);
                config.set("enchants.mending", 1);
                config.set("use-cooldown.cooldown_group", "basicshop:money_hoe");
                config.set("use-cooldown.seconds", 0.5);
            }
            case SORTING_STAFF -> {
                config.set("material", "AMETHYST_SHARD");
                config.set("name", "<light_purple>Sorting Staff");
                config.set("lore", List.of("<gray>Click a container to sort its contents."));
                config.set("enchantment-glint-override", true);
                config.set("use-cooldown.cooldown_group", "basicshop:sorting_staff");
                config.set("use-cooldown.seconds", 2.0);
            }
        }
        try {
            ItemStack parsed = YamlItem.parse(config);
            if (parsed != null && !parsed.getType().isAir()) {
                return parsed;
            }
        } catch (Throwable ignored) {}
        return new ItemStack(getDefaultMaterial(type));
    }

    public static Optional<ShopToolType> resolveToolArgument(String input) {
        return ShopToolType.fromId(input);
    }
}
