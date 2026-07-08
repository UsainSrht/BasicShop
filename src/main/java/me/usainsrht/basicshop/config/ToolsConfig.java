package me.usainsrht.basicshop.config;

import me.usainsrht.basicshop.api.model.ShopToolType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parsed tool definitions from config.yml ({@code tools} section).
 */
public final class ToolsConfig {

    public record ToolDefinition(
            Material material,
            String name,
            List<String> lore,
            Map<Enchantment, Integer> enchants,
            Boolean enchantmentGlintOverride
    ) {}

    private static ToolDefinition defaultFor(ShopToolType type) {
        return switch (type) {
            case MONEY_STAFF -> new ToolDefinition(
                    Material.BLAZE_ROD,
                    "<gold>Money Staff",
                    List.of("<gray>Click a container to sell its contents."),
                    Map.of(),
                    true
            );
            case MONEY_HOE -> new ToolDefinition(
                    Material.GOLDEN_HOE,
                    "<gold>Money Hoe",
                    List.of(
                            "<gray>Break mature crops to sell drops and replant.",
                            "<gray>Click air to toggle auto-sell."
                    ),
                    buildDefaultHoeEnchants(),
                    null
            );
        };
    }

    private final Map<ShopToolType, ToolDefinition> tools;

    public ToolsConfig(FileConfiguration cfg) {
        Map<ShopToolType, ToolDefinition> parsed = new EnumMap<>(ShopToolType.class);
        ConfigurationSection section = cfg.getConfigurationSection("tools");

        for (ShopToolType type : ShopToolType.values()) {
            ToolDefinition fallback = defaultFor(type);
            ConfigurationSection sub = section != null ? section.getConfigurationSection(type.getId()) : null;

            Material material = parseMaterial(
                    sub != null ? sub.getString("material") : null,
                    fallback.material()
            );
            String name = sub != null ? sub.getString("name", fallback.name()) : fallback.name();
            List<String> lore = sub != null ? sub.getStringList("lore") : fallback.lore();
            if (lore.isEmpty()) lore = fallback.lore();
            Map<Enchantment, Integer> enchants = parseEnchants(sub, fallback.enchants());
            Boolean glintOverride = parseGlintOverride(sub, fallback.enchantmentGlintOverride());

            parsed.put(type, new ToolDefinition(
                    material,
                    name,
                    Collections.unmodifiableList(lore),
                    enchants,
                    glintOverride
            ));
        }

        this.tools = Collections.unmodifiableMap(parsed);
    }

    public ToolDefinition get(ShopToolType type) {
        return tools.get(type);
    }

    public Map<ShopToolType, ToolDefinition> getAll() {
        return tools;
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material mat = Material.matchMaterial(name.toUpperCase());
        return mat != null ? mat : fallback;
    }

    private static Map<Enchantment, Integer> buildDefaultHoeEnchants() {
        Map<Enchantment, Integer> map = new LinkedHashMap<>();
        putEnchant(map, "efficiency", 5);
        putEnchant(map, "unbreaking", 3);
        putEnchant(map, "fortune", 3);
        putEnchant(map, "mending", 1);
        return Collections.unmodifiableMap(map);
    }

    private static Map<Enchantment, Integer> parseEnchants(ConfigurationSection sub, Map<Enchantment, Integer> fallback) {
        if (sub == null || !sub.isConfigurationSection("enchants")) {
            return fallback;
        }
        ConfigurationSection enchantsSection = sub.getConfigurationSection("enchants");
        if (enchantsSection == null || enchantsSection.getKeys(false).isEmpty()) {
            return fallback;
        }

        Map<Enchantment, Integer> parsed = new LinkedHashMap<>();
        for (String key : enchantsSection.getKeys(false)) {
            Enchantment enchant = resolveEnchantment(key);
            if (enchant == null) continue;
            int level = enchantsSection.getInt(key, 1);
            if (level > 0) {
                parsed.put(enchant, level);
            }
        }
        return parsed.isEmpty() ? fallback : Collections.unmodifiableMap(parsed);
    }

    private static Boolean parseGlintOverride(ConfigurationSection sub, Boolean fallback) {
        if (sub == null || !sub.contains("enchantment-glint-override")) {
            return fallback;
        }
        return sub.getBoolean("enchantment-glint-override");
    }

    private static void putEnchant(Map<Enchantment, Integer> map, String key, int level) {
        Enchantment enchant = resolveEnchantment(key);
        if (enchant != null) {
            map.put(enchant, level);
        }
    }

    private static Enchantment resolveEnchantment(String key) {
        if (key == null || key.isBlank()) return null;

        String normalized = key.toLowerCase().replace('_', '-');
        Enchantment enchant = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(normalized));
        if (enchant != null) return enchant;

        return Registry.ENCHANTMENT.get(NamespacedKey.fromString(key));
    }

    public static Optional<ShopToolType> resolveToolArgument(String input) {
        return ShopToolType.fromId(input);
    }
}
