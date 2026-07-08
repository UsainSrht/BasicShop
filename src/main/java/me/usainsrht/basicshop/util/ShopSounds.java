package me.usainsrht.basicshop.util;

import me.usainsrht.basicshop.config.MainConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Plays configured shop sounds for a player.
 */
public final class ShopSounds {

    private ShopSounds() {}

    public static void play(Player player, MainConfig.SoundSettings settings) {
        if (player == null || settings == null || !settings.enabled()) return;

        String name = settings.sound();
        if (name == null || name.isBlank()) return;

        Sound sound = resolveSound(name);
        if (sound == null) return;

        player.playSound(player.getLocation(), sound, settings.volume(), settings.pitch());
    }

    private static Sound resolveSound(String name) {
        try {
            return Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            // Fall through to registry lookup (e.g. entity.experience_orb.pickup)
        }

        String key = name.trim().toLowerCase();
        if (key.contains(":")) {
            return Registry.SOUNDS.get(NamespacedKey.fromString(key));
        }
        return Registry.SOUNDS.get(NamespacedKey.minecraft(key.replace('_', '.')));
    }
}
